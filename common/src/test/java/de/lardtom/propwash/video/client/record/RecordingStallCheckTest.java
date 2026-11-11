package de.lardtom.propwash.video.client.record;

import de.lardtom.propwash.video.record.PipelineStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingStallCheckTest {
    private static final long CAPTURE_NANOS = 202_000L;
    private static final long READBACK_NANOS = 302_000L;

    private static PipelineStats recording(int frames, int slowCaptures, double captureMs, int slowReadbacks, double readbackMs) {
        PipelineStats stats = new PipelineStats(60);
        for (int i = 0; i < frames; i++) {
            stats.renderFrame(8_333_333L);
            stats.slot(0L);
            stats.captured(slow(i, frames, slowCaptures) ? Math.round(captureMs * 1.0E6) : CAPTURE_NANOS);
            stats.readback(slow(i, frames, slowReadbacks) ? Math.round(readbackMs * 1.0E6) : READBACK_NANOS, 0L);
        }
        return stats;
    }

    private static boolean slow(int frame, int frames, int count) {
        if (count <= 0) {
            return false;
        }
        int spacing = frames / count;
        return frame % spacing == spacing / 2 && frame / spacing < count;
    }

    @Test
    void singleSlowReadbackFromTheFullRunIsNotedNotFailed() {
        RecordingStallCheck.Result result = RecordingStallCheck.check(recording(484, 0, 0.0, 1, 26.95).snapshot());
        assertNull(result.failure(), result.failure());
        assertNotNull(result.outliers());
        assertTrue(result.outliers().contains("Auslesen 1 von 484"), result.outliers());
        assertTrue(result.outliers().contains("26.95"), result.outliers());
    }

    @Test
    void slowFramesUpToOnePercentAreNotedNotFailed() {
        RecordingStallCheck.Result result = RecordingStallCheck.check(recording(480, 0, 0.0, 4, 30.0).snapshot());
        assertNull(result.failure(), result.failure());
        assertNotNull(result.outliers());
        assertTrue(result.outliers().contains("Auslesen 4 von 480"), result.outliers());
    }

    @Test
    void moreThanOnePercentSlowReadbacksStillFail() {
        RecordingStallCheck.Result result = RecordingStallCheck.check(recording(480, 0, 0.0, 5, 30.0).snapshot());
        assertNotNull(result.failure());
        assertTrue(result.failure().startsWith("Renderthread hängt"), result.failure());
    }

    @Test
    void repeatedSlowRequestsStillFail() {
        RecordingStallCheck.Result result = RecordingStallCheck.check(recording(480, 10, 40.0, 0, 0.0).snapshot());
        assertNotNull(result.failure());
        assertTrue(result.failure().startsWith("Renderthread hängt"), result.failure());
    }

    @Test
    void hangOnEveryFrameStillFails() {
        RecordingStallCheck.Result result = RecordingStallCheck.check(recording(480, 0, 0.0, 480, 26.0).snapshot());
        assertNotNull(result.failure());
        assertTrue(result.failure().startsWith("Renderthread hängt"), result.failure());
    }

    @Test
    void failureNamesThePercentileAndTheSlowFrames() {
        RecordingStallCheck.Result result = RecordingStallCheck.check(recording(480, 0, 0.0, 12, 30.0).snapshot());
        assertNotNull(result.failure());
        assertTrue(result.failure().contains("99. Perzentil"), result.failure());
        assertTrue(result.failure().contains("Auslesen 12 von 480"), result.failure());
    }

    @Test
    void quietRecordingHasNoNote() {
        RecordingStallCheck.Result result = RecordingStallCheck.check(recording(600, 0, 0.0, 0, 0.0).snapshot());
        assertNull(result.failure(), result.failure());
        assertNull(result.outliers(), result.outliers());
    }
}
