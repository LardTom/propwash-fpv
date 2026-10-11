package de.lardtom.propwash.compat.iris.client;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.led.LedColor;
import de.lardtom.propwash.drone.led.LedMode;
import de.lardtom.propwash.drone.led.LedSettings;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.client.GateStackSelfTests;
import de.lardtom.propwash.rescue.client.RescueController;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class IrisSite {
    static final long MORNING = 1000L;
    static final long NIGHT = 18000L;
    static final double HOVER = 1.3;
    private static final int OFFSET_X = -70;
    private static final int OFFSET_Z = 190;
    private static final int LAKE_WEST = 36;
    private static final int LAKE_EAST = 1;
    private static final int LAKE_HALF = 12;
    private static final int LAKE_DEPTH = 2;
    private static final float UPTILT = 10.0F;
    private static final float WEST = (float) (Math.PI / 2.0);
    private static final String SINCE = "iris_since";
    private static final String LAST_Y = "iris_last_y";
    private static final String INTEGRAL = "iris_integral";
    private static final String TARGET_Y = "iris_target_y";
    private static final String ARRIVE = "iris_arrive";
    private static final String STARTED = "iris_started";
    static final String ARRIVE_TICK = "iris_arrive_tick";
    private static final int ARRIVE_TICKS = 400;
    private static final int MARGIN = 6;
    private static final AtomicReference<BlockPos> TARGET = new AtomicReference<>();
    private static final AtomicBoolean ARRIVED = new AtomicBoolean();
    private static final AtomicReference<BlockPos> SITE = new AtomicReference<>();
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final AtomicLong CLOCK = new AtomicLong(-1L);
    private static final AtomicReference<Saved> SAVED = new AtomicReference<>();
    private static final List<Placed> PLACED = new CopyOnWriteArrayList<>();
    private static final List<Integer> EXTRAS = new CopyOnWriteArrayList<>();

    private IrisSite() {
    }

    enum View { FPV, LOS }

    enum Spot {
        WATER(0, 3.5, 2.5, 90.0F),
        LAND(9, 0.5, 6.5, 180.0F);

        final int droneX;
        final double pilotX;
        final double pilotZ;
        final float pilotYaw;

        Spot(int droneX, double pilotX, double pilotZ, float pilotYaw) {
            this.droneX = droneX;
            this.pilotX = pilotX;
            this.pilotZ = pilotZ;
            this.pilotYaw = pilotYaw;
        }
    }

    static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    static BlockPos site() {
        BlockPos site = SITE.get();
        if (site == null) {
            throw new IllegalStateException("Platz am See nicht gebaut");
        }
        return site;
    }

    static StepResult arrive(SelfTest.StepContext ctx) {
        if (SITE.get() != null) {
            return StepResult.pass();
        }
        Map<String, Object> state = ctx.state();
        if (state.get(ARRIVE) instanceof CompletableFuture<?> future) {
            if (future.isCompletedExceptionally()) {
                return StepResult.fail("Anfahrt zum See: " + future.exceptionNow());
            }
            if (!future.isDone()) {
                return StepResult.pending();
            }
            if (ARRIVED.get()) {
                return StepResult.pass();
            }
            if (!state.containsKey(STARTED)) {
                state.put(STARTED, ctx.tick());
            }
            if (ctx.tick() - (int) state.get(STARTED) > ARRIVE_TICKS) {
                return StepResult.fail("Entities am See nach " + ARRIVE_TICKS + " Ticks nicht geladen");
            }
        }
        state.put(ARRIVE, ctx.onServer(server -> ARRIVED.set(approach(server))));
        return StepResult.pending();
    }

    static boolean approach(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        BlockPos target = TARGET.get();
        if (target == null) {
            SAVED.compareAndSet(null, new Saved(player.position(), player.getYRot(), player.getXRot(), player.getItemBySlot(EquipmentSlot.HEAD).copy(),
                    player.getMainHandItem().copy()));
            int x = player.getBlockX() + OFFSET_X;
            int z = player.getBlockZ() + OFFSET_Z;
            level.getChunk(x >> 4, z >> 4);
            target = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
            TARGET.set(target);
            player.teleportTo(level, x + 0.5, target.getY(), z + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
        }
        for (int cx = (target.getX() - LAKE_WEST - MARGIN) >> 4; cx <= (target.getX() + MARGIN) >> 4; cx++) {
            for (int cz = (target.getZ() - LAKE_HALF - MARGIN) >> 4; cz <= (target.getZ() + LAKE_HALF + MARGIN) >> 4; cz++) {
                if (!level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    static void build(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        if (SITE.get() != null) {
            return;
        }
        BlockPos site = TARGET.get();
        if (site == null || !ARRIVED.get()) {
            throw new IllegalStateException("Platz am See noch nicht angefahren");
        }
        int x = site.getX();
        int y = site.getY();
        int z = site.getZ();
        SITE.set(site);
        BlockState water = Blocks.WATER.defaultBlockState();
        for (int dx = -LAKE_WEST - 1; dx <= -LAKE_EAST + 1; dx++) {
            for (int dz = -LAKE_HALF - 1; dz <= LAKE_HALF + 1; dz++) {
                boolean rim = dx == -LAKE_WEST - 1 || dx == -LAKE_EAST + 1 || Math.abs(dz) == LAKE_HALF + 1;
                if (rim) {
                    place(level, site.offset(dx, -1, dz), Blocks.SAND.defaultBlockState());
                    continue;
                }
                for (int dy = -LAKE_DEPTH; dy <= -1; dy++) {
                    place(level, site.offset(dx, dy, dz), dy == -LAKE_DEPTH && (dx + dz) % 3 == 0 ? Blocks.GRAVEL.defaultBlockState() : water);
                }
            }
        }
        tree(level, site.offset(-9, 0, -LAKE_HALF - 4));
        tree(level, site.offset(-22, 0, LAKE_HALF + 4));
        BlockPos pillar = site.offset(2, 0, -3);
        place(level, pillar, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        place(level, pillar.above(), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        ItemFrame frame = new ItemFrame(level, pillar.above().east(), Direction.EAST);
        frame.setItem(DroneAssembly.preset(DroneBuild.FREESTYLE));
        level.addFreshEntity(frame);
        EXTRAS.add(frame.getId());
        GateEntity near = GateStackSelfTests.place(player, GateSize.SMALL, x + 4, y, z + 6, 90.0F);
        EXTRAS.add(near.getId());
        for (int dz : new int[]{-5, 5}) {
            GateEntity gate = GateStackSelfTests.place(player, GateSize.MEDIUM, x - LAKE_WEST - 4, y, z + dz, 90.0F);
            EXTRAS.add(gate.getId());
        }
    }

    private static void tree(ServerLevel level, BlockPos foot) {
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int dy = 3; dy <= 6; dy++) {
            int radius = dy >= 5 ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && radius == 2) {
                        continue;
                    }
                    place(level, foot.offset(dx, dy, dz), leaves);
                }
            }
        }
        for (int dy = 0; dy < 5; dy++) {
            place(level, foot.above(dy), log);
        }
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState before = level.getBlockState(pos);
        if (before.equals(state)) {
            return;
        }
        PLACED.add(new Placed(pos.immutable(), before));
        level.setBlockAndUpdate(pos, state);
    }

    static void time(MinecraftServer server, long dayTime) {
        ServerLevel level = player(server).level();
        Optional<Holder<WorldClock>> clock = level.dimensionType().defaultClock();
        if (clock.isEmpty()) {
            return;
        }
        long now = server.clockManager().getInstance(clock.get()).totalTicks();
        CLOCK.compareAndSet(-1L, now);
        server.clockManager().setTotalTicks(clock.get(), now - Math.floorMod(now, 24000L) + 24000L + dayTime);
    }

    static FlightTune tune(boolean leds) {
        Map<String, Float> values = new TreeMap<>(new LedSettings(LedMode.STATIC, LedColor.WHITE, 100, leds).values());
        values.put(TuneMapping.CAMERA_UPTILT, UPTILT);
        return new FlightTune(values);
    }

    static void start(MinecraftServer server, View view, Spot spot, long dayTime, boolean leds) {
        build(server);
        time(server, dayTime);
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        discardDrone(level);
        BlockPos site = site();
        BlockPos start = site.offset(spot.droneX, 0, 0);
        if (view == View.FPV) {
            player.teleportTo(level, start.getX() + 3.5, start.getY(), start.getZ() + 2.5, Set.of(), 90.0F, 10.0F, true);
        } else {
            player.teleportTo(level, start.getX() + spot.pilotX, start.getY(), start.getZ() + spot.pilotZ, Set.of(), spot.pilotYaw, 10.0F, true);
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        DroneBuild build = leds ? DroneBuild.FREESTYLE.withAccessory(AccessoryType.LED_STRIP) : DroneBuild.FREESTYLE;
        Vec3 at = new Vec3(start.getX() + 0.5, start.getY() + 0.02, start.getZ() + 0.5);
        drone.snapTo(at.x, at.y, at.z, 90.0F, 0.0F);
        drone.setBuild(build);
        drone.setTune(tune(leds));
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht in die Welt gesetzt");
        }
        drone.resetTo(at, new Quaternionf().rotationY(WEST));
        DRONE.set(drone.getId());
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        if (view == View.FPV) {
            ItemStack goggles = ContentIds.item(build.video().system == VideoSystem.ANALOG ? ContentIds.GOGGLES_ANALOG : ContentIds.GOGGLES_DIGITAL)
                    .map(ItemStack::new).orElse(ItemStack.EMPTY);
            if (goggles.isEmpty()) {
                throw new IllegalStateException("Brille nicht registriert");
            }
            goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            player.setItemSlot(EquipmentSlot.HEAD, goggles);
        } else {
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, view == View.FPV ? PilotMode.FPV : PilotMode.LINE_OF_SIGHT);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("Start verweigert: " + result);
        }
    }

    static void closeUp(MinecraftServer server, long dayTime) {
        build(server);
        time(server, dayTime);
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        discardDrone(level);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        BlockPos site = site();
        player.teleportTo(level, site.getX() + 6.5, site.getY(), site.getZ() - 1.5, Set.of(), 106.0F, 2.0F, true);
    }

    static int droneId() {
        return DRONE.get();
    }

    static void homeAhead(double distance) {
        Minecraft mc = Minecraft.getInstance();
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (mc.level == null || drone == null) {
            return;
        }
        BlockPos below = BlockPos.containing(drone.getX() - distance, site().getY() - 1, drone.getZ());
        double top = mc.level.getBlockState(below).is(Blocks.WATER) ? 0.9 : 1.0;
        RescueController.INSTANCE.setHome(new Vec3(drone.getX() - distance, below.getY() + top, drone.getZ()), mc.level.dimension(), true);
    }

    private static void discardDrone(ServerLevel level) {
        if (level.getEntity(DRONE.getAndSet(-1)) instanceof QuadcopterEntity drone) {
            if (drone.pilot().isPresent()) {
                DronePiloting.release(drone);
            }
            drone.discard();
        }
    }

    static void stopFlight(SelfTest.StepContext ctx) {
        PilotSession.INSTANCE.clearTestInput();
        if (PilotSession.INSTANCE.isActive()) {
            PilotSession.INSTANCE.requestStop();
        }
        RescueController.INSTANCE.clearHome();
        ctx.onServer(server -> discardDrone(player(server).level()));
    }

    static StepResult awaitServer(SelfTest.StepContext ctx, String key, String label) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(key);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(label + ": " + future.exceptionNow());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    static StepResult climb(SelfTest.StepContext ctx, View view) {
        PilotSession session = PilotSession.INSTANCE;
        boolean flying = view == View.FPV ? session.isFpv() : session.isActive() && !session.isFpv();
        QuadcopterEntity drone = session.drone();
        if (!flying || drone == null || drone.getId() != DRONE.get()) {
            return ctx.tick() - (int) ctx.state().getOrDefault(ARRIVE_TICK, 0) > 300 ? StepResult.fail("keine Sitzung " + view) : StepResult.pending();
        }
        DroneSim sim = session.sim();
        if (sim == null) {
            return StepResult.fail("keine Simulation");
        }
        Map<String, Object> state = ctx.state();
        int since = (int) state.computeIfAbsent(SINCE, key -> ctx.tick());
        int elapsed = ctx.tick() - since;
        if (elapsed < 20 || !sim.telemetry().armed) {
            session.setTestInput(StickPositions.IDLE, sim.telemetry().armed || (elapsed >= 10 && sim.telemetry().armBlockers == 0 && ctx.tick() % 10 >= 5),
                    StabMode.ANGLE);
            return elapsed > 160 ? StepResult.fail("nicht scharf: " + sim.telemetry().armBlockers) : StepResult.pending();
        }
        double target = site().getY() + HOVER;
        state.put(TARGET_Y, target);
        hold(ctx);
        double y = session.comPosition(new Vector3d()).y;
        if (elapsed > 60 && Math.abs(y - target) < 0.25 && Math.abs((double) state.getOrDefault(LAST_Y, y) - y) < 0.02) {
            return StepResult.pass();
        }
        return elapsed > 700 ? StepResult.fail("Höhe nicht erreicht: " + y + " statt " + target) : StepResult.pending();
    }

    static void hold(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        double y = session.comPosition(new Vector3d()).y;
        double target = (double) state.getOrDefault(TARGET_Y, y);
        double last = (double) state.getOrDefault(LAST_Y, y);
        double integral = (double) state.getOrDefault(INTEGRAL, 0.0);
        double vy = (y - last) * 20.0;
        double error = target - y;
        integral = Math.max(-0.2, Math.min(0.2, integral + error * 0.004));
        state.put(LAST_Y, y);
        state.put(INTEGRAL, integral);
        float throttle = (float) Math.max(0.05, Math.min(0.7, 0.28 + integral + error * 0.12 - vy * 0.08));
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
    }

    static void tearDown(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        discardDrone(level);
        for (int id : EXTRAS) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        EXTRAS.clear();
        for (int i = PLACED.size() - 1; i >= 0; i--) {
            Placed placed = PLACED.get(i);
            level.setBlockAndUpdate(placed.pos(), placed.state());
        }
        PLACED.clear();
        SITE.set(null);
        TARGET.set(null);
        ARRIVED.set(false);
        long ticks = CLOCK.getAndSet(-1L);
        Optional<Holder<WorldClock>> clock = level.dimensionType().defaultClock();
        if (ticks >= 0L && clock.isPresent()) {
            server.clockManager().setTotalTicks(clock.get(), ticks);
        }
        Saved saved = SAVED.getAndSet(null);
        if (saved != null) {
            player.setItemSlot(EquipmentSlot.HEAD, saved.head());
            player.setItemSlot(EquipmentSlot.MAINHAND, saved.hand());
            player.teleportTo(level, saved.pos().x, saved.pos().y, saved.pos().z, Set.of(), saved.yaw(), saved.pitch(), true);
        }
    }

    private record Placed(BlockPos pos, BlockState state) {
    }

    private record Saved(Vec3 pos, float yaw, float pitch, ItemStack head, ItemStack hand) {
    }
}
