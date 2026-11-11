package de.lardtom.propwash.capture;

import de.lardtom.propwash.api.drone.CameraChunkSupply;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureRulesTest {
    @Test
    void radiusFollowsRequestWorldRuleAndMaximum() {
        assertEquals(4, CaptureRules.granted(4, 4));
        assertEquals(3, CaptureRules.granted(3, 8));
        assertEquals(4, CaptureRules.granted(20, 4));
        assertEquals(CameraChunkSupply.MAX_RADIUS, CaptureRules.granted(20, 99));
        assertEquals(1, CaptureRules.granted(0, 4));
        assertEquals(1, CaptureRules.granted(-5, 4));
        assertEquals(0, CaptureRules.granted(4, 0));
    }

    @Test
    void worldRuleIsANeutralVideoNumberWithOffAtZero() {
        RuleSpec spec = WorldRuleSpecs.CAMERA_CHUNKS;
        assertEquals("propwash:camera_chunks", spec.id());
        assertEquals(RuleSpec.Group.VIDEO, spec.group());
        assertEquals(RuleSpec.Kind.INT, spec.kind());
        assertEquals(4, spec.defaultValue());
        assertEquals(0, spec.min());
        assertEquals(CameraChunkSupply.MAX_RADIUS, spec.max());
        assertTrue(spec.zeroSpecial());
        assertFalse(spec.raceRelevant());
    }

    @Test
    void costCountsTheChunksOfTheArea() {
        assertEquals(0, CaptureRules.cost(0));
        int previous = 0;
        for (int r = 1; r <= CameraChunkSupply.MAX_RADIUS + 2; r++) {
            CameraArea area = new CameraArea(5, -7, r);
            int[] count = new int[1];
            area.forEach(pos -> count[0]++);
            assertEquals(count[0], CaptureRules.cost(r), "Radius " + r);
            assertEquals(area.chunks(), CaptureRules.cost(r));
            assertTrue(CaptureRules.cost(r) > previous);
            previous = CaptureRules.cost(r);
        }
        assertTrue(CaptureRules.cost(CameraChunkSupply.MAX_RADIUS) <= (2 * CameraChunkSupply.MAX_RADIUS + 3) * (2 * CameraChunkSupply.MAX_RADIUS + 3));
    }

    @Test
    void admissionLimitsCountServerAndLoad() {
        int cost = CaptureRules.cost(4);
        assertEquals(CaptureRules.Admission.ADMITTED, CaptureRules.admit(0, 0, 0, cost, 4096, false));
        assertEquals(CaptureRules.Admission.ADMITTED, CaptureRules.admit(1, 5, 500, cost, 4096, false));
        assertEquals(CaptureRules.Admission.TOO_MANY, CaptureRules.admit(CameraChunkSupply.MAX_PER_PLAYER, 2, 0, cost, 4096, false));
        assertEquals(CaptureRules.Admission.SERVER_BUSY, CaptureRules.admit(0, CaptureRules.MAX_SERVER, 0, cost, 4096, false));
        assertEquals(CaptureRules.Admission.SERVER_BUSY, CaptureRules.admit(0, 3, 4096 - cost + 1, cost, 4096, false));
        assertEquals(CaptureRules.Admission.ADMITTED, CaptureRules.admit(0, 3, 4096 - cost, cost, 4096, false));
        assertEquals(CaptureRules.Admission.SERVER_BUSY, CaptureRules.admit(0, 0, 0, cost, 4096, true));
        assertTrue(CaptureRules.overloaded(CaptureRules.OVERLOAD_NANOS + 1));
        assertFalse(CaptureRules.overloaded(CaptureRules.OVERLOAD_NANOS));
        assertFalse(CaptureRules.overloaded(10_000_000L));
    }

    @Test
    void endReasonsInOrder() {
        assertNull(CaptureRules.end(new CaptureRules.Check(true, true, true, true, true, 4)));
        assertEquals(CaptureRules.End.DISCONNECTED, CaptureRules.end(new CaptureRules.Check(false, false, false, false, false, 0)));
        assertEquals(CaptureRules.End.DIED, CaptureRules.end(new CaptureRules.Check(true, false, true, true, true, 4)));
        assertEquals(CaptureRules.End.DRONE_GONE, CaptureRules.end(new CaptureRules.Check(true, true, false, false, false, 4)));
        assertEquals(CaptureRules.End.DIMENSION, CaptureRules.end(new CaptureRules.Check(true, true, true, false, true, 4)));
        assertEquals(CaptureRules.End.NOT_OWN_DRONE, CaptureRules.end(new CaptureRules.Check(true, true, true, true, false, 4)));
        assertEquals(CaptureRules.End.RULE_OFF, CaptureRules.end(new CaptureRules.Check(true, true, true, true, true, 0)));
    }

    @Test
    void storageCoversEveryAreaChunkInSteps() {
        assertEquals(0, CaptureRules.storageRadius(0, 0, List.of()));
        CameraArea far = new CameraArea(19, 0, 4);
        int storage = CaptureRules.storageRadius(0, 0, List.of(far));
        assertEquals(0, storage % CaptureRules.STORAGE_STEP);
        assertEquals(32, storage);
        int[] widest = new int[1];
        far.forEach(pos -> widest[0] = Math.max(widest[0], Math.max(Math.abs(pos.x()), Math.abs(pos.z()))));
        assertTrue(storage >= widest[0] + CaptureRules.STORAGE_MARGIN, "Speicher " + storage + " für Chunk in " + widest[0]);
        CameraArea other = new CameraArea(-3, -40, 2);
        assertEquals(48, CaptureRules.storageRadius(0, 0, List.of(far, other)));
        CameraArea limit = new CameraArea(CameraChunkSupply.MAX_DISTANCE_CHUNKS, 0, CameraChunkSupply.MAX_RADIUS);
        assertTrue(CaptureRules.storageRadius(0, 0, List.of(limit)) <= 144);
    }

    @Test
    void storageGrowsAtOnceAndShrinksWithSlack() {
        assertEquals(32, CaptureRules.grown(0, 32));
        assertEquals(32, CaptureRules.grown(32, 8));
        assertEquals(32, CaptureRules.shrunk(32, 24));
        assertEquals(24, CaptureRules.shrunk(48, 24));
        assertEquals(48, CaptureRules.shrunk(48, 40));
        assertEquals(0, CaptureRules.shrunk(48, 0));
        assertEquals(0, CaptureRules.shrunk(0, 0));
    }

    @Test
    void areasBeyondTheDistanceLimitPause() {
        CameraArea edge = new CameraArea(CameraChunkSupply.MAX_DISTANCE_CHUNKS, 3, 4);
        CameraArea beyond = new CameraArea(-CameraChunkSupply.MAX_DISTANCE_CHUNKS - 1, 0, 4);
        assertFalse(CaptureRules.tooFar(0, 0, edge));
        assertTrue(CaptureRules.tooFar(0, 0, beyond));
        assertFalse(CaptureRules.tooFar(-10, 0, beyond));
    }

    @Test
    void ticketsReachTwoChunksBeyondTheArea() {
        assertEquals(6, CaptureRules.ticketRadius(4));
        CameraArea area = new CameraArea(0, 0, 4);
        assertTrue(CaptureRules.ticketRadius(4) >= area.maxX() + 1);
    }

    @Test
    void requestsAreLimitedPerWindow() {
        int[] window = {0, 0};
        for (int i = 0; i < CaptureRules.REQUESTS_PER_WINDOW; i++) {
            assertTrue(CaptureRules.allowRequest(window, 5));
        }
        assertFalse(CaptureRules.allowRequest(window, 6));
        assertFalse(CaptureRules.allowRequest(window, CaptureRules.REQUEST_WINDOW_TICKS - 1));
        assertTrue(CaptureRules.allowRequest(window, CaptureRules.REQUEST_WINDOW_TICKS));
        assertTrue(CaptureRules.allowRequest(window, 1));
    }

    @Test
    void apiLimitsMatchTheRules() {
        assertEquals(CameraChunkSupply.MAX_RADIUS, CaptureRules.MAX_RADIUS);
        assertEquals(CameraChunkSupply.MAX_PER_PLAYER, CaptureRules.MAX_PER_PLAYER);
        assertEquals(CameraChunkSupply.MAX_DISTANCE_CHUNKS, CaptureRules.MAX_DISTANCE);
        assertEquals(2, CameraChunkSupply.MAX_PER_PLAYER);
        assertEquals(8, CameraChunkSupply.MAX_RADIUS);
    }
}
