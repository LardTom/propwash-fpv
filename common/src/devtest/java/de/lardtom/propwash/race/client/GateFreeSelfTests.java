package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.OrientedBox;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateItem;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.RaceModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

public final class GateFreeSelfTests {
    public static final String DIAGONAL = "diagonal";
    public static final String DIAGONAL_BACK = "diagonal_back";
    public static final String LEANING = "leaning";
    public static final String DIAMOND = "diamond";
    public static final String DIVE = "tilted_dive";
    public static final String HANGING = "tilted_hanging";
    public static final String TINY = "tiny";
    public static final String WIDE = "wide";
    public static final String TALL = "tall";
    public static final String HUGE = "huge";
    public static final int FLOOR_X0 = -4;
    public static final int FLOOR_X1 = 48;
    public static final int FLOOR_Z0 = -6;
    public static final int FLOOR_Z1 = 42;
    public static final int WALL_Z = 24;
    public static final int WALL_X1 = 12;
    public static final int WALL_HEIGHT = 11;
    public static final int CEILING_Y = 12;
    public static final int CEILING_X0 = 30;
    public static final int CEILING_X1 = 46;
    public static final int CEILING_Z1 = 16;
    private static final int OFFSET_X = 64;
    private static final int OFFSET_Z = 72;
    private static final int SHOT_OFFSET_X = -30;
    private static final int SMALL_GUI_WIDTH = 480;
    private static final int SMALL_GUI_HEIGHT = 270;
    private static final int SHOT_OFFSET_Z = -260;
    private static final double VIEW_X = -2.5;
    private static final double VIEW_Z = 20.5;
    private static final String FUTURE = "future";
    private static final String PLACING = "placing";
    private static final double EYE_HEIGHT = 1.62;
    private static final AtomicReference<Rig> RIG = new AtomicReference<>();
    private static final AtomicReference<Rig> SHOT_RIG = new AtomicReference<>();
    private static final AtomicReference<String> RESULT = new AtomicReference<>();
    private static final AtomicReference<Vec3> HOME = new AtomicReference<>();
    private static final AtomicReference<Vec3> SHOT_HOME = new AtomicReference<>();
    private static final Map<BlockPos, Map<BlockPos, BlockState>> FLOORS = new ConcurrentHashMap<>();

    public record Plan(String name, GateSize size, GateMount mount, int dx, int dy, int dz, Direction face, float playerYaw, float heading, float pitch,
                       float roll, double width, double height) {
        GateMountSelfTests.Spec spec() {
            return new GateMountSelfTests.Spec(this.name, this.size, this.mount, 0, this.dx, this.dy, this.dz, this.face, this.playerYaw);
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
            new Plan(DIAGONAL, GateSize.MEDIUM, GateMount.STANDING, 4, -1, 4, Direction.UP, 0.0F, 45.0F, 0.0F, 0.0F, 3.0, 3.0),
            new Plan(DIAGONAL_BACK, GateSize.MEDIUM, GateMount.STANDING, 9, -1, 9, Direction.UP, 0.0F, 315.0F, 0.0F, 0.0F, 3.0, 3.0),
            new Plan(LEANING, GateSize.MEDIUM, GateMount.STANDING, 16, -1, 5, Direction.UP, 0.0F, 0.0F, 25.0F, 0.0F, 3.0, 3.0),
            new Plan(DIAMOND, GateSize.MEDIUM, GateMount.STANDING, 23, -1, 5, Direction.UP, 0.0F, 0.0F, 0.0F, 45.0F, 3.0, 3.0),
            new Plan(DIVE, GateSize.MEDIUM, GateMount.WALL_DIVE, 8, 6, WALL_Z, Direction.NORTH, 0.0F, 180.0F, 30.0F, 0.0F, 3.0, 3.0),
            new Plan(HANGING, GateSize.MEDIUM, GateMount.HANGING, 37, CEILING_Y, 8, Direction.DOWN, 0.0F, 0.0F, 0.0F, 20.0F, 3.0, 3.0),
            new Plan(TINY, GateSize.SMALL, GateMount.STANDING, 6, -1, 34, Direction.UP, 0.0F, 0.0F, 0.0F, 0.0F, 1.0, 1.0),
            new Plan(WIDE, GateSize.MEDIUM, GateMount.STANDING, 13, -1, 34, Direction.UP, 0.0F, 0.0F, 0.0F, 0.0F, 8.0, 2.0),
            new Plan(TALL, GateSize.LARGE, GateMount.STANDING, 22, -1, 34, Direction.UP, 0.0F, 0.0F, 0.0F, 0.0F, 3.0, 9.5),
            new Plan(HUGE, GateSize.LARGE, GateMount.STANDING, 34, -1, 34, Direction.UP, 0.0F, 0.0F, 0.0F, 0.0F, 12.0, 12.0));

    private GateFreeSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("race: Gates frei drehen und Größe ändern", 300, GateFreeSelfTests::editStep);
        SelfTest.clientStep("race: frei gedrehte Gates beim Client", 200, GateFreeSelfTests::clientStep);
        SelfTest.clientStep("race: Zielen auf schräge und große Gates", 40, GateFreeSelfTests::pickStep);
        SelfTest.clientStep("race: Drohne an schrägen Gates", 200, GateFreeSelfTests::droneStep);
        SelfTest.clientStep("race: Gate einstellen im Spiel mit Mausrad, Laufen, freier Maus, Enter und Esc", 600, GateFreeSelfTests::screenStep);
        SelfTest.clientStep("race: Drehung und Größe im Entity speichern und laden", 200, GateFreeSelfTests::persistenceStep);
        SelfTest.clientStep("race: frei gedrehte Gates aufräumen", 200, GateFreeSelfTests::cleanupStep);
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

    public static void buildWorld(ServerLevel level, BlockPos o) {
        for (int cx = (o.getX() + FLOOR_X0 - 2) >> 4; cx <= (o.getX() + FLOOR_X1 + 2) >> 4; cx++) {
            for (int cz = (o.getZ() + FLOOR_Z0 - 2) >> 4; cz <= (o.getZ() + FLOOR_Z1 + 2) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        fill(level, o.offset(FLOOR_X0, 0, FLOOR_Z0), o.offset(FLOOR_X1, CEILING_Y + 14, FLOOR_Z1), Blocks.AIR.defaultBlockState());
        Map<BlockPos, BlockState> floor = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(o.offset(FLOOR_X0, -3, FLOOR_Z0), o.offset(FLOOR_X1, -1, FLOOR_Z1))) {
            floor.put(pos.immutable(), level.getBlockState(pos));
        }
        FLOORS.putIfAbsent(o.immutable(), floor);
        fill(level, o.offset(FLOOR_X0, -3, FLOOR_Z0), o.offset(FLOOR_X1, -1, FLOOR_Z1), Blocks.SMOOTH_STONE.defaultBlockState());
        fill(level, o.offset(0, 0, WALL_Z), o.offset(WALL_X1, WALL_HEIGHT - 1, WALL_Z), Blocks.STONE_BRICKS.defaultBlockState());
        fill(level, o.offset(CEILING_X0, CEILING_Y, 0), o.offset(CEILING_X1, CEILING_Y, CEILING_Z1), Blocks.STONE_BRICKS.defaultBlockState());
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

    private static Vec3 beside(GateGeometry gate, double floorY) {
        double nx = gate.normalX();
        double nz = gate.normalZ();
        double length = Math.sqrt(nx * nx + nz * nz);
        if (length < 0.2) {
            nx = gate.upX();
            nz = gate.upZ();
            length = Math.sqrt(nx * nx + nz * nz);
        }
        if (length < 0.2) {
            nx = 1.0;
            nz = 0.0;
            length = 1.0;
        }
        double sign = gate.tilt().pitch() > 0.0F ? 1.0 : -1.0;
        double y = gate.mount() == GateMount.HANGING ? Math.max(floorY, gate.centerY() - EYE_HEIGHT) : floorY;
        return new Vec3(gate.centerX() + sign * nx / length * 2.6, y, gate.centerZ() + sign * nz / length * 2.6);
    }

    public static GateMounting.Result edit(ServerPlayer player, GateEntity gate, float heading, float pitch, float roll, double width, double height,
                                           double floorY) {
        Vec3 home = player.position();
        ServerLevel level = player.level();
        GateGeometry next = GateMounting.preview(gate, heading, pitch, roll, width, height);
        Vec3 stand = beside(next, floorY);
        try {
            player.teleportTo(level, stand.x, stand.y, stand.z, Set.of(), player.getYRot(), player.getXRot(), true);
            return GateMounting.edit(player, gate, heading, pitch, roll, width, height);
        } finally {
            player.teleportTo(level, home.x, home.y, home.z, Set.of(), player.getYRot(), player.getXRot(), true);
        }
    }

    public static Rig place(ServerPlayer player, BlockPos origin, boolean course) {
        ServerLevel level = player.level();
        buildWorld(level, origin);
        Map<String, Placed> placed = new LinkedHashMap<>();
        AABB area = new AABB(Vec3.atLowerCornerOf(origin.offset(FLOOR_X0 - 8, -4, FLOOR_Z0 - 8)),
                Vec3.atLowerCornerOf(origin.offset(FLOOR_X1 + 8, CEILING_Y + 16, FLOOR_Z1 + 8)));
        try {
            for (Plan plan : PLANS) {
                List<GateEntity> before = level.getEntitiesOfClass(GateEntity.class, area);
                if (!GateMountSelfTests.use(player, plan.spec(), origin).consumesAction()) {
                    throw new IllegalStateException(plan.name() + " nicht gesetzt");
                }
                List<GateEntity> after = level.getEntitiesOfClass(GateEntity.class, area, gate -> !before.contains(gate) && !gate.isRemoved());
                if (after.size() != 1) {
                    throw new IllegalStateException(plan.name() + ": " + after.size() + " neue Gates");
                }
                GateEntity gate = after.getFirst();
                placed.put(plan.name(), new Placed(plan.name(), gate.getId(), gate.geometry()));
                GateGeometry want = GateMounting.preview(gate, plan.heading(), plan.pitch(), plan.roll(), plan.width(), plan.height());
                GateMounting.Result result = edit(player, gate, plan.heading(), plan.pitch(), plan.roll(), plan.width(), plan.height(), origin.getY());
                if (result != GateMounting.Result.EDITED) {
                    throw new IllegalStateException(plan.name() + " nicht eingestellt: " + result + " " + want);
                }
                if (!gate.geometry().equals(want)) {
                    throw new IllegalStateException(plan.name() + " liegt bei " + gate.geometry() + " statt " + want);
                }
                placed.put(plan.name(), new Placed(plan.name(), gate.getId(), gate.geometry()));
            }
        } catch (RuntimeException e) {
            discard(level, new Rig(origin.immutable(), placed, GateEntity.NO_COURSE));
            throw e;
        }
        int id = GateEntity.NO_COURSE;
        if (course) {
            ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
            for (String name : List.of(DIAGONAL, LEANING, DIAMOND, DIVE, HANGING, DIAGONAL_BACK)) {
                CourseToolItem.useOnGate(tool, player, (GateEntity) level.getEntity(placed.get(name).id()));
            }
            id = CourseToolItem.courseOf(tool);
        }
        Rig rig = new Rig(origin.immutable(), placed, id);
        RIG.set(rig);
        return rig;
    }

    private static double lowest(GateGeometry gate, double mx, double my, double mz) {
        double best = Double.POSITIVE_INFINITY;
        double half = gate.width() * 0.5;
        double n = gate.depth() * 0.5;
        for (int c = 0; c < 8; c++) {
            double s = (c & 1) == 0 ? -half : half;
            double u = (c & 2) == 0 ? 0.0 : gate.height();
            double d = (c & 4) == 0 ? -n : n;
            best = Math.min(best, gate.worldX(s, u, d) * mx + gate.worldY(s, u, d) * my + gate.worldZ(s, u, d) * mz);
        }
        return best;
    }

    private static String checkEdits(MinecraftServer server) {
        ServerPlayer player = player(server);
        HOME.set(player.position());
        String problem = edits(player);
        Rig rig = RIG.get();
        if (rig != null) {
            Vec3 view = rig.at(VIEW_X, 0.0, VIEW_Z);
            player.teleportTo(player.level(), view.x, view.y, view.z, Set.of(), 135.0F, 10.0F, true);
        }
        return problem;
    }

    private static @Nullable String edits(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos origin = new BlockPos(player.getBlockX() + OFFSET_X, player.getBlockY(), player.getBlockZ() + OFFSET_Z);
        Rig rig = place(player, origin, true);
        for (Plan plan : PLANS) {
            GateEntity gate = (GateEntity) level.getEntity(rig.gates().get(plan.name()).id());
            GateGeometry geometry = gate.geometry();
            if (Math.abs(gate.openingWidth() - plan.width()) > 1.0E-9 || Math.abs(gate.openingHeight() - plan.height()) > 1.0E-9) {
                return plan.name() + ": Öffnung " + gate.openingWidth() + " × " + gate.openingHeight();
            }
            if (Math.abs(geometry.heading() - plan.heading() % 360.0F) > 0.05F && plan.mount().turns() == 4) {
                return plan.name() + ": Richtung " + geometry.heading() + " statt " + plan.heading();
            }
            if (geometry.tilt().pitch() != plan.pitch() || geometry.tilt().roll() != plan.roll()) {
                return plan.name() + ": Neigung " + geometry.tilt();
            }
            AABB box = gate.getBoundingBox().inflate(1.0E-6);
            for (OrientedBox part : gate.orientedColliders().length > 0 ? List.of(gate.orientedColliders()) : List.<OrientedBox>of()) {
                if (part.minX() < box.minX || part.maxX() > box.maxX || part.minY() < box.minY || part.maxY() > box.maxY || part.minZ() < box.minZ
                        || part.maxZ() > box.maxZ) {
                    return plan.name() + ": schräges Teil außerhalb der Entity-Box";
                }
            }
            for (AABB part : gate.colliders()) {
                if (!box.contains(part.getMinPosition()) || !box.contains(part.getMaxPosition())) {
                    return plan.name() + ": Näherungsbox außerhalb der Entity-Box";
                }
            }
            if (!GateItem.free(level, geometry, gate.base(), false, gate)) {
                return plan.name() + ": eingestelltes Gate steckt in Blöcken oder Gates";
            }
            if (plan.mount() == GateMount.STANDING && Math.abs(lowest(geometry, 0.0, 1.0, 0.0) - origin.getY()) > 1.0E-6) {
                return plan.name() + ": steht nicht auf dem Boden, unterster Punkt " + lowest(geometry, 0.0, 1.0, 0.0);
            }
            if (plan.mount() == GateMount.WALL_DIVE && Math.abs(lowest(geometry, 0.0, 0.0, -1.0) + (origin.getZ() + WALL_Z)) > 1.0E-6) {
                return plan.name() + ": Tauchtor liegt nicht an der Wand: " + -lowest(geometry, 0.0, 0.0, -1.0);
            }
            if (plan.mount() == GateMount.HANGING) {
                double[] top = {Double.NEGATIVE_INFINITY};
                geometry.straps((x0, y0, z0, x1, y1, z1) -> top[0] = Math.max(top[0], y1));
                if (Math.abs(top[0] - (origin.getY() + CEILING_Y)) > 1.0E-6) {
                    return plan.name() + ": Gurte reichen nicht an die Decke: " + top[0];
                }
            }
            boolean tilted = plan.pitch() != 0.0F || plan.roll() != 0.0F;
            if (plan.mount() == GateMount.STANDING && tilted != (gate.base() == GateStacking.Base.MOUNTED)) {
                return plan.name() + ": Fußvariante falsch: " + gate.base();
            }
        }
        GateEntity leaning = (GateEntity) level.getEntity(rig.gates().get(LEANING).id());
        GateGeometry before = leaning.geometry();
        BlockPos block = BlockPos.containing(before.centerX(), origin.getY() + 1.0, before.centerZ() - 1.0);
        level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
        GateMounting.Result blocked = edit(player, leaning, 0.0F, 60.0F, 0.0F, 3.0, 3.0, origin.getY());
        level.setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
        if (blocked != GateMounting.Result.BLOCKED || !leaning.geometry().equals(before)) {
            return "Neigen in einen Block nicht abgelehnt: " + blocked;
        }
        if (GateMounting.edit(player, leaning, 0.0F, 10.0F, 0.0F, 3.0, 3.0) != GateMounting.Result.TOO_FAR) {
            return "Einstellen aus der Ferne nicht abgelehnt";
        }
        GateEntity diagonal = (GateEntity) level.getEntity(rig.gates().get(DIAGONAL).id());
        ItemStack previous = player.getMainHandItem().copy();
        Vec3 home = player.position();
        Vec3 stand = beside(diagonal.geometry(), origin.getY());
        try {
            player.teleportTo(level, stand.x, stand.y, stand.z, Set.of(), 0.0F, 0.0F, true);
            float heading = diagonal.geometry().heading();
            if (GateMounting.rotate(player, diagonal, false, 15.0F) != GateMounting.Result.ROTATED
                    || Math.abs(diagonal.geometry().heading() - (heading + 15.0F)) > 0.05F) {
                return "Taste mit 15° dreht nicht: " + diagonal.geometry().heading();
            }
            if (GateMounting.rotate(player, diagonal, false, 45.0F) != GateMounting.Result.ROTATED
                    || Math.abs(diagonal.geometry().heading() - (heading + 60.0F)) > 0.05F || diagonal.tilt().yaw() >= 90.0F) {
                return "Taste mit 45° dreht nicht: " + diagonal.geometry();
            }
            if (GateMounting.edit(player, diagonal, heading, 0.0F, 0.0F, 3.0, 3.0) != GateMounting.Result.EDITED) {
                return "Diagonales Gate nicht zurückgestellt";
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.gateItem(GateSize.SMALL).orElseThrow()));
            if (!diagonal.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction()) {
                return "Auf ein diagonales Gate lässt sich nicht stapeln";
            }
            List<GateEntity> column = diagonal.stack();
            if (column.size() != 2 || column.get(1).tilt().yaw() != diagonal.tilt().yaw()) {
                return "Stapel auf dem diagonalen Gate falsch: " + column.size();
            }
            if (GateMounting.edit(player, diagonal, heading, 10.0F, 0.0F, 3.0, 3.0) != GateMounting.Result.STACK_LOCKED) {
                return "Gestapeltes Gate ließ sich einstellen";
            }
            if (GateMounting.rotate(player, diagonal, false, 15.0F) != GateMounting.Result.STACK_LOCKED) {
                return "Gestapeltes Gate drehte sich in kleinen Schritten";
            }
            column.get(1).discard();
            diagonal.refreshColumn();
            GateEntity tilted = (GateEntity) level.getEntity(rig.gates().get(DIAMOND).id());
            if (tilted.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction()) {
                return "Auf ein geneigtes Gate gestapelt";
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            player.teleportTo(level, home.x, home.y, home.z, Set.of(), player.getYRot(), player.getXRot(), true);
        }
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(rig.course());
        if (course == null || course.gates.size() != 6) {
            return "Kurs aus geneigten Gates fehlt";
        }
        CourseData data = course.toData();
        CourseData.Gate dive = data.gates().get(3);
        if (!dive.tilt().equals(rig.gates().get(DIVE).geometry().tilt()) || !dive.geometry().equals(rig.gates().get(DIVE).geometry())) {
            return "Kursdaten ohne Neigung: " + dive;
        }
        return null;
    }

    private static StepResult editStep(SelfTest.StepContext ctx) {
        return onServer(ctx, GateFreeSelfTests::checkEdits);
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
            GateEntity server = ctx.server() == null ? null : (GateEntity) ctx.server().overworld().getEntity(placed.id());
            GateGeometry want = server == null ? placed.geometry() : server.geometry();
            if (!gate.geometry().equals(want)) {
                return ctx.tick() > 150 ? StepResult.fail(placed.name() + ": Client " + gate.geometry() + " statt " + want) : StepResult.pending();
            }
            AABB serverBox = server == null ? gate.getBoundingBox() : server.getBoundingBox();
            if (gate.getBoundingBox().minX != serverBox.minX || gate.getBoundingBox().maxY != serverBox.maxY) {
                return ctx.tick() > 150 ? StepResult.fail(placed.name() + ": Entity-Box beim Client " + gate.getBoundingBox()) : StepResult.pending();
            }
        }
        CourseData course = ClientCourses.get(rig.course());
        if (course == null || course.gates().size() != 6 || course.gates().get(2).tilt().none()) {
            return ctx.tick() > 150 ? StepResult.fail("Kurs mit Neigung fehlt beim Client") : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static @Nullable EntityHitResult pick(Minecraft minecraft, Vec3 from, Vec3 to) {
        AABB box = new AABB(from, to).inflate(1.0);
        return ProjectileUtil.getEntityHitResult(minecraft.player, from, to, box, EntitySelector.CAN_BE_PICKED, from.distanceToSqr(to));
    }

    private static StepResult pickStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        Rig rig = RIG.get();
        if (rig == null || minecraft.level == null || minecraft.player == null) {
            return StepResult.fail("kein Testbau");
        }
        GateEntity diamond = (GateEntity) minecraft.level.getEntity(rig.gates().get(DIAMOND).id());
        GateGeometry g = diamond.geometry();
        Vec3 center = new Vec3(g.centerX(), g.centerY(), g.centerZ());
        Vec3 normal = new Vec3(g.normalX(), g.normalY(), g.normalZ());
        EntityHitResult opening = pick(minecraft, center.subtract(normal.scale(3.0)), center.add(normal.scale(3.0)));
        if (opening != null && opening.getEntity() == diamond) {
            return StepResult.fail("Blick durch die Öffnung der Raute trifft das Gate");
        }
        Vec3 corner = new Vec3(g.worldX(0.0, g.height() - g.frame() * 0.5, 0.0), g.worldY(0.0, g.height() - g.frame() * 0.5, 0.0),
                g.worldZ(0.0, g.height() - g.frame() * 0.5, 0.0));
        EntityHitResult bar = pick(minecraft, corner.subtract(normal.scale(3.0)), corner.add(normal.scale(3.0)));
        if (bar == null || bar.getEntity() != diamond) {
            return StepResult.fail("Blick auf den schrägen Balken trifft das Gate nicht");
        }
        double[] hull = new double[6];
        g.bounds((x0, y0, z0, x1, y1, z1) -> {
            hull[0] = x0;
            hull[2] = z0;
        });
        Vec3 empty = new Vec3(hull[0] + 0.3, g.originY() + 0.4, hull[2] + 0.3);
        EntityHitResult air = pick(minecraft, empty.subtract(normal.scale(2.0)), empty.add(normal.scale(2.0)));
        if (air != null && air.getEntity() == diamond) {
            return StepResult.fail("Blick in die leere Ecke der Hülle trifft die Raute");
        }
        GateEntity huge = (GateEntity) minecraft.level.getEntity(rig.gates().get(HUGE).id());
        GateGeometry h = huge.geometry();
        double post = h.width() * 0.5 - h.frame() * 0.5;
        Vec3 far = new Vec3(h.worldX(post, h.height() - 1.0, 0.0), h.worldY(post, h.height() - 1.0, 0.0), h.worldZ(post, h.height() - 1.0, 0.0));
        Vec3 n = new Vec3(h.normalX(), h.normalY(), h.normalZ());
        EntityHitResult farPost = pick(minecraft, far.subtract(n.scale(2.5)), far.add(n.scale(2.5)));
        if (farPost == null || farPost.getEntity() != huge) {
            return StepResult.fail("Pfosten oben am großen Gate (12 × 12) nicht anvisierbar");
        }
        return StepResult.pass();
    }

    private static String checkDrone(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Rig rig = RIG.get();
        GateEntity diamond = (GateEntity) level.getEntity(rig.gates().get(DIAMOND).id());
        GateGeometry g = diamond.geometry();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setNoGravity(true);
        try {
            double side = g.width() * 0.5 - g.frame() * 0.5;
            double up = g.height() * 0.5;
            Vec3 post = new Vec3(g.worldX(side, up, 0.0), g.worldY(side, up, 0.0), g.worldZ(side, up, 0.0));
            Vec3 normal = new Vec3(g.normalX(), g.normalY(), g.normalZ());
            Vec3 start = post.subtract(normal.scale(1.5));
            drone.snapTo(start.x, start.y - drone.getBbHeight() * 0.5, start.z, 0.0F, 0.0F);
            if (!level.addFreshEntity(drone)) {
                return "Drohne nicht gespawnt";
            }
            drone.move(MoverType.SELF, normal.scale(3.0));
            if (!drone.obliqueContact()) {
                return "Drohne fliegt durch den schrägen Pfosten der Raute";
            }
            AABB box = drone.getBoundingBox();
            for (OrientedBox part : diamond.orientedColliders()) {
                if (part.intersects(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
                    return "Drohne steckt im schrägen Pfosten";
                }
            }
            Vec3 contact = drone.obliqueNormal();
            boolean along = Math.abs(contact.x * g.sideX() + contact.y * g.sideY() + contact.z * g.sideZ()) > 0.6
                    || Math.abs(contact.x * g.upX() + contact.y * g.upY() + contact.z * g.upZ()) > 0.6
                    || Math.abs(contact.dot(normal)) > 0.6;
            if (!along) {
                return "Berührungsnormale passt nicht zum Gate: " + contact;
            }
            Vec3 center = new Vec3(g.centerX(), g.centerY(), g.centerZ());
            Vec3 before = center.subtract(normal.scale(1.5));
            drone.snapTo(before.x, before.y - drone.getBbHeight() * 0.5, before.z, 0.0F, 0.0F);
            Vec3 from = drone.position();
            drone.move(MoverType.SELF, normal.scale(3.0));
            if (drone.obliqueContact() || drone.position().distanceTo(from.add(normal.scale(3.0))) > 1.0E-6) {
                return "Flug durch die Öffnung der Raute gebremst";
            }
        } finally {
            drone.discard();
        }
        return null;
    }

    private static StepResult droneStep(SelfTest.StepContext ctx) {
        return onServer(ctx, GateFreeSelfTests::checkDrone);
    }

    private static StepResult screenStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        Rig rig = RIG.get();
        if (rig == null || minecraft.level == null || minecraft.player == null) {
            return StepResult.fail("kein Testbau");
        }
        GateEntity gate = (GateEntity) minecraft.level.getEntity(rig.gates().get(DIAGONAL_BACK).id());
        int stage = (int) ctx.state().getOrDefault("stage", 0);
        if (stage == 0) {
            ctx.state().put("home", minecraft.player.position());
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                Vec3 stand = beside(gate.geometry(), rig.origin().getY());
                player.teleportTo(player.level(), stand.x, stand.y, stand.z, Set.of(), 0.0F, 20.0F, true);
                ctx.state().put("held", player.getMainHandItem().copy());
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.gateItem(GateSize.MEDIUM).orElseThrow()));
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
            if (!(player.getMainHandItem().getItem() instanceof GateItem) || !player.isWithinEntityInteractionRange(gate, 2.0)) {
                return ctx.tick() > 200 ? StepResult.fail("Spieler nicht am Gate") : StepResult.pending();
            }
            minecraft.options.keyShift.setDown(true);
            if (!player.isShiftKeyDown()) {
                if (ctx.tick() > 220) {
                    minecraft.options.keyShift.setDown(false);
                    return StepResult.fail("Schleichen kommt nicht an");
                }
                return StepResult.pending();
            }
            Vec3 hit = new Vec3(gate.getX(), gate.getY() + 1.0, gate.getZ());
            minecraft.gameMode.interact(player, gate, new EntityHitResult(gate, hit), InteractionHand.MAIN_HAND);
            minecraft.options.keyShift.setDown(false);
            GateEditor editor = GateEditor.current();
            if (editor == null || editor.gate() != gate) {
                return StepResult.fail("Schleichen + Rechtsklick startet Gate einstellen nicht");
            }
            if (minecraft.gui.screen() != null) {
                return StepResult.fail("Gate einstellen öffnet ein Fenster: " + minecraft.gui.screen().getClass().getSimpleName());
            }
            if (editor.status() != GateEditor.Status.SAME) {
                return StepResult.fail("Gate einstellen startet mit Änderung: " + editor.status());
            }
            GateGeometry start = gate.geometry();
            ctx.state().put("start", start);
            editor.set(start.heading(), 0.0F, 0.0F, 3.0, 3.0);
            if (editor.status() != GateEditor.Status.SAME) {
                return StepResult.fail("Unveränderte Werte gelten als Änderung");
            }
            BlockPos block = BlockPos.containing(start.centerX() + start.normalX() * 1.7, rig.origin().getY() + 1.0,
                    start.centerZ() + start.normalZ() * 1.7);
            ctx.state().put("block", block);
            ctx.state().put(FUTURE, ctx.onServer(server -> player(server).level().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState())));
            ctx.state().put("stage", 2);
            return StepResult.pending();
        }
        GateEditor editor = GateEditor.current();
        GateGeometry start = (GateGeometry) ctx.state().get("start");
        if (stage <= 5 && (editor == null || editor.gate() != gate)) {
            minecraft.options.keyUp.setDown(false);
            return StepResult.fail("Gate einstellen vorzeitig beendet (Schritt " + stage + ")");
        }
        if (stage == 2) {
            StepResult waiting = future(ctx);
            if (waiting != null) {
                return waiting;
            }
            BlockPos block = (BlockPos) ctx.state().get("block");
            if (!minecraft.level.getBlockState(block).is(Blocks.STONE)) {
                return ctx.tick() > 250 ? StepResult.fail("Block beim Client nicht da") : StepResult.pending();
            }
            editor.set(start.heading() + 90.0F, 0.0F, 0.0F, 3.0, 3.0);
            if (editor.status() != GateEditor.Status.BLOCKED || editor.pending() == null || GateEditor.replaces(gate)) {
                return StepResult.fail("Vorschau zeigt den Block nicht als Hindernis: " + editor.status());
            }
            GateEditDriver.press(minecraft, InputConstants.KEY_RETURN);
            if (GateEditor.current() != editor) {
                return StepResult.fail("Enter übernimmt eine blockierte Stellung");
            }
            editor.set(start.heading(), 0.0F, 0.0F, 3.0, 3.0);
            ctx.state().put(FUTURE, ctx.onServer(server -> player(server).level().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState())));
            ctx.state().put("stage", 3);
            return StepResult.pending();
        }
        if (stage == 3) {
            StepResult waiting = future(ctx);
            if (waiting != null) {
                return waiting;
            }
            int slot = minecraft.player.getInventory().getSelectedSlot();
            float heading = GateEditModel.wrapHeading((float) GateEditModel.onGrid(start.heading(), GateEditModel.WHEEL_DEGREES, 6));
            GateEditDriver.scroll(minecraft, 6);
            if (minecraft.player.getInventory().getSelectedSlot() != slot) {
                return StepResult.fail("Das Mausrad wechselt beim Einstellen den Slot");
            }
            if (Math.abs(editor.model().heading() - heading) > 0.05F) {
                return StepResult.fail("Mausrad dreht nicht in 5°-Schritten: " + editor.model().heading() + " statt " + heading);
            }
            if (!GateEditDriver.select(minecraft, editor, GateEditModel.Row.PITCH)) {
                return StepResult.fail("Schleichen + Mausrad wählt die Zeile Nicken nicht");
            }
            GateEditDriver.scroll(minecraft, -3);
            GateEditDriver.select(minecraft, editor, GateEditModel.Row.ROLL);
            GateEditDriver.scroll(minecraft, 2);
            GateEditDriver.select(minecraft, editor, GateEditModel.Row.WIDTH);
            GateEditDriver.scroll(minecraft, 2);
            GateEditDriver.select(minecraft, editor, GateEditModel.Row.HEIGHT);
            GateEditDriver.scroll(minecraft, 1);
            GateEditModel model = editor.model();
            if (model.pitch() != -15.0F || model.roll() != 10.0F || model.width() != 4.0 || model.height() != 3.5) {
                return StepResult.fail("Mausrad stellt falsch ein: " + model.pitch() + "/" + model.roll() + "/" + model.width() + "/" + model.height());
            }
            if (editor.status() != GateEditor.Status.FREE || !GateEditor.replaces(gate)) {
                return StepResult.fail("Vorschau nicht frei oder Gate nicht live gezeigt: " + editor.status());
            }
            for (boolean interactive : new boolean[] {false, true}) {
                GateEditPanel probe = new GateEditPanel(editor, interactive);
                probe.layout(SMALL_GUI_WIDTH, SMALL_GUI_HEIGHT);
                if (probe.top() < 0 || probe.bottom() > SMALL_GUI_HEIGHT || probe.left() < SMALL_GUI_WIDTH / 2) {
                    return StepResult.fail("Panel passt nicht in " + SMALL_GUI_WIDTH + " × " + SMALL_GUI_HEIGHT + " (GUI-Skala 4 bei 1080p): "
                            + probe.left() + "/" + probe.top() + "–" + probe.bottom());
                }
            }
            ctx.state().put("heading", heading);
            Vec3 at = minecraft.player.position();
            minecraft.player.setYRot((float) Math.toDegrees(Math.atan2(-(at.x - start.centerX()), at.z - start.centerZ())));
            ctx.state().put("walk", at);
            ctx.state().put("walkTick", ctx.tick());
            minecraft.options.keyUp.setDown(true);
            ctx.state().put("stage", 4);
            return StepResult.pending();
        }
        if (stage == 4) {
            if (ctx.tick() - (int) ctx.state().get("walkTick") < 8) {
                minecraft.options.keyUp.setDown(true);
                return StepResult.pending();
            }
            minecraft.options.keyUp.setDown(false);
            Vec3 walk = (Vec3) ctx.state().get("walk");
            if (minecraft.player.position().distanceTo(walk) < 0.3) {
                return StepResult.fail("Spieler kann sich beim Einstellen nicht bewegen");
            }
            if (!ctx.state().containsKey("cursor")) {
                if (minecraft.gui.screen() != null) {
                    return StepResult.fail("Beim Laufen ist ein Fenster offen");
                }
                GateEditDriver.cursorHeld(minecraft, true);
                ctx.state().put("cursor", ctx.tick());
                return StepResult.pending();
            }
            if (!(minecraft.gui.screen() instanceof GateEditCursorScreen screen) || screen.panel() == null) {
                return ctx.tick() - (int) ctx.state().get("cursor") > 20 ? StepResult.fail("Taste für die freie Maus öffnet das Panel nicht")
                        : StepResult.pending();
            }
            GateEditPanel panel = screen.panel();
            AbstractWidget plus = panel.plus(GateEditModel.Row.WIDTH);
            editor.model().select(GateEditModel.Row.PITCH);
            if (plus == null || !GateEditDriver.click(minecraft, plus) || editor.model().width() != 4.5) {
                return StepResult.fail("Klick auf + bei Breite mit freier Maus ändert nichts: " + editor.model().width());
            }
            if (editor.model().selected() != GateEditModel.Row.WIDTH) {
                return StepResult.fail("Klick in eine Zeile wählt sie nicht aus");
            }
            GateEditDriver.cursorHeld(minecraft, false);
            if (minecraft.gui.screen() != null) {
                return StepResult.fail("Loslassen der Taste schließt die freie Maus nicht");
            }
            ctx.state().put("stage", 5);
            return StepResult.pending();
        }
        if (stage == 5) {
            GateEditDriver.press(minecraft, InputConstants.KEY_RETURN);
            if (GateEditor.current() != null) {
                return StepResult.fail("Enter übernimmt nicht: " + editor.status());
            }
            ctx.state().put("stage", 6);
            return StepResult.pending();
        }
        float heading = (float) ctx.state().get("heading");
        GateTilt tilt = gate.tilt();
        boolean applied = tilt.pitch() == -15.0F && tilt.roll() == 10.0F && Math.abs(gate.openingWidth() - 4.5) < 1.0E-9
                && Math.abs(gate.openingHeight() - 3.5) < 1.0E-9 && Math.abs(gate.geometry().heading() - heading) < 0.05F;
        if (stage == 6) {
            if (!applied) {
                return ctx.tick() > 500 ? StepResult.fail("Server hat die Einstellung nicht übernommen: " + gate.geometry()) : StepResult.pending();
            }
            GateEditor.start(gate);
            GateEditor again = GateEditor.current();
            if (again == null || again.status() != GateEditor.Status.SAME) {
                return StepResult.fail("Nach dem Übernehmen bleibt eine Änderung offen");
            }
            GateEditDriver.scroll(minecraft, 3);
            if (again.status() == GateEditor.Status.SAME) {
                return StepResult.fail("Mausrad ändert nach dem Neustart nichts");
            }
            GateEditDriver.press(minecraft, InputConstants.KEY_ESCAPE);
            if (GateEditor.current() != null || minecraft.gui.screen() != null) {
                return StepResult.fail("Esc bricht nicht ab oder öffnet das Spielmenü");
            }
            ctx.state().put("stage", 7);
            ctx.state().put("since", ctx.tick());
            return StepResult.pending();
        }
        if (ctx.tick() - (int) ctx.state().get("since") < 10) {
            return StepResult.pending();
        }
        if (!applied) {
            return StepResult.fail("Esc hat trotzdem etwas geändert: " + gate.geometry());
        }
        Vec3 home = (Vec3) ctx.state().get("home");
        ItemStack held = (ItemStack) ctx.state().get("held");
        ctx.onServer(server -> {
            ServerPlayer player = player(server);
            player.setItemInHand(InteractionHand.MAIN_HAND, held == null ? ItemStack.EMPTY : held);
            player.teleportTo(player.level(), home.x, home.y, home.z, Set.of(), player.getYRot(), player.getXRot(), true);
        });
        return StepResult.pass();
    }

    private static String checkPersistence(MinecraftServer server) {
        ServerLevel level = player(server).level();
        Rig rig = RIG.get();
        GateEntity source = (GateEntity) level.getEntity(rig.gates().get(HANGING).id());
        ProblemReporter.Collector reporter = new ProblemReporter.Collector();
        TagValueOutput output = TagValueOutput.createWithContext(reporter, server.registryAccess());
        source.saveWithoutId(output);
        CompoundTag tag = output.buildResult();
        if (!tag.contains("Tilt")) {
            return "Neigung nicht gespeichert";
        }
        GateEntity copy = RaceModule.GATE.get().create(level, EntitySpawnReason.LOAD);
        copy.load(TagValueInput.create(reporter, server.registryAccess(), tag));
        if (!copy.geometry().equals(source.geometry())) {
            return "Neigung nach dem Laden anders: " + copy.geometry();
        }
        GateEntity big = (GateEntity) level.getEntity(rig.gates().get(HUGE).id());
        output = TagValueOutput.createWithContext(reporter, server.registryAccess());
        big.saveWithoutId(output);
        tag = output.buildResult();
        if (!tag.contains("OpeningWidth")) {
            return "Größe nicht gespeichert";
        }
        GateEntity bigCopy = RaceModule.GATE.get().create(level, EntitySpawnReason.LOAD);
        bigCopy.load(TagValueInput.create(reporter, server.registryAccess(), tag));
        if (Math.abs(bigCopy.width() - big.width()) > 1.0E-9 || !bigCopy.getBoundingBox().equals(big.getBoundingBox())) {
            return "Größe nach dem Laden anders: " + bigCopy.width();
        }
        tag.remove("Tilt");
        tag.remove("OpeningWidth");
        tag.remove("OpeningHeight");
        GateEntity old = RaceModule.GATE.get().create(level, EntitySpawnReason.LOAD);
        old.load(TagValueInput.create(reporter, server.registryAccess(), tag));
        if (!old.tilt().none() || old.customOpening() || old.width() != GateSize.LARGE.width()) {
            return "altes Gate ohne neue Felder lädt nicht als Standard";
        }
        if (!reporter.isEmpty()) {
            return "Meldungen beim Speichern/Laden: " + reporter.getReport();
        }
        return null;
    }

    private static StepResult persistenceStep(SelfTest.StepContext ctx) {
        return onServer(ctx, GateFreeSelfTests::checkPersistence);
    }

    private static void discard(ServerLevel level, Rig rig) {
        for (int id : rig.ids()) {
            if (level.getEntity(id) instanceof GateEntity gate) {
                gate.discard();
            }
        }
        BlockPos o = rig.origin();
        fill(level, o.offset(FLOOR_X0, 0, FLOOR_Z0), o.offset(FLOOR_X1, CEILING_Y + 14, FLOOR_Z1), Blocks.AIR.defaultBlockState());
        Map<BlockPos, BlockState> floor = FLOORS.remove(o);
        if (floor != null) {
            floor.forEach(level::setBlockAndUpdate);
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
        mc.options.keyUp.setDown(false);
        Rig rig = RIG.get();
        if (rig == null) {
            return StepResult.pass();
        }
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                discard(player.level(), rig);
                Vec3 home = HOME.getAndSet(null);
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
        Minecraft minecraft = Minecraft.getInstance();
        boolean gone = minecraft.level != null && rig.ids().stream().allMatch(id -> minecraft.level.getEntity(id) == null);
        if (!gone || ClientCourses.get(rig.course()) != null) {
            return ctx.tick() > 150 ? StepResult.fail("Gates oder Kurs noch beim Client") : StepResult.pending();
        }
        RIG.set(null);
        return StepResult.pass();
    }

    private static float wrapHeading(float value) {
        float wrapped = value % 360.0F;
        return wrapped < 0.0F ? wrapped + 360.0F : wrapped;
    }

    public static void registerScreenshots(Supplier<BlockPos> base) {
        Supplier<BlockPos> origin = () -> base.get().offset(SHOT_OFFSET_X, 0, SHOT_OFFSET_Z);
        Screenshots.step("bildschirmfotos: Gates diagonal (45°) im Slalom", "gate_free_diagonal", 40,
                new View(origin, new Vec3(1.0, 4.5, -5.0), new Vec3(7.0, 1.5, 7.0), null));
        Screenshots.step("bildschirmfotos: geneigtes Gate und Raute von der Einflugseite", "gate_free_tilted", 40,
                new View(origin, new Vec3(20.0, 2.5, -4.5), new Vec3(20.0, 2.0, 5.5), null));
        Screenshots.step("bildschirmfotos: schräges Tauchtor an der Wand", "gate_free_dive", 40,
                new View(origin, new Vec3(6.0, 13.5, 16.5), new Vec3(8.5, 6.0, 22.5), null));
        Screenshots.step("bildschirmfotos: gerollt hängendes Gate unter der Decke", "gate_free_hanging", 30,
                new View(origin, new Vec3(33.0, 1.0, -3.0), new Vec3(37.5, 8.0, 8.0), null));
        Screenshots.step("bildschirmfotos: Gates in eigener Größe von 1 × 1 bis 12 × 12", "gate_free_sizes", 40,
                new View(origin, new Vec3(20.0, 8.0, 16.0), new Vec3(21.0, 4.0, 34.0), null));
        Screenshots.step("bildschirmfotos: Gate einstellen im Spiel mit Live-Vorschau", "gate_edit_mode", 30, new EditView(origin, false, false));
        Screenshots.step("bildschirmfotos: Gate einstellen mit freier Maus", "gate_edit_cursor", 30, new EditView(origin, false, true));
        SelfTest.clientStep("bildschirmfotos: frei gedrehte Gates aufräumen", 200, GateFreeSelfTests::cleanupShotsStep);
    }

    public static Screenshots.Scene editScene(Supplier<BlockPos> base) {
        return new EditView(() -> base.get().offset(SHOT_OFFSET_X, 0, SHOT_OFFSET_Z), true, false);
    }

    private static StepResult cleanupShotsStep(SelfTest.StepContext ctx) {
        Rig rig = SHOT_RIG.get();
        if (rig == null) {
            return StepResult.pass();
        }
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                discard(player.level(), rig);
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

    private static class View implements Screenshots.Scene {
        protected final Supplier<BlockPos> origin;
        private final Vec3 feet;
        private final Vec3 target;
        private final @Nullable Supplier<ItemStack> held;
        private ItemStack previous = ItemStack.EMPTY;

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
                            Rig keep = RIG.get();
                            SHOT_RIG.set(place(player, this.origin.get(), true));
                            RIG.set(keep);
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
                return ctx.tick() > 300 ? StepResult.fail("Testgelände für frei gedrehte Gates lädt nicht") : StepResult.pending();
            }
            Rig rig = SHOT_RIG.get();
            if (mc.player == null || mc.level == null || rig == null || PilotSession.INSTANCE.isActive()) {
                return ctx.tick() > 300 ? StepResult.fail("keine Szene für frei gedrehte Gates") : StepResult.pending();
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

    private static final class EditView extends View {
        private final boolean standalone;
        private final boolean cursor;

        private EditView(Supplier<BlockPos> origin, boolean standalone, boolean cursor) {
            super(origin, new Vec3(9.5, 0.0, 3.0), new Vec3(9.0, 1.2, 9.0), () -> new ItemStack(RaceModule.gateItem(GateSize.MEDIUM).orElseThrow()));
            this.standalone = standalone;
            this.cursor = cursor;
        }

        @Override
        protected StepResult ready(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Rig rig = SHOT_RIG.get();
            if (rig == null || mc.level == null || mc.player == null
                    || !(mc.level.getEntity(rig.gates().get(DIAGONAL_BACK).id()) instanceof GateEntity gate)) {
                return StepResult.fail("Gate zum Einstellen fehlt");
            }
            if (!GateMounting.holdsTool(mc.player)) {
                return ctx.tick() > 300 ? StepResult.fail("Gate-Item kommt nicht an") : StepResult.pending();
            }
            GateEditor editor = GateEditor.current();
            if (editor == null || editor.gate() != gate) {
                GateEditor.start(gate);
                editor = GateEditor.current();
                if (editor == null) {
                    return StepResult.fail("Gate einstellen startet nicht");
                }
                GateGeometry g = gate.geometry();
                editor.set(g.heading() + 30.0F, 20.0F, 15.0F, 4.0, 3.5);
                editor.model().select(GateEditModel.Row.PITCH);
            }
            if (editor.status() != GateEditor.Status.FREE) {
                return ctx.tick() > 300 ? StepResult.fail("Vorschau beim Einstellen nicht frei: " + editor.status()) : StepResult.pending();
            }
            String cut = GateEditDriver.titleCut(mc);
            if (cut != null) {
                return StepResult.fail(cut);
            }
            if (this.cursor && !this.cursorOpen(mc)) {
                return ctx.tick() > 300 ? StepResult.fail("Freie Maus öffnet nicht") : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            super.hold(ctx);
            if (this.cursor) {
                this.cursorOpen(Minecraft.getInstance());
            }
        }

        private boolean cursorOpen(Minecraft mc) {
            if (!(mc.gui.screen() instanceof GateEditCursorScreen)) {
                GateEditCursorScreen.open();
            }
            if (mc.gui.screen() instanceof GateEditCursorScreen screen && screen.panel() != null) {
                AbstractWidget slider = screen.panel().control(GateEditModel.Row.ROLL);
                if (slider != null) {
                    GateEditDriver.hover(mc, slider);
                }
                return true;
            }
            return false;
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            GateEditor.stop();
            Screenshots.parkMouse(mc);
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
