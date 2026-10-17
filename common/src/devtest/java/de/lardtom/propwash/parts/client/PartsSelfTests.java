package de.lardtom.propwash.parts.client;

import com.mojang.serialization.DynamicOps;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.build.ImportTarget;
import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.registry.ExtensionTab;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.build.PaintSwatch;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.ContentBlocks;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.content.ImportService;
import de.lardtom.propwash.content.PartCondition;
import de.lardtom.propwash.content.Recycling;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.DroneBuildCodecs;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.core.PartCategory;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.network.PartsManifestPayload;
import de.lardtom.propwash.parts.AddonItems;
import de.lardtom.propwash.parts.PartsHandshake;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class PartsSelfTests {
    private static final String FUTURE = "future";
    private static final Identifier GHOST_MOTOR = Identifier.fromNamespaceAndPath("propwash_selftest", "ghost_motor");
    private static final Identifier GHOST_CAM = Identifier.fromNamespaceAndPath("propwash_selftest", "ghost_cam");

    private PartsSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("parts: Register eingefroren", 20, ctx -> frozenStep());
        SelfTest.clientStep("parts: Platzhalter-Rundreise", 100, server(PartsSelfTests::placeholderOnServer));
        SelfTest.clientStep("parts: Addon-Teile als Items", 100, server(PartsSelfTests::addonItemsOnServer));
        SelfTest.clientStep("parts: Kreativ-Tab und Tooltips", 20, ctx -> creativeAndTooltipStep());
        SelfTest.clientStep("parts: Import in die Werkbank", 200, server(PartsSelfTests::importOnServer));
        SelfTest.clientStep("parts: Tuning-Import auf die gehaltene Drohne", 100, server(PartsSelfTests::heldDroneOnServer));
        SelfTest.clientStep("parts: Handshake", 20, ctx -> handshakeStep());
    }

    private static SelfTest.ClientStep server(Consumer<MinecraftServer> body) {
        return ctx -> {
            if (ctx.tick() == 0) {
                ctx.state().put(FUTURE, ctx.onServer(body));
                return StepResult.pending();
            }
            CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
            if (!future.isDone()) {
                return StepResult.pending();
            }
            if (future.isCompletedExceptionally()) {
                Throwable error = future.exceptionNow();
                return StepResult.fail(error.getClass().getSimpleName() + ": " + error.getMessage());
            }
            return StepResult.pass();
        };
    }

    private static StepResult frozenStep() {
        if (!PartIndex.isFrozen()) {
            return StepResult.fail("Bauteil-Register ist nach dem Start nicht eingefroren");
        }
        int[] builtins = {5, 4, 6, 6, 4, 5, 5};
        for (PartKind kind : PartKind.values()) {
            long count = PartIndex.all(kind).stream().filter(PartInfo::builtin).count();
            if (count != builtins[kind.ordinal()]) {
                return StepResult.fail(kind.key() + ": " + count + " eingebaute Teile statt " + builtins[kind.ordinal()]);
            }
        }
        if (!AddonItems.INSTANCE.pending().isEmpty()) {
            return StepResult.fail("Teil-Items blieben in der Warteschlange: " + AddonItems.INSTANCE.pending());
        }
        for (Identifier id : AddonItems.INSTANCE.registeredItems().keySet()) {
            if (PartIndex.byId(id).isEmpty() || !BuiltInRegistries.ITEM.containsKey(id)) {
                return StepResult.fail("Teil-Item ohne Teil oder nicht im Register: " + id);
            }
        }
        return StepResult.check(PartsHandshake.localManifest().fingerprint().equals(PartIndex.fingerprint()), "Fingerabdruck der Handshake-Liste");
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static void placeholderOnServer(MinecraftServer server) {
        MotorType motor = (MotorType) PartIndex.missing(PartKind.MOTOR, GHOST_MOTOR);
        AccessoryType camera = (AccessoryType) PartIndex.missing(PartKind.ACCESSORY, GHOST_CAM, AccessorySlot.TOP);
        DroneBuild build = new DroneBuild(DroneBuild.FREESTYLE.frame(), DroneBuild.FREESTYLE.stack(), motor, DroneBuild.FREESTYLE.prop(),
                DroneBuild.FREESTYLE.video(), DroneBuild.FREESTYLE.battery()).withAccessory(camera);
        ItemStack drone = DroneAssembly.preset(build);
        DynamicOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag saved = ItemStack.CODEC.encodeStart(ops, drone).getOrThrow();
        ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
        DroneBuild back = DroneAssembly.buildOf(loaded);
        if (!back.motor().missing() || !back.motor().key().equals(GHOST_MOTOR.toString())) {
            throw new IllegalStateException("Motor-Platzhalter ging verloren: " + back.motor().key());
        }
        AccessoryType top = back.accessories().at(AccessoryMount.TOP).orElseThrow(() -> new IllegalStateException("Zubehör-Platzhalter fehlt"));
        if (!top.missing() || !top.key().equals(GHOST_CAM.toString())) {
            throw new IllegalStateException("Zubehör-Platzhalter falsch: " + top.key());
        }
        if (back.problems().isEmpty() || back.problems().getFirst() != DroneBuild.Issue.MISSING_PART || back.buildable()) {
            throw new IllegalStateException("Drohne mit fehlendem Teil gilt als baubar: " + back.problems());
        }
        ByteBuf buffer = Unpooled.buffer();
        DroneBuildCodecs.STREAM_CODEC.encode(buffer, back);
        if (!back.equals(DroneBuildCodecs.STREAM_CODEC.decode(buffer))) {
            throw new IllegalStateException("Netz-Rundreise mit Platzhaltern");
        }
        List<ItemStack> parts = DroneAssembly.disassemble(loaded);
        for (int i = 0; i < DroneBuild.MOTOR_COUNT; i++) {
            if (!parts.get(BuildSlots.MOTOR + i).isEmpty()) {
                throw new IllegalStateException("Zerlegen gab ein Item für den fehlenden Motor");
            }
        }
        if (!parts.get(BuildSlots.accessorySlot(AccessoryMount.TOP)).isEmpty()) {
            throw new IllegalStateException("Zerlegen gab ein Item für das fehlende Zubehör");
        }
        if (DroneAssembly.part(parts.get(BuildSlots.FRAME)).orElse(null) != DroneBuild.FREESTYLE.frame()
                || DroneAssembly.part(parts.get(BuildSlots.BATTERY)).orElse(null) != DroneBuild.FREESTYLE.battery()) {
            throw new IllegalStateException("vorhandene Teile kamen beim Zerlegen nicht zurück");
        }
        if (!ItemStack.EMPTY.isEmpty() || !ItemStack.EMPTY.getComponentsPatch().isEmpty()) {
            throw new IllegalStateException("ItemStack.EMPTY wurde verändert");
        }
    }

    private static void addonItemsOnServer(MinecraftServer server) {
        int checked = 0;
        for (PartInfo part : PartIndex.all()) {
            if (part.builtin()) {
                continue;
            }
            ItemStack stack = ContentItems.stack(part);
            if (stack.isEmpty()) {
                continue;
            }
            if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(part.id())) {
                throw new IllegalStateException("Item-Id " + BuiltInRegistries.ITEM.getKey(stack.getItem()) + " statt " + part.id());
            }
            if (DroneAssembly.part(stack).orElse(null) != part || AddonItems.INSTANCE.partOf(stack).orElse(null) == null) {
                throw new IllegalStateException("PartRef von " + part.id() + " löst nicht auf");
            }
            if (stack.getMaxStackSize() != AddonItems.stackSize(part)) {
                throw new IllegalStateException("Stapelgröße von " + part.id() + ": " + stack.getMaxStackSize());
            }
            int slot = BuildSlots.slotsFor(part)[0];
            if (!BuildSlots.accepts(slot, part) || BuildSlots.categoryOf(slot) != PartCategory.of(part)) {
                throw new IllegalStateException(part.id() + " passt nicht in seinen Werkbank-Slot");
            }
            if (part instanceof BatteryType battery && Batteries.charge(stack) != Batteries.FULL) {
                throw new IllegalStateException("Addon-Akku " + battery.id() + " ist nicht voll");
            }
            if (Recycling.recyclable(part)) {
                List<ItemStack> yield = Recycling.recycle(PartCondition.with(stack.copy(), 0.0F));
                Identifier expected = Recycling.yieldOf(part).orElseThrow().itemId();
                if (yield.isEmpty() || !BuiltInRegistries.ITEM.getKey(yield.getFirst().getItem()).equals(expected)) {
                    throw new IllegalStateException("Recycling von " + part.id() + " liefert " + yield + " statt " + expected);
                }
            }
            checked++;
        }
        Constants.LOG.info("parts-Selbsttest: {} Addon-Teile als Items geprüft", checked);
    }

    private static int indexOf(List<ItemStack> tab, PartInfo part) {
        int index = -1;
        for (int i = 0; i < tab.size(); i++) {
            if (DroneAssembly.part(tab.get(i)).orElse(null) == part) {
                index = i;
            }
        }
        return index;
    }

    private static StepResult creativeAndTooltipStep() {
        Minecraft mc = Minecraft.getInstance();
        List<ItemStack> tab = ContentTab.contents();
        int lastBuiltinPart = -1;
        for (int i = 0; i < tab.size(); i++) {
            Optional<PartInfo> part = DroneAssembly.part(tab.get(i));
            if (part.isPresent() && part.get().builtin() && !DroneAssembly.isDrone(tab.get(i))) {
                lastBuiltinPart = i;
            }
        }
        for (PartInfo part : ContentTab.addonParts()) {
            if (ContentItems.stack(part).isEmpty()) {
                continue;
            }
            int index = indexOf(tab, part);
            Optional<ExtensionTab> own = PropwashApi.extensionTabs().stream()
                    .filter(extension -> extension.registered() && extension.namespace().equals(part.id().getNamespace())).findFirst();
            if (own.isPresent()) {
                if (index >= 0) {
                    return StepResult.fail("Addon-Teil steht im Propwash-Tab statt in " + own.get().tabId() + ": " + part.id());
                }
                if (indexOf(own.get().contents(), part) < 0) {
                    return StepResult.fail("Addon-Teil fehlt im eigenen Kreativ-Tab " + own.get().tabId() + ": " + part.id());
                }
                continue;
            }
            if (index < 0) {
                return StepResult.fail("Addon-Teil fehlt im Kreativ-Tab: " + part.id());
            }
            if (index < lastBuiltinPart) {
                return StepResult.fail("Addon-Teil steht vor eingebauten Teilen: " + part.id());
            }
        }
        for (PartInfo part : PartIndex.all()) {
            if (part.builtin()) {
                continue;
            }
            ItemStack stack = ContentItems.stack(part);
            if (stack.isEmpty()) {
                continue;
            }
            List<Component> lines = stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
            if (lines.size() < 2) {
                return StepResult.fail("Tooltip von " + part.id() + " zu kurz: " + lines);
            }
        }
        return StepResult.pass();
    }

    private static DroneData data(DroneBuild build, DronePaint paint, FlightTune tune) {
        return new DroneData(Conversions.spec(build), Conversions.paint(paint), Conversions.tune(tune), Optional.empty());
    }

    private static void importOnServer(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Inventory inventory = player.getInventory();
        List<ItemStack> backup = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            backup.add(inventory.getItem(i).copy());
        }
        GameType mode = player.gameMode();
        BlockPos pos = player.blockPosition().offset(-2, 0, 2);
        BlockState state = ContentBlocks.DRONE_WORKBENCH.block().get().defaultBlockState();
        try {
            level.setBlockAndUpdate(pos, state);
            player.setGameMode(GameType.SURVIVAL);
            inventory.clearContent();
            if (player.openMenu(state.getMenuProvider(level, pos)).isEmpty() || !(player.containerMenu instanceof DroneWorkbenchMenu menu)) {
                throw new IllegalStateException("Werkbank ließ sich nicht öffnen");
            }
            DroneBuild build = DroneBuild.FREESTYLE;
            PaintSwatch swatch = PaintSwatch.dyes().getFirst();
            DronePaint paint = DronePaint.DEFAULT.with(PaintSlot.values()[0], swatch.rgb());
            DroneData data = data(build, paint, FlightTune.EMPTY.with("pid.roll.p", 47.0F));
            inventory.add(ContentItems.stack(build.frame()));
            inventory.add(ContentItems.stack(build.stack()));
            inventory.add(ContentItems.stack(build.video()));
            inventory.add(ContentItems.stack(build.battery()));
            inventory.add(ContentItems.stack(build.motor()).copyWithCount(3));
            inventory.add(ContentItems.stack(build.prop()).copyWithCount(4));
            ImportResult missing = ImportService.apply(player, menu.containerId, ImportRequest.wholeBuild(data));
            if (missing.status() != ImportStatus.MISSING_PARTS || !missing.missingParts().equals(List.of(build.motor().id()))) {
                throw new IllegalStateException("fehlender Motor nicht gemeldet: " + missing);
            }
            if (!menu.partsEmpty()) {
                throw new IllegalStateException("gescheiterter Import hat Teile verschoben");
            }
            inventory.add(ContentItems.stack(build.motor()));
            ImportRequest parts = new ImportRequest(data, EnumSet.of(ImportLayer.PARTS), TuneGroup.FLIGHT, ImportTarget.WORKBENCH);
            ImportResult applied = ImportService.apply(player, menu.containerId, parts);
            if (applied.status() != ImportStatus.APPLIED || !build.equals(menu.check().build().orElse(null))) {
                throw new IllegalStateException("Teile-Import: " + applied + " / " + menu.check());
            }
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                if (DroneAssembly.part(inventory.getItem(i)).isPresent()) {
                    throw new IllegalStateException("Teil blieb im Inventar: " + inventory.getItem(i));
                }
            }
            if (ImportService.apply(player, menu.containerId, parts).status() != ImportStatus.UNCHANGED) {
                throw new IllegalStateException("zweiter gleicher Import ist nicht UNCHANGED");
            }
            ImportRequest paintOnly = new ImportRequest(data, EnumSet.of(ImportLayer.PAINT), TuneGroup.FLIGHT, ImportTarget.WORKBENCH);
            ImportResult noDyes = ImportService.apply(player, menu.containerId, paintOnly);
            if (noDyes.status() != ImportStatus.MISSING_DYES || noDyes.missingDyes().getOrDefault(swatch.dye(), 0) != 1) {
                throw new IllegalStateException("fehlender Farbstoff nicht gemeldet: " + noDyes);
            }
            Item dye = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(swatch.dye() + "_dye"));
            inventory.add(new ItemStack(dye, 2));
            ImportResult painted = ImportService.apply(player, menu.containerId, paintOnly);
            if (painted.status() != ImportStatus.APPLIED || !paint.equals(menu.paint())) {
                throw new IllegalStateException("Lack-Import: " + painted + " / " + menu.paint());
            }
            ImportRequest tune = new ImportRequest(data, EnumSet.of(ImportLayer.TUNE), TuneGroup.FLIGHT, ImportTarget.WORKBENCH);
            if (!ImportService.apply(player, menu.containerId, tune).status().success() || menu.pendingTune().isEmpty()) {
                throw new IllegalStateException("Tuning-Import in die Werkbank");
            }
            FlightTune resultTune = menu.resultPreview().getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY);
            if (!resultTune.values().containsKey("pid.roll.p")) {
                throw new IllegalStateException("Ergebnis-Drohne trägt das Tuning nicht: " + resultTune);
            }
            player.setGameMode(GameType.CREATIVE);
            List<ItemStack> empty = new ArrayList<>();
            for (int i = 0; i < BuildSlots.COUNT; i++) {
                empty.add(ItemStack.EMPTY);
            }
            menu.replaceParts(empty);
            inventory.clearContent();
            ImportResult creative = ImportService.apply(player, menu.containerId, ImportRequest.wholeBuild(data(DroneBuild.RACER, DronePaint.DEFAULT,
                    FlightTune.EMPTY)));
            if (!creative.status().success() || !DroneBuild.RACER.equals(menu.check().build().orElse(null))) {
                throw new IllegalStateException("Kreativ-Import: " + creative + " / " + menu.check());
            }
            if (!ImportService.apply(player, 9999, ImportRequest.wholeBuild(data)).status().equals(ImportStatus.NO_TARGET)) {
                throw new IllegalStateException("falsche Container-Id wurde angenommen");
            }
        } finally {
            if (player.containerMenu instanceof DroneWorkbenchMenu menu) {
                List<ItemStack> empty = new ArrayList<>();
                for (int i = 0; i < BuildSlots.COUNT; i++) {
                    empty.add(ItemStack.EMPTY);
                }
                menu.replaceParts(empty);
            }
            player.closeContainer();
            level.removeBlock(pos, false);
            inventory.clearContent();
            for (int i = 0; i < backup.size(); i++) {
                inventory.setItem(i, backup.get(i));
            }
            player.setGameMode(mode);
        }
    }

    private static void heldDroneOnServer(MinecraftServer server) {
        ServerPlayer player = player(server);
        ItemStack before = player.getItemInHand(InteractionHand.MAIN_HAND).copy();
        try {
            ItemStack drone = DroneAssembly.preset(DroneBuild.RACER);
            player.setItemInHand(InteractionHand.MAIN_HAND, drone);
            TuneSpec tune = new TuneSpec(Map.of("pid.roll.p", 47.0F, "vtx.channel", 3.0F));
            DroneData data = new DroneData(Conversions.spec(DroneBuild.RACER), null, tune, Optional.empty());
            ImportResult result = ImportService.apply(player, ImportRequest.tuneOnHeldDrone(data));
            if (result.status() != ImportStatus.APPLIED) {
                throw new IllegalStateException("Tuning-Import auf die Drohne: " + result);
            }
            FlightTune stored = player.getItemInHand(InteractionHand.MAIN_HAND).getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY);
            if (!stored.values().containsKey("pid.roll.p") || stored.values().containsKey("vtx.channel")) {
                throw new IllegalStateException("Tuning-Gruppen falsch übernommen: " + stored);
            }
            if (ImportService.apply(player, ImportRequest.tuneOnHeldDrone(data)).status() != ImportStatus.UNCHANGED) {
                throw new IllegalStateException("zweiter gleicher Tuning-Import ist nicht UNCHANGED");
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (ImportService.apply(player, ImportRequest.tuneOnHeldDrone(data)).status() != ImportStatus.NO_TARGET) {
                throw new IllegalStateException("Import ohne Drohne in der Hand");
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, before);
        }
    }

    private static StepResult handshakeStep() {
        PartsManifestPayload local = PartsHandshake.localManifest();
        if (!PartsHandshake.compare(local, local).matches()) {
            return StepResult.fail("eigene Bauteil-Liste passt nicht zu sich selbst");
        }
        List<PartsManifestPayload.Entry> extra = new ArrayList<>(local.entries());
        extra.add(new PartsManifestPayload.Entry("motor", GHOST_MOTOR, "0"));
        PartsManifestPayload other = new PartsManifestPayload(local.fingerprint() + "x", extra);
        PartsHandshake.Diff diff = PartsHandshake.compare(local, other);
        if (diff.matches() || !diff.missingOnServer().equals(List.of(GHOST_MOTOR))) {
            return StepResult.fail("Abweichung nicht erkannt: " + diff);
        }
        String text = PartsHandshake.message(diff).getString();
        return StepResult.check(text.contains(GHOST_MOTOR.toString()), "Trennmeldung nennt das Teil nicht: " + text);
    }
}
