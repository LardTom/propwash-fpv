package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.api.part.FrameShape;
import de.lardtom.propwash.apiimpl.ApiExtensions;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.client.ShowcaseInventoryScreen;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.rescue.client.RescueController;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class PartsRenderShowcase {
    private static final int OFFSET = -40;
    private static final float HOVER_HEIGHT = 2.2F;
    private static final double LOOSE_ITEM_RANGE = 48.0;
    private static final int CLOSE_UP_FOV = 30;
    private static final double CLOSE_UP_PITCH_DEGREES = 28.0;
    private static final double CLOSE_UP_RADII = 2.9;
    private static final double CLOSE_UP_MIN_DISTANCE = 1.0;
    private static final double CLOSE_UP_AIM = 0.2;
    private static final int EMPTY_CRAFTING_SLOT = 1;
    private static final int EMPTY_HOTBAR_SLOT = 8;
    private static final int LAKE_HALF_WIDTH = 32;
    private static final int LAKE_BEHIND = 8;
    private static final int LAKE_AHEAD = 64;
    private static final int LAKE_DEPTH = 2;
    private static final int ISLAND_HALF_WIDTH = 1;
    private static final int ISLAND_HALF_LENGTH = 3;
    private static final int STOP_SETTLE_TICKS = 5;
    private static final String FUTURE = "future";
    private static final String SINCE = "since";
    private static final String LAST_Y = "last_y";
    private static final String INTEGRAL = "integral";
    private static final String TARGET_Y = "target_y";
    private static final String BASELINE = "baseline";
    private static final String RACE_BATTERY = "bildschirmfotos: Race-Akku unter dem Rahmen – ";
    private static final Identifier JMP_WHOOP_75 = Identifier.fromNamespaceAndPath("justmoreparts", "whoop_75_duct");
    private static final Identifier JMP_WHOOP_85 = Identifier.fromNamespaceAndPath("justmoreparts", "whoop_85_duct");
    private static final Identifier JMP_CINEWHOOP_25 = Identifier.fromNamespaceAndPath("justmoreparts", "cw_25_duct");
    private static final Identifier JMP_CINEWHOOP_35 = Identifier.fromNamespaceAndPath("justmoreparts", "cw_35_duct");
    private static final Identifier JMP_RACE_SLIM = Identifier.fromNamespaceAndPath("justmoreparts", "race_5_stretched");
    private static final AtomicReference<BlockPos> BASE = new AtomicReference<>();
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static int savedFov = -1;
    private static int waterBlursBefore = -1;
    private static int waterBlursAfter = -1;
    private static final List<SavedBlock> LAKE = new ArrayList<>();
    private static DroneRenderConfig savedConfig;

    private PartsRenderShowcase() {
    }

    static void register() {
        if (!Screenshots.enabled()) {
            return;
        }
        SelfTest.setupStep("bildschirmfotos: Nahaufnahmen und FPV-Bilder – Fenster und Platz vorbereiten", 400, PartsRenderShowcase::prepareStep);
        Screenshots.step("bildschirmfotos: Deadcat-Rahmen aus dem Beispiel-Addon", "drone_closeup_deadcat", 25,
                new CloseUpScene(frame -> frame.definition().shape() == FrameShape.DEADCAT, "Deadcat", () -> DronePaint.DEFAULT));
        Screenshots.step("bildschirmfotos: X-Class 13 Zoll aus dem Beispiel-Addon", "drone_closeup_xclass", 25,
                new CloseUpScene(frame -> frame.definition().role() == FrameRole.X_CLASS, "X-Class",
                        () -> Conversions.paint(ExampleAddon.xclass().paint())));
        Screenshots.step("bildschirmfotos: Props im FPV-Bild eines 5-Zöllers", "fpv_props_in_view", 20, new FpvPropsScene(false));
        Screenshots.step("bildschirmfotos: drehende Props im FPV-Bild über Wasser", "fpv_props_over_water", 20, new FpvPropsScene(true));
        SelfTest.clientStep("drone-render: Prop-Scheiben über Wasser wurden gezeichnet", 1, ctx -> StepResult.check(
                waterBlursBefore >= 0 && waterBlursAfter > waterBlursBefore,
                "im FPV-Flug über Wasser keine drehenden Props gezeichnet (" + waterBlursBefore + " → " + waterBlursAfter + ")"));
        fpvShot("Whoop", "fpv_props_whoop", () -> DroneBuild.WHOOP);
        fpvShot("Cinewhoop", "fpv_props_cinewhoop", () -> DroneBuild.CINEWHOOP);
        fpvShot("Race-5-Zöller", "fpv_props_race", () -> DroneBuild.RACER);
        fpvShot("Long-Range-7-Zöller", "fpv_props_long_range", () -> DroneBuild.LONG_RANGE);
        fpvShot("Stretched-X aus dem Beispiel-Addon", "fpv_props_stretched", () -> Conversions.build(ExampleAddon.stretched().build()).orElseThrow());
        fpvShot("Deadcat aus dem Beispiel-Addon", "fpv_props_deadcat", () -> Conversions.build(ExampleAddon.deadcat().build()).orElseThrow());
        guardShots("Whoop 65 mm", "fpv_guards_whoop_65", () -> Optional.of(DroneBuild.WHOOP), () -> DronePaint.DEFAULT);
        guardShots("Cinewhoop 3 Zoll", "fpv_guards_cinewhoop_3", () -> Optional.of(DroneBuild.CINEWHOOP), () -> DronePaint.DEFAULT);
        guardShots("Whoop 75 mm aus Just More Parts", "fpv_guards_jmp_whoop_75", () -> addonPreset(JMP_WHOOP_75), () -> addonPaint(JMP_WHOOP_75));
        guardShots("Cinewhoop 3,5 Zoll aus Just More Parts", "fpv_guards_jmp_cinewhoop_35", () -> addonPreset(JMP_CINEWHOOP_35),
                () -> addonPaint(JMP_CINEWHOOP_35));
        closeUp("Whoop 65 mm", "drone_closeup_whoop_65", () -> Optional.of(DroneBuild.WHOOP), () -> DronePaint.DEFAULT);
        closeUp("Cinewhoop 3 Zoll", "drone_closeup_cinewhoop_3", () -> Optional.of(DroneBuild.CINEWHOOP), () -> DronePaint.DEFAULT);
        closeUp("Whoop 75 mm aus Just More Parts", "drone_closeup_jmp_whoop_75", () -> addonPreset(JMP_WHOOP_75), () -> addonPaint(JMP_WHOOP_75));
        closeUp("Whoop 85 mm aus Just More Parts", "drone_closeup_jmp_whoop_85", () -> addonPreset(JMP_WHOOP_85), () -> addonPaint(JMP_WHOOP_85));
        closeUp("Cinewhoop 2,5 Zoll aus Just More Parts", "drone_closeup_jmp_cinewhoop_25", () -> addonPreset(JMP_CINEWHOOP_25),
                () -> addonPaint(JMP_CINEWHOOP_25));
        closeUp("Cinewhoop 3,5 Zoll aus Just More Parts", "drone_closeup_jmp_cinewhoop_35", () -> addonPreset(JMP_CINEWHOOP_35),
                () -> addonPaint(JMP_CINEWHOOP_35));
        Screenshots.step(RACE_BATTERY + "Nahaufnahme", "race_battery_closeup", 25,
                new CloseUpScene(() -> Optional.of(DroneBuild.RACER), "Race 5 Zoll", () -> DronePaint.DEFAULT));
        Screenshots.step(RACE_BATTERY + "von der Seite", "race_battery_side", 25,
                new CloseUpScene(() -> Optional.of(DroneBuild.RACER), "Race 5 Zoll", () -> DronePaint.DEFAULT, 4.0, 2, 90.0F));
        Screenshots.step(RACE_BATTERY + "schräg von vorn", "race_battery_front", 25,
                new CloseUpScene(() -> Optional.of(DroneBuild.RACER), "Race 5 Zoll", () -> DronePaint.DEFAULT, 10.0, 2, 160.0F));
        Screenshots.step(RACE_BATTERY + "Schwebeflug von unten", "race_battery_hover_below", 25,
                new LosHoverScene(() -> DroneBuild.RACER, 2.8, 1.5, 120.0F));
        Screenshots.optionalStep(RACE_BATTERY + "Race 5 Zoll (Slim) aus Just More Parts", "race_battery_jmp_slim", 25,
                () -> addonPreset(JMP_RACE_SLIM).isPresent(),
                new CloseUpScene(() -> addonPreset(JMP_RACE_SLIM), "Race 5 Zoll (Slim)", () -> addonPaint(JMP_RACE_SLIM), 4.0, 2, 90.0F));
        Screenshots.step(RACE_BATTERY + "Freestyle zum Vergleich von unten", "race_battery_hover_below_freestyle", 25,
                new LosHoverScene(() -> DroneBuild.FREESTYLE, 2.8, 1.5, 120.0F));
        Screenshots.step("bildschirmfotos: Race-Preset – True-X von oben", "race_preset_top", 25,
                new CloseUpScene(() -> Optional.of(DroneBuild.RACER), "Race 5 Zoll", () -> DronePaint.DEFAULT, 62.0, 2, 180.0F, 0.85));
        Screenshots.step("bildschirmfotos: Inventar mit Teilen des Beispiel-Addons", "inventory_addon_parts", 30, new InventoryScene());
        SelfTest.setupStep("bildschirmfotos: Nahaufnahmen und FPV-Bilder – aufräumen", 200, PartsRenderShowcase::cleanupStep);
    }

    private static void fpvShot(String label, String file, Supplier<DroneBuild> build) {
        Screenshots.step("bildschirmfotos: Props im FPV-Bild – " + label, file, 20, new FpvPropsScene(false, build));
    }

    private static void guardShots(String label, String file, Supplier<Optional<DroneBuild>> build, Supplier<DronePaint> paint) {
        for (float tilt : FpvGuardSelfTests.TILTS) {
            int degrees = Math.round(tilt);
            Supplier<DroneBuild> creative = () -> FpvGuardSelfTests.withCreativeFc(build.get().orElseThrow());
            Screenshots.optionalStep("bildschirmfotos: Guards im FPV-Bild – " + label + " bei " + degrees + "°", file + "_" + degrees, 20,
                    () -> build.get().isPresent(), new FpvPropsScene(false, creative, tilt, paint));
        }
    }

    private static void closeUp(String label, String file, Supplier<Optional<DroneBuild>> build, Supplier<DronePaint> paint) {
        Screenshots.optionalStep("bildschirmfotos: Nahaufnahme " + label, file, 25, () -> build.get().isPresent(),
                new CloseUpScene(build, label, paint));
    }

    static DronePaint addonPaint(Identifier frame) {
        for (DronePreset preset : ApiExtensions.registeredPresets()) {
            Optional<DroneBuild> build = Conversions.build(preset.data().build());
            if (build.isPresent() && build.get().frame().id().equals(frame)) {
                return Conversions.paint(preset.data().paint());
            }
        }
        return DronePaint.DEFAULT;
    }

    static Optional<DroneBuild> addonPreset(Identifier frame) {
        for (DronePreset preset : ApiExtensions.registeredPresets()) {
            Optional<DroneBuild> build = Conversions.build(preset.data().build());
            if (build.isPresent() && build.get().frame().id().equals(frame)) {
                return build;
            }
        }
        return Optional.empty();
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

    private static void look(LocalPlayer player, double x, double y, double z) {
        Vec3 eye = player.getEyePosition();
        double dx = x - eye.x;
        double dy = y - eye.y;
        double dz = z - eye.z;
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

    private static QuadcopterEntity spawnDrone(ServerLevel level, Vec3 pos, float facingYaw, DroneBuild build) {
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
        return drone;
    }

    private static void discardDrone(MinecraftServer server) {
        int id = DRONE.getAndSet(-1);
        if (id < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof QuadcopterEntity drone) {
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                drone.discard();
            }
        }
    }

    static Optional<FrameType> exampleFrame(Predicate<FrameType> accepts) {
        for (FrameType frame : FrameType.values()) {
            if (!frame.builtin() && PartsRenderSelfTests.EXAMPLE_NAMESPACE.equals(frame.id().getNamespace()) && accepts.test(frame)) {
                return Optional.of(frame);
            }
        }
        return Optional.empty();
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
            savedConfig = DroneRenderConfig.get().copy();
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                int x = player.getBlockX() + OFFSET;
                int z = player.getBlockZ() + OFFSET;
                level.getChunk(x >> 4, z >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BASE.set(new BlockPos(x, y, z));
                player.setGameMode(GameType.CREATIVE);
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                place(player, x + 0.5, y, z + 0.5, 0.0F, 0.0F);
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Platz vorbereiten");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        BlockPos base = BASE.get();
        if (mc.player == null || mc.level == null || base == null || mc.player.blockPosition().distManhattan(base) > 2) {
            return ctx.tick() > 380 ? StepResult.fail("Spieler kam nicht am Platz an") : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            PilotSession.INSTANCE.clearTestInput();
            if (PilotSession.INSTANCE.isActive()) {
                PilotSession.INSTANCE.requestStop();
            }
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            if (savedFov > 0) {
                mc.options.fov().set(savedFov);
            }
            if (savedConfig != null) {
                DroneRenderConfig.CONFIG.set(savedConfig);
            }
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            Screenshots.hideMarker(false);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                discardDrone(server);
                ServerPlayer player = player(server);
                restoreLake(player.level());
                BlockPos base = BASE.get();
                if (base != null) {
                    for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, 0, -2), base.offset(2, 2, 4))) {
                        if (player.level().getBlockState(pos).is(Blocks.POLISHED_DEEPSLATE)) {
                            player.level().setBlockAndUpdate(pos.immutable(), Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                player.getInventory().clearContent();
                select(player, 0);
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.setGameMode(GameType.CREATIVE);
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Aufräumen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        if (PilotSession.INSTANCE.isActive()) {
            return StepResult.pending();
        }
        return Screenshots.restoreWindow(ctx);
    }

    private static final class CloseUpScene implements Screenshots.Scene {
        private final @Nullable Predicate<FrameType> accepts;
        private final @Nullable Supplier<Optional<DroneBuild>> builds;
        private final String what;
        private final Supplier<DronePaint> paint;
        private final double pitchDegrees;
        private final int standHeight;
        private final float droneYaw;
        private final double minDistance;
        private double aimHeight;

        private CloseUpScene(Predicate<FrameType> accepts, String what, Supplier<DronePaint> paint) {
            this.accepts = accepts;
            this.builds = null;
            this.what = what;
            this.paint = paint;
            this.pitchDegrees = CLOSE_UP_PITCH_DEGREES;
            this.standHeight = 1;
            this.droneYaw = 215.0F;
            this.minDistance = CLOSE_UP_MIN_DISTANCE;
        }

        private CloseUpScene(Supplier<Optional<DroneBuild>> builds, String what, Supplier<DronePaint> paint) {
            this(builds, what, paint, CLOSE_UP_PITCH_DEGREES, 1, 215.0F);
        }

        private CloseUpScene(Supplier<Optional<DroneBuild>> builds, String what, Supplier<DronePaint> paint, double pitchDegrees,
                             int standHeight, float droneYaw) {
            this(builds, what, paint, pitchDegrees, standHeight, droneYaw, CLOSE_UP_MIN_DISTANCE);
        }

        private CloseUpScene(Supplier<Optional<DroneBuild>> builds, String what, Supplier<DronePaint> paint, double pitchDegrees,
                             int standHeight, float droneYaw, double minDistance) {
            this.accepts = null;
            this.builds = builds;
            this.what = what;
            this.paint = paint;
            this.pitchDegrees = pitchDegrees;
            this.standHeight = standHeight;
            this.droneYaw = droneYaw;
            this.minDistance = minDistance;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            Optional<FrameType> frame = this.builds != null ? this.builds.get().map(DroneBuild::frame) : exampleFrame(this.accepts);
            if (frame.isEmpty()) {
                return StepResult.fail("kein " + this.what + "-Rahmen im Beispiel-Addon (" + PartsRenderSelfTests.EXAMPLE_NAMESPACE + ") angemeldet");
            }
            Optional<DroneBuild> build = this.builds != null ? this.builds.get() : PartsRenderSelfTests.buildFor(frame.get());
            if (build.isEmpty()) {
                return StepResult.fail("kein passender Bauplan für " + frame.get().id());
            }
            if (!state.containsKey(FUTURE)) {
                float radius = DroneVisuals.estimatedRadiusBlocks(build.get());
                double distance = Math.max(this.minDistance, radius * CLOSE_UP_RADII);
                double aim = this.standHeight + radius * CLOSE_UP_AIM;
                double rise = distance * Math.tan(Math.toRadians(this.pitchDegrees));
                this.aimHeight = aim;
                DronePaint paint = this.paint.get();
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    BlockPos base = base();
                    double feet = Math.max(base.getY(), base.getY() + aim + rise - player.getEyeHeight());
                    player.getAbilities().flying = feet > base.getY() + 0.01;
                    player.onUpdateAbilities();
                    place(player, base.getX() + 0.5, feet, base.getZ() + 1.5 - distance, 0.0F, (float) this.pitchDegrees);
                    select(player, EMPTY_HOTBAR_SLOT);
                    BlockPos stand = base.offset(0, 0, 1);
                    for (int k = 0; k < this.standHeight; k++) {
                        level.setBlockAndUpdate(stand.above(k), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                    }
                    QuadcopterEntity drone = spawnDrone(level, Vec3.atBottomCenterOf(stand.above(this.standHeight)), this.droneYaw, build.get());
                    drone.setPaint(paint);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, this.what);
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (mc.level == null || !(mc.level.getEntity(DRONE.get()) instanceof QuadcopterEntity drone) || drone.build().frame() != frame.get()) {
                return ctx.tick() > 200 ? StepResult.fail(this.what + ": Drohne kam nicht beim Client an") : StepResult.pending();
            }
            mc.options.fov().set(CLOSE_UP_FOV);
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            BlockPos stand = base().offset(0, 0, 1);
            if (mc.player != null) {
                look(mc.player, stand.getX() + 0.5, stand.getY() + this.aimHeight, stand.getZ() + 0.5);
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
            ctx.onServer(server -> {
                discardDrone(server);
                ServerPlayer player = player(server);
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                for (int k = 0; k < this.standHeight; k++) {
                    player.level().setBlockAndUpdate(base().offset(0, k, 1), Blocks.AIR.defaultBlockState());
                }
                BlockPos base = base();
                place(player, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0.0F, 0.0F);
            });
        }
    }

    private static float holdAltitude(SelfTest.StepContext ctx, PilotSession session) {
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
        return (float) Math.max(0.05, Math.min(0.7, 0.28 + integral + error * 0.12 - vy * 0.08));
    }

    private static void fillLake(ServerLevel level, BlockPos base) {
        restoreLake(level);
        BlockPos from = base.offset(-LAKE_HALF_WIDTH, -LAKE_DEPTH, -LAKE_BEHIND);
        BlockPos to = base.offset(LAKE_HALF_WIDTH, -1, LAKE_AHEAD);
        BlockState water = Blocks.WATER.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (Math.abs(pos.getX() - base.getX()) <= ISLAND_HALF_WIDTH && Math.abs(pos.getZ() - base.getZ()) <= ISLAND_HALF_LENGTH) {
                continue;
            }
            BlockPos fixed = pos.immutable();
            LAKE.add(new SavedBlock(fixed, level.getBlockState(fixed)));
            level.setBlock(fixed, water, Block.UPDATE_CLIENTS);
        }
    }

    private static void restoreLake(ServerLevel level) {
        for (SavedBlock saved : LAKE) {
            level.setBlock(saved.pos(), saved.state(), Block.UPDATE_CLIENTS);
        }
        LAKE.clear();
    }

    private record SavedBlock(BlockPos pos, BlockState state) {
    }

    private static final class FpvPropsScene implements Screenshots.Scene {
        private final boolean overWater;
        private final Supplier<DroneBuild> build;
        private final float tilt;
        private final Supplier<DronePaint> paint;

        private FpvPropsScene(boolean overWater) {
            this(overWater, () -> DroneBuild.FREESTYLE);
        }

        private FpvPropsScene(boolean overWater, Supplier<DroneBuild> build) {
            this(overWater, build, Float.NaN, () -> DronePaint.DEFAULT);
        }

        private FpvPropsScene(boolean overWater, Supplier<DroneBuild> build, float tilt, Supplier<DronePaint> paint) {
            this.overWater = overWater;
            this.build = build;
            this.tilt = tilt;
            this.paint = paint;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            PilotSession session = PilotSession.INSTANCE;
            if (!state.containsKey(FUTURE)) {
                session.clearTestInput();
                if (ctx.tick() < STOP_SETTLE_TICKS) {
                    return StepResult.pending();
                }
                DroneRenderConfig config = DroneRenderConfig.get().copy();
                config.fpvProps = true;
                config.fpvDucts = true;
                DroneRenderConfig.CONFIG.set(config);
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    BlockPos base = base();
                    if (this.overWater) {
                        fillLake(level, base);
                    }
                    level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(LOOSE_ITEM_RANGE)).forEach(Entity::discard);
                    place(player, base.getX() + 0.5, base.getY(), base.getZ() - 1.5, 0.0F, 10.0F);
                    DroneBuild shown = this.build.get();
                    QuadcopterEntity drone = spawnDrone(level, new Vec3(base.getX() + 0.5, base.getY() + 0.1, base.getZ() + 1.5), 0.0F, shown);
                    if (Float.isFinite(this.tilt)) {
                        drone.setTune(new FlightTune(CameraMount.withUptilt(drone.tune().values(), shown, this.tilt)));
                    }
                    drone.setPaint(this.paint.get());
                    ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
                    remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
                    player.getInventory().setItem(0, remote);
                    select(player, 0);
                    ItemStack goggles = ContentIds.item(shown.video().system == VideoSystem.ANALOG ? ContentIds.GOGGLES_ANALOG : ContentIds.GOGGLES_DIGITAL)
                            .map(ItemStack::new).orElse(ItemStack.EMPTY);
                    goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
                    player.setItemSlot(EquipmentSlot.HEAD, goggles);
                    DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
                    if (result != DronePiloting.StartResult.STARTED) {
                        throw new IllegalStateException("Start verweigert: " + result);
                    }
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "FPV-Start");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (!session.isFpv()) {
                return ctx.tick() > 200 ? StepResult.fail("keine FPV-Sitzung") : StepResult.pending();
            }
            QuadcopterEntity flown = session.drone();
            if (Float.isFinite(this.tilt) && (flown == null || Math.abs(FpvProps.cameraTilt(flown) - this.tilt) > 0.01F)) {
                return ctx.tick() > 200 ? StepResult.fail("Kamerawinkel " + this.tilt + "° nicht übernommen") : StepResult.pending();
            }
            DroneSim sim = session.sim();
            if (sim == null) {
                return StepResult.fail("keine Sitzung");
            }
            int since = (int) state.computeIfAbsent(SINCE, key -> ctx.tick());
            int elapsed = ctx.tick() - since;
            if (elapsed < 20 || !sim.telemetry().armed) {
                session.setTestInput(StickPositions.IDLE, sim.telemetry().armed || (elapsed >= 10 && sim.telemetry().armBlockers == 0 && ctx.tick() % 10 >= 5),
                        StabMode.ANGLE);
                return elapsed > 120 ? StepResult.fail("nicht scharf: " + sim.telemetry().armBlockers) : StepResult.pending();
            }
            double target = base().getY() + HOVER_HEIGHT;
            state.put(TARGET_Y, target);
            float throttle = holdAltitude(ctx, session);
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
            double y = session.comPosition(new Vector3d()).y;
            if (Math.abs(y - target) < 0.35 && Math.abs((double) state.getOrDefault(LAST_Y, y) - y) < 0.02) {
                state.put(BASELINE, FpvProps.propsDrawn());
                if (this.overWater) {
                    waterBlursBefore = FpvProps.blursDrawn();
                }
                return StepResult.pass();
            }
            return elapsed > 600 ? StepResult.fail("Höhe nicht erreicht: " + y + " statt " + target) : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            float throttle = holdAltitude(ctx, session);
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            if (this.overWater) {
                waterBlursAfter = FpvProps.blursDrawn();
            }
            PilotSession.INSTANCE.clearTestInput();
            if (PilotSession.INSTANCE.isActive()) {
                PilotSession.INSTANCE.requestStop();
            }
            RescueController.INSTANCE.clearHome();
            ctx.onServer(server -> {
                discardDrone(server);
                ServerPlayer player = player(server);
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                restoreLake(player.level());
            });
        }
    }

    private static final class LosHoverScene implements Screenshots.Scene {
        private final Supplier<DroneBuild> build;
        private final double height;
        private final double distance;
        private final float droneYaw;

        private LosHoverScene(Supplier<DroneBuild> build, double height, double distance, float droneYaw) {
            this.build = build;
            this.height = height;
            this.distance = distance;
            this.droneYaw = droneYaw;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            PilotSession session = PilotSession.INSTANCE;
            if (!state.containsKey(FUTURE)) {
                session.clearTestInput();
                if (ctx.tick() < STOP_SETTLE_TICKS) {
                    return StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    BlockPos base = base();
                    level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(LOOSE_ITEM_RANGE)).forEach(Entity::discard);
                    place(player, base.getX() + 0.5, base.getY(), base.getZ() + 1.5 - this.distance, 0.0F, 0.0F);
                    QuadcopterEntity drone = spawnDrone(level, new Vec3(base.getX() + 0.5, base.getY() + 0.1, base.getZ() + 1.5), this.droneYaw,
                            this.build.get());
                    ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
                    remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
                    player.getInventory().setItem(0, remote);
                    select(player, 0);
                    player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.LINE_OF_SIGHT);
                    if (result != DronePiloting.StartResult.STARTED) {
                        throw new IllegalStateException("Start verweigert: " + result);
                    }
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Sichtflug-Start");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (!session.isActive() || session.mode() != PilotMode.LINE_OF_SIGHT) {
                return ctx.tick() > 200 ? StepResult.fail("keine Sichtflug-Sitzung") : StepResult.pending();
            }
            DroneSim sim = session.sim();
            if (sim == null) {
                return StepResult.fail("keine Sitzung");
            }
            int since = (int) state.computeIfAbsent(SINCE, key -> ctx.tick());
            int elapsed = ctx.tick() - since;
            if (elapsed < 20 || !sim.telemetry().armed) {
                session.setTestInput(StickPositions.IDLE, sim.telemetry().armed || (elapsed >= 10 && sim.telemetry().armBlockers == 0 && ctx.tick() % 10 >= 5),
                        StabMode.ANGLE);
                return elapsed > 120 ? StepResult.fail("nicht scharf: " + sim.telemetry().armBlockers) : StepResult.pending();
            }
            double target = base().getY() + this.height;
            state.put(TARGET_Y, target);
            this.hold(ctx);
            double y = session.comPosition(new Vector3d()).y;
            if (Math.abs(y - target) < 0.2 && Math.abs((double) state.getOrDefault(LAST_Y, y) - y) < 0.01 && elapsed > 80) {
                mc.options.fov().set(CLOSE_UP_FOV);
                if (!mc.gui.hud.isHidden()) {
                    mc.gui.hud.toggle();
                }
                return StepResult.pass();
            }
            return elapsed > 600 ? StepResult.fail("Höhe nicht erreicht: " + y + " statt " + target) : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            float throttle = holdAltitude(ctx, session);
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && session.isActive()) {
                Vector3d drone = session.comPosition(new Vector3d());
                look(mc.player, drone.x, drone.y, drone.z);
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
            PilotSession.INSTANCE.clearTestInput();
            if (PilotSession.INSTANCE.isActive()) {
                PilotSession.INSTANCE.requestStop();
            }
            RescueController.INSTANCE.clearHome();
            ctx.onServer(PartsRenderShowcase::discardDrone);
        }
    }

    private static final class InventoryScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            List<PartInfo> parts = new ArrayList<>();
            for (PartInfo part : PartIndex.all()) {
                if (PartsRenderSelfTests.EXAMPLE_NAMESPACE.equals(part.id().getNamespace())) {
                    parts.add(part);
                }
            }
            if (parts.isEmpty()) {
                return StepResult.fail("keine Teile im Beispiel-Addon (" + PartsRenderSelfTests.EXAMPLE_NAMESPACE + ") angemeldet");
            }
            if (!state.containsKey(FUTURE)) {
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.setGameMode(GameType.SURVIVAL);
                    Inventory inventory = player.getInventory();
                    inventory.clearContent();
                    int slot = 9;
                    for (PartInfo part : parts) {
                        ItemStack stack = PropwashApi.partItems().stack(part.id());
                        if (!stack.isEmpty() && slot < 36) {
                            inventory.setItem(slot++, stack);
                        }
                    }
                    int hotbar = 0;
                    for (PartInfo part : parts) {
                        if (part instanceof FrameType frame && hotbar < EMPTY_HOTBAR_SLOT) {
                            Optional<DroneBuild> build = PartsRenderSelfTests.buildFor(frame);
                            if (build.isPresent()) {
                                ItemStack drone = ContentTab.presetDrone(build.get());
                                if (!drone.isEmpty()) {
                                    inventory.setItem(hotbar++, drone);
                                }
                            }
                        }
                    }
                    if (hotbar < EMPTY_HOTBAR_SLOT) {
                        inventory.setItem(hotbar, ContentTab.presetDrone(DroneBuild.FREESTYLE));
                    }
                    select(player, EMPTY_HOTBAR_SLOT);
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Inventar");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (mc.player == null || mc.player.hasInfiniteMaterials() || mc.player.getInventory().getItem(9).isEmpty()
                    || mc.player.getInventory().getSelectedSlot() != EMPTY_HOTBAR_SLOT) {
                return ctx.tick() > 200 ? StepResult.fail("Beispiel-Teile kamen nicht im Inventar an") : StepResult.pending();
            }
            RescueController.INSTANCE.clearHome();
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            if (!(mc.gui.screen() instanceof ShowcaseInventoryScreen)) {
                mc.gui.setScreen(new ShowcaseInventoryScreen(mc.player, EMPTY_CRAFTING_SLOT));
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
                player.setGameMode(GameType.CREATIVE);
            });
        }
    }
}
