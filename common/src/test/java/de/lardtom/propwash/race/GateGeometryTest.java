package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateGeometryTest {
    private static final double EPS = 1.0E-9;

    @Test
    void forwardCrossingThroughOpeningIsDetected() {
        GateGeometry gate = GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM);
        double t = gate.crossing(0.2, 65.5, -1.0, 0.2, 65.5, 3.0, true);
        assertEquals(0.25, t, EPS);
    }

    @Test
    void backwardCrossingIsNotForward() {
        GateGeometry gate = GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM);
        assertEquals(GateGeometry.NO_CROSSING, gate.crossing(0.0, 65.5, 1.0, 0.0, 65.5, -1.0, true));
        assertEquals(0.5, gate.crossing(0.0, 65.5, 1.0, 0.0, 65.5, -1.0, false), EPS);
    }

    @Test
    void crossingThroughPostOrAboveIsRejected() {
        GateGeometry gate = GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM);
        assertEquals(GateGeometry.NO_CROSSING, gate.crossing(1.75, 65.5, -1.0, 1.75, 65.5, 1.0, true));
        assertEquals(GateGeometry.NO_CROSSING, gate.crossing(0.0, 67.8, -1.0, 0.0, 67.8, 1.0, true));
        assertEquals(GateGeometry.NO_CROSSING, gate.crossing(0.0, 64.1, -1.0, 0.0, 64.1, 1.0, true));
        assertEquals(GateGeometry.NO_CROSSING, gate.crossing(5.0, 65.5, -1.0, 5.0, 65.5, 1.0, true));
    }

    @Test
    void rotatedGateUsesItsFacing() {
        GateGeometry gate = GateGeometry.of(10.0, 0.0, 10.0, 270.0F, GateSize.SMALL);
        assertEquals(1.0, gate.normalX(), EPS);
        assertEquals(0.0, gate.normalZ(), EPS);
        assertTrue(gate.crossing(9.0, 1.0, 10.0, 11.0, 1.0, 10.0, true) >= 0.0);
        assertEquals(GateGeometry.NO_CROSSING, gate.crossing(10.0, 1.0, 9.0, 10.0, 1.0, 11.0, true));
    }

    @Test
    void diagonalSegmentThroughCornerOfOpening() {
        GateGeometry gate = GateGeometry.of(0.0, 0.0, 0.0, 0.0F, GateSize.LARGE);
        double t = gate.crossing(-2.0, 0.5, -2.0, 2.0, 3.5, 2.0, true);
        assertEquals(0.5, t, EPS);
    }

    @Test
    void snapYawToFourDirections() {
        assertEquals(0.0F, GateGeometry.snapYaw(44.0F));
        assertEquals(90.0F, GateGeometry.snapYaw(46.0F));
        assertEquals(270.0F, GateGeometry.snapYaw(-90.0F));
        assertEquals(0.0F, GateGeometry.snapYaw(359.0F));
        assertEquals(180.0F, GateGeometry.snapYaw(-181.0F));
    }

    @Test
    void axisAlignedBoxesCoverPostsAndBarsButNotOpening() {
        GateGeometry gate = GateGeometry.of(0.0, 0.0, 0.0, 0.0F, GateSize.MEDIUM);
        List<double[]> boxes = collect(gate);
        assertEquals(4, boxes.size());
        double[] left = boxes.get(0);
        assertEquals(-2.0, left[0], EPS);
        assertEquals(-1.5, left[3], EPS);
        assertEquals(-0.25, left[2], EPS);
        assertEquals(0.25, left[5], EPS);
        assertEquals(4.0, left[4], EPS);
        assertTrue(contains(boxes, -1.75, 1.5, 0.0));
        assertTrue(contains(boxes, 1.75, 1.5, 0.0));
        assertTrue(contains(boxes, 0.0, 0.1, 0.0));
        assertTrue(contains(boxes, 0.0, 3.8, 0.0));
        assertFalse(contains(boxes, 0.0, 1.5, 0.0));
        assertFalse(contains(boxes, 1.2, 0.6, 0.0));
        assertFalse(contains(boxes, 0.0, 1.5, 0.3));
    }

    @Test
    void quarterTurnSwapsAxes() {
        GateGeometry gate = GateGeometry.of(5.0, 10.0, 5.0, 90.0F, GateSize.SMALL);
        List<double[]> boxes = collect(gate);
        assertEquals(4, boxes.size());
        assertTrue(contains(boxes, 5.0, 11.0, 5.0 - 1.25));
        assertFalse(contains(boxes, 5.0, 11.0, 5.0));
        assertEquals(GateSize.SMALL.depth() * 0.5, gate.halfExtentX(), EPS);
        assertEquals(1.5, gate.halfExtentZ(), EPS);
    }

    @Test
    void unalignedGateIsApproximatedWithSegments() {
        GateGeometry gate = GateGeometry.of(0.0, 0.0, 0.0, 45.0F, GateSize.MEDIUM);
        List<double[]> boxes = collect(gate);
        assertTrue(boxes.size() > 4);
        assertFalse(contains(boxes, 0.0, 1.5, 0.0));
    }

    @Test
    void pathLengthIsClosedLoop() {
        List<GateGeometry> gates = List.of(
                GateGeometry.of(0.0, 0.0, 0.0, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(0.0, 0.0, 10.0, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(10.0, 0.0, 10.0, 0.0F, GateSize.MEDIUM));
        assertEquals(10.0 + 10.0 + Math.sqrt(200.0), GateGeometry.pathLength(gates), 1.0E-9);
    }

    private static List<double[]> collect(GateGeometry gate) {
        List<double[]> boxes = new ArrayList<>();
        gate.boxes((minX, minY, minZ, maxX, maxY, maxZ) -> boxes.add(new double[]{minX, minY, minZ, maxX, maxY, maxZ}));
        return boxes;
    }

    private static boolean contains(List<double[]> boxes, double x, double y, double z) {
        for (double[] b : boxes) {
            if (x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5]) {
                return true;
            }
        }
        return false;
    }
}
