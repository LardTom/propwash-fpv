package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.core.OrientedBox;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseEdits;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.CourseOrder;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateCollisions;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GatePlacement;
import de.lardtom.propwash.race.GatePoleLinks;
import de.lardtom.propwash.race.GatePoles;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

public final class RaceTopologySelfTests {
    private static final int SITE_X = -70;
    private static final int SITE_Z = -40;
    private static final int MIN_X = -22;
    private static final int MAX_X = 44;
    private static final int MIN_Z = -18;
    private static final int MAX_Z = 20;
    private static final int HEIGHT = 14;
    private static final double EPS = 1.0E-4;
    private static final long MS = 1_000_000L;
    private static final AtomicReference<Site> SITE = new AtomicReference<>();
    private static final AtomicReference<String> DEBUG = new AtomicReference<>("");
    private static final String LOOP_A = "loop_a";
    private static final String LOOP_B = "loop_b";
    private static final String ROW_1 = "row_1";
    private static final String ROW_2 = "row_2";
    private static final String ROW_3 = "row_3";
    private static final String CUBE_S = "cube_south";
    private static final String CUBE_E = "cube_east";
    private static final String CUBE_W = "cube_west";
    private static final String CUBE_N = "cube_north";
    private static final String CUBE_LID = "cube_lid";
    private static final String CUBE_START = "cube_start";
    private static final float TURN = 30.0F;
    private static final String TURN_ROW_1 = "turn_row_1";
    private static final String TURN_ROW_2 = "turn_row_2";
    private static final String TURN_ROW_3 = "turn_row_3";
    private static final String TURN_S = "turn_cube_south";
    private static final String TURN_E = "turn_cube_east";
    private static final String TURN_W = "turn_cube_west";
    private static final String TURN_N = "turn_cube_north";
    private static final String TURN_LID = "turn_cube_lid";
    private static final String CARRIER = "carrier";
    private static final String FLAG = "flag";
    private static final String DIVE = "dive";
    private static final String DIVE_POLE = "dive_pole";
    private static final String LOOP = "course_loop";
    private static final String CUBE = "course_cube";
    private static final String STACK = "course_stack";
    private static final double[][] LOOP_PATH = {{0.5, 1.5, -3.0}, {0.5, 1.5, 16.0}, {6.5, 1.5, 16.0}, {6.5, 1.5, 4.0}, {0.5, 1.5, 4.0},
            {0.5, 1.5, -4.0}, {-5.5, 1.5, -4.0}, {-5.5, 1.5, -6.0}, {0.5, 1.5, -6.0}, {0.5, 1.5, 2.0}};
    private static final double[][] WRONG_PATH = {{0.5, 1.5, -3.0}, {0.5, 1.5, 16.0}, {6.5, 1.5, 16.0}, {6.5, 1.5, -4.0}, {0.5, 1.5, -4.0},
            {0.5, 1.5, 3.0}};
    private static final double[][] CUBE_PATH = {{32.5, 1.5, -14.0}, {32.5, 1.5, -6.0}, {32.5, 6.0, -6.0}, {32.5, 6.0, 2.25}, {32.5, 2.0, 2.25},
            {38.0, 2.0, 2.25}, {38.0, 2.0, -14.0}, {32.5, 1.5, -14.0}, {32.5, 1.5, -6.0}};
    private static final double[][] FLAG_PATH = {{-13.5, 1.5, -4.0}, {-13.5, 1.5, 3.0}, {-16.0, 1.5, 3.0}, {-16.0, 6.0, 3.0}, {-16.0, 6.0, -3.0},
            {-16.0, 6.0, 3.0}, {-11.0, 6.0, 3.0}, {-11.0, 6.0, -3.0}, {-11.0, 1.5, -4.0}, {-13.5, 1.5, -4.0}, {-13.5, 1.5, 3.0}};

    private record Site(BlockPos origin, Vec3 home, Map<BlockPos, BlockState> saved, Map<String, Integer> ids) {
        Vec3 raw(double dx, double dy, double dz) {
            return new Vec3(this.origin.getX() + dx, this.origin.getY() + dy, this.origin.getZ() + dz);
        }

        BlockPos block(int dx, int dy, int dz) {
            return this.origin.offset(dx, dy, dz);
        }
    }

    private record Pending(CompletableFuture<Void> future, AtomicReference<String> result) {
    }

    private RaceTopologySelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("race-topo: Aufbau – Schleife, Reihe, Würfel aus Gates und Flagge auf dem Gate mit echten Klicks", 600,
                RaceTopologySelfTests::setupStep);
        SelfTest.clientStep("race-topo: Schleife – ein Gate zweimal, einmal rückwärts, eindeutig beim Client und im HUD", 300,
                RaceTopologySelfTests::loopStep);
        SelfTest.clientStep("race-topo: Schleife – Runde beim Client und am Server, falsche Richtung zählt nicht", 300,
                RaceTopologySelfTests::loopLapStep);
        SelfTest.clientStep("race-topo: Reihe – Gates rasten Pfosten auf Pfosten ein und kollidieren einmal", 300,
                RaceTopologySelfTests::rowStep);
        SelfTest.clientStep("race-topo: Würfel – vier Gates mit Deckel, oben hinein und seitlich hinaus", 300,
                RaceTopologySelfTests::cubeStep);
        SelfTest.clientStep("race-topo: Gedreht – Reihe und Würfel bei 30° rasten Pfosten auf Pfosten ein, Deckel dreht mit", 300,
                RaceTopologySelfTests::turnedStep);
        SelfTest.clientStep("race-topo: Darstellung – jeden geteilten Pfosten und jeden Deckelbalken zeichnet genau ein Gate", 300,
                RaceTopologySelfTests::drawnOnceStep);
        SelfTest.clientStep("race-topo: Stapeln – Flagge auf dem Gate, Slalomstange auf dem Tauchwürfel, Runde um die Flagge", 300,
                RaceTopologySelfTests::stackStep);
        if (Screenshots.enabled()) {
            Supplier<ItemStack> tool = () -> new ItemStack(RaceModule.COURSE_TOOL.get());
            Screenshots.step("bildschirmfotos: Topologie – Schleife durch ein Gate in beide Richtungen", "race_topo_loop", 40,
                    new ThirdPerson(new Vec3(-7.0, 2.6, -6.0), new Vec3(0.5, 2.0, 6.0), tool));
            Screenshots.step("bildschirmfotos: Topologie – Reihe ohne doppelte Pfosten", "race_topo_row", 40,
                    new ThirdPerson(new Vec3(18.0, 1.8, -8.0), new Vec3(18.0, 2.0, 0.5), () -> new ItemStack(RaceModule.GATE_MEDIUM.get())));
            Screenshots.step("bildschirmfotos: Topologie – geteilter Pfosten der Reihe aus der Nähe", "race_topo_row_post", 40,
                    new ThirdPerson(new Vec3(15.2, 0.8, -1.6), new Vec3(16.25, 2.4, 0.5), tool));
            Screenshots.step("bildschirmfotos: Topologie – Würfel aus vier Gates und Deckel", "race_topo_cube", 40,
                    new ThirdPerson(new Vec3(39.0, 6.5, -6.0), new Vec3(32.5, 2.5, 2.25), tool));
            Screenshots.step("bildschirmfotos: Topologie – Ecke des Würfels unter dem Deckel aus der Nähe", "race_topo_cube_corner", 40,
                    new ThirdPerson(new Vec3(35.8, 2.6, -1.2), new Vec3(34.25, 3.6, 0.5), tool));
            Screenshots.step("bildschirmfotos: Topologie – gedrehte Reihe bei 30°", "race_topo_turned_row", 40,
                    new ThirdPerson(new Vec3(21.5, 1.2, 5.3), new Vec3(17.53, 2.0, 12.25), () -> new ItemStack(RaceModule.GATE_MEDIUM.get())));
            Screenshots.step("bildschirmfotos: Topologie – gedrehter Würfel bei 30° mit Deckel", "race_topo_turned_cube", 40,
                    new ThirdPerson(new Vec3(43.0, 4.5, 8.0), new Vec3(37.6, 2.5, 14.0), tool));
            Screenshots.step("bildschirmfotos: Topologie – Flagge auf dem Gate", "race_topo_flag_on_gate", 40,
                    new ThirdPerson(new Vec3(-20.0, 3.2, -6.0), new Vec3(-13.5, 4.5, 0.5), tool));
            Screenshots.step("bildschirmfotos: Topologie – Kurs-Fenster mit Durchgängen", "race_topo_course_window", 40, new Window());
        }
        SelfTest.clientStep("race-topo: Abbauen – die anderen bleiben stehen und bekommen ihren Pfosten zurück", 300,
                RaceTopologySelfTests::removeStep);
        SelfTest.clientStep("race-topo: Aufräumen", 300, RaceTopologySelfTests::cleanupStep);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static @Nullable StepResult once(SelfTest.StepContext ctx, String key, Function<MinecraftServer, String> action) {
        Object stored = ctx.state().get(key);
        if (stored == null) {
            AtomicReference<String> result = new AtomicReference<>();
            CompletableFuture<Void> future = ctx.onServer(server -> result.set(action.apply(server)));
            ctx.state().put(key, new Pending(future, result));
            return StepResult.pending();
        }
        Pending pending = (Pending) stored;
        if (pending.future().isCompletedExceptionally()) {
            return StepResult.fail(key + ": " + pending.future().exceptionNow());
        }
        if (!pending.future().isDone()) {
            return StepResult.pending();
        }
        String problem = pending.result().get();
        return problem == null ? null : StepResult.fail(key + ": " + problem);
    }

    private static boolean loaded(ServerLevel level, BlockPos o) {
        for (int cx = (o.getX() + MIN_X - 2) >> 4; cx <= (o.getX() + MAX_X + 2) >> 4; cx++) {
            for (int cz = (o.getZ() + MIN_Z - 2) >> 4; cz <= (o.getZ() + MAX_Z + 2) >> 4; cz++) {
                if (!level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void set(ServerLevel level, Site site, BlockPos pos, BlockState state) {
        BlockState before = level.getBlockState(pos);
        if (before.equals(state)) {
            return;
        }
        site.saved().putIfAbsent(pos.immutable(), before);
        level.setBlockAndUpdate(pos, state);
    }

    private static void clear(ServerLevel level, Site site) {
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                set(level, site, site.block(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
                for (int y = 0; y < HEIGHT; y++) {
                    BlockPos pos = site.block(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        set(level, site, pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    private static GateEntity spawn(ServerLevel level, GateGeometry geometry, GateSize size) {
        GateEntity entity = RaceModule.GATE.get().create(level, EntitySpawnReason.COMMAND);
        if (entity == null) {
            throw new IllegalStateException("Gate nicht erzeugt");
        }
        entity.setSize(size);
        entity.applyGeometry(geometry);
        if (!level.addFreshEntity(entity)) {
            throw new IllegalStateException("Gate nicht in die Welt gesetzt");
        }
        return entity;
    }

    private static @Nullable GateEntity entity(ServerLevel level, Site site, String key) {
        Integer id = site.ids().get(key);
        return id != null && level.getEntity(id) instanceof GateEntity gate && !gate.isRemoved() ? gate : null;
    }

    private static GateEntity require(ServerLevel level, Site site, String key) {
        GateEntity gate = entity(level, site, key);
        if (gate == null) {
            throw new IllegalStateException(key + " fehlt am Server");
        }
        return gate;
    }

    private static @Nullable GateEntity clientEntity(String key) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        Integer id = site == null ? null : site.ids().get(key);
        return id != null && mc.level != null && mc.level.getEntity(id) instanceof GateEntity gate && !gate.isRemoved() ? gate : null;
    }

    private static @Nullable GateEntity found(ServerLevel level, Vec3 at, CourseElement kind) {
        List<GateEntity> gates = level.getEntitiesOfClass(GateEntity.class, new AABB(at, at).inflate(0.3),
                gate -> !gate.isRemoved() && gate.kind() == kind && gate.position().distanceTo(at) < 0.3);
        return gates.size() == 1 ? gates.getFirst() : null;
    }

    private static void face(ServerPlayer player, float yaw) {
        player.setYRot(yaw);
        player.setXRot(0.0F);
        player.yRotO = yaw;
        player.xRotO = 0.0F;
        player.setYHeadRot(yaw);
    }

    private static InteractionResult useOnFloor(ServerPlayer player, ItemStack stack, BlockPos floor, float yaw) {
        ItemStack previous = player.getMainHandItem().copy();
        try {
            face(player, yaw);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockHitResult hit = new BlockHitResult(new Vec3(floor.getX() + 0.5, floor.getY() + 1.0, floor.getZ() + 0.5), Direction.UP, floor, false);
            return stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
    }

    private static InteractionResult interact(ServerPlayer player, GateEntity target, ItemStack stack, Vec3 point, float yaw) {
        ItemStack previous = player.getMainHandItem().copy();
        try {
            face(player, yaw);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            return target.interact(player, InteractionHand.MAIN_HAND, point.subtract(target.position()));
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
    }

    private static GateGeometry standing(Site site, double x, double z, float yaw) {
        Vec3 at = site.raw(x, 0.0, z);
        return GateGeometry.of(at.x, at.y, at.z, yaw, GateSize.MEDIUM);
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        StepResult wait = once(ctx, "Platz", server -> {
            ServerPlayer player = player(server);
            ServerLevel level = player.level();
            int x = player.getBlockX() + SITE_X;
            int z = player.getBlockZ() + SITE_Z;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            Site site = new Site(new BlockPos(x, y, z), player.position(), new LinkedHashMap<>(), new ConcurrentHashMap<>());
            SITE.set(site);
            Vec3 stand = site.raw(10.0, 0.0, -12.0);
            player.teleportTo(level, stand.x, stand.y, stand.z, Set.of(), 0.0F, 0.0F, true);
            return null;
        });
        if (wait != null) {
            return wait;
        }
        Site site = SITE.get();
        if (!ctx.state().containsKey("geladen")) {
            CompletableFuture<Void> probe = ctx.onServer(server -> {
                if (loaded(player(server).level(), site.origin())) {
                    ctx.state().put("geladen", true);
                }
            });
            ctx.state().put("probe " + (ctx.tick() / 10), probe);
            return ctx.tick() > 400 ? StepResult.fail("Platz lädt nicht") : StepResult.pending();
        }
        wait = once(ctx, "Aufbau", RaceTopologySelfTests::build);
        if (wait != null) {
            return wait;
        }
        for (String key : List.of(LOOP_A, LOOP_B, ROW_1, ROW_2, ROW_3, CUBE_S, CUBE_E, CUBE_W, CUBE_N, CUBE_LID, CUBE_START, CARRIER, FLAG, DIVE,
                DIVE_POLE, TURN_ROW_1, TURN_ROW_2, TURN_ROW_3, TURN_S, TURN_E, TURN_W, TURN_N, TURN_LID)) {
            if (clientEntity(key) == null) {
                return ctx.tick() > 500 ? StepResult.fail(key + " fehlt beim Client") : StepResult.pending();
            }
        }
        for (String key : List.of(LOOP, CUBE, STACK)) {
            if (ClientCourses.get(site.ids().getOrDefault(key, GateEntity.NO_COURSE)) == null) {
                return ctx.tick() > 500 ? StepResult.fail(key + " fehlt beim Client") : StepResult.pending();
            }
        }
        ctx.log(DEBUG.get());
        return StepResult.pass();
    }

    private static @Nullable String build(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Site site = SITE.get();
        clear(level, site);
        StringBuilder log = new StringBuilder();
        GateEntity a = spawn(level, standing(site, 0.5, 0.5, 0.0F), GateSize.MEDIUM);
        GateEntity b = spawn(level, standing(site, 0.5, 12.5, 0.0F), GateSize.MEDIUM);
        site.ids().put(LOOP_A, a.getId());
        site.ids().put(LOOP_B, b.getId());
        ItemStack loopTool = new ItemStack(RaceModule.COURSE_TOOL.get());
        face(player, 0.0F);
        CourseToolItem.useOnGate(loopTool, player, a);
        CourseToolItem.useOnGate(loopTool, player, b);
        face(player, 180.0F);
        CourseToolItem.useOnGate(loopTool, player, a);
        int loop = CourseToolItem.courseOf(loopTool);
        site.ids().put(LOOP, loop);
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course loopCourse = registry == null ? null : registry.course(loop);
        if (loopCourse == null || loopCourse.gates.size() != 3 || !loopCourse.gates.get(2).reversed() || !loopCourse.gates.get(2).uuid.equals(a.getUUID().toString())) {
            return "Schleife nicht angelegt: " + (loopCourse == null ? "kein Kurs" : loopCourse.gates.size() + " Durchgänge");
        }
        if (!a.passes().equals(List.of(new CourseOrder.Pass(0, false), new CourseOrder.Pass(2, true)))) {
            return "Gate A trägt " + a.passes();
        }
        log.append("Schleife: Kurs ").append(loop).append(" A, B, A rückwärts (Klick von hinten); ");
        GateEntity row1 = spawn(level, standing(site, 14.5, 0.5, 0.0F), GateSize.MEDIUM);
        site.ids().put(ROW_1, row1.getId());
        ItemStack gateItem = new ItemStack(RaceModule.GATE_MEDIUM.get());
        if (!useOnFloor(player, gateItem.copy(), site.block(18, -1, 0), 0.0F).consumesAction()) {
            return "zweites Gate der Reihe nicht gesetzt";
        }
        GateEntity row2 = found(level, site.raw(18.0, 0.0, 0.5), CourseElement.GATE);
        if (row2 == null) {
            return "zweites Gate der Reihe nicht eingerastet (erwartet x " + (site.origin().getX() + 18.0) + ")";
        }
        site.ids().put(ROW_2, row2.getId());
        if (!useOnFloor(player, gateItem.copy(), site.block(21, -1, 0), 0.0F).consumesAction()) {
            return "drittes Gate der Reihe nicht gesetzt";
        }
        GateEntity row3 = found(level, site.raw(21.5, 0.0, 0.5), CourseElement.GATE);
        if (row3 == null) {
            return "drittes Gate der Reihe fehlt";
        }
        site.ids().put(ROW_3, row3.getId());
        log.append("Reihe: Klick auf Block 18 rastet bei x+18,0 ein, Block 21 bei x+21,5; ");
        GateEntity south = spawn(level, standing(site, 32.5, 0.5, 0.0F), GateSize.MEDIUM);
        site.ids().put(CUBE_S, south.getId());
        String placed = side(player, level, site, gateItem, CUBE_E, site.block(34, -1, 2), 90.0F, site.raw(34.25, 0.0, 2.25));
        placed = placed != null ? placed : side(player, level, site, gateItem, CUBE_W, site.block(30, -1, 2), 90.0F, site.raw(30.75, 0.0, 2.25));
        placed = placed != null ? placed : side(player, level, site, gateItem, CUBE_N, site.block(32, -1, 4), 0.0F, site.raw(32.5, 0.0, 4.0));
        if (placed != null) {
            return placed;
        }
        GatePlacement.Preference before = GateMounting.preference(player, false);
        GateMounting.setPreference(player, before.with(GateMount.FLOOR_FLAT, 0));
        InteractionResult lidResult;
        try {
            lidResult = interact(player, south, gateItem.copy(), site.raw(32.5, 4.0, 0.5), 0.0F);
        } finally {
            GateMounting.setPreference(player, before);
        }
        if (!lidResult.consumesAction()) {
            return "Deckel nicht gesetzt: " + lidResult;
        }
        List<GateEntity> lids = level.getEntitiesOfClass(GateEntity.class, new AABB(site.raw(30.0, 3.0, -0.5), site.raw(35.0, 4.5, 5.0)),
                gate -> !gate.isRemoved() && gate.mount() == GateMount.FLOOR_FLAT);
        if (lids.size() != 1) {
            return lids.size() + " Deckel statt einem";
        }
        GateEntity lid = lids.getFirst();
        site.ids().put(CUBE_LID, lid.getId());
        GateEntity start = spawn(level, standing(site, 32.5, -10.0, 0.0F), GateSize.MEDIUM);
        site.ids().put(CUBE_START, start.getId());
        ItemStack cubeTool = new ItemStack(RaceModule.COURSE_TOOL.get());
        GateEntity east = require(level, site, CUBE_E);
        face(player, 0.0F);
        CourseToolItem.useOnGate(cubeTool, player, start);
        CourseToolItem.useOnGate(cubeTool, player, lid);
        CourseToolItem.useOnGate(cubeTool, player, east);
        int cube = CourseToolItem.courseOf(cubeTool);
        site.ids().put(CUBE, cube);
        CourseEdits.Result reversed = CourseEdits.apply(player, new RacePayloads.CourseEditC2S(cube, CourseEdits.Action.REVERSE, 2, 0));
        if (reversed != CourseEdits.Result.DONE || !east.passReversed(2)) {
            return "Ausgang nicht umgedreht: " + reversed + ", " + east.passes();
        }
        log.append("Würfel: drei Seiten per Klick eingerastet, Deckel liegend auf dem Südgate, Kurs ").append(cube)
                .append(" Start, Deckel abwärts, Ostseite als Ausgang (⇄ im Kurs-Fenster); ");
        String turned = buildTurned(player, level, site, gateItem, log);
        if (turned != null) {
            return turned;
        }
        GateEntity carrier = spawn(level, standing(site, -13.5, 0.5, 0.0F), GateSize.MEDIUM);
        site.ids().put(CARRIER, carrier.getId());
        InteractionResult flagResult = interact(player, carrier, new ItemStack(RaceModule.RACE_FLAG.get()), site.raw(-13.4, 4.0, 0.5), 0.0F);
        GateEntity flag = found(level, site.raw(-13.5, 4.0, 0.5), CourseElement.FLAG);
        if (!flagResult.consumesAction() || flag == null) {
            return "Flagge nicht auf das Gate gesetzt: " + flagResult;
        }
        site.ids().put(FLAG, flag.getId());
        Vec3 diveAt = site.raw(-13.5, 0.0, 12.5);
        GateEntity dive = spawn(level, GateGeometry.element(diveAt.x, diveAt.y, diveAt.z, 0.0F, CourseElement.CUBE, CourseElement.EXIT_FRONT, 4.5, 4.5,
                GateTilt.NONE), GateSize.MEDIUM);
        site.ids().put(DIVE, dive.getId());
        GatePoles.Seat corner = GatePoles.seats(dive.geometry()).getFirst();
        InteractionResult poleResult = interact(player, dive, new ItemStack(RaceModule.SLALOM_POLE.get()), new Vec3(corner.x(), corner.y(), corner.z()),
                0.0F);
        GateEntity divePole = found(level, new Vec3(corner.x(), corner.y(), corner.z()), CourseElement.POLE);
        if (!poleResult.consumesAction() || divePole == null) {
            return "Slalomstange nicht auf den Tauchwürfel gesetzt: " + poleResult;
        }
        site.ids().put(DIVE_POLE, divePole.getId());
        ItemStack stackTool = new ItemStack(RaceModule.COURSE_TOOL.get());
        CourseToolItem.useOnGate(stackTool, player, carrier);
        CourseToolItem.useOnGate(stackTool, player, flag);
        site.ids().put(STACK, CourseToolItem.courseOf(stackTool));
        log.append("Flagge per Klick auf die Mitte des Gates, Slalomstange auf eine Ecke des Tauchwürfels, Kurs ")
                .append(CourseToolItem.courseOf(stackTool)).append(" Gate und Flagge");
        DEBUG.set(log.toString());
        return null;
    }

    private static GateGeometry turned(Site site, double x, double z, float heading) {
        Vec3 at = site.raw(x, 0.0, z);
        return GatePlacement.turnedTo(GateGeometry.of(at.x, at.y, at.z, 0.0F, GateSize.MEDIUM), heading);
    }

    private static Vec3 beside(GateGeometry gate, double side, double depth) {
        return new Vec3(gate.x() + gate.sideX() * side + gate.normalX() * depth, gate.y(), gate.z() + gate.sideZ() * side + gate.normalZ() * depth);
    }

    private static @Nullable String buildTurned(ServerPlayer player, ServerLevel level, Site site, ItemStack gateItem, StringBuilder log) {
        GatePlacement.Preference before = GateMounting.preference(player, false);
        GateEntity south;
        try {
            GateMounting.setPreference(player, before.with(GateMount.STANDING, 0, TURN));
            GateEntity first = spawn(level, turned(site, 14.5, 10.5, TURN), GateSize.MEDIUM);
            site.ids().put(TURN_ROW_1, first.getId());
            GateGeometry row = first.geometry();
            double step = 2.0 * row.postOffset();
            String placed = side(player, level, site, gateItem, TURN_ROW_2, site.block(17, -1, 12), 0.0F, beside(row, step, 0.0));
            placed = placed != null ? placed : side(player, level, site, gateItem, TURN_ROW_3, site.block(20, -1, 13), 0.0F, beside(row, 2.0 * step, 0.0));
            if (placed != null) {
                return "gedreht: " + placed;
            }
            south = spawn(level, turned(site, 38.5, 12.5, TURN), GateSize.MEDIUM);
            site.ids().put(TURN_S, south.getId());
            GateGeometry s = south.geometry();
            double p = s.postOffset();
            placed = side(player, level, site, gateItem, TURN_E, site.block(39, -1, 14), 90.0F, beside(s, p, p));
            placed = placed != null ? placed : side(player, level, site, gateItem, TURN_W, site.block(36, -1, 13), 90.0F, beside(s, -p, p));
            placed = placed != null ? placed : side(player, level, site, gateItem, TURN_N, site.block(36, -1, 15), 0.0F, beside(s, 0.0, 2.0 * p));
            if (placed != null) {
                return "gedreht: " + placed;
            }
            GateMounting.setPreference(player, before.with(GateMount.FLOOR_FLAT, 0));
            InteractionResult lidResult = interact(player, south, gateItem.copy(), south.position().add(0.0, 4.0, 0.0), 0.0F);
            if (!lidResult.consumesAction()) {
                return "Deckel auf dem gedrehten Würfel nicht gesetzt: " + lidResult;
            }
        } finally {
            GateMounting.setPreference(player, before);
        }
        List<GateEntity> lids = level.getEntitiesOfClass(GateEntity.class, new AABB(site.raw(34.0, 3.0, 10.0), site.raw(42.0, 4.5, 18.0)),
                gate -> !gate.isRemoved() && gate.mount() == GateMount.FLOOR_FLAT);
        if (lids.size() != 1) {
            return lids.size() + " Deckel auf dem gedrehten Würfel statt einem";
        }
        site.ids().put(TURN_LID, lids.getFirst().getId());
        log.append("gedreht um ").append(TURN).append("°: Reihe aus drei Gates und Würfel aus vier Gates per Klick eingerastet, Deckel ")
                .append(lids.getFirst().geometry().heading()).append("°; ");
        return null;
    }

    private static @Nullable String side(ServerPlayer player, ServerLevel level, Site site, ItemStack item, String key, BlockPos floor, float yaw,
                                         Vec3 expected) {
        InteractionResult result = useOnFloor(player, item.copy(), floor, yaw);
        GateEntity gate = found(level, expected, CourseElement.GATE);
        if (!result.consumesAction() || gate == null) {
            List<GateEntity> near = level.getEntitiesOfClass(GateEntity.class, new AABB(expected, expected).inflate(2.0), g -> !g.isRemoved());
            return key + " nicht eingerastet: " + result + ", in der Nähe " + near.stream().map(g -> g.position().subtract(site.raw(0, 0, 0))).toList();
        }
        site.ids().put(key, gate.getId());
        return null;
    }

    private static StepResult loopStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Aufbau");
        }
        GateEntity a = clientEntity(LOOP_A);
        GateEntity b = clientEntity(LOOP_B);
        CourseData data = ClientCourses.get(site.ids().getOrDefault(LOOP, GateEntity.NO_COURSE));
        if (a == null || b == null || data == null) {
            return StepResult.fail("Schleife fehlt beim Client");
        }
        if (!"1 · 3←".equals(a.passText()) || !"2".equals(b.passText())) {
            return ctx.tick() > 200 ? StepResult.fail("Nummern beim Client: A " + a.passText() + ", B " + b.passText()) : StepResult.pending();
        }
        if (data.gates().size() != 3 || !data.gates().get(2).reverse() || data.firstPass(2) != 0 || data.gates().get(0).reverse()) {
            return StepResult.fail("Kursdaten beim Client: " + data.gates());
        }
        if (!a.flownForward() || !a.flownReversed() || !b.flownForward() || b.flownReversed()) {
            return StepResult.fail("Richtungen für die Bodenpfeile falsch");
        }
        if (!a.hasPass(2) || !a.passReversed(2) || a.hasPass(1) || a.gateIndex() != 0) {
            return StepResult.fail("Durchgänge von A: " + a.passes());
        }
        ctx.log("Gate A zeigt „" + a.passText() + "“, B „" + b.passText() + "“; Kurs-Fenster: Zeile 3 ist „noch einmal wie 1“ und rückwärts");
        return StepResult.pass();
    }

    private static List<Vec3> path(Site site, double[][] points) {
        List<Vec3> out = new ArrayList<>(points.length);
        for (double[] point : points) {
            out.add(site.raw(point[0], point[1], point[2]));
        }
        return out;
    }

    private static List<LapTracker.Event> fly(List<GateGeometry> gates, int course, List<Vec3> path) {
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(0);
        tracker.setCourse(course, gates, 0L);
        List<LapTracker.Event> events = new ArrayList<>();
        long now = 1_000L * MS;
        Vec3 at = path.getFirst();
        tracker.update(now, at.x, at.y, at.z);
        for (int i = 1; i < path.size(); i++) {
            Vec3 to = path.get(i);
            int steps = Math.max(1, (int) Math.ceil(at.distanceTo(to) / 0.3));
            for (int s = 1; s <= steps; s++) {
                now += 20 * MS;
                Vec3 p = at.lerp(to, s / (double) steps);
                tracker.update(now, p.x, p.y, p.z);
                events.addAll(tracker.drainEvents());
            }
            at = to;
        }
        return events;
    }

    private static List<Integer> of(List<LapTracker.Event> events, LapTracker.EventType type) {
        return events.stream().filter(e -> e.type() == type).map(LapTracker.Event::gate).toList();
    }

    private static @Nullable String clientLap(Site site, String key, double[][] points, List<Integer> expected) {
        int course = site.ids().getOrDefault(key, GateEntity.NO_COURSE);
        CourseData data = ClientCourses.get(course);
        if (data == null) {
            return "Kurs " + key + " fehlt beim Client";
        }
        List<LapTracker.Event> events = fly(ClientCourses.geometry(data), course, path(site, points));
        if (!of(events, LapTracker.EventType.GATE).equals(expected) || of(events, LapTracker.EventType.LAP).size() != 1
                || !of(events, LapTracker.EventType.MISSED).isEmpty() || !of(events, LapTracker.EventType.WRONG_WAY).isEmpty()) {
            return "Client zählt " + events;
        }
        return null;
    }

    private static double[] startCrossings(List<Vec3> path, GateGeometry start) {
        double travelled = 0.0;
        double first = -1.0;
        double last = -1.0;
        for (int i = 1; i < path.size(); i++) {
            Vec3 a = path.get(i - 1);
            Vec3 b = path.get(i);
            double length = a.distanceTo(b);
            double t = start.stageCrossing(0, a.x, a.y, a.z, b.x, b.y, b.z);
            if (start.gate()) {
                t = start.crossing(a.x, a.y, a.z, b.x, b.y, b.z, true);
            }
            if (t >= 0.0) {
                double at = travelled + length * t;
                if (first < 0.0) {
                    first = at;
                }
                last = at;
            }
            travelled += length;
        }
        return new double[]{first, last};
    }

    private static @Nullable String serverLap(MinecraftServer server, Site site, String key, double[][] points) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        CourseRegistry registry = CourseRegistry.get();
        int courseId = site.ids().getOrDefault(key, GateEntity.NO_COURSE);
        CourseRegistry.Course course = registry == null ? null : registry.course(courseId);
        if (course == null) {
            return "Kurs fehlt am Server";
        }
        registry.setLaps(course, 1);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        List<Vec3> path = path(site, points);
        Vec3 first = path.getFirst();
        drone.snapTo(first.x, first.y, first.z, 0.0F, 0.0F);
        drone.setPilot(player);
        if (!level.addFreshEntity(drone)) {
            return "Drohne nicht gespawnt";
        }
        try {
            long lap = Math.max(RaceServer.minimumLapNanos(course, player) * 3L, 8_000L * MS);
            double[] crossings = startCrossings(path, course.toData().geometry().getFirst());
            if (crossings[0] < 0.0 || crossings[1] <= crossings[0]) {
                return "Weg kreuzt das Start-Gate nicht zweimal";
            }
            double perBlock = lap / (crossings[1] - crossings[0]);
            long t0 = System.nanoTime() + 60_000L * MS;
            RaceServer.Verdict verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.START, courseId, 0, 0L, 0L),
                    player, t0 - 100L * MS);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Start abgelehnt: " + verdict;
            }
            double travelled = 0.0;
            long t = t0;
            for (int i = 1; i < path.size(); i++) {
                Vec3 a = path.get(i - 1);
                Vec3 b = path.get(i);
                double length = a.distanceTo(b);
                int steps = Math.max(1, (int) Math.ceil(length / 0.5));
                for (int s = 1; s <= steps; s++) {
                    Vec3 from = a.lerp(b, (s - 1) / (double) steps);
                    Vec3 next = a.lerp(b, s / (double) steps);
                    t = t0 + Math.round((travelled + length * s / steps - crossings[0]) * perBlock);
                    RaceServer.onDroneMoved(player, from, next, t);
                }
                travelled += length;
            }
            t += 100L * MS;
            verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, courseId, 1, lap, 0L), player, t);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Runde am Server abgelehnt: " + verdict;
            }
            List<Leaderboard.Entry> entries = new ArrayList<>(registry.board(course).entries());
            entries.addAll(registry.board(course).assistedEntries());
            if (entries.stream().noneMatch(entry -> entry.player().equals(player.getUUID()))) {
                return "Runde fehlt in der Bestenliste";
            }
            return null;
        } finally {
            drone.setPilot(null);
            drone.discard();
        }
    }

    private static StepResult loopLapStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Aufbau");
        }
        String problem = clientLap(site, LOOP, LOOP_PATH, List.of(1, 2));
        if (problem != null) {
            return StepResult.fail("Schleife: " + problem);
        }
        int course = site.ids().getOrDefault(LOOP, GateEntity.NO_COURSE);
        CourseData data = ClientCourses.get(course);
        List<LapTracker.Event> wrong = fly(ClientCourses.geometry(data), course, path(site, WRONG_PATH));
        if (!of(wrong, LapTracker.EventType.GATE).equals(List.of(1)) || of(wrong, LapTracker.EventType.WRONG_WAY).size() != 1
                || !of(wrong, LapTracker.EventType.MISSED).isEmpty() || !of(wrong, LapTracker.EventType.LAP).isEmpty()) {
            return StepResult.fail("Falsche Richtung durch A: " + wrong);
        }
        StepResult wait = once(ctx, "Server-Runde", server -> serverLap(server, site, LOOP, LOOP_PATH));
        if (wait != null) {
            return wait;
        }
        ctx.log("Client: Start, Gate 2, Gate 3 (A rückwärts), Runde; A vorwärts statt rückwärts: eine Meldung falsche Richtung, kein Auslassen; "
                + "Server nimmt die Runde an und trägt sie in die Bestenliste ein");
        return StepResult.pass();
    }

    private static List<AABB> collidersNear(ServerLevel level, Vec3 point) {
        List<AABB> out = new ArrayList<>();
        GateCollisions.collect(level, new AABB(point, point).inflate(0.05), out);
        return out;
    }

    private static StepResult rowStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Aufbau");
        }
        StepResult wait = once(ctx, "Reihe am Server", server -> {
            ServerLevel level = player(server).level();
            GateEntity r1 = require(level, site, ROW_1);
            GateEntity r2 = require(level, site, ROW_2);
            GateEntity r3 = require(level, site, ROW_3);
            for (GateEntity gate : List.of(r1, r2, r3)) {
                gate.refreshStack();
            }
            if (r1.joints() != 0 || r2.joints() != GateGeometry.JOINT_LEFT || r3.joints() != GateGeometry.JOINT_LEFT) {
                return "Pfosten: " + r1.joints() + "/" + r2.joints() + "/" + r3.joints();
            }
            if (!r1.linked() || !r2.linked() || !r3.linked()) {
                return "Reihe nicht verbunden";
            }
            if (r1.colliders().length != 4 || r2.colliders().length != 3 || r3.colliders().length != 3) {
                return "Kollisionsboxen " + r1.colliders().length + "/" + r2.colliders().length + "/" + r3.colliders().length;
            }
            Vec3 shared = site.raw(16.25, 2.0, 0.5);
            if (collidersNear(level, shared).size() != 1) {
                return collidersNear(level, shared).size() + " Boxen im geteilten Pfosten";
            }
            GateGeometry middle = r2.geometry();
            Vec3 a = site.raw(16.8, 1.5, 2.0);
            Vec3 b = site.raw(16.8, 1.5, -1.0);
            if (middle.crossing(b.x, b.y, b.z, a.x, a.y, a.z, true) < 0.0) {
                return "Öffnung reicht nicht bis an den geteilten Pfosten";
            }
            ServerPlayer player = player(server);
            Vec3 near = site.raw(18.0, 0.0, -3.0);
            player.teleportTo(level, near.x, near.y, near.z, Set.of(), 0.0F, 0.0F, true);
            GateMounting.Result mount = GateMounting.rotate(player, r2, true);
            if (mount != GateMounting.Result.STACK_LOCKED) {
                return "Lage eines Gates in der Reihe lässt sich ändern: " + mount;
            }
            Vec3 before = r2.position();
            for (int turn = 0; turn < 2; turn++) {
                GateMounting.Result turned = GateMounting.rotate(player, r2, false);
                r2.refreshStack();
                r3.refreshStack();
                if (turned != GateMounting.Result.ROTATED || r2.position().distanceTo(before) > EPS || Integer.bitCount(r2.joints()) != 1
                        || r2.colliders().length != 3) {
                    return "Drehen in der Reihe: " + turned + ", Lage " + r2.position().subtract(before) + ", Pfosten " + r2.joints();
                }
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        GateEntity r2 = clientEntity(ROW_2);
        GateEntity r3 = clientEntity(ROW_3);
        if (r2 == null || r3 == null || r2.joints() != GateGeometry.JOINT_LEFT || r3.joints() != GateGeometry.JOINT_LEFT) {
            return ctx.tick() > 200 ? StepResult.fail("Client: Pfosten " + (r2 == null ? "-" : r2.joints()) + "/" + (r3 == null ? "-" : r3.joints()))
                    : StepResult.pending();
        }
        ctx.log("Drei Gates in Reihe: das mittlere und das rechte zeichnen und kollidieren ohne linken Pfosten (3 statt 4 Boxen), im geteilten "
                + "Pfosten liegt eine Box, Durchflug direkt neben dem geteilten Pfosten zählt; Lage wechseln gesperrt");
        return StepResult.pass();
    }

    private static StepResult cubeStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Aufbau");
        }
        StepResult wait = once(ctx, "Würfel am Server", server -> {
            ServerLevel level = player(server).level();
            List<GateEntity> sides = new ArrayList<>();
            for (String key : List.of(CUBE_S, CUBE_E, CUBE_W, CUBE_N)) {
                GateEntity gate = require(level, site, key);
                gate.refreshStack();
                sides.add(gate);
            }
            GateEntity lid = require(level, site, CUBE_LID);
            lid.refreshStack();
            int posts = 0;
            for (GateEntity side : sides) {
                if ((side.joints() & GateGeometry.JOINT_TOP) == 0 || !side.linked()) {
                    return "Seite ohne Deckel: " + side.joints();
                }
                posts += Integer.bitCount(side.joints() & (GateGeometry.JOINT_LEFT | GateGeometry.JOINT_RIGHT));
            }
            if (posts != 4 || !lid.linked() || lid.geometry().normalY() >= 0.0) {
                return "Ecken " + posts + ", Deckel verbunden " + lid.linked() + ", Normale " + lid.geometry().normalY();
            }
            for (double[] corner : new double[][]{{30.75, 0.5}, {34.25, 0.5}, {30.75, 4.0}, {34.25, 4.0}}) {
                Vec3 post = site.raw(corner[0], 2.0, corner[1]);
                if (collidersNear(level, post).size() != 1) {
                    return collidersNear(level, post).size() + " Boxen im Eckpfosten " + corner[0] + "/" + corner[1];
                }
                Vec3 top = site.raw(corner[0], 3.9, corner[1]);
                if (collidersNear(level, top).size() != 1) {
                    return collidersNear(level, top).size() + " Boxen in der Ecke unter dem Deckel";
                }
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        String problem = clientLap(site, CUBE, CUBE_PATH, List.of(1, 2));
        if (problem != null) {
            return StepResult.fail("Würfel: " + problem);
        }
        wait = once(ctx, "Server-Runde", server -> serverLap(server, site, CUBE, CUBE_PATH));
        if (wait != null) {
            return wait;
        }
        GateEntity east = clientEntity(CUBE_E);
        if (east == null || !"3←".equals(east.passText())) {
            return StepResult.fail("Ausgang beim Client: " + (east == null ? "-" : east.passText()));
        }
        ctx.log("Vier Gates teilen sich die Eckpfosten (je eine Box), der Deckel liegt auf den oberen Balken (je eine Box), "
                + "Runde: Start, Deckel abwärts, Ostseite rückwärts als Ausgang – beim Client und am Server");
        return StepResult.pass();
    }

    private static Vec3 post(GateGeometry gate, int side, double up) {
        return new Vec3(gate.worldX(side * gate.postOffset(), up, 0.0), gate.worldY(side * gate.postOffset(), up, 0.0),
                gate.worldZ(side * gate.postOffset(), up, 0.0));
    }

    private static int gatesAt(ServerLevel level, Vec3 point) {
        int count = 0;
        for (GateEntity gate : level.getEntitiesOfClass(GateEntity.class, new AABB(point, point).inflate(4.0), gate -> !gate.isRemoved())) {
            boolean hit = false;
            OrientedBox[] oriented = gate.orientedColliders();
            if (oriented.length > 0) {
                for (OrientedBox box : oriented) {
                    hit |= box.contains(point.x, point.y, point.z);
                }
            } else {
                for (AABB box : gate.colliders()) {
                    hit |= box.contains(point);
                }
            }
            count += hit ? 1 : 0;
        }
        return count;
    }

    private static StepResult turnedStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Aufbau");
        }
        StepResult wait = once(ctx, "Gedreht am Server", server -> {
            ServerLevel level = player(server).level();
            List<GateEntity> row = new ArrayList<>();
            for (String key : List.of(TURN_ROW_1, TURN_ROW_2, TURN_ROW_3)) {
                GateEntity gate = require(level, site, key);
                gate.refreshStack();
                row.add(gate);
            }
            for (GateEntity gate : row) {
                if (Math.abs(gate.geometry().heading() - TURN) > 0.01F || !gate.linked()) {
                    return "Reihe: Richtung " + gate.geometry().heading() + ", verbunden " + gate.linked();
                }
            }
            if (row.get(0).joints() != 0 || row.get(1).joints() != GateGeometry.JOINT_LEFT || row.get(2).joints() != GateGeometry.JOINT_LEFT) {
                return "Pfosten der Reihe: " + row.get(0).joints() + "/" + row.get(1).joints() + "/" + row.get(2).joints();
            }
            for (int i = 0; i < 2; i++) {
                Vec3 shared = post(row.get(i).geometry(), 1, 2.0);
                if (gatesAt(level, shared) != 1) {
                    return gatesAt(level, shared) + " Gates im geteilten Pfosten " + i;
                }
            }
            GateGeometry middle = row.get(1).geometry();
            double inner = middle.innerHalfWidth() - 0.05;
            Vec3 a = new Vec3(middle.worldX(-inner, 1.5, -1.0), middle.worldY(-inner, 1.5, -1.0), middle.worldZ(-inner, 1.5, -1.0));
            Vec3 b = new Vec3(middle.worldX(-inner, 1.5, 1.0), middle.worldY(-inner, 1.5, 1.0), middle.worldZ(-inner, 1.5, 1.0));
            if (middle.crossing(a.x, a.y, a.z, b.x, b.y, b.z, true) < 0.0) {
                return "Öffnung reicht nicht bis an den geteilten Pfosten";
            }
            List<GateEntity> sides = new ArrayList<>();
            for (String key : List.of(TURN_S, TURN_E, TURN_W, TURN_N)) {
                GateEntity gate = require(level, site, key);
                gate.refreshStack();
                sides.add(gate);
            }
            GateEntity lid = require(level, site, TURN_LID);
            lid.refreshStack();
            int posts = 0;
            for (GateEntity side : sides) {
                if ((side.joints() & GateGeometry.JOINT_TOP) == 0 || !side.linked()) {
                    return "gedrehte Seite ohne Deckel: " + side.joints();
                }
                posts += Integer.bitCount(side.joints() & (GateGeometry.JOINT_LEFT | GateGeometry.JOINT_RIGHT));
                for (int end = -1; end <= 1; end += 2) {
                    Vec3 corner = post(side.geometry(), end, 2.0);
                    if (gatesAt(level, corner) != 1) {
                        return gatesAt(level, corner) + " Gates im Eckpfosten des gedrehten Würfels";
                    }
                }
            }
            GateGeometry top = lid.geometry();
            float turn = Math.abs(top.heading() - TURN) % 90.0F;
            if (posts != 4 || !lid.linked() || top.normalY() >= 0.0 || (turn > 0.01F && turn < 89.99F)) {
                return "Ecken " + posts + ", Deckel verbunden " + lid.linked() + ", Normale " + top.normalY() + ", Richtung " + top.heading();
            }
            if (top.crossing(top.centerX(), top.centerY() + 1.0, top.centerZ(), top.centerX(), top.centerY() - 1.0, top.centerZ(), true) < 0.0) {
                return "Deckel des gedrehten Würfels nicht von oben durchfliegbar";
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        GateEntity r2 = clientEntity(TURN_ROW_2);
        GateEntity r3 = clientEntity(TURN_ROW_3);
        GateEntity side = clientEntity(TURN_E);
        if (r2 == null || r3 == null || side == null || r2.joints() != GateGeometry.JOINT_LEFT || r3.joints() != GateGeometry.JOINT_LEFT
                || (side.joints() & GateGeometry.JOINT_TOP) == 0) {
            return ctx.tick() > 200 ? StepResult.fail("Client: gedrehte Pfosten " + (r2 == null ? "-" : r2.joints()) + "/" + (r3 == null ? "-" : r3.joints())
                    + ", Würfelseite " + (side == null ? "-" : side.joints())) : StepResult.pending();
        }
        ctx.log("Bei 30° gedreht: das zweite und dritte Gate der Reihe rasten per Klick Pfosten auf Pfosten ein (ohne linken Pfosten, je ein Gate "
                + "im geteilten Pfosten, Öffnung bis an den Pfosten); drei Seiten des Würfels rasten über Eck ein, der Deckel liegt mit derselben "
                + "Drehung auf und wird von oben durchflogen");
        return StepResult.pass();
    }

    private static String postKey(GateGeometry gate, int side) {
        Vec3 at = post(gate, side, 0.0);
        return Math.round(at.x * 1000.0) + "/" + Math.round(at.z * 1000.0);
    }

    private static @Nullable String drawnOnce(GateRenderer renderer, List<String> keys, int posts) {
        Map<String, Set<String>> drawn = new HashMap<>();
        Map<String, Set<String>> lit = new HashMap<>();
        for (String key : keys) {
            GateEntity gate = clientEntity(key);
            if (gate == null) {
                return key + " fehlt beim Client";
            }
            GateRenderState state = renderer.createRenderState();
            renderer.extractRenderState(gate, state, 0.0F);
            GateGeometry geometry = gate.geometry();
            if (state.joints != gate.joints()) {
                return key + ": gezeichnet mit Fugen " + state.joints + " statt " + gate.joints();
            }
            if (!state.modelled) {
                return key + " ohne Modell";
            }
            if (!state.tiled) {
                if (gate.joints() != 0) {
                    return key + " zeichnet trotz geteilter Teile das ganze Modell";
                }
                for (int side = -1; side <= 1; side += 2) {
                    drawn.computeIfAbsent(postKey(geometry, side), k -> new HashSet<>()).add(key);
                    lit.computeIfAbsent(postKey(geometry, side), k -> new HashSet<>()).add(key);
                }
                continue;
            }
            float half = state.width * 0.5F;
            float topLine = state.height - state.size.frame();
            boolean covered = (state.joints & GateGeometry.JOINT_TOP) != 0;
            List<String> wrong = new ArrayList<>();
            GateTiles.layout(state.width, state.height, state.size.frame(), state.base, state.joints, state.startModelled && !state.covered,
                    (piece, x, y, glow) -> {
                        if (piece.banner()) {
                            return;
                        }
                        if (Math.abs(Math.abs(x) - half) < 1.0E-4F) {
                            String at = postKey(geometry, x < 0.0F ? -1 : 1);
                            drawn.computeIfAbsent(at, k -> new HashSet<>()).add(key);
                            if (glow) {
                                lit.computeIfAbsent(at, k -> new HashSet<>()).add(key);
                            }
                        }
                        if (covered && y > topLine - 1.0E-3F) {
                            wrong.add(piece.key());
                        }
                    });
            if (!wrong.isEmpty()) {
                return key + " zeichnet unter dem Deckel " + wrong;
            }
        }
        if (drawn.size() != posts) {
            return drawn.size() + " Pfosten statt " + posts + ": " + drawn;
        }
        for (Map.Entry<String, Set<String>> post : drawn.entrySet()) {
            if (post.getValue().size() != 1) {
                return "Pfosten " + post.getKey() + " zeichnen " + post.getValue();
            }
        }
        if (lit.size() != posts) {
            return "Pfosten ohne Lichter: " + lit.size() + " von " + posts;
        }
        return null;
    }

    private static StepResult drawnOnceStep(SelfTest.StepContext ctx) {
        if (SITE.get() == null) {
            return StepResult.fail("kein Aufbau");
        }
        GateRenderer renderer = GateRenderer.current();
        if (renderer == null) {
            return StepResult.fail("kein GateRenderer");
        }
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("Reihe", List.of(ROW_1, ROW_2, ROW_3));
        groups.put("Würfel", List.of(CUBE_S, CUBE_E, CUBE_W, CUBE_N));
        groups.put("gedrehte Reihe", List.of(TURN_ROW_1, TURN_ROW_2, TURN_ROW_3));
        groups.put("gedrehter Würfel", List.of(TURN_S, TURN_E, TURN_W, TURN_N));
        for (Map.Entry<String, List<String>> group : groups.entrySet()) {
            String problem = drawnOnce(renderer, group.getValue(), 4);
            if (problem != null) {
                return ctx.tick() > 200 ? StepResult.fail(group.getKey() + ": " + problem) : StepResult.pending();
            }
        }
        ctx.log("Reihe, Würfel, gedrehte Reihe und gedrehter Würfel: je vier Pfosten, jeden zeichnet genau ein Gate samt Lichtern, die Seiten "
                + "unter dem Deckel zeichnen weder oberen Balken noch dessen Lichter (Darstellung aus dem echten GateRenderer)");
        return StepResult.pass();
    }

    private static StepResult stackStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Aufbau");
        }
        StepResult wait = once(ctx, "Stapel am Server", server -> {
            ServerPlayer player = player(server);
            ServerLevel level = player.level();
            GateEntity carrier = require(level, site, CARRIER);
            GateEntity flag = require(level, site, FLAG);
            GateEntity dive = require(level, site, DIVE);
            GateEntity pole = require(level, site, DIVE_POLE);
            carrier.refreshStack();
            dive.refreshStack();
            if (GatePoleLinks.support(flag) != carrier || GatePoleLinks.support(pole) != dive) {
                return "Aufsatz ohne Träger";
            }
            if (!carrier.carrying() || !dive.carrying()) {
                return "Träger wissen nichts von ihrem Aufsatz";
            }
            if (Math.abs(flag.getY() - (carrier.getY() + carrier.height())) > EPS) {
                return "Flagge nicht auf der Oberkante: " + flag.getY();
            }
            Vec3 near = site.raw(-13.5, 0.0, -3.0);
            player.teleportTo(level, near.x, near.y, near.z, Set.of(), 0.0F, 0.0F, true);
            GateMounting.Result mount = GateMounting.rotate(player, carrier, true);
            if (mount != GateMounting.Result.STACK_LOCKED) {
                return "Gate mit Flagge darauf wechselt die Lage: " + mount;
            }
            GateMounting.Result turned = GateMounting.rotate(player, carrier, false);
            if (turned != GateMounting.Result.ROTATED || GatePoleLinks.support(flag) != carrier) {
                return "Gate mit Flagge um 180° gedreht: " + turned + ", Flagge sitzt " + (GatePoleLinks.support(flag) == carrier);
            }
            GateMounting.rotate(player, carrier, false);
            if (!GateStacking.jointable(carrier.geometry())) {
                return "Träger steht nicht";
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        String problem = clientLap(site, STACK, FLAG_PATH, List.of(1));
        if (problem != null) {
            return StepResult.fail("Flagge auf dem Gate: " + problem);
        }
        wait = once(ctx, "Server-Runde", server -> serverLap(server, site, STACK, FLAG_PATH));
        if (wait != null) {
            return wait;
        }
        ctx.log("Flagge sitzt in der Mitte des oberen Balkens, Slalomstange auf der Ecke des Tauchwürfels, Träger gesperrt; Runde durch das Gate "
                + "und über ihm um die Flagge zählt beim Client und am Server");
        return StepResult.pass();
    }

    private static StepResult removeStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Aufbau");
        }
        StepResult wait = once(ctx, "Abbauen", server -> {
            ServerLevel level = player(server).level();
            GateEntity r2 = require(level, site, ROW_2);
            r2.kill(level);
            GateEntity r1 = require(level, site, ROW_1);
            GateEntity r3 = require(level, site, ROW_3);
            if (r3.joints() != 0 || r3.colliders().length != 4 || r1.joints() != 0 || r1.linked() || r3.linked()) {
                return "Nachbarn nach dem Abbau: " + r1.joints() + "/" + r3.joints();
            }
            GateEntity west = require(level, site, CUBE_W);
            west.kill(level);
            GateEntity south = require(level, site, CUBE_S);
            GateEntity north = require(level, site, CUBE_N);
            GateEntity lid = require(level, site, CUBE_LID);
            if ((south.joints() & GateGeometry.JOINT_LEFT) != 0 || (north.joints() & GateGeometry.JOINT_LEFT) != 0) {
                return "Pfosten an der Westseite fehlen noch: " + south.joints() + "/" + north.joints();
            }
            if (!lid.linked() || (south.joints() & GateGeometry.JOINT_TOP) == 0) {
                return "Deckel verliert die übrigen Seiten";
            }
            GateEntity carrier = require(level, site, CARRIER);
            carrier.kill(level);
            if (entity(level, site, FLAG) != null) {
                return "Flagge bleibt ohne Gate in der Luft";
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        GateEntity r3 = clientEntity(ROW_3);
        if (r3 == null || r3.joints() != 0) {
            return ctx.tick() > 200 ? StepResult.fail("Client: drittes Gate ohne eigenen Pfosten") : StepResult.pending();
        }
        ctx.log("Mittleres Gate der Reihe abgebaut: das rechte bekommt seinen Pfosten zurück; Westseite des Würfels abgebaut: Süd- und Nordseite "
                + "bekommen ihre Pfosten, der Deckel bleibt; Gate mit Flagge abgebaut: die Flagge kommt mit ab");
        return StepResult.pass();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.pass();
        }
        StepResult wait = once(ctx, "Aufräumen", server -> {
            ServerPlayer player = player(server);
            ServerLevel level = player.level();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            AABB area = new AABB(Vec3.atCenterOf(site.block(MIN_X - 4, -2, MIN_Z - 4)), Vec3.atCenterOf(site.block(MAX_X + 4, HEIGHT + 4, MAX_Z + 4)));
            for (GateEntity gate : level.getEntitiesOfClass(GateEntity.class, area, gate -> !gate.isRemoved())) {
                gate.discard();
            }
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, item -> !item.isRemoved())) {
                item.discard();
            }
            CourseRegistry registry = CourseRegistry.get();
            if (registry != null) {
                for (String key : List.of(LOOP, CUBE, STACK)) {
                    int id = site.ids().getOrDefault(key, GateEntity.NO_COURSE);
                    if (id != GateEntity.NO_COURSE) {
                        registry.deleteCourse(id);
                    }
                }
            }
            List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(site.saved().entrySet());
            for (int i = entries.size() - 1; i >= 0; i--) {
                level.setBlockAndUpdate(entries.get(i).getKey(), entries.get(i).getValue());
            }
            site.saved().clear();
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(level, site.home().x, site.home().y, site.home().z, Set.of(), player.getYRot(), player.getXRot(), true);
            return null;
        });
        if (wait != null) {
            return wait;
        }
        SITE.set(null);
        return StepResult.pass();
    }

    private static void aim(LocalPlayer player, Vec3 point) {
        Vec3 eye = player.getEyePosition();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        player.yBodyRotO = yaw;
        player.yHeadRotO = yaw;
    }

    private static final class ThirdPerson implements Screenshots.Scene {
        private final Vec3 feet;
        private final Vec3 target;
        private final Supplier<ItemStack> held;

        private ThirdPerson(Vec3 feet, Vec3 target, Supplier<ItemStack> held) {
            this.feet = feet;
            this.target = target;
            this.held = held;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Site site = SITE.get();
            Minecraft mc = Minecraft.getInstance();
            if (site == null || mc.player == null) {
                return StepResult.fail("kein Aufbau für das Bildschirmfoto");
            }
            Vec3 at = site.raw(this.feet.x, this.feet.y, this.feet.z);
            Supplier<ItemStack> held = this.held;
            StepResult wait = once(ctx, "Blickpunkt", server -> {
                ServerPlayer player = player(server);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(player.level(), at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
                player.setItemInHand(InteractionHand.MAIN_HAND, held.get());
                return null;
            });
            if (wait != null) {
                return wait;
            }
            if (mc.player.position().distanceTo(at) > 0.6 || mc.player.getMainHandItem().getItem() != held.get().getItem()) {
                return ctx.tick() > 300 ? StepResult.fail("Spieler nicht am Blickpunkt") : StepResult.pending();
            }
            this.hold(ctx);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Site site = SITE.get();
            LocalPlayer player = Minecraft.getInstance().player;
            if (site != null && player != null) {
                aim(player, site.raw(this.target.x, this.target.y, this.target.z));
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            ctx.onServer(server -> {
                ServerPlayer player = player(server);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            });
        }
    }

    private static final class Window implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Site site = SITE.get();
            Minecraft mc = Minecraft.getInstance();
            if (site == null) {
                return StepResult.fail("kein Aufbau für das Bildschirmfoto");
            }
            Vec3 at = site.raw(-4.0, 0.0, -8.0);
            StepResult wait = once(ctx, "Fenster", server -> {
                ServerPlayer player = player(server);
                player.teleportTo(player.level(), at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
                return null;
            });
            if (wait != null) {
                return wait;
            }
            int course = site.ids().getOrDefault(LOOP, GateEntity.NO_COURSE);
            if (!(mc.gui.screen() instanceof CourseEditScreen)) {
                CourseEditScreen.open(null, course);
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
        }
    }
}
