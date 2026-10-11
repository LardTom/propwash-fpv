package de.lardtom.propwash.relay.client;

import de.lardtom.propwash.build.DroneBuild;
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
import de.lardtom.propwash.relay.RelayModule;
import de.lardtom.propwash.relay.RepeaterIndex;
import de.lardtom.propwash.relay.SignalRepeaterBlock;
import de.lardtom.propwash.video.client.VideoState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public final class RelayShowcase {
    private static final String FUTURE = "relay_shot_future";
    private static final String SINCE = "relay_shot_since";
    private static final String ROUNDS = "relay_shot_rounds";
    private static final BlockPos FLOOR = new BlockPos(2, 1, 3);
    private static final BlockPos WALL = new BlockPos(4, 2, 5);
    private static final BlockPos CEILING = new BlockPos(5, 4, 4);
    private static final Vec3 DRONE_ON_FLOOR = new Vec3(4.5, 11.0, 4.5);
    private static final AtomicLong CLOCK = new AtomicLong(-1L);

    private RelayShowcase() {
    }

    public static void register() {
        if (!Screenshots.enabled()) {
            return;
        }
        Screenshots.step("bildschirmfotos: Signal-Repeater – am Boden, an der Wand und unter der Decke (Tag)", "signal_repeater_mounts_day", 30,
                new MountScene(ClockTimeMarkers.NOON, false));
        Screenshots.step("bildschirmfotos: Signal-Repeater – am Boden, an der Wand und unter der Decke (Nacht, Status-LED)",
                "signal_repeater_mounts_night", 30, new MountScene(ClockTimeMarkers.MIDNIGHT, false));
        Screenshots.step("bildschirmfotos: Signal-Repeater – in der Hand", "signal_repeater_in_hand", 30, new MountScene(ClockTimeMarkers.NOON, true));
        Screenshots.step("bildschirmfotos: Signal-Repeater – FPV im Betonbau zwei Etagen über dem Piloten, ohne Repeater", "signal_repeater_fpv_without", 30,
                new FpvScene(false));
        Screenshots.step("bildschirmfotos: Signal-Repeater – FPV im Betonbau über zwei Repeater, RPT im OSD", "signal_repeater_fpv_with", 30, new FpvScene(true));
        SelfTest.clientStep("bildschirmfotos: Signal-Repeater aufräumen", 600, RelayShowcase::cleanupStep);
    }

    private static void setTime(MinecraftServer server, ResourceKey<ClockTimeMarker> marker) {
        ServerLevel level = RelaySelfTests.player(server).level();
        Optional<Holder<WorldClock>> clock = level.dimensionType().defaultClock();
        if (clock.isPresent()) {
            CLOCK.compareAndSet(-1L, server.clockManager().getInstance(clock.get()).totalTicks());
            server.clockManager().moveToTimeMarker(clock.get(), marker);
        }
    }

    private static void corner(MinecraftServer server, boolean hand) {
        ServerPlayer player = RelaySelfTests.player(server);
        ServerLevel level = player.level();
        RelaySelfTests.save(player);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        BlockPos c = RelaySelfTests.origin(player).offset(RelaySelfTests.CORNER);
        for (int x = -1; x <= 7; x++) {
            for (int z = -3; z <= 7; z++) {
                RelaySelfTests.put(level, c.offset(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        for (int x = 0; x <= 6; x++) {
            for (int y = 1; y <= 5; y++) {
                RelaySelfTests.put(level, c.offset(x, y, 6), Blocks.STONE_BRICKS.defaultBlockState());
            }
            RelaySelfTests.put(level, c.offset(x, 5, 4), Blocks.STONE_BRICKS.defaultBlockState());
            RelaySelfTests.put(level, c.offset(x, 5, 5), Blocks.STONE_BRICKS.defaultBlockState());
        }
        mountAt(level, c.offset(FLOOR), AttachFace.FLOOR, Direction.SOUTH);
        mountAt(level, c.offset(WALL), AttachFace.WALL, Direction.NORTH);
        mountAt(level, c.offset(CEILING), AttachFace.CEILING, Direction.SOUTH);
        ItemStack held = hand ? new ItemStack(RelayModule.SIGNAL_REPEATER.item().get()) : ItemStack.EMPTY;
        player.setItemSlot(EquipmentSlot.MAINHAND, held);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        RelaySelfTests.teleport(player, Vec3.atBottomCenterOf(c.offset(3, 1, -2)), 0.0F, hand ? 8.0F : -6.0F);
    }

    private static void mountAt(ServerLevel level, BlockPos pos, AttachFace face, Direction facing) {
        RelaySelfTests.put(level, pos, RelayModule.SIGNAL_REPEATER.block().get().defaultBlockState()
                .setValue(SignalRepeaterBlock.FACE, face).setValue(SignalRepeaterBlock.FACING, facing));
    }

    private static void fpv(MinecraftServer server, boolean repeaters) {
        ServerPlayer player = RelaySelfTests.player(server);
        ServerLevel level = player.level();
        RelaySelfTests.save(player);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        BlockPos o = RelaySelfTests.origin(player);
        RelaySelfTests.build(level, o);
        if (repeaters) {
            RelaySelfTests.mount(level, RelaySelfTests.LOWER, AttachFace.FLOOR, Direction.NORTH);
            RelaySelfTests.mount(level, RelaySelfTests.UPPER, AttachFace.CEILING, Direction.NORTH);
        } else {
            RelaySelfTests.unmount(level, RelaySelfTests.LOWER);
            RelaySelfTests.unmount(level, RelaySelfTests.UPPER);
            RelaySelfTests.unmount(level, RelaySelfTests.SIDE);
        }
        RelaySelfTests.teleport(player, Vec3.atBottomCenterOf(o.offset(RelaySelfTests.PILOT)), -45.0F, -30.0F);
        if (level.getEntity(RelaySelfTests.DRONE.get()) instanceof QuadcopterEntity old) {
            old.discard();
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        Vec3 at = Vec3.atLowerCornerOf(o).add(DRONE_ON_FLOOR).add(0.0, 0.02, 0.0);
        drone.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        drone.setBuild(DroneBuild.RACER);
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht in die Welt gesetzt");
        }
        drone.resetTo(at, new Quaternionf().rotationY((float) Math.toRadians(-135.0)));
        RelaySelfTests.DRONE.set(drone.getId());
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_ANALOG).map(ItemStack::new).orElse(ItemStack.EMPTY);
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

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            PilotSession.INSTANCE.clearTestInput();
            state.put(FUTURE, ctx.onServer(server -> {
                RelaySelfTests.tearDown(server);
                long ticks = CLOCK.getAndSet(-1L);
                Optional<Holder<WorldClock>> clock = RelaySelfTests.player(server).level().dimensionType().defaultClock();
                if (ticks >= 0L && clock.isPresent()) {
                    server.clockManager().setTotalTicks(clock.get(), ticks);
                }
            }));
            return StepResult.pending();
        }
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (PilotSession.INSTANCE.isActive()) {
            return ctx.tick() > 300 ? StepResult.fail("FPV-Sitzung endet nicht") : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static StepResult waitFuture(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return null;
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Szene: " + future.exceptionNow());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private record MountScene(ResourceKey<ClockTimeMarker> time, boolean hand) implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                boolean hand = this.hand;
                ResourceKey<ClockTimeMarker> time = this.time;
                state.put(FUTURE, ctx.onServer(server -> {
                    corner(server, hand);
                    setTime(server, time);
                }));
                state.put(SINCE, ctx.tick());
                return StepResult.pending();
            }
            StepResult waiting = waitFuture(ctx);
            if (waiting != null) {
                return waiting;
            }
            BlockPos o = RelaySelfTests.ORIGIN.get();
            RepeaterIndex index = RepeaterIndex.existing(mc.level);
            if (o == null || index == null || mc.player == null) {
                return StepResult.pending();
            }
            BlockPos c = o.offset(RelaySelfTests.CORNER);
            boolean ready = index.contains(c.offset(FLOOR)) && index.contains(c.offset(WALL)) && index.contains(c.offset(CEILING))
                    && mc.player.position().distanceTo(Vec3.atBottomCenterOf(c.offset(3, 1, -2))) < 0.05
                    && mc.player.getMainHandItem().is(RelayModule.SIGNAL_REPEATER.item().get()) == this.hand;
            if (!ready) {
                return (int) state.get(SINCE) + 300 < ctx.tick() ? StepResult.fail("Repeater-Szene kommt nicht an") : StepResult.pending();
            }
            return StepResult.pass();
        }
    }

    private record FpvScene(boolean repeaters) implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                boolean repeaters = this.repeaters;
                state.put(FUTURE, ctx.onServer(server -> {
                    fpv(server, repeaters);
                    setTime(server, ClockTimeMarkers.NOON);
                }));
                state.put(SINCE, ctx.tick());
                return StepResult.pending();
            }
            StepResult waiting = waitFuture(ctx);
            if (waiting != null) {
                return waiting;
            }
            PilotSession session = PilotSession.INSTANCE;
            session.setTestInput(StickPositions.IDLE, false, null);
            VideoState video = VideoState.INSTANCE;
            QuadcopterEntity drone = session.drone();
            boolean ready = session.isFpv() && drone != null && drone.getId() == RelaySelfTests.DRONE.get()
                    && video.status() == VideoState.LinkStatus.CONNECTED && video.relay().settled();
            if (!ready) {
                state.remove(ROUNDS);
                return (int) state.get(SINCE) + 400 < ctx.tick() ? StepResult.fail("FPV im Betonbau startet nicht") : StepResult.pending();
            }
            if (!state.containsKey(ROUNDS)) {
                state.put(ROUNDS, ctx.tick());
                return StepResult.pending();
            }
            if (ctx.tick() - (int) state.get(ROUNDS) < 20) {
                return StepResult.pending();
            }
            float quality = video.signalQuality();
            boolean expected = this.repeaters ? quality > 0.9F && video.relayHops() == 2 : quality < 0.05F && video.relayHops() == 0;
            if (!expected) {
                return (int) state.get(SINCE) + 600 < ctx.tick() ? StepResult.fail("Bild " + quality + " über " + video.relayHops()
                        + " Repeater passt nicht zur Szene") : StepResult.pending();
            }
            ctx.log("FPV im Betonbau: Bild " + quality + ", " + video.relayHops() + " Repeater im Weg");
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, null);
        }
    }
}
