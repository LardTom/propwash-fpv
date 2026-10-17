package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;

final class LandingSelfTests {
    private static final int FROM_DX = 3;
    private static final int TO_DX = 13;
    private static final int HALF_WIDTH = 4;
    private static final int HEIGHT = 6;
    private static final int SLAB_DX = 9;
    private static final double PLACED = 0.3;
    private static final double ON_SURFACE = 0.03;
    private static final double UPRIGHT = 30.0;
    private static final double LEVEL = 5.0;
    private static final int SYNC_TICKS = 60;
    private static final int ARM_TICKS = 40;
    private static final int TOUCH_TICKS = 80;
    private static final int IDLE_TICKS = 20;
    private static final int SETTLE_TICKS = 120;
    private static final String STAGE = "stage";
    private static final String MARK = "mark";
    private static final String FUTURE = "future";
    private static final String TRACE = "trace";
    private static final String CYCLE = "cycle";
    private static final String PREP = "prep";
    private static final int FLIP_CYCLES = 6;
    private static final int REST_TICKS = 100;
    private static final double FLIPPED = 150.0;
    private static final Quaternionf UPSIDE_DOWN = new Quaternionf().rotationZ((float) Math.PI);
    private static final AtomicReference<int[]> ARENA = new AtomicReference<>();
    private static final Map<BlockPos, BlockState> ORIGINAL = new LinkedHashMap<>();

    enum Surface {
        STONE, GRASS, SLAB_EDGE
    }

    record Case(String name, Surface surface, double dx, double dz, double tiltDeg, double tiltAxisDeg, double vx, double vy, double vz) {
    }

    static final List<Case> CASES = List.of(
            new Case("senkrecht auf Stein", Surface.STONE, 5.5, -3.0, 6.0, 30.0, 0.0, -0.6, 0.0),
            new Case("mit Fahrt auf Stein", Surface.STONE, 4.5, -3.0, 10.0, 200.0, 2.5, -0.5, 0.4),
            new Case("mit Fahrt in kurzes Gras", Surface.GRASS, 4.5, 3.0, 8.0, 120.0, 1.5, -0.7, -0.3),
            new Case("auf der Kante einer Stufe", Surface.SLAB_EDGE, SLAB_DX, 0.5, 5.0, 300.0, 1.0, -0.6, 0.0));

    private LandingSelfTests() {
    }

    static void registerPiloted(IntSupplier droneId) {
        SelfTest.clientStep("drone: Landungen – Testfeld mit Stein, Gras und Stufe", 100, LandingSelfTests::arenaStep);
        for (Case c : CASES) {
            SelfTest.clientStep("drone: Landung " + c.name() + " – bleibt aufrecht", 500, ctx -> landStep(ctx, droneId.getAsInt(), c));
        }
        if (Screenshots.enabled()) {
            Screenshots.step("bildschirmfotos: Landung – Drohne an der Kante einer Stufe aufgesetzt", "landing_slab_edge", 15, new RestScene());
        }
        SelfTest.clientStep("drone: Landung nach Kopflage – Rücksetzen erreicht die Simulation", 2400,
                ctx -> afterFlipStep(ctx, droneId.getAsInt(), CASES.getFirst()));
        SelfTest.clientStep("drone: Landungen – Testfeld abbauen", 60, LandingSelfTests::cleanupStep);
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

    private static void input(PilotSession session, float throttle, boolean arm) {
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), arm, StabMode.ACRO);
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        ORIGINAL.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlockAndUpdate(pos, state);
    }

    private static void build(ServerPlayer player) {
        ServerLevel level = player.level();
        int[] arena = {player.getBlockX(), player.getBlockY(), player.getBlockZ()};
        for (int dx = FROM_DX; dx <= TO_DX; dx++) {
            for (int dz = -HALF_WIDTH; dz <= HALF_WIDTH; dz++) {
                BlockPos floor = new BlockPos(arena[0] + dx, arena[1] - 1, arena[2] + dz);
                if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) {
                    throw new IllegalStateException("kein fester Boden unter dem Testfeld bei " + floor.toShortString());
                }
            }
        }
        ORIGINAL.clear();
        for (int dx = FROM_DX; dx <= TO_DX; dx++) {
            for (int dz = -HALF_WIDTH; dz <= HALF_WIDTH; dz++) {
                for (int dy = 0; dy < HEIGHT; dy++) {
                    place(level, new BlockPos(arena[0] + dx, arena[1] + dy, arena[2] + dz), Blocks.AIR.defaultBlockState());
                }
                boolean stone = dz < 0 && dx < SLAB_DX - 1;
                boolean grass = dz > 1 && dx < SLAB_DX - 1;
                BlockPos floor = new BlockPos(arena[0] + dx, arena[1] - 1, arena[2] + dz);
                if (stone) {
                    place(level, floor, Blocks.STONE.defaultBlockState());
                } else if (grass) {
                    place(level, floor, Blocks.GRASS_BLOCK.defaultBlockState());
                    place(level, floor.above(), Blocks.SHORT_GRASS.defaultBlockState());
                }
                if (dx >= SLAB_DX && dx <= SLAB_DX + 2 && Math.abs(dz) <= 1) {
                    place(level, floor.above(), Blocks.STONE_SLAB.defaultBlockState());
                }
            }
        }
        ARENA.set(arena);
    }

    static StepResult arenaStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            input(PilotSession.INSTANCE, 0.0F, false);
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

    private static double surfaceY(int[] arena, Case c) {
        return arena[1] + (c.surface() == Surface.SLAB_EDGE ? 0.5 : 0.0);
    }

    private static Vec3 groundPoint(int[] arena, Case c) {
        return new Vec3(arena[0] + SLAB_DX - 1.5, arena[1], arena[2] + c.dz());
    }

    private static Vec3 airPoint(int[] arena, Case c) {
        return new Vec3(arena[0] + c.dx(), surfaceY(arena, c) + 0.12, arena[2] + c.dz());
    }

    private static boolean near(QuadcopterEntity drone, Vec3 target) {
        return Math.abs(drone.getX() - target.x) < PLACED && Math.abs(drone.getZ() - target.z) < PLACED
                && Math.abs(drone.getY() - target.y) < PLACED;
    }

    private static double simDistance(DroneSim sim, Vec3 target) {
        Vector3d p = new Vector3d();
        sim.getPosition(p);
        return Math.sqrt((p.x - target.x) * (p.x - target.x) + (p.y + sim.params().hitboxFootOffset - target.y) * (p.y
                + sim.params().hitboxFootOffset - target.y) + (p.z - target.z) * (p.z - target.z));
    }

    private static boolean arrived(QuadcopterEntity drone, DroneSim sim, Vec3 target) {
        return near(drone, target) && simDistance(sim, target) < PLACED && sim.telemetry().tiltDeg < LEVEL;
    }

    private static String synced(QuadcopterEntity drone, DroneSim sim, Vec3 target) {
        return String.format(Locale.ROOT, "Drohne %.2f m und Simulation %.2f m vom Ziel, Simulation %.1f°, Rücksetzung %d",
                drone.position().distanceTo(target), simDistance(sim, target), sim.telemetry().tiltDeg, drone.resetEpoch());
    }

    private static double lowest(QuadcopterEntity drone, DroneSim sim) {
        return drone.getY() - sim.params().hitboxFootOffset + sim.lowestPointOffset();
    }

    private static String where(QuadcopterEntity drone, DroneSim sim, double surface) {
        Telemetry t = sim.telemetry();
        return String.format(Locale.ROOT, "tiefster Punkt %.3f m über der Fläche, Box %.3f m, Neigung %.1f°, %.2f m/s, am Boden %s, ruhend %s, scharf %s",
                lowest(drone, sim) - surface, drone.getY() - surface, t.tiltDeg, t.speed, t.grounded, t.sleeping, t.armed);
    }

    private static void tilt(DroneSim sim, Case c) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        double axis = Math.toRadians(c.tiltAxisDeg());
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
        sim.copyStateFrom(snapshot);
    }

    private static void note(SelfTest.StepContext ctx, String where, QuadcopterEntity drone, DroneSim sim) {
        Telemetry t = sim.telemetry();
        String entry = String.format(Locale.ROOT, "%s: Simulation %.1f°, ruhend %s, scharf %s, Rücksetzung %d", where, t.tiltDeg, t.sleeping,
                t.armed, drone.resetEpoch());
        Object before = ctx.state().get(TRACE);
        ctx.state().put(TRACE, before == null ? entry : before + "; " + entry);
    }

    static StepResult landStep(SelfTest.StepContext ctx, int droneId, Case c) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        int[] arena = ARENA.get();
        if (drone == null || sim == null || arena == null) {
            return StepResult.fail("keine aktive Sitzung oder kein Testfeld");
        }
        double surface = surfaceY(arena, c);
        switch (stage(ctx)) {
            case 0 -> {
                input(session, 0.0F, false);
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    QuadcopterEntity serverDrone = serverDrone(serverPlayer(server), droneId);
                    serverDrone.setWear(PartWear.PRISTINE);
                    serverDrone.resetTo(groundPoint(arena, c), new Quaternionf());
                }));
                stage(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                input(session, 0.0F, false);
                StepResult server = awaitServer(ctx, "Drohne auf den Boden setzen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (!arrived(drone, sim, groundPoint(arena, c)) || !sim.wear().isNew()) {
                    return since(ctx) > SYNC_TICKS ? StepResult.fail("Rücksetzen auf den Boden kommt nicht in der Simulation an: "
                            + synced(drone, sim, groundPoint(arena, c)) + ", " + where(drone, sim, arena[1])) : StepResult.pending();
                }
                note(ctx, "am Boden", drone, sim);
                stage(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                input(session, 0.0F, since(ctx) >= 3);
                if (sim.telemetry().armed) {
                    ctx.state().put(FUTURE, ctx.onServer(server -> serverDrone(serverPlayer(server), droneId)
                            .resetTo(airPoint(arena, c), new Quaternionf())));
                    stage(ctx, 3);
                    return StepResult.pending();
                }
                return since(ctx) > ARM_TICKS ? StepResult.fail("nicht scharf geschaltet, Sperren=" + sim.telemetry().armBlockers)
                        : StepResult.pending();
            }
            case 3 -> {
                input(session, (float) sim.telemetry().hoverThrottleStick, true);
                StepResult server = awaitServer(ctx, "Drohne über die Fläche setzen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (!arrived(drone, sim, airPoint(arena, c))) {
                    return since(ctx) > SYNC_TICKS ? StepResult.fail("Rücksetzen über die Fläche kommt nicht in der Simulation an: "
                            + synced(drone, sim, airPoint(arena, c)) + ", " + where(drone, sim, surface)) : StepResult.pending();
                }
                if (!sim.telemetry().armed) {
                    return StepResult.fail("beim Versetzen entschärft: " + sim.telemetry().lastDisarmReason);
                }
                note(ctx, "über der Fläche", drone, sim);
                tilt(sim, c);
                stage(ctx, 4);
                return StepResult.pending();
            }
            case 4 -> {
                input(session, (float) (0.9 * sim.telemetry().hoverThrottleStick), true);
                if (!sim.telemetry().grounded) {
                    return since(ctx) > TOUCH_TICKS ? StepResult.fail("setzt nicht auf: " + where(drone, sim, surface)) : StepResult.pending();
                }
                stage(ctx, 5);
                return StepResult.pending();
            }
            case 5 -> {
                boolean idle = since(ctx) < IDLE_TICKS;
                input(session, 0.0F, idle);
                if (idle) {
                    return StepResult.pending();
                }
                stage(ctx, 6);
                return StepResult.pending();
            }
            default -> {
                input(session, 0.0F, false);
                Telemetry t = sim.telemetry();
                boolean resting = t.sleeping || (t.grounded && t.speed < 0.02 && since(ctx) > 40);
                if (!resting) {
                    return since(ctx) > SETTLE_TICKS ? StepResult.fail("kommt nicht zur Ruhe: " + where(drone, sim, surface))
                            : StepResult.pending();
                }
                String state = where(drone, sim, surface) + "; " + ctx.state().getOrDefault(TRACE, "");
                if (t.tiltDeg > UPRIGHT) {
                    return StepResult.fail("liegt nicht aufrecht: " + state);
                }
                if (Math.abs(lowest(drone, sim) - surface) > ON_SURFACE) {
                    return StepResult.fail("steht nicht auf der Fläche: " + state);
                }
                ctx.log("gelandet " + c.name() + ": " + state);
                return StepResult.pass();
            }
        }
    }

    private static Vec3 flipPoint(int[] arena) {
        return new Vec3(arena[0] + 4.5, arena[1] + 0.15, arena[2] - 1.0);
    }

    static StepResult afterFlipStep(SelfTest.StepContext ctx, int droneId, Case c) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        int[] arena = ARENA.get();
        if (drone == null || sim == null || arena == null) {
            return StepResult.fail("keine aktive Sitzung oder kein Testfeld");
        }
        Map<String, Object> state = ctx.state();
        int cycle = (int) state.getOrDefault(CYCLE, 0);
        if (cycle >= FLIP_CYCLES) {
            return StepResult.pass();
        }
        if ((boolean) state.getOrDefault(PREP, true)) {
            StepResult prep = flipPrep(ctx, droneId, drone, sim, arena, cycle);
            if (prep.status() != StepResult.Status.PASS) {
                return prep;
            }
            state.put(PREP, false);
            state.remove(TRACE);
            stage(ctx, 0);
            return StepResult.pending();
        }
        StepResult landed = landStep(ctx, droneId, c);
        if (landed.status() == StepResult.Status.FAIL) {
            return StepResult.fail("Durchgang " + (cycle + 1) + " von " + FLIP_CYCLES + ": " + landed.message());
        }
        if (landed.status() == StepResult.Status.PASS) {
            state.put(CYCLE, cycle + 1);
            state.put(PREP, true);
            stage(ctx, 0);
        }
        return StepResult.pending();
    }

    private static StepResult flipPrep(SelfTest.StepContext ctx, int droneId, QuadcopterEntity drone, DroneSim sim, int[] arena, int cycle) {
        input(PilotSession.INSTANCE, 0.0F, false);
        Telemetry t = sim.telemetry();
        switch (stage(ctx)) {
            case 0 -> {
                ctx.state().put(FUTURE, ctx.onServer(server -> serverDrone(serverPlayer(server), droneId).resetTo(flipPoint(arena), UPSIDE_DOWN)));
                stage(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = awaitServer(ctx, "Drohne kopfüber ablegen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                Vec3 target = flipPoint(arena);
                boolean placed = Math.abs(drone.getX() - target.x) < PLACED && Math.abs(drone.getZ() - target.z) < PLACED;
                if (!placed || t.tiltDeg < FLIPPED || !t.grounded || t.speed > 0.05) {
                    return since(ctx) > REST_TICKS ? StepResult.fail("Durchgang " + (cycle + 1) + ": liegt nicht ruhig auf dem Kopf: "
                            + where(drone, sim, arena[1])) : StepResult.pending();
                }
                stage(ctx, 2);
                return StepResult.pending();
            }
            default -> {
                if (t.tiltDeg < FLIPPED) {
                    return StepResult.fail("Durchgang " + (cycle + 1) + ": kippt vor dem Rücksetzen zurück: " + where(drone, sim, arena[1]));
                }
                return since(ctx) >= (cycle % 3) * 10 ? StepResult.pass() : StepResult.pending();
            }
        }
    }

    private static final class RestScene implements Screenshots.Scene {
        private static final String MOVED = "moved";
        private static final double VIEW_BACK = 0.9;
        private static final double VIEW_SIDE = 1.6;
        private static final double MAX_LOOK_ERROR = 5.0;
        private static final AtomicReference<double[]> HOME = new AtomicReference<>();

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            PilotSession session = PilotSession.INSTANCE;
            QuadcopterEntity drone = session.drone();
            int[] arena = ARENA.get();
            if (drone == null || arena == null || mc.player == null) {
                return StepResult.fail("keine Sitzung für das Bildschirmfoto");
            }
            input(session, 0.0F, false);
            StepResult resized = Screenshots.resizeWindow(ctx);
            if (resized.status() != StepResult.Status.PASS) {
                return resized;
            }
            Screenshots.hideMarker(true);
            Vec3 view = new Vec3(drone.getX() + VIEW_BACK, arena[1], drone.getZ() + VIEW_SIDE);
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
                return ctx.tick() > SYNC_TICKS ? StepResult.fail(String.format(Locale.ROOT,
                        "Blick nicht auf der Drohne (%.1f°, Spieler %s)", error, there ? "neben der Drohne" : "nicht versetzt"))
                        : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            input(PilotSession.INSTANCE, 0.0F, false);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
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
