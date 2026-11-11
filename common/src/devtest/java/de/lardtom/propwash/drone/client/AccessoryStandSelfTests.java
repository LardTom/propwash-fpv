package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleAccessories;
import de.lardtom.propwash.dev.example.ExampleDispenser;
import de.lardtom.propwash.dev.example.ExampleOperator;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.render.DronePhysique;
import de.lardtom.propwash.drone.client.render.DroneRenderState;
import de.lardtom.propwash.drone.client.render.DroneVisuals;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.ArmBlocker;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class AccessoryStandSelfTests {
    public static final String PREFIX = "drone: steht auf Zubehör";
    private static final int OFFSET_X = -64;
    private static final int OFFSET_Z = 112;
    private static final int SPACING = 6;
    private static final int PROBE_SPACING = 2;
    private static final double SLAB_TOP = 0.5;
    private static final double TOUCH = 0.002;
    private static final double EYE_TOLERANCE = 0.001;
    private static final double DROP = 0.3;
    private static final int SETTLE_TICKS = 60;
    private static final int FPV_TICKS = 40;
    private static final int SHOT_FOV = 30;
    private static final double VIEW_RANGE = 1.1;
    private static final double EDGE_OVERLAP = 0.05;
    private static final double LEVEL_DEG = 1.0;
    private static final String EDGE_LABEL = "7″ mit Gimbal vorn lose an der Kante einer Stufe aus glattem Stein, Hitbox 5 cm auf der Stufe, Gimbal über dem 0,5 m tieferen Boden";
    private static final String PILOTED_LABEL = "7″ mit Gimbal vorn gesteuert an der Kante einer Stufe aus glattem Stein, Mitte 66 mm auf der Stufe, "
            + "Gimbal über dem 0,5 m tieferen Boden";
    private static final String PILOTED_FILE = "accessory_stand_edge_piloted";
    private static final double PILOTED_CENTER = 0.066;
    private static final int ARM_TICKS = 40;
    private static final String RISE = "rise";
    private static final String FUTURE = "future";
    private static final String SINCE = "since";
    private static final String PHASE = "phase";
    private static final String SHOT = "shot";
    private static final AtomicReference<@Nullable BlockPos> BASE = new AtomicReference<>();
    private static final Map<Case, Integer> DRONES = new ConcurrentHashMap<>();
    private static final AtomicReference<@Nullable Integer> EDGE_DRONE = new AtomicReference<>();
    private static final List<Saved> SAVED = new ArrayList<>();
    private static final AtomicReference<@Nullable SavedPlayer> PLAYER = new AtomicReference<>();
    private static int savedFov = -1;

    enum Station {
        OAK, STONE, PROBE
    }

    enum Case {
        FREESTYLE("Freestyle 5″ mit Dispenser vorn und Speer-Halterung unten auf einer Eichenstufe", "accessory_stand_freestyle", Station.OAK),
        GIMBAL("7″ mit Gimbal vorn auf einer Stufe aus glattem Stein", "accessory_stand_gimbal", Station.STONE);

        final String label;
        final String file;
        final Station station;

        Case(String label, String file, Station station) {
            this.label = label;
            this.file = file;
            this.station = station;
        }

        DroneBuild build() {
            return switch (this) {
                case FREESTYLE -> DroneBuild.FREESTYLE.withAccessory(type(ExampleDispenser.DISPENSER)).withAccessory(type(ExampleAccessories.SPEAR));
                case GIMBAL -> DroneBuild.LONG_RANGE.withAccessory(type(ExampleOperator.GIMBAL_FRONT));
            };
        }
    }

    record Probe(String label, int cell, double height, boolean covered) {
    }

    private static final List<BlockState> PROBE_BLOCKS = List.of(
            Blocks.SMOOTH_STONE_SLAB.defaultBlockState(),
            Blocks.OAK_SLAB.defaultBlockState(),
            Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3),
            Blocks.MOSS_CARPET.defaultBlockState(),
            Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH),
            Blocks.STONE.defaultBlockState(),
            Blocks.GLASS.defaultBlockState());

    private static final List<Probe> PROBES = List.of(
            new Probe("5 mm über einer Stufe aus glattem Stein", 0, 0.505, false),
            new Probe("5 mm über einer Eichenstufe", 1, 0.505, false),
            new Probe("in einer Stufe aus glattem Stein", 0, 0.25, true),
            new Probe("in einer Eichenstufe", 1, 0.25, true),
            new Probe("in drei Schneeschichten", 2, 0.2, true),
            new Probe("2 mm über drei Schneeschichten", 2, 0.377, false),
            new Probe("im Teppich", 3, 0.03, true),
            new Probe("2 mm über dem Teppich", 3, 0.0645, false),
            new Probe("in der unteren Treppenstufe", 4, 0.25, true),
            new Probe("in einem vollen Block", 5, 0.5, true),
            new Probe("in Glas", 6, 0.5, false));

    private AccessoryStandSelfTests() {
    }

    public static void register() {
        String cleanup = PREFIX + ": aufräumen";
        SelfTest.switchStep(PREFIX + ": Prüfplatz mit Eichenstufen, Stufen aus glattem Stein und Teil-Blöcken", cleanup, 600,
                AccessoryStandSelfTests::buildStep);
        for (Case c : Case.values()) {
            SelfTest.clientStep(PREFIX + ": " + c.label + " – lose abgestellt steht sie auf dem Zubehör, Modell an der Physik", 900, ctx -> looseStep(ctx, c));
            SelfTest.clientStep(PREFIX + ": " + c.label + " – im FPV Auge an der gezeichneten Kamera, Bild frei", 900, ctx -> fpvStep(ctx, c));
        }
        SelfTest.clientStep(PREFIX + ": " + EDGE_LABEL + " – liegt auf der Stufe statt über ihr zu schweben", 900, AccessoryStandSelfTests::edgeStep);
        SelfTest.clientStep(PREFIX + ": " + PILOTED_LABEL + " – liegt im FPV unscharf und scharf auf der Stufe, springt beim Start nicht hoch", 1800,
                AccessoryStandSelfTests::pilotedEdgeStep);
        SelfTest.clientStep(PREFIX + ": FPV-Bild an Teil-Blöcken – knapp darüber frei, darin abgedeckt", 300, AccessoryStandSelfTests::probeStep);
        SelfTest.clientStep(cleanup, 300, AccessoryStandSelfTests::cleanupStep);
    }

    static boolean ready() {
        return JoinTestEnv.exampleAddon() && AccessoryType.byId(ExampleDispenser.DISPENSER).isPresent()
                && AccessoryType.byId(ExampleAccessories.SPEAR).isPresent() && AccessoryType.byId(ExampleOperator.GIMBAL_FRONT).isPresent();
    }

    static AccessoryType type(Identifier id) {
        return AccessoryType.byId(id).orElseThrow(() -> new IllegalStateException("Beispiel-Zubehör fehlt: " + id));
    }

    private static BlockPos base() {
        BlockPos base = BASE.get();
        if (base == null) {
            throw new IllegalStateException("Prüfplatz nicht gebaut");
        }
        return base;
    }

    private static int stationX(Station station) {
        return base().getX() + station.ordinal() * SPACING;
    }

    private static Vec3 stationCenter(Station station) {
        return new Vec3(stationX(station) + 0.5, base().getY(), base().getZ() + 0.5);
    }

    private static double slabTop() {
        return base().getY() + SLAB_TOP;
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

    private static void build(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        restore(level);
        PLAYER.compareAndSet(null, new SavedPlayer(player.position(), player.getYRot(), player.getXRot(), player.gameMode.getGameModeForPlayer(),
                player.getInventory().getSelectedSlot(), player.getInventory().getItem(0).copy(), player.getItemBySlot(EquipmentSlot.HEAD).copy()));
        int x = player.getBlockX() + OFFSET_X;
        int z = player.getBlockZ() + OFFSET_Z;
        int length = Station.values().length * SPACING + PROBE_BLOCKS.size() * PROBE_SPACING;
        for (int cx = (x - 4) >> 4; cx <= (x + length + 4) >> 4; cx++) {
            for (int cz = (z - 5) >> 4; cz <= (z + 6) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BASE.set(new BlockPos(x, top, z));
        for (int dx = -3; dx <= length + 3; dx++) {
            for (int dz = -4; dz <= 5; dz++) {
                BlockPos column = new BlockPos(x + dx, top, z + dz);
                set(level, column.below(), Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy <= 5; dy++) {
                    set(level, column.above(dy), Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (Station station : List.of(Station.OAK, Station.STONE)) {
            BlockState slab = station == Station.OAK ? Blocks.OAK_SLAB.defaultBlockState() : Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    set(level, new BlockPos(x + station.ordinal() * SPACING + dx, top, z + dz), slab);
                }
            }
        }
        for (int i = 0; i < PROBE_BLOCKS.size(); i++) {
            set(level, probeCell(i), PROBE_BLOCKS.get(i));
        }
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(level, x + SPACING * 0.5 + 0.5, top + 1.0, z + 4.5, Set.of(), 180.0F, 20.0F, true);
    }

    private static BlockPos probeCell(int index) {
        BlockPos base = base();
        return new BlockPos(stationX(Station.PROBE) + index * PROBE_SPACING, base.getY(), base.getZ());
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
            state.put(FUTURE, ctx.onServer(AccessoryStandSelfTests::build));
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
        boolean ready = mc.level.getBlockState(new BlockPos(stationX(Station.OAK), base.getY(), base.getZ())).is(Blocks.OAK_SLAB)
                && mc.level.getBlockState(probeCell(PROBE_BLOCKS.size() - 1)).is(Blocks.GLASS)
                && mc.player.blockPosition().distManhattan(base) < 30;
        if (!ready) {
            return ctx.tick() - (int) state.get(SINCE) > 500 ? StepResult.fail("Prüfplatz kommt nicht beim Client an") : StepResult.pending();
        }
        ctx.log("Prüfplatz bei " + base.toShortString() + ", Oberkante der Stufen y " + number(slabTop()));
        return StepResult.pass();
    }

    private static StepResult await(SelfTest.StepContext ctx, String what) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return StepResult.fail(what + ": keine Server-Aktion");
        }
        if (future.isCompletedExceptionally()) {
            Throwable error = future.exceptionNow();
            return StepResult.fail(what + ": " + (error.getCause() != null ? error.getCause() : error));
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

    private static void spawn(MinecraftServer server, Case c) {
        discard(server, c);
        Vec3 center = stationCenter(c.station);
        DRONES.put(c, place(player(server).level(), c, new Vec3(center.x, slabTop() + DROP, center.z)).getId());
    }

    static Vec3 edgeSpot(DroneBuild build) {
        Vec3 center = stationCenter(Station.STONE);
        double half = AirframeSpec.of(build).hitboxWidth / 2.0;
        return new Vec3(center.x, slabTop() + DROP, edgeZ() - (half - EDGE_OVERLAP));
    }

    private static double edgeZ() {
        return base().getZ() - 1.0;
    }

    private static void spawnEdge(MinecraftServer server) {
        discardEdge(server);
        EDGE_DRONE.set(place(player(server).level(), Case.GIMBAL, edgeSpot(Case.GIMBAL.build())).getId());
    }

    private static void spawnPilotedEdge(MinecraftServer server) {
        discardEdge(server);
        Vec3 center = stationCenter(Station.STONE);
        EDGE_DRONE.set(place(player(server).level(), Case.GIMBAL, new Vec3(center.x, slabTop() + DROP, edgeZ() + PILOTED_CENTER)).getId());
    }

    private static void discardEdge(MinecraftServer server) {
        Integer id = EDGE_DRONE.getAndSet(null);
        if (id == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                drone.discard();
            }
        }
    }

    private static QuadcopterEntity place(ServerLevel level, Case c, Vec3 pos) {
        DroneBuild build = c.build();
        if (build.accessories().isEmpty()) {
            throw new IllegalStateException(c.label + ": Zubehör passt nicht an den Rahmen");
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), build);
        drone.loadFromStack(stack);
        drone.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(pos, new Quaternionf());
        return drone;
    }

    private static void discard(MinecraftServer server, Case c) {
        Integer id = DRONES.remove(c);
        if (id == null) {
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

    private static @Nullable QuadcopterEntity clientDrone(Case c) {
        Minecraft mc = Minecraft.getInstance();
        Integer id = DRONES.get(c);
        return mc.level != null && id != null && mc.level.getEntity(id) instanceof QuadcopterEntity drone ? drone : null;
    }

    static double lowestAccessory(DroneBuild build, Vector3dc com, Quaternionfc orientation) {
        return lowestCorner(build, com, orientation).y;
    }

    static Vec3 lowestCorner(DroneBuild build, Vector3dc com, Quaternionfc orientation) {
        AirframeSpec p = AirframeSpec.of(build);
        Vec3 lowest = new Vec3(com.x(), Double.POSITIVE_INFINITY, com.z());
        Vector3f offset = new Vector3f();
        for (AccessoryMount mount : build.accessories().mounts()) {
            if (mount == AccessoryMount.TOP) {
                continue;
            }
            AccessoryType type = build.accessories().at(mount).orElseThrow();
            for (double[] corner : AccessoryAnchor.of(build.frame(), mount).corners(type)) {
                offset.set((float) (corner[0] / 1000.0 + p.comX), (float) (corner[1] / 1000.0 - p.comHeight), (float) (corner[2] / 1000.0 + p.comZ));
                orientation.transform(offset);
                if (com.y() + offset.y < lowest.y) {
                    lowest = new Vec3(com.x() + offset.x, com.y() + offset.y, com.z() + offset.z);
                }
            }
        }
        return lowest;
    }

    private static StepResult edgeStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            ctx.log("Beispiel-Add-on aus, übersprungen");
            return StepResult.pass();
        }
        Map<String, Object> state = ctx.state();
        if (phase(state) == 0) {
            if (PilotSession.INSTANCE.isActive()) {
                return since(ctx) > 200 ? StepResult.fail("vorige FPV-Sitzung endet nicht") : StepResult.pending();
            }
            if (!state.containsKey(FUTURE)) {
                state.put(FUTURE, ctx.onServer(AccessoryStandSelfTests::spawnEdge));
                return StepResult.pending();
            }
            StepResult server = await(ctx, "Drohne an die Kante setzen");
            return server.status() == StepResult.Status.PASS ? next(ctx, 1) : server;
        }
        Minecraft mc = Minecraft.getInstance();
        Integer id = EDGE_DRONE.get();
        QuadcopterEntity drone = mc.level != null && id != null && mc.level.getEntity(id) instanceof QuadcopterEntity d ? d : null;
        if (drone == null || since(ctx) < SETTLE_TICKS) {
            return since(ctx) > 300 ? StepResult.fail(EDGE_LABEL + ": Drohne kommt nicht beim Client an") : StepResult.pending();
        }
        DroneBuild build = drone.build();
        AirframeSpec p = AirframeSpec.of(build);
        Vector3d com = new Vector3d(drone.getX(), drone.getY() - p.hitboxFootOffset, drone.getZ());
        Vec3 corner = lowestCorner(build, com, drone.orientation());
        double foot = drone.getY() - slabTop();
        double floor = corner.y - base().getY();
        double beyond = edgeZ() - corner.z;
        double onStep = drone.getBoundingBox().maxZ - edgeZ();
        ctx.log(String.format(Locale.ROOT, "%s: Hitbox %.1f mm auf der Stufe, Fuß %.1f mm über der Stufe, tiefste Zubehör-Ecke %.1f mm über dem Boden daneben, "
                + "%.1f mm vor der Kante", EDGE_LABEL, onStep * 1000.0, foot * 1000.0, floor * 1000.0, beyond * 1000.0));
        if (beyond <= 0.0 || onStep <= 0.0) {
            return StepResult.fail(EDGE_LABEL + ": Prüfaufbau falsch, Gimbal nicht über dem tieferen Boden oder Hitbox nicht auf der Stufe");
        }
        if (foot > TOUCH) {
            return StepResult.fail(String.format(Locale.ROOT, "%s: Drohne schwebt %.1f mm über der Stufe, obwohl unter dem Zubehör %.1f mm frei sind", EDGE_LABEL,
                    foot * 1000.0, floor * 1000.0));
        }
        if (foot < -TOUCH) {
            return StepResult.fail(String.format(Locale.ROOT, "%s: Hitbox steckt %.1f mm in der Stufe", EDGE_LABEL, -foot * 1000.0));
        }
        if (floor < -TOUCH) {
            return StepResult.fail(String.format(Locale.ROOT, "%s: Zubehör steckt %.1f mm im Boden", EDGE_LABEL, -floor * 1000.0));
        }
        return StepResult.pass();
    }

    private static @Nullable QuadcopterEntity edgeDrone() {
        Minecraft mc = Minecraft.getInstance();
        Integer id = EDGE_DRONE.get();
        return mc.level != null && id != null && mc.level.getEntity(id) instanceof QuadcopterEntity d ? d : null;
    }

    private static String edgeState(QuadcopterEntity drone, Vector3dc com, Quaternionfc body) {
        Vec3 corner = lowestCorner(drone.build(), com, body);
        double tilt = Math.toDegrees(Math.acos(Math.clamp(DronePhysique.rotatedY(body, 0.0F, 1.0F, 0.0F), -1.0, 1.0)));
        return String.format(Locale.ROOT, "Fuß der Hitbox %.1f mm über der Stufe, tiefste Zubehör-Ecke %.1f mm über der Stufe und %.1f mm über dem Boden darunter, "
                + "%.1f mm vor der Kante, Neigung %.1f°", (drone.getY() - slabTop()) * 1000.0, (corner.y - slabTop()) * 1000.0, (corner.y - base().getY()) * 1000.0,
                (edgeZ() - corner.z) * 1000.0, tilt);
    }

    private static @Nullable String edgeProblem(String what, QuadcopterEntity drone, double rise, Quaternionfc body) {
        double foot = drone.getY() - slabTop();
        double tilt = Math.toDegrees(Math.acos(Math.clamp(DronePhysique.rotatedY(body, 0.0F, 1.0F, 0.0F), -1.0, 1.0)));
        if (rise > TOUCH) {
            return String.format(Locale.ROOT, "%s: %s springt %.1f mm über die Stufe", PILOTED_LABEL, what, rise * 1000.0);
        }
        if (foot > TOUCH) {
            return String.format(Locale.ROOT, "%s: %s schwebt %.1f mm über der Stufe", PILOTED_LABEL, what, foot * 1000.0);
        }
        if (foot < -TOUCH) {
            return String.format(Locale.ROOT, "%s: %s steckt %.1f mm in der Stufe", PILOTED_LABEL, what, -foot * 1000.0);
        }
        if (tilt > LEVEL_DEG) {
            return String.format(Locale.ROOT, "%s: %s steht %.1f° schräg", PILOTED_LABEL, what, tilt);
        }
        return null;
    }

    private static double trackRise(Map<String, Object> state, QuadcopterEntity drone) {
        double rise = Math.max((double) state.getOrDefault(RISE, Double.NEGATIVE_INFINITY), drone.getY() - slabTop());
        state.put(RISE, rise);
        return rise;
    }

    private static StepResult pilotedEdgeStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            ctx.log("Beispiel-Add-on aus, übersprungen");
            return StepResult.pass();
        }
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        PilotSession session = PilotSession.INSTANCE;
        int phase = phase(state);
        if (phase == 0) {
            if (!state.containsKey(FUTURE)) {
                if (session.isActive()) {
                    return since(ctx) > 200 ? finish(ctx, StepResult.fail("vorige FPV-Sitzung endet nicht")) : StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(AccessoryStandSelfTests::spawnPilotedEdge));
                return StepResult.pending();
            }
            StepResult server = await(ctx, "Drohne an die Kante setzen");
            return server.status() == StepResult.Status.PASS ? next(ctx, 1) : server;
        }
        QuadcopterEntity drone = edgeDrone();
        if (phase == 1) {
            if (drone == null || since(ctx) < SETTLE_TICKS) {
                return since(ctx) > 300 ? StepResult.fail(PILOTED_LABEL + ": Drohne kommt nicht beim Client an") : StepResult.pending();
            }
            AirframeSpec p = AirframeSpec.of(drone.build());
            Vector3d com = new Vector3d(drone.getX(), drone.getY() - p.hitboxFootOffset, drone.getZ());
            Vec3 corner = lowestCorner(drone.build(), com, drone.orientation());
            ctx.log(PILOTED_LABEL + " lose: " + edgeState(drone, com, drone.orientation()));
            if (edgeZ() - corner.z <= 0.0 || drone.getZ() <= edgeZ()) {
                return StepResult.fail(PILOTED_LABEL + ": Prüfaufbau falsch, Gimbal nicht über dem tieferen Boden oder Mitte nicht auf der Stufe");
            }
            String problem = edgeProblem("lose", drone, Double.NEGATIVE_INFINITY, drone.orientation());
            if (problem != null) {
                return StepResult.fail(problem);
            }
            session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            next(ctx, 2);
            state.put(FUTURE, ctx.onServer(server -> startPiloted(server, EDGE_DRONE.get(), Station.STONE, PILOTED_LABEL, PilotMode.FPV)));
            return StepResult.pending();
        }
        if (phase == 2 || phase == 3) {
            StepResult server = phase == 2 ? await(ctx, "FPV starten") : StepResult.pass();
            if (server.status() == StepResult.Status.FAIL) {
                return finish(ctx, server);
            }
            if (server.status() != StepResult.Status.PASS || !session.isFpv() || drone == null || session.drone() != drone) {
                return since(ctx) > 400 ? finish(ctx, StepResult.fail(PILOTED_LABEL + ": keine FPV-Sitzung")) : StepResult.pending();
            }
            double rise = trackRise(state, drone);
            if (since(ctx) < (phase == 2 ? FPV_TICKS : ARM_TICKS)) {
                return StepResult.pending();
            }
            Vector3d com = new Vector3d();
            Quaternionf body = new Quaternionf();
            if (!FpvView.displayedBody(com, body)) {
                return finish(ctx, StepResult.fail(PILOTED_LABEL + ": FPV zeigt keine Drohne"));
            }
            String what = phase == 2 ? "im FPV unscharf" : "im FPV scharf";
            ctx.log(String.format(Locale.ROOT, "%s %s: %s, seit dem Start höchstens %.1f mm über der Stufe", PILOTED_LABEL, what, edgeState(drone, com, body),
                    rise * 1000.0));
            if (phase == 3 && !session.armed()) {
                return finish(ctx, StepResult.fail(PILOTED_LABEL + ": lässt sich an der Kante nicht scharf schalten (Sperren " + session.armBlockers() + ")"));
            }
            String problem = edgeProblem(what, drone, rise, body);
            if (problem != null) {
                return finish(ctx, StepResult.fail(problem));
            }
            state.remove(RISE);
            if (phase == 2) {
                session.setTestInput(StickPositions.IDLE, true, StabMode.ANGLE);
                return next(ctx, 3);
            }
            finish(ctx, StepResult.pass());
            return next(ctx, 4);
        }
        if (phase == 4) {
            if (session.isActive() || drone == null || since(ctx) < SETTLE_TICKS) {
                return since(ctx) > 400 ? StepResult.fail(PILOTED_LABEL + ": FPV-Sitzung endet nicht") : StepResult.pending();
            }
            ctx.log(PILOTED_LABEL + " nach der Sitzung lose: " + edgeState(drone, new Vector3d(drone.getX(), drone.getY() - AirframeSpec.of(drone.build()).hitboxFootOffset,
                    drone.getZ()), drone.orientation()));
            String problem = edgeProblem("nach der Sitzung", drone, Double.NEGATIVE_INFINITY, drone.orientation());
            if (problem != null) {
                return StepResult.fail(problem);
            }
            if (!Screenshots.enabled()) {
                return StepResult.pass();
            }
            session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            next(ctx, 5);
            state.put(FUTURE, ctx.onServer(server -> startPiloted(server, EDGE_DRONE.get(), Station.STONE, PILOTED_LABEL, PilotMode.LINE_OF_SIGHT)));
            return StepResult.pending();
        }
        if (phase == 5) {
            StepResult server = await(ctx, "Sichtflug starten");
            if (server.status() == StepResult.Status.FAIL) {
                return finish(ctx, server);
            }
            if (server.status() != StepResult.Status.PASS || !session.isActive() || session.isFpv() || drone == null || session.drone() != drone) {
                return since(ctx) > 400 ? finish(ctx, StepResult.fail(PILOTED_LABEL + ": keine Sichtflug-Sitzung")) : StepResult.pending();
            }
            Vec3 target = new Vec3(drone.getX(), slabTop() + 0.02, edgeZ());
            Vec3 eye = target.add(VIEW_RANGE, 0.08, -0.3);
            next(ctx, 6);
            state.put("target", target);
            state.put(FUTURE, ctx.onServer(server2 -> {
                ServerPlayer player = player(server2);
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(player.level(), eye.x, eye.y - player.getEyeHeight(), eye.z, Set.of(), 0.0F, 0.0F, true);
            }));
            return StepResult.pending();
        }
        Vec3 target = (Vec3) state.get("target");
        if (mc.player != null) {
            look(mc.player, target);
        }
        if (phase == 6) {
            StepResult moved = await(ctx, "Blick von der Seite");
            if (moved.status() == StepResult.Status.FAIL) {
                return finish(ctx, moved);
            }
            mc.options.fov().set(SHOT_FOV);
            if (!mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            Screenshots.clearOverlays(mc);
            double rise = drone == null ? Double.NEGATIVE_INFINITY : trackRise(state, drone);
            if (since(ctx) < FPV_TICKS || moved.status() != StepResult.Status.PASS) {
                return since(ctx) > 400 ? finish(ctx, StepResult.fail(PILOTED_LABEL + ": Blick von der Seite kommt nicht an")) : StepResult.pending();
            }
            if (drone == null || !session.isActive() || session.drone() != drone) {
                return finish(ctx, StepResult.fail(PILOTED_LABEL + ": Sichtflug-Sitzung vor dem Bild beendet"));
            }
            ctx.log(String.format(Locale.ROOT, "%s im Sichtflug unscharf: Fuß der Hitbox %.1f mm über der Stufe, höchstens %.1f mm", PILOTED_LABEL,
                    (drone.getY() - slabTop()) * 1000.0, rise * 1000.0));
            String problem = edgeProblem("im Sichtflug", drone, rise, drone.orientation());
            if (problem != null) {
                return finish(ctx, StepResult.fail(problem));
            }
            state.put(SHOT, Screenshots.capture(PILOTED_FILE));
            return next(ctx, 7);
        }
        @SuppressWarnings("unchecked")
        CompletableFuture<String> shot = (CompletableFuture<String>) state.get(SHOT);
        if (!shot.isDone()) {
            return StepResult.pending();
        }
        if (shot.isCompletedExceptionally()) {
            return finish(ctx, StepResult.fail("Bildschirmfoto: " + shot.exceptionNow()));
        }
        ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(PILOTED_FILE + ".png"));
        return finish(ctx, StepResult.pass());
    }

    private static StepResult looseStep(SelfTest.StepContext ctx, Case c) {
        if (!ready()) {
            ctx.log("Beispiel-Add-on aus, übersprungen");
            return StepResult.pass();
        }
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        switch (phase(state)) {
            case 0 -> {
                if (PilotSession.INSTANCE.isActive()) {
                    return since(ctx) > 200 ? StepResult.fail("vorige FPV-Sitzung endet nicht") : StepResult.pending();
                }
                if (!state.containsKey(FUTURE)) {
                    state.put(FUTURE, ctx.onServer(server -> spawn(server, c)));
                    return StepResult.pending();
                }
                StepResult server = await(ctx, "Drohne setzen");
                return server.status() == StepResult.Status.PASS ? next(ctx, 1) : server;
            }
            case 1 -> {
                QuadcopterEntity drone = clientDrone(c);
                if (drone == null || since(ctx) < SETTLE_TICKS) {
                    return since(ctx) > 300 ? StepResult.fail(c.label + ": Drohne kommt nicht beim Client an") : StepResult.pending();
                }
                DroneBuild build = drone.build();
                AirframeSpec p = AirframeSpec.of(build);
                Vector3d com = new Vector3d(drone.getX(), drone.getY() - p.hitboxFootOffset, drone.getZ());
                double lowest = lowestAccessory(build, com, drone.orientation());
                double gap = lowest - slabTop();
                DroneRenderState s = new DroneRenderState();
                DroneVisuals.fillFromEntity(s, drone, 1.0F);
                double shift = s.originY - DronePhysique.of(build).comAboveFoot();
                ctx.log(String.format(Locale.ROOT, "%s lose: Fuß y %s, tiefste Zubehör-Ecke %.1f mm über der Stufe, Modell %.1f mm gegen das Flugmodell verschoben",
                        c.label, number(drone.getY()), gap * 1000.0, shift * 1000.0));
                if (gap < -TOUCH) {
                    return StepResult.fail(String.format(Locale.ROOT, "%s: Zubehör steckt %.1f mm in der Stufe", c.label, -gap * 1000.0));
                }
                if (gap > TOUCH) {
                    return StepResult.fail(String.format(Locale.ROOT, "%s: Zubehör schwebt %.1f mm über der Stufe", c.label, gap * 1000.0));
                }
                if (Math.abs(shift) > TOUCH) {
                    return StepResult.fail(String.format(Locale.ROOT, "%s: Modell %.1f mm gegen das Flugmodell verschoben", c.label, shift * 1000.0));
                }
                if (!Screenshots.enabled()) {
                    return StepResult.pass();
                }
                Vec3 target = new Vec3(drone.getX(), slabTop() + 0.05, drone.getZ());
                Vec3 eye = target.add(VIEW_RANGE * 0.8, 0.12, VIEW_RANGE * 0.6);
                state.put("target", target);
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.setGameMode(GameType.SPECTATOR);
                    player.teleportTo(player.level(), eye.x, eye.y - player.getEyeHeight(), eye.z, Set.of(), 0.0F, 0.0F, true);
                }));
                return next(ctx, 2);
            }
            case 2 -> {
                Vec3 target = (Vec3) state.get("target");
                if (mc.player != null) {
                    look(mc.player, target);
                }
                mc.options.fov().set(SHOT_FOV);
                if (!mc.gui.hud.isHidden()) {
                    mc.gui.hud.toggle();
                }
                Screenshots.clearOverlays(mc);
                if (since(ctx) < 20) {
                    return StepResult.pending();
                }
                state.put(SHOT, Screenshots.capture(c.file + "_side"));
                return next(ctx, 3);
            }
            default -> {
                Vec3 target = (Vec3) state.get("target");
                if (mc.player != null) {
                    look(mc.player, target);
                }
                @SuppressWarnings("unchecked")
                CompletableFuture<String> shot = (CompletableFuture<String>) state.get(SHOT);
                if (!shot.isDone()) {
                    return StepResult.pending();
                }
                restoreView(mc);
                if (shot.isCompletedExceptionally()) {
                    return StepResult.fail("Bildschirmfoto: " + shot.exceptionNow());
                }
                ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(c.file + "_side.png"));
                return StepResult.pass();
            }
        }
    }

    private static void restoreView(Minecraft mc) {
        if (savedFov > 0) {
            mc.options.fov().set(savedFov);
        }
        if (mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
    }

    private static void startFpv(MinecraftServer server, Case c) {
        startPiloted(server, DRONES.get(c), c.station, c.label, PilotMode.FPV);
    }

    private static void startPiloted(MinecraftServer server, @Nullable Integer id, Station station, String label, PilotMode mode) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        if (id == null || !(level.getEntity(id) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException(label + ": keine Drohne");
        }
        Vec3 center = stationCenter(station);
        player.setGameMode(GameType.CREATIVE);
        player.teleportTo(level, center.x, base().getY() + 1.0, center.z + 3.5, Set.of(), 180.0F, 20.0F, true);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.getInventory().setItem(0, remote);
        player.getInventory().setSelectedSlot(0);
        player.connection.send(new ClientboundSetHeldSlotPacket(0));
        ItemStack goggles = ContentIds.item(drone.build().video().system == VideoSystem.ANALOG ? ContentIds.GOGGLES_ANALOG : ContentIds.GOGGLES_DIGITAL)
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, mode == PilotMode.FPV ? goggles : ItemStack.EMPTY);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, mode);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("Start verweigert: " + result);
        }
    }

    static Vec3 drawnCamera(QuadcopterEntity drone) {
        DroneRenderState s = new DroneRenderState();
        DroneVisuals.fillFromEntity(s, drone, 1.0F);
        if (!DroneVisuals.placeAtDisplayedPose(s, drone)) {
            throw new IllegalStateException("keine angezeigte Lage");
        }
        PilotSession session = PilotSession.INSTANCE;
        Vector3f lens = new Vector3f((float) session.lensRightOffset(), (float) session.lensUpOffset(), (float) -session.lensForwardOffset());
        s.orientation.transform(lens);
        return new Vec3(s.x + lens.x, s.y + s.originY + lens.y, s.z + lens.z);
    }

    private static StepResult fpvStep(SelfTest.StepContext ctx, Case c) {
        if (!ready()) {
            ctx.log("Beispiel-Add-on aus, übersprungen");
            return StepResult.pass();
        }
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        PilotSession session = PilotSession.INSTANCE;
        switch (phase(state)) {
            case 0 -> {
                if (!state.containsKey(FUTURE)) {
                    if (session.isActive()) {
                        return since(ctx) > 200 ? finish(ctx, StepResult.fail("vorige FPV-Sitzung endet nicht")) : StepResult.pending();
                    }
                    session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                    state.put(FUTURE, ctx.onServer(server -> startFpv(server, c)));
                    return StepResult.pending();
                }
                StepResult server = await(ctx, "FPV starten");
                if (server.status() == StepResult.Status.FAIL) {
                    return finish(ctx, server);
                }
                return server.status() == StepResult.Status.PASS ? next(ctx, 1) : server;
            }
            case 1 -> {
                Screenshots.clearOverlays(mc);
                QuadcopterEntity drone = session.drone();
                Integer id = DRONES.get(c);
                if (!session.isFpv() || drone == null || id == null || drone.getId() != id || since(ctx) < FPV_TICKS) {
                    return since(ctx) > 400 ? finish(ctx, StepResult.fail(c.label + ": keine FPV-Sitzung")) : StepResult.pending();
                }
                String problem = checkFpv(ctx, c, drone);
                if (problem != null) {
                    return finish(ctx, StepResult.fail(problem));
                }
                if (!Screenshots.enabled()) {
                    return finish(ctx, StepResult.pass());
                }
                if (!mc.gui.hud.isHidden()) {
                    mc.gui.hud.toggle();
                }
                Screenshots.clearOverlays(mc);
                state.put(SHOT, Screenshots.capture(c.file + "_fpv"));
                state.put(PHASE, 2);
                return StepResult.pending();
            }
            default -> {
                @SuppressWarnings("unchecked")
                CompletableFuture<String> shot = (CompletableFuture<String>) state.get(SHOT);
                if (!shot.isDone()) {
                    return StepResult.pending();
                }
                restoreView(mc);
                if (shot.isCompletedExceptionally()) {
                    return finish(ctx, StepResult.fail("Bildschirmfoto: " + shot.exceptionNow()));
                }
                ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(c.file + "_fpv.png"));
                return finish(ctx, StepResult.pass());
            }
        }
    }

    private static @Nullable String checkFpv(SelfTest.StepContext ctx, Case c, QuadcopterEntity drone) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return "keine Welt";
        }
        Vector3d com = new Vector3d();
        Quaternionf body = new Quaternionf();
        if (!FpvView.displayedBody(com, body)) {
            return c.label + ": FPV zeigt keine Drohne";
        }
        double lowest = lowestAccessory(drone.build(), com, body);
        Vec3 drawn = drawnCamera(drone);
        Vector3dc requested = FpvEyeGuard.INSTANCE.requested();
        Vec3 eye = new Vec3(requested.x(), requested.y(), requested.z());
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        PlayerRenderState.BlockOverlay overlay = mc.gameRenderer.gameRenderState().levelRenderState.playerRenderState.blockOverlay;
        double tilt = Math.toDegrees(Math.acos(Math.clamp(DronePhysique.rotatedY(body, 0.0F, 1.0F, 0.0F), -1.0, 1.0)));
        boolean tilted = (PilotSession.INSTANCE.armBlockers() & ArmBlocker.TILTED.bit()) != 0;
        ctx.log(String.format(Locale.ROOT, "%s im FPV: tiefste Zubehör-Ecke %.1f mm über der Stufe, Auge %.1f mm neben der gezeichneten Kamera (%.1f mm höher), "
                        + "Kamera %.1f mm über der Stufe, Überdeckung %s, Neigung %.1f°, Scharfschalt-Sperre wegen Neigung %s", c.label, (lowest - slabTop()) * 1000.0,
                eye.distanceTo(drawn) * 1000.0, (eye.y - drawn.y) * 1000.0, (camera.y - slabTop()) * 1000.0, overlay == null ? "keine" : overlay.atlasLocation(),
                tilt, tilted ? "ja" : "nein"));
        if (tilt > LEVEL_DEG) {
            return String.format(Locale.ROOT, "%s: steht im FPV %.1f° schräg auf dem Zubehör statt eben", c.label, tilt);
        }
        if (tilted) {
            return c.label + ": lässt sich am Boden wegen der Neigung nicht scharf schalten";
        }
        if (lowest < slabTop() - TOUCH) {
            return String.format(Locale.ROOT, "%s: im FPV steckt das Zubehör %.1f mm in der Stufe", c.label, (slabTop() - lowest) * 1000.0);
        }
        if (eye.distanceTo(drawn) > EYE_TOLERANCE) {
            return String.format(Locale.ROOT, "%s: FPV-Auge %.1f mm neben der gezeichneten Kamera (%.1f mm tiefer)", c.label, eye.distanceTo(drawn) * 1000.0,
                    (drawn.y - eye.y) * 1000.0);
        }
        if (overlay != null) {
            return c.label + ": FPV-Bild von der Block-Überlagerung bedeckt (" + overlay.atlasLocation() + ")";
        }
        if (CameraClipSelfTests.insideVisual(mc.level, camera.x, camera.y, camera.z)) {
            return c.label + ": FPV-Kamera steckt im Block bei " + camera;
        }
        return null;
    }

    private static StepResult finish(SelfTest.StepContext ctx, StepResult result) {
        PilotSession.INSTANCE.clearTestInput();
        if (PilotSession.INSTANCE.isActive()) {
            PilotSession.INSTANCE.requestStop();
        }
        Minecraft mc = Minecraft.getInstance();
        restoreView(mc);
        return result;
    }

    private static final class ProbeCamera extends Camera {
        void place(Vec3 at) {
            this.setPosition(at);
        }
    }

    private static StepResult probeStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (PilotSession.INSTANCE.isActive() || FpvView.active()) {
            return ctx.tick() > 200 ? StepResult.fail("FPV-Sitzung endet nicht") : StepResult.pending();
        }
        if (mc.level == null || ctx.tick() < 5) {
            return StepResult.pending();
        }
        ProbeCamera camera = new ProbeCamera();
        List<String> wrong = new ArrayList<>();
        int covered = 0;
        for (Probe probe : PROBES) {
            BlockPos cell = probeCell(probe.cell());
            camera.place(new Vec3(cell.getX() + 0.5, cell.getY() + probe.height(), cell.getZ() + 0.5));
            PlayerRenderState state = new PlayerRenderState();
            FpvView.coverInsideBlock(camera, state);
            boolean hidden = state.blockOverlay != null;
            if (hidden) {
                covered++;
            }
            if (hidden != probe.covered()) {
                wrong.add(probe.label() + (hidden ? ": abgedeckt" : ": nicht abgedeckt"));
            }
        }
        if (!wrong.isEmpty()) {
            return StepResult.fail("FPV-Bild an Teil-Blöcken falsch: " + String.join("; ", wrong));
        }
        ctx.log(PROBES.size() + " Kamerastellen an Stufen, Schnee, Teppich, Treppe, Stein und Glas: " + covered + " abgedeckt, "
                + (PROBES.size() - covered) + " frei, alle wie erwartet");
        return StepResult.pass();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            PilotSession.INSTANCE.clearTestInput();
            if (PilotSession.INSTANCE.isActive()) {
                PilotSession.INSTANCE.requestStop();
            }
            Minecraft mc = Minecraft.getInstance();
            restoreView(mc);
            if (Screenshots.enabled()) {
                Screenshots.hideMarker(false);
            }
            state.put(FUTURE, ctx.onServer(server -> {
                for (Case c : Case.values()) {
                    discard(server, c);
                }
                discardEdge(server);
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
        StepResult server = await(ctx, "Aufräumen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        BASE.set(null);
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

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private record Saved(BlockPos pos, BlockState state) {
    }

    private record SavedPlayer(Vec3 pos, float yRot, float xRot, GameType mode, int selected, ItemStack slot0, ItemStack head) {
    }
}
