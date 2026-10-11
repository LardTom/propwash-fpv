package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.FreeformBlocks;
import de.lardtom.propwash.race.FreeformFill;
import de.lardtom.propwash.race.FreeformGateItem;
import de.lardtom.propwash.race.FreeformRegion;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

public final class FreeformGateSelfTests {
    public static final int OFFSET_X = -30;
    public static final int OFFSET_Z = -60;
    public static final Bounds TEST_AREA = new Bounds(-14, 18, -6, 40, 23);
    public static final Bounds SHOT_AREA = new Bounds(-12, 28, -14, 14, 12);
    public static final int CORRIDOR_Z = 6;
    public static final int CAVE_Z = 22;
    public static final int SLOT_Z = 28;
    public static final int BIG_Z = 34;
    public static final int[][] CAVE_ROWS = {{-2, 1}, {-3, 2}, {-3, 2}, {-2, 2}, {-1, 1}};
    private static final long MS = 1_000_000L;
    private static final String FUTURE = "future";
    private static final String PLACING = "placing";
    private static final AtomicReference<Site> SITE = new AtomicReference<>();
    private static final AtomicReference<Site> SHOT_SITE = new AtomicReference<>();
    private static final AtomicReference<String> RESULT = new AtomicReference<>();
    private static final AtomicReference<Vec3> SHOT_HOME = new AtomicReference<>();
    private static final AtomicLong CLOCK = new AtomicLong(-1L);
    private static final Map<BlockPos, Map<BlockPos, BlockState>> SAVED = new LinkedHashMap<>();

    public record Site(BlockPos origin, int corridor, int cave, int shaft, int course) {
    }

    public record Bounds(int x0, int x1, int z0, int z1, int top) {
    }

    private FreeformGateSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("race: Freiform-Gate setzen im Gang, Höhleneingang und Schacht, Ablehnungen und Welt-Regel", 300,
                ctx -> onServer(ctx, FreeformGateSelfTests::placeCheck));
        SelfTest.clientStep("race: Freiform-Gate beim Client, Zielen nur mit Werkzeug oder beim Schleichen", 200, FreeformGateSelfTests::clientStep);
        SelfTest.clientStep("race: Freiform-Gate als Kursstart, Runde vom Client und Server gezählt", 200,
                ctx -> onServer(ctx, FreeformGateSelfTests::lapCheck));
        SelfTest.clientStep("race: Freiform-Gate drehen, Lücke melden, neu abtasten, speichern und laden", 300,
                ctx -> onServer(ctx, FreeformGateSelfTests::editCheck));
        SelfTest.clientStep("race: Freiform-Gate im Spiel einstellen kehrt die Richtung um", 400, FreeformGateSelfTests::screenStep);
        SelfTest.clientStep("race: Freiform-Gate aufräumen", 200, FreeformGateSelfTests::cleanupStep);
    }

    private static StepResult future(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Aktion fehlgeschlagen: " + future.exceptionNow());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private static StepResult onServer(SelfTest.StepContext ctx, Function<MinecraftServer, String> action) {
        if (ctx.tick() == 0) {
            RESULT.set(null);
            ctx.state().put(FUTURE, ctx.onServer(server -> RESULT.set(action.apply(server))));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        String problem = RESULT.get();
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static void fill(ServerLevel level, BlockPos from, BlockPos to, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            level.setBlockAndUpdate(pos, state);
        }
    }

    private static void loadChunks(ServerLevel level, BlockPos o, Bounds b) {
        for (int cx = (o.getX() + b.x0() - 2) >> 4; cx <= (o.getX() + b.x1() + 2) >> 4; cx++) {
            for (int cz = (o.getZ() + b.z0() - 2) >> 4; cz <= (o.getZ() + b.z1() + 2) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    private static boolean ticking(ServerLevel level, BlockPos o, Bounds b) {
        for (int cx = (o.getX() + b.x0() - 2) >> 4; cx <= (o.getX() + b.x1() + 2) >> 4; cx++) {
            for (int cz = (o.getZ() + b.z0() - 2) >> 4; cz <= (o.getZ() + b.z1() + 2) >> 4; cz++) {
                if (!level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void clearArea(ServerLevel level, BlockPos o, BlockState floor, Bounds b) {
        loadChunks(level, o, b);
        Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(o.offset(b.x0(), -1, b.z0()), o.offset(b.x1(), b.top(), b.z1()))) {
            saved.put(pos.immutable(), level.getBlockState(pos));
        }
        synchronized (SAVED) {
            SAVED.putIfAbsent(o.immutable(), saved);
        }
        fill(level, o.offset(b.x0(), 0, b.z0()), o.offset(b.x1(), b.top(), b.z1()), Blocks.AIR.defaultBlockState());
        fill(level, o.offset(b.x0(), -1, b.z0()), o.offset(b.x1(), -1, b.z1()), floor);
    }

    private static void restoreArea(ServerLevel level, BlockPos o) {
        Map<BlockPos, BlockState> saved;
        synchronized (SAVED) {
            saved = SAVED.remove(o);
        }
        if (saved != null) {
            saved.forEach(level::setBlockAndUpdate);
        }
    }

    public static boolean caveOpening(int x, int y) {
        return y >= 0 && y < CAVE_ROWS.length && x >= CAVE_ROWS[y][0] && x <= CAVE_ROWS[y][1];
    }

    public static int caveCells() {
        int cells = 0;
        for (int[] row : CAVE_ROWS) {
            cells += row[1] - row[0] + 1;
        }
        return cells;
    }

    private static void build(ServerLevel level, BlockPos o) {
        clearArea(level, o, Blocks.SMOOTH_STONE.defaultBlockState(), TEST_AREA);
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        fill(level, o.offset(-2, 0, 0), o.offset(-2, 2, 14), bricks);
        fill(level, o.offset(2, 0, 0), o.offset(2, 2, 14), bricks);
        fill(level, o.offset(-2, 3, 0), o.offset(2, 3, 14), bricks);
        fill(level, o.offset(-6, 0, CAVE_Z), o.offset(6, 7, CAVE_Z + 1), stone);
        for (int y = 0; y < CAVE_ROWS.length; y++) {
            for (int x = CAVE_ROWS[y][0]; x <= CAVE_ROWS[y][1]; x++) {
                level.setBlockAndUpdate(o.offset(x, y, CAVE_Z), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(o.offset(x, y, CAVE_Z + 1), Blocks.AIR.defaultBlockState());
            }
        }
        fill(level, o.offset(-4, 0, SLOT_Z), o.offset(4, 5, SLOT_Z), stone);
        fill(level, o.offset(0, 0, SLOT_Z), o.offset(1, 5, SLOT_Z), Blocks.AIR.defaultBlockState());
        fill(level, o.offset(-12, 0, BIG_Z), o.offset(12, 21, BIG_Z), stone);
        fill(level, o.offset(-10, 0, BIG_Z), o.offset(10, 19, BIG_Z), Blocks.AIR.defaultBlockState());
        fill(level, o.offset(9, 0, 5), o.offset(12, 8, 8), stone);
        fill(level, o.offset(10, 0, 6), o.offset(11, 8, 7), Blocks.AIR.defaultBlockState());
    }

    public static InteractionResult use(ServerPlayer player, BlockPos clicked, Direction face, float yaw, float pitch) {
        ItemStack previous = player.getMainHandItem().copy();
        float previousYaw = player.getYRot();
        float previousPitch = player.getXRot();
        try {
            player.setYRot(yaw);
            player.setXRot(pitch);
            ItemStack stack = new ItemStack(RaceModule.FREEFORM_GATE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            Vec3 hit = Vec3.atCenterOf(clicked).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5));
            return stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, clicked, false)));
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            player.setYRot(previousYaw);
            player.setXRot(previousPitch);
        }
    }

    private static AABB area(BlockPos o) {
        int x0 = Math.min(TEST_AREA.x0(), SHOT_AREA.x0()) - 4;
        int x1 = Math.max(TEST_AREA.x1(), SHOT_AREA.x1()) + 4;
        int z0 = Math.min(TEST_AREA.z0(), SHOT_AREA.z0()) - 4;
        int z1 = Math.max(TEST_AREA.z1(), SHOT_AREA.z1()) + 4;
        return new AABB(Vec3.atLowerCornerOf(o.offset(x0, -4, z0)), Vec3.atLowerCornerOf(o.offset(x1, TEST_AREA.top() + 4, z1)));
    }

    private static List<GateEntity> freeform(ServerLevel level, BlockPos o) {
        return level.getEntitiesOfClass(GateEntity.class, area(o), gate -> !gate.isRemoved() && gate.kind() == CourseElement.FREEFORM);
    }

    private static GateEntity placeOne(ServerPlayer player, BlockPos o, BlockPos clicked, Direction face, float yaw, float pitch, String name) {
        ServerLevel level = player.level();
        List<GateEntity> before = freeform(level, o);
        InteractionResult result = use(player, clicked, face, yaw, pitch);
        if (!result.consumesAction()) {
            throw new IllegalStateException(name + " nicht gesetzt: " + result);
        }
        List<GateEntity> after = freeform(level, o);
        after.removeAll(before);
        if (after.size() != 1) {
            throw new IllegalStateException(name + ": " + after.size() + " neue Freiform-Gates");
        }
        return after.getFirst();
    }

    private static @Nullable String refused(ServerPlayer player, BlockPos o, BlockPos clicked, Direction face, float yaw, float pitch, String what) {
        int before = freeform(player.level(), o).size();
        InteractionResult result = use(player, clicked, face, yaw, pitch);
        if (result.consumesAction() || freeform(player.level(), o).size() != before) {
            return what + " nicht abgelehnt: " + result;
        }
        return null;
    }

    private static @Nullable String expect(GateEntity gate, String name, int axis, int variant, int width, int height, int cells, Vec3 anchor) {
        FreeformRegion region = gate.region();
        if (region == null) {
            return name + ": keine Fläche";
        }
        if (region.axis() != axis || gate.variant() != variant || region.width() != width || region.height() != height || region.cells() != cells) {
            return name + ": Fläche " + region + " Richtung " + gate.variant();
        }
        if (gate.position().distanceTo(anchor) > 1.0E-6) {
            return name + ": Anker " + gate.position() + " statt " + anchor;
        }
        if (gate.colliders().length != 0 || gate.orientedColliders().length != 0) {
            return name + ": hat Kollisionsteile";
        }
        AABB box = gate.getBoundingBox();
        GateGeometry g = gate.geometry();
        double[] bounds = new double[6];
        g.bounds((minX, minY, minZ, maxX, maxY, maxZ) -> {
            bounds[0] = minX;
            bounds[1] = minY;
            bounds[2] = minZ;
            bounds[3] = maxX;
            bounds[4] = maxY;
            bounds[5] = maxZ;
        });
        if (Math.abs(box.minX - bounds[0]) > 1.0E-6 || Math.abs(box.maxY - bounds[4]) > 1.0E-6 || Math.abs(box.maxZ - bounds[5]) > 1.0E-6) {
            return name + ": Entity-Box " + box + " passt nicht zur Fläche";
        }
        return null;
    }

    private static Site place(ServerPlayer player, BlockPos o) {
        ServerLevel level = player.level();
        build(level, o);
        GateEntity corridor = placeOne(player, o, o.offset(0, -1, CORRIDOR_Z), Direction.UP, 0.0F, 35.0F, "Gang");
        GateEntity cave = placeOne(player, o, o.offset(0, -1, CAVE_Z), Direction.UP, 0.0F, 20.0F, "Höhleneingang");
        GateEntity shaft = placeOne(player, o, o.offset(9, 4, 6), Direction.EAST, 90.0F, 75.0F, "Schacht");
        return new Site(o.immutable(), corridor.getId(), cave.getId(), shaft.getId(), GateEntity.NO_COURSE);
    }

    private static @Nullable String placeCheck(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        BlockPos o = new BlockPos(player.getBlockX() + OFFSET_X, player.getBlockY(), player.getBlockZ() + OFFSET_Z);
        if (WorldRules.get(server, WorldRuleSpecs.FREEFORM_GATE_MAX_SIZE) != 20) {
            return "Welt-Regel freeform_gate_max_size steht nicht auf 20";
        }
        Site site = place(player, o);
        SITE.set(site);
        GateEntity corridor = (GateEntity) level.getEntity(site.corridor());
        GateEntity cave = (GateEntity) level.getEntity(site.cave());
        GateEntity shaft = (GateEntity) level.getEntity(site.shaft());
        String problem = expect(corridor, "Gang", FreeformRegion.AXIS_Z, 0, 3, 3, 9, new Vec3(o.getX() + 0.5, o.getY() + 1.5, o.getZ() + CORRIDOR_Z + 0.5));
        if (problem != null) {
            return problem;
        }
        problem = expect(cave, "Höhleneingang", FreeformRegion.AXIS_Z, 0, 6, 5, caveCells(), new Vec3(o.getX(), o.getY() + 2.5, o.getZ() + CAVE_Z + 0.5));
        if (problem != null) {
            return problem;
        }
        problem = expect(shaft, "Schacht", FreeformRegion.AXIS_Y, 1, 2, 2, 4, new Vec3(o.getX() + 11.0, o.getY() + 4.5, o.getZ() + 7.0));
        if (problem != null) {
            return problem;
        }
        FreeformRegion caveRegion = cave.region();
        for (int y = 0; y < CAVE_ROWS.length; y++) {
            for (int x = -3; x <= 2; x++) {
                if (caveRegion.contains(x + 3, y) != caveOpening(x, y)) {
                    return "Höhleneingang: Feld " + x + "/" + y + " falsch";
                }
            }
        }
        problem = refused(player, o, o.offset(0, -1, CORRIDOR_Z), Direction.UP, 0.0F, 35.0F, "zweites Freiform-Gate in derselben Öffnung");
        if (problem == null) {
            problem = refused(player, o, o.offset(0, -1, SLOT_Z), Direction.UP, 0.0F, 30.0F, "Schlitz bis zum Himmel");
        }
        if (problem == null) {
            problem = refused(player, o, o.offset(-6, 0, CAVE_Z), Direction.SOUTH, 0.0F, 0.0F, "Klick ohne freien Platz davor");
        }
        if (problem == null) {
            problem = refused(player, o, o.offset(0, -1, BIG_Z), Direction.UP, 0.0F, 20.0F, "Öffnung 21 × 20 bei Grenze 20");
        }
        if (problem != null) {
            return problem;
        }
        FreeformFill.Result big = FreeformBlocks.fill(level, new FreeformFill.Aim(FreeformRegion.AXIS_Z, 0, o.getX(), o.getY(), o.getZ() + BIG_Z));
        if (big.status() != FreeformFill.Status.TOO_BIG || big.width() != 21 || big.height() != 20) {
            return "zu große Öffnung: " + big.status() + " " + big.width() + "×" + big.height();
        }
        if (!WorldRules.set(server, WorldRuleSpecs.FREEFORM_GATE_MAX_SIZE, 21)) {
            return "Welt-Regel nicht gesetzt";
        }
        try {
            GateEntity wide = placeOne(player, o, o.offset(0, -1, BIG_Z), Direction.UP, 0.0F, 20.0F, "Öffnung 21 × 20 bei Grenze 21");
            if (wide.region().cells() != 420) {
                return "große Öffnung: " + wide.region();
            }
            wide.discard();
        } finally {
            WorldRules.set(server, WorldRuleSpecs.FREEFORM_GATE_MAX_SIZE, WorldRuleSpecs.FREEFORM_GATE_MAX_SIZE.defaultValue());
        }
        problem = triggersReleased(level, o.offset(-1, 0, CORRIDOR_Z), o.offset(1, 0, CORRIDOR_Z));
        if (problem != null) {
            return problem;
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        drone.setBuild(DroneBuild.WHOOP);
        drone.setNoGravity(true);
        try {
            Vec3 start = new Vec3(o.getX() + 0.5, o.getY() + 1.2, o.getZ() + CORRIDOR_Z - 1.0);
            drone.snapTo(start.x, start.y, start.z, 0.0F, 0.0F);
            if (!level.addFreshEntity(drone)) {
                return "Drohne nicht gespawnt";
            }
            drone.move(MoverType.SELF, new Vec3(0.0, 0.0, 3.0));
            if (drone.position().distanceTo(start.add(0.0, 0.0, 3.0)) > 1.0E-6) {
                return "Drohne prallt am Freiform-Gate ab: " + drone.position();
            }
        } finally {
            drone.discard();
        }
        return null;
    }

    private static @Nullable String triggersReleased(ServerLevel level, BlockPos platePos, BlockPos wirePos) {
        level.setBlockAndUpdate(platePos, Blocks.OAK_PRESSURE_PLATE.defaultBlockState().setValue(PressurePlateBlock.POWERED, true));
        level.setBlockAndUpdate(wirePos, Blocks.TRIPWIRE.defaultBlockState().setValue(TripWireBlock.POWERED, true));
        try {
            level.getBlockState(platePos).tick(level, platePos, level.getRandom());
            level.getBlockState(wirePos).tick(level, wirePos, level.getRandom());
            BlockState plate = level.getBlockState(platePos);
            BlockState wire = level.getBlockState(wirePos);
            if (!plate.is(Blocks.OAK_PRESSURE_PLATE) || plate.getValue(PressurePlateBlock.POWERED)) {
                return "Freiform-Gate hält die Holz-Druckplatte im Gang gedrückt: " + plate;
            }
            if (!wire.is(Blocks.TRIPWIRE) || wire.getValue(TripWireBlock.POWERED)) {
                return "Freiform-Gate löst den Stolperdraht im Gang aus: " + wire;
            }
            return null;
        } finally {
            level.setBlockAndUpdate(platePos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(wirePos, Blocks.AIR.defaultBlockState());
        }
    }

    private static StepResult clientStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        Site site = SITE.get();
        LocalPlayer player = minecraft.player;
        if (site == null || minecraft.level == null || player == null) {
            return StepResult.fail("kein Testbau");
        }
        int stage = (int) ctx.state().getOrDefault("stage", 0);
        if (stage == 0) {
            for (int id : List.of(site.corridor(), site.cave(), site.shaft())) {
                if (!(minecraft.level.getEntity(id) instanceof GateEntity gate) || !gate.freeform()) {
                    return ctx.tick() > 150 ? StepResult.fail("Freiform-Gate " + id + " fehlt beim Client") : StepResult.pending();
                }
            }
            ctx.state().put(FUTURE, ctx.onServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(RaceModule.FREEFORM_GATE.get()))));
            ctx.state().put("stage", 1);
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (!player.getMainHandItem().is(RaceModule.FREEFORM_GATE.get())) {
            return ctx.tick() > 150 ? StepResult.fail("Item kommt nicht beim Client an") : StepResult.pending();
        }
        GateEntity corridor = (GateEntity) minecraft.level.getEntity(site.corridor());
        BlockPos o = site.origin();
        Vec3 from = new Vec3(o.getX() + 0.5, o.getY() + 1.5, o.getZ() + CORRIDOR_Z - 3.0);
        Vec3 to = new Vec3(o.getX() + 0.5, o.getY() + 1.5, o.getZ() + CORRIDOR_Z + 3.0);
        Vec3 expected = new Vec3(from.x, from.y, o.getZ() + CORRIDOR_Z + 0.5);
        if (stage == 1) {
            AABB through = corridor.pickBox(from, to, player);
            if (through.maxY > minecraft.level.getMinY()) {
                return StepResult.fail("ohne Schleichen fängt das Freiform-Gate den Blick ab, dahinter ließe sich nichts setzen: " + through);
            }
            minecraft.options.keyShift.setDown(true);
            ctx.state().put("stage", 2);
            ctx.state().put("since", ctx.tick());
            return StepResult.pending();
        }
        if (!player.isShiftKeyDown()) {
            if (ctx.tick() - (int) ctx.state().get("since") > 40) {
                minecraft.options.keyShift.setDown(false);
                return StepResult.fail("Schleichen kommt nicht an");
            }
            return StepResult.pending();
        }
        AABB hit = corridor.pickBox(from, to, player);
        minecraft.options.keyShift.setDown(false);
        if (hit.getCenter().distanceTo(expected) > 0.05) {
            return StepResult.fail("Zielen beim Schleichen mit Freiform-Gate in der Hand trifft die Fläche nicht: " + hit);
        }
        ItemStack held = player.getMainHandItem();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            AABB miss = corridor.pickBox(from, to, player);
            if (miss.maxY > minecraft.level.getMinY()) {
                return StepResult.fail("ohne Werkzeug fängt das Freiform-Gate den Blick ab: " + miss);
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
            AABB tool = corridor.pickBox(from, to, player);
            if (tool.getCenter().distanceTo(expected) > 0.05) {
                return StepResult.fail("Zielen mit dem Kurs-Werkzeug trifft die Fläche nicht: " + tool);
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
        }
        FreeformView view = FreeformGateRenderer.view(corridor, corridor.region());
        if (!view.intact() || view.segmentCount() != 4) {
            return StepResult.fail("Umriss beim Client: " + view.segmentCount() + " Stücke, intakt " + view.intact());
        }
        ctx.onServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        return StepResult.pass();
    }

    private static List<Vec3> lapPath(BlockPos o, GateGeometry cave, boolean skipCave) {
        double x = o.getX();
        double y = o.getY();
        double z = o.getZ();
        List<Vec3> path = new ArrayList<>();
        path.add(new Vec3(x + 0.5, y + 1.5, z + 2.0));
        path.add(new Vec3(x + 0.5, y + 1.5, z + 10.0));
        path.add(new Vec3(x + 0.5, y + 1.5, z + 16.0));
        if (!skipCave) {
            path.add(new Vec3(cave.centerX(), cave.centerY(), z + 19.0));
            path.add(new Vec3(cave.centerX(), cave.centerY(), z + 26.0));
        }
        path.add(new Vec3(x + 16.0, y + 3.0, z + 30.0));
        path.add(new Vec3(x + 16.0, y + 3.0, z + 15.0));
        path.add(new Vec3(x + 16.0, y + 3.0, z));
        path.add(new Vec3(x + 0.5, y + 1.5, z - 2.0));
        path.add(new Vec3(x + 0.5, y + 1.5, z + 10.0));
        return path;
    }

    private static long fly(ServerPlayer player, List<Vec3> path, long start, long lap) {
        for (int i = 1; i < path.size(); i++) {
            long t = start + lap * i / (path.size() - 1);
            RaceServer.onDroneMoved(player, path.get(i - 1), path.get(i), t);
        }
        return start + lap;
    }

    private static RacePayloads.RaceReportC2S report(RacePayloads.ReportKind kind, int course, int lap, long lapNanos, long total) {
        return new RacePayloads.RaceReportC2S(kind, course, lap, lapNanos, total);
    }

    private static @Nullable String lapCheck(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Site site = SITE.get();
        CourseRegistry registry = CourseRegistry.get();
        if (site == null || registry == null) {
            return "kein Testbau";
        }
        GateEntity corridor = (GateEntity) level.getEntity(site.corridor());
        GateEntity cave = (GateEntity) level.getEntity(site.cave());
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        CourseToolItem.useOnGate(tool, player, corridor);
        CourseToolItem.useOnGate(tool, player, cave);
        int id = CourseToolItem.courseOf(tool);
        CourseRegistry.Course course = registry.course(id);
        if (course == null || course.gates.size() != 2 || corridor.gateIndex() != 0 || cave.gateIndex() != 1) {
            return "Freiform-Gates nicht als Kurs mit Start angehängt";
        }
        SITE.set(new Site(site.origin(), site.corridor(), site.cave(), site.shaft(), id));
        List<GateGeometry> gates = course.toData().geometry();
        if (!gates.get(0).equals(corridor.geometry()) || !gates.get(1).equals(cave.geometry())) {
            return "Kursdaten des Servers weichen von den Entities ab";
        }
        List<Vec3> path = lapPath(site.origin(), cave.geometry(), false);
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(1);
        tracker.setCourse(id, gates, 0L);
        long now = 0L;
        List<LapTracker.Event> events = new ArrayList<>();
        for (Vec3 p : path) {
            now += 200 * MS;
            tracker.update(now, p.x, p.y, p.z);
            events.addAll(tracker.drainEvents());
        }
        List<LapTracker.EventType> types = events.stream().map(LapTracker.Event::type).toList();
        if (!types.equals(List.of(LapTracker.EventType.START, LapTracker.EventType.GATE, LapTracker.EventType.LAP, LapTracker.EventType.FINISH))) {
            return "Runde durch die Freiform-Gates zählt nicht: " + types;
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        Vec3 first = path.getFirst();
        drone.snapTo(first.x, first.y, first.z, 0.0F, 0.0F);
        drone.setPilot(player);
        if (!level.addFreshEntity(drone)) {
            return "Drohne nicht gespawnt";
        }
        try {
            long minimum = RaceServer.minimumLapNanos(course, player);
            long lap = Math.max(minimum * 3L, 9_000L * MS);
            long t = System.nanoTime() + 60_000L * MS;
            if (RaceServer.handle(report(RacePayloads.ReportKind.START, id, 0, 0L, 0L), player, t) != RaceServer.Verdict.ACCEPTED) {
                return "Start abgelehnt";
            }
            long crossed = fly(player, lapPath(site.origin(), cave.geometry(), true), t + 100L * MS, lap);
            t = crossed + 100L * MS;
            RaceServer.Verdict verdict = RaceServer.handle(report(RacePayloads.ReportKind.LAP, id, 1, lap, 0L), player, t);
            if (verdict == RaceServer.Verdict.PENDING) {
                t += RaceServer.PENDING_TIMEOUT_NANOS + 100L * MS;
                verdict = RaceServer.flush(player, t);
            }
            if (verdict == RaceServer.Verdict.ACCEPTED || !registry.board(course).top(null, 10).isEmpty()) {
                return "Runde am Höhleneingang vorbei angenommen: " + verdict;
            }
            t += 2_000L * MS;
            RaceServer.handle(report(RacePayloads.ReportKind.START, id, 0, 0L, 0L), player, t);
            crossed = fly(player, path, t + 100L * MS, lap);
            t = crossed + 100L * MS;
            verdict = RaceServer.handle(report(RacePayloads.ReportKind.LAP, id, 1, lap, 0L), player, t);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Runde durch beide Freiform-Gates abgelehnt: " + verdict + " (Mindestzeit " + minimum / MS + " ms)";
            }
            List<Leaderboard.Entry> ranking = registry.board(course).top(null, 10);
            if (ranking.isEmpty() || ranking.getFirst().nanos() != lap) {
                return "Bestenliste mit Freiform-Gates nicht aktualisiert";
            }
            RaceServer.handle(report(RacePayloads.ReportKind.FINISH, id, 1, 0L, lap), player, t);
        } finally {
            drone.setPilot(null);
            drone.discard();
        }
        return null;
    }

    private static @Nullable String editCheck(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Site site = SITE.get();
        if (site == null) {
            return "kein Testbau";
        }
        BlockPos o = site.origin();
        GateEntity corridor = (GateEntity) level.getEntity(site.corridor());
        GateGeometry before = corridor.geometry();
        Vec3 home = player.position();
        Vec3 stand = new Vec3(o.getX() + 0.5, o.getY(), o.getZ() + CORRIDOR_Z - 2.5);
        ItemStack held = player.getMainHandItem().copy();
        player.teleportTo(level, stand.x, stand.y, stand.z, Set.of(), 0.0F, 10.0F, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.FREEFORM_GATE.get()));
        try {
            if (GateMounting.rotate(player, corridor, false) != GateMounting.Result.ROTATED || corridor.variant() != 1) {
                return "Gate drehen kehrt die Richtung nicht um: " + corridor.geometry();
            }
            CourseRegistry registry = CourseRegistry.get();
            CourseRegistry.Course course = registry == null ? null : registry.course(site.course());
            if (course == null || course.gates.getFirst().variant() != 1 || !course.toData().geometry().getFirst().equals(corridor.geometry())) {
                return "Kursdaten nach dem Drehen nicht nachgeführt";
            }
            if (GateMounting.rotate(player, corridor, true) != GateMounting.Result.ROTATED || !corridor.geometry().equals(before)) {
                return "Schleichen + Drehen kehrt nicht zurück: " + corridor.geometry();
            }
            BlockPos wall = o.offset(2, 1, CORRIDOR_Z);
            level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
            FreeformFill.Integrity opened = FreeformBlocks.integrity(level, corridor.geometry());
            if (opened.openEdges() != 1 || opened.intact()) {
                return "Lücke im Rand nicht erkannt: " + opened;
            }
            if (GateMounting.rescan(player, corridor) != GateMounting.Result.RESCAN_FAILED || !corridor.geometry().equals(before)) {
                return "Neu abtasten mit Lücke nicht abgelehnt";
            }
            level.setBlockAndUpdate(wall, Blocks.STONE_BRICKS.defaultBlockState());
            BlockPos inside = o.offset(-1, 0, CORRIDOR_Z);
            level.setBlockAndUpdate(inside, Blocks.STONE.defaultBlockState());
            if (GateMounting.rescan(player, corridor) != GateMounting.Result.RESCANNED || corridor.region().cells() != 8) {
                return "Neu abtasten übernimmt den Block nicht: " + corridor.region();
            }
            if (course.toData().geometry().getFirst().region().cells() != 8) {
                return "Kursdaten nach dem Neu-Abtasten nicht nachgeführt";
            }
            level.setBlockAndUpdate(inside, Blocks.AIR.defaultBlockState());
            if (GateMounting.rescan(player, corridor) != GateMounting.Result.RESCANNED || !corridor.geometry().equals(before)) {
                return "Neu abtasten stellt die Fläche nicht wieder her: " + corridor.geometry();
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            player.teleportTo(level, home.x, home.y, home.z, Set.of(), player.getYRot(), player.getXRot(), true);
        }
        ProblemReporter.Collector reporter = new ProblemReporter.Collector();
        TagValueOutput output = TagValueOutput.createWithContext(reporter, server.registryAccess());
        corridor.saveWithoutId(output);
        CompoundTag tag = output.buildResult();
        if (!tag.getString("Region").orElse("").equals(corridor.region().encode())) {
            return "NBT ohne Fläche: " + tag;
        }
        GateEntity copy = RaceModule.GATE.get().create(level, EntitySpawnReason.LOAD);
        copy.load(TagValueInput.create(reporter, server.registryAccess(), tag));
        if (!copy.geometry().equals(corridor.geometry()) || !copy.freeform()) {
            return "Freiform-Gate nach dem Laden anders: " + copy.geometry();
        }
        tag.putString("Region", "kaputt");
        GateEntity broken = RaceModule.GATE.get().create(level, EntitySpawnReason.LOAD);
        broken.load(TagValueInput.create(reporter, server.registryAccess(), tag));
        if (broken.region() == null || broken.region().cells() != 1) {
            return "kaputte Fläche lädt nicht als Einzelfeld";
        }
        if (!reporter.isEmpty()) {
            return "Meldungen beim Speichern/Laden: " + reporter.getReport();
        }
        return null;
    }

    private static StepResult screenStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        Site site = SITE.get();
        if (site == null || minecraft.level == null || minecraft.player == null) {
            return StepResult.fail("kein Testbau");
        }
        if (!(minecraft.level.getEntity(site.cave()) instanceof GateEntity cave)) {
            return StepResult.fail("Höhleneingang fehlt beim Client");
        }
        int stage = (int) ctx.state().getOrDefault("stage", 0);
        BlockPos o = site.origin();
        if (stage == 0) {
            ctx.state().put("home", minecraft.player.position());
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                player.teleportTo(player.level(), o.getX() + 0.5, o.getY(), o.getZ() + CAVE_Z - 3.0, Set.of(), 0.0F, 15.0F, true);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.FREEFORM_GATE.get()));
            }));
            ctx.state().put("stage", 1);
            return StepResult.pending();
        }
        if (stage == 1) {
            StepResult waiting = future(ctx);
            if (waiting != null) {
                return waiting;
            }
            LocalPlayer player = minecraft.player;
            if (!player.getMainHandItem().is(RaceModule.FREEFORM_GATE.get()) || !player.isWithinEntityInteractionRange(cave, 2.0)) {
                return ctx.tick() > 200 ? StepResult.fail("Spieler nicht am Höhleneingang") : StepResult.pending();
            }
            ctx.state().put("variant", cave.variant());
            GateEditor.start(cave);
            GateEditor editor = GateEditor.current();
            if (editor == null || editor.gate() != cave || minecraft.gui.screen() != null) {
                return StepResult.fail("Einstellen des Freiform-Gates startet nicht oder öffnet ein Fenster");
            }
            if (editor.integrity() == null || !editor.integrity().intact()) {
                return StepResult.fail("Einstellen meldet eine veränderte Öffnung");
            }
            if (!editor.model().rows(0).equals(List.of(GateEditModel.Row.VARIANT))
                    || !editor.model().rows(1).equals(List.of(GateEditModel.Row.SIGNAL, GateEditModel.Row.PULSE, GateEditModel.Row.RACE_ONLY))) {
                return StepResult.fail("Zeilen am Freiform-Gate falsch: " + editor.model().rows());
            }
            GateEditDriver.scroll(minecraft, 1);
            if (editor.status() != GateEditor.Status.FREE || !GateEditor.replaces(cave)
                    || editor.model().variant() != CourseElement.FREEFORM.opposite(cave.variant())) {
                return StepResult.fail("Mausrad kehrt die Richtung in der Vorschau nicht um: " + editor.status());
            }
            GateEditDriver.press(minecraft, InputConstants.KEY_RETURN);
            if (GateEditor.current() != null) {
                return StepResult.fail("Enter übernimmt die Richtung nicht");
            }
            ctx.state().put("stage", 2);
            ctx.state().put("since", ctx.tick());
            return StepResult.pending();
        }
        int variant = (int) ctx.state().get("variant");
        if (stage == 2) {
            if (cave.variant() == variant) {
                return ctx.tick() - (int) ctx.state().get("since") > 100 ? StepResult.fail("Richtung nicht umgekehrt") : StepResult.pending();
            }
            GateEditor.start(cave);
            GateEditor editor = GateEditor.current();
            if (editor == null || editor.status() != GateEditor.Status.SAME) {
                return StepResult.fail("Nach dem Umkehren bleibt eine Änderung offen");
            }
            editor.rotateKey(false);
            if (!editor.apply()) {
                return StepResult.fail("Gate drehen kehrt die Richtung beim Einstellen nicht um: " + editor.status());
            }
            ctx.state().put("stage", 3);
            ctx.state().put("since", ctx.tick());
            return StepResult.pending();
        }
        if (cave.variant() != variant) {
            return ctx.tick() - (int) ctx.state().get("since") > 100 ? StepResult.fail("Richtung nicht zurückgestellt") : StepResult.pending();
        }
        Vec3 home = (Vec3) ctx.state().get("home");
        ctx.onServer(server -> {
            ServerPlayer player = player(server);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.teleportTo(player.level(), home.x, home.y, home.z, Set.of(), player.getYRot(), player.getXRot(), true);
        });
        return StepResult.pass();
    }

    private static void discard(ServerLevel level, BlockPos o, int course) {
        for (GateEntity gate : level.getEntitiesOfClass(GateEntity.class, area(o), gate -> gate.kind() == CourseElement.FREEFORM)) {
            gate.discard();
        }
        restoreArea(level, o);
        CourseRegistry registry = CourseRegistry.get();
        if (registry != null && course != GateEntity.NO_COURSE) {
            registry.deleteCourse(course);
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.keyShift.setDown(false);
        GateEditor.stop();
        Site site = SITE.get();
        if (site == null) {
            return StepResult.pass();
        }
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                discard(player(server).level(), site.origin(), site.course());
                WorldRules.set(server, WorldRuleSpecs.FREEFORM_GATE_MAX_SIZE, WorldRuleSpecs.FREEFORM_GATE_MAX_SIZE.defaultValue());
            }));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        boolean gone = mc.level != null && List.of(site.corridor(), site.cave(), site.shaft()).stream().allMatch(id -> mc.level.getEntity(id) == null);
        if (!gone || (site.course() != GateEntity.NO_COURSE && ClientCourses.get(site.course()) != null)) {
            return ctx.tick() > 150 ? StepResult.fail("Freiform-Gates oder Kurs noch beim Client") : StepResult.pending();
        }
        SITE.set(null);
        return StepResult.pass();
    }

    public static void registerScreenshots(Supplier<BlockPos> base) {
        Supplier<BlockPos> origin = () -> base.get().offset(-34, 0, -420);
        Screenshots.step("bildschirmfotos: Freiform-Gate im Höhleneingang (Tag)", "freeform_gate_cave", 40,
                new View(origin, new Vec3(0.5, 0.0, -11.0), new Vec3(0.3, 2.6, 0.5), false, null));
        Screenshots.step("bildschirmfotos: Freiform-Gate im Höhleneingang (Nacht)", "freeform_gate_cave_night", 40,
                new View(origin, new Vec3(0.5, 0.0, -11.0), new Vec3(0.3, 2.6, 0.5), true, null));
        Screenshots.step("bildschirmfotos: Freiform-Gate im Gang (Tag)", "freeform_gate_corridor", 40,
                new View(origin, new Vec3(16.5, 0.0, -4.5), new Vec3(16.5, 1.8, 6.5), false, null));
        Screenshots.step("bildschirmfotos: Freiform-Gate im Gang (Nacht)", "freeform_gate_corridor_night", 40,
                new View(origin, new Vec3(16.5, 0.0, -4.5), new Vec3(16.5, 1.8, 6.5), true, null));
        Screenshots.step("bildschirmfotos: Freiform-Gate Vorschau beim Zielen", "freeform_gate_preview", 70,
                new View(origin, new Vec3(23.5, 0.0, -2.0), new Vec3(21.98, 1.5, 2.5), false, () -> new ItemStack(RaceModule.FREEFORM_GATE.get())));
        Screenshots.step("bildschirmfotos: Freiform-Gate im Spiel einstellen", "freeform_gate_edit_mode", 70, new EditView(origin));
        Screenshots.step("bildschirmfotos: Freiform-Gate mit offener Stelle im Rand", "freeform_gate_changed", 50, new LeakView(origin));
        SelfTest.clientStep("bildschirmfotos: Freiform-Gate aufräumen", 200, FreeformGateSelfTests::cleanupShotsStep);
    }

    private static final int[][] SHOT_CAVE = {{-2, 2}, {-3, 3}, {-3, 3}, {-3, 2}, {-2, 2}, {-1, 1}};

    private static boolean shotOpening(int x, int y) {
        return y >= 0 && y < SHOT_CAVE.length && x >= SHOT_CAVE[y][0] && x <= SHOT_CAVE[y][1];
    }

    private static BlockState rock(int x, int y, int z) {
        int h = Math.floorMod(x * 73856093 ^ y * 19349663 ^ z * 83492791, 11);
        return switch (h) {
            case 0, 1 -> Blocks.ANDESITE.defaultBlockState();
            case 2 -> Blocks.COBBLESTONE.defaultBlockState();
            case 3 -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            case 4 -> Blocks.TUFF.defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }

    private static void buildShots(ServerLevel level, BlockPos s) {
        clearArea(level, s, Blocks.GRASS_BLOCK.defaultBlockState(), SHOT_AREA);
        for (int x = -9; x <= 9; x++) {
            int top = 8 - Math.abs(x) / 3 - (Math.floorMod(x * 7, 3) == 0 ? 1 : 0);
            for (int z = 0; z <= 9; z++) {
                int height = z == 0 ? top - 1 : top;
                for (int y = 0; y <= height; y++) {
                    boolean tunnel = z >= 1 && z <= 8 && x >= -4 && x <= 4 && y <= 6;
                    boolean mouth = z <= 1 && shotOpening(x, y);
                    if (tunnel && !(x == -4 || x == 4 || y == 6) || mouth) {
                        continue;
                    }
                    BlockState state = y == height ? (z == 0 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState())
                            : rock(x, y, z);
                    level.setBlockAndUpdate(s.offset(x, y, z), state);
                }
            }
        }
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        for (int z = 0; z <= 12; z++) {
            for (int y = 0; y <= 4; y++) {
                BlockState wall = (z + y) % 5 == 0 ? mossy : bricks;
                level.setBlockAndUpdate(s.offset(14, y, z), y == 4 ? bricks : wall);
                level.setBlockAndUpdate(s.offset(18, y, z), y == 4 ? bricks : wall);
            }
            for (int x = 15; x <= 17; x++) {
                level.setBlockAndUpdate(s.offset(x, 4, z), bricks);
                level.setBlockAndUpdate(s.offset(x, -1, z), Blocks.POLISHED_ANDESITE.defaultBlockState());
            }
        }
        fill(level, s.offset(21, 0, 2), s.offset(21, 3, 2), bricks);
        fill(level, s.offset(26, 0, 2), s.offset(26, 3, 2), bricks);
        fill(level, s.offset(20, 4, 2), s.offset(27, 4, 2), bricks);
        level.setBlockAndUpdate(s.offset(22, 3, 2), mossy);
        level.setBlockAndUpdate(s.offset(25, 3, 2), mossy);
    }

    private static Site placeShots(ServerPlayer player, BlockPos s) {
        ServerLevel level = player.level();
        buildShots(level, s);
        GateEntity cave = placeOne(player, s, s.offset(0, -1, 0), Direction.UP, 0.0F, 25.0F, "Höhleneingang");
        GateEntity corridor = placeOne(player, s, s.offset(16, -1, 6), Direction.UP, 0.0F, 30.0F, "Gang");
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        CourseToolItem.useOnGate(tool, player, cave);
        CourseToolItem.useOnGate(tool, player, corridor);
        return new Site(s.immutable(), corridor.getId(), cave.getId(), -1, CourseToolItem.courseOf(tool));
    }

    private static Optional<Holder<WorldClock>> clock(ServerLevel level) {
        return level.dimensionType().defaultClock();
    }

    private static void night(MinecraftServer server, boolean on) {
        ServerLevel level = server.overworld();
        Optional<Holder<WorldClock>> clock = clock(level);
        if (clock.isEmpty()) {
            return;
        }
        if (on) {
            CLOCK.compareAndSet(-1L, server.clockManager().getInstance(clock.get()).totalTicks());
            server.clockManager().moveToTimeMarker(clock.get(), ClockTimeMarkers.MIDNIGHT);
        } else {
            long ticks = CLOCK.getAndSet(-1L);
            if (ticks >= 0L) {
                server.clockManager().setTotalTicks(clock.get(), ticks);
            }
        }
    }

    private static StepResult cleanupShotsStep(SelfTest.StepContext ctx) {
        Site site = SHOT_SITE.get();
        if (site == null && CLOCK.get() < 0L) {
            return StepResult.pass();
        }
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                night(server, false);
                if (site != null) {
                    discard(player.level(), site.origin(), site.course());
                }
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                Vec3 home = SHOT_HOME.getAndSet(null);
                if (home != null) {
                    player.teleportTo(player.level(), home.x, home.y, home.z, Set.of(), player.getYRot(), player.getXRot(), true);
                }
            }));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        SHOT_SITE.set(null);
        return StepResult.pass();
    }

    private static class View implements Screenshots.Scene {
        protected final Supplier<BlockPos> origin;
        private final Vec3 feet;
        private final Vec3 target;
        private final boolean night;
        private final @Nullable Supplier<ItemStack> held;

        View(Supplier<BlockPos> origin, Vec3 feet, Vec3 target, boolean night, @Nullable Supplier<ItemStack> held) {
            this.origin = origin;
            this.feet = feet;
            this.target = target;
            this.night = night;
            this.held = held;
        }

        protected Vec3 world(Vec3 local) {
            BlockPos o = this.origin.get();
            return new Vec3(o.getX() + local.x, o.getY() + local.y, o.getZ() + local.z);
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(FUTURE)) {
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    Vec3 at = this.world(this.feet);
                    SHOT_HOME.compareAndSet(null, player.position());
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.teleportTo(player.level(), at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
                    player.setItemInHand(InteractionHand.MAIN_HAND, this.held == null ? ItemStack.EMPTY : this.held.get());
                    night(server, this.night);
                }));
                return StepResult.pending();
            }
            StepResult waiting = future(ctx);
            if (waiting != null) {
                return waiting;
            }
            if (SHOT_SITE.get() == null) {
                CompletableFuture<?> placing = (CompletableFuture<?>) ctx.state().get(PLACING);
                if (placing == null) {
                    ctx.state().put(PLACING, ctx.onServer(server -> {
                        ServerPlayer player = player(server);
                        if (SHOT_SITE.get() == null && ticking(player.level(), this.origin.get(), SHOT_AREA)) {
                            SHOT_SITE.set(placeShots(player, this.origin.get()));
                        }
                    }));
                    return StepResult.pending();
                }
                if (placing.isCompletedExceptionally()) {
                    return StepResult.fail("Server-Aktion fehlgeschlagen: " + placing.exceptionNow());
                }
                if (placing.isDone()) {
                    ctx.state().remove(PLACING);
                }
                return ctx.tick() > 300 ? StepResult.fail("Testgelände für Freiform-Gates lädt nicht") : StepResult.pending();
            }
            Site site = SHOT_SITE.get();
            if (mc.player == null || mc.level == null || site == null) {
                return ctx.tick() > 300 ? StepResult.fail("keine Szene für Freiform-Gates") : StepResult.pending();
            }
            this.hold(ctx);
            if (mc.player.position().distanceTo(this.world(this.feet)) > 0.6) {
                return ctx.tick() > 300 ? StepResult.fail("Spieler nicht am Blickpunkt") : StepResult.pending();
            }
            for (int id : List.of(site.cave(), site.corridor())) {
                if (!(mc.level.getEntity(id) instanceof GateEntity gate) || !gate.inCourse()) {
                    return ctx.tick() > 300 ? StepResult.fail("Freiform-Gate fehlt beim Client") : StepResult.pending();
                }
            }
            CourseData course = ClientCourses.get(site.course());
            if (course == null) {
                return ctx.tick() > 300 ? StepResult.fail("Kurs fehlt beim Client") : StepResult.pending();
            }
            boolean dark = mc.level.getMaxLocalRawBrightness(BlockPos.containing(this.world(this.feet)).above()) <= 6;
            if (this.night != dark && ctx.tick() < 300) {
                return StepResult.pending();
            }
            if (this.held != null && !mc.player.getMainHandItem().is(this.held.get().getItem())) {
                return ctx.tick() > 300 ? StepResult.fail("Item fehlt in der Hand") : StepResult.pending();
            }
            return this.ready(ctx);
        }

        protected StepResult ready(SelfTest.StepContext ctx) {
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            Vec3 eye = player.getEyePosition();
            Vec3 aim = this.world(this.target);
            double dx = aim.x - eye.x;
            double dy = aim.y - eye.y;
            double dz = aim.z - eye.z;
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

        @Override
        public void finish(SelfTest.StepContext ctx) {
            ctx.onServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        }
    }

    private static final class LeakView extends View {
        private static final BlockPos HOLE = new BlockPos(18, 1, 6);

        LeakView(Supplier<BlockPos> origin) {
            super(origin, new Vec3(16.5, 0.0, 1.5), new Vec3(16.6, 1.6, 6.5), false, null);
        }

        @Override
        protected StepResult ready(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Site site = SHOT_SITE.get();
            if (site == null || mc.level == null || !(mc.level.getEntity(site.corridor()) instanceof GateEntity corridor)) {
                return StepResult.fail("Gang-Gate fehlt");
            }
            if (!ctx.state().containsKey("leak")) {
                BlockPos hole = this.origin.get().offset(HOLE);
                ctx.state().put("leak", ctx.onServer(server -> player(server).level().setBlockAndUpdate(hole, Blocks.AIR.defaultBlockState())));
                return StepResult.pending();
            }
            FreeformView view = FreeformGateRenderer.view(corridor, corridor.region());
            if (view.leaks() == 0) {
                return ctx.tick() > 300 ? StepResult.fail("offene Stelle im Rand nicht erkannt") : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            BlockPos hole = this.origin.get().offset(HOLE);
            ctx.onServer(server -> player(server).level().setBlockAndUpdate(hole, Blocks.STONE_BRICKS.defaultBlockState()));
            super.finish(ctx);
        }
    }

    private static final class EditView extends View {
        EditView(Supplier<BlockPos> origin) {
            super(origin, new Vec3(16.5, 0.0, 3.0), new Vec3(16.5, 1.8, 6.5), false, () -> new ItemStack(RaceModule.FREEFORM_GATE.get()));
        }

        @Override
        protected StepResult ready(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Site site = SHOT_SITE.get();
            if (site == null || mc.level == null || !(mc.level.getEntity(site.corridor()) instanceof GateEntity corridor)) {
                return StepResult.fail("Gang-Gate fehlt");
            }
            if (mc.player == null || !GateMounting.holdsTool(mc.player)) {
                return ctx.tick() > 300 ? StepResult.fail("Freiform-Gate-Item kommt nicht an") : StepResult.pending();
            }
            GateEditor editor = GateEditor.current();
            if (editor == null || editor.gate() != corridor) {
                GateEditor.start(corridor);
                editor = GateEditor.current();
                if (editor == null) {
                    return StepResult.fail("Einstellen des Freiform-Gates startet nicht");
                }
                editor.setVariant(CourseElement.FREEFORM.opposite(corridor.variant()));
            }
            return editor.status() == GateEditor.Status.FREE ? StepResult.pass()
                    : ctx.tick() > 300 ? StepResult.fail("Vorschau beim Freiform-Gate nicht frei: " + editor.status()) : StepResult.pending();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            GateEditor.stop();
            super.finish(ctx);
        }
    }
}
