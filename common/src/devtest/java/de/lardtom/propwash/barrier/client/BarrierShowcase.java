package de.lardtom.propwash.barrier.client;

import de.lardtom.propwash.barrier.BarrierKind;
import de.lardtom.propwash.barrier.DroneBarrierBlock;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.TooltipShift;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.client.ShowcaseInventoryScreen;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class BarrierShowcase {
    private static final String PREFIX = "bildschirmfotos: Drohnen-Barrieren – ";
    private static final String FUTURE = "barrier_shot_future";
    private static final int TOOLTIP_SLOT = 9;
    private static final int SCENE_X = 7;
    private static final int SCENE_Z = 28;
    private static final int OUTLINE_DX = 3;
    private static final int OUTLINE_DZ = 2;
    private static final AtomicLong CLOCK = new AtomicLong(-1L);
    private static final AtomicInteger DRONE = new AtomicInteger(-1);

    private BarrierShowcase() {
    }

    static void register() {
        if (!Screenshots.enabled()) {
            return;
        }
        Screenshots.step(PREFIX + "Symbole mit der Drohnen-durchlässigen Barriere in der Hand", "barriers_markers", 100, new WorldScene(true, false));
        Screenshots.step(PREFIX + "ohne Barriere in der Hand unsichtbar", "barriers_hidden", 20, new WorldScene(false, false));
        Screenshots.step(PREFIX + "Umriss der Drohnen-Sperre im Kreativmodus ohne Barriere in der Hand", "barriers_outline", 20,
                new WorldScene(false, true));
        Screenshots.step(PREFIX + "Shift-Tooltip Drohnen-durchlässige Barriere", "tooltip_drone_pass_barrier", 20, new TooltipScene(BarrierKind.DRONE_PASS));
        Screenshots.step(PREFIX + "Shift-Tooltip Drohnen-Sperre", "tooltip_drone_block_barrier", 20, new TooltipScene(BarrierKind.DRONE_BLOCK));
        SelfTest.switchStep(PREFIX + "Sprache Deutsch", PREFIX + "Sprache zurücksetzen", 1200, ctx -> Screenshots.language(ctx, "de_de"));
        Screenshots.step(PREFIX + "Shift-Tooltip Drohnen-durchlässige Barriere deutsch", "tooltip_drone_pass_barrier_de", 20,
                new TooltipScene(BarrierKind.DRONE_PASS));
        Screenshots.step(PREFIX + "Shift-Tooltip Drohnen-Sperre deutsch", "tooltip_drone_block_barrier_de", 20,
                new TooltipScene(BarrierKind.DRONE_BLOCK));
        SelfTest.clientStep(PREFIX + "Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
        SelfTest.clientStep(PREFIX + "aufräumen", 400, BarrierShowcase::cleanupStep);
    }

    private static Vec3 stand(BlockPos o, boolean close) {
        return new Vec3(o.getX() + (close ? SCENE_X - OUTLINE_DX : 0) + 0.5, o.getY(), o.getZ() + SCENE_Z + 0.5);
    }

    private static BlockPos outlined(BlockPos o) {
        return o.offset(SCENE_X, 1, SCENE_Z + OUTLINE_DZ);
    }

    private static void build(MinecraftServer server, boolean holding, boolean close) {
        BlockPos o = BarrierSite.prepare(server);
        ServerPlayer player = BarrierSite.player(server);
        ServerLevel level = player.level();
        for (int dy = 0; dy < 4; dy++) {
            for (int dz = -4; dz <= 4; dz++) {
                boolean opening = Math.abs(dz) <= 1 && dy < 3;
                BarrierSite.set(level, o.offset(SCENE_X, dy, SCENE_Z + dz), BarrierSite.state(opening ? BarrierKind.DRONE_PASS : BarrierKind.DRONE_BLOCK));
            }
        }
        if (!(DRONE.get() >= 0 && level.getEntity(DRONE.get()) instanceof QuadcopterEntity)) {
            QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                throw new IllegalStateException("Drohne nicht erzeugt");
            }
            Vec3 pos = new Vec3(o.getX() + 2.0, o.getY() + 1.3, o.getZ() + SCENE_Z + 0.8);
            drone.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            drone.setBuild(DroneBuild.FREESTYLE);
            drone.setNoGravity(true);
            level.addFreshEntity(drone);
            drone.resetTo(pos, new Quaternionf().rotationY((float) (-Math.PI / 2.0)).rotateX(-0.2F));
            DRONE.set(drone.getId());
        }
        Optional<Holder<WorldClock>> clock = level.dimensionType().defaultClock();
        if (clock.isPresent()) {
            CLOCK.compareAndSet(-1L, server.clockManager().getInstance(clock.get()).totalTicks());
            server.clockManager().moveToTimeMarker(clock.get(), ClockTimeMarkers.NOON);
        }
        player.setGameMode(GameType.CREATIVE);
        player.setItemSlot(EquipmentSlot.MAINHAND, holding ? BarrierSite.stack(BarrierKind.DRONE_PASS) : ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        Vec3 stand = stand(o, close);
        if (close) {
            Vec3 look = Vec3.atCenterOf(outlined(o)).subtract(stand.add(0.0, player.getEyeHeight(), 0.0));
            BarrierSite.teleport(player, stand, (float) Math.toDegrees(Math.atan2(-look.x, look.z)),
                    (float) Math.toDegrees(-Math.atan2(look.y, look.horizontalDistance())));
        } else {
            BarrierSite.teleport(player, stand, -90.0F, 8.0F);
        }
    }

    private static final class WorldScene implements Screenshots.Scene {
        private final boolean holding;
        private final boolean close;

        private WorldScene(boolean holding, boolean close) {
            this.holding = holding;
            this.close = close;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(FUTURE)) {
                boolean hold = this.holding;
                boolean near = this.close;
                ctx.state().put(FUTURE, ctx.onServer(server -> build(server, hold, near)));
                return StepResult.pending();
            }
            StepResult waiting = BarrierSite.await(ctx, FUTURE, "Szene");
            if (waiting != null) {
                return waiting;
            }
            BlockPos o = BarrierSite.originOrNull();
            if (mc.player == null || mc.level == null || o == null) {
                return StepResult.pending();
            }
            boolean ready = mc.level.getBlockState(o.offset(SCENE_X, 0, SCENE_Z)).getBlock() instanceof DroneBarrierBlock
                    && mc.player.position().distanceTo(stand(o, this.close)) < 0.1 && mc.player.isCreative()
                    && mc.player.getMainHandItem().isEmpty() != this.holding
                    && (!this.close || mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                    && hit.getBlockPos().equals(outlined(o)));
            if (!ready) {
                return ctx.tick() > 400 ? StepResult.fail("Szene kommt beim Client nicht an") : StepResult.pending();
            }
            mc.gui.setScreen(null);
            if (!this.holding) {
                mc.particleEngine.clearParticles();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            if (!this.holding) {
                Minecraft.getInstance().particleEngine.clearParticles();
            }
        }
    }

    private static final class TooltipScene implements Screenshots.Scene {
        private final BarrierKind kind;
        private volatile @Nullable ItemStack previous;
        private volatile @Nullable GameType previousMode;

        private TooltipScene(BarrierKind kind) {
            this.kind = kind;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            ItemStack stack = BarrierSite.stack(this.kind);
            if (!ctx.state().containsKey(FUTURE)) {
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = BarrierSite.player(server);
                    player.closeContainer();
                    this.previousMode = player.gameMode();
                    player.setGameMode(GameType.SURVIVAL);
                    this.previous = player.getInventory().getItem(TOOLTIP_SLOT).copy();
                    player.getInventory().setItem(TOOLTIP_SLOT, stack.copy());
                }));
                return StepResult.pending();
            }
            StepResult waiting = BarrierSite.await(ctx, FUTURE, "Tooltip");
            if (waiting != null) {
                return waiting;
            }
            ItemStack shown = mc.player == null ? ItemStack.EMPTY : mc.player.getInventory().getItem(TOOLTIP_SLOT);
            if (!ItemStack.isSameItemSameComponents(shown, stack) || mc.player.hasInfiniteMaterials()) {
                return ctx.tick() > 200 ? StepResult.fail("Tooltip: Barriere kommt nicht an") : StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ShowcaseInventoryScreen)) {
                TooltipShift.override(true);
                mc.gui.setScreen(new ShowcaseInventoryScreen(mc.player, TOOLTIP_SLOT));
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            TooltipShift.override(null);
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            ItemStack old = this.previous;
            GameType mode = this.previousMode;
            ctx.onServer(server -> {
                ServerPlayer player = BarrierSite.player(server);
                player.getInventory().setItem(TOOLTIP_SLOT, old == null ? ItemStack.EMPTY : old);
                player.setGameMode(mode == null ? GameType.CREATIVE : mode);
            });
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        if (!ctx.state().containsKey(FUTURE)) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerLevel level = BarrierSite.player(server).level();
                int id = DRONE.getAndSet(-1);
                Entity drone = id >= 0 ? level.getEntity(id) : null;
                if (drone != null) {
                    drone.discard();
                }
                long ticks = CLOCK.getAndSet(-1L);
                Optional<Holder<WorldClock>> clock = level.dimensionType().defaultClock();
                if (ticks >= 0L && clock.isPresent()) {
                    server.clockManager().setTotalTicks(clock.get(), ticks);
                }
                if (BarrierSite.originOrNull() != null) {
                    BarrierSite.restore(server);
                }
            }));
            return StepResult.pending();
        }
        StepResult waiting = BarrierSite.await(ctx, FUTURE, "Aufräumen");
        return waiting != null ? waiting : StepResult.pass();
    }
}
