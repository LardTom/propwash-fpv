package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.render.DroneRenderState;
import de.lardtom.propwash.drone.client.render.DroneVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class AccessoryDisplayShowcase {
    private static final int OFFSET_X = 40;
    private static final int OFFSET_Z = 60;
    private static final int STAND = 2;
    private static final int FOV = 30;
    private static final double RANGE = 2.9;
    private static final double LIFT = 0.35;
    private static final String FUTURE = "future";
    private static final AtomicReference<BlockPos> BASE = new AtomicReference<>();
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final AtomicReference<GameType> MODE = new AtomicReference<>();
    private static int savedFov = -1;

    private AccessoryDisplayShowcase() {
    }

    static void register(String group) {
        if (!Screenshots.enabled()) {
            return;
        }
        SelfTest.clientStep(group + "Bildschirmfotos vorbereiten", 400, AccessoryDisplayShowcase::prepareStep);
        Screenshots.step(group + "Bildschirmfoto Speer und Rakete von der Seite", "accessory_display_side", 30, new Scene(true, 90.0, 12.0));
        Screenshots.step(group + "Bildschirmfoto Speer und Rakete schräg von vorn", "accessory_display_front", 30, new Scene(true, 35.0, 18.0));
        Screenshots.step(group + "Bildschirmfoto nach dem Boost: Rakete fort, Speer bleibt", "accessory_display_boosted", 30, new Scene(false, 90.0, 12.0));
        SelfTest.clientStep(group + "Bildschirmfotos aufräumen", 300, AccessoryDisplayShowcase::cleanupStep);
    }

    private static StepResult awaitServer(SelfTest.StepContext ctx, String what) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return StepResult.fail(what + ": keine Server-Aktion");
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    private static StepResult prepareStep(SelfTest.StepContext ctx) {
        if (!AccessoryGapsSelfTests.ready()) {
            return StepResult.pass();
        }
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            StepResult resized = Screenshots.resizeWindow(ctx);
            if (resized.status() != StepResult.Status.PASS) {
                return resized;
            }
            Screenshots.hideMarker(true);
            savedFov = mc.options.fov().get();
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = InteractSelfTests.player(server);
                ServerLevel level = player.level();
                int x = player.getBlockX() + OFFSET_X;
                int z = player.getBlockZ() + OFFSET_Z;
                level.getChunk(x >> 4, z >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BASE.set(new BlockPos(x, y, z));
                MODE.compareAndSet(null, player.gameMode());
                player.setGameMode(GameType.SPECTATOR);
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.teleportTo(level, x + 0.5, y + 1.0, z + 3.5, Set.of(), 180.0F, 10.0F, true);
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Platz vorbereiten");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        BlockPos base = BASE.get();
        if (mc.player == null || mc.level == null || base == null || mc.player.blockPosition().distManhattan(base) > 8) {
            return ctx.tick() > 380 ? StepResult.fail("Spieler kam nicht am Platz an") : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        if (!AccessoryGapsSelfTests.ready()) {
            return StepResult.pass();
        }
        Minecraft mc = Minecraft.getInstance();
        if (!ctx.state().containsKey(FUTURE)) {
            if (savedFov > 0) {
                mc.options.fov().set(savedFov);
            }
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            Screenshots.hideMarker(false);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                discardDrone(server);
                ServerPlayer player = InteractSelfTests.player(server);
                BlockPos base = BASE.get();
                if (base != null) {
                    for (int k = 0; k < STAND; k++) {
                        if (player.level().getBlockState(base.above(k)).is(Blocks.POLISHED_DEEPSLATE)) {
                            player.level().setBlockAndUpdate(base.above(k), Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                GameType mode = MODE.getAndSet(null);
                player.setGameMode(mode == null ? GameType.CREATIVE : mode);
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Aufräumen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        return Screenshots.restoreWindow(ctx);
    }

    private static void discardDrone(MinecraftServer server) {
        int id = DRONE.getAndSet(-1);
        if (id < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                drone.discard();
            }
        }
    }

    private static void look(LocalPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
    }

    private static final class Scene implements Screenshots.Scene {
        private final boolean rocket;
        private final double azimuth;
        private final double elevation;
        private Vec3 aim = Vec3.ZERO;

        private Scene(boolean rocket, double azimuth, double elevation) {
            this.rocket = rocket;
            this.azimuth = azimuth;
            this.elevation = elevation;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            if (!AccessoryGapsSelfTests.ready()) {
                return StepResult.fail("Beispiel-Add-on aus");
            }
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            BlockPos base = BASE.get();
            if (base == null) {
                return StepResult.fail("Platz nicht vorbereitet");
            }
            DroneBuild build = AccessoryGapsSelfTests.displayBuild();
            if (!state.containsKey(FUTURE)) {
                Vec3 feet = Vec3.atBottomCenterOf(base.above(STAND)).add(0.0, LIFT, 0.0);
                double radius = DroneVisuals.estimatedRadiusBlocks(build);
                Vec3 center = feet.add(0.0, radius * 0.15, 0.0);
                this.aim = center;
                double range = radius * RANGE;
                double side = Math.toRadians(this.azimuth);
                double up = Math.toRadians(this.elevation);
                Vec3 eye = center.add(Math.cos(side) * Math.cos(up) * range, Math.sin(up) * range, Math.sin(side) * Math.cos(up) * range);
                state.put(FUTURE, ctx.onServer(server -> {
                    discardDrone(server);
                    ServerPlayer player = InteractSelfTests.player(server);
                    ServerLevel level = player.level();
                    for (int k = 0; k < STAND; k++) {
                        level.setBlockAndUpdate(base.above(k), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                    }
                    player.setGameMode(GameType.SPECTATOR);
                    player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, Set.of(), 0.0F, 0.0F, true);
                    QuadcopterEntity drone = AccessoryGapsSelfTests.park(level, build, feet);
                    drone.setNoGravity(true);
                    DRONE.set(drone.getId());
                    PropwashApi.accessories().inventory(drone, AccessorySlot.BOTTOM).orElseThrow().set(0, new ItemStack(Items.IRON_SPEAR));
                    if (this.rocket) {
                        PropwashApi.accessories().inventory(drone, AccessorySlot.TOP).orElseThrow().set(0, new ItemStack(Items.FIREWORK_ROCKET));
                    }
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Drohne setzen");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (mc.level == null || !(mc.level.getEntity(DRONE.get()) instanceof QuadcopterEntity drone)) {
                return ctx.tick() > 200 ? StepResult.fail("Drohne kam nicht beim Client an") : StepResult.pending();
            }
            DroneRenderState shown = AccessoryGapsSelfTests.extract(drone);
            if (shown == null || !AccessoryGapsSelfTests.spearAndRocket(shown, this.rocket)) {
                return ctx.tick() > 200 ? StepResult.fail("Inhalt nicht im Render-Zustand: " + AccessoryGapsSelfTests.describe(shown)) : StepResult.pending();
            }
            mc.options.fov().set(FOV);
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                look(mc.player, this.aim);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (savedFov > 0) {
                mc.options.fov().set(savedFov);
            }
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
        }
    }
}
