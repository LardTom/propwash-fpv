package de.lardtom.propwash.race;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostRecorderTest {
    private static final long MS = 1_000_000L;

    private static void fly(GhostRecorder recorder, long from, long to, double speed) {
        Quaternionf level = new Quaternionf();
        for (long t = from; t <= to; t += 10 * MS) {
            recorder.sample(t, t / 1.0E9 * speed, 64.0, 0.0, level);
        }
    }

    @Test
    void lapIsResampledOnFixedGrid() {
        GhostRecorder recorder = new GhostRecorder();
        recorder.onLapStart(4, 0L);
        fly(recorder, 0L, 2_050L * MS, 10.0);
        GhostTrack track = recorder.onLapComplete(2_000L * MS, 2_000L * MS, "race_5", 42);
        assertNotNull(track);
        assertEquals(GhostTrack.INTERVAL_NANOS, track.intervalNanos());
        assertEquals(41, track.samples());
        assertEquals(2_000L * MS, track.durationNanos());
        assertEquals(2_000L * MS, track.lapNanos());
        assertEquals("race_5", track.frame());
        assertEquals(42, track.layout());
        double[] position = new double[3];
        Quaternionf rotation = new Quaternionf();
        assertTrue(track.pose(1_010L * MS, position, rotation));
        assertEquals(10.1, position[0], 1.0E-6);
        assertEquals(64.0, position[1], 1.0E-9);
        assertFalse(track.pose(2_500L * MS, position, rotation));
        assertFalse(track.pose(-1L, position, rotation));
    }

    @Test
    void startBetweenSamplesIsInterpolated() {
        GhostRecorder recorder = new GhostRecorder();
        fly(recorder, 0L, 100L * MS, 10.0);
        recorder.onLapStart(1, 5L * MS);
        fly(recorder, 110L * MS, 1_100L * MS, 10.0);
        GhostTrack track = recorder.onLapComplete(1_000L * MS, 1_005L * MS, "", 0);
        assertNotNull(track);
        assertEquals(0.05, track.x(0), 1.0E-9);
        assertEquals(10.05, track.x(track.samples() - 1), 1.0E-9);
    }

    @Test
    void consecutiveLapsStartAtTheCrossing() {
        GhostRecorder recorder = new GhostRecorder();
        recorder.onLapStart(1, 0L);
        fly(recorder, 0L, 3_000L * MS, 10.0);
        GhostTrack first = recorder.onLapComplete(1_000L * MS, 1_000L * MS, "", 0);
        GhostTrack second = recorder.onLapComplete(1_500L * MS, 2_500L * MS, "", 0);
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(10.0, second.x(0), 1.0E-9);
        assertEquals(25.0, second.x(second.samples() - 1), 1.0E-9);
    }

    @Test
    void idleHistoryIsTrimmed() {
        GhostRecorder recorder = new GhostRecorder();
        fly(recorder, 0L, 5_000L * MS, 10.0);
        assertTrue(recorder.recordedSamples() <= 102, "Proben: " + recorder.recordedSamples());
        assertFalse(recorder.recording());
    }

    @Test
    void stoppedRecorderReturnsNoTrack() {
        GhostRecorder recorder = new GhostRecorder();
        recorder.onLapStart(1, 0L);
        fly(recorder, 0L, 1_000L * MS, 10.0);
        recorder.stop();
        assertNull(recorder.onLapComplete(1_000L * MS, 1_000L * MS, "", 0));
    }

    @Test
    void serverCutUsesArbitraryWindow() {
        GhostRecorder recorder = new GhostRecorder();
        recorder.onLapStart(3, 0L);
        Quaternionf level = new Quaternionf();
        for (long t = 0L; t <= 4_000L * MS; t += 50 * MS) {
            recorder.sample(t, 0.0, 64.0, t / 1.0E9 * 5.0, level);
        }
        GhostTrack track = recorder.cut(1_000L * MS, 3_000L * MS, 2_100L * MS, "whoop_65", 7);
        assertNotNull(track);
        assertEquals(5.0, track.z(0), 1.0E-9);
        assertEquals(15.0, track.z(track.samples() - 1), 1.0E-9);
        assertEquals(2_100L * MS, track.lapNanos());
        recorder.trim(3_000L * MS);
        assertNull(recorder.cut(1_000L * MS, 3_000L * MS, 2_000L * MS, "", 0));
    }
}
