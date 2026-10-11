package de.lardtom.propwash.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpactJoltTest {
    private static final long MS = 1_000_000L;
    private static final long T0 = 5_000_000_000L;

    private static double peak(ImpactJolt jolt, long from, long to, float intensity, int axis) {
        double[] out = new double[3];
        double peak = 0.0;
        for (long t = from; t <= to; t += MS) {
            jolt.sample(t, intensity, out);
            peak = Math.max(peak, Math.abs(out[axis]));
        }
        return peak;
    }

    @Test
    void amplitudeGrowsWithTheLostSpeedAndIsCapped() {
        assertEquals(0.0, ImpactJolt.amplitude(0.1), 0.0);
        assertEquals(0.0, ImpactJolt.amplitude(Double.NaN), 0.0);
        assertEquals(ImpactJolt.DEG_PER_MPS * 4.0, ImpactJolt.amplitude(4.0), 1.0E-12);
        assertEquals(ImpactJolt.MAX_DEG, ImpactJolt.amplitude(40.0), 0.0);
        assertTrue(ImpactJolt.amplitude(2.0) < ImpactJolt.amplitude(5.0));
    }

    @Test
    void nothingBeforeTheHitReachesThePicture() {
        ImpactJolt jolt = new ImpactJolt();
        double[] out = new double[3];
        assertFalse(jolt.sample(T0, 1.0F, out));
        assertTrue(jolt.hit(T0, 5.0, 1L));
        assertFalse(jolt.sample(T0 - 30 * MS, 1.0F, out), "das Bild zeigt den Schlag erst, wenn es den Durchbruch zeigt");
        assertFalse(jolt.active(T0 - MS));
        assertTrue(jolt.active(T0 + 100 * MS));
        assertEquals(0.0, out[0], 0.0);
        assertTrue(jolt.sample(T0 + 19 * MS, 1.0F, out));
        assertTrue(Math.abs(out[0]) > 0.6 * ImpactJolt.amplitude(5.0), "nach einem Bild steht der Schlag im Nicken: " + out[0]);
    }

    @Test
    void theJoltIsShortAndStrongestInPitch() {
        ImpactJolt jolt = new ImpactJolt();
        jolt.hit(T0, 5.0, 7L);
        double amplitude = ImpactJolt.amplitude(5.0);
        double pitch = peak(jolt, T0, T0 + 150 * MS, 1.0F, 0);
        double yaw = peak(jolt, T0, T0 + 150 * MS, 1.0F, 1);
        double roll = peak(jolt, T0, T0 + 150 * MS, 1.0F, 2);
        assertTrue(pitch > 0.7 * amplitude && pitch <= amplitude, "Spitze " + pitch + " von " + amplitude);
        assertTrue(roll < pitch && yaw < roll, "Nicken vor Rollen vor Gieren: " + pitch + " " + roll + " " + yaw);
        assertTrue(peak(jolt, T0 + 400 * MS, T0 + 600 * MS, 1.0F, 0) < 0.05 * amplitude, "nach 0,4 s ist es vorbei");
        double[] out = new double[3];
        assertFalse(jolt.sample(T0 + 2_000 * MS, 1.0F, out));
        assertFalse(jolt.active(T0 + 2_000 * MS));
    }

    @Test
    void followsTheVibrationSetting() {
        ImpactJolt jolt = new ImpactJolt();
        jolt.hit(T0, 5.0, 7L);
        double[] out = new double[3];
        assertFalse(jolt.sample(T0 + 19 * MS, 0.0F, out), "Kamera-Vibration 0 % schaltet auch den Schlag ab");
        assertEquals(0.0, out[0], 0.0);
        double normal = peak(jolt, T0, T0 + 150 * MS, 1.0F, 0);
        double doubled = peak(jolt, T0, T0 + 150 * MS, 2.0F, 0);
        assertEquals(2.0 * normal, doubled, 1.0E-9);
        assertEquals(doubled, peak(jolt, T0, T0 + 150 * MS, 9.0F, 0), 1.0E-9, "begrenzt wie die Vibration auf 200 %");
    }

    @Test
    void severalHitsAddUpWithinTheLimitAndResetClears() {
        ImpactJolt jolt = new ImpactJolt();
        for (int i = 0; i < 6; i++) {
            jolt.hit(T0, 40.0, i);
        }
        assertEquals(6, jolt.hits());
        assertEquals(ImpactJolt.MAX_DEG, jolt.lastAmplitude(), 0.0);
        double[] out = new double[3];
        for (long t = T0; t < T0 + 200 * MS; t += MS) {
            jolt.sample(t, 1.0F, out);
            for (double value : out) {
                assertTrue(Math.abs(value) <= ImpactJolt.MAX_DEG + 1.0E-9);
            }
        }
        jolt.reset();
        assertFalse(jolt.sample(T0 + 19 * MS, 1.0F, out));
        assertEquals(0, jolt.hits());
        assertFalse(jolt.hit(T0, 0.0, 1L), "ohne Tempoverlust kein Schlag");
    }

    @Test
    void sameHitSamePicture() {
        ImpactJolt a = new ImpactJolt();
        ImpactJolt b = new ImpactJolt();
        a.hit(T0, 3.0, 99L);
        b.hit(T0, 3.0, 99L);
        double[] x = new double[3];
        double[] y = new double[3];
        for (long t = T0; t < T0 + 300 * MS; t += 7 * MS) {
            a.sample(t, 1.0F, x);
            b.sample(t, 1.0F, y);
            assertEquals(x[0], y[0], 0.0);
            assertEquals(x[1], y[1], 0.0);
            assertEquals(x[2], y[2], 0.0);
        }
    }
}
