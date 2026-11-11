package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class CovCrashRestSelfTests {
    public static final String GROUP = "drone: Absturz ohne Disarm";
    private static final double FIELD_DX = 5.5;
    private static final double FIELD_DZ = -2.5;
    private static final double DROP_HEIGHT = 0.6;
    private static final double PLACED = 0.3;
    private static final double LEVEL = 5.0;
    private static final double LATCH_THROTTLE = 0.35;
    private static final int SYNC_TICKS = 60;
    private static final int ARM_TICKS = 40;
    private static final int LATCH_TICKS = 2;
    private static final int TOUCH_TICKS = 80;
    private static final int MEASURE_TICKS = 100;
    private static final int SETTLED_TICKS = 20;
    private static final int LATE_TICKS = 40;
    private static final double MAX_TRAVEL = 1.0;
    private static final double MAX_LATE_THRUST = 0.2;
    private static final double MAX_LATE_ITERM = 50.0;
    private static final int TWITCH_TICKS = 10;
    private static final double THRUST_SHARE = 0.3;
    private static final int MAX_THRUST_TICKS = 5;
    private static final double MAX_EXTRA_TRAVEL = 2.5;
    private static final double MAX_EXTRA_HEIGHT = 0.5;
    private static final String STAGE = "stage";
    private static final String MARK = "mark";
    private static final String FUTURE = "future";
    private static final String ORIGIN = "origin";
    private static final String SETTLED = "settled";
    private static final String TRAVEL = "travel";
    private static final String THRUST = "thrust";
    private static final String ITERM = "iterm";
    private static final String SPEED = "speed";
    private static final String IMPACTS = "impacts";
    private static final String HEIGHT = "height";
    private static final String BASE = "base";
    private static final String PUSHED = "pushed";
    private static final String WOUND = "wound";
    private static final AtomicReference<int[]> FIELD = new AtomicReference<>();
    private static final AtomicReference<DroneBuild> ORIGINAL_BUILD = new AtomicReference<>();
    private static final Map<String, double[]> PASSIVE = new ConcurrentHashMap<>();

    record Case(String name, String file, StabMode mode, double tiltDeg, double axisDeg, double vx, double vy, double vz, DroneBuild build) {
        Case(String name, String file, StabMode mode, double tiltDeg, double axisDeg, double vx, double vy, double vz) {
            this(name, file, mode, tiltDeg, axisDeg, vx, vy, vz, null);
        }
    }

    static final List<Case> CASES = List.of(
            new Case("Acro, 75° schräg auf Stein", "acro_tilted", StabMode.ACRO, 75.0, 45.0, 0.5, -2.0, 0.0),
            new Case("Angle, auf dem Rücken auf Stein", "angle_back", StabMode.ANGLE, 180.0, 45.0, 0.5, -2.0, 0.0));

    static final List<Case> SLIDES = List.of(
            new Case("Cinewhoop, Acro, 15° rutschend mit 12 m/s", "cinewhoop_skid", StabMode.ACRO, 15.0, 0.0, 12.0, -1.0, 0.0,
                    DroneBuild.CINEWHOOP),
            new Case("Freestyle, Angle, 135° mit 8 m/s vor und 2,4 m/s ab", "angle_bounce", StabMode.ANGLE, 135.0, 45.0, 8.0, -2.4, 0.0,
                    DroneBuild.FREESTYLE),
            new Case("Freestyle, Acro, 90° mit 8 m/s vor und 4,8 m/s ab", "acro_bounce", StabMode.ACRO, 90.0, 45.0, 8.0, -4.8, 0.0,
                    DroneBuild.FREESTYLE));

    private CovCrashRestSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + " – Testfeld mit Stein, Gras und Stufe", 300, LandingSelfTests::arenaStep);
        for (Case c : CASES) {
            SelfTest.clientStep(GROUP + " – " + c.name() + ", Gas 0 mit Airmode: kein Schub, kein Wegfahren", 600, ctx -> runStep(ctx, c));
            if (Screenshots.enabled()) {
                Screenshots.step("bildschirmfotos: " + GROUP + " – " + c.name() + ", nach 5 s scharf in Ruhe", "crash_rest_" + c.file(), 10,
                        new RestScene(c.mode()));
            }
        }
        for (Case c : SLIDES) {
            SelfTest.clientStep(GROUP + " – " + c.name() + ", Vergleich: unscharf ab dem Aufprall", 600, ctx -> runStep(ctx, c, true));
            SelfTest.clientStep(GROUP + " – " + c.name() + ", Gas 0 mit Airmode: kein Schub, nicht weiter oder höher als unscharf", 600,
                    ctx -> runStep(ctx, c, false));
        }
        SelfTest.clientStep(GROUP + " – Bauart zurücksetzen", 200, CovCrashRestSelfTests::restoreStep);
        SelfTest.clientStep(GROUP + " – Testfeld abbauen", 200, LandingSelfTests::cleanupStep);
    }

    private static ServerPlayer serverPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static QuadcopterEntity serverDrone(ServerPlayer player, int id) {
        if (!(player.level().getEntity(id) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Drohne auf dem Server verschwunden");
        }
        return drone;
    }

    private static int stage(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(STAGE, 0);
    }

    private static void stage(SelfTest.StepContext ctx, int next) {
        ctx.state().put(STAGE, next);
        ctx.state().put(MARK, ctx.tick());
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(MARK, 0);
    }

    private static StepResult awaitServer(SelfTest.StepContext ctx, String what) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return StepResult.fail(what + ": kein Serverauftrag");
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow().getMessage());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    private static void input(PilotSession session, double throttle, boolean arm, StabMode mode) {
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, (float) throttle), arm, mode);
    }

    private static Vec3 groundPoint(int[] field) {
        return new Vec3(field[0] + FIELD_DX, field[1], field[2] + FIELD_DZ);
    }

    private static Vec3 airPoint(int[] field) {
        return new Vec3(field[0] + FIELD_DX, field[1] + DROP_HEIGHT, field[2] + FIELD_DZ);
    }

    private static Vector3d position(DroneSim sim) {
        Vector3d p = new Vector3d();
        sim.getPosition(p);
        return p;
    }

    private static boolean arrived(QuadcopterEntity drone, DroneSim sim, Vec3 target) {
        Vector3d p = position(sim);
        double foot = sim.params().hitboxFootOffset;
        return Math.abs(drone.getX() - target.x) < PLACED && Math.abs(drone.getZ() - target.z) < PLACED
                && Math.abs(drone.getY() - target.y) < PLACED && Math.abs(p.x - target.x) < PLACED && Math.abs(p.z - target.z) < PLACED
                && Math.abs(p.y + foot - target.y) < PLACED && sim.telemetry().tiltDeg < LEVEL;
    }

    private static void launch(DroneSim sim, Case c) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        double axis = Math.toRadians(c.axisDeg());
        Quaternionf q = new Quaternionf((float) snapshot.qx, (float) snapshot.qy, (float) snapshot.qz, (float) snapshot.qw);
        Quaternionf lean = new Quaternionf().rotationAxis((float) Math.toRadians(c.tiltDeg()), (float) Math.cos(axis), 0.0F, (float) Math.sin(axis));
        lean.mul(q, q);
        snapshot.qw = q.w;
        snapshot.qx = q.x;
        snapshot.qy = q.y;
        snapshot.qz = q.z;
        snapshot.vx = c.vx();
        snapshot.vy = c.vy();
        snapshot.vz = c.vz();
        snapshot.lx = 0.0;
        snapshot.ly = 0.0;
        snapshot.lz = 0.0;
        snapshot.sleeping = false;
        snapshot.groundedFrames = 0;
        sim.copyStateFrom(snapshot);
    }

    private static double iterm(Telemetry t) {
        return Math.max(Math.abs(t.iTerm[0]), Math.max(Math.abs(t.iTerm[1]), Math.abs(t.iTerm[2])));
    }

    private static boolean built(QuadcopterEntity drone, DroneSim sim, Case c) {
        return c.build() == null || (drone.build().equals(c.build()) && sim.params().buildKey.equals(AirframeSpec.of(c.build()).buildKey));
    }

    static StepResult restoreStep(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        DroneBuild original = ORIGINAL_BUILD.get();
        if (original == null || drone == null) {
            return StepResult.pass();
        }
        input(session, 0.0, false, StabMode.ACRO);
        int droneId = drone.getId();
        if (!ctx.state().containsKey(FUTURE)) {
            ctx.state().put(FUTURE, ctx.onServer(server -> serverDrone(serverPlayer(server), droneId).setBuild(original)));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Bauart zurücksetzen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        if (!drone.build().equals(original)) {
            return ctx.tick() > SYNC_TICKS ? StepResult.fail("Bauart kommt nicht zurück") : StepResult.pending();
        }
        ORIGINAL_BUILD.set(null);
        return StepResult.pass();
    }

    static StepResult runStep(SelfTest.StepContext ctx, Case c) {
        return runStep(ctx, c, false);
    }

    static StepResult runStep(SelfTest.StepContext ctx, Case c, boolean passive) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        if (drone == null || sim == null) {
            return StepResult.fail("keine aktive Sitzung");
        }
        int droneId = drone.getId();
        Map<String, Object> state = ctx.state();
        switch (stage(ctx)) {
            case 0 -> {
                input(session, 0.0, false, c.mode());
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = serverPlayer(server);
                    int[] field = {player.getBlockX(), player.getBlockY(), player.getBlockZ()};
                    FIELD.set(field);
                    QuadcopterEntity serverDrone = serverDrone(player, droneId);
                    if (c.build() != null && !serverDrone.build().equals(c.build())) {
                        ORIGINAL_BUILD.compareAndSet(null, serverDrone.build());
                        serverDrone.setBuild(c.build());
                    }
                    serverDrone.setWear(PartWear.PRISTINE);
                    serverDrone.resetTo(groundPoint(field), new Quaternionf());
                }));
                stage(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                input(session, 0.0, false, c.mode());
                StepResult server = awaitServer(ctx, "Drohne auf den Stein setzen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (!arrived(drone, sim, groundPoint(FIELD.get())) || !built(drone, sim, c)) {
                    return since(ctx) > SYNC_TICKS ? StepResult.fail("Rücksetzen auf den Stein kommt nicht in der Simulation an") : StepResult.pending();
                }
                stage(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                input(session, 0.0, since(ctx) >= 3, c.mode());
                if (sim.telemetry().armed) {
                    int[] field = FIELD.get();
                    state.put(FUTURE, ctx.onServer(server -> serverDrone(serverPlayer(server), droneId).resetTo(airPoint(field), new Quaternionf())));
                    stage(ctx, 3);
                    return StepResult.pending();
                }
                return since(ctx) > ARM_TICKS ? StepResult.fail("nicht scharf geschaltet, Sperren=" + sim.telemetry().armBlockers) : StepResult.pending();
            }
            case 3 -> {
                input(session, sim.telemetry().hoverThrottleStick, true, c.mode());
                StepResult server = awaitServer(ctx, "Drohne über den Stein setzen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (!arrived(drone, sim, airPoint(FIELD.get()))) {
                    return since(ctx) > SYNC_TICKS ? StepResult.fail("Rücksetzen über den Stein kommt nicht in der Simulation an") : StepResult.pending();
                }
                if (!sim.telemetry().armed) {
                    return StepResult.fail("beim Versetzen entschärft: " + sim.telemetry().lastDisarmReason);
                }
                launch(sim, c);
                state.put(IMPACTS, sim.telemetry().impactCount);
                stage(ctx, 4);
                return StepResult.pending();
            }
            case 4 -> {
                boolean latch = since(ctx) < LATCH_TICKS;
                input(session, latch ? Math.max(LATCH_THROTTLE, sim.telemetry().hoverThrottleStick) : 0.0, true, c.mode());
                Telemetry t = sim.telemetry();
                if (latch || !(t.grounded || t.impactCount > (int) state.get(IMPACTS))) {
                    return since(ctx) > TOUCH_TICKS ? StepResult.fail("schlägt nicht auf") : StepResult.pending();
                }
                state.put(TRAVEL, 0.0);
                state.put(THRUST, 0.0);
                state.put(ITERM, 0.0);
                state.put(SPEED, 0.0);
                state.put(HEIGHT, 0.0);
                state.put(PUSHED, 0);
                state.put(WOUND, 0.0);
                state.put(BASE, position(sim).y);
                stage(ctx, 5);
                return StepResult.pending();
            }
            default -> {
                input(session, 0.0, !passive, c.mode());
                Telemetry t = sim.telemetry();
                int after = since(ctx);
                Vector3d p = position(sim);
                state.put(HEIGHT, Math.max((double) state.get(HEIGHT), p.y - (double) state.get(BASE)));
                if (after > TWITCH_TICKS) {
                    state.put(WOUND, Math.max((double) state.get(WOUND), iterm(t)));
                    if (t.thrustToWeight > THRUST_SHARE) {
                        state.put(PUSHED, (int) state.get(PUSHED) + 1);
                    }
                }
                if (after == SETTLED_TICKS) {
                    state.put(SETTLED, new double[]{p.x, p.z});
                }
                if (after > SETTLED_TICKS) {
                    double[] settled = (double[]) state.get(SETTLED);
                    state.put(TRAVEL, Math.max((double) state.get(TRAVEL), Math.hypot(p.x - settled[0], p.z - settled[1])));
                    state.put(SPEED, Math.max((double) state.get(SPEED), t.speed));
                }
                if (after > MEASURE_TICKS - LATE_TICKS) {
                    state.put(THRUST, (double) state.get(THRUST) + t.thrustToWeight / LATE_TICKS);
                    state.put(ITERM, Math.max((double) state.get(ITERM), iterm(t)));
                }
                if (after < MEASURE_TICKS) {
                    return StepResult.pending();
                }
                double travel = (double) state.get(TRAVEL);
                double thrust = (double) state.get(THRUST);
                double i = (double) state.get(ITERM);
                double height = (double) state.get(HEIGHT);
                String line = String.format(Locale.ROOT, "%s: Weg nach 1 s %.2f Blöcke, schnellstens %.1f m/s, Schub/Gewicht in den letzten 2 s %.2f, "
                                + "I-Anteil bis %.0f ‰, Neigung %.0f°, scharf %s", c.name(), travel, (double) state.get(SPEED), thrust, i, t.tiltDeg,
                        t.armed);
                if (c.build() != null) {
                    return slideResult(ctx, c, passive, t, travel, height, (int) state.get(PUSHED), thrust, (double) state.get(WOUND), line);
                }
                if (!t.armed) {
                    return StepResult.fail("nicht mehr scharf (" + t.lastDisarmReason + "): " + line);
                }
                if (travel > MAX_TRAVEL || thrust > MAX_LATE_THRUST || i > MAX_LATE_ITERM) {
                    return StepResult.fail(line);
                }
                ctx.log(line);
                return StepResult.pass();
            }
        }
    }

    private static StepResult slideResult(SelfTest.StepContext ctx, Case c, boolean passive, Telemetry t, double travel, double height, int pushed,
                                          double thrust, double i, String line) {
        String full = String.format(Locale.ROOT, "%s, höchstens %.2f m über dem Aufprall, ab 0,5 s %d Ticks mit Schub über %.1f × Gewicht und "
                + "I-Anteil bis %.0f ‰", line, height, pushed, THRUST_SHARE, i);
        if (passive) {
            PASSIVE.put(c.file(), new double[]{travel, height});
            ctx.log(full);
            return StepResult.pass();
        }
        double[] reference = PASSIVE.get(c.file());
        if (reference == null) {
            return StepResult.fail("kein Vergleich unscharf: " + full);
        }
        String compared = String.format(Locale.ROOT, "%s; unscharf %.2f Blöcke und %.2f m", full, reference[0], reference[1]);
        if (!t.armed) {
            return StepResult.fail("nicht mehr scharf (" + t.lastDisarmReason + "): " + compared);
        }
        if (pushed > MAX_THRUST_TICKS || thrust > MAX_LATE_THRUST || i > MAX_LATE_ITERM || travel > reference[0] + MAX_EXTRA_TRAVEL
                || height > reference[1] + MAX_EXTRA_HEIGHT) {
            return StepResult.fail(compared);
        }
        ctx.log(compared);
        return StepResult.pass();
    }

    private static final class RestScene implements Screenshots.Scene {
        private static final String MOVED = "moved";
        private static final double VIEW_BACK = 0.9;
        private static final double VIEW_SIDE = 1.6;
        private static final double MAX_LOOK_ERROR = 5.0;
        private static final AtomicReference<double[]> HOME = new AtomicReference<>();
        private final StabMode mode;

        RestScene(StabMode mode) {
            this.mode = mode;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            PilotSession session = PilotSession.INSTANCE;
            QuadcopterEntity drone = session.drone();
            int[] field = FIELD.get();
            if (drone == null || field == null || mc.player == null) {
                return StepResult.fail("keine Sitzung für das Bildschirmfoto");
            }
            input(session, 0.0, true, this.mode);
            StepResult resized = Screenshots.resizeWindow(ctx);
            if (resized.status() != StepResult.Status.PASS) {
                return resized;
            }
            Screenshots.hideMarker(true);
            Vec3 view = new Vec3(drone.getX() + VIEW_BACK, field[1], drone.getZ() + VIEW_SIDE);
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(MOVED)) {
                state.put(MOVED, ctx.onServer(server -> {
                    ServerPlayer player = serverPlayer(server);
                    HOME.set(new double[]{player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()});
                    player.teleportTo(player.level(), view.x, view.y, view.z, Set.of(), 150.0F, 31.0F, true);
                }));
                return StepResult.pending();
            }
            CompletableFuture<?> moved = (CompletableFuture<?>) state.get(MOVED);
            if (moved.isCompletedExceptionally()) {
                return StepResult.fail("Spieler nicht neben die Drohne gesetzt: " + moved.exceptionNow().getMessage());
            }
            boolean there = moved.isDone() && mc.player.position().distanceTo(view) < 0.2;
            Vec3 eye = mc.player.getEyePosition();
            Vec3 wanted = drone.position().subtract(eye).normalize();
            double error = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, wanted.dot(mc.player.getLookAngle())))));
            if (!there || error > MAX_LOOK_ERROR) {
                return ctx.tick() > SYNC_TICKS ? StepResult.fail(String.format(Locale.ROOT, "Blick nicht auf der Drohne (%.1f°, Spieler %s)", error,
                        there ? "neben der Drohne" : "nicht versetzt")) : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            input(PilotSession.INSTANCE, 0.0, true, this.mode);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            input(PilotSession.INSTANCE, 0.0, false, this.mode);
            double[] home = HOME.getAndSet(null);
            if (home != null) {
                ctx.onServer(server -> {
                    ServerPlayer player = serverPlayer(server);
                    player.teleportTo(player.level(), home[0], home[1], home[2], Set.of(), (float) home[3], (float) home[4], true);
                });
            }
            Screenshots.hideMarker(false);
            Screenshots.restoreWindow(ctx);
        }
    }
}
