package de.lardtom.propwash.race;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
    private static final long BASE_TOLERANCE_NANOS = 1_500_000_000L;
    private static final double GATE_SEARCH_MARGIN = 4.0;
    private static final double CONTINUITY_EPSILON = 1.0E-6;
    private static final int SAVE_INTERVAL_TICKS = 100;
    private static final int LOBBY_REFRESH_TICKS = 20;
    private static final int MAX_REPORTS_PER_SECOND = 8;
    private static final int MAX_REQUESTS_PER_SECOND = 4;
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
        long lastEventNanos;
        long lapSum;
        long windowStart;
        int windowReports;
        long requestWindowStart;
        int requestWindow;
        int lapAssist;
        int raceAssist;
        boolean hasLastMove;
        Vec3 lastMove = Vec3.ZERO;
        long lastMoveNanos;

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

        void begin(int courseId, long now) {
            this.courseId = courseId;
            this.lap = 0;
            this.lapSum = 0L;
            this.lastEventNanos = now;
            this.lapAssist = LapAssist.NONE;
            this.raceAssist = LapAssist.NONE;
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
        ticks = 0;
    }

    static void onLeave(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
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
        tickLobbies(server, now, ticks % LOBBY_REFRESH_TICKS == 0);
        HEATS.values().removeIf(heat -> heat.done(now));
        if (ticks % SAVE_INTERVAL_TICKS == 0) {
            CourseRegistry registry = CourseRegistry.get();
            if (registry != null) {
                registry.save();
            }
        }
    }

    static boolean modifiedPhysics(QuadcopterEntity drone) {
        return serverAssist(drone) != LapAssist.NONE;
    }

    public static int serverAssist(QuadcopterEntity drone) {
        int mask = LapAssist.NONE;
        if (drone.physicsModified() || WorldRules.physics(drone.level().getServer()).modified()) {
            mask |= LapAssist.WORLD_PHYSICS.bit();
        }
        if (drone.build().isCreative()) {
            mask |= LapAssist.CREATIVE_DRONE.bit();
        }
        if (!drone.build().buildable()) {
            mask |= LapAssist.PARTS_MISMATCH.bit();
        }
        return mask;
    }

    static void onDroneMoved(QuadcopterEntity drone, ServerPlayer pilot, Vec3 from, Vec3 to) {
        int assist = serverAssist(drone);
        if (assist != LapAssist.NONE) {
            Session session = SESSIONS.computeIfAbsent(pilot.getUUID(), uuid -> new Session());
            session.lapAssist |= assist;
            session.raceAssist |= assist;
        }
        onDroneMoved(pilot, from, to, drone.orientation(), System.nanoTime());
    }

    public static void onDroneMoved(ServerPlayer pilot, Vec3 from, Vec3 to, long now) {
        onDroneMoved(pilot, from, to, LEVEL, now);
    }

    public static void onDroneMoved(ServerPlayer pilot, Vec3 from, Vec3 to, Quaternionfc orientation, long now) {
        Session session = SESSIONS.computeIfAbsent(pilot.getUUID(), uuid -> new Session());
        boolean continuous = session.hasLastMove && session.lastMove.distanceToSqr(from) < CONTINUITY_EPSILON && now > session.lastMoveNanos;
        long fromNanos = continuous ? session.lastMoveNanos : now;
        session.hasLastMove = true;
        session.lastMove = to;
        session.lastMoveNanos = now;
        if (session.courseId != GateEntity.NO_COURSE) {
            session.ghost.sample(now, to.x, to.y, to.z, orientation);
        }
        if (from.distanceToSqr(to) > LapTracker.MAX_SEGMENT * LapTracker.MAX_SEGMENT || from.distanceToSqr(to) == 0.0) {
            return;
        }
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            return;
        }
        AABB area = new AABB(from, to).inflate(GATE_SEARCH_MARGIN + Math.max(CourseElement.REACH, FreeformReach.reach()));
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
            if (gate.signals() && gate.geometry().stageCrossing(0, from.x, from.y, from.z, to.x, to.y, to.z) >= 0.0) {
                GateSignals.passed(gate, member, member && session.courseId == course);
            }
            if (!member) {
                continue;
            }
            GateGeometry geometry = gate.geometry();
            if (geometry.gate()) {
                double t = geometry.crossing(from.x, from.y, from.z, to.x, to.y, to.z, true);
                if (t >= 0.0) {
                    hits.add(new double[]{t, course, checkpoint(registry, course, gate.gateIndex(), 0)});
                }
                continue;
            }
            List<Passage> stages = geometry.stages();
            for (int stage = 0; stage < stages.size(); stage++) {
                double t = stages.get(stage).crossing(from.x, from.y, from.z, to.x, to.y, to.z, true);
                if (t >= 0.0) {
                    hits.add(new double[]{t, course, checkpoint(registry, course, gate.gateIndex(), stage)});
                }
            }
        }
        hits.removeIf(hit -> hit[2] < 0.0);
        hits.sort((a, b) -> Double.compare(a[0], b[0]));
        for (double[] hit : hits) {
            long time = fromNanos + Math.round((now - fromNanos) * hit[0]);
            session.verifier.record((int) hit[1], (int) hit[2], time);
        }
        if (!session.pending.isEmpty()) {
            drainPending(session, pilot, now);
        }
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
        return CourseCheckpoints.index(course.kinds(), element, stage);
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
            session.reset();
            return Verdict.NO_COURSE;
        }
        if (!CourseRegistry.sameDimension(course, player.level())) {
            session.reset();
            return Verdict.WRONG_DIMENSION;
        }
        QuadcopterEntity drone = pilotedDrone(player);
        if (drone == null) {
            session.reset();
            return Verdict.NOT_PILOTING;
        }
        CourseRegistry.GateRecord start = course.gates.getFirst();
        if (drone.distanceToSqr(start.x, start.y, start.z) > MAX_REPORT_DISTANCE * MAX_REPORT_DISTANCE && report.kind() != RacePayloads.ReportKind.FINISH) {
            session.reset();
            return Verdict.TOO_FAR;
        }
        if (report.kind() == RacePayloads.ReportKind.START) {
            drainPending(session, player, now);
            session.begin(course.id, now);
            session.verifier.prune(now - MAX_LAP_NANOS - START_SLACK_NANOS - COUNTDOWN_SLACK_NANOS);
            return Verdict.ACCEPTED;
        }
        if (session.courseId != course.id) {
            session.reset();
            return Verdict.OUT_OF_SEQUENCE;
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
        CourseRegistry registry = CourseRegistry.get();
        while (!session.pending.isEmpty()) {
            Pending head = session.pending.peekFirst();
            CourseRegistry.Course course = registry == null ? null : registry.course(head.report().courseId());
            if (course == null || course.gates.isEmpty() || course.id != session.courseId) {
                session.reset();
                return Verdict.NO_COURSE;
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
                session.reset();
                Constants.LOG.debug("Runde von {} auf Kurs {} vom Server nicht bestätigt", player.getGameProfile().name(), course.id);
                return Verdict.UNVERIFIED;
            }
            session.pending.pollFirst();
            if (verdict != Verdict.ACCEPTED) {
                session.reset();
                return verdict;
            }
        }
        return verdict;
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
        long notBefore = session.lap == 0 ? session.startedAt - START_SLACK_NANOS - COUNTDOWN_SLACK_NANOS : Long.MIN_VALUE;
        int checkpoints = course.checkpoints();
        LapVerifier.VerifiedLap verified = session.verifier.find(course.id, checkpoints, session.lastEndSequence, notBefore);
        while (verified != null && session.lap == 0 && report.penaltyNanos() <= 0L
                && verified.startNanos() < session.startedAt - START_SLACK_NANOS) {
            verified = session.verifier.find(course.id, checkpoints, verified.endSequence(), notBefore);
        }
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
        session.lastEventNanos = now;
        session.lastEndSequence = verified.endSequence();
        QuadcopterEntity drone = pilotedDrone(player);
        int assist = session.lapAssist | report.assistMask() | (drone != null ? serverAssist(drone) : LapAssist.NONE);
        session.lapAssist = LapAssist.NONE;
        session.raceAssist |= assist;
        String frame = drone == null ? "" : drone.build().frame().key();
        if (assist != LapAssist.NONE) {
            session.ghost.trim(verified.endNanos() - LapTracker.SECOND);
            if (WorldRules.on(player.level().getServer(), WorldRuleSpecs.SAVE_ASSISTED_LAPS)
                    && registry.submitAssistedLap(course, player, frame, lapNanos, assist)) {
                player.sendSystemMessage(Component.translatable("propwash.race.server.personal_best_assisted", CourseRegistry.ref(course), RaceFormat.time(lapNanos),
                        registry.board(course).assistedRank(player.getUUID(), frame), className(frame), assistReasons(assist))
                        .withStyle(ChatFormatting.AQUA));
            }
            return Verdict.ACCEPTED;
        }
        Leaderboard.Outcome outcome = registry.submitLap(course, player, frame, lapNanos);
        if (outcome.record()) {
            storeGhost(registry, course, session, verified, player, frame, lapNanos);
        }
        session.ghost.trim(verified.endNanos() - LapTracker.SECOND);
        announce(registry, course, outcome, player, frame, lapNanos);
        return Verdict.ACCEPTED;
    }

    private static void storeGhost(CourseRegistry registry, CourseRegistry.Course course, Session session, LapVerifier.VerifiedLap verified,
                                   ServerPlayer player, String frame, long lapNanos) {
        int layout = course.toData().layoutHash();
        GhostTrack track = session.ghost.cut(verified.startNanos(), verified.endNanos(), lapNanos, frame, layout);
        if (track == null) {
            return;
        }
        QuadcopterEntity drone = pilotedDrone(player);
        if (drone != null && drone.build().frame().key().equals(frame)) {
            track = track.withLook(GhostLook.of(drone.build(), drone.paint()));
        }
        byte[] data = GhostCodec.encode(track);
        if (data.length <= GhostCodec.MAX_ENCODED_BYTES) {
            registry.ghosts().put(course.id, frame, layout, player.getGameProfile().name(), lapNanos, data);
        }
    }

    public static long crossingTolerance(long serverLapNanos) {
        return CROSSING_TOLERANCE_NANOS + Math.max(0L, serverLapNanos) / 100L;
    }

    private static Verdict finish(CourseRegistry registry, CourseRegistry.Course course, Session session, RacePayloads.RaceReportC2S report,
                                  ServerPlayer player) {
        boolean consistent = session.courseId == course.id && session.lap > 0 && report.lap() == session.lap
                && CourseLaps.finishAllowed(course.laps, session.lap)
                && Math.abs(report.totalNanos() - session.lapSum) <= 5_000_000L + session.lap * 1_000_000L;
        if (!consistent) {
            session.reset();
            return Verdict.OUT_OF_SEQUENCE;
        }
        int assist = session.raceAssist | session.lapAssist;
        if (assist == LapAssist.NONE) {
            if (registry.submitRace(course, player, session.lap, report.totalNanos())) {
                player.sendSystemMessage(Component.translatable("propwash.race.server.race_best", session.lap, CourseRegistry.ref(course),
                        RaceFormat.time(report.totalNanos())).withStyle(ChatFormatting.GOLD));
            }
        } else if (WorldRules.on(player.level().getServer(), WorldRuleSpecs.SAVE_ASSISTED_LAPS)
                && registry.submitAssistedRace(course, player, session.lap, report.totalNanos(), assist)) {
            player.sendSystemMessage(Component.translatable("propwash.race.server.race_best_assisted", session.lap, CourseRegistry.ref(course),
                    RaceFormat.time(report.totalNanos()), assistReasons(assist)).withStyle(ChatFormatting.AQUA));
        }
        RaceLobby.Heat heat = HEATS.get(course.id);
        if (heat != null) {
            int place = heat.finish(player.getUUID());
            if (place > 0 && heat.multiplayer()) {
                Component message = Component.translatable("propwash.race.server.heat_place", place, player.getDisplayName(),
                        RaceFormat.time(report.totalNanos())).withStyle(place == 1 ? ChatFormatting.GOLD : ChatFormatting.WHITE);
                for (UUID pilot : heat.pilots()) {
                    ServerPlayer other = player.level().getServer().getPlayerList().getPlayer(pilot);
                    if (other != null) {
                        other.sendSystemMessage(message);
                    }
                }
            }
        }
        session.reset();
        return Verdict.ACCEPTED;
    }

    public static long minimumLapNanos(CourseRegistry.Course course, ServerPlayer player) {
        double length = GateGeometry.pathLength(course.toData().geometry());
        double speed = Math.min(ABSOLUTE_MAX_SPEED, ServerRules.maxDroneSpeed(player));
        if (!(speed > 0.0) || length <= 0.0) {
            return 0L;
        }
        return (long) (length / speed * 1.0E9);
    }

    public static Component assistReasons(int mask) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (LapAssist assist : LapAssist.list(mask)) {
            if (!first) {
                out.append(Component.translatable("propwash.race.assist.separator"));
            }
            out.append(Component.translatable(assist.translationKey()));
            first = false;
        }
        return out;
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
        return PropwashNet.sendToPlayer(player, leaderboardPayload(registry.board(course), course, player.getUUID(), open, assisted));
    }

    public static RacePayloads.LeaderboardS2C leaderboardPayload(Leaderboard board, CourseRegistry.Course course, UUID viewer, boolean open) {
        return leaderboardPayload(board, course, viewer, open, false);
    }

    public static RacePayloads.LeaderboardS2C leaderboardPayload(Leaderboard board, CourseRegistry.Course course, UUID viewer, boolean open,
                                                                 boolean assisted) {
        List<RacePayloads.BoardRow> rows = new ArrayList<>();
        for (Leaderboard.Entry entry : board.view(viewer, Leaderboard.TOP)) {
            if (rows.size() >= RacePayloads.LeaderboardS2C.MAX_ROWS) {
                break;
            }
            Leaderboard.Entry overall = board.best(entry.player(), null);
            int overallRank = entry.equals(overall) ? board.rank(entry.player(), null) : -1;
            rows.add(new RacePayloads.BoardRow(entry.name(), entry.frame(), entry.nanos(), entry.dateMillis(), entry.player().equals(viewer),
                    board.rank(entry.player(), entry.frame()), overallRank));
        }
        if (assisted) {
            for (Leaderboard.Entry entry : board.assistedView(viewer, Leaderboard.TOP)) {
                if (rows.size() >= RacePayloads.LeaderboardS2C.MAX_ROWS) {
                    break;
                }
                Leaderboard.Entry overall = board.bestAssisted(entry.player(), null);
                int overallRank = entry.equals(overall) ? board.assistedRank(entry.player(), null) : -1;
                rows.add(new RacePayloads.BoardRow(entry.name(), entry.frame(), entry.nanos(), entry.dateMillis(), entry.player().equals(viewer),
                        board.assistedRank(entry.player(), entry.frame()), overallRank, entry.assist()));
            }
        }
        int raceLaps = CourseEdits.lapsFor(course, viewer);
        long raceBest = raceLaps > CourseLaps.ENDLESS ? board.raceBest(viewer, raceLaps) : LapTracker.NONE;
        CourseRegistry registry = CourseRegistry.get();
        int archived = registry == null ? 0 : registry.records().archive(course.id).size();
        return new RacePayloads.LeaderboardS2C(course.id, course.layout, course.gates.size(), open, board.lapsOf(viewer), rows, raceLaps, raceBest,
                archived);
    }

    static void onGhostRequest(RacePayloads.GhostRequestC2S request, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), uuid -> new Session());
        if (!session.allowRequest(System.nanoTime())) {
            return;
        }
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(request.courseId());
        RecordGhosts.Stored stored = course == null ? null : registry.ghosts().get(course.id, request.frame());
        if (stored == null || stored.layout() != course.toData().layoutHash()) {
            PropwashNet.sendToPlayer(player, new RacePayloads.GhostS2C(request.courseId(), request.frame(), 0, "", LapTracker.NONE, new byte[0]));
            return;
        }
        byte[] data = stored.revision() == request.knownRevision() ? new byte[0] : stored.data();
        PropwashNet.sendToPlayer(player, new RacePayloads.GhostS2C(course.id, stored.frame(), stored.revision(), stored.holder(), stored.nanos(), data));
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
