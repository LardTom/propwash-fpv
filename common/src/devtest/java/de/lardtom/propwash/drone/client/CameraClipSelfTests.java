package de.lardtom.propwash.drone.client;

import com.mojang.blaze3d.platform.NativeImage;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.drone.client.render.FpvGuardSelfTests;
import de.lardtom.propwash.drone.client.render.FpvPropPass;
import de.lardtom.propwash.drone.client.render.FpvProps;
import de.lardtom.propwash.video.ImageArea;
import de.lardtom.propwash.video.client.CleanFrameTap;
import de.lardtom.propwash.video.client.FpvImage;
import net.minecraft.client.Screenshot;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.rescue.client.RescueController;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.video.FovMath;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class CameraClipSelfTests {
    public static final String PREFIX = "drone: FPV-Kamera nie im Block";
    private static final int OFFSET_X = 72;
    private static final int OFFSET_Z = -72;
    private static final int STATION_SPACING = 8;
    private static final int HALF = 3;
    private static final int SETTLE_TICKS = 12;
    private static final double SAMPLE_INSET = 1.0E-4;
    private static final String FUTURE = "future";
    private static final String SINCE = "since";
    private static final String PHASE = "phase";
    private static final String SHOT = "shot";
    private static final String BEFORE_DIR = "../../build/fpv-clip-vorher/";
    private static final AtomicReference<BlockPos> BASE = new AtomicReference<>();
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final List<Saved> SAVED = new ArrayList<>();
    private static final AtomicReference<SavedPlayer> PLAYER = new AtomicReference<>();

    enum Station {
        GROUND, SNOW_1, SNOW_4, WALL, STAIRS, SLAB, CEILING
    }

    record Pose(String label, double yaw, double pitch, double roll) {
        Quaterniond rotation() {
            return new Quaterniond().rotateY(Math.toRadians(this.yaw)).rotateX(Math.toRadians(this.pitch)).rotateZ(Math.toRadians(this.roll));
        }
    }

    private static final List<Pose> POSES = List.of(
            new Pose("aufrecht", 0.0, 0.0, 0.0),
            new Pose("seitlich", 0.0, 0.0, 90.0),
            new Pose("seitlich gedreht", 45.0, 0.0, -90.0),
            new Pose("kopfüber", 0.0, 0.0, 180.0),
            new Pose("Nase unten", 0.0, -90.0, 0.0),
            new Pose("Nase 45° unten", 0.0, -45.0, 0.0),
            new Pose("Nase 45° unten, schräg", 45.0, -45.0, 20.0),
            new Pose("Heck unten", 0.0, 90.0, 0.0),
            new Pose("kopfüber, Nase unten", 0.0, -45.0, 180.0),
            new Pose("gekippt", 90.0, 30.0, 60.0));

    record Scene(String label, String file, Station station, Supplier<DroneBuild> build, Pose pose, boolean eyeInside) {
    }

    private static final List<Scene> SCENES = List.of(
            new Scene("Whoop in einer Schneeschicht", "fpv_clip_whoop_snow", Station.SNOW_1, () -> DroneBuild.WHOOP,
                    new Pose("aufrecht", 0.0, 0.0, 0.0), true),
            new Scene("5-Zöller kopfüber über einer Höhle", "fpv_clip_freestyle_upside_down", Station.GROUND, () -> DroneBuild.FREESTYLE,
                    new Pose("kopfüber", 0.0, 0.0, 180.0), false),
            new Scene("Deadcat mit der Nase im Gras", "fpv_clip_deadcat_nose_down", Station.GROUND,
                    () -> Conversions.build(ExampleAddon.deadcat().build()).orElseThrow(), new Pose("Nase 60° unten", 0.0, -60.0, 0.0), true),
            new Scene("X-Class schräg im tiefen Schnee", "fpv_clip_xclass_snow", Station.SNOW_4,
                    () -> Conversions.build(ExampleAddon.xclass().build()).orElseThrow(), new Pose("Nase 30° unten", 0.0, -30.0, 15.0), true),
            new Scene("5-Zöller an der Wand", "fpv_clip_freestyle_wall", Station.WALL, () -> DroneBuild.FREESTYLE,
                    new Pose("aufrecht", 0.0, 0.0, 0.0), false),
            new Scene("Cinewhoop auf der Treppenstufe", "fpv_clip_cinewhoop_stairs", Station.STAIRS, () -> DroneBuild.CINEWHOOP,
                    new Pose("aufrecht", 0.0, 0.0, 0.0), false));

    record DuctScene(String label, String file, Station station, Supplier<DroneBuild> build, double gap, double lift) {
    }

    private static final List<DuctScene> DUCT_SCENES = List.of(
            new DuctScene("Whoop 65 gelandet im Gras", "fpv_ducts_whoop_ground", Station.GROUND, creative(DroneBuild.WHOOP), 0.0, 0.0),
            new DuctScene("Cinewhoop 3″ gelandet im Gras", "fpv_ducts_cinewhoop_ground", Station.GROUND, creative(DroneBuild.CINEWHOOP), 0.0, 0.0),
            new DuctScene("Whoop 65 schwebend vor der Wand", "fpv_ducts_whoop_wall", Station.WALL, creative(DroneBuild.WHOOP), 0.08, 0.6),
            new DuctScene("Cinewhoop 3″ schwebend vor der Wand", "fpv_ducts_cinewhoop_wall", Station.WALL, creative(DroneBuild.CINEWHOOP), 0.15, 0.6));
    private static final float DUCT_TILT = 0.0F;
    private static final double OCCLUDED_SHARE = 0.5;
    private static final String COUNT = "count";

    private CameraClipSelfTests() {
    }

    private static Supplier<DroneBuild> creative(DroneBuild build) {
        return () -> FpvGuardSelfTests.withCreativeFc(build);
    }

    public static void register() {
        String cleanup = PREFIX + ": aufräumen";
        SelfTest.switchStep(PREFIX + ": Prüfplatz mit Höhle, Schnee, Wand, Treppe, Stufe und Decke", cleanup, 600, CameraClipSelfTests::buildStep);
        SelfTest.clientStep(PREFIX + ": alle Drohnen in kritischen Lagen (Auge, Nahebene, Sichtlinie)", 200, CameraClipSelfTests::geometryStep);
        for (Scene scene : SCENES) {
            SelfTest.clientStep(PREFIX + ": FPV-Bild – " + scene.label(), 1200, ctx -> sceneStep(ctx, scene));
        }
        for (DuctScene scene : DUCT_SCENES) {
            SelfTest.clientStep(PREFIX + ": Ducts im FPV-Bild – " + scene.label(), 1200, ctx -> ductStep(ctx, scene));
        }
        SelfTest.clientStep(cleanup, 300, CameraClipSelfTests::cleanupStep);
    }

    static BlockPos base() {
        BlockPos base = BASE.get();
        if (base == null) {
            throw new IllegalStateException("Prüfplatz nicht gebaut");
        }
        return base;
    }

    static int stationX(Station station) {
        return base().getX() + station.ordinal() * STATION_SPACING;
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos fixed = pos.immutable();
        SAVED.add(new Saved(fixed, level.getBlockState(fixed)));
        level.setBlock(fixed, state, Block.UPDATE_CLIENTS);
    }

    private static void build(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        restore(level);
        PLAYER.compareAndSet(null, new SavedPlayer(player.position(), player.getYRot(), player.getXRot(), player.gameMode.getGameModeForPlayer(),
                player.getInventory().getSelectedSlot(), player.getInventory().getItem(0).copy(), player.getItemBySlot(EquipmentSlot.HEAD).copy()));
        int x = player.getBlockX() + OFFSET_X;
        int z = player.getBlockZ() + OFFSET_Z;
        for (int cx = (x - HALF) >> 4; cx <= (x + Station.values().length * STATION_SPACING) >> 4; cx++) {
            for (int cz = (z - HALF - 2) >> 4; cz <= (z + HALF + 6) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BASE.set(new BlockPos(x, top, z));
        for (Station station : Station.values()) {
            int sx = x + station.ordinal() * STATION_SPACING;
            for (int dx = -HALF; dx <= HALF; dx++) {
                for (int dz = -HALF; dz <= HALF; dz++) {
                    BlockPos column = new BlockPos(sx + dx, top, z + dz);
                    set(level, column.below(4), Blocks.STONE.defaultBlockState());
                    set(level, column.below(3), Blocks.AIR.defaultBlockState());
                    set(level, column.below(2), Blocks.AIR.defaultBlockState());
                    set(level, column.below(), Blocks.GRASS_BLOCK.defaultBlockState());
                    for (int dy = 0; dy <= 3; dy++) {
                        set(level, column.above(dy), Blocks.AIR.defaultBlockState());
                    }
                }
            }
            for (int dx = -HALF; dx <= HALF; dx++) {
                for (int dz = -HALF; dz <= HALF; dz++) {
                    BlockPos column = new BlockPos(sx + dx, top, z + dz);
                    switch (station) {
                        case SNOW_1 -> set(level, column, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1));
                        case SNOW_4 -> set(level, column, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 4));
                        case CEILING -> set(level, column.above(2), Blocks.STONE.defaultBlockState());
                        case WALL -> {
                            if (dz == -1) {
                                for (int dy = 0; dy <= 2; dy++) {
                                    set(level, column.above(dy), Blocks.STONE_BRICKS.defaultBlockState());
                                }
                            }
                        }
                        case STAIRS -> {
                            if (dz == 0) {
                                set(level, column, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
                            } else if (dz == -1) {
                                set(level, column, Blocks.OAK_PLANKS.defaultBlockState());
                            }
                        }
                        case SLAB -> {
                            if (dz == -1) {
                                set(level, column, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
                            }
                        }
                        case GROUND -> {
                        }
                    }
                }
            }
        }
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.teleportTo(level, x + 3.0 * STATION_SPACING + 0.5, top, z + HALF + 2.5, Set.of(), 180.0F, 20.0F, true);
    }

    private static void restore(ServerLevel level) {
        for (int i = SAVED.size() - 1; i >= 0; i--) {
            Saved saved = SAVED.get(i);
            level.setBlock(saved.pos(), saved.state(), Block.UPDATE_CLIENTS);
        }
        SAVED.clear();
    }

    private static StepResult buildStep(SelfTest.StepContext ctx) {
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
                mc.getTutorial().setStep(TutorialSteps.NONE);
            }
            state.put(FUTURE, ctx.onServer(CameraClipSelfTests::build));
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Prüfplatz: " + future.exceptionNow());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        BlockPos base = BASE.get();
        if (mc.level == null || mc.player == null || base == null) {
            return StepResult.pending();
        }
        BlockState snow = mc.level.getBlockState(new BlockPos(stationX(Station.SNOW_4), base.getY(), base.getZ()));
        BlockState stairs = mc.level.getBlockState(new BlockPos(stationX(Station.STAIRS), base.getY(), base.getZ()));
        BlockState cave = mc.level.getBlockState(new BlockPos(stationX(Station.GROUND), base.getY() - 2, base.getZ()));
        BlockState ceiling = mc.level.getBlockState(new BlockPos(stationX(Station.CEILING), base.getY() + 2, base.getZ()));
        boolean ready = snow.is(Blocks.SNOW) && snow.getValue(SnowLayerBlock.LAYERS) == 4 && stairs.is(Blocks.OAK_STAIRS) && cave.isAir()
                && ceiling.is(Blocks.STONE) && mc.player.blockPosition().distManhattan(base) < 4 * STATION_SPACING;
        if (!ready) {
            return ctx.tick() - (int) state.get(SINCE) > 500 ? StepResult.fail("Prüfplatz kommt nicht beim Client an") : StepResult.pending();
        }
        ctx.log("Prüfplatz bei " + base.toShortString() + ": " + Station.values().length + " Stationen, Höhle unter dem Gras");
        return StepResult.pass();
    }

    record Placement(Vector3d com, AABB box) {
    }

    static Placement place(BlockGetter level, Station station, AirframeSpec spec) {
        BlockPos base = base();
        double half = 0.5 * spec.hitboxWidth;
        double height = spec.hitboxHeight;
        double x = stationX(station) + 0.5;
        double z = base.getZ() + 0.5;
        double y = base.getY();
        switch (station) {
            case SNOW_4 -> y += 0.375;
            case WALL, SLAB -> z = base.getZ() + half;
            case STAIRS -> {
                y += 0.5;
                z = base.getZ() + 0.5 + half;
            }
            case CEILING -> y = base.getY() + 2.0 - height;
            case GROUND, SNOW_1 -> {
            }
        }
        double com = -spec.hitboxFootOffset;
        return new Placement(new Vector3d(x, y + com, z), new AABB(x - half, y, z - half, x + half, y + height, z + half));
    }

    static final class Eye {
        final Vector3d eye = new Vector3d();
        final Vector3f forwards = new Vector3f();
        final Vector3f up = new Vector3f();
        final Vector3f left = new Vector3f();
        float fov;
        float aspect;

        void setup(DroneBuild build, Vector3d com, Quaterniond body, float aspect) {
            AirframeSpec spec = AirframeSpec.of(build);
            this.eye.set(spec.lensRight, spec.lensUp, -spec.lensForward);
            body.transform(this.eye).add(com);
            Quaterniond camera = new Quaterniond(body).rotateX(Math.toRadians(CameraMount.defaultUptilt(build.frame())));
            Vector3d v = new Vector3d();
            camera.transform(v.set(0.0, 0.0, -1.0));
            this.forwards.set((float) v.x, (float) v.y, (float) v.z);
            camera.transform(v.set(0.0, 1.0, 0.0));
            this.up.set((float) v.x, (float) v.y, (float) v.z);
            camera.transform(v.set(-1.0, 0.0, 0.0));
            this.left.set((float) v.x, (float) v.y, (float) v.z);
            this.aspect = aspect;
            this.fov = FovMath.verticalFromHorizontal(FovMath.cameraHorizontalDegrees(build.video()), aspect);
        }
    }

    static boolean insideVisual(BlockGetter level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        for (AABB box : state.getVisualShape(level, pos, CollisionContext.empty()).toAabbs()) {
            AABB world = box.move(pos);
            if (x > world.minX + SAMPLE_INSET && x < world.maxX - SAMPLE_INSET && y > world.minY + SAMPLE_INSET && y < world.maxY - SAMPLE_INSET
                    && z > world.minZ + SAMPLE_INSET && z < world.maxZ - SAMPLE_INSET) {
                return true;
            }
        }
        return false;
    }

    static @Nullable String viewProblem(BlockGetter level, Vec3 eye, Basis basis, double near, double tanX, double tanY, Vector3d anchor) {
        if (insideVisual(level, eye.x, eye.y, eye.z)) {
            return "Auge im Block";
        }
        if (!insideVisual(level, anchor.x, anchor.y, anchor.z)) {
            int steps = 48;
            for (int i = 1; i < steps; i++) {
                double t = (double) i / steps;
                if (insideVisual(level, anchor.x + t * (eye.x - anchor.x), anchor.y + t * (eye.y - anchor.y), anchor.z + t * (eye.z - anchor.z))) {
                    return "Auge hinter einem Block";
                }
            }
        }
        int steps = 6;
        for (int a = 0; a <= steps; a++) {
            for (int b = 0; b <= steps; b++) {
                for (int t = 1; t <= steps; t++) {
                    double depth = near * t / steps;
                    double sx = (2.0 * a / steps - 1.0) * tanX;
                    double sy = (2.0 * b / steps - 1.0) * tanY;
                    double px = eye.x + depth * (basis.fx() + sx * basis.lx() + sy * basis.ux());
                    double py = eye.y + depth * (basis.fy() + sx * basis.ly() + sy * basis.uy());
                    double pz = eye.z + depth * (basis.fz() + sx * basis.lz() + sy * basis.uz());
                    if (insideVisual(level, px, py, pz)) {
                        return String.format(Locale.ROOT, "Nahebene im Block (near %.4f)", near);
                    }
                }
            }
        }
        return null;
    }

    record Basis(double fx, double fy, double fz, double ux, double uy, double uz, double lx, double ly, double lz) {
        static Basis of(Vector3f f, Vector3f u, Vector3f l) {
            return new Basis(f.x, f.y, f.z, u.x, u.y, u.z, l.x, l.y, l.z);
        }
    }

    static List<DroneBuild> builds() {
        List<DroneBuild> out = new ArrayList<>(DroneBuild.PRESETS);
        Conversions.build(ExampleAddon.deadcat().build()).ifPresent(out::add);
        Conversions.build(ExampleAddon.stretched().build()).ifPresent(out::add);
        Conversions.build(ExampleAddon.xclass().build()).ifPresent(out::add);
        return out;
    }

    private static StepResult geometryStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return StepResult.fail("keine Welt");
        }
        List<DroneBuild> builds = builds();
        if (builds.size() < DroneBuild.PRESETS.size() + 3) {
            return StepResult.fail("Beispiel-Addon-Drohnen fehlen: " + builds.size());
        }
        float aspect = mc.getWindow().getHeight() > 0 ? (float) mc.getWindow().getWidth() / mc.getWindow().getHeight() : 16.0F / 9.0F;
        FpvEyeGuard guard = new FpvEyeGuard();
        Eye probe = new Eye();
        int poses = 0;
        int eyeInside = 0;
        int nearOnly = 0;
        double deepest = 0.0;
        double largestShift = 0.0;
        List<String> clipped = new ArrayList<>();
        for (DroneBuild build : builds) {
            AirframeSpec spec = AirframeSpec.of(build);
            for (Station station : Station.values()) {
                Placement placement = place(mc.level, station, spec);
                if (!mc.level.noBlockCollision(null, placement.box().deflate(1.0E-3))) {
                    return StepResult.fail("Platzierung von " + build.frame().key() + " an " + station + " steckt im Block");
                }
                int stationClips = 0;
                for (Pose pose : POSES) {
                    probe.setup(build, placement.com(), pose.rotation(), aspect);
                    double tanY = Math.tan(Math.toRadians(probe.fov) / 2.0);
                    double tanX = tanY * aspect;
                    Basis basis = Basis.of(probe.forwards, probe.up, probe.left);
                    Vec3 raw = new Vec3(probe.eye.x, probe.eye.y, probe.eye.z);
                    poses++;
                    String before = viewProblem(mc.level, raw, basis, EyeClearance.VANILLA_NEAR, tanX, tanY, placement.com());
                    if (before != null) {
                        stationClips++;
                        if (before.startsWith("Nahebene")) {
                            nearOnly++;
                        } else {
                            eyeInside++;
                            deepest = Math.max(deepest, depthInside(mc.level, raw));
                        }
                    }
                    for (double least : new double[]{EyeClearance.MIN_NEAR, EyeClearance.VANILLA_NEAR}) {
                        Vector3d eye = new Vector3d(probe.eye);
                        guard.apply(mc.level, eye, placement.com(), probe.forwards, probe.up, probe.left, probe.fov, aspect, least);
                        double near = guard.near(0.05F);
                        String after = viewProblem(mc.level, new Vec3(eye.x, eye.y, eye.z), basis, near, tanX, tanY, placement.com());
                        if (after != null) {
                            return StepResult.fail(build.frame().key() + " an " + station + ", " + pose.label() + (least < EyeClearance.VANILLA_NEAR ? ""
                                    : " (Shaderpack)") + ": " + after + " – Auge " + fmt(eye) + ", vorher " + fmt(probe.eye));
                        }
                        boolean shader = least >= EyeClearance.VANILLA_NEAR;
                        if (before == null && guard.moved() && (!shader || eye.distance(probe.eye) > 3.0 * EyeClearance.MARGIN)) {
                            return StepResult.fail(build.frame().key() + " an " + station + ", " + pose.label() + (shader ? " (Shaderpack)" : "")
                                    + ": Auge ohne Not um " + String.format(Locale.ROOT, "%.1f mm", eye.distance(probe.eye) * 1000.0) + " verschoben");
                        }
                        largestShift = Math.max(largestShift, eye.distance(probe.eye));
                    }
                }
                if (stationClips > 0) {
                    clipped.add(build.frame().key() + "@" + station.name().toLowerCase(Locale.ROOT) + " " + stationClips);
                }
            }
        }
        if (eyeInside == 0) {
            return StepResult.fail("keine Lage mit dem Auge im Block – Prüfplatz falsch aufgebaut?");
        }
        ctx.log(String.format(Locale.ROOT, "%d Lagen: vorher %d mit Auge im/hinter Block (bis %.0f mm im Block), %d nur mit der Nahebene; nachher alle frei, "
                + "größte Verschiebung %.0f mm", poses, eyeInside, deepest * 1000.0, nearOnly, largestShift * 1000.0));
        ctx.log("vorher betroffen: " + String.join(", ", clipped));
        return StepResult.pass();
    }

    private static double depthInside(BlockGetter level, Vec3 eye) {
        for (int i = 1; i <= 1000; i++) {
            double d = i * 0.001;
            if (!insideVisual(level, eye.x, eye.y + d, eye.z) || !insideVisual(level, eye.x, eye.y - d, eye.z)
                    || !insideVisual(level, eye.x + d, eye.y, eye.z) || !insideVisual(level, eye.x - d, eye.y, eye.z)
                    || !insideVisual(level, eye.x, eye.y, eye.z + d) || !insideVisual(level, eye.x, eye.y, eye.z - d)) {
                return d;
            }
        }
        return 1.0;
    }

    private static String fmt(Vector3d v) {
        return String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", v.x, v.y, v.z);
    }

    private static void startScene(MinecraftServer server, Scene scene, DroneBuild build) {
        startScene(server, scene, build, Float.NaN, 0.0, 0.0);
    }

    private static void startScene(MinecraftServer server, Scene scene, DroneBuild build, float tilt, double gap, double lift) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        discardDrone(server);
        Placement placement = place(level, scene.station(), AirframeSpec.of(build));
        double foot = placement.box().minY;
        Vec3 pos = new Vec3(placement.com().x, foot + lift, placement.com().z + gap);
        player.teleportTo(level, pos.x, base().getY(), base().getZ() + HALF + 2.5, Set.of(), 180.0F, 20.0F, true);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), build);
        drone.loadFromStack(stack);
        if (Float.isFinite(tilt)) {
            drone.setTune(new FlightTune(CameraMount.withUptilt(drone.tune().values(), build, tilt)));
        }
        drone.snapTo(pos.x, pos.y, pos.z, 180.0F, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(pos, new Quaternionf());
        DRONE.set(drone.getId());
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.getInventory().setItem(0, remote);
        player.getInventory().setSelectedSlot(0);
        player.connection.send(new ClientboundSetHeldSlotPacket(0));
        ItemStack goggles = ContentIds.item(build.video().system == VideoSystem.ANALOG ? ContentIds.GOGGLES_ANALOG : ContentIds.GOGGLES_DIGITAL)
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("Start verweigert: " + result);
        }
    }

    @SuppressWarnings("unchecked")
    private static StepResult ductStep(SelfTest.StepContext ctx, DuctScene scene) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        PilotSession session = PilotSession.INSTANCE;
        DroneBuild build;
        try {
            build = scene.build().get();
        } catch (RuntimeException e) {
            return StepResult.fail("Bauplan: " + e);
        }
        Pose pose = new Pose("aufrecht", 0.0, 0.0, 0.0);
        switch (phase(state)) {
            case 0 -> {
                if (ctx.tick() < 10 || session.isActive()) {
                    return ctx.tick() > 200 ? StepResult.fail("vorige FPV-Sitzung endet nicht") : StepResult.pending();
                }
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                state.put(FUTURE, ctx.onServer(server -> startDuctScene(server, scene, build)));
                return next(ctx, 1);
            }
            case 1 -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    return ductFinish(ctx, StepResult.fail("FPV-Start: " + future.exceptionNow()));
                }
                DroneSim sim = session.sim();
                QuadcopterEntity flying = session.drone();
                if (!future.isDone() || !session.isFpv() || sim == null || flying == null || flying.getId() != DRONE.get()
                        || Math.abs(FpvProps.cameraTilt(flying) - DUCT_TILT) > 0.01F) {
                    return since(ctx) > 300 ? ductFinish(ctx, StepResult.fail("keine FPV-Sitzung mit dem Kamerawinkel")) : StepResult.pending();
                }
                if (since(ctx) < 10) {
                    return StepResult.pending();
                }
                holdPose(sim, pose);
                return next(ctx, 2);
            }
            case 2, 4 -> {
                Screenshots.clearOverlays(mc);
                FpvPropPass.forceWorld(phase(state) == 4);
                FpvProps.guardMaskColor(FpvGuardSelfTests.KEY_RGB);
                if (since(ctx) < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                AtomicReference<int[]> counted = new AtomicReference<>();
                state.put(COUNT + phase(state), counted);
                CleanFrameTap.request(target -> {
                    ImageArea area = FpvImage.area(target.width, target.height);
                    Screenshot.takeScreenshot(target, 1, image -> {
                        try (NativeImage owned = image) {
                            counted.set(FpvGuardSelfTests.count(owned, area));
                        }
                    });
                });
                return next(ctx, phase(state) + 1);
            }
            case 3, 5 -> {
                int[] counted = ((AtomicReference<int[]>) state.get(COUNT + (phase(state) - 1))).get();
                if (counted == null) {
                    return since(ctx) > 200 ? ductFinish(ctx, StepResult.fail("kein Bild vor dem Videoeffekt")) : StepResult.pending();
                }
                if (phase(state) == 3) {
                    return next(ctx, 4);
                }
                FpvPropPass.forceWorld(false);
                FpvProps.guardMaskColor(-1);
                String problem = ductProblem(ctx, scene, build, session.drone(),
                        ((AtomicReference<int[]>) state.get(COUNT + 2)).get(), counted);
                if (problem != null) {
                    return ductFinish(ctx, StepResult.fail(problem));
                }
                if (!Screenshots.enabled()) {
                    return ductFinish(ctx, StepResult.pass());
                }
                return next(ctx, 6);
            }
            case 6 -> {
                Screenshots.clearOverlays(mc);
                if (since(ctx) < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                state.put(SHOT, Screenshots.capture(scene.file()));
                return next(ctx, 7);
            }
            default -> {
                CompletableFuture<String> shot = (CompletableFuture<String>) state.get(SHOT);
                if (!shot.isDone()) {
                    return StepResult.pending();
                }
                if (shot.isCompletedExceptionally()) {
                    return ductFinish(ctx, StepResult.fail("Bildschirmfoto: " + shot.exceptionNow()));
                }
                ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(scene.file() + ".png"));
                return ductFinish(ctx, StepResult.pass());
            }
        }
    }

    private static @Nullable String ductProblem(SelfTest.StepContext ctx, DuctScene scene, DroneBuild build, @Nullable QuadcopterEntity drone,
                                                int[] own, int[] world) {
        double[] separate = FpvGuardSelfTests.measure(build, drone, own);
        double[] shared = FpvGuardSelfTests.measure(build, drone, world);
        ctx.log(String.format(Locale.ROOT, "%s: Ducts im Bild %.2f %% (eigener Durchgang), %.2f %% (mit der Welt), Analyse %.2f %%",
                scene.label(), separate[0], shared[0], separate[1]));
        if (separate[1] <= FpvGuardSelfTests.VISIBLE_PERCENT) {
            return scene.label() + ": laut Analyse keine Ducts im Bild – Szene prüft nichts";
        }
        if (separate[0] <= FpvGuardSelfTests.VISIBLE_PERCENT || !FpvGuardSelfTests.matches(separate[0], separate[1])) {
            return String.format(Locale.ROOT, "%s: Ducts im Bild %.2f %% statt %.2f %% laut Analyse", scene.label(), separate[0], separate[1]);
        }
        if (shared[0] > OCCLUDED_SHARE * separate[1]) {
            return String.format(Locale.ROOT, "%s: auch mit der Welt %.2f %% Ducts sichtbar – die Szene verdeckt nichts", scene.label(), shared[0]);
        }
        return null;
    }

    private static StepResult ductFinish(SelfTest.StepContext ctx, StepResult result) {
        FpvPropPass.forceWorld(false);
        FpvProps.guardMaskColor(-1);
        return finish(ctx, result);
    }

    private static void startDuctScene(MinecraftServer server, DuctScene scene, DroneBuild build) {
        Scene base = new Scene(scene.label(), scene.file(), scene.station(), scene.build(), new Pose("aufrecht", 0.0, 0.0, 0.0), false);
        startScene(server, base, build, DUCT_TILT, scene.gap(), scene.lift());
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

    private static void holdPose(DroneSim sim, Pose pose) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        Quaterniond body = pose.rotation().rotateY(Math.PI);
        snapshot.qw = body.w;
        snapshot.qx = body.x;
        snapshot.qy = body.y;
        snapshot.qz = body.z;
        snapshot.vx = 0.0;
        snapshot.vy = 0.0;
        snapshot.vz = 0.0;
        snapshot.lx = 0.0;
        snapshot.ly = 0.0;
        snapshot.lz = 0.0;
        snapshot.sleeping = true;
        sim.copyStateFrom(snapshot);
    }

    private static int phase(Map<String, Object> state) {
        return (int) state.getOrDefault(PHASE, 0);
    }

    private static StepResult next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        return StepResult.pending();
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, 0);
    }

    @SuppressWarnings("unchecked")
    private static StepResult sceneStep(SelfTest.StepContext ctx, Scene scene) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        PilotSession session = PilotSession.INSTANCE;
        DroneBuild build;
        try {
            build = scene.build().get();
        } catch (RuntimeException e) {
            return StepResult.fail("Bauplan: " + e);
        }
        switch (phase(state)) {
            case 0 -> {
                if (ctx.tick() < 10 || session.isActive()) {
                    return ctx.tick() > 200 ? StepResult.fail("vorige FPV-Sitzung endet nicht") : StepResult.pending();
                }
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                state.put(FUTURE, ctx.onServer(server -> startScene(server, scene, build)));
                return next(ctx, 1);
            }
            case 1 -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    return finish(ctx, StepResult.fail("FPV-Start: " + future.exceptionNow()));
                }
                DroneSim sim = session.sim();
                QuadcopterEntity flying = session.drone();
                if (!future.isDone() || !session.isFpv() || sim == null || flying == null || flying.getId() != DRONE.get()) {
                    return since(ctx) > 300 ? finish(ctx, StepResult.fail("keine FPV-Sitzung")) : StepResult.pending();
                }
                if (since(ctx) < 10) {
                    return StepResult.pending();
                }
                holdPose(sim, scene.pose());
                return next(ctx, 2);
            }
            case 2 -> {
                Screenshots.clearOverlays(mc);
                if (since(ctx) < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                String problem = checkCamera(mc, scene, build, ctx);
                if (problem != null) {
                    return finish(ctx, StepResult.fail(problem));
                }
                if (!Screenshots.enabled()) {
                    return finish(ctx, StepResult.pass());
                }
                Screenshots.clearOverlays(mc);
                state.put(SHOT, Screenshots.capture(scene.file()));
                return next(ctx, 3);
            }
            case 3 -> {
                CompletableFuture<String> shot = (CompletableFuture<String>) state.get(SHOT);
                if (!shot.isDone()) {
                    return StepResult.pending();
                }
                if (shot.isCompletedExceptionally()) {
                    return finish(ctx, StepResult.fail("Bildschirmfoto: " + shot.exceptionNow()));
                }
                ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(scene.file() + ".png"));
                FpvEyeGuard.INSTANCE.bypass(true);
                return next(ctx, 4);
            }
            case 4 -> {
                Screenshots.clearOverlays(mc);
                if (since(ctx) < 6) {
                    return StepResult.pending();
                }
                Screenshots.clearOverlays(mc);
                state.put(SHOT, Screenshots.capture(BEFORE_DIR + scene.file()));
                return next(ctx, 5);
            }
            default -> {
                CompletableFuture<String> shot = (CompletableFuture<String>) state.get(SHOT);
                if (!shot.isDone()) {
                    return StepResult.pending();
                }
                FpvEyeGuard.INSTANCE.bypass(false);
                if (shot.isCompletedExceptionally()) {
                    return finish(ctx, StepResult.fail("Bildschirmfoto ohne Schutz: " + shot.exceptionNow()));
                }
                ctx.log("Vergleich ohne Schutz: " + Screenshots.dir().resolve(BEFORE_DIR + scene.file() + ".png").normalize());
                return finish(ctx, StepResult.pass());
            }
        }
    }

    private static @Nullable String checkCamera(Minecraft mc, Scene scene, DroneBuild build, SelfTest.StepContext ctx) {
        if (mc.level == null) {
            return "keine Welt";
        }
        FpvEyeGuard guard = FpvEyeGuard.INSTANCE;
        if (!guard.active()) {
            return "Schutz im FPV nicht aktiv";
        }
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 pos = camera.position();
        Vector3d placed = new Vector3d(guard.placed());
        if (pos.distanceTo(new Vec3(placed.x, placed.y, placed.z)) > 1.0E-4) {
            return "Kamera steht nicht am geschützten Auge: " + pos + " statt " + fmt(placed);
        }
        Camera.NearPlane plane = camera.getNearPlane(camera.getFov());
        Vec3 forward = plane.getPointOnPlane(0.0F, 0.0F);
        double near = forward.length();
        Vec3 corner = plane.getTopLeft().subtract(forward);
        Vector3f f = new Vector3f(camera.forwardVector());
        Vector3f u = new Vector3f(camera.upVector());
        Vector3f l = new Vector3f(camera.leftVector());
        double tanY = Math.abs(corner.dot(new Vec3(u.x, u.y, u.z))) / near;
        double tanX = Math.abs(corner.dot(new Vec3(l.x, l.y, l.z))) / near;
        Vector3d anchor = new Vector3d(FpvView.anchor());
        String after = viewProblem(mc.level, pos, Basis.of(f, u, l), near, tanX, tanY, anchor);
        if (after != null) {
            return scene.label() + ": " + after + " bei " + pos;
        }
        Vector3d raw = new Vector3d(guard.requested());
        Vec3 rawEye = new Vec3(raw.x, raw.y, raw.z);
        String before = viewProblem(mc.level, rawEye, Basis.of(f, u, l), EyeClearance.VANILLA_NEAR, tanX, tanY, anchor);
        if (before == null) {
            return scene.label() + ": Lage ist gar nicht kritisch (Auge ohne Schutz frei) – Szene prüft nichts";
        }
        if (scene.eyeInside() == before.startsWith("Nahebene")) {
            return scene.label() + ": erwartet " + (scene.eyeInside() ? "Auge im Block" : "nur Nahebene im Block") + ", ohne Schutz: " + before;
        }
        double shift = raw.distance(placed);
        ctx.log(String.format(Locale.ROOT, "%s (%s): ohne Schutz %s; mit Schutz Auge %.0f mm verschoben, Nahebene %.1f mm statt 50 mm",
                scene.label(), build.frame().key(), before, shift * 1000.0, near * 1000.0));
        return null;
    }

    private static StepResult finish(SelfTest.StepContext ctx, StepResult result) {
        FpvEyeGuard.INSTANCE.bypass(false);
        PilotSession.INSTANCE.clearTestInput();
        if (PilotSession.INSTANCE.isActive()) {
            PilotSession.INSTANCE.requestStop();
        }
        RescueController.INSTANCE.clearHome();
        ctx.onServer(server -> {
            discardDrone(server);
            player(server).setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        });
        return result;
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            FpvEyeGuard.INSTANCE.bypass(false);
            PilotSession.INSTANCE.clearTestInput();
            if (PilotSession.INSTANCE.isActive()) {
                PilotSession.INSTANCE.requestStop();
            }
            if (Screenshots.enabled()) {
                Screenshots.hideMarker(false);
                Screenshots.restoreWindow(ctx);
            }
            state.put(FUTURE, ctx.onServer(server -> {
                discardDrone(server);
                ServerPlayer player = player(server);
                restore(player.level());
                SavedPlayer saved = PLAYER.getAndSet(null);
                if (saved != null) {
                    player.getInventory().setItem(0, saved.slot0());
                    player.setItemSlot(EquipmentSlot.HEAD, saved.head());
                    player.getInventory().setSelectedSlot(saved.selected());
                    player.connection.send(new ClientboundSetHeldSlotPacket(saved.selected()));
                    player.setGameMode(saved.mode());
                    player.teleportTo(player.level(), saved.pos().x, saved.pos().y, saved.pos().z, Set.of(), saved.yRot(), saved.xRot(), true);
                } else {
                    player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                }
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufräumen: " + future.exceptionNow());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    private record Saved(BlockPos pos, BlockState state) {
    }

    private record SavedPlayer(Vec3 pos, float yRot, float xRot, GameType mode, int selected, ItemStack slot0, ItemStack head) {
    }
}
