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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;

final class CrashFallSelfTests {
    private static final int WALL_DX = 8;
    private static final int WALL_HALF_WIDTH = 3;
    private static final int WALL_HEIGHT = 5;
    private static final int CLEAR_FROM_DX = 3;
    private static final int CLEAR_TO_DX = 13;
    private static final int CLEAR_HALF_WIDTH = 4;
    private static final int CLEAR_HEIGHT = 8;
    private static final double LAUNCH_DX = 5.5;
    private static final double LAUNCH_HEIGHT = 2.0;
    private static final double DROP_HEIGHT = 3.0;
    private static final double CRASH_SPEED = 15.0;
    private static final double SLIDE_SPEED = 4.0;
    private static final double LEDGE_INSET = 0.35;
    private static final double PROBE = 0.05;
    private static final double ON_GROUND = 0.05;
    private static final double SUPPORTED = 0.01;
    private static final double PLACED = 0.3;
    private static final int SYNC_TICKS = 60;
    private static final int ARM_TICKS = 40;
    private static final int CRASH_TICKS = 40;
    private static final int FALL_TICKS = 50;
    private static final int LEAVE_TICKS = 40;
    private static final int START_FALLING_TICKS = 3;
    private static final int SETTLE_TICKS = 15;
    private static final String STAGE = "stage";
    private static final String MARK = "mark";
    private static final String FUTURE = "future";
    private static final String COUNT = "count";
    private static final String SAMPLE = "sample";
    private static final String SAMPLES = "samples";
    private static final String START = "start";
    private static final String FALLING = "falling";
    private static final Quaternionf FACING_WALL = new Quaternionf().rotationY((float) (-Math.PI / 2.0));
    private static final AtomicReference<int[]> ARENA = new AtomicReference<>();

    private CrashFallSelfTests() {
    }

    static void registerPiloted(IntSupplier droneId) {
        SelfTest.clientStep("drone: Absturz an einer Mauer in der Luft – Drohne fällt zu Boden", 400,
                ctx -> wallCrashStep(ctx, droneId.getAsInt()));
        if (Screenshots.enabled()) {
            Screenshots.step("bildschirmfotos: Drohne nach dem Absturz an der Mauer am Boden", "crash_wall_fallen", SETTLE_TICKS,
                    new FallenScene());
        }
        SelfTest.clientStep("drone: über die Mauerkrone gerutscht – fällt statt in der Luft zu hängen", 400,
                ctx -> ledgeStep(ctx, droneId.getAsInt()));
        SelfTest.clientStep("drone: Mauer des Absturztests abbauen", 60, CrashFallSelfTests::cleanupStep);
    }

    static void registerUnpiloted(IntSupplier droneId) {
        SelfTest.clientStep("drone: ohne Pilot fällt die versetzte Drohne sofort", 200, ctx -> unpilotedStep(ctx, droneId.getAsInt()));
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
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), arm, StabMode.ANGLE);
    }

    private static void push(DroneSim sim, double vx, double vy, double vz) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        snapshot.vx = vx;
        snapshot.vy = vy;
        snapshot.vz = vz;
        snapshot.sleeping = false;
        sim.copyStateFrom(snapshot);
    }

    private static boolean near(QuadcopterEntity drone, Vec3 target) {
        return Math.abs(drone.getX() - target.x) < PLACED && Math.abs(drone.getZ() - target.z) < PLACED
                && Math.abs(drone.getY() - target.y) < PLACED;
    }

    private static Vec3 launchPoint(int[] arena, double height) {
        return new Vec3(arena[0] + LAUNCH_DX, arena[1] + height, arena[2] + 0.5);
    }

    private static Vec3 ledgePoint(int[] arena) {
        return new Vec3(arena[0] + WALL_DX + LEDGE_INSET, arena[1] + WALL_HEIGHT, arena[2] + 0.5);
    }

    private static void fill(ServerLevel level, int[] arena, boolean wall) {
        for (int dx = CLEAR_FROM_DX; dx <= CLEAR_TO_DX; dx++) {
            for (int dz = -CLEAR_HALF_WIDTH; dz <= CLEAR_HALF_WIDTH; dz++) {
                for (int dy = 0; dy < CLEAR_HEIGHT; dy++) {
                    boolean stone = wall && dx == WALL_DX && Math.abs(dz) <= WALL_HALF_WIDTH && dy < WALL_HEIGHT;
                    BlockPos pos = new BlockPos(arena[0] + dx, arena[1] + dy, arena[2] + dz);
                    level.setBlockAndUpdate(pos, stone ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static int[] buildArena(ServerPlayer player) {
        ServerLevel level = player.level();
        int[] arena = {player.getBlockX(), player.getBlockY(), player.getBlockZ()};
        for (int dx = CLEAR_FROM_DX; dx <= CLEAR_TO_DX; dx++) {
            for (int dz = -CLEAR_HALF_WIDTH; dz <= CLEAR_HALF_WIDTH; dz++) {
                BlockPos floor = new BlockPos(arena[0] + dx, arena[1] - 1, arena[2] + dz);
                if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) {
                    throw new IllegalStateException("kein fester Boden unter dem Testfeld bei " + floor.toShortString());
                }
            }
        }
        fill(level, arena, true);
        return arena;
    }

    private static String where(QuadcopterEntity drone, int[] arena, DroneSim sim) {
        Telemetry t = sim.telemetry();
        return String.format(Locale.ROOT, "Füße %.3f m über dem Boden, Lücke darunter %.3f m, %.2f m/s, am Boden %s, ruhend %s, scharf %s",
                drone.getY() - arena[1], drone.supportGap(PROBE), t.speed, t.grounded, t.sleeping, t.armed);
    }

    private static boolean landed(QuadcopterEntity drone, int[] arena, DroneSim sim) {
        double lowest = drone.getY() - sim.params().hitboxFootOffset + sim.lowestPointOffset();
        double lift = Math.max(0.0, drone.getY() - lowest);
        return lowest - arena[1] < ON_GROUND && drone.supportGap(PROBE) - lift < SUPPORTED
                && (sim.telemetry().grounded || sim.telemetry().sleeping);
    }

    static StepResult wallCrashStep(SelfTest.StepContext ctx, int droneId) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        if (drone == null || sim == null) {
            return StepResult.fail("keine aktive Sitzung");
        }
        Map<String, Object> state = ctx.state();
        switch (stage(ctx)) {
            case 0 -> {
                input(session, 0.0F, false);
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = serverPlayer(server);
                    QuadcopterEntity serverDrone = serverDrone(player, droneId);
                    int[] arena = buildArena(player);
                    ARENA.set(arena);
                    serverDrone.setWear(PartWear.PRISTINE);
                    serverDrone.resetTo(launchPoint(arena, 0.0), FACING_WALL);
                }));
                stage(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                input(session, 0.0F, false);
                StepResult server = awaitServer(ctx, "Testfeld");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                int[] arena = ARENA.get();
                boolean placed = near(drone, launchPoint(arena, 0.0)) && drone.wear().isNew() && sim.wear().isNew();
                if (!placed) {
                    return since(ctx) > SYNC_TICKS ? StepResult.fail("Drohne nicht vor die Mauer gesetzt oder nicht repariert: "
                            + where(drone, arena, sim)) : StepResult.pending();
                }
                stage(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                boolean arm = since(ctx) >= 3;
                input(session, 0.0F, arm);
                if (sim.telemetry().armed) {
                    state.put(FUTURE, ctx.onServer(server -> serverDrone(serverPlayer(server), droneId)
                            .resetTo(launchPoint(ARENA.get(), LAUNCH_HEIGHT), FACING_WALL)));
                    stage(ctx, 3);
                    return StepResult.pending();
                }
                return since(ctx) > ARM_TICKS ? StepResult.fail("nicht scharf geschaltet, Sperren=" + sim.telemetry().armBlockers)
                        : StepResult.pending();
            }
            case 3 -> {
                input(session, (float) sim.telemetry().hoverThrottleStick, true);
                StepResult server = awaitServer(ctx, "Drohne in die Luft setzen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (!near(drone, launchPoint(ARENA.get(), LAUNCH_HEIGHT))) {
                    return since(ctx) > SYNC_TICKS ? StepResult.fail("Drohne nicht in die Luft gesetzt: " + where(drone, ARENA.get(), sim))
                            : StepResult.pending();
                }
                if (!sim.telemetry().armed) {
                    return StepResult.fail("beim Versetzen entschärft: " + sim.telemetry().lastDisarmReason);
                }
                state.put(COUNT, session.crashCount());
                push(sim, CRASH_SPEED, 0.0, 0.0);
                stage(ctx, 4);
                return StepResult.pending();
            }
            case 4 -> {
                input(session, (float) sim.telemetry().hoverThrottleStick, true);
                if (session.crashCount() <= (int) state.get(COUNT)) {
                    return since(ctx) > CRASH_TICKS ? StepResult.fail("kein Absturz an der Mauer erkannt: " + where(drone, ARENA.get(), sim))
                            : StepResult.pending();
                }
                input(session, 0.0F, false);
                ctx.log(String.format(Locale.ROOT, "Absturz an der Mauer mit %.1f m/s, %.2f m über dem Boden, danach %s",
                        sim.telemetry().lastImpactSpeed, drone.getY() - ARENA.get()[1], sim.telemetry().armed ? "scharf" : "entschärft"));
                stage(ctx, 5);
                return StepResult.pending();
            }
            default -> {
                input(session, 0.0F, false);
                int[] arena = ARENA.get();
                if (!landed(drone, arena, sim)) {
                    return since(ctx) > FALL_TICKS ? StepResult.fail("Drohne hängt nach dem Absturz in der Luft: " + where(drone, arena, sim))
                            : StepResult.pending();
                }
                ctx.log(String.format(Locale.ROOT, "nach dem Absturz in %d Ticks am Boden: %s", since(ctx), where(drone, arena, sim)));
                return StepResult.pass();
            }
        }
    }

    static StepResult ledgeStep(SelfTest.StepContext ctx, int droneId) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        int[] arena = ARENA.get();
        if (drone == null || sim == null || arena == null) {
            return StepResult.fail("keine aktive Sitzung oder kein Testfeld");
        }
        input(session, 0.0F, false);
        Map<String, Object> state = ctx.state();
        switch (stage(ctx)) {
            case 0 -> {
                state.put(FUTURE, ctx.onServer(server -> serverDrone(serverPlayer(server), droneId).resetTo(ledgePoint(arena), FACING_WALL)));
                stage(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = awaitServer(ctx, "Drohne auf die Mauerkrone setzen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                boolean resting = near(drone, ledgePoint(arena)) && sim.telemetry().grounded
                        && Math.abs(drone.getY() - arena[1] - WALL_HEIGHT) < ON_GROUND;
                if (!resting) {
                    return since(ctx) > SYNC_TICKS ? StepResult.fail("Drohne liegt nicht auf der Mauerkrone: " + where(drone, arena, sim))
                            : StepResult.pending();
                }
                push(sim, SLIDE_SPEED, 0.0, 0.0);
                stage(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                double edge = arena[0] + WALL_DX + 1.0 + drone.getBbWidth() / 2.0;
                if (drone.getX() <= edge) {
                    return since(ctx) > LEAVE_TICKS ? StepResult.fail("Drohne rutscht nicht über die Mauerkrone: " + where(drone, arena, sim))
                            : StepResult.pending();
                }
                stage(ctx, 3);
                return StepResult.pending();
            }
            default -> {
                if (!landed(drone, arena, sim)) {
                    return since(ctx) > FALL_TICKS ? StepResult.fail("Drohne hängt hinter der Mauerkrone in der Luft (Mauerkrone "
                            + WALL_HEIGHT + " m): " + where(drone, arena, sim)) : StepResult.pending();
                }
                ctx.log(String.format(Locale.ROOT, "hinter der Mauerkrone in %d Ticks %d m tief gefallen, %s", since(ctx), WALL_HEIGHT,
                        where(drone, arena, sim)));
                return StepResult.pass();
            }
        }
    }

    static StepResult cleanupStep(SelfTest.StepContext ctx) {
        int[] arena = ARENA.get();
        if (arena == null) {
            return StepResult.pass();
        }
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> fill(serverPlayer(server).level(), arena, false)));
            return StepResult.pending();
        }
        return awaitServer(ctx, "Mauer abbauen");
    }

    static StepResult unpilotedStep(SelfTest.StepContext ctx, int droneId) {
        int[] arena = ARENA.get();
        if (arena == null) {
            return StepResult.fail("kein Testfeld");
        }
        if (PilotSession.INSTANCE.isActive()) {
            return StepResult.fail("Sitzung läuft noch");
        }
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            AtomicReference<double[]> start = new AtomicReference<>();
            state.put(START, start);
            state.put(FUTURE, ctx.onServer(server -> {
                QuadcopterEntity drone = serverDrone(serverPlayer(server), droneId);
                if (drone.pilot().isPresent()) {
                    throw new IllegalStateException("Drohne hat noch einen Piloten");
                }
                drone.resetTo(launchPoint(arena, DROP_HEIGHT), FACING_WALL);
                start.set(new double[]{drone.getY(), server.getTickCount()});
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Drohne ohne Pilot versetzen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        @SuppressWarnings("unchecked")
        AtomicReference<double[]> sample = (AtomicReference<double[]>) state.computeIfAbsent(SAMPLE, key -> new AtomicReference<double[]>());
        CompletableFuture<?> pending = (CompletableFuture<?>) state.get(SAMPLES);
        if (pending == null || pending.isDone()) {
            if (pending != null && pending.isCompletedExceptionally()) {
                return StepResult.fail("Serverabfrage: " + pending.exceptionNow().getMessage());
            }
            state.put(SAMPLES, ctx.onServer(srv -> {
                QuadcopterEntity drone = serverDrone(serverPlayer(srv), droneId);
                sample.set(new double[]{drone.getY(), srv.getTickCount(), drone.onGround() ? 1.0 : 0.0});
            }));
        }
        @SuppressWarnings("unchecked")
        double[] start = ((AtomicReference<double[]>) state.get(START)).get();
        double[] now = sample.get();
        if (start == null || now == null) {
            return StepResult.pending();
        }
        int ticks = (int) (now[1] - start[1]);
        if (!state.containsKey(FALLING)) {
            if (now[0] < start[0] - 0.01) {
                state.put(FALLING, ticks);
            } else if (ticks > START_FALLING_TICKS) {
                return StepResult.fail(String.format(Locale.ROOT, "Drohne ohne Pilot hängt nach dem Versetzen %d Server-Ticks in der Luft", ticks));
            }
            return StepResult.pending();
        }
        if (now[2] < 0.5 || now[0] - arena[1] > ON_GROUND) {
            return ticks > FALL_TICKS ? StepResult.fail(String.format(Locale.ROOT, "Drohne ohne Pilot nicht am Boden: %.3f m nach %d Ticks",
                    now[0] - arena[1], ticks)) : StepResult.pending();
        }
        Entity client = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(droneId);
        if (!(client instanceof QuadcopterEntity clientDrone) || Math.abs(clientDrone.getY() - now[0]) > 0.1) {
            return ticks > FALL_TICKS + 20 ? StepResult.fail("Client sieht die gelandete Drohne nicht am Boden") : StepResult.pending();
        }
        ctx.log(String.format(Locale.ROOT, "Drohne ohne Pilot fällt nach %d Server-Ticks, nach %d Ticks am Boden (auch auf dem Client)",
                (int) state.get(FALLING), ticks));
        return StepResult.pass();
    }

    private static final class FallenScene implements Screenshots.Scene {
        private static final String MOVED = "moved";
        private static final String SETTLING = "settling";
        private static final int SETTLE_TICKS = 100;
        private static final double VIEW_BACK = 2.2;
        private static final double VIEW_SIDE = 2.2;
        private static final double MAX_LOOK_ERROR = 5.0;
        private static final AtomicReference<double[]> HOME = new AtomicReference<>();

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            PilotSession session = PilotSession.INSTANCE;
            QuadcopterEntity drone = session.drone();
            DroneSim sim = session.sim();
            int[] arena = ARENA.get();
            if (drone == null || sim == null || arena == null || mc.player == null) {
                return StepResult.fail("keine Sitzung für das Bildschirmfoto");
            }
            input(session, 0.0F, false);
            StepResult resized = Screenshots.resizeWindow(ctx);
            if (resized.status() != StepResult.Status.PASS) {
                return resized;
            }
            Screenshots.hideMarker(true);
            Map<String, Object> state = ctx.state();
            if (!landed(drone, arena, sim)) {
                int since = (int) state.computeIfAbsent(SETTLING, key -> ctx.tick());
                if (ctx.tick() - since < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                return StepResult.fail("Drohne für das Bildschirmfoto nach " + SETTLE_TICKS + " Ticks nicht am Boden: " + where(drone, arena, sim));
            }
            Vec3 view = new Vec3(drone.getX() - VIEW_BACK, arena[1], drone.getZ() + VIEW_SIDE);
            if (!state.containsKey(MOVED)) {
                state.put(MOVED, ctx.onServer(server -> {
                    ServerPlayer player = serverPlayer(server);
                    HOME.set(new double[]{player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()});
                    player.teleportTo(player.level(), view.x, view.y, view.z, Set.of(), -135.0F, 30.0F, true);
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
