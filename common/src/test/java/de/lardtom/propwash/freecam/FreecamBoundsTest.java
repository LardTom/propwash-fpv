package de.lardtom.propwash.freecam;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreecamBoundsTest {
    private static final int RADIUS = 2;
    private static final double MIN_Y = -64.0;
    private static final double TOP_Y = 320.0;

    private static FreecamBounds square() {
        return new FreecamBounds((cx, cz) -> Math.abs(cx) <= RADIUS && Math.abs(cz) <= RADIUS, MIN_Y, TOP_Y, 8.0, 8.0);
    }

    @Test
    void chunkCoordinatesFloorNegativeValues() {
        assertEquals(0, FreecamBounds.chunk(0.0));
        assertEquals(0, FreecamBounds.chunk(15.99));
        assertEquals(1, FreecamBounds.chunk(16.0));
        assertEquals(-1, FreecamBounds.chunk(-0.01));
        assertEquals(-1, FreecamBounds.chunk(-16.0));
        assertEquals(-2, FreecamBounds.chunk(-16.01));
    }

    @Test
    void heightBandIsFloorMinusSixteenToBuildTopPlusSixtyFour() {
        FreecamBounds bounds = square();
        assertEquals(-80.0, bounds.minY(), 0.0);
        assertEquals(384.0, bounds.maxY(), 0.0);
        assertTrue(bounds.heightAllowed(-80.0));
        assertTrue(bounds.heightAllowed(384.0));
        assertFalse(bounds.heightAllowed(-80.01));
        assertFalse(bounds.heightAllowed(384.01));
    }

    @Test
    void freeMoveInsideTheLoadedAreaIsUnchanged() {
        FreecamBounds.Step step = square().step(8.0, 70.0, 8.0, 9.5, 71.0, 7.0, new FreecamBounds.Step());
        assertFalse(step.edge());
        assertEquals(9.5, step.x, 0.0);
        assertEquals(71.0, step.y, 0.0);
        assertEquals(7.0, step.z, 0.0);
    }

    @Test
    void edgeStopsOnlyTheBlockedAxisSoTheCameraSlidesAlong() {
        double edge = (RADIUS + 1) * 16.0;
        FreecamBounds.Step step = square().step(edge - 0.1, 70.0, 5.0, edge + 0.4, 70.5, 6.0, new FreecamBounds.Step());
        assertTrue(step.blockedX);
        assertFalse(step.blockedY);
        assertFalse(step.blockedZ);
        assertEquals(edge - 0.1, step.x, 0.0);
        assertEquals(70.5, step.y, 0.0);
        assertEquals(6.0, step.z, 0.0);
    }

    @Test
    void cornerBlocksBothHorizontalAxes() {
        double edge = (RADIUS + 1) * 16.0;
        FreecamBounds.Step step = square().step(edge - 0.1, 70.0, edge - 0.1, edge + 0.1, 70.0, edge + 0.1, new FreecamBounds.Step());
        assertTrue(step.blockedX);
        assertTrue(step.blockedZ);
        assertEquals(edge - 0.1, step.x, 0.0);
        assertEquals(edge - 0.1, step.z, 0.0);
    }

    @Test
    void negativeEdgeUsesTheLoadedColumnsToo() {
        double edge = -RADIUS * 16.0;
        FreecamBounds.Step step = square().step(edge + 0.05, 70.0, 0.0, edge - 0.05, 70.0, 0.0, new FreecamBounds.Step());
        assertTrue(step.blockedX);
        assertEquals(edge + 0.05, step.x, 0.0);
    }

    @Test
    void zAxisIsCheckedInTheColumnAfterTheXMove() {
        FreecamBounds bounds = new FreecamBounds((cx, cz) -> cx == 0 || cz == 0, MIN_Y, TOP_Y, 0.0, 0.0);
        FreecamBounds.Step step = bounds.step(15.5, 70.0, 0.5, 16.5, 70.0, 16.5, new FreecamBounds.Step());
        assertFalse(step.blockedX);
        assertTrue(step.blockedZ);
        assertEquals(16.5, step.x, 0.0);
        assertEquals(0.5, step.z, 0.0);
    }

    @Test
    void verticalLimitsHoldButAllowTheWayBack() {
        FreecamBounds bounds = square();
        FreecamBounds.Step down = bounds.step(8.0, -79.9, 8.0, 8.0, -80.5, 8.0, new FreecamBounds.Step());
        assertTrue(down.blockedY);
        assertEquals(-79.9, down.y, 0.0);
        FreecamBounds.Step up = bounds.step(8.0, 383.9, 8.0, 8.0, 384.5, 8.0, new FreecamBounds.Step());
        assertTrue(up.blockedY);
        assertEquals(383.9, up.y, 0.0);
        FreecamBounds.Step back = bounds.step(8.0, 400.0, 8.0, 8.0, 399.0, 8.0, new FreecamBounds.Step());
        assertFalse(back.blockedY);
        FreecamBounds.Step away = bounds.step(8.0, 400.0, 8.0, 8.0, 401.0, 8.0, new FreecamBounds.Step());
        assertTrue(away.blockedY);
    }

    @Test
    void outsideTheLoadedAreaOnlyTheWayTowardsThePlayerIsOpen() {
        FreecamBounds bounds = square();
        double outside = (RADIUS + 2) * 16.0 + 4.0;
        FreecamBounds.Step back = bounds.step(outside, 70.0, 8.0, outside - 1.0, 70.0, 8.0, new FreecamBounds.Step());
        assertFalse(back.blockedX);
        FreecamBounds.Step further = bounds.step(outside, 70.0, 8.0, outside + 1.0, 70.0, 8.0, new FreecamBounds.Step());
        assertTrue(further.blockedX);
    }

    @Test
    void stepFromALoadedColumnNeverEndsInAnUnloadedOneWhileChunksComeAndGo() {
        int size = 8;
        boolean[][] loaded = new boolean[size][size];
        Random random = new Random(20261022L);
        for (int cx = 0; cx < size; cx++) {
            for (int cz = 0; cz < size; cz++) {
                loaded[cx][cz] = random.nextInt(10) < 7;
            }
        }
        loaded[0][0] = true;
        double anchor = 8.0;
        FreecamBounds bounds = new FreecamBounds((cx, cz) -> cx >= 0 && cz >= 0 && cx < size && cz < size && loaded[cx][cz], MIN_Y, TOP_Y,
                anchor, anchor);
        FreecamBounds.Step step = new FreecamBounds.Step();
        double x = anchor;
        double z = anchor;
        int outsideSteps = 0;
        for (int i = 0; i < 50_000; i++) {
            if (random.nextInt(3) == 0) {
                int cx = random.nextInt(size);
                int cz = random.nextInt(size);
                loaded[cx][cz] = !loaded[cx][cz];
            }
            double toX = x + (random.nextDouble() - 0.45) * 8.0;
            double toZ = z + (random.nextDouble() - 0.45) * 8.0;
            boolean from = bounds.columnLoaded(x, z);
            bounds.step(x, 70.0, z, toX, 70.0, toZ, step);
            if (from) {
                assertTrue(bounds.columnLoaded(step.x, step.z), "Schritt " + i + " aus geladener Spalte endet außerhalb: " + step.x + " / " + step.z);
            } else {
                outsideSteps++;
                if (step.x != x && !bounds.columnLoaded(step.x, z)) {
                    assertTrue((step.x - x) * (x - anchor) < 0.0, "Schritt " + i + " weiter weg vom Spieler in x");
                }
                if (step.z != z && !bounds.columnLoaded(step.x, step.z)) {
                    assertTrue((step.z - z) * (z - anchor) < 0.0, "Schritt " + i + " weiter weg vom Spieler in z");
                }
            }
            x = step.x;
            z = step.z;
        }
        assertTrue(outsideSteps > 100, "Spalte unter der Freecam auch entladen: " + outsideSteps);
    }

    @Test
    void nonFiniteTargetsAreRejected() {
        FreecamBounds.Step step = square().step(8.0, 70.0, 8.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NaN, new FreecamBounds.Step());
        assertTrue(step.blockedX);
        assertTrue(step.blockedY);
        assertTrue(step.blockedZ);
        assertEquals(8.0, step.x, 0.0);
        assertEquals(70.0, step.y, 0.0);
        assertEquals(8.0, step.z, 0.0);
    }

    @Test
    void insideCombinesHeightAndColumns() {
        FreecamBounds bounds = square();
        assertTrue(bounds.inside(0.0, 64.0, 0.0));
        assertFalse(bounds.inside(0.0, 500.0, 0.0));
        assertFalse(bounds.inside(100.0, 64.0, 0.0));
        assertFalse(bounds.columnLoaded(Double.NaN, 0.0));
    }
}
