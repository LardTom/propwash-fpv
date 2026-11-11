package de.lardtom.propwash.race.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.StackGateJoinCheck;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.guide.client.TrainingSelfTests;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateOpenings;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.GhostCodec;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.race.RecordGhosts;
import de.lardtom.propwash.race.RaceLine;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.StackTurnRoute;
import de.lardtom.propwash.race.world.WorldCoursePiece;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class GateStackSelfTests {
    public static final int DOUBLE_OFFSET = 10;
    private static final GateStacking.Base[] BASES = {GateStacking.Base.LEGS, GateStacking.Base.STACKED, GateStacking.Base.STACKED,
            GateStacking.Base.LEGS, GateStacking.Base.STACKED};
    private static final String FUTURE = "future";
    private static final long MS = 1_000_000L;
    private static final AtomicReference<List<Integer>> IDS = new AtomicReference<>(List.of());
    private static final AtomicInteger COURSE = new AtomicInteger(GateEntity.NO_COURSE);

    public record Built(List<GateEntity> gates, int course) {
    }

    private GateStackSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("race: Gates stapeln, Grenzen und Nachrutschen", 100, GateStackSelfTests::limitsStep);
        SelfTest.clientStep("race: Leiter und Doppel-Gate als Kurs", 200, GateStackSelfTests::courseStep);
        SelfTest.clientStep("race: Runde durch Leiter und Doppel-Gate", 20, GateStackSelfTests::lapStep);
        SelfTest.clientStep("race: gestapelte Gates aufräumen", 200, GateStackSelfTests::cleanupStep);
        SelfTest.clientStep("race: Gate-Stapel: Durchflug unten am Gate auf Beinen zählt (Client und Server)", 400, GateStackSelfTests::legsLapStep);
        SelfTest.clientStep("race: Gate-Stapel: einzelnes Gate zählt unten erst ab Rahmen", 100, GateStackSelfTests::singleFrameStep);
        SelfTest.clientStep("race: Gate-Stapel: nach Abbau des oberen Gates wieder Rahmen unten", 200, GateStackSelfTests::removeTopStep);
        SelfTest.clientStep("race: Gate-Stapel: aufräumen", 200, GateStackSelfTests::legsCleanupStep);
        SelfTest.clientStep("race: Gate-Stapel: frisch gestapelt: Beine beim Client im Tick des neuen Gates, höchstens 5 Ticks ab dem Stapeln (Client und Server)",
                900, GateStackSelfTests::freshStep);
        SelfTest.clientStep("race: Weltkurs-Leiter: Rekorde und Ghost-Layout unverändert", 900, GateStackSelfTests::arenaStep);
    }

    private static StepResult future(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Aktion fehlgeschlagen: " + future.exceptionNow().getMessage());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    public static InteractionResult use(ServerPlayer player, GateSize size, int x, int groundY, int z, float yaw) {
        ItemStack previous = player.getMainHandItem().copy();
        float previousYaw = player.getYRot();
        ItemStack stack = new ItemStack(RaceModule.gateItem(size).orElseThrow());
        try {
            player.setYRot(yaw);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockPos ground = new BlockPos(x, groundY - 1, z);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0), Direction.UP, ground, false);
            return stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            player.setYRot(previousYaw);
        }
    }

    public static GateEntity place(ServerPlayer player, GateSize size, int x, int groundY, int z, float yaw) {
        ServerLevel level = player.level();
        InteractionResult result = use(player, size, x, groundY, z, yaw);
        if (!result.consumesAction()) {
            throw new IllegalStateException("Gate bei " + x + "," + z + " nicht platziert: " + result);
        }
        List<GateEntity> found = level.getEntitiesOfClass(GateEntity.class, new AABB(new BlockPos(x, groundY, z)).inflate(0.4),
                gate -> !gate.isRemoved() && gate.getY() < groundY + 0.1);
        if (found.size() != 1) {
            throw new IllegalStateException("Gate bei " + x + "," + z + " nicht gefunden: " + found.size());
        }
        return found.getFirst();
    }

    public static InteractionResult stack(ServerPlayer player, GateEntity target, GateSize size, float yaw) {
        ItemStack previous = player.getMainHandItem().copy();
        float previousYaw = player.getYRot();
        try {
            player.setYRot(yaw);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.gateItem(size).orElseThrow()));
            return target.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            player.setYRot(previousYaw);
        }
    }

    public static GateEntity stacked(ServerPlayer player, GateEntity target, GateSize size, float yaw) {
        InteractionResult result = stack(player, target, size, yaw);
        if (!result.consumesAction()) {
            throw new IllegalStateException("Gate " + size + " nicht gestapelt: " + result);
        }
        GateEntity above = target.stack().getLast();
        if (above == target || above.size() != size) {
            throw new IllegalStateException("gestapeltes Gate nicht gefunden");
        }
        return above;
    }

    public static Built build(ServerPlayer player, int x, int groundY, int z) {
        GateEntity a0 = place(player, GateSize.MEDIUM, x, groundY, z, 0.0F);
        GateEntity a1 = stacked(player, a0, GateSize.SMALL, 180.0F);
        GateEntity a2 = stacked(player, a1, GateSize.SMALL, 0.0F);
        GateEntity b0 = place(player, GateSize.MEDIUM, x + DOUBLE_OFFSET, groundY, z, 0.0F);
        GateEntity b1 = stacked(player, b0, GateSize.MEDIUM, 10.0F);
        List<GateEntity> gates = List.of(a0, a1, a2, b0, b1);
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        for (GateEntity gate : gates) {
            CourseToolItem.useOnGate(tool, player, gate);
        }
        return new Built(gates, CourseToolItem.courseOf(tool));
    }

    private static String checkLimits(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        int x = player.getBlockX() - 14;
        int z = player.getBlockZ() - 16;
        int y = player.getBlockY();
        List<GateEntity> column = new ArrayList<>();
        boolean sneaking = player.isShiftKeyDown();
        try {
            column.add(place(player, GateSize.MEDIUM, x, y, z, 90.0F));
            if (use(player, GateSize.SMALL, x, y, z, 90.0F).consumesAction()) {
                return "zweites Gate an derselben Stelle am Boden erlaubt";
            }
            column.add(stacked(player, column.getFirst(), GateSize.SMALL, 90.0F));
            if (stack(player, column.getFirst(), GateSize.MEDIUM, 90.0F).consumesAction()) {
                return "breiteres Gate auf schmales gestapelt";
            }
            column.add(stacked(player, column.get(1), GateSize.SMALL, 270.0F));
            column.add(stacked(player, column.getFirst(), GateSize.SMALL, 90.0F));
            for (int i = 1; i < column.size(); i++) {
                GateGeometry lower = column.get(i - 1).geometry();
                GateGeometry upper = column.get(i).geometry();
                if (!GateStacking.restsOn(upper, lower)) {
                    return "Gate " + i + " sitzt nicht auf dem darunter: " + upper + " / " + lower;
                }
            }
            if (column.get(2).facingYaw() != 270.0F || column.get(3).facingYaw() != 90.0F) {
                return "Ausrichtung beim Stapeln falsch: " + column.get(2).facingYaw() + "/" + column.get(3).facingYaw();
            }
            if (column.getFirst().stack().size() != GateStacking.MAX_STACK) {
                return "Stapel hat " + column.getFirst().stack().size() + " Gates";
            }
            if (stack(player, column.get(1), GateSize.SMALL, 90.0F).consumesAction()) {
                return "fünftes Gate gestapelt";
            }
            player.setShiftKeyDown(true);
            GateEntity bottom = column.removeFirst();
            if (!bottom.hurtServer(level, level.damageSources().playerAttack(player), 1.0F) || !bottom.isRemoved()) {
                return "unterstes Gate nicht abgebaut";
            }
            double support = bottom.getY();
            for (GateEntity gate : column) {
                if (Math.abs(gate.getY() - support) > 1.0E-6) {
                    return "Gate nicht nachgerutscht: y=" + gate.getY() + " statt " + support;
                }
                support += gate.size().height() - gate.size().frame();
            }
            GateEntity middle = column.remove(1);
            middle.discard();
            if (Math.abs(column.get(1).getY() - column.getFirst().getY() - GateSize.SMALL.height() + GateSize.SMALL.frame()) > 1.0E-6) {
                return "oberes Gate nach dem Entfernen der Mitte nicht nachgerutscht";
            }
            return null;
        } finally {
            player.setShiftKeyDown(sneaking);
            for (GateEntity gate : column) {
                gate.discard();
            }
        }
    }

    private static StepResult limitsStep(SelfTest.StepContext ctx) {
        AtomicReference<String> result = new AtomicReference<>();
        if (ctx.tick() == 0) {
            ctx.state().put("result", result);
            ctx.state().put(FUTURE, ctx.onServer(server -> result.set(checkLimits(server))));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        @SuppressWarnings("unchecked")
        String problem = ((AtomicReference<String>) ctx.state().get("result")).get();
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }

    private static StepResult courseStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                Built built = build(player, player.getBlockX() - 14, player.getBlockY(), player.getBlockZ() + 14);
                CourseRegistry registry = CourseRegistry.get();
                CourseRegistry.Course course = registry == null ? null : registry.course(built.course());
                if (course == null || course.gates.size() != built.gates().size()) {
                    throw new IllegalStateException("Kurs mit gestapelten Gates nicht angelegt");
                }
                List<Integer> ids = new ArrayList<>();
                for (int i = 0; i < built.gates().size(); i++) {
                    GateEntity gate = built.gates().get(i);
                    if (gate.gateIndex() != i) {
                        throw new IllegalStateException("Gate " + i + " hat Nummer " + gate.gateIndex());
                    }
                    ids.add(gate.getId());
                }
                IDS.set(List.copyOf(ids));
                COURSE.set(built.course());
            }));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return StepResult.pending();
        }
        List<Integer> ids = IDS.get();
        boolean[] covered = {true, true, false, true, false};
        String problem = null;
        for (int i = 0; i < ids.size() && problem == null; i++) {
            if (!(minecraft.level.getEntity(ids.get(i)) instanceof GateEntity gate)) {
                problem = "Gate " + i + " fehlt auf dem Client";
            } else if (gate.gateIndex() != i || gate.courseId() != COURSE.get()) {
                problem = "Gate " + i + " falsch nummeriert: " + gate.gateIndex();
            } else if (gate.covered() != covered[i] || gate.supported() != (i == 1 || i == 2 || i == 4) || gate.base() != BASES[i]) {
                problem = "Gate " + i + " Stapelzustand falsch: oben belegt=" + gate.covered() + " getragen=" + gate.supported() + " " + gate.base();
            } else if (gate.colliders().length != 3) {
                problem = "Gate " + i + " hat " + gate.colliders().length + " Kollisionsboxen statt 3 (kein eigener unterer Balken)";
            } else if (GateRenderer.marking(gate) != (i == 0 ? GateRenderer.Marking.START : GateRenderer.Marking.NUMBER)) {
                problem = "Gate " + i + " Markierung " + GateRenderer.marking(gate);
            }
        }
        CourseData data = ClientCourses.get(COURSE.get());
        if (problem == null && (data == null || data.gates().size() != ids.size())) {
            problem = "Kurs nicht synchronisiert: " + data;
        }
        if (problem != null) {
            return ctx.tick() > 180 ? StepResult.fail(problem) : StepResult.pending();
        }
        List<GateGeometry> gates = data.geometry();
        if (!RaceLine.needsDetour(gates.get(3), gates.get(4)) || RaceLine.needsDetour(gates.get(0), gates.get(1))) {
            return StepResult.fail("Umweg der Ideallinie falsch erkannt");
        }
        GateGeometry start = gates.getFirst();
        RaceLine.Path path = RaceLine.build(gates, 0, 5, RaceLine.ENDLESS, 0, start.x(), start.centerY(), start.z() - 5.0);
        if (path.ahead().size() < 20) {
            return StepResult.fail("Ideallinie durch den Stapel zu kurz: " + path.ahead().size());
        }
        String missing = missingVariant();
        if (missing != null) {
            return StepResult.fail("Leiter-Modell fehlt: " + missing);
        }
        float plate = GateRenderer.plateBottom(GateSize.MEDIUM, true);
        if (plate + GateRenderer.PLATE_HEIGHT > GateSize.MEDIUM.height() + 1.0E-4F) {
            return StepResult.fail("Nummern-Tafel eines belegten Gates ragt in das Gate darüber");
        }
        ctx.log("Leiter (3) und Doppel-Gate (2) als Kurs " + COURSE.get() + " auf dem Client");
        return StepResult.pass();
    }

    private static String missingVariant() {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStackRenderState probe = new ItemStackRenderState();
        for (GateSize size : GateSize.values()) {
            for (GateStacking.Base base : GateStacking.Base.values()) {
                for (Identifier model : List.of(GateRenderer.frameModelId(size, base), GateRenderer.lightsModelId(size, base),
                        GateRenderer.startFrameModelId(size, base))) {
                    ItemStack stack = new ItemStack(Items.PAPER);
                    stack.set(DataComponents.ITEM_MODEL, model);
                    minecraft.getItemModelResolver().updateForTopItem(probe, stack, ItemDisplayContext.NONE, minecraft.level, null, 0);
                    if (probe.isEmpty()) {
                        return model.toString();
                    }
                    AABB box = probe.getModelBoundingBox();
                    if (base == GateStacking.Base.STACKED && !model.getPath().contains("lights") && box.minY >= 0.0) {
                        return model + " ohne T-Stücke unten";
                    }
                    probe.clear();
                }
            }
        }
        return null;
    }

    public static List<Vec3> lapPath(List<GateGeometry> gates) {
        List<Vec3> path = new ArrayList<>();
        for (int i = 0; i <= gates.size(); i++) {
            GateGeometry gate = gates.get(i % gates.size());
            Vec3 before = point(gate, -2.0, 0.0);
            if (i > 0) {
                GateGeometry previous = gates.get(i - 1);
                if (RaceLine.needsDetour(previous, gate)) {
                    double reach = Math.max(previous.width(), gate.width()) * 0.5 + 2.0;
                    Vec3 exit = point(previous, 2.0, reach);
                    path.add(exit);
                    path.add(new Vec3(exit.x, gate.centerY(), exit.z));
                    Vec3 back = point(gate, -2.0, reach);
                    path.add(back);
                }
            }
            path.add(before);
            path.add(point(gate, 2.0, 0.0));
        }
        return path;
    }

    private static Vec3 point(GateGeometry gate, double ahead, double side) {
        return new Vec3(gate.x() + gate.normalX() * ahead + gate.sideX() * side, gate.centerY(),
                gate.z() + gate.normalZ() * ahead + gate.sideZ() * side);
    }

    private static StepResult lapStep(SelfTest.StepContext ctx) {
        CourseData data = ClientCourses.get(COURSE.get());
        if (data == null) {
            return StepResult.fail("Kurs fehlt");
        }
        List<GateGeometry> gates = data.geometry();
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(0);
        tracker.setCourse(COURSE.get(), gates, 0L);
        List<Vec3> path = lapPath(gates);
        long now = 1_000L * MS;
        Vec3 at = point(gates.getFirst(), -4.0, 0.0);
        tracker.update(now, at.x, at.y, at.z);
        List<LapTracker.Event> events = new ArrayList<>();
        for (Vec3 target : path) {
            Vec3 delta = target.subtract(at);
            int steps = Math.max(1, (int) Math.ceil(delta.length() / 0.3));
            Vec3 from = at;
            for (int i = 1; i <= steps; i++) {
                now += 30 * MS;
                at = from.add(delta.scale((double) i / steps));
                tracker.update(now, at.x, at.y, at.z);
                events.addAll(tracker.drainEvents());
            }
        }
        List<Integer> passed = events.stream().filter(e -> e.type() == LapTracker.EventType.GATE).map(LapTracker.Event::gate).toList();
        long laps = events.stream().filter(e -> e.type() == LapTracker.EventType.LAP).count();
        boolean wrong = events.stream().anyMatch(e -> e.type() == LapTracker.EventType.MISSED || e.type() == LapTracker.EventType.WRONG_WAY);
        if (!passed.equals(List.of(1, 2, 3, 4)) || laps != 1 || wrong) {
            return StepResult.fail("Runde durch den Stapel falsch gezählt: Gates " + passed + " Runden " + laps + " Fehler " + wrong);
        }
        return StepResult.pass();
    }

    public static void discard(ServerLevel level, List<Integer> ids) {
        List<Entity> gates = new ArrayList<>();
        for (int id : ids) {
            Entity gate = level.getEntity(id);
            if (gate != null) {
                gates.add(gate);
            }
        }
        for (int i = gates.size() - 1; i >= 0; i--) {
            gates.get(i).discard();
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                discard(player.level(), IDS.get());
                CourseRegistry registry = CourseRegistry.get();
                if (registry != null) {
                    registry.deleteCourse(COURSE.get());
                }
            }));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean gone = minecraft.level != null && IDS.get().stream().allMatch(id -> minecraft.level.getEntity(id) == null);
        if (!gone || ClientCourses.get(COURSE.get()) != null) {
            return ctx.tick() > 150 ? StepResult.fail("gestapelte Gates oder Kurs noch auf dem Client") : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static final int LADDER_X = -14;
    private static final int LADDER_Z = -30;
    private static final int ARENA_OFFSET = 6;
    private static final long LEGS_BASE_NANOS = 900_000L * MS;
    private static final long ARENA_LAP = 41_870L * MS;
    private static final String ARENA_FRAME = "race_5";
    private static final String OK = "ok";
    private static final String PENDING = "pending";
    private static final double JOIN_SPEED = 6.0;
    private static final int JOIN_TIMEOUT_TICKS = 20 * 90;
    private static final AtomicReference<List<Integer>> LEGS_IDS = new AtomicReference<>(List.of());
    private static final AtomicInteger LEGS_COURSE = new AtomicInteger(GateEntity.NO_COURSE);
    private static final AtomicReference<String> SERVER_RESULT = new AtomicReference<>();
    private static final AtomicLong REMOVED_AT = new AtomicLong();
    private static final AtomicReference<Arena> ARENA = new AtomicReference<>();
    private static final int FRESH_X = -26;
    private static final int FRESH_Z = -30;
    private static final int FRESH_PHASES = 5;
    private static final int FRESH_MAX_TICKS = 5;
    private static final int FRESH_MAX_LAG = 1;
    private static final AtomicReference<List<Integer>> FRESH_IDS = new AtomicReference<>(List.of());
    private static final AtomicInteger FRESH_COURSE = new AtomicInteger(GateEntity.NO_COURSE);
    private static final AtomicInteger FRESH_TOP = new AtomicInteger(-1);

    private record Arena(BlockPos origin, BoundingBox box, String site, int[] values) {
    }

    private static double low(GateGeometry gate) {
        return gate.y() + gate.frame() * 0.5;
    }

    private static Vec3 point(GateGeometry gate, double ahead, double side, double y) {
        return new Vec3(gate.x() + gate.normalX() * ahead + gate.sideX() * side, y, gate.z() + gate.normalZ() * ahead + gate.sideZ() * side);
    }

    public static List<Vec3> legsPath(List<GateGeometry> gates) {
        List<Vec3> path = new ArrayList<>();
        for (int i = 0; i <= gates.size(); i++) {
            GateGeometry gate = gates.get(i % gates.size());
            double height = i % gates.size() == 0 ? low(gate) : gate.centerY();
            if (i > 0) {
                GateGeometry previous = gates.get(i - 1);
                double before = i - 1 == 0 ? low(previous) : previous.centerY();
                if (RaceLine.needsDetour(previous, gate)) {
                    double reach = Math.max(previous.width(), gate.width()) * 0.5 + 2.0;
                    Vec3 exit = point(previous, 2.0, reach, before);
                    path.add(exit);
                    path.add(new Vec3(exit.x, height, exit.z));
                    path.add(point(gate, -2.0, reach, height));
                }
            }
            path.add(point(gate, -2.0, 0.0, height));
            path.add(point(gate, 2.0, 0.0, height));
        }
        return path;
    }

    private static List<LapTracker.Event> fly(List<GateGeometry> gates, List<Vec3> path, GateOpenings openings) {
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(0);
        tracker.setCourse(LEGS_COURSE.get(), gates, 0L);
        tracker.setOpenings(openings);
        long now = 1_000L * MS;
        Vec3 at = point(gates.getFirst(), -4.0, 0.0, low(gates.getFirst()));
        tracker.update(now, at.x, at.y, at.z);
        List<LapTracker.Event> events = new ArrayList<>();
        for (Vec3 target : path) {
            Vec3 delta = target.subtract(at);
            int steps = Math.max(1, (int) Math.ceil(delta.length() / 0.3));
            Vec3 from = at;
            for (int i = 1; i <= steps; i++) {
                now += 30 * MS;
                at = from.add(delta.scale((double) i / steps));
                tracker.update(now, at.x, at.y, at.z);
                events.addAll(tracker.drainEvents());
            }
        }
        return events;
    }

    private static @Nullable GateEntity clientGate(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        List<Integer> ids = LEGS_IDS.get();
        if (minecraft.level == null || index >= ids.size()) {
            return null;
        }
        return minecraft.level.getEntity(ids.get(index)) instanceof GateEntity gate ? gate : null;
    }

    private static @Nullable GateEntity serverGate(ServerLevel level, int index) {
        List<Integer> ids = LEGS_IDS.get();
        return index < ids.size() && level.getEntity(ids.get(index)) instanceof GateEntity gate ? gate : null;
    }

    private static StepResult legsLapStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            SERVER_RESULT.set(null);
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                int x = player.getBlockX() + LADDER_X;
                int z = player.getBlockZ() + LADDER_Z;
                int ground = player.getBlockY();
                StackGateJoinCheck.Ladder ladder = StackGateJoinCheck.buildLadder(player, x, ground, z);
                GateEntity single = StackGateJoinCheck.place(player, GateSize.MEDIUM, x + DOUBLE_OFFSET, ground, z, 0.0F);
                CourseRegistry registry = CourseRegistry.get();
                if (registry == null || registry.appendGate(ladder.course(), single) != 2) {
                    throw new IllegalStateException("einzelnes Gate nicht als Gate 3 im Kurs");
                }
                LEGS_IDS.set(List.of(ladder.bottom().getId(), ladder.top().getId(), single.getId()));
                LEGS_COURSE.set(ladder.course());
            }));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (state.containsKey("server")) {
            String problem = SERVER_RESULT.get();
            return problem == null ? StepResult.pass() : StepResult.fail(problem);
        }
        CourseData data = ClientCourses.get(LEGS_COURSE.get());
        GateEntity bottom = clientGate(0);
        boolean numbered = true;
        for (int i = 0; i < 3; i++) {
            GateEntity gate = clientGate(i);
            numbered &= gate != null && gate.courseId() == LEGS_COURSE.get() && gate.gateIndex() == i;
        }
        if (data == null || data.gates().size() != 3 || bottom == null || bottom.base() != GateStacking.Base.LEGS || !numbered) {
            return ctx.tick() > 200 ? StepResult.fail("Leiter beim Client nicht bereit: Kurs " + data + ", unten "
                    + (bottom == null ? "-" : bottom.base()) + ", nummeriert " + numbered) : StepResult.pending();
        }
        List<GateGeometry> gates = data.geometry();
        for (GateGeometry gate : gates) {
            if (gate.legs()) {
                return StepResult.fail("Kursdaten tragen das Flag legs");
            }
        }
        if (!bottom.geometry().legs() || bottom.geometry().openingFloor() != 0.0) {
            return StepResult.fail("Entity-Geometrie unten beim Client ohne Beine: Öffnung ab " + bottom.geometry().openingFloor());
        }
        ClientGateOpenings openings = ClientGateOpenings.of(data.id(), gates);
        if (!openings.legs(0) || openings.legs(1) || openings.legs(2)) {
            return StepResult.fail("Anbieter meldet Beine falsch: " + openings.legs(0) + "/" + openings.legs(1) + "/" + openings.legs(2));
        }
        if (!(ClientRaceTracker.INSTANCE.tracker().openings() instanceof ClientGateOpenings)) {
            return StepResult.fail("Renn-Tracker des Clients ohne Anbieter: " + ClientRaceTracker.INSTANCE.tracker().openings());
        }
        List<Vec3> path = legsPath(gates);
        List<LapTracker.Event> plain = fly(gates, path, GateOpenings.NONE);
        if (plain.stream().anyMatch(e -> e.type() == LapTracker.EventType.START || e.type() == LapTracker.EventType.LAP)) {
            return StepResult.fail("Durchflug unten ohne Anbieter gezählt: " + plain);
        }
        int[] asked = {0};
        List<LapTracker.Event> events = fly(gates, path, index -> {
            asked[0]++;
            return openings.legs(index);
        });
        List<Integer> passed = events.stream().filter(e -> e.type() == LapTracker.EventType.GATE).map(LapTracker.Event::gate).toList();
        long starts = events.stream().filter(e -> e.type() == LapTracker.EventType.START).count();
        long laps = events.stream().filter(e -> e.type() == LapTracker.EventType.LAP).count();
        boolean wrong = events.stream().anyMatch(e -> e.type() == LapTracker.EventType.MISSED || e.type() == LapTracker.EventType.WRONG_WAY);
        if (starts != 1 || !passed.equals(List.of(1, 2)) || laps != 1 || wrong || asked[0] != 2) {
            return StepResult.fail("Runde unten durch das Gate auf Beinen falsch gezählt: Start " + starts + " Gates " + passed + " Runden " + laps
                    + " Fehler " + wrong + " Anbieter " + asked[0] + "x gefragt");
        }
        state.put("server", true);
        state.put(FUTURE, ctx.onServer(server -> SERVER_RESULT.set(serverLegs(server))));
        ctx.log("Client: Runde in halber Rahmenhöhe durch das untere Gate gezählt, Anbieter " + asked[0] + "x gefragt");
        return StepResult.pending();
    }

    private static RacePayloads.RaceReportC2S report(RacePayloads.ReportKind kind, int course, int lap, long lapNanos) {
        return new RacePayloads.RaceReportC2S(kind, course, lap, lapNanos, 0L);
    }

    private static void pass(ServerPlayer player, GateGeometry gate, double y, long nanos) {
        RaceServer.onDroneMoved(player, point(gate, -1.0, 0.0, y), point(gate, 1.0, 0.0, y), nanos);
    }

    private static @Nullable String serverLegs(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        GateEntity bottom = serverGate(level, 0);
        GateEntity top = serverGate(level, 1);
        GateEntity single = serverGate(level, 2);
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(LEGS_COURSE.get());
        if (bottom == null || top == null || single == null || course == null) {
            return "Gates oder Kurs fehlen auf dem Server";
        }
        if (bottom.base() != GateStacking.Base.LEGS || !bottom.geometry().legs() || bottom.geometry().openingFloor() != 0.0) {
            return "Server: unteres Gate " + bottom.base() + ", Öffnung ab " + bottom.geometry().openingFloor();
        }
        if (top.base() != GateStacking.Base.STACKED || top.geometry().legs() || single.base() != GateStacking.Base.BAR || single.geometry().legs()) {
            return "Server: oberes Gate " + top.base() + " oder einzelnes Gate " + single.base() + " mit Beinen";
        }
        List<GateGeometry> gates = course.toData().geometry();
        for (GateGeometry gate : gates) {
            if (gate.legs()) {
                return "Kursdaten auf dem Server tragen das Flag legs";
            }
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        drone.snapTo(bottom.getX(), bottom.getY() + 1.5, bottom.getZ() - 3.0, 0.0F, 0.0F);
        drone.setPilot(player);
        if (!level.addFreshEntity(drone)) {
            return "Drohne nicht gespawnt";
        }
        try {
            int id = course.id;
            long t = System.nanoTime() + LEGS_BASE_NANOS;
            pass(player, single.geometry(), low(single.geometry()), t);
            if (single.pulseLeft() != 0) {
                return "einzelnes Gate pulst beim Durchflug durch den unteren Balken";
            }
            long lap = Math.max(RaceServer.minimumLapNanos(course, player) * 3L, 6_000L * MS);
            t += 1_000L * MS;
            RaceServer.Verdict verdict = RaceServer.handle(report(RacePayloads.ReportKind.START, id, 0, 0L), player, t);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Start abgelehnt: " + verdict;
            }
            long start = t + 100L * MS;
            pass(player, gates.getFirst(), low(gates.getFirst()), start);
            if (bottom.pulseLeft() != bottom.signal().pulseTicks() || bottom.emitting() == null) {
                return "kein Redstone-Puls am Gate auf Beinen nach dem Durchflug unten: " + bottom.pulseLeft();
            }
            if (top.pulseLeft() != 0) {
                return "oberes Gate pulst beim Durchflug unten";
            }
            pass(player, gates.get(1), gates.get(1).centerY(), start + lap / 3L);
            pass(player, gates.get(2), gates.get(2).centerY(), start + lap * 2L / 3L);
            pass(player, gates.getFirst(), low(gates.getFirst()), start + lap);
            verdict = RaceServer.handle(report(RacePayloads.ReportKind.LAP, id, 1, lap), player, start + lap + 100L * MS);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Runde unten durch das Gate auf Beinen vom Server abgelehnt: " + verdict;
            }
            boolean listed = false;
            for (Leaderboard.Entry entry : registry.board(course).entries()) {
                listed |= entry.player().equals(player.getUUID()) && entry.nanos() == lap;
            }
            if (!listed) {
                return "Bestenliste ohne die Runde unten durch das Gate auf Beinen";
            }
            RaceServer.handle(report(RacePayloads.ReportKind.ABORT, id, 0, 0L), player, start + lap + 200L * MS);
            return null;
        } finally {
            drone.setPilot(null);
            drone.discard();
        }
    }

    private static StepResult singleFrameStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            SERVER_RESULT.set(null);
            state.put(FUTURE, ctx.onServer(server -> SERVER_RESULT.set(serverSingle(server))));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (SERVER_RESULT.get() != null) {
            return StepResult.fail(SERVER_RESULT.get());
        }
        GateEntity single = clientGate(2);
        GateEntity top = clientGate(1);
        CourseData data = ClientCourses.get(LEGS_COURSE.get());
        if (single == null || top == null || data == null || data.gates().size() != 3) {
            return StepResult.fail("einzelnes Gate oder Kurs fehlt beim Client");
        }
        if (single.base() != GateStacking.Base.BAR || single.geometry().legs() || single.geometry().openingFloor() != single.geometry().frame()) {
            return StepResult.fail("einzelnes Gate beim Client " + single.base() + ", Öffnung ab " + single.geometry().openingFloor());
        }
        if (top.geometry().legs() || ClientGateOpenings.of(data.id(), data.geometry()).legs(2)) {
            return StepResult.fail("Anbieter meldet Beine am einzelnen oder oberen Gate");
        }
        String problem = frameOnly(single.geometry());
        return problem == null ? StepResult.pass() : StepResult.fail("Client: " + problem);
    }

    private static @Nullable String frameOnly(GateGeometry gate) {
        double frame = gate.frame();
        if (gate.insideOpening(0.0, frame * 0.5) || gate.insideOpening(0.0, frame) || !gate.insideOpening(0.0, frame + 0.01)) {
            return "Öffnung des einzelnen Gates beginnt nicht bei der Rahmenstärke";
        }
        Vec3 a = point(gate, -1.0, 0.0, low(gate));
        Vec3 b = point(gate, 1.0, 0.0, low(gate));
        if (gate.crossing(a.x, a.y, a.z, b.x, b.y, b.z, true) != GateGeometry.NO_CROSSING) {
            return "Durchflug durch den unteren Balken zählt";
        }
        Vec3 c = point(gate, -1.0, 0.0, gate.y() + frame + 0.1);
        Vec3 d = point(gate, 1.0, 0.0, gate.y() + frame + 0.1);
        return gate.crossing(c.x, c.y, c.z, d.x, d.y, d.z, true) >= 0.0 ? null : "Durchflug knapp über dem Balken zählt nicht";
    }

    private static @Nullable String serverSingle(MinecraftServer server) {
        ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
        GateEntity single = serverGate(level, 2);
        GateEntity top = serverGate(level, 1);
        if (single == null || top == null) {
            return "einzelnes oder oberes Gate fehlt auf dem Server";
        }
        if (single.base() != GateStacking.Base.BAR || single.geometry().legs() || single.colliders().length != 4) {
            return "einzelnes Gate auf dem Server " + single.base() + " mit " + single.colliders().length + " Kollisionsboxen";
        }
        GateGeometry upper = top.geometry();
        Vec3 a = point(upper, -1.0, 0.0, low(upper));
        Vec3 b = point(upper, 1.0, 0.0, low(upper));
        if (upper.legs() || upper.crossing(a.x, a.y, a.z, b.x, b.y, b.z, true) != GateGeometry.NO_CROSSING) {
            return "gestapeltes Gate zählt im Balken des Gates darunter";
        }
        String problem = frameOnly(single.geometry());
        return problem == null ? null : "Server: " + problem;
    }

    private static StepResult removeTopStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            AtomicLong serverTicks = new AtomicLong(-1L);
            state.put("serverTicks", serverTicks);
            state.put(FUTURE, ctx.onServer(server -> {
                ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
                GateEntity top = serverGate(level, 1);
                if (top == null) {
                    throw new IllegalStateException("oberes Gate fehlt");
                }
                REMOVED_AT.set(level.getGameTime());
                top.discard();
            }));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        AtomicLong serverTicks = (AtomicLong) state.get("serverTicks");
        if (serverTicks.get() < 0L) {
            state.put(FUTURE, ctx.onServer(server -> {
                ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
                GateEntity bottom = serverGate(level, 0);
                if (bottom != null && bottom.base() == GateStacking.Base.BAR && !bottom.geometry().legs()) {
                    serverTicks.set(level.getGameTime() - REMOVED_AT.get());
                }
            }));
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getEntity(LEGS_IDS.get().get(1)) == null && !state.containsKey("gone")) {
            state.put("gone", ctx.tick());
        }
        GateEntity bottom = clientGate(0);
        CourseData data = ClientCourses.get(LEGS_COURSE.get());
        boolean frame = bottom != null && bottom.base() == GateStacking.Base.BAR && !bottom.geometry().legs()
                && bottom.geometry().openingFloor() == bottom.geometry().frame() && (data == null || !ClientGateOpenings.of(data.id(), data.geometry()).legs(0));
        if (state.containsKey("gone") && frame && !state.containsKey("client")) {
            state.put("client", ctx.tick() - (int) state.get("gone"));
        }
        if (state.containsKey("client") && serverTicks.get() >= 0L) {
            int client = (int) state.get("client");
            if (client > 10 || serverTicks.get() > 10L) {
                return StepResult.fail("unten erst nach " + serverTicks.get() + " Server- und " + client + " Client-Ticks wieder Rahmen");
            }
            ctx.log("unten wieder Rahmen nach " + serverTicks.get() + " Server-Ticks und " + client + " Client-Ticks");
            return StepResult.pass();
        }
        return ctx.tick() > 150 ? StepResult.fail("unten nach dem Abbau kein Rahmen: Client " + (bottom == null ? "-" : bottom.base())
                + ", Server-Ticks " + serverTicks.get()) : StepResult.pending();
    }

    private static StepResult legsCleanupStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                discard(player.level(), LEGS_IDS.get());
                CourseRegistry registry = CourseRegistry.get();
                if (registry != null) {
                    registry.deleteCourse(LEGS_COURSE.get());
                }
            }));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean gone = minecraft.level != null && LEGS_IDS.get().stream().allMatch(id -> minecraft.level.getEntity(id) == null);
        if (!gone || ClientCourses.get(LEGS_COURSE.get()) != null) {
            return ctx.tick() > 150 ? StepResult.fail("Leiter oder Kurs noch auf dem Client") : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static @Nullable GateEntity freshClient(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        List<Integer> ids = FRESH_IDS.get();
        if (minecraft.level == null || index >= ids.size()) {
            return null;
        }
        return minecraft.level.getEntity(ids.get(index)) instanceof GateEntity gate ? gate : null;
    }

    private static void freshDiscard(MinecraftServer server) {
        ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
        Entity top = level.getEntity(FRESH_TOP.get());
        if (top != null) {
            top.discard();
        }
        discard(level, FRESH_IDS.get());
        CourseRegistry registry = CourseRegistry.get();
        if (registry != null) {
            registry.deleteCourse(FRESH_COURSE.get());
        }
    }

    private static StepResult freshFail(SelfTest.StepContext ctx, String problem) {
        ctx.onServer(GateStackSelfTests::freshDiscard);
        return StepResult.fail(problem);
    }

    private static @Nullable StepResult freshFuture(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return freshFail(ctx, "Server-Aktion fehlgeschlagen: " + future.exceptionNow().getMessage());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private static void freshPhase(Map<String, Object> state, int phase, int tick) {
        state.put("phase", phase);
        state.put("since", tick);
    }

    private static void freshStack(MinecraftServer server, int round) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        if (!(level.getEntity(FRESH_IDS.get().getFirst()) instanceof GateEntity bottom)) {
            SERVER_RESULT.set("unteres Gate fehlt auf dem Server");
            return;
        }
        GateEntity top = stacked(player, bottom, GateSize.MEDIUM, 0.0F);
        FRESH_TOP.set(top.getId());
        if (bottom.base() != GateStacking.Base.LEGS || !bottom.geometry().legs() || top.base() != GateStacking.Base.STACKED) {
            SERVER_RESULT.set("Server: Runde " + round + " im Tick des Stapelns unten " + bottom.base() + ", oben " + top.base());
        }
    }

    @SuppressWarnings("unchecked")
    private static StepResult freshStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            SERVER_RESULT.set(null);
            FRESH_IDS.set(List.of());
            FRESH_TOP.set(-1);
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                int x = player.getBlockX() + FRESH_X;
                int z = player.getBlockZ() + FRESH_Z;
                int ground = player.getBlockY();
                GateEntity bottom = StackGateJoinCheck.place(player, GateSize.MEDIUM, x, ground, z, 0.0F);
                GateEntity other = StackGateJoinCheck.place(player, GateSize.MEDIUM, x, ground, z + DOUBLE_OFFSET, 0.0F);
                ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
                CourseToolItem.useOnGate(tool, player, bottom);
                CourseToolItem.useOnGate(tool, player, other);
                FRESH_IDS.set(List.of(bottom.getId(), other.getId()));
                FRESH_COURSE.set(CourseToolItem.courseOf(tool));
                if (bottom.gateIndex() != 0 || other.gateIndex() != 1) {
                    throw new IllegalStateException("Gates nicht als Kurs angelegt");
                }
            }));
            state.put("rounds", new ArrayList<String>());
            state.put("late", new ArrayList<String>());
            state.put("round", 0);
            freshPhase(state, 0, 0);
            return StepResult.pending();
        }
        if (SERVER_RESULT.get() != null) {
            return freshFail(ctx, SERVER_RESULT.get());
        }
        int phase = (int) state.get("phase");
        int since = ctx.tick() - (int) state.get("since");
        int round = (int) state.get("round");
        List<String> rounds = (List<String>) state.get("rounds");
        List<String> late = (List<String>) state.get("late");
        if (phase == 3) {
            StepResult waiting = freshFuture(ctx);
            if (waiting != null) {
                return waiting;
            }
            Minecraft minecraft = Minecraft.getInstance();
            boolean gone = minecraft.level != null && FRESH_IDS.get().stream().allMatch(id -> minecraft.level.getEntity(id) == null)
                    && minecraft.level.getEntity(FRESH_TOP.get()) == null;
            if (!gone || ClientCourses.get(FRESH_COURSE.get()) != null) {
                return since > 150 ? StepResult.fail("frisch gestapelte Gates oder Kurs noch auf dem Client") : StepResult.pending();
            }
            rounds.forEach(ctx::log);
            if (!late.isEmpty()) {
                return StepResult.fail("frisch gestapelt: Beine beim Client zu spät (höchstens " + FRESH_MAX_LAG + " Tick nach dem neuen Gate und "
                        + FRESH_MAX_TICKS + " Ticks ab dem Stapeln): " + String.join("; ", late));
            }
            ctx.log("Server: Beine im Tick des Stapelns in allen " + FRESH_PHASES + " Runden");
            return StepResult.pass();
        }
        GateEntity bottom = freshClient(0);
        CourseData data = ClientCourses.get(FRESH_COURSE.get());
        if (bottom == null || data == null || data.gates().size() != 2 || bottom.courseId() != FRESH_COURSE.get() || !bottom.hasPass(0)) {
            return since > 200 ? freshFail(ctx, "Gate oder Kurs beim Client nicht bereit (Runde " + (round + 1) + ")") : StepResult.pending();
        }
        ClientGateOpenings openings = ClientGateOpenings.of(data.id(), data.geometry());
        GateEntity above = bottom.gateAbove();
        boolean legs = bottom.base() == GateStacking.Base.LEGS && bottom.geometry().legs() && openings.legs(0);
        switch (phase) {
            case 0 -> {
                StepResult waiting = freshFuture(ctx);
                if (waiting != null) {
                    return waiting;
                }
                boolean frame = above == null && bottom.base() == GateStacking.Base.BAR && !openings.legs(0);
                if (!frame || (bottom.tickCount + bottom.getId()) % FRESH_PHASES != round % FRESH_PHASES) {
                    return since > 100 ? freshFail(ctx, "vor Runde " + (round + 1) + " unten kein Rahmen: " + bottom.base()) : StepResult.pending();
                }
                state.put("request", ctx.tick());
                state.remove("seen");
                state.remove("legs");
                int number = round + 1;
                state.put(FUTURE, ctx.onServer(server -> freshStack(server, number)));
                freshPhase(state, 1, ctx.tick());
                return StepResult.pending();
            }
            case 1 -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    return freshFail(ctx, "Stapeln fehlgeschlagen: " + future.exceptionNow().getMessage());
                }
                if (above != null && !state.containsKey("seen")) {
                    state.put("seen", ctx.tick());
                }
                if (above != null && legs && !state.containsKey("legs")) {
                    state.put("legs", ctx.tick());
                }
                if (!state.containsKey("legs") || !future.isDone()) {
                    return since > 100 ? freshFail(ctx, "Runde " + (round + 1) + ": Beine beim Client nicht gemeldet, neues Gate "
                            + (state.containsKey("seen") ? "da" : "fehlt") + ", unten " + bottom.base()) : StepResult.pending();
                }
                int request = (int) state.get("request");
                int seen = (int) state.get("seen") - request;
                int at = (int) state.get("legs") - request;
                String line = "Runde " + (round + 1) + " (Phase " + round % FRESH_PHASES + "): neues Gate beim Client nach " + seen
                        + ", Beine nach " + at + " Ticks ab dem Stapeln";
                rounds.add(line);
                if (at - seen > FRESH_MAX_LAG || at > FRESH_MAX_TICKS) {
                    late.add(line);
                }
                state.put(FUTURE, ctx.onServer(server -> {
                    Entity top = server.getPlayerList().getPlayers().getFirst().level().getEntity(FRESH_TOP.get());
                    if (top != null) {
                        top.discard();
                    }
                }));
                freshPhase(state, 2, ctx.tick());
                return StepResult.pending();
            }
            default -> {
                StepResult waiting = freshFuture(ctx);
                if (waiting != null) {
                    return waiting;
                }
                if (above != null) {
                    return since > 100 ? freshFail(ctx, "Runde " + (round + 1) + ": oberes Gate beim Client nicht abgebaut") : StepResult.pending();
                }
                state.put("round", round + 1);
                if (round + 1 < FRESH_PHASES) {
                    freshPhase(state, 0, ctx.tick());
                    return StepResult.pending();
                }
                state.put(FUTURE, ctx.onServer(GateStackSelfTests::freshDiscard));
                freshPhase(state, 3, ctx.tick());
                return StepResult.pending();
            }
        }
    }

    private static Arena placeArena(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        StructureTemplate template = server.getStructureTemplateManager().get(Constants.id("world_course/drone_arena")).orElseThrow();
        StructurePlaceSettings settings = WorldCoursePiece.settings(Rotation.NONE);
        BoundingBox local = template.getBoundingBox(settings, BlockPos.ZERO);
        int cornerX = player.getBlockX() + ARENA_OFFSET;
        int cornerZ = player.getBlockZ() + ARENA_OFFSET;
        int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, cornerX, cornerZ) - 1;
        BlockPos origin = new BlockPos(cornerX - local.minX(), ground, cornerZ - local.minZ());
        BoundingBox box = template.getBoundingBox(settings, origin);
        for (GateEntity old : level.getEntitiesOfClass(GateEntity.class, AABB.of(box).inflate(2.0))) {
            old.discard();
        }
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        return new Arena(origin, box, origin.getX() + "," + origin.getY() + "," + origin.getZ(), new int[]{GateEntity.NO_COURSE, 0, 0, -1});
    }

    private static CourseRegistry.@Nullable Course arenaCourse(ServerLevel level, Arena arena) {
        CourseRegistry registry = CourseRegistry.get();
        return registry == null ? null : registry.worldCourse(CourseRegistry.dimensionOf(level), arena.site());
    }

    private static String recordArena(MinecraftServer server, Arena arena) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = arenaCourse(level, arena);
        if (registry == null || course == null || course.gates.size() < 10) {
            return PENDING;
        }
        List<GateEntity> gates = new ArrayList<>();
        int legs = -1;
        int stacked = 0;
        for (int i = 0; i < course.gates.size(); i++) {
            if (!(level.getEntity(UUID.fromString(course.gates.get(i).uuid)) instanceof GateEntity gate)) {
                return PENDING;
            }
            GateStacking.Base base = gate.base();
            if (base == GateStacking.Base.LEGS) {
                legs = legs < 0 ? i : -2;
            } else if (base == GateStacking.Base.STACKED) {
                stacked++;
            }
            if (gate.geometry().legs() != (base == GateStacking.Base.LEGS)) {
                return "Gate " + i + " (" + base + ") mit falschem Flag legs";
            }
            gates.add(gate);
        }
        if (legs < 0 || stacked != 2) {
            return PENDING;
        }
        CourseData data = course.toData();
        for (GateGeometry gate : data.geometry()) {
            if (gate.legs()) {
                return "Kursdaten der Drohnen-Arena tragen das Flag legs";
            }
        }
        int before = data.layoutHash();
        if (registry.submitLap(course, player, ARENA_FRAME, ARENA_LAP) != Leaderboard.Outcome.COURSE_RECORD) {
            return "Testrunde in der Drohnen-Arena kein Kursrekord";
        }
        double[] positions = new double[6];
        float[] rotations = {0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F};
        GhostTrack ghost = new GhostTrack(GhostTrack.INTERVAL_NANOS, positions, rotations, ARENA_LAP, ARENA_FRAME, before);
        registry.ghosts().put(course.id, ARENA_FRAME, before, player.getGameProfile().name(), ARENA_LAP, GhostCodec.encode(ghost));
        arena.values()[0] = course.id;
        arena.values()[1] = before;
        arena.values()[2] = course.layout;
        arena.values()[3] = legs;
        for (GateEntity gate : gates) {
            gate.refreshColumn();
            registry.reconcile(gate);
        }
        return OK;
    }

    private static String checkArena(MinecraftServer server, Arena arena) {
        ServerLevel level = server.overworld();
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(arena.values()[0]);
        if (course == null) {
            return "Weltkurs der Drohnen-Arena verschwunden";
        }
        int before = arena.values()[1];
        if (course.layout != arena.values()[2]) {
            return "Bestenliste archiviert: Layout " + arena.values()[2] + " → " + course.layout;
        }
        if (course.toData().layoutHash() != before) {
            return "Layout-Hash geändert: " + before + " → " + course.toData().layoutHash();
        }
        List<Leaderboard.Entry> entries = registry.board(course).entries();
        if (entries.isEmpty() || entries.getFirst().nanos() != ARENA_LAP) {
            return "Rekord verloren: " + entries;
        }
        RecordGhosts.Stored stored = registry.ghosts().get(course.id, ARENA_FRAME);
        if (stored == null || stored.layout() != before) {
            return "Rekord-Geist verloren oder mit anderem Layout: " + stored;
        }
        GateEntity bottom = level.getEntity(UUID.fromString(course.gates.get(arena.values()[3]).uuid)) instanceof GateEntity gate ? gate : null;
        if (bottom == null || bottom.base() != GateStacking.Base.LEGS || !bottom.geometry().legs()) {
            return "unterstes Gate der Leiter nicht auf Beinen: " + (bottom == null ? "-" : bottom.base());
        }
        return OK;
    }

    private static void cleanupArena(MinecraftServer server, @Nullable Arena arena) {
        if (arena == null) {
            return;
        }
        ServerLevel level = server.overworld();
        BoundingBox box = arena.box();
        for (GateEntity gate : level.getEntitiesOfClass(GateEntity.class, AABB.of(box).inflate(2.0))) {
            gate.discard();
        }
        CourseRegistry.Course course = arenaCourse(level, arena);
        CourseRegistry registry = CourseRegistry.get();
        if (course != null && registry != null) {
            registry.deleteCourse(course.id);
        }
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.minY() + 2, box.maxZ())) {
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                chest.setLootTable(null);
            }
        }
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            level.setBlock(pos, pos.getY() == box.minY() ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), flags);
        }
        for (Entity item : level.getEntitiesOfClass(ItemEntity.class, AABB.of(box).inflate(2.0))) {
            item.discard();
        }
    }

    private static StepResult arenaFail(SelfTest.StepContext ctx, String problem) {
        Arena arena = ARENA.getAndSet(null);
        ctx.onServer(server -> cleanupArena(server, arena));
        return StepResult.fail(problem);
    }

    @SuppressWarnings("unchecked")
    private static StepResult arenaStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        AtomicReference<String> status = (AtomicReference<String>) state.computeIfAbsent("status", key -> new AtomicReference<>(PENDING));
        if (ctx.tick() == 0) {
            state.put("phase", "record");
            state.put(FUTURE, ctx.onServer(server -> ARENA.set(placeArena(server))));
            return StepResult.pending();
        }
        CompletableFuture<?> pending = (CompletableFuture<?>) state.get(FUTURE);
        if (pending.isCompletedExceptionally()) {
            return arenaFail(ctx, "Server-Aktion fehlgeschlagen: " + pending.exceptionNow().getMessage());
        }
        if (!pending.isDone()) {
            return StepResult.pending();
        }
        String phase = (String) state.get("phase");
        if (phase.equals("cleanup")) {
            return StepResult.pass();
        }
        Arena arena = ARENA.get();
        if (arena == null) {
            return StepResult.fail("Drohnen-Arena nicht platziert");
        }
        String current = status.get();
        if (!current.equals(PENDING) && !current.equals(OK)) {
            return arenaFail(ctx, current);
        }
        switch (phase) {
            case "record" -> {
                if (current.equals(OK)) {
                    state.put("phase", "settle");
                    state.put("since", ctx.tick());
                    status.set(PENDING);
                } else if (ctx.tick() > 500) {
                    return arenaFail(ctx, "Drohnen-Arena nicht als Weltkurs mit Leiter übernommen");
                } else if (ctx.tick() % 10 == 0) {
                    state.put(FUTURE, ctx.onServer(server -> status.set(recordArena(server, arena))));
                }
            }
            case "settle" -> {
                if (ctx.tick() - (int) state.get("since") < 30) {
                    return StepResult.pending();
                }
                state.put("phase", "check");
                state.put(FUTURE, ctx.onServer(server -> status.set(checkArena(server, arena))));
            }
            case "check" -> {
                if (!current.equals(OK)) {
                    return StepResult.pending();
                }
                int id = arena.values()[0];
                CourseData data = ClientCourses.get(id);
                int legs = arena.values()[3];
                String problem = null;
                if (data == null || data.layoutHash() != arena.values()[1]) {
                    problem = "Client kennt den Weltkurs nicht mit gleichem Layout-Hash: " + (data == null ? "-" : data.layoutHash());
                } else {
                    ClientGateOpenings openings = ClientGateOpenings.of(id, data.geometry());
                    for (int i = 0; i < data.gates().size() && problem == null; i++) {
                        if (openings.legs(i) != (i == legs)) {
                            problem = "Anbieter beim Client meldet für Gate " + (i + 1) + " Beine " + openings.legs(i);
                        }
                    }
                }
                if (problem != null) {
                    return ctx.tick() - (int) state.get("since") > 300 ? arenaFail(ctx, problem) : StepResult.pending();
                }
                ctx.log("Drohnen-Arena: Layout-Hash " + arena.values()[1] + ", Rekord und Geist erhalten, Gate " + (legs + 1) + " auf Beinen");
                state.put("phase", "cleanup");
                state.put(FUTURE, ctx.onServer(server -> cleanupArena(server, ARENA.getAndSet(null))));
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
        return StepResult.pending();
    }

    public static @Nullable String joinProbe(Map<String, Object> state, int tick, Minecraft minecraft) {
        int start = (int) state.computeIfAbsent("stackgateStart", key -> tick);
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.isFpv() ? session.drone() : null;
        if (!state.containsKey("stackgatePath")) {
            CourseData ladder = drone == null ? null : ladderNear(drone.position());
            if (ladder == null) {
                state.remove("stackgateReady");
                return tick - start > JOIN_TIMEOUT_TICKS ? "stackgate client=no_ladder\nstackgate fpv=" + session.isFpv() + "\n" : null;
            }
            int ready = (int) state.computeIfAbsent("stackgateReady", key -> tick);
            if (tick - ready < 20) {
                return null;
            }
            List<GateGeometry> gates = ClientCourses.geometry(ladder);
            List<Vec3> path = new ArrayList<>();
            path.add(new Vec3(drone.getX(), drone.getY() + drone.getBbHeight() * 0.5, drone.getZ()));
            if (JoinTestEnv.stackGateTurned()) {
                for (double[] at : StackTurnRoute.lap(gates, ClientGateOpenings.of(ladder.id(), gates))) {
                    path.add(new Vec3(at[0], at[1], at[2]));
                }
                GateEntity upper = ClientGateOpenings.find(ladder.id(), 1, gates.get(1));
                state.put("stackgateUpper", "stackgate client_upper=" + (upper == null ? "missing" : upper.base()) + "\nstackgate client_turn="
                        + GateTilt.wrap(gates.get(1).heading() - gates.getFirst().heading()) + "\n");
            } else {
                path.add(point(gates.getFirst(), -3.0, 0.0, low(gates.getFirst())));
                path.addAll(legsPath(gates));
                path.add(point(gates.getFirst(), 4.0, 0.0, gates.getFirst().centerY()));
            }
            state.put("stackgateCourse", ladder.id());
            state.put("stackgateLegs", ClientGateOpenings.of(ladder.id(), gates).legs(0));
            state.put("stackgatePath", path);
            state.put("stackgateDistance", 0.0);
            return null;
        }
        if (drone == null) {
            return "stackgate client=session_lost\n";
        }
        @SuppressWarnings("unchecked")
        List<Vec3> path = (List<Vec3>) state.get("stackgatePath");
        double distance = (double) state.get("stackgateDistance") + JOIN_SPEED / 20.0;
        state.put("stackgateDistance", distance);
        Vec3[] pose = along(path, distance);
        TrainingSelfTests.teleport(pose[0].x, pose[0].y - drone.getBbHeight() * 0.5, pose[0].z, 0.0, pose[1].x * JOIN_SPEED, pose[1].y * JOIN_SPEED,
                pose[1].z * JOIN_SPEED);
        if (pose[1].lengthSqr() > 0.0) {
            return null;
        }
        int flown = (int) state.computeIfAbsent("stackgateFlown", key -> tick);
        int course = (int) state.get("stackgateCourse");
        LapTracker tracker = ClientRaceTracker.INSTANCE.tracker();
        long lap = tracker.courseId() == course ? tracker.lastLapNanos() : LapTracker.NONE;
        String head = "stackgate client_legs=" + state.get("stackgateLegs") + "\n" + state.getOrDefault("stackgateUpper", "");
        if (lap <= 0L) {
            return tick - flown > 60 ? head + "stackgate client=missed phase=" + tracker.phase() + " course=" + tracker.courseId() + "\n" : null;
        }
        String counted = head + "stackgate client=counted\n" + String.format(Locale.ROOT, "stackgate lap_ms=%.1f%n", lap / 1.0E6);
        if ((tick - flown) % 20 == 0) {
            ClientLeaderboards.request(course);
        }
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(course);
        String name = minecraft.player == null ? "" : minecraft.player.getGameProfile().name();
        if (board != null) {
            for (RacePayloads.BoardRow row : board.rows()) {
                if (row.name().equals(name) && Math.abs(row.nanos() - lap) <= MS) {
                    return counted + "stackgate server=accepted\nstackgate server_assist=" + row.assist() + "\n";
                }
            }
        }
        return tick - flown > 20 * 30 ? counted + "stackgate server=missing\n" : null;
    }

    private static @Nullable CourseData ladderNear(Vec3 position) {
        for (CourseData course : ClientCourses.all()) {
            if (course.gates().size() != 2) {
                continue;
            }
            List<GateGeometry> gates = ClientCourses.geometry(course);
            GateGeometry bottom = gates.getFirst();
            if (position.distanceToSqr(bottom.x(), bottom.y(), bottom.z()) < 48.0 * 48.0 && ClientGateOpenings.of(course.id(), gates).legs(0)) {
                return course;
            }
        }
        return null;
    }

    private static Vec3[] along(List<Vec3> path, double distance) {
        double left = distance;
        for (int i = 1; i < path.size(); i++) {
            Vec3 from = path.get(i - 1);
            Vec3 to = path.get(i);
            double length = from.distanceTo(to);
            if (left <= length && length > 1.0E-9) {
                Vec3 direction = to.subtract(from).scale(1.0 / length);
                return new Vec3[]{from.add(direction.scale(left)), direction};
            }
            left -= length;
        }
        return new Vec3[]{path.getLast(), Vec3.ZERO};
    }
}
