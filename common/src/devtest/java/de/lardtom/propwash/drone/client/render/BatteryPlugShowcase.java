package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.client.render.DronePreview;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.client.ShowcaseInventoryScreen;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.rescue.client.RescueController;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class BatteryPlugShowcase {
    static final String PREFIX = "bildschirmfotos (drone-render): Akku gesteckt – ";
    private static final int OFFSET_X = 56;
    private static final int OFFSET_Z = -56;
    private static final int FOV = 30;
    private static final double SIDE_DEGREES = 35.0;
    private static final double MIN_RANGE = 0.25;
    private static final int STAND = 2;
    private static final int GUI_SCALE = 4;
    private static final int EMPTY_HOTBAR_SLOT = 8;
    private static final int CRAFTING_SLOT = 1;
    private static final String FUTURE = "future";
    private static final float SCALED_YAW = 215.0F;
    private static final float SCALED_VIEW_MM = 300.0F;
    private static final AtomicReference<BlockPos> BASE = new AtomicReference<>();
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static int savedFov = -1;

    private BatteryPlugShowcase() {
    }

    static void register() {
        if (!Screenshots.enabled()) {
            return;
        }
        SelfTest.setupStep(PREFIX + "Fenster und Platz vorbereiten", 400, BatteryPlugShowcase::prepareStep);
        for (DroneBuild build : DroneBuild.PRESETS) {
            String frame = build.frame().key();
            Screenshots.step(PREFIX + frame + " schräg von hinten oben", "battery_plugged_" + frame, 25,
                    new RearScene(build, 32.0, 0.0F, 2.4, 0.0F));
        }
        Screenshots.step(PREFIX + "Nahaufnahme des Paars am 5-Zöller", "battery_plugged_closeup_freestyle_5", 25,
                new RearScene(DroneBuild.FREESTYLE, 26.0, 0.0F, 0.0, 0.32F));
        Screenshots.step(PREFIX + "Nahaufnahme des Paars am Race-Rahmen (Akku unten)", "battery_plugged_closeup_race_5", 25,
                new RearScene(DroneBuild.RACER, 14.0, 0.0F, 0.0, 0.30F));
        Screenshots.step(PREFIX + "Drohnen als Item in der GUI", "battery_plugged_item_gui", 30,
                Screenshots.guiScale(GUI_SCALE, new InventoryScene()));
        Screenshots.step(PREFIX + "skalierter Beispielrahmen (transformation) von hinten", "battery_plugged_scaled_frame", 25, new ScaledScene());
        BatteryPlugSeries.register(PREFIX);
        SelfTest.setupStep(PREFIX + "aufräumen", 200, BatteryPlugShowcase::cleanupStep);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static BlockPos base() {
        BlockPos pos = BASE.get();
        if (pos == null) {
            throw new IllegalStateException("Platz nicht vorbereitet");
        }
        return pos;
    }

    private static void place(ServerPlayer player, double x, double y, double z, float yRot, float xRot) {
        player.teleportTo(player.level(), x, y, z, Set.of(), yRot, xRot, true);
    }

    private static void select(ServerPlayer player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        player.connection.send(new ClientboundSetHeldSlotPacket(slot));
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
        player.setYBodyRot(yaw);
        player.yBodyRotO = yaw;
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

    private static void spawnDrone(ServerLevel level, Vec3 pos, float facingYaw, DroneBuild build) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), build);
        drone.loadFromStack(stack);
        drone.snapTo(pos.x, pos.y, pos.z, facingYaw, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(pos, new Quaternionf().rotationY((float) Math.toRadians(-facingYaw) + (float) Math.PI));
        DRONE.set(drone.getId());
    }

    private static void discardDrone(MinecraftServer server) {
        int id = DRONE.getAndSet(-1);
        if (id < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof QuadcopterEntity drone) {
                drone.discard();
            }
        }
    }

    private static StepResult prepareStep(SelfTest.StepContext ctx) {
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
                player.setGameMode(GameType.SPECTATOR);
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                place(player, x + 0.5, y + 1.0, z - 2.5, 0.0F, 20.0F);
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

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
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
                discardDrone(server);
                ServerPlayer player = player(server);
                BlockPos base = BASE.get();
                if (base != null) {
                    for (int k = 0; k < STAND; k++) {
                        if (player.level().getBlockState(base.above(k)).is(Blocks.POLISHED_DEEPSLATE)) {
                            player.level().setBlockAndUpdate(base.above(k), Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                player.getInventory().clearContent();
                select(player, 0);
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

    static Vector3f plugSpot(DroneAssembly assembly) {
        PlugAnchors.Box plug = null;
        for (PlugAnchors.Box box : DroneVisuals.resources().batteryPlug(assembly.battery().model()).boxes()) {
            plug = plug == null ? box : plug.union(box);
        }
        Vector3f spot = plug == null ? new Vector3f(0.0F, 0.0F, 40.0F)
                : new Vector3f((plug.minX() + plug.maxX()) * 0.5F, (plug.minY() + plug.maxY()) * 0.5F, plug.minZ());
        return spot.add(assembly.battery().position());
    }

    static Vec3 plugCenter(DroneBuild build, Vec3 feet, float facingYaw) {
        DroneAssembly assembly = DroneVisuals.resources().assembly(build);
        Vector3f local = plugSpot(assembly);
        local.y -= assembly.hull().min().y();
        new Quaternionf().rotationY((float) Math.toRadians(-facingYaw) + (float) Math.PI).transform(local);
        float scale = DroneVisualScale.blocksPerMillimetre(DroneRenderConfig.get().visualScale);
        return feet.add(local.x * scale, local.y * scale, local.z * scale);
    }

    private static final class RearScene implements Screenshots.Scene {
        private final DroneBuild build;
        private final double pitchDegrees;
        private final float droneYaw;
        private final double radii;
        private final float distance;
        private Vec3 aim = Vec3.ZERO;

        private RearScene(DroneBuild build, double pitchDegrees, float droneYaw, double radii, float distance) {
            this.build = build;
            this.pitchDegrees = pitchDegrees;
            this.droneYaw = droneYaw;
            this.radii = radii;
            this.distance = distance;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                BlockPos stand = base();
                Vec3 feet = Vec3.atBottomCenterOf(stand.above(STAND));
                Vec3 plug = plugCenter(this.build, feet, this.droneYaw);
                double range = this.distance > 0.0F ? this.distance
                        : Math.max(MIN_RANGE, DroneVisuals.estimatedRadiusBlocks(this.build) * this.radii);
                Vec3 center = this.distance > 0.0F ? plug : feet.add(0.0, DroneVisuals.estimatedRadiusBlocks(this.build) * 0.25, 0.0);
                this.aim = center;
                double pitch = Math.toRadians(this.pitchDegrees);
                double yaw = Math.toRadians(this.droneYaw + 180.0 + SIDE_DEGREES);
                Vec3 eye = center.add(-Math.sin(yaw) * Math.cos(pitch) * range, Math.sin(pitch) * range, Math.cos(yaw) * Math.cos(pitch) * range);
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    for (int k = 0; k < STAND; k++) {
                        level.setBlockAndUpdate(stand.above(k), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                    }
                    player.setGameMode(GameType.SPECTATOR);
                    place(player, eye.x, eye.y - player.getEyeHeight(), eye.z, 0.0F, 0.0F);
                    spawnDrone(level, feet, this.droneYaw, this.build);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, this.build.frame().key());
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (mc.level == null || !(mc.level.getEntity(DRONE.get()) instanceof QuadcopterEntity drone) || drone.build().frame() != this.build.frame()) {
                return ctx.tick() > 200 ? StepResult.fail(this.build.frame().key() + ": Drohne kam nicht beim Client an") : StepResult.pending();
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
            ctx.onServer(BatteryPlugShowcase::discardDrone);
        }
    }

    private static final class InventoryScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.setGameMode(GameType.SURVIVAL);
                    Inventory inventory = player.getInventory();
                    inventory.clearContent();
                    int slot = 0;
                    for (DroneBuild build : DroneBuild.PRESETS) {
                        inventory.setItem(slot, ContentTab.presetDrone(build));
                        inventory.setItem(9 + slot, ContentTab.presetDrone(build));
                        slot++;
                    }
                    select(player, EMPTY_HOTBAR_SLOT);
                    BlockPos base = base();
                    place(player, base.getX() + 0.5, base.getY() + STAND + 1.0, base.getZ() - 2.5, 0.0F, 10.0F);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Inventar");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (mc.player == null || mc.player.hasInfiniteMaterials() || mc.player.getInventory().getItem(9).isEmpty()
                    || mc.player.getInventory().getSelectedSlot() != EMPTY_HOTBAR_SLOT) {
                return ctx.tick() > 200 ? StepResult.fail("Drohnen kamen nicht im Inventar an") : StepResult.pending();
            }
            RescueController.INSTANCE.clearHome();
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            if (!(mc.gui.screen() instanceof ShowcaseInventoryScreen)) {
                mc.gui.setScreen(new ShowcaseInventoryScreen(mc.player, CRAFTING_SLOT));
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            ctx.onServer(server -> {
                ServerPlayer player = player(server);
                player.getInventory().clearContent();
                player.setGameMode(GameType.SPECTATOR);
            });
        }
    }

    private static final class ScaledScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof ScaledFrameScreen)) {
                mc.gui.setScreen(new ScaledFrameScreen(BatteryPlugSelfTests.scaledBuild()));
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Screenshots.parkMouse(mc);
            Screenshots.clearOverlays(mc);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof ScaledFrameScreen) {
                mc.gui.setScreen(null);
            }
        }
    }

    private static final class ScaledFrameScreen extends Screen {
        private final DroneBuild build;

        private ScaledFrameScreen(DroneBuild build) {
            super(Component.empty());
            this.build = build;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            ThemeDraw.backdrop(g, this.width, this.height);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            ThemeDraw.previewStage(g, 0, 0, this.width, this.height);
            DroneRenderState state = DroneVisuals.previewState(this.build);
            DroneAssembly assembly = DroneVisuals.resources().assembly(this.build);
            if (state != null) {
                Vector3f spot = plugSpot(assembly);
                Vector3f focus = new Vector3f(spot.x, (spot.y + assembly.stack().position().y()) * 0.5F, spot.z);
                float scale = DroneVisuals.scaleForPixelsPerMillimetre(state, this.width / SCALED_VIEW_MM);
                Quaternionf rotation = DronePreview.rotation(SCALED_YAW);
                Vector3f translation = rotation.transform(focus.sub(state.pivot).mul(DroneVisualScale.blocksPerMillimetre(state.visualScale))).negate();
                g.entity(state, scale, translation, rotation, null, 0, 0, this.width, this.height);
            }
            String label = String.format(Locale.ROOT, "%s + %s + %s: Stecker %s", this.build.frame().id(), this.build.battery().key(),
                    this.build.stack().key(), assembly.powerLead().present() ? "ausgeblendet" : "sichtbar (Rückfall)");
            ThemeText.text(g, this.font, label, 6, 6, Palette.TEXT);
            super.extractRenderState(g, mouseX, mouseY, a);
        }
    }
}
