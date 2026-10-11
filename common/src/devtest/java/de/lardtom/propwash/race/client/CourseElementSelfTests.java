package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GatePlacement;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceLine;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.sim.StabMode;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

public final class CourseElementSelfTests {
    public static final String START = "start";
    public static final String POLE_1 = "pole_1";
    public static final String POLE_2 = "pole_2";
    public static final String POLE_3 = "pole_3";
    public static final String FLAG = "flag";
    public static final String CUBE = "cube";
    public static final String END = "end";
    public static final List<String> ORDER = List.of(START, POLE_1, POLE_2, POLE_3, FLAG, CUBE, END);
    public static final int FLOOR_X0 = -10;
    public static final int FLOOR_X1 = 18;
    public static final int FLOOR_Z0 = -10;
    public static final int FLOOR_Z1 = 38;
    public static final int AIR_HEIGHT = 12;
    public static final BlockPos WALL = new BlockPos(14, 0, 0);
    private static final int OFFSET_X = -56;
    private static final int OFFSET_Z = 16;
    private static final int SHOT_OFFSET_X = 36;
    private static final int SHOT_OFFSET_Z = -330;
    private static final long MS = 1_000_000L;
    private static final String FUTURE = "future";
    private static final String PLACING = "placing";
    private static final AtomicReference<Rig> RIG = new AtomicReference<>();
    private static final AtomicReference<Rig> SHOT_RIG = new AtomicReference<>();
    private static final AtomicReference<String> RESULT = new AtomicReference<>();
    private static final AtomicReference<Vec3> SHOT_HOME = new AtomicReference<>();
    private static final AtomicInteger SHOT_DRONE = new AtomicInteger(-1);
    private static final Map<BlockPos, Map<BlockPos, BlockState>> FLOORS = new ConcurrentHashMap<>();

    public record Plan(String name, @Nullable CourseElement kind, int dx, int dz, float yaw, int variant) {
        ItemStack stack() {
            if (this.kind == null) {
                return new ItemStack(RaceModule.gateItem(GateSize.MEDIUM).orElseThrow());
            }
            return new ItemStack(RaceModule.item(this.kind, GateSize.MEDIUM).orElseThrow());
        }

        CourseElement element() {
            return this.kind == null ? CourseElement.GATE : this.kind;
        }
    }

    public record Placed(String name, int id, GateGeometry geometry) {
    }

    public record Rig(BlockPos origin, Map<String, Placed> gates, int course) {
        public List<Integer> ids() {
            return this.gates.values().stream().map(Placed::id).toList();
        }

        public Vec3 at(double dx, double dy, double dz) {
            return new Vec3(this.origin.getX() + dx, this.origin.getY() + dy, this.origin.getZ() + dz);
        }
    }

    public static final List<Plan> PLANS = List.of(
            new Plan(START, null, 0, 0, 0.0F, 0),
            new Plan(POLE_1, CourseElement.POLE, 0, 8, 0.0F, CourseElement.LEFT),
            new Plan(POLE_2, CourseElement.POLE, 0, 14, 0.0F, CourseElement.LEFT),
            new Plan(POLE_3, CourseElement.POLE, 0, 20, 0.0F, CourseElement.LEFT),
            new Plan(FLAG, CourseElement.FLAG, 0, 30, 0.0F, CourseElement.LEFT),
            new Plan(CUBE, CourseElement.CUBE, 8, 20, 180.0F, CourseElement.EXIT_FRONT),
            new Plan(END, null, 8, 8, 180.0F, 0));
    public static final int[] LINKED_SIDES = {CourseElement.LEFT, CourseElement.RIGHT, CourseElement.LEFT};

    private CourseElementSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("race: Kurs-Elemente setzen, Slalom anhängen und Regeln", 300, CourseElementSelfTests::placeStep);
        SelfTest.clientStep("race: Kurs-Elemente beim Client und Runde mit falscher Seite", 200, CourseElementSelfTests::clientStep);
        SelfTest.clientStep("race: Runde über Slalom, Flagge und Würfel vom Server geprüft", 200, CourseElementSelfTests::serverLapStep);
        SelfTest.clientStep("race: Kurs-Elemente im Spiel einstellen, Ausgang per Mausrad, Rechtsklick und Enter", 400, CourseElementSelfTests::screenStep);
        SelfTest.clientStep("race: Kurs-Elemente speichern, laden und Drohne prallt ab", 200, CourseElementSelfTests::persistenceStep);
        SelfTest.clientStep("race: Kurs-Elemente aufräumen", 200, CourseElementSelfTests::cleanupStep);
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

    public static void buildWorld(ServerLevel level, BlockPos o, boolean wall) {
        for (int cx = (o.getX() + FLOOR_X0 - 2) >> 4; cx <= (o.getX() + FLOOR_X1 + 2) >> 4; cx++) {
            for (int cz = (o.getZ() + FLOOR_Z0 - 2) >> 4; cz <= (o.getZ() + FLOOR_Z1 + 2) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(o.offset(FLOOR_X0, -1, FLOOR_Z0), o.offset(FLOOR_X1, AIR_HEIGHT, FLOOR_Z1))) {
            saved.put(pos.immutable(), level.getBlockState(pos));
        }
        FLOORS.putIfAbsent(o.immutable(), saved);
        fill(level, o.offset(FLOOR_X0, 0, FLOOR_Z0), o.offset(FLOOR_X1, AIR_HEIGHT, FLOOR_Z1), Blocks.AIR.defaultBlockState());
        fill(level, o.offset(FLOOR_X0, -1, FLOOR_Z0), o.offset(FLOOR_X1, -1, FLOOR_Z1), Blocks.GRASS_BLOCK.defaultBlockState());
        if (wall) {
            level.setBlockAndUpdate(o.offset(WALL), Blocks.STONE_BRICKS.defaultBlockState());
        }
    }

    private static boolean entitiesTicking(ServerLevel level, BlockPos o) {
        for (int cx = (o.getX() + FLOOR_X0 - 2) >> 4; cx <= (o.getX() + FLOOR_X1 + 2) >> 4; cx++) {
            for (int cz = (o.getZ() + FLOOR_Z0 - 2) >> 4; cz <= (o.getZ() + FLOOR_Z1 + 2) >> 4; cz++) {
                if (!level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static AABB area(BlockPos o) {
        return new AABB(Vec3.atLowerCornerOf(o.offset(FLOOR_X0 - 8, -4, FLOOR_Z0 - 8)), Vec3.atLowerCornerOf(o.offset(FLOOR_X1 + 8, AIR_HEIGHT + 8,
                FLOOR_Z1 + 8)));
    }

    public static InteractionResult use(ServerPlayer player, ItemStack stack, BlockPos clicked, Direction face, float yaw, int variant) {
        ItemStack previous = player.getMainHandItem().copy();
        float previousYaw = player.getYRot();
        GatePlacement.Preference before = GateMounting.preference(player, false);
        try {
            GateMounting.setPreference(player, GatePlacement.Preference.DEFAULT.withVariant(variant));
            player.setYRot(yaw);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            Vec3 hit = Vec3.atCenterOf(clicked).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5));
            return stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, clicked, false)));
        } finally {
            GateMounting.setPreference(player, before);
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            player.setYRot(previousYaw);
        }
    }

    private static GateEntity placeOne(ServerPlayer player, BlockPos origin, Plan plan) {
        ServerLevel level = player.level();
        AABB area = area(origin);
        List<GateEntity> before = level.getEntitiesOfClass(GateEntity.class, area);
        BlockPos clicked = origin.offset(plan.dx(), -1, plan.dz());
        if (!use(player, plan.stack(), clicked, Direction.UP, plan.yaw(), plan.variant()).consumesAction()) {
            throw new IllegalStateException(plan.name() + " nicht gesetzt");
        }
        List<GateEntity> after = level.getEntitiesOfClass(GateEntity.class, area, gate -> !before.contains(gate) && !gate.isRemoved());
        if (after.size() != 1) {
            throw new IllegalStateException(plan.name() + ": " + after.size() + " neue Gates");
        }
        return after.getFirst();
    }

    public static Rig place(ServerPlayer player, BlockPos origin, boolean course) {
        ServerLevel level = player.level();
        buildWorld(level, origin, !course);
        Map<String, Placed> placed = new LinkedHashMap<>();
        try {
            for (Plan plan : PLANS) {
                GateEntity gate = placeOne(player, origin, plan);
                placed.put(plan.name(), new Placed(plan.name(), gate.getId(), gate.geometry()));
            }
        } catch (RuntimeException e) {
            discard(level, new Rig(origin.immutable(), placed, GateEntity.NO_COURSE));
            throw e;
        }
        int id = GateEntity.NO_COURSE;
        if (course) {
            ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
            for (String name : ORDER) {
                CourseToolItem.useOnGate(tool, player, (GateEntity) level.getEntity(placed.get(name).id()));
            }
            id = CourseToolItem.courseOf(tool);
            for (String name : ORDER) {
                GateEntity gate = (GateEntity) level.getEntity(placed.get(name).id());
                placed.put(name, new Placed(name, gate.getId(), gate.geometry()));
            }
        }
        return new Rig(origin.immutable(), placed, id);
    }

    private static @Nullable String checkPlacement(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        BlockPos origin = new BlockPos(player.getBlockX() + OFFSET_X, player.getBlockY(), player.getBlockZ() + OFFSET_Z);
        Rig rig = place(player, origin, false);
        RIG.set(rig);
        for (Plan plan : PLANS) {
            GateEntity gate = (GateEntity) level.getEntity(rig.gates().get(plan.name()).id());
            if (gate.kind() != plan.element()) {
                return plan.name() + ": Art " + gate.kind() + " statt " + plan.element();
            }
            GateGeometry g = gate.geometry();
            if (plan.kind() != null) {
                if (gate.variant() != plan.variant()) {
                    return plan.name() + ": Variante " + gate.variant() + " statt " + plan.variant();
                }
                if (g.tilt().pitch() != 0.0F || g.tilt().roll() != 0.0F || gate.base() != GateStacking.Base.MOUNTED) {
                    return plan.name() + ": Element steht nicht aufrecht: " + g;
                }
                if (Math.abs(g.originY() - origin.getY()) > 1.0E-6) {
                    return plan.name() + ": steht nicht auf dem Boden: " + g.originY();
                }
                if (Math.abs(g.heading() - plan.yaw()) > 0.05F && Math.abs(Math.abs(g.heading() - plan.yaw()) - 360.0F) > 0.05F) {
                    return plan.name() + ": Richtung " + g.heading() + " statt " + plan.yaw();
                }
                AABB box = gate.getBoundingBox().inflate(1.0E-6);
                for (AABB part : gate.colliders()) {
                    if (!box.contains(part.getMinPosition()) || !box.contains(part.getMaxPosition())) {
                        return plan.name() + ": Kollisionsteil außerhalb der Entity-Box";
                    }
                }
                int expected = switch (plan.kind()) {
                    case POLE, GATE_POLE -> 1;
                    case FLAG -> 2;
                    case CUBE -> 12;
                    case GATE, FREEFORM -> 0;
                };
                if (gate.colliders().length < expected) {
                    return plan.name() + ": " + gate.colliders().length + " Kollisionsteile statt mindestens " + expected;
                }
            }
        }
        ItemStack flag = new ItemStack(RaceModule.RACE_FLAG.get());
        AABB area = area(origin);
        int count = level.getEntitiesOfClass(GateEntity.class, area).size();
        if (use(player, flag, origin.offset(WALL), Direction.NORTH, 0.0F, 0).consumesAction()
                || level.getEntitiesOfClass(GateEntity.class, area).size() != count) {
            return "Flagge an einer Wand gesetzt";
        }
        GateEntity start = (GateEntity) level.getEntity(rig.gates().get(START).id());
        GateEntity pole = (GateEntity) level.getEntity(rig.gates().get(POLE_1).id());
        ItemStack previous = player.getMainHandItem().copy();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.SLALOM_POLE.get()));
            if (start.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction()
                    || level.getEntitiesOfClass(GateEntity.class, area).size() != count) {
                return "Stange auf ein Gate gestapelt";
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.gateItem(GateSize.SMALL).orElseThrow()));
            pole.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
            if (level.getEntitiesOfClass(GateEntity.class, area).size() != count || pole.stackable() || !pole.stack().equals(List.of(pole))) {
                return "Gate auf eine Stange gestapelt";
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            return "keine Kursdaten";
        }
        ItemStack fresh = new ItemStack(RaceModule.COURSE_TOOL.get());
        CourseToolItem.useOnGate(fresh, player, pole);
        if (CourseToolItem.courseOf(fresh) != GateEntity.NO_COURSE || pole.inCourse()) {
            return "Kurs mit einer Stange als Start angelegt";
        }
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        for (String name : ORDER) {
            CourseToolItem.useOnGate(tool, player, (GateEntity) level.getEntity(rig.gates().get(name).id()));
        }
        int id = CourseToolItem.courseOf(tool);
        CourseRegistry.Course course = registry.course(id);
        if (course == null || course.gates.size() != ORDER.size()) {
            return "Kurs aus Gate und Elementen fehlt: " + (course == null ? "kein Kurs" : course.gates.size());
        }
        Map<String, Placed> linked = new LinkedHashMap<>();
        for (int i = 0; i < ORDER.size(); i++) {
            String name = ORDER.get(i);
            GateEntity gate = (GateEntity) level.getEntity(rig.gates().get(name).id());
            if (gate.gateIndex() != i || gate.courseId() != id) {
                return name + ": Nummer " + gate.gateIndex() + " statt " + i;
            }
            CourseRegistry.GateRecord record = course.gates.get(i);
            if (record.kind() != gate.kind() || record.variant() != gate.variant()) {
                return name + ": Kursdaten " + record.kind() + "/" + record.variant() + " statt " + gate.kind() + "/" + gate.variant();
            }
            linked.put(name, new Placed(name, gate.getId(), gate.geometry()));
        }
        List<String> poles = List.of(POLE_1, POLE_2, POLE_3);
        for (int i = 0; i < poles.size(); i++) {
            GateEntity gate = (GateEntity) level.getEntity(rig.gates().get(poles.get(i)).id());
            if (gate.variant() != LINKED_SIDES[i]) {
                return poles.get(i) + ": Slalom-Seite " + gate.variant() + " statt " + LINKED_SIDES[i];
            }
        }
        if (course.checkpoints() != 1 + 1 + 1 + 1 + 3 + 2 + 1) {
            return "Prüfpunkte des Kurses: " + course.checkpoints();
        }
        CourseData data = course.toData();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CourseData.STREAM_CODEC.encode(buf, data);
        CourseData copy = CourseData.STREAM_CODEC.decode(buf);
        if (!copy.geometry().equals(data.geometry())) {
            return "Kursdaten mit Elementen über das Netz anders: " + copy.geometry();
        }
        RIG.set(new Rig(rig.origin(), linked, id));
        return null;
    }

    private static StepResult placeStep(SelfTest.StepContext ctx) {
        return onServer(ctx, CourseElementSelfTests::checkPlacement);
    }

    private static StepResult clientStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        Rig rig = RIG.get();
        if (rig == null || minecraft.level == null) {
            return StepResult.fail("kein Testbau");
        }
        for (Placed placed : rig.gates().values()) {
            if (!(minecraft.level.getEntity(placed.id()) instanceof GateEntity gate)) {
                return ctx.tick() > 150 ? StepResult.fail(placed.name() + " fehlt beim Client") : StepResult.pending();
            }
            if (!gate.geometry().equals(placed.geometry()) || gate.gateIndex() != ORDER.indexOf(placed.name())) {
                return ctx.tick() > 150 ? StepResult.fail(placed.name() + ": Client " + gate.geometry() + " statt " + placed.geometry())
                        : StepResult.pending();
            }
        }
        CourseData course = ClientCourses.get(rig.course());
        if (course == null || course.gates().size() != ORDER.size()) {
            return ctx.tick() > 150 ? StepResult.fail("Kurs mit Elementen fehlt beim Client") : StepResult.pending();
        }
        List<GateGeometry> gates = ClientCourses.geometry(course);
        for (int i = 0; i < ORDER.size(); i++) {
            if (!gates.get(i).equals(rig.gates().get(ORDER.get(i)).geometry())) {
                return StepResult.fail("Kursdaten beim Client: " + ORDER.get(i) + " " + gates.get(i));
            }
        }
        GateGeometry start = gates.getFirst();
        RaceLine.Path path = RaceLine.build(gates, 0, RaceLine.MAX_AHEAD, 1 + gates.size(), 0, start.centerX() - start.normalX() * 4.0,
                start.centerY(), start.centerZ() - start.normalZ() * 4.0);
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(1);
        tracker.setCourse(rig.course(), gates, 0L);
        long now = 0L;
        List<LapTracker.Event> events = new ArrayList<>();
        for (RaceLine.Sample sample : path.ahead()) {
            now += 10 * MS;
            tracker.update(now, sample.x(), sample.y(), sample.z());
            events.addAll(tracker.drainEvents());
        }
        List<Integer> passed = events.stream().filter(e -> e.type() == LapTracker.EventType.GATE).map(LapTracker.Event::gate).toList();
        if (!passed.equals(List.of(1, 2, 3, 4, 5, 6)) || events.stream().noneMatch(e -> e.type() == LapTracker.EventType.LAP)) {
            return StepResult.fail("Ideallinie durch die Elemente zählt nicht: " + events);
        }
        if (events.stream().anyMatch(e -> e.type() == LapTracker.EventType.WRONG_SIDE || e.type() == LapTracker.EventType.MISSED)) {
            return StepResult.fail("Ideallinie meldet falsche Seite oder Auslassen: " + events);
        }
        LapTracker wrong = new LapTracker();
        wrong.setLapTarget(1);
        wrong.setCourse(rig.course(), gates, 0L);
        double y = start.centerY();
        double[] last = {Double.NaN, 0.0, 0.0};
        now = 0L;
        now = feed(wrong, last, now, start.centerX() - start.normalX() * 3.0, y, start.centerZ() - start.normalZ() * 3.0);
        now = feed(wrong, last, now, start.centerX() + start.normalX() * 2.0, y, start.centerZ() + start.normalZ() * 2.0);
        GateGeometry pole = gates.get(1);
        double sx = -pole.sideX() * 1.5;
        double sz = -pole.sideZ() * 1.5;
        now = feed(wrong, last, now, pole.x() + sx - pole.normalX() * 2.0, y, pole.z() + sz - pole.normalZ() * 2.0);
        feed(wrong, last, now, pole.x() + sx + pole.normalX() * 2.0, y, pole.z() + sz + pole.normalZ() * 2.0);
        List<LapTracker.Event> warned = wrong.drainEvents();
        if (warned.stream().noneMatch(e -> e.type() == LapTracker.EventType.WRONG_SIDE && e.gate() == 1) || wrong.nextGate() != 1) {
            return StepResult.fail("Stange auf der falschen Seite nicht gemeldet: " + warned);
        }
        return StepResult.pass();
    }

    private static long feed(LapTracker tracker, double[] last, long now, double x, double y, double z) {
        if (Double.isNaN(last[0])) {
            tracker.update(now, x, y, z);
        } else {
            int steps = 12;
            for (int i = 1; i <= steps; i++) {
                double f = i / (double) steps;
                now += 10 * MS;
                tracker.update(now, last[0] + (x - last[0]) * f, last[1] + (y - last[1]) * f, last[2] + (z - last[2]) * f);
            }
        }
        last[0] = x;
        last[1] = y;
        last[2] = z;
        return now + 10 * MS;
    }

    private static RacePayloads.RaceReportC2S report(RacePayloads.ReportKind kind, int course, int lap, long lapNanos, long total) {
        return new RacePayloads.RaceReportC2S(kind, course, lap, lapNanos, total);
    }

    private static long fly(ServerPlayer player, List<RaceLine.Sample> samples, long start, long lap) {
        for (int i = 1; i < samples.size(); i++) {
            RaceLine.Sample a = samples.get(i - 1);
            RaceLine.Sample b = samples.get(i);
            long t = start + lap * i / (samples.size() - 1);
            RaceServer.onDroneMoved(player, new Vec3(a.x(), a.y(), a.z()), new Vec3(b.x(), b.y(), b.z()), t);
        }
        return start + lap;
    }

    private static @Nullable String checkServerLap(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Rig rig = RIG.get();
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null || rig == null ? null : registry.course(rig.course());
        if (course == null) {
            return "Kurs mit Elementen fehlt auf dem Server";
        }
        List<GateGeometry> gates = course.toData().geometry();
        GateGeometry start = gates.getFirst();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        drone.snapTo(start.centerX() - start.normalX() * 3.0, start.y() + 1.0, start.centerZ() - start.normalZ() * 3.0, 0.0F, 0.0F);
        drone.setPilot(player);
        if (!level.addFreshEntity(drone)) {
            return "Drohne nicht gespawnt";
        }
        try {
            double px = start.centerX() - start.normalX() * 4.0;
            double pz = start.centerZ() - start.normalZ() * 4.0;
            List<RaceLine.Sample> line = RaceLine.build(gates, 0, RaceLine.MAX_AHEAD, 1 + gates.size(), 0, px, start.centerY(), pz).ahead();
            List<GateGeometry> withoutFlag = new ArrayList<>(gates);
            withoutFlag.remove(4);
            List<RaceLine.Sample> shortcut = RaceLine.build(withoutFlag, 0, RaceLine.MAX_AHEAD, 1 + withoutFlag.size(), 0, px, start.centerY(), pz)
                    .ahead();
            int id = course.id;
            long minimum = RaceServer.minimumLapNanos(course, player);
            long lap = Math.max(minimum * 3L, 9_000L * MS);
            long t = System.nanoTime() + 60_000L * MS;
            if (RaceServer.handle(report(RacePayloads.ReportKind.START, id, 0, 0L, 0L), player, t) != RaceServer.Verdict.ACCEPTED) {
                return "Start abgelehnt";
            }
            long crossed = fly(player, shortcut, t + 100L * MS, lap);
            t = crossed + 100L * MS;
            RaceServer.Verdict verdict = RaceServer.handle(report(RacePayloads.ReportKind.LAP, id, 1, lap, 0L), player, t);
            if (verdict != RaceServer.Verdict.PENDING) {
                return "Runde ohne Wende um die Flagge nicht zurückgehalten: " + verdict;
            }
            t += RaceServer.PENDING_TIMEOUT_NANOS + 100L * MS;
            verdict = RaceServer.flush(player, t);
            if (verdict != RaceServer.Verdict.UNVERIFIED || !registry.board(course).top(null, 10).isEmpty()) {
                return "Runde ohne Wende um die Flagge angenommen: " + verdict;
            }
            t += 2_000L * MS;
            RaceServer.handle(report(RacePayloads.ReportKind.START, id, 0, 0L, 0L), player, t);
            crossed = fly(player, line, t + 100L * MS, lap);
            t = crossed + 100L * MS;
            verdict = RaceServer.handle(report(RacePayloads.ReportKind.LAP, id, 1, lap, 0L), player, t);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Runde über alle Elemente abgelehnt: " + verdict + " (Mindestzeit " + minimum / MS + " ms)";
            }
            List<Leaderboard.Entry> ranking = registry.board(course).top(null, 10);
            if (ranking.isEmpty() || ranking.getFirst().nanos() != lap || !ranking.getFirst().player().equals(player.getUUID())) {
                return "Bestenliste mit Elementen nicht aktualisiert";
            }
            RaceServer.handle(report(RacePayloads.ReportKind.FINISH, id, 1, 0L, lap), player, t);
        } finally {
            drone.setPilot(null);
            drone.discard();
        }
        return null;
    }

    private static StepResult serverLapStep(SelfTest.StepContext ctx) {
        return onServer(ctx, CourseElementSelfTests::checkServerLap);
    }

    private static Vec3 beside(GateGeometry gate, double floorY) {
        double reach = (gate.kind() == CourseElement.CUBE ? gate.width() * 0.5 : 0.0) + 2.0;
        return new Vec3(gate.x() - gate.sideX() * reach, floorY, gate.z() - gate.sideZ() * reach);
    }

    private static StepResult screenStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        Rig rig = RIG.get();
        if (rig == null || minecraft.level == null || minecraft.player == null) {
            return StepResult.fail("kein Testbau");
        }
        if (!(minecraft.level.getEntity(rig.gates().get(CUBE).id()) instanceof GateEntity cube)) {
            return StepResult.fail("Würfel fehlt beim Client");
        }
        int stage = (int) ctx.state().getOrDefault("stage", 0);
        if (stage == 0) {
            ctx.state().put("home", minecraft.player.position());
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                Vec3 stand = beside(cube.geometry(), rig.origin().getY());
                player.teleportTo(player.level(), stand.x, stand.y, stand.z, Set.of(), 0.0F, 20.0F, true);
                ctx.state().put("held", player.getMainHandItem().copy());
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.DIVE_CUBE.get()));
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
            if (!player.getMainHandItem().is(RaceModule.DIVE_CUBE.get()) || !player.isWithinEntityInteractionRange(cube, 2.0)) {
                return ctx.tick() > 200 ? StepResult.fail("Spieler nicht am Würfel") : StepResult.pending();
            }
            minecraft.options.keyShift.setDown(true);
            if (!player.isShiftKeyDown()) {
                if (ctx.tick() > 220) {
                    minecraft.options.keyShift.setDown(false);
                    return StepResult.fail("Schleichen kommt nicht an");
                }
                return StepResult.pending();
            }
            Vec3 hit = new Vec3(cube.getX(), cube.getY() + 1.0, cube.getZ());
            minecraft.gameMode.interact(player, cube, new EntityHitResult(cube, hit), InteractionHand.MAIN_HAND);
            minecraft.options.keyShift.setDown(false);
            GateEditor editor = GateEditor.current();
            if (editor == null || editor.gate() != cube || minecraft.gui.screen() != null) {
                return StepResult.fail("Schleichen + Rechtsklick startet das Einstellen am Würfel nicht (ohne Fenster)");
            }
            if (editor.status() != GateEditor.Status.SAME || editor.model().variant() != CourseElement.EXIT_FRONT) {
                return StepResult.fail("Einstellen startet mit Änderung oder falschem Ausgang: " + editor.status() + " " + editor.model().variant());
            }
            if (!editor.model().rows().equals(List.of(GateEditModel.Row.HEADING, GateEditModel.Row.VARIANT, GateEditModel.Row.WIDTH,
                    GateEditModel.Row.HEIGHT))) {
                return StepResult.fail("Zeilen am Würfel falsch: " + editor.model().rows());
            }
            if (!GateEditDriver.select(minecraft, editor, GateEditModel.Row.VARIANT)) {
                return StepResult.fail("Schleichen + Mausrad wählt die Zeile Ausgang nicht");
            }
            GateEditDriver.scroll(minecraft, 3);
            if (editor.status() != GateEditor.Status.FREE || editor.pending() == null || editor.pending().variant() != CourseElement.EXIT_LEFT) {
                return StepResult.fail("Vorschau zeigt den neuen Ausgang nicht: " + editor.status() + " " + editor.model().variant());
            }
            GateEditDriver.rightClick(minecraft);
            ctx.state().put("stage", 2);
            return StepResult.pending();
        }
        if (stage == 2) {
            if (GateEditor.current() != null) {
                return ctx.tick() > 300 ? StepResult.fail("Rechtsklick übernimmt nicht") : StepResult.pending();
            }
            CourseData course = ClientCourses.get(rig.course());
            boolean applied = cube.variant() == CourseElement.EXIT_LEFT && course != null && course.gates().get(5).variant() == CourseElement.EXIT_LEFT;
            if (!applied) {
                return ctx.tick() > 380 ? StepResult.fail("Server hat den Ausgang nicht übernommen: " + cube.variant()) : StepResult.pending();
            }
            GateEditor.start(cube);
            GateEditor editor = GateEditor.current();
            if (editor == null || editor.status() != GateEditor.Status.SAME) {
                return StepResult.fail("Nach dem Übernehmen bleibt eine Änderung offen");
            }
            editor.rotateKey(true);
            if (editor.model().variant() != CourseElement.EXIT_FRONT) {
                return StepResult.fail("Schleichen + Gate drehen wechselt den Ausgang nicht: " + editor.model().variant());
            }
            GateEditDriver.press(minecraft, InputConstants.KEY_RETURN);
            if (GateEditor.current() != null) {
                return StepResult.fail("Enter übernimmt nicht");
            }
            ctx.state().put("stage", 3);
            return StepResult.pending();
        }
        if (cube.variant() != CourseElement.EXIT_FRONT) {
            return ctx.tick() > 390 ? StepResult.fail("Ausgang nicht zurückgestellt") : StepResult.pending();
        }
        Vec3 home = (Vec3) ctx.state().get("home");
        ItemStack held = (ItemStack) ctx.state().get("held");
        ctx.onServer(server -> {
            ServerPlayer player = player(server);
            player.setItemInHand(InteractionHand.MAIN_HAND, held == null ? ItemStack.EMPTY : held);
            player.teleportTo(player.level(), home.x, home.y, home.z, Set.of(), player.getYRot(), player.getXRot(), true);
        });
        Rig refreshed = RIG.get();
        Map<String, Placed> gates = new LinkedHashMap<>(refreshed.gates());
        gates.put(CUBE, new Placed(CUBE, cube.getId(), cube.geometry()));
        RIG.set(new Rig(refreshed.origin(), gates, refreshed.course()));
        return StepResult.pass();
    }

    private static @Nullable String checkPersistence(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Rig rig = RIG.get();
        ProblemReporter.Collector reporter = new ProblemReporter.Collector();
        for (String name : List.of(POLE_2, FLAG, CUBE)) {
            GateEntity source = (GateEntity) level.getEntity(rig.gates().get(name).id());
            TagValueOutput output = TagValueOutput.createWithContext(reporter, server.registryAccess());
            source.saveWithoutId(output);
            CompoundTag tag = output.buildResult();
            if (!tag.contains("Kind") || !tag.contains("Variant")) {
                return name + ": Art oder Variante nicht gespeichert";
            }
            GateEntity copy = RaceModule.GATE.get().create(level, EntitySpawnReason.LOAD);
            copy.load(TagValueInput.create(reporter, server.registryAccess(), tag));
            if (!copy.geometry().equals(source.geometry()) || !copy.getBoundingBox().equals(source.getBoundingBox())) {
                return name + ": nach dem Laden anders: " + copy.geometry();
            }
        }
        GateEntity flag = (GateEntity) level.getEntity(rig.gates().get(FLAG).id());
        GateGeometry before = flag.geometry();
        Vec3 home = player.position();
        Vec3 stand = beside(before, rig.origin().getY());
        try {
            player.teleportTo(level, stand.x, stand.y, stand.z, Set.of(), 0.0F, 0.0F, true);
            if (GateMounting.edit(player, flag, before.heading(), 0.0F, 0.0F, 4.0, 5.0, CourseElement.RIGHT) != GateMounting.Result.EDITED) {
                return "Flagge nicht eingestellt";
            }
        } finally {
            player.teleportTo(level, home.x, home.y, home.z, Set.of(), player.getYRot(), player.getXRot(), true);
        }
        if (flag.variant() != CourseElement.RIGHT || Math.abs(flag.height() - 5.0) > 1.0E-9 || !flag.customOpening()) {
            return "Einstellung der Flagge fehlt: " + flag.geometry();
        }
        TagValueOutput output = TagValueOutput.createWithContext(reporter, server.registryAccess());
        flag.saveWithoutId(output);
        CompoundTag tag = output.buildResult();
        GateEntity copy = RaceModule.GATE.get().create(level, EntitySpawnReason.LOAD);
        copy.load(TagValueInput.create(reporter, server.registryAccess(), tag));
        if (!copy.geometry().equals(flag.geometry())) {
            return "eingestellte Flagge nach dem Laden anders: " + copy.geometry();
        }
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(rig.course());
        if (course == null || course.gates.get(4).variant() != CourseElement.RIGHT || !course.toData().gates().get(4).geometry().equals(flag.geometry())) {
            return "Kursdaten nach dem Einstellen der Flagge nicht nachgeführt";
        }
        player.teleportTo(level, stand.x, stand.y, stand.z, Set.of(), 0.0F, 0.0F, true);
        try {
            GateMounting.edit(player, flag, before.heading(), 0.0F, 0.0F, CourseElement.FLAG.defaultWidth(), CourseElement.FLAG.defaultHeight(),
                    CourseElement.LEFT);
        } finally {
            player.teleportTo(level, home.x, home.y, home.z, Set.of(), player.getYRot(), player.getXRot(), true);
        }
        if (!flag.geometry().equals(before) || flag.customOpening()) {
            return "Flagge nicht zurückgestellt: " + flag.geometry();
        }
        tag.remove("Kind");
        tag.remove("Variant");
        tag.remove("OpeningWidth");
        tag.remove("OpeningHeight");
        GateEntity old = RaceModule.GATE.get().create(level, EntitySpawnReason.LOAD);
        old.load(TagValueInput.create(reporter, server.registryAccess(), tag));
        if (old.element() || old.width() != GateSize.MEDIUM.width()) {
            return "altes Gate ohne Art lädt nicht als Gate";
        }
        if (!reporter.isEmpty()) {
            return "Meldungen beim Speichern/Laden: " + reporter.getReport();
        }
        return checkDrone(level, rig);
    }

    private static @Nullable String checkDrone(ServerLevel level, Rig rig) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setNoGravity(true);
        try {
            GateGeometry pole = ((GateEntity) level.getEntity(rig.gates().get(POLE_1).id())).geometry();
            Vec3 normal = new Vec3(pole.normalX(), 0.0, pole.normalZ());
            Vec3 start = new Vec3(pole.x(), pole.y() + 1.5, pole.z()).subtract(normal.scale(1.5));
            drone.snapTo(start.x, start.y, start.z, 0.0F, 0.0F);
            if (!level.addFreshEntity(drone)) {
                return "Drohne nicht gespawnt";
            }
            drone.move(MoverType.SELF, normal.scale(3.0));
            if (drone.position().distanceTo(start.add(normal.scale(3.0))) < 0.5) {
                return "Drohne fliegt durch die Slalomstange";
            }
            GateGeometry cube = ((GateEntity) level.getEntity(rig.gates().get(CUBE).id())).geometry();
            Vec3 above = new Vec3(cube.x(), cube.y() + cube.height() + 1.0, cube.z());
            drone.snapTo(above.x, above.y, above.z, 0.0F, 0.0F);
            drone.move(MoverType.SELF, new Vec3(0.0, -3.0, 0.0));
            if (drone.position().distanceTo(above.add(0.0, -3.0, 0.0)) > 1.0E-6) {
                return "Tauchen in den Würfel von oben gebremst: " + drone.position();
            }
            Vec3 side = new Vec3(cube.x() + cube.sideX() * (cube.width() * 0.5 - cube.frame() * 0.5), cube.y() + cube.height() - cube.frame() * 0.5
                    + 1.2, cube.z() + cube.sideZ() * (cube.width() * 0.5 - cube.frame() * 0.5));
            drone.snapTo(side.x, side.y, side.z, 0.0F, 0.0F);
            drone.move(MoverType.SELF, new Vec3(0.0, -2.0, 0.0));
            if (drone.position().distanceTo(side.add(0.0, -2.0, 0.0)) < 0.5) {
                return "Drohne fliegt durch den oberen Balken des Würfels";
            }
        } finally {
            drone.discard();
        }
        return null;
    }

    private static StepResult persistenceStep(SelfTest.StepContext ctx) {
        return onServer(ctx, CourseElementSelfTests::checkPersistence);
    }

    private static void discard(ServerLevel level, Rig rig) {
        for (int id : rig.ids()) {
            if (level.getEntity(id) instanceof GateEntity gate) {
                gate.discard();
            }
        }
        for (GateEntity stray : level.getEntitiesOfClass(GateEntity.class, area(rig.origin()), GateEntity::element)) {
            stray.discard();
        }
        Map<BlockPos, BlockState> saved = FLOORS.remove(rig.origin());
        if (saved != null) {
            saved.forEach(level::setBlockAndUpdate);
        }
        CourseRegistry registry = CourseRegistry.get();
        if (registry != null && rig.course() != GateEntity.NO_COURSE) {
            registry.deleteCourse(rig.course());
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        GateEditor.stop();
        mc.options.keyShift.setDown(false);
        Rig rig = RIG.get();
        if (rig == null) {
            return StepResult.pass();
        }
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> discard(player(server).level(), rig)));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        boolean gone = mc.level != null && rig.ids().stream().allMatch(id -> mc.level.getEntity(id) == null);
        if (!gone || ClientCourses.get(rig.course()) != null) {
            return ctx.tick() > 150 ? StepResult.fail("Elemente oder Kurs noch beim Client") : StepResult.pending();
        }
        RIG.set(null);
        return StepResult.pass();
    }

    public static void registerScreenshots(Supplier<BlockPos> base) {
        Supplier<BlockPos> origin = () -> base.get().offset(SHOT_OFFSET_X, 0, SHOT_OFFSET_Z);
        Screenshots.step("bildschirmfotos: Kurs mit Slalom, Wendeflagge und Tauchwürfel", "course_elements", 40,
                new View(origin, new Vec3(-5.0, 6.0, -5.0), new Vec3(2.5, 0.5, 16.0)));
        Screenshots.step("bildschirmfotos: Slalomstangen mit Pfeilen und Kreuzen", "course_elements_slalom", 40,
                new View(origin, new Vec3(-1.8, 0.2, 3.0), new Vec3(0.3, 1.2, 20.0)));
        Screenshots.step("bildschirmfotos: Wendeflagge mit Pfeilbogen", "course_elements_flag", 40,
                new View(origin, new Vec3(-3.5, 1.5, 24.5), new Vec3(0.5, 2.0, 30.5)));
        Screenshots.step("bildschirmfotos: Tauchwürfel mit Ausgang und Tauchpfeilen", "course_elements_cube", 40,
                new View(origin, new Vec3(14.0, 1.6, 27.0), new Vec3(8.5, 2.6, 20.5)));
        Screenshots.step("bildschirmfotos: Ideallinie durch Slalom und um die Flagge", "course_elements_race_line", 40, new RaceLineView(origin));
        Screenshots.step("bildschirmfotos: Tauchwürfel einstellen im Spiel mit Ausgang", "course_element_edit_mode", 30, new EditView(origin, false));
        SelfTest.clientStep("bildschirmfotos: Kurs-Elemente aufräumen", 200, CourseElementSelfTests::cleanupShotsStep);
    }

    public static Screenshots.Scene editScene(Supplier<BlockPos> base) {
        return new EditView(() -> base.get().offset(SHOT_OFFSET_X, 0, SHOT_OFFSET_Z), true);
    }

    private static StepResult cleanupShotsStep(SelfTest.StepContext ctx) {
        Rig rig = SHOT_RIG.get();
        if (rig == null && SHOT_DRONE.get() < 0) {
            return StepResult.pass();
        }
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                releaseShotDrone(player);
                if (rig != null) {
                    discard(player.level(), rig);
                }
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                returnFromShots(player);
            }));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        SHOT_RIG.set(null);
        return StepResult.pass();
    }

    private static void returnFromShots(ServerPlayer player) {
        Vec3 home = SHOT_HOME.getAndSet(null);
        if (home != null) {
            player.teleportTo(player.level(), home.x, home.y, home.z, Set.of(), player.getYRot(), player.getXRot(), true);
        }
    }

    private static void releaseShotDrone(ServerPlayer player) {
        int id = SHOT_DRONE.getAndSet(-1);
        if (id >= 0 && player.level().getEntity(id) instanceof QuadcopterEntity drone) {
            if (drone.pilot().isPresent()) {
                DronePiloting.release(drone);
            }
            drone.discard();
        }
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
    }

    private static class View implements Screenshots.Scene {
        protected final Supplier<BlockPos> origin;
        private final Vec3 feet;
        private final Vec3 target;
        private final @Nullable Supplier<ItemStack> held;
        private ItemStack previous = ItemStack.EMPTY;

        private View(Supplier<BlockPos> origin, Vec3 feet, Vec3 target) {
            this(origin, feet, target, null);
        }

        private View(Supplier<BlockPos> origin, Vec3 feet, Vec3 target, @Nullable Supplier<ItemStack> held) {
            this.origin = origin;
            this.feet = feet;
            this.target = target;
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
                    this.previous = player.getMainHandItem().copy();
                    player.setItemInHand(InteractionHand.MAIN_HAND, this.held == null ? ItemStack.EMPTY : this.held.get());
                }));
                return StepResult.pending();
            }
            StepResult waiting = future(ctx);
            if (waiting != null) {
                return waiting;
            }
            if (SHOT_RIG.get() == null) {
                CompletableFuture<?> placing = (CompletableFuture<?>) ctx.state().get(PLACING);
                if (placing == null) {
                    ctx.state().put(PLACING, ctx.onServer(server -> {
                        ServerPlayer player = player(server);
                        if (SHOT_RIG.get() == null && entitiesTicking(player.level(), this.origin.get())) {
                            SHOT_RIG.set(place(player, this.origin.get(), true));
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
                return ctx.tick() > 300 ? StepResult.fail("Testgelände für Kurs-Elemente lädt nicht") : StepResult.pending();
            }
            Rig rig = SHOT_RIG.get();
            if (mc.player == null || mc.level == null || rig == null) {
                return ctx.tick() > 300 ? StepResult.fail("keine Szene für Kurs-Elemente") : StepResult.pending();
            }
            this.hold(ctx);
            if (mc.player.position().distanceTo(this.world(this.feet)) > 0.6) {
                return ctx.tick() > 300 ? StepResult.fail("Spieler nicht am Blickpunkt") : StepResult.pending();
            }
            for (Placed placed : rig.gates().values()) {
                if (!(mc.level.getEntity(placed.id()) instanceof GateEntity gate) || !gate.geometry().equals(placed.geometry())) {
                    return ctx.tick() > 300 ? StepResult.fail(placed.name() + " fehlt beim Client") : StepResult.pending();
                }
            }
            if (ClientCourses.get(rig.course()) == null) {
                return ctx.tick() > 300 ? StepResult.fail("Kurs fehlt beim Client") : StepResult.pending();
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
            ItemStack restore = this.previous;
            ctx.onServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, restore));
        }
    }

    private static final class RaceLineView extends View {
        private static final String SESSION = "session";
        private int savedAhead = -1;

        private RaceLineView(Supplier<BlockPos> origin) {
            super(origin, new Vec3(-6.5, 3.5, -3.5), new Vec3(1.0, 1.0, 14.0));
        }

        private Vec3 park() {
            return this.world(new Vec3(0.5, 0.05, 2.5));
        }

        @Override
        protected StepResult ready(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Rig rig = SHOT_RIG.get();
            if (!ctx.state().containsKey(SESSION)) {
                Vec3 park = this.park();
                ctx.state().put(SESSION, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    releaseShotDrone(player);
                    QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
                    if (drone == null) {
                        throw new IllegalStateException("Drohne nicht erzeugt");
                    }
                    ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
                    stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
                    drone.loadFromStack(stack);
                    drone.snapTo(park.x, park.y, park.z, 0.0F, 0.0F);
                    level.addFreshEntity(drone);
                    drone.resetTo(park, new Quaternionf().rotationY((float) Math.PI));
                    SHOT_DRONE.set(drone.getId());
                    ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
                    remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
                    player.setItemInHand(InteractionHand.MAIN_HAND, remote);
                    DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.LINE_OF_SIGHT);
                    if (result != DronePiloting.StartResult.STARTED) {
                        throw new IllegalStateException("Start verweigert: " + result);
                    }
                }));
                return StepResult.pending();
            }
            CompletableFuture<?> session = (CompletableFuture<?>) ctx.state().get(SESSION);
            if (session.isCompletedExceptionally()) {
                return StepResult.fail("Sichtflug für die Ideallinie: " + session.exceptionNow());
            }
            if (!session.isDone() || !PilotSession.INSTANCE.isLineOfSight()) {
                return ctx.tick() > 400 ? StepResult.fail("keine Sichtflug-Sitzung") : StepResult.pending();
            }
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            if (this.savedAhead < 0) {
                this.savedAhead = RaceConfig.CONFIG.get().raceLineGates;
                RaceConfig.CONFIG.get().raceLineGates = 5;
            }
            ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
            if (!tracker.tracker().racing() || tracker.courseId() != rig.course()) {
                GateGeometry start = rig.gates().get(START).geometry();
                Vec3 before = new Vec3(start.centerX() - start.normalX() * 3.0, start.centerY(), start.centerZ() - start.normalZ() * 3.0);
                Vec3 middle = new Vec3(start.centerX() + start.normalX() * 0.5, start.centerY() - 0.8, start.centerZ() + start.normalZ() * 0.5);
                Vec3 after = this.park().add(0.0, 0.1, 0.0);
                long now = tracker.now();
                tracker.selectCourseAt(mc, before);
                tracker.tracker().resetPosition();
                tracker.feed(now, before);
                tracker.feed(now + 150_000_000L, middle);
                tracker.feed(now + 300_000_000L, after);
                return ctx.tick() > 450 ? StepResult.fail("Rennen über die Elemente startet nicht: " + tracker.tracker().phase()) : StepResult.pending();
            }
            if (RaceLineRenderer.active() && RaceLineRenderer.lastSamples() > 0) {
                return StepResult.pass();
            }
            return ctx.tick() > 500 ? StepResult.fail("Ideallinie wird nicht gezeichnet") : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            super.hold(ctx);
            if (PilotSession.INSTANCE.isActive()) {
                PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            PilotSession.INSTANCE.clearTestInput();
            if (this.savedAhead >= 0) {
                RaceConfig.CONFIG.get().raceLineGates = this.savedAhead;
                this.savedAhead = -1;
            }
            ctx.onServer(server -> releaseShotDrone(player(server)));
            super.finish(ctx);
        }
    }

    private static final class EditView extends View {
        private final boolean standalone;

        private EditView(Supplier<BlockPos> origin, boolean standalone) {
            super(origin, new Vec3(13.5, 0.0, 15.5), new Vec3(8.5, 1.5, 20.5), () -> new ItemStack(RaceModule.DIVE_CUBE.get()));
            this.standalone = standalone;
        }

        @Override
        protected StepResult ready(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Rig rig = SHOT_RIG.get();
            if (rig == null || mc.level == null || mc.player == null
                    || !(mc.level.getEntity(rig.gates().get(CUBE).id()) instanceof GateEntity cube)) {
                return StepResult.fail("Würfel zum Einstellen fehlt");
            }
            if (!GateMounting.holdsTool(mc.player)) {
                return ctx.tick() > 300 ? StepResult.fail("Würfel-Item kommt nicht an") : StepResult.pending();
            }
            GateEditor editor = GateEditor.current();
            if (editor == null || editor.gate() != cube) {
                GateEditor.start(cube);
                editor = GateEditor.current();
                if (editor == null) {
                    return StepResult.fail("Einstellen startet nicht");
                }
                GateGeometry g = cube.geometry();
                editor.set(g.heading(), 0.0F, 0.0F, 3.0, 3.0);
                editor.setVariant(CourseElement.EXIT_RIGHT);
                editor.model().select(GateEditModel.Row.VARIANT);
            }
            return editor.status() == GateEditor.Status.FREE ? StepResult.pass()
                    : ctx.tick() > 300 ? StepResult.fail("Vorschau beim Einstellen nicht frei: " + editor.status()) : StepResult.pending();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            GateEditor.stop();
            super.finish(ctx);
            Rig rig = SHOT_RIG.get();
            if (this.standalone && rig != null) {
                SHOT_RIG.set(null);
                ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    discard(player.level(), rig);
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                    returnFromShots(player);
                });
            }
        }
    }
}
