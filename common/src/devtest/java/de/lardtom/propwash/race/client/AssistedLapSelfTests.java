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
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class AssistedLapSelfTests {
    private static final long MS = 1_000_000L;
    private static final int HOLD = LapAssist.HOLD_MODE.bit();
    private static final int RESCUE = LapAssist.RESCUE.bit();
    private static final int PHYSICS = LapAssist.WORLD_PHYSICS.bit();
    private static final AtomicReference<@Nullable Site> SITE = new AtomicReference<>();
    private static final AtomicReference<@Nullable String> RESULT = new AtomicReference<>();
    private static final String FUTURE = "future";

    private AssistedLapSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("flughilfe: Runden mit Flughilfe nur mit Welt-Regel gespeichert, getrennt und markiert", 300,
                AssistedLapSelfTests::serverStep);
        SelfTest.clientStep("flughilfe: Bestenliste zeigt den Abschnitt „Mit Flughilfe“", 300, AssistedLapSelfTests::screenStep);
        SelfTest.clientStep("flughilfe: Aufräumen", 200, AssistedLapSelfTests::cleanupStep);
    }

    public static Screenshots.Scene boardScene() {
        return new BoardScene();
    }

    public static Screenshots.Scene raceScene() {
        return new RaceScene();
    }

    private record Site(int course, int gateA, int gateB, int drone) {
    }

    private static @Nullable StepResult future(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return StepResult.fail("kein Server-Schritt");
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Schritt fehlgeschlagen: " + future.exceptionNow());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private static StepResult serverStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            RESULT.set(null);
            ctx.state().put(FUTURE, ctx.onServer(server -> RESULT.set(check(server))));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        String result = RESULT.get();
        return result == null ? StepResult.pass() : StepResult.fail(result);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    static Site build(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            throw new IllegalStateException("CourseRegistry läuft nicht");
        }
        double x = player.getX() + 10.0;
        double z = player.getZ() - 20.0;
        GateEntity a = gate(level, x, z);
        GateEntity b = gate(level, x, z - 24.0);
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        CourseToolItem.useOnGate(tool, player, a);
        CourseToolItem.useOnGate(tool, player, b);
        int id = CourseToolItem.courseOf(tool);
        CourseRegistry.Course course = registry.course(id);
        if (course == null || course.gates.size() != 2) {
            throw new IllegalStateException("Kurs nicht angelegt");
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        CourseRegistry.GateRecord start = course.gates.getFirst();
        drone.snapTo(start.x, start.y + 1.5, start.z + 3.0, 0.0F, 0.0F);
        drone.setNoGravity(true);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht gespawnt");
        }
        drone.setPilot(player);
        Site site = new Site(id, a.getId(), b.getId(), drone.getId());
        SITE.set(site);
        return site;
    }

    private static GateEntity gate(ServerLevel level, double x, double z) {
        GateEntity gate = RaceModule.GATE.get().create(level, EntitySpawnReason.COMMAND);
        if (gate == null) {
            throw new IllegalStateException("Gate nicht erzeugt");
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        gate.setSize(GateSize.MEDIUM);
        gate.snapTo(x, top, z, 0.0F, 0.0F);
        if (!level.addFreshEntity(gate)) {
            throw new IllegalStateException("Gate nicht gespawnt");
        }
        return gate;
    }

    private static void passGate(ServerPlayer player, GateGeometry gate, long nanos) {
        Vec3 center = new Vec3(gate.x(), gate.centerY(), gate.z());
        Vec3 normal = new Vec3(gate.normalX(), 0.0, gate.normalZ());
        RaceServer.onDroneMoved(player, center.subtract(normal), center.add(normal), nanos);
    }

    private static long flyLap(ServerPlayer player, List<GateGeometry> gates, long start, long lap) {
        passGate(player, gates.getFirst(), start);
        for (int i = 1; i < gates.size(); i++) {
            passGate(player, gates.get(i), start + lap * i / gates.size());
        }
        passGate(player, gates.getFirst(), start + lap);
        return start + lap;
    }

    private static RacePayloads.RaceReportC2S report(RacePayloads.ReportKind kind, int course, int lap, long lapNanos, long total, int assist) {
        return new RacePayloads.RaceReportC2S(kind, course, lap, lapNanos, total, RacePayloads.RaceReportC2S.assistFlags(assist), 0L);
    }

    private static @Nullable String check(MinecraftServer server) {
        ServerPlayer player = player(server);
        WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, 0);
        Site site = build(server);
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(site.course());
        if (course == null) {
            return "Kurs fehlt";
        }
        int id = course.id;
        Leaderboard board = registry.board(course);
        registry.setLaps(course, 2);
        List<GateGeometry> gates = course.toData().geometry();
        long lap = Math.max(RaceServer.minimumLapNanos(course, player) * 3L, 6_000L * MS);
        long t = System.nanoTime() + 60_000L * MS;
        RaceServer.handle(report(RacePayloads.ReportKind.START, id, 0, 0L, 0L, 0), player, t);
        long crossed = flyLap(player, gates, t + 100L * MS, lap);
        RaceServer.Verdict verdict = RaceServer.handle(report(RacePayloads.ReportKind.LAP, id, 1, lap, 0L, HOLD), player, crossed + 100L * MS);
        if (verdict != RaceServer.Verdict.ACCEPTED) {
            return "Runde mit Flughilfe (Regel aus) abgelehnt: " + verdict;
        }
        if (board.times() != 0) {
            return "Regel aus, aber Runde mit Flughilfe gespeichert: " + board.entries() + board.assistedEntries();
        }
        if (!RaceServer.leaderboardPayload(board, course, player.getUUID(), false, false).rows().isEmpty()) {
            return "Regel aus, aber Zeilen in der Bestenliste";
        }
        WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, 1);
        t = crossed + 2_000L * MS;
        RaceServer.handle(report(RacePayloads.ReportKind.START, id, 0, 0L, 0L, 0), player, t);
        crossed = flyLap(player, gates, t + 100L * MS, lap);
        verdict = RaceServer.handle(report(RacePayloads.ReportKind.LAP, id, 1, lap, 0L, HOLD | RESCUE), player, crossed + 100L * MS);
        if (verdict != RaceServer.Verdict.ACCEPTED) {
            return "Runde mit Flughilfe (Regel an) abgelehnt: " + verdict;
        }
        Leaderboard.Entry assisted = board.bestAssisted(player.getUUID(), null);
        if (assisted == null || assisted.nanos() != lap || assisted.assist() != (HOLD | RESCUE)) {
            return "Runde mit Flughilfe nicht mit Gründen gespeichert: " + assisted;
        }
        if (!board.top(null, 10).isEmpty() || board.record(null).isPresent()) {
            return "Runde mit Flughilfe landet in der Wertung ohne Flughilfe";
        }
        long faster = lap - 500L * MS;
        crossed = flyLap(player, gates, crossed, faster);
        verdict = RaceServer.handle(report(RacePayloads.ReportKind.LAP, id, 2, faster, 0L, 0), player, crossed + 100L * MS);
        if (verdict != RaceServer.Verdict.ACCEPTED) {
            return "Zweite Runde ohne Flughilfe abgelehnt: " + verdict;
        }
        Leaderboard.Entry plain = board.best(player.getUUID(), null);
        if (plain == null || plain.nanos() != faster || plain.assisted()) {
            return "Runde ohne Flughilfe nach einer Runde mit Flughilfe nicht gewertet: " + plain;
        }
        verdict = RaceServer.handle(report(RacePayloads.ReportKind.FINISH, id, 2, 0L, lap + faster, 0), player, crossed + 200L * MS);
        Leaderboard.AssistedRace race = board.assistedRaceBest(player.getUUID(), 2);
        if (verdict != RaceServer.Verdict.ACCEPTED || race == null || race.nanos() != lap + faster || board.raceBest(player.getUUID(), 2) != LapTracker.NONE) {
            return "Rennen mit Flughilfe nicht getrennt gespeichert: " + verdict + " " + race;
        }
        WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, 130);
        try {
            t = crossed + 2_000L * MS;
            RaceServer.handle(report(RacePayloads.ReportKind.START, id, 0, 0L, 0L, 0), player, t);
            long quick = lap - 1_000L * MS;
            QuadcopterEntity drone = (QuadcopterEntity) player.level().getEntity(site.drone());
            if (drone == null || (RaceServer.serverAssist(drone) & PHYSICS) == 0) {
                return "Server erkennt veränderte Physik nicht als Flughilfe";
            }
            crossed = flyLap(player, gates, t + 100L * MS, quick);
            verdict = RaceServer.handle(report(RacePayloads.ReportKind.LAP, id, 1, quick, 0L, 0), player, crossed + 100L * MS);
            Leaderboard.Entry physics = board.bestAssisted(player.getUUID(), null);
            if (verdict != RaceServer.Verdict.ACCEPTED || physics == null || physics.nanos() != quick || (physics.assist() & PHYSICS) == 0) {
                return "Runde mit veränderter Welt-Physik nicht als Flughilfe gespeichert: " + verdict + " " + physics;
            }
            if (board.best(player.getUUID(), null).nanos() != faster) {
                return "Runde mit veränderter Physik verändert die Wertung ohne Flughilfe";
            }
        } finally {
            WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.GRAVITY_SCALE.defaultValue());
        }
        RacePayloads.LeaderboardS2C payload = RaceServer.leaderboardPayload(board, course, player.getUUID(), false, true);
        long assistedRows = payload.rows().stream().filter(RacePayloads.BoardRow::assisted).count();
        long plainRows = payload.rows().stream().filter(row -> !row.assisted()).count();
        if (assistedRows != 1 || plainRows != 1) {
            return "Bestenliste sendet " + plainRows + " Zeilen ohne und " + assistedRows + " mit Flughilfe";
        }
        if (!RaceServer.leaderboardPayload(board, course, player.getUUID(), false, false).rows().stream().noneMatch(RacePayloads.BoardRow::assisted)) {
            return "Regel aus, aber Zeilen mit Flughilfe";
        }
        fill(board);
        return null;
    }

    static void fill(Leaderboard board) {
        long now = System.currentTimeMillis();
        board.submit(new UUID(77L, 1L), "Swift", "race_5", 9_310L * MS, now - 86_400_000L);
        board.submit(new UUID(77L, 2L), "Heron", "freestyle_5", 10_870L * MS, now - 3L * 86_400_000L);
        board.submitAssisted(new UUID(77L, 3L), "Lark", "whoop_65", 11_420L * MS, now - 7_200_000L, HOLD);
        board.submitAssisted(new UUID(77L, 4L), "Wren", "freestyle_5", 12_050L * MS, now, PHYSICS | HOLD);
    }

    private static StepResult screenStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Kurs aus dem vorigen Schritt");
        }
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> RaceServer.sendLeaderboard(player(server), site.course(), true)));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        LeaderboardScreen screen = LeaderboardScreen.active();
        if (screen == null || screen.courseId() != site.course() || screen.assistedRowsDrawn() == 0 || !ClientWorldRules.INSTANCE.on(WorldRuleSpecs.SAVE_ASSISTED_LAPS)) {
            return ctx.tick() > 200 ? StepResult.fail("Abschnitt „Mit Flughilfe“ nicht gezeichnet: Fenster=" + (screen != null)
                    + " Zeilen=" + (screen == null ? 0 : screen.assistedRowsDrawn())) : StepResult.pending();
        }
        List<RacePayloads.BoardRow> assisted = screen.shownAssistedRows();
        List<RacePayloads.BoardRow> plain = screen.shownRows();
        if (assisted.isEmpty() || plain.isEmpty() || plain.stream().anyMatch(RacePayloads.BoardRow::assisted)
                || assisted.stream().anyMatch(row -> !row.assisted())) {
            return StepResult.fail("Zeilen gemischt: ohne=" + plain + " mit=" + assisted);
        }
        String reasons = AssistText.storedReasons(assisted.getFirst().assist()).getString();
        if (reasons.isBlank()) {
            return StepResult.fail("keine Gründe für die Runde mit Flughilfe");
        }
        ctx.log("Bestenliste: " + plain.size() + " Zeilen ohne, " + assisted.size() + " mit Flughilfe, Gründe der ersten: " + reasons);
        mc.gui.setScreen(null);
        return StepResult.pass();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(AssistedLapSelfTests::teardown));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        return waiting != null ? waiting : StepResult.pass();
    }

    static void teardown(MinecraftServer server) {
        WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, WorldRuleSpecs.SAVE_ASSISTED_LAPS.defaultValue());
        Site site = SITE.getAndSet(null);
        if (site == null) {
            return;
        }
        ServerLevel level = player(server).level();
        for (int entity : new int[]{site.drone(), site.gateA(), site.gateB()}) {
            if (level.getEntity(entity) instanceof QuadcopterEntity drone) {
                drone.setPilot(null);
                drone.discard();
            } else if (level.getEntity(entity) != null) {
                level.getEntity(entity).discard();
            }
        }
        CourseRegistry registry = CourseRegistry.get();
        if (registry != null) {
            registry.deleteCourse(site.course());
        }
    }

    private static final class RaceScene implements Screenshots.Scene {
        private static final double HOVER_RISE = 2.5;
        private static final int SETTLE_TICKS = 50;
        private int phase;
        private int since;
        private double targetY;
        private double lastY = Double.NaN;
        private int armedAt = -1;
        private volatile int droneId = -1;
        private volatile @Nullable String failure;
        private @Nullable CompletableFuture<Void> pending;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            CompletableFuture<Void> future = this.pending;
            if (future != null) {
                if (future.isCompletedExceptionally()) {
                    return StepResult.fail("Server-Schritt fehlgeschlagen: " + future.exceptionNow());
                }
                if (!future.isDone()) {
                    return StepResult.pending();
                }
                this.pending = null;
            }
            if (this.failure != null) {
                return StepResult.fail(this.failure);
            }
            Minecraft mc = Minecraft.getInstance();
            PilotSession session = PilotSession.INSTANCE;
            switch (this.phase) {
                case 0 -> {
                    if (ctx.tick() < DronePiloting.REMOTE_DEBOUNCE_TICKS + 2) {
                        return StepResult.pending();
                    }
                    this.pending = ctx.onServer(this::start);
                    this.phase = 1;
                    this.since = ctx.tick();
                }
                case 1 -> {
                    QuadcopterEntity drone = session.drone();
                    if (!session.isFpv() || drone == null || drone.getId() != this.droneId) {
                        return ctx.tick() - this.since > 200 ? StepResult.fail("FPV-Sitzung startet nicht") : StepResult.pending();
                    }
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
                    Site site = SITE.get();
                    CourseData course = site == null ? null : ClientCourses.get(site.course());
                    if (course == null || !ClientWorldRules.INSTANCE.physics().modified()
                            || !ClientWorldRules.INSTANCE.on(WorldRuleSpecs.SAVE_ASSISTED_LAPS)) {
                        return ctx.tick() - this.since > 200 ? StepResult.fail("Kurs oder Welt-Regeln fehlen beim Client") : StepResult.pending();
                    }
                    List<GateGeometry> gates = ClientCourses.geometry(course);
                    GateGeometry gate = gates.getFirst();
                    GateGeometry next = gates.getLast();
                    Vec3 after = new Vec3(gate.x() + gate.normalX() * 2.0, gate.y() + 0.2, gate.z() + gate.normalZ() * 2.0);
                    float yaw = (float) Math.atan2(-(next.x() - after.x), -(next.z() - after.z));
                    int id = this.droneId;
                    this.pending = ctx.onServer(server -> {
                        if (player(server).level().getEntity(id) instanceof QuadcopterEntity serverDrone) {
                            serverDrone.resetTo(after, new Quaternionf().rotateY(yaw));
                        }
                    });
                    this.phase = 2;
                    this.since = ctx.tick();
                }
                case 2 -> {
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
                    Site site = SITE.get();
                    CourseData course = site == null ? null : ClientCourses.get(site.course());
                    QuadcopterEntity drone = session.drone();
                    if (course == null || drone == null) {
                        return StepResult.fail("Sitzung oder Kurs verloren");
                    }
                    GateGeometry gate = ClientCourses.geometry(course).getFirst();
                    Vec3 center = new Vec3(gate.x(), gate.centerY(), gate.z());
                    Vec3 normal = new Vec3(gate.normalX(), 0.0, gate.normalZ());
                    if (drone.position().distanceTo(center) > 4.0) {
                        return ctx.tick() - this.since > 200 ? StepResult.fail("Drohne kommt nicht ans Start-Gate") : StepResult.pending();
                    }
                    ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
                    Vec3 before = center.subtract(normal.scale(3.0));
                    long now = tracker.now();
                    tracker.selectCourseAt(mc, before);
                    tracker.tracker().resetPosition();
                    tracker.feed(now, before);
                    tracker.feed(now + 150_000_000L, center);
                    tracker.feed(now + 300_000_000L, center.add(normal.scale(1.5)));
                    this.phase = 3;
                    this.since = ctx.tick();
                }
                case 3 -> {
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
                    ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
                    if (!tracker.tracker().racing() || !tracker.lapAssisted() || !RaceHud.showsAssistTag()) {
                        return ctx.tick() - this.since > 100 ? StepResult.fail("Runde nicht mit Flughilfe gestartet: Rennen="
                                + tracker.tracker().racing() + " Hilfe=" + tracker.lapAssist()) : StepResult.pending();
                    }
                    if ((tracker.lapAssist() & PHYSICS) == 0) {
                        return StepResult.fail("Grund Welt-Physik fehlt: " + tracker.lapAssist());
                    }
                    this.targetY = session.comPosition(new Vector3d()).y + HOVER_RISE;
                    this.phase = 4;
                    this.since = ctx.tick();
                }
                default -> {
                    DroneSim sim = session.sim();
                    if (sim == null || !session.isFpv()) {
                        return StepResult.fail("FPV-Sitzung beim Abheben verloren");
                    }
                    if (!sim.telemetry().armed) {
                        int elapsed = ctx.tick() - this.since;
                        session.setTestInput(StickPositions.IDLE, elapsed >= 10 && sim.telemetry().armBlockers == 0 && ctx.tick() % 10 >= 5, StabMode.ANGLE);
                        return elapsed > 160 ? StepResult.fail("nicht scharf: Sperren=" + sim.telemetry().armBlockers) : StepResult.pending();
                    }
                    if (this.armedAt < 0) {
                        this.armedAt = ctx.tick();
                    }
                    this.hover(session, sim);
                    double y = session.comPosition(new Vector3d()).y;
                    boolean settled = ctx.tick() - this.armedAt >= SETTLE_TICKS && Math.abs(y - this.targetY) < 0.8;
                    if (!settled) {
                        return ctx.tick() - this.since > 400 ? StepResult.fail(String.format(Locale.ROOT, "Drohne schwebt nicht: scharf=%s y=%.2f Ziel=%.2f Sperren=%d",
                                sim.telemetry().armed, y, this.targetY, sim.telemetry().armBlockers)) : StepResult.pending();
                    }
                    if (!ClientRaceTracker.INSTANCE.tracker().racing() || !RaceHud.showsAssistTag()) {
                        return StepResult.fail("Runde mit Flughilfe beim Abheben beendet");
                    }
                    return StepResult.pass();
                }
            }
            return StepResult.pending();
        }

        private void start(MinecraftServer server) {
            ServerPlayer player = player(server);
            ServerLevel level = player.level();
            WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, 1);
            WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, 130);
            Site site = SITE.get();
            if (site == null) {
                site = build(server);
            }
            if (level.getEntity(site.drone()) instanceof QuadcopterEntity old) {
                old.setPilot(null);
                old.discard();
            }
            int x = player.getBlockX() + 3;
            int z = player.getBlockZ() + 2;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos ground = new BlockPos(x, top - 1, z);
            ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
            stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
            stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            InteractionResult use = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(new Vec3(x + 0.5, top, z + 0.5), Direction.UP, ground, false)));
            if (!use.consumesAction()) {
                this.failure = "Platzieren lieferte " + use;
                return;
            }
            List<QuadcopterEntity> drones = level.getEntitiesOfClass(QuadcopterEntity.class, new AABB(ground).inflate(3.0),
                    d -> d.build().equals(DroneBuild.FREESTYLE) && d.pilot().isEmpty());
            if (drones.isEmpty()) {
                this.failure = "keine Drohne nach dem Platzieren";
                return;
            }
            QuadcopterEntity drone = drones.getFirst();
            this.droneId = drone.getId();
            ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
            remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            player.setItemInHand(InteractionHand.MAIN_HAND, remote);
            ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_DIGITAL).map(ItemStack::new).orElse(ItemStack.EMPTY);
            goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            player.setItemSlot(EquipmentSlot.HEAD, goggles);
            DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
            if (result != DronePiloting.StartResult.STARTED) {
                this.failure = "FPV-Start verweigert: " + result;
            }
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            DroneSim sim = session.sim();
            if (sim != null) {
                this.hover(session, sim);
            }
        }

        private void hover(PilotSession session, DroneSim sim) {
            double y = session.comPosition(new Vector3d()).y;
            double vy = Double.isNaN(this.lastY) ? 0.0 : (y - this.lastY) * 20.0;
            this.lastY = y;
            double throttle = sim.telemetry().hoverThrottleStick + (this.targetY - y) * 0.12 - vy * 0.08;
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, (float) Math.clamp(throttle, 0.05, 0.85)), true, StabMode.ANGLE);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            session.clearTestInput();
            ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
            tracker.tracker().abort(tracker.now(), LapTracker.AbortReason.RESTART);
            if (session.isActive()) {
                session.requestStop();
            }
            int id = this.droneId;
            this.phase = 0;
            this.droneId = -1;
            this.failure = null;
            this.lastY = Double.NaN;
            this.armedAt = -1;
            ctx.onServer(server -> {
                WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.GRAVITY_SCALE.defaultValue());
                ServerPlayer player = player(server);
                if (id >= 0 && player.level().getEntity(id) instanceof QuadcopterEntity drone) {
                    if (drone.pilot().isPresent()) {
                        DronePiloting.release(drone);
                    }
                    drone.discard();
                }
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                teardown(server);
            });
        }
    }

    private static final class BoardScene implements Screenshots.Scene {
        private boolean requested;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            if (!this.requested) {
                this.requested = true;
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, 1);
                    Site site = SITE.get();
                    if (site == null) {
                        site = build(server);
                        CourseRegistry registry = CourseRegistry.get();
                        CourseRegistry.Course course = registry == null ? null : registry.course(site.course());
                        if (course == null) {
                            throw new IllegalStateException("Kurs fehlt");
                        }
                        Leaderboard board = registry.board(course);
                        ServerPlayer player = player(server);
                        board.submit(player.getUUID(), player.getGameProfile().name(), "freestyle_5", 10_120L * MS, System.currentTimeMillis());
                        board.submitAssisted(player.getUUID(), player.getGameProfile().name(), "freestyle_5", 9_940L * MS, System.currentTimeMillis(),
                                HOLD | RESCUE);
                        fill(board);
                    }
                }));
                return StepResult.pending();
            }
            StepResult waiting = future(ctx);
            if (waiting != null) {
                return waiting;
            }
            Site site = SITE.get();
            if (site == null) {
                return StepResult.fail("kein Kurs");
            }
            if (!ctx.state().containsKey("sent")) {
                ctx.state().put("sent", true);
                ctx.onServer(server -> RaceServer.sendLeaderboard(player(server), site.course(), true));
                return StepResult.pending();
            }
            LeaderboardScreen screen = LeaderboardScreen.active();
            if (screen == null || screen.assistedRowsDrawn() < 2 || screen.firstAssistedRowY() < 0) {
                return ctx.tick() > 300 ? StepResult.fail("Bestenliste mit Flughilfe öffnet nicht") : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            LeaderboardScreen screen = LeaderboardScreen.active();
            Minecraft mc = Minecraft.getInstance();
            if (screen == null || screen.firstAssistedRowY() < 0) {
                Screenshots.parkMouse(mc);
                return;
            }
            double guiX = screen.tableCenterX() - 40.0;
            double guiY = screen.lastAssistedRowY() + 6.0;
            double x = guiX * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            double y = guiY * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
            mc.mouseHandler.onMove(mc.getWindow().handle(), x, y, 0.0, 0.0);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
            Screenshots.parkMouse(Minecraft.getInstance());
            this.requested = false;
            ctx.state().remove("sent");
            ctx.onServer(AssistedLapSelfTests::teardown);
        }
    }
}
