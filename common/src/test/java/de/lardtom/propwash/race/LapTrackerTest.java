package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LapTrackerTest {
    private static final long MS = 1_000_000L;
    private static final long TOLERANCE = 10_000L;
    private static final double Y = 65.5;
    private static final double START_Z = -1.0E-6;

    private static List<GateGeometry> course() {
        return List.of(
                GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(0.0, 64.0, 10.0, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(0.0, 64.0, 20.0, 0.0F, GateSize.MEDIUM));
    }

    private static final class Flight {
        final LapTracker tracker;
        final List<LapTracker.Event> events = new ArrayList<>();
        long now = 1_000L * MS;
        double x;
        double y = Y;
        double z = START_Z;

        Flight(LapTracker tracker) {
            this.tracker = tracker;
            tracker.update(this.now, this.x, this.y, this.z);
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
                this.tracker.update(this.now, this.x, this.y, this.z);
                this.events.addAll(this.tracker.drainEvents());
            }
        }

        void hover(long nanos) {
            long end = this.now + nanos;
            while (this.now < end) {
                this.now = Math.min(end, this.now + 50 * MS);
                this.tracker.update(this.now, this.x, this.y, this.z);
                this.events.addAll(this.tracker.drainEvents());
            }
        }

        void lap(double speed) {
            this.to(0.0, 25.0, speed);
            this.to(10.0, 25.0, speed);
            this.to(10.0, -5.0, speed);
            this.to(0.0, -5.0, speed);
            this.to(0.0, START_Z, speed);
        }

        List<LapTracker.Event> of(LapTracker.EventType type) {
            return this.events.stream().filter(e -> e.type() == type).toList();
        }
    }

    private static LapTracker tracker(int laps) {
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(laps);
        tracker.setCourse(7, course(), 0L);
        return tracker;
    }

    @Test
    void flyingStartCountsLapsAndFinishes() {
        LapTracker tracker = tracker(2);
        Flight flight = new Flight(tracker);
        flight.lap(10.0);
        assertTrue(tracker.racing());
        assertEquals(1, tracker.lap());
        assertEquals(0, tracker.nextGate());
        assertEquals(LapTracker.NONE, tracker.lastLapNanos());
        flight.lap(10.0);
        assertTrue(tracker.racing());
        assertEquals(2, tracker.lap());
        assertEquals(0, tracker.nextGate());
        assertEquals(3, tracker.gateCount());
        assertEquals(8_000L * MS, tracker.lastLapNanos(), TOLERANCE);
        flight.lap(10.0);
        assertFalse(tracker.racing());
        assertEquals(LapTracker.Phase.FINISHED, tracker.phase());
        assertEquals(1, flight.of(LapTracker.EventType.START).size());
        assertEquals(4, flight.of(LapTracker.EventType.GATE).size());
        assertEquals(2, flight.of(LapTracker.EventType.LAP).size());
        List<LapTracker.Event> finish = flight.of(LapTracker.EventType.FINISH);
        assertEquals(1, finish.size());
        assertEquals(16_000L * MS, finish.getFirst().valueNanos(), 2 * TOLERANCE);
        assertEquals(16_000L * MS, tracker.totalNanos(), 2 * TOLERANCE);
        assertEquals(-1, tracker.nextGate());
        assertEquals(0, tracker.gateCount());
        assertEquals(List.of(8_000L * MS, 8_000L * MS), tracker.lapTimes().stream().map(t -> Math.round(t / 1.0E8) * 100_000_000L).toList());
    }

    @Test
    void splitTimesAreRelativeToLapStart() {
        LapTracker tracker = tracker(0);
        Flight flight = new Flight(tracker);
        flight.lap(10.0);
        List<LapTracker.Event> gates = flight.of(LapTracker.EventType.GATE);
        assertEquals(2, gates.size());
        assertEquals(1, gates.get(0).gate());
        assertEquals(1_000L * MS, gates.get(0).valueNanos(), TOLERANCE);
        assertEquals(2_000L * MS, gates.get(1).valueNanos(), TOLERANCE);
        assertFalse(gates.get(0).hasDelta());
    }

    @Test
    void fasterLapGivesNegativeSplitDeltas() {
        LapTracker tracker = tracker(0);
        Flight flight = new Flight(tracker);
        flight.lap(10.0);
        flight.events.clear();
        flight.lap(20.0);
        List<LapTracker.Event> gates = flight.of(LapTracker.EventType.GATE);
        assertEquals(2, gates.size());
        assertEquals(500L * MS - 1_000L * MS, gates.get(0).deltaNanos(), TOLERANCE);
        assertEquals(1_000L * MS - 2_000L * MS, gates.get(1).deltaNanos(), TOLERANCE);
        LapTracker.Event first = flight.of(LapTracker.EventType.LAP).getFirst();
        assertEquals(8_000L * MS, first.valueNanos(), TOLERANCE);
        assertFalse(first.hasDelta());
        assertTrue(first.best());
        flight.events.clear();
        flight.lap(10.0);
        LapTracker.Event second = flight.of(LapTracker.EventType.LAP).getFirst();
        assertEquals(4_000L * MS, second.valueNanos(), TOLERANCE);
        assertEquals(-4_000L * MS, second.deltaNanos(), TOLERANCE);
        assertTrue(second.best());
        assertEquals(4_000L * MS, tracker.bestLapNanos(), TOLERANCE);
        List<LapTracker.Event> slowGates = flight.of(LapTracker.EventType.GATE);
        assertEquals(500L * MS, slowGates.get(0).deltaNanos(), TOLERANCE);
        assertEquals(1_000L * MS, tracker.splitDeltaNanos(), TOLERANCE);
        flight.events.clear();
        flight.lap(10.0);
        LapTracker.Event third = flight.of(LapTracker.EventType.LAP).getFirst();
        assertEquals(4_000L * MS, third.deltaNanos(), TOLERANCE);
        assertFalse(third.best());
        assertEquals(4_000L * MS, tracker.bestLapNanos(), TOLERANCE);
        assertEquals(8_000L * MS, tracker.lastLapNanos(), TOLERANCE);
    }

    @Test
    void skippedGateIsReportedAndMustBeFlown() {
        LapTracker tracker = tracker(0);
        Flight flight = new Flight(tracker);
        flight.to(0.0, 5.0, 10.0);
        flight.to(5.0, 5.0, 10.0);
        flight.to(5.0, 15.0, 10.0);
        flight.to(0.0, 15.0, 10.0);
        flight.to(0.0, 25.0, 10.0);
        List<LapTracker.Event> missed = flight.of(LapTracker.EventType.MISSED);
        assertEquals(1, missed.size());
        assertEquals(1, missed.getFirst().gate());
        assertEquals(2, missed.getFirst().valueNanos());
        assertEquals(1, tracker.nextGate());
        assertTrue(flight.of(LapTracker.EventType.GATE).isEmpty());
    }

    @Test
    void wrongWayThroughNextGateIsReported() {
        LapTracker tracker = tracker(0);
        Flight flight = new Flight(tracker);
        flight.to(0.0, 5.0, 10.0);
        flight.to(5.0, 5.0, 10.0);
        flight.to(5.0, 15.0, 10.0);
        flight.to(0.0, 15.0, 10.0);
        flight.to(0.0, 5.0, 10.0);
        assertEquals(1, flight.of(LapTracker.EventType.WRONG_WAY).size());
        assertEquals(1, tracker.nextGate());
    }

    @Test
    void countdownStartsStandingRaceWithHoleshot() {
        LapTracker tracker = tracker(1);
        Flight flight = new Flight(tracker);
        assertTrue(tracker.beginCountdown(flight.now, 3_000L * MS));
        flight.hover(3_500L * MS);
        List<LapTracker.Event> countdown = flight.of(LapTracker.EventType.COUNTDOWN);
        assertEquals(3, countdown.size());
        assertEquals(3L, countdown.get(0).valueNanos());
        assertEquals(1L, countdown.get(2).valueNanos());
        assertEquals(1, flight.of(LapTracker.EventType.GO).size());
        assertTrue(tracker.racing());
        assertEquals(0, tracker.nextGate());
        flight.lap(10.0);
        assertTrue(tracker.racing());
        assertEquals(0, tracker.nextGate());
        flight.to(0.0, 5.0, 10.0);
        assertEquals(LapTracker.Phase.FINISHED, tracker.phase());
        long expected = 500L * MS + 8_000L * MS;
        assertEquals(expected, tracker.lastLapNanos(), 2 * TOLERANCE);
    }

    @Test
    void movingBehindTheStartIsNoFalseStart() {
        LapTracker tracker = tracker(1);
        Flight flight = new Flight(tracker);
        tracker.beginCountdown(flight.now, 3_000L * MS);
        flight.hover(1_200L * MS);
        flight.to(0.0, -2.0, 5.0);
        assertEquals(0, flight.of(LapTracker.EventType.FALSE_START).size());
        assertEquals(LapTracker.Phase.COUNTDOWN, tracker.phase());
    }

    @Test
    void crossingTheStartBeforeGoCostsAPenalty() {
        LapTracker tracker = tracker(1);
        Flight flight = new Flight(tracker);
        tracker.beginCountdown(flight.now, 3_000L * MS);
        flight.hover(1_000L * MS);
        flight.to(0.0, 2.0, 5.0);
        List<LapTracker.Event> falseStarts = flight.of(LapTracker.EventType.FALSE_START);
        assertEquals(1, falseStarts.size());
        assertEquals(LapTracker.FALSE_START_PENALTY_NANOS, falseStarts.getFirst().valueNanos());
        assertTrue(tracker.falseStarted());
        assertEquals(LapTracker.Phase.COUNTDOWN, tracker.phase());
        flight.to(0.0, -1.0, 5.0);
        flight.to(0.0, 2.0, 5.0);
        assertEquals(1, flight.of(LapTracker.EventType.FALSE_START).size());
        flight.hover(2_000L * MS);
        assertTrue(tracker.racing());
        assertEquals(1, tracker.nextGate());
        assertEquals(LapTracker.FALSE_START_PENALTY_NANOS, tracker.penaltyNanos());
        long go = flight.of(LapTracker.EventType.GO).getFirst().nanos();
        flight.to(0.0, 25.0, 10.0);
        flight.to(10.0, 25.0, 10.0);
        flight.to(10.0, -5.0, 10.0);
        flight.to(0.0, -5.0, 10.0);
        flight.to(0.0, 1.0, 10.0);
        List<LapTracker.Event> laps = flight.of(LapTracker.EventType.LAP);
        assertEquals(1, laps.size());
        long crossing = laps.getFirst().nanos();
        assertEquals(crossing - go + LapTracker.FALSE_START_PENALTY_NANOS, laps.getFirst().valueNanos(), TOLERANCE);
        assertEquals(laps.getFirst().valueNanos(), tracker.totalNanos(), TOLERANCE);
    }

    @Test
    void ghostReferenceDrivesSplitDeltas() {
        LapTracker tracker = tracker(0);
        Flight flight = new Flight(tracker);
        flight.to(0.0, 1.0, 10.0);
        tracker.setReference(new long[]{100_000L * MS, 500L * MS, 100_000L * MS});
        assertTrue(tracker.hasReference());
        flight.lap(10.0);
        flight.to(0.0, 1.0, 10.0);
        List<LapTracker.Event> gates = flight.of(LapTracker.EventType.GATE);
        assertTrue(gates.get(0).deltaNanos() > 0L, "Gate 2 langsamer als der Geist: " + gates.get(0).deltaNanos());
        assertTrue(gates.get(1).deltaNanos() < 0L, "Gate 3 schneller als der Geist: " + gates.get(1).deltaNanos());
        assertTrue(flight.of(LapTracker.EventType.LAP).getFirst().deltaNanos() < 0L);
        tracker.setReference(new long[]{1L, 2L});
        assertFalse(tracker.hasReference());
    }

    @Test
    void idleRaceTimesOut() {
        LapTracker tracker = tracker(0);
        tracker.setTimeoutNanos(5_000L * MS);
        Flight flight = new Flight(tracker);
        flight.to(0.0, 5.0, 10.0);
        assertTrue(tracker.racing());
        flight.hover(6_000L * MS);
        assertFalse(tracker.racing());
        List<LapTracker.Event> aborts = flight.of(LapTracker.EventType.ABORT);
        assertEquals(1, aborts.size());
        assertEquals(LapTracker.AbortReason.TIMEOUT, aborts.getFirst().reason());
    }

    @Test
    void teleportDoesNotCountAsCrossing() {
        LapTracker tracker = tracker(0);
        Flight flight = new Flight(tracker);
        flight.now += 50 * MS;
        tracker.update(flight.now, 0.0, Y, 30.0);
        assertTrue(tracker.drainEvents().isEmpty());
        assertFalse(tracker.racing());
    }

    @Test
    void changingCourseAbortsRace() {
        LapTracker tracker = tracker(0);
        Flight flight = new Flight(tracker);
        flight.to(0.0, 5.0, 10.0);
        assertTrue(tracker.racing());
        tracker.setCourse(7, course().subList(0, 2), flight.now);
        assertFalse(tracker.racing());
        assertEquals(LapTracker.AbortReason.COURSE_CHANGED, tracker.drainEvents().getFirst().reason());
    }

    @Test
    void singleGateCourseCountsEveryPass() {
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(0);
        tracker.setCourse(3, course().subList(0, 1), 0L);
        Flight flight = new Flight(tracker);
        flight.lap(10.0);
        flight.lap(10.0);
        flight.lap(10.0);
        assertEquals(2, flight.of(LapTracker.EventType.LAP).size());
        assertEquals(8_000L * MS, tracker.lastLapNanos(), TOLERANCE);
    }
}
