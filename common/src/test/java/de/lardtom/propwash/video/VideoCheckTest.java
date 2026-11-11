package de.lardtom.propwash.video;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoCheckTest {
    private static final int KERNEL_PX = 3;

    @Test
    void patternHasFourBrightDistinctQuadrants() {
        Set<Integer> colours = new HashSet<>();
        for (int[] p : VideoCheck.samples()) {
            int argb = VideoCheck.expectedArgb(p[0], p[1]);
            colours.add(argb);
            assertEquals(0xFF, (argb >>> 24) & 0xFF);
            assertTrue(VideoCheck.luma((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF) > 100.0, Integer.toHexString(argb));
        }
        assertEquals(4, colours.size());
        assertEquals(4, VideoCheck.samples().length);
        assertTrue(VideoCheck.expectedMeanLuma() > 4.0 * VideoCheck.DARK_LUMA);
    }

    @Test
    void samplesStayInTheirQuadrantThroughTheLens() {
        FisheyeLens lens = VideoCheck.lens();
        double[] source = new double[2];
        for (int[] p : VideoCheck.samples()) {
            double dx = p[0] + 0.5 - VideoCheck.WIDTH * 0.5;
            double dy = p[1] + 0.5 - VideoCheck.HEIGHT * 0.5;
            assertTrue(Math.abs(dx) >= 8.0 && Math.abs(dy) >= 8.0, "Messpunkt zu nah an der Quadrantengrenze: " + dx + "/" + dy);
            assertTrue(lens.covers(dx, dy), "Messpunkt außerhalb des Bildkreises: " + dx + "/" + dy);
            assertTrue(lens.sourceAt(dx, dy, source), "keine Quelle für " + dx + "/" + dy);
            assertEquals(Math.signum(dx), Math.signum(source[0]));
            assertEquals(Math.signum(dy), Math.signum(source[1]));
            assertTrue(Math.abs(source[0]) >= KERNEL_PX && Math.abs(source[1]) >= KERNEL_PX,
                    "Quelle zu nah an der Quadrantengrenze: " + source[0] + "/" + source[1]);
        }
    }

    @Test
    void intactPicturesPass() {
        byte[] pattern = pattern();
        VideoCheck.Result result = VideoCheck.evaluate(pattern, pattern.clone(), noisy(pattern, 0.5, 1L), pattern.clone());
        assertEquals(VideoCheck.Outcome.PASS, result.outcome(), result.detail());
        assertFalse(result.outcome().simplifies());
        assertEquals(4, result.full().matched());
        assertTrue(result.detail().contains("voller Durchgang richtig"), result.detail());
    }

    @Test
    void smallDeviationsPass() {
        byte[] pattern = pattern();
        byte[] shifted = shift(pattern, VideoCheck.TOLERANCE);
        assertEquals(VideoCheck.Outcome.PASS, VideoCheck.evaluate(pattern, shifted, shifted, shift(pattern, -VideoCheck.TOLERANCE)).outcome());
        byte[] off = shift(pattern, VideoCheck.TOLERANCE + 1);
        assertEquals(VideoCheck.Outcome.FULL_BROKEN, VideoCheck.evaluate(pattern, off, pattern, pattern).outcome());
    }

    @Test
    void blackFullPassWithWorkingSimpleShaderSimplifies() {
        byte[] pattern = pattern();
        byte[] black = filled(0, 0, 0);
        VideoCheck.Result result = VideoCheck.evaluate(pattern, black, black, pattern.clone());
        assertEquals(VideoCheck.Outcome.FULL_BROKEN, result.outcome());
        assertTrue(result.outcome().simplifies());
        assertTrue(result.full().black());
        assertTrue(result.detail().contains("schwarz oder NaN"), result.detail());
        assertEquals(VideoCheck.Outcome.FULL_BROKEN, VideoCheck.evaluate(pattern, pattern, black, pattern).outcome());
        assertEquals(VideoCheck.Outcome.FULL_BROKEN, VideoCheck.evaluate(pattern, black, pattern, pattern).outcome());
    }

    @Test
    void wrongColoursCountAsBroken() {
        byte[] pattern = pattern();
        byte[] swapped = pattern.clone();
        for (int i = 0; i < swapped.length; i += VideoCheck.BYTES_PER_PIXEL) {
            byte red = swapped[i];
            swapped[i] = swapped[i + 2];
            swapped[i + 2] = red;
        }
        VideoCheck.Result result = VideoCheck.evaluate(pattern, swapped, pattern, pattern);
        assertEquals(VideoCheck.Outcome.FULL_BROKEN, result.outcome());
        assertFalse(result.full().black());
        assertTrue(result.detail().contains("voller Durchgang falsch"), result.detail());
    }

    @Test
    void everythingBlackLeavesNoShader() {
        byte[] pattern = pattern();
        byte[] black = filled(0, 0, 0);
        assertEquals(VideoCheck.Outcome.ALL_BROKEN, VideoCheck.evaluate(pattern, black, black, black).outcome());
        assertTrue(VideoCheck.Outcome.ALL_BROKEN.simplifies());
    }

    @Test
    void unreadableControlKeepsTheEffects() {
        byte[] black = filled(0, 0, 0);
        VideoCheck.Result result = VideoCheck.evaluate(black, black, black, black);
        assertEquals(VideoCheck.Outcome.UNAVAILABLE, result.outcome());
        assertFalse(result.outcome().simplifies());
        assertEquals(VideoCheck.Outcome.UNAVAILABLE, VideoCheck.evaluate(new byte[16], pattern(), pattern(), pattern()).outcome());
        VideoCheck.Reading shortRead = VideoCheck.read(new byte[16]);
        assertTrue(shortRead.black());
        assertFalse(shortRead.exact());
    }

    @Test
    void darkNoisyPictureFails() {
        byte[] pattern = pattern();
        byte[] dim = scale(pattern, 0.2);
        assertEquals(VideoCheck.Outcome.FULL_BROKEN, VideoCheck.evaluate(pattern, pattern, dim, pattern).outcome());
        byte[] lit = scale(pattern, 0.6);
        assertEquals(VideoCheck.Outcome.PASS, VideoCheck.evaluate(pattern, pattern, lit, pattern).outcome());
    }

    @Test
    void outcomesAndSizes() {
        assertEquals(VideoCheck.WIDTH * VideoCheck.HEIGHT * 4, VideoCheck.bytes());
        assertFalse(VideoCheck.Outcome.PENDING.simplifies());
        assertFalse(VideoCheck.Outcome.PASS.simplifies());
        assertTrue(VideoCheck.Outcome.FULL_BROKEN.simplifies());
        assertEquals(16.0 / 9.0, (double) VideoCheck.WIDTH / VideoCheck.HEIGHT, 1.0E-9);
    }

    static byte[] pattern() {
        byte[] bytes = new byte[VideoCheck.bytes()];
        for (int y = 0; y < VideoCheck.HEIGHT; y++) {
            for (int x = 0; x < VideoCheck.WIDTH; x++) {
                int argb = VideoCheck.expectedArgb(x, y);
                int o = (y * VideoCheck.WIDTH + x) * VideoCheck.BYTES_PER_PIXEL;
                bytes[o] = (byte) (argb >> 16);
                bytes[o + 1] = (byte) (argb >> 8);
                bytes[o + 2] = (byte) argb;
                bytes[o + 3] = (byte) 0xFF;
            }
        }
        return bytes;
    }

    private static byte[] filled(int red, int green, int blue) {
        byte[] bytes = new byte[VideoCheck.bytes()];
        for (int o = 0; o < bytes.length; o += VideoCheck.BYTES_PER_PIXEL) {
            bytes[o] = (byte) red;
            bytes[o + 1] = (byte) green;
            bytes[o + 2] = (byte) blue;
            bytes[o + 3] = (byte) 0xFF;
        }
        return bytes;
    }

    private static byte[] shift(byte[] source, int delta) {
        byte[] bytes = source.clone();
        for (int o = 0; o < bytes.length; o += VideoCheck.BYTES_PER_PIXEL) {
            for (int c = 0; c < 3; c++) {
                bytes[o + c] = (byte) Math.clamp((source[o + c] & 0xFF) + delta, 0, 255);
            }
        }
        return bytes;
    }

    private static byte[] scale(byte[] source, double factor) {
        byte[] bytes = source.clone();
        for (int o = 0; o < bytes.length; o += VideoCheck.BYTES_PER_PIXEL) {
            for (int c = 0; c < 3; c++) {
                bytes[o + c] = (byte) Math.round((source[o + c] & 0xFF) * factor);
            }
        }
        return bytes;
    }

    private static byte[] noisy(byte[] source, double amount, long seed) {
        Random random = new Random(seed);
        byte[] bytes = source.clone();
        for (int o = 0; o < bytes.length; o += VideoCheck.BYTES_PER_PIXEL) {
            double n = random.nextDouble();
            for (int c = 0; c < 3; c++) {
                double value = (source[o + c] & 0xFF) / 255.0;
                double mixed = value * (1.0 - amount * 0.6) + n * amount * 0.6 + (n - 0.5) * amount * 0.4;
                bytes[o + c] = (byte) Math.round(Math.clamp(mixed, 0.0, 1.0) * 255.0);
            }
        }
        return bytes;
    }
}
