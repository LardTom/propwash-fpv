package de.lardtom.propwash.server;

import de.lardtom.propwash.server.GeneratedArea.Status;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedAreaTest {
    @Test
    void fullyGeneratedAreaReachesTheScanRadius() {
        GeneratedArea.Scan scan = GeneratedArea.scan((x, z) -> Status.GENERATED, 100, -40, 8);
        assertEquals(8, scan.generatedRadius());
        assertEquals(GeneratedArea.NONE, scan.missingDistance());
        assertTrue(scan.complete());
    }

    @Test
    void missingChunkLimitsTheRadiusByChebyshevDistance() {
        GeneratedArea.Scan scan = GeneratedArea.scan((x, z) -> x == 4 && z == -3 ? Status.MISSING : Status.GENERATED, 0, 0, 10);
        assertEquals(3, scan.generatedRadius());
        assertEquals(4, scan.missingDistance());
        assertTrue(scan.missingWithin(4));
        assertFalse(scan.missingWithin(3));
        assertTrue(scan.complete());
    }

    @Test
    void missingCenterMeansNothingIsUsable() {
        GeneratedArea.Scan scan = GeneratedArea.scan((x, z) -> x == 5 && z == 5 ? Status.MISSING : Status.GENERATED, 5, 5, 4);
        assertEquals(-1, scan.generatedRadius());
        assertEquals(0, scan.missingDistance());
    }

    @Test
    void unknownChunksBlockButDoNotCountAsMissing() {
        GeneratedArea.Scan scan = GeneratedArea.scan((x, z) -> Math.abs(x) == 2 ? Status.UNKNOWN : Status.GENERATED, 0, 0, 5);
        assertEquals(1, scan.generatedRadius());
        assertEquals(GeneratedArea.NONE, scan.missingDistance());
        assertFalse(scan.complete());
    }

    @Test
    void everyChunkInTheSquareIsVisitedExactlyOnce() {
        Set<Long> seen = new HashSet<>();
        int[] calls = {0};
        GeneratedArea.scan((x, z) -> {
            calls[0]++;
            seen.add(((long) x << 32) ^ (z & 0xFFFFFFFFL));
            return Status.GENERATED;
        }, 3, 7, 6);
        assertEquals(13 * 13, calls[0]);
        assertEquals(13 * 13, seen.size());
    }

    @Test
    void laterMissingRingIsStillReported() {
        GeneratedArea.Scan scan = GeneratedArea.scan((x, z) -> {
            if (x == 1 && z == 0) {
                return Status.UNKNOWN;
            }
            return x == 3 ? Status.MISSING : Status.GENERATED;
        }, 0, 0, 4);
        assertEquals(0, scan.generatedRadius());
        assertEquals(3, scan.missingDistance());
        assertFalse(scan.complete());
    }
}
