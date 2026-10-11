package de.lardtom.propwash.server;

import de.lardtom.propwash.server.GeneratedArea.Status;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdgeFenceTest {
    private static final double DT = 1.0 / 60.0;
    private static final double EDGE = 48.0;

    private static EdgeFence fence(GeneratedArea.Lookup lookup, int centerX, int centerZ) {
        return EdgeFence.of(EdgeMask.scan(lookup, centerX, centerZ));
    }

    private record Flight(double x, double z, double vx, double vz, double closest) {
    }

    private static Flight fly(EdgeFence fence, double x, double z, double vx, double vz, double ax, double az, int frames) {
        double closest = Double.POSITIVE_INFINITY;
        double[] v = new double[2];
        for (int i = 0; i < frames; i++) {
            v[0] = vx;
            v[1] = vz;
            fence.limit(x, z, v);
            vx = v[0] + ax * DT;
            vz = v[1] + az * DT;
            x += vx * DT;
            z += vz * DT;
            closest = Math.min(closest, fence.distance(x, z));
        }
        return new Flight(x, z, vx, vz, closest);
    }

    @Test
    void maskMarksMissingChunksAndTheUnsentOnesNextToThem() {
        EdgeMask mask = EdgeMask.scan((x, z) -> x == 3 ? Status.MISSING : x == -2 ? Status.UNKNOWN : Status.GENERATED, 0, 0);
        assertTrue(mask.any());
        assertEquals(3 * EdgeMask.SIDE, mask.count());
        assertTrue(mask.blocked(3, -6));
        assertTrue(mask.blocked(3, 6));
        assertTrue(mask.blocked(2, 0));
        assertTrue(mask.blocked(4, 0));
        assertFalse(mask.blocked(-2, 0));
        assertFalse(mask.blocked(-1, 0));
        assertFalse(mask.blocked(0, 0));
        assertFalse(mask.blocked(1, 0));
        assertFalse(mask.blocked(3, 7));
        assertTrue(EdgeMask.scan((x, z) -> x == 0 && z == 7 ? Status.MISSING : Status.GENERATED, 0, 0).blocked(0, 6));
        assertFalse(EdgeMask.scan((x, z) -> Status.GENERATED, 5, 5).any());
        assertTrue(EdgeFence.of(EdgeMask.NONE).empty());
    }

    @Test
    void brakingCurveStopsAtTheStopDistance() {
        assertEquals(0.0, EdgeFence.allowedApproach(EdgeFence.STOP_DISTANCE));
        assertEquals(-EdgeFence.RETURN_MAX, EdgeFence.allowedApproach(0.0));
        assertTrue(EdgeFence.allowedApproach(EdgeFence.STOP_DISTANCE - 0.1) < 0.0);
        assertEquals(0.0, EdgeFence.allowedApproach(Double.NaN));
        double previous = 0.0;
        for (int d = 5; d < 120; d++) {
            double allowed = EdgeFence.allowedApproach(d);
            assertTrue(allowed > previous);
            previous = allowed;
        }
        assertEquals(Math.sqrt(2.0 * EdgeFence.BRAKE * 76.0), EdgeFence.allowedApproach(80.0), 1.0E-9);
    }

    @Test
    void fullSpeedIntoAStraightEdgeStopsBeforeIt() {
        EdgeFence fence = fence((x, z) -> x >= 4 ? Status.MISSING : Status.GENERATED, 2, 0);
        assertEquals(18.0, fence.distance(30.0, 8.0), 1.0E-9);
        for (double speed : new double[]{5.0, 20.0, 40.0, 55.0}) {
            Flight flight = fly(fence, EDGE - 76.0, 8.0, speed, 0.0, 15.0, 0.0, 900);
            assertTrue(flight.closest() >= EdgeFence.STOP_DISTANCE - 0.25, speed + " m/s: " + flight);
            assertTrue(flight.x() < EDGE - EdgeFence.STOP_DISTANCE + 0.25, speed + " m/s: " + flight);
            assertTrue(flight.x() > EDGE - EdgeFence.STOP_DISTANCE - 1.0, speed + " m/s bleibt zu früh stehen: " + flight);
            assertTrue(Math.abs(flight.vx()) < 0.5, speed + " m/s: " + flight);
        }
    }

    @Test
    void slidingAlongTheEdgeKeepsTheSideSpeed() {
        EdgeFence fence = fence((x, z) -> x >= 4 ? Status.MISSING : Status.GENERATED, 2, 0);
        double[] v = {6.0, 25.0};
        fence.limit(EDGE - EdgeFence.STOP_DISTANCE, 8.0, v);
        assertEquals(0.0, v[0], 1.0E-9);
        assertEquals(25.0, v[1], 1.0E-9);
        Flight flight = fly(fence, EDGE - 14.0, -40.0, 4.0, 25.0, 6.0, 0.0, 180);
        assertTrue(flight.closest() >= EdgeFence.STOP_DISTANCE - 0.25, flight.toString());
        assertTrue(flight.vz() > 24.0, "Seitentempo gebremst: " + flight);
    }

    @Test
    void movingAwayIsNeverBraked() {
        EdgeFence fence = fence((x, z) -> x >= 4 ? Status.MISSING : Status.GENERATED, 2, 0);
        double[] v = {-30.0, 3.0};
        assertFalse(fence.limit(EDGE - 2.0, 8.0, v));
        assertEquals(-30.0, v[0]);
        assertEquals(3.0, v[1]);
        double[] slow = {2.0, 0.0};
        assertFalse(fence.limit(20.0, 8.0, slow));
    }

    @Test
    void concaveCornerHoldsBothWalls() {
        EdgeFence fence = fence((x, z) -> x >= 4 || z >= 4 ? Status.MISSING : Status.GENERATED, 2, 2);
        Flight flight = fly(fence, 10.0, 10.0, 30.0, 30.0, 12.0, 12.0, 900);
        assertTrue(flight.x() < EDGE - EdgeFence.STOP_DISTANCE + 0.25, flight.toString());
        assertTrue(flight.z() < EDGE - EdgeFence.STOP_DISTANCE + 0.25, flight.toString());
        assertTrue(flight.closest() >= EdgeFence.STOP_DISTANCE - 0.25, flight.toString());
    }

    @Test
    void singleMissingChunkIsFlownAround() {
        EdgeFence fence = fence((x, z) -> x == 4 && z == 0 ? Status.MISSING : Status.GENERATED, 2, 0);
        Flight straight = fly(fence, 20.0, 8.0, 25.0, 0.0, 10.0, 0.0, 600);
        assertTrue(straight.x() < EDGE - EdgeFence.STOP_DISTANCE + 0.25, straight.toString());
        Flight past = fly(fence, 20.0, 50.0, 25.0, 0.0, 0.0, 0.0, 240);
        assertEquals(120.0, past.x(), 1.0E-6, "Vorbeiflug neben dem Loch gebremst: " + past);
        assertEquals(25.0, past.vx(), 1.0E-9);
    }

    @Test
    void edgeBehindTheDroneOrOutsideTheMaskIsIgnored() {
        EdgeFence fence = fence((x, z) -> x <= -1 ? Status.MISSING : Status.GENERATED, 2, 0);
        double[] v = {40.0, 0.0};
        assertFalse(fence.limit(40.0, 8.0, v));
        assertEquals(40.0, v[0]);
        EdgeFence far = fence((x, z) -> x >= 20 ? Status.MISSING : Status.GENERATED, 2, 0);
        assertTrue(far.empty());
    }

    @Test
    void edgeTravelsToThePilot() {
        EdgeMask mask = EdgeMask.scan((x, z) -> x >= 4 || z <= -100 ? Status.MISSING : Status.GENERATED, -97, -101);
        GenerationEdgeS2C payload = GenerationEdgeS2C.of(mask);
        ByteBuf buffer = Unpooled.buffer();
        GenerationEdgeS2C.CODEC.encode(buffer, payload);
        GenerationEdgeS2C decoded = GenerationEdgeS2C.CODEC.decode(buffer);
        assertEquals(0, buffer.readableBytes());
        assertEquals(payload, decoded);
        assertEquals(mask, decoded.mask());
        assertTrue(decoded.restricted());
        assertEquals(EdgeMask.NONE, GenerationEdgeS2C.OFF.mask());
        assertFalse(GenerationEdgeS2C.OFF.restricted());
    }

    @Test
    void invalidInputDoesNothing() {
        EdgeFence fence = fence((x, z) -> x >= 4 ? Status.MISSING : Status.GENERATED, 2, 0);
        double[] v = {Double.NaN, 1.0};
        assertFalse(fence.limit(60.0, 8.0, v));
        double[] w = {30.0, 0.0};
        assertFalse(fence.limit(Double.POSITIVE_INFINITY, 8.0, w));
        assertFalse(fence.limit(40.0, Double.NaN, w));
        assertEquals(30.0, w[0]);
    }
}
