package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.guide.client.screen.SceneScreen;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scenes.DroneBuildScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

final class CovBatteryPlugShowcase {
    static final String PREFIX = "bildschirmfotos (drone-render): Abdeckung Akku gesteckt – ";
    static final int GHOST_COLOR = 0x9CB4E6FF;
    private static final int OFFSET_X = 72;
    private static final int OFFSET_Z = -88;
    private static final int STAND = 2;
    private static final int FOV = 30;
    private static final double SIDE_DEGREES = 35.0;
    private static final String FUTURE = "cov_plug_shot_future";
    static final AtomicReference<BlockPos> BASE = new AtomicReference<>();
    private static final AtomicReference<Ghost> GHOST = new AtomicReference<>();
    private static int savedFov = -1;
    private static boolean listening;

    private CovBatteryPlugShowcase() {
    }

    record Ghost(DroneBuild build, Vec3 feet, float yaw) {
    }

    static void register() {
        if (!Screenshots.enabled()) {
            return;
        }
        if (!listening) {
            listening = true;
            ClientBus.SUBMIT_WORLD_GEOMETRY.register((collector, poseStack, levelState) -> {
                Ghost ghost = GHOST.get();
                if (ghost == null) {
                    return;
                }
                Vec3 camera = levelState.cameraRenderState.pos;
                poseStack.pushPose();
                poseStack.translate(ghost.feet().x - camera.x, ghost.feet().y - camera.y, ghost.feet().z - camera.z);
                DroneGhost.submit(ghost.build(), DronePaint.DEFAULT, rotation(ghost.yaw()), GHOST_COLOR, poseStack, collector);
                poseStack.popPose();
            });
        }
        SelfTest.clientStep(PREFIX + "Fenster und Platz vorbereiten", 400, CovBatteryPlugShowcase::prepareStep);
        Screenshots.step(PREFIX + "Ghost des 5-Zöllers von hinten", "cov_battery_plugged_ghost_freestyle_5", 25,
                new GhostScene(DroneBuild.FREESTYLE, 26.0, 0.42F));
        Screenshots.step(PREFIX + "Ghost des Racers von hinten (Akku unten)", "cov_battery_plugged_ghost_race_5", 25,
                new GhostScene(DroneBuild.RACER, 14.0, 0.40F));
        Screenshots.step(PREFIX + "Flugschul-Szene mit dem 5-Zöller", "cov_battery_plugged_scene_freestyle_5", 30, new FlightSchoolScene(DroneBuild.FREESTYLE));
        Screenshots.step(PREFIX + "Flugschul-Szene mit dem Racer", "cov_battery_plugged_scene_race_5", 30, new FlightSchoolScene(DroneBuild.RACER));
        SelfTest.clientStep(PREFIX + "aufräumen", 200, CovBatteryPlugShowcase::cleanupStep);
    }

    static Quaternionf rotation(float facingYaw) {
        return new Quaternionf().rotationY((float) Math.toRadians(-facingYaw) + (float) Math.PI);
    }

    static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    static BlockPos base() {
        BlockPos pos = BASE.get();
        if (pos == null) {
            throw new IllegalStateException("Platz nicht vorbereitet");
        }
        return pos;
    }

    static void place(ServerPlayer player, double x, double y, double z) {
        player.teleportTo(player.level(), x, y, z, Set.of(), 0.0F, 0.0F, true);
    }

    static void look(LocalPlayer player, Vec3 target) {
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
        player.setYBodyRot(yaw);
        player.yBodyRotO = yaw;
    }

    static StepResult awaitServer(SelfTest.StepContext ctx, String what) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return StepResult.fail(what + ": keine Server-Aktion");
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    static Vec3 eyeFor(DroneBuild build, Vec3 feet, float droneYaw, double pitchDegrees, float distance) {
        Vec3 plug = BatteryPlugShowcase.plugCenter(build, feet, droneYaw);
        double pitch = Math.toRadians(pitchDegrees);
        double yaw = Math.toRadians(droneYaw + 180.0 + SIDE_DEGREES);
        return plug.add(-Math.sin(yaw) * Math.cos(pitch) * distance, Math.sin(pitch) * distance, Math.cos(yaw) * Math.cos(pitch) * distance);
    }

    static StepResult prepareStep(SelfTest.StepContext ctx) {
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
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                int x = player.getBlockX() + OFFSET_X;
                int z = player.getBlockZ() + OFFSET_Z;
                level.getChunk(x >> 4, z >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BASE.set(new BlockPos(x, y, z));
                for (int k = 0; k < STAND; k++) {
                    level.setBlockAndUpdate(new BlockPos(x, y + k, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                }
                player.setGameMode(GameType.SPECTATOR);
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                place(player, x + 0.5, y + 1.0, z - 2.5);
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Platz vorbereiten");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        BlockPos base = BASE.get();
        if (mc.player == null || mc.level == null || base == null || mc.player.blockPosition().distManhattan(base) > 6) {
            return ctx.tick() > 380 ? StepResult.fail("Spieler kam nicht am Platz an") : StepResult.pending();
        }
        return StepResult.pass();
    }

    static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!ctx.state().containsKey(FUTURE)) {
            GHOST.set(null);
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            if (savedFov > 0) {
                mc.options.fov().set(savedFov);
            }
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            Screenshots.hideMarker(false);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                BlockPos base = BASE.getAndSet(null);
                if (base != null) {
                    for (int k = 0; k < STAND; k++) {
                        if (player.level().getBlockState(base.above(k)).is(Blocks.POLISHED_DEEPSLATE)) {
                            player.level().setBlockAndUpdate(base.above(k), Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                player.setGameMode(GameType.CREATIVE);
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Aufräumen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        return Screenshots.restoreWindow(ctx);
    }

    private static final class GhostScene implements Screenshots.Scene {
        private final DroneBuild build;
        private final double pitchDegrees;
        private final float distance;
        private Vec3 aim = Vec3.ZERO;

        private GhostScene(DroneBuild build, double pitchDegrees, float distance) {
            this.build = build;
            this.pitchDegrees = pitchDegrees;
            this.distance = distance;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                Vec3 feet = Vec3.atBottomCenterOf(base().above(STAND));
                this.aim = BatteryPlugShowcase.plugCenter(this.build, feet, 0.0F);
                Vec3 eye = eyeFor(this.build, feet, 0.0F, this.pitchDegrees, this.distance);
                GHOST.set(new Ghost(this.build, feet, 0.0F));
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.setGameMode(GameType.SPECTATOR);
                    place(player, eye.x, eye.y - player.getEyeHeight(), eye.z);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, this.build.frame().key());
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (mc.player == null || mc.player.position().distanceTo(this.aim) > 3.0) {
                return ctx.tick() > 200 ? StepResult.fail("Spieler kam nicht an") : StepResult.pending();
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
            GHOST.set(null);
            if (savedFov > 0) {
                mc.options.fov().set(savedFov);
            }
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
        }
    }

    private static final class FlightSchoolScene implements Screenshots.Scene {
        private final DroneBuild build;
        private @Nullable Scene scene;

        private FlightSchoolScene(DroneBuild build) {
            this.build = build;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.scene == null) {
                this.scene = SceneBuilder.scene("cov_battery_plug_" + this.build.frame().key())
                        .icon(DroneBuildScene.WORKBENCH)
                        .view(1.2F)
                        .camera(new ScenePos(0.5F, 1.1F, 0.5F), 215.0F, 30.0F, 1.0F)
                        .step("all", 60.0F, s -> {
                            s.at(0.0F).spawn("drone", new ActorSpec.Drone(this.build, true, ActorSpec.Drone.STAGE_SCALE), new ScenePos(0.5F, 1.0F, 0.5F), 0.0F,
                                    Cue.Appear.INSTANT, 0.0F);
                        })
                        .build();
                mc.gui.setScreen(new SceneScreen(this.scene, null));
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof SceneScreen screen)) {
                return StepResult.fail("Szenen-Fenster nicht offen: " + mc.gui.screen());
            }
            if (screen.frames() < 10) {
                return ctx.tick() > 200 ? StepResult.fail("Szene nicht gezeichnet") : StepResult.pending();
            }
            screen.playback().seek(20.0F);
            screen.playback().pause();
            screen.snapView(0.0F, 0.0F, 4.0F);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof SceneScreen) {
                mc.gui.setScreen(null);
            }
            this.scene = null;
        }
    }
}
