package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.event.ServerBus;
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
import de.lardtom.propwash.osd.FlightPathMarker;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdGrid;
import de.lardtom.propwash.osd.OsdProjection;
import de.lardtom.propwash.osd.OsdSampler;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.video.SpectateServer;
import de.lardtom.propwash.video.client.FisheyeSelfTests;
import de.lardtom.propwash.video.client.FpvLens;
import de.lardtom.propwash.video.client.SpectateSession;
import de.lardtom.propwash.video.client.VideoState;
import de.lardtom.propwash.video.net.ChannelListS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class FlightPathMarkerSelfTests {
    public static final String PREFIX = "osd: Flugbahn-Marker";
    public static final String FPV_STEP = PREFIX + " – Vorwärtsflug im FPV liegt unter der Bildmitte, Schweben und Videoverlust blenden ihn aus";
    public static final String SPECTATE_STEP = PREFIX + " – beim Zuschauen (F7) aus den gezeigten Posen der fremden Drohne";
    static final double FLIGHT_HEIGHT = 5.0;
    static final float FORWARD_PITCH = 0.2F;
    static final float BACKWARD_PITCH = -0.35F;
    static final double CRUISE_SPEED = 6.0;
    private static final double BACKWARD_SPEED = 4.0;
    private static final double BRAKE_GAIN = 0.12;
    private static final float BRAKE_LIMIT = 0.2F;
    private static final int ARM_TICKS = 20;
    private static final int SETTLE_TICKS = 40;
    private static final int MEASURE_TICKS = 40;
    private static final int TIMEOUT_TICKS = 400;
    private static final double SPECTATE_BLOCKS_PER_TICK = 0.45;
    private static final String PILOT_NAME = "Markerpilot";
    private static final String PHASE = "fpm_phase";
    private static final String SINCE = "fpm_since";
    private static final String FUTURE = "fpm_future";
    private static final String COUNT = "fpm_count";
    private static final String RESULT = "fpm_result";
    private static final String DRONE = "fpm_drone";
    private static volatile int droneId = -1;
    private static volatile double groundY;
    private static volatile @Nullable ItemStack previousHead;
    private static volatile @Nullable ItemStack previousHand;
    private static volatile int moverId = -1;
    private static volatile double moverDz;
    private static boolean moverInstalled;

    private FlightPathMarkerSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(FPV_STEP, 3000, FlightPathMarkerSelfTests::fpvStep);
        SelfTest.clientStep(SPECTATE_STEP, 1200, FlightPathMarkerSelfTests::spectateStep);
        if (FlightPathMarkerLanCheck.hostEnabled()) {
            SelfTest.clientStep(FlightPathMarkerLanCheck.HOST_STEP, FlightPathMarkerLanCheck.HOST_TICKS, FlightPathMarkerLanCheck::hostStep);
        }
    }

    public static void registerShowcase() {
        if (Screenshots.enabled()) {
            Screenshots.step("bildschirmfotos: Flugbahn-Marker – FPV geradlinig im Vorwärtsflug", "osd_flight_path_marker", 6,
                    new FlightScene(FORWARD_PITCH, true));
            Screenshots.step("bildschirmfotos: Flugbahn-Marker – FPV mit Fisheye im Vorwärtsflug", "osd_flight_path_marker_fisheye", 6,
                    FisheyeSelfTests.withFisheye(new FlightScene(FORWARD_PITCH, true)));
            Screenshots.step("bildschirmfotos: Flugbahn-Marker – rückwärts am Rand als Umriss", "osd_flight_path_marker_edge", 6,
                    new FlightScene(BACKWARD_PITCH, false));
        }
    }

    static void showMarker() {
        OsdConfig config = OsdClient.CONFIG.get().copy();
        config.slot(OsdElement.FLIGHT_PATH_MARKER).enabled = true;
        OsdHud.overrideConfig(config);
    }

    static void restoreOsd() {
        OsdHud.overrideConfig(null);
    }

    static boolean drawn() {
        return OsdHud.drewLastFrame(OsdElement.FLIGHT_PATH_MARKER);
    }

    static FlightPathMarker.State shown() {
        return OsdHud.renderer().flightPathState();
    }

    static OsdGrid grid(Minecraft mc) {
        return OsdHud.grid(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    static FlightPathMarker.@Nullable State predict(Minecraft mc, double vx, double vy, double vz) {
        OsdRenderer.View view = OsdHud.liveView(mc);
        if (view == null) {
            return null;
        }
        OsdGrid grid = grid(mc);
        OsdProjection projection = new OsdProjection();
        if (view.fisheyeDeg() > 0.0F) {
            projection.setupFisheye(view.rotation(), view.fisheyeDeg(), view.fisheyeBend(), grid.centerX(), grid.centerY(), grid.width());
        } else {
            projection.setup(view.rotation(), view.fovDeg(), grid.centerX(), grid.centerY(), grid.height());
        }
        float margin = grid.cellHeight() * 1.5F;
        return new FlightPathMarker().compute(vx, vy, vz, projection, grid.left() + margin, grid.top() + margin,
                grid.left() + grid.width() - margin, grid.top() + grid.height() - margin, new FlightPathMarker.State());
    }

    private static int increment(Map<String, Object> state, String key) {
        int value = (int) state.getOrDefault(key, 0) + 1;
        state.put(key, value);
        return value;
    }

    private static StepResult next(SelfTest.StepContext ctx, String phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        return StepResult.pending();
    }

    private static String phase(SelfTest.StepContext ctx, String initial) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(PHASE)) {
            state.put(PHASE, initial);
            state.put(SINCE, ctx.tick());
        }
        return (String) state.get(PHASE);
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().get(SINCE);
    }

    static @Nullable Telemetry telemetry() {
        DroneSim sim = PilotSession.INSTANCE.sim();
        return sim == null ? null : sim.telemetry();
    }

    static double height() {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        return drone == null ? 0.0 : drone.getY() - groundY;
    }

    static boolean flying() {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        return session.isFpv() && session.sim() != null && drone != null && drone.getId() == droneId;
    }

    static void hover() {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        Vector3d velocity = simVelocity();
        if (drone == null || !velocity.isFinite()) {
            fly(0.0F, 0.0F);
            return;
        }
        Vector3f forward = new Quaternionf(drone.orientation()).transform(new Vector3f(0.0F, 0.0F, -1.0F));
        double length = Math.sqrt(forward.x * forward.x + forward.z * forward.z);
        if (length < 1.0E-4) {
            fly(0.0F, 0.0F);
            return;
        }
        double fx = forward.x / length;
        double fz = forward.z / length;
        double ahead = velocity.x * fx + velocity.z * fz;
        double right = velocity.x * -fz + velocity.z * fx;
        fly((float) Math.clamp(-BRAKE_GAIN * ahead, -BRAKE_LIMIT, BRAKE_LIMIT), (float) Math.clamp(-BRAKE_GAIN * right, -BRAKE_LIMIT, BRAKE_LIMIT));
    }

    static void fly(float pitch) {
        fly(pitch, 0.0F);
    }

    static void fly(float pitch, float roll) {
        Telemetry t = telemetry();
        double hover = t == null ? 0.3 : Math.clamp(t.hoverThrottleStick, 0.15, 0.6);
        double vs = t == null ? 0.0 : t.verticalSpeed;
        double wanted = Math.clamp(1.2 * (FLIGHT_HEIGHT - height()), -3.0, 3.0);
        float throttle = (float) Math.clamp(hover + 0.06 * (wanted - vs) + (Math.abs(pitch) > 0.1F ? 0.02 : 0.0), 0.05, 0.9);
        PilotSession.INSTANCE.setTestInput(new StickPositions(roll, pitch, 0.0F, throttle), true, StabMode.ANGLE);
    }

    static void arm(int since) {
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), since >= ARM_TICKS / 2, StabMode.ANGLE);
    }

    static Vector3d simVelocity() {
        Vector3d out = new Vector3d(Double.NaN);
        DroneSim sim = PilotSession.INSTANCE.sim();
        if (sim != null) {
            sim.getVelocity(out);
        }
        return out;
    }

    static double forwardSpeed() {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        Vector3d velocity = simVelocity();
        if (drone == null || !velocity.isFinite()) {
            return Double.NaN;
        }
        Vector3f forward = new Quaternionf(drone.orientation()).transform(new Vector3f(0.0F, 0.0F, -1.0F));
        double horizontal = Math.sqrt(forward.x * forward.x + forward.z * forward.z);
        if (horizontal < 1.0E-4) {
            return Double.NaN;
        }
        return (velocity.x * forward.x + velocity.z * forward.z) / horizontal;
    }

    static double horizontalSpeed() {
        Vector3d velocity = simVelocity();
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }

    static void spawnAndFly(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        ServerPlayer player = players.getFirst();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        discardDrone(server);
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        Vec3 feet = player.position();
        groundY = feet.y;
        drone.snapTo(feet.x + 2.0, feet.y + 0.1, feet.z - 4.0, 180.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        drone.setCustomName(Component.literal("Flugbahn-Marker"));
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        droneId = drone.getId();
        if (previousHead == null) {
            previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        }
        if (previousHand == null) {
            previousHand = player.getMainHandItem().copy();
        }
        player.setItemSlot(EquipmentSlot.HEAD, boundGoggles(drone));
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    static ItemStack boundGoggles(@Nullable QuadcopterEntity drone) {
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        if (drone != null) {
            goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        }
        return goggles;
    }

    private static void discardDrone(MinecraftServer server) {
        int id = droneId;
        if (id < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof QuadcopterEntity drone) {
                SpectateServer.simulateTransmitter(drone, null);
                drone.discard();
            }
        }
        droneId = -1;
    }

    static void release(MinecraftServer server) {
        moverId = -1;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (!players.isEmpty()) {
            ServerPlayer player = players.getFirst();
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            ItemStack head = previousHead;
            if (head != null) {
                player.setItemSlot(EquipmentSlot.HEAD, head);
            }
            ItemStack hand = previousHand;
            if (hand != null) {
                player.setItemSlot(EquipmentSlot.MAINHAND, hand);
            }
        }
        discardDrone(server);
        SpectateServer.refreshSoon();
        previousHead = null;
        previousHand = null;
    }

    private static StepResult cleanup(SelfTest.StepContext ctx, StepResult result) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(RESULT)) {
            state.put(RESULT, result);
            PilotSession.INSTANCE.clearTestInput();
            SpectateSession.INSTANCE.stop(Minecraft.getInstance(), true);
            restoreOsd();
            state.put(FUTURE, ctx.onServer(FlightPathMarkerSelfTests::release));
            return next(ctx, "cleanup");
        }
        return StepResult.pending();
    }

    private static StepResult finishCleanup(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if ((future != null && !future.isDone()) || PilotSession.INSTANCE.isActive() || SpectateSession.INSTANCE.active()) {
            return since(ctx) > 200 ? StepResult.fail("Aufräumen hängt: FPV " + PilotSession.INSTANCE.isActive() + ", Zuschauen "
                    + SpectateSession.INSTANCE.active()) : StepResult.pending();
        }
        return (StepResult) state.get(RESULT);
    }

    private static StepResult fpvStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        Minecraft mc = Minecraft.getInstance();
        String phase = phase(ctx, "spawn");
        int since = since(ctx);
        if (!phase.equals("spawn") && !phase.equals("fpv") && !phase.equals("cleanup") && !flying()) {
            return cleanup(ctx, StepResult.fail("FPV-Sitzung endete in Phase " + phase));
        }
        switch (phase) {
            case "spawn" -> {
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                showMarker();
                state.put(FUTURE, ctx.onServer(FlightPathMarkerSelfTests::spawnAndFly));
                return next(ctx, "fpv");
            }
            case "fpv" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    return cleanup(ctx, StepResult.fail("Start: " + future.exceptionNow().getMessage()));
                }
                if (!future.isDone() || !flying() || !VideoState.INSTANCE.active()) {
                    return since > TIMEOUT_TICKS ? cleanup(ctx, StepResult.fail("keine FPV-Sitzung")) : StepResult.pending();
                }
                return next(ctx, "arm");
            }
            case "arm" -> {
                arm(since);
                return since >= ARM_TICKS ? next(ctx, "climb") : StepResult.pending();
            }
            case "climb" -> {
                hover();
                Telemetry t = telemetry();
                boolean settled = t != null && Math.abs(height() - FLIGHT_HEIGHT) < 0.4 && Math.abs(t.verticalSpeed) < 0.3 && horizontalSpeed() < 0.5;
                int calm = settled ? increment(state, COUNT) : 0;
                if (!settled) {
                    state.put(COUNT, 0);
                }
                if (calm >= SETTLE_TICKS) {
                    state.put(COUNT, 0);
                    state.put("hover_max", 0.0);
                    return next(ctx, "hover");
                }
                return since > TIMEOUT_TICKS ? cleanup(ctx, StepResult.fail(String.format(Locale.ROOT,
                        "Schwebeflug wird nicht ruhig: Höhe %.2f, Steigen %.2f, waagerecht %.2f m/s", height(), t == null ? Double.NaN : t.verticalSpeed,
                        horizontalSpeed()))) : StepResult.pending();
            }
            case "hover" -> {
                hover();
                if (drawn()) {
                    increment(state, COUNT);
                }
                OsdData data = OsdHud.sampler().data();
                double speed = data.hasVelocity() ? Math.sqrt(data.velocityX * data.velocityX + data.velocityY * data.velocityY
                        + data.velocityZ * data.velocityZ) : 0.0;
                state.merge("hover_max", speed, (a, b) -> Math.max((double) a, (double) b));
                if (since < MEASURE_TICKS) {
                    return StepResult.pending();
                }
                int shownTicks = (int) state.getOrDefault(COUNT, 0);
                double max = (double) state.get("hover_max");
                if (shownTicks > 0) {
                    return cleanup(ctx, StepResult.fail(String.format(Locale.ROOT, "Marker beim Schweben in %d von %d Ticks gezeichnet (OSD-Tempo bis %.2f m/s)",
                            shownTicks, MEASURE_TICKS, max)));
                }
                state.put("hover_log", String.format(Locale.ROOT, "Schweben: %d Ticks ohne Marker, OSD-Tempo bis %.2f m/s", MEASURE_TICKS, max));
                state.put(COUNT, 0);
                return next(ctx, "forward");
            }
            case "forward" -> {
                fly(FORWARD_PITCH);
                double forward = forwardSpeed();
                if (since >= 60 && forward >= CRUISE_SPEED) {
                    state.put(COUNT, 0);
                    state.put("sum_dy", 0.0);
                    state.put("sum_dx", 0.0);
                    state.put("max_error", 0.0);
                    state.put("edge", 0);
                    return next(ctx, "cruise");
                }
                return since > TIMEOUT_TICKS ? cleanup(ctx, StepResult.fail(String.format(Locale.ROOT,
                        "Vorwärtsflug erreicht %.1f m/s nicht: vorwärts %.2f m/s, waagerecht %.2f m/s, Höhe %.1f", CRUISE_SPEED, forward,
                        horizontalSpeed(), height()))) : StepResult.pending();
            }
            case "cruise" -> {
                fly(FORWARD_PITCH);
                if (drawn()) {
                    FlightPathMarker.State shown = shown();
                    OsdGrid grid = grid(mc);
                    increment(state, COUNT);
                    if (!shown.inView) {
                        increment(state, "edge");
                    }
                    state.merge("sum_dy", (double) (shown.y - grid.centerY()), (a, b) -> (double) a + (double) b);
                    state.merge("sum_dx", (double) Math.abs(shown.x - grid.centerX()), (a, b) -> (double) a + (double) b);
                    Vector3d velocity = simVelocity();
                    FlightPathMarker.State expected = predict(mc, velocity.x, velocity.y, velocity.z);
                    if (expected != null && expected.visible) {
                        double error = Math.hypot(expected.x - shown.x, expected.y - shown.y);
                        state.merge("max_error", error, (a, b) -> Math.max((double) a, (double) b));
                    }
                }
                if (since < MEASURE_TICKS) {
                    return StepResult.pending();
                }
                int count = (int) state.get(COUNT);
                OsdGrid grid = grid(mc);
                double dy = count == 0 ? Double.NaN : (double) state.get("sum_dy") / count;
                double dx = count == 0 ? Double.NaN : (double) state.get("sum_dx") / count;
                double error = (double) state.get("max_error");
                String numbers = String.format(Locale.ROOT, "%d von %d Ticks gezeichnet, %d am Rand, im Mittel %.1f Punkte unter und %.1f neben der Mitte "
                                + "(Bild %.0f × %.0f), größte Abweichung zur Sim-Geschwindigkeit %.1f Punkte; vorwärts %.1f m/s, Uptilt %.0f°",
                        count, MEASURE_TICKS, (int) state.get("edge"), dy, dx, grid.width(), grid.height(), error, forwardSpeed(),
                        PilotSession.INSTANCE.cameraUptiltDeg());
                if (count < MEASURE_TICKS * 9 / 10 || (int) state.get("edge") > 0) {
                    return cleanup(ctx, StepResult.fail("Marker im Vorwärtsflug nicht durchgehend im Bild: " + numbers));
                }
                if (!(dy > 2.0) || !(dx < grid.width() * 0.12)) {
                    return cleanup(ctx, StepResult.fail("Marker im Vorwärtsflug nicht unter der Mitte: " + numbers));
                }
                if (!(error < Math.max(8.0, grid.height() * 0.05))) {
                    return cleanup(ctx, StepResult.fail("Marker weicht von der Projektion der Geschwindigkeit ab: " + numbers));
                }
                state.put("cruise_log", numbers);
                state.put(COUNT, 0);
                LostSource lost = new LostSource();
                state.put("lost_source", lost);
                OsdHud.sampler().setOverride(lost);
                return next(ctx, "lost");
            }
            case "lost" -> {
                fly(FORWARD_PITCH);
                LostSource lost = (LostSource) state.get("lost_source");
                if (since < 5) {
                    return StepResult.pending();
                }
                if (since < 15) {
                    if (drawn()) {
                        increment(state, COUNT);
                    }
                    return StepResult.pending();
                }
                if (lost.videoLost) {
                    if ((int) state.get(COUNT) > 0) {
                        OsdHud.sampler().setOverride(null);
                        return cleanup(ctx, StepResult.fail("Marker bei Videoverlust in " + state.get(COUNT) + " von 10 Ticks gezeichnet"));
                    }
                    lost.videoLost = false;
                    state.put(SINCE, ctx.tick());
                    return StepResult.pending();
                }
                OsdHud.sampler().setOverride(null);
                if ((int) state.get(COUNT) < 9) {
                    return cleanup(ctx, StepResult.fail("Marker mit derselben Quelle ohne Videoverlust nur in " + state.get(COUNT) + " von 10 Ticks gezeichnet"));
                }
                ctx.log("osd: Flugbahn-Marker – " + state.get("hover_log") + "; Vorwärtsflug: " + state.get("cruise_log")
                        + "; Videoverlust: 10 Ticks ohne Marker, danach wieder da");
                return cleanup(ctx, StepResult.pass());
            }
            case "cleanup" -> {
                return finishCleanup(ctx);
            }
            default -> {
                return cleanup(ctx, StepResult.fail("unbekannte Phase " + phase));
            }
        }
    }

    private static synchronized void installMover() {
        if (!moverInstalled) {
            moverInstalled = true;
            ServerBus.TICK_END.register(FlightPathMarkerSelfTests::moveDrone);
        }
    }

    private static void moveDrone(MinecraftServer server) {
        int id = moverId;
        if (id < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                drone.setDeltaMovement(Vec3.ZERO);
                drone.snapTo(drone.getX(), drone.getY(), drone.getZ() + moverDz, drone.getYRot(), drone.getXRot());
                return;
            }
        }
    }

    static void spawnWatched(MinecraftServer server, Map<String, Object> state) {
        installMover();
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        discardDrone(server);
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        Vec3 eye = player.getEyePosition();
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setNoGravity(true);
        drone.snapTo(eye.x, eye.y + 4.0, eye.z + 6.0, 180.0F, 0.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        drone.setAttitude(new Quaternionf());
        droneId = drone.getId();
        state.put(DRONE, drone.getUUID());
        if (previousHead == null) {
            previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        }
        player.setItemSlot(EquipmentSlot.HEAD, boundGoggles(null));
        SpectateServer.simulateTransmitter(drone, PILOT_NAME);
        SpectateServer.refreshSoon();
    }

    private static StepResult spectateStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        Minecraft mc = Minecraft.getInstance();
        SpectateSession session = SpectateSession.INSTANCE;
        String phase = phase(ctx, "spawn");
        int since = since(ctx);
        switch (phase) {
            case "spawn" -> {
                if (PilotSession.INSTANCE.isActive()) {
                    PilotSession.INSTANCE.requestStop();
                    return since > 200 ? cleanup(ctx, StepResult.fail("alte Flugsitzung endet nicht")) : StepResult.pending();
                }
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                showMarker();
                state.put(FUTURE, ctx.onServer(server -> spawnWatched(server, state)));
                return next(ctx, "list");
            }
            case "list" -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    return cleanup(ctx, StepResult.fail("Aufbau: " + future.exceptionNow().getMessage()));
                }
                ChannelListS2C.Entry entry = state.get(DRONE) instanceof UUID id ? session.entry(id) : null;
                if (!future.isDone() || entry == null || SpectateSession.goggles(mc) == null) {
                    return since > 300 ? cleanup(ctx, StepResult.fail("Kanal der Drohne oder Brille fehlt auf dem Client")) : StepResult.pending();
                }
                if (!session.request(mc, entry)) {
                    return cleanup(ctx, StepResult.fail("Zuschauen abgelehnt: " + session.blocker(mc, entry)));
                }
                return next(ctx, "watch");
            }
            case "watch" -> {
                if (!session.watching() || session.drone() == null) {
                    return since > 300 ? cleanup(ctx, StepResult.fail("Zuschauen startet nicht: " + session.phase() + ", " + session.lastResult()))
                            : StepResult.pending();
                }
                moverDz = -SPECTATE_BLOCKS_PER_TICK;
                moverId = droneId;
                state.put(COUNT, 0);
                state.put("sum_error", 0.0);
                state.put("edge", 0);
                return next(ctx, "move");
            }
            case "move" -> {
                QuadcopterEntity drone = session.drone();
                if (!session.watching() || drone == null) {
                    return cleanup(ctx, StepResult.fail("Zuschauen endete während der Fahrt"));
                }
                if (since < 30) {
                    return StepResult.pending();
                }
                if (since == 30) {
                    state.put("from_pos", drone.position());
                    state.put("from_nanos", Util.getNanos());
                }
                OsdData data = OsdHud.sampler().data();
                if (!data.spectating) {
                    return cleanup(ctx, StepResult.fail("OSD zeigt beim Zuschauen nicht die fremde Drohne"));
                }
                if (drawn()) {
                    FlightPathMarker.State shown = shown();
                    increment(state, COUNT);
                    if (!shown.inView) {
                        increment(state, "edge");
                    }
                    FlightPathMarker.State expected = predict(mc, 0.0, 0.0, moverDz * 20.0);
                    if (expected != null && expected.visible) {
                        state.merge("sum_error", Math.hypot(expected.x - shown.x, expected.y - shown.y), (a, b) -> (double) a + (double) b);
                    }
                    state.put("last_dy", (double) (shown.y - grid(mc).centerY()));
                }
                if (data.hasVelocity()) {
                    state.merge("sum_speed", Math.sqrt(data.velocityX * data.velocityX + data.velocityY * data.velocityY + data.velocityZ * data.velocityZ),
                            (a, b) -> (double) a + (double) b);
                    increment(state, "speed_samples");
                }
                if (since < 30 + MEASURE_TICKS) {
                    return StepResult.pending();
                }
                int count = (int) state.get(COUNT);
                double error = count == 0 ? Double.NaN : (double) state.get("sum_error") / count;
                int samples = (int) state.getOrDefault("speed_samples", 0);
                double speed = samples == 0 ? Double.NaN : (double) state.get("sum_speed") / samples;
                double seconds = (Util.getNanos() - (long) state.get("from_nanos")) * 1.0E-9;
                double actual = drone.position().distanceTo((Vec3) state.get("from_pos")) / seconds;
                String numbers = String.format(Locale.ROOT, "%d von %d Ticks gezeichnet, %d am Rand, %.1f Punkte unter der Mitte, mittlere Abweichung "
                                + "zur Fahrtrichtung %.1f Punkte, OSD-Tempo im Mittel %.1f m/s, gezeigte Fahrt %.1f m/s in %.2f s (Server %.1f m/s je 20 Ticks), "
                                + "Uptilt %.0f°", count, MEASURE_TICKS, (int) state.get("edge"), (double) state.getOrDefault("last_dy", Double.NaN), error, speed,
                        actual, seconds, SPECTATE_BLOCKS_PER_TICK * 20.0, session.cameraUptiltDeg(drone));
                if (count < MEASURE_TICKS * 9 / 10 || (int) state.get("edge") > 0) {
                    return cleanup(ctx, StepResult.fail("Marker beim Zuschauen nicht durchgehend im Bild: " + numbers));
                }
                if (!(error < Math.max(8.0, grid(mc).height() * 0.05))) {
                    return cleanup(ctx, StepResult.fail("Marker beim Zuschauen nicht in Fahrtrichtung: " + numbers));
                }
                if (!(Math.abs(speed - actual) < Math.max(1.5, actual * 0.2))) {
                    return cleanup(ctx, StepResult.fail("Geschwindigkeit aus den gezeigten Posen passt nicht zur Fahrt: " + numbers));
                }
                state.put("move_log", numbers);
                moverId = -1;
                state.put(COUNT, 0);
                return next(ctx, "stop");
            }
            case "stop" -> {
                if (since < 30) {
                    return StepResult.pending();
                }
                if (drawn()) {
                    increment(state, COUNT);
                }
                if (since < 40) {
                    return StepResult.pending();
                }
                if ((int) state.get(COUNT) > 0) {
                    return cleanup(ctx, StepResult.fail("Marker bleibt nach dem Anhalten der fremden Drohne stehen"));
                }
                ctx.log("osd: Flugbahn-Marker beim Zuschauen – " + state.get("move_log") + "; angehalten: ohne Marker");
                return cleanup(ctx, StepResult.pass());
            }
            case "cleanup" -> {
                return finishCleanup(ctx);
            }
            default -> {
                return cleanup(ctx, StepResult.fail("unbekannte Phase " + phase));
            }
        }
    }

    private static final class LostSource implements OsdSampler.Source {
        private volatile boolean videoLost = true;

        @Override
        public boolean active() {
            return true;
        }

        @Override
        public void fill(OsdData d) {
            d.videoSystem = VideoSystem.DIGITAL;
            d.videoSignal = this.videoLost ? 0.0 : 1.0;
            d.videoLost = this.videoLost;
            d.velocityX = 0.0;
            d.velocityY = 0.0;
            d.velocityZ = 0.0;
            OsdRenderer.View view = OsdHud.liveView(Minecraft.getInstance());
            if (view != null) {
                Vector3f forward = view.rotation().transform(new Vector3f(0.0F, 0.0F, -1.0F), new Vector3f());
                d.velocityX = forward.x * 10.0;
                d.velocityY = forward.y * 10.0;
                d.velocityZ = forward.z * 10.0;
            }
        }
    }

    private static final class FlightScene implements Screenshots.Scene {
        private final float pitch;
        private final boolean inView;

        FlightScene(float pitch, boolean inView) {
            this.pitch = pitch;
            this.inView = inView;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            Minecraft mc = Minecraft.getInstance();
            String phase = phase(ctx, "spawn");
            int since = since(ctx);
            if (!phase.equals("spawn") && !phase.equals("fpv") && !flying()) {
                return StepResult.fail("FPV-Sitzung endete in Phase " + phase);
            }
            switch (phase) {
                case "spawn" -> {
                    if (mc.gui.screen() != null) {
                        mc.gui.setScreen(null);
                    }
                    showMarker();
                    state.put(FUTURE, ctx.onServer(FlightPathMarkerSelfTests::spawnAndFly));
                    return next(ctx, "fpv");
                }
                case "fpv" -> {
                    CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                    if (future.isCompletedExceptionally()) {
                        return StepResult.fail("Start: " + future.exceptionNow().getMessage());
                    }
                    if (!future.isDone() || !flying() || !VideoState.INSTANCE.active()) {
                        return since > TIMEOUT_TICKS ? StepResult.fail("keine FPV-Sitzung") : StepResult.pending();
                    }
                        return next(ctx, "arm");
                }
                case "arm" -> {
                    arm(since);
                    return since >= ARM_TICKS ? next(ctx, "climb") : StepResult.pending();
                }
                case "climb" -> {
                    hover();
                    boolean high = Math.abs(height() - FLIGHT_HEIGHT) < 0.5;
                    if (high && since >= 60) {
                        return next(ctx, "go");
                    }
                    return since > TIMEOUT_TICKS ? StepResult.fail(String.format(Locale.ROOT, "Höhe %.1f nicht erreicht", height())) : StepResult.pending();
                }
                case "go" -> {
                    fly(this.pitch);
                    double forward = forwardSpeed();
                    boolean fast = this.inView ? forward >= CRUISE_SPEED : forward <= -BACKWARD_SPEED;
                    FlightPathMarker.State shown = shown();
                    if (since >= 40 && fast && drawn() && shown.inView == this.inView && shown.alpha >= 1.0F) {
                        FpvLens lens = FpvLens.INSTANCE;
                        ctx.log(String.format(Locale.ROOT, "Flugbahn-Marker bei %.1f m/s vorwärts, %s bei %.0f/%.0f, Fisheye %s", forward,
                                shown.inView ? "im Bild" : "am Rand", shown.x, shown.y, lens.rendered() != null ? "an" : "aus"));
                        return StepResult.pass();
                    }
                    return since > TIMEOUT_TICKS ? StepResult.fail(String.format(Locale.ROOT, "Szene nicht erreicht: vorwärts %.2f m/s, gezeichnet %b, im Bild %b",
                            forward, drawn(), shown.inView)) : StepResult.pending();
                }
                default -> {
                    return StepResult.fail("unbekannte Phase " + phase);
                }
            }
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            if (flying()) {
                fly(this.pitch);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            PilotSession.INSTANCE.clearTestInput();
            restoreOsd();
            ctx.onServer(FlightPathMarkerSelfTests::release);
        }
    }
}
