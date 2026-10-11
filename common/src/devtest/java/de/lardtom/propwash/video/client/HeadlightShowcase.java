package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.client.ShowcaseTour;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.led.LedSettings;
import de.lardtom.propwash.video.HeadlightCheck;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class HeadlightShowcase {
    private static final String FUTURE = "night_future";
    private static final String CHECKS = "night_checks";
    private static final String LOGGED = "night_logged";
    private static final int SITE_X = -140;
    private static final int SITE_Z = -20;
    private static final int PILLAR = 2;
    private static final float UPTILT = 10.0F;
    private static final double EDGE = 0.99;
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final AtomicLong CLOCK = new AtomicLong(-1L);
    private static final AtomicReference<Saved> SAVED = new AtomicReference<>();
    private static final List<Placed> PLACED = new CopyOnWriteArrayList<>();

    private HeadlightShowcase() {
    }

    public static void register() {
        if (!Screenshots.enabled()) {
            return;
        }
        Screenshots.step("bildschirmfotos: FPV bei Nacht im Freien mit LED-Streifen, LEDs aus", "headlight_night_off", 30, new NightScene(false));
        Screenshots.step("bildschirmfotos: FPV bei Nacht im Freien mit LED-Streifen, LEDs an", "headlight_night", 30, new NightScene(true));
        SelfTest.clientStep("bildschirmfotos: FPV bei Nacht aufräumen", 400, HeadlightShowcase::cleanupStep);
    }

    private static BlockPos site() {
        return ShowcaseTour.base().offset(SITE_X, 0, SITE_Z);
    }

    private static FlightTune tune(boolean leds) {
        LedSettings settings = leds ? HeadlightNightSelfTests.LIGHT_ON : HeadlightNightSelfTests.LIGHT_OFF;
        Map<String, Float> values = new TreeMap<>(settings.values());
        values.put(TuneMapping.CAMERA_UPTILT, UPTILT);
        return new FlightTune(values);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static void setUp(MinecraftServer server, boolean leds) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        if (level.getEntity(DRONE.get()) instanceof QuadcopterEntity existing) {
            existing.setTune(tune(leds));
            return;
        }
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        Optional<Holder<WorldClock>> clock = level.dimensionType().defaultClock();
        if (clock.isPresent()) {
            CLOCK.compareAndSet(-1L, server.clockManager().getInstance(clock.get()).totalTicks());
            server.clockManager().moveToTimeMarker(clock.get(), ClockTimeMarkers.MIDNIGHT);
        }
        SAVED.compareAndSet(null, new Saved(player.position(), player.getYRot(), player.getXRot(), player.getItemBySlot(EquipmentSlot.HEAD).copy(),
                player.getMainHandItem().copy()));
        BlockPos site = site();
        build(level, site);
        Vec3 feet = new Vec3(site.getX() + 0.5, site.getY(), site.getZ() - 1.5);
        player.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), 0.0F, 10.0F, true);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        Vec3 at = new Vec3(site.getX() + 0.5, site.getY() + PILLAR + 0.02, site.getZ() + EDGE);
        drone.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        drone.setBuild(DroneBuild.FREESTYLE.withAccessory(AccessoryType.LED_STRIP));
        drone.setTune(tune(leds));
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht in die Welt gesetzt");
        }
        drone.resetTo(at, new Quaternionf().rotationY((float) Math.PI));
        DRONE.set(drone.getId());
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static void build(ServerLevel level, BlockPos site) {
        for (int y = 0; y < PILLAR; y++) {
            place(level, site.above(y), Blocks.STONE_BRICKS.defaultBlockState());
        }
        BlockPos wall = site.offset(-4, 0, 8);
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 5; y++) {
                boolean window = x >= 3 && x <= 5 && y >= 1 && y <= 2;
                boolean broken = y == 4 && (x == 0 || x == 1 || x == 7);
                if (window || broken) {
                    continue;
                }
                BlockState brick = (x + y) % 3 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                        : (x * 7 + y) % 5 == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
                place(level, wall.offset(x, y, 0), brick);
            }
        }
        for (int z = 1; z < 8; z++) {
            place(level, site.offset(0, -1, z), Blocks.GRAVEL.defaultBlockState());
            if (z % 2 == 0) {
                place(level, site.offset(1, -1, z), Blocks.COARSE_DIRT.defaultBlockState());
            }
        }
        place(level, site.offset(-3, 0, 4), Blocks.HAY_BLOCK.defaultBlockState());
        place(level, site.offset(-3, 1, 4), Blocks.HAY_BLOCK.defaultBlockState());
        place(level, site.offset(-2, 0, 5), Blocks.HAY_BLOCK.defaultBlockState());
        place(level, site.offset(3, 0, 5), Blocks.OAK_LOG.defaultBlockState());
        place(level, site.offset(3, 0, 6), Blocks.OAK_LOG.defaultBlockState());
        place(level, site.offset(3, 1, 5), Blocks.OAK_LOG.defaultBlockState());
        place(level, site.offset(2, 0, 3), Blocks.SHORT_GRASS.defaultBlockState());
        place(level, site.offset(-1, 0, 6), Blocks.SHORT_GRASS.defaultBlockState());
        place(level, site.offset(1, 0, 2), Blocks.POPPY.defaultBlockState());
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState before = level.getBlockState(pos);
        if (before.equals(state)) {
            return;
        }
        PLACED.add(new Placed(pos.immutable(), before));
        level.setBlockAndUpdate(pos, state);
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        if (!ctx.state().containsKey(FUTURE)) {
            PilotSession.INSTANCE.clearTestInput();
            ctx.state().put(FUTURE, ctx.onServer(HeadlightShowcase::tearDown));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufräumen: " + future.exceptionNow());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (PilotSession.INSTANCE.isActive()) {
            return ctx.tick() > 300 ? StepResult.fail("FPV-Sitzung bei Nacht endet nicht") : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static void tearDown(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        if (level.getEntity(DRONE.getAndSet(-1)) instanceof QuadcopterEntity drone) {
            drone.discard();
        }
        for (int i = PLACED.size() - 1; i >= 0; i--) {
            Placed placed = PLACED.get(i);
            level.setBlockAndUpdate(placed.pos(), placed.state());
        }
        PLACED.clear();
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

    private static final class NightScene implements Screenshots.Scene {
        private final boolean leds;

        private NightScene(boolean leds) {
            this.leds = leds;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                boolean leds = this.leds;
                state.put(FUTURE, ctx.onServer(server -> setUp(server, leds)));
                return StepResult.pending();
            }
            CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
            if (future.isCompletedExceptionally()) {
                return StepResult.fail("Nacht-Szene: " + future.exceptionNow());
            }
            if (!future.isDone()) {
                return StepResult.pending();
            }
            PilotSession session = PilotSession.INSTANCE;
            QuadcopterEntity drone = session.drone();
            if (!session.isFpv() || drone == null || drone.getId() != DRONE.get() || mc.level == null) {
                return ctx.tick() > 300 ? StepResult.fail("keine FPV-Sitzung bei Nacht") : StepResult.pending();
            }
            if (mc.level.getMaxLocalRawBrightness(drone.blockPosition()) > 6) {
                return ctx.tick() > 300 ? StepResult.fail("nicht dunkel genug: " + mc.level.getMaxLocalRawBrightness(drone.blockPosition()))
                        : StepResult.pending();
            }
            Headlight light = Headlight.INSTANCE;
            if (light.active() != this.leds || (this.leds && !light.usesShaderPass())) {
                return ctx.tick() > 400 ? StepResult.fail("Scheinwerfer " + (this.leds ? "nicht über den Shader-Weg an" : "an") + " (Overlay "
                        + light.usesOverlay() + ", Fehler " + HeadlightPass.INSTANCE.failureDetail() + ")") : StepResult.pending();
            }
            if (this.leds) {
                if (!state.containsKey(CHECKS)) {
                    state.put(CHECKS, HeadlightPass.INSTANCE.checksPassed());
                    HeadlightPass.INSTANCE.restartChecks();
                    return StepResult.pending();
                }
                HeadlightCheck.Result check = HeadlightPass.INSTANCE.lastCheck();
                if (HeadlightPass.INSTANCE.checksPassed() <= (int) state.get(CHECKS) || check == null || check.brightened() == 0) {
                    return ctx.tick() > 500 ? StepResult.fail("Bildprüfung meldet sich nicht: " + check) : StepResult.pending();
                }
                if (!state.containsKey(LOGGED)) {
                    state.put(LOGGED, Boolean.TRUE);
                    ctx.log("Bildprüfung im FPV bei Nacht: " + check);
                }
            }
            return ctx.tick() > 40 ? StepResult.pass() : StepResult.pending();
        }
    }
}
