package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
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
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.FreeformBlocks;
import de.lardtom.propwash.race.FreeformRegion;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GatePoles;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.race.ScoreArea;
import de.lardtom.propwash.rescue.HomePoint;
import de.lardtom.propwash.rescue.HomePointCodecs;
import de.lardtom.propwash.rescue.HomeResets;
import de.lardtom.propwash.sim.StabMode;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class RaceScoringSelfTests {
    private static final int SITE_X = -34;
    private static final int SITE_Z = 30;
    private static final int MIN_X = -12;
    private static final int MAX_X = 12;
    private static final int MIN_Z = -10;
    private static final int MAX_Z = 44;
    private static final int HEIGHT = 12;
    private static final double FLY = 1.5;
    private static final double PILLAR = 3.0;
    private static final double EPS = 1.0E-4;
    private static final long MS = 1_000_000L;
    private static final AtomicReference<Site> SITE = new AtomicReference<>();
    private static final AtomicReference<String> DEBUG = new AtomicReference<>("");
    private static final AtomicReference<Boolean> AUTO_ARM = new AtomicReference<>();
    private static final String START = "start";
    private static final String FLAG = "flag";
    private static final String POLE = "pole";
    private static final String GATE_POLE = "gate_pole";
    private static final String WALL_POLE = "wall_pole";
    private static final String FREEFORM = "freeform";
    private static final String DRONE = "drone";
    private static final String COURSE = "course";
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final double[][] LAP = {{0.0, FLY, -4.0}, {0.0, FLY, 3.0}, {-2.0, FLY, 8.0}, {-2.0, FLY, 12.0}, {2.0, FLY, 12.0},
            {2.0, FLY, 8.0}, {4.0, FLY, 8.0}, {4.0, FLY, 15.0}, {1.5, FLY, 16.0}, {1.5, FLY, 21.0}, {-1.5, FLY, 23.0}, {-1.5, FLY, 29.0},
            {-7.0, FLY, 29.0}, {-7.0, FLY, -6.0}, {0.0, FLY, -6.0}, {0.0, FLY, 3.0}};

    private record Site(BlockPos origin, Vec3 home, Map<BlockPos, BlockState> saved, Map<String, Integer> ids) {
        Vec3 at(double dx, double dy, double dz) {
            return new Vec3(this.origin.getX() + 0.5 + dx, this.origin.getY() + dy, this.origin.getZ() + 0.5 + dz);
        }

        BlockPos block(int dx, int dy, int dz) {
            return this.origin.offset(dx, dy, dz);
        }
    }

    private record Pending(CompletableFuture<Void> future, AtomicReference<String> result) {
    }

    private RaceScoringSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("race: Wertung – Aufbau mit Flagge auf der Säule, Slalomstange, Gate-Stangen und Freiform-Torpfosten", 400,
                RaceScoringSelfTests::setupStep);
        SelfTest.clientStep("race: Wertung – Runde unter der Flagge, Slalom und Gate-Stange zählt beim Client und am Server", 300,
                RaceScoringSelfTests::lapStep);
        SelfTest.clientStep("race: Wertung – Einstell-Menü: nach unten, Gate-Stange umrunden, Pfeiltasten verschieben, Rand am Freiform-Gate", 600,
                RaceScoringSelfTests::menuStep);
        SelfTest.clientStep("race: Wertung – Flächen beim Bauen, in der Nähe und Durchflugfläche des Start-Gates", 300,
                RaceScoringSelfTests::areaStep);
        SelfTest.clientStep("race: Wertung – Kursdatei Format 2 und NBT der Wertung", 200, RaceScoringSelfTests::formatStep);
        SelfTest.clientStep("race: Wertung – Reset scharf am Start-Gate startet keinen Countdown, Scharfschalten nur mit Einstellung", 1400,
                RaceScoringSelfTests::resetStep);
        if (Screenshots.enabled()) {
            Supplier<ItemStack> tool = () -> new ItemStack(RaceModule.COURSE_TOOL.get());
            Screenshots.step("bildschirmfotos: Wertung – Flächen beim Bauen", "race_score_areas_build", 40,
                    new ThirdPerson(new Vec3(9.0, 3.2, 3.0), new Vec3(-0.5, 2.0, 18.0), 0.0F, tool, null, null));
            Screenshots.step("bildschirmfotos: Wertung – Flagge auf der Säule wertet bis zum Boden", "race_score_flag_pillar", 40,
                    new ThirdPerson(new Vec3(-6.0, 1.6, 4.0), new Vec3(0.0, 3.0, 11.0), 0.0F, tool, null, null));
            Screenshots.step("bildschirmfotos: Wertung – Menü Seite Wertung der Flagge", "race_score_menu_flag", 40,
                    new ThirdPerson(new Vec3(-8.0, 1.0, 3.0), new Vec3(0.0, 3.5, 10.5), 24.0F, tool, FLAG, editor -> {
                        editor.showPage(1);
                        editor.set(GateEditModel.Row.SCORE_ABOVE, 2.0);
                    }));
            Screenshots.step("bildschirmfotos: Wertung – Menü Seite Wertung der Gate-Stange", "race_score_menu_gate_pole", 40,
                    new ThirdPerson(new Vec3(-8.0, 1.0, 19.0), new Vec3(0.0, 2.0, 26.5), 24.0F, tool, GATE_POLE, editor -> {
                        editor.showPage(1);
                        editor.set(GateEditModel.Row.SCORE_REACH, 6.0);
                    }));
            Screenshots.step("bildschirmfotos: Wertung – Menü Seite Position", "race_score_menu_position", 40,
                    new ThirdPerson(new Vec3(-6.0, 1.0, 12.5), new Vec3(0.0, 1.5, 18.5), 24.0F, tool, POLE, editor -> {
                        editor.showPage(2);
                        editor.nudge(GateEditModel.AXIS_X, 2);
                        editor.nudge(GateEditModel.AXIS_Y, 1);
                    }));
            Screenshots.step("bildschirmfotos: Wertung – Freiform-Gate mit ignorierten Lücken", "race_score_freeform_gaps", 40,
                    new ThirdPerson(new Vec3(-6.0, 1.6, 34.0), new Vec3(-6.0, 2.0, 40.0), 0.0F, () -> new ItemStack(RaceModule.FREEFORM_GATE.get()), null,
                            null));
            Screenshots.step("bildschirmfotos: Wertung – Durchflugfläche des Start-Gates im FPV", "race_score_start_gate_fpv", 40,
                    new Fpv(false));
            Screenshots.step("bildschirmfotos: Wertung – Flagge als nächstes Ziel im FPV", "race_score_flag_fpv", 40, new Fpv(true));
        }
        SelfTest.clientStep("race: Wertung – Aufräumen", 300, RaceScoringSelfTests::cleanupStep);
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

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, 0);
    }

    private static void next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
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

    private static @Nullable GateEntity clientEntity(String key) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        Integer id = site == null ? null : site.ids().get(key);
        return id != null && mc.level != null && mc.level.getEntity(id) instanceof GateEntity gate && !gate.isRemoved() ? gate : null;
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
            Vec3 stand = site.at(-4.0, 0.0, 14.0);
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
            return ctx.tick() > 300 ? StepResult.fail("Platz lädt nicht") : StepResult.pending();
        }
        wait = once(ctx, "Aufbau", RaceScoringSelfTests::build);
        if (wait != null) {
            return wait;
        }
        Minecraft mc = Minecraft.getInstance();
        for (String key : List.of(START, FLAG, POLE, GATE_POLE, WALL_POLE, FREEFORM)) {
            if (clientEntity(key) == null) {
                return ctx.tick() > 300 ? StepResult.fail(key + " fehlt beim Client") : StepResult.pending();
            }
        }
        GateEntity flag = clientEntity(FLAG);
        GateEntity gatePole = clientEntity(GATE_POLE);
        GateEntity freeform = clientEntity(FREEFORM);
        int course = site.ids().getOrDefault(COURSE, GateEntity.NO_COURSE);
        CourseData data = ClientCourses.get(course);
        if (flag.score().below() != PILLAR || gatePole.score().role() != ScoreArea.Role.PASS || !freeform.score().open() || data == null
                || data.gates().size() != 4) {
            return ctx.tick() > 300 ? StepResult.fail("Client: Flagge " + flag.score() + ", Gate-Stange " + gatePole.score() + ", Freiform "
                    + freeform.score() + ", Kurs " + (data == null ? "-" : data.gates().size())) : StepResult.pending();
        }
        if (data.gates().get(3).scoringKind() != CourseElement.POLE || data.gates().get(3).score().side() != CourseElement.RIGHT
                || data.gates().get(1).score().below() != PILLAR) {
            return StepResult.fail("Kursdaten beim Client: " + data.gates());
        }
        if (mc.level == null || !FreeformBlocks.integrity(mc.level, freeform.geometry()).intact()) {
            return StepResult.fail("Freiform-Gate mit ignorierten Lücken meldet offene Kanten: "
                    + (mc.level == null ? "-" : FreeformBlocks.integrity(mc.level, freeform.geometry())));
        }
        ctx.log("Kurs " + course + " mit Start-Gate, Flagge auf 3 Blöcken Säule (Wertung 3 nach unten), Slalomstange, Gate-Stange (rechts vorbei); "
                + "Wandstange abgelehnt; Freiform-Torpfosten nur mit Schleichen gesetzt, " + freeform.region().cells() + " Felder");
        return StepResult.pass();
    }

    private static String build(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Site site = SITE.get();
        clear(level, site);
        for (int y = 0; y < (int) PILLAR; y++) {
            set(level, site, site.block(0, y, 10), Blocks.STONE_BRICKS.defaultBlockState());
        }
        for (int y = 0; y <= 4; y++) {
            set(level, site, site.block(-8, y, 40), Blocks.STONE_BRICKS.defaultBlockState());
            set(level, site, site.block(-4, y, 40), Blocks.STONE_BRICKS.defaultBlockState());
        }
        Vec3 s = site.at(0.0, 0.0, 0.0);
        GateEntity start = spawn(level, GateGeometry.of(s.x, s.y, s.z, 0.0F, GateSize.MEDIUM), GateSize.MEDIUM);
        Vec3 f = site.at(0.0, PILLAR, 10.0);
        GateEntity flag = spawn(level, GateGeometry.element(f.x, f.y, f.z, 0.0F, CourseElement.FLAG, CourseElement.LEFT, 5.0, 4.0, GateTilt.NONE),
                GateSize.MEDIUM);
        Vec3 p = site.at(0.0, 0.0, 18.0);
        GateEntity pole = spawn(level, GateGeometry.element(p.x, p.y, p.z, 0.0F, CourseElement.POLE, CourseElement.LEFT, 4.0, 3.0, GateTilt.NONE),
                GateSize.MEDIUM);
        Vec3 g = site.at(0.0, 0.0, 26.0);
        GateEntity gatePole = spawn(level, GateGeometry.element(g.x, g.y, g.z, 0.0F, CourseElement.GATE_POLE, 4, GatePoles.THICKNESS, 4.0,
                GateTilt.NONE, GateMount.STANDING), GateSize.MEDIUM);
        for (int x = 7; x <= 9; x++) {
            for (int y = 0; y <= 4; y++) {
                set(level, site, site.block(x, y, 26), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        Vec3 w = site.at(8.0, 2.5, 25.5);
        GateEntity wallPole = spawn(level, GateGeometry.element(w.x, w.y, w.z, 180.0F, CourseElement.GATE_POLE, 9, GatePoles.THICKNESS, 3.0,
                GateTilt.NONE, GateMount.WALL_DIVE), GateSize.MEDIUM);
        site.ids().put(START, start.getId());
        site.ids().put(FLAG, flag.getId());
        site.ids().put(POLE, pole.getId());
        site.ids().put(GATE_POLE, gatePole.getId());
        site.ids().put(WALL_POLE, wallPole.getId());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
        GateMounting.Result edited = GateMounting.edit(player, flag, flag.geometry().heading(), 0.0F, 0.0F, flag.openingWidth(),
                flag.openingHeight(), flag.variant(), flag.score().withBelow(PILLAR), 0.0, 0.0, 0.0);
        if (edited != GateMounting.Result.EDITED || flag.score().below() != PILLAR) {
            return "Wertung nach unten am Server nicht gesetzt: " + edited + " " + flag.score();
        }
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        CourseToolItem.useOnGate(tool, player, gatePole);
        if (gatePole.inCourse()) {
            return "Gate-Stange wurde Kursstart";
        }
        for (GateEntity gate : List.of(start, flag, pole, gatePole)) {
            CourseToolItem.useOnGate(tool, player, gate);
        }
        CourseToolItem.useOnGate(tool, player, wallPole);
        int course = CourseToolItem.courseOf(tool);
        site.ids().put(COURSE, course);
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course data = registry == null ? null : registry.course(course);
        if (data == null || data.gates.size() != 4 || wallPole.inCourse()) {
            return "Kurs falsch: " + (data == null ? "keiner" : data.gates.size() + " Elemente, Wandstange im Kurs " + wallPole.inCourse());
        }
        if (gatePole.score().role() != ScoreArea.Role.PASS || gatePole.score().side() != CourseElement.RIGHT) {
            return "Gate-Stange nicht als Slalom angehängt: " + gatePole.score();
        }
        if (data.checkpoints() != 1 + 3 + 1 + 1 || data.scoringKinds().get(3) != CourseElement.POLE) {
            return "Prüfpunkte " + data.checkpoints() + ", Wertung " + data.scoringKinds();
        }
        if (GateMounting.edit(player, gatePole, gatePole.geometry().heading(), 0.0F, 0.0F, gatePole.openingWidth(), gatePole.openingHeight(),
                gatePole.variant(), gatePole.score().withRole(ScoreArea.Role.NONE), 0.0, 0.0, 0.0) != GateMounting.Result.SCORE_LOCKED) {
            return "Gate-Stange im Kurs ließ sich auf „keine Wertung“ stellen";
        }
        String freeform = placeFreeform(player, site);
        if (freeform != null) {
            return freeform;
        }
        DEBUG.set("Kurs " + course);
        return null;
    }

    private static @Nullable String placeFreeform(ServerPlayer player, Site site) {
        ServerLevel level = player.level();
        BlockPos post = site.block(-8, 1, 40);
        Vec3 click = Vec3.atCenterOf(post).add(0.5, 0.0, 0.0);
        BlockHitResult hit = new BlockHitResult(click, Direction.EAST, post, false);
        ItemStack previous = player.getMainHandItem().copy();
        float yaw = player.getYRot();
        try {
            player.setYRot(0.0F);
            player.setXRot(0.0F);
            ItemStack stack = new ItemStack(RaceModule.FREEFORM_GATE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            player.setShiftKeyDown(false);
            InteractionResult strict = stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            if (strict.consumesAction()) {
                return "Freiform-Gate ohne Schleichen trotz offenem Rand gesetzt";
            }
            player.setShiftKeyDown(true);
            InteractionResult lenient = stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            if (!lenient.consumesAction()) {
                return "Freiform-Gate mit Schleichen nicht gesetzt: " + lenient;
            }
        } finally {
            player.setShiftKeyDown(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            player.setYRot(yaw);
        }
        List<GateEntity> found = level.getEntitiesOfClass(GateEntity.class, new AABB(site.block(-8, 0, 39)).expandTowards(5, 6, 2),
                gate -> gate.kind() == CourseElement.FREEFORM && !gate.isRemoved());
        if (found.size() != 1) {
            return "Freiform-Gate nicht gefunden: " + found.size();
        }
        GateEntity gate = found.getFirst();
        FreeformRegion region = gate.region();
        if (!gate.score().open() || region == null || region.width() != 3 || region.height() != 5 || region.cells() != 15) {
            return "Freiform-Gate mit Lücken: offen " + gate.score().open() + ", Fläche " + region;
        }
        site.ids().put(FREEFORM, gate.getId());
        return null;
    }

    private static List<Vec3> lapPath(Site site) {
        List<Vec3> points = new ArrayList<>();
        for (double[] point : LAP) {
            points.add(site.at(point[0], point[1], point[2]));
        }
        return points;
    }

    private static StepResult lapStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Aufbau");
        }
        int course = site.ids().getOrDefault(COURSE, GateEntity.NO_COURSE);
        CourseData data = ClientCourses.get(course);
        if (data == null) {
            return StepResult.fail("Kurs beim Client fehlt");
        }
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(0);
        tracker.setCourse(course, data.geometry(), 0L);
        List<LapTracker.Event> events = new ArrayList<>();
        List<Vec3> path = lapPath(site);
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
        List<Integer> gates = events.stream().filter(e -> e.type() == LapTracker.EventType.GATE).map(LapTracker.Event::gate).toList();
        if (!gates.equals(List.of(1, 2, 3)) || events.stream().noneMatch(e -> e.type() == LapTracker.EventType.LAP)) {
            return StepResult.fail("Client zählt " + gates + ", Ereignisse " + events);
        }
        StepResult wait = once(ctx, "Server-Runde", server -> serverLap(server, site, course));
        if (wait != null) {
            return wait;
        }
        ctx.log("Runde 1,5 Blöcke über dem Boden unter der Flagge auf der Säule: Client zählt Flagge, Slalomstange und Gate-Stange, "
                + DEBUG.get());
        return StepResult.pass();
    }

    private static @Nullable String serverLap(MinecraftServer server, Site site, int courseId) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(courseId);
        if (course == null) {
            return "Kurs fehlt am Server";
        }
        registry.setLaps(course, 1);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        Vec3 first = site.at(0.0, FLY, -4.0);
        drone.snapTo(first.x, first.y, first.z, 0.0F, 0.0F);
        drone.setPilot(player);
        if (!level.addFreshEntity(drone)) {
            return "Drohne nicht gespawnt";
        }
        try {
            long lap = Math.max(RaceServer.minimumLapNanos(course, player) * 3L, 8_000L * MS);
            List<Vec3> path = lapPath(site);
            double total = 0.0;
            for (int i = 1; i < path.size(); i++) {
                total += path.get(i - 1).distanceTo(path.get(i));
            }
            double startCrossing = Math.abs(LAP[0][2]);
            double endCrossing = total - Math.abs(LAP[LAP.length - 1][2]);
            double perBlock = lap / (endCrossing - startCrossing);
            long t0 = System.nanoTime() + 60_000L * MS;
            RaceServer.Verdict verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.START, courseId, 0, 0L, 0L),
                    player, t0 - 100L * MS);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Start abgelehnt: " + verdict;
            }
            double travelled = 0.0;
            long t = t0;
            Vec3 at = path.getFirst();
            for (int i = 1; i < path.size(); i++) {
                Vec3 to = path.get(i);
                double length = at.distanceTo(to);
                int steps = Math.max(1, (int) Math.ceil(length / 0.5));
                for (int s = 1; s <= steps; s++) {
                    Vec3 from = at.lerp(to, (s - 1) / (double) steps);
                    Vec3 next = at.lerp(to, s / (double) steps);
                    t = t0 + Math.round((travelled + length * s / steps) * perBlock);
                    RaceServer.onDroneMoved(player, from, next, t);
                }
                travelled += length;
                at = to;
            }
            t += 100L * MS;
            verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, courseId, 1, lap, 0L), player, t);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Runde unter der Flagge am Server abgelehnt: " + verdict;
            }
            List<Leaderboard.Entry> entries = new ArrayList<>(registry.board(course).entries());
            entries.addAll(registry.board(course).assistedEntries());
            if (entries.stream().noneMatch(entry -> entry.player().equals(player.getUUID()))) {
                return "Runde fehlt in der Bestenliste";
            }
            DEBUG.set("Server nimmt die Runde an (" + lap / MS + " ms)");
            return null;
        } finally {
            drone.setPilot(null);
            drone.discard();
        }
    }

    private static StepResult menuStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Site site = SITE.get();
        LocalPlayer player = mc.player;
        if (site == null || player == null) {
            return StepResult.fail("kein Aufbau");
        }
        GateEntity flag = clientEntity(FLAG);
        GateEntity pole = clientEntity(POLE);
        GateEntity gatePole = clientEntity(GATE_POLE);
        GateEntity freeform = clientEntity(FREEFORM);
        if (flag == null || pole == null || gatePole == null || freeform == null) {
            return StepResult.fail("Elemente fehlen beim Client");
        }
        switch (phase(ctx)) {
            case 0 -> {
                StepResult wait = once(ctx, "Werkzeug", server -> {
                    ServerPlayer p = player(server);
                    Vec3 stand = site.at(-4.0, 0.0, 24.0);
                    p.teleportTo(p.level(), stand.x, stand.y, stand.z, Set.of(), 0.0F, 0.0F, true);
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                if (!GateMounting.holdsTool(player) || player.position().distanceTo(site.at(-4.0, 0.0, 24.0)) > 0.6) {
                    return since(ctx) > 200 ? StepResult.fail("Kurs-Werkzeug oder Platz kommt nicht an") : StepResult.pending();
                }
                RaceConfig.CONFIG.get().nudgeStep = GateEditModel.DEFAULT_NUDGE_STEP;
                player.setYRot(0.0F);
                GateEditor.start(flag);
                GateEditor editor = GateEditor.current();
                if (editor == null || editor.model().pages() != 3 || editor.model().pageKind(1) != GateEditModel.Page.SCORE) {
                    return StepResult.fail("Menü der Flagge ohne Seite Wertung: " + (editor == null ? "-" : editor.model().pages()));
                }
                editor.showPage(1);
                if (editor.model().selected() != GateEditModel.Row.SCORE_BELOW) {
                    return StepResult.fail("Seite Wertung wählt " + editor.model().selected());
                }
                for (boolean interactive : new boolean[]{false, true}) {
                    GateEditPanel probe = new GateEditPanel(editor, interactive);
                    for (int[] size : new int[][]{{480, 270}, {320, 240}}) {
                        probe.layout(size[0], size[1]);
                        if (probe.top() < 0 || probe.bottom() > size[1]) {
                            return StepResult.fail("Seite Wertung passt nicht in " + size[0] + " × " + size[1]);
                        }
                    }
                }
                GateEditDriver.scroll(mc, 2);
                if (editor.model().score().below() != PILLAR + 1.0) {
                    return StepResult.fail("Mausrad stellt die Wertung nach unten auf " + editor.model().score().below());
                }
                GateEditDriver.press(mc, InputConstants.KEY_RETURN);
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (flag.score().below() != PILLAR + 1.0) {
                    return since(ctx) > 100 ? StepResult.fail("Wertung nach unten kommt nicht an: " + flag.score()) : StepResult.pending();
                }
                GateEditor.start(gatePole);
                GateEditor editor = GateEditor.current();
                if (editor == null || !editor.model().roleRequired() || editor.model().roles().size() != 2) {
                    return StepResult.fail("Gate-Stange im Kurs bietet „keine Wertung“ an");
                }
                editor.showPage(1);
                if (editor.model().selected() != GateEditModel.Row.SCORE_ROLE) {
                    return StepResult.fail("Seite Wertung der Gate-Stange wählt " + editor.model().selected());
                }
                GateEditDriver.scroll(mc, 1);
                if (editor.model().score().role() != ScoreArea.Role.TURN) {
                    return StepResult.fail("Rolle nach dem Mausrad: " + editor.model().score().role());
                }
                GateEditDriver.press(mc, InputConstants.KEY_RETURN);
                next(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                int course = site.ids().getOrDefault(COURSE, GateEntity.NO_COURSE);
                CourseData data = ClientCourses.get(course);
                if (gatePole.score().role() != ScoreArea.Role.TURN || data == null || data.gates().get(3).scoringKind() != CourseElement.FLAG) {
                    return since(ctx) > 100 ? StepResult.fail("Rolle „umrunden“ kommt nicht in den Kurs: " + gatePole.score())
                            : StepResult.pending();
                }
                ctx.state().put("Stange vorher", pole.position());
                player.setYRot(0.0F);
                GateEditor.start(pole);
                GateEditor editor = GateEditor.current();
                if (editor == null) {
                    return StepResult.fail("Menü der Slalomstange startet nicht");
                }
                GateEditDriver.press(mc, InputConstants.KEY_PAGEUP);
                GateEditDriver.press(mc, InputConstants.KEY_RIGHT);
                GateEditDriver.press(mc, InputConstants.KEY_UP);
                GateEditDriver.press(mc, InputConstants.KEY_UP);
                GateEditModel model = editor.model();
                if (Math.abs(model.dy() - 0.25) > EPS || Math.abs(model.dx() + 0.25) > EPS || Math.abs(model.dz() - 0.5) > EPS
                        || model.page() != 2 || editor.status() != GateEditor.Status.FREE) {
                    return StepResult.fail("Pfeiltasten verschieben falsch: " + model.dx() + "/" + model.dy() + "/" + model.dz() + ", Seite "
                            + model.page() + ", " + editor.status());
                }
                GateEditDriver.press(mc, InputConstants.KEY_RETURN);
                next(ctx, 3);
                return StepResult.pending();
            }
            case 3 -> {
                Vec3 before = (Vec3) ctx.state().get("Stange vorher");
                Vec3 moved = pole.position().subtract(before);
                if (Math.abs(moved.x + 0.25) > EPS || Math.abs(moved.y - 0.25) > EPS || Math.abs(moved.z - 0.5) > EPS) {
                    return since(ctx) > 100 ? StepResult.fail("Slalomstange nicht verschoben: " + moved) : StepResult.pending();
                }
                GateEditor.start(pole);
                GateEditor editor = GateEditor.current();
                if (editor == null) {
                    return StepResult.fail("Menü der Slalomstange startet nicht erneut");
                }
                editor.set(GateEditModel.Row.MOVE_X, 0.25);
                editor.set(GateEditModel.Row.MOVE_Y, -0.25);
                editor.set(GateEditModel.Row.MOVE_Z, -0.5);
                editor.apply();
                next(ctx, 4);
                return StepResult.pending();
            }
            case 4 -> {
                Vec3 before = (Vec3) ctx.state().get("Stange vorher");
                if (pole.position().distanceTo(before) > EPS) {
                    return since(ctx) > 100 ? StepResult.fail("Slalomstange nicht zurück: " + pole.position()) : StepResult.pending();
                }
                GateEditor.start(freeform);
                GateEditor editor = GateEditor.current();
                if (editor == null || !editor.model().rows(0).equals(List.of(GateEditModel.Row.VARIANT, GateEditModel.Row.OPEN_EDGES))
                        || editor.model().value(GateEditModel.Row.OPEN_EDGES) < 0.5) {
                    return StepResult.fail("Zeile „Rand“ am Freiform-Gate fehlt oder steht falsch");
                }
                editor.set(GateEditModel.Row.OPEN_EDGES, 0.0);
                editor.apply();
                next(ctx, 5);
                return StepResult.pending();
            }
            case 5 -> {
                if (freeform.score().open() || mc.level == null || FreeformBlocks.integrity(mc.level, freeform.geometry()).openEdges() == 0) {
                    return since(ctx) > 100 ? StepResult.fail("„Lücken melden“ meldet keine offenen Kanten: " + freeform.score())
                            : StepResult.pending();
                }
                GateEditor.start(freeform);
                GateEditor editor = GateEditor.current();
                if (editor == null) {
                    return StepResult.fail("Menü des Freiform-Gates startet nicht erneut");
                }
                editor.set(GateEditModel.Row.OPEN_EDGES, 1.0);
                editor.rescan();
                next(ctx, 6);
                return StepResult.pending();
            }
            default -> {
                if (!freeform.score().open() || freeform.region() == null || freeform.region().cells() != 15) {
                    return since(ctx) > 100 ? StepResult.fail("„Neu abtasten“ mit ignorierten Lücken: " + freeform.score() + " "
                            + freeform.region()) : StepResult.pending();
                }
                GateEditor.stop();
                ctx.log("Mausrad stellt die Wertung nach unten (4 Blöcke), Gate-Stange im Kurs nur „vorbei“/„umrunden“, Pfeiltasten und "
                        + "Bild↑ verschieben um 1/4 Block blickbezogen, Freiform-Rand melden/ignorieren und Neu abtasten");
                return StepResult.pass();
            }
        }
    }

    private static StepResult areaStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Site site = SITE.get();
        LocalPlayer player = mc.player;
        GateEntity start = clientEntity(START);
        GateEntity flag = clientEntity(FLAG);
        GateEntity pole = clientEntity(POLE);
        GateEntity gatePole = clientEntity(GATE_POLE);
        if (site == null || player == null || start == null || flag == null || pole == null || gatePole == null) {
            return StepResult.fail("Aufbau fehlt");
        }
        switch (phase(ctx)) {
            case 0 -> {
                StepResult wait = once(ctx, "Bauen", server -> {
                    ServerPlayer p = player(server);
                    Vec3 stand = site.at(-4.0, 0.0, 20.0);
                    p.teleportTo(p.level(), stand.x, stand.y, stand.z, Set.of(), 0.0F, 0.0F, true);
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                if (!GateMounting.holdsTool(player) || player.position().distanceTo(site.at(-4.0, 0.0, 20.0)) > 0.6) {
                    return since(ctx) > 200 ? StepResult.fail("Kurs-Werkzeug oder Platz kommt nicht an: " + player.getMainHandItem() + " bei "
                            + player.position()) : StepResult.pending();
                }
                ctx.state().put("Bilder", ScoreAreaRenderer.framesDrawn());
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (ScoreAreaRenderer.framesDrawn() <= (int) ctx.state().get("Bilder") + 2) {
                    return since(ctx) > 60 ? StepResult.fail("Wertungsflächen werden nicht gezeichnet") : StepResult.pending();
                }
                for (GateEntity element : ctx.state().containsKey("gebaut") ? List.<GateEntity>of() : List.of(flag, pole, gatePole)) {
                    ScoreAreaRenderer.Drawn drawn = ScoreAreaRenderer.drawnFor(element.getId());
                    if (drawn == null || drawn.kind() != ScoreAreaRenderer.Kind.AREA || drawn.strength() < ScoreAreaRenderer.BUILD - EPS) {
                        Vec3 camera = ScoreAreaRenderer.lastCamera();
                        return since(ctx) > 60 ? StepResult.fail("Fläche beim Bauen fehlt für " + element.kind() + ": " + drawn + ", Bauen "
                                + ScoreAreaRenderer.lastBuilding() + ", Werkzeug " + GateMounting.holdsTool(player) + " ("
                                + player.getMainHandItem() + "), Kamera " + camera + ", Abstand "
                                + (camera == null ? "-" : camera.distanceTo(element.position()))) : StepResult.pending();
                    }
                }
                if (!ctx.state().containsKey("gebaut")) {
                    if (ScoreAreaRenderer.drawnFor(gatePole.getId()).passages() != 3 || ScoreAreaRenderer.drawnFor(flag.getId()).passages() != 3) {
                        return StepResult.fail("Flagge und umrundende Gate-Stange zeigen nicht drei Flächen");
                    }
                    ctx.state().put("gebaut", true);
                }
                StepResult wait = once(ctx, "leere Hand", server -> {
                    player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    Vec3 stand = site.at(-3.0, 0.0, 17.0);
                    player(server).teleportTo(player(server).level(), stand.x, stand.y, stand.z, Set.of(), 0.0F, 0.0F, true);
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                next(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (GateMounting.holdsTool(player) || player.position().distanceTo(site.at(-3.0, 0.0, 17.0)) > 0.6) {
                    return since(ctx) > 100 ? StepResult.fail("Hand wird nicht leer") : StepResult.pending();
                }
                ScoreAreaRenderer.Drawn near = ScoreAreaRenderer.drawnFor(pole.getId());
                ScoreAreaRenderer.Drawn far = ScoreAreaRenderer.drawnFor(gatePole.getId());
                if (near == null || near.strength() > ScoreAreaRenderer.NEAR + EPS) {
                    return since(ctx) > 60 ? StepResult.fail("nahe Slalomstange ohne dezente Fläche: " + near) : StepResult.pending();
                }
                if (far != null && far.strength() >= near.strength()) {
                    return StepResult.fail("ferne Gate-Stange kräftiger als die nahe Stange: " + far + " / " + near);
                }
                ClientRaceTracker.INSTANCE.selectCourseAt(mc, site.at(0.0, FLY, -3.0));
                next(ctx, 3);
                return StepResult.pending();
            }
            case 3 -> {
                ScoreAreaRenderer.Drawn opening = ScoreAreaRenderer.drawnFor(start.getId());
                if (opening == null || opening.kind() != ScoreAreaRenderer.Kind.OPENING || opening.strength() < ScoreAreaRenderer.ACTIVE - EPS) {
                    return since(ctx) > 60 ? StepResult.fail("Start-Gate ohne Durchflugfläche vor dem Start: " + opening + ", Kurs "
                            + ClientRaceTracker.INSTANCE.courseId()) : StepResult.pending();
                }
                RaceConfig.CONFIG.get().scoreAreas = false;
                next(ctx, 4);
                return StepResult.pending();
            }
            default -> {
                if (!ScoreAreaRenderer.lastDrawn().isEmpty()) {
                    return since(ctx) > 40 ? StepResult.fail("Schalter aus, trotzdem Flächen: " + ScoreAreaRenderer.lastDrawn()) : StepResult.pending();
                }
                RaceConfig.CONFIG.get().scoreAreas = true;
                ctx.log("Flächen beim Bauen (alle drei Elemente), nah dezent, Start-Gate mit Durchflugfläche vor dem Start, Schalter aus wirkt");
                return StepResult.pass();
            }
        }
    }

    private static StepResult formatStep(SelfTest.StepContext ctx) {
        StepResult wait = once(ctx, "Format", server -> {
            CourseRegistry registry = CourseRegistry.get();
            if (registry == null) {
                return "keine Kurse";
            }
            registry.save();
            Path file = server.getWorldPath(LevelResource.DATA).resolve("propwash_race.json");
            String text;
            try {
                text = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "Kursdatei nicht lesbar: " + e;
            }
            if (!text.contains("\"format\": 2") || !text.contains("\"below\": 4.0") || !text.contains("\"role\": \"turn\"")) {
                return "Kursdatei ohne Format 2 oder Wertung";
            }
            ServerPlayer player = player(server);
            ServerLevel level = player.level();
            Site site = SITE.get();
            ProblemReporter.Collector reporter = new ProblemReporter.Collector();
            for (String key : List.of(FLAG, GATE_POLE, FREEFORM)) {
                GateEntity source = entity(level, site, key);
                if (source == null) {
                    return key + " fehlt";
                }
                TagValueOutput output = TagValueOutput.createWithContext(reporter, server.registryAccess());
                source.saveWithoutId(output);
                CompoundTag tag = output.buildResult();
                GateEntity copy = RaceModule.GATE.get().create(level, EntitySpawnReason.LOAD);
                if (copy == null) {
                    return "Kopie nicht erzeugt";
                }
                copy.load(TagValueInput.create(reporter, server.registryAccess(), tag));
                if (!copy.score().equals(source.score()) || !copy.geometry().equals(source.geometry())) {
                    return key + " nach dem Laden anders: " + copy.score() + " statt " + source.score();
                }
            }
            GateEntity start = entity(level, site, START);
            TagValueOutput plain = TagValueOutput.createWithContext(reporter, server.registryAccess());
            start.saveWithoutId(plain);
            if (plain.buildResult().keySet().stream().anyMatch(name -> name.startsWith("Score") || name.equals("OpenFrame"))) {
                return "Gate ohne Wertung schreibt Wertungsschlüssel";
            }
            return reporter.isEmpty() ? null : "Meldungen: " + reporter.getReport();
        });
        if (wait != null) {
            return wait;
        }
        ctx.log("propwash_race.json mit format 2 und score, NBT der Wertung hin und zurück, einfaches Gate ohne Wertungsschlüssel");
        return StepResult.pass();
    }

    private static void startFpv(MinecraftServer server, Vec3 at) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Site site = SITE.get();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        Integer previous = site.ids().remove(DRONE);
        if (previous != null && level.getEntity(previous) instanceof QuadcopterEntity old) {
            old.discard();
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht gespawnt");
        }
        drone.resetTo(at, new Quaternionf().rotationY((float) Math.PI));
        drone.setHomePoint(new HomePoint(at.x, at.y, at.z, HomePointCodecs.dimension(level), level.getGameTime(), HomePoint.Source.MANUAL));
        site.ids().put(DRONE, drone.getId());
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
    }

    private static @Nullable QuadcopterEntity sessionDrone() {
        PilotSession session = PilotSession.INSTANCE;
        return session.isActive() ? session.drone() : null;
    }

    private static StepResult resetStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Aufbau");
        }
        PilotSession session = PilotSession.INSTANCE;
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        int phase = phase(ctx);
        boolean arm = phase == 1 || phase == 2 || phase == 3 || phase == 5 && since(ctx) >= 10 || phase == 7 && since(ctx) >= 10;
        if (phase > 0) {
            session.setTestInput(StickPositions.IDLE, arm, StabMode.ANGLE);
        }
        int course = site.ids().getOrDefault(COURSE, GateEntity.NO_COURSE);
        switch (phase) {
            case 0 -> {
                StepResult wait = once(ctx, "FPV", server -> {
                    startFpv(server, site.at(0.0, 0.05, -5.0));
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                QuadcopterEntity drone = sessionDrone();
                if (drone == null || !session.isFpv() || drone.getId() != site.ids().getOrDefault(DRONE, -1) || tracker.courseId() != course) {
                    return since(ctx) > 300 ? StepResult.fail("FPV am Start-Gate kommt nicht zustande, Kurs " + tracker.courseId())
                            : StepResult.pending();
                }
                ctx.state().put("Einstellung", RaceConfig.CONFIG.get().countdown);
                RaceConfig.CONFIG.get().countdown = false;
                AUTO_ARM.compareAndSet(null, InputConfig.get().autoArmAfterReset);
                InputConfig.get().autoArmAfterReset = false;
                next(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                QuadcopterEntity drone = sessionDrone();
                if (drone == null || !drone.isArmed()) {
                    return since(ctx) > 200 ? StepResult.fail("Drohne lässt sich am Start nicht scharf schalten") : StepResult.pending();
                }
                if (tracker.lobbyMember() || tracker.countdownActive()) {
                    return StepResult.fail("Scharfschalten ohne „Start beim Scharfschalten“ startet einen Countdown");
                }
                if (since(ctx) < 20) {
                    return StepResult.pending();
                }
                StepResult wait = once(ctx, "weg", server -> {
                    QuadcopterEntity d = DronePiloting.pilotedBy(player(server).getUUID()).orElse(null);
                    if (d == null) {
                        return "keine Drohne am Server";
                    }
                    d.resetTo(site.at(5.0, 3.0, -8.0), d.orientation());
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                next(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (since(ctx) < 20) {
                    return StepResult.pending();
                }
                StepResult wait = once(ctx, "Reset", server -> {
                    ServerPlayer player = player(server);
                    QuadcopterEntity d = DronePiloting.pilotedBy(player.getUUID()).orElse(null);
                    if (d == null) {
                        return "keine Drohne am Server";
                    }
                    var outcome = HomeResets.reset(player, d);
                    return outcome.moves() ? null : "Reset verweigert: " + outcome;
                });
                if (wait != null) {
                    return wait;
                }
                next(ctx, 3);
                return StepResult.pending();
            }
            case 3 -> {
                if (tracker.lobbyMember() || tracker.countdownActive()) {
                    return StepResult.fail("Rückkehr zum Homepoint startet von selbst den Countdown");
                }
                QuadcopterEntity drone = sessionDrone();
                if (drone == null || drone.position().distanceTo(site.at(0.0, 0.05, -5.0)) > 1.5) {
                    return since(ctx) > 200 ? StepResult.fail("Drohne nicht am Homepoint") : StepResult.pending();
                }
                if (since(ctx) < 80) {
                    return StepResult.pending();
                }
                if (drone.isArmed()) {
                    return StepResult.fail("Drohne nach dem Reset scharf");
                }
                next(ctx, 4);
                return StepResult.pending();
            }
            case 4 -> {
                if (since(ctx) < 10) {
                    return StepResult.pending();
                }
                next(ctx, 5);
                return StepResult.pending();
            }
            case 5 -> {
                if (tracker.lobbyMember() || tracker.countdownActive()) {
                    return StepResult.fail("Scharfschalten am Start nach dem Reset startet ohne Einstellung einen Countdown");
                }
                QuadcopterEntity drone = sessionDrone();
                if (since(ctx) < 90) {
                    return StepResult.pending();
                }
                if (drone == null || !drone.isArmed()) {
                    return StepResult.fail("Drohne lässt sich nach dem Reset nicht scharf schalten");
                }
                RaceConfig.CONFIG.get().countdown = true;
                next(ctx, 6);
                return StepResult.pending();
            }
            case 6 -> {
                QuadcopterEntity drone = sessionDrone();
                if (drone != null && drone.isArmed()) {
                    return since(ctx) > 60 ? StepResult.fail("Drohne bleibt scharf") : StepResult.pending();
                }
                next(ctx, 7);
                return StepResult.pending();
            }
            case 7 -> {
                if (tracker.lobbyMember() || tracker.countdownActive()) {
                    next(ctx, 8);
                    return StepResult.pending();
                }
                return since(ctx) > 140 ? StepResult.fail("Scharfschalten am Start-Gate mit Einstellung startet keine Aufstellung")
                        : StepResult.pending();
            }
            default -> {
                StepResult wait = once(ctx, "zweiter Reset", server -> {
                    ServerPlayer player = player(server);
                    DronePiloting.pilotedBy(player.getUUID()).ifPresent(d -> HomeResets.reset(player, d));
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                if (tracker.lobbyMember() || tracker.countdownActive() || tracker.tracker().racing()) {
                    return since(ctx) > 200 ? StepResult.fail("Reset verlässt Aufstellung oder Lauf nicht") : StepResult.pending();
                }
                RaceConfig.CONFIG.get().countdown = (boolean) ctx.state().getOrDefault("Einstellung", false);
                restoreAutoArm();
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                ctx.log("Reset scharf am Start-Gate: kein Countdown; Scharfschalten danach ohne Einstellung: keiner; mit Einstellung: Aufstellung "
                        + "am Server; zweiter Reset verlässt sie");
                return StepResult.pass();
            }
        }
    }

    private static void restoreAutoArm() {
        Boolean saved = AUTO_ARM.getAndSet(null);
        if (saved != null) {
            InputConfig.get().autoArmAfterReset = saved;
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        GateEditor.stop();
        PilotSession.INSTANCE.clearTestInput();
        RaceConfig.CONFIG.get().scoreAreas = true;
        restoreAutoArm();
        if (site == null) {
            return StepResult.pass();
        }
        StepResult wait = once(ctx, "Aufräumen", server -> {
            ServerPlayer player = player(server);
            ServerLevel level = player.level();
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            Integer drone = site.ids().get(DRONE);
            if (drone != null && level.getEntity(drone) instanceof QuadcopterEntity d) {
                d.discard();
            }
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            AABB area = new AABB(Vec3.atCenterOf(site.block(MIN_X - 4, -2, MIN_Z - 4)), Vec3.atCenterOf(site.block(MAX_X + 4, HEIGHT + 4, MAX_Z + 4)));
            for (GateEntity gate : level.getEntitiesOfClass(GateEntity.class, area, gate -> !gate.isRemoved())) {
                gate.discard();
            }
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, item -> !item.isRemoved())) {
                item.discard();
            }
            List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(site.saved().entrySet());
            for (int i = entries.size() - 1; i >= 0; i--) {
                level.setBlockAndUpdate(entries.get(i).getKey(), entries.get(i).getValue());
            }
            site.saved().clear();
            player.teleportTo(level, site.home().x, site.home().y, site.home().z, Set.of(), player.getYRot(), player.getXRot(), true);
            return null;
        });
        if (wait != null) {
            return wait;
        }
        if (PilotSession.INSTANCE.isActive()) {
            return ctx.tick() > 250 ? StepResult.fail("FPV-Sitzung endet nicht") : StepResult.pending();
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
        private final float turn;
        private final Supplier<ItemStack> held;
        private final @Nullable String edit;
        private final @Nullable Consumer<GateEditor> setup;

        private ThirdPerson(Vec3 feet, Vec3 target, float turn, Supplier<ItemStack> held, @Nullable String edit,
                            @Nullable Consumer<GateEditor> setup) {
            this.feet = feet;
            this.target = target;
            this.turn = turn;
            this.held = held;
            this.edit = edit;
            this.setup = setup;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Site site = SITE.get();
            Minecraft mc = Minecraft.getInstance();
            if (site == null || mc.player == null) {
                return StepResult.fail("kein Aufbau für das Bildschirmfoto");
            }
            Vec3 at = site.at(this.feet.x, this.feet.y, this.feet.z);
            Supplier<ItemStack> held = this.held;
            StepResult wait = once(ctx, "Blickpunkt", server -> {
                ServerPlayer player = player(server);
                DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
                Integer drone = site.ids().remove(DRONE);
                if (drone != null && player.level().getEntity(drone) instanceof QuadcopterEntity d) {
                    d.discard();
                }
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(player.level(), at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
                player.setItemInHand(InteractionHand.MAIN_HAND, held.get());
                return null;
            });
            if (wait != null) {
                return wait;
            }
            if (PilotSession.INSTANCE.isActive() || mc.player.position().distanceTo(at) > 0.6
                    || mc.player.getMainHandItem().getItem() != held.get().getItem()) {
                return ctx.tick() > 300 ? StepResult.fail("Spieler nicht am Blickpunkt") : StepResult.pending();
            }
            this.hold(ctx);
            if (this.edit != null) {
                GateEntity gate = clientEntity(this.edit);
                if (gate == null) {
                    return StepResult.fail(this.edit + " fehlt für das Menü");
                }
                GateEditor editor = GateEditor.current();
                if (editor == null || editor.gate() != gate) {
                    GateEditor.start(gate);
                    editor = GateEditor.current();
                    if (editor == null) {
                        return StepResult.fail("Menü startet nicht");
                    }
                    if (this.setup != null) {
                        this.setup.accept(editor);
                    }
                }
                if (editor.status() != GateEditor.Status.FREE) {
                    return ctx.tick() > 300 ? StepResult.fail("Vorschau im Menü nicht frei: " + editor.status()) : StepResult.pending();
                }
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Site site = SITE.get();
            LocalPlayer player = Minecraft.getInstance().player;
            if (site != null && player != null) {
                aim(player, site.at(this.target.x, this.target.y, this.target.z));
                if (this.turn != 0.0F) {
                    float yaw = player.getYRot() + this.turn;
                    player.setYRot(yaw);
                    player.yRotO = yaw;
                    player.setYHeadRot(yaw);
                    player.setYBodyRot(yaw);
                    player.yBodyRotO = yaw;
                    player.yHeadRotO = yaw;
                }
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            GateEditor.stop();
            ctx.onServer(server -> {
                ServerPlayer player = player(server);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            });
        }
    }

    private static final class Fpv implements Screenshots.Scene {
        private final boolean racing;

        private Fpv(boolean racing) {
            this.racing = racing;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Site site = SITE.get();
            if (site == null) {
                return StepResult.fail("kein Aufbau für das Bildschirmfoto");
            }
            Vec3 at = this.racing ? site.at(-2.0, 1.2, 4.0) : site.at(0.0, 0.05, -6.0);
            StepResult wait = once(ctx, "FPV", server -> {
                startFpv(server, site.at(0.0, 0.05, -6.0));
                return null;
            });
            if (wait != null) {
                return wait;
            }
            QuadcopterEntity drone = sessionDrone();
            ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
            if (drone == null || !PilotSession.INSTANCE.isFpv() || drone.getId() != site.ids().getOrDefault(DRONE, -1)) {
                return ctx.tick() > 400 ? StepResult.fail("keine FPV-Sitzung") : StepResult.pending();
            }
            if (!this.racing) {
                return tracker.courseId() == site.ids().getOrDefault(COURSE, -1) ? StepResult.pass()
                        : ctx.tick() > 400 ? StepResult.fail("Kurs nicht gewählt") : StepResult.pending();
            }
            Quaternionf ahead = new Quaternionf().rotationY((float) Math.PI);
            if (!ctx.state().containsKey("vor dem Start")) {
                ctx.state().put("vor dem Start", ctx.onServer(server -> DronePiloting.pilotedBy(player(server).getUUID())
                        .ifPresent(d -> d.resetTo(site.at(0.0, 1.0, -0.8), ahead))));
                ctx.state().put("seit", ctx.tick());
                return StepResult.pending();
            }
            if (!ctx.state().containsKey("durch")) {
                if (ctx.tick() - (int) ctx.state().get("seit") < 10) {
                    return StepResult.pending();
                }
                ctx.state().put("durch", ctx.onServer(server -> DronePiloting.pilotedBy(player(server).getUUID())
                        .ifPresent(d -> d.resetTo(site.at(0.0, 1.0, 0.8), ahead))));
                return StepResult.pending();
            }
            if (!tracker.tracker().racing()) {
                return ctx.tick() > 400 ? StepResult.fail("Rennen startet nicht") : StepResult.pending();
            }
            if (!ctx.state().containsKey("vor der Flagge")) {
                ctx.state().put("vor der Flagge", ctx.onServer(server -> DronePiloting.pilotedBy(player(server).getUUID())
                        .ifPresent(d -> d.resetTo(at, ahead))));
                return StepResult.pending();
            }
            QuadcopterEntity current = sessionDrone();
            if (current == null || current.position().distanceTo(at) > 0.8 || tracker.nextGateIndex() != 1) {
                return ctx.tick() > 400 ? StepResult.fail("Drohne nicht vor der Flagge, nächstes Ziel " + tracker.nextGateIndex()) : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            PilotSession.INSTANCE.clearTestInput();
            ctx.onServer(server -> DronePiloting.pilotedBy(player(server).getUUID()).ifPresent(DronePiloting::release));
        }
    }
}
