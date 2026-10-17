package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;

final class CrashOutcomeSelfTests {
    static final String PREFIX = "drone: Absturz-Statistik";
    private static final int FROM_DX = 2;
    private static final int TO_DX = 32;
    private static final int HALF_WIDTH = 10;
    private static final int HEIGHT = 8;
    private static final int WALL_DX = 30;
    private static final int WALL_HEIGHT = 6;
    private static final double START_DX = 5.5;
    private static final double WALL_START = 2.6;
    private static final double PLACED = 0.3;
    private static final int SYNC_TICKS = 60;
    private static final int REST_TICKS = 20;
    private static final int SETTLE_TICKS = 240;
    private static final int RUN_TICKS = 340;
    private static final double SIDE = 60.0;
    private static final double UPSIDE_DOWN = 120.0;
    private static final String STAGE = "stage";
    private static final String MARK = "mark";
    private static final String FUTURE = "future";
    private static final String RUN = "run";
    private static final String CALM = "calm";
    private static final String TILTS = "tilts";
    private static final String RANDOM = "random";
    private static final String CRASHES = "crashes";
    private static final String THROW = "throw";
    private static final double UPSIDE_DOWN_REST = 150.0;
    private static final double TURTLE_UPRIGHT = 25.0;
    private static final int TURTLE_TRIES = 4;
    private static final int REPAIR_TICKS = 10;
    private static final int TURTLE_TICKS = 300;
    private static final String TOUCH = "touch";
    private static final int ARM_TICKS = 60;
    private static final int CONTACT_TICKS = 40;
    private static final int REACTION_TICKS = 4;
    private static final int DISARM_TICKS = 12;
    private static final AtomicReference<int[]> ARENA = new AtomicReference<>();
    private static final Map<BlockPos, BlockState> ORIGINAL = new LinkedHashMap<>();

    enum Kind {
        GENTLE("sanft abgesetzt", 8, 1.0, 0.0),
        FLAT("flach in den Boden, 8-14 m/s", 10, 0.0, 0.0),
        STEEP("steil in den Boden, 6-12 m/s", 10, 0.0, 0.2),
        WALL("gegen die Mauer, 6-12 m/s", 10, 0.0, 0.2),
        TUMBLE("taumelnd aus 1-2,5 m", 10, 0.0, 0.2),
        INVERTED("kopfüber aufgeschlagen", 8, 0.0, 0.5);

        final String label;
        final int runs;
        final double minUpright;
        final double minUpsideDown;

        Kind(String label, int runs, double minUpright, double minUpsideDown) {
            this.label = label;
            this.runs = runs;
            this.minUpright = minUpright;
            this.minUpsideDown = minUpsideDown;
        }
    }

    record Throw(double heading, double pitch, double roll, double[] attitude, double vx, double vy, double vz, double wx, double wy,
                 double wz, double height, double dz, double throttle) {
    }

    private CrashOutcomeSelfTests() {
    }

    static void registerPiloted(IntSupplier droneId) {
        SelfTest.clientStep(PREFIX + " – Testfeld mit Mauer", 100, CrashOutcomeSelfTests::arenaStep);
        for (Kind k : Kind.values()) {
            SelfTest.clientStep(PREFIX + " " + k.label, k.runs * RUN_TICKS, ctx -> runStep(ctx, droneId.getAsInt(), k));
        }
        SelfTest.clientStep(PREFIX + " – kopfüber liegende Drohne mit Turtle umdrehen", 1600, ctx -> turtleStep(ctx, droneId.getAsInt()));
        SelfTest.clientStep(PREFIX + " – Testfeld abbauen", 60, CrashOutcomeSelfTests::cleanupStep);
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

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        ORIGINAL.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlockAndUpdate(pos, state);
    }

    private static void build(ServerPlayer player) {
        ServerLevel level = player.level();
        int[] arena = {player.getBlockX(), player.getBlockY(), player.getBlockZ()};
        ORIGINAL.clear();
        for (int dx = FROM_DX; dx <= TO_DX; dx++) {
            for (int dz = -HALF_WIDTH; dz <= HALF_WIDTH; dz++) {
                BlockPos floor = new BlockPos(arena[0] + dx, arena[1] - 1, arena[2] + dz);
                place(level, floor, Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy < HEIGHT; dy++) {
                    boolean wall = dx >= WALL_DX && dy < WALL_HEIGHT;
                    place(level, floor.above(1 + dy), wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        ARENA.set(arena);
    }

    static StepResult arenaStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            input(PilotSession.INSTANCE, 0.0, false);
            ctx.state().put(FUTURE, ctx.onServer(server -> build(serverPlayer(server))));
            return StepResult.pending();
        }
        return awaitServer(ctx, "Testfeld bauen");
    }

    static StepResult cleanupStep(SelfTest.StepContext ctx) {
        if (ARENA.get() == null) {
            return StepResult.pass();
        }
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerLevel level = serverPlayer(server).level();
                List<Map.Entry<BlockPos, BlockState>> entries = List.copyOf(ORIGINAL.entrySet());
                for (int i = entries.size() - 1; i >= 0; i--) {
                    level.setBlockAndUpdate(entries.get(i).getKey(), entries.get(i).getValue());
                }
                ORIGINAL.clear();
                ARENA.set(null);
            }));
            return StepResult.pending();
        }
        return awaitServer(ctx, "Testfeld abbauen");
    }

    private static void input(PilotSession session, double throttle, boolean arm) {
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, (float) throttle), arm, StabMode.ACRO);
    }

    static double uniform(SplittableRandom r, double lo, double hi) {
        return lo + (hi - lo) * r.nextDouble();
    }

    static Throw draw(Kind k, SplittableRandom r) {
        double east = Math.PI / 2.0;
        switch (k) {
            case GENTLE -> {
                double sink = uniform(r, 0.2, 0.8);
                return new Throw(east + uniform(r, -1.0, 1.0), Math.toRadians(uniform(r, -8.0, 8.0)), Math.toRadians(uniform(r, -8.0, 8.0)),
                        null, uniform(r, -0.3, 0.3), -sink, uniform(r, -0.3, 0.3), rate(r, 15.0), rate(r, 15.0), rate(r, 15.0), 0.03,
                        uniform(r, -4.0, 4.0), 0.9);
            }
            case FLAT, STEEP -> {
                double speed = k == Kind.FLAT ? uniform(r, 8.0, 14.0) : uniform(r, 6.0, 12.0);
                double path = Math.toRadians(k == Kind.FLAT ? uniform(r, 10.0, 30.0) : uniform(r, 45.0, 80.0));
                double heading = east + Math.toRadians(uniform(r, -10.0, 10.0));
                return new Throw(heading + Math.toRadians(uniform(r, -15.0, 15.0)), path + Math.toRadians(uniform(r, 0.0, 30.0)),
                        Math.toRadians(uniform(r, -20.0, 20.0)), null, speed * Math.cos(path) * Math.sin(heading), -speed * Math.sin(path),
                        speed * Math.cos(path) * Math.cos(heading), rate(r, 60.0), rate(r, 60.0), rate(r, 60.0), 0.6, uniform(r, -4.0, 4.0), uniform(r, 0.2, 1.0));
            }
            case WALL -> {
                double speed = uniform(r, 6.0, 12.0);
                double heading = east + Math.toRadians(uniform(r, -35.0, 35.0));
                return new Throw(heading, Math.toRadians(uniform(r, 15.0, 50.0)), Math.toRadians(uniform(r, -15.0, 15.0)), null,
                        speed * Math.sin(heading), uniform(r, -1.0, 1.0), speed * Math.cos(heading), rate(r, 60.0), rate(r, 60.0),
                        rate(r, 60.0), uniform(r, 0.8, 2.2), uniform(r, -4.0, 4.0), uniform(r, 1.0, 1.5));
            }
            case TUMBLE -> {
                double u1 = r.nextDouble();
                double u2 = r.nextDouble() * 2.0 * Math.PI;
                double u3 = r.nextDouble() * 2.0 * Math.PI;
                double a = Math.sqrt(1.0 - u1);
                double b = Math.sqrt(u1);
                double[] q = {a * Math.sin(u2), a * Math.cos(u2), b * Math.sin(u3), b * Math.cos(u3)};
                double ax = r.nextDouble() * 2.0 - 1.0;
                double ay = r.nextDouble() * 2.0 - 1.0;
                double az = r.nextDouble() * 2.0 - 1.0;
                double n = Math.sqrt(ax * ax + ay * ay + az * az) + 1.0e-9;
                double w = uniform(r, 10.0, 25.0);
                double speed = uniform(r, 2.0, 8.0);
                double heading = east + uniform(r, -0.5, 0.5);
                return new Throw(0.0, 0.0, 0.0, q, speed * Math.sin(heading), uniform(r, -2.0, 1.0), speed * Math.cos(heading),
                        w * ax / n, w * ay / n, w * az / n, uniform(r, 1.0, 2.5), uniform(r, -4.0, 4.0), 0.0);
            }
            default -> {
                double lean = 180.0 - uniform(r, 0.0, 30.0);
                double forward = uniform(r, 0.0, 4.0);
                double heading = east + uniform(r, -0.5, 0.5);
                return new Throw(heading, Math.toRadians(uniform(r, -1.0, 1.0) * (180.0 - lean)), Math.PI + Math.toRadians(uniform(r, -1.0,
                        1.0) * (180.0 - lean)), null, forward * Math.sin(heading), -uniform(r, 1.0, 5.0), forward * Math.cos(heading),
                        rate(r, 90.0), rate(r, 90.0), rate(r, 90.0), 0.4, uniform(r, -4.0, 4.0), uniform(r, 0.2, 0.6));
            }
        }
    }

    private static double rate(SplittableRandom r, double deg) {
        return Math.toRadians(uniform(r, -deg, deg));
    }

    static Quaternionf attitude(Throw t) {
        if (t.attitude != null) {
            return new Quaternionf((float) t.attitude[1], (float) t.attitude[2], (float) t.attitude[3], (float) t.attitude[0]).normalize();
        }
        return new Quaternionf().rotationY((float) t.heading).rotateX((float) t.pitch).rotateZ((float) t.roll);
    }

    private static Vec3 start(int[] arena, Kind k, Throw t) {
        double dx = k == Kind.WALL ? WALL_DX - WALL_START : START_DX;
        return new Vec3(arena[0] + dx, arena[1] + (k == Kind.GENTLE ? t.height : t.height + 0.35), arena[2] + 0.5 + t.dz);
    }

    private static void launch(DroneSim sim, Throw t) {
        AirframeSpec p = sim.params();
        Quaternionf q = attitude(t);
        SimSnapshot s = new SimSnapshot();
        sim.copyStateTo(s);
        s.qw = q.w;
        s.qx = q.x;
        s.qy = q.y;
        s.qz = q.z;
        s.vx = t.vx;
        s.vy = t.vy;
        s.vz = t.vz;
        Vector3d l = new Vector3d(p.inertiaXx * t.wx, p.inertiaYy * t.wy, p.inertiaZz * t.wz);
        new Quaterniond(q.x, q.y, q.z, q.w).transform(l);
        s.lx = l.x;
        s.ly = l.y;
        s.lz = l.z;
        s.sleeping = false;
        sim.copyStateFrom(s);
    }

    private static boolean near(QuadcopterEntity drone, Vec3 target) {
        return Math.abs(drone.getX() - target.x) < PLACED && Math.abs(drone.getZ() - target.z) < PLACED
                && Math.abs(drone.getY() - target.y) < PLACED;
    }

    @SuppressWarnings("unchecked")
    static StepResult runStep(SelfTest.StepContext ctx, int droneId, Kind k) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        int[] arena = ARENA.get();
        if (drone == null || sim == null || arena == null) {
            return StepResult.fail("keine aktive Sitzung oder kein Testfeld");
        }
        SplittableRandom random = (SplittableRandom) ctx.state().computeIfAbsent(RANDOM, key -> new SplittableRandom(20261015L + k.ordinal()));
        List<Double> tilts = (List<Double>) ctx.state().computeIfAbsent(TILTS, key -> new ArrayList<Double>());
        int run = (int) ctx.state().getOrDefault(RUN, 0);
        if (run >= k.runs) {
            input(session, 0.0, false);
            return summary(ctx, k, tilts);
        }
        Throw t = (Throw) ctx.state().computeIfAbsent("throw" + run, key -> draw(k, random));
        boolean powered = k != Kind.TUMBLE;
        Vec3 start = start(arena, k, t);
        Vec3 ground = new Vec3(arena[0] + START_DX - 2.0, arena[1], arena[2] + 0.5 + t.dz);
        double hover = sim.telemetry().hoverThrottleStick;
        switch (stage(ctx)) {
            case 0 -> {
                input(session, 0.0, false);
                Vec3 first = powered ? ground : start;
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    QuadcopterEntity serverDrone = serverDrone(serverPlayer(server), droneId);
                    serverDrone.setWear(PartWear.PRISTINE);
                    serverDrone.resetTo(first, new Quaternionf());
                }));
                stage(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                input(session, 0.0, false);
                StepResult server = awaitServer(ctx, "Drohne versetzen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (!near(drone, powered ? ground : start)) {
                    return since(ctx) > SYNC_TICKS ? StepResult.fail("Drohne nicht versetzt (Lauf " + run + ")") : StepResult.pending();
                }
                stage(ctx, powered ? 2 : 4);
                return StepResult.pending();
            }
            case 2 -> {
                input(session, 0.0, since(ctx) >= 3);
                if (sim.telemetry().armed) {
                    ctx.state().put(FUTURE, ctx.onServer(server -> serverDrone(serverPlayer(server), droneId).resetTo(start, new Quaternionf())));
                    stage(ctx, 3);
                    return StepResult.pending();
                }
                return since(ctx) > ARM_TICKS ? StepResult.fail("nicht scharf geschaltet, Sperren=" + sim.telemetry().armBlockers)
                        : StepResult.pending();
            }
            case 3 -> {
                input(session, hover, true);
                StepResult server = awaitServer(ctx, "Drohne in die Luft setzen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (!near(drone, start)) {
                    return since(ctx) > SYNC_TICKS ? StepResult.fail("Drohne nicht in die Luft gesetzt (Lauf " + run + ")") : StepResult.pending();
                }
                if (!sim.telemetry().armed) {
                    return StepResult.fail("beim Versetzen entschärft: " + sim.telemetry().lastDisarmReason);
                }
                stage(ctx, 4);
                return StepResult.pending();
            }
            case 4 -> {
                launch(sim, t);
                input(session, t.throttle * hover, powered);
                ctx.state().put(CALM, 0);
                ctx.state().put(CRASHES, sim.telemetry().crashCount);
                ctx.state().put(TOUCH, -1);
                stage(ctx, 5);
                return StepResult.pending();
            }
            default -> {
                Telemetry tm = sim.telemetry();
                int touch = (int) ctx.state().get(TOUCH);
                if (touch < 0 && (tm.grounded || tm.crashCount > (int) ctx.state().get(CRASHES) || since(ctx) > CONTACT_TICKS)) {
                    touch = since(ctx);
                    ctx.state().put(TOUCH, touch);
                }
                int after = touch < 0 ? -1 : since(ctx) - touch;
                boolean throttle = touch < 0 || after < REACTION_TICKS;
                boolean armed = powered && (touch < 0 || after < DISARM_TICKS);
                input(session, throttle ? t.throttle * hover : 0.0, armed);
                int calm = !armed && (tm.sleeping || (tm.grounded && tm.speed < 0.02)) ? (int) ctx.state().getOrDefault(CALM, 0) + 1 : 0;
                ctx.state().put(CALM, calm);
                if (calm < REST_TICKS && since(ctx) < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                tilts.add(tm.tiltDeg);
                ctx.log(String.format(Locale.ROOT, "Lauf %d: %.1f° %s", run, tm.tiltDeg, calm >= REST_TICKS ? "" : "(noch in Bewegung)"));
                ctx.state().put(RUN, run + 1);
                stage(ctx, 0);
                return StepResult.pending();
            }
        }
    }

    static StepResult turtleStep(SelfTest.StepContext ctx, int droneId) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        int[] arena = ARENA.get();
        if (drone == null || sim == null || arena == null) {
            return StepResult.fail("keine aktive Sitzung oder kein Testfeld");
        }
        SplittableRandom random = (SplittableRandom) ctx.state().computeIfAbsent(RANDOM, key -> new SplittableRandom(20261016L));
        Telemetry tm = sim.telemetry();
        int tries = (int) ctx.state().getOrDefault(RUN, 0);
        switch (stage(ctx)) {
            case 0 -> {
                input(session, 0.0, false);
                session.setTestTurtle(false);
                boolean resting = tm.sleeping || (tm.grounded && tm.speed < 0.02);
                if (resting && tm.tiltDeg > UPSIDE_DOWN_REST) {
                    ctx.log(String.format(Locale.ROOT, "liegt kopfüber (%.1f°) nach %d Würfen", tm.tiltDeg, tries));
                    ctx.state().put(FUTURE, ctx.onServer(server -> serverDrone(serverPlayer(server), droneId).setWear(PartWear.PRISTINE)));
                    stage(ctx, 3);
                    return StepResult.pending();
                }
                if (tries >= TURTLE_TRIES) {
                    return StepResult.fail("keine Kopflage nach " + tries + " Würfen");
                }
                Throw t = draw(Kind.INVERTED, random);
                ctx.state().put(THROW, t);
                Vec3 start = start(arena, Kind.INVERTED, t);
                ctx.state().put(FUTURE, ctx.onServer(server -> serverDrone(serverPlayer(server), droneId).resetTo(start, new Quaternionf())));
                ctx.state().put(RUN, tries + 1);
                stage(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                input(session, 0.0, false);
                Throw t = (Throw) ctx.state().get(THROW);
                StepResult server = awaitServer(ctx, "Drohne versetzen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (!near(drone, start(arena, Kind.INVERTED, t))) {
                    return since(ctx) > SYNC_TICKS ? StepResult.fail("Drohne nicht versetzt") : StepResult.pending();
                }
                launch(sim, t);
                stage(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                input(session, 0.0, false);
                boolean resting = tm.sleeping || (tm.grounded && tm.speed < 0.02);
                if (!resting && since(ctx) < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                stage(ctx, since(ctx) > REST_TICKS ? 0 : 2);
                return StepResult.pending();
            }
            case 3 -> {
                input(session, 0.0, false);
                StepResult server = awaitServer(ctx, "Drohne reparieren");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (since(ctx) >= REPAIR_TICKS) {
                    stage(ctx, 4);
                }
                return StepResult.pending();
            }
            case 4 -> {
                session.setTestTurtle(true);
                input(session, 0.0, since(ctx) >= 10);
                if (tm.armed && tm.turtle) {
                    stage(ctx, 5);
                    return StepResult.pending();
                }
                return since(ctx) > ARM_TICKS ? StepResult.fail("Turtle nicht scharf, Sperren=" + tm.armBlockers) : StepResult.pending();
            }
            case 5 -> {
                session.setTestInput(new StickPositions(1.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ACRO);
                if (tm.tiltDeg < TURTLE_UPRIGHT) {
                    ctx.log(String.format(Locale.ROOT, "Turtle hat sie nach %d Ticks umgedreht (%.1f°)", since(ctx), tm.tiltDeg));
                    session.setTestTurtle(false);
                    stage(ctx, 6);
                    return StepResult.pending();
                }
                return since(ctx) > TURTLE_TICKS ? StepResult.fail(String.format(Locale.ROOT, "Turtle dreht sie nicht um: %.1f°", tm.tiltDeg))
                        : StepResult.pending();
            }
            default -> {
                input(session, 0.0, false);
                if (tm.armed) {
                    return since(ctx) > ARM_TICKS ? StepResult.fail("nach Turtle noch scharf") : StepResult.pending();
                }
                session.setTestTurtle(null);
                return StepResult.pass();
            }
        }
    }

    private static StepResult summary(SelfTest.StepContext ctx, Kind k, List<Double> tilts) {
        int up = 0;
        int side = 0;
        int down = 0;
        for (double tilt : tilts) {
            if (tilt > UPSIDE_DOWN) {
                down++;
            } else if (tilt > SIDE) {
                side++;
            } else {
                up++;
            }
        }
        String text = String.format(Locale.ROOT, "%s: %d von %d aufrecht, %d auf der Seite, %d auf dem Kopf", k.label, up, tilts.size(), side,
                down);
        ctx.log(text);
        if (up < k.minUpright * tilts.size()) {
            return StepResult.fail(text + " (aufrecht verlangt " + Math.round(100.0 * k.minUpright) + " %)");
        }
        if (down < k.minUpsideDown * tilts.size()) {
            return StepResult.fail(text + " (auf dem Kopf verlangt " + Math.round(100.0 * k.minUpsideDown) + " %)");
        }
        return StepResult.pass();
    }
}
