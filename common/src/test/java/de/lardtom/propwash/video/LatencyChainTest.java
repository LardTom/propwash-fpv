package de.lardtom.propwash.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatencyChainTest {
    private static final long MS = 1_000_000L;

    @Test
    void totalCountsTheOlderOfSimStateAndVideoDelayOnce() {
        assertEquals(1 * MS + 14 * MS + 6 * MS + 8 * MS, LatencyChain.total(1 * MS, MS / 4, 14 * MS, 6 * MS, 8 * MS));
        assertEquals(1 * MS + MS / 4 + 6 * MS + 8 * MS, LatencyChain.total(1 * MS, MS / 4, 0L, 6 * MS, 8 * MS));
        assertEquals(0L, LatencyChain.total(-1L, -1L, -1L, -1L, -1L));
    }

    @Test
    void framesCollectMeanMaxAndUncompensatedTotal() {
        LatencyChain chain = new LatencyChain();
        chain.frame(2 * MS, MS / 2, 30 * MS, 16 * MS, 6 * MS, 8 * MS);
        chain.frame(4 * MS, 0L, 30 * MS, 16 * MS, 6 * MS, 8 * MS);
        assertEquals(2L, chain.frames());
        assertEquals(3.0, chain.meanMs(LatencyChain.Part.INPUT_AGE), 1.0E-9);
        assertEquals(4.0, chain.maxMs(LatencyChain.Part.INPUT_AGE), 1.0E-9);
        assertEquals(30.0, chain.meanMs(LatencyChain.Part.VIDEO_SYSTEM), 1.0E-9);
        assertEquals(16.0, chain.meanMs(LatencyChain.Part.VIDEO_APPLIED), 1.0E-9);
        assertEquals(3.0 + 16.0 + 6.0 + 8.0, chain.meanMs(LatencyChain.Part.TOTAL), 1.0E-9);
        assertEquals(3.0 + 0.25 + 30.0 + 6.0 + 8.0, chain.meanMs(LatencyChain.Part.TOTAL_UNCOMPENSATED), 1.0E-9);
        assertEquals(34.0, chain.lastMs(LatencyChain.Part.TOTAL), 1.0E-9);
        assertTrue(chain.meanMs(LatencyChain.Part.TOTAL) < chain.meanMs(LatencyChain.Part.TOTAL_UNCOMPENSATED));
        assertTrue(chain.summary().startsWith("frames=2 input_age=3.00/4.00"));
        chain.reset();
        assertEquals(0L, chain.frames());
        assertEquals(0.0, chain.meanMs(LatencyChain.Part.TOTAL));
    }
}
