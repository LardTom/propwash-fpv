package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.CovSurfaceSelfTests;
import de.lardtom.propwash.interact.DroneBlockTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class CovDroneTriggerSelfTests {
    public static final String CRASH_STEP = DroneTriggerSelfTests.GROUP + "abgestürzte Drohne hält die Holzdruckplatte gedrückt";
    public static final String HEAVY_STEP = DroneTriggerSelfTests.GROUP + "Drohne auf schwerer Wägeplatte gibt Signal 1, Komparator 1";
    public static final String FLYOVER_STEP = DroneTriggerSelfTests.GROUP + "schneller Überflug löst nicht aus, tiefes Schweben schon";
    public static final String PARKED_STEP = DroneTriggerSelfTests.GROUP + "20 geparkte Drohnen kosten nichts";
    public static final String DATAPACK_STEP = DroneTriggerSelfTests.GROUP
            + "Datenpaket drone_triggers: leere Liste löst nichts aus, nur #minecraft:pressure_plates drückt Platten, aber keinen Stolperdraht";
    private static final String EMPTY_PACK = "triggers_empty";
    private static final String PLATES_PACK = "triggers_plates";
    private static final String TRIGGERS_FILE = "data/propwash/tags/block/drone_triggers.json";
    private static final int PACK_TICKS = 600;
    private static final int ARENA_DX = -34;
    private static final int ARENA_DZ = -14;
    private static final int ARENA_HALF = 6;
    private static final int ARENA_HEIGHT = 5;
    private static final int HEAVY_TICKS = 30;
    private static final int COMPARATOR_DELAY = 4;
    private static final int QUIET = 30;
    private static final double FLYOVER_SPEED = 15.0;
    private static final double LOW_HOVER = 0.2;
    private static final int PARKED = 20;
    private static final int SETTLE_TICKS = 40;
    private static final int MEASURE_TICKS = 200;
    private static final double MSPT_LIMIT_MS = 0.5;
    private static final Map<Integer, Vec3> LAST = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> CHECKS = new ConcurrentHashMap<>();
    private static final AtomicReference<int @Nullable []> ARENA = new AtomicReference<>();
    private static final AtomicReference<List<Integer>> DRONES = new AtomicReference<>(List.of());
    private static final AtomicReference<String> REPORT = new AtomicReference<>("");
    private static final Map<BlockPos, BlockState> ORIGINAL = new LinkedHashMap<>();
    private static volatile @Nullable Mover mover;
    private static volatile @Nullable Sampler sampler;
    private static boolean installed;

    private CovDroneTriggerSelfTests() {
    }

    public static void register() {
        QuadcopterEntity.SERVER_TICK.register(CovDroneTriggerSelfTests::observe);
        SelfTest.clientStep(CRASH_STEP, 1500, CovSurfaceSelfTests::plateCrashStep);
        SelfTest.clientStep(HEAVY_STEP, 400, CovDroneTriggerSelfTests::heavyStep);
        SelfTest.clientStep(FLYOVER_STEP, 600, CovDroneTriggerSelfTests::flyoverStep);
        SelfTest.clientStep(PARKED_STEP, 1200, CovDroneTriggerSelfTests::parkedStep);
        SelfTest.clientStep(DATAPACK_STEP, 2400, CovDroneTriggerSelfTests::datapackStep);
        SelfTest.clientStep(DroneTriggerSelfTests.GROUP + "Abdeckung: Testfeld abbauen", 200, CovDroneTriggerSelfTests::cleanupStep);
    }

    private static synchronized void install() {
        if (!installed) {
            installed = true;
            ServerBus.TICK_END.register(CovDroneTriggerSelfTests::serverTick);
        }
    }

    private static void observe(QuadcopterEntity drone) {
        if (!DRONES.get().contains(drone.getId())) {
            return;
        }
        Vec3 now = drone.position();
        Vec3 previous = LAST.put(drone.getId(), now);
        if (DroneBlockTriggers.moved(previous, now)) {
            CHECKS.merge(drone.getId(), 1, Integer::sum);
        }
    }

    private static final class Mover {
        final int id;
        final double y;
        final double fromX;
        final double toX;
        final double z;
        final double step;
        double x;
        boolean done;

        Mover(int id, double fromX, double toX, double y, double z, double step) {
            this.id = id;
            this.fromX = fromX;
            this.toX = toX;
            this.x = fromX;
            this.y = y;
            this.z = z;
            this.step = step;
        }
    }

    private static final class Sampler {
        final BlockPos plate;
        final @Nullable BlockPos comparator;
        int ticks;
        int maxSignal;
        int minSignal = Integer.MAX_VALUE;
        int lastSignal;
        int maxComparator;
        int minComparator = Integer.MAX_VALUE;
        final long[] nanos = new long[MEASURE_TICKS];
        int measured;
        boolean measure;
        long lastTick;

        Sampler(BlockPos plate, @Nullable BlockPos comparator) {
            this.plate = plate;
            this.comparator = comparator;
        }
    }

    private static void serverTick(MinecraftServer server) {
        Mover m = mover;
        ServerLevel level = server.overworld();
        if (m != null && !m.done && level.getEntity(m.id) instanceof QuadcopterEntity drone) {
            m.x = Math.min(m.toX, m.x + m.step);
            drone.setDeltaMovement(new Vec3(m.step, 0.0, 0.0));
            drone.snapTo(m.x, m.y, m.z, drone.getYRot(), drone.getXRot());
            if (m.x >= m.toX) {
                m.done = true;
                drone.setDeltaMovement(Vec3.ZERO);
            }
        }
        Sampler s = sampler;
        if (s != null) {
            s.ticks++;
            int signal = DroneTriggerSelfTests.signal(level.getBlockState(s.plate));
            s.lastSignal = signal;
            s.maxSignal = Math.max(s.maxSignal, signal);
            s.minSignal = Math.min(s.minSignal, signal);
            if (s.comparator != null && level.getBlockEntity(s.comparator) instanceof ComparatorBlockEntity comparator) {
                s.maxComparator = Math.max(s.maxComparator, comparator.getOutputSignal());
                s.minComparator = Math.min(s.minComparator, comparator.getOutputSignal());
            }
            if (s.measure && s.measured < MEASURE_TICKS) {
                long[] times = server.getTickTimesNanos();
                long tick = server.getTickCount();
                if (tick != s.lastTick && times.length > 0) {
                    s.lastTick = tick;
                    s.nanos[s.measured++] = times[(int) ((tick - 1) % times.length + times.length) % times.length];
                }
            }
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static int[] arena(ServerPlayer player) {
        int[] arena = ARENA.get();
        if (arena == null) {
            ServerLevel level = player.level();
            int x = player.getBlockX() + ARENA_DX;
            int z = player.getBlockZ() + ARENA_DZ;
            arena = new int[]{x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z};
            ARENA.set(arena);
        }
        return arena;
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        ORIGINAL.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private static void clear(ServerLevel level, int[] arena) {
        for (int dx = -ARENA_HALF; dx <= ARENA_HALF + 14; dx++) {
            for (int dz = -ARENA_HALF; dz <= ARENA_HALF; dz++) {
                for (int dy = ARENA_HEIGHT - 1; dy >= 0; dy--) {
                    BlockPos pos = new BlockPos(arena[0] + dx, arena[1] + dy, arena[2] + dz);
                    if (!level.getBlockState(pos).isAir()) {
                        set(level, pos, Blocks.AIR.defaultBlockState());
                    }
                }
                set(level, new BlockPos(arena[0] + dx, arena[1] - 1, arena[2] + dz), Blocks.STONE.defaultBlockState());
            }
        }
    }

    private static QuadcopterEntity spawn(ServerLevel level, double x, double y, double z, boolean floating) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        drone.setNoGravity(floating);
        drone.snapTo(x, y, z, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht in die Welt gesetzt");
        }
        List<Integer> ids = new ArrayList<>(DRONES.get());
        ids.add(drone.getId());
        DRONES.set(List.copyOf(ids));
        return drone;
    }

    private static void teardown(MinecraftServer server) {
        mover = null;
        sampler = null;
        for (Integer id : DRONES.getAndSet(List.of())) {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                    drone.discard();
                }
            }
        }
        LAST.clear();
        CHECKS.clear();
    }

    private static StepResult await(SelfTest.StepContext ctx, String what) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get("future");
        if (future == null) {
            return StepResult.fail(what + ": kein Serverauftrag");
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow().getMessage());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault("phase", 0);
    }

    private static StepResult next(SelfTest.StepContext ctx) {
        ctx.state().put("phase", phase(ctx) + 1);
        ctx.state().put("mark", ctx.tick());
        return StepResult.pending();
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault("mark", 0);
    }

    private static StepResult finish(SelfTest.StepContext ctx, @Nullable String problem, String log) {
        ctx.state().put("problem", problem);
        ctx.state().put("log", log);
        ctx.state().put("future", ctx.onServer(CovDroneTriggerSelfTests::teardown));
        ctx.state().put("phase", 99);
        return StepResult.pending();
    }

    private static StepResult done(SelfTest.StepContext ctx) {
        StepResult server = await(ctx, "Abbau");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        String problem = (String) ctx.state().get("problem");
        if (problem != null) {
            return StepResult.fail(problem);
        }
        ctx.log((String) ctx.state().get("log"));
        return StepResult.pass();
    }

    private static StepResult heavyStep(SelfTest.StepContext ctx) {
        int phase = phase(ctx);
        if (phase == 99) {
            return done(ctx);
        }
        switch (phase) {
            case 0 -> {
                ctx.state().put("future", ctx.onServer(server -> {
                    teardown(server);
                    install();
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    int[] arena = arena(player);
                    clear(level, arena);
                    BlockPos plate = new BlockPos(arena[0], arena[1], arena[2]);
                    BlockPos comparator = plate.east();
                    set(level, plate, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
                    set(level, comparator, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.WEST));
                    if (DroneTriggerSelfTests.signal(level.getBlockState(plate)) != 0) {
                        throw new IllegalStateException("schwere Wägeplatte schon vor der Drohne an");
                    }
                    spawn(level, plate.getX() + 0.5, plate.getY(), plate.getZ() + 0.5, false);
                    sampler = new Sampler(plate, comparator);
                }));
                return next(ctx);
            }
            case 1 -> {
                StepResult server = await(ctx, "schwere Wägeplatte");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                return next(ctx);
            }
            case 2 -> {
                Sampler s = sampler;
                if (s == null) {
                    return StepResult.fail("kein Beobachter");
                }
                if (s.maxSignal <= 0 && s.ticks < 20) {
                    return StepResult.pending();
                }
                if (!ctx.state().containsKey("on_at")) {
                    ctx.state().put("on_at", s.ticks);
                    return StepResult.pending();
                }
                if (!ctx.state().containsKey("settled")) {
                    if (s.ticks < (int) ctx.state().get("on_at") + COMPARATOR_DELAY) {
                        return StepResult.pending();
                    }
                    ctx.state().put("settled", true);
                    s.minSignal = Integer.MAX_VALUE;
                    s.maxSignal = 0;
                    s.minComparator = Integer.MAX_VALUE;
                    s.maxComparator = 0;
                    s.ticks = 0;
                    return StepResult.pending();
                }
                if (s.ticks < HEAVY_TICKS) {
                    return StepResult.pending();
                }
                String log = String.format(Locale.ROOT, "schwere Wägeplatte: an nach %d Ticks, %d Ticks Signal %d–%d, Komparator %d–%d",
                        (int) ctx.state().get("on_at"), HEAVY_TICKS, s.minSignal, s.maxSignal, s.minComparator, s.maxComparator);
                boolean ok = s.minSignal == 1 && s.maxSignal == 1 && s.minComparator == 1 && s.maxComparator == 1;
                return finish(ctx, ok ? null : log, log);
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static StepResult flyoverStep(SelfTest.StepContext ctx) {
        int phase = phase(ctx);
        if (phase == 99) {
            return done(ctx);
        }
        switch (phase) {
            case 0 -> {
                ctx.state().put("future", ctx.onServer(server -> {
                    teardown(server);
                    install();
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    int[] arena = arena(player);
                    clear(level, arena);
                    BlockPos plate = new BlockPos(arena[0] + 6, arena[1], arena[2]);
                    set(level, plate, Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
                    QuadcopterEntity drone = spawn(level, arena[0] - 3 + 0.5, plate.getY() + 1.0, plate.getZ() + 0.5, true);
                    sampler = new Sampler(plate, null);
                    mover = new Mover(drone.getId(), arena[0] - 3 + 0.5, arena[0] + 15 + 0.5, plate.getY() + 1.0, plate.getZ() + 0.5, FLYOVER_SPEED / 20.0);
                }));
                return next(ctx);
            }
            case 1 -> {
                StepResult server = await(ctx, "Überflug");
                return server.status() == StepResult.Status.PASS ? next(ctx) : server;
            }
            case 2 -> {
                Mover m = mover;
                Sampler s = sampler;
                if (m == null || s == null) {
                    return StepResult.fail("Überflug nicht vorbereitet");
                }
                if (!m.done || since(ctx) < 30) {
                    return since(ctx) > 200 ? StepResult.fail("Überflug endet nicht") : StepResult.pending();
                }
                ctx.state().put("fly_signal", s.maxSignal);
                ctx.state().put("fly_ticks", s.ticks);
                BlockPos plate = s.plate;
                int id = m.id;
                ctx.state().put("future", ctx.onServer(server -> {
                    mover = null;
                    if (server.overworld().getEntity(id) instanceof QuadcopterEntity drone) {
                        drone.setDeltaMovement(Vec3.ZERO);
                        drone.snapTo(plate.getX() + 0.5, plate.getY() + LOW_HOVER, plate.getZ() + 0.5, 0.0F, 0.0F);
                    }
                    sampler = new Sampler(plate, null);
                }));
                return next(ctx);
            }
            case 3 -> {
                StepResult server = await(ctx, "tiefes Schweben");
                return server.status() == StepResult.Status.PASS ? next(ctx) : server;
            }
            case 4 -> {
                Sampler s = sampler;
                if (s == null) {
                    return StepResult.fail("kein Beobachter");
                }
                if (s.maxSignal <= 0 && s.ticks < 20) {
                    return StepResult.pending();
                }
                int flown = (int) ctx.state().get("fly_signal");
                String log = String.format(Locale.ROOT, "Überflug mit %.0f m/s in 1 Block Höhe: Signal höchstens %d in %d Ticks; 0,2 Block darüber "
                        + "schwebend: Signal %d nach %d Ticks", FLYOVER_SPEED, flown, (int) ctx.state().get("fly_ticks"), s.maxSignal, s.ticks);
                boolean ok = flown == 0 && s.maxSignal == 15;
                return finish(ctx, ok ? null : log, log);
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static double median(long[] nanos, int count) {
        long[] copy = Arrays.copyOf(nanos, count);
        Arrays.sort(copy);
        return count == 0 ? Double.NaN : copy[count / 2] / 1.0E6;
    }

    private static StepResult parkedStep(SelfTest.StepContext ctx) {
        int phase = phase(ctx);
        if (phase == 99) {
            return done(ctx);
        }
        switch (phase) {
            case 0 -> {
                ctx.state().put("future", ctx.onServer(server -> {
                    teardown(server);
                    install();
                    ServerPlayer player = player(server);
                    int[] arena = arena(player);
                    clear(player.level(), arena);
                    Sampler s = new Sampler(new BlockPos(arena[0], arena[1], arena[2]), null);
                    s.measure = true;
                    sampler = s;
                }));
                return next(ctx);
            }
            case 1 -> {
                StepResult server = await(ctx, "Messung ohne Drohnen");
                return server.status() == StepResult.Status.PASS ? next(ctx) : server;
            }
            case 2 -> {
                Sampler s = sampler;
                if (s == null || s.measured < MEASURE_TICKS) {
                    return since(ctx) > MEASURE_TICKS * 3 ? StepResult.fail("Messung ohne Drohnen läuft nicht") : StepResult.pending();
                }
                ctx.state().put("without", median(s.nanos, s.measured));
                ctx.state().put("future", ctx.onServer(server -> {
                    sampler = null;
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    int[] arena = arena(player);
                    for (int i = 0; i < PARKED; i++) {
                        spawn(level, arena[0] + (i % 5) * 2 + 0.5, arena[1], arena[2] - 4 + (i / 5) * 2 + 0.5, false);
                    }
                }));
                return next(ctx);
            }
            case 3 -> {
                StepResult server = await(ctx, "20 Drohnen abstellen");
                return server.status() == StepResult.Status.PASS ? next(ctx) : server;
            }
            case 4 -> {
                if (since(ctx) < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                CHECKS.clear();
                ctx.state().put("future", ctx.onServer(server -> {
                    Sampler s = new Sampler(BlockPos.ZERO, null);
                    s.measure = true;
                    sampler = s;
                    CHECKS.clear();
                }));
                return next(ctx);
            }
            case 5 -> {
                StepResult server = await(ctx, "Messung mit Drohnen");
                return server.status() == StepResult.Status.PASS ? next(ctx) : server;
            }
            case 6 -> {
                Sampler s = sampler;
                if (s == null || s.measured < MEASURE_TICKS) {
                    return since(ctx) > MEASURE_TICKS * 3 ? StepResult.fail("Messung mit Drohnen läuft nicht") : StepResult.pending();
                }
                double without = (double) ctx.state().get("without");
                double with = median(s.nanos, s.measured);
                int checks = CHECKS.values().stream().mapToInt(Integer::intValue).sum();
                int alive = DRONES.get().size();
                String log = String.format(Locale.ROOT, "%d geparkte Drohnen: %d Prüfungen der Druckplatten-Auslöser in %d Server-Ticks nach dem Abstellen, "
                        + "Tick-Zeit im Median %.3f ms ohne und %.3f ms mit Drohnen (Zuwachs %.3f ms, Grenze %.1f ms)", alive, checks, MEASURE_TICKS, without, with,
                        with - without, MSPT_LIMIT_MS);
                boolean ok = alive == PARKED && checks == 0 && with - without < MSPT_LIMIT_MS;
                return finish(ctx, ok ? null : log, log);
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static boolean tagged(Block block) {
        return block.defaultBlockState().is(DroneBlockTriggers.DRONE_TRIGGERS);
    }

    private static void station(MinecraftServer server, boolean tripwire) {
        teardown(server);
        install();
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        int[] arena = arena(player);
        clear(level, arena);
        BlockPos center = new BlockPos(arena[0], arena[1], arena[2]);
        BlockPos probe = center;
        if (tripwire) {
            BlockPos west = center.west(2);
            BlockPos east = center.east(2);
            set(level, west.west(), Blocks.STONE.defaultBlockState());
            set(level, east.east(), Blocks.STONE.defaultBlockState());
            set(level, west, Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(net.minecraft.world.level.block.TripWireHookBlock.FACING, Direction.EAST));
            set(level, east, Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(net.minecraft.world.level.block.TripWireHookBlock.FACING, Direction.WEST));
            for (int dx = -1; dx <= 1; dx++) {
                set(level, center.east(dx), Blocks.TRIPWIRE.defaultBlockState());
            }
            probe = west;
        } else {
            set(level, center, Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
        }
        spawn(level, center.getX() + 0.5, center.getY() + (tripwire ? 0.3 : 0.0), center.getZ() + 0.5, false);
        sampler = new Sampler(probe, null);
    }

    private static StepResult datapackStep(SelfTest.StepContext ctx) {
        int phase = phase(ctx);
        if (phase == 99) {
            return done(ctx);
        }
        java.util.Map<String, Object> state = ctx.state();
        switch (phase) {
            case 0 -> {
                if (!tagged(Blocks.OAK_PRESSURE_PLATE) || !tagged(Blocks.TRIPWIRE)) {
                    return StepResult.fail("Holzplatte oder Stolperdraht schon vor dem Datenpaket nicht im Tag");
                }
                ctx.state().put("future", ctx.onServer(server -> de.lardtom.propwash.drone.CovFlightDatapack.install(server, EMPTY_PACK, List.of(TRIGGERS_FILE))));
                return next(ctx);
            }
            case 1 -> {
                StepResult server = await(ctx, "Datenpaket mit leerer Liste");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (tagged(Blocks.OAK_PRESSURE_PLATE) || tagged(Blocks.TRIPWIRE)) {
                    return since(ctx) > PACK_TICKS ? StepResult.fail("leere Liste wirkt nach /reload nicht") : StepResult.pending();
                }
                state.put("empty_at", since(ctx));
                ctx.state().put("future", ctx.onServer(srv -> station(srv, false)));
                return next(ctx);
            }
            case 2 -> {
                StepResult server = await(ctx, "Holzplatte mit leerer Liste");
                return server.status() == StepResult.Status.PASS ? next(ctx) : server;
            }
            case 3 -> {
                Sampler s = sampler;
                if (s == null || s.ticks < QUIET) {
                    return StepResult.pending();
                }
                state.put("empty_signal", s.maxSignal);
                ctx.state().put("future", ctx.onServer(server -> {
                    teardown(server);
                    de.lardtom.propwash.drone.CovFlightDatapack.remove(server, EMPTY_PACK);
                    de.lardtom.propwash.drone.CovFlightDatapack.install(server, PLATES_PACK, List.of(TRIGGERS_FILE));
                }));
                return next(ctx);
            }
            case 4 -> {
                StepResult server = await(ctx, "Datenpaket nur mit Druckplatten");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (!tagged(Blocks.OAK_PRESSURE_PLATE) || tagged(Blocks.TRIPWIRE)) {
                    return since(ctx) > PACK_TICKS ? StepResult.fail("Liste mit #minecraft:pressure_plates wirkt nach /reload nicht") : StepResult.pending();
                }
                ctx.state().put("future", ctx.onServer(srv -> station(srv, false)));
                return next(ctx);
            }
            case 5 -> {
                StepResult server = await(ctx, "Holzplatte mit Druckplatten-Liste");
                return server.status() == StepResult.Status.PASS ? next(ctx) : server;
            }
            case 6 -> {
                Sampler s = sampler;
                if (s == null || s.ticks < QUIET) {
                    return StepResult.pending();
                }
                state.put("plates_signal", s.maxSignal);
                ctx.state().put("future", ctx.onServer(server -> station(server, true)));
                return next(ctx);
            }
            case 7 -> {
                StepResult server = await(ctx, "Stolperdraht mit Druckplatten-Liste");
                return server.status() == StepResult.Status.PASS ? next(ctx) : server;
            }
            case 8 -> {
                Sampler s = sampler;
                if (s == null || s.ticks < QUIET) {
                    return StepResult.pending();
                }
                state.put("wire_signal", s.maxSignal);
                ctx.state().put("future", ctx.onServer(server -> {
                    teardown(server);
                    de.lardtom.propwash.drone.CovFlightDatapack.remove(server, PLATES_PACK);
                }));
                return next(ctx);
            }
            case 9 -> {
                StepResult server = await(ctx, "Datenpaket entfernen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (!tagged(Blocks.OAK_PRESSURE_PLATE) || !tagged(Blocks.TRIPWIRE)) {
                    return since(ctx) > PACK_TICKS ? StepResult.fail("Tag kommt nach dem Entfernen des Datenpakets nicht zurück") : StepResult.pending();
                }
                int empty = (int) state.get("empty_signal");
                int plates = (int) state.get("plates_signal");
                int wire = (int) state.get("wire_signal");
                String log = String.format(Locale.ROOT, "Datenpaket mit leerer Liste: Holzplatte Signal %d in %d Ticks; nur #minecraft:pressure_plates: Holzplatte Signal %d, "
                        + "Stolperdraht-Haken %d; danach entfernt, Tag wieder wie geliefert", empty, QUIET, plates, wire);
                return finish(ctx, empty == 0 && plates == 15 && wire == 0 ? null : log, log);
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ctx.state().put("future", ctx.onServer(server -> {
                teardown(server);
                de.lardtom.propwash.drone.CovFlightDatapack.remove(server, EMPTY_PACK);
                de.lardtom.propwash.drone.CovFlightDatapack.remove(server, PLATES_PACK);
                ServerLevel level = player(server).level();
                List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(ORIGINAL.entrySet());
                for (int i = entries.size() - 1; i >= 0; i--) {
                    level.setBlock(entries.get(i).getKey(), entries.get(i).getValue(), Block.UPDATE_ALL);
                }
                ORIGINAL.clear();
                ARENA.set(null);
            }));
            return StepResult.pending();
        }
        return await(ctx, "Abbau");
    }
}
