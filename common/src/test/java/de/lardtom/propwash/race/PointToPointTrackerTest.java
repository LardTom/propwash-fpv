package de.lardtom.propwash.race;

import de.lardtom.propwash.api.race.CourseMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointToPointTrackerTest {
    private static final long MS = 1_000_000L;
    private static final long S = LapTracker.SECOND;
    private static final long TOLERANCE = 20_000L;
    private static final double Y = 65.5;
    private static final int COURSE = 4;

    private static GateGeometry gate(double x, double z, float yaw) {
        return GateGeometry.of(x, 64.0, z, yaw, GateSize.MEDIUM);
    }

    private static List<GateGeometry> line() {
        return List.of(gate(0.0, 0.0, 0.0F), gate(0.0, 10.0, 0.0F), gate(0.0, 20.0, 0.0F));
    }

    private static LapTracker tracker(List<GateGeometry> gates, boolean pointToPoint, int laps) {
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(laps);
        tracker.setCourse(COURSE, gates, pointToPoint, 0L);
        return tracker;
    }

    private static final class Flight {
        final LapTracker tracker;
        final List<LapTracker.Event> events = new ArrayList<>();
        long now = 1_000L * MS;
        double x;
        double z;

        Flight(LapTracker tracker, double x, double z) {
            this.tracker = tracker;
            this.x = x;
            this.z = z;
            tracker.update(this.now, x, Y, z);
        }

        void to(double tx, double tz, double speed) {
            double dx = tx - this.x;
            double dz = tz - this.z;
            double length = Math.sqrt(dx * dx + dz * dz);
            int steps = Math.max(1, (int) Math.ceil(length / 0.4));
            long stepNanos = Math.round(length / speed / steps * 1.0E9);
            double sx = this.x;
            double sz = this.z;
            for (int i = 1; i <= steps; i++) {
                this.now += stepNanos;
                this.x = sx + dx * i / steps;
                this.z = sz + dz * i / steps;
                this.tracker.update(this.now, this.x, Y, this.z);
                this.events.addAll(this.tracker.drainEvents());
            }
        }

        void hover(long nanos) {
            long end = this.now + nanos;
            while (this.now < end) {
                this.now = Math.min(end, this.now + 50 * MS);
                this.tracker.update(this.now, this.x, Y, this.z);
                this.events.addAll(this.tracker.drainEvents());
            }
        }

        List<LapTracker.Event> of(LapTracker.EventType type) {
            return this.events.stream().filter(e -> e.type() == type).toList();
        }
    }

    @Test
    void flyingStartRunsFromTheStartToTheFinish() {
        LapTracker tracker = tracker(line(), true, 3);
        assertTrue(tracker.pointToPoint());
        assertEquals(1, tracker.lapTarget(), "keine Rundenzahl bei Punkt zu Punkt");
        assertEquals(2, tracker.finishIndex());
        Flight flight = new Flight(tracker, 0.0, -5.0);
        flight.to(0.0, 25.0, 10.0);
        assertEquals(1, flight.of(LapTracker.EventType.START).size());
        List<LapTracker.Event> gates = flight.of(LapTracker.EventType.GATE);
        assertEquals(1, gates.size(), "Zwischenzeit nur am mittleren Element");
        assertEquals(1, gates.getFirst().gate());
        assertEquals(S, gates.getFirst().valueNanos(), TOLERANCE);
        List<LapTracker.Event> laps = flight.of(LapTracker.EventType.LAP);
        assertEquals(1, laps.size());
        assertEquals(2 * S, laps.getFirst().valueNanos(), TOLERANCE);
        List<LapTracker.Event> finish = flight.of(LapTracker.EventType.FINISH);
        assertEquals(1, finish.size());
        assertEquals(laps.getFirst().valueNanos(), finish.getFirst().valueNanos());
        assertEquals(LapTracker.Phase.FINISHED, tracker.phase());
        assertEquals(2 * S, tracker.totalNanos(), TOLERANCE);
        assertEquals(0, flight.of(LapTracker.EventType.MISSED).size());
    }

    @Test
    void lapsModeDoesNotStopAtTheLastGate() {
        LapTracker tracker = tracker(line(), false, 3);
        assertFalse(tracker.pointToPoint());
        assertEquals(3, tracker.lapTarget());
        Flight flight = new Flight(tracker, 0.0, -5.0);
        flight.to(0.0, 25.0, 10.0);
        assertEquals(0, flight.of(LapTracker.EventType.LAP).size());
        assertTrue(tracker.racing());
        assertEquals(0, tracker.nextGate(), "nach dem letzten Gate geht es zurück zum Start");
    }

    @Test
    void finishWaitsForEveryElementInOrder() {
        LapTracker tracker = tracker(List.of(gate(0.0, 0.0, 0.0F), gate(8.0, 10.0, 0.0F), gate(0.0, 20.0, 0.0F)), true, 1);
        Flight flight = new Flight(tracker, 0.0, -5.0);
        flight.to(0.0, 25.0, 10.0);
        assertEquals(1, flight.of(LapTracker.EventType.MISSED).size(), "Ziel ohne das mittlere Element");
        assertEquals(0, flight.of(LapTracker.EventType.LAP).size());
        assertTrue(tracker.racing());
        assertEquals(1, tracker.nextGate());
    }

    @Test
    void standingStartTimesFromGoAndCountsTheHoleshot() {
        LapTracker tracker = tracker(line(), true, 5);
        Flight flight = new Flight(tracker, 0.0, -2.0);
        assertTrue(tracker.beginCountdown(flight.now, 3 * S));
        flight.hover(3_000L * MS);
        long go = flight.of(LapTracker.EventType.GO).getFirst().nanos();
        flight.to(0.0, 25.0, 9.0);
        List<LapTracker.Event> laps = flight.of(LapTracker.EventType.LAP);
        assertEquals(1, laps.size());
        assertEquals(laps.getFirst().nanos() - go, laps.getFirst().valueNanos(), TOLERANCE);
        assertEquals(2, flight.of(LapTracker.EventType.GATE).size(), "Holeshot am Start und das mittlere Element");
        assertEquals(LapTracker.Phase.FINISHED, tracker.phase());
    }

    @Test
    void nextRunStartsAgainAtTheStart() {
        LapTracker tracker = tracker(line(), true, 1);
        Flight flight = new Flight(tracker, 0.0, -5.0);
        flight.to(0.0, 25.0, 10.0);
        flight.to(10.0, 25.0, 10.0);
        flight.to(10.0, -5.0, 10.0);
        flight.to(0.0, -5.0, 10.0);
        flight.hover(LapTracker.RESTART_COOLDOWN_NANOS);
        flight.to(0.0, 25.0, 12.0);
        List<LapTracker.Event> laps = flight.of(LapTracker.EventType.LAP);
        assertEquals(2, laps.size());
        assertTrue(laps.get(1).valueNanos() < laps.get(0).valueNanos());
        assertEquals(laps.get(1).valueNanos(), tracker.sessionBestNanos());
    }

    @Test
    void finishCanBeTheStartGateAfterALoop() {
        GateGeometry a = gate(0.0, 0.0, 0.0F);
        GateGeometry b = gate(10.0, 10.0, 180.0F);
        LapTracker tracker = tracker(List.of(a, b, a), true, 3);
        Flight flight = new Flight(tracker, 0.0, -5.0);
        flight.to(0.0, 20.0, 10.0);
        flight.to(10.0, 20.0, 10.0);
        flight.to(10.0, -5.0, 10.0);
        flight.to(0.0, -5.0, 10.0);
        assertTrue(tracker.racing());
        assertEquals(2, tracker.nextGate());
        flight.to(0.0, 2.0, 10.0);
        assertEquals(1, flight.of(LapTracker.EventType.LAP).size());
        assertEquals(LapTracker.Phase.FINISHED, tracker.phase());
        assertEquals(0, flight.of(LapTracker.EventType.MISSED).size());
    }

    @Test
    void reversedFinishCountsOnlyInItsDirection() {
        GateGeometry a = gate(0.0, 0.0, 0.0F);
        GateGeometry b = gate(0.0, 10.0, 0.0F);
        LapTracker tracker = tracker(List.of(a, b, b.withReverse(true)), true, 1);
        Flight flight = new Flight(tracker, 0.0, -5.0);
        flight.to(0.0, 15.0, 10.0);
        assertTrue(tracker.racing());
        assertEquals(2, tracker.nextGate());
        flight.to(0.0, 5.0, 10.0);
        assertEquals(1, flight.of(LapTracker.EventType.LAP).size());
        assertEquals(2 * S, flight.of(LapTracker.EventType.LAP).getFirst().valueNanos(), TOLERANCE);
    }

    @Test
    void switchingTheModeAbortsARunningRace() {
        LapTracker tracker = tracker(line(), false, 3);
        Flight flight = new Flight(tracker, 0.0, -5.0);
        flight.to(0.0, 5.0, 10.0);
        assertTrue(tracker.racing());
        tracker.setCourse(COURSE, line(), true, flight.now);
        flight.events.addAll(tracker.drainEvents());
        List<LapTracker.Event> aborts = flight.of(LapTracker.EventType.ABORT);
        assertEquals(1, aborts.size());
        assertEquals(LapTracker.AbortReason.COURSE_CHANGED, aborts.getFirst().reason());
        assertTrue(tracker.pointToPoint());
    }

    @Test
    void singleElementStaysALap() {
        LapTracker tracker = tracker(List.of(gate(0.0, 0.0, 0.0F)), true, 2);
        assertFalse(tracker.pointToPoint());
        assertEquals(2, tracker.lapTarget());
        assertEquals(0, tracker.finishIndex());
    }

    @Test
    void ghostReferenceGivesTheDeltaAtTheFinish() {
        LapTracker tracker = tracker(line(), true, 1);
        tracker.setReference(new long[]{2_500L * MS, 1_200L * MS, 2_500L * MS});
        Flight flight = new Flight(tracker, 0.0, -5.0);
        flight.to(0.0, 25.0, 10.0);
        LapTracker.Event lap = flight.of(LapTracker.EventType.LAP).getFirst();
        assertEquals(-500L * MS, lap.deltaNanos(), TOLERANCE);
        assertEquals(-200L * MS, flight.of(LapTracker.EventType.GATE).getFirst().deltaNanos(), TOLERANCE);
    }

    @Test
    void verifierFindsARunFromStartToFinish() {
        LapVerifier verifier = new LapVerifier();
        verifier.record(COURSE, 0, 10 * S);
        verifier.record(COURSE, 1, 12 * S);
        verifier.record(COURSE, 2, 15 * S);
        assertNull(verifier.find(COURSE, 3, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE), "als Runde fehlt die Rückkehr zum Start");
        LapVerifier.VerifiedLap run = verifier.find(COURSE, 3, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE, true);
        assertNotNull(run);
        assertEquals(10 * S, run.startNanos());
        assertEquals(5 * S, run.nanos());
    }

    @Test
    void verifierRejectsASkippedElement() {
        LapVerifier verifier = new LapVerifier();
        verifier.record(COURSE, 0, S);
        verifier.record(COURSE, 2, 3 * S);
        assertNull(verifier.find(COURSE, 3, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE, true));
        verifier.record(COURSE, 1, 4 * S);
        assertNull(verifier.find(COURSE, 3, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE, true));
    }

    @Test
    void verifierNeedsSeparateCrossingsWhenStartAndFinishShareAGate() {
        LapVerifier verifier = new LapVerifier();
        verifier.record(COURSE, 2, S, 1L);
        verifier.record(COURSE, 0, S, 1L);
        verifier.record(COURSE, 1, 3 * S, 2L);
        assertNull(verifier.find(COURSE, 3, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE, true));
        verifier.record(COURSE, 2, 6 * S, 3L);
        verifier.record(COURSE, 0, 6 * S, 3L);
        LapVerifier.VerifiedLap run = verifier.find(COURSE, 3, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE, true);
        assertNotNull(run);
        assertEquals(5 * S, run.nanos());
    }

    @Test
    void twoElementsAreStartAndFinish() {
        LapVerifier verifier = new LapVerifier();
        verifier.record(COURSE, 0, S);
        verifier.record(COURSE, 1, 4 * S);
        LapVerifier.VerifiedLap run = verifier.find(COURSE, 2, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE, true);
        assertNotNull(run);
        assertEquals(3 * S, run.nanos());
    }

    @Test
    void racingLineEndsAtTheFinish() {
        assertEquals(3, PointToPoint.remaining(3, 0, true));
        assertEquals(2, PointToPoint.remaining(3, 1, false));
        assertEquals(1, PointToPoint.remaining(3, 2, false));
        assertEquals(0, PointToPoint.remaining(3, -1, false));
        LapTracker tracker = tracker(line(), true, 1);
        Flight flight = new Flight(tracker, 0.0, -5.0);
        flight.to(0.0, 12.0, 10.0);
        assertEquals(2, tracker.nextGate());
        RaceLine.Path path = RaceLine.of(tracker, RaceLine.MAX_AHEAD, flight.x, Y, flight.z);
        assertFalse(path.isEmpty());
        assertTrue(path.ahead().getLast().z() > 18.0, "Ideallinie endet am Ziel: " + path.ahead().getLast());
        LapTracker loop = tracker(line(), false, 1);
        Flight lap = new Flight(loop, 0.0, -5.0);
        lap.to(0.0, 12.0, 10.0);
        RaceLine.Path back = RaceLine.of(loop, RaceLine.MAX_AHEAD, lap.x, Y, lap.z);
        assertTrue(back.ahead().getLast().z() < 18.0, "bei Runden führt sie zurück zum Start: " + back.ahead().getLast());
    }

    @Test
    void minimumTimeUsesTheOpenPath() {
        List<GateGeometry> gates = line();
        assertEquals(20.0, PointToPoint.pathLength(gates), 1.0E-9);
        assertEquals(40.0, GateGeometry.pathLength(gates), 1.0E-9);
        assertEquals(0.0, PointToPoint.pathLength(List.of(gates.getFirst())), 1.0E-9);
    }

    @Test
    void modeActiveNeedsTwoPasses() {
        assertTrue(PointToPoint.active(CourseMode.POINT_TO_POINT, 2));
        assertFalse(PointToPoint.active(CourseMode.POINT_TO_POINT, 1));
        assertFalse(PointToPoint.active(CourseMode.LAPS, 5));
        assertTrue(PointToPoint.finish(CourseMode.POINT_TO_POINT, 4, 3));
        assertFalse(PointToPoint.finish(CourseMode.POINT_TO_POINT, 4, 0));
        assertFalse(PointToPoint.finish(CourseMode.LAPS, 4, 3));
    }
}
