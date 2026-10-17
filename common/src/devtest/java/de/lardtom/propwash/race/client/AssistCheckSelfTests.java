package de.lardtom.propwash.race.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.race.AssistChecklist;
import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.AssistServerChecks;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.widget.ContentScroll;
import de.lardtom.propwash.video.client.VideoConfig;
import de.lardtom.propwash.video.client.VideoSettings;
import de.lardtom.propwash.video.client.VideoState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class AssistCheckSelfTests {
    private static final String FUTURE = "future";
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String SAVED_VIDEO = "video";
    private static final int GRAVITY = 130;
    private static final int WIND = 3;
    private static final int WAIT_TICKS = 120;
    private static final AtomicReference<@Nullable String> RESULT = new AtomicReference<>();

    private AssistCheckSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("flughilfe-prüfung: Server wertet vier Piloten je Runde mit genau ihren Gründen", 300, AssistCheckSelfTests::serverStep);
        SelfTest.clientStep("flughilfe-prüfung: Race-Tab nennt jeden Grund vor dem Flug mit Wert und Standard", 600, AssistCheckSelfTests::tabStep);
        SelfTest.clientStep("flughilfe-prüfung: Physik nach dem Zurücksetzen im Flug sofort wieder ohne Flughilfe", 900,
                AssistCheckSelfTests::flightStep);
    }

    public static Screenshots.Scene tabScene(boolean assisted) {
        return new TabScene(assisted);
    }

    private static @Nullable StepResult waitFuture(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return null;
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Schritt fehlgeschlagen: " + future.exceptionNow());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        ctx.state().remove(FUTURE);
        return null;
    }

    private static StepResult serverStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            RESULT.set(null);
            ctx.state().put(FUTURE, ctx.onServer(server -> RESULT.set(AssistServerChecks.check(server))));
            return StepResult.pending();
        }
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        String result = RESULT.get();
        if (result != null) {
            return StepResult.fail(result);
        }
        ctx.log("Piloten je Runde: " + AssistServerChecks.summary());
        return StepResult.pass();
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    static void applyAssisted(MinecraftServer server, boolean assisted) {
        WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, assisted ? GRAVITY : WorldRuleSpecs.GRAVITY_SCALE.defaultValue());
        WorldRules.set(server, WorldRuleSpecs.WIND, assisted ? WIND : WorldRuleSpecs.WIND.defaultValue());
        WorldRules.set(server, WorldRuleSpecs.DRONE_TRAILS, assisted ? 1 : WorldRuleSpecs.DRONE_TRAILS.defaultValue());
        WorldRules.set(server, WorldRuleSpecs.CRASH_DAMAGE, assisted ? 0 : WorldRuleSpecs.CRASH_DAMAGE.defaultValue());
        ServerPlayer player = player(server);
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack drone = new ItemStack(DroneModule.DRONE_ITEM.get());
        drone.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
        player.setItemInHand(InteractionHand.MAIN_HAND, drone);
    }

    static void restore(MinecraftServer server) {
        for (RuleSpec spec : List.of(WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.WIND, WorldRuleSpecs.DRONE_TRAILS, WorldRuleSpecs.CRASH_DAMAGE)) {
            WorldRules.set(server, spec, spec.defaultValue());
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    static void ownLatency(boolean own) {
        VideoConfig config = VideoSettings.get().copy();
        config.creativeLatencyFromModule = !own;
        config.creativeLatencyMs = 0;
        VideoSettings.CONFIG.set(config);
    }

    static boolean synced(boolean assisted) {
        Minecraft mc = Minecraft.getInstance();
        int gravity = assisted ? GRAVITY : WorldRuleSpecs.GRAVITY_SCALE.defaultValue();
        int wind = assisted ? WIND : WorldRuleSpecs.WIND.defaultValue();
        return ClientWorldRules.INSTANCE.get(WorldRuleSpecs.GRAVITY_SCALE) == gravity && ClientWorldRules.INSTANCE.get(WorldRuleSpecs.WIND) == wind
                && VideoState.INSTANCE.goggles(mc.player) == VideoSystem.CREATIVE && mc.player != null
                && ContentIds.is(mc.player.getMainHandItem(), ContentIds.DRONE);
    }

    static String readiness(@Nullable RaceConfigTab tab) {
        Minecraft mc = Minecraft.getInstance();
        return "Tab=" + (tab != null) + " Schwerkraft=" + ClientWorldRules.INSTANCE.get(WorldRuleSpecs.GRAVITY_SCALE) + " Wind="
                + ClientWorldRules.INSTANCE.get(WorldRuleSpecs.WIND) + " Brille=" + VideoState.INSTANCE.goggles(mc.player) + " Hand="
                + (mc.player == null ? "-" : mc.player.getMainHandItem()) + " Fenster=" + mc.gui.screen();
    }

    static @Nullable RaceConfigTab openTab() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            ConfigTabs.open(null, "race");
            return null;
        }
        ConfigTab active = screen.activeTab();
        if (active == null || !"race".equals(active.id())) {
            screen.selectTab("race");
            return null;
        }
        return active instanceof RaceConfigTab tab ? tab : null;
    }

    static void scrollToCheck() {
        if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
            ContentScroll scroll = screen.contentScroll();
            if (scroll != null) {
                scroll.edge(true);
            }
        }
    }

    static @Nullable String verify(AssistPreflight.Result result, boolean assisted) {
        if (!assisted) {
            if (!result.clean()) {
                return "Gründe ohne Flughilfe: " + result.reasons();
            }
            for (AssistPreflight.Row row : result.rows()) {
                if (row.status() == AssistChecklist.Status.FLAGGED) {
                    return "gelbe Zeile ohne Grund: " + row.label().getString();
                }
            }
            return null;
        }
        AssistReasons reasons = result.reasons();
        int expectedMask = LapAssist.WORLD_PHYSICS.bit() | LapAssist.GOGGLES_LATENCY.bit();
        if (reasons.mask() != expectedMask) {
            return "Gründe " + LapAssist.list(reasons.mask()) + " statt Welt-Physik und Brille";
        }
        List<AssistDetail> physics = reasons.details(LapAssist.WORLD_PHYSICS);
        List<AssistDetail> expected = List.of(AssistDetail.rule(WorldRuleSpecs.GRAVITY_SCALE, GRAVITY), AssistDetail.rule(WorldRuleSpecs.WIND, WIND));
        if (!physics.equals(expected)) {
            return "Welt-Physik nennt " + physics + " statt " + expected;
        }
        int transmitter = DroneBuild.FREESTYLE.video().latencyMs;
        if (!reasons.details(LapAssist.GOGGLES_LATENCY).equals(List.of(AssistDetail.goggles(0, transmitter)))) {
            return "Brille nennt " + reasons.details(LapAssist.GOGGLES_LATENCY);
        }
        long flagged = result.rows().stream().filter(row -> row.status() == AssistChecklist.Status.FLAGGED).count();
        if (flagged != 2) {
            return flagged + " gelbe Zeilen statt 2";
        }
        String text = result.rows().stream().map(row -> row.detail().getString()).reduce("", (a, b) -> a + " | " + b);
        if (!text.contains("130") || !text.contains("100")) {
            return "Text nennt Wert und Standard nicht: " + text;
        }
        return null;
    }

    private static StepResult tabStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (phase == 0) {
            state.put(SAVED_VIDEO, VideoSettings.get().copy());
            ownLatency(true);
            state.put(FUTURE, ctx.onServer(server -> applyAssisted(server, true)));
            state.put(PHASE, 1);
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        int waited = ctx.tick() - (int) state.get(SINCE);
        boolean assisted = phase == 1;
        if (phase <= 2) {
            RaceConfigTab tab = synced(assisted) ? openTab() : null;
            if (tab == null) {
                return waited > WAIT_TICKS ? finishTab(ctx, StepResult.fail("Race-Tab oder Welt-Regeln nicht bereit (Phase " + phase + "): "
                        + readiness(tab))) : StepResult.pending();
            }
            AssistPreflight.Result result = AssistPreflight.check(Minecraft.getInstance());
            String problem = verify(result, assisted);
            if (problem != null || tab.checkRowsDrawn() < result.rows().size()) {
                return waited > WAIT_TICKS ? finishTab(ctx, StepResult.fail((assisted ? "mit Flughilfe: " : "ohne Flughilfe: ")
                        + (problem != null ? problem : "Zeilen gezeichnet " + tab.checkRowsDrawn()))) : StepResult.pending();
            }
            ctx.log((assisted ? "mit Flughilfe: " : "ohne Flughilfe: ") + result.summary().getString() + " – "
                    + AssistText.reasons(result.reasons()).getString());
            if (assisted) {
                if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
                    screen.discardAndClose();
                }
                ownLatency(false);
                state.put(FUTURE, ctx.onServer(server -> applyAssisted(server, false)));
                state.put(PHASE, 2);
                state.put(SINCE, ctx.tick());
                return StepResult.pending();
            }
            return finishTab(ctx, StepResult.pass());
        }
        return StepResult.pending();
    }

    private static StepResult finishTab(SelfTest.StepContext ctx, StepResult result) {
        Object saved = ctx.state().get(SAVED_VIDEO);
        if (saved instanceof VideoConfig config) {
            VideoSettings.CONFIG.set(config);
        }
        if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        }
        ctx.onServer(AssistCheckSelfTests::restore);
        return result;
    }

    private static final class TabScene implements Screenshots.Scene {
        private final boolean assisted;
        private int phase;
        private int since;
        private @Nullable VideoConfig saved;
        private @Nullable CompletableFuture<Void> pending;

        TabScene(boolean assisted) {
            this.assisted = assisted;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            CompletableFuture<Void> future = this.pending;
            if (future != null && !future.isDone()) {
                return StepResult.pending();
            }
            if (this.phase == 0) {
                this.saved = VideoSettings.get().copy();
                ownLatency(this.assisted);
                this.pending = ctx.onServer(server -> applyAssisted(server, this.assisted));
                this.phase = 1;
                this.since = ctx.tick();
                return StepResult.pending();
            }
            RaceConfigTab tab = synced(this.assisted) ? openTab() : null;
            if (tab == null) {
                return ctx.tick() - this.since > WAIT_TICKS ? StepResult.fail("Race-Tab nicht bereit") : StepResult.pending();
            }
            scrollToCheck();
            String problem = verify(AssistPreflight.check(Minecraft.getInstance()), this.assisted);
            if (problem != null || tab.checkRowsDrawn() == 0) {
                return ctx.tick() - this.since > WAIT_TICKS ? StepResult.fail(problem != null ? problem : "Prüfung nicht gezeichnet") : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            scrollToCheck();
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            if (this.saved != null) {
                VideoSettings.CONFIG.set(this.saved);
            }
            if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            ctx.onServer(AssistCheckSelfTests::restore);
            this.phase = 0;
            this.saved = null;
            this.pending = null;
        }
    }

    private static final class Flight {
        volatile int droneId = -1;
        volatile @Nullable String failure;
        double targetY = Double.NaN;
        double lastY = Double.NaN;
        final AtomicBoolean serverFlag = new AtomicBoolean();
    }

    private static StepResult flightStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        Flight flight = (Flight) state.computeIfAbsent("flight", key -> new Flight());
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (flight.failure != null) {
            return endFlight(ctx, flight, StepResult.fail(flight.failure));
        }
        PilotSession session = PilotSession.INSTANCE;
        int phase = (int) state.getOrDefault(PHASE, 0);
        int waited = ctx.tick() - (int) state.getOrDefault(SINCE, 0);
        DroneSim sim = session.sim();
        switch (phase) {
            case 0 -> {
                if (ctx.tick() < DronePiloting.REMOTE_DEBOUNCE_TICKS + 2) {
                    return StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(server -> startFlight(server, flight)));
                next(ctx, 1);
            }
            case 1 -> {
                QuadcopterEntity drone = session.drone();
                if (!session.isFpv() || drone == null || drone.getId() != flight.droneId || sim == null) {
                    return waited > 200 ? endFlight(ctx, flight, StepResult.fail("FPV-Sitzung startet nicht")) : StepResult.pending();
                }
                if (!sim.telemetry().armed) {
                    session.setTestInput(StickPositions.IDLE, waited >= 10 && sim.telemetry().armBlockers == 0 && ctx.tick() % 10 >= 5, StabMode.ANGLE);
                    return waited > 200 ? endFlight(ctx, flight, StepResult.fail("nicht scharf: Sperren=" + sim.telemetry().armBlockers)) : StepResult.pending();
                }
                flight.targetY = session.comPosition(new Vector3d()).y + 2.5;
                if (session.physicsModified() || ClientRaceTracker.INSTANCE.reasonsNow().any()) {
                    return endFlight(ctx, flight, StepResult.fail("saubere Sitzung meldet " + ClientRaceTracker.INSTANCE.reasonsNow()));
                }
                state.put(FUTURE, ctx.onServer(server -> WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, GRAVITY)));
                next(ctx, 2);
            }
            case 2 -> {
                hover(session, sim, flight);
                AssistReasons now = ClientRaceTracker.INSTANCE.reasonsNow();
                boolean client = session.physicsModified() && now.details(LapAssist.WORLD_PHYSICS)
                        .equals(List.of(AssistDetail.rule(WorldRuleSpecs.GRAVITY_SCALE, GRAVITY)));
                if (!client || !flight.serverFlag.get()) {
                    probeServer(ctx, flight);
                    return waited > WAIT_TICKS ? endFlight(ctx, flight, StepResult.fail("Schwerkraft " + GRAVITY + " % nicht erkannt: Client="
                            + client + " Server=" + flight.serverFlag.get() + " Gründe=" + now)) : StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(server -> WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.GRAVITY_SCALE.defaultValue())));
                next(ctx, 3);
            }
            case 3 -> {
                hover(session, sim, flight);
                if (sim == null || !sim.telemetry().armed) {
                    return endFlight(ctx, flight, StepResult.fail("Drohne nach dem Zurücksetzen nicht mehr scharf"));
                }
                QuadcopterEntity drone = session.drone();
                AssistReasons now = drone == null ? AssistReasons.NONE : AssistText.evaluate(session, drone);
                boolean clean = !session.physicsModified() && now.none() && ClientRaceTracker.INSTANCE.reasonsNow().none();
                if (!clean || flight.serverFlag.get()) {
                    probeServer(ctx, flight);
                    return waited > WAIT_TICKS ? endFlight(ctx, flight, StepResult.fail("nach dem Zurücksetzen noch Flughilfe (scharf): Client="
                            + now + " Server-Flag=" + flight.serverFlag.get())) : StepResult.pending();
                }
                ctx.log("Schwerkraft 130 % im Flug erkannt, nach dem Zurücksetzen auf 100 % ohne Entschärfen wieder ohne Flughilfe (Client und Server)");
                return endFlight(ctx, flight, StepResult.pass());
            }
            default -> {
                return StepResult.pending();
            }
        }
        return StepResult.pending();
    }

    private static void next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
    }

    private static void probeServer(SelfTest.StepContext ctx, Flight flight) {
        if (ctx.tick() % 5 != 0) {
            return;
        }
        int id = flight.droneId;
        ctx.onServer(server -> flight.serverFlag.set(player(server).level().getEntity(id) instanceof QuadcopterEntity drone && drone.physicsModified()));
    }

    private static void hover(PilotSession session, @Nullable DroneSim sim, Flight flight) {
        if (sim == null) {
            return;
        }
        double y = session.comPosition(new Vector3d()).y;
        double vy = Double.isNaN(flight.lastY) ? 0.0 : (y - flight.lastY) * 20.0;
        flight.lastY = y;
        double throttle = sim.telemetry().hoverThrottleStick + (flight.targetY - y) * 0.12 - vy * 0.08;
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, (float) Math.clamp(throttle, 0.05, 0.85)), true, StabMode.ANGLE);
    }

    private static void startFlight(MinecraftServer server, Flight flight) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.GRAVITY_SCALE.defaultValue());
        int x = player.getBlockX() - 3;
        int z = player.getBlockZ() + 3;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos ground = new BlockPos(x, top - 1, z);
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult use = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(x + 0.5, top, z + 0.5), Direction.UP, ground, false)));
        if (!use.consumesAction()) {
            flight.failure = "Platzieren lieferte " + use;
            return;
        }
        List<QuadcopterEntity> drones = level.getEntitiesOfClass(QuadcopterEntity.class, new AABB(ground).inflate(3.0),
                d -> d.build().equals(DroneBuild.FREESTYLE) && d.pilot().isEmpty());
        if (drones.isEmpty()) {
            flight.failure = "keine Drohne nach dem Platzieren";
            return;
        }
        QuadcopterEntity drone = drones.getFirst();
        flight.droneId = drone.getId();
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemInHand(InteractionHand.MAIN_HAND, remote);
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_DIGITAL).map(ItemStack::new).orElse(ItemStack.EMPTY);
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            flight.failure = "FPV-Start verweigert: " + result;
        }
    }

    private static StepResult endFlight(SelfTest.StepContext ctx, Flight flight, StepResult result) {
        PilotSession session = PilotSession.INSTANCE;
        session.clearTestInput();
        if (session.isActive()) {
            session.requestStop();
        }
        int id = flight.droneId;
        ctx.onServer(server -> {
            WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.GRAVITY_SCALE.defaultValue());
            if (id >= 0 && server.overworld().getEntity(id) instanceof QuadcopterEntity drone) {
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                drone.discard();
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        });
        return result;
    }
}
