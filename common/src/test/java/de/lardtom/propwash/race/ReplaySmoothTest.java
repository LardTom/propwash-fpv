package de.lardtom.propwash.race;

import de.lardtom.propwash.dev.CameraPathMeter;
import de.lardtom.propwash.video.record.FrameClock;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplaySmoothTest {
    private static final long MS = 1_000_000L;
    private static final long SECOND = 1_000L * MS;
    private static final long LAP_NANOS = 6L * SECOND;
    private static final long START = 10L * SECOND;
    private static final double RADIUS = 10.0;
    private static final double SPEED = 15.0;
    private static final long SYNC_DELAY = 30L * MS;

    private static double angle(long nanos) {
        return SPEED / RADIUS * nanos * 1.0E-9;
    }

    private static double[] position(long nanos) {
        double a = angle(nanos);
        return new double[]{RADIUS * Math.cos(a), 70.0 + 0.6 * Math.sin(0.9 * nanos * 1.0E-9), RADIUS * Math.sin(a)};
    }

    private static Quaternionf attitude(long nanos) {
        double a = angle(nanos);
        float yaw = (float) (Math.PI - a);
        float bank = (float) (0.35 + 0.1 * Math.sin(1.3 * nanos * 1.0E-9));
        return new Quaternionf().rotateY(yaw).rotateX(-0.3F).rotateZ(bank);
    }

    private static long[] frames(long from, long to, long frame, long jitter, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        int count = (int) ((to - from) / frame) + 2;
        long[] out = new long[count];
        long t = from;
        for (int i = 0; i < count; i++) {
            out[i] = t;
            t += frame + (jitter == 0L ? 0L : random.nextLong(-jitter, jitter + 1L));
        }
        return out;
    }

    private static GhostTrack roundTrip(GhostTrack track) throws IOException {
        return GhostCodec.decode(GhostCodec.encode(track));
    }

    private static CameraPathMeter.Result play(GhostTrack track, boolean smooth) {
        CameraPathMeter meter = new CameraPathMeter();
        double[] at = new double[3];
        Quaternionf rotation = new Quaternionf();
        for (long t = track.beginNanos(); t <= track.durationNanos(); t += CameraPathMeter.FRAME_NANOS) {
            boolean shown = smooth ? track.smoothPose(t, at, rotation) : track.pose(t, at, rotation);
            if (shown) {
                meter.frame(at[0], at[1], at[2], rotation);
            }
        }
        return meter.result();
    }

    private record Laps(GhostTrack ghost, GhostTrack upload) {
    }

    private static Laps pilotLap(long frameNanos, boolean heldAttitude) {
        GhostRecorder recorder = new GhostRecorder();
        long[] frames = frames(START - 2L * SECOND, START + LAP_NANOS + 200L * MS, frameNanos, 2L * MS, 7L);
        boolean started = false;
        Quaternionf held = attitude(START - 3L * SECOND);
        long nextSync = START - 3L * SECOND;
        for (long t : frames) {
            if (!started && t >= START) {
                recorder.onLapStart(1, START);
                started = true;
            }
            if (t >= START + LAP_NANOS) {
                break;
            }
            while (nextSync + SYNC_DELAY <= t) {
                held = attitude(nextSync);
                nextSync += 50L * MS;
            }
            double[] p = position(t);
            recorder.sample(t, p[0], p[1], p[2], heldAttitude ? held : attitude(t));
        }
        GhostTrack ghost = recorder.onLapComplete(LAP_NANOS, START + LAP_NANOS, "race_5", 1);
        GhostTrack upload = recorder.keptLap(1, LAP_NANOS);
        assertNotNull(ghost);
        assertNotNull(upload);
        return new Laps(ghost, upload);
    }

    @Test
    void meterSeesTickStepsInTheAttitude() {
        CameraPathMeter smooth = new CameraPathMeter();
        CameraPathMeter stepped = new CameraPathMeter();
        for (int k = 0; k < 240; k++) {
            long t = k * CameraPathMeter.FRAME_NANOS;
            double[] p = position(t);
            smooth.frame(p[0], p[1], p[2], attitude(t));
            stepped.frame(p[0], p[1], p[2], attitude(t / (50L * MS) * 50L * MS));
        }
        CameraPathMeter.Result fine = smooth.result();
        CameraPathMeter.Result steps = stepped.result();
        assertTrue(fine.smooth(), fine.text());
        assertFalse(steps.smooth(), steps.text());
        assertTrue(steps.rotation().tickShare() > 0.8, steps.text());
        assertTrue(steps.rotation().jumpRatio() > 5.0, steps.text());
        assertTrue(steps.position().smooth(), steps.text());
    }

    @Test
    void heldAttitudeInTheRecordingStaysSteppedInTheReplay() throws IOException {
        Laps laps = pilotLap(CameraPathMeter.FRAME_NANOS, true);
        CameraPathMeter.Result replay = play(roundTrip(laps.upload()), true);
        assertFalse(replay.rotation().smooth(), "Stufen der Lage verschwinden nicht durch die Interpolation: " + replay.text());
        assertTrue(replay.position().smooth(), replay.text());
    }

    @Test
    void pilotLapPlaysSmoothlyAtSixtyFps() throws IOException {
        for (long frame : new long[]{CameraPathMeter.FRAME_NANOS, SECOND / 144L, SECOND / 40L}) {
            Laps laps = pilotLap(frame, false);
            CameraPathMeter.Result replay = play(roundTrip(laps.upload()), true);
            assertTrue(replay.smooth(), "Replay bei " + SECOND / frame + " fps im Spiel: " + replay.text());
            assertTrue(replay.rotation().mean() > 1.0, replay.text());
            CameraPathMeter.Result ghost = play(laps.ghost(), false);
            assertTrue(ghost.smooth(), "Geist im Rennen bei " + SECOND / frame + " fps im Spiel: " + ghost.text());
        }
    }

    @Test
    void serverTrackPlaysSmoothlyAtSixtyFps() throws IOException {
        for (long frame : new long[]{CameraPathMeter.FRAME_NANOS, SECOND / 144L}) {
            GhostRecorder recorder = new GhostRecorder();
            SplittableRandom random = new SplittableRandom(11L);
            long[] frames = frames(START - 2L * SECOND, START + LAP_NANOS + SECOND, frame, 2L * MS, 5L);
            long lastSend = Long.MIN_VALUE;
            boolean started = false;
            for (long t : frames) {
                if (lastSend != Long.MIN_VALUE && t - lastSend < 50L * MS) {
                    continue;
                }
                lastSend = t;
                long arrival = t + 25L * MS + random.nextLong(0L, 4L * MS);
                if (!started && arrival >= START) {
                    recorder.onLapStart(1, START);
                    started = true;
                }
                double[] p = position(t);
                recorder.sample(arrival, p[0], p[1], p[2], attitude(t));
            }
            GhostTrack track = recorder.cut(START, START + LAP_NANOS, LAP_NANOS, "race_5", 1, GhostServer.FINE_INTERVAL_NANOS, GhostRecorder.LEAD_NANOS);
            assertNotNull(track);
            CameraPathMeter.Result replay = play(roundTrip(track), true);
            assertTrue(replay.smooth(), "Server-Spur, Pilot mit " + SECOND / frame + " fps: " + replay.text());
        }
    }

    private static List<Long> exported(int gameFps, long renderNanos) {
        FrameClock clock = new FrameClock(CameraPathMeter.FPS);
        SplittableRandom random = new SplittableRandom(gameFps);
        clock.start(0L);
        List<Long> shown = new ArrayList<>();
        long frame = SECOND / gameFps;
        for (long t = 0L; t < 6L * SECOND; t += frame) {
            long start = t + random.nextLong(0L, MS + 1L);
            long content = clock.nextSlotNanos(start);
            long end = start + renderNanos;
            if (!clock.due(end)) {
                continue;
            }
            long slot = clock.take(end);
            while (shown.size() < slot) {
                shown.add(shown.isEmpty() ? 0L : shown.getLast());
            }
            shown.add(content);
        }
        return shown;
    }

    @Test
    void exportShowsOneVideoFrameOfReplayTimePerFrame() {
        for (int gameFps : new int[]{60, 75, 90, 120, 144, 165, 240}) {
            List<Long> shown = exported(gameFps, 3L * MS);
            long worst = 0L;
            for (int k = 30; k < shown.size(); k++) {
                worst = Math.max(worst, Math.abs(shown.get(k) - shown.get(k - 1) - CameraPathMeter.FRAME_NANOS));
            }
            assertTrue(worst <= MS, "Spiel mit " + gameFps + " fps: Replay-Zeit je Videobild weicht bis " + worst / 1000L + " µs ab");
            assertEquals(0L, shown.get(0));
        }
    }
}
