package de.lardtom.propwash.video.link;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObstructionScanTest {
    private static final ObstructionTracer.World AIR = (x, y, z) -> Medium.AIR;

    private static ObstructionTracer.World pole(int x, int z) {
        return (bx, by, bz) -> bx == x && bz == z && by > -50 && by < 50 ? ScenarioWorld.LOG : Medium.AIR;
    }

    private static double scan(ObstructionTracer.World world, double toX, double toY, double toZ) {
        return scanFrom(0.5, world, toX, toY, toZ);
    }

    private static double scanFrom(double fromZ, ObstructionTracer.World world, double toX, double toY, double toZ) {
        ObstructionScan scan = new ObstructionScan();
        scan.start(0.5, 0.5, fromZ, toX, toY, toZ);
        return scan.evaluate(world);
    }

    @Test
    void samplePathsMeetAtBothEndsAndBulgeByTheFresnelRadius() {
        ObstructionScan scan = new ObstructionScan();
        scan.start(0.0, 10.0, 0.0, 100.0, 10.0, 0.0);
        int points = ObstructionScan.pathPoints();
        double[] path = new double[points * 3];
        scan.samplePath(2, 1.0, path);
        assertEquals(0.0, path[0], 1.0E-12);
        assertEquals(10.0, path[1], 1.0E-12);
        assertEquals(100.0, path[3 * (points - 1)], 1.0E-12);
        assertEquals(10.0, path[3 * (points - 1) + 1], 1.0E-12);
        int mid = points / 2;
        assertEquals(50.0, path[3 * mid], 1.0E-9);
        double expected = RadioBand.firstFresnelRadius(50.0, 50.0) / Math.sqrt(2.0);
        assertEquals(10.0 + expected, path[3 * mid + 1], 1.0E-9);
        scan.samplePath(0, 2.0, path);
        assertEquals(10.0, path[3 * mid + 1], 1.0E-9);
        double right = path[3 * mid + 2];
        assertEquals(2.0 * expected, Math.abs(right), 1.0E-9);
        scan.samplePath(1, 2.0, path);
        assertEquals(-right, path[3 * mid + 2], 1.0E-9);
    }

    @Test
    void openSkyCostsNothingAndNeedsFewRays() {
        ObstructionScan scan = new ObstructionScan();
        scan.start(0.5, 70.0, 0.5, 300.5, 90.0, 120.5);
        assertEquals(0.0, scan.evaluate(AIR), 0.0);
        assertEquals(17, scan.roundRays());
        assertTrue(scan.complete());
    }

    @Test
    void budgetedRoundsGiveTheSameResultAsOneGo() {
        ScenarioWorld world = ScenarioWorld.flat().oak(45, 0, 5);
        double whole = scan(world, 50.5, 1.5, 0.5);
        ObstructionScan scan = new ObstructionScan();
        scan.start(0.5, 0.5, 0.5, 50.5, 1.5, 0.5);
        int ticks = 0;
        while (!scan.advance(world, 200)) {
            ticks++;
            assertTrue(ticks < 1000);
        }
        assertTrue(ticks > 3, "spread over several calls: " + ticks);
        assertEquals(whole, scan.lossDb(), 0.0);
        assertTrue(whole > 5.0, "behind the trunk " + whole);
    }

    @Test
    void solidWallAcrossThePathBlocksTheVideo() {
        ObstructionTracer.World wall = (x, y, z) -> x == 20 && Math.abs(y) < 40 && Math.abs(z) < 40 ? ScenarioWorld.STONE : Medium.AIR;
        assertTrue(scan(wall, 40.5, 0.5, 0.5) >= 55.0);
    }

    @Test
    void poleOnTheLineCostsMoreThanBesideIt() {
        double onLine = scan(pole(25, 0), 50.5, 0.5, 0.5);
        double near = scanFrom(0.8, pole(25, 1), 50.5, 0.5, 0.8);
        double outside = scan(pole(25, 1), 50.5, 0.5, 0.5);
        double beside = scan(pole(25, 4), 50.5, 0.5, 0.5);
        assertTrue(onLine > 2.0 && onLine < 14.0, "pole on the line " + onLine);
        assertTrue(near > 1.0 && near < onLine, "pole 0.2 m next to the line " + near);
        assertTrue(outside < near, "pole 0.5 m next to the line " + outside);
        assertEquals(0.0, beside, 0.3, "pole 3.5 m beside the line");
    }

    @Test
    void anObstacleCloseToTheDroneShadowsMoreThanOneHalfway() {
        double halfway = scan(pole(50, 0), 100.5, 0.5, 0.5);
        double closeToDrone = scan(pole(95, 0), 100.5, 0.5, 0.5);
        assertTrue(closeToDrone > halfway + 3.0, "close " + closeToDrone + " halfway " + halfway);
    }

    @Test
    void degenerateGeometryIsClear() {
        ObstructionScan scan = new ObstructionScan();
        scan.start(1.0, 2.0, 3.0, 1.0, 2.0, 3.0);
        assertEquals(0.0, scan.evaluate((x, y, z) -> ScenarioWorld.STONE), 0.0);
        scan.clear();
        assertTrue(scan.complete());
        assertEquals(0.0, scan.lossDb(), 0.0);
    }

    @Test
    void representativeLossesFeedTheMultipathEstimate() {
        ObstructionScan scan = new ObstructionScan();
        scan.start(0.5, 0.5, 0.5, 50.5, 0.5, 0.5);
        scan.evaluate(pole(25, 0));
        double[] out = new double[ObstructionScan.REPRESENTATIVE];
        int n = scan.representativeLosses(out);
        assertEquals(ObstructionScan.REPRESENTATIVE, n);
        assertEquals(scan.centerLossDb(), out[0], 0.0);
        assertTrue(out[0] > 30.0, "center ray through the trunk " + out[0]);
    }
}
