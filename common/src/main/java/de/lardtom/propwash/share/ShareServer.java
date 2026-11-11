package de.lardtom.propwash.share;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.build.ImportTarget;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.apiimpl.BuildAccessImpl;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.apiimpl.ImportNet;
import de.lardtom.propwash.apiimpl.ImportPayloads;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartAvailability;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.content.ImportService;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.creative.CreativeEdits;
import de.lardtom.propwash.creative.CreativeLimits;
import de.lardtom.propwash.creative.CreativeSpec;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.server.WorldRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ShareServer {
    private ShareServer() {
    }

    public record Slot(PartKind kind, Optional<AccessorySlot> accessory, Identifier id, int count) {
        public Slot {
            Objects.requireNonNull(kind, "kind");
            accessory = Objects.requireNonNullElse(accessory, Optional.empty());
            Objects.requireNonNull(id, "id");
        }
    }

    public static List<Slot> slots(BuildSpec build) {
        List<Slot> out = new ArrayList<>();
        out.add(new Slot(PartKind.FRAME, Optional.empty(), build.frame(), 1));
        out.add(new Slot(PartKind.STACK, Optional.empty(), build.stack(), 1));
        out.add(new Slot(PartKind.MOTOR, Optional.empty(), build.motor(), 4));
        out.add(new Slot(PartKind.PROP, Optional.empty(), build.prop(), 4));
        out.add(new Slot(PartKind.VIDEO, Optional.empty(), build.video(), 1));
        out.add(new Slot(PartKind.BATTERY, Optional.empty(), build.battery(), 1));
        for (AccessorySlot slot : Pw3Params.ACCESSORY_SLOTS) {
            build.accessory(slot).ifPresent(id -> out.add(new Slot(PartKind.ACCESSORY, Optional.of(slot), id, 1)));
        }
        return out;
    }

    public static Optional<DroneData> held(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        return PropwashApi.builds().isDrone(stack) ? PropwashApi.builds().read(stack) : Optional.empty();
    }

    public static ItemStack createDrone(ShareContent content) {
        ItemStack drone = PropwashApi.builds().createDrone(content.toDroneData());
        if (content.creative().isPresent() && creativeOf(drone).isPresent() && !applyCreative(drone, content.creative().get())) {
            throw new ShareException(ShareStatus.CORRUPT, CreativeEdits.REJECTED_KEY);
        }
        return drone;
    }

    public static Optional<CreativeSpec> heldCreative(ServerPlayer player) {
        return creativeOf(player.getMainHandItem());
    }

    public static Optional<CreativeSpec> creativeOf(ItemStack stack) {
        if (!ContentIds.is(stack, ContentIds.DRONE)) {
            return Optional.empty();
        }
        DroneBuild build = stack.get(CoreComponents.DRONE_BUILD.get());
        return build != null && build.isCreative() ? Optional.of(build.creative()) : Optional.empty();
    }

    public static boolean applyCreative(ItemStack drone, CreativeSpec spec) {
        DroneBuild build = drone.get(CoreComponents.DRONE_BUILD.get());
        if (build == null || !build.isCreative()) {
            return false;
        }
        DroneBuild next = build.withCreative(CreativeLimits.sanitize(spec).spec());
        if (!CreativeEdits.usable(next)) {
            return false;
        }
        drone.set(CoreComponents.DRONE_BUILD.get(), next);
        return true;
    }

    public static Optional<DroneBuild> creativeBuild(ShareContent content) {
        Optional<DroneBuild> build = Conversions.build(content.build());
        if (build.isEmpty() || content.creative().isEmpty() || !build.get().isCreative()) {
            return build;
        }
        return Optional.of(build.get().withCreative(CreativeLimits.sanitize(content.creative().get()).spec()));
    }

    public static Optional<BuildStats> stats(ShareContent content) {
        if (content.creative().isEmpty()) {
            return PropwashApi.builds().analyze(content.build());
        }
        try {
            return creativeBuild(content).map(BuildAccessImpl::stats);
        } catch (RuntimeException e) {
            return PropwashApi.builds().analyze(content.build());
        }
    }

    static void onCreative(ShareCreativeC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        ImportResult result = createCreative(player, payload, context.server().getTickCount());
        PropwashNet.sendToPlayer(player, ImportPayloads.ImportResultS2C.of(payload.requestId(), result));
    }

    public static ImportResult createCreative(ServerPlayer player, ShareCreativeC2S payload, long tick) {
        boolean allowed = ImportNet.RATE.tryAcquire(player.getUUID(), tick);
        ImportRequest request;
        try {
            request = payload.request().toRequest();
        } catch (IllegalArgumentException e) {
            return ImportResult.of(allowed ? ImportStatus.INVALID : ImportStatus.BUSY);
        }
        if (!allowed) {
            return BuildAccessImpl.imported(player, request, ImportResult.of(ImportStatus.BUSY));
        }
        return BuildAccessImpl.imported(player, request, createCreative(player, request, payload.spec()));
    }

    public static ImportResult createCreative(ServerPlayer player, ImportRequest request, CreativeSpec spec) {
        if (request.target() != ImportTarget.NEW_DRONE) {
            return ImportResult.of(ImportStatus.INVALID);
        }
        if (player.isSpectator() || !ImportService.mayCreateDrones(player)) {
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
        ItemStack drone;
        try {
            drone = BuildAccessImpl.INSTANCE.createDrone(chosen, WorldRules.partCompatibility(player.level().getServer()));
        } catch (RuntimeException e) {
            return ImportResult.of(ImportStatus.INVALID);
        }
        if (build.get().isCreative() && !applyCreative(drone, spec)) {
            player.sendOverlayMessage(Component.translatable(CreativeEdits.REJECTED_KEY).withStyle(ChatFormatting.RED));
            return ImportResult.of(ImportStatus.INVALID);
        }
        player.getInventory().placeItemBackInInventory(drone, Prediction.SERVER_ONLY);
        player.containerMenu.broadcastChanges();
        return ImportResult.applied(layers);
    }

    public static void give(ServerPlayer player, ItemStack stack) {
        ItemStack rest = stack.copy();
        boolean added = player.getInventory().add(rest);
        if (!added || !rest.isEmpty()) {
            ItemEntity drop = player.createItemStackToDrop(rest, false, false);
            if (drop != null) {
                drop.setNoPickUpDelay();
                drop.setTarget(player.getUUID());
                player.level().addFreshEntity(drop);
            }
        }
        player.containerMenu.broadcastChanges();
    }

    public static EnumSet<ImportLayer> heldLayers(ShareContent content) {
        EnumSet<ImportLayer> layers = EnumSet.noneOf(ImportLayer.class);
        if (content.paint().isPresent()) {
            layers.add(ImportLayer.PAINT);
        }
        if (content.tune().isPresent()) {
            layers.add(ImportLayer.TUNE);
        }
        return layers;
    }

    public static ImportResult applyToHeld(ServerPlayer player, ShareContent content) {
        EnumSet<ImportLayer> layers = heldLayers(content);
        if (layers.isEmpty()) {
            return ImportResult.of(ImportStatus.UNCHANGED);
        }
        BuildSpec target = held(player).map(DroneData::build).orElse(content.build());
        return PropwashApi.builds().applyImport(player, new ImportRequest(importData(content, layers, target), layers, Pw3Params.TUNE_GROUPS,
                ImportTarget.HELD_DRONE));
    }

    public static DroneData importData(ShareContent content, Set<ImportLayer> layers, BuildSpec target) {
        if (layers.contains(ImportLayer.PARTS) || usable(content.build())) {
            return content.toDroneData();
        }
        return content.withBuild(target).toDroneData();
    }

    public static boolean usable(BuildSpec build) {
        return PropwashApi.builds().check(build).known() && build.allParts().stream().allMatch(id -> PartAvailability.active(id));
    }

    public static boolean needsSubstitutes(ShareDecoded decoded) {
        return decoded.ok() && !decoded.partsKnown();
    }

    public static Substitutes.Result substitutes(ShareDecoded decoded, ShareTables tables) {
        if (!decoded.ok()) {
            return new Substitutes.Result(Substitutes.Method.NONE, Optional.empty(), List.of(), Optional.empty());
        }
        try {
            BuildSpec build = decoded.require().build();
            Substitutes.Result result = Substitutes.find(build, tables);
            if (result.found()) {
                return result;
            }
            List<List<AccessorySlot>> drops = new ArrayList<>();
            List<AccessorySlot> known = new ArrayList<>();
            for (AccessorySlot slot : Pw3Params.ACCESSORY_SLOTS) {
                Optional<Identifier> id = build.accessory(slot);
                if (id.isPresent() && !decoded.unknownParts().contains(id.get()) && !decoded.inactiveParts().contains(id.get())) {
                    known.add(slot);
                    drops.add(List.of(slot));
                }
            }
            if (known.size() > 1) {
                drops.add(List.copyOf(known));
            }
            for (List<AccessorySlot> drop : drops) {
                BuildSpec stripped = build;
                List<Substitutes.Swap> removed = new ArrayList<>();
                for (AccessorySlot slot : drop) {
                    removed.add(new Substitutes.Swap("accessory:" + slot.key(), build.accessory(slot).orElseThrow(), Optional.empty()));
                    stripped = stripped.withoutAccessory(slot);
                }
                Substitutes.Result retry = Substitutes.find(stripped, tables);
                if (retry.found()) {
                    List<Substitutes.Swap> swaps = new ArrayList<>(retry.swaps());
                    swaps.addAll(removed);
                    return new Substitutes.Result(retry.method(), retry.build(), swaps, retry.preset());
                }
            }
            return result;
        } catch (RuntimeException e) {
            return new Substitutes.Result(Substitutes.Method.NONE, Optional.empty(), List.of(), Optional.empty());
        }
    }

    public static Optional<ShareContent> substituted(ShareDecoded decoded, ShareTables tables) {
        Substitutes.Result result = substitutes(decoded, tables);
        return result.build().map(build -> decoded.require().withBuild(build));
    }
}
