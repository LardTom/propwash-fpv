package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RaceLine;
import de.lardtom.propwash.race.RaceModule;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
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
}
