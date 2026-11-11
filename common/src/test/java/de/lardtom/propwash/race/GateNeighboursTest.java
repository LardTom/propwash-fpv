package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateNeighboursTest {
    private static final double EPS = 1.0E-9;

    private static GateGeometry gate(double x, double z, float yaw) {
        return GateGeometry.of(x, 64.0, z, yaw, GateSize.MEDIUM);
    }

    private static List<double[]> boxes(GateGeometry gate) {
        List<double[]> out = new ArrayList<>();
        gate.boxes((x0, y0, z0, x1, y1, z1) -> out.add(new double[]{x0, y0, z0, x1, y1, z1}), GateStacking.Base.BAR);
        return out;
    }

    private static boolean overlaps(List<double[]> a, List<double[]> b) {
        for (double[] p : a) {
            for (double[] q : b) {
                if (p[0] < q[3] - 0.01 && p[3] > q[0] + 0.01 && p[1] < q[4] - 0.01 && p[4] > q[1] + 0.01 && p[2] < q[5] - 0.01 && p[5] > q[2] + 0.01) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void gateNextToAGateSnapsPostOnPost() {
        GateGeometry left = gate(0.5, 0.5, 0.0F);
        GateGeometry touching = gate(4.5, 0.5, 0.0F);
        GateGeometry snapped = GateStacking.snap(touching, List.of(left));
        assertEquals(0.5 + 3.5, snapped.x(), EPS, "Pfostenmitte auf Pfostenmitte");
        assertEquals(0.5, snapped.z(), EPS);
        assertEquals(1, GateStacking.sharedSide(left, snapped));
        assertEquals(-1, GateStacking.sharedSide(snapped, left));
        GateGeometry overlapping = gate(3.5, 0.5, 0.0F);
        assertEquals(0.5 + 3.5, GateStacking.snap(overlapping, List.of(left)).x(), EPS, "auch aus der Überlappung");
        GateGeometry deep = gate(2.5, 0.5, 0.0F);
        assertSame(deep, GateStacking.snap(deep, List.of(left)), "mehr als einen Block hinein rastet nicht");
        GateGeometry far = gate(6.5, 0.5, 0.0F);
        assertSame(far, GateStacking.snap(far, List.of(left)), "mit Abstand bleibt das Gate, wo es ist");
        GateGeometry on = gate(0.5, 0.5, 180.0F);
        assertSame(on, GateStacking.snap(on, List.of(left)), "nie auf das Gate selbst");
        GateGeometry higher = GateGeometry.of(4.5, 65.0, 0.5, 0.0F, GateSize.MEDIUM);
        assertSame(higher, GateStacking.snap(higher, List.of(left)), "nur gleich hoch stehende Gates");
        GateGeometry small = GateGeometry.of(4.0, 64.0, 0.5, 0.0F, GateSize.SMALL);
        assertSame(small, GateStacking.snap(small, List.of(left)), "nur gleich hohe Gates");
    }

    @Test
    void sharedPostIsKeptByExactlyOneGate() {
        GateGeometry left = gate(0.5, 0.5, 0.0F);
        GateGeometry right = gate(4.0, 0.5, 180.0F);
        GateStacking.Joints leftJoints = GateStacking.joints(left, List.of(right));
        GateStacking.Joints rightJoints = GateStacking.joints(right, List.of(left));
        assertTrue(leftJoints.linked());
        assertTrue(rightJoints.linked());
        assertEquals(0, leftJoints.mask(), "das westliche Gate behält den Pfosten");
        assertEquals(1, Integer.bitCount(rightJoints.mask()));
        GateGeometry trimmed = right.withJoints(rightJoints.mask());
        assertEquals(right.boxCount(GateStacking.Base.BAR) - 1, trimmed.boxCount(GateStacking.Base.BAR));
        assertEquals(trimmed.boxCount(GateStacking.Base.BAR), boxes(trimmed).size());
        assertFalse(overlaps(boxes(left), boxes(trimmed)), "geteilter Pfosten kollidiert einmal");
        assertTrue(overlaps(boxes(left), boxes(right)), "ohne Teilen lägen zwei Pfosten ineinander");
        assertTrue(trimmed.crossing(4.0, 65.5, 1.5, 4.0, 65.5, -0.5, true) >= 0.0, "Öffnung reicht bis an den geteilten Pfosten");
        double inner = trimmed.innerHalfWidth();
        assertTrue(trimmed.crossing(4.0 + inner - 0.05, 65.5, 1.5, 4.0 + inner - 0.05, 65.5, -0.5, true) >= 0.0);
        assertEquals(GateStacking.Joints.NONE, GateStacking.joints(left, List.of()), "ohne Nachbarn wieder eigener Pfosten");
        assertEquals(GateGeometry.JOINT_RIGHT, GateStacking.placementJoints(left, List.of(right)));
    }

    private static List<GateGeometry> ring() {
        GateGeometry south = gate(0.5, 0.5, 0.0F);
        GateGeometry east = GateStacking.snap(gate(2.5, 2.5, 90.0F), List.of(south));
        GateGeometry west = GateStacking.snap(gate(-1.5, 2.5, 90.0F), List.of(south));
        GateGeometry north = GateStacking.snap(gate(0.5, 4.5, 0.0F), List.of(east, west));
        return List.of(south, east, west, north);
    }

    @Test
    void fourGatesShareTheirCornersAsARing() {
        List<GateGeometry> ring = ring();
        GateGeometry south = ring.get(0);
        GateGeometry east = ring.get(1);
        GateGeometry west = ring.get(2);
        GateGeometry north = ring.get(3);
        assertEquals(2.25, east.x(), EPS);
        assertEquals(2.25, east.z(), EPS);
        assertEquals(-1.25, west.x(), EPS);
        assertEquals(4.0, north.z(), EPS);
        assertEquals(0.5, north.x(), EPS);
        int dropped = 0;
        for (GateGeometry gate : ring) {
            List<GateGeometry> others = new ArrayList<>(ring);
            others.remove(gate);
            GateStacking.Joints joints = GateStacking.joints(gate, others);
            assertTrue(joints.linked());
            dropped += Integer.bitCount(joints.mask());
        }
        assertEquals(4, dropped, "vier Ecken, je ein Pfosten");
        assertTrue(GateStacking.sharedSide(north, east) != 0 && GateStacking.sharedSide(north, west) != 0);
        assertTrue(GateStacking.sharedSide(south, north) == 0);
    }

    @Test
    void lidOnTheRingMakesADiveBox() {
        List<GateGeometry> ring = ring();
        GateGeometry south = ring.get(0);
        GateStacking.Lid lid = GateStacking.lid(south, ring.subList(1, 4), GateSize.MEDIUM);
        assertEquals(GateStacking.LidRefusal.NONE, lid.refusal());
        GateGeometry top = lid.geometry();
        assertEquals(GateMount.FLOOR_FLAT, top.mount());
        assertEquals(64.0 + 4.0 - 0.25, top.y(), EPS);
        assertEquals(0.5, top.centerX(), EPS);
        assertEquals(2.25, top.centerZ(), EPS);
        assertTrue(top.normalY() < 0.0, "Deckel wird von oben nach unten durchflogen");
        assertTrue(top.crossing(0.5, 69.0, 2.25, 0.5, 66.0, 2.25, true) >= 0.0);
        for (GateGeometry side : ring) {
            assertTrue(GateStacking.covers(top, side), "Deckel liegt auf " + side);
            List<GateGeometry> others = new ArrayList<>(ring);
            others.remove(side);
            others.add(top);
            assertTrue((GateStacking.joints(side, others).mask() & GateGeometry.JOINT_TOP) != 0);
            GateGeometry trimmed = side.withJoints(GateGeometry.JOINT_TOP);
            List<double[]> lidBoxes = new ArrayList<>();
            top.boxes((x0, y0, z0, x1, y1, z1) -> lidBoxes.add(new double[]{x0, y0, z0, x1, y1, z1}), GateStacking.placedBase(top));
            assertFalse(overlaps(boxes(trimmed), lidBoxes), "Balken und Pfostenenden kollidieren nur einmal");
            for (double[] box : boxes(side)) {
                if (overlaps(List.of(box), lidBoxes)) {
                    assertTrue(GateStacking.underLid(box[1], box[4], top), "nur Teile unter dem Deckel überlappen");
                }
            }
        }
        assertTrue(GateStacking.joints(top, ring).linked());
        assertEquals(GateStacking.LidRefusal.NO_RING, GateStacking.lid(south, List.of(), GateSize.MEDIUM).refusal());
    }

    @Test
    void flagsAndPolesSitOnGatesAndCubes() {
        GateGeometry gate = gate(0.5, 0.5, 0.0F);
        List<GatePoles.Seat> seats = GatePoles.seats(gate);
        assertEquals(3, seats.size());
        GatePoles.Seat middle = seats.get(2);
        assertEquals(68.0, middle.y(), EPS);
        GateGeometry flag = GatePoles.elementOnSeat(middle, CourseElement.FLAG, CourseElement.LEFT, 90.0F, 0.0F);
        assertTrue(GatePoles.seated(flag));
        assertTrue(GatePoles.restsOn(flag, gate));
        assertEquals(68.0, flag.y(), EPS);
        assertEquals(3, flag.stages().size(), "Flagge auf dem Gate wertet wie am Boden");
        GateGeometry slalom = GatePoles.elementOnSeat(seats.getFirst(), CourseElement.POLE, CourseElement.RIGHT, 0.0F, 0.0F);
        assertTrue(GatePoles.restsOn(slalom, gate));
        assertFalse(GatePoles.restsOn(slalom.at(slalom.x() + 0.3, slalom.y(), slalom.z()), gate));
        GateGeometry cube = GateGeometry.element(10.0, 64.0, 10.0, 0.0F, CourseElement.CUBE, 0, 4.5, 4.5, GateTilt.NONE);
        assertTrue(GatePoles.carriesPoles(cube));
        List<GatePoles.Seat> cubeSeats = GatePoles.seats(cube);
        assertEquals(8, cubeSeats.size(), "vier Ecken und vier Kanten");
        for (GatePoles.Seat seat : cubeSeats) {
            assertEquals(68.5, seat.y(), EPS);
            GateGeometry pole = GatePoles.elementOnSeat(seat, CourseElement.POLE, 0, 0.0F, 0.0F);
            assertTrue(GatePoles.restsOn(pole, cube));
        }
        assertFalse(GatePoles.carriesPoles(flag), "auf Flaggen kommt nichts");
        assertTrue(GatePoles.seats(flag).isEmpty());
        GateGeometry onFlag = GatePoles.onSeat(middle, 0, 4.0);
        assertTrue(GatePoles.restsOn(onFlag, gate), "Gate-Stange auch in der Mitte");
    }

    @Test
    void flagOnAGateScoresThroughTheGateWhenAskedTo() {
        GateGeometry gate = gate(0.5, 0.5, 0.0F);
        GatePoles.Seat middle = GatePoles.seats(gate).get(2);
        GateGeometry flag = GatePoles.elementOnSeat(middle, CourseElement.POLE, CourseElement.LEFT, 0.0F, 0.0F);
        GateGeometry deep = flag.withScore(ScoreArea.DEFAULT.withBelow(4.0));
        Passage stage = deep.stages().getFirst();
        double side = 1.0;
        double px = stage.ox() + stage.sx() * side;
        double pz = stage.oz() + stage.sz() * side;
        assertTrue(stage.crossing(px - stage.nx(), 65.0, pz - stage.nz(), px + stage.nx(), 65.0, pz + stage.nz(), true) >= 0.0);
        Passage shallow = flag.stages().getFirst();
        assertTrue(shallow.crossing(px - stage.nx(), 65.0, pz - stage.nz(), px + stage.nx(), 65.0, pz + stage.nz(), true) < 0.0);
    }

    @Test
    void jointsSurviveMovingButNotCourseData() {
        GateGeometry gate = gate(0.5, 0.5, 0.0F).withJoints(GateGeometry.JOINT_LEFT);
        assertEquals(GateGeometry.JOINT_LEFT, gate.moved(0.0, 1.0, 0.0).joints());
        assertEquals(0, GateGeometry.element(0.0, 64.0, 0.0, 0.0F, CourseElement.FLAG, 0, 5.0, 4.0, GateTilt.NONE).withJoints(7).joints());
        CourseData.Gate stored = new CourseData.Gate(0, 0.5, 64.0, 0.5, 0.0F, GateSize.MEDIUM);
        assertEquals(0, stored.geometry().joints());
        assertNotNull(GateStacking.snap(gate, List.of()));
    }
}
