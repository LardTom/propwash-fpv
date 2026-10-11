package de.lardtom.propwash.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraShakeTest {
    private static final long FRAME = 16_666_667L;
    private static final long START = 5_000_000_000L;

    private static long run(CameraShake shake, long from, int frames, double ratio, double wear, double wash, double recirculation, boolean spinning) {
        long t = from;
        for (int i = 0; i < frames; i++) {
            t += FRAME;
            shake.update(t, ratio, wear, wash, recirculation, spinning);
        }
        return t;
    }

    private static double rms(CameraShake shake, long from, long to, float intensity, int axis) {
        double[] out = new double[3];
        double sum = 0.0;
        int n = 0;
        for (long t = from; t <= to; t += 1_000_000L) {
            shake.sample(t, intensity, out);
            sum += out[axis] * out[axis];
            n++;
        }
        return Math.sqrt(sum / n);
    }

    @Test
    void offWhenIntensityIsZeroOrMotorsStand() {
        CameraShake shake = new CameraShake();
        long t = run(shake, START, 120, 1.0, 0.0, 0.5, 0.0, true);
        double[] out = new double[3];
        assertFalse(shake.sample(t, 0.0F, out));
        assertArrayEquals(new double[]{0.0, 0.0, 0.0}, out);
        long stopped = run(shake, t, 240, 0.0, 0.0, 0.0, 0.0, false);
        assertTrue(shake.idle());
        assertFalse(shake.sample(stopped, 1.0F, out));
        CameraShake fresh = new CameraShake();
        assertFalse(fresh.sample(START, 1.0F, out));
    }

    @Test
    void healthyMotorBuzzStaysAtHoverLevelAboveHoverRpm() {
        assertEquals(CameraShake.BUZZ_HOVER_DEG, CameraShake.buzzDegrees(1.0, 0.0), 1.0E-12);
        assertEquals(CameraShake.BUZZ_HOVER_DEG, CameraShake.buzzDegrees(2.0, 0.0), 1.0E-12);
        assertEquals(CameraShake.BUZZ_HOVER_DEG, CameraShake.buzzDegrees(3.7, 0.0), 1.0E-12);
        assertEquals(0.25 * CameraShake.BUZZ_HOVER_DEG, CameraShake.buzzDegrees(0.5, 0.0), 1.0E-12);
        assertEquals(0.0, CameraShake.buzzDegrees(0.0, 0.0));
        assertEquals(0.0, CameraShake.buzzDegrees(Double.NaN, 0.0));
        CameraShake hover = new CameraShake();
        long a = run(hover, START, 120, 1.0, 0.0, 0.0, 0.0, true);
        CameraShake fullThrottle = new CameraShake();
        long b = run(fullThrottle, START, 120, 3.3, 0.0, 0.0, 0.0, true);
        double hoverRms = rms(hover, a - 500_000_000L, a, 1.0F, 0);
        double fullRms = rms(fullThrottle, b - 500_000_000L, b, 1.0F, 0);
        assertTrue(hoverRms > 0.005 && hoverRms < 0.05, "hover " + hoverRms);
        assertEquals(1.0, fullRms / hoverRms, 1.0E-6);
    }

    @Test
    void wornPartsAddBuzzThatGrowsWithRpm() {
        assertEquals(CameraShake.BUZZ_HOVER_DEG * (1.0 + CameraShake.WEAR_GAIN * 1.2), CameraShake.buzzDegrees(1.0, 1.2), 1.0E-12);
        assertTrue(CameraShake.buzzDegrees(1.0, 1.2) > 1.8 * CameraShake.buzzDegrees(1.0, 0.0));
        assertEquals(CameraShake.BUZZ_HOVER_DEG * (1.0 + 4.0 * CameraShake.WEAR_GAIN * 0.5), CameraShake.buzzDegrees(2.0, 0.5), 1.0E-12);
        assertEquals(CameraShake.BUZZ_HOVER_DEG + CameraShake.WEAR_BUZZ_MAX_DEG * CameraShake.WEAR_GAIN * 4.0, CameraShake.buzzDegrees(3.0, 4.0),
                1.0E-12);
        assertTrue(CameraShake.buzzDegrees(3.0, 0.7) > 3.0 * CameraShake.buzzDegrees(3.0, 0.0));
        assertEquals(CameraShake.buzzDegrees(2.0, 0.0), CameraShake.buzzDegrees(2.0, -1.0), 1.0E-12);
    }

    @Test
    void recirculationFadesWithAirspeed() {
        double induced = 7.2;
        assertEquals(1.0, CameraShake.recirculationFade(0.0, induced, 1.0), 1.0E-12);
        assertEquals(0.5 * (Math.sqrt(5.0) - 1.0) / (0.5 * (Math.sqrt(5.0) - 1.0) + 1.0), CameraShake.recirculationFade(induced, induced, 1.0), 1.0E-12);
        assertTrue(CameraShake.recirculationFade(1.2, induced, 1.0) > 0.95);
        assertTrue(CameraShake.recirculationFade(2.0 * induced, induced, 1.0) < 0.06);
        assertTrue(CameraShake.recirculationFade(40.0, induced, 3.3) < 0.12);
        assertTrue(CameraShake.recirculationFade(40.0, induced, 3.3) > CameraShake.recirculationFade(40.0, induced, 1.0));
        double previous = 1.0;
        for (double v = 0.5; v <= 60.0; v += 0.5) {
            double fade = CameraShake.recirculationFade(v, induced, 1.5);
            assertTrue(fade < previous && fade > 0.0, "speed " + v);
            previous = fade;
        }
        assertEquals(1.0, CameraShake.recirculationFade(0.0, 0.0, 1.0));
        assertEquals(0.0, CameraShake.recirculationFade(5.0, 0.0, 1.0));
        assertEquals(0.0, CameraShake.recirculationFade(5.0, induced, 0.0));
        assertEquals(1.0, CameraShake.recirculationFade(Double.NaN, induced, 1.0));
        assertEquals(1.0, CameraShake.recirculationFade(-3.0, induced, 1.0));
    }

    @Test
    void propWashAndRecirculationBuffet() {
        assertEquals(0.0, CameraShake.buffetDegrees(1.0, 0.0, 0.0));
        assertEquals(CameraShake.BUFFET_DEG, CameraShake.buffetDegrees(1.0, 1.0, 0.0), 1.0E-12);
        assertEquals(CameraShake.FLOW_CAP * (CameraShake.BUFFET_DEG + CameraShake.RECIRCULATION_DEG), CameraShake.buffetDegrees(9.0, 2.0, 2.0), 1.0E-12);
        CameraShake calm = new CameraShake();
        long a = run(calm, START, 120, 1.0, 0.0, 0.0, 0.0, true);
        CameraShake washed = new CameraShake();
        long b = run(washed, START, 120, 1.0, 0.0, 0.8, 0.0, true);
        assertTrue(rms(washed, b - 500_000_000L, b, 1.0F, 2) > 3.0 * rms(calm, a - 500_000_000L, a, 1.0F, 2));
        assertEquals(0.8 * CameraShake.BUFFET_DEG, washed.buffetLevel(), 1.0E-3);
    }

    @Test
    void punchOutIsATransientThatDecays() {
        CameraShake shake = new CameraShake();
        long t = run(shake, START, 60, 1.0, 0.0, 0.0, 0.0, true);
        assertEquals(0.0, shake.punchLevel());
        t = run(shake, t, 6, 2.2, 0.0, 0.0, 0.0, true);
        assertTrue(shake.punchLevel() > 0.5 * CameraShake.PUNCH_DEG, "punch " + shake.punchLevel());
        run(shake, t, 90, 2.2, 0.0, 0.0, 0.0, true);
        assertTrue(shake.punchLevel() < 0.01 * CameraShake.PUNCH_DEG, "decayed " + shake.punchLevel());
    }

    @Test
    void gentleThrottleWorkDoesNotJolt() {
        CameraShake shake = new CameraShake();
        long t = run(shake, START, 60, 2.0, 0.0, 0.0, 0.0, true);
        double ratio = 2.0;
        double step = 0.9 * CameraShake.PUNCH_RATE_START * FRAME * 1.0E-9;
        for (int cycle = 0; cycle < 6; cycle++) {
            for (int i = 0; i < 20; i++) {
                ratio += step;
                t += FRAME;
                shake.update(t, ratio, 0.0, 0.0, 0.0, true);
                assertEquals(0.0, shake.punchLevel());
            }
            for (int i = 0; i < 20; i++) {
                ratio -= step;
                t += FRAME;
                shake.update(t, ratio, 0.0, 0.0, 0.0, true);
                assertEquals(0.0, shake.punchLevel());
            }
        }
        assertEquals(0.0, CameraShake.punchExcess(0.02, 0.01));
        assertEquals(0.5 - CameraShake.PUNCH_RATE_START * 0.01, CameraShake.punchExcess(0.5, 0.01), 1.0E-12);
        assertEquals(0.0, CameraShake.punchExcess(-1.0, 0.01));
        assertEquals(0.0, CameraShake.punchExcess(Double.NaN, 0.01));
    }

    @Test
    void joltScalesWithTheSizeOfTheThrottleStab() {
        double[] rises = {0.1, 0.3, 0.6, 1.5};
        double[] levels = new double[rises.length];
        for (int k = 0; k < rises.length; k++) {
            CameraShake shake = new CameraShake();
            long t = run(shake, START, 60, 2.0, 0.0, 0.0, 0.0, true);
            run(shake, t, 2, 2.0 + rises[k], 0.0, 0.0, 0.0, true);
            levels[k] = shake.punchLevel();
        }
        assertTrue(levels[0] < 0.1 * CameraShake.PUNCH_DEG, "small " + levels[0]);
        assertTrue(levels[1] > levels[0] && levels[2] > levels[1] && levels[3] > levels[2]);
        assertTrue(levels[2] < 0.8 * CameraShake.PUNCH_DEG, "medium " + levels[2]);
        assertEquals(CameraShake.PUNCH_DEG, levels[3], 1.0E-12);
    }

    @Test
    void joltDoesNotDependOnTheFrameRate() {
        double[] peaks = new double[2];
        long[] frames = {16_666_667L, 4_166_667L};
        for (int k = 0; k < 2; k++) {
            CameraShake shake = new CameraShake();
            long t = START;
            for (long elapsed = 0L; elapsed < 600_000_000L; elapsed += frames[k]) {
                t += frames[k];
                double seconds = elapsed * 1.0E-9;
                double ratio = 1.5 + 0.7 * Math.min(1.0, Math.max(0.0, (seconds - 0.3) / 0.08));
                shake.update(t, ratio, 0.0, 0.0, 0.0, true);
                peaks[k] = Math.max(peaks[k], shake.punchLevel());
            }
        }
        assertTrue(peaks[0] > 0.2 * CameraShake.PUNCH_DEG && peaks[0] < 0.8 * CameraShake.PUNCH_DEG, "peak " + peaks[0]);
        assertEquals(1.0, peaks[0] / peaks[1], 0.12);
    }

    @Test
    void sampleUsesTheLevelsOfTheDisplayedTime() {
        CameraShake shake = new CameraShake();
        long calm = run(shake, START, 30, 1.0, 0.0, 0.0, 0.0, true);
        long washed = run(shake, calm, 30, 1.0, 0.0, 1.0, 0.0, true);
        assertTrue(rms(shake, calm - 200_000_000L, calm - 30_000_000L, 1.0F, 0) < 0.6 * rms(shake, washed - 200_000_000L, washed, 1.0F, 0));
    }

    @Test
    void boundedDeterministicAndYawIsSmaller() {
        CameraShake a = new CameraShake();
        CameraShake b = new CameraShake();
        long ta = run(a, START, 200, 3.0, 4.0, 1.0, 1.0, true);
        run(b, START, 200, 3.0, 4.0, 1.0, 1.0, true);
        double[] outA = new double[3];
        double[] outB = new double[3];
        for (long t = ta - 1_000_000_000L; t <= ta; t += 3_000_000L) {
            a.sample(t, CameraShake.MAX_INTENSITY, outA);
            b.sample(t, CameraShake.MAX_INTENSITY, outB);
            assertArrayEquals(outA, outB);
            for (double v : outA) {
                assertTrue(Math.abs(v) <= CameraShake.MAX_TOTAL_DEG * CameraShake.MAX_INTENSITY + 1.0E-9);
            }
        }
        double pitch = rms(a, ta - 1_000_000_000L, ta, 1.0F, 0);
        double yaw = rms(a, ta - 1_000_000_000L, ta, 1.0F, 1);
        assertTrue(yaw < 0.6 * pitch, "yaw " + yaw + " pitch " + pitch);
    }

    @Test
    void intensityScalesLinearly() {
        CameraShake shake = new CameraShake();
        long t = run(shake, START, 120, 1.5, 0.0, 0.3, 0.2, true);
        double full = rms(shake, t - 400_000_000L, t, 1.0F, 0);
        double half = rms(shake, t - 400_000_000L, t, 0.5F, 0);
        assertEquals(0.5, half / full, 1.0E-9);
    }

    @Test
    void rmsDoesNotDependOnTheFrameRate() {
        CameraShake shake = new CameraShake();
        long t = run(shake, START, 200, 2.0, 0.0, 0.5, 0.0, true);
        double[] out = new double[3];
        double[] rms = new double[2];
        long[] steps = {16_666_667L, 6_944_444L};
        for (int k = 0; k < 2; k++) {
            double sum = 0.0;
            int n = 0;
            for (long s = t - 1_500_000_000L; s <= t; s += steps[k]) {
                shake.sample(s, 1.0F, out);
                sum += out[0] * out[0];
                n++;
            }
            rms[k] = Math.sqrt(sum / n);
        }
        assertEquals(1.0, rms[0] / rms[1], 0.25);
    }

    @Test
    void noiseIsContinuousAndInRange() {
        double previous = CameraShake.noise(0.0, 7L);
        for (double x = 0.001; x < 50.0; x += 0.001) {
            double v = CameraShake.noise(x, 7L);
            assertTrue(v >= -1.0 && v <= 1.0);
            assertTrue(Math.abs(v - previous) < 0.01, "jump at " + x);
            previous = v;
        }
    }
}
