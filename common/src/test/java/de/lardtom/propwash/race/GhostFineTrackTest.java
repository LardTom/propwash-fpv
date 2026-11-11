package de.lardtom.propwash.race;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostFineTrackTest {
    private static final long MS = 1_000_000L;

    @Test
    void serverCutUsesTheFineGrid() {
        GhostRecorder recorder = new GhostRecorder();
        recorder.onLapStart(1, 0L);
        Quaternionf level = new Quaternionf();
        for (long t = 0L; t <= 10_100L * MS; t += 50 * MS) {
            recorder.sample(t, t / 1.0E9 * 20.0, 64.0, 0.0, level);
        }
        GhostTrack track = recorder.cut(0L, 10_000L * MS, 10_000L * MS, "race_5", 3, GhostServer.FINE_INTERVAL_NANOS);
        assertNotNull(track);
        assertEquals(GhostServer.FINE_INTERVAL_NANOS, track.intervalNanos());
        assertEquals(501, track.samples());
        byte[] data = GhostCodec.encode(track);
        assertTrue(data.length < 501 * 6, "Spur zu groß: " + data.length);
    }

    @Test
    void longLapsKeepTheSampleLimit() {
        long tenMinutes = GhostTrack.MAX_DURATION_NANOS;
        long interval = GhostTrack.intervalFor(tenMinutes, GhostServer.FINE_INTERVAL_NANOS);
        assertTrue(interval >= GhostTrack.INTERVAL_NANOS);
        assertTrue(tenMinutes / interval + 1 <= GhostTrack.MAX_SAMPLES);
        assertEquals(GhostServer.FINE_INTERVAL_NANOS, GhostTrack.intervalFor(60_000L * MS, GhostServer.FINE_INTERVAL_NANOS));
    }

    @Test
    void jitteryArrivalTimesAreEvenedOut() {
        long[] times = new long[40];
        for (int i = 0; i < times.length; i++) {
            times[i] = i * 50 * MS + (i % 2 == 0 ? 12 * MS : -12 * MS);
        }
        long[] steady = GhostRecorder.steady(times, times.length);
        long worst = 0L;
        for (int i = 5; i < times.length - 5; i++) {
            long step = steady[i] - steady[i - 1];
            worst = Math.max(worst, Math.abs(step - 50 * MS));
        }
        assertTrue(worst < 8 * MS, "Abstände schwanken noch um " + worst / MS + " ms");
        for (int i = 1; i < steady.length; i++) {
            assertTrue(steady[i] > steady[i - 1]);
        }
    }

    @Test
    void gapsInTheRecordingStaySeparate() {
        long[] times = {0L, 50 * MS, 100 * MS, 150 * MS, 900 * MS, 950 * MS, 1000 * MS, 1050 * MS};
        long[] steady = GhostRecorder.steady(times, times.length);
        assertTrue(steady[4] - steady[3] > 500 * MS);
    }

    @Test
    void smoothPoseFollowsTheTrackWithoutJumps() {
        int samples = 51;
        double[] positions = new double[samples * 3];
        float[] rotations = new float[samples * 4];
        for (int i = 0; i < samples; i++) {
            positions[i * 3] = i * 0.4;
            positions[i * 3 + 1] = 64.0;
            Quaternionf q = new Quaternionf().rotateY(i * 0.05F);
            rotations[i * 4] = q.x;
            rotations[i * 4 + 1] = q.y;
            rotations[i * 4 + 2] = q.z;
            rotations[i * 4 + 3] = q.w;
        }
        GhostTrack track = new GhostTrack(GhostServer.FINE_INTERVAL_NANOS, positions, rotations, 1_000L * MS, "race_5", 1);
        double[] position = new double[3];
        Quaternionf rotation = new Quaternionf();
        double last = -1.0;
        for (long t = 0L; t <= 1_000L * MS; t += 5 * MS) {
            assertTrue(track.smoothPose(t, position, rotation));
            assertTrue(position[0] >= last - 1.0E-9);
            assertEquals(1.0, rotation.lengthSquared(), 1.0E-4);
            last = position[0];
        }
        assertEquals(20.0, position[0], 1.0E-6);
    }
}
