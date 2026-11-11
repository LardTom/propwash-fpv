package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.EntryGhost;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GhostCodec;
import de.lardtom.propwash.race.GhostKey;
import de.lardtom.propwash.race.GhostRecorder;
import de.lardtom.propwash.race.GhostServer;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceRuleSet;
import de.lardtom.propwash.race.RaceServer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

public final class RaceGhostsJoinCheck {
    public static final String RESULT = "[race-ghosts] ";
    public static final String SECOND = "Zweiter";
    public static final UUID LONG = new UUID(0x6E057L, 0x10A6L);
    public static final String LONG_NAME = "Langstrecke";
    public static final String LONG_FRAME = "race_5";
    public static final long LONG_LAP = 200_000_000_000L;
    public static final int SETTLE_TICKS = 60;
    public static final int CLEANUP_AFTER_TICKS = 20 * 120;
    private static final long S = 1_000_000_000L;
    private static final long MS = 1_000_000L;
    public static final long RUN_UP_NANOS = 1_500L * MS;
    public static final long START_BEFORE_GATE_NANOS = 300L * MS;
    public static final double RUN_UP_BLOCKS = 3.0;
    private static final long STEP_NANOS = 50L * MS;
    private static final double ON_RUN_UP = 0.25;
    private static final List<GateEntity> SPAWNED = new ArrayList<>();
    private static int joined = -1;
    private static int ranAt = -1;
    private static boolean cleaned;
    private static @Nullable Integer course;

    private RaceGhostsJoinCheck() {
    }

    public static @Nullable GhostTrack noisyTrack(List<GateGeometry> gates, long lapNanos, String frame, int layout, long seed) {
        GhostRecorder recorder = new GhostRecorder();
        recorder.onLapStart(1, -GhostRecorder.LEAD_NANOS);
        Random random = new Random(seed);
        Quaternionf facing = new Quaternionf();
        int legs = gates.size();
        long loop = Math.max(1L, lapNanos / 8);
        for (long t = -GhostRecorder.LEAD_NANOS; t <= lapNanos + 20 * MS; t += 20 * MS) {
            double phase = (double) Math.floorMod(t, loop) / loop * legs;
            int leg = Math.min(legs - 1, (int) Math.floor(phase));
            double alpha = phase - leg;
            GateGeometry from = gates.get(leg);
            GateGeometry to = gates.get((leg + 1) % legs);
            double x = from.x() + (to.x() - from.x()) * alpha + (random.nextDouble() - 0.5) * 0.3;
            double y = from.centerY() + (to.centerY() - from.centerY()) * alpha + (random.nextDouble() - 0.5) * 0.3;
            double z = from.z() + (to.z() - from.z()) * alpha + (random.nextDouble() - 0.5) * 0.3;
            facing.identity().rotateY((float) (random.nextDouble() * Math.PI * 2.0)).rotateX((float) (random.nextDouble() - 0.5));
            recorder.sample(t, x, y, z, facing);
        }
        return recorder.cut(0L, lapNanos, lapNanos, frame, layout, GhostServer.FINE_INTERVAL_NANOS, GhostRecorder.LEAD_NANOS);
    }

    private static Vec3 center(GateGeometry gate) {
        return new Vec3(gate.x(), gate.centerY(), gate.z());
    }

    private static Vec3 normal(GateGeometry gate) {
        return new Vec3(gate.normalX(), 0.0, gate.normalZ());
    }

    private static Vec3 runUp(GateGeometry start, long fromGateNanos) {
        return center(start).add(normal(start).scale(RUN_UP_BLOCKS * fromGateNanos / (double) RUN_UP_NANOS));
    }

    private static void pass(ServerPlayer pilot, GateGeometry gate, long nanos) {
        RaceServer.onDroneMoved(pilot, center(gate).subtract(normal(gate)), center(gate).add(normal(gate)), nanos);
    }

    public static @Nullable String flyWithRunUp(CourseRegistry.Course course, ServerPlayer pilot, long lapNanos) {
        List<GateGeometry> gates = course.toData().geometry();
        if (gates.isEmpty()) {
            return "Kurs ohne Gates";
        }
        GateGeometry start = gates.getFirst();
        ServerLevel level = pilot.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        Vec3 from = runUp(start, -RUN_UP_NANOS);
        drone.snapTo(from.x, from.y, from.z, 0.0F, 0.0F);
        drone.setNoGravity(true);
        drone.setPilot(pilot);
        if (!level.addFreshEntity(drone)) {
            return "Drohne nicht gespawnt";
        }
        try {
            long lap = Math.max(RaceServer.minimumLapNanos(course, pilot) * 3L, lapNanos);
            long t0 = RaceRulesJoinCheck.nextStart(lap);
            long startAt = t0 - START_BEFORE_GATE_NANOS;
            boolean started = false;
            for (long t = t0 - RUN_UP_NANOS + STEP_NANOS / 2; t <= t0 + STEP_NANOS / 2; t += STEP_NANOS) {
                if (!started && t >= startAt) {
                    RaceServer.Verdict verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.START, course.id, 0, 0L, 0L),
                            pilot, startAt);
                    if (verdict != RaceServer.Verdict.ACCEPTED) {
                        return "Start abgelehnt: " + verdict;
                    }
                    started = true;
                }
                Vec3 to = runUp(start, t - t0);
                RaceServer.onDroneMoved(pilot, from, to, t);
                from = to;
            }
            for (int i = 1; i < gates.size(); i++) {
                pass(pilot, gates.get(i), t0 + lap * i / gates.size());
            }
            pass(pilot, start, t0 + lap);
            RaceServer.Verdict verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, course.id, 1, lap, 0L), pilot,
                    t0 + lap + 100L * MS);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Runde abgelehnt: " + verdict;
            }
            RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.ABORT, course.id, 0, 0L, 0L), pilot, t0 + lap + 200L * MS);
            return null;
        } finally {
            drone.setPilot(null);
            drone.discard();
        }
    }

    public static @Nullable String leadProblem(CourseRegistry registry, CourseRegistry.Course course, UUID pilot) {
        Leaderboard.Entry entry = registry.board(course).best(pilot, null);
        if (entry == null) {
            return "Zeit fehlt in der Bestenliste";
        }
        EntryGhost stored = registry.entryGhosts().get(course.key(), GhostKey.of(entry));
        if (stored == null || stored.info().source() != EntryGhost.Source.SERVER) {
            return "Geist der Bestenliste fehlt: " + (stored == null ? "keiner" : stored.info().source());
        }
        GhostTrack track;
        try {
            track = stored.decodeTrack();
        } catch (IOException e) {
            return "Geist nicht lesbar: " + e.getMessage();
        }
        if (track.leadNanos() != GhostRecorder.LEAD_NANOS) {
            return "Vorlauf " + track.leadNanos() / MS + " ms statt " + GhostRecorder.LEAD_NANOS / MS + " ms (Anflug " + RUN_UP_NANOS / MS
                    + " ms, Startmeldung " + START_BEFORE_GATE_NANOS / MS + " ms vor dem Gate)";
        }
        Vec3 expected = runUp(course.toData().geometry().getFirst(), -GhostRecorder.LEAD_NANOS);
        double[] at = new double[3];
        if (!track.pose(track.beginNanos(), at, new Quaternionf())) {
            return "Anfang des Vorlaufs ohne Pose";
        }
        double off = expected.distanceTo(new Vec3(at[0], at[1], at[2]));
        if (off > ON_RUN_UP) {
            return "Anfang des Vorlaufs " + String.format(Locale.ROOT, "%.2f", off) + " Blöcke neben dem Anflug";
        }
        return null;
    }

    static void tick(MinecraftServer server, int tick) {
        if (ranAt >= 0) {
            if (!cleaned && tick - ranAt >= CLEANUP_AFTER_TICKS) {
                cleaned = true;
                cleanup();
            }
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            joined = -1;
            return;
        }
        if (joined < 0) {
            joined = tick;
        }
        if (tick - joined < SETTLE_TICKS) {
            return;
        }
        ranAt = tick;
        ServerPlayer player = players.getFirst();
        String line;
        try {
            line = run(server, player);
        } catch (RuntimeException e) {
            Constants.LOG.error("[Beitrittstest] race-ghosts: Ablauf fehlgeschlagen", e);
            line = "fail " + e;
        }
        Constants.LOG.info("[Beitrittstest] race-ghosts: {}", line);
        player.sendSystemMessage(Component.literal(RESULT + line).withStyle(ChatFormatting.GRAY));
    }

    private static String run(MinecraftServer server, ServerPlayer player) {
        Vec3 origin = RaceDataJoinCheck.origin(player);
        CourseRegistry.Course built = RaceDataJoinCheck.build(player, origin, SPAWNED);
        CourseRegistry registry = CourseRegistry.get();
        if (built == null || registry == null) {
            return "fail Kurs nicht angelegt";
        }
        course = built.id;
        if (!registry.entryGhosts().enabled()) {
            return "fail keine Ablage unter race-data";
        }
        String flown = flyWithRunUp(built, player, 9 * S);
        if (flown != null) {
            return "fail eigene Runde: " + flown;
        }
        ServerPlayer second = RaceDataJoinCheck.secondPilot(server, player.level(), SECOND);
        flown = flyWithRunUp(built, second, 11 * S);
        if (flown != null) {
            return "fail zweiter Pilot: " + flown;
        }
        String lead = leadProblem(registry, built, player.getUUID());
        if (lead != null) {
            return "fail Vorlauf eigene Runde: " + lead;
        }
        lead = leadProblem(registry, built, second.getUUID());
        if (lead != null) {
            return "fail Vorlauf zweiter Pilot: " + lead;
        }
        Leaderboard board = registry.board(built);
        int ghosts = 0;
        int codes = 0;
        for (Leaderboard.Entry entry : board.storedTimes()) {
            EntryGhost.Info info = registry.entryGhosts().info(built.key(), GhostKey.of(entry));
            if (info != null) {
                ghosts++;
                if (info.code().startsWith("PW3-")) {
                    codes++;
                }
            }
        }
        int layout = built.toData().layoutHash();
        GhostTrack track = noisyTrack(built.toData().geometry(), LONG_LAP, LONG_FRAME, layout, 42L);
        if (track == null) {
            return "fail lange Spur nicht geschnitten";
        }
        long date = System.currentTimeMillis();
        registry.submitTime(built, LONG, LONG_NAME, LONG_FRAME, LONG_LAP, date, AssistReasons.NONE, RaceRuleSet.DEFAULTS, true);
        byte[] data = GhostCodec.encode(track);
        EntryGhost.Info info = new EntryGhost.Info(LONG, LONG_NAME, LONG_FRAME, LONG_LAP, date, LapAssist.NONE, layout, 20.0F, "",
                EntryGhost.Source.SERVER);
        if (!registry.entryGhosts().put(built.key(), new EntryGhost(info, data))) {
            return "fail lange Spur nicht abgelegt";
        }
        RaceServer.sendLeaderboard(player, built.id, false);
        return "ok course=" + built.id + " ghosts=" + ghosts + " codes=" + codes + " long=" + data.length + " lead=" + GhostRecorder.LEAD_NANOS / MS
                + "ms";
    }

    private static void cleanup() {
        for (GateEntity gate : SPAWNED) {
            gate.discard();
        }
        SPAWNED.clear();
        CourseRegistry registry = CourseRegistry.get();
        Integer id = course;
        if (registry != null && id != null) {
            registry.deleteCourse(id);
        }
        course = null;
    }
}
