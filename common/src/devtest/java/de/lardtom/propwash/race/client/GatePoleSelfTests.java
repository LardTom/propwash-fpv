package de.lardtom.propwash.race.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
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
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
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
import de.lardtom.propwash.race.RaceModule;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
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

public final class GatePoleSelfTests {
    private static final String FUTURE = "future";
    private static final String STAGE = "stage";
    private static final int SITE_X = 26;
    private static final int SITE_Z = -48;
    private static final int SIZE_X = 22;
    private static final int SIZE_Z = 28;
    private static final int HEIGHT = 20;
    private static final int SHOT_X = 60;
    private static final int SHOT_Z = -480;
    private static final double EPS = 1.0E-6;
    private static final AtomicReference<String> RESULT = new AtomicReference<>();
    private static final AtomicReference<Site> SITE = new AtomicReference<>();
    private static final AtomicReference<Site> SHOT_SITE = new AtomicReference<>();
    private static final AtomicInteger SHOT_DRONE = new AtomicInteger(-1);
    private static final AtomicReference<Vec3> SHOT_HOME = new AtomicReference<>();

    private GatePoleSelfTests() {
    }

    private record Site(BlockPos origin, Vec3 home, Map<BlockPos, BlockState> saved, Map<String, Integer> ids) {
        Vec3 at(double x, double y, double z) {
            return new Vec3(this.origin.getX() + x, this.origin.getY() + y, this.origin.getZ() + z);
        }

        @Nullable GateEntity entity(ServerLevel level, String name) {
            Integer id = this.ids.get(name);
            return id != null && level.getEntity(id) instanceof GateEntity gate && !gate.isRemoved() ? gate : null;
        }
    }

    public static void register() {
        SelfTest.clientStep("race: Gate-Stange an Boden, Wand und Decke setzen", 300, GatePoleSelfTests::placeStep);
        SelfTest.clientStep("race: Gate-Stange auf Gate-Ecke setzen, verlängern und Grenzen", 200, GatePoleSelfTests::seatStep);
        SelfTest.clientStep("race: Gate-Stange beim Client mit 3-Wege-Verbinder und Vorschau auf der Gate-Ecke", 400, GatePoleSelfTests::clientStep);
        SelfTest.clientStep("race: Gate-Stange einstellen, färben, speichern und Drohne prallt ab", 200, GatePoleSelfTests::editStep);
        SelfTest.clientStep("race: Gate-Stange rutscht mit dem Gate nach und kommt beim Abbau mit ab", 200, GatePoleSelfTests::carryStep);
        SelfTest.clientStep("race: Gate-Stange aufräumen", 200, GatePoleSelfTests::cleanupStep);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
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

    private static boolean loaded(ServerLevel level, BlockPos o) {
        for (int cx = (o.getX() - 2) >> 4; cx <= (o.getX() + SIZE_X + 2) >> 4; cx++) {
            for (int cz = (o.getZ() - 2) >> 4; cz <= (o.getZ() + SIZE_Z + 2) >> 4; cz++) {
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
        BlockPos o = site.origin();
        for (int x = -2; x <= SIZE_X; x++) {
            for (int z = -2; z <= SIZE_Z; z++) {
                set(level, site, new BlockPos(o.getX() + x, o.getY() - 1, o.getZ() + z), Blocks.GRASS_BLOCK.defaultBlockState());
                for (int y = 0; y < HEIGHT; y++) {
                    BlockPos pos = new BlockPos(o.getX() + x, o.getY() + y, o.getZ() + z);
                    if (!level.getBlockState(pos).isAir()) {
                        set(level, site, pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    private static void restore(ServerLevel level, Site site) {
        List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(site.saved().entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) {
            level.setBlockAndUpdate(entries.get(i).getKey(), entries.get(i).getValue());
        }
        site.saved().clear();
    }

    private static void discardAll(ServerLevel level, Site site) {
        discardIn(level, new AABB(site.at(-4, -2, -4), site.at(SIZE_X + 4, HEIGHT + 4, SIZE_Z + 4)));
    }

    private static void discardIn(ServerLevel level, AABB area) {
        for (GateEntity gate : level.getEntitiesOfClass(GateEntity.class, area, gate -> !gate.isRemoved())) {
            gate.discard();
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, item -> !item.isRemoved())) {
            item.discard();
        }
    }

    private static ItemStack poleStack() {
        return new ItemStack(RaceModule.GATE_POLE.get());
    }

    private static InteractionResult use(ServerPlayer player, BlockHitResult hit) {
        ItemStack previous = player.getMainHandItem().copy();
        ItemStack stack = poleStack();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            return stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
    }

    private static InteractionResult interact(ServerPlayer player, GateEntity target, ItemStack stack, Vec3 point) {
        ItemStack previous = player.getMainHandItem().copy();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            return target.interact(player, InteractionHand.MAIN_HAND, point.subtract(target.position()));
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
    }

    private static @Nullable GateEntity poleNear(ServerLevel level, Vec3 point) {
        List<GateEntity> found = level.getEntitiesOfClass(GateEntity.class, new AABB(point, point).inflate(0.05),
                gate -> !gate.isRemoved() && gate.kind() == CourseElement.GATE_POLE && gate.position().distanceTo(point) < 0.05);
        return found.size() == 1 ? found.getFirst() : null;
    }

    private static boolean near(AABB box, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return Math.abs(box.minX - minX) < EPS && Math.abs(box.minY - minY) < EPS && Math.abs(box.minZ - minZ) < EPS && Math.abs(box.maxX - maxX) < EPS
                && Math.abs(box.maxY - maxY) < EPS && Math.abs(box.maxZ - maxZ) < EPS;
    }

    private static void stand(ServerPlayer player, Vec3 at) {
        player.teleportTo(player.level(), at.x, at.y, at.z, Set.of(), player.getYRot(), player.getXRot(), true);
    }

    private static StepResult placeStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            RESULT.set(null);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                BlockPos o = new BlockPos(player.getBlockX() + SITE_X, player.getBlockY(), player.getBlockZ() + SITE_Z);
                Site site = new Site(o, player.position(), new LinkedHashMap<>(), new ConcurrentHashMap<>());
                SITE.set(site);
                stand(player, site.at(6.5, 0.0, 6.5));
            }));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (!ctx.state().containsKey(STAGE)) {
            CompletableFuture<?> check = ctx.onServer(server -> {
                ServerLevel level = player(server).level();
                Site site = SITE.get();
                if (!loaded(level, site.origin())) {
                    RESULT.set("lädt");
                    return;
                }
                RESULT.set(checkPlace(server));
            });
            ctx.state().put(STAGE, check);
            return StepResult.pending();
        }
        CompletableFuture<?> check = (CompletableFuture<?>) ctx.state().get(STAGE);
        if (check.isCompletedExceptionally()) {
            return StepResult.fail("Server-Aktion fehlgeschlagen: " + check.exceptionNow());
        }
        if (!check.isDone()) {
            return StepResult.pending();
        }
        String problem = RESULT.get();
        if ("lädt".equals(problem)) {
            ctx.state().remove(STAGE);
            return ctx.tick() > 250 ? StepResult.fail("Testgelände der Gate-Stangen lädt nicht") : StepResult.pending();
        }
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }

    private static @Nullable String checkPlace(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Site site = SITE.get();
        BlockPos o = site.origin();
        clear(level, site);
        for (int y = 0; y < 6; y++) {
            for (int z = 1; z <= 3; z++) {
                set(level, site, o.offset(0, y, z), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        for (int x = 8; x <= 12; x++) {
            for (int z = 0; z <= 4; z++) {
                set(level, site, o.offset(x, 6, z), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        InteractionResult ground = use(player, new BlockHitResult(site.at(4.5, 0.0, 8.5), Direction.UP, o.offset(4, -1, 8), false));
        GateEntity pole = poleNear(level, site.at(4.5, 0.0, 8.5));
        if (!ground.consumesAction() || pole == null) {
            return "Gate-Stange am Boden nicht gesetzt: " + ground;
        }
        site.ids().put("ground", pole.getId());
        GateGeometry g = pole.geometry();
        if (g.mount() != GateMount.STANDING || Math.abs(g.height() - GatePoles.DEFAULT_LENGTH) > EPS || pole.colliders().length != 1) {
            return "Gate-Stange am Boden falsch: " + g + ", Teile " + pole.colliders().length;
        }
        if (!near(pole.colliders()[0], o.getX() + 4.25, o.getY(), o.getZ() + 8.25, o.getX() + 4.75, o.getY() + 4.0, o.getZ() + 8.75)) {
            return "Kollision der Gate-Stange am Boden falsch: " + pole.colliders()[0];
        }
        if (!pole.getBoundingBox().inflate(EPS).contains(pole.colliders()[0].getMinPosition())) {
            return "Kollisionsteil außerhalb der Entity-Box";
        }
        if (pole.base() != GateStacking.Base.BAR) {
            return "freie Gate-Stange ohne Fuß: " + pole.base();
        }
        if (use(player, new BlockHitResult(site.at(4.5, 0.0, 8.5), Direction.UP, o.offset(4, -1, 8), false)).consumesAction()) {
            return "zweite Gate-Stange an derselben Stelle gesetzt";
        }
        InteractionResult wall = use(player, new BlockHitResult(site.at(1.0, 2.5, 2.5), Direction.EAST, o.offset(0, 2, 2), false));
        GateEntity out = poleNear(level, site.at(1.0, 2.5, 2.5));
        if (!wall.consumesAction() || out == null) {
            return "Gate-Stange an der Wand nicht gesetzt: " + wall;
        }
        site.ids().put("wall", out.getId());
        if (out.mount() != GateMount.WALL_DIVE || !near(out.colliders()[0], o.getX() + 1.0, o.getY() + 2.25, o.getZ() + 2.25, o.getX() + 5.0,
                o.getY() + 2.75, o.getZ() + 2.75)) {
            return "Gate-Stange an der Wand falsch: " + out.geometry() + " / " + out.colliders()[0];
        }
        InteractionResult ceiling = use(player, new BlockHitResult(site.at(10.5, 6.0, 2.5), Direction.DOWN, o.offset(10, 6, 2), false));
        GateEntity hanging = poleNear(level, site.at(10.5, 2.0, 2.5));
        if (!ceiling.consumesAction() || hanging == null) {
            return "Gate-Stange an der Decke nicht gesetzt: " + ceiling;
        }
        site.ids().put("ceiling", hanging.getId());
        if (hanging.mount() != GateMount.HANGING || !near(hanging.colliders()[0], o.getX() + 10.25, o.getY() + 2.0, o.getZ() + 2.25, o.getX() + 10.75,
                o.getY() + 6.0, o.getZ() + 2.75)) {
            return "Gate-Stange an der Decke falsch: " + hanging.geometry() + " / " + hanging.colliders()[0];
        }
        if (!GatePoles.seats(out.geometry()).isEmpty() || !GatePoles.seats(hanging.geometry()).isEmpty()) {
            return "Stangen an Wand oder Decke bieten Plätze zum Aufsetzen";
        }
        for (int y = 0; y < 7; y++) {
            for (int x = 17; x <= 19; x++) {
                set(level, site, o.offset(x, y, 5), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        InteractionResult north = use(player, new BlockHitResult(site.at(18.5, 2.5, 5.0), Direction.NORTH, o.offset(18, 2, 5), false));
        GateEntity northPole = poleNear(level, site.at(18.5, 2.5, 5.0));
        if (!north.consumesAction() || northPole == null) {
            return "Gate-Stange an der Nordseite einer Wand nicht gesetzt: " + north;
        }
        Vec3 shortAt = site.at(12.5, 3.0, 4.5);
        GateEntity shortHanging = spawn(level, GateGeometry.element(shortAt.x, shortAt.y, shortAt.z, 0.0F, CourseElement.GATE_POLE, GatePoles.WHITE,
                GatePoles.THICKNESS, 3.0, GateTilt.NONE, GateMount.HANGING), GateSize.MEDIUM);
        String[] names = {"Boden", "Wand Ost", "Wand Nord", "Decke 4,0", "Decke 3,0"};
        GateEntity[] poles = {pole, out, northPole, hanging, shortHanging};
        for (int i = 0; i < poles.length; i++) {
            String problem = lightProbe(level, names[i], poles[i]);
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    private static @Nullable String lightProbe(ServerLevel level, String name, GateEntity pole) {
        GateGeometry g = pole.geometry();
        BlockPos probe = BlockPos.containing(pole.getLightProbePosition(1.0F));
        BlockPos center = BlockPos.containing(g.centerX(), g.centerY(), g.centerZ());
        BlockState state = level.getBlockState(probe);
        if (!probe.equals(center) || state.isSolidRender()) {
            return "Lichtmesspunkt der Gate-Stange (" + name + ") liegt in " + probe.toShortString() + " = " + state + " statt in der Mitte "
                    + center.toShortString();
        }
        int light = Math.max(level.getBrightness(LightLayer.SKY, probe), level.getBrightness(LightLayer.BLOCK, probe));
        return light > 0 ? null : "Gate-Stange (" + name + ") wird schwarz gezeichnet: Licht 0 in " + probe.toShortString();
    }

    private static StepResult seatStep(SelfTest.StepContext ctx) {
        return onServer(ctx, GatePoleSelfTests::checkSeat);
    }

    private static @Nullable String checkSeat(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Site site = SITE.get();
        BlockPos o = site.origin();
        stand(player, site.at(4.5, 0.0, 9.5));
        GateEntity gate = GateStackSelfTests.place(player, GateSize.MEDIUM, o.getX() + 4, o.getY(), o.getZ() + 12, 0.0F);
        site.ids().put("gate", gate.getId());
        GateEntity preview = GateStackSelfTests.place(player, GateSize.SMALL, o.getX() + 14, o.getY(), o.getZ() + 12, 0.0F);
        site.ids().put("preview", preview.getId());
        List<GatePoles.Seat> seats = GatePoles.seats(gate.geometry());
        GatePoles.Seat left = seats.get(0);
        GatePoles.Seat right = seats.get(1);
        Vec3 rightPoint = new Vec3(right.x(), right.y() - 0.3, right.z());
        if (!interact(player, gate, poleStack(), rightPoint).consumesAction()) {
            return "Gate-Stange nicht auf die Gate-Ecke gesetzt";
        }
        GateEntity first = GatePoleLinks.riderOn(level, right);
        if (first == null || !GatePoles.restsOn(first.geometry(), gate.geometry())) {
            return "Gate-Stange sitzt nicht auf der rechten Ecke";
        }
        site.ids().put("first", first.getId());
        first.refreshStack();
        gate.refreshStack();
        if (first.base() != GateStacking.Base.STACKED || GatePoleLinks.support(first) != gate) {
            return "Gate-Stange auf dem Gate ohne 3-Wege-Verbinder: " + first.base();
        }
        if (!gate.carrying() || !gate.inStack() || gate.base() != GateStacking.Base.BAR) {
            return "Gate mit Stange: trägt " + gate.carrying() + ", Stapel " + gate.inStack() + ", Fuß " + gate.base();
        }
        if (!interact(player, gate, poleStack(), rightPoint).consumesAction()) {
            return "zweite Stange über das Gate nicht verlängert";
        }
        GateEntity second = GatePoleLinks.riderOn(level, GatePoles.seats(first.geometry()).getFirst());
        if (second == null) {
            return "zweite Stange sitzt nicht auf der ersten";
        }
        if (!interact(player, second, poleStack(), second.position().add(0.0, 1.0, 0.0)).consumesAction()
                || !interact(player, first, poleStack(), first.position().add(0.0, 1.0, 0.0)).consumesAction()) {
            return "Stange über eine Stange nicht verlängert";
        }
        GateEntity top = GatePoleLinks.top(first);
        if (GatePoleLinks.chainBelow(top) != GatePoles.MAX_CHAIN || Math.abs(top.getY() - (right.y() + 3.0 * GatePoles.DEFAULT_LENGTH)) > EPS) {
            return "Turm aus Stangen falsch: " + GatePoleLinks.chainBelow(top) + " Stangen, oben y=" + top.getY();
        }
        site.ids().put("top", top.getId());
        if (interact(player, top, poleStack(), top.position()).consumesAction()) {
            return "fünfte Stange übereinander gesetzt";
        }
        if (!interact(player, gate, poleStack(), new Vec3(left.x(), left.y() - 0.3, left.z())).consumesAction()) {
            return "Gate-Stange nicht auf die linke Ecke gesetzt";
        }
        GateEntity other = GatePoleLinks.riderOn(level, left);
        if (other == null) {
            return "Gate-Stange sitzt nicht auf der linken Ecke";
        }
        site.ids().put("left", other.getId());
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        CourseToolItem.useOnGate(tool, player, first);
        CourseRegistry registry = CourseRegistry.get();
        if (CourseToolItem.courseOf(tool) != GateEntity.NO_COURSE || (registry != null && registry.courseOf(first.getUUID()) != GateEntity.NO_COURSE)
                || first.inCourse()) {
            return "Kurs-Werkzeug hat die Gate-Stange in einen Kurs genommen";
        }
        if (GateMounting.edit(player, gate, 0.0F, 0.0F, 0.0F, 5.0, 5.0) != GateMounting.Result.STACK_LOCKED) {
            return "Gate mit Stangen ließ sich verstellen";
        }
        if (GateMounting.edit(player, first, first.geometry().heading(), 0.0F, 0.0F, 1.0, 6.0, first.variant())
                != GateMounting.Result.STACK_LOCKED) {
            return "Stange unter einer Stange ließ sich verlängern";
        }
        if (GateMounting.edit(player, first, first.geometry().heading(), 0.0F, 0.0F, 1.0, first.height(), 14) != GateMounting.Result.EDITED
                || first.variant() != 14) {
            return "Farbe einer Stange mit Stange darauf nicht geändert: " + first.variant();
        }
        if (GateMounting.rotate(player, gate, false) != GateMounting.Result.ROTATED || gate.facingYaw() != 180.0F) {
            return "Gate mit Stangen nicht umgedreht: " + gate.facingYaw();
        }
        if (!GatePoles.restsOn(first.geometry(), gate.geometry()) || !GatePoles.restsOn(other.geometry(), gate.geometry())) {
            return "Stangen sitzen nach dem Umdrehen nicht mehr auf den Ecken";
        }
        return null;
    }

    private static StepResult clientStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Site site = SITE.get();
        if (site == null || mc.level == null || mc.player == null) {
            return StepResult.fail("kein Testgelände");
        }
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                stand(player, site.at(14.5, 0.0, 9.6));
                player.setItemInHand(InteractionHand.MAIN_HAND, poleStack());
            }));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        GateRenderer renderer = GateRenderer.current();
        if (renderer == null || !renderer.piecesPresent()) {
            return StepResult.fail("Gate-Teile für die Stange fehlen");
        }
        Integer firstId = site.ids().get("first");
        Integer gateId = site.ids().get("gate");
        Integer previewId = site.ids().get("preview");
        if (firstId == null || gateId == null || previewId == null) {
            return StepResult.fail("Stangen aus dem vorigen Schritt fehlen");
        }
        if (!(mc.level.getEntity(firstId) instanceof GateEntity first) || !(mc.level.getEntity(gateId) instanceof GateEntity gate)
                || !(mc.level.getEntity(previewId) instanceof GateEntity small)) {
            return ctx.tick() > 300 ? StepResult.fail("Stange oder Gate fehlt beim Client") : StepResult.pending();
        }
        if (first.kind() != CourseElement.GATE_POLE || first.variant() != 14 || !GatePoles.restsOn(first.geometry(), gate.geometry())
                || first.base() != GateStacking.Base.STACKED) {
            return ctx.tick() > 300 ? StepResult.fail("Stange beim Client: " + first.geometry() + ", Fuß " + first.base()) : StepResult.pending();
        }
        LocalPlayer player = mc.player;
        GatePoles.Seat seat = GatePoles.seats(small.geometry()).get(1);
        aim(player, new Vec3(seat.x(), seat.y() - 0.35, seat.z()));
        if (!(mc.hitResult instanceof EntityHitResult hit) || hit.getEntity() != small || !(player.getMainHandItem().getItem() == RaceModule.GATE_POLE.get())) {
            return ctx.tick() > 350 ? StepResult.fail("Blick trifft das kleine Gate nicht: " + mc.hitResult) : StepResult.pending();
        }
        GatePreview.Target target = GatePreview.target(mc);
        GateGeometry expected = GatePoles.onSeat(seat, GateMountKeys.preference().variant(CourseElement.GATE_POLE), GatePoles.DEFAULT_LENGTH);
        if (target == null || !target.seated() || !target.free() || !target.geometry().sameAs(expected)) {
            return ctx.tick() > 350 ? StepResult.fail("Vorschau auf der Gate-Ecke: " + (target == null ? "keine" : target.geometry() + " frei "
                    + target.free())) : StepResult.pending();
        }
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
    }

    private static StepResult editStep(SelfTest.StepContext ctx) {
        return onServer(ctx, GatePoleSelfTests::checkEdit);
    }

    private static @Nullable String checkEdit(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Site site = SITE.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, poleStack());
        stand(player, site.at(6.5, 0.0, 4.5));
        GateEntity ground = site.entity(level, "ground");
        GateEntity wall = site.entity(level, "wall");
        GateEntity hanging = site.entity(level, "ceiling");
        if (ground == null || wall == null || hanging == null) {
            return "Stangen aus dem ersten Schritt fehlen";
        }
        if (GateMounting.edit(player, ground, 30.0F, 0.0F, 0.0F, 1.0, 6.0, 11) != GateMounting.Result.EDITED) {
            return "Gate-Stange nicht eingestellt";
        }
        GateGeometry edited = ground.geometry();
        if (Math.abs(edited.height() - 6.0) > EPS || Math.abs(edited.heading() - 30.0F) > 1.0E-3F || ground.variant() != 11 || !ground.customOpening()
                || Math.abs(ground.getY() - site.origin().getY()) > EPS) {
            return "Einstellung der Stange fehlt: " + edited;
        }
        if (GateMounting.edit(player, hanging, hanging.geometry().heading(), 0.0F, 0.0F, 1.0, 3.0, 2) != GateMounting.Result.EDITED
                || Math.abs(hanging.getY() + hanging.height() - (site.origin().getY() + 6.0)) > EPS) {
            return "hängende Stange beim Kürzen nicht an der Decke geblieben: " + hanging.geometry();
        }
        ProblemReporter.Collector reporter = new ProblemReporter.Collector();
        for (GateEntity source : List.of(ground, wall, hanging)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, server.registryAccess());
            source.saveWithoutId(output);
            CompoundTag tag = output.buildResult();
            if (!"gate_pole".equals(tag.getStringOr("Kind", ""))) {
                return "Art der Stange nicht gespeichert: " + tag;
            }
            GateEntity copy = RaceModule.GATE.get().create(level, EntitySpawnReason.LOAD);
            if (copy == null) {
                return "Kopie nicht erzeugt";
            }
            copy.load(TagValueInput.create(reporter, server.registryAccess(), tag));
            if (!copy.geometry().equals(source.geometry()) || !copy.getBoundingBox().equals(source.getBoundingBox())) {
                return "Stange nach dem Laden anders: " + copy.geometry() + " statt " + source.geometry();
            }
        }
        if (!reporter.isEmpty()) {
            return "Meldungen beim Speichern/Laden: " + reporter.getReport();
        }
        if (!interact(player, ground, new ItemStack(Items.DYE.pick(DyeColor.RED)), ground.position()).consumesAction() || ground.variant() != 14) {
            return "Farbstoff färbt die Stange nicht: " + ground.variant();
        }
        if (GatePlacement.rotated(ground.geometry(), true, false, 90.0F).variant() != 15) {
            return "Schleichen + R wechselt die Farbe nicht";
        }
        return checkDrone(level, ground, wall);
    }

    private static @Nullable String checkDrone(ServerLevel level, GateEntity ground, GateEntity wall) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setNoGravity(true);
        try {
            GateGeometry pole = ground.geometry();
            Vec3 start = new Vec3(pole.x() - 1.5, pole.y() + 2.0, pole.z());
            drone.snapTo(start.x, start.y, start.z, 0.0F, 0.0F);
            if (!level.addFreshEntity(drone)) {
                return "Drohne nicht gespawnt";
            }
            drone.move(MoverType.SELF, new Vec3(3.0, 0.0, 0.0));
            if (drone.position().distanceTo(start.add(3.0, 0.0, 0.0)) < 0.5) {
                return "Drohne fliegt durch die Gate-Stange";
            }
            GateGeometry out = wall.geometry();
            Vec3 above = new Vec3(out.centerX(), out.centerY() + 1.5, out.centerZ());
            drone.snapTo(above.x, above.y, above.z, 0.0F, 0.0F);
            drone.move(MoverType.SELF, new Vec3(0.0, -3.0, 0.0));
            if (drone.position().distanceTo(above.add(0.0, -3.0, 0.0)) < 0.5) {
                return "Drohne fliegt durch die Stange an der Wand";
            }
        } finally {
            drone.discard();
        }
        return null;
    }

    private static StepResult carryStep(SelfTest.StepContext ctx) {
        return onServer(ctx, GatePoleSelfTests::checkCarry);
    }

    private static int poleItems(ServerLevel level, AABB area) {
        int count = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, item -> !item.isRemoved())) {
            if (item.getItem().is(RaceModule.GATE_POLE.get())) {
                count += item.getItem().getCount();
            }
        }
        return count;
    }

    private static @Nullable String checkCarry(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Site site = SITE.get();
        BlockPos o = site.origin();
        stand(player, site.at(4.5, 0.0, 17.0));
        boolean sneaking = player.isShiftKeyDown();
        GameType mode = player.gameMode();
        try {
            GateEntity bottom = GateStackSelfTests.place(player, GateSize.MEDIUM, o.getX() + 4, o.getY(), o.getZ() + 21, 0.0F);
            GateEntity upper = GateStackSelfTests.stacked(player, bottom, GateSize.SMALL, 0.0F);
            GatePoles.Seat seat = GatePoles.seats(upper.geometry()).get(1);
            if (!interact(player, upper, poleStack(), new Vec3(seat.x(), seat.y() - 0.3, seat.z())).consumesAction()) {
                return "Stange nicht auf das obere Gate gesetzt";
            }
            GateEntity low = GatePoleLinks.riderOn(level, seat);
            if (low == null || !interact(player, low, poleStack(), low.position()).consumesAction()) {
                return "Stange auf dem oberen Gate nicht verlängert";
            }
            GateEntity high = GatePoleLinks.riderOn(level, GatePoles.seats(low.geometry()).getFirst());
            if (high == null) {
                return "zweite Stange fehlt";
            }
            double drop = upper.getY() - bottom.getY();
            player.setShiftKeyDown(true);
            if (!bottom.hurtServer(level, level.damageSources().playerAttack(player), 1.0F) || !bottom.isRemoved()) {
                return "unteres Gate nicht abgebaut";
            }
            if (Math.abs(upper.getY() - o.getY()) > EPS || !GatePoles.restsOn(low.geometry(), upper.geometry())
                    || !GatePoles.restsOn(high.geometry(), low.geometry()) || low.isRemoved() || high.isRemoved()) {
                return "Stangen nicht mit dem Gate nachgerutscht (" + drop + "): " + low.geometry() + " / " + upper.geometry();
            }
            AABB area = upper.getBoundingBox().inflate(6.0);
            int before = poleItems(level, area);
            player.setGameMode(GameType.SURVIVAL);
            if (!upper.hurtServer(level, level.damageSources().playerAttack(player), 1.0F) || !upper.isRemoved()) {
                return "oberes Gate nicht abgebaut";
            }
            if (!low.isRemoved() || !high.isRemoved()) {
                return "Stangen auf dem abgebauten Gate stehen noch";
            }
            if (poleItems(level, area) - before != 2) {
                return "beim Abbau im Überleben " + (poleItems(level, area) - before) + " Stangen-Items statt 2";
            }
            player.setGameMode(GameType.CREATIVE);
            GateEntity gate = site.entity(level, "gate");
            List<GateEntity> riders = new ArrayList<>();
            for (String name : List.of("first", "top", "left")) {
                GateEntity pole = site.entity(level, name);
                if (pole == null) {
                    return "Stange " + name + " fehlt vor dem Entfernen des Gates";
                }
                riders.add(pole);
            }
            if (gate == null) {
                return "Gate mit dem Stangenturm fehlt";
            }
            AABB tower = gate.getBoundingBox().inflate(4.0, 20.0, 4.0);
            int towerItems = poleItems(level, tower);
            gate.discard();
            for (GateEntity pole : riders) {
                if (!pole.isRemoved()) {
                    return "Stange bleibt nach dem Entfernen des Gates stehen";
                }
            }
            if (!level.getEntitiesOfClass(GateEntity.class, tower, g -> !g.isRemoved() && g.kind() == CourseElement.GATE_POLE
                    && Math.hypot(g.getX() - gate.getX(), g.getZ() - gate.getZ()) < 3.0).isEmpty()) {
                return "Stangen aus dem Turm bleiben stehen";
            }
            if (poleItems(level, tower) != towerItems) {
                return "Entfernen ohne Spieler lässt Stangen fallen";
            }
            return null;
        } finally {
            player.setShiftKeyDown(sneaking);
            player.setGameMode(mode);
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.pass();
        }
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                discardAll(level, site);
                restore(level, site);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                stand(player, site.home());
            }));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        SITE.set(null);
        return StepResult.pass();
    }

    public static void registerScreenshots(Supplier<BlockPos> base) {
        Supplier<BlockPos> origin = () -> base.get().offset(SHOT_X, 0, SHOT_Z);
        Screenshots.step("bildschirmfotos: Gate-Stange am Boden in drei Farben", "gate_pole_ground", 40,
                new ShotView(origin, new Vec3(-7.5, 1.2, -5.0), new Vec3(-7.5, 2.0, 0.5), null, false));
        Screenshots.step("bildschirmfotos: Gate-Stange auf der Gate-Ecke wie bei MultiGP", "gate_pole_multigp", 40,
                new ShotView(origin, new Vec3(13.0, 2.4, -9.5), new Vec3(12.5, 5.2, 0.5), null, false));
        Screenshots.step("bildschirmfotos: Gate-Stangen gestapelt auf Gate und Boden", "gate_pole_stacked", 40,
                new ShotView(origin, new Vec3(33.0, 3.0, -10.0), new Vec3(33.0, 6.0, 0.5), null, false));
        Screenshots.step("bildschirmfotos: Gate-Stangen an Wand und Decke", "gate_pole_wall_ceiling", 40,
                new ShotView(origin, new Vec3(46.5, 2.0, -3.0), new Vec3(53.0, 2.8, 2.5), null, false));
        Screenshots.step("bildschirmfotos: Gate-Stange einstellen im Spiel", "gate_pole_edit_mode", 40,
                new ShotView(origin, new Vec3(-5.5, 0.0, -3.5), new Vec3(-6.0, 1.8, 0.5), () -> new ItemStack(RaceModule.GATE_POLE.get()), true));
        Screenshots.step("bildschirmfotos: Gate-Stange auf dem Gate im FPV", "gate_pole_fpv", 40, new FpvView(origin));
        SelfTest.clientStep("bildschirmfotos: Gate-Stangen aufräumen", 300, GatePoleSelfTests::cleanupShotsStep);
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

    private static GateEntity poleOn(ServerLevel level, GateGeometry support, int corner, int color, double length) {
        GatePoles.Seat seat = null;
        for (GatePoles.Seat candidate : GatePoles.seats(support)) {
            if (candidate.corner() == corner) {
                seat = candidate;
            }
        }
        if (seat == null) {
            throw new IllegalStateException("kein Platz auf " + support);
        }
        return spawn(level, GatePoles.onSeat(seat, color, length), GateSize.MEDIUM);
    }

    private static GateGeometry ground(Site site, double x, double z, float yaw, int color, double length) {
        Vec3 at = site.at(x, 0.0, z);
        return GateGeometry.element(at.x, at.y, at.z, GateGeometry.snapYaw(yaw), CourseElement.GATE_POLE, color, GatePoles.THICKNESS, length,
                new GateTilt(yaw - GateGeometry.snapYaw(yaw), 0.0F, 0.0F), GateMount.STANDING);
    }

    private static void buildShots(ServerLevel level, Site site) {
        clear(level, site);
        BlockPos o = site.origin();
        spawn(level, ground(site, -9.5, 0.5, 0.0F, GatePoles.WHITE, 4.0), GateSize.MEDIUM);
        spawn(level, ground(site, -7.5, 1.5, 20.0F, 14, 3.0), GateSize.MEDIUM);
        spawn(level, ground(site, -5.5, 0.5, 45.0F, 11, 5.0), GateSize.MEDIUM);
        Vec3 g = site.at(12.5, 0.0, 0.5);
        GateGeometry multigp = GateGeometry.of(g.x, g.y, g.z, 0.0F, GateSize.MEDIUM, GateMount.STANDING, false, 7.0, 6.0, GateTilt.NONE);
        spawn(level, multigp, GateSize.MEDIUM);
        poleOn(level, multigp, -1, GatePoles.WHITE, 5.0);
        Vec3 t = site.at(30.5, 0.0, 0.5);
        GateGeometry tower = GateGeometry.of(t.x, t.y, t.z, 0.0F, GateSize.MEDIUM);
        spawn(level, tower, GateSize.MEDIUM);
        for (int corner = -1; corner <= 1; corner += 2) {
            GateEntity lower = poleOn(level, tower, corner, 14, 3.0);
            poleOn(level, lower.geometry(), 0, GatePoles.WHITE, 3.0);
        }
        GateEntity a = spawn(level, ground(site, 35.5, 0.5, 0.0F, 1, 3.0), GateSize.MEDIUM);
        GateEntity b = poleOn(level, a.geometry(), 0, GatePoles.WHITE, 3.0);
        poleOn(level, b.geometry(), 0, 1, 3.0);
        for (int x = 50; x <= 56; x++) {
            for (int y = 0; y < 7; y++) {
                set(level, site, o.offset(x, y, 4), Blocks.STONE_BRICKS.defaultBlockState());
            }
            for (int z = 0; z <= 3; z++) {
                if (x >= 54) {
                    set(level, site, o.offset(x, 6, z), Blocks.STONE_BRICKS.defaultBlockState());
                }
            }
        }
        Vec3 w = site.at(51.5, 2.5, 4.0);
        spawn(level, GateGeometry.element(w.x, w.y, w.z, 180.0F, CourseElement.GATE_POLE, 9, GatePoles.THICKNESS, 3.0, GateTilt.NONE,
                GateMount.WALL_DIVE), GateSize.MEDIUM);
        Vec3 c = site.at(55.5, 6.0 - 3.5, 1.5);
        spawn(level, GateGeometry.element(c.x, c.y, c.z, 0.0F, CourseElement.GATE_POLE, 4, GatePoles.THICKNESS, 3.5, GateTilt.NONE,
                GateMount.HANGING), GateSize.MEDIUM);
        for (int y = 0; y < 2; y++) {
            set(level, site, o.offset(12, y, -9), Blocks.STONE_BRICKS.defaultBlockState());
        }
    }

    private static StepResult cleanupShotsStep(SelfTest.StepContext ctx) {
        Site site = SHOT_SITE.get();
        if (site == null && SHOT_DRONE.get() < 0) {
            return StepResult.pass();
        }
        if (ctx.tick() == 0) {
            PilotSession.INSTANCE.clearTestInput();
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                releaseDrone(player);
                if (site != null) {
                    discardIn(level, new AABB(site.at(-16, -2, -16), site.at(62, HEIGHT + 4, 12)));
                    restore(level, site);
                }
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                Vec3 home = SHOT_HOME.getAndSet(null);
                if (home != null) {
                    stand(player, home);
                }
            }));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (PilotSession.INSTANCE.isActive()) {
            return ctx.tick() > 250 ? StepResult.fail("FPV-Sitzung endet nicht") : StepResult.pending();
        }
        SHOT_SITE.set(null);
        return StepResult.pass();
    }

    private static void releaseDrone(ServerPlayer player) {
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        int id = SHOT_DRONE.getAndSet(-1);
        if (id >= 0 && player.level().getEntity(id) instanceof QuadcopterEntity drone) {
            drone.discard();
        }
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
    }

    private static StepResult prepareShots(SelfTest.StepContext ctx, Supplier<BlockPos> origin, Vec3 feet) {
        if (!ctx.state().containsKey(FUTURE)) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                SHOT_HOME.compareAndSet(null, player.position());
                BlockPos o = origin.get();
                Vec3 at = new Vec3(o.getX() + feet.x, o.getY() + feet.y, o.getZ() + feet.z);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(player.level(), at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
            }));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (SHOT_SITE.get() == null) {
            if (!ctx.state().containsKey(STAGE)) {
                ctx.state().put(STAGE, ctx.onServer(server -> {
                    ServerLevel level = player(server).level();
                    BlockPos o = origin.get();
                    if (SHOT_SITE.get() == null && loaded(level, o.offset(-12, 0, -12)) && loaded(level, o.offset(10, 0, -12))
                            && loaded(level, o.offset(34, 0, -12))) {
                        Site site = new Site(o, Vec3.ZERO, new LinkedHashMap<>(), new ConcurrentHashMap<>());
                        buildShots(level, site);
                        SHOT_SITE.set(site);
                    }
                }));
                return StepResult.pending();
            }
            CompletableFuture<?> stage = (CompletableFuture<?>) ctx.state().get(STAGE);
            if (stage.isCompletedExceptionally()) {
                return StepResult.fail("Szene der Gate-Stangen: " + stage.exceptionNow());
            }
            if (stage.isDone()) {
                ctx.state().remove(STAGE);
            }
            return ctx.tick() > 300 ? StepResult.fail("Szene der Gate-Stangen lädt nicht") : StepResult.pending();
        }
        return null;
    }

    private static final class ShotView implements Screenshots.Scene {
        private final Supplier<BlockPos> origin;
        private final Vec3 feet;
        private final Vec3 target;
        private final @Nullable Supplier<ItemStack> held;
        private final boolean edit;

        private ShotView(Supplier<BlockPos> origin, Vec3 feet, Vec3 target, @Nullable Supplier<ItemStack> held, boolean edit) {
            this.origin = origin;
            this.feet = feet;
            this.target = target;
            this.held = held;
            this.edit = edit;
        }

        private Vec3 world(Vec3 local) {
            BlockPos o = this.origin.get();
            return new Vec3(o.getX() + local.x, o.getY() + local.y, o.getZ() + local.z);
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            StepResult waiting = prepareShots(ctx, this.origin, this.feet);
            if (waiting != null) {
                return waiting;
            }
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey("held")) {
                Supplier<ItemStack> held = this.held;
                ctx.state().put("held", ctx.onServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND,
                        held == null ? ItemStack.EMPTY : held.get())));
                return StepResult.pending();
            }
            if (mc.player == null || mc.level == null || mc.player.position().distanceTo(this.world(this.feet)) > 0.6) {
                return ctx.tick() > 300 ? StepResult.fail("Spieler nicht am Blickpunkt") : StepResult.pending();
            }
            this.hold(ctx);
            List<GateEntity> poles = mc.level.getEntitiesOfClass(GateEntity.class, new AABB(this.world(new Vec3(-14, -1, -4)),
                    this.world(new Vec3(60, HEIGHT, 8))), gate -> gate.kind() == CourseElement.GATE_POLE);
            if (poles.size() < 13) {
                return ctx.tick() > 300 ? StepResult.fail("Gate-Stangen fehlen beim Client: " + poles.size()) : StepResult.pending();
            }
            if (this.edit) {
                GateEntity pole = null;
                for (GateEntity candidate : poles) {
                    if (candidate.position().distanceTo(this.world(new Vec3(-5.5, 0.0, 0.5))) < 0.1) {
                        pole = candidate;
                    }
                }
                if (pole == null || mc.player.getMainHandItem().getItem() != RaceModule.GATE_POLE.get()) {
                    return ctx.tick() > 300 ? StepResult.fail("Stange zum Einstellen fehlt") : StepResult.pending();
                }
                GateEditor editor = GateEditor.current();
                if (editor == null || editor.gate() != pole) {
                    GateEditor.start(pole);
                    editor = GateEditor.current();
                    if (editor == null) {
                        return StepResult.fail("Einstellen startet nicht");
                    }
                    editor.set(pole.geometry().heading(), 0.0F, 0.0F, 1.0, 6.5);
                    editor.setVariant(13);
                    editor.model().select(GateEditModel.Row.VARIANT);
                }
                if (editor.status() != GateEditor.Status.FREE) {
                    return ctx.tick() > 300 ? StepResult.fail("Vorschau beim Einstellen nicht frei: " + editor.status()) : StepResult.pending();
                }
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                aim(player, this.world(this.target));
                player.setYBodyRot(player.getYRot());
                player.yBodyRotO = player.getYRot();
                player.yHeadRotO = player.getYRot();
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            if (this.edit) {
                GateEditor.stop();
            }
            ctx.onServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        }
    }

    private static final class FpvView implements Screenshots.Scene {
        private final Supplier<BlockPos> origin;

        private FpvView(Supplier<BlockPos> origin) {
            this.origin = origin;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            StepResult waiting = prepareShots(ctx, this.origin, new Vec3(12.5, 0.0, -11.5));
            if (waiting != null) {
                return waiting;
            }
            if (!ctx.state().containsKey("drone")) {
                Supplier<BlockPos> origin = this.origin;
                ctx.state().put("drone", ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    releaseDrone(player);
                    QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
                    if (drone == null) {
                        throw new IllegalStateException("Drohne nicht erzeugt");
                    }
                    BlockPos o = origin.get();
                    Vec3 at = new Vec3(o.getX() + 12.5, o.getY() + 2.02, o.getZ() - 8.01);
                    drone.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
                    drone.setBuild(DroneBuild.FREESTYLE);
                    drone.setBatteryCharge(1.0F);
                    if (!level.addFreshEntity(drone)) {
                        throw new IllegalStateException("Drohne nicht in die Welt gesetzt");
                    }
                    drone.resetTo(at, new Quaternionf().rotationY((float) Math.PI));
                    SHOT_DRONE.set(drone.getId());
                    ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
                    goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
                    player.setItemSlot(EquipmentSlot.HEAD, goggles);
                    ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
                    remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
                    player.setItemSlot(EquipmentSlot.MAINHAND, remote);
                    DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
                    if (result != DronePiloting.StartResult.STARTED) {
                        throw new IllegalStateException("FPV-Start verweigert: " + result);
                    }
                }));
                return StepResult.pending();
            }
            CompletableFuture<?> started = (CompletableFuture<?>) ctx.state().get("drone");
            if (started.isCompletedExceptionally()) {
                return StepResult.fail("FPV vor dem Gate: " + started.exceptionNow());
            }
            if (!started.isDone()) {
                return StepResult.pending();
            }
            PilotSession session = PilotSession.INSTANCE;
            QuadcopterEntity drone = session.drone();
            if (!session.isFpv() || drone == null || drone.getId() != SHOT_DRONE.get()) {
                return ctx.tick() > 400 ? StepResult.fail("keine FPV-Sitzung vor dem Gate") : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            PilotSession.INSTANCE.clearTestInput();
            ctx.onServer(server -> releaseDrone(player(server)));
        }
    }
}
