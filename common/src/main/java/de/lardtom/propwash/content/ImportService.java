package de.lardtom.propwash.content;

import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.apiimpl.BuildAccessImpl;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintCost;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.content.paint.PaintItems;
import de.lardtom.propwash.content.paint.PaintService;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.drone.CraftNames;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.server.WorldRules;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ImportService {
    public static final int ANY_CONTAINER = -1;
    public static final String CUSTOM_DYES = "custom";
    public static final int CREATE_PERMISSION_LEVEL = 2;

    private ImportService() {
    }

    public static ImportResult apply(ServerPlayer player, ImportRequest request) {
        return apply(player, ANY_CONTAINER, request);
    }

    public static ImportResult apply(ServerPlayer player, int containerId, ImportRequest request) {
        if (player.isSpectator()) {
            return ImportResult.of(ImportStatus.NOT_ALLOWED);
        }
        try {
            return switch (request.target()) {
                case WORKBENCH -> workbench(player, containerId, request);
                case HELD_DRONE -> heldDrone(player, request);
                case NEW_DRONE -> newDrone(player, request);
            };
        } catch (IllegalArgumentException e) {
            return ImportResult.of(ImportStatus.INVALID);
        }
    }

    private static ImportResult workbench(ServerPlayer player, int containerId, ImportRequest request) {
        if (!(player.containerMenu instanceof DroneWorkbenchMenu menu) || containerId != ANY_CONTAINER && menu.containerId != containerId
                || !menu.stillValid(player)) {
            return ImportResult.of(ImportStatus.NO_TARGET);
        }
        DroneData data = request.data();
        Set<ImportLayer> applied = EnumSet.noneOf(ImportLayer.class);
        boolean changed = false;
        if (request.layers().contains(ImportLayer.PARTS)) {
            List<Identifier> unknown = Conversions.unknownParts(data.build());
            if (!unknown.isEmpty()) {
                return new ImportResult(ImportStatus.UNKNOWN_PARTS, applied, unknown, Map.of());
            }
            Optional<DroneBuild> build = Conversions.build(data.build());
            if (build.isEmpty()) {
                return ImportResult.of(ImportStatus.INVALID);
            }
            PartsOutcome outcome = applyParts(player, menu, build.get());
            if (outcome.failure() != null) {
                return outcome.failure();
            }
            changed |= outcome.changed();
            applied.add(ImportLayer.PARTS);
        }
        if (request.layers().contains(ImportLayer.PAINT)) {
            DronePaint target = Conversions.paint(data.paint());
            ItemStack frame = menu.frame();
            if (frame.isEmpty()) {
                return new ImportResult(ImportStatus.NO_TARGET, applied, List.of(), Map.of());
            }
            DronePaint current = PaintItems.paint(frame);
            if (!current.equals(target)) {
                PaintService.Result result = menu.applyPaint(player, target);
                if (result == PaintService.Result.MISSING_DYES) {
                    return new ImportResult(ImportStatus.MISSING_DYES, applied, List.of(), missingDyes(player, PaintCost.of(current, target)));
                }
                if (result == PaintService.Result.NO_FRAME) {
                    return new ImportResult(ImportStatus.NO_TARGET, applied, List.of(), Map.of());
                }
                changed |= result == PaintService.Result.APPLIED;
            }
            applied.add(ImportLayer.PAINT);
        }
        if (request.layers().contains(ImportLayer.TUNE)) {
            TuneSpec imported = data.tune().only(request.tuneGroups());
            DroneWorkbenchMenu.PendingTune pending = new DroneWorkbenchMenu.PendingTune(imported, request.tuneGroups());
            changed |= !menu.pendingTune().equals(Optional.of(pending));
            menu.setPendingTune(pending);
            applied.add(ImportLayer.TUNE);
        }
        if (request.layers().contains(ImportLayer.NAME)) {
            ItemStack frame = menu.frame();
            if (frame.isEmpty()) {
                return new ImportResult(ImportStatus.NO_TARGET, applied, List.of(), Map.of());
            }
            String name = importedName(data);
            if (!name.isEmpty() && !name.equals(CraftNames.of(frame.get(DataComponents.CUSTOM_NAME)))) {
                ItemStack named = frame.copy();
                named.set(DataComponents.CUSTOM_NAME, Component.literal(name));
                menu.partsContainer().setItem(BuildSlots.FRAME, named);
                menu.broadcastChanges();
                changed = true;
            }
            applied.add(ImportLayer.NAME);
        }
        return changed ? ImportResult.applied(applied) : new ImportResult(ImportStatus.UNCHANGED, applied, List.of(), Map.of());
    }

    private record PartsOutcome(boolean changed, ImportResult failure) {
    }

    private static PartsOutcome applyParts(ServerPlayer player, DroneWorkbenchMenu menu, DroneBuild build) {
        List<PartInfo> target = BuildSlots.layout(build);
        SimpleContainer parts = menu.partsContainer();
        List<ItemStack> next = new ArrayList<>(BuildSlots.COUNT);
        List<ItemStack> outgoing = new ArrayList<>();
        List<PartInfo> current = new ArrayList<>(BuildSlots.COUNT);
        for (int slot = 0; slot < BuildSlots.COUNT; slot++) {
            ItemStack stack = parts.getItem(slot);
            PartInfo want = target.get(slot);
            PartInfo have = DroneAssembly.part(stack).orElse(null);
            current.add(have);
            if (want != null && have == want) {
                next.add(stack);
                continue;
            }
            if (!stack.isEmpty()) {
                outgoing.add(stack);
            }
            next.add(ItemStack.EMPTY);
        }
        Inventory inventory = player.getInventory();
        NonNullList<ItemStack> items = inventory.getNonEquipmentItems();
        PartsPlan plan = plan(target, current, items, player.hasInfiniteMaterials());
        if (plan.needSlots().isEmpty() && outgoing.isEmpty()) {
            return new PartsOutcome(false, null);
        }
        if (!plan.missing().isEmpty()) {
            return new PartsOutcome(false, ImportResult.missingParts(plan.missing()));
        }
        if (player.hasInfiniteMaterials()) {
            for (int slot : plan.needSlots()) {
                next.set(slot, ContentItems.stack(target.get(slot)));
            }
        } else {
            int free = 0;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).isEmpty() || plan.left()[i] == 0) {
                    free++;
                }
            }
            if (free < outgoing.size()) {
                return new PartsOutcome(false, ImportResult.of(ImportStatus.NOT_ALLOWED));
            }
            for (int slot : plan.needSlots()) {
                next.set(slot, items.get(plan.source()[slot]).split(1));
            }
            inventory.setChanged();
        }
        menu.replaceParts(next);
        for (ItemStack stack : outgoing) {
            inventory.placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
        }
        menu.broadcastChanges();
        return new PartsOutcome(true, null);
    }

    public record PartsPlan(List<Integer> needSlots, int[] source, int[] left, List<Identifier> missing) {
    }

    public static List<Identifier> missingParts(DroneBuild build, List<? extends @Nullable PartInfo> current, List<ItemStack> inventory,
                                                boolean infiniteMaterials) {
        return plan(BuildSlots.layout(build), current, inventory, infiniteMaterials).missing();
    }

    public static PartsPlan plan(List<? extends @Nullable PartInfo> target, List<? extends @Nullable PartInfo> current, List<ItemStack> items,
                                 boolean infiniteMaterials) {
        List<Integer> needSlots = new ArrayList<>();
        for (int slot = 0; slot < BuildSlots.COUNT; slot++) {
            PartInfo want = target.get(slot);
            PartInfo have = slot < current.size() ? current.get(slot) : null;
            if (want != null && have != want) {
                needSlots.add(slot);
            }
        }
        int[] left = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            left[i] = items.get(i).getCount();
        }
        int[] source = new int[BuildSlots.COUNT];
        List<Identifier> missing = new ArrayList<>();
        for (int slot : needSlots) {
            PartInfo want = target.get(slot);
            if (infiniteMaterials) {
                if (ContentItems.stack(want).isEmpty()) {
                    missing.add(want.id());
                }
                continue;
            }
            int best = bestSource(items, left, want);
            if (best < 0) {
                missing.add(want.id());
                continue;
            }
            left[best]--;
            source[slot] = best;
        }
        return new PartsPlan(List.copyOf(needSlots), source, left, List.copyOf(missing));
    }

    private static int bestSource(List<ItemStack> items, int[] left, PartInfo want) {
        int best = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (left[i] <= 0 || stack.isEmpty() || DroneAssembly.part(stack).orElse(null) != want) {
                continue;
            }
            double score = want instanceof BatteryType ? Batteries.charge(stack) * 2.0 + PartCondition.of(stack) : PartCondition.of(stack);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private static ImportResult heldDrone(ServerPlayer player, ImportRequest request) {
        ItemStack held = player.getMainHandItem();
        if (!DroneAssembly.isDrone(held)) {
            return ImportResult.of(ImportStatus.NO_TARGET);
        }
        DroneData data = request.data();
        Set<ImportLayer> applied = EnumSet.noneOf(ImportLayer.class);
        boolean changed = false;
        if (request.layers().contains(ImportLayer.PAINT)) {
            DronePaint target = Conversions.paint(data.paint());
            DronePaint current = PaintItems.paint(held);
            if (!current.equals(target)) {
                PaintCost cost = PaintCost.of(current, target);
                if (!PaintService.pay(player, cost)) {
                    return new ImportResult(ImportStatus.MISSING_DYES, applied, List.of(), missingDyes(player, cost));
                }
                PaintItems.store(held, target);
                changed = true;
            }
            applied.add(ImportLayer.PAINT);
        }
        if (request.layers().contains(ImportLayer.TUNE)) {
            FlightTune stored = held.getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY);
            TuneSpec combined = new TuneSpec(stored.values()).overlay(data.tune(), request.tuneGroups());
            FlightTune sanitized = DronePiloting.sanitize(stored, new FlightTune(combined.values()), DroneAssembly.buildOf(held));
            if (!sanitized.equals(stored)) {
                if (sanitized.equals(FlightTune.EMPTY)) {
                    held.remove(CoreComponents.FLIGHT_TUNE.get());
                } else {
                    held.set(CoreComponents.FLIGHT_TUNE.get(), sanitized);
                }
                changed = true;
            }
            applied.add(ImportLayer.TUNE);
        }
        if (request.layers().contains(ImportLayer.NAME)) {
            String name = importedName(data);
            if (!name.isEmpty() && !name.equals(CraftNames.of(held.get(DataComponents.CUSTOM_NAME)))) {
                CraftNames.renameStack(player, held, name);
                changed = true;
            }
            applied.add(ImportLayer.NAME);
        }
        if (changed) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
        return changed ? ImportResult.applied(applied) : new ImportResult(ImportStatus.UNCHANGED, applied, List.of(), Map.of());
    }

    private static ImportResult newDrone(ServerPlayer player, ImportRequest request) {
        if (!mayCreateDrones(player)) {
            return ImportResult.of(ImportStatus.NOT_ALLOWED);
        }
        DroneData data = request.data();
        List<Identifier> unknown = Conversions.unknownParts(data.build());
        if (!unknown.isEmpty()) {
            return new ImportResult(ImportStatus.UNKNOWN_PARTS, Set.of(), unknown, Map.of());
        }
        Optional<DroneBuild> build = Conversions.build(data.build());
        if (build.isEmpty() || !build.get().buildable(WorldRules.partCompatibility(player.level().getServer()))) {
            return ImportResult.of(ImportStatus.INVALID);
        }
        Set<ImportLayer> layers = request.layers();
        DroneData chosen = new DroneData(data.build(), layers.contains(ImportLayer.PAINT) ? data.paint() : PaintSpec.DEFAULT,
                layers.contains(ImportLayer.TUNE) ? data.tune().only(request.tuneGroups()) : TuneSpec.EMPTY,
                layers.contains(ImportLayer.NAME) ? data.name() : Optional.empty());
        ItemStack drone = BuildAccessImpl.INSTANCE.createDrone(chosen, WorldRules.partCompatibility(player.level().getServer()));
        player.getInventory().placeItemBackInInventory(drone, Prediction.SERVER_ONLY);
        player.containerMenu.broadcastChanges();
        return ImportResult.applied(layers);
    }

    public static boolean mayCreateDrones(ServerPlayer player) {
        return player.hasInfiniteMaterials() || ServerRules.hasPermissionLevel(player, CREATE_PERMISSION_LEVEL);
    }

    private static String importedName(DroneData data) {
        return data.name().map(CraftNames::clean).orElse("");
    }

    public static Map<String, Integer> missingDyes(ServerPlayer player, PaintCost cost) {
        Map<String, Integer> available = PaintService.availableDyes(player);
        Map<String, Integer> missing = new LinkedHashMap<>();
        int spare = 0;
        for (Map.Entry<String, Integer> entry : available.entrySet()) {
            int need = cost.dyes().getOrDefault(entry.getKey(), 0);
            int have = entry.getValue();
            if (need > have) {
                missing.put(entry.getKey(), need - have);
            } else {
                spare += have - need;
            }
        }
        if (cost.custom().size() > spare) {
            missing.put(CUSTOM_DYES, cost.custom().size() - spare);
        }
        return missing;
    }
}
