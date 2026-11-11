package de.lardtom.propwash.race;

import de.lardtom.propwash.race.client.GateEditModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateNudgeTest {
    @Test
    void movedKeepsEverythingButThePosition() {
        GateGeometry gate = GateGeometry.of(0.5, 64.0, 0.5, 30.0F, GateSize.MEDIUM, GateMount.STANDING, false, 5.0, 4.5,
                new GateTilt(30.0F, 12.0F, -8.0F)).withLegs(true);
        GateGeometry moved = gate.moved(0.0625, -0.25, 1.0);
        assertEquals(0.5625, moved.x(), 1.0E-12);
        assertEquals(63.75, moved.y(), 1.0E-12);
        assertEquals(1.5, moved.z(), 1.0E-12);
        assertEquals(gate.tilt(), moved.tilt());
        assertEquals(gate.width(), moved.width());
        assertTrue(moved.legs());
        assertSame(gate, gate.moved(0.0, 0.0, 0.0));
        GateGeometry flag = GateGeometry.element(3.0, 64.0, 3.0, 0.0F, CourseElement.FLAG, CourseElement.RIGHT, 5.0, 4.0, GateTilt.NONE,
                GateMount.STANDING, ScoreArea.DEFAULT.withBelow(4.0));
        GateGeometry nudged = flag.moved(0.25, 2.0, 0.0);
        assertEquals(flag.score(), nudged.score());
        assertEquals(CourseElement.RIGHT, nudged.variant());
        assertEquals(-4.0, nudged.stages().getFirst().up0());
        assertEquals(66.0, nudged.stages().getFirst().oy(), 1.0E-12);
    }

    @Test
    void editingAfterANudgeKeepsTheOffGridPosition() {
        GateGeometry gate = GateGeometry.of(0.5, 64.0, 0.5, 0.0F, GateSize.MEDIUM).moved(0.0625, 0.0, -0.3125);
        GateGeometry turned = GatePlacement.edited(gate, 90.0F, 0.0F, 0.0F, gate.width(), gate.height());
        assertEquals(0.5625, turned.x(), 1.0E-9);
        assertEquals(0.1875, turned.z(), 1.0E-9);
    }

    @Test
    void arrowKeysFollowTheViewSnappedToWorldAxes() {
        assertArrayEquals(new int[]{GateEditModel.AXIS_Z, 1}, GateEditModel.viewNudge(0.0F, GateEditModel.Nudge.AWAY), "Blick nach Süden");
        assertArrayEquals(new int[]{GateEditModel.AXIS_X, -1}, GateEditModel.viewNudge(0.0F, GateEditModel.Nudge.RIGHT), "rechts ist Westen");
        assertArrayEquals(new int[]{GateEditModel.AXIS_X, 1}, GateEditModel.viewNudge(-90.0F, GateEditModel.Nudge.AWAY), "Blick nach Osten");
        assertArrayEquals(new int[]{GateEditModel.AXIS_Z, 1}, GateEditModel.viewNudge(-90.0F, GateEditModel.Nudge.RIGHT), "rechts ist Süden");
        assertArrayEquals(new int[]{GateEditModel.AXIS_Z, -1}, GateEditModel.viewNudge(170.0F, GateEditModel.Nudge.AWAY), "Blick nach Norden");
        assertArrayEquals(new int[]{GateEditModel.AXIS_X, -1}, GateEditModel.viewNudge(80.0F, GateEditModel.Nudge.AWAY), "fast West");
        assertArrayEquals(new int[]{GateEditModel.AXIS_X, 1}, GateEditModel.viewNudge(80.0F, GateEditModel.Nudge.TOWARD));
        assertArrayEquals(new int[]{GateEditModel.AXIS_Z, 1}, GateEditModel.viewNudge(80.0F, GateEditModel.Nudge.LEFT));
        assertArrayEquals(new int[]{GateEditModel.AXIS_Y, 1}, GateEditModel.viewNudge(33.0F, GateEditModel.Nudge.UP));
        assertArrayEquals(new int[]{GateEditModel.AXIS_Y, -1}, GateEditModel.viewNudge(33.0F, GateEditModel.Nudge.DOWN));
    }
}
