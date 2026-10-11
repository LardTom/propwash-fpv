package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateItem;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GatePlacement;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceRecords;
import de.lardtom.propwash.race.RaceServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

public final class GateMountSelfTests {
    public static final String DIVE = "wall_dive_medium";
    public static final String LAUNCH = "wall_dive_small_up";
    public static final String DIVE_LARGE = "wall_dive_large";
    public static final String SIDE = "wall_side_medium";
    public static final String SIDE_LEFT = "wall_side_small_left";
    public static final String WINDOW = "wall_window_large";
    public static final String WINDOW_OUT = "wall_window_medium_out";
    public static final String HANGING = "hanging_medium";
    public static final String HANGING_TURNED = "hanging_small_right";
    public static final String CEILING = "ceiling_flat_medium";
    public static final String CEILING_UP = "ceiling_flat_small_up";
    public static final String STANDING = "standing_medium";
    public static final String FLOOR = "floor_flat_medium";
    public static final String FLOOR_UP = "floor_flat_small_up";
    public static final int WALL_LENGTH = 40;
    public static final int WALL_HEIGHT = 10;
    public static final int CEILING_X0 = 42;
    public static final int CEILING_X1 = 60;
    public static final int CEILING_Z0 = -2;
    public static final int CEILING_Z1 = 14;
    private static final int OFFSET = -64;
    private static final long MS = 1_000_000L;
    private static final String FUTURE = "future";
    private static final AtomicReference<Rig> RIG = new AtomicReference<>();
    private static final AtomicReference<Rig> SHOT_RIG = new AtomicReference<>();
    private static final AtomicReference<String> RESULT = new AtomicReference<>();
    private static final AtomicReference<Placed> TURNED = new AtomicReference<>();

    public record Spec(String name, GateSize size, GateMount mount, int turn, int dx, int dy, int dz, Direction face, float yaw) {
    }

    public record Placed(String name, int id, GateMount mount, boolean flipped, float yaw, GateGeometry geometry) {
    }

    public record Rig(BlockPos origin, Map<String, Placed> gates, int course) {
        public List<Integer> ids() {
            return this.gates.values().stream().map(Placed::id).toList();
        }

        public Vec3 at(double dx, double dy, double dz) {
            return new Vec3(this.origin.getX() + dx, this.origin.getY() + dy, this.origin.getZ() + dz);
        }
    }

    public static final List<Spec> SPECS = List.of(
            new Spec(DIVE, GateSize.MEDIUM, GateMount.WALL_DIVE, 0, 3, 5, 0, Direction.SOUTH, 180.0F),
            new Spec(LAUNCH, GateSize.SMALL, GateMount.WALL_DIVE, 1, 9, 5, 0, Direction.SOUTH, 180.0F),
            new Spec(DIVE_LARGE, GateSize.LARGE, GateMount.WALL_DIVE, 0, 16, 5, 0, Direction.SOUTH, 180.0F),
            new Spec(SIDE, GateSize.MEDIUM, GateMount.WALL_SIDE, 0, 22, 3, 0, Direction.SOUTH, 180.0F),
            new Spec(SIDE_LEFT, GateSize.SMALL, GateMount.WALL_SIDE, 1, 25, 3, 0, Direction.SOUTH, 180.0F),
            new Spec(WINDOW, GateSize.LARGE, GateMount.WALL_WINDOW, 0, 30, 3, 0, Direction.SOUTH, 180.0F),
            new Spec(WINDOW_OUT, GateSize.MEDIUM, GateMount.WALL_WINDOW, 1, 37, 3, 0, Direction.SOUTH, 180.0F),
            new Spec(HANGING, GateSize.MEDIUM, GateMount.HANGING, 0, 46, WALL_HEIGHT - 1, 4, Direction.DOWN, 0.0F),
            new Spec(HANGING_TURNED, GateSize.SMALL, GateMount.HANGING, 1, 53, WALL_HEIGHT - 1, 4, Direction.DOWN, 0.0F),
            new Spec(CEILING, GateSize.MEDIUM, GateMount.CEILING_FLAT, 0, 46, WALL_HEIGHT - 1, 10, Direction.DOWN, 0.0F),
            new Spec(CEILING_UP, GateSize.SMALL, GateMount.CEILING_FLAT, 1, 53, WALL_HEIGHT - 1, 10, Direction.DOWN, 0.0F),
            new Spec(STANDING, GateSize.MEDIUM, GateMount.STANDING, 0, 3, -1, 12, Direction.UP, 0.0F),
            new Spec(FLOOR, GateSize.MEDIUM, GateMount.FLOOR_FLAT, 0, 10, -1, 12, Direction.UP, 0.0F),
            new Spec(FLOOR_UP, GateSize.SMALL, GateMount.FLOOR_FLAT, 1, 17, -1, 12, Direction.UP, 0.0F));

    private GateMountSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("race: Gates an Wand, Decke und Boden in jeder Lage setzen", 200, GateMountSelfTests::placeStep);
        SelfTest.clientStep("race: Lagen der Gates beim Client", 200, GateMountSelfTests::clientStep);
        SelfTest.clientStep("race: Gates an Ort und Stelle drehen", 200, GateMountSelfTests::rotateStep);
        SelfTest.clientStep("race: gedrehtes stehendes Gate beim Client", 200, GateMountSelfTests::turnedClientStep);
        SelfTest.clientStep("race: Gates auf Gras, Blumen, Flechten und oberen Stufen", 200, GateMountSelfTests::surfaceStep);
        SelfTest.clientStep("race: Kurs mit Tauchtor und hängendem Gate", 200, GateMountSelfTests::courseStep);
        SelfTest.clientStep("race: Runde durchs Tauchtor (Client)", 20, GateMountSelfTests::clientLapStep);
        SelfTest.clientStep("race: Runde durchs Tauchtor (Server-Prüfung)", 200, GateMountSelfTests::serverLapStep);
        SelfTest.clientStep("race: Drehen eines Kurs-Gates archiviert die Bestenliste", 200, GateMountSelfTests::archiveStep);
        SelfTest.clientStep("race: Lage im Entity speichern und laden", 200, GateMountSelfTests::persistenceStep);
        SelfTest.clientStep("race: Gates an Wand und Decke aufräumen", 200, GateMountSelfTests::cleanupStep);
    }

    public static @Nullable Rig rig() {
        return RIG.get();
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
        for (int cx = (o.getX() - 4) >> 4; cx <= (o.getX() + CEILING_X1 + 4) >> 4; cx++) {
            for (int cz = (o.getZ() + CEILING_Z0 - 4) >> 4; cz <= (o.getZ() + 20) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        fill(level, o.offset(-2, 0, CEILING_Z0 - 2), o.offset(CEILING_X1 + 2, WALL_HEIGHT + 2, 18), Blocks.AIR.defaultBlockState());
        fill(level, o.offset(-2, -3, CEILING_Z0 - 2), o.offset(CEILING_X1 + 2, -1, 18), Blocks.SMOOTH_STONE.defaultBlockState());
        fill(level, o.offset(0, 0, 0), o.offset(WALL_LENGTH, WALL_HEIGHT - 1, 0), Blocks.STONE_BRICKS.defaultBlockState());
        fill(level, o.offset(CEILING_X0, WALL_HEIGHT - 1, CEILING_Z0), o.offset(CEILING_X1, WALL_HEIGHT - 1, CEILING_Z1),
                Blocks.STONE_BRICKS.defaultBlockState());
    }

    public static void cutOpenings(ServerLevel level, BlockPos o) {
        fill(level, o.offset(28, 2, 0), o.offset(32, 4, 0), Blocks.AIR.defaultBlockState());
        fill(level, o.offset(36, 2, 0), o.offset(38, 4, 0), Blocks.AIR.defaultBlockState());
        fill(level, o.offset(9, -3, 11), o.offset(11, -1, 13), Blocks.AIR.defaultBlockState());
        fill(level, o.offset(17, -3, 12), o.offset(18, -1, 13), Blocks.AIR.defaultBlockState());
    }

    public static InteractionResult use(ServerPlayer player, Spec spec, BlockPos origin) {
        ItemStack previous = player.getMainHandItem().copy();
        float previousYaw = player.getYRot();
        ItemStack stack = new ItemStack(RaceModule.gateItem(spec.size()).orElseThrow());
        GatePlacement.Preference before = GateMounting.preference(player, false);
        try {
            GateMounting.setPreference(player, GatePlacement.Preference.DEFAULT.with(spec.mount(), spec.turn()));
            player.setYRot(spec.yaw());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockPos clicked = origin.offset(spec.dx(), spec.dy(), spec.dz());
            Vec3 hit = Vec3.atCenterOf(clicked).add(Vec3.atLowerCornerOf(spec.face().getUnitVec3i()).scale(0.5));
            return stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, spec.face(), clicked, false)));
        } finally {
            GateMounting.setPreference(player, before);
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            player.setYRot(previousYaw);
        }
    }

    public static GateGeometry expected(ServerPlayer player, Spec spec, BlockPos origin) {
        ItemStack previous = player.getMainHandItem().copy();
        float previousYaw = player.getYRot();
        ItemStack stack = new ItemStack(RaceModule.gateItem(spec.size()).orElseThrow());
        try {
            player.setYRot(spec.yaw());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockPos clicked = origin.offset(spec.dx(), spec.dy(), spec.dz());
            Vec3 hit = Vec3.atCenterOf(clicked).add(Vec3.atLowerCornerOf(spec.face().getUnitVec3i()).scale(0.5));
            UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, spec.face(), clicked, false));
            return GatePlacement.resolve(GateItem.click(context), spec.mount(), spec.turn(), spec.size());
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            player.setYRot(previousYaw);
        }
    }

    public static Rig place(ServerPlayer player, BlockPos origin, boolean course) {
        return place(player, origin, course ? List.of(STANDING, DIVE, HANGING) : List.of());
    }

    public static Rig place(ServerPlayer player, BlockPos origin, List<String> course) {
        ServerLevel level = player.level();
        buildWorld(level, origin);
        Map<String, Placed> placed = new LinkedHashMap<>();
        for (Spec spec : SPECS) {
            GateGeometry want = expected(player, spec, origin);
            AABB area = new AABB(Vec3.atLowerCornerOf(origin.offset(-4, -4, CEILING_Z0 - 4)), Vec3.atLowerCornerOf(origin.offset(CEILING_X1 + 4, WALL_HEIGHT + 4, 20)));
            List<GateEntity> before = level.getEntitiesOfClass(GateEntity.class, area);
            InteractionResult result = use(player, spec, origin);
            if (!result.consumesAction()) {
                throw new IllegalStateException(spec.name() + " nicht gesetzt: " + result + " " + want);
            }
            List<GateEntity> after = level.getEntitiesOfClass(GateEntity.class, area, gate -> !before.contains(gate) && !gate.isRemoved());
            if (after.size() != 1) {
                throw new IllegalStateException(spec.name() + ": " + after.size() + " neue Gates");
            }
            GateEntity gate = after.getFirst();
            placed.put(spec.name(), new Placed(spec.name(), gate.getId(), gate.mount(), gate.flipped(), gate.facingYaw(), gate.geometry()));
            if (!gate.geometry().equals(want)) {
                throw new IllegalStateException(spec.name() + " liegt bei " + gate.geometry() + " statt " + want);
            }
        }
        cutOpenings(level, origin);
        int id = GateEntity.NO_COURSE;
        if (!course.isEmpty()) {
            ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
            for (String name : course) {
                CourseToolItem.useOnGate(tool, player, (GateEntity) level.getEntity(placed.get(name).id()));
            }
            id = CourseToolItem.courseOf(tool);
        }
        Rig rig = new Rig(origin.immutable(), placed, id);
        RIG.set(rig);
        return rig;
    }

    private static String checkPlacement(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        BlockPos origin = new BlockPos(player.getBlockX() + OFFSET, player.getBlockY(), player.getBlockZ() + OFFSET);
        Rig rig = place(player, origin, true);
        for (Spec spec : SPECS) {
            Placed placed = rig.gates().get(spec.name());
            GateEntity gate = (GateEntity) level.getEntity(placed.id());
            GateGeometry geometry = gate.geometry();
            boolean wantFlip = spec.mount().reversible() && spec.turn() == 1;
            if (gate.mount() != spec.mount() || gate.flipped() != wantFlip) {
                return spec.name() + ": Lage " + gate.mount() + "/" + gate.flipped();
            }
            if (gate.colliders().length != geometry.boxCount(gate.base())) {
                return spec.name() + ": " + gate.colliders().length + " Kollisionsboxen statt " + geometry.boxCount(gate.base());
            }
            AABB box = gate.getBoundingBox().inflate(1.0E-6);
            for (AABB part : gate.colliders()) {
                if (!box.contains(part.getMinPosition()) || !box.contains(part.getMaxPosition())) {
                    return spec.name() + ": Kollisionsbox " + part + " außerhalb der Entity-Box " + box;
                }
                if (!level.noCollision(null, part.deflate(0.02))) {
                    return spec.name() + ": Gate steckt in Blöcken bei " + part;
                }
            }
            if (spec.mount().surface() == GateMount.Surface.WALL && Math.abs(geometry.normalY()) < 0.5 && spec.mount() != GateMount.WALL_WINDOW
                    && Math.abs(geometry.normalZ()) > 0.5) {
                return spec.name() + ": seitliches Wandgate fliegt in die Wand";
            }
            if (spec.mount() == GateMount.WALL_DIVE && geometry.normalY() != (wantFlip ? 1.0 : -1.0)) {
                return spec.name() + ": Tauchtor zeigt " + geometry.normalY();
            }
            if (spec.mount() == GateMount.HANGING && Math.abs(geometry.y() + geometry.topExtent() - (rig.origin().getY() + WALL_HEIGHT - 1)) > 1.0E-6) {
                return spec.name() + ": Gurte reichen nicht bis an die Decke";
            }
        }
        if (use(player, SPECS.getFirst(), origin).consumesAction()) {
            return "zweites Tauchtor an derselben Stelle gesetzt";
        }
        GateEntity dive = (GateEntity) level.getEntity(rig.gates().get(DIVE).id());
        ItemStack previous = player.getMainHandItem().copy();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.gateItem(GateSize.SMALL).orElseThrow()));
            if (dive.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction()) {
                return "Gate auf ein Tauchtor gestapelt";
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
        return null;
    }

    private static StepResult placeStep(SelfTest.StepContext ctx) {
        return onServer(ctx, GateMountSelfTests::checkPlacement);
    }

    private static StepResult clientStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        Rig rig = RIG.get();
        if (rig == null || minecraft.level == null) {
            return StepResult.fail("kein Testbau");
        }
        String problem = null;
        for (Placed placed : rig.gates().values()) {
            if (!(minecraft.level.getEntity(placed.id()) instanceof GateEntity gate)) {
                problem = placed.name() + " fehlt beim Client";
            } else if (gate.mount() != placed.mount() || gate.flipped() != placed.flipped()) {
                problem = placed.name() + ": Client-Lage " + gate.mount() + "/" + gate.flipped();
            } else if (!gate.geometry().equals(placed.geometry())) {
                problem = placed.name() + ": Client-Geometrie " + gate.geometry() + " statt " + placed.geometry();
            } else {
                AABB[] box = new AABB[1];
                placed.geometry().bounds((minX, minY, minZ, maxX, maxY, maxZ) -> box[0] = new AABB(minX, minY, minZ, maxX, maxY, maxZ));
                if (!close(gate.getBoundingBox(), box[0])) {
                    problem = placed.name() + ": Client-Box " + gate.getBoundingBox() + " statt " + box[0];
                }
            }
            if (problem != null) {
                break;
            }
        }
        if (problem != null) {
            return ctx.tick() > 180 ? StepResult.fail(problem) : StepResult.pending();
        }
        ctx.log(rig.gates().size() + " Gates in " + GateMount.values().length + " Lagen beim Client");
        return StepResult.pass();
    }

    private static boolean close(AABB a, AABB b) {
        double e = 1.0E-5;
        return Math.abs(a.minX - b.minX) < e && Math.abs(a.minY - b.minY) < e && Math.abs(a.minZ - b.minZ) < e
                && Math.abs(a.maxX - b.maxX) < e && Math.abs(a.maxY - b.maxY) < e && Math.abs(a.maxZ - b.maxZ) < e;
    }

    private static String checkRotation(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Rig rig = RIG.get();
        Vec3 home = player.position();
        try {
            GateEntity side = (GateEntity) level.getEntity(rig.gates().get(SIDE).id());
            if (GateMounting.rotate(player, side, false) != GateMounting.Result.TOO_FAR) {
                return "Drehen aus der Ferne nicht abgelehnt";
            }
            Vec3 front = rig.at(23.5, 0.0, 6.5);
            player.teleportTo(level, front.x, front.y, front.z, Set.of(), 180.0F, 0.0F, true);
            double[] point = GatePlacement.mountPoint(side.geometry());
            GateMounting.Result turned = GateMounting.rotate(player, side, false);
            if (turned != GateMounting.Result.ROTATED || !side.flipped() || side.geometry().normalX() != -1.0) {
                return "seitliches Gate nicht umgedreht: " + turned + " " + side.geometry();
            }
            double[] after = GatePlacement.mountPoint(side.geometry());
            for (int i = 0; i < 3; i++) {
                if (Math.abs(point[i] - after[i]) > 1.0E-9) {
                    return "Befestigungspunkt beim Drehen verschoben";
                }
            }
            for (GateMount next : List.of(GateMount.WALL_WINDOW, GateMount.WALL_DIVE, GateMount.WALL_SIDE)) {
                GateMounting.Result changed = GateMounting.rotate(player, side, true);
                if (changed != GateMounting.Result.ROTATED || side.mount() != next || side.flipped()) {
                    return "Lage nicht zu " + next + " gewechselt: " + changed + " " + side.mount();
                }
            }
            GateEntity hanging = (GateEntity) level.getEntity(rig.gates().get(HANGING).id());
            Vec3 under = rig.at(46.5, 0.0, 9.0);
            player.teleportTo(level, under.x, under.y, under.z, Set.of(), 180.0F, -20.0F, true);
            BlockPos wall = rig.origin().offset(46, 5, 6);
            level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            GateMounting.Result blocked = GateMounting.rotate(player, hanging, false);
            float yaw = hanging.facingYaw();
            if (blocked != GateMounting.Result.BLOCKED || yaw != rig.gates().get(HANGING).yaw()) {
                return "Drehen in einen Block nicht abgelehnt: " + blocked + " yaw " + yaw;
            }
            level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
            if (GateMounting.rotate(player, hanging, false) != GateMounting.Result.ROTATED || hanging.facingYaw() != GateGeometry.snapYaw(yaw + 90.0F)) {
                return "hängendes Gate nicht gedreht: " + hanging.facingYaw();
            }
            CourseRegistry registry = CourseRegistry.get();
            CourseRegistry.Course course = registry == null ? null : registry.course(rig.course());
            if (course == null || course.gates.get(2).yaw != hanging.facingYaw() || !GateMount.HANGING.key().equals(course.gates.get(2).mount)) {
                return "Kurs übernimmt das gedrehte Gate nicht";
            }
            for (int i = 0; i < 3; i++) {
                GateMounting.rotate(player, hanging, false);
            }
            if (hanging.facingYaw() != yaw || course.gates.get(2).yaw != yaw) {
                return "hängendes Gate nach vier Drehungen nicht zurück: " + hanging.facingYaw();
            }
            GateEntity standing = (GateEntity) level.getEntity(rig.gates().get(STANDING).id());
            Vec3 floor = rig.at(3.5, 0.0, 17.0);
            player.teleportTo(level, floor.x, floor.y, floor.z, Set.of(), 180.0F, 0.0F, true);
            if (GateMounting.rotate(player, standing, true) != GateMounting.Result.ROTATED || standing.mount() != GateMount.FLOOR_FLAT) {
                return "stehendes Gate nicht hingelegt: " + standing.mount();
            }
            if (GateMounting.rotate(player, standing, true) != GateMounting.Result.ROTATED || standing.mount() != GateMount.STANDING
                    || !standing.geometry().equals(rig.gates().get(STANDING).geometry())) {
                return "stehendes Gate nicht wieder aufgestellt: " + standing.geometry();
            }
            ItemStack previous = player.getMainHandItem().copy();
            try {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.gateItem(GateSize.SMALL).orElseThrow()));
                if (!standing.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction()) {
                    return "kleines Gate nicht auf das stehende gestapelt";
                }
            } finally {
                player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            }
            GateEntity top = standing.stack().getLast();
            try {
                if (top == standing || GateMounting.rotate(player, top, true) != GateMounting.Result.STACK_LOCKED) {
                    return "Lagewechsel im Stapel nicht gesperrt";
                }
                float before = top.facingYaw();
                if (GateMounting.rotate(player, top, false) != GateMounting.Result.ROTATED || top.facingYaw() != GateGeometry.snapYaw(before + 180.0F)) {
                    return "gestapeltes Gate nicht um 180° gedreht: " + top.facingYaw();
                }
            } finally {
                top.discard();
                standing.refreshStack();
            }
            BlockPos ground = rig.origin().offset(30, -1, 15);
            GateEntity large = placeAt(player, GateSize.LARGE, ground, Direction.UP, Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0),
                    rig.at(30.5, 0.0, 11.0));
            if (large == null || large.mount() != GateMount.STANDING) {
                return "großes stehendes Gate nicht gesetzt";
            }
            TURNED.set(new Placed("standing_large", large.getId(), large.mount(), large.flipped(), large.facingYaw(), large.geometry()));
            return null;
        } finally {
            player.teleportTo(level, home.x, home.y, home.z, Set.of(), player.getYRot(), player.getXRot(), true);
        }
    }

    private static StepResult rotateStep(SelfTest.StepContext ctx) {
        return onServer(ctx, GateMountSelfTests::checkRotation);
    }

    private static @Nullable GateEntity placeAt(ServerPlayer player, GateSize size, BlockPos clicked, Direction face, Vec3 hit, Vec3 feet) {
        ServerLevel level = player.level();
        Vec3 eye = feet.add(0.0, player.getEyeHeight(), 0.0);
        double dx = hit.x - eye.x;
        double dy = hit.y - eye.y;
        double dz = hit.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), yaw, pitch, true);
        ItemStack previous = player.getMainHandItem().copy();
        GatePlacement.Preference before = GateMounting.preference(player, false);
        AABB area = new AABB(hit, hit).inflate(10.0);
        List<GateEntity> existing = level.getEntitiesOfClass(GateEntity.class, area);
        try {
            GateMounting.setPreference(player, GatePlacement.Preference.DEFAULT);
            ItemStack stack = new ItemStack(RaceModule.gateItem(size).orElseThrow());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            InteractionResult result = stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, clicked, false)));
            if (!result.consumesAction()) {
                return null;
            }
            List<GateEntity> fresh = level.getEntitiesOfClass(GateEntity.class, area, gate -> !existing.contains(gate) && !gate.isRemoved());
            return fresh.size() == 1 ? fresh.getFirst() : null;
        } finally {
            GateMounting.setPreference(player, before);
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
    }

    private static AABB bounds(GateGeometry geometry) {
        AABB[] box = new AABB[1];
        geometry.bounds((minX, minY, minZ, maxX, maxY, maxZ) -> box[0] = new AABB(minX, minY, minZ, maxX, maxY, maxZ));
        return box[0];
    }

    private static String turnLarge(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Rig rig = RIG.get();
        Placed placed = TURNED.get();
        if (rig == null || placed == null || !(level.getEntity(placed.id()) instanceof GateEntity large)) {
            return "großes stehendes Gate fehlt";
        }
        Vec3 home = player.position();
        Vec3 beside = rig.at(32.5, 0.0, 13.0);
        player.teleportTo(level, beside.x, beside.y, beside.z, Set.of(), 180.0F, 0.0F, true);
        try {
            if (GateMounting.rotate(player, large, false) != GateMounting.Result.ROTATED
                    || large.facingYaw() != GateGeometry.snapYaw(placed.yaw() + 90.0F) || large.position().distanceToSqr(placed.geometry().x(),
                    placed.geometry().y(), placed.geometry().z()) > 1.0E-10) {
                return "großes stehendes Gate nicht an Ort und Stelle um 90° gedreht: " + large.geometry();
            }
            TURNED.set(new Placed("standing_large_turned", large.getId(), large.mount(), large.flipped(), large.facingYaw(), large.geometry()));
            return null;
        } finally {
            player.teleportTo(level, home.x, home.y, home.z, Set.of(), player.getYRot(), player.getXRot(), true);
        }
    }

    private static @Nullable String clientMatches(Minecraft minecraft, Placed expected) {
        if (!(minecraft.level.getEntity(expected.id()) instanceof GateEntity gate)) {
            return "Gate fehlt beim Client";
        }
        if (gate.facingYaw() != expected.yaw()) {
            return "Client-Richtung " + gate.facingYaw() + " statt " + expected.yaw();
        }
        if (!close(gate.getBoundingBox(), bounds(expected.geometry()))) {
            return "Client-Box " + gate.getBoundingBox() + " statt " + bounds(expected.geometry());
        }
        return null;
    }

    private static StepResult turnedClientStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (TURNED.get() == null || minecraft.level == null) {
            return StepResult.fail("kein großes stehendes Gate");
        }
        int phase = (int) state.getOrDefault("phase", 0);
        if (phase == 0) {
            String problem = clientMatches(minecraft, TURNED.get());
            if (problem != null) {
                return ctx.tick() > 180 ? StepResult.fail("vor dem Drehen: " + problem) : StepResult.pending();
            }
            RESULT.set(null);
            state.put(FUTURE, ctx.onServer(server -> RESULT.set(turnLarge(server))));
            state.put("phase", 1);
            return StepResult.pending();
        }
        if (phase == 1) {
            StepResult waiting = future(ctx);
            if (waiting != null) {
                return waiting;
            }
            if (RESULT.get() != null) {
                return StepResult.fail(RESULT.get());
            }
            state.put("phase", 2);
            state.put("since", ctx.tick());
            return StepResult.pending();
        }
        if (phase == 2) {
            String problem = clientMatches(minecraft, TURNED.get());
            if (problem != null) {
                return ctx.tick() - (int) state.get("since") > 180 ? StepResult.fail("nach der Vierteldrehung: " + problem) : StepResult.pending();
            }
            ctx.log("Entity-Box beim Client nach einer Vierteldrehung an Ort und Stelle: " + gate(minecraft, TURNED.get()).getBoundingBox());
            int id = TURNED.get().id();
            state.put(FUTURE, ctx.onServer(server -> {
                Entity entity = player(server).level().getEntity(id);
                if (entity != null) {
                    entity.discard();
                }
            }));
            state.put("phase", 3);
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        return waiting != null ? waiting : StepResult.pass();
    }

    private static GateEntity gate(Minecraft minecraft, Placed placed) {
        return (GateEntity) minecraft.level.getEntity(placed.id());
    }

    private static String checkSurfaces(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Rig rig = RIG.get();
        if (rig == null) {
            return "kein Testbau";
        }
        BlockPos o = rig.origin();
        Vec3 home = player.position();
        BlockPos grass = o.offset(24, 0, 15);
        BlockPos flower = o.offset(36, 0, 15);
        BlockPos lichen = o.offset(58, WALL_HEIGHT - 2, 1);
        BlockPos slab = o.offset(58, WALL_HEIGHT - 2, 8);
        List<GateEntity> placed = new ArrayList<>();
        try {
            level.setBlockAndUpdate(grass.below(), Blocks.GRASS_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(flower.below(), Blocks.GRASS_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(grass, Blocks.SHORT_GRASS.defaultBlockState());
            level.setBlockAndUpdate(flower, Blocks.POPPY.defaultBlockState());
            level.setBlockAndUpdate(lichen, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true));
            level.setBlockAndUpdate(slab, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
            if (!level.getBlockState(grass).is(Blocks.SHORT_GRASS) || !level.getBlockState(flower).is(Blocks.POPPY)
                    || !level.getBlockState(lichen).is(Blocks.GLOW_LICHEN)) {
                return "Pflanzen für den Test nicht gesetzt";
            }
            GateEntity onGrass = placeAt(player, GateSize.MEDIUM, grass, Direction.NORTH,
                    new Vec3(grass.getX() + 0.5, grass.getY() + 0.4, grass.getZ() + 0.125), new Vec3(grass.getX() + 0.5, grass.getY(), grass.getZ() - 1.5));
            if (onGrass == null) {
                return "Gate an der Seite eines Grasbüschels nicht gesetzt";
            }
            placed.add(onGrass);
            if (onGrass.mount() != GateMount.STANDING || !near(onGrass.position(), Vec3.atBottomCenterOf(grass))) {
                return "Gate an der Seite eines Grasbüschels: " + onGrass.geometry() + " statt stehend bei " + Vec3.atBottomCenterOf(grass);
            }
            GateEntity onFlower = placeAt(player, GateSize.MEDIUM, flower, Direction.UP,
                    new Vec3(flower.getX() + 0.5, flower.getY() + 0.625, flower.getZ() + 0.5), new Vec3(flower.getX() + 0.5, flower.getY(), flower.getZ() - 2.0));
            if (onFlower == null) {
                return "Gate auf einer Blume nicht gesetzt";
            }
            placed.add(onFlower);
            if (onFlower.mount() != GateMount.STANDING || !near(onFlower.position(), Vec3.atBottomCenterOf(flower))) {
                return "Gate auf einer Blume: " + onFlower.geometry() + " statt stehend am Boden bei " + Vec3.atBottomCenterOf(flower);
            }
            GateEntity underLichen = placeAt(player, GateSize.MEDIUM, lichen, Direction.DOWN,
                    new Vec3(lichen.getX() + 0.5, lichen.getY() + 0.9375, lichen.getZ() + 0.5), new Vec3(lichen.getX() + 0.5, o.getY(), lichen.getZ() + 3.5));
            if (underLichen == null) {
                return "Gate unter Glühflechten nicht gesetzt";
            }
            placed.add(underLichen);
            GateGeometry hanging = underLichen.geometry();
            if (underLichen.mount() != GateMount.HANGING || Math.abs(hanging.y() + hanging.topExtent() - (lichen.getY() + 1)) > 1.0E-6) {
                return "Gate unter Glühflechten: " + hanging + " statt hängend an der Decke bei Y " + (lichen.getY() + 1);
            }
            GateEntity underSlab = placeAt(player, GateSize.MEDIUM, slab, Direction.DOWN,
                    new Vec3(slab.getX() + 0.5, slab.getY() + 0.5, slab.getZ() + 0.5), new Vec3(slab.getX() + 0.5, o.getY(), slab.getZ() + 3.5));
            if (underSlab == null) {
                return "Gate unter einer oberen Stufe nicht gesetzt";
            }
            placed.add(underSlab);
            GateGeometry slabGate = underSlab.geometry();
            if (underSlab.mount() != GateMount.HANGING || Math.abs(slabGate.y() + slabGate.topExtent() - (slab.getY() + 0.5)) > 1.0E-6) {
                return "Gate unter einer oberen Stufe: " + slabGate + " statt hängend an der Unterseite bei Y " + (slab.getY() + 0.5);
            }
            return null;
        } finally {
            placed.forEach(Entity::discard);
            for (BlockPos pos : List.of(grass, flower, lichen, slab)) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            level.setBlockAndUpdate(grass.below(), Blocks.SMOOTH_STONE.defaultBlockState());
            level.setBlockAndUpdate(flower.below(), Blocks.SMOOTH_STONE.defaultBlockState());
            player.teleportTo(level, home.x, home.y, home.z, Set.of(), player.getYRot(), player.getXRot(), true);
        }
    }

    private static boolean near(Vec3 a, Vec3 b) {
        return a.distanceToSqr(b) < 1.0E-10;
    }

    private static StepResult surfaceStep(SelfTest.StepContext ctx) {
        return onServer(ctx, GateMountSelfTests::checkSurfaces);
    }

    private static String checkArchive(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Rig rig = RIG.get();
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(rig.course());
        if (course == null) {
            return "Kurs fehlt auf dem Server";
        }
        if (!registry.records().hasEntries(course.id)) {
            return "keine Bestzeit nach der bestätigten Runde";
        }
        int layout = course.layout;
        int hash = course.toData().layoutHash();
        GateEntity hanging = (GateEntity) level.getEntity(rig.gates().get(HANGING).id());
        float yaw = hanging.facingYaw();
        Vec3 home = player.position();
        Vec3 under = rig.at(46.5, 0.0, 9.0);
        player.teleportTo(level, under.x, under.y, under.z, Set.of(), 180.0F, -20.0F, true);
        try {
            if (GateMounting.rotate(player, hanging, false) != GateMounting.Result.ROTATED) {
                return "hängendes Kurs-Gate nicht gedreht";
            }
            if (course.layout != layout + 1) {
                return "Layout nach dem Drehen " + course.layout + " statt " + (layout + 1);
            }
            if (registry.records().hasEntries(course.id)) {
                return "alte Zeiten stehen nach dem Drehen weiter auf der Bestenliste";
            }
            List<RaceRecords.Archived> archive = registry.records().archive(course.id);
            if (archive.isEmpty() || archive.getLast().layout() != layout) {
                return "Bestenliste des alten Layouts nicht archiviert: " + archive.size();
            }
            if (course.toData().layoutHash() == hash) {
                return "Layout-Prüfsumme nach dem Drehen unverändert";
            }
            for (int i = 0; i < 3; i++) {
                GateMounting.rotate(player, hanging, false);
            }
            if (hanging.facingYaw() != yaw || course.layout != layout + 1) {
                return "Drehen ohne neue Zeiten legt weitere Layouts an: " + course.layout;
            }
            return null;
        } finally {
            player.teleportTo(level, home.x, home.y, home.z, Set.of(), player.getYRot(), player.getXRot(), true);
        }
    }

    private static StepResult archiveStep(SelfTest.StepContext ctx) {
        return onServer(ctx, GateMountSelfTests::checkArchive);
    }

    private static StepResult courseStep(SelfTest.StepContext ctx) {
        Rig rig = RIG.get();
        if (rig == null) {
            return StepResult.fail("kein Testbau");
        }
        CourseData data = ClientCourses.get(rig.course());
        if (data == null || data.gates().size() != 3) {
            return ctx.tick() > 180 ? StepResult.fail("Kurs nicht beim Client: " + data) : StepResult.pending();
        }
        List<GateMount> mounts = data.gates().stream().map(CourseData.Gate::mount).toList();
        if (!mounts.equals(List.of(GateMount.STANDING, GateMount.WALL_DIVE, GateMount.HANGING))) {
            return ctx.tick() > 180 ? StepResult.fail("Kurs-Lagen beim Client: " + mounts) : StepResult.pending();
        }
        Minecraft minecraft = Minecraft.getInstance();
        for (int i = 0; i < 3; i++) {
            String name = List.of(STANDING, DIVE, HANGING).get(i);
            if (!(minecraft.level.getEntity(rig.gates().get(name).id()) instanceof GateEntity gate) || gate.gateIndex() != i
                    || GateRenderer.marking(gate) != (i == 0 ? GateRenderer.Marking.START : GateRenderer.Marking.NUMBER)) {
                return ctx.tick() > 180 ? StepResult.fail("Kurs-Gate " + name + " ohne Nummer") : StepResult.pending();
            }
        }
        return StepResult.pass();
    }

    public static List<Vec3> lapPath(List<GateGeometry> gates, boolean diveUpwards) {
        GateGeometry start = gates.get(0);
        GateGeometry dive = gates.get(1);
        GateGeometry hanging = gates.get(2);
        List<Vec3> path = new ArrayList<>();
        path.add(along(start, -4.0));
        path.add(along(start, 2.5));
        Vec3 over = new Vec3(dive.centerX(), dive.centerY() + 3.5, dive.centerZ());
        Vec3 below = new Vec3(dive.centerX(), dive.centerY() - 2.5, dive.centerZ());
        path.add(new Vec3(path.getLast().x, over.y, path.getLast().z));
        if (diveUpwards) {
            path.add(new Vec3(below.x, below.y, below.z + 6.0));
            path.add(below);
            path.add(over);
            path.add(new Vec3(over.x, over.y, over.z + 6.0));
        } else {
            path.add(over);
            path.add(below);
            path.add(new Vec3(below.x, below.y, below.z + 4.0));
        }
        path.add(along(hanging, -2.5));
        path.add(along(hanging, 2.5));
        path.add(new Vec3(path.getLast().x, start.centerY(), path.getLast().z + 2.0));
        path.add(along(start, -4.0));
        path.add(along(start, 2.5));
        return path;
    }

    private static Vec3 along(GateGeometry gate, double ahead) {
        return new Vec3(gate.centerX() + gate.normalX() * ahead, gate.centerY() + gate.normalY() * ahead, gate.centerZ() + gate.normalZ() * ahead);
    }

    private static List<LapTracker.Event> fly(LapTracker tracker, List<Vec3> path) {
        long now = 1_000L * MS;
        Vec3 at = path.getFirst();
        tracker.update(now, at.x, at.y, at.z);
        List<LapTracker.Event> events = new ArrayList<>();
        for (Vec3 target : path.subList(1, path.size())) {
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

    private static StepResult clientLapStep(SelfTest.StepContext ctx) {
        Rig rig = RIG.get();
        CourseData data = rig == null ? null : ClientCourses.get(rig.course());
        if (data == null) {
            return StepResult.fail("Kurs fehlt");
        }
        List<GateGeometry> gates = data.geometry();
        for (boolean upwards : new boolean[]{false, true}) {
            LapTracker tracker = new LapTracker();
            tracker.setLapTarget(0);
            tracker.setCourse(rig.course(), gates, 0L);
            List<LapTracker.Event> events = fly(tracker, lapPath(gates, upwards));
            List<Integer> passed = events.stream().filter(e -> e.type() == LapTracker.EventType.GATE).map(LapTracker.Event::gate).toList();
            long laps = events.stream().filter(e -> e.type() == LapTracker.EventType.LAP).count();
            if (!upwards && (!passed.equals(List.of(1, 2)) || laps != 1)) {
                return StepResult.fail("Tauchtor abwärts falsch gezählt: Gates " + passed + " Runden " + laps + " " + events);
            }
            if (upwards && (passed.contains(1) || laps != 0)) {
                return StepResult.fail("Tauchtor aufwärts gezählt: Gates " + passed + " Runden " + laps);
            }
        }
        return StepResult.pass();
    }

    private static RacePayloads.RaceReportC2S report(RacePayloads.ReportKind kind, int course, int lap, long lapNanos) {
        return new RacePayloads.RaceReportC2S(kind, course, lap, lapNanos, 0L);
    }

    private static void pass(ServerPlayer player, GateGeometry gate, boolean forward, long nanos) {
        Vec3 before = along(gate, forward ? -1.0 : 1.0);
        Vec3 after = along(gate, forward ? 1.0 : -1.0);
        RaceServer.onDroneMoved(player, before, after, nanos);
    }

    private static RaceServer.Verdict lap(ServerPlayer player, CourseRegistry.Course course, List<GateGeometry> gates, boolean diveForward,
                                          long start, long lap) {
        RaceServer.handle(report(RacePayloads.ReportKind.START, course.id, 0, 0L), player, start);
        long t = start + 100L * MS;
        pass(player, gates.get(0), true, t);
        pass(player, gates.get(1), diveForward, t + lap / 3L);
        pass(player, gates.get(2), true, t + lap * 2L / 3L);
        pass(player, gates.get(0), true, t + lap);
        return RaceServer.handle(report(RacePayloads.ReportKind.LAP, course.id, 1, lap), player, t + lap + 100L * MS);
    }

    private static String checkServerLap(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Rig rig = RIG.get();
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(rig.course());
        if (course == null) {
            return "Kurs fehlt auf dem Server";
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        CourseRegistry.GateRecord start = course.gates.getFirst();
        drone.snapTo(start.x, start.y + 1.5, start.z - 3.0, 0.0F, 0.0F);
        drone.setPilot(player);
        if (!level.addFreshEntity(drone)) {
            return "Drohne nicht gespawnt";
        }
        try {
            List<GateGeometry> gates = course.toData().geometry();
            long lap = Math.max(RaceServer.minimumLapNanos(course, player) * 3L, 6_000L * MS);
            long base = System.nanoTime() + 600_000L * MS;
            RaceServer.Verdict down = lap(player, course, gates, true, base, lap);
            if (down != RaceServer.Verdict.ACCEPTED) {
                return "Runde durchs Tauchtor abwärts nicht bestätigt: " + down;
            }
            long next = base + lap + 10_000L * MS;
            RaceServer.Verdict up = lap(player, course, gates, false, next, lap);
            if (up != RaceServer.Verdict.PENDING) {
                return "Runde mit Tauchtor aufwärts nicht zurückgehalten: " + up;
            }
            RaceServer.Verdict flushed = RaceServer.flush(player, next + lap + RaceServer.PENDING_TIMEOUT_NANOS + 500L * MS);
            if (flushed != RaceServer.Verdict.UNVERIFIED) {
                return "Runde mit Tauchtor aufwärts angenommen: " + flushed;
            }
            return null;
        } finally {
            RaceServer.handle(report(RacePayloads.ReportKind.ABORT, course.id, 0, 0L), player, System.nanoTime() + 900_000L * MS);
            drone.setPilot(null);
            drone.discard();
        }
    }

    private static StepResult serverLapStep(SelfTest.StepContext ctx) {
        return onServer(ctx, GateMountSelfTests::checkServerLap);
    }

    private static String checkPersistence(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Rig rig = RIG.get();
        ProblemReporter.Collector reporter = new ProblemReporter.Collector();
        for (String name : List.of(LAUNCH, WINDOW_OUT, HANGING_TURNED, CEILING_UP, FLOOR, STANDING)) {
            GateEntity gate = (GateEntity) level.getEntity(rig.gates().get(name).id());
            TagValueOutput output = TagValueOutput.createWithContext(reporter, server.registryAccess());
            gate.saveWithoutId(output);
            CompoundTag saved = output.buildResult();
            GateEntity copy = RaceModule.GATE.get().create(level, EntitySpawnReason.LOAD);
            copy.load(TagValueInput.create(reporter, server.registryAccess(), saved));
            if (copy.mount() != gate.mount() || copy.flipped() != gate.flipped() || !copy.geometry().equals(gate.geometry())) {
                return name + " nach dem Laden " + copy.mount() + "/" + copy.flipped() + " statt " + gate.mount() + "/" + gate.flipped();
            }
            if (!copy.getBoundingBox().equals(gate.getBoundingBox())) {
                return name + ": Entity-Box nach dem Laden " + copy.getBoundingBox();
            }
            CompoundTag legacy = saved.copy();
            legacy.remove("Mount");
            legacy.remove("Flipped");
            GateEntity old = RaceModule.GATE.get().create(level, EntitySpawnReason.LOAD);
            old.load(TagValueInput.create(reporter, server.registryAccess(), legacy));
            if (old.mount() != GateMount.STANDING || old.flipped()) {
                return name + ": alter Spielstand lädt nicht als stehendes Gate";
            }
        }
        if (!reporter.isEmpty()) {
            return "Meldungen beim Speichern/Laden: " + reporter.getReport();
        }
        return null;
    }

    private static StepResult persistenceStep(SelfTest.StepContext ctx) {
        return onServer(ctx, GateMountSelfTests::checkPersistence);
    }

    public static void discard(ServerLevel level, Rig rig) {
        for (int id : rig.ids()) {
            Entity gate = level.getEntity(id);
            if (gate != null) {
                gate.discard();
            }
        }
        CourseRegistry registry = CourseRegistry.get();
        if (registry != null && rig.course() != GateEntity.NO_COURSE) {
            registry.deleteCourse(rig.course());
        }
        fill(level, rig.origin().offset(-2, 0, CEILING_Z0 - 2), rig.origin().offset(CEILING_X1 + 2, WALL_HEIGHT + 2, 18),
                Blocks.AIR.defaultBlockState());
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
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
        Minecraft minecraft = Minecraft.getInstance();
        boolean gone = minecraft.level != null && rig.ids().stream().allMatch(id -> minecraft.level.getEntity(id) == null);
        if (!gone || ClientCourses.get(rig.course()) != null) {
            return ctx.tick() > 150 ? StepResult.fail("Gates oder Kurs noch beim Client") : StepResult.pending();
        }
        RIG.set(null);
        return StepResult.pass();
    }

    public static void registerScreenshots(Supplier<BlockPos> base) {
        Supplier<BlockPos> origin = () -> base.get().offset(-30, 0, -110);
        GateSize none = null;
        Screenshots.step("bildschirmfotos: Tauchtore an der Wand (abwärts, aufwärts, groß), eins als Start-/Ziel-Gate", "gate_mount_wall_dive", 40,
                new View(origin, new Vec3(10.5, 9.0, 11.0), new Vec3(10.5, 5.0, 2.5), none, null));
        Screenshots.step("bildschirmfotos: seitliche Wandgates und Fenster in der Wand", "gate_mount_wall_side_window", 30,
                new View(origin, new Vec3(31.0, 1.0, 16.0), new Vec3(31.0, 3.5, 2.0), none, null));
        Screenshots.step("bildschirmfotos: hängende und liegende Gates unter der Decke, eins als Kurs-Gate 2", "gate_mount_ceiling", 30,
                new View(origin, new Vec3(53.0, 0.0, -8.0), new Vec3(48.5, 5.5, 5.0), none, null));
        Screenshots.step("bildschirmfotos: liegende Deckengates von unten", "gate_mount_ceiling_below", 30,
                new View(origin, new Vec3(50.0, 0.0, 15.5), new Vec3(50.0, 8.5, 10.0), none, null));
        Screenshots.step("bildschirmfotos: stehendes Kurs-Gate 3 und liegende Gates über einer Grube", "gate_mount_floor", 30,
                new View(origin, new Vec3(10.5, 6.0, 24.0), new Vec3(10.5, 0.5, 12.5), none, null));
        Screenshots.step("bildschirmfotos: Vorschau eines Tauchtors beim Zielen auf die Wand", "gate_mount_preview", 20,
                new View(origin, new Vec3(19.0, 1.4, 4.0), new Vec3(16.5, 1.5, 1.0), GateSize.MEDIUM, true));
        Screenshots.step("bildschirmfotos: rote Vorschau, wenn das Tauchtor nicht passt", "gate_mount_preview_blocked", 20,
                new View(origin, new Vec3(15.0, 5.4, 4.0), new Vec3(12.5, 5.5, 1.0), GateSize.MEDIUM, false));
        SelfTest.clientStep("bildschirmfotos: Gates an Wand und Decke aufräumen", 200, GateMountSelfTests::cleanupShotsStep);
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

    private static final class View implements Screenshots.Scene {
        private final Supplier<BlockPos> origin;
        private final Vec3 feet;
        private final Vec3 target;
        private final @Nullable GateSize gate;
        private final @Nullable Boolean previewFree;
        private ItemStack previous = ItemStack.EMPTY;

        private View(Supplier<BlockPos> origin, Vec3 feet, Vec3 target, @Nullable GateSize gate, @Nullable Boolean previewFree) {
            this.origin = origin;
            this.feet = feet;
            this.target = target;
            this.gate = gate;
            this.previewFree = previewFree;
        }

        private ItemStack held() {
            return this.gate == null ? ItemStack.EMPTY : new ItemStack(RaceModule.gateItem(this.gate).orElseThrow());
        }

        private Vec3 world(Vec3 local) {
            BlockPos o = this.origin.get();
            return new Vec3(o.getX() + local.x, o.getY() + local.y, o.getZ() + local.z);
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(FUTURE)) {
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    if (SHOT_RIG.get() == null) {
                        SHOT_RIG.set(place(player, this.origin.get(), List.of(DIVE, HANGING, STANDING)));
                        RIG.set(null);
                    }
                    Vec3 at = this.world(this.feet);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.teleportTo(player.level(), at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
                    this.previous = player.getMainHandItem().copy();
                    player.setItemInHand(InteractionHand.MAIN_HAND, this.held());
                }));
                return StepResult.pending();
            }
            StepResult waiting = future(ctx);
            if (waiting != null) {
                return waiting;
            }
            Rig rig = SHOT_RIG.get();
            if (mc.player == null || mc.level == null || rig == null || PilotSession.INSTANCE.isActive()) {
                return ctx.tick() > 300 ? StepResult.fail("keine Szene für die Gate-Lagen") : StepResult.pending();
            }
            this.hold(ctx);
            if (mc.player.position().distanceTo(this.world(this.feet)) > 0.6 || !ItemStack.isSameItem(mc.player.getMainHandItem(), this.held())) {
                return ctx.tick() > 300 ? StepResult.fail("Spieler nicht am Blickpunkt") : StepResult.pending();
            }
            for (Placed placed : rig.gates().values()) {
                if (!(mc.level.getEntity(placed.id()) instanceof GateEntity gate) || gate.mount() != placed.mount()) {
                    return ctx.tick() > 300 ? StepResult.fail(placed.name() + " fehlt beim Client") : StepResult.pending();
                }
            }
            if (ClientCourses.get(rig.course()) == null) {
                return ctx.tick() > 300 ? StepResult.fail("Kurs fehlt beim Client") : StepResult.pending();
            }
            if (this.previewFree != null) {
                GatePreview.Target preview = GatePreview.target(mc);
                if (preview == null || preview.free() != this.previewFree || preview.geometry().mount() != GateMount.WALL_DIVE) {
                    return ctx.tick() > 300 ? StepResult.fail("Vorschau " + (preview == null ? "fehlt" : preview.free() + " " + preview.geometry()))
                            : StepResult.pending();
                }
            }
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
}
