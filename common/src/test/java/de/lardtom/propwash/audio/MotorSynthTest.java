package de.lardtom.propwash.audio;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotorSynthTest {
    private static final int RATE = MotorSynth.SAMPLE_RATE;
    private static final int CHUNK = 720;
    private static final String PILOT_MIX = "e704e7c18f0d44f3881b38c4";

    @Test
    void samplesStayBoundedForEveryPresetAndHostileInput() {
        Random random = new Random(7);
        float[] out = new float[CHUNK];
        short[] pcm = new short[CHUNK];
        for (DroneBuild build : DroneBuild.PRESETS) {
            MotorControl control = new MotorControl();
            MotorSynth synth = new MotorSynth(MotorTimbre.of(build), control, 11L);
            for (int chunk = 0; chunk < 400; chunk++) {
                for (int m = 0; m < MotorControl.MOTORS; m++) {
                    double rpm = switch (chunk % 5) {
                        case 0 -> random.nextDouble() * 300_000.0;
                        case 1 -> Double.NaN;
                        case 2 -> -5_000.0;
                        case 3 -> Double.POSITIVE_INFINITY;
                        default -> random.nextDouble() * 60_000.0;
                    };
                    control.setMotor(m, rpm, random.nextDouble() * 3.0 - 1.0);
                }
                control.setWash(chunk % 7 == 0 ? Double.NaN : random.nextDouble() * 2.0);
                control.setDoppler(chunk % 11 == 0 ? 0.0 : 0.5 + random.nextDouble() * 2.0);
                control.setGain(chunk % 13 == 0 ? 50.0 : random.nextDouble() * 2.0);
                control.setBrightnessHz(random.nextDouble() * 30_000.0);
                if (chunk % 17 == 0) {
                    control.impulse(1.0);
                }
                control.publish();
                if (chunk % 2 == 0) {
                    synth.render(out, CHUNK);
                    for (float sample : out) {
                        assertTrue(Float.isFinite(sample), "not finite for " + build);
                        assertTrue(Math.abs(sample) <= 1.0F, "out of range " + sample + " for " + build);
                    }
                } else {
                    synth.render(pcm, CHUNK);
                }
            }
        }
    }

    @Test
    void bladePassFrequencyTracksRpm() {
        MotorTimbre timbre = MotorTimbre.of(DroneBuild.FREESTYLE);
        for (double rpm : new double[]{9_000.0, 16_000.0, 27_000.0}) {
            MotorControl control = steady(rpm, 0.5);
            MotorSynth synth = new MotorSynth(timbre, control, 3L);
            float[] warm = new float[RATE / 2];
            synth.render(warm, warm.length);
            float[] signal = new float[RATE];
            synth.render(signal, signal.length);
            double expected = rpm / 60.0 * timbre.blades();
            double peak = strongestFrequency(signal, expected * 0.55, expected * 1.6);
            assertEquals(expected, peak, expected * 0.02, "blade-pass peak for " + rpm + " rpm");
            double atPeak = goertzel(signal, expected);
            assertTrue(atPeak > 8.0 * goertzel(signal, expected * 0.78), "tone not dominant below at " + rpm);
            assertTrue(atPeak > 8.0 * goertzel(signal, expected * 1.27), "tone not dominant above at " + rpm);
        }
    }

    @Test
    void dopplerShiftsPitch() {
        MotorTimbre timbre = MotorTimbre.of(DroneBuild.RACER);
        double rpm = 18_000.0;
        MotorControl control = steady(rpm, 0.4);
        control.setDoppler(1.2);
        control.publish();
        MotorSynth synth = new MotorSynth(timbre, control, 5L);
        float[] warm = new float[RATE / 2];
        synth.render(warm, warm.length);
        float[] signal = new float[RATE];
        synth.render(signal, signal.length);
        double expected = rpm / 60.0 * timbre.blades() * 1.2;
        assertEquals(expected, strongestFrequency(signal, expected * 0.6, expected * 1.5), expected * 0.02);
    }

    @Test
    void renderingDoesNotAllocatePerChunk() {
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        MotorControl control = steady(20_000.0, 0.7);
        control.setWash(0.6);
        control.publish();
        MotorSynth synth = new MotorSynth(MotorTimbre.of(DroneBuild.CINEWHOOP), control, 9L);
        short[] pcm = new short[CHUNK];
        for (int i = 0; i < 2_000; i++) {
            control.setMotor(i & 3, 15_000.0 + i, 0.5);
            control.publish();
            synth.render(pcm, CHUNK);
        }
        long before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 1_000; i++) {
            control.setMotor(i & 3, 15_000.0 + i, 0.5);
            if ((i & 63) == 0) {
                control.impulse(0.5);
            }
            control.publish();
            synth.render(pcm, CHUNK);
        }
        long allocated = bean.getThreadAllocatedBytes(thread) - before;
        assertTrue(allocated < 1024, "allocated " + allocated + " bytes in 1000 chunks");
    }

    @Test
    void releaseFadesToSilence() {
        MotorControl control = steady(20_000.0, 0.8);
        MotorSynth synth = new MotorSynth(MotorTimbre.of(DroneBuild.FREESTYLE), control, 1L);
        float[] buffer = new float[RATE / 4];
        synth.render(buffer, buffer.length);
        assertTrue(rms(buffer) > 0.02, "motor should be audible, rms " + rms(buffer));
        control.setActive(false);
        control.publish();
        synth.render(buffer, buffer.length);
        float[] tail = new float[RATE / 10];
        synth.render(tail, tail.length);
        assertTrue(rms(tail) < 1.0e-3, "released voice still audible, rms " + rms(tail));
    }

    @Test
    void spinningDownLowersPitchAndLevel() {
        MotorTimbre timbre = MotorTimbre.of(DroneBuild.FREESTYLE);
        MotorControl control = steady(25_000.0, 0.8);
        MotorSynth synth = new MotorSynth(timbre, control, 2L);
        float[] loud = new float[RATE / 2];
        synth.render(loud, loud.length);
        synth.render(loud, loud.length);
        for (int m = 0; m < MotorControl.MOTORS; m++) {
            control.setMotor(m, timbre.idleRpm(), 0.02);
        }
        control.publish();
        float[] quiet = new float[RATE / 2];
        synth.render(quiet, quiet.length);
        synth.render(quiet, quiet.length);
        assertTrue(rms(quiet) < 0.5 * rms(loud), "idle " + rms(quiet) + " vs full " + rms(loud));
    }

    @Test
    void airflowAddsWindNoise() {
        MotorTimbre timbre = MotorTimbre.of(DroneBuild.RACER);
        MotorControl calm = steady(timbre.hoverRpm(), 0.3);
        MotorControl fast = steady(timbre.hoverRpm(), 0.3);
        fast.setAirflow(1.0);
        fast.publish();
        float[] a = new float[RATE];
        float[] b = new float[RATE];
        new MotorSynth(timbre, calm, 4L).render(a, a.length);
        new MotorSynth(timbre, fast, 4L).render(b, b.length);
        assertTrue(rms(b) > 1.3 * rms(a), "wind " + rms(b) + " vs calm " + rms(a));
    }

    @Test
    void pilotMixStaysBitIdentical() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        float[] chunk = new float[CHUNK];
        ByteBuffer bytes = ByteBuffer.allocate(chunk.length * 4);
        for (DroneBuild build : DroneBuild.PRESETS) {
            MotorTimbre timbre = MotorTimbre.of(build);
            MotorControl control = new MotorControl();
            MotorSynth synth = new MotorSynth(timbre, control, 17L);
            for (int c = 0; c < 48; c++) {
                double x = c / 47.0;
                for (int m = 0; m < MotorControl.MOTORS; m++) {
                    control.setMotor(m, timbre.rpmForOutput(Math.min(1.0, x * (0.8 + 0.1 * m))), 0.2 + 0.6 * x);
                }
                control.setWash(c > 20 && c < 32 ? 0.8 : 0.0);
                control.setAirflow(x);
                control.setDoppler(1.0 + 0.1 * Math.sin(c));
                control.setBrightnessHz(c < 24 ? 14_000.0 : 9_000.0);
                control.setGain(0.68);
                control.setSmoothingSeconds(0.012);
                if (c == 10 || c == 30) {
                    control.impulse(c == 10 ? 0.55 : 1.0);
                }
                control.publish();
                synth.render(chunk, chunk.length);
                bytes.clear();
                for (float f : chunk) {
                    bytes.putInt(Float.floatToIntBits(f));
                }
                digest.update(bytes.array());
            }
        }
        assertEquals(PILOT_MIX, HexFormat.of().formatHex(digest.digest()).substring(0, 24), "Motorklang des Piloten hat sich geändert");
    }

    @Test
    void outsideListenerHearsNoCameraWind() {
        MotorTimbre timbre = MotorTimbre.of(DroneBuild.LONG_RANGE);
        MotorControl calm = steady(timbre.hoverRpm(), 0.4);
        MotorControl fast = steady(timbre.hoverRpm(), 0.4);
        calm.setOnboard(false);
        fast.setOnboard(false);
        fast.setAirflow(1.0);
        calm.publish();
        fast.publish();
        float[] a = new float[RATE];
        float[] b = new float[RATE];
        new MotorSynth(timbre, calm, 9L).render(a, a.length);
        new MotorSynth(timbre, fast, 9L).render(b, b.length);
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i], b[i], 0.0F, "Fahrtwind draußen hörbar bei Sample " + i);
        }
    }

    @Test
    void outsideListenerHearsLittleRumble() {
        for (DroneBuild build : new DroneBuild[]{DroneBuild.FREESTYLE, DroneBuild.LONG_RANGE}) {
            MotorTimbre timbre = MotorTimbre.of(build);
            for (double wash : new double[]{0.0, 1.0}) {
                MotorControl onboard = steady(timbre.hoverRpm(), 0.6);
                MotorControl outside = steady(timbre.hoverRpm(), 0.6);
                onboard.setWash(wash);
                outside.setWash(wash);
                outside.setOnboard(false);
                onboard.publish();
                outside.publish();
                float[] a = new float[RATE];
                float[] b = new float[RATE];
                new MotorSynth(timbre, onboard, 5L).render(a, a.length);
                new MotorSynth(timbre, outside, 5L).render(b, b.length);
                double low = lowBand(a);
                double lowOutside = lowBand(b);
                assertTrue(lowOutside < 0.6 * low, build + " Wash " + wash + ": Rumpeln draußen " + lowOutside + " vs an Bord " + low);
                assertTrue(rms(b) > 0.5 * rms(a), build + " Wash " + wash + ": Motor draußen zu leise " + rms(b) + " vs " + rms(a));
            }
        }
    }

    private static double lowBand(float[] signal) {
        float k = SynthMath.onePoleCoefficient(35.0, RATE);
        float[] pole = new float[4];
        double sum = 0.0;
        for (int i = 0; i < signal.length; i++) {
            float x = signal[i];
            for (int p = 0; p < pole.length; p++) {
                pole[p] += k * (x - pole[p]);
                x = pole[p];
            }
            if (i >= RATE / 4) {
                sum += x * x;
            }
        }
        return Math.sqrt(sum / (signal.length - RATE / 4));
    }

    @Test
    void presetsSoundDistinct() {
        MotorTimbre whoop = MotorTimbre.of(DroneBuild.WHOOP);
        MotorTimbre freestyle = MotorTimbre.of(DroneBuild.FREESTYLE);
        MotorTimbre longRange = MotorTimbre.of(DroneBuild.LONG_RANGE);
        double whoopHover = whoop.bladePassHz(whoop.hoverRpm());
        double freestyleHover = freestyle.bladePassHz(freestyle.hoverRpm());
        double longRangeHover = longRange.bladePassHz(longRange.hoverRpm());
        assertTrue(whoopHover > freestyleHover, "whoop " + whoopHover + " vs 5\" " + freestyleHover);
        assertTrue(freestyleHover > longRangeHover, "5\" " + freestyleHover + " vs 7\" " + longRangeHover);
        assertTrue(SoundReach.of(DroneBuild.WHOOP).hoverRangeBlocks() < SoundReach.of(DroneBuild.FREESTYLE).hoverRangeBlocks()
                && SoundReach.of(DroneBuild.FREESTYLE).hoverRangeBlocks() < SoundReach.of(DroneBuild.LONG_RANGE).hoverRangeBlocks());
        assertTrue(whoop.loudness() < freestyle.loudness());
        assertTrue(whoop.harmonicTilt() < longRange.harmonicTilt());
        assertEquals(6, whoop.polePairs());
        assertEquals(7, freestyle.polePairs());
        assertTrue(MotorTimbre.of(DroneBuild.CINEWHOOP).ducted() && MotorTimbre.of(DroneBuild.CINEWHOOP).ductGain() > 0.0F);
        for (DroneBuild build : DroneBuild.PRESETS) {
            MotorTimbre t = MotorTimbre.of(build);
            assertTrue(t.idleRpm() < t.hoverRpm() && t.hoverRpm() < t.maxRpm(), "rpm anchors for " + build);
            double last = -1.0;
            for (int i = 0; i <= 20; i++) {
                double rpm = t.rpmForOutput(i / 20.0);
                assertTrue(rpm > last, "rpmForOutput not monotonic for " + build);
                last = rpm;
            }
            assertEquals(t.hoverRpm(), t.rpmForOutput(t.hoverOutput()), 1.0);
            assertEquals(t.maxRpm(), t.rpmForOutput(1.0), 1.0);
        }
    }

    private static MotorControl steady(double rpm, double load) {
        MotorControl control = new MotorControl();
        for (int m = 0; m < MotorControl.MOTORS; m++) {
            control.setMotor(m, rpm, load);
        }
        control.setGain(1.0);
        control.setWash(0.0);
        control.setDoppler(1.0);
        control.setSmoothingSeconds(0.02);
        control.publish();
        return control;
    }

    private static double strongestFrequency(float[] signal, double from, double to) {
        double best = from;
        double bestPower = -1.0;
        for (double f = from; f <= to; f += 1.0) {
            double p = goertzel(signal, f);
            if (p > bestPower) {
                bestPower = p;
                best = f;
            }
        }
        return best;
    }

    private static double goertzel(float[] signal, double frequency) {
        double w = 2.0 * Math.PI * frequency / RATE;
        double coefficient = 2.0 * Math.cos(w);
        double s1 = 0.0;
        double s2 = 0.0;
        int n = signal.length;
        for (int i = 0; i < n; i++) {
            double window = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (n - 1));
            double s0 = signal[i] * window + coefficient * s1 - s2;
            s2 = s1;
            s1 = s0;
        }
        return s1 * s1 + s2 * s2 - coefficient * s1 * s2;
    }

    private static double rms(float[] signal) {
        double sum = 0.0;
        for (float s : signal) {
            sum += s * s;
        }
        return Math.sqrt(sum / signal.length);
    }
}
