package de.lardtom.propwash.video.record.h264;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncoderQualityTest {
    static byte[] smoothScene(int width, int height, int offsetX, int offsetY) {
        byte[] out = new byte[width * height * 3 / 2];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double sx = x + offsetX;
                double sy = y + offsetY;
                double value = 128.0 + 55.0 * Math.sin(sx / 23.0) + 35.0 * Math.sin(sy / 17.0 + sx / 41.0) + 18.0 * Math.sin((sx + sy) / 7.0)
                        + 8.0 * Math.sin(sx / 3.1) * Math.cos(sy / 2.7);
                out[y * width + x] = (byte) Math.max(16, Math.min(235, (int) Math.round(value)));
            }
        }
        int cw = width / 2;
        int ch = height / 2;
        for (int y = 0; y < ch; y++) {
            for (int x = 0; x < cw; x++) {
                double sx = 2 * x + offsetX;
                double sy = 2 * y + offsetY;
                out[width * height + y * cw + x] = (byte) (128 + (int) Math.round(20.0 * Math.sin(sx / 31.0)));
                out[width * height + cw * ch + y * cw + x] = (byte) (128 + (int) Math.round(20.0 * Math.cos(sy / 29.0)));
            }
        }
        return out;
    }

    static byte[] flatAndBusy(int width, int height) {
        byte[] out = new byte[width * height * 3 / 2];
        long seed = 12345L;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value;
                if (x < width / 2) {
                    value = 24 + x / 40;
                } else {
                    seed = seed * 6364136223846793005L + 1442695040888963407L;
                    value = 40 + (int) ((seed >>> 33) % 180);
                }
                out[y * width + x] = (byte) value;
            }
        }
        java.util.Arrays.fill(out, width * height, out.length, (byte) 128);
        return out;
    }

    private static double averageQp(H264Encoder encoder, boolean leftHalf) {
        int mbWidth = encoder.macroblockWidth();
        double sum = 0.0;
        int count = 0;
        for (int mb = 0; mb < encoder.macroblockCount(); mb++) {
            boolean left = mb % mbWidth < mbWidth / 2;
            if (left == leftHalf) {
                sum += encoder.macroblockQp(mb);
                count++;
            }
        }
        return sum / count;
    }

    @Test
    void adaptiveQuantisationSpendsMoreBitsOnFlatDarkAreas() {
        EncoderSettings settings = EncoderSettings.of(1, 8_000_000L, 30, 26);
        try (H264Encoder encoder = new H264Encoder(320, 192, 30, settings)) {
            EncodedFrame frame = encoder.encode(flatAndBusy(320, 192), SliceEncoder.PRESET_QUALITY);
            assertTrue(frame.keyframe());
            double flat = averageQp(encoder, true);
            double busy = averageQp(encoder, false);
            assertTrue(flat + 6.0 < busy, "flat " + flat + " busy " + busy);
            for (int mb = 0; mb < encoder.macroblockCount(); mb++) {
                int qp = encoder.macroblockQp(mb);
                assertTrue(qp >= settings.minQp() && qp <= settings.maxQp(), "qp " + qp);
            }
        }
    }

    @Test
    void withoutAdaptiveQuantisationEveryMacroblockUsesTheFrameQp() {
        EncoderSettings settings = EncoderSettings.of(1, 8_000_000L, 30, 26).withQuality(26, 0.0);
        try (H264Encoder encoder = new H264Encoder(320, 192, 30, settings)) {
            encoder.encode(flatAndBusy(320, 192), SliceEncoder.PRESET_QUALITY);
            for (int mb = 0; mb < encoder.macroblockCount(); mb++) {
                assertEquals(26 - RateControl.KEYFRAME_BONUS, encoder.macroblockQp(mb));
            }
        }
    }

    @Test
    void lowerQualityNumbersGiveBiggerAndCleanerFrames() {
        byte[] source = smoothScene(320, 192, 0, 0);
        int[] sizes = new int[2];
        double[] psnr = new double[2];
        int[] qualities = {20, 30};
        for (int i = 0; i < 2; i++) {
            try (H264Encoder encoder = new H264Encoder(320, 192, 30, EncoderSettings.of(1, 50_000_000L, 30, qualities[i]))) {
                sizes[i] = encoder.encode(source, SliceEncoder.PRESET_QUALITY).sampleSize();
                psnr[i] = H264EncoderTest.psnr(source, encoder.reconstructionI420(), 320 * 192);
            }
        }
        assertTrue(sizes[0] > sizes[1] * 1.5, sizes[0] + " vs " + sizes[1]);
        assertTrue(psnr[0] > psnr[1] + 2.0, psnr[0] + " vs " + psnr[1]);
    }

    @Test
    void fastPansFarBeyondTheOldSearchRangeAreFound() {
        int shift = 96;
        EncoderSettings oneSlice = new EncoderSettings(1, 1, 50_000_000L, 60, 12, 46, true, 24, EncoderSettings.DEFAULT_AQ);
        try (H264Encoder encoder = new H264Encoder(320, 192, 30, oneSlice)) {
            int intra = encoder.encode(smoothScene(320, 192, 0, 0), SliceEncoder.PRESET_QUALITY).sampleSize();
            int inter = encoder.encode(smoothScene(320, 192, shift, 0), SliceEncoder.PRESET_QUALITY).sampleSize();
            assertTrue(inter * 5 < intra * 3, "P " + inter + " I " + intra);
            int mbWidth = encoder.macroblockWidth();
            int matched = 0;
            int candidates = 0;
            for (int mb = 0; mb < encoder.macroblockCount(); mb++) {
                if ((mb % mbWidth) * 16 + 16 + shift <= 320) {
                    candidates++;
                    if (!encoder.macroblockIntra(mb) && Math.abs(encoder.motionX(mb) - shift * 4) <= 2 && Math.abs(encoder.motionY(mb)) <= 2) {
                        matched++;
                    }
                }
            }
            assertTrue(matched * 10 >= candidates * 8, matched + " of " + candidates);
        }
    }

    @Test
    void rateControlKeepsTheQualityWhileUnderTheCap() {
        RateControl rate = new RateControl(10_000_000L, 30, 23, 12, 46);
        assertEquals(23 - RateControl.KEYFRAME_BONUS, rate.qpFor(true));
        rate.update(true, 21, 400_000L);
        for (int i = 0; i < 90; i++) {
            assertEquals(23, rate.qpFor(false));
            rate.update(false, 23, 150_000L);
        }
        assertEquals(0.0, rate.fill(), 1.0E-9);
    }

    @Test
    void rateControlRaisesTheQpOnlyWhileTheCapIsExceeded() {
        RateControl rate = new RateControl(10_000_000L, 30, 23, 12, 46);
        long perFrame = 10_000_000L / 30;
        int highest = 0;
        for (int i = 0; i < 60; i++) {
            int qp = rate.qpFor(false);
            highest = Math.max(highest, qp);
            long bits = Math.round(perFrame * 3.0 * Math.pow(2.0, (23 - qp) / 6.0));
            rate.update(false, qp, bits);
            assertTrue(rate.fill() < 1.0, "buffer overflow at frame " + i + ": " + rate.fill());
        }
        assertTrue(highest >= 30, "qp " + highest);
        for (int i = 0; i < 400; i++) {
            int qp = rate.qpFor(false);
            rate.update(false, qp, perFrame / 4);
        }
        assertEquals(23, rate.qpFor(false));
    }

    @Test
    void settingsClampQualityAndKeepTheOldConstructor() {
        EncoderSettings legacy = new EncoderSettings(2, 4, 3_000_000L, 30, 16, 44, true);
        assertEquals(EncoderSettings.DEFAULT_QUALITY, legacy.quality());
        assertEquals(EncoderSettings.DEFAULT_AQ, legacy.adaptiveQuant(), 1.0E-9);
        EncoderSettings clamped = new EncoderSettings(1, 1, 1_000_000L, 30, 16, 40, true, 60, 5.0);
        assertEquals(40, clamped.quality());
        assertEquals(2.0, clamped.adaptiveQuant(), 1.0E-9);
        EncoderSettings of = EncoderSettings.of(3, 20_000_000L, 60, 20);
        assertEquals(6, of.slices());
        assertEquals(120, of.gop());
        assertEquals(20, of.quality());
        assertEquals(20_000_000L, of.bitrate());
    }
}
