package de.lardtom.propwash.video.record.aac;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AacEncoderTest {
    private static final int FRAME = AacEncoder.FRAME;

    private static float[] tone(int samples, double hz, double amplitude) {
        float[] out = new float[samples];
        for (int i = 0; i < samples; i++) {
            out[i] = (float) (amplitude * Math.sin(2.0 * Math.PI * hz * i / AacEncoder.SAMPLE_RATE));
        }
        return out;
    }

    private static float[] motor(int samples, long seed) {
        Random random = new Random(seed);
        float[] out = new float[samples];
        double phase = 0.0;
        double noise = 0.0;
        for (int i = 0; i < samples; i++) {
            double t = i / (double) AacEncoder.SAMPLE_RATE;
            double hz = 180.0 + 120.0 * Math.sin(2.0 * Math.PI * 0.7 * t);
            phase += 2.0 * Math.PI * hz / AacEncoder.SAMPLE_RATE;
            noise = 0.7 * noise + 0.3 * random.nextGaussian();
            double v = 0.35 * Math.sin(phase) + 0.18 * Math.sin(2.0 * phase + 0.3) + 0.12 * Math.sin(4.0 * phase) + 0.08 * noise;
            out[i] = (float) (v * 32767.0);
        }
        return out;
    }

    private static List<byte[]> encode(AacEncoder encoder, float[] input) {
        List<byte[]> frames = new ArrayList<>();
        float[] padded = new float[(input.length + FRAME - 1) / FRAME * FRAME];
        System.arraycopy(input, 0, padded, 0, input.length);
        for (int at = 0; at < padded.length; at += FRAME) {
            frames.add(encoder.encode(padded, at));
        }
        frames.add(encoder.flush());
        return frames;
    }

    private static double[] decode(AacTestDecoder decoder, List<byte[]> frames) {
        double[] out = new double[frames.size() * FRAME];
        for (int i = 0; i < frames.size(); i++) {
            System.arraycopy(decoder.decode(frames.get(i)), 0, out, i * FRAME, FRAME);
        }
        return out;
    }

    private static double snr(float[] input, double[] decoded, int from, int to) {
        double signal = 0.0;
        double noise = 0.0;
        for (int i = from; i < to; i++) {
            double reference = input[i];
            double error = decoded[i + AacEncoder.PRIMING] - reference;
            signal += reference * reference;
            noise += error * error;
        }
        return 10.0 * Math.log10(signal / Math.max(1.0E-9, noise));
    }

    @Test
    void audioSpecificConfigIsLowComplexity48kMono() {
        assertArrayEquals(new byte[]{0x11, (byte) 0x88}, AacEncoder.audioSpecificConfig());
    }

    @Test
    void mdctMatchesTheDefinition() {
        Mdct mdct = new Mdct(64);
        Random random = new Random(3);
        float[] block = new float[64];
        for (int i = 0; i < block.length; i++) {
            block[i] = (float) random.nextGaussian();
        }
        double[] fast = new double[32];
        mdct.forward(block, fast, 2.0);
        double n0 = (64 / 2.0 + 1.0) / 2.0;
        for (int k = 0; k < 32; k++) {
            double sum = 0.0;
            for (int n = 0; n < 64; n++) {
                sum += block[n] * mdct.window(n) * Math.cos(2.0 * Math.PI / 64 * (n + n0) * (k + 0.5));
            }
            assertEquals(2.0 * sum, fast[k], 1.0E-9, "bin " + k);
        }
    }

    @Test
    void mdctOfTheFullFrameMatchesTheDefinition() {
        Mdct mdct = new Mdct(2 * FRAME);
        Random random = new Random(9);
        float[] block = new float[2 * FRAME];
        for (int i = 0; i < block.length; i++) {
            block[i] = (float) (random.nextGaussian() * 1000.0);
        }
        double[] fast = new double[FRAME];
        mdct.forward(block, fast, 2.0);
        double n0 = (2 * FRAME / 2.0 + 1.0) / 2.0;
        for (int k : new int[]{0, 1, 17, 511, 512, 1000, 1023}) {
            double sum = 0.0;
            for (int n = 0; n < 2 * FRAME; n++) {
                sum += block[n] * mdct.window(n) * Math.cos(2.0 * Math.PI / (2 * FRAME) * (n + n0) * (k + 0.5));
            }
            assertEquals(2.0 * sum, fast[k], Math.abs(sum) * 1.0E-9 + 1.0E-6, "bin " + k);
        }
    }

    @Test
    void toneSurvivesTheRoundTrip() {
        float[] input = tone(AacEncoder.SAMPLE_RATE / 2, 997.0, 12000.0);
        AacEncoder encoder = new AacEncoder(AacEncoder.SAMPLE_RATE, AacEncoder.DEFAULT_BITRATE);
        List<byte[]> frames = encode(encoder, input);
        double[] decoded = decode(new AacTestDecoder(), frames);
        double quality = snr(input, decoded, FRAME, input.length - FRAME);
        assertTrue(quality > 25.0, "SNR " + quality);
    }

    @Test
    void motorLikeSoundSurvivesTheRoundTripAtTheTargetRate() {
        float[] input = motor(AacEncoder.SAMPLE_RATE * 2, 5L);
        AacEncoder encoder = new AacEncoder(AacEncoder.SAMPLE_RATE, AacEncoder.DEFAULT_BITRATE);
        List<byte[]> frames = encode(encoder, input);
        AacTestDecoder decoder = new AacTestDecoder();
        double[] decoded = decode(decoder, frames);
        double quality = snr(input, decoded, FRAME, input.length - FRAME);
        assertTrue(quality > 12.0, "SNR " + quality);
        for (byte[] frame : frames) {
            assertTrue(frame.length * 8 <= 6144, "frame with " + frame.length * 8 + " bits");
        }
        long bitrate = encoder.averageBitrate();
        assertTrue(bitrate > AacEncoder.DEFAULT_BITRATE * 0.4 && bitrate < AacEncoder.DEFAULT_BITRATE * 1.4, "bitrate " + bitrate);
    }

    @Test
    void cleanTonesAtHighRatesUseEscapeCodes() {
        float[] input = tone(FRAME * 24, 15_000.0, 8000.0);
        AacEncoder encoder = new AacEncoder(AacEncoder.SAMPLE_RATE, 256_000);
        List<byte[]> frames = encode(encoder, input);
        AacTestDecoder decoder = new AacTestDecoder();
        double[] decoded = decode(decoder, frames);
        assertTrue(decoder.escapes > 0, "no escape codes used");
        double quality = snr(input, decoded, FRAME * 20, input.length - FRAME);
        assertTrue(quality > 30.0, "SNR " + quality);
    }

    @Test
    void silenceEncodesToTinyEmptyFrames() {
        float[] input = new float[FRAME * 4];
        AacEncoder encoder = new AacEncoder(AacEncoder.SAMPLE_RATE, AacEncoder.DEFAULT_BITRATE);
        List<byte[]> frames = encode(encoder, input);
        AacTestDecoder decoder = new AacTestDecoder();
        for (byte[] frame : frames) {
            assertTrue(frame.length <= 8, "silent frame of " + frame.length + " bytes");
            for (double sample : decoder.decode(frame)) {
                assertEquals(0.0, sample, 1.0E-9);
            }
            assertEquals(0, decoder.lastMaxSfb);
        }
    }

    @Test
    void everyBandRespectsTheScalefactorLimits() {
        float[] input = new float[FRAME * 3];
        for (int i = 0; i < input.length; i++) {
            double t = i / (double) AacEncoder.SAMPLE_RATE;
            input[i] = (float) (20000.0 * Math.sin(2.0 * Math.PI * 60.0 * t) + 3.0 * Math.sin(2.0 * Math.PI * 12000.0 * t));
        }
        AacEncoder encoder = new AacEncoder(AacEncoder.SAMPLE_RATE, AacEncoder.DEFAULT_BITRATE);
        AacTestDecoder decoder = new AacTestDecoder();
        for (byte[] frame : encode(encoder, input)) {
            decoder.decode(frame);
            assertTrue(decoder.maxAbsQuant <= AacEncoder.MAX_QUANT);
        }
    }

    @Test
    void onlyFortyEightKilohertzIsAccepted() {
        assertThrows(IllegalArgumentException.class, () -> new AacEncoder(44_100, AacEncoder.DEFAULT_BITRATE));
    }
}
