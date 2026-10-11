package de.lardtom.propwash.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayLatencyTest {
    private static final long MS = 1_000_000L;

    @Test
    void appliedLatencyIsTheVideoLatencyMinusTheGameDisplayPath() {
        assertEquals(30 * MS, DisplayLatency.appliedLatency(30 * MS, 0L));
        assertEquals(14 * MS, DisplayLatency.appliedLatency(30 * MS, 16 * MS));
        assertEquals(0L, DisplayLatency.appliedLatency(12 * MS, 16 * MS));
        assertEquals(0L, DisplayLatency.appliedLatency(0L, 16 * MS));
        assertEquals(30 * MS, DisplayLatency.appliedLatency(30 * MS, -5 * MS));
    }

    @Test
    void unprimedCompensatesOnlyTheScanout() {
        DisplayLatency display = new DisplayLatency();
        assertFalse(display.primed());
        assertEquals(0L, display.pipelineNanos());
        assertEquals(Math.round(0.5E9 / 60.0), display.scanoutNanos());
        assertEquals(30 * MS - display.scanoutNanos(), display.applied(30 * MS));
    }

    @Test
    void firstFrameSetsThePipelineThenSmoothsSlowly() {
        DisplayLatency display = new DisplayLatency();
        long t = 1_000_000_000L;
        display.frameStart(t);
        display.presented(t + 8 * MS);
        assertTrue(display.primed());
        assertEquals(8 * MS, display.pipelineNanos());
        long frame = 16_666_667L;
        for (int i = 1; i <= 6; i++) {
            long start = t + i * frame;
            display.frameStart(start);
            display.presented(start + 4 * MS);
        }
        long smoothed = display.pipelineNanos();
        assertTrue(smoothed < 8 * MS && smoothed > 6 * MS, "smoothed " + smoothed);
        for (int i = 7; i <= 7 + 60 * 10; i++) {
            long start = t + i * frame;
            display.frameStart(start);
            display.presented(start + 4 * MS);
        }
        assertEquals(4 * MS, display.pipelineNanos(), 50_000L);
    }

    @Test
    void hitchesAreClampedAndPresentWithoutStartIsIgnored() {
        DisplayLatency display = new DisplayLatency();
        display.presented(5 * MS);
        assertFalse(display.primed());
        display.frameStart(10 * MS);
        display.presented(10 * MS + 400 * MS);
        assertEquals(DisplayLatency.MAX_SAMPLE_NANOS, display.pipelineNanos());
        display.presented(900 * MS);
        assertEquals(DisplayLatency.MAX_SAMPLE_NANOS, display.pipelineNanos());
        display.frameStart(950 * MS);
        display.presented(940 * MS);
        assertEquals(DisplayLatency.MAX_SAMPLE_NANOS, display.pipelineNanos());
    }

    @Test
    void refreshRateSetsTheMeanScanoutDelay() {
        DisplayLatency display = new DisplayLatency();
        display.refreshHz(144.0);
        assertEquals(Math.round(0.5E9 / 144.0), display.scanoutNanos());
        display.refreshHz(0.0);
        assertEquals(DisplayLatency.DEFAULT_REFRESH_HZ, display.refreshHz());
        display.refreshHz(Double.NaN);
        assertEquals(DisplayLatency.DEFAULT_REFRESH_HZ, display.refreshHz());
        display.refreshHz(5000.0);
        assertEquals(DisplayLatency.MAX_REFRESH_HZ, display.refreshHz());
    }

    @Test
    void compensationIsPipelinePlusScanout() {
        DisplayLatency display = new DisplayLatency();
        display.frameStart(0L);
        display.presented(6 * MS);
        display.refreshHz(60.0);
        assertEquals(6 * MS + display.scanoutNanos(), display.compensationNanos());
        display.frameStart(16 * MS);
        assertEquals(30 * MS - 6 * MS - display.scanoutNanos(), display.applied(30 * MS));
        assertEquals(0L, display.applied(12 * MS));
        display.reset();
        assertFalse(display.primed());
    }

    @Test
    void appliedLatencyStaysFixedWithinAFrame() {
        DisplayLatency display = new DisplayLatency();
        display.frameStart(0L);
        display.presented(10 * MS);
        display.frameStart(16 * MS);
        long first = display.applied(30 * MS);
        display.presented(16 * MS + 2 * MS);
        assertTrue(display.compensationNanos() < 10 * MS + display.scanoutNanos());
        display.frameStart(16 * MS);
        assertEquals(first, display.applied(30 * MS));
        display.presented(40 * MS);
        assertEquals(first, display.applied(30 * MS));
        display.frameStart(33 * MS);
        assertTrue(display.applied(30 * MS) > first);
    }
}
