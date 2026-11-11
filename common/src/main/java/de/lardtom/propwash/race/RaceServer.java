package de.lardtom.propwash.race;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.race.export.RaceDataEvents;
import de.lardtom.propwash.server.AddonWorldRules;
import de.lardtom.propwash.server.PersonalRulesServer;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.stats.PilotStats;
import de.lardtom.propwash.stats.advancement.FpvAdvancements;
import de.lardtom.propwash.stats.advancement.Milestone;
import de.lardtom.propwash.zone.ZoneField;
import de.lardtom.propwash.zone.ZoneLimits;
import de.lardtom.propwash.zone.ZoneServer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RaceServer {
    public static final double ABSOLUTE_MAX_SPEED = 120.0;
    public static final double MAX_REPORT_DISTANCE = 48.0;
    public static final double BROADCAST_RADIUS = 256.0;
    public static final double LOBBY_RANGE = 32.0;
    public static final long MAX_LAP_NANOS = 3_600L * LapTracker.SECOND;
    public static final long PENDING_TIMEOUT_NANOS = 2L * LapTracker.SECOND;
    public static final long START_SLACK_NANOS = 3L * LapTracker.SECOND;
    public static final long COUNTDOWN_SLACK_NANOS = RaceLobby.MAX_COUNTDOWN_SECONDS * LapTracker.SECOND;
    public static final long CROSSING_TOLERANCE_NANOS = 250_000_000L;
    public static final long ZONE_SETTLE_NANOS = ZoneLimits.MAX_TRANSITION_TENTHS * 100_000_000L;
    private static final long BASE_TOLERANCE_NANOS = 1_500_000_000L;
    private static final double GATE_SEARCH_MARGIN = 4.0;
    private static final double CONTINUITY_EPSILON = 1.0E-6;
    private static final int SAVE_INTERVAL_TICKS = 100;
    private static final int LOBBY_REFRESH_TICKS = 20;
    private static final int MAX_REPORTS_PER_SECOND = 8;
    private static final int MAX_REQUESTS_PER_SECOND = 4;
    private static final int MAX_PAGES_PER_SECOND = 10;
    private static final Quaternionfc LEVEL = new Quaternionf();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<Integer, RaceLobby> LOBBIES = new HashMap<>();
    private static final Map<Integer, RaceLobby.Heat> HEATS = new HashMap<>();
    private static int ticks;

    private RaceServer() {
    }

    private record Pending(RacePayloads.RaceReportC2S report, long deadline) {
    }

    private static final class Session {
        final LapVerifier verifier = new LapVerifier();
        final GhostRecorder ghost = new GhostRecorder();
        final ArrayDeque<Pending> pending = new ArrayDeque<>();
        int courseId = GateEntity.NO_COURSE;
        int lap;
        long startedAt;
        long lastEndSequence = LapVerifier.NO_SEQUENCE;
        long nextEvent;
        long lastEventNanos;
        long lapSum;
        int counted;
        int skipped;
        long windowStart;
        int windowReports;
        long requestWindowStart;
        int requestWindow;
        long pageWindowStart;
        int pageWindow;
        final AssistTimeline assist = new AssistTimeline();
        final RuleTimeline rules = new RuleTimeline();
        final ZoneField zones = new ZoneField();
        int zoneDrone = Integer.MIN_VALUE;
        long zoneNanos;
        AssistReasons raceFixed = AssistReasons.NONE;
        @Nullable RaceRuleSet raceRules;
        boolean raceCreativeFc;
        boolean hasLastMove;
        Vec3 lastMove = Vec3.ZERO;
        long lastMoveNanos;

        RaceRuleSet observe(QuadcopterEntity drone, @Nullable UUID pilot, long now) {
            MinecraftServer server = drone.level().getServer();
            if (server == null) {
                return RaceRules.observe(RaceRules.values(server), ZoneServer.resolve(drone.level(), pilot, drone.getX(), drone.getY(), drone.getZ()),
                        null);
            }
            boolean fresh = drone.getId() != this.zoneDrone || now < this.zoneNanos || now - this.zoneNanos > ZONE_SETTLE_NANOS;
            if (fresh) {
                this.zones.reset();
            }
            RuleValues world = WorldRules.values(server);
            this.zones.update(ZoneServer.zones(drone.level()), ZoneServer.dimension(drone.level()), PersonalRulesServer.base(server, pilot), drone.getX(),
                    drone.getY(), drone.getZ(), fresh ? 0.0 : (now - this.zoneNanos) / 1.0e9);
            this.zoneDrone = drone.getId();
            this.zoneNanos = now;
            Map<Identifier, String> addon = AddonWorldRules.registered().isEmpty() ? Map.of() : AddonWorldRules.values(server);
            return RaceRules.observe(RaceRules.values(world, addon), this.zones.resolution(), this.zones);
        }

        boolean allow(long now) {
            if (now - this.windowStart > LapTracker.SECOND || now < this.windowStart) {
                this.windowStart = now;
                this.windowReports = 0;
            }
            return ++this.windowReports <= MAX_REPORTS_PER_SECOND;
        }

        boolean allowRequest(long now) {
            if (now - this.requestWindowStart > LapTracker.SECOND || now < this.requestWindowStart) {
                this.requestWindowStart = now;
                this.requestWindow = 0;
            }
            return ++this.requestWindow <= MAX_REQUESTS_PER_SECOND;
        }

        boolean allowPage(long now) {
            if (now - this.pageWindowStart > LapTracker.SECOND || now < this.pageWindowStart) {
                this.pageWindowStart = now;
                this.pageWindow = 0;
            }
            return ++this.pageWindow <= MAX_PAGES_PER_SECOND;
        }

        void begin(int courseId, long now) {
            this.courseId = courseId;
            this.lap = 0;
            this.lapSum = 0L;
            this.counted = 0;
            this.skipped = 0;
            this.lastEventNanos = now;
            this.raceFixed = AssistReasons.NONE;
            this.raceRules = null;
            this.raceCreativeFc = false;
            this.startedAt = now;
            this.lastEndSequence = LapVerifier.NO_SEQUENCE;
            this.pending.clear();
            this.ghost.onLapStart(courseId, now - START_SLACK_NANOS - COUNTDOWN_SLACK_NANOS);
        }

        void reset() {
            this.courseId = GateEntity.NO_COURSE;
            this.lap = 0;
            this.lapSum = 0L;
            this.lastEndSequence = LapVerifier.NO_SEQUENCE;
            this.pending.clear();
            this.ghost.stop();
        }
    }

    public enum Verdict {
        ACCEPTED,
        NO_COURSE,
        WRONG_DIMENSION,
        NOT_PILOTING,
        TOO_FAR,
        OUT_OF_SEQUENCE,
        IMPLAUSIBLE_TIME,
        UNVERIFIED,
        PENDING,
        RATE_LIMITED
    }

    static void clear() {
        SESSIONS.clear();
        LOBBIES.clear();
        HEATS.clear();
        GhostServer.clear();
        GhostUploads.clear();
        ticks = 0;
    }

    static void onLeave(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
        GhostServer.leave(player);
        GhostUploads.leave(player);
        leaveLobbies(player.level().getServer(), player.getUUID());
        for (RaceLobby.Heat heat : HEATS.values()) {
            heat.drop(player.getUUID());
        }
    }

    static void onServerTick(MinecraftServer server) {
        long now = System.nanoTime();
        if (!SESSIONS.isEmpty()) {
            for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
                Session session = entry.getValue();
                if (!session.pending.isEmpty()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                    if (player == null) {
                        session.reset();
                    } else {
                        drainPending(session, player, now);
                    }
                }
            }
        }
        ticks++;
        GhostServer.tick(server);
        GhostUploads.tick(server);
        tickLobbies(server, now, ticks % LOBBY_REFRESH_TICKS == 0);
        HEATS.values().removeIf(heat -> heat.done(now));
        if (ticks % SAVE_INTERVAL_TICKS == 0) {
            CourseRegistry registry = CourseRegistry.get();
            if (registry != null) {
                registry.save();
            }
        }
    }

    public static int serverAssist(QuadcopterEntity drone) {
        return serverReasons(drone).mask();
    }

    public static AssistReasons serverReasons(QuadcopterEntity drone) {
        RaceStandard standard = RaceStandards.server(drone.level().getServer());
        AssistReasons physics = serverRules(drone).personalReasons(standard);
        if (physics.none() && drone.physicsModified()) {
            physics = AssistReasons.of(List.of(AssistDetail.clientPhysics()));
        }
        return physics.plus(AssistCheck.drone(drone.build().isCreative(), issues(drone.build()), drone.scale()));
    }

    public static RaceRuleSet serverRules(QuadcopterEntity drone) {
        return RaceRules.observe(RaceRules.values(drone.level().getServer()), ZoneServer.resolve(drone.level(), drone.pilot().orElse(null), drone.getX(),
                drone.getY(), drone.getZ()), null);
    }

    public static List<String> issues(DroneBuild build) {
        List<String> out = new ArrayList<>();
        for (DroneBuild.Issue issue : build.problems()) {
            out.add(issue.name().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    static void onDroneMoved(QuadcopterEntity drone, ServerPlayer pilot, Vec3 from, Vec3 to) {
        onDroneMoved(drone, pilot, from, to, System.nanoTime());
    }

    public static void onDroneMoved(QuadcopterEntity drone, ServerPlayer pilot, Vec3 from, Vec3 to, long now) {
        Session session = SESSIONS.computeIfAbsent(pilot.getUUID(), uuid -> new Session());
        AssistReasons build = AssistCheck.drone(drone.build().isCreative(), issues(drone.build()), drone.scale());
        RaceRuleSet rules = session.observe(drone, pilot.getUUID(), now);
        RaceStandard serverNow = RaceStandards.server(drone.level().getServer());
        session.rules.observe(now, rules, serverNow);
        AssistReasons claim = drone.physicsModified() && rules.matches(serverNow)
                ? AssistReasons.of(List.of(AssistDetail.clientPhysics())) : AssistReasons.NONE;
        session.assist.observe(now, build.plus(claim));
        moved(pilot, session, from, to, drone.orientation(), drone.getBbHeight() * 0.5, now);
    }

    public static void onHomeReset(QuadcopterEntity drone, ServerPlayer pilot) {
        Session session = SESSIONS.get(pilot.getUUID());
        if (session != null) {
            RaceLobby.Heat heat = HEATS.get(session.courseId);
            if (heat != null) {
                heat.drop(pilot.getUUID());
            }
            session.reset();
            session.hasLastMove = false;
        }
        leaveLobbies(pilot.level().getServer(), pilot.getUUID());
    }

    public static int sessionCourse(ServerPlayer pilot) {
        Session session = SESSIONS.get(pilot.getUUID());
        return session == null ? GateEntity.NO_COURSE : session.courseId;
    }

    public static void observe(ServerPlayer pilot, AssistReasons reasons, long now) {
        SESSIONS.computeIfAbsent(pilot.getUUID(), uuid -> new Session()).assist.observe(now, reasons);
    }

    public static void onDroneMoved(ServerPlayer pilot, Vec3 from, Vec3 to, long now) {
        onDroneMoved(pilot, from, to, LEVEL, now);
    }

    public static void onDroneMoved(ServerPlayer pilot, Vec3 from, Vec3 to, Quaternionfc orientation, long now) {
        moved(pilot, SESSIONS.computeIfAbsent(pilot.getUUID(), uuid -> new Session()), from, to, orientation, 0.0, now);
    }

    private static void moved(ServerPlayer pilot, Session session, Vec3 from, Vec3 to, Quaternionfc orientation, double lift, long now) {
        boolean continuous = session.hasLastMove && session.lastMove.distanceToSqr(from) < CONTINUITY_EPSILON && now > session.lastMoveNanos;
        long fromNanos = continuous ? session.lastMoveNanos : now;
        session.hasLastMove = true;
        session.lastMove = to;
        session.lastMoveNanos = now;
        session.ghost.sample(now, to.x, to.y, to.z, orientation);
        if (from.distanceToSqr(to) > LapTracker.MAX_SEGMENT * LapTracker.MAX_SEGMENT || from.distanceToSqr(to) == 0.0) {
            return;
        }
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            return;
        }
        AABB area = new AABB(from, to).inflate(GATE_SEARCH_MARGIN + Math.max(Math.max(CourseElement.REACH, ScoreArea.MAX_EXTENT + 1.0), FreeformReach.reach()));
        List<GateEntity> gates = pilot.level().getEntities(RaceModule.GATE.get(), area,
                gate -> (gate.inCourse() || gate.signals()) && !gate.isRemoved());
        if (gates.isEmpty()) {
            if (!session.pending.isEmpty()) {
                drainPending(session, pilot, now);
            }
            return;
        }
        List<double[]> hits = new ArrayList<>();
        for (GateEntity gate : gates) {
            int course = gate.inCourse() ? registry.courseOf(gate.getUUID()) : GateEntity.NO_COURSE;
            boolean member = course != GateEntity.NO_COURSE && course == gate.courseId();
            if (gate.signals() && gate.geometry().stageCrossing(0, from.x, from.y + lift, from.z, to.x, to.y + lift, to.z) >= 0.0) {
                GateSignals.passed(gate, member, member && session.courseId == course);
            }
            if (!member) {
                continue;
            }
            CourseCrossings.collect(gate.geometry(), gate.passes(), course, (element, stage) -> checkpoint(registry, course, element, stage), from.x, from.y + lift,
                    from.z, to.x, to.y + lift, to.z, () -> session.nextEvent++, hits);
        }
        hits.removeIf(hit -> hit[CourseCrossings.CHECKPOINT] < 0.0);
        CourseCrossings.sort(hits, id -> loopCheckpoints(registry, id));
        for (double[] hit : hits) {
            long time = fromNanos + Math.round((now - fromNanos) * hit[CourseCrossings.TIME]);
            session.verifier.record((int) hit[CourseCrossings.COURSE], (int) hit[CourseCrossings.CHECKPOINT], time, (long) hit[CourseCrossings.EVENT]);
        }
        if (!session.pending.isEmpty()) {
            drainPending(session, pilot, now);
        }
    }

    private static int loopCheckpoints(CourseRegistry registry, int courseId) {
        CourseRegistry.Course course = registry.course(courseId);
        return course == null || course.pointToPoint() ? CourseCrossings.OPEN : course.checkpoints();
    }

    private static int checkpoint(CourseRegistry registry, int courseId, int element, int stage) {
        CourseRegistry.Course course = registry.course(courseId);
        if (course == null) {
            return CourseCheckpoints.NONE;
        }
        boolean plain = true;
        for (CourseRegistry.GateRecord record : course.gates) {
            if (!record.kind().gate()) {
                plain = false;
                break;
            }
        }
        if (plain) {
            return stage == 0 ? element : CourseCheckpoints.NONE;
        }
        return CourseCheckpoints.index(course.scoringKinds(), element, stage);
    }

    static void onReport(RacePayloads.RaceReportC2S report, ServerPayloadContext context) {
        Verdict verdict = handle(report, context.player(), System.nanoTime());
        if (verdict != Verdict.ACCEPTED) {
            Constants.LOG.debug("Rennmeldung {} von {} verworfen: {}", report, context.player().getGameProfile().name(), verdict);
        }
    }

    public static Verdict handle(RacePayloads.RaceReportC2S report, ServerPlayer player, long now) {
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            return Verdict.NO_COURSE;
        }
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), uuid -> new Session());
        if (!session.allow(now)) {
            return Verdict.RATE_LIMITED;
        }
        if (report.kind() == RacePayloads.ReportKind.ABORT) {
            RaceLobby.Heat heat = HEATS.get(session.courseId);
            if (heat != null) {
                heat.drop(player.getUUID());
            }
            session.reset();
            return Verdict.ACCEPTED;
        }
        CourseRegistry.Course course = registry.course(report.courseId());
        if (course == null || course.gates.isEmpty()) {
            return refuse(session, player, report, Verdict.NO_COURSE);
        }
        if (!CourseRegistry.sameDimension(course, player.level())) {
            return refuse(session, player, report, Verdict.WRONG_DIMENSION);
        }
        QuadcopterEntity drone = pilotedDrone(player);
        if (drone == null) {
            return refuse(session, player, report, Verdict.NOT_PILOTING);
        }
        CourseRegistry.GateRecord start = report.kind() == RacePayloads.ReportKind.LAP && course.pointToPoint() ? course.gates.getLast()
                : course.gates.getFirst();
        if (drone.distanceToSqr(start.x, start.y, start.z) > MAX_REPORT_DISTANCE * MAX_REPORT_DISTANCE && report.kind() != RacePayloads.ReportKind.FINISH) {
            return refuse(session, player, report, Verdict.TOO_FAR);
        }
        if (report.kind() == RacePayloads.ReportKind.START) {
            drainPending(session, player, now);
            session.begin(course.id, now);
            session.verifier.prune(now - MAX_LAP_NANOS - START_SLACK_NANOS - COUNTDOWN_SLACK_NANOS);
            return Verdict.ACCEPTED;
        }
        if (session.courseId != course.id) {
            return refuse(session, player, report, Verdict.OUT_OF_SEQUENCE);
        }
        session.pending.addLast(new Pending(report, now + PENDING_TIMEOUT_NANOS));
        Verdict verdict = drainPending(session, player, now);
        return session.pending.isEmpty() ? verdict : Verdict.PENDING;
    }

    public static Verdict flush(ServerPlayer player, long now) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null || session.pending.isEmpty() ? Verdict.ACCEPTED : drainPending(session, player, now);
    }

    private static Verdict drainPending(Session session, ServerPlayer player, long now) {
        Verdict verdict = Verdict.ACCEPTED;
        boolean skipped = false;
        CourseRegistry registry = CourseRegistry.get();
        while (!session.pending.isEmpty()) {
            Pending head = session.pending.peekFirst();
            CourseRegistry.Course course = registry == null ? null : registry.course(head.report().courseId());
            if (course == null || course.gates.isEmpty() || course.id != session.courseId) {
                return refuse(session, player, head.report(), Verdict.NO_COURSE);
            }
            verdict = switch (head.report().kind()) {
                case LAP -> lap(registry, course, session, head.report(), player, now);
                case FINISH -> finish(registry, course, session, head.report(), player);
                default -> Verdict.ACCEPTED;
            };
            if (verdict == Verdict.PENDING) {
                if (now - head.deadline() < 0L) {
                    return Verdict.PENDING;
                }
                skipLap(course, session, head, player);
                session.pending.pollFirst();
                skipped = true;
                continue;
            }
            session.pending.pollFirst();
            if (verdict != Verdict.ACCEPTED) {
                return refuse(session, player, head.report(), verdict);
            }
            tell(session, player, head.report(), RacePayloads.LapResult.COUNTED, RacePayloads.LapVerdictS2C.NO_ELEMENT);
        }
        return skipped ? Verdict.UNVERIFIED : verdict;
    }

    private static void skipLap(CourseRegistry.Course course, Session session, Pending head, ServerPlayer player) {
        RacePayloads.RaceReportC2S report = head.report();
        boolean pointToPoint = course.pointToPoint();
        long notBefore = session.lap == 0 ? session.startedAt - START_SLACK_NANOS - COUNTDOWN_SLACK_NANOS : Long.MIN_VALUE;
        int missing = session.verifier.firstMissing(course.id, course.checkpoints(), session.lastEndSequence, notBefore, pointToPoint);
        int element = CourseCheckpoints.element(course.scoringKinds(), missing);
        long reported = head.deadline() - PENDING_TIMEOUT_NANOS;
        session.lap = report.lap();
        session.lapSum += report.lapNanos();
        session.skipped++;
        session.lastEventNanos = reported;
        session.lastEndSequence = session.verifier.restart(course.id, pointToPoint ? PointToPoint.finish(course.checkpoints()) : 0);
        session.ghost.trim(reported - LapTracker.SECOND);
        Constants.LOG.debug("Runde {} von {} auf Kurs {} vom Server nicht bestätigt, erster fehlender Prüfpunkt {}", report.lap(), player.getGameProfile().name(),
                course.id, missing);
        tell(session, player, report, element == CourseCheckpoints.NONE ? RacePayloads.LapResult.REJECTED : RacePayloads.LapResult.MISSED, element);
    }

    private static Verdict refuse(Session session, ServerPlayer player, RacePayloads.RaceReportC2S report, Verdict verdict) {
        session.reset();
        tell(session, player, report, RacePayloads.LapResult.REJECTED, RacePayloads.LapVerdictS2C.NO_ELEMENT);
        return verdict;
    }

    private static void tell(Session session, ServerPlayer player, RacePayloads.RaceReportC2S report, RacePayloads.LapResult result, int element) {
        if (!player.connection.isAcceptingMessages()) {
            return;
        }
        switch (report.kind()) {
            case LAP -> PropwashNet.sendToPlayer(player, new RacePayloads.LapVerdictS2C(report.courseId(), report.lap(), result, element, session.counted));
            case FINISH -> PropwashNet.sendToPlayer(player, new RacePayloads.LapVerdictS2C(report.courseId(), report.lap(), RacePayloads.LapResult.RACE,
                    RacePayloads.LapVerdictS2C.NO_ELEMENT, session.counted));
            default -> {
            }
        }
    }

    public static boolean penaltyValid(RacePayloads.RaceReportC2S report) {
        long penalty = report.penaltyNanos();
        return penalty == 0L || report.lap() == 1 && penalty == LapTracker.FALSE_START_PENALTY_NANOS;
    }

    private static Verdict lap(CourseRegistry registry, CourseRegistry.Course course, Session session, RacePayloads.RaceReportC2S report,
                               ServerPlayer player, long now) {
        if (session.courseId != course.id || report.lap() != session.lap + 1) {
            return Verdict.OUT_OF_SEQUENCE;
        }
        if (!penaltyValid(report)) {
            return Verdict.IMPLAUSIBLE_TIME;
        }
        long lapNanos = report.lapNanos();
        long flown = lapNanos - report.penaltyNanos();
        long elapsed = now - session.lastEventNanos;
        long tolerance = BASE_TOLERANCE_NANOS + Math.max(0L, elapsed) / 10L;
        if (flown < minimumLapNanos(course, player) || lapNanos > MAX_LAP_NANOS || flown > elapsed + tolerance) {
            return Verdict.IMPLAUSIBLE_TIME;
        }
        LapVerifier.VerifiedLap verified = verifiedLap(session.verifier, course.id, course.checkpoints(), course.pointToPoint(), session.lastEndSequence,
                session.lap == 0, session.startedAt, flown, report.penaltyNanos());
        if (verified == null) {
            return Verdict.PENDING;
        }
        long serverStart = verified.startNanos();
        if (report.penaltyNanos() > 0L) {
            serverStart = Math.max(serverStart, session.startedAt - START_SLACK_NANOS);
        } else if (session.lap == 0 && serverStart < session.startedAt - START_SLACK_NANOS) {
            return Verdict.PENDING;
        }
        long serverNanos = verified.endNanos() - serverStart;
        if (flown + crossingTolerance(serverNanos) < serverNanos) {
            return Verdict.IMPLAUSIBLE_TIME;
        }
        session.lap = report.lap();
        session.lapSum += lapNanos;
        session.counted++;
        session.lastEventNanos = now;
        session.lastEndSequence = verified.endSequence();
        PilotStats.lap(player);
        FpvAdvancements.lap(player, course.ownedBy(player.getUUID()));
        QuadcopterEntity drone = pilotedDrone(player);
        MinecraftServer server = player.level().getServer();
        AssistCheck.LapVerdict lapVerdict = AssistCheck.creativeFc(lapVerdict(session, report, serverStart, verified.endNanos(), drone, server),
                registry.creativeFcLegal(course));
        AssistReasons assist = lapVerdict.reasons();
        session.raceFixed = session.raceFixed.plus(lapVerdict.fixed());
        session.raceRules = session.raceRules == null ? lapVerdict.rules() : session.raceRules.union(lapVerdict.rules());
        session.raceCreativeFc |= lapVerdict.creativeFc();
        notifyServerVerdict(player, report, assist);
        String frame = drone == null ? "" : drone.build().frame().key();
        boolean saved = WorldRules.on(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS);
        long date = System.currentTimeMillis();
        RaceStandard lapServer = RaceStandards.server(server);
        if (assist.any()) {
            Leaderboard.Result result = registry.submitTime(course, player.getUUID(), player.getGameProfile().name(), frame, lapNanos, date,
                    lapVerdict.fixed(), lapVerdict.rules(), saved, lapServer, lapVerdict.creativeFc());
            if (result.stored()) {
                storeGhost(registry, course, session, verified, player, frame, lapNanos, date, assist.mask(), report);
            }
            session.ghost.trim(verified.endNanos() - LapTracker.SECOND);
            boolean best = saved && result.improved();
            if (best) {
                player.sendSystemMessage(Component.translatable("propwash.race.server.personal_best_assisted", CourseRegistry.ref(course), RaceFormat.time(lapNanos),
                        registry.board(course).assistedRank(player.getUUID(), frame), className(frame), assistReasons(assist))
                        .withStyle(ChatFormatting.AQUA));
            }
            RaceDataEvents.lap(registry, course, player.getUUID(), player.getGameProfile().name(), report.lap(), lapNanos, frame, assist, saved, best,
                    false, false);
            return Verdict.ACCEPTED;
        }
        boolean hadBest = registry.board(course).best(player.getUUID(), frame) != null;
        Leaderboard.Result result = registry.submitTime(course, player.getUUID(), player.getGameProfile().name(), frame, lapNanos, date,
                lapVerdict.fixed(), lapVerdict.rules(), saved, lapServer, lapVerdict.creativeFc());
        Leaderboard.Outcome outcome = result.outcome();
        if (hadBest && outcome.improved()) {
            FpvAdvancements.milestone(player, Milestone.PERSONAL_BEST);
        }
        if (result.stored()) {
            storeGhost(registry, course, session, verified, player, frame, lapNanos, date, LapAssist.NONE, report);
        }
        session.ghost.trim(verified.endNanos() - LapTracker.SECOND);
        announce(registry, course, outcome, player, frame, lapNanos);
        RaceDataEvents.lap(registry, course, player.getUUID(), player.getGameProfile().name(), report.lap(), lapNanos, frame, AssistReasons.NONE, true,
                outcome.improved(), outcome.record(), outcome == Leaderboard.Outcome.COURSE_RECORD);
        return Verdict.ACCEPTED;
    }

    public static LapVerifier.@Nullable VerifiedLap verifiedLap(LapVerifier verifier, int courseId, int checkpoints, boolean pointToPoint,
                                                                long afterSequence, boolean firstLap, long startedAt, long flownNanos, long penaltyNanos) {
        long notBefore = firstLap ? startedAt - START_SLACK_NANOS - COUNTDOWN_SLACK_NANOS : Long.MIN_VALUE;
        LapVerifier.VerifiedLap verified = verifier.find(courseId, checkpoints, afterSequence, notBefore, pointToPoint);
        while (verified != null && firstLap && penaltyNanos <= 0L && verified.startNanos() < startedAt - START_SLACK_NANOS) {
            verified = verifier.find(courseId, checkpoints, verified.endSequence(), notBefore, pointToPoint);
        }
        if (verified == null) {
            return null;
        }
        long earliest = firstLap && penaltyNanos <= 0L ? startedAt - START_SLACK_NANOS : Long.MIN_VALUE;
        long floor = penaltyNanos > 0L ? startedAt - START_SLACK_NANOS : Long.MIN_VALUE;
        return timedStart(verified, flownNanos, earliest, floor);
    }

    public static LapVerifier.VerifiedLap timedStart(LapVerifier.VerifiedLap verified, long flownNanos, long earliestNanos, long floorNanos) {
        long best = verified.startNanos();
        long bestGap = Long.MAX_VALUE;
        for (long start : verified.starts()) {
            if (start < earliestNanos) {
                continue;
            }
            long nanos = verified.endNanos() - Math.max(start, floorNanos);
            if (flownNanos + crossingTolerance(nanos) < nanos) {
                continue;
            }
            long gap = Math.abs(nanos - flownNanos);
            if (gap <= bestGap) {
                bestGap = gap;
                best = start;
            }
        }
        return best == verified.startNanos() ? verified : verified.startingAt(best);
    }

    private static void storeGhost(CourseRegistry registry, CourseRegistry.Course course, Session session, LapVerifier.VerifiedLap verified,
                                   ServerPlayer player, String frame, long lapNanos, long date, int assist, RacePayloads.RaceReportC2S report) {
        int layout = course.toData().layoutHash();
        GhostTrack cut = session.ghost.cut(verified.startNanos(), verified.endNanos(), lapNanos, frame, layout, GhostServer.FINE_INTERVAL_NANOS,
                GhostRecorder.LEAD_NANOS);
        GhostTrack track = cut == null ? null : cut.withRules(RaceStandards.server(player.level().getServer()));
        String clipped = Leaderboard.clipFrame(frame);
        if (GhostServer.store(registry, course, track, player, clipped, lapNanos, date, assist)) {
            GhostRecorder.Raw raw = session.ghost.window(verified.startNanos() - GhostRecorder.LEAD_NANOS, verified.endNanos() + GhostRecorder.LEAD_NANOS);
            GhostUploadCheck.Reference reference = GhostUploadCheck.Reference.of(raw, verified.startNanos(), verified.endNanos(), track,
                    course.toData().geometry(), lapNanos, report.penaltyNanos(), player.connection.latency() * 1_000_000L);
            GhostUploads.expect(player, course, new GhostKey(player.getUUID(), clipped, lapNanos, date), report.lap(), reference);
        }
    }

    public static long crossingTolerance(long serverLapNanos) {
        return CROSSING_TOLERANCE_NANOS + Math.max(0L, serverLapNanos) / 100L;
    }

    private static Verdict finish(CourseRegistry registry, CourseRegistry.Course course, Session session, RacePayloads.RaceReportC2S report,
                                  ServerPlayer player) {
        boolean consistent = session.courseId == course.id && session.lap > 0 && report.lap() == session.lap
                && (course.pointToPoint() ? session.lap == 1 : CourseLaps.finishAllowed(course.laps, session.lap))
                && Math.abs(report.totalNanos() - session.lapSum) <= 5_000_000L + session.lap * 1_000_000L;
        if (!consistent) {
            return Verdict.OUT_OF_SEQUENCE;
        }
        if (session.skipped > 0) {
            return Verdict.UNVERIFIED;
        }
        MinecraftServer server = player.level().getServer();
        RaceStandard standard = RaceStandards.server(server);
        RaceRuleSet raceRules = session.raceRules == null ? RaceRuleSet.DEFAULTS : session.raceRules;
        AssistReasons assist = session.raceFixed;
        QuadcopterEntity drone = pilotedDrone(player);
        String frame = drone == null ? "" : drone.build().frame().key();
        boolean keepAssisted = WorldRules.on(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS);
        boolean saved = assist.none() || keepAssisted;
        boolean best = false;
        Leaderboard.Result result = registry.submitRaceTime(course, player, session.lap, report.totalNanos(), frame, session.raceFixed, raceRules,
                keepAssisted, standard, session.raceCreativeFc);
        if (assist.none()) {
            best = result.improved();
            if (best && !course.pointToPoint()) {
                player.sendSystemMessage(Component.translatable("propwash.race.server.race_best", session.lap, CourseRegistry.ref(course),
                        RaceFormat.time(report.totalNanos())).withStyle(ChatFormatting.GOLD));
            }
        } else if (keepAssisted && result.improved()) {
            best = true;
            if (!course.pointToPoint()) {
                player.sendSystemMessage(Component.translatable("propwash.race.server.race_best_assisted", session.lap, CourseRegistry.ref(course),
                        RaceFormat.time(report.totalNanos()), assistReasons(assist)).withStyle(ChatFormatting.AQUA));
            }
        }
        int heatPlace = 0;
        RaceLobby.Heat heat = HEATS.get(course.id);
        if (heat != null) {
            int place = heat.finish(player.getUUID());
            heatPlace = Math.max(0, place);
            if (place > 0 && heat.multiplayer()) {
                Component message = (assist.none() ? Component.translatable("propwash.race.server.heat_place", place, player.getDisplayName(),
                        RaceFormat.time(report.totalNanos())) : Component.translatable("propwash.race.server.heat_place_assisted", place,
                        player.getDisplayName(), RaceFormat.time(report.totalNanos()), assistReasons(assist)))
                        .withStyle(place == 1 ? ChatFormatting.GOLD : ChatFormatting.WHITE);
                for (UUID pilot : heat.pilots()) {
                    ServerPlayer other = player.level().getServer().getPlayerList().getPlayer(pilot);
                    if (other != null) {
                        other.sendSystemMessage(message);
                    }
                }
            }
        }
        PilotStats.raceFinished(player);
        RaceDataEvents.race(course, player.getUUID(), player.getGameProfile().name(), session.lap, report.totalNanos(), frame, assist, saved, best,
                heatPlace);
        session.reset();
        return Verdict.ACCEPTED;
    }

    public static long minimumLapNanos(CourseRegistry.Course course, ServerPlayer player) {
        List<GateGeometry> geometry = course.toData().geometry();
        double length = course.pointToPoint() ? PointToPoint.pathLength(geometry) : GateGeometry.pathLength(geometry);
        double speed = Math.min(ABSOLUTE_MAX_SPEED, ServerRules.maxDroneSpeed(player));
        if (!(speed > 0.0) || length <= 0.0) {
            return 0L;
        }
        return (long) (length / speed * 1.0E9);
    }

    public static Component assistReasons(int mask) {
        return assistReasons(AssistReasons.of(mask));
    }

    public static Component assistReasons(AssistReasons reasons) {
        return AssistWords.describe(reasons);
    }

    static AssistReasons lapReasons(Session session, RacePayloads.RaceReportC2S report, long start, long end, @Nullable QuadcopterEntity drone) {
        return lapVerdict(session, report, start, end, drone, drone == null ? null : drone.level().getServer()).reasons();
    }

    static AssistCheck.LapVerdict lapVerdict(Session session, RacePayloads.RaceReportC2S report, long start, long end, @Nullable QuadcopterEntity drone,
                                             @Nullable MinecraftServer server) {
        AssistReasons fallback = drone != null ? serverReasons(drone) : AssistReasons.NONE;
        RaceRuleSet fallbackRules = drone != null ? serverRules(drone) : RaceRules.world(RaceRules.values(server));
        return AssistCheck.serverLapVerdict(session.assist, session.rules, start, end, fallback, fallbackRules, report.reasons(),
                RaceStandards.server(server));
    }

    private static void notifyServerVerdict(ServerPlayer player, RacePayloads.RaceReportC2S report, AssistReasons verdict) {
        if (verdict.mask() == report.assistMask()) {
            return;
        }
        Component text = verdict.any()
                ? Component.translatable("propwash.race.server.verdict_assisted", report.lap(), assistReasons(verdict))
                : Component.translatable("propwash.race.server.verdict_clean", report.lap(), assistReasons(report.reasons()));
        player.sendSystemMessage(text.copy().withStyle(ChatFormatting.GRAY));
    }

    public static Component className(String frame) {
        return frame.isEmpty() ? Component.translatable("propwash.race.class.unknown") : Component.translatable("propwash.race.class." + frame);
    }

    private static void announce(CourseRegistry registry, CourseRegistry.Course course, Leaderboard.Outcome outcome, ServerPlayer player,
                                 String frame, long lapNanos) {
        QuadcopterEntity piloted = pilotedDrone(player);
        Entity drone = piloted == null ? player : piloted;
        String time = RaceFormat.time(lapNanos);
        switch (outcome) {
            case COURSE_RECORD, CLASS_RECORD -> {
                String key = outcome == Leaderboard.Outcome.COURSE_RECORD ? "propwash.race.server.record" : "propwash.race.server.class_record";
                Component message = Component.translatable(key, player.getDisplayName(), CourseRegistry.ref(course), time, className(frame))
                        .withStyle(ChatFormatting.GOLD);
                ServerLevel level = player.level();
                for (ServerPlayer other : level.players()) {
                    if (other == player || other.distanceToSqr(drone) <= BROADCAST_RADIUS * BROADCAST_RADIUS
                            || other.distanceToSqr(player) <= BROADCAST_RADIUS * BROADCAST_RADIUS) {
                        other.sendSystemMessage(message);
                    }
                }
            }
            case PERSONAL_BEST -> player.sendSystemMessage(Component.translatable("propwash.race.server.personal_best", CourseRegistry.ref(course), time,
                    registry.rankOf(course, player.getUUID(), frame), className(frame)).withStyle(ChatFormatting.AQUA));
            case NONE -> {
            }
        }
    }

    public static @Nullable QuadcopterEntity pilotedDrone(ServerPlayer player) {
        UUID uuid = player.getUUID();
        List<? extends QuadcopterEntity> drones = player.level().getEntities(DroneModule.DRONE.get(),
                drone -> drone.pilot().filter(uuid::equals).isPresent());
        QuadcopterEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (QuadcopterEntity drone : drones) {
            double distance = drone.distanceToSqr((Entity) player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = drone;
            }
        }
        return best;
    }

    static void onLeaderboardRequest(RacePayloads.LeaderboardRequestC2S request, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), uuid -> new Session());
        if (!session.allowRequest(System.nanoTime())) {
            return;
        }
        sendLeaderboard(player, request.courseId(), false);
    }

    public static boolean sendLeaderboard(ServerPlayer player, int courseId, boolean open) {
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(courseId);
        if (registry == null || course == null) {
            return PropwashNet.sendToPlayer(player, new RacePayloads.LeaderboardS2C(courseId, 0, 0, open, 0, List.of()));
        }
        boolean assisted = WorldRules.on(player.level().getServer(), WorldRuleSpecs.SAVE_ASSISTED_LAPS);
        return PropwashNet.sendToPlayer(player, leaderboardPayload(registry.board(course), course, player.getUUID(), open, assisted,
                GhostServer.access(player)));
    }

    public static RacePayloads.LeaderboardS2C leaderboardPayload(Leaderboard board, CourseRegistry.Course course, UUID viewer, boolean open) {
        return leaderboardPayload(board, course, viewer, open, false);
    }

    public static RacePayloads.LeaderboardS2C leaderboardPayload(Leaderboard board, CourseRegistry.Course course, UUID viewer, boolean open,
                                                                 boolean assisted) {
        return leaderboardPayload(board, course, viewer, open, assisted, 0);
    }

    public static RacePayloads.LeaderboardS2C leaderboardPayload(Leaderboard board, CourseRegistry.Course course, UUID viewer, boolean open,
                                                                 boolean assisted, int access) {
        List<Leaderboard.Ranked> clean = board.ranked(false);
        List<Leaderboard.Ranked> flagged = assisted ? board.ranked(true) : List.of();
        List<RacePayloads.BoardRow> rows = boardRows(course, LeaderboardPages.first(clean, flagged, viewer), viewer, RacePayloads.LeaderboardS2C.MAX_ROWS);
        int raceLaps = course.pointToPoint() ? 1 : CourseEdits.lapsFor(course, viewer);
        long raceBest = raceLaps > CourseLaps.ENDLESS && !course.pointToPoint() ? board.raceBest(viewer, raceLaps) : LapTracker.NONE;
        CourseRegistry registry = CourseRegistry.get();
        int archived = registry == null ? 0 : registry.records().archive(course.id).size();
        int times = board.size() + (assisted ? board.assistedSize() : 0);
        return new RacePayloads.LeaderboardS2C(course.id, course.layout, course.gates.size(), open, board.lapsOf(viewer), rows, raceLaps, raceBest,
                archived, access, times, LeaderboardPages.totals(board.revision(), clean, flagged));
    }

    public static RacePayloads.LeaderboardPageS2C leaderboardPage(Leaderboard board, CourseRegistry.Course course, UUID viewer,
                                                                  RacePayloads.LeaderboardPageC2S request, boolean assisted) {
        int revision = board.revision();
        if (request.assisted() && !assisted) {
            return new RacePayloads.LeaderboardPageS2C(course.id, revision, List.of());
        }
        List<Leaderboard.Ranked> view = LeaderboardPages.view(board.ranked(request.assisted()), request.view());
        List<LeaderboardPages.Pick> picks = LeaderboardPages.picks(LeaderboardPages.page(view, request.offset()), request.assisted());
        return new RacePayloads.LeaderboardPageS2C(course.id, revision, boardRows(course, picks, viewer, LeaderboardPages.PAGE));
    }

    private static List<RacePayloads.BoardRow> boardRows(CourseRegistry.Course course, List<LeaderboardPages.Pick> picks, UUID viewer, int maxRows) {
        List<RacePayloads.BoardRow> rows = new ArrayList<>(picks.size());
        List<Leaderboard.Entry> listed = new ArrayList<>(picks.size());
        for (LeaderboardPages.Pick pick : picks) {
            if (rows.size() >= maxRows) {
                break;
            }
            listed.add(pick.ranked().entry());
            rows.add(LeaderboardPages.row(pick, viewer));
        }
        CourseRegistry registry = CourseRegistry.get();
        List<RacePayloads.BoardRow> decorated = registry == null ? rows : GhostServer.decorate(registry, course, rows, listed);
        return LeaderboardPages.fit(decorated, maxRows, LeaderboardPages.MAX_BYTES);
    }

    static void onLeaderboardPage(RacePayloads.LeaderboardPageC2S request, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), uuid -> new Session());
        long now = System.nanoTime();
        if (!session.allowPage(now)) {
            return;
        }
        RacePayloads.LeaderboardPageS2C page = leaderboardPage(player, request);
        if (page != null) {
            PropwashNet.sendToPlayer(player, page);
        } else if (session.allowRequest(now)) {
            sendLeaderboard(player, request.courseId(), false);
        }
    }

    public static RacePayloads.@Nullable LeaderboardPageS2C leaderboardPage(ServerPlayer player, RacePayloads.LeaderboardPageC2S request) {
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(request.courseId());
        if (registry == null || course == null) {
            return null;
        }
        Leaderboard board = registry.board(course);
        if (board.revision() != request.revision()) {
            return null;
        }
        boolean assisted = WorldRules.on(player.level().getServer(), WorldRuleSpecs.SAVE_ASSISTED_LAPS);
        RacePayloads.LeaderboardPageS2C page = leaderboardPage(board, course, player.getUUID(), request, assisted);
        return page.rows().isEmpty() ? null : page;
    }

    static void onLobby(RacePayloads.LobbyC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        MinecraftServer server = context.server();
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), uuid -> new Session());
        long now = System.nanoTime();
        if (!session.allowRequest(now)) {
            return;
        }
        if (payload.action() == RacePayloads.LobbyAction.LEAVE) {
            leaveLobbies(server, player.getUUID());
            PropwashNet.sendToPlayer(player, new RacePayloads.LobbyS2C(payload.courseId(), false, List.of(), 0L));
            return;
        }
        joinLobby(player, payload.courseId(), payload.countdownSeconds(), now);
    }

    public static boolean joinLobby(ServerPlayer player, int courseId, int countdownSeconds, long now) {
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(courseId);
        if (course == null || course.gates.isEmpty() || !CourseRegistry.sameDimension(course, player.level()) || !atStart(player, course)) {
            player.sendOverlayMessage(Component.translatable("propwash.race.lobby.not_at_start").withStyle(ChatFormatting.RED));
            return false;
        }
        for (Map.Entry<Integer, RaceLobby> entry : LOBBIES.entrySet()) {
            if (entry.getKey() != courseId && entry.getValue().leave(player.getUUID())) {
                broadcastLobby(player.level().getServer(), entry.getValue());
            }
        }
        RaceLobby lobby = LOBBIES.computeIfAbsent(courseId, RaceLobby::new);
        if (!lobby.join(player.getUUID(), player.getGameProfile().name(), countdownSeconds, now)) {
            player.sendOverlayMessage(Component.translatable("propwash.race.lobby.full").withStyle(ChatFormatting.RED));
            return false;
        }
        broadcastLobby(player.level().getServer(), lobby);
        return true;
    }

    private static void leaveLobbies(@Nullable MinecraftServer server, UUID player) {
        for (RaceLobby lobby : LOBBIES.values()) {
            if (lobby.leave(player) && server != null) {
                broadcastLobby(server, lobby);
            }
        }
    }

    private static boolean atStart(ServerPlayer player, CourseRegistry.Course course) {
        QuadcopterEntity drone = pilotedDrone(player);
        if (drone == null || course.gates.isEmpty()) {
            return false;
        }
        CourseRegistry.GateRecord start = course.gates.getFirst();
        return drone.distanceToSqr(start.x, start.y, start.z) <= LOBBY_RANGE * LOBBY_RANGE;
    }

    private static List<ServerPlayer> pilotsAtStart(MinecraftServer server, CourseRegistry.Course course) {
        List<ServerPlayer> pilots = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (CourseRegistry.sameDimension(course, player.level()) && atStart(player, course)) {
                pilots.add(player);
            }
        }
        return pilots;
    }

    private static void tickLobbies(MinecraftServer server, long now, boolean refresh) {
        if (LOBBIES.isEmpty()) {
            return;
        }
        CourseRegistry registry = CourseRegistry.get();
        Iterator<Map.Entry<Integer, RaceLobby>> iterator = LOBBIES.entrySet().iterator();
        while (iterator.hasNext()) {
            RaceLobby lobby = iterator.next().getValue();
            CourseRegistry.Course course = registry == null ? null : registry.course(lobby.course());
            if (course == null || course.gates.isEmpty()) {
                for (UUID member : lobby.members()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(member);
                    if (player != null) {
                        PropwashNet.sendToPlayer(player, new RacePayloads.LobbyS2C(lobby.course(), false, List.of(), 0L));
                    }
                }
                iterator.remove();
                continue;
            }
            boolean changed = false;
            for (UUID member : lobby.members()) {
                ServerPlayer player = server.getPlayerList().getPlayer(member);
                if (player == null || !CourseRegistry.sameDimension(course, player.level()) || !atStart(player, course)) {
                    lobby.leave(member);
                    changed = true;
                    if (player != null) {
                        PropwashNet.sendToPlayer(player, new RacePayloads.LobbyS2C(lobby.course(), false, List.of(), 0L));
                    }
                }
            }
            if (lobby.isEmpty()) {
                if (changed) {
                    broadcastLobby(server, lobby);
                }
                iterator.remove();
                continue;
            }
            List<ServerPlayer> atStart = pilotsAtStart(server, course);
            List<UUID> ids = atStart.stream().map(ServerPlayer::getUUID).toList();
            if (lobby.shouldStart(ids, now)) {
                long countdown = lobby.countdownSeconds() * LapTracker.SECOND;
                RaceLobby.Heat heat = lobby.start(now);
                HEATS.put(course.id, heat);
                for (UUID pilot : heat.pilots()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(pilot);
                    if (player != null) {
                        PropwashNet.sendToPlayer(player, new RacePayloads.RaceStartS2C(course.id, countdown, heat.pilots().size()));
                    }
                }
                broadcastLobby(server, lobby);
                iterator.remove();
                continue;
            }
            if (changed || refresh) {
                broadcastLobby(server, lobby);
            }
        }
    }

    private static void broadcastLobby(MinecraftServer server, RaceLobby lobby) {
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(lobby.course());
        long remaining = lobby.remainingNanos(System.nanoTime());
        List<String> names = lobby.names();
        List<ServerPlayer> targets = new ArrayList<>();
        for (UUID member : lobby.members()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                targets.add(player);
            }
        }
        if (course != null) {
            for (ServerPlayer player : pilotsAtStart(server, course)) {
                if (!targets.contains(player)) {
                    targets.add(player);
                }
            }
        }
        for (ServerPlayer player : targets) {
            PropwashNet.sendToPlayer(player, new RacePayloads.LobbyS2C(lobby.course(), lobby.contains(player.getUUID()), names, remaining));
        }
    }

    public static @Nullable RaceLobby lobby(int courseId) {
        return LOBBIES.get(courseId);
    }

    public static RaceLobby.@Nullable Heat heat(int courseId) {
        return HEATS.get(courseId);
    }
}
