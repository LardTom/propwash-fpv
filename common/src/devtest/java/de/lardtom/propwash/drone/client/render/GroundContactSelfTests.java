package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.apiimpl.ApiExtensions;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.GroundSettle;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.SoftGround;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class GroundContactSelfTests {
    public static final String PREFIX = "drone: Bodenkontakt";
    private static final int OFFSET_X = 104;
    private static final int OFFSET_Z = 88;
    private static final int SPACING = 4;
    private static final int STAGE_Z = 8;
    private static final int STAGE_WIDTH = 7;
    private static final int STAGE_SPACING = 10;
    private static final double DROP = 0.3;
    private static final double TOLERANCE = 0.0015;
    private static final float HULL_SINK_MM = 0.5F;
    private static final float HULL_FLOAT_MM = 1.5F;
    private static final int HULL_TILTS = 37;
    private static final int HULL_AZIMUTHS = 24;
    private static final Map<DroneBuild, MeshProbe> MESHES = new HashMap<>();
    private static final int SETTLE_TICKS = 24;
    private static final double LINEUP_GAP = 0.09;
    private static final double CAMERA_DISTANCE = 2.0;
    private static final double EYE_ABOVE = 0.22;
    private static final int SHOT_FOV = 30;
    private static final String FUTURE = "future";
    private static final String SINCE = "since";
    private static final String PHASE = "phase";
    private static final String INDEX = "index";
    private static final AtomicReference<BlockPos> BASE = new AtomicReference<>();
    private static final List<Integer> DRONES = new ArrayList<>();
    private static final List<Saved> SAVED = new ArrayList<>();
    private static final AtomicReference<SavedPlayer> PLAYER = new AtomicReference<>();
    private static int savedFov = -1;
    private static boolean hudHidden;

    enum Station {
        STONE, SLAB, STAIRS, SNOW_1, SNOW_3, CARPET, SOUL_SAND, MUD, HONEY
    }

    enum Stage {
        STONE, SNOW, SOUL_SAND
    }

    record Pose(String label, float yaw, float pitch, float roll) {
        Quaternionf rotation() {
            return new Quaternionf().rotateY((float) Math.toRadians(this.yaw)).rotateX((float) Math.toRadians(this.pitch))
                    .rotateZ((float) Math.toRadians(this.roll));
        }
    }

    private static final List<Pose> POSES = List.of(
            new Pose("aufrecht", 0.0F, 0.0F, 0.0F),
            new Pose("aufrecht gedreht", 45.0F, 0.0F, 0.0F),
            new Pose("11,5° gerollt", 0.0F, 0.0F, 11.5F),
            new Pose("30° gerollt", 0.0F, 0.0F, 30.0F),
            new Pose("45° gerollt, gedreht", 45.0F, 0.0F, -45.0F),
            new Pose("auf der Seite", 0.0F, 0.0F, 90.0F),
            new Pose("Nase 30° unten", 0.0F, -30.0F, 0.0F),
            new Pose("Nase 60° unten, gedreht", 30.0F, -60.0F, 0.0F),
            new Pose("Nase unten", 0.0F, -90.0F, 0.0F),
            new Pose("Heck 45° unten", 0.0F, 45.0F, 0.0F),
            new Pose("schräg gekippt", 90.0F, 30.0F, 60.0F),
            new Pose("kopfüber", 0.0F, 0.0F, 180.0F),
            new Pose("kopfüber, 30° geneigt", 45.0F, -30.0F, 180.0F),
            new Pose("kopfüber, 20° gerollt", 0.0F, 0.0F, 160.0F));

    record Shot(String label, String file, Stage stage, float pitch, float roll, boolean fixed, boolean posed) {
    }

    private static final List<Shot> SHOTS = List.of(
            new Shot("gekippt am Boden, vorher", "ground_contact_tilted_before", Stage.STONE, 0.0F, 30.0F, false, true),
            new Shot("gekippt am Boden, nachher", "ground_contact_tilted_after", Stage.STONE, 0.0F, 30.0F, true, true),
            new Shot("kopfüber am Boden, vorher", "ground_contact_inverted_before", Stage.STONE, 0.0F, 180.0F, false, true),
            new Shot("kopfüber am Boden, nachher", "ground_contact_inverted_after", Stage.STONE, 0.0F, 180.0F, true, true),
            new Shot("Nase unten am Boden, vorher", "ground_contact_nose_before", Stage.STONE, 40.0F, 0.0F, false, true),
            new Shot("Nase unten am Boden, nachher", "ground_contact_nose_after", Stage.STONE, 40.0F, 0.0F, true, true),
            new Shot("auf einer Schneeschicht, vorher", "ground_contact_snow_before", Stage.SNOW, 0.0F, 0.0F, false, false),
            new Shot("auf einer Schneeschicht, nachher", "ground_contact_snow_after", Stage.SNOW, 0.0F, 0.0F, true, false),
            new Shot("auf Seelensand, vorher", "ground_contact_soul_sand_before", Stage.SOUL_SAND, 0.0F, 0.0F, false, false),
            new Shot("auf Seelensand, nachher", "ground_contact_soul_sand_after", Stage.SOUL_SAND, 0.0F, 0.0F, true, false));

    private GroundContactSelfTests() {
    }

    public static void register() {
        String cleanup = PREFIX + ": aufräumen";
        SelfTest.switchStep(PREFIX + ": Prüfplatz mit Stein, Stufe, Treppe, Schnee, Teppich, Seelensand, Schlamm und Honig", cleanup, 600,
                GroundContactSelfTests::buildStep);
        SelfTest.clientStep(PREFIX + ": Hülle folgt dem gezeichneten Netz – jede Lage, mit Zubehör und in jedem Prop-Zustand", 6000,
                GroundContactSelfTests::hullStep);
        SelfTest.clientStep(PREFIX + ": jede Drohne liegt in jeder Lage auf jeder Fläche auf", 4000, GroundContactSelfTests::surfaceStep);
        SelfTest.clientStep(PREFIX + ": in der Luft bleibt das Modell an seinem Platz", 400, GroundContactSelfTests::airStep);
        SelfTest.clientStep(PREFIX + ": lose Drohne kippt am Boden flach", 600, GroundContactSelfTests::settleStep);
        if (Screenshots.enabled()) {
            for (Shot shot : SHOTS) {
                Screenshots.step("bildschirmfotos: Bodenkontakt – " + shot.label(), shot.file(), 30, new LineupScene(shot));
            }
        }
        SelfTest.clientStep(cleanup, 300, GroundContactSelfTests::cleanupStep);
    }

    static List<DroneBuild> builds() {
        Map<String, DroneBuild> out = new LinkedHashMap<>();
        for (DroneBuild build : DroneBuild.PRESETS) {
            out.put(build.frame().id().toString(), build);
        }
        Conversions.build(ExampleAddon.deadcat().build()).ifPresent(build -> out.putIfAbsent(build.frame().id().toString(), build));
        Conversions.build(ExampleAddon.stretched().build()).ifPresent(build -> out.putIfAbsent(build.frame().id().toString(), build));
        Conversions.build(ExampleAddon.xclass().build()).ifPresent(build -> out.putIfAbsent(build.frame().id().toString(), build));
        for (DronePreset preset : ApiExtensions.registeredPresets()) {
            Conversions.build(preset.data().build()).ifPresent(build -> out.putIfAbsent(build.frame().id().toString(), build));
        }
        return new ArrayList<>(out.values());
    }

    private static BlockPos base() {
        BlockPos base = BASE.get();
        if (base == null) {
            throw new IllegalStateException("Prüfplatz nicht gebaut");
        }
        return base;
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos fixed = pos.immutable();
        SAVED.add(new Saved(fixed, level.getBlockState(fixed)));
        level.setBlock(fixed, state, Block.UPDATE_CLIENTS);
    }

    private static void restore(ServerLevel level) {
        for (int i = SAVED.size() - 1; i >= 0; i--) {
            Saved saved = SAVED.get(i);
            level.setBlock(saved.pos(), saved.state(), Block.UPDATE_CLIENTS);
        }
        SAVED.clear();
    }

    private static int stationX(Station station) {
        return base().getX() + station.ordinal() * SPACING;
    }

    private static Vec3 stationCenter(Station station) {
        return new Vec3(stationX(station) + 1.5, base().getY() + 1.0, base().getZ() + 1.5);
    }

    private static int stageX(Stage stage) {
        return base().getX() + stage.ordinal() * STAGE_SPACING;
    }

    private static Vec3 stageCenter(Stage stage) {
        return new Vec3(stageX(stage) + STAGE_WIDTH * 0.5, base().getY() + 1.0, base().getZ() + STAGE_Z + 1.5);
    }

    private static BlockState platform(Station station) {
        return switch (station) {
            case SOUL_SAND -> Blocks.SOUL_SAND.defaultBlockState();
            case MUD -> Blocks.MUD.defaultBlockState();
            case HONEY -> Blocks.HONEY_BLOCK.defaultBlockState();
            default -> Blocks.SMOOTH_STONE.defaultBlockState();
        };
    }

    private static BlockState cover(Station station) {
        return switch (station) {
            case SLAB -> Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
            case STAIRS -> Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
            case SNOW_1 -> Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1);
            case SNOW_3 -> Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3);
            case CARPET -> Blocks.MOSS_CARPET.defaultBlockState();
            default -> Blocks.AIR.defaultBlockState();
        };
    }

    private static void build(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        restore(level);
        PLAYER.compareAndSet(null, new SavedPlayer(player.position(), player.getYRot(), player.getXRot(), player.gameMode.getGameModeForPlayer()));
        int x = player.getBlockX() + OFFSET_X;
        int z = player.getBlockZ() + OFFSET_Z;
        int length = Math.max(Station.values().length * SPACING, Stage.values().length * STAGE_SPACING);
        for (int cx = (x - 3) >> 4; cx <= (x + length + 3) >> 4; cx++) {
            for (int cz = (z - 3) >> 4; cz <= (z + STAGE_Z + 8) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BASE.set(new BlockPos(x, top, z));
        for (int dx = -3; dx <= length + 3; dx++) {
            for (int dz = -3; dz <= STAGE_Z + 8; dz++) {
                BlockPos column = new BlockPos(x + dx, top, z + dz);
                set(level, column.below(), Blocks.GRASS_BLOCK.defaultBlockState());
                for (int dy = 0; dy <= 5; dy++) {
                    set(level, column.above(dy), Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (Station station : Station.values()) {
            for (int dx = 0; dx < 3; dx++) {
                for (int dz = 0; dz < 3; dz++) {
                    BlockPos column = new BlockPos(x + station.ordinal() * SPACING + dx, top, z + dz);
                    set(level, column, platform(station));
                    set(level, column.above(), cover(station));
                }
            }
        }
        for (Stage stage : Stage.values()) {
            for (int dx = 0; dx < STAGE_WIDTH; dx++) {
                for (int dz = 0; dz < 3; dz++) {
                    BlockPos column = new BlockPos(x + stage.ordinal() * STAGE_SPACING + dx, top, z + STAGE_Z + dz);
                    set(level, column, stage == Stage.SOUL_SAND ? Blocks.SOUL_SAND.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState());
                    if (stage == Stage.SNOW) {
                        set(level, column.above(), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1));
                    }
                }
            }
        }
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.teleportTo(level, x + Station.values().length * SPACING * 0.5, top, z + 5.5, Set.of(), 180.0F, 10.0F, true);
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
                savedFov = mc.options.fov().get();
            }
            GroundContact.setEnabled(true);
            SoftGround.setEnabled(true);
            state.put(FUTURE, ctx.onServer(GroundContactSelfTests::build));
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        StepResult server = await(ctx, "Prüfplatz");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        BlockPos base = BASE.get();
        if (mc.level == null || mc.player == null || base == null) {
            return StepResult.pending();
        }
        BlockState snow = mc.level.getBlockState(new BlockPos(stationX(Station.SNOW_3), base.getY() + 1, base.getZ()));
        BlockState honey = mc.level.getBlockState(new BlockPos(stationX(Station.HONEY), base.getY(), base.getZ()));
        BlockState stage = mc.level.getBlockState(new BlockPos(stageX(Stage.SOUL_SAND), base.getY(), base.getZ() + STAGE_Z));
        boolean ready = snow.is(Blocks.SNOW) && snow.getValue(SnowLayerBlock.LAYERS) == 3 && honey.is(Blocks.HONEY_BLOCK) && stage.is(Blocks.SOUL_SAND)
                && mc.player.blockPosition().distManhattan(base) < 60;
        if (!ready) {
            return ctx.tick() - (int) state.get(SINCE) > 500 ? StepResult.fail("Prüfplatz kommt nicht beim Client an") : StepResult.pending();
        }
        for (Station station : Station.values()) {
            BlockState ground = mc.level.getBlockState(BlockPos.containing(stationCenter(station)).below());
            BlockState top = mc.level.getBlockState(BlockPos.containing(stationCenter(station)));
            boolean tagged = ground.is(SoftGround.SURFACE) || top.is(SoftGround.SURFACE);
            boolean expected = station == Station.SNOW_1 || station == Station.SNOW_3 || station == Station.SOUL_SAND || station == Station.MUD
                    || station == Station.HONEY;
            if (tagged != expected) {
                return ctx.tick() - (int) state.get(SINCE) > 500
                        ? StepResult.fail("Block-Tag propwash:drone_surface an Station " + station + ": " + tagged + " (Boden " + ground + ", darauf " + top + ")")
                        : StepResult.pending();
            }
        }
        ctx.log("Prüfplatz bei " + base.toShortString() + ": " + Station.values().length + " Flächen, " + Stage.values().length + " Bühnen");
        return StepResult.pass();
    }

    private static StepResult await(SelfTest.StepContext ctx, String what) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return StepResult.fail(what + ": keine Server-Aktion");
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    private static int phase(Map<String, Object> state) {
        return (int) state.getOrDefault(PHASE, 0);
    }

    private static StepResult next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        ctx.state().remove(FUTURE);
        return StepResult.pending();
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, 0);
    }

    private static QuadcopterEntity spawn(ServerLevel level, DroneBuild build, Vec3 pos, Quaternionf orientation, boolean floating) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), build);
        drone.loadFromStack(stack);
        drone.setNoGravity(floating);
        drone.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(pos, orientation);
        synchronized (DRONES) {
            DRONES.add(drone.getId());
        }
        return drone;
    }

    private static void discardDrones(MinecraftServer server) {
        List<Integer> ids;
        synchronized (DRONES) {
            ids = new ArrayList<>(DRONES);
            DRONES.clear();
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (int id : ids) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    entity.discard();
                }
            }
        }
    }

    private static List<QuadcopterEntity> clientDrones(ClientLevel level) {
        List<Integer> ids;
        synchronized (DRONES) {
            ids = new ArrayList<>(DRONES);
        }
        List<QuadcopterEntity> out = new ArrayList<>();
        for (int id : ids) {
            if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                out.add(drone);
            }
        }
        return out.size() == ids.size() ? out : List.of();
    }

    static double surfaceBelow(BlockGetter level, AABB box) {
        double best = Double.NEGATIVE_INFINITY;
        int x0 = Mth.floor(box.minX + 1.0E-6);
        int x1 = Mth.floor(box.maxX - 1.0E-6);
        int z0 = Mth.floor(box.minZ + 1.0E-6);
        int z1 = Mth.floor(box.maxZ - 1.0E-6);
        int y1 = Mth.floor(box.minY + 0.5);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = y1 - 2; y <= y1; y++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    for (AABB part : state.getShape(level, pos).toAabbs()) {
                        AABB placed = part.move(x, y, z);
                        if (placed.maxX > box.minX + 1.0E-6 && placed.minX < box.maxX - 1.0E-6 && placed.maxZ > box.minZ + 1.0E-6
                                && placed.minZ < box.maxZ - 1.0E-6 && placed.maxY <= box.minY + 0.45) {
                            best = Math.max(best, placed.maxY);
                        }
                    }
                }
            }
        }
        return best;
    }

    private static MeshProbe mesh(DroneBuild build) {
        return MESHES.computeIfAbsent(build, b -> MeshProbe.of(DroneVisuals.resources().assembly(b)));
    }

    private static double lowestPoint(DroneRenderState s, QuadcopterEntity drone) {
        float lowest = mesh(s.build).lowest(s.orientation, s.pivot, DroneVisuals.propVariants(s));
        return drone.getY() + s.originY + lowest * DroneVisualScale.blocksPerMillimetre(s.visualScale);
    }

    static List<DroneBuild> variants() {
        List<DroneBuild> out = new ArrayList<>();
        for (DroneBuild build : builds()) {
            out.add(build);
            for (AccessoryType type : AccessoryType.values()) {
                try {
                    DroneBuild with = build.withAccessory(type);
                    if (!with.equals(build)) {
                        out.add(with);
                    }
                } catch (RuntimeException ignored) {
                    continue;
                }
            }
        }
        return out;
    }

    private static StepResult hullStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (mc.level == null) {
            return StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        List<DroneBuild> all = (List<DroneBuild>) state.computeIfAbsent("variants", key -> variants());
        int index = (int) state.getOrDefault(INDEX, 0);
        if (index >= all.size()) {
            ctx.log(String.format(Locale.ROOT, "%d Aufbauten (%d Vorlagen mit Zubehör) × %d Prop-Zustände × %d Lagen: Netz höchstens %.2f mm unter der Hülle (%s), "
                            + "Hülle höchstens %.2f mm unter dem Netz (%s), aufrecht höchstens %.2f mm daneben; größtes Netz %d Punkte, größte Hülle %d Punkte, "
                            + "%d Teile ohne Modell mit ihrer Ersatzform", all.size(), builds().size(), VisualHull.BLUR + 1, HULL_TILTS * HULL_AZIMUTHS,
                    (double) state.getOrDefault("sink", 0.0), state.getOrDefault("sinkAt", "–"), (double) state.getOrDefault("float", 0.0),
                    state.getOrDefault("floatAt", "–"), (double) state.getOrDefault("upright", 0.0), (int) state.getOrDefault("vertices", 0),
                    (int) state.getOrDefault("points", 0), (int) state.getOrDefault("boxes", 0)));
            return StepResult.pass();
        }
        DroneBuild build = all.get(index);
        DroneAssembly assembly = DroneVisuals.resources().assembly(build);
        VisualHull hull = assembly.hull();
        MeshProbe mesh = MeshProbe.of(assembly);
        Vector3f pivot = new Vector3f(0.0F, DronePhysique.of(build).comHeightMm(), 0.0F);
        String label = name(build) + (build.accessories().isEmpty() ? "" : " mit " + build.accessories());
        state.put("vertices", Math.max((int) state.getOrDefault("vertices", 0), mesh.vertices()));
        state.put("points", Math.max((int) state.getOrDefault("points", 0), hull.contactPoints()));
        state.put("boxes", (int) state.getOrDefault("boxes", 0) + mesh.boxes());
        Quaternionf q = new Quaternionf();
        int[] variants = new int[DroneLayout.SLOTS];
        for (int ti = 0; ti < HULL_TILTS; ti++) {
            float tilt = ti * 180.0F / (HULL_TILTS - 1);
            for (int ai = 0; ai < HULL_AZIMUTHS; ai++) {
                float azimuth = ai * 360.0F / HULL_AZIMUTHS;
                q.rotationAxis((float) Math.toRadians(tilt), (float) Math.cos(Math.toRadians(azimuth)), 0.0F, (float) Math.sin(Math.toRadians(azimuth)));
                float fixed = mesh.lowestFixed(q, pivot);
                for (int variant = 0; variant <= VisualHull.BLUR; variant++) {
                    if (!mesh.hasProp(0, variant)) {
                        continue;
                    }
                    Arrays.fill(variants, variant);
                    float real = Math.min(fixed, mesh.lowestProps(q, pivot, variants));
                    float shell = hull.lowestPoint(q, pivot, variants);
                    String at = String.format(Locale.ROOT, "%s, Props %s, %.0f° um %.0f°", label,
                            variant == VisualHull.BLUR ? "drehend" : PropDamage.values()[variant].name(), tilt, azimuth);
                    if (shell - real > (double) state.getOrDefault("sink", 0.0)) {
                        state.put("sink", (double) (shell - real));
                        state.put("sinkAt", at);
                    }
                    if (real - shell > (double) state.getOrDefault("float", 0.0)) {
                        state.put("float", (double) (real - shell));
                        state.put("floatAt", at);
                    }
                    if (ti == 0) {
                        state.put("upright", Math.max((double) state.getOrDefault("upright", 0.0), Math.abs(shell - real)));
                    }
                    if (shell - real > HULL_SINK_MM) {
                        return StepResult.fail(String.format(Locale.ROOT, "%s: gezeichnetes Netz ragt %.2f mm unter die Hülle (im Boden)", at, shell - real));
                    }
                    if (real - shell > HULL_FLOAT_MM) {
                        return StepResult.fail(String.format(Locale.ROOT, "%s: Hülle liegt %.2f mm unter dem gezeichneten Netz (Drohne schwebt)", at,
                                real - shell));
                    }
                }
            }
        }
        state.put(INDEX, index + 1);
        return StepResult.pending();
    }

    private static DroneRenderState posed(DroneBuild build, Quaternionf orientation) {
        DroneRenderState s = new DroneRenderState();
        DroneVisuals.fill(s, build, false, false, 0.0F);
        s.orientation.set(orientation);
        DroneVisuals.placeInWorld(s);
        return s;
    }

    private static String name(DroneBuild build) {
        return build.frame().id().toString();
    }

    private static StepResult surfaceStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        List<DroneBuild> builds = builds();
        int index = (int) state.getOrDefault(INDEX, 0);
        if (mc.level == null) {
            return StepResult.pending();
        }
        if (index >= builds.size()) {
            double worst = (double) state.getOrDefault("worst", 0.0);
            double floated = (double) state.getOrDefault("floated", 0.0);
            ctx.log(String.format(Locale.ROOT, "%d Drohnen × %d Flächen × %d Lagen: liegen auf (± %.1f mm); ohne Korrektur bis %.0f mm im Boden und bis %.0f mm"
                    + " darüber (%s)", builds.size(), Station.values().length, POSES.size(), TOLERANCE * 1000.0, worst * 1000.0, floated * 1000.0,
                    state.getOrDefault("worstAt", "–")));
            return worst > 0.02 ? StepResult.pass() : StepResult.fail("Gegenprobe ohne Korrektur zeigt kein Versinken: " + worst);
        }
        DroneBuild build = builds.get(index);
        switch (phase(state)) {
            case 0 -> {
                if (!state.containsKey(FUTURE)) {
                    state.put(FUTURE, ctx.onServer(server -> {
                        discardDrones(server);
                        ServerLevel level = player(server).level();
                        for (Station station : Station.values()) {
                            Vec3 center = stationCenter(station);
                            spawn(level, build, new Vec3(center.x, center.y + 1.0 + DROP, center.z), new Quaternionf(), false);
                        }
                    }));
                    return StepResult.pending();
                }
                StepResult server = await(ctx, "Drohnen setzen");
                return server.status() == StepResult.Status.PASS ? next(ctx, 1) : server;
            }
            case 1 -> {
                List<QuadcopterEntity> drones = clientDrones(mc.level);
                if (drones.isEmpty() || since(ctx) < SETTLE_TICKS) {
                    return since(ctx) > 300 ? StepResult.fail(name(build) + ": Drohnen kommen nicht beim Client an") : StepResult.pending();
                }
                for (Station station : Station.values()) {
                    QuadcopterEntity drone = drones.get(station.ordinal());
                    double surface = surfaceBelow(mc.level, drone.getBoundingBox().move(0.0, 0.25, 0.0));
                    String at = name(build) + " auf " + station;
                    if (Math.abs(drone.getY() - surface) > TOLERANCE) {
                        return StepResult.fail(String.format(Locale.ROOT, "%s: Drohne liegt bei %.4f, Fläche bei %.4f", at, drone.getY(), surface));
                    }
                    for (Pose pose : POSES) {
                        DroneRenderState s = posed(build, pose.rotation());
                        double before = lowestPoint(s, drone);
                        DroneVisuals.restOnGround(s, drone, drone.getY());
                        double after = lowestPoint(s, drone);
                        if (Math.abs(after - surface) > TOLERANCE) {
                            return StepResult.fail(String.format(Locale.ROOT, "%s, %s: tiefster Punkt %.1f mm über der Fläche (ohne Korrektur %.1f mm)",
                                    at, pose.label(), (after - surface) * 1000.0, (before - surface) * 1000.0));
                        }
                        if (surface - before > (double) state.getOrDefault("worst", 0.0)) {
                            state.put("worst", surface - before);
                            state.put("worstAt", at + ", " + pose.label());
                        }
                        if (before - surface > (double) state.getOrDefault("floated", 0.0)) {
                            state.put("floated", before - surface);
                        }
                    }
                }
                state.put(INDEX, index + 1);
                return next(ctx, 0);
            }
            default -> {
                return StepResult.fail("unbekannte Phase");
            }
        }
    }

    private static StepResult airStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        DroneBuild build = DroneBuild.FREESTYLE;
        Quaternionf tilted = new Quaternionf().rotateZ((float) Math.toRadians(40.0));
        double[] heights = {1.0, 0.02, 0.0};
        if (mc.level == null) {
            return StepResult.pending();
        }
        if (phase(state) == 0) {
            if (!state.containsKey(FUTURE)) {
                state.put(FUTURE, ctx.onServer(server -> {
                    discardDrones(server);
                    ServerLevel level = player(server).level();
                    Vec3 center = stationCenter(Station.STONE);
                    for (int i = 0; i < heights.length; i++) {
                        spawn(level, build, new Vec3(center.x - 0.8 + 0.8 * i, center.y + heights[i], center.z), tilted, true);
                    }
                }));
                return StepResult.pending();
            }
            StepResult server = await(ctx, "Drohnen setzen");
            return server.status() == StepResult.Status.PASS ? next(ctx, 1) : server;
        }
        List<QuadcopterEntity> drones = clientDrones(mc.level);
        if (drones.isEmpty() || since(ctx) < 12) {
            return since(ctx) > 200 ? StepResult.fail("Drohnen kommen nicht beim Client an") : StepResult.pending();
        }
        float depth = -posed(build, tilted).lowestAboveFoot;
        if (depth < 0.04F) {
            return StepResult.fail("40° gerollt ragt nur " + depth + " unter die Kollisionsbox");
        }
        float[] offsets = new float[heights.length];
        for (int i = 0; i < heights.length; i++) {
            QuadcopterEntity drone = drones.get(i);
            DroneRenderState s = new DroneRenderState();
            DroneVisuals.fillFromEntity(s, drone, 1.0F);
            offsets[i] = s.groundOffset;
            if (Math.abs(GroundSettle.upY(s.orientation) - Math.cos(Math.toRadians(40.0))) > 0.01) {
                return StepResult.fail("schwebende Drohne hat ihre Lage verloren: " + s.orientation);
            }
        }
        if (offsets[0] != 0.0F) {
            return StepResult.fail("1 m über dem Boden verschoben um " + offsets[0]);
        }
        if (Math.abs(offsets[1] - (depth - 0.02F)) > 0.002F) {
            return StepResult.fail(String.format(Locale.ROOT, "2 cm über dem Boden: %.4f statt %.4f", offsets[1], depth - 0.02F));
        }
        if (Math.abs(offsets[2] - depth) > 0.002F) {
            return StepResult.fail(String.format(Locale.ROOT, "am Boden: %.4f statt %.4f", offsets[2], depth));
        }
        GroundContact.setEnabled(false);
        DroneRenderState off = new DroneRenderState();
        DroneVisuals.fillFromEntity(off, drones.get(2), 1.0F);
        GroundContact.setEnabled(true);
        if (off.groundOffset != 0.0F) {
            return StepResult.fail("abgeschaltet verschiebt trotzdem: " + off.groundOffset);
        }
        ctx.log(String.format(Locale.ROOT, "5-Zöller 40° gerollt (ragt %.0f mm unter die Box): 1 m hoch 0 mm, 2 cm hoch %.0f mm, am Boden %.0f mm angehoben",
                depth * 1000.0F, offsets[1] * 1000.0F, offsets[2] * 1000.0F));
        return StepResult.pass();
    }

    private static float tiltDeg(Quaternionf q) {
        Vector3f up = new Quaternionf(q).normalize().transform(new Vector3f(0.0F, 1.0F, 0.0F));
        return (float) Math.toDegrees(Math.acos(Mth.clamp(up.y, -1.0F, 1.0F)));
    }

    private static StepResult settleStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (mc.level == null) {
            return StepResult.pending();
        }
        Station[] stations = {Station.STONE, Station.SNOW_1, Station.SLAB, Station.SOUL_SAND};
        Quaternionf[] starts = {new Quaternionf().rotateY(0.6F).rotateZ((float) Math.toRadians(40.0)),
                new Quaternionf().rotateZ((float) Math.toRadians(150.0)),
                new Quaternionf().rotateX((float) Math.toRadians(-70.0)),
                new Quaternionf().rotateY(-1.0F).rotateX((float) Math.toRadians(25.0)).rotateZ((float) Math.toRadians(-20.0))};
        DroneBuild[] builds = {DroneBuild.FREESTYLE, DroneBuild.WHOOP, DroneBuild.CINEWHOOP, DroneBuild.RACER};
        if (phase(state) == 0) {
            if (!state.containsKey(FUTURE)) {
                state.put(FUTURE, ctx.onServer(server -> {
                    discardDrones(server);
                    ServerLevel level = player(server).level();
                    for (int i = 0; i < stations.length; i++) {
                        Vec3 center = stationCenter(stations[i]);
                        spawn(level, builds[i], new Vec3(center.x, center.y + 1.0 + DROP, center.z), starts[i], false);
                    }
                }));
                return StepResult.pending();
            }
            StepResult server = await(ctx, "Drohnen setzen");
            return server.status() == StepResult.Status.PASS ? next(ctx, 1) : server;
        }
        List<QuadcopterEntity> drones = clientDrones(mc.level);
        if (drones.isEmpty() || since(ctx) < 40) {
            return since(ctx) > 300 ? StepResult.fail("Drohnen kommen nicht beim Client an") : StepResult.pending();
        }
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < stations.length; i++) {
            QuadcopterEntity drone = drones.get(i);
            Quaternionf now = new Quaternionf(drone.orientation());
            float tilt = tiltDeg(now);
            float started = tiltDeg(starts[i]);
            boolean back = started > 90.0F;
            float fromFlat = back ? 180.0F - tilt : tilt;
            String at = name(builds[i]) + " auf " + stations[i];
            if (fromFlat > 1.0F) {
                return since(ctx) > 200 ? StepResult.fail(String.format(Locale.ROOT, "%s: liegt nach dem Fall noch %.1f° schief (Start %.0f°)", at, fromFlat,
                        started)) : StepResult.pending();
            }
            double surface = surfaceBelow(mc.level, drone.getBoundingBox().move(0.0, 0.25, 0.0));
            if (Math.abs(drone.getY() - surface) > TOLERANCE) {
                return since(ctx) > 200 ? StepResult.fail(String.format(Locale.ROOT, "%s: liegt bei %.4f, Fläche bei %.4f", at, drone.getY(), surface))
                        : StepResult.pending();
            }
            DroneRenderState s = new DroneRenderState();
            DroneVisuals.fillFromEntity(s, drone, 1.0F);
            double lowest = lowestPoint(s, drone);
            if (Math.abs(lowest - surface) > 0.004) {
                return since(ctx) > 200 ? StepResult.fail(String.format(Locale.ROOT, "%s: tiefster Punkt %.1f mm über der Fläche", at,
                        (lowest - surface) * 1000.0)) : StepResult.pending();
            }
            log.append(String.format(Locale.ROOT, "%s %.0f° → %.1f°; ", at, started, tilt));
        }
        ctx.log("lose Drohnen liegen flach: " + log);
        return StepResult.pass();
    }

    private static final class LineupScene implements Screenshots.Scene {
        private final Shot shot;

        LineupScene(Shot shot) {
            this.shot = shot;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            Shot shot = this.shot;
            Vec3 center = stageCenter(shot.stage());
            if (phase(state) == 0) {
                if (!state.containsKey(FUTURE)) {
                    GroundContact.setEnabled(shot.fixed());
                    SoftGround.setEnabled(shot.fixed());
                    mc.options.fov().set(SHOT_FOV);
                    if (!mc.gui.hud.isHidden()) {
                        mc.gui.hud.toggle();
                        hudHidden = true;
                    }
                    state.put(FUTURE, ctx.onServer(server -> {
                        discardDrones(server);
                        ServerPlayer player = player(server);
                        ServerLevel level = player.level();
                        List<DroneBuild> lineup = DroneBuild.PRESETS;
                        Quaternionf orientation = new Quaternionf().rotateY((float) Math.PI).rotateX((float) Math.toRadians(shot.pitch()))
                                .rotateZ((float) Math.toRadians(shot.roll()));
                        double surface = shot.stage() == Stage.SNOW && shot.posed() ? center.y + 0.125 : center.y;
                        double total = LINEUP_GAP * (lineup.size() - 1);
                        for (DroneBuild build : lineup) {
                            total += AirframeSpec.of(build).hitboxWidth;
                        }
                        double left = center.x - total * 0.5;
                        for (DroneBuild build : lineup) {
                            double width = AirframeSpec.of(build).hitboxWidth;
                            double y = shot.posed() ? surface : center.y + (shot.stage() == Stage.SNOW ? 0.125 : 0.0) + DROP;
                            spawn(level, build, new Vec3(left + width * 0.5, y, center.z), orientation, shot.posed());
                            left += width + LINEUP_GAP;
                        }
                        double rest = center.y + (shot.stage() == Stage.SNOW ? 0.125 : 0.0);
                        double feetY = cameraFeet(shot.stage(), player.getEyeHeight());
                        double dy = rest + 0.04 - (feetY + player.getEyeHeight());
                        float pitch = (float) -Math.toDegrees(Math.atan2(dy, CAMERA_DISTANCE));
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                        player.teleportTo(level, center.x, feetY, center.z + CAMERA_DISTANCE, Set.of(), 180.0F, pitch, true);
                    }));
                    return StepResult.pending();
                }
                StepResult server = await(ctx, "Aufstellung");
                return server.status() == StepResult.Status.PASS ? next(ctx, 1) : server;
            }
            if (mc.level == null || mc.player == null) {
                return StepResult.pending();
            }
            List<QuadcopterEntity> drones = clientDrones(mc.level);
            Vec3 camera = new Vec3(center.x, cameraFeet(shot.stage(), mc.player.getEyeHeight()), center.z + CAMERA_DISTANCE);
            boolean near = mc.player.position().distanceTo(camera) < 0.05;
            if (drones.isEmpty() || !near || since(ctx) < SETTLE_TICKS) {
                return since(ctx) > 300 ? StepResult.fail("Aufstellung kommt nicht beim Client an") : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            GroundContact.setEnabled(true);
            SoftGround.setEnabled(true);
            showHud();
            if (savedFov > 0) {
                Minecraft.getInstance().options.fov().set(savedFov);
            }
        }
    }

    private static double cameraFeet(Stage stage, double eyeHeight) {
        return stageCenter(stage).y + (stage == Stage.SNOW ? 0.125 : 0.0) + EYE_ABOVE - eyeHeight;
    }

    private static void showHud() {
        Minecraft mc = Minecraft.getInstance();
        if (hudHidden && mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
        hudHidden = false;
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            GroundContact.setEnabled(true);
            SoftGround.setEnabled(true);
            showHud();
            if (Screenshots.enabled()) {
                if (savedFov > 0) {
                    Minecraft.getInstance().options.fov().set(savedFov);
                }
                Screenshots.hideMarker(false);
                Screenshots.restoreWindow(ctx);
            }
            state.put(FUTURE, ctx.onServer(server -> {
                discardDrones(server);
                ServerPlayer player = player(server);
                restore(player.level());
                SavedPlayer saved = PLAYER.getAndSet(null);
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                if (saved != null) {
                    player.setGameMode(saved.mode());
                    player.teleportTo(player.level(), saved.pos().x, saved.pos().y, saved.pos().z, Set.of(), saved.yRot(), saved.xRot(), true);
                }
            }));
            return StepResult.pending();
        }
        return await(ctx, "Aufräumen");
    }

    private record Saved(BlockPos pos, BlockState state) {
    }

    private record SavedPlayer(Vec3 pos, float yRot, float xRot, GameType mode) {
    }
}
