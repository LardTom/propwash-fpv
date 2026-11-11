package de.lardtom.propwash.video.record;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PipelineStatsTest {
    @Test
    void percentileIsTheUpperEdgeOfItsBucketAndNeverAboveTheMaximum() {
        PipelineStats stats = new PipelineStats(60);
        for (int i = 0; i < 99; i++) {
            stats.readback(300_000L, 0L);
        }
        stats.readback(26_950_000L, 0L);
        PipelineStats.Snapshot snapshot = stats.snapshot();
        assertEquals(0.5, snapshot.readbackMsAt(0.99), 1.0E-9);
        assertEquals(26.95, snapshot.readbackMsAt(1.0), 1.0E-9);
        assertEquals(0.0, snapshot.captureMsAt(0.99), 1.0E-9);
    }

    @Test
    void percentileCountsTheSlowestOnePercent() {
        PipelineStats stats = new PipelineStats(60);
        for (int i = 0; i < 480; i++) {
            stats.captured(i < 5 ? 30_000_000L : 200_000L);
        }
        assertEquals(30.0, stats.snapshot().captureMsAt(0.99), 1.0E-9);
        PipelineStats fewer = new PipelineStats(60);
        for (int i = 0; i < 480; i++) {
            fewer.captured(i < 4 ? 30_000_000L : 200_000L);
        }
        assertEquals(0.25, fewer.snapshot().captureMsAt(0.99), 1.0E-9);
    }

    @Test
    void framesBeyondTheLastBucketUseTheMaximum() {
        PipelineStats stats = new PipelineStats(60);
        stats.captured(500_000_000L);
        assertEquals(500.0, stats.snapshot().captureMsAt(0.5), 1.0E-9);
        assertEquals(1L, stats.snapshot().capturesOver(25.0));
        assertEquals(0L, stats.snapshot().capturesOver(500.0));
    }

    @Test
    void countOverTheLimitUsesQuarterMillisecondBuckets() {
        PipelineStats stats = new PipelineStats(60);
        stats.readback(24_990_000L, 0L);
        stats.readback(25_010_000L, 0L);
        stats.readback(26_950_000L, 0L);
        assertEquals(2L, stats.snapshot().readbacksOver(25.0));
        assertEquals(1L, stats.snapshot().readbacksOver(26.0));
        PipelineStats atLimit = new PipelineStats(60);
        atLimit.readback(25_000_000L, 0L);
        assertEquals(0L, atLimit.snapshot().readbacksOver(25.0));
    }

    @Test
    void windowCountsOnlyTheFramesSinceTheEarlierSnapshot() {
        PipelineStats stats = new PipelineStats(60);
        stats.readback(30_000_000L, 0L);
        PipelineStats.Snapshot earlier = stats.snapshot();
        for (int i = 0; i < 100; i++) {
            stats.readback(300_000L, 0L);
        }
        PipelineStats.Snapshot window = stats.snapshot().since(earlier);
        assertEquals(0L, window.readbacksOver(25.0));
        assertEquals(0.5, window.readbackMsAt(1.0), 1.0E-9);
        assertEquals(1L, stats.snapshot().readbacksOver(25.0));
    }
}
