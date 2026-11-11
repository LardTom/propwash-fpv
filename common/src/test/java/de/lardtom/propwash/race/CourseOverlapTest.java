package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseOverlapTest {
    private static final double Y = 64.0;
    private static final long MS = 1_000_000L;
    private static final int COURSE = 21;
    private static final GateGeometry START = GateGeometry.of(0.0, Y, 0.0, 0.0F, GateSize.MEDIUM);
    private static final GateGeometry OUTER = GateGeometry.of(0.0, Y, 14.0, 0.0F, GateSize.LARGE);
    private static final GateGeometry INNER = GateGeometry.of(0.0, Y, 14.0, 0.0F, GateSize.MEDIUM);
    private static final double BETWEEN = 2.25;

    private static final class Flight {
        final LapTracker tracker = new LapTracker();
        final List<LapTracker.Event> events = new ArrayList<>();
        long now = 1_000L * MS;
        double x;
        double y;
        double z;

        Flight(List<GateGeometry> gates, double x, double y, double z) {
            this.tracker.setCourse(COURSE, gates, this.now);
            this.tracker.setLapTarget(0);
            this.x = x;
            this.y = y;
            this.z = z;
            this.tracker.update(this.now, x, y, z);
        }

        void to(double tx, double tz) {
            double dx = tx - this.x;
            double dz = tz - this.z;
            int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) / 0.3));
            double sx = this.x;
            double sz = this.z;
            for (int i = 1; i <= steps; i++) {
                this.now += 20 * MS;
                this.x = sx + dx * i / steps;
                this.z = sz + dz * i / steps;
                this.tracker.update(this.now, this.x, this.y, this.z);
                this.events.addAll(this.tracker.drainEvents());
            }
        }

        List<Integer> of(LapTracker.EventType type) {
            return this.events.stream().filter(e -> e.type() == type).map(LapTracker.Event::gate).toList();
        }
    }

    private static Flight lap(double throughX) {
        Flight flight = new Flight(List.of(START, OUTER, INNER), 0.0, START.centerY(), -3.0);
        flight.to(0.0, 3.0);
        flight.to(throughX, 8.0);
        flight.to(throughX, 20.0);
        flight.to(8.0, 20.0);
        flight.to(8.0, -4.0);
        flight.to(0.0, -4.0);
        flight.to(0.0, 3.0);
        return flight;
    }

    @Test
    void gateInsideAGateScoresBothThroughTheInnerOpening() {
        assertEquals(OUTER.centerY(), INNER.centerY());
        Flight flight = lap(0.0);
        assertEquals(List.of(1, 2), flight.of(LapTracker.EventType.GATE), flight.events.toString());
        assertEquals(1, flight.of(LapTracker.EventType.LAP).size(), flight.events.toString());
    }

    @Test
    void passingOnlyTheOuterGateDoesNotScoreTheInnerOne() {
        Flight flight = lap(BETWEEN);
        assertEquals(List.of(1), flight.of(LapTracker.EventType.GATE), flight.events.toString());
        assertTrue(flight.of(LapTracker.EventType.LAP).isEmpty(), flight.events.toString());
    }

    @Test
    void identicalGatesOnTopOfEachOtherCountLikeOneGateFlownTwice() {
        Flight flight = new Flight(List.of(START, INNER, INNER), 0.0, START.centerY(), -3.0);
        flight.to(0.0, 3.0);
        flight.to(0.0, 20.0);
        assertEquals(List.of(1), flight.of(LapTracker.EventType.GATE), flight.events.toString());
        flight.to(8.0, 20.0);
        flight.to(8.0, 8.0);
        flight.to(0.0, 8.0);
        flight.to(0.0, 20.0);
        flight.to(8.0, 20.0);
        flight.to(8.0, -4.0);
        flight.to(0.0, -4.0);
        flight.to(0.0, 3.0);
        assertEquals(List.of(1, 2), flight.of(LapTracker.EventType.GATE), flight.events.toString());
        assertEquals(1, flight.of(LapTracker.EventType.LAP).size(), flight.events.toString());
    }

    private static List<double[]> boxes(GateGeometry gate) {
        List<double[]> out = new ArrayList<>();
        gate.boxes((minX, minY, minZ, maxX, maxY, maxZ) -> out.add(new double[]{minX, minY, minZ, maxX, maxY, maxZ}));
        return out;
    }

    private static boolean hits(List<double[]> boxes, double x, double y, double z) {
        for (double[] box : boxes) {
            if (x > box[0] && x < box[3] && y > box[1] && y < box[4] && z > box[2] && z < box[5]) {
                return true;
            }
        }
        return false;
    }

    @Test
    void overlappingGatesKeepTheirOwnColliders() {
        List<double[]> outer = boxes(OUTER);
        List<double[]> inner = boxes(INNER);
        double y = INNER.centerY();
        double z = INNER.z();
        assertFalse(hits(outer, 0.0, y, z));
        assertFalse(hits(inner, 0.0, y, z));
        assertFalse(hits(outer, BETWEEN, y, z));
        assertFalse(hits(inner, BETWEEN, y, z));
        assertTrue(hits(inner, 1.75, y, z));
        assertFalse(hits(outer, 1.75, y, z));
        assertTrue(hits(outer, 2.75, y, z));
        assertFalse(hits(inner, 2.75, y, z));
        assertEquals(boxes(GateGeometry.of(0.0, Y, 14.0, 0.0F, GateSize.MEDIUM)).size(), inner.size());
    }
}
