package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.interact.DroneBlockTriggers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class DroneTriggerSelfTests {
    public static final String GROUP = "interact: ";
    public static final int JOIN_TIMEOUT_TICKS = 20 * 60;
    private static final int ARENA_DX = -34;
    private static final int ARENA_DZ = 6;
    private static final int ARENA_WEST = 3;
    private static final int ARENA_EAST = 20;
    private static final int ARENA_SIDE = 4;
    private static final int ARENA_HEIGHT = 5;
    private static final int POWER_TICKS = 5;
    private static final int RELEASE_TICKS = 40;
    private static final int QUIET_TICKS = 30;
    private static final int SIDE_TICKS = 60;
    private static final int INSIDE_TICK = 2;
    private static final int FALL_TICK = 6;
    private static final int STEP_TICKS = 400;
    private static final double AWAY = 3.0;
    private static final double FALL_START = 2.5;
    private static final double FALL_TOLERANCE = 1.0E-3;
    private static final int JOIN_SEARCH = 2;
    private static final String PHASE = "trigger_phase";
    private static final String FUTURE = "trigger_future";
    private static final String MARK = "trigger_mark";
    private static final Map<Integer, Watch> WATCHES = new ConcurrentHashMap<>();
    private static final Set<Integer> DAMAGED = ConcurrentHashMap.newKeySet();
    private static final AtomicReference<int @Nullable []> ARENA = new AtomicReference<>();
    private static final AtomicReference<String> REPORT = new AtomicReference<>("");
    private static final AtomicReference<List<Integer>> CURRENT = new AtomicReference<>(List.of());

    private DroneTriggerSelfTests() {
    }

    public static void register() {
        QuadcopterEntity.SERVER_TICK.register(DroneTriggerSelfTests::sample);
        PropwashEvents.DRONE_DAMAGED.register(damage -> {
            int id = damage.drone().getId();
            if (WATCHES.containsKey(id)) {
                DAMAGED.add(id);
            }
        });
        SelfTest.clientStep(GROUP + "Block-Tag propwash:drone_triggers mit Druckplatten und Stolperdraht", 100, DroneTriggerSelfTests::tagStep);
        SelfTest.clientStep(GROUP + "Drohne drückt Holzdruckplatte", STEP_TICKS, ctx -> triggerStep(ctx, Station.OAK));
        SelfTest.clientStep(GROUP + "Drohne auf leichter Wägeplatte gibt Signal 1", STEP_TICKS, ctx -> triggerStep(ctx, Station.LIGHT));
        SelfTest.clientStep(GROUP + "Stein-Druckplatte bleibt bei Drohne aus", STEP_TICKS, ctx -> triggerStep(ctx, Station.STONE));
        SelfTest.clientStep(GROUP + "Drohne löst Stolperdraht aus", STEP_TICKS, ctx -> triggerStep(ctx, Station.TRIPWIRE));
        SelfTest.clientStep(GROUP + "Drohne ohne Nebenwirkungen an Feuer, Lava, Kaktus, Portal und Spinnwebe", STEP_TICKS,
                DroneTriggerSelfTests::sideEffectStep);
        SelfTest.clientStep(GROUP + "Testfeld der Druckplatten abbauen", 100, DroneTriggerSelfTests::cleanupStep);
    }

    private enum Station {
        OAK("Holzdruckplatte", true, 15),
        LIGHT("leichte Wägeplatte", false, 1),
        STONE("Stein-Druckplatte", false, 0),
        TRIPWIRE("Stolperdraht", true, 15);

        final String what;
        final boolean release;
        final int signal;

        Station(String what, boolean release, int signal) {
            this.what = what;
            this.release = release;
            this.signal = signal;
        }
    }

    private enum Hazard {
        FIRE("Feuer", 0),
        LAVA("Lava", 3),
        CACTUS("Kaktus", 6),
        PORTAL("Portal", 10),
        WEB("Spinnwebe", 15),
        AIR("Luft", 18);

        final String what;
        final int dx;

        Hazard(String what, int dx) {
            this.what = what;
            this.dx = dx;
        }
    }

    private static final class Watch {
        final @Nullable BlockPos probe;
        final @Nullable BlockPos hazard;
        final @Nullable Block hazardBlock;
        volatile int ticks;
        volatile int firstOn = -1;
        volatile int maxSignal;
        volatile int lastSignal;
        volatile int movedAt = -1;
        volatile int offAt = -1;
        volatile boolean burned;
        volatile boolean portal;
        volatile boolean inside;
        volatile double fallY = Double.NaN;

        Watch(@Nullable BlockPos probe, @Nullable BlockPos hazard, @Nullable Block hazardBlock) {
            this.probe = probe;
            this.hazard = hazard;
            this.hazardBlock = hazardBlock;
        }
    }

    private static void sample(QuadcopterEntity drone) {
        Watch watch = WATCHES.get(drone.getId());
        if (watch == null || !(drone.level() instanceof ServerLevel level)) {
            return;
        }
        int tick = watch.ticks + 1;
        watch.ticks = tick;
        if (watch.probe != null) {
            int signal = signal(level.getBlockState(watch.probe));
            watch.lastSignal = signal;
            watch.maxSignal = Math.max(watch.maxSignal, signal);
            if (signal > 0 && watch.firstOn < 0) {
                watch.firstOn = tick;
            }
            if (watch.movedAt >= 0 && tick > watch.movedAt && signal == 0 && watch.offAt < 0) {
                watch.offAt = tick;
            }
        }
        if (drone.isOnFire() || drone.getRemainingFireTicks() > 0) {
            watch.burned = true;
        }
        if (drone.portalProcess != null) {
            watch.portal = true;
        }
        if (tick == INSIDE_TICK && watch.hazard != null && watch.hazardBlock != null) {
            watch.inside = level.getBlockState(watch.hazard).is(watch.hazardBlock) && drone.getBoundingBox().intersects(new AABB(watch.hazard));
        }
        if (tick == FALL_TICK) {
            watch.fallY = drone.getY();
        }
    }

    static int signal(BlockState state) {
        if (state.hasProperty(BlockStateProperties.POWER)) {
            return state.getValue(BlockStateProperties.POWER);
        }
        if (state.hasProperty(BlockStateProperties.POWERED)) {
            return state.getValue(BlockStateProperties.POWERED) ? 15 : 0;
        }
        return -1;
    }

    private static StepResult tagStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> REPORT.set(checkTag())));
            return StepResult.pending();
        }
        StepResult server = await(ctx, "Block-Tag");
        if (server.status() == StepResult.Status.PASS) {
            ctx.log(REPORT.get());
        }
        return server;
    }

    private static String checkTag() {
        List<Block> inside = List.of(Blocks.OAK_PRESSURE_PLATE, Blocks.BIRCH_PRESSURE_PLATE, Blocks.CRIMSON_PRESSURE_PLATE,
                Blocks.STONE_PRESSURE_PLATE, Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,
                Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE, Blocks.TRIPWIRE);
        List<Block> outside = List.of(Blocks.COBWEB, Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.LAVA, Blocks.CACTUS, Blocks.NETHER_PORTAL,
                Blocks.END_PORTAL, Blocks.SWEET_BERRY_BUSH, Blocks.POWDER_SNOW, Blocks.TRIPWIRE_HOOK, Blocks.BUBBLE_COLUMN, Blocks.STONE);
        List<String> wrong = new ArrayList<>();
        for (Block block : inside) {
            if (!block.defaultBlockState().is(DroneBlockTriggers.DRONE_TRIGGERS)) {
                wrong.add("fehlt: " + block);
            }
        }
        for (Block block : outside) {
            if (block.defaultBlockState().is(DroneBlockTriggers.DRONE_TRIGGERS)) {
                wrong.add("zu viel: " + block);
            }
        }
        if (!wrong.isEmpty()) {
            throw new IllegalStateException(String.join(", ", wrong));
        }
        return inside.size() + " Blöcke im Tag, " + outside.size() + " Gefahren und Nachbarn nicht";
    }

    private static StepResult triggerStep(SelfTest.StepContext ctx, Station station) {
        switch (phase(ctx)) {
            case 0 -> {
                ctx.state().put(FUTURE, ctx.onServer(server -> build(server, station)));
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = await(ctx, station.what);
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                Watch watch = watch();
                if (watch == null) {
                    return StepResult.fail(station.what + ": keine Drohne beobachtet");
                }
                if (station.signal > 0 && station.release) {
                    if (watch.firstOn > 0) {
                        if (watch.firstOn > POWER_TICKS) {
                            return StepResult.fail(station.what + ": erst nach " + watch.firstOn + " Ticks an (höchstens " + POWER_TICKS + ")");
                        }
                        ctx.state().put(FUTURE, ctx.onServer(server -> away(server, watch)));
                        next(ctx);
                        return StepResult.pending();
                    }
                    return watch.ticks > POWER_TICKS ? StepResult.fail(station.what + ": nach " + watch.ticks + " Ticks kein Signal") : StepResult.pending();
                }
                if (watch.ticks < QUIET_TICKS) {
                    return StepResult.pending();
                }
                return finish(ctx, station, watch);
            }
            case 3 -> {
                StepResult server = await(ctx, station.what + " wegsetzen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                Watch watch = watch();
                if (watch == null) {
                    return StepResult.fail(station.what + ": Drohne nach dem Wegsetzen verschwunden");
                }
                if (watch.offAt < 0) {
                    return watch.movedAt >= 0 && watch.ticks - watch.movedAt > RELEASE_TICKS
                            ? StepResult.fail(station.what + ": " + RELEASE_TICKS + " Ticks nach dem Wegsetzen noch an (Signal " + watch.lastSignal + ")")
                            : StepResult.pending();
                }
                return finish(ctx, station, watch);
            }
            default -> {
                StepResult server = await(ctx, station.what + " abbauen");
                if (server.status() == StepResult.Status.PASS) {
                    ctx.log((String) ctx.state().get(MARK + "_log"));
                }
                return server;
            }
        }
    }

    private static StepResult finish(SelfTest.StepContext ctx, Station station, Watch watch) {
        String log;
        switch (station) {
            case OAK, TRIPWIRE -> {
                if (watch.maxSignal != station.signal) {
                    return StepResult.fail(station.what + ": Signal " + watch.maxSignal + " statt " + station.signal);
                }
                log = String.format(Locale.ROOT, "%s: an nach %d Tick(s), Signal %d, aus %d Ticks nach dem Wegsetzen", station.what, watch.firstOn,
                        watch.maxSignal, watch.offAt - watch.movedAt);
            }
            case LIGHT -> {
                if (watch.firstOn < 1 || watch.firstOn > POWER_TICKS) {
                    return StepResult.fail(station.what + ": erstes Signal nach " + watch.firstOn + " Ticks (höchstens " + POWER_TICKS + ")");
                }
                if (watch.maxSignal != 1 || watch.lastSignal != 1) {
                    return StepResult.fail(station.what + ": Signal höchstens " + watch.maxSignal + ", zuletzt " + watch.lastSignal + " statt 1");
                }
                log = String.format(Locale.ROOT, "%s: Signal 1 nach %d Tick(s), nach %d Ticks weiter 1", station.what, watch.firstOn, watch.ticks);
            }
            default -> {
                if (watch.maxSignal != 0) {
                    return StepResult.fail(station.what + ": Signal " + watch.maxSignal + " durch die Drohne");
                }
                log = String.format(Locale.ROOT, "%s: %d Ticks mit ruhender Drohne, kein Signal", station.what, watch.ticks);
            }
        }
        ctx.state().put(MARK + "_log", log);
        ctx.state().put(FUTURE, ctx.onServer(DroneTriggerSelfTests::teardown));
        ctx.state().put(PHASE, 9);
        return StepResult.pending();
    }

    private static StepResult sideEffectStep(SelfTest.StepContext ctx) {
        switch (phase(ctx)) {
            case 0 -> {
                ctx.state().put(FUTURE, ctx.onServer(DroneTriggerSelfTests::buildHazards));
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                StepResult server = await(ctx, "Gefahren");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                int longest = 0;
                for (Integer id : CURRENT.get()) {
                    Watch watch = WATCHES.get(id);
                    longest = watch == null ? longest : Math.max(longest, watch.ticks);
                }
                if (longest < SIDE_TICKS && since(ctx) < 2 * SIDE_TICKS) {
                    return StepResult.pending();
                }
                ctx.state().put(FUTURE, ctx.onServer(server -> REPORT.set(judgeHazards(server))));
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                StepResult server = await(ctx, "Nebenwirkungen");
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                ctx.state().put(MARK + "_log", REPORT.get());
                ctx.state().put(FUTURE, ctx.onServer(DroneTriggerSelfTests::teardown));
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                StepResult server = await(ctx, "Gefahren abbauen");
                if (server.status() == StepResult.Status.PASS) {
                    ctx.log((String) ctx.state().get(MARK + "_log"));
                }
                return server;
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                teardown(server);
                int[] arena = ARENA.get();
                if (arena != null) {
                    clear(player(server).level(), arena, false);
                }
            }));
            return StepResult.pending();
        }
        return await(ctx, "Abbau");
    }

    private static void build(MinecraftServer server, Station station) {
        teardown(server);
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        int[] arena = arena(player);
        clear(level, arena, true);
        BlockPos center = new BlockPos(arena[0], arena[1], arena[2]);
        BlockPos probe = center;
        switch (station) {
            case OAK -> level.setBlockAndUpdate(center, Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
            case LIGHT -> level.setBlockAndUpdate(center, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
            case STONE -> level.setBlockAndUpdate(center, Blocks.STONE_PRESSURE_PLATE.defaultBlockState());
            case TRIPWIRE -> {
                BlockPos west = center.west(2);
                BlockPos east = center.east(2);
                level.setBlockAndUpdate(west.west(), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(east.east(), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(west, Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.FACING, Direction.EAST));
                level.setBlockAndUpdate(east, Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.FACING, Direction.WEST));
                for (int dx = -1; dx <= 1; dx++) {
                    level.setBlockAndUpdate(center.east(dx), Blocks.TRIPWIRE.defaultBlockState());
                }
                BlockState hook = level.getBlockState(west);
                if (!hook.is(Blocks.TRIPWIRE_HOOK) || !hook.getValue(TripWireHookBlock.ATTACHED) || hook.getValue(TripWireHookBlock.POWERED)) {
                    throw new IllegalStateException("Stolperdraht nicht gespannt: " + hook);
                }
                probe = west;
            }
        }
        if (signal(level.getBlockState(probe)) != 0) {
            throw new IllegalStateException(station.what + " schon vor der Drohne an: " + level.getBlockState(probe));
        }
        QuadcopterEntity drone = spawn(level, center.getX() + 0.5, center.getY(), center.getZ() + 0.5, new Watch(probe, null, null));
        CURRENT.set(List.of(drone.getId()));
    }

    private static void away(MinecraftServer server, Watch watch) {
        List<Integer> ids = CURRENT.get();
        if (ids.isEmpty() || !(player(server).level().getEntity(ids.getFirst()) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Drohne fehlt auf dem Server");
        }
        watch.movedAt = watch.ticks;
        drone.setPos(drone.getX(), drone.getY(), drone.getZ() + AWAY);
    }

    private static void buildHazards(MinecraftServer server) {
        teardown(server);
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        int[] arena = arena(player);
        clear(level, arena, true);
        int y = arena[1];
        int z = arena[2];
        List<Integer> ids = new ArrayList<>();
        for (Hazard hazard : Hazard.values()) {
            int x = arena[0] + hazard.dx;
            BlockPos pos = new BlockPos(x, y, z);
            double dy = 0.0;
            Block block;
            switch (hazard) {
                case FIRE -> {
                    level.setBlockAndUpdate(pos.below(), Blocks.NETHERRACK.defaultBlockState());
                    level.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
                    block = Blocks.FIRE;
                }
                case LAVA -> {
                    for (Direction side : Direction.Plane.HORIZONTAL) {
                        level.setBlockAndUpdate(pos.relative(side), Blocks.STONE.defaultBlockState());
                    }
                    level.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
                    dy = 0.2;
                    block = Blocks.LAVA;
                }
                case CACTUS -> {
                    level.setBlockAndUpdate(pos.below(), Blocks.SAND.defaultBlockState());
                    level.setBlockAndUpdate(pos, Blocks.CACTUS.defaultBlockState());
                    dy = 15.0 / 16.0;
                    block = Blocks.CACTUS;
                }
                case PORTAL -> {
                    portal(level, pos);
                    block = Blocks.NETHER_PORTAL;
                }
                case WEB -> {
                    for (int i = 0; i < 3; i++) {
                        level.setBlockAndUpdate(pos.above(i), Blocks.COBWEB.defaultBlockState());
                    }
                    pos = pos.above(2);
                    dy = FALL_START - 2.0;
                    block = Blocks.COBWEB;
                }
                default -> {
                    dy = FALL_START;
                    block = null;
                }
            }
            QuadcopterEntity drone = spawn(level, x + 0.5, pos.getY() + dy, z + 0.5, new Watch(null, block == null ? null : pos, block));
            ids.add(drone.getId());
        }
        CURRENT.set(List.copyOf(ids));
        for (Hazard hazard : Hazard.values()) {
            BlockPos pos = new BlockPos(arena[0] + hazard.dx, y, z);
            if (hazard == Hazard.FIRE && !level.getBlockState(pos).is(Blocks.FIRE)
                    || hazard == Hazard.CACTUS && !level.getBlockState(pos).is(Blocks.CACTUS)
                    || hazard == Hazard.PORTAL && !level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) {
                throw new IllegalStateException(hazard.what + " nicht gesetzt: " + level.getBlockState(pos));
            }
        }
    }

    private static void portal(ServerLevel level, BlockPos pos) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                BlockPos at = pos.offset(dx, dy, 0);
                boolean frame = dx == -1 || dx == 2 || dy == -1 || dy == 3;
                level.setBlock(at, frame ? Blocks.OBSIDIAN.defaultBlockState()
                        : Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X), flags);
            }
        }
    }

    private static String judgeHazards(MinecraftServer server) {
        List<Integer> ids = CURRENT.get();
        if (ids.size() != Hazard.values().length) {
            throw new IllegalStateException("Drohnen fehlen: " + ids);
        }
        ServerLevel level = player(server).level();
        List<String> wrong = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        double airY = Double.NaN;
        double webY = Double.NaN;
        for (Hazard hazard : Hazard.values()) {
            int id = ids.get(hazard.ordinal());
            Watch watch = WATCHES.get(id);
            Entity entity = level.getEntity(id);
            if (!(entity instanceof QuadcopterEntity drone) || drone.isRemoved()) {
                wrong.add(hazard.what + ": Drohne weg (Dimension gewechselt oder zerbrochen)");
                continue;
            }
            if (watch == null) {
                wrong.add(hazard.what + ": nicht beobachtet");
                continue;
            }
            if (drone.level().dimension() != Level.OVERWORLD) {
                wrong.add(hazard.what + ": in " + drone.level().dimension());
            }
            if (watch.burned || drone.isOnFire()) {
                wrong.add(hazard.what + ": brennt");
            }
            if (watch.portal) {
                wrong.add(hazard.what + ": Portal-Vorgang gestartet");
            }
            if (DAMAGED.contains(id)) {
                wrong.add(hazard.what + ": Schaden genommen");
            }
            if (watch.hazard != null && !watch.inside) {
                wrong.add(hazard.what + ": Drohne steckt nicht im Block (Test ohne Aussage)");
            }
            if (hazard == Hazard.AIR) {
                airY = watch.fallY;
            } else if (hazard == Hazard.WEB) {
                webY = watch.fallY;
            }
            seen.add(hazard.what);
        }
        double start = ARENA.get()[1] + FALL_START;
        if (!(start - airY > 0.5)) {
            wrong.add(String.format(Locale.ROOT, "Luft: Drohne nicht gefallen (y %.3f, Start %.3f)", airY, start));
        }
        if (!(Math.abs(webY - airY) < FALL_TOLERANCE)) {
            wrong.add(String.format(Locale.ROOT, "Spinnwebe bremst den Fall: nach %d Ticks %.3f Blöcke gefallen statt %.3f wie in Luft", FALL_TICK,
                    start - webY, start - airY));
        }
        if (!wrong.isEmpty()) {
            throw new IllegalStateException(String.join("; ", wrong));
        }
        return String.format(Locale.ROOT, "%s: %d Ticks im Block, nicht brennend, kein Schaden, Oberwelt, kein Portal; Spinnwebe fällt wie Luft "
                + "(%.3f Blöcke nach %d Ticks)", String.join(", ", seen), SIDE_TICKS, start - webY, FALL_TICK);
    }

    private static QuadcopterEntity spawn(ServerLevel level, double x, double y, double z, Watch watch) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        drone.snapTo(x, y, z, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        WATCHES.put(drone.getId(), watch);
        if (!level.addFreshEntity(drone)) {
            WATCHES.remove(drone.getId());
            throw new IllegalStateException("Drohne nicht in die Welt gesetzt");
        }
        return drone;
    }

    private static void teardown(MinecraftServer server) {
        for (Integer id : CURRENT.getAndSet(List.of())) {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                    if (drone.pilot().isPresent()) {
                        DronePiloting.release(drone);
                    }
                    drone.discard();
                }
            }
        }
        WATCHES.clear();
        DAMAGED.clear();
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

    private static void clear(ServerLevel level, int[] arena, boolean floor) {
        for (int dx = -ARENA_WEST; dx <= ARENA_EAST; dx++) {
            for (int dz = -ARENA_SIDE; dz <= ARENA_SIDE; dz++) {
                for (int dy = ARENA_HEIGHT - 1; dy >= 0; dy--) {
                    BlockPos pos = new BlockPos(arena[0] + dx, arena[1] + dy, arena[2] + dz);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
                BlockPos below = new BlockPos(arena[0] + dx, arena[1] - 1, arena[2] + dz);
                BlockState ground = floor ? Blocks.STONE.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                if (!level.getBlockState(below).equals(ground)) {
                    level.setBlockAndUpdate(below, ground);
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

    private static @Nullable Watch watch() {
        List<Integer> ids = CURRENT.get();
        return ids.isEmpty() ? null : WATCHES.get(ids.getFirst());
    }

    private static StepResult await(SelfTest.StepContext ctx, String what) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return StepResult.fail(what + ": kein Serverauftrag");
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow().getMessage());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static void next(SelfTest.StepContext ctx) {
        ctx.state().put(PHASE, phase(ctx) + 1);
        ctx.state().put(MARK, ctx.tick());
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(MARK, 0);
    }

    public static @Nullable String plateJoinProbe(Map<String, Object> state, int tick, Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return null;
        }
        int start = (int) state.computeIfAbsent("plates_at", key -> tick);
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.isFpv() ? session.drone() : null;
        if (drone == null) {
            if (state.containsKey("plates_fpv_at")) {
                return report(level, state, "ended", Double.NaN);
            }
            return tick - start > JOIN_TIMEOUT_TICKS ? "plates no session\n" : null;
        }
        int fpvAt = (int) state.computeIfAbsent("plates_fpv_at", key -> tick);
        if (!state.containsKey("plates_plate")) {
            BlockPos plate = find(level, drone.blockPosition(), Blocks.OAK_PRESSURE_PLATE);
            if (plate != null) {
                state.put("plates_plate", plate);
                BlockPos door = find(level, plate, Blocks.OAK_DOOR);
                if (door != null) {
                    state.put("plates_door", door);
                }
            }
        }
        BlockPos plate = (BlockPos) state.get("plates_plate");
        BlockPos door = (BlockPos) state.get("plates_door");
        boolean powered = plate != null && signal(level.getBlockState(plate)) > 0;
        boolean open = door != null && level.getBlockState(door).is(Blocks.OAK_DOOR) && level.getBlockState(door).getValue(DoorBlock.OPEN);
        if (!(powered && open) && tick - fpvAt < JOIN_TIMEOUT_TICKS) {
            return null;
        }
        return report(level, state, "fpv", plate == null ? Double.NaN : drone.getY() - plate.getY());
    }

    private static String report(ClientLevel level, Map<String, Object> state, String pilot, double height) {
        BlockPos plate = (BlockPos) state.get("plates_plate");
        BlockPos door = (BlockPos) state.get("plates_door");
        String plateText = plate == null ? "missing" : signal(level.getBlockState(plate)) > 0 ? "powered" : "unpowered";
        String doorText = door == null || !level.getBlockState(door).is(Blocks.OAK_DOOR) ? "missing"
                : level.getBlockState(door).getValue(DoorBlock.OPEN) ? "open" : "closed";
        return "plate " + plateText + "\ndoor " + doorText + "\nplate_pilot=" + pilot
                + String.format(Locale.ROOT, " drone %.2f above the plate%n", height);
    }

    private static @Nullable BlockPos find(ClientLevel level, BlockPos around, Block block) {
        for (int dy = -JOIN_SEARCH; dy <= JOIN_SEARCH; dy++) {
            for (int dx = -JOIN_SEARCH; dx <= JOIN_SEARCH; dx++) {
                for (int dz = -JOIN_SEARCH; dz <= JOIN_SEARCH; dz++) {
                    BlockPos pos = around.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(block) && (!state.hasProperty(DoorBlock.HALF) || state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }
}
