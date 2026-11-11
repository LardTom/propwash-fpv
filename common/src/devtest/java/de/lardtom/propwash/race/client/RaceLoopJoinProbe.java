package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.RaceLoopJoinCheck;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RacePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RaceLoopJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 90;
    private static final String START = "raceloop_start";
    private static final String RESULT = "raceloop_result";
    private static final String LINES = "raceloop_lines";
    private static final String STAGE = "raceloop_stage";
    private static final long MS = 1_000_000L;

    private RaceLoopJoinProbe() {
    }

    private static void line(Map<String, Object> state, String text) {
        state.put(LINES, state.getOrDefault(LINES, "") + text + "\n");
    }

    private static String finish(Map<String, Object> state, String last) {
        line(state, last);
        String result = (String) state.get(LINES);
        state.put(RESULT, result);
        return result;
    }

    private static @Nullable CourseData loop() {
        for (CourseData course : ClientCourses.all()) {
            if (course.gates().size() == 3 && course.gates().get(2).reverse() && course.firstPass(2) == 0) {
                return course;
            }
        }
        return null;
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        if (state.containsKey(RESULT)) {
            return (String) state.get(RESULT);
        }
        int start = (int) state.computeIfAbsent(START, key -> tick);
        if (tick - start > TIMEOUT_TICKS) {
            return finish(state, "raceloop timeout stage=" + state.getOrDefault(STAGE, 0));
        }
        int stage = (int) state.getOrDefault(STAGE, 0);
        CourseData course = loop();
        if (course == null || mc.level == null) {
            return null;
        }
        if (stage == 0) {
            CourseData.Gate first = course.gates().getFirst();
            Vec3 at = new Vec3(first.x(), first.y(), first.z());
            List<GateEntity> gates = mc.level.getEntitiesOfClass(GateEntity.class, new AABB(at, at).inflate(0.2),
                    gate -> !gate.isRemoved() && gate.courseId() == course.id());
            if (gates.size() != 1 || !"1 · 3←".equals(gates.getFirst().passText())) {
                return null;
            }
            line(state, "raceloop passes ok " + gates.getFirst().passText());
            state.put(STAGE, 1);
            return null;
        }
        if (stage == 1) {
            CourseData.Gate first = course.gates().getFirst();
            Vec3 origin = new Vec3(first.x(), first.y(), first.z());
            LapTracker tracker = new LapTracker();
            tracker.setLapTarget(0);
            tracker.setCourse(course.id(), ClientCourses.geometry(course), 0L);
            List<LapTracker.Event> events = new ArrayList<>();
            long now = 1_000L * MS;
            Vec3 at = origin.add(RaceLoopJoinCheck.LOOP[0][0], RaceLoopJoinCheck.LOOP[0][1], RaceLoopJoinCheck.LOOP[0][2]);
            tracker.update(now, at.x, at.y, at.z);
            for (int i = 1; i < RaceLoopJoinCheck.LOOP.length; i++) {
                Vec3 to = origin.add(RaceLoopJoinCheck.LOOP[i][0], RaceLoopJoinCheck.LOOP[i][1], RaceLoopJoinCheck.LOOP[i][2]);
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
            boolean lap = events.stream().anyMatch(e -> e.type() == LapTracker.EventType.LAP);
            boolean clean = events.stream().noneMatch(e -> e.type() == LapTracker.EventType.MISSED || e.type() == LapTracker.EventType.WRONG_WAY);
            if (!gates.equals(List.of(1, 2)) || !lap || !clean) {
                return finish(state, "raceloop client lap wrong " + events);
            }
            line(state, "raceloop client lap ok");
            state.put(STAGE, 2);
            return null;
        }
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(course.id());
        if (board == null) {
            return null;
        }
        boolean second = board.rows().stream().anyMatch(row -> row.name().equals(RaceLoopJoinCheck.SECOND));
        boolean self = board.rows().stream().anyMatch(RacePayloads.BoardRow::self);
        if (board.rows().size() < 2 || !second || !self) {
            return null;
        }
        boolean counted = ClientRaceTracker.INSTANCE.verdicts().stream()
                .anyMatch(verdict -> verdict.courseId() == course.id() && verdict.lap() == 1 && verdict.result() == RacePayloads.LapResult.COUNTED);
        if (!counted) {
            return null;
        }
        line(state, "raceloop verdict counted");
        return finish(state, "raceloop board " + board.rows().size() + " pilots");
    }
}
