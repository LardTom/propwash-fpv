package de.lardtom.propwash.interact.client;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WallContactTest {
    private static final double FRONT = 7.0 / 16.0;
    private static final double BACK = 9.0 / 16.0;
    private static final double WHOOP_WIDTH = 0.083;
    private static final double WHOOP_HEIGHT = 0.024;
    private static final AABB PANE = new AABB(FRONT, 0.0, -1.5625, BACK, 4.0, 2.5625);
    private static final AABB FLOOR = new AABB(-3.0, -1.0, -6.0, 3.0, 0.0, 6.0);
    private static final List<AABB> WALL = List.of(PANE, FLOOR);

    private static AABB whoop(double maxX, double minY, double centerZ) {
        return new AABB(maxX - WHOOP_WIDTH, minY, centerZ - WHOOP_WIDTH / 2.0, maxX, minY + WHOOP_HEIGHT, centerZ + WHOOP_WIDTH / 2.0);
    }

    @Test
    void besideTheEndOfTheWallTheWhoopIsNotInTheGlass() {
        AABB beside = whoop(FRONT + 0.04, 0.0, -2.42);
        assertTrue(beside.maxX > FRONT + WallContact.STUCK, "die alte Prüfung meldete hier „steckt im Block (x=0.04)“");
        assertEquals(0.0, WallContact.depth(beside, WALL), 1.0E-12);
    }

    @Test
    void restingOnTheFloorAgainstThePaneIsNoOverlap() {
        assertEquals(0.0, WallContact.depth(whoop(FRONT, 0.0, 0.5), WALL), 1.0E-12);
        assertEquals(0.0, WallContact.depth(whoop(FRONT - 0.2, 0.0, 0.5), WALL), 1.0E-12);
    }

    @Test
    void aWhoopReallyInsideThePaneIsReported() {
        double depth = WallContact.depth(whoop(FRONT + 0.04, 0.0, 0.5), WALL);
        assertEquals(WHOOP_HEIGHT, depth, 1.0E-9);
        assertTrue(depth > WallContact.STUCK);
        assertTrue(WallContact.depth(whoop(FRONT + 0.03, 1.0, 0.5), WALL) > WallContact.STUCK);
        assertFalse(WallContact.depth(whoop(FRONT + 0.015, 1.0, 0.5), WALL) > WallContact.STUCK, "1,5 cm liegen in der alten Toleranz");
    }

    @Test
    void serverToleranceIntoTheFloorIsNoOverlap() {
        AABB fivePlus = new AABB(-0.145, -0.02, 0.355, 0.145, 0.046, 0.645);
        assertEquals(0.02, WallContact.depth(fivePlus, WALL), 1.0E-9);
        assertFalse(WallContact.depth(fivePlus, WALL) > WallContact.STUCK);
    }

    @Test
    void onlyOutwardDriftBeyondTheLimitIsStopped() {
        assertTrue(WallContact.drifting(-1.2, -0.5, 1.0));
        assertTrue(WallContact.drifting(1.2, 0.5, 1.0));
        assertFalse(WallContact.drifting(-1.2, 0.5, 1.0), "zurück zur Mitte bleibt frei");
        assertFalse(WallContact.drifting(0.8, 0.5, 1.0));
        assertFalse(WallContact.drifting(1.2, 0.0, 1.0));
    }
}
