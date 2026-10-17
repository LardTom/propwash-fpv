package de.lardtom.propwash.creative.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.PartItem;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.creative.CreativeFlight;
import de.lardtom.propwash.creative.CreativeLimits;
import de.lardtom.propwash.creative.CreativeModule;
import de.lardtom.propwash.creative.CreativeParam;
import de.lardtom.propwash.creative.CreativeShareCode;
import de.lardtom.propwash.creative.CreativeSpec;
import de.lardtom.propwash.creative.net.CreativeEditC2S;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class CreativeSelfTests {
    public static final DroneBuild INSTALLED = new DroneBuild(FrameType.FREESTYLE_5, StackType.CREATIVE_FC, MotorType.M2207_1750,
            PropType.P51_3B, VideoType.DIGITAL_HD, BatteryType.B6S_1300);
    private static final String INDEX = "creative_index";
    private static final String SINCE = "creative_since";
    private static final String FUTURE = "creative_future";
    private static final int WAIT_TICKS = 120;
    private static final AtomicInteger DRONE_ID = new AtomicInteger(-1);

    private CreativeSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("kreativ-drohne: Kreativ-FC im Kreativ-Tab, episch, ohne Rezept, passt in jeden Rahmen", 200,
                CreativeSelfTests::itemStep);
        SelfTest.clientStep("kreativ-drohne: Werkbank übernimmt die Editor-Werte vom FC und gibt sie beim Zerlegen zurück", 200,
                CreativeSelfTests::workbenchStep);
        SelfTest.clientStep("kreativ-drohne: Drohnen-Editor speichert über den Server (Kreativmodus, Drohne in der Hand)", 600,
                CreativeSelfTests::editStep);
        SelfTest.clientStep("kreativ-drohne: Im Überlebensmodus nur lesbar, Server lehnt Änderungen ab", 600,
                CreativeSelfTests::survivalStep);
        SelfTest.clientStep("kreativ-drohne: Teilen-Code und eigene Vorlage im Editor", 400, CreativeSelfTests::templateStep);
        SelfTest.clientStep("kreativ-drohne: Fliegt mit den Editor-Werten, Änderung gilt nach dem Entschärfen", 1200,
                CreativeSelfTests::flightStep);
    }

    public static CreativeSpec sample() {
        EnumMap<PartKind, String> base = new EnumMap<>(PartKind.class);
        base.put(PartKind.FRAME, FrameType.RACE_5.key());
        base.put(PartKind.MOTOR, MotorType.M2306_1860.key());
        return new CreativeSpec(base, Map.of()).with(CreativeParam.MOTOR_KV, 2150.0).with(CreativeParam.PROP_THRUST_COEFFICIENT, 0.125)
                .with(CreativeParam.FRAME_MASS, 140.0).with(CreativeParam.ARM_FRONT_LEFT_FORWARD, 95.0)
                .with(CreativeParam.ARM_FRONT_RIGHT_FORWARD, 95.0);
    }

    public static ItemStack drone(DroneBuild build) {
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), build);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        return stack;
    }

    private interface Phase {
        @Nullable StepResult run(SelfTest.StepContext ctx, int waited);
    }

    private static Phase server(String what, Consumer<MinecraftServer> action) {
        return (ctx, waited) -> {
            Map<String, Object> state = ctx.state();
            CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
            if (future == null) {
                state.put(FUTURE, ctx.onServer(action));
                return StepResult.pending();
            }
            if (!future.isDone()) {
                return StepResult.pending();
            }
            state.remove(FUTURE);
            if (future.isCompletedExceptionally()) {
                Throwable cause = future.exceptionNow();
                return StepResult.fail(what + ": " + (cause.getMessage() != null ? cause.getMessage() : cause.toString()));
            }
            return null;
        };
    }

    private static Phase await(String what, BooleanSupplier condition) {
        return (ctx, waited) -> {
            if (condition.getAsBoolean()) {
                return null;
            }
            return waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail(what);
        };
    }

    private static Phase check(String what, BooleanSupplier condition) {
        return (ctx, waited) -> condition.getAsBoolean() ? null : StepResult.fail(what);
    }

    private static Phase client(Runnable action) {
        return (ctx, waited) -> {
            action.run();
            return null;
        };
    }

    private static Phase wait(int ticks) {
        return (ctx, waited) -> waited < ticks ? StepResult.pending() : null;
    }

    private static StepResult script(SelfTest.StepContext ctx, List<Phase> phases, Consumer<MinecraftServer> cleanup) {
        Map<String, Object> state = ctx.state();
        int index = (int) state.getOrDefault(INDEX, 0);
        if (!state.containsKey(SINCE)) {
            state.put(SINCE, ctx.tick());
        }
        while (index < phases.size()) {
            int waited = ctx.tick() - (int) state.get(SINCE);
            StepResult result;
            try {
                result = phases.get(index).run(ctx, waited);
            } catch (RuntimeException e) {
                result = StepResult.fail(e.getMessage() != null ? e.getMessage() : e.toString());
            }
            if (result == null) {
                index++;
                state.put(INDEX, index);
                state.put(SINCE, ctx.tick());
                continue;
            }
            if (result.status() == StepResult.Status.FAIL) {
                closeScreen();
                PilotSession.INSTANCE.clearTestInput();
                if (PilotSession.INSTANCE.isActive()) {
                    PilotSession.INSTANCE.requestStop();
                }
                ctx.onServer(cleanup);
            }
            return result;
        }
        return StepResult.pass();
    }

    @SuppressWarnings("unchecked")
    private static <T> T keep(SelfTest.StepContext ctx, String key, Supplier<T> init) {
        return (T) ctx.state().computeIfAbsent(key, k -> init.get());
    }

    static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    static DroneBuild clientHeld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return DroneBuild.FREESTYLE;
        }
        return mc.player.getMainHandItem().getOrDefault(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
    }

    static @Nullable CreativeEditorTab openTab() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            ConfigTabs.open(null, CreativeEditorTab.ID);
            return null;
        }
        ConfigTab active = screen.activeTab();
        if (active == null || !CreativeEditorTab.ID.equals(active.id())) {
            screen.selectTab(CreativeEditorTab.ID);
            return null;
        }
        return active instanceof CreativeEditorTab tab && !tab.rows().isEmpty() ? tab : null;
    }

    static @Nullable CreativeEditorTab activeTab() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gui.screen() instanceof ConfiguratorScreen screen && screen.activeTab() instanceof CreativeEditorTab tab ? tab : null;
    }

    static void closeScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        }
    }

    private static Phase tabOpen(AtomicReference<CreativeEditorTab> tab) {
        return (ctx, waited) -> {
            CreativeEditorTab open = openTab();
            if (open != null) {
                tab.set(open);
                return null;
            }
            return waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail("Tab Drohnen-Editor öffnet nicht");
        };
    }

    private static StepResult itemStep(SelfTest.StepContext ctx) {
        AtomicReference<String> problem = keep(ctx, "problem", AtomicReference::new);
        return script(ctx, List.of(
                client(() -> {
                    ItemStack stack = ContentItems.stack(StackType.CREATIVE_FC);
                    if (stack.isEmpty() || !(stack.getItem() instanceof PartItem)) {
                        problem.set("Item creative_fc fehlt oder ist kein Bauteil");
                        return;
                    }
                    if (stack.getRarity() != Rarity.EPIC) {
                        problem.set("Seltenheit " + stack.getRarity());
                        return;
                    }
                    boolean listed = false;
                    for (ItemStack shown : ContentTab.contents()) {
                        listed |= ItemStack.isSameItem(shown, stack);
                    }
                    if (!listed) {
                        problem.set("nicht im Kreativ-Tab");
                        return;
                    }
                    for (FrameType frame : FrameType.all()) {
                        DroneBuild build = new DroneBuild(frame, StackType.CREATIVE_FC, INSTALLED.motor(), INSTALLED.prop(), INSTALLED.video(),
                                INSTALLED.battery());
                        if (build.problems().contains(DroneBuild.Issue.STACK_MOUNT) || !build.isCreative()) {
                            problem.set("passt nicht in " + frame.key());
                            return;
                        }
                    }
                }),
                check("Kreativ-FC", () -> problem.get() == null),
                server("kein Rezept", server -> {
                    ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Constants.id(StackType.CREATIVE_FC.key()));
                    if (server.getRecipeManager().byKey(key).isPresent()) {
                        throw new IllegalStateException("Rezept für creative_fc vorhanden");
                    }
                })), server -> {
        });
    }

    private static StepResult workbenchStep(SelfTest.StepContext ctx) {
        return script(ctx, List.of(server("Werkbank", server -> {
            CreativeSpec spec = sample();
            SimpleContainer parts = new SimpleContainer(BuildSlots.COUNT);
            parts.setItem(BuildSlots.FRAME, ContentItems.stack(INSTALLED.frame()));
            ItemStack controller = ContentItems.stack(StackType.CREATIVE_FC);
            controller.set(CreativeModule.SPEC.get(), spec);
            parts.setItem(BuildSlots.STACK, controller);
            parts.setItem(BuildSlots.VIDEO, ContentItems.stack(INSTALLED.video()));
            parts.setItem(BuildSlots.BATTERY, Batteries.stackOf(INSTALLED.battery(), 1.0F));
            for (int i = 0; i < DroneBuild.MOTOR_COUNT; i++) {
                parts.setItem(BuildSlots.MOTOR + i, ContentItems.stack(INSTALLED.motor()));
                parts.setItem(BuildSlots.PROP + i, ContentItems.stack(INSTALLED.prop()));
            }
            DroneBuild shown = DroneAssembly.check(parts).build().orElseThrow(() -> new IllegalStateException("Werkbank ohne Bauplan"));
            if (!shown.creative().equals(spec)) {
                throw new IllegalStateException("Werkbank zeigt ohne Editor-Werte: " + shown.creative());
            }
            ItemStack drone = DroneAssembly.assemble(parts);
            DroneBuild built = DroneAssembly.buildOf(drone);
            if (drone.isEmpty() || !built.isCreative() || !built.creative().equals(spec)) {
                throw new IllegalStateException("gebaute Drohne ohne Editor-Werte: " + built);
            }
            List<ItemStack> back = DroneAssembly.disassemble(drone);
            CreativeSpec stored = back.get(BuildSlots.STACK).get(CreativeModule.SPEC.get());
            if (!spec.equals(stored)) {
                throw new IllegalStateException("FC nach dem Zerlegen: " + stored);
            }
            List<ItemStack> plain = DroneAssembly.disassemble(drone(INSTALLED));
            if (plain.get(BuildSlots.STACK).has(CreativeModule.SPEC.get())) {
                throw new IllegalStateException("FC ohne Editor-Werte bekommt trotzdem welche");
            }
            DroneBuild swapped = DroneAssembly.buildOf(drone).withCreative(spec);
            if (!AirframeSpec.of(swapped).equals(AirframeSpec.of(built))) {
                throw new IllegalStateException("Flugmodell nicht aus dem Zwischenspeicher");
            }
        })), server -> {
        });
    }

    private static Consumer<MinecraftServer> restoreHand(AtomicReference<ItemStack> previous, AtomicReference<GameType> mode) {
        return server -> {
            ServerPlayer player = player(server);
            ItemStack saved = previous.getAndSet(null);
            player.setItemInHand(InteractionHand.MAIN_HAND, saved == null ? ItemStack.EMPTY : saved);
            GameType savedMode = mode.getAndSet(null);
            if (savedMode != null) {
                player.setGameMode(savedMode);
            }
        };
    }

    private static Phase give(DroneBuild build, GameType gameType, AtomicReference<ItemStack> previous, AtomicReference<GameType> mode) {
        return server("Drohne geben", server -> {
            ServerPlayer player = player(server);
            player.closeContainer();
            if (previous.get() == null) {
                previous.set(player.getMainHandItem().copy());
            }
            if (mode.get() == null) {
                mode.set(player.gameMode());
            }
            player.setGameMode(gameType);
            player.setItemInHand(InteractionHand.MAIN_HAND, drone(build));
        });
    }

    private static StepResult editStep(SelfTest.StepContext ctx) {
        AtomicReference<ItemStack> previous = keep(ctx, "previous", AtomicReference::new);
        AtomicReference<GameType> mode = keep(ctx, "mode", AtomicReference::new);
        AtomicReference<CreativeEditorTab> tab = keep(ctx, "tab", AtomicReference::new);
        AtomicReference<CreativeSpec> expected = keep(ctx, "expected", AtomicReference::new);
        AtomicReference<String> log = keep(ctx, "log", AtomicReference::new);
        Consumer<MinecraftServer> cleanup = restoreHand(previous, mode);
        return script(ctx, List.of(
                give(INSTALLED, GameType.CREATIVE, previous, mode),
                await("Kreativ-Drohne kommt in der Hand an", () -> clientHeld().equals(INSTALLED)
                        && Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative()),
                tabOpen(tab),
                (c, waited) -> {
                    CreativeEditorTab t = tab.get();
                    if (t.readOnly() || !CreativeEditorTab.editable()) {
                        return StepResult.fail("Editor im Kreativmodus nur lesbar");
                    }
                    if (t.row(CreativeParam.MOTOR_KV).isEmpty() || t.row(CreativeParam.ARM_REAR_RIGHT_UP).isEmpty()) {
                        return StepResult.fail("Zeilen fehlen: " + t.rows().size());
                    }
                    Optional<CreativeLimits.Note> clamp = t.draft().set(CreativeParam.FRAME_MASS, 1.0E9);
                    if (clamp.isEmpty() || t.draft().value(CreativeParam.FRAME_MASS) != CreativeParam.FRAME_MASS.max()) {
                        return StepResult.fail("Masse nicht begrenzt: " + t.draft().value(CreativeParam.FRAME_MASS));
                    }
                    t.draft().reset(CreativeParam.FRAME_MASS);
                    t.selectTemplate("preset:racer");
                    t.loadTemplate();
                    if (!t.draft().basePart(PartKind.FRAME).equals(Optional.of(FrameType.RACE_5.key()))
                            || !t.draft().basePart(PartKind.MOTOR).equals(Optional.of(MotorType.M2306_1860.key()))
                            || !t.draft().basePart(PartKind.PROP).equals(Optional.of(PropType.P51_29_3B.key()))) {
                        return StepResult.fail("Vorlage Racer nicht geladen: " + t.draft().spec());
                    }
                    double before = t.draft().value(CreativeParam.MOTOR_KV);
                    t.draft().set(CreativeParam.MOTOR_KV, 2600.0);
                    t.draft().set(CreativeParam.BATTERY_CELLS, 4.0);
                    if (t.draft().value(CreativeParam.MOTOR_KV) != 2600.0 || before != DroneBuild.RACER.motor().kv) {
                        return StepResult.fail("KV im Entwurf " + t.draft().value(CreativeParam.MOTOR_KV) + ", vorher " + before);
                    }
                    if (t.warningLines() == null) {
                        return StepResult.fail("Hinweise fehlen");
                    }
                    expected.set(t.draft().spec());
                    log.set("Entwurf " + CreativeShareCode.encode(t.draft().spec()) + ", Hinweise " + t.warningLines().size());
                    ((ConfiguratorScreen) Minecraft.getInstance().gui.screen()).saveAndClose();
                    return null;
                },
                await("Server übernimmt die Editor-Werte", () -> clientHeld().creative().equals(expected.get())),
                server("Server-Stand", server -> {
                    DroneBuild build = player(server).getMainHandItem().getOrDefault(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
                    if (!build.creative().equals(expected.get())) {
                        throw new IllegalStateException("Server-Item: " + build.creative());
                    }
                    AirframeSpec spec = AirframeSpec.of(build);
                    if (Math.abs(spec.kv - 2600.0 * 2.0 * Math.PI / 60.0) > 1.0E-6 || spec.cells != 4) {
                        throw new IllegalStateException("Flugmodell: kv " + spec.kv + ", Zellen " + spec.cells);
                    }
                }),
                server("Aufräumen", cleanup),
                (c, waited) -> {
                    c.log(log.get());
                    return null;
                }), cleanup);
    }

    private static StepResult survivalStep(SelfTest.StepContext ctx) {
        AtomicReference<ItemStack> previous = keep(ctx, "previous", AtomicReference::new);
        AtomicReference<GameType> mode = keep(ctx, "mode", AtomicReference::new);
        AtomicReference<CreativeEditorTab> tab = keep(ctx, "tab", AtomicReference::new);
        Consumer<MinecraftServer> cleanup = restoreHand(previous, mode);
        DroneBuild edited = INSTALLED.withCreative(sample());
        return script(ctx, List.of(
                give(edited, GameType.SURVIVAL, previous, mode),
                await("Drohne und Überlebensmodus kommen an", () -> clientHeld().equals(edited)
                        && Minecraft.getInstance().player != null && !Minecraft.getInstance().player.isCreative()),
                tabOpen(tab),
                (c, waited) -> {
                    CreativeEditorTab t = tab.get();
                    if (!t.readOnly() || t.hasDefaults()) {
                        return StepResult.fail("Editor im Überlebensmodus änderbar");
                    }
                    for (CreativeEditorTab.Row row : t.rows()) {
                        if (row.spinner().active) {
                            return StepResult.fail("Feld " + row.param().key() + " im Überlebensmodus aktiv");
                        }
                    }
                    if (!t.shareCode().startsWith(CreativeShareCode.PREFIX)) {
                        return StepResult.fail("Teilen-Code fehlt im Lesemodus");
                    }
                    closeScreen();
                    ClientNet.sendToServer(new CreativeEditC2S(CreativeEditC2S.HELD_ITEM, false, CreativeSpec.EMPTY.with(CreativeParam.MOTOR_KV, 9000.0),
                            Float.NaN));
                    return null;
                },
                wait(20),
                server("Server lehnt ab", server -> {
                    DroneBuild build = player(server).getMainHandItem().getOrDefault(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
                    if (!build.equals(edited)) {
                        throw new IllegalStateException("Server änderte die Drohne im Überlebensmodus: " + build.creative());
                    }
                }),
                server("Aufräumen", cleanup)), cleanup);
    }

    private static StepResult templateStep(SelfTest.StepContext ctx) {
        AtomicReference<ItemStack> previous = keep(ctx, "previous", AtomicReference::new);
        AtomicReference<GameType> mode = keep(ctx, "mode", AtomicReference::new);
        AtomicReference<CreativeEditorTab> tab = keep(ctx, "tab", AtomicReference::new);
        AtomicReference<CreativeEditorTab> reopened = keep(ctx, "reopened", AtomicReference::new);
        Consumer<MinecraftServer> cleanup = server -> {
            CreativeTemplates.delete("Selbsttest");
            restoreHand(previous, mode).accept(server);
        };
        CreativeSpec shared = sample().with(CreativeParam.INERTIA_YAW, 31000.0);
        return script(ctx, List.of(
                give(INSTALLED, GameType.CREATIVE, previous, mode),
                await("Kreativ-Drohne kommt an", () -> clientHeld().equals(INSTALLED)),
                tabOpen(tab),
                (c, waited) -> {
                    CreativeEditorTab t = tab.get();
                    if (t.paste("kein Code") || !t.paste(CreativeShareCode.encode(shared))) {
                        return StepResult.fail("Teilen-Code einfügen");
                    }
                    if (!t.draft().spec().equals(shared)) {
                        return StepResult.fail("Code ergab " + t.draft().spec());
                    }
                    CreativeTemplates.save("Selbsttest", t.draft().spec());
                    closeScreen();
                    return null;
                },
                tabOpen(reopened),
                (c, waited) -> {
                    CreativeEditorTab t = reopened.get();
                    boolean found = t.templates().stream().anyMatch(choice -> choice.own() && choice.id().equals("own:Selbsttest")
                            && choice.spec().equals(shared));
                    if (!found) {
                        return StepResult.fail("eigene Vorlage fehlt in der Liste");
                    }
                    if (t.templates().stream().filter(choice -> choice.id().startsWith("preset:")).count() != DroneBuild.PRESETS.size()) {
                        return StepResult.fail("Presets fehlen in der Vorlagen-Liste");
                    }
                    t.selectTemplate("own:Selbsttest");
                    t.loadTemplate();
                    if (!t.draft().spec().equals(shared)) {
                        return StepResult.fail("eigene Vorlage lädt nicht");
                    }
                    closeScreen();
                    if (!CreativeTemplates.delete("Selbsttest") || CreativeTemplates.byName("Selbsttest").isPresent()) {
                        return StepResult.fail("eigene Vorlage lässt sich nicht löschen");
                    }
                    return null;
                },
                server("Aufräumen", cleanup)), cleanup);
    }

    private static @Nullable QuadcopterEntity serverDrone(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(DRONE_ID.get()) instanceof QuadcopterEntity drone) {
                return drone;
            }
        }
        return null;
    }

    private static Phase fly(String what, int ticks, boolean arm, double extraThrottle) {
        return (ctx, waited) -> {
            PilotSession session = PilotSession.INSTANCE;
            DroneSim sim = session.sim();
            if (sim == null) {
                return StepResult.fail(what + ": Sitzung verloren");
            }
            Telemetry t = sim.telemetry();
            float throttle = extraThrottle <= 0.0 ? 0.0F : (float) Math.min(1.0, t.hoverThrottleStick + extraThrottle);
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), arm, StabMode.ANGLE);
            if (!Double.isFinite(t.tiltDeg) || !Double.isFinite(t.speed)) {
                return StepResult.fail(what + ": Flugzustand nicht endlich");
            }
            return waited < ticks ? StepResult.pending() : null;
        };
    }

    private static StepResult flightStep(SelfTest.StepContext ctx) {
        AtomicReference<ItemStack> previous = keep(ctx, "previous", AtomicReference::new);
        AtomicReference<GameType> mode = keep(ctx, "mode", AtomicReference::new);
        AtomicReference<String> log = keep(ctx, "log", AtomicReference::new);
        DroneBuild first = INSTALLED.withCreative(sample().with(CreativeParam.BATTERY_MASS, 300.0));
        CreativeSpec second = sample().with(CreativeParam.BATTERY_MASS, 300.0).with(CreativeParam.MOTOR_KV, 1700.0);
        Consumer<MinecraftServer> cleanup = server -> {
            QuadcopterEntity d = serverDrone(server);
            if (d != null) {
                d.setPilot(null);
                d.discard();
            }
            restoreHand(previous, mode).accept(server);
        };
        PilotSession session = PilotSession.INSTANCE;
        return script(ctx, List.of(
                (c, waited) -> c.tick() < DronePiloting.REMOTE_DEBOUNCE_TICKS + 2 ? StepResult.pending() : null,
                server("Drohne platzieren und Sichtflug starten", server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    if (previous.get() == null) {
                        previous.set(player.getMainHandItem().copy());
                    }
                    if (mode.get() == null) {
                        mode.set(player.gameMode());
                    }
                    player.setGameMode(GameType.CREATIVE);
                    int x = player.getBlockX() + 5;
                    int z = player.getBlockZ() + 4;
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
                    if (drone == null) {
                        throw new IllegalStateException("Drohne nicht erzeugt");
                    }
                    drone.setBuild(first);
                    drone.snapTo(x + 0.5, top + 0.1, z + 0.5, 180.0F, 0.0F);
                    if (!level.addFreshEntity(drone)) {
                        throw new IllegalStateException("addFreshEntity fehlgeschlagen");
                    }
                    drone.setAttitude(new Quaternionf());
                    DRONE_ID.set(drone.getId());
                    ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
                    remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
                    player.setItemInHand(InteractionHand.MAIN_HAND, remote);
                    remote.use(level, player, InteractionHand.MAIN_HAND);
                    if (!drone.isFlownBy(player.getUUID())) {
                        throw new IllegalStateException("Sichtflug nicht gestartet");
                    }
                }),
                await("Sitzung fliegt mit dem Flugmodell der Kreativ-Drohne", () -> {
                    QuadcopterEntity drone = session.drone();
                    DroneSim sim = session.sim();
                    return drone != null && drone.getId() == DRONE_ID.get() && sim != null && drone.build().equals(first)
                            && sim.params() == AirframeSpec.of(first);
                }),
                check("Masse aus dem Editor", () -> {
                    DroneSim sim = session.sim();
                    double expected = AirframeSpec.of(first).massKg;
                    return sim != null && Math.abs(sim.params().massKg - expected) < 1.0E-9
                            && Math.abs(expected - AirframeSpec.of(INSTALLED).massKg) > 0.05;
                }),
                fly("entschärft warten", 10, false, 0.0),
                fly("scharf schalten und steigen", 40, true, 0.15),
                fly("schweben", 40, true, 0.02),
                fly("landen", 60, true, -1.0),
                fly("entschärfen", 20, false, 0.0),
                client(() -> {
                    DroneSim sim = session.sim();
                    log.set("geflogen: Masse " + (sim == null ? "?" : String.format(java.util.Locale.ROOT, "%.3f kg", sim.params().massKg))
                            + ", Höhe " + (sim == null ? "?" : String.format(java.util.Locale.ROOT, "%.2f m", sim.telemetry().heightAboveGround)));
                    ClientNet.sendToServer(new CreativeEditC2S(DRONE_ID.get(), false, second, Float.NaN));
                }),
                await("Neue Werte gelten im entschärften Flug", () -> {
                    DroneSim sim = session.sim();
                    QuadcopterEntity drone = session.drone();
                    return sim != null && drone != null && drone.build().creative().equals(second)
                            && Math.abs(sim.params().kv - 1700.0 * 2.0 * Math.PI / 60.0) < 1.0E-6;
                }),
                client(() -> {
                    session.clearTestInput();
                    session.requestStop();
                }),
                await("Sitzung beendet", () -> !session.isActive()),
                server("Aufräumen", cleanup),
                (c, waited) -> {
                    c.log(log.get() + ", Wert danach " + CreativeFlight.value(INSTALLED.withCreative(second), CreativeParam.MOTOR_KV));
                    return null;
                }), cleanup);
    }
}
