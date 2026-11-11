package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LapParityTest {
    private static final long MS = 1_000_000L;
    private static final double Y = 65.5;
    private static final int COURSE = 65;
    private static final double SPEED_STEP = 0.6;

    private static final class Duel {
        final List<GateGeometry> gates;
        final List<CourseElement> kinds;
        final boolean pointToPoint;
        final LapTracker client = new LapTracker();
        final LapVerifier server = new LapVerifier();
        final List<LapTracker.Event> events = new ArrayList<>();
        final List<Integer> clientPasses = new ArrayList<>();
        final List<Integer> serverPasses = new ArrayList<>();
        final List<Long> verifiedLaps = new ArrayList<>();
        final List<Long> verifiedStarts = new ArrayList<>();
        final List<Long> clientStarts = new ArrayList<>();
        long lastEnd = LapVerifier.NO_SEQUENCE;
        long startedAt = Long.MIN_VALUE;
        long nextEvent;
        long now = 1_000L * MS;
        double x;
        double z;

        Duel(List<GateGeometry> gates, boolean pointToPoint, double x, double z) {
            this.gates = gates;
            this.kinds = CourseCheckpoints.kinds(gates);
            this.pointToPoint = pointToPoint;
            this.client.setCourse(COURSE, gates, pointToPoint, this.now);
            this.client.setLapTarget(3);
            this.x = x;
            this.z = z;
            this.client.update(this.now, x, Y, z);
        }

        Duel(List<GateGeometry> gates, double x, double z) {
            this(gates, false, x, z);
        }

        int checkpoints() {
            return CourseCheckpoints.count(this.kinds);
        }

        void segment(double x0, double z0, double x1, double z1, long from, long to) {
            List<double[]> hits = new ArrayList<>();
            List<List<CourseOrder.Pass>> groups = new ArrayList<>();
            List<GateGeometry> seen = new ArrayList<>();
            for (int i = 0; i < this.gates.size(); i++) {
                GateGeometry base = this.gates.get(i).withReverse(false);
                int at = seen.indexOf(base);
                if (at < 0) {
                    seen.add(base);
                    groups.add(new ArrayList<>());
                    at = seen.size() - 1;
                }
                groups.get(at).add(new CourseOrder.Pass(i, this.gates.get(i).reversed()));
            }
            for (int g = 0; g < seen.size(); g++) {
                CourseCrossings.collect(seen.get(g), groups.get(g), COURSE, (element, stage) -> CourseCheckpoints.index(this.kinds, element, stage), x0, Y, z0, x1, Y,
                        z1, () -> this.nextEvent++, hits);
            }
            CourseCrossings.sort(hits, id -> this.pointToPoint ? CourseCrossings.OPEN : this.checkpoints());
            for (double[] hit : hits) {
                long time = from + Math.round((to - from) * hit[CourseCrossings.TIME]);
                int checkpoint = (int) hit[CourseCrossings.CHECKPOINT];
                this.server.record(COURSE, checkpoint, time, (long) hit[CourseCrossings.EVENT]);
                this.serverPasses.add(checkpoint);
            }
        }

        Duel to(double tx, double tz) {
            double dx = tx - this.x;
            double dz = tz - this.z;
            double length = Math.sqrt(dx * dx + dz * dz);
            int steps = Math.max(1, (int) Math.ceil(length / SPEED_STEP));
            double sx = this.x;
            double sz = this.z;
            for (int i = 1; i <= steps; i++) {
                double px = this.x;
                double pz = this.z;
                long from = this.now;
                this.now += 20 * MS;
                this.x = sx + dx * i / steps;
                this.z = sz + dz * i / steps;
                this.segment(px, pz, this.x, this.z, from, this.now);
                this.client.update(this.now, this.x, Y, this.z);
                for (LapTracker.Event event : this.client.drainEvents()) {
                    this.events.add(event);
                    this.onClientEvent(event);
                }
            }
            return this;
        }

        void onClientEvent(LapTracker.Event event) {
            switch (event.type()) {
                case START -> {
                    this.clientPasses.add(0);
                    this.startedAt = event.nanos();
                }
                case GATE -> this.clientPasses.add(event.gate() + 0);
                case LAP -> {
                    this.clientPasses.add(this.pointToPoint ? this.gates.size() - 1 : 0);
                    LapVerifier.VerifiedLap verified = RaceServer.verifiedLap(this.server, COURSE, this.checkpoints(), this.pointToPoint, this.lastEnd,
                            event.lap() == 1, this.startedAt, event.valueNanos(), 0L);
                    assertNotNull(verified, "Server findet die Runde nicht: Client " + this.clientPasses + ", Server " + this.serverPasses);
                    long gap = Math.abs(verified.nanos() - event.valueNanos());
                    assertTrue(gap <= 40 * MS, "Rundenzeit Client " + event.valueNanos() + " und Server " + verified.nanos());
                    this.lastEnd = verified.endSequence();
                    this.verifiedLaps.add(verified.nanos());
                    this.verifiedStarts.add(verified.startNanos());
                    this.clientStarts.add(event.nanos() - event.valueNanos());
                }
                default -> {
                }
            }
        }

        List<Integer> serverElements() {
            List<Integer> out = new ArrayList<>();
            for (int checkpoint : this.serverPasses) {
                int element = CourseCheckpoints.element(this.kinds, checkpoint);
                if (element >= 0 && CourseCheckpoints.index(this.kinds, element, this.kinds.get(element).stages() - 1) == checkpoint) {
                    out.add(element);
                }
            }
            return out;
        }

        long count(LapTracker.EventType type) {
            return this.events.stream().filter(event -> event.type() == type).count();
        }
    }

    private static boolean subsequence(List<Integer> shorter, List<Integer> longer) {
        int at = 0;
        for (int value : longer) {
            if (at < shorter.size() && shorter.get(at) == value) {
                at++;
            }
        }
        return at == shorter.size();
    }

    private static void sameOrder(Duel duel) {
        assertTrue(subsequence(duel.clientPasses, duel.serverElements()), "Reihenfolge: Client " + duel.clientPasses + ", Server " + duel.serverElements());
    }

    private static GateGeometry gate(double x, double z, float yaw) {
        return GateGeometry.of(x, 64.0, z, yaw, GateSize.MEDIUM);
    }

    private static GateGeometry pole(double x, double z, int side) {
        return GateGeometry.element(x, 64.0, z, 0.0F, CourseElement.POLE, side, 4.0, 3.0, GateTilt.NONE);
    }

    private static GateGeometry shortPole(double x, double z, double width) {
        return GateGeometry.element(x, 64.0, z, 0.0F, CourseElement.POLE, CourseElement.LEFT, width, 3.0, GateTilt.NONE);
    }

    private static GateGeometry flag(double x, double z, int side) {
        return GateGeometry.element(x, 64.0, z, 0.0F, CourseElement.FLAG, side, 5.0, 4.0, GateTilt.NONE);
    }

    private static GateGeometry poleOnGate(double x, double z) {
        return GateGeometry.element(x, 64.0, z, 0.0F, CourseElement.GATE_POLE, 0, 1.0, 3.0, GateTilt.NONE, GateMount.STANDING,
                ScoreArea.DEFAULT.withRole(ScoreArea.Role.PASS));
    }

    private static void lap(Duel duel) {
        duel.to(0.0, 2.0).to(0.0, 11.5).to(0.0, 13.0).to(6.0, 13.0).to(6.0, -3.0).to(0.0, -3.0);
    }

    @Test
    void poleInThePlaneOfTheNextGateCountsOnBothSides() {
        List<GateGeometry> course = List.of(gate(0.0, 0.0, 0.0F), pole(-3.0, 12.0, CourseElement.LEFT), gate(0.0, 12.0, 0.0F));
        Duel duel = new Duel(course, 0.0, -3.0);
        for (int i = 0; i < 3; i++) {
            lap(duel);
        }
        duel.to(0.0, 2.0);
        assertEquals(3, duel.count(LapTracker.EventType.LAP), duel.events.toString());
        assertEquals(1, duel.count(LapTracker.EventType.FINISH));
        assertEquals(0, duel.count(LapTracker.EventType.MISSED), "Pole und Gate im selben Moment: keine Meldung");
        assertEquals(3, duel.verifiedLaps.size());
        sameOrder(duel);
    }

    @Test
    void gatePoleInThePlaneOfTheNextGateCountsOnBothSides() {
        List<GateGeometry> course = List.of(gate(0.0, 0.0, 0.0F), poleOnGate(-3.0, 12.0), gate(0.0, 12.0, 0.0F));
        Duel duel = new Duel(course, 0.0, -3.0);
        for (int i = 0; i < 3; i++) {
            lap(duel);
        }
        duel.to(0.0, 2.0);
        assertEquals(3, duel.verifiedLaps.size(), duel.events.toString());
        sameOrder(duel);
    }

    @Test
    void poleAtTheEndOfTheCourseBesideTheStartGate() {
        List<GateGeometry> course = List.of(gate(0.0, 0.0, 0.0F), gate(0.0, 12.0, 0.0F), pole(-3.0, 0.0, CourseElement.LEFT));
        Duel duel = new Duel(course, 0.0, -3.0);
        for (int i = 0; i < 3; i++) {
            lap(duel);
        }
        duel.to(0.0, 2.0);
        assertEquals(3, duel.count(LapTracker.EventType.LAP), "Jede Runde endet im ersten Durchflug: " + duel.events);
        assertEquals(0, duel.count(LapTracker.EventType.MISSED), duel.events.toString());
        assertEquals(3, duel.verifiedLaps.size());
        sameOrder(duel);
    }

    @Test
    void wholeRowInOnePlaneKeepsTheCourseOrder() {
        List<GateGeometry> course = List.of(gate(0.0, 0.0, 0.0F), gate(-8.0, 12.0, 0.0F), pole(-3.0, 12.0, CourseElement.LEFT), gate(0.0, 12.0, 0.0F));
        Duel duel = new Duel(course, 0.0, -3.0);
        duel.to(0.0, 2.0).to(-8.0, 8.0).to(-8.0, 14.0).to(-12.0, 14.0).to(-12.0, 8.0).to(0.0, 8.0).to(0.0, 14.0).to(6.0, 14.0).to(6.0, -3.0).to(0.0, -3.0).to(0.0, 2.0);
        assertEquals(1, duel.count(LapTracker.EventType.LAP), duel.events.toString());
        assertEquals(1, duel.verifiedLaps.size());
        sameOrder(duel);
    }

    @Test
    void turnPoleWithThreeStagesAndAGateInItsLastPlane() {
        List<GateGeometry> course = List.of(gate(0.0, 0.0, 0.0F), flag(0.0, 10.0, CourseElement.LEFT), gate(3.0, 10.0, 180.0F));
        Duel duel = new Duel(course, 0.0, -3.0);
        for (int i = 0; i < 2; i++) {
            duel.to(0.0, 2.0).to(-2.0, 8.0).to(-2.0, 12.0).to(3.0, 12.0).to(3.0, 8.0).to(0.0, -3.0);
        }
        duel.to(0.0, 2.0);
        assertEquals(2, duel.count(LapTracker.EventType.LAP), duel.events.toString());
        assertEquals(0, duel.count(LapTracker.EventType.MISSED), duel.events.toString());
        assertEquals(2, duel.verifiedLaps.size());
        sameOrder(duel);
    }

    @Test
    void flagFollowedByAGateInItsFirstPlane() {
        List<GateGeometry> course = List.of(gate(0.0, 0.0, 0.0F), flag(4.0, 12.0, CourseElement.LEFT), gate(0.0, 12.0, 0.0F));
        Duel duel = new Duel(course, 0.0, -3.0);
        duel.to(0.0, 2.0).to(2.0, 10.0).to(2.0, 14.0).to(7.0, 14.0).to(7.0, 10.0).to(0.0, 10.0).to(0.0, 14.0).to(-6.0, 14.0).to(-6.0, -3.0).to(0.0, -3.0).to(0.0, 2.0);
        assertEquals(1, duel.count(LapTracker.EventType.LAP), duel.events.toString());
        assertEquals(1, duel.verifiedLaps.size());
        sameOrder(duel);
    }

    @Test
    void poleBehindTheGateNeedsItsOwnPassOnTheClient() {
        List<GateGeometry> course = List.of(gate(0.0, 0.0, 0.0F), gate(0.0, 12.0, 0.0F), pole(-3.0, 12.0, CourseElement.LEFT), gate(14.0, 6.0, 180.0F));
        Duel duel = new Duel(course, 0.0, -3.0);
        duel.to(0.0, 2.0).to(0.0, 14.0);
        assertEquals(List.of(0, 1), duel.clientPasses, "Der Client wertet nur das Gate, die Stange wartet auf ihren eigenen Durchflug");
        duel.to(-5.0, 14.0).to(-5.0, 8.0).to(0.0, 8.0).to(0.0, 14.0).to(14.0, 14.0).to(14.0, 2.0).to(0.0, -3.0).to(0.0, 2.0);
        assertEquals(1, duel.verifiedLaps.size(), duel.events.toString());
        sameOrder(duel);
    }

    @Test
    void shortenedReachMakesThePoleAPassOfItsOwn() {
        List<GateGeometry> course = List.of(gate(0.0, 0.0, 0.0F), shortPole(-3.0, 12.0, 1.0), gate(0.0, 12.0, 0.0F));
        Duel shortcut = new Duel(course, 0.0, -3.0);
        lap(shortcut);
        shortcut.to(0.0, 2.0);
        assertEquals(0, shortcut.count(LapTracker.EventType.LAP), "Ohne die Stange gibt es keine Runde: " + shortcut.events);
        assertTrue(shortcut.events.stream().anyMatch(event -> event.type() == LapTracker.EventType.MISSED && event.gate() == 1),
                "Die Stange gilt als verpasst: " + shortcut.events);
        assertNull(shortcut.server.find(COURSE, shortcut.checkpoints(), LapVerifier.NO_SEQUENCE, Long.MIN_VALUE), "Server wertet die Abkürzung auch nicht");
        Duel full = new Duel(course, 0.0, -3.0);
        full.to(0.0, 2.0).to(-2.5, 10.0).to(-2.5, 14.0).to(-5.0, 14.0).to(-5.0, 10.0).to(0.0, 10.0).to(0.0, 14.0).to(6.0, 14.0).to(6.0, -3.0).to(0.0, -3.0).to(0.0, 2.0);
        assertEquals(1, full.count(LapTracker.EventType.LAP), full.events.toString());
        assertEquals(1, full.verifiedLaps.size());
        sameOrder(full);
    }

    @Test
    void wrongSideAndUndoStayStricterOnTheClient() {
        List<GateGeometry> course = List.of(gate(0.0, 0.0, 0.0F), pole(5.0, 10.0, CourseElement.LEFT), flag(-5.0, 20.0, CourseElement.LEFT));
        Duel duel = new Duel(course, 0.0, -3.0);
        duel.to(0.0, 2.0).to(3.0, 8.0).to(3.0, 12.0);
        assertEquals(1, duel.count(LapTracker.EventType.WRONG_SIDE), "Falsche Seite der Stange: " + duel.events);
        assertEquals(List.of(0), duel.clientPasses);
        duel.to(1.0, 12.0).to(1.0, 8.0).to(7.0, 8.0).to(7.0, 12.0);
        assertEquals(List.of(0, 1), duel.clientPasses, duel.events.toString());
        duel.to(-7.0, 18.0).to(-7.0, 22.0);
        assertEquals(1, duel.client.stage(2));
        duel.to(-7.0, 18.0);
        assertEquals(0, duel.client.stage(2), "Zurück nimmt die Stufe zurück");
        duel.to(-7.0, 22.0).to(-2.0, 22.0).to(-2.0, 18.0).to(0.0, -3.0).to(0.0, 2.0);
        assertEquals(1, duel.verifiedLaps.size(), duel.events.toString());
        sameOrder(duel);
    }

    @Test
    void repeatedGateBothWaysVerifiesEveryLap() {
        GateGeometry a = gate(0.0, 0.0, 0.0F);
        List<GateGeometry> course = List.of(a, gate(0.0, 12.0, 0.0F), a.withReverse(true));
        Duel duel = new Duel(course, 0.0, -3.0);
        for (int i = 0; i < 2; i++) {
            duel.to(0.0, 16.0).to(6.0, 16.0).to(6.0, 4.0).to(0.0, 4.0).to(0.0, -4.0).to(-6.0, -4.0).to(-6.0, -6.0).to(0.0, -6.0).to(0.0, -3.0);
        }
        duel.to(0.0, 2.0);
        assertEquals(2, duel.count(LapTracker.EventType.LAP), duel.events.toString());
        assertEquals(2, duel.verifiedLaps.size());
    }

    @Test
    void pointToPointRunOverAPoleBesideTheFinish() {
        List<GateGeometry> course = List.of(gate(0.0, 0.0, 0.0F), pole(-3.0, 12.0, CourseElement.LEFT), gate(0.0, 12.0, 0.0F));
        Duel duel = new Duel(course, true, 0.0, -3.0);
        duel.to(0.0, 2.0).to(0.0, 14.0);
        assertEquals(1, duel.count(LapTracker.EventType.LAP), duel.events.toString());
        assertEquals(1, duel.verifiedLaps.size());
        assertFalse(duel.client.racing());
    }

    private static List<GateGeometry> triangle() {
        return List.of(gate(0.0, 0.0, 0.0F), gate(0.0, 12.0, 0.0F), gate(8.0, 6.0, 180.0F));
    }

    private static Duel startAgain(Duel duel) {
        return duel.to(-6.0, 2.0).to(-6.0, -20.0).to(0.0, -20.0).to(0.0, 2.0);
    }

    private static void sameStarts(Duel duel, int laps) {
        assertEquals(laps, duel.count(LapTracker.EventType.LAP), duel.events.toString());
        assertEquals(laps, duel.verifiedLaps.size(), duel.events.toString());
        for (int i = 0; i < laps; i++) {
            long gap = Math.abs(duel.verifiedStarts.get(i) - duel.clientStarts.get(i));
            assertTrue(gap <= 40 * MS, "Runde " + (i + 1) + ": Server beginnt " + (duel.verifiedStarts.get(i) - duel.clientStarts.get(i)) / MS
                    + " ms nach dem Start der Uhr");
        }
    }

    @Test
    void startGateAgainBeforeTheFirstGateKeepsTheTimedStart() {
        Duel duel = new Duel(triangle(), 0.0, -3.0);
        duel.to(0.0, 2.0);
        startAgain(duel);
        assertEquals(1, duel.count(LapTracker.EventType.START), "Der zweite Durchflug startet die Uhr nicht neu: " + duel.events);
        assertTrue(duel.count(LapTracker.EventType.MISSED) >= 1, duel.events.toString());
        duel.to(0.0, 14.0).to(8.0, 14.0).to(8.0, -3.0).to(0.0, -3.0).to(0.0, 2.0);
        sameStarts(duel, 1);
        assertTrue(duel.verifiedLaps.getFirst() > 3_000L * MS, "Runde mit Schleife zurück zum Start: " + duel.verifiedLaps);
    }

    @Test
    void startGateAgainAfterTheFirstGateKeepsTheTimedStart() {
        Duel duel = new Duel(triangle(), 0.0, -3.0);
        duel.to(0.0, 2.0).to(0.0, 14.0).to(-6.0, 14.0).to(-6.0, -20.0).to(0.0, -20.0).to(0.0, 2.0);
        assertEquals(1, duel.count(LapTracker.EventType.START), duel.events.toString());
        duel.to(0.0, 14.0).to(8.0, 14.0).to(8.0, -3.0).to(0.0, -3.0).to(0.0, 2.0);
        sameStarts(duel, 1);
    }

    @Test
    void startGateAgainInTheSecondLapKeepsItsStart() {
        Duel duel = new Duel(triangle(), 0.0, -3.0);
        duel.to(0.0, 2.0).to(0.0, 14.0).to(8.0, 14.0).to(8.0, -3.0).to(0.0, -3.0).to(0.0, 2.0);
        startAgain(duel);
        duel.to(0.0, 14.0).to(8.0, 14.0).to(8.0, -3.0).to(0.0, -3.0).to(0.0, 2.0);
        duel.to(0.0, 14.0).to(-6.0, 14.0).to(-6.0, -20.0).to(0.0, -20.0).to(0.0, 2.0);
        duel.to(0.0, 14.0).to(8.0, 14.0).to(8.0, -3.0).to(0.0, -3.0).to(0.0, 2.0);
        assertEquals(1, duel.count(LapTracker.EventType.FINISH), duel.events.toString());
        sameStarts(duel, 3);
    }

    @Test
    void startGateAgainOnPointToPointKeepsTheTimedStart() {
        List<GateGeometry> course = List.of(gate(0.0, 0.0, 0.0F), gate(0.0, 12.0, 0.0F), gate(0.0, 24.0, 0.0F));
        Duel duel = new Duel(course, true, 0.0, -3.0);
        duel.to(0.0, 2.0).to(0.0, 14.0).to(-6.0, 14.0).to(-6.0, -20.0).to(0.0, -20.0).to(0.0, 2.0).to(0.0, 26.0);
        assertFalse(duel.client.racing(), duel.events.toString());
        sameStarts(duel, 1);
    }

    @Test
    void startGateAgainOnALoopCourseKeepsTheTimedStart() {
        GateGeometry a = gate(0.0, 0.0, 0.0F);
        Duel back = new Duel(List.of(a, gate(0.0, 12.0, 0.0F), a.withReverse(true)), 0.0, -3.0);
        back.to(0.0, 2.0);
        startAgain(back);
        back.to(0.0, 16.0).to(6.0, 16.0).to(6.0, 4.0).to(0.0, 4.0).to(0.0, -4.0).to(-6.0, -4.0).to(-6.0, -6.0).to(0.0, -6.0).to(0.0, 2.0);
        sameStarts(back, 1);
        Duel twice = new Duel(List.of(a, gate(0.0, 12.0, 0.0F), a), 0.0, -3.0);
        twice.to(0.0, 2.0);
        startAgain(twice);
        twice.to(0.0, 14.0).to(6.0, 14.0).to(6.0, -6.0).to(0.0, -6.0).to(0.0, 2.0).to(-6.0, 2.0).to(-6.0, -6.0).to(0.0, -6.0).to(0.0, 2.0);
        sameStarts(twice, 1);
    }
}
