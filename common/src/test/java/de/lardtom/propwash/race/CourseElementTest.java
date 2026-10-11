package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseElementTest {
    private static final long MS = 1_000_000L;
    private static final double GROUND = 64.0;
    private static final double FLY = 65.5;

    private static GateGeometry pole(double x, double z, float yaw, int side) {
        return GateGeometry.element(x, GROUND, z, yaw, CourseElement.POLE, side, 4.0, 3.0, GateTilt.NONE);
    }

    private static GateGeometry flag(double x, double z, float yaw, int turn) {
        return GateGeometry.element(x, GROUND, z, yaw, CourseElement.FLAG, turn, 5.0, 4.0, GateTilt.NONE);
    }

    private static GateGeometry cube(double x, double z, float yaw, int exit) {
        double outer = CourseElement.CUBE.outerWidth(3.0);
        return GateGeometry.element(x, GROUND, z, yaw, CourseElement.CUBE, exit, outer, outer, GateTilt.NONE);
    }

    private static final class Flight {
        final LapTracker tracker;
        final List<LapTracker.Event> events = new ArrayList<>();
        long now = 1_000L * MS;
        double x;
        double y;
        double z;

        Flight(LapTracker tracker, double x, double y, double z) {
            this.tracker = tracker;
            this.x = x;
            this.y = y;
            this.z = z;
            tracker.update(this.now, x, y, z);
        }

        Flight to(double tx, double ty, double tz) {
            double dx = tx - this.x;
            double dy = ty - this.y;
            double dz = tz - this.z;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int steps = Math.max(1, (int) Math.ceil(length / 0.3));
            double sx = this.x;
            double sy = this.y;
            double sz = this.z;
            for (int i = 1; i <= steps; i++) {
                this.now += 20 * MS;
                this.x = sx + dx * i / steps;
                this.y = sy + dy * i / steps;
                this.z = sz + dz * i / steps;
                this.tracker.update(this.now, this.x, this.y, this.z);
                this.events.addAll(this.tracker.drainEvents());
            }
            return this;
        }

        Flight around(double cx, double cz, double radius, double fromDegrees, double toDegrees) {
            int steps = (int) Math.ceil(Math.abs(toDegrees - fromDegrees) / 6.0);
            for (int i = 1; i <= steps; i++) {
                double a = Math.toRadians(fromDegrees + (toDegrees - fromDegrees) * i / steps);
                this.to(cx + Math.cos(a) * radius, this.y, cz + Math.sin(a) * radius);
            }
            return this;
        }

        List<LapTracker.Event> of(LapTracker.EventType type) {
            return this.events.stream().filter(e -> e.type() == type).toList();
        }
    }

    private static LapTracker racing(List<GateGeometry> course) {
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(0);
        tracker.setCourse(5, course, 0L);
        return tracker;
    }

    private static GateGeometry start() {
        return GateGeometry.of(0.0, GROUND, 0.0, 0.0F, GateSize.MEDIUM);
    }

    @Test
    void sideAxisIsThePilotsLeft() {
        GateGeometry pole = pole(0.0, 0.0, 0.0F, CourseElement.LEFT);
        assertEquals(1.0, pole.sideX(), 1.0E-12);
        assertEquals(1.0, pole.normalZ(), 1.0E-12);
        GateGeometry west = pole(0.0, 0.0, 90.0F, CourseElement.LEFT);
        assertEquals(-1.0, west.normalX(), 1.0E-12);
        assertEquals(1.0, west.sideZ(), 1.0E-12);
    }

    @Test
    void elementsStandUprightAndKeepOnlyTheirYaw() {
        GateGeometry tilted = GateGeometry.element(0.0, GROUND, 0.0, 0.0F, CourseElement.POLE, 0, 4.0, 3.0, new GateTilt(30.0F, 40.0F, 50.0F));
        assertTrue(tilted.upright());
        assertEquals(new GateTilt(30.0F, 0.0F, 0.0F), tilted.tilt());
        assertEquals(GateMount.STANDING, tilted.mount());
        assertEquals(1.0, tilted.upY(), 1.0E-12);
    }

    @Test
    void poleCountsOnlyTheRequiredSide() {
        GateGeometry left = pole(0.0, 10.0, 0.0F, CourseElement.LEFT);
        Passage pass = left.stages().getFirst();
        assertTrue(pass.crossing(1.5, FLY, 9.0, 1.5, FLY, 11.0, true) >= 0.0);
        assertTrue(pass.crossing(-1.5, FLY, 9.0, -1.5, FLY, 11.0, true) < 0.0);
        assertTrue(pass.crossing(1.5, FLY, 11.0, 1.5, FLY, 9.0, true) < 0.0);
        assertTrue(pass.crossing(1.5, GROUND + 3.5, 9.0, 1.5, GROUND + 3.5, 11.0, true) < 0.0, "über die Stange zählt nicht");
        assertTrue(pass.crossing(5.0, FLY, 9.0, 5.0, FLY, 11.0, true) < 0.0, "zu weit weg zählt nicht");
        assertTrue(left.fouls(0).getFirst().crossing(-1.5, FLY, 9.0, -1.5, FLY, 11.0, true) >= 0.0);
        GateGeometry right = left.withVariant(CourseElement.RIGHT);
        assertTrue(right.stages().getFirst().crossing(-1.5, FLY, 9.0, -1.5, FLY, 11.0, true) >= 0.0);
        assertNotEquals(left, right);
    }

    @Test
    void slalomCountsEachPoleWithItsSide() {
        List<GateGeometry> course = List.of(start(), pole(0.0, 8.0, 0.0F, CourseElement.LEFT), pole(0.0, 14.0, 0.0F, CourseElement.RIGHT),
                pole(0.0, 20.0, 0.0F, CourseElement.LEFT));
        LapTracker tracker = racing(course);
        Flight flight = new Flight(tracker, 0.0, FLY, -2.0);
        flight.to(0.0, FLY, 2.0).to(1.5, FLY, 8.0).to(-1.5, FLY, 14.0).to(1.5, FLY, 20.0).to(1.5, FLY, 23.0);
        List<LapTracker.Event> gates = flight.of(LapTracker.EventType.GATE);
        assertEquals(3, gates.size(), flight.events.toString());
        assertEquals(List.of(1, 2, 3), gates.stream().map(LapTracker.Event::gate).toList());
        assertTrue(flight.of(LapTracker.EventType.WRONG_SIDE).isEmpty());
    }

    @Test
    void wrongSideOfAPoleWarnsAndDoesNotCount() {
        List<GateGeometry> course = List.of(start(), pole(0.0, 8.0, 0.0F, CourseElement.LEFT), pole(0.0, 14.0, 0.0F, CourseElement.RIGHT));
        LapTracker tracker = racing(course);
        Flight flight = new Flight(tracker, 0.0, FLY, -2.0);
        flight.to(0.0, FLY, 2.0).to(-1.5, FLY, 8.0).to(-1.5, FLY, 10.0);
        assertTrue(flight.of(LapTracker.EventType.GATE).isEmpty());
        assertEquals(1, flight.of(LapTracker.EventType.WRONG_SIDE).size());
        assertEquals(1, flight.of(LapTracker.EventType.WRONG_SIDE).getFirst().gate());
        assertEquals(1, tracker.nextGate());
        flight.to(-1.5, FLY, 12.0).to(1.5, FLY, 12.0).to(1.5, FLY, 6.0).to(1.5, FLY, 10.0);
        assertEquals(1, flight.of(LapTracker.EventType.GATE).size(), "zurück und richtig herum vorbei zählt");
        assertEquals(2, tracker.nextGate());
    }

    @Test
    void flagCountsAHalfTurnAroundItInTheRightDirection() {
        GateGeometry left = flag(0.0, 10.0, 0.0F, CourseElement.LEFT);
        List<GateGeometry> course = List.of(start(), left);
        LapTracker tracker = racing(course);
        Flight flight = new Flight(tracker, 0.0, FLY, -2.0);
        flight.to(0.0, FLY, 2.0).to(-2.0, FLY, 8.0).to(-2.0, FLY, 10.0);
        assertEquals(1, tracker.stage(1));
        flight.around(0.0, 10.0, 2.0, 180.0, 90.0);
        assertEquals(2, tracker.stage(1));
        flight.around(0.0, 10.0, 2.0, 90.0, 0.0);
        assertEquals(1, flight.of(LapTracker.EventType.GATE).size(), flight.events.toString());
        assertEquals(1, flight.of(LapTracker.EventType.GATE).getFirst().gate());
    }

    @Test
    void flagTurnedTheOtherWayDoesNotCount() {
        GateGeometry right = flag(0.0, 10.0, 0.0F, CourseElement.RIGHT);
        LapTracker tracker = racing(List.of(start(), right));
        Flight flight = new Flight(tracker, 0.0, FLY, -2.0);
        flight.to(0.0, FLY, 2.0).to(-2.0, FLY, 8.0).to(-2.0, FLY, 10.0);
        flight.around(0.0, 10.0, 2.0, 180.0, 0.0);
        assertTrue(flight.of(LapTracker.EventType.GATE).isEmpty());
        assertEquals(1, flight.of(LapTracker.EventType.WRONG_SIDE).size());
        flight.to(2.0, FLY, 6.0).to(2.0, FLY, 10.0);
        flight.around(0.0, 10.0, 2.0, 0.0, 180.0);
        assertEquals(1, flight.of(LapTracker.EventType.GATE).size(), flight.events.toString());
    }

    @Test
    void turningBackHalfwayUndoesTheFlagProgress() {
        LapTracker tracker = racing(List.of(start(), flag(0.0, 10.0, 0.0F, CourseElement.LEFT)));
        Flight flight = new Flight(tracker, 0.0, FLY, -2.0);
        flight.to(0.0, FLY, 2.0).to(-2.0, FLY, 8.0).to(-2.0, FLY, 10.0);
        flight.around(0.0, 10.0, 2.0, 180.0, 90.0);
        assertEquals(2, tracker.stage(1));
        flight.around(0.0, 10.0, 2.0, 90.0, 120.0);
        assertEquals(1, tracker.stage(1));
        flight.around(0.0, 10.0, 2.0, 120.0, 180.0).to(-2.0, FLY, 6.0);
        assertEquals(0, tracker.stage(1));
        assertTrue(flight.of(LapTracker.EventType.GATE).isEmpty());
    }

    @Test
    void cubeNeedsTheDiveAndTheConfiguredExit() {
        GateGeometry cube = cube(0.0, 10.0, 0.0F, CourseElement.EXIT_LEFT);
        LapTracker tracker = racing(List.of(start(), cube));
        Flight flight = new Flight(tracker, 0.0, FLY, -2.0);
        flight.to(0.0, FLY, 2.0).to(0.0, GROUND + 5.0, 9.0).to(0.0, GROUND + 5.0, 10.0).to(0.0, GROUND + 1.5, 10.0);
        assertEquals(1, tracker.stage(1));
        flight.to(4.0, GROUND + 1.5, 10.0);
        assertEquals(1, flight.of(LapTracker.EventType.GATE).size(), flight.events.toString());
        assertTrue(flight.of(LapTracker.EventType.WRONG_SIDE).isEmpty());
    }

    @Test
    void cubeWrongExitOrFlyingThroughWarns() {
        GateGeometry cube = cube(0.0, 10.0, 0.0F, CourseElement.EXIT_FRONT);
        LapTracker tracker = racing(List.of(start(), cube));
        Flight flight = new Flight(tracker, 0.0, FLY, -2.0);
        flight.to(0.0, FLY, 2.0).to(0.0, FLY, 14.0);
        assertTrue(flight.of(LapTracker.EventType.GATE).isEmpty(), "ohne Eintauchen zählt der Ausgang nicht");
        assertEquals(1, flight.of(LapTracker.EventType.WRONG_SIDE).size());
        flight.to(0.0, GROUND + 5.0, 14.0).to(0.0, GROUND + 5.0, 10.0).to(0.0, GROUND + 1.5, 10.0).to(-4.0, GROUND + 1.5, 10.0);
        assertTrue(flight.of(LapTracker.EventType.GATE).isEmpty());
        assertEquals(2, flight.of(LapTracker.EventType.WRONG_SIDE).size());
        assertEquals(0, tracker.stage(1));
        flight.to(-4.0, GROUND + 5.0, 10.0).to(0.0, GROUND + 5.0, 10.0).to(0.0, GROUND + 1.5, 10.0).to(0.0, GROUND + 1.5, 14.0);
        assertEquals(1, flight.of(LapTracker.EventType.GATE).size());
    }

    @Test
    void climbingBackOutOfTheTopUndoesTheDive() {
        LapTracker tracker = racing(List.of(start(), cube(0.0, 10.0, 0.0F, CourseElement.EXIT_FRONT)));
        Flight flight = new Flight(tracker, 0.0, FLY, -2.0);
        flight.to(0.0, FLY, 2.0).to(0.0, GROUND + 5.0, 10.0).to(0.0, GROUND + 1.5, 10.0);
        assertEquals(1, tracker.stage(1));
        flight.to(0.0, GROUND + 5.0, 10.0);
        assertEquals(0, tracker.stage(1));
    }

    @Test
    void cubeCollidersAreTwelveBarsAndBoundsCoverTheFrame() {
        GateGeometry cube = cube(0.5, 10.5, 0.0F, CourseElement.EXIT_FRONT);
        List<double[]> boxes = new ArrayList<>();
        cube.boxes((minX, minY, minZ, maxX, maxY, maxZ) -> boxes.add(new double[]{minX, minY, minZ, maxX, maxY, maxZ}));
        assertEquals(12, boxes.size());
        assertEquals(12, cube.boxCount(GateStacking.Base.BAR));
        double[] hull = new double[6];
        cube.bounds((minX, minY, minZ, maxX, maxY, maxZ) -> {
            hull[0] = minX;
            hull[1] = minY;
            hull[2] = minZ;
            hull[3] = maxX;
            hull[4] = maxY;
            hull[5] = maxZ;
        });
        assertEquals(0.5 - 1.75, hull[0], 1.0E-9);
        assertEquals(GROUND + 3.5, hull[4], 1.0E-9);
        assertEquals(10.5 + 1.75, hull[5], 1.0E-9);
        GateGeometry turned = GateGeometry.element(0.5, GROUND, 10.5, 0.0F, CourseElement.CUBE, 0, 3.5, 3.5, new GateTilt(30.0F, 0.0F, 0.0F));
        List<double[]> segments = new ArrayList<>();
        turned.boxes((minX, minY, minZ, maxX, maxY, maxZ) -> segments.add(new double[]{minX, minY, minZ, maxX, maxY, maxZ}));
        assertTrue(segments.size() > 12, "schräge Stäbe werden in Stücke geteilt");
    }

    @Test
    void poleAndFlagCollideOnlyWithThePostAndTheBanner() {
        List<double[]> pole = new ArrayList<>();
        pole(0.5, 0.5, 0.0F, CourseElement.LEFT).boxes((minX, minY, minZ, maxX, maxY, maxZ) -> pole.add(new double[]{minX, maxX, maxY}));
        assertEquals(1, pole.size());
        assertEquals(0.375, pole.getFirst()[0], 1.0E-9);
        assertEquals(GROUND + 3.0, pole.getFirst()[2], 1.0E-9);
        List<double[]> flag = new ArrayList<>();
        flag(0.5, 0.5, 0.0F, CourseElement.LEFT).boxes((minX, minY, minZ, maxX, maxY, maxZ) -> flag.add(new double[]{minX, maxX}));
        assertEquals(2, flag.size());
        assertTrue(flag.get(1)[0] > 0.5, "Fahne hängt bei links herum nach links (+X bei Blick nach Süden)");
        List<double[]> mirrored = new ArrayList<>();
        flag(0.5, 0.5, 0.0F, CourseElement.RIGHT).boxes((minX, minY, minZ, maxX, maxY, maxZ) -> mirrored.add(new double[]{minX, maxX}));
        assertTrue(mirrored.get(1)[1] < 0.5);
    }

    @Test
    void elementsNeverCountAsAPlainGateCrossing() {
        GateGeometry pole = pole(0.0, 0.0, 0.0F, CourseElement.LEFT);
        assertEquals(GateGeometry.NO_CROSSING, pole.crossing(1.0, FLY, -1.0, 1.0, FLY, 1.0, true));
        assertEquals(GateGeometry.NO_CROSSING, pole.crossing(0.0, FLY, -1.0, 0.0, FLY, 1.0, true));
    }

    @Test
    void checkpointsKeepPlainCoursesUnchanged() {
        List<CourseElement> gates = List.of(CourseElement.GATE, CourseElement.GATE, CourseElement.GATE);
        assertEquals(3, CourseCheckpoints.count(gates));
        for (int i = 0; i < 3; i++) {
            assertEquals(i, CourseCheckpoints.index(gates, i, 0));
        }
        List<CourseElement> mixed = List.of(CourseElement.GATE, CourseElement.FLAG, CourseElement.POLE, CourseElement.CUBE);
        assertEquals(7, CourseCheckpoints.count(mixed));
        assertEquals(0, CourseCheckpoints.index(mixed, 0, 0));
        assertEquals(List.of(1, 2, 3), List.of(CourseCheckpoints.index(mixed, 1, 0), CourseCheckpoints.index(mixed, 1, 1),
                CourseCheckpoints.index(mixed, 1, 2)));
        assertEquals(4, CourseCheckpoints.index(mixed, 2, 0));
        assertEquals(6, CourseCheckpoints.index(mixed, 3, 1));
        assertEquals(CourseCheckpoints.NONE, CourseCheckpoints.index(mixed, 2, 1));
        List<CourseElement> cubeFirst = List.of(CourseElement.CUBE, CourseElement.GATE);
        assertEquals(0, CourseCheckpoints.index(cubeFirst, 0, 1));
        assertEquals(1, CourseCheckpoints.index(cubeFirst, 1, 0));
        assertEquals(2, CourseCheckpoints.index(cubeFirst, 0, 0));
    }

    @Test
    void verifierFindsALapOverAllStages() {
        List<CourseElement> mixed = List.of(CourseElement.GATE, CourseElement.POLE, CourseElement.FLAG, CourseElement.CUBE);
        LapVerifier verifier = new LapVerifier();
        long t = 0L;
        verifier.record(3, CourseCheckpoints.index(mixed, 0, 0), t);
        verifier.record(3, CourseCheckpoints.index(mixed, 1, 0), t += 100 * MS);
        verifier.record(3, CourseCheckpoints.index(mixed, 2, 0), t += 100 * MS);
        verifier.record(3, CourseCheckpoints.index(mixed, 2, 1), t += 100 * MS);
        verifier.record(3, CourseCheckpoints.index(mixed, 3, 0), t += 100 * MS);
        verifier.record(3, CourseCheckpoints.index(mixed, 3, 1), t += 100 * MS);
        verifier.record(3, CourseCheckpoints.index(mixed, 0, 0), t += 100 * MS);
        assertEquals(null, verifier.find(3, CourseCheckpoints.count(mixed), LapVerifier.NO_SEQUENCE, Long.MIN_VALUE),
                "ohne die letzte Flaggen-Stufe keine Runde");
        verifier.record(3, CourseCheckpoints.index(mixed, 1, 0), t += 100 * MS);
        verifier.record(3, CourseCheckpoints.index(mixed, 2, 0), t += 100 * MS);
        verifier.record(3, CourseCheckpoints.index(mixed, 2, 1), t += 100 * MS);
        verifier.record(3, CourseCheckpoints.index(mixed, 2, 2), t += 100 * MS);
        verifier.record(3, CourseCheckpoints.index(mixed, 3, 0), t += 100 * MS);
        verifier.record(3, CourseCheckpoints.index(mixed, 3, 1), t += 100 * MS);
        long end = t += 100 * MS;
        verifier.record(3, CourseCheckpoints.index(mixed, 0, 0), end);
        LapVerifier.VerifiedLap lap = verifier.find(3, CourseCheckpoints.count(mixed), LapVerifier.NO_SEQUENCE, Long.MIN_VALUE);
        assertTrue(lap != null);
        assertEquals(end, lap.endNanos());
        assertEquals(600 * MS, lap.startNanos());
    }

    private static List<GateGeometry> mixedCourse() {
        return List.of(
                GateGeometry.of(0.0, GROUND, 0.0, 0.0F, GateSize.MEDIUM),
                pole(0.0, 8.0, 0.0F, CourseElement.LEFT),
                pole(0.0, 14.0, 0.0F, CourseElement.RIGHT),
                pole(0.0, 20.0, 0.0F, CourseElement.LEFT),
                flag(0.0, 30.0, 0.0F, CourseElement.LEFT),
                cube(8.0, 20.0, 180.0F, CourseElement.EXIT_FRONT),
                GateGeometry.of(8.0, GROUND, 8.0, 180.0F, GateSize.MEDIUM));
    }

    @Test
    void raceLineFliesEveryElementTheWayItCounts() {
        List<GateGeometry> course = mixedCourse();
        RaceLine.Path path = RaceLine.build(course, 0, RaceLine.MAX_AHEAD, 1 + course.size(), 0, 0.0, 66.0, -4.0);
        assertFalse(path.ahead().isEmpty());
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(1);
        tracker.setCourse(9, course, 0L);
        long now = 0L;
        List<LapTracker.Event> events = new ArrayList<>();
        for (RaceLine.Sample sample : path.ahead()) {
            now += 10 * MS;
            tracker.update(now, sample.x(), sample.y(), sample.z());
            events.addAll(tracker.drainEvents());
        }
        List<Integer> passed = events.stream().filter(e -> e.type() == LapTracker.EventType.GATE).map(LapTracker.Event::gate).toList();
        assertEquals(List.of(1, 2, 3, 4, 5, 6), passed, events.toString());
        assertTrue(events.stream().anyMatch(e -> e.type() == LapTracker.EventType.LAP), events.toString());
        assertTrue(events.stream().noneMatch(e -> e.type() == LapTracker.EventType.WRONG_SIDE || e.type() == LapTracker.EventType.MISSED),
                events.toString());
    }

    @Test
    void raceLineStaysSmoothAroundTheFlag() {
        List<GateGeometry> course = mixedCourse();
        RaceLine.Path path = RaceLine.build(course, 4, 2, 10, 4, -2.0, 66.0, 24.0);
        List<RaceLine.Sample> samples = path.ahead();
        double worst = 0.0;
        for (int i = 2; i < samples.size(); i++) {
            RaceLine.Sample a = samples.get(i - 2);
            RaceLine.Sample b = samples.get(i - 1);
            RaceLine.Sample c = samples.get(i);
            double ux = b.x() - a.x();
            double uy = b.y() - a.y();
            double uz = b.z() - a.z();
            double vx = c.x() - b.x();
            double vy = c.y() - b.y();
            double vz = c.z() - b.z();
            double lu = Math.sqrt(ux * ux + uy * uy + uz * uz);
            double lv = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (lu < 1.0E-6 || lv < 1.0E-6) {
                continue;
            }
            double cos = (ux * vx + uy * vy + uz * vz) / (lu * lv);
            worst = Math.max(worst, Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos)))));
        }
        assertTrue(worst < 25.0, "Knick " + worst);
        float last = Float.NaN;
        for (RaceLine.Sample sample : samples) {
            if (sample.gate() <= 0.0) {
                last = sample.highlight();
            }
        }
        assertEquals(1.0F, last, "bis die Flagge umrundet ist, bleibt die Linie grün");
    }

    @Test
    void ghostSplitsFollowTheStages() {
        List<GateGeometry> course = List.of(start(), pole(0.0, 8.0, 0.0F, CourseElement.LEFT), cube(0.0, 16.0, 0.0F, CourseElement.EXIT_FRONT));
        List<double[]> points = new ArrayList<>();
        double[][] legs = {{0.0, FLY, -2.0}, {0.0, FLY, 2.0}, {1.5, FLY, 8.0}, {0.0, GROUND + 5.0, 13.0}, {0.0, GROUND + 5.0, 16.0},
                {0.0, GROUND + 1.5, 16.0}, {0.0, GROUND + 1.5, 20.0}, {-6.0, FLY, 10.0}, {0.0, FLY, -4.0}, {0.0, FLY, 0.5}};
        for (int leg = 1; leg < legs.length; leg++) {
            for (int i = 0; i < 20; i++) {
                double f = i / 20.0;
                points.add(new double[]{legs[leg - 1][0] + (legs[leg][0] - legs[leg - 1][0]) * f, legs[leg - 1][1] + (legs[leg][1] - legs[leg - 1][1]) * f,
                        legs[leg - 1][2] + (legs[leg][2] - legs[leg - 1][2]) * f});
            }
        }
        points.add(legs[legs.length - 1]);
        double[] positions = new double[points.size() * 3];
        float[] rotations = new float[points.size() * 4];
        for (int i = 0; i < points.size(); i++) {
            System.arraycopy(points.get(i), 0, positions, i * 3, 3);
            rotations[i * 4 + 3] = 1.0F;
        }
        GhostTrack track = new GhostTrack(GhostTrack.INTERVAL_NANOS, positions, rotations, (points.size() - 1) * GhostTrack.INTERVAL_NANOS,
                "race_5", 0);
        long[] splits = track.splits(course);
        assertTrue(GhostTrack.completeSplits(splits), java.util.Arrays.toString(splits));
        assertTrue(splits[1] < splits[2]);
    }
}
