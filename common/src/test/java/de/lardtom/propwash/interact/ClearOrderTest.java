package de.lardtom.propwash.interact;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClearOrderTest {
    private static final double HALF = 0.25;

    private record Block(double x, double y, double z, boolean pane) {
        double score(double speed) {
            return ClearOrder.score(this.x, this.y, this.z, 0.0, 0.0, speed, HALF);
        }
    }

    private static List<Block> tube(double length, double paneAt) {
        List<Block> blocks = new ArrayList<>();
        for (int z = -8; z < length; z++) {
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    if (Math.abs(x) == 2 || Math.abs(y) == 2) {
                        blocks.add(new Block(x, y, z + 0.5, false));
                    }
                }
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                blocks.add(new Block(x, y, paneAt, true));
            }
        }
        return blocks;
    }

    @Test
    void paneInTheFlightPathComesBeforeTheGlassAroundIt() {
        for (double speed : new double[]{15.0, 25.0, 38.0, 60.0}) {
            List<Block> blocks = tube(32.0, 30.0);
            blocks.removeIf(block -> block.score(speed) == ClearOrder.SKIP);
            blocks.sort(Comparator.comparingDouble(block -> block.score(speed)));
            for (int i = 0; i < 9; i++) {
                assertTrue(blocks.get(i).pane(), "Tempo " + speed + " Platz " + i + ": " + blocks.get(i));
            }
        }
    }

    @Test
    void glassBehindAFastDroneIsNotAsked() {
        assertEquals(ClearOrder.SKIP, ClearOrder.score(0.0, 0.0, -6.0, 0.0, 0.0, 30.0, HALF), 0.0);
        assertEquals(ClearOrder.SKIP, ClearOrder.score(2.0, 1.0, -5.0, 0.0, 0.0, 30.0, HALF), 0.0);
        assertTrue(ClearOrder.score(0.0, 0.0, -2.0, 0.0, 0.0, 30.0, HALF) < ClearOrder.OUTSIDE + 1.0);
        assertTrue(ClearOrder.score(0.0, 0.0, -6.0, 0.0, 0.0, 4.0, HALF) < ClearOrder.OUTSIDE + 10.0);
    }

    @Test
    void hoveringDroneAsksByDistanceInAllDirections() {
        double near = ClearOrder.score(1.0, 0.0, -1.0, 0.1, 0.0, 0.2, HALF);
        double far = ClearOrder.score(0.0, 0.0, 6.0, 0.1, 0.0, 0.2, HALF);
        double behind = ClearOrder.score(0.0, 0.0, -6.0, 0.1, 0.0, 0.2, HALF);
        assertTrue(near < far);
        assertEquals(far, behind, 1.0E-12);
        assertEquals(6.0 / ClearOrder.MIN_SPEED, far, 1.0E-12);
    }

    @Test
    void scoreIsTheTimeToContactInsideTheCorridor() {
        assertEquals(30.0 / 36.0, ClearOrder.score(0.0, 0.0, 30.0, 0.0, 0.0, 36.0, HALF), 1.0E-12);
        assertEquals(30.0 / 36.0, ClearOrder.score(1.0, 1.0, 30.0, 0.0, 0.0, 36.0, HALF), 1.0E-9);
        double beside = ClearOrder.score(2.5, 0.0, 30.0, 0.0, 0.0, 36.0, HALF) - ClearOrder.OUTSIDE;
        assertTrue(beside > 30.0 / 36.0 && beside < 30.0 / 36.0 + 0.2);
        double nearBeside = ClearOrder.score(2.5, 0.0, 3.0, 0.0, 0.0, 36.0, HALF);
        assertTrue(nearBeside > ClearOrder.score(0.0, 0.0, 30.0, 0.0, 0.0, 36.0, HALF));
        assertTrue(nearBeside < ClearOrder.score(2.5, 0.0, 30.0, 0.0, 0.0, 36.0, HALF));
        double diagonal = ClearOrder.score(21.2132, 0.0, 21.2132, 25.4558, 0.0, 25.4558, HALF);
        assertEquals(30.0 / 36.0, diagonal, 1.0E-4);
        assertEquals(ClearOrder.SKIP, ClearOrder.score(Double.NaN, 0.0, 1.0, 0.0, 0.0, 10.0, HALF), 0.0);
        assertTrue(Double.isFinite(ClearOrder.score(1.0, 0.0, 1.0, Double.NaN, 0.0, 10.0, HALF)));
    }

    @Test
    void serverRangeCoversTheFarthestRequestOfAFastDrone() {
        double farthestAsk = 24.0 + 6.0 + 1.0;
        for (double speed : new double[]{0.0, 15.0, 27.0, 36.5, 45.0, 60.0}) {
            double lag = 3.0 * speed / 20.0;
            assertTrue(DroneBreaks.clearRange(speed) >= farthestAsk + lag, "Tempo " + speed + " Reichweite " + DroneBreaks.clearRange(speed));
        }
        assertEquals(DroneBreaks.CLEAR_RANGE, DroneBreaks.clearRange(0.0), 0.0);
        assertEquals(DroneBreaks.CLEAR_RANGE, DroneBreaks.clearRange(Double.NaN), 0.0);
        assertEquals(DroneBreaks.CLEAR_RANGE, DroneBreaks.clearRange(-5.0), 0.0);
        assertEquals(DroneBreaks.MAX_CLEAR_RANGE, DroneBreaks.clearRange(500.0), 0.0);
        assertEquals(DroneBreaks.CLEAR_RANGE, DroneBreaks.clearRange(Double.POSITIVE_INFINITY), 0.0);
    }
}
