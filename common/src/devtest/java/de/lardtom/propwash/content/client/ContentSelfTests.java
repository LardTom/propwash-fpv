package de.lardtom.propwash.content.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.BatteryChargerBlock;
import de.lardtom.propwash.content.BatteryChargerBlockEntity;
import de.lardtom.propwash.content.BatteryItem;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.ChargerState;
import de.lardtom.propwash.content.ContentBlocks;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.content.ItemHelp;
import de.lardtom.propwash.content.PartItem;
import de.lardtom.propwash.content.TooltipShift;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.core.PartCategory;
import de.lardtom.propwash.core.PartRef;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.registry.RegHolder;
import de.lardtom.propwash.server.ServerRulesConfig;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.AtlasIds;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

public final class ContentSelfTests {
    private static final String OPEN = "open";
    private static final String ASSEMBLE = "assemble";
    private static final String FINISH = "finish";
    private static final String ASSEMBLED_AT = "assembled_at";
    private static final FlightTune TEST_TUNE = FlightTune.EMPTY.with("rate.roll.rc", 1.2F);
    private static final Component TEST_NAME = Component.literal("Selbsttest-Quad");
    private static final float TEST_CHARGE = 0.35F;
    private static final String RESULT = "result";
    private static final String CHECK = "check";
    private static final String CHECK_RESULT = "check_result";
    private static final String BREAK = "break";
    private static final String SAVED_RULES = "saved_rules";
    private static final String SAW_CHARGING = "saw_charging";
    private static final String SAW_REMAINING = "saw_remaining";
    private static final AtomicReference<BlockPos> CHARGER_AT = new AtomicReference<>();

    private ContentSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("content: Bauteil-Items und Brillen", 20, ctx -> itemsStep());
        SelfTest.clientStep("content: Rezepte geladen", 100, ContentSelfTests::recipeStep);
        SelfTest.clientStep("content: Werkbank bauen und zerlegen", 400, ContentSelfTests::workbenchStep);
        SelfTest.clientStep("content: Ladegerät", 200, ContentSelfTests::chargerStep);
        SelfTest.clientStep("content: Akkufarben im Ladegerät", 200, ContentSelfTests::chargerColorsStep);
        SelfTest.clientStep("content: Werkbank übernimmt Ladestand", 20, ContentSelfTests::workbenchChargeStep);
        SelfTest.clientStep("content: Werkbank-Kennzahlen passen neben ihre Werte (Spielschrift)", 20, WorkbenchFitSelfTest::check);
        SelfTest.clientStep("content: Shift-Tooltips", 20, ContentSelfTests::tooltipStep);
        SelfTest.clientStep("content: Zubehör einbauen und zerlegen", 100, ContentSelfTests::accessoryStep);
        PaintSelfTests.register();
        ItemSizeSelfTests.register();
    }

    private static StepResult chargerStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            CHARGER_AT.set(null);
            AtomicReference<String> seen = new AtomicReference<>("");
            AtomicReference<ServerRulesConfig> savedRules = new AtomicReference<>();
            AtomicBoolean sawCharging = new AtomicBoolean();
            AtomicBoolean sawRemaining = new AtomicBoolean();
            ctx.state().put(RESULT, seen);
            ctx.state().put(SAVED_RULES, savedRules);
            ctx.state().put(SAW_CHARGING, sawCharging);
            ctx.state().put(SAW_REMAINING, sawRemaining);
            ctx.state().put(OPEN, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                BlockPos pos = chargerPos(player);
                ServerRulesConfig config = ServerRulesConfig.get();
                savedRules.set(config.copy());
                config.batteryChargeSeconds = 10.0;
                config.batteryChargeMinSeconds = 1.0;
                config.redstoneChargeMultiplier = 2.0;
                level.setBlockAndUpdate(pos, ContentBlocks.BATTERY_CHARGER.block().get().defaultBlockState());
                level.setBlockAndUpdate(pos.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                if (!(level.getBlockEntity(pos) instanceof BatteryChargerBlockEntity charger)) {
                    throw new IllegalStateException("Ladegerät ohne Block-Entity");
                }
                charger.setItem(0, Batteries.stackOf(BatteryType.B6S_1300, 0.5F));
                seen.set("placed");
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(OPEN);
        if (future.isCompletedExceptionally()) {
            restoreRules(ctx);
            return StepResult.fail("Ladegerät setzen fehlgeschlagen: " + future.exceptionNow().getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (ctx.state().containsKey(BREAK)) {
            return brokenResult(ctx);
        }
        if (!ctx.state().containsKey(CHECK)) {
            AtomicReference<String> state = new AtomicReference<>("");
            AtomicBoolean sawCharging = (AtomicBoolean) ctx.state().get(SAW_CHARGING);
            AtomicBoolean sawRemaining = (AtomicBoolean) ctx.state().get(SAW_REMAINING);
            ctx.state().put(CHECK_RESULT, state);
            ctx.state().put(CHECK, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                BlockPos pos = chargerPos(player);
                BlockState block = player.level().getBlockState(pos);
                if (!(player.level().getBlockEntity(pos) instanceof BatteryChargerBlockEntity charger) || !block.hasProperty(BatteryChargerBlock.STATE)) {
                    state.set("fehlt bei " + pos.toShortString() + ": " + block + ", Spieler bei " + player.blockPosition().toShortString());
                    return;
                }
                ChargerState chargerState = block.getValue(BatteryChargerBlock.STATE);
                float charge = Batteries.charge(charger.getItem(0));
                if (chargerState == ChargerState.CHARGING) {
                    sawCharging.set(true);
                    if (charger.data().get(0) >= 0) {
                        sawRemaining.set(true);
                    }
                }
                state.set(chargerState.getSerializedName() + ":" + charge);
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> check = (CompletableFuture<?>) ctx.state().get(CHECK);
        if (!check.isDone()) {
            return StepResult.pending();
        }
        ctx.state().remove(CHECK);
        @SuppressWarnings("unchecked")
        String state = ((AtomicReference<String>) ctx.state().get(CHECK_RESULT)).get();
        if (!state.startsWith("done:1.0")) {
            if (ctx.tick() > 180) {
                restoreRules(ctx);
                return StepResult.fail("Ladegerät lädt nicht voll: " + state);
            }
            return StepResult.pending();
        }
        if (!((AtomicBoolean) ctx.state().get(SAW_CHARGING)).get()) {
            restoreRules(ctx);
            return StepResult.fail("Blockzustand CHARGING nie gesehen");
        }
        if (!((AtomicBoolean) ctx.state().get(SAW_REMAINING)).get()) {
            restoreRules(ctx);
            return StepResult.fail("Menü-Daten ohne Restzeit");
        }
        if (!ctx.state().containsKey(BREAK)) {
            AtomicReference<String> drop = new AtomicReference<>("");
            ctx.state().put(RESULT, drop);
            ctx.state().put(BREAK, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                BlockPos pos = chargerPos(player);
                level.destroyBlock(pos, true);
                level.setBlockAndUpdate(pos.east(), Blocks.AIR.defaultBlockState());
                List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0));
                boolean battery = false;
                for (ItemEntity item : items) {
                    if (Batteries.typeOf(item.getItem()).isPresent()) {
                        battery = true;
                    }
                    item.discard();
                }
                drop.set(battery ? "ok" : "kein Akku");
            }));
            return StepResult.pending();
        }
        return brokenResult(ctx);
    }

    private static final BatteryType[] COLOR_PACKS = {BatteryType.B6S_1100, BatteryType.B4S_650, null, BatteryType.B1S_300_HV};

    private static StepResult chargerColorsStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            AtomicReference<BlockPos> at = new AtomicReference<>();
            ctx.state().put(RESULT, at);
            ctx.state().put(OPEN, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                BlockPos pos = player.blockPosition().offset(-3, 0, 2);
                at.set(pos);
                level.setBlockAndUpdate(pos, ContentBlocks.BATTERY_CHARGER.block().get().defaultBlockState());
                if (!(level.getBlockEntity(pos) instanceof BatteryChargerBlockEntity charger)) {
                    throw new IllegalStateException("Ladegerät ohne Block-Entity");
                }
                for (int slot = 0; slot < COLOR_PACKS.length; slot++) {
                    if (COLOR_PACKS[slot] != null) {
                        charger.setItem(slot, Batteries.stackOf(COLOR_PACKS[slot], 1.0F));
                    }
                }
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> placed = (CompletableFuture<?>) ctx.state().get(OPEN);
        if (placed.isCompletedExceptionally()) {
            return StepResult.fail("Ladegerät setzen fehlgeschlagen: " + placed.exceptionNow().getMessage());
        }
        if (!placed.isDone() || !(ctx.state().get(RESULT) instanceof AtomicReference<?> at) || !(at.get() instanceof BlockPos pos)) {
            return StepResult.pending();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(pos) instanceof BatteryChargerBlockEntity charger)) {
            return ctx.tick() > 150 ? StepResult.fail("Ladegerät nicht beim Client") : StepResult.pending();
        }
        for (int slot = 0; slot < COLOR_PACKS.length; slot++) {
            BatteryType expected = COLOR_PACKS[slot];
            if (!Batteries.typeOf(charger.getItem(slot)).equals(Optional.ofNullable(expected))) {
                return ctx.tick() > 150 ? StepResult.fail("Client sieht Schacht " + slot + " nicht: " + charger.getItem(slot)) : StepResult.pending();
            }
        }
        BlockState state = mc.level.getBlockState(pos);
        List<BlockTintSource> layers = mc.getBlockColors().getTintSources(state);
        List<String> errors = new ArrayList<>();
        if (layers.size() != BatteryChargerBlockEntity.SLOTS) {
            errors.add("Farbebenen: " + layers.size());
        } else {
            for (int slot = 0; slot < COLOR_PACKS.length; slot++) {
                BatteryType type = COLOR_PACKS[slot] != null ? COLOR_PACKS[slot] : ChargerPackTint.FALLBACK;
                int color = layers.get(slot).colorInWorld(state, mc.level, pos);
                if (color != ChargerPackTint.colorOf(type)) {
                    errors.add("Schacht " + slot + ": " + Integer.toHexString(color) + " statt " + type);
                }
            }
            if (layers.getFirst().color(state) != ChargerPackTint.colorOf(ChargerPackTint.FALLBACK)) {
                errors.add("Farbe ohne Welt ist nicht der 6S-1300-Akku");
            }
            if (layers.getFirst().colorAsTerrainParticle(state, mc.level, pos) != -1) {
                errors.add("Abbau-Partikel werden eingefärbt");
            }
        }
        ctx.onServer(server -> player(server).level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
        return errors.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", errors));
    }

    private static StepResult brokenResult(SelfTest.StepContext ctx) {
        CompletableFuture<?> broken = (CompletableFuture<?>) ctx.state().get(BREAK);
        if (!broken.isDone()) {
            return StepResult.pending();
        }
        restoreRules(ctx);
        if (broken.isCompletedExceptionally()) {
            return StepResult.fail("Abbauen fehlgeschlagen: " + broken.exceptionNow().getMessage());
        }
        @SuppressWarnings("unchecked")
        String drop = ((AtomicReference<String>) ctx.state().get(RESULT)).get();
        return "ok".equals(drop) ? StepResult.pass() : StepResult.fail("Abbauen: " + drop);
    }

    private static void restoreRules(SelfTest.StepContext ctx) {
        Object saved = ctx.state().remove(SAVED_RULES);
        if (saved instanceof AtomicReference<?> reference) {
            ctx.onServer(server -> {
                if (reference.get() instanceof ServerRulesConfig rules) {
                    ServerRulesConfig.get().assign(rules);
                }
            });
        }
    }

    private static BlockPos chargerPos(ServerPlayer player) {
        BlockPos pos = CHARGER_AT.get();
        if (pos == null) {
            pos = player.blockPosition().offset(-3, 0, -3);
            CHARGER_AT.set(pos);
        }
        return pos;
    }

    private static StepResult workbenchChargeStep(SelfTest.StepContext ctx) {
        if (!DroneAssembly.droneItemAvailable()) {
            return StepResult.fail("propwash:drone ist nicht registriert");
        }
        List<ItemStack> parts = DroneAssembly.disassemble(ContentTab.presetDrone(DroneBuild.FREESTYLE));
        ItemStack battery = parts.get(BuildSlots.BATTERY);
        Batteries.storeCharge(battery, 0.3F);
        ItemStack drone = DroneAssembly.createDrone(DroneBuild.FREESTYLE, parts.get(BuildSlots.STACK), parts.get(BuildSlots.FRAME), battery);
        if (Math.abs(DroneAssembly.charge(drone) - 0.3F) > 1.0E-4F) {
            return StepResult.fail("Drohne übernimmt den Ladestand nicht: " + DroneAssembly.charge(drone));
        }
        List<PartInfo> whoop = new ArrayList<>(BuildSlots.layout(DroneBuild.WHOOP));
        whoop.set(BuildSlots.BATTERY, BatteryType.B6S_1300);
        BuildSlots.Check check = BuildSlots.check(whoop);
        if (check.buildable() || !check.build().orElseThrow().problems().contains(DroneBuild.Issue.BATTERY_FRAME)) {
            return StepResult.fail("Whoop mit 6S ist baubar");
        }
        List<ItemStack> back = DroneAssembly.disassemble(drone);
        if (Math.abs(Batteries.charge(back.get(BuildSlots.BATTERY)) - 0.3F) > 1.0E-4F) {
            return StepResult.fail("Zerlegen gibt den Ladestand nicht zurück");
        }
        UUID id = UUID.fromString("7d3c1a52-9f0e-4b1a-8c55-1f2e3d4c5b6a");
        ItemStack bound = drone.copy();
        bound.set(CoreComponents.DRONE_ID.get(), id);
        List<ItemStack> boundParts = DroneAssembly.disassemble(bound);
        ItemStack rebuiltBound = DroneAssembly.createDrone(DroneBuild.FREESTYLE, boundParts.get(BuildSlots.STACK), boundParts.get(BuildSlots.FRAME),
                boundParts.get(BuildSlots.BATTERY));
        if (!id.equals(rebuiltBound.get(CoreComponents.DRONE_ID.get()))) {
            return StepResult.fail("Zerlegen und neu Bauen verliert die Drohnen-Id (Funke und Brille wären getrennt)");
        }
        ItemStack empty = drone.copy();
        empty.set(CoreComponents.BATTERY_INSTALLED.get(), false);
        empty.remove(CoreComponents.BATTERY_CHARGE.get());
        if (!DroneAssembly.disassemble(empty).get(BuildSlots.BATTERY).isEmpty()) {
            return StepResult.fail("Zerlegen einer Drohne ohne Akku liefert einen Akku");
        }
        return StepResult.pass();
    }

    private static StepResult tooltipStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Item.TooltipContext context = Item.TooltipContext.of(mc.level);
        List<String> errors = new ArrayList<>();
        Language language = Language.getInstance();
        try {
            for (Item item : BuiltInRegistries.ITEM) {
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                if (!Constants.MOD_ID.equals(id.getNamespace())) {
                    continue;
                }
                ItemStack stack = ContentIds.DRONE.equals(id) ? ContentTab.presetDrone(DroneBuild.FREESTYLE) : new ItemStack(item);
                TooltipShift.override(false);
                List<Component> brief = new ArrayList<>();
                item.appendHoverText(stack, context, TooltipDisplay.DEFAULT, brief::add, TooltipFlag.NORMAL);
                TooltipShift.override(true);
                List<Component> detail = new ArrayList<>();
                item.appendHoverText(stack, context, TooltipDisplay.DEFAULT, detail::add, TooltipFlag.NORMAL);
                if (brief.isEmpty() || brief.size() > 3) {
                    errors.add(id.getPath() + ": " + brief.size() + " Zeilen ohne Shift");
                } else if (!(brief.getLast().getContents() instanceof TranslatableContents last) || !ItemHelp.SHIFT_KEY.equals(last.getKey())) {
                    errors.add(id.getPath() + ": letzte Zeile ist nicht der Shift-Hinweis");
                }
                if (detail.size() <= brief.size()) {
                    errors.add(id.getPath() + ": mit Shift nicht mehr Zeilen");
                }
                for (Component line : detail) {
                    if (line.getContents() instanceof TranslatableContents translatable && !language.has(translatable.getKey())) {
                        errors.add(id.getPath() + ": fehlende Übersetzung " + translatable.getKey());
                    }
                }
            }
        } finally {
            TooltipShift.override(null);
        }
        return errors.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", errors));
    }

    private static StepResult itemsStep() {
        List<String> errors = new ArrayList<>();
        for (PartCategory category : PartCategory.values()) {
            for (PartInfo part : category.parts()) {
                Identifier id = part.id();
                Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
                if (item.isEmpty()) {
                    errors.add(id + " fehlt");
                    continue;
                }
                if (!(item.get() instanceof PartItem)) {
                    errors.add(id + " ist kein PartItem");
                }
                ItemStack stack = new ItemStack(item.get());
                PartRef ref = stack.get(CoreComponents.PART.get());
                if (!PartRef.of(part).equals(ref)) {
                    errors.add(id + " hat PartRef " + ref);
                }
                if (DroneAssembly.part(stack).orElse(null) != part) {
                    errors.add(id + " löst nicht zu " + part.key() + " auf");
                }
                if (stack.getMaxStackSize() != ContentItems.stackSize(category)) {
                    errors.add(id + " Stapelgröße " + stack.getMaxStackSize());
                }
                if (!stack.getHoverName().getString().equals(Component.translatable(part.translationKey()).getString())) {
                    errors.add(id + " Name " + stack.getHoverName().getString());
                }
            }
        }
        checkGoggles(errors, ContentIds.GOGGLES_ANALOG, VideoSystem.ANALOG);
        checkGoggles(errors, ContentIds.GOGGLES_DIGITAL, VideoSystem.DIGITAL);
        checkGoggles(errors, ContentIds.GOGGLES_CREATIVE, VideoSystem.CREATIVE);
        if (ContentIds.item(ContentIds.DRONE_WORKBENCH).isEmpty()) {
            errors.add("drone_workbench-Item fehlt");
        }
        if (ContentIds.item(ContentIds.BATTERY_CHARGER).isEmpty() || !ContentBlocks.BATTERY_CHARGER_MENU.isBound()) {
            errors.add("battery_charger fehlt");
        }
        if (ContentIds.item(ContentIds.REMOTE).isEmpty()) {
            errors.add("remote fehlt");
        }
        for (RegHolder<PartItem> part : ContentItems.parts().values()) {
            ItemStack stack = new ItemStack(part.get());
            if (DroneAssembly.category(stack).orElse(null) == PartCategory.BATTERY
                    && (!(part.get() instanceof BatteryItem) || Batteries.charge(stack) != Batteries.FULL || !part.get().isBarVisible(stack))) {
                errors.add(part.id() + " ist kein Akku-Item mit vollem Ladestand");
            }
        }
        if (!BuiltInRegistries.BLOCK.getKey(ContentBlocks.DRONE_WORKBENCH.block().get()).equals(ContentIds.DRONE_WORKBENCH)) {
            errors.add("drone_workbench-Block falsch registriert");
        }
        if (!ContentBlocks.DRONE_WORKBENCH_MENU.isBound()) {
            errors.add("Werkbank-Menü nicht registriert");
        }
        List<ItemStack> tab = ContentTab.contents();
        for (RegHolder<PartItem> part : ContentItems.parts().values()) {
            if (tab.stream().noneMatch(stack -> stack.is(part.get()))) {
                errors.add("Creative-Tab ohne " + part.id());
            }
        }
        if (DroneAssembly.droneItemAvailable()) {
            long presets = tab.stream().filter(DroneAssembly::isDrone).count();
            if (presets != DroneBuild.PRESETS.size()) {
                errors.add("Creative-Tab hat " + presets + " Drohnen statt " + DroneBuild.PRESETS.size());
            }
            List<ItemStack> parts = DroneAssembly.disassemble(ContentTab.presetDrone(DroneBuild.RACER));
            List<PartInfo> layout = BuildSlots.layout(DroneBuild.RACER);
            for (int i = 0; i < BuildSlots.COUNT; i++) {
                if (DroneAssembly.part(parts.get(i)).orElse(null) != layout.get(i)) {
                    errors.add("Zerlegen: Slot " + i + " falsch");
                }
            }
            checkChargeRoundTrip(errors, parts);
        }
        return errors.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", errors));
    }

    private static void checkChargeRoundTrip(List<String> errors, List<ItemStack> fullParts) {
        ItemStack fullBattery = fullParts.get(BuildSlots.BATTERY);
        if (!fullBattery.getComponentsPatch().isEmpty()) {
            errors.add("voller Akku trägt beim Zerlegen einen eigenen Ladestand und stapelt nicht mit neuen Akkus");
        }
        if (!ItemStack.isSameItemSameComponents(fullBattery, ContentItems.stack(DroneBuild.RACER.battery()))) {
            errors.add("voller Akku aus dem Zerlegen unterscheidet sich von einem neuen Akku");
        }
        ItemStack drained = ContentTab.presetDrone(DroneBuild.RACER);
        drained.set(CoreComponents.BATTERY_CHARGE.get(), TEST_CHARGE);
        List<ItemStack> parts = DroneAssembly.disassemble(drained);
        ItemStack battery = parts.get(BuildSlots.BATTERY);
        if (Math.abs(DroneAssembly.charge(battery) - TEST_CHARGE) > 1.0E-6F) {
            errors.add("Zerlegen verliert den Akkustand: " + battery.get(CoreComponents.BATTERY_CHARGE.get()));
        }
        if (!battery.getItem().isBarVisible(battery)) {
            errors.add("halb leerer Akku zeigt keinen Ladebalken");
        }
        ItemStack rebuilt = DroneAssembly.createDrone(DroneBuild.RACER, parts.get(BuildSlots.STACK), parts.get(BuildSlots.FRAME), battery);
        if (Math.abs(DroneAssembly.charge(rebuilt) - TEST_CHARGE) > 1.0E-6F) {
            errors.add("Zusammenbau lädt den Akku auf: " + rebuilt.get(CoreComponents.BATTERY_CHARGE.get()));
        }
    }

    private static void checkGoggles(List<String> errors, Identifier id, VideoSystem system) {
        Optional<Item> item = ContentIds.item(id);
        if (item.isEmpty()) {
            errors.add(id + " fehlt");
            return;
        }
        ItemStack stack = new ItemStack(item.get());
        if (ContentIds.gogglesSystem(stack).orElse(null) != system) {
            errors.add(id + " liefert System " + ContentIds.gogglesSystem(stack));
        }
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != EquipmentSlot.HEAD) {
            errors.add(id + " nicht im Kopf-Slot tragbar");
        } else if (equippable.assetId().isPresent()) {
            errors.add(id + " trägt das Equipment-Asset " + equippable.assetId().get().identifier()
                    + " und würde als flache Kopf-Ebene statt als Modell gezeichnet");
        }
    }

    private static List<String> expectedRecipes() {
        List<String> names = new ArrayList<>();
        for (PartCategory category : PartCategory.values()) {
            for (PartInfo part : category.parts()) {
                if (part.builtin() && !(part instanceof StackType stack && stack.creative())) {
                    names.add(part.key());
                }
            }
        }
        names.add(ContentIds.GOGGLES_ANALOG.getPath());
        names.add(ContentIds.GOGGLES_DIGITAL.getPath());
        names.add(ContentIds.DRONE_WORKBENCH.getPath());
        names.add(ContentIds.BATTERY_CHARGER.getPath());
        names.add(ContentIds.REMOTE.getPath());
        return names;
    }

    private static StepResult recipeStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ctx.state().put(OPEN, ctx.onServer(server -> {
                List<String> missing = new ArrayList<>();
                for (String name : expectedRecipes()) {
                    ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Constants.id(name));
                    if (server.getRecipeManager().byKey(key).isEmpty()) {
                        missing.add(name);
                    }
                }
                if (!missing.isEmpty()) {
                    throw new IllegalStateException("Rezepte fehlen: " + missing);
                }
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(OPEN);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(future.exceptionNow().getMessage());
        }
        return StepResult.pass();
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static BlockPos benchPos(ServerPlayer player) {
        return player.blockPosition().offset(2, 0, 2);
    }

    private static StepResult workbenchStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            if (!DroneAssembly.droneItemAvailable()) {
                return StepResult.fail("propwash:drone ist nicht registriert (drone-Modul)");
            }
            ctx.state().put(OPEN, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                BlockPos pos = benchPos(player);
                BlockState state = ContentBlocks.DRONE_WORKBENCH.block().get().defaultBlockState();
                level.setBlockAndUpdate(pos, state);
                player.getInventory().clearContent();
                if (player.openMenu(state.getMenuProvider(level, pos)).isEmpty()) {
                    throw new IllegalStateException("Menü ließ sich nicht öffnen");
                }
                if (!(player.containerMenu instanceof DroneWorkbenchMenu)) {
                    throw new IllegalStateException("falsches Menü " + player.containerMenu);
                }
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> open = (CompletableFuture<?>) ctx.state().get(OPEN);
        if (open.isCompletedExceptionally()) {
            return StepResult.fail("Öffnen: " + open.exceptionNow().getMessage());
        }
        CompletableFuture<?> assemble = (CompletableFuture<?>) ctx.state().get(ASSEMBLE);
        if (assemble == null) {
            if (!(mc.gui.screen() instanceof DroneWorkbenchScreen)) {
                return ctx.tick() > 100 ? StepResult.fail("Werkbank-Screen öffnete sich nicht: " + mc.gui.screen()) : StepResult.pending();
            }
            ctx.state().put(ASSEMBLE, ctx.onServer(ContentSelfTests::assembleOnServer));
            return StepResult.pending();
        }
        if (assemble.isCompletedExceptionally()) {
            closeQuietly(ctx);
            return StepResult.fail("Zusammenbau: " + assemble.exceptionNow().getMessage());
        }
        if (!assemble.isDone()) {
            return StepResult.pending();
        }
        CompletableFuture<?> finish = (CompletableFuture<?>) ctx.state().get(FINISH);
        if (finish == null) {
            if (!(mc.gui.screen() instanceof DroneWorkbenchScreen screen)) {
                return StepResult.fail("Werkbank-Screen wurde vorzeitig geschlossen");
            }
            Integer since = (Integer) ctx.state().get(ASSEMBLED_AT);
            if (since == null) {
                ctx.state().put(ASSEMBLED_AT, ctx.tick());
                return StepResult.pending();
            }
            DroneBuild shown = screen.getMenu().check().build().orElse(null);
            if (!DroneBuild.FREESTYLE.equals(shown) || screen.getMenu().resultPreview().isEmpty()) {
                return ctx.tick() - since > 60
                        ? StepResult.fail("Client zeigt nicht den fertigen Build: " + shown + " Ergebnis=" + screen.getMenu().resultPreview())
                        : StepResult.pending();
            }
            if (ctx.tick() - since < 10) {
                return StepResult.pending();
            }
            if (ThemeStats.frames(DroneWorkbenchScreen.class) == 0L || !ThemedScreen.active()) {
                closeQuietly(ctx);
                return StepResult.fail("Werkbank-Screen zeichnet nicht im Propwash-Theme");
            }
            ctx.log("Client zeigt FREESTYLE mit Ergebnis-Drohne, Werte: " + screen.report().rows().size() + " Zeilen");
            ctx.state().put(FINISH, ctx.onServer(ContentSelfTests::takeAndDisassembleOnServer));
            return StepResult.pending();
        }
        if (finish.isCompletedExceptionally()) {
            closeQuietly(ctx);
            return StepResult.fail("Entnehmen/Zerlegen: " + finish.exceptionNow().getMessage());
        }
        if (!finish.isDone()) {
            return StepResult.pending();
        }
        if (mc.gui.screen() instanceof DroneWorkbenchScreen) {
            return ctx.tick() > 380 ? StepResult.fail("Screen blieb nach dem Schließen offen") : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static StepResult accessoryStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ctx.state().put(OPEN, ctx.onServer(ContentSelfTests::accessoriesOnServer));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(OPEN);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(future.exceptionNow().getMessage());
        }
        List<String> errors = new ArrayList<>();
        TextureAtlas gui = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
        for (int slot = 0; slot < BuildSlots.COUNT; slot++) {
            Identifier icon = DroneWorkbenchScreen.slotIcon(slot);
            if (gui.getSprite(icon) == gui.missingSprite()) {
                errors.add("Slot-Symbol fehlt: " + icon);
            }
            if (!Language.getInstance().has(DroneWorkbenchScreen.slotHintKey(slot))) {
                errors.add("Slot " + slot + " ohne Hinweis " + DroneWorkbenchScreen.slotHintKey(slot));
            }
            if (DroneWorkbenchScreen.slotName(slot).getString().startsWith("propwash.")) {
                errors.add("Slot " + slot + " ohne Namen");
            }
        }
        for (AccessoryType type : AccessoryType.values()) {
            List<Component> lines = new ArrayList<>();
            PartItem.help(type, List.of()).appendTo(lines::add);
            if (lines.isEmpty() || !Language.getInstance().has(type.translationKey())) {
                errors.add(type.key() + " ohne Namen oder Tooltip");
            }
            for (Component line : lines) {
                if (line.getString().contains("propwash.")) {
                    errors.add(type.key() + ": unübersetzte Zeile " + line.getString());
                }
            }
        }
        return errors.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", errors));
    }

    private static void accessoriesOnServer(MinecraftServer server) {
        ServerPlayer player = player(server);
        Inventory inventory = player.getInventory();
        DroneBuild expected = DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_HEAVY).withAccessory(AccessoryType.LED_STRIP);
        DroneWorkbenchMenu menu = new DroneWorkbenchMenu(0, inventory);
        try {
            fillRequired(menu, player, DroneBuild.FREESTYLE);
            int top = BuildSlots.accessorySlot(AccessoryMount.TOP);
            int bottom = BuildSlots.accessorySlot(AccessoryMount.BOTTOM);
            menu.setCarried(ContentItems.stack(AccessoryType.LED_STRIP));
            menu.clicked(top, 0, ContainerInput.PICKUP, player);
            if (menu.getSlot(top).hasItem() || menu.getCarried().isEmpty()) {
                throw new IllegalStateException("LED-Streifen ließ sich in die obere Halterung legen");
            }
            menu.setCarried(ItemStack.EMPTY);
            placeByClick(menu, player, bottom, ContentItems.stack(AccessoryType.LED_STRIP));
            inventory.setItem(11, ContentItems.stack(AccessoryType.ACTION_CAM_HEAVY));
            menu.clicked(menuIndexOfInventorySlot(11), 0, ContainerInput.QUICK_MOVE, player);
            if (!inventory.getItem(11).isEmpty()) {
                throw new IllegalStateException("Shift-Klick legte die Action-Cam nicht in die Halterung");
            }
            expectPart(menu, top, AccessoryType.ACTION_CAM_HEAVY);
            ItemStack result = menu.getSlot(DroneWorkbenchMenu.RESULT_SLOT).getItem();
            DroneBuild built = result.get(CoreComponents.DRONE_BUILD.get());
            if (!expected.equals(built)) {
                throw new IllegalStateException("Ergebnis mit Zubehör falsch: " + built + " / " + menu.check());
            }
            List<ItemStack> parts = DroneAssembly.disassemble(result);
            if (DroneAssembly.part(parts.get(top)).orElse(null) != AccessoryType.ACTION_CAM_HEAVY
                    || DroneAssembly.part(parts.get(bottom)).orElse(null) != AccessoryType.LED_STRIP) {
                throw new IllegalStateException("Zerlegen gibt das Zubehör nicht zurück: " + parts);
            }
            List<ItemStack> bare = DroneAssembly.disassemble(ContentTab.presetDrone(DroneBuild.RACER));
            if (!bare.get(top).isEmpty() || !bare.get(bottom).isEmpty()) {
                throw new IllegalStateException("Drohne ohne Zubehör liefert Zubehör beim Zerlegen");
            }
            QuadcopterEntity drone = DroneModule.DRONE.get().create(player.level(), EntitySpawnReason.COMMAND);
            if (drone == null) {
                throw new IllegalStateException("Drohne ließ sich nicht erzeugen");
            }
            drone.loadFromStack(result);
            if (!expected.equals(drone.build()) || !expected.equals(drone.toStack().get(CoreComponents.DRONE_BUILD.get()))) {
                throw new IllegalStateException("Drohne verliert das Zubehör: " + drone.build());
            }
            drone.discard();
        } finally {
            inventory.setItem(11, ItemStack.EMPTY);
        }
        DroneWorkbenchMenu whoop = new DroneWorkbenchMenu(0, inventory);
        fillRequired(whoop, player, DroneBuild.WHOOP);
        placeByClick(whoop, player, BuildSlots.accessorySlot(AccessoryMount.TOP), ContentItems.stack(AccessoryType.ACTION_CAM_LIGHT));
        if (!whoop.resultPreview().isEmpty() || !whoop.check().build().orElseThrow().problems().contains(DroneBuild.Issue.ACCESSORY_MOUNT)) {
            throw new IllegalStateException("Whoop mit Action-Cam wurde gebaut");
        }
    }

    private static void fillRequired(DroneWorkbenchMenu menu, ServerPlayer player, DroneBuild build) {
        placeByClick(menu, player, BuildSlots.FRAME, ContentItems.stack(build.frame()));
        placeByClick(menu, player, BuildSlots.STACK, ContentItems.stack(build.stack()));
        placeByClick(menu, player, BuildSlots.VIDEO, ContentItems.stack(build.video()));
        placeByClick(menu, player, BuildSlots.BATTERY, ContentItems.stack(build.battery()));
        for (int i = 0; i < DroneBuild.MOTOR_COUNT; i++) {
            placeByClick(menu, player, BuildSlots.MOTOR + i, ContentItems.stack(build.motor()));
            placeByClick(menu, player, BuildSlots.PROP + i, ContentItems.stack(build.prop()));
        }
    }

    private static void closeQuietly(SelfTest.StepContext ctx) {
        ctx.onServer(server -> {
            ServerPlayer player = player(server);
            player.closeContainer();
            player.getInventory().clearContent();
            player.level().removeBlock(benchPos(player), false);
        });
    }

    private static int menuIndexOfInventorySlot(int inventorySlot) {
        if (inventorySlot < Inventory.getSelectionSize()) {
            return DroneWorkbenchMenu.HOTBAR_START + inventorySlot;
        }
        return DroneWorkbenchMenu.INV_START + inventorySlot - Inventory.getSelectionSize();
    }

    private static DroneWorkbenchMenu menu(ServerPlayer player) {
        if (!(player.containerMenu instanceof DroneWorkbenchMenu menu)) {
            throw new IllegalStateException("Werkbank-Menü nicht offen: " + player.containerMenu);
        }
        return menu;
    }

    private static void assembleOnServer(MinecraftServer server) {
        ServerPlayer player = player(server);
        DroneWorkbenchMenu menu = menu(player);
        DroneBuild build = DroneBuild.FREESTYLE;
        ItemStack frame = ContentItems.stack(build.frame());
        frame.set(DataComponents.CUSTOM_NAME, TEST_NAME);
        ItemStack stackPart = ContentItems.stack(build.stack());
        stackPart.set(CoreComponents.FLIGHT_TUNE.get(), TEST_TUNE);
        placeByClick(menu, player, BuildSlots.FRAME, frame);
        placeByClick(menu, player, BuildSlots.STACK, stackPart);
        placeByClick(menu, player, BuildSlots.VIDEO, ContentItems.stack(build.video()));
        placeByClick(menu, player, BuildSlots.BATTERY, ContentItems.stack(build.battery()));

        menu.setCarried(ContentItems.stack(build.prop()));
        menu.clicked(BuildSlots.FRAME, 0, ContainerInput.PICKUP, player);
        if (DroneAssembly.part(menu.getSlot(BuildSlots.FRAME).getItem()).orElse(null) != build.frame()
                || DroneAssembly.part(menu.getCarried()).orElse(null) != build.prop()) {
            throw new IllegalStateException("Propeller ließ sich in den Rahmen-Slot legen");
        }
        menu.setCarried(ItemStack.EMPTY);

        player.getInventory().setItem(9, ContentItems.stack(build.motor()).copyWithCount(6));
        player.getInventory().setItem(10, ContentItems.stack(build.prop()).copyWithCount(5));
        menu.clicked(menuIndexOfInventorySlot(9), 0, ContainerInput.QUICK_MOVE, player);
        menu.clicked(menuIndexOfInventorySlot(10), 0, ContainerInput.QUICK_MOVE, player);
        if (player.getInventory().getItem(9).getCount() != 2 || player.getInventory().getItem(10).getCount() != 1) {
            throw new IllegalStateException("Shift-Klick verteilte falsch: Motoren übrig " + player.getInventory().getItem(9).getCount()
                    + ", Props übrig " + player.getInventory().getItem(10).getCount());
        }
        for (int i = 0; i < DroneBuild.MOTOR_COUNT; i++) {
            expectPart(menu, BuildSlots.MOTOR + i, build.motor());
            expectPart(menu, BuildSlots.PROP + i, build.prop());
        }
        ItemStack result = menu.getSlot(DroneWorkbenchMenu.RESULT_SLOT).getItem();
        if (!DroneAssembly.isDrone(result)) {
            throw new IllegalStateException("kein Drohnen-Item im Ergebnis: " + result + " / " + menu.check());
        }
        if (!build.equals(result.get(CoreComponents.DRONE_BUILD.get()))) {
            throw new IllegalStateException("Ergebnis-Build " + result.get(CoreComponents.DRONE_BUILD.get()));
        }
        Float charge = result.get(CoreComponents.BATTERY_CHARGE.get());
        if (charge == null || charge != DroneAssembly.FULL_CHARGE) {
            throw new IllegalStateException("Akku nicht voll: " + charge);
        }
        if (!TEST_TUNE.equals(result.get(CoreComponents.FLIGHT_TUNE.get())) || !TEST_NAME.equals(result.get(DataComponents.CUSTOM_NAME))) {
            throw new IllegalStateException("Tuning/Name nicht übernommen");
        }
    }

    private static void placeByClick(DroneWorkbenchMenu menu, ServerPlayer player, int slot, ItemStack stack) {
        menu.setCarried(stack);
        menu.clicked(slot, 0, ContainerInput.PICKUP, player);
        if (!menu.getCarried().isEmpty()) {
            throw new IllegalStateException("Slot " + slot + " nahm " + stack + " nicht an");
        }
    }

    private static void expectPart(DroneWorkbenchMenu menu, int slot, @Nullable PartInfo part) {
        ItemStack stack = menu.getSlot(slot).getItem();
        if (part == null) {
            if (!stack.isEmpty()) {
                throw new IllegalStateException("Slot " + slot + " enthält " + stack + " statt nichts");
            }
            return;
        }
        if (stack.getCount() != 1 || DroneAssembly.part(stack).orElse(null) != part) {
            throw new IllegalStateException("Slot " + slot + " enthält " + stack + " statt " + part.key());
        }
    }

    private static void takeAndDisassembleOnServer(MinecraftServer server) {
        ServerPlayer player = player(server);
        DroneWorkbenchMenu menu = menu(player);
        Inventory inventory = player.getInventory();
        menu.clicked(DroneWorkbenchMenu.RESULT_SLOT, 0, ContainerInput.QUICK_MOVE, player);
        int droneSlot = -1;
        int drones = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (DroneAssembly.isDrone(inventory.getItem(i))) {
                drones += inventory.getItem(i).getCount();
                droneSlot = i;
            }
        }
        if (drones != 1) {
            throw new IllegalStateException("nach dem Entnehmen " + drones + " Drohnen im Inventar");
        }
        if (!menu.partsEmpty() || !menu.getSlot(DroneWorkbenchMenu.RESULT_SLOT).getItem().isEmpty()) {
            throw new IllegalStateException("Teile nach dem Entnehmen nicht verbraucht");
        }
        menu.clicked(DroneWorkbenchMenu.RESULT_SLOT, 0, ContainerInput.QUICK_MOVE, player);

        inventory.getItem(droneSlot).set(CoreComponents.BATTERY_CHARGE.get(), TEST_CHARGE);
        menu.clicked(menuIndexOfInventorySlot(droneSlot), 0, ContainerInput.QUICK_MOVE, player);
        if (DroneAssembly.isDrone(inventory.getItem(droneSlot))) {
            throw new IllegalStateException("Drohne wurde nicht in den Zerlege-Slot verschoben");
        }
        if (!menu.getSlot(DroneWorkbenchMenu.SALVAGE_SLOT).getItem().isEmpty()) {
            throw new IllegalStateException("Zerlege-Slot nicht geleert");
        }
        List<@Nullable PartInfo> layout = BuildSlots.layout(DroneBuild.FREESTYLE);
        for (int slot = 0; slot < BuildSlots.COUNT; slot++) {
            expectPart(menu, slot, layout.get(slot));
        }
        if (!TEST_TUNE.equals(menu.getSlot(BuildSlots.STACK).getItem().get(CoreComponents.FLIGHT_TUNE.get()))) {
            throw new IllegalStateException("Tuning nicht im Stack gespeichert");
        }
        if (!TEST_NAME.equals(menu.getSlot(BuildSlots.FRAME).getItem().get(DataComponents.CUSTOM_NAME))) {
            throw new IllegalStateException("Name nicht im Rahmen gespeichert");
        }
        ItemStack rebuilt = menu.getSlot(DroneWorkbenchMenu.RESULT_SLOT).getItem();
        if (!DroneAssembly.isDrone(rebuilt)) {
            throw new IllegalStateException("zerlegte Teile ergeben keine Drohne mehr");
        }
        float batteryCharge = DroneAssembly.charge(menu.getSlot(BuildSlots.BATTERY).getItem());
        if (Math.abs(batteryCharge - TEST_CHARGE) > 1.0E-6F) {
            throw new IllegalStateException("Akkustand nicht im Akku gespeichert: " + batteryCharge);
        }
        if (Math.abs(DroneAssembly.charge(rebuilt) - TEST_CHARGE) > 1.0E-6F) {
            throw new IllegalStateException("Werkbank lädt den Akku beim Zusammenbau auf: " + DroneAssembly.charge(rebuilt));
        }

        player.closeContainer();
        Map<PartInfo, Integer> counts = new HashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (DroneAssembly.isDrone(stack)) {
                throw new IllegalStateException("Drohne nach dem Schließen dupliziert");
            }
            DroneAssembly.part(stack).ifPresent(part -> counts.merge(part, stack.getCount(), Integer::sum));
        }
        DroneBuild build = DroneBuild.FREESTYLE;
        Map<PartInfo, Integer> expected = Map.of(build.frame(), 1, build.stack(), 1, build.video(), 1, build.battery(), 1,
                build.motor(), DroneBuild.MOTOR_COUNT + 2, build.prop(), DroneBuild.MOTOR_COUNT + 1);
        if (!counts.equals(expected)) {
            throw new IllegalStateException("Inventar nach dem Schließen: " + counts + " erwartet " + expected);
        }
        inventory.clearContent();
        ServerLevel level = player.level();
        BlockPos pos = benchPos(player);
        if (level.getBlockState(pos).is(ContentBlocks.DRONE_WORKBENCH.block().get())) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }
}
