package de.lardtom.propwash.rescue;

import de.lardtom.propwash.server.EdgeFence;
import de.lardtom.propwash.server.EdgeMask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightFenceTest {
    private static final double DT = 0.05 / 4.0;
    private static final int EDGE_CHUNK = 407;
    private static final double EDGE = EDGE_CHUNK * 16.0;

    private record Run(double x, double z, double vx, double vz, double furthest) {
    }

    private static Run fly(EdgeFence fence, double x, double z, double vx, double vz, double ax, double az, double maxSpeed, int slices) {
        double furthest = x;
        double[] v = new double[2];
        for (int i = 0; i < slices; i++) {
            vx = clamp(vx + ax * DT, maxSpeed);
            vz = clamp(vz + az * DT, maxSpeed);
            v[0] = vx;
            v[1] = vz;
            fence.limit(x, z, v);
            vx = v[0];
            vz = v[1];
            x += vx * DT;
            z += vz * DT;
            furthest = Math.max(furthest, x);
        }
        return new Run(x, z, vx, vz, furthest);
    }

    private static double clamp(double v, double max) {
        return Math.max(-max, Math.min(max, v));
    }

    @Test
    void maskBlocksExactlyTheChunksThatCannotKeepTicking() {
        EdgeMask mask = FlightFence.mask(405, 432, (x, z) -> x < EDGE_CHUNK);
        for (int dz = -FlightFence.REACH; dz <= FlightFence.REACH; dz++) {
            for (int dx = -FlightFence.REACH; dx <= FlightFence.REACH; dx++) {
                int x = 405 + dx;
                int z = 432 + dz;
                assertEquals(x >= EDGE_CHUNK, mask.blocked(x, z), "Chunk " + x + "/" + z);
            }
        }
        assertFalse(mask.blocked(405 + FlightFence.REACH + 1, 432), "außerhalb der Reichweite nie gesperrt");
        assertEquals(2 * (2 * FlightFence.REACH + 1), mask.count());
    }

    @Test
    void openTerrainLeavesTheFlightAlone() {
        EdgeFence fence = FlightFence.of(405, 432, (x, z) -> true);
        assertTrue(fence.empty());
        Run run = fly(fence, 6490.0, 6922.5, 8.0, 0.0, 0.0, 0.0, 8.0, 400);
        assertEquals(8.0, run.vx(), 1.0E-9);
    }

    @Test
    void stripOfOneGeneratedRowStopsTheDroneBeforeTheFirstChunkItCannotKeep() {
        EdgeFence fence = FlightFence.of(405, 432, (x, z) -> x < EDGE_CHUNK);
        Run run = fly(fence, 6490.0, 6922.5, 8.0, 0.0, 6.0, 0.0, 8.0, 4 * 20 * 10);
        assertTrue(run.furthest() < EDGE - 2.0, "kommt bis " + run.furthest() + " an die Kante bei " + EDGE);
        assertTrue(run.x() > EDGE - 6.0, "hält erst " + (EDGE - run.x()) + " m vor der Kante");
        assertEquals(0.0, run.vx(), 0.05);
    }

    @Test
    void fastFlightStillStopsInTime() {
        EdgeFence fence = FlightFence.of(404, 432, (x, z) -> x < EDGE_CHUNK);
        Run run = fly(fence, 6470.0, 6922.5, 20.0, 0.0, 10.0, 0.0, 20.0, 4 * 20 * 10);
        assertTrue(run.furthest() < EDGE, "kommt bis " + run.furthest() + " über die Kante bei " + EDGE);
    }

    @Test
    void theLimitCountsOnlyWhileTheFlightPushesIntoTheEdge() {
        FlightFence fence = new FlightFence();
        fence.use(FlightFence.of(405, 432, (x, z) -> x < EDGE_CHUNK));
        double[] v = {8.0, 0.0};
        assertFalse(fence.limit(6470.0, 6922.5, v), "weit vor der Kante frei");
        assertFalse(fence.atLimit());
        double x = 6490.0;
        double vx = 8.0;
        int pushing = 0;
        for (int i = 0; i < 4 * 20 * 10; i++) {
            vx = Math.min(8.0, vx + 6.0 * DT);
            v[0] = vx;
            v[1] = 0.0;
            fence.limit(x, 6922.5, v);
            vx = v[0];
            x += vx * DT;
            if (fence.atLimit()) {
                pushing++;
                assertTrue(EDGE - x <= EdgeFence.STOP_DISTANCE + FlightFence.HOLD_BAND + 1.0E-6, "Grenze erst " + (EDGE - x) + " m vor der Kante");
            }
        }
        assertTrue(pushing > 4 * 20 * 7, "hält nur " + pushing + " Rechenschritte an der Grenze");
        double[] still = {0.0, 0.0};
        fence.limit(EDGE - EdgeFence.STOP_DISTANCE - 0.5, 6922.5, still);
        assertFalse(fence.atLimit(), "Schweben vor der Kante ist keine Grenze");
        double[] along = {0.0, 8.0};
        fence.limit(EDGE - EdgeFence.STOP_DISTANCE, 6922.5, along);
        assertFalse(fence.atLimit(), "Flug entlang der Kante ist keine Grenze");
        double[] back = {-8.0, 0.0};
        fence.limit(EDGE - EdgeFence.STOP_DISTANCE, 6922.5, back);
        assertFalse(fence.atLimit(), "Umkehren ist keine Grenze");
        FlightFence open = new FlightFence();
        open.use(FlightFence.of(405, 432, (cx, cz) -> true));
        double[] fast = {20.0, 0.0};
        assertFalse(open.limit(EDGE - 1.0, 6922.5, fast));
        assertFalse(open.atLimit());
    }

    @Test
    void generatingFlightsLookAheadUpToFourChunks() {
        assertEquals(0, FlightChunks.aheadChunks(0.0));
        assertEquals(0, FlightChunks.aheadChunks(2.9));
        assertEquals(1, FlightChunks.aheadChunks(4.0));
        assertEquals(2, FlightChunks.aheadChunks(8.0));
        assertEquals(FlightChunks.MAX_AHEAD, FlightChunks.aheadChunks(20.0));
        assertEquals(0, FlightChunks.aheadChunks(Double.NaN));
    }

    @Test
    void sideStepsAndTurningBackStayFree() {
        EdgeFence fence = FlightFence.of(405, 432, (x, z) -> x < EDGE_CHUNK);
        Run along = fly(fence, 6500.0, 6922.5, 0.0, 8.0, 0.0, 0.0, 8.0, 40);
        assertEquals(8.0, along.vz(), 1.0E-9);
        Run back = fly(fence, 6508.0, 6922.5, -8.0, 0.0, 0.0, 0.0, 8.0, 40);
        assertEquals(-8.0, back.vx(), 1.0E-9);
    }
}
