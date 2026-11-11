package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.accessory.MountPoses;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.MountPose;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleDispenser;
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
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class AccessoryHangSelfTests {
    public static final String GROUP = "addon-api-17: gerade hängend – ";
    private static final int OFFSET_X = -40;
    private static final int OFFSET_Z = 60;
    private static final int STAND = 2;
    private static final int FOV = 30;
    private static final double RANGE = 2.9;
    private static final double MIN_RANGE = 0.4;
    private static final double LIFT = 0.35;
    private static final double WHOOP_GAP = 4.0;
    private static final double BOOSTER_TILT = 22.0;
    private static final String FUTURE = "future";
    private static final AtomicReference<@Nullable BlockPos> BASE = new AtomicReference<>();
    private static final AtomicReference<@Nullable GameType> MODE = new AtomicReference<>();
    private static final AtomicReference<String> REPORT = new AtomicReference<>("");
    private static final Set<Integer> DRONES = ConcurrentHashMap.newKeySet();
    private static final AtomicReference<int[]> CHECKED = new AtomicReference<>(new int[]{-1, -1});
    private static int savedFov = -1;

    private AccessoryHangSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Platz vorbereiten", 400, AccessoryHangSelfTests::prepareStep);
        SelfTest.clientStep(GROUP + "Speer-Halterung am freestyle_5 und am Whoop ohne Neigung und Streckung, MountPose waagerecht, Booster weiter 22° geneigt",
                400, AccessoryHangSelfTests::checkStep);
        if (Screenshots.enabled()) {
            Screenshots.step(GROUP + "Bildschirmfoto freestyle_5 mit gerade hängender Speer-Halterung", "accessory_hang_freestyle", 30, new Scene(false, true));
            Screenshots.step(GROUP + "Bildschirmfoto freestyle_5 mit leerer Speer-Halterung", "accessory_hang_freestyle_empty", 30, new Scene(false, false));
            Screenshots.step(GROUP + "Bildschirmfoto Whoop mit gerade hängender Speer-Halterung", "accessory_hang_whoop", 30, new Scene(true, true));
        }
        SelfTest.clientStep(GROUP + "Aufräumen", 300, AccessoryHangSelfTests::cleanupStep);
    }

    static DroneBuild freestyleBuild() {
        return DroneBuild.FREESTYLE.withAccessory(AccessoryGapsSelfTests.type(ExampleDispenser.SPEAR_HOLDER))
                .withAccessory(AccessoryGapsSelfTests.type(ExampleDispenser.BOOSTER));
    }

    static DroneBuild whoopBuild() {
        return DroneBuild.WHOOP.withAccessory(AccessoryGapsSelfTests.type(ExampleDispenser.SPEAR_HOLDER));
    }

    private static StepResult awaitServer(SelfTest.StepContext ctx, String what) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return StepResult.fail(what + ": keine Server-Aktion");
        }
        if (future.isCompletedExceptionally()) {
            Throwable error = future.exceptionNow();
            return StepResult.fail(what + ": " + (error.getCause() != null ? error.getCause().getMessage() : error.getMessage()));
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    private static StepResult prepareStep(SelfTest.StepContext ctx) {
        if (!AccessoryGapsSelfTests.ready()) {
            ctx.log("Beispiel-Add-on aus, übersprungen");
            return StepResult.pass();
        }
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            if (Screenshots.enabled()) {
                StepResult resized = Screenshots.resizeWindow(ctx);
                if (resized.status() != StepResult.Status.PASS) {
                    return resized;
                }
                Screenshots.hideMarker(true);
                savedFov = mc.options.fov().get();
            }
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

    private static Vec3 feet(BlockPos base) {
        return Vec3.atBottomCenterOf(base.above(STAND)).add(0.0, LIFT, 0.0);
    }

    private static QuadcopterEntity place(ServerLevel level, DroneBuild build, Vec3 feet, boolean spear, boolean rocket) {
        QuadcopterEntity drone = AccessoryGapsSelfTests.park(level, build, feet);
        drone.setNoGravity(true);
        DRONES.add(drone.getId());
        if (spear) {
            PropwashApi.accessories().inventory(drone, AccessorySlot.BOTTOM).orElseThrow().set(0, new ItemStack(Items.IRON_SPEAR));
        }
        if (rocket) {
            PropwashApi.accessories().inventory(drone, AccessorySlot.TOP).orElseThrow().set(0, new ItemStack(Items.FIREWORK_ROCKET));
        }
        return drone;
    }

    private static void discardDrones(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Integer id : DRONES) {
                if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                    drone.discard();
                }
            }
        }
        DRONES.clear();
    }

    private static Vec3 forwardOf(QuadcopterEntity drone) {
        Vector3f v = drone.orientation().transform(new Vector3f(0.0F, 0.0F, -1.0F));
        return new Vec3(v.x, v.y, v.z);
    }

    private static String checkPose(QuadcopterEntity drone, String what) {
        MountPose bottom = MountPoses.of(drone, AccessoryMount.BOTTOM)
                .orElseThrow(() -> new IllegalStateException(what + ": keine Halterung unten"));
        Vec3 forward = forwardOf(drone);
        double along = bottom.forward().dot(forward);
        if (along < 0.9999 || Math.abs(bottom.forward().y) > 1.0E-4 || Math.abs(bottom.up().y - 1.0) > 1.0E-4) {
            throw new IllegalStateException(String.format(Locale.ROOT, "%s: MountPose unten geneigt, vorwärts %s (Drohne %s), oben %s", what,
                    bottom.forward(), forward, bottom.up()));
        }
        double drop = bottom.anchor().y - bottom.front().y;
        double reach = bottom.anchor().subtract(bottom.front()).horizontalDistance();
        if (Math.abs(drop - 0.005) > 1.0E-4 || Math.abs(reach - 0.030) > 1.0E-4) {
            throw new IllegalStateException(String.format(Locale.ROOT, "%s: Mündung %.4f m unter und %.4f m vor dem Anbaupunkt statt 0,005 und 0,030",
                    what, drop, reach));
        }
        return String.format(Locale.ROOT, "%s unten vorwärts y %.5f, Mündung %.1f mm tiefer und %.1f mm vorn", what, bottom.forward().y, drop * 1000.0,
                reach * 1000.0);
    }

    private static StepResult checkStep(SelfTest.StepContext ctx) {
        if (!AccessoryGapsSelfTests.ready()) {
            return StepResult.pass();
        }
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            BlockPos base = BASE.get();
            if (base == null) {
                return StepResult.fail("Platz nicht vorbereitet");
            }
            AccessoryType holder = AccessoryGapsSelfTests.type(ExampleDispenser.SPEAR_HOLDER);
            if (!holder.hangsStraight() || !holder.definition().hangsStraight()) {
                return StepResult.fail("Beispiel-Speer-Halterung ohne hangsStraight");
            }
            if (AccessoryGapsSelfTests.type(ExampleDispenser.BOOSTER).hangsStraight()) {
                return StepResult.fail("Beispiel-Booster hängt gerade, sollte geneigt bleiben");
            }
            state.put(FUTURE, ctx.onServer(server -> {
                discardDrones(server);
                ServerLevel level = InteractSelfTests.player(server).level();
                QuadcopterEntity freestyle = place(level, freestyleBuild(), feet(base), true, true);
                QuadcopterEntity whoop = place(level, whoopBuild(), feet(base).add(0.0, 0.0, -WHOOP_GAP), true, false);
                CHECKED.set(new int[]{freestyle.getId(), whoop.getId()});
                MountPose top = MountPoses.of(freestyle, AccessoryMount.TOP).orElseThrow(() -> new IllegalStateException("freestyle_5: keine Halterung oben"));
                double topTilt = Math.toDegrees(Math.asin(Math.clamp(top.forward().y, -1.0, 1.0)));
                if (Math.abs(topTilt - BOOSTER_TILT) > 0.05) {
                    throw new IllegalStateException(String.format(Locale.ROOT, "Booster oben um %.2f° statt %.0f° geneigt", topTilt, BOOSTER_TILT));
                }
                REPORT.set(checkPose(freestyle, "freestyle_5") + "; " + checkPose(whoop, "Whoop")
                        + String.format(Locale.ROOT, "; Booster oben %.1f° geneigt", topTilt));
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Drohnen setzen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        Minecraft mc = Minecraft.getInstance();
        int[] ids = CHECKED.get();
        if (mc.level == null || !(mc.level.getEntity(ids[0]) instanceof QuadcopterEntity freestyle)
                || !(mc.level.getEntity(ids[1]) instanceof QuadcopterEntity whoop)) {
            return ctx.tick() > 300 ? StepResult.fail("Drohnen kamen nicht beim Client an") : StepResult.pending();
        }
        DroneRenderState big = AccessoryGapsSelfTests.extract(freestyle);
        DroneRenderState small = AccessoryGapsSelfTests.extract(whoop);
        String problem = renderProblem(big, true, true, "freestyle_5");
        if (problem == null) {
            problem = renderProblem(small, false, true, "Whoop");
        }
        if (problem != null) {
            return ctx.tick() > 300 ? StepResult.fail(problem) : StepResult.pending();
        }
        int bottom = AccessoryMount.BOTTOM.ordinal();
        ctx.log(REPORT.get() + String.format(Locale.ROOT, "; Anzeige unten Neigung %.1f°/%.1f°, Streckung %.2f/%.2f, oben %.1f°", big.accessoryTilt[bottom],
                small.accessoryTilt[bottom], big.accessoryStretch[bottom], small.accessoryStretch[bottom], big.accessoryTilt[AccessoryMount.TOP.ordinal()]));
        return StepResult.pass();
    }

    private static @Nullable String renderProblem(@Nullable DroneRenderState state, boolean freestyle, boolean items, String what) {
        if (state == null) {
            return what + ": kein Render-Zustand";
        }
        int bottom = AccessoryMount.BOTTOM.ordinal();
        AccessoryType type = state.accessoryType[bottom];
        if (type == null || !type.id().equals(ExampleDispenser.SPEAR_HOLDER)) {
            return what + ": Speer-Halterung fehlt unten (" + type + ")";
        }
        if (state.accessoryTilt[bottom] != 0.0F || state.accessoryStretch[bottom] != 1.0F) {
            return String.format(Locale.ROOT, "%s: Speer-Halterung um %.1f° geneigt und %.2f-fach gestreckt", what, state.accessoryTilt[bottom],
                    state.accessoryStretch[bottom]);
        }
        ItemStack spear = state.accessoryDisplay.shown(AccessoryMount.BOTTOM, 0);
        if (items ? spear == null || !spear.is(Items.IRON_SPEAR) : spear != null) {
            return what + (items ? ": Speer nicht im Render-Zustand: " : ": Speer trotz leerer Halterung im Render-Zustand: ")
                    + AccessoryGapsSelfTests.describe(state);
        }
        if (freestyle && Math.abs(state.accessoryTilt[AccessoryMount.TOP.ordinal()] - BOOSTER_TILT) > 1.0E-3) {
            return String.format(Locale.ROOT, "%s: Booster oben um %.1f° statt %.0f° geneigt", what, state.accessoryTilt[AccessoryMount.TOP.ordinal()],
                    BOOSTER_TILT);
        }
        return null;
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
                discardDrones(server);
                ServerPlayer player = InteractSelfTests.player(server);
                BlockPos base = BASE.getAndSet(null);
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
        return Screenshots.enabled() ? Screenshots.restoreWindow(ctx) : StepResult.pass();
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
        private static final double AZIMUTH = 90.0;
        private static final double ELEVATION = 4.0;
        private static final double EMPTY_AZIMUTH = 55.0;
        private static final double EMPTY_ELEVATION = -12.0;
        private final boolean whoop;
        private final boolean items;
        private Vec3 aim = Vec3.ZERO;

        private Scene(boolean whoop, boolean items) {
            this.whoop = whoop;
            this.items = items;
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
            DroneBuild build = this.whoop ? whoopBuild() : freestyleBuild();
            if (!state.containsKey(FUTURE)) {
                Vec3 feet = feet(base);
                double radius = DroneVisuals.estimatedRadiusBlocks(build);
                Vec3 center = feet.add(0.0, radius * 0.1, 0.0);
                this.aim = center;
                double range = Math.max(radius * RANGE, MIN_RANGE);
                double side = Math.toRadians(this.items ? AZIMUTH : EMPTY_AZIMUTH);
                double up = Math.toRadians(this.items ? ELEVATION : EMPTY_ELEVATION);
                Vec3 eye = center.add(Math.cos(side) * Math.cos(up) * range, Math.sin(up) * range, Math.sin(side) * Math.cos(up) * range);
                AtomicReference<Integer> placed = new AtomicReference<>(-1);
                state.put(FUTURE, ctx.onServer(server -> {
                    discardDrones(server);
                    ServerPlayer player = InteractSelfTests.player(server);
                    ServerLevel level = player.level();
                    for (int k = 0; k < STAND; k++) {
                        level.setBlockAndUpdate(base.above(k), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                    }
                    player.setGameMode(GameType.SPECTATOR);
                    player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, Set.of(), 0.0F, 0.0F, true);
                    placed.set(place(level, build, feet, this.items, this.items && !this.whoop).getId());
                }));
                state.put("placed", placed);
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Drohne setzen");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            @SuppressWarnings("unchecked")
            int id = ((AtomicReference<Integer>) state.get("placed")).get();
            if (mc.level == null || !(mc.level.getEntity(id) instanceof QuadcopterEntity entity)) {
                return ctx.tick() > 200 ? StepResult.fail("Drohne kam nicht beim Client an") : StepResult.pending();
            }
            String problem = renderProblem(AccessoryGapsSelfTests.extract(entity), !this.whoop, this.items, this.whoop ? "Whoop" : "freestyle_5");
            if (problem != null) {
                return ctx.tick() > 200 ? StepResult.fail(problem) : StepResult.pending();
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
