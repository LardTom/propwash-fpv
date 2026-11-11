package de.lardtom.propwash.race;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayMarksTest {
    private static final long MS = 1_000_000L;
    private static final long LAP = 8_000L * MS;
    private static final double SPACING = 20.0;

    private static List<GateGeometry> gates(int count) {
        List<GateGeometry> gates = new ArrayList<>();
        double direction = GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM).normalZ();
        for (int i = 0; i < count; i++) {
            gates.add(GateGeometry.of(0.0, 64.0, i * SPACING * direction, 0.0F, GateSize.MEDIUM));
        }
        return gates;
    }

    private static GhostTrack flight(List<GateGeometry> gates, long lapNanos) {
        GhostRecorder recorder = new GhostRecorder();
        Quaternionf level = new Quaternionf();
        recorder.onLapStart(1, 0L);
        GateGeometry first = gates.getFirst();
        double direction = first.normalZ();
        double from = first.z() + 0.1 * direction;
        double to = first.z() + gates.size() * SPACING * direction;
        for (long t = 0L; t <= lapNanos + 50L * MS; t += 10L * MS) {
            recorder.sample(t, first.x(), first.centerY(), from + (to - from) * t / lapNanos, level);
        }
        return recorder.cut(0L, lapNanos, lapNanos, "race_5", 0, 10L * MS, 0L);
    }

    @Test
    void lapCourseCountsItsStartGate() {
        ReplayMarks three = ReplayMarks.of(3, new long[] {LAP, 2_000L * MS, 5_000L * MS}, LAP);
        assertEquals(3, three.gates());
        assertArrayEquals(new long[] {0L, 2_000L * MS, 5_000L * MS}, three.times());
        ReplayMarks four = ReplayMarks.of(4, new long[] {LAP, 1_000L * MS, 3_000L * MS, 6_000L * MS}, LAP);
        assertEquals(4, four.gates());
        assertEquals(4, four.times().length);
    }

    @Test
    void pointToPointCountsStartAndFinish() {
        ReplayMarks marks = ReplayMarks.of(4, new long[] {LAP, 2_000L * MS, 5_000L * MS, LAP}, LAP);
        assertEquals(4, marks.gates());
        assertArrayEquals(new long[] {0L, 2_000L * MS, 5_000L * MS, LAP}, marks.times());
    }

    @Test
    void twoGatesAndOneGateStayRight() {
        assertArrayEquals(new long[] {0L, LAP}, ReplayMarks.of(2, new long[] {LAP, LAP}, LAP).times());
        assertArrayEquals(new long[] {0L, 3_000L * MS}, ReplayMarks.of(2, new long[] {LAP, 3_000L * MS}, LAP).times());
        assertArrayEquals(new long[] {0L}, ReplayMarks.of(1, new long[] {LAP}, LAP).times());
    }

    @Test
    void labelFollowsTheCourseEvenWhenAPassWasNotFound() {
        ReplayMarks marks = ReplayMarks.of(4, new long[] {LAP, 2_000L * MS, LapTracker.NONE, LapTracker.NONE}, LAP);
        assertEquals(4, marks.gates());
        assertArrayEquals(new long[] {0L, 2_000L * MS}, marks.times());
    }

    @Test
    void lateLastGateKeepsItsMarkAtTheEnd() {
        ReplayMarks marks = ReplayMarks.of(3, new long[] {LAP, 2_000L * MS, LAP + 5L * MS}, LAP);
        assertArrayEquals(new long[] {0L, 2_000L * MS, LAP}, marks.times());
    }

    @Test
    void unknownCourseShowsNoNumber() {
        ReplayMarks marks = ReplayMarks.of(0, new long[0], LAP);
        assertEquals(0, marks.gates());
        assertEquals(0, marks.times().length);
    }

    @Test
    void startAndFinishAreTheGreenMarks() {
        assertTrue(ReplayMarks.startOrFinish(0L, LAP));
        assertTrue(ReplayMarks.startOrFinish(LAP, LAP));
        assertFalse(ReplayMarks.startOrFinish(2_000L * MS, LAP));
    }

    @Test
    void realFlightGivesOneMarkPerGateOnBothCourseTypes() {
        for (int count : new int[] {2, 3, 4, 7}) {
            List<GateGeometry> gates = gates(count);
            GhostTrack lapFlight = flight(gates, LAP);
            long[] splits = lapFlight.splits(gates);
            ReplayMarks laps = ReplayMarks.of(count, splits, LAP);
            assertEquals(count, laps.gates());
            assertEquals(count, laps.times().length, "Runden-Kurs mit " + count + " Gates");
            assertEquals(0L, laps.times()[0]);
            long[] open = lapFlight.splits(gates);
            open[open.length - 1] = LAP;
            ReplayMarks p2p = ReplayMarks.of(count, open, LAP);
            assertEquals(count, p2p.gates());
            assertEquals(count, p2p.times().length, "Punkt-zu-Punkt-Kurs mit " + count + " Gates");
            assertEquals(0L, p2p.times()[0]);
            assertEquals(LAP, p2p.times()[count - 1]);
        }
    }
}
