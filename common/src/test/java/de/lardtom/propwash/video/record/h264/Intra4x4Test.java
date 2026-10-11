package de.lardtom.propwash.video.record.h264;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Intra4x4Test {
    private static Intra4x4 neighbours() {
        Intra4x4 p = Intra4x4.create();
        int[] top = {10, 20, 30, 40, 50, 60, 70, 80};
        int[] left = {15, 25, 35, 45};
        System.arraycopy(top, 0, p.top, 0, 8);
        System.arraycopy(left, 0, p.left, 0, 4);
        p.corner = 5;
        p.hasTop = true;
        p.hasLeft = true;
        p.hasCorner = true;
        return p;
    }

    @Test
    void verticalHorizontalAndDcFollowTheNeighbours() {
        Intra4x4 p = neighbours();
        int[] out = new int[16];
        p.predict(Intra4x4.VERTICAL, out);
        assertArrayEquals(new int[]{10, 20, 30, 40, 10, 20, 30, 40, 10, 20, 30, 40, 10, 20, 30, 40}, out);
        p.predict(Intra4x4.HORIZONTAL, out);
        assertArrayEquals(new int[]{15, 15, 15, 15, 25, 25, 25, 25, 35, 35, 35, 35, 45, 45, 45, 45}, out);
        p.predict(Intra4x4.DC, out);
        assertEquals((100 + 120 + 4) >> 3, out[0]);
        p.hasTop = false;
        p.hasLeft = false;
        p.predict(Intra4x4.DC, out);
        assertEquals(128, out[15]);
    }

    @Test
    void diagonalModesUseTheCornerAndTopRight() {
        Intra4x4 p = neighbours();
        int[] out = new int[16];
        p.predict(Intra4x4.DIAGONAL_DOWN_LEFT, out);
        assertEquals((10 + 2 * 20 + 30 + 2) >> 2, out[0]);
        assertEquals((70 + 3 * 80 + 2) >> 2, out[15]);
        p.predict(Intra4x4.DIAGONAL_DOWN_RIGHT, out);
        assertEquals((10 + 2 * 5 + 15 + 2) >> 2, out[0]);
        assertEquals(out[0], out[5]);
        assertEquals((5 + 2 * 10 + 20 + 2) >> 2, out[1]);
        assertEquals((5 + 2 * 15 + 25 + 2) >> 2, out[4]);
        p.predict(Intra4x4.VERTICAL_RIGHT, out);
        assertEquals((5 + 10 + 1) >> 1, out[0]);
        p.predict(Intra4x4.HORIZONTAL_UP, out);
        assertEquals(45, out[15]);
        assertEquals((35 + 3 * 45 + 2) >> 2, out[2 * 4 + 1]);
    }

    @Test
    void availabilityMatchesTheNeighbourRules() {
        Intra4x4 p = neighbours();
        p.hasCorner = false;
        assertTrue(p.available(Intra4x4.VERTICAL));
        assertTrue(p.available(Intra4x4.DIAGONAL_DOWN_LEFT));
        assertFalse(p.available(Intra4x4.DIAGONAL_DOWN_RIGHT));
        assertFalse(p.available(Intra4x4.VERTICAL_RIGHT));
        assertFalse(p.available(Intra4x4.HORIZONTAL_DOWN));
        p.hasTop = false;
        assertFalse(p.available(Intra4x4.VERTICAL_LEFT));
        assertTrue(p.available(Intra4x4.HORIZONTAL_UP));
        assertTrue(p.available(Intra4x4.DC));
    }

    @Test
    void detailedKeyframesStaySmallAndClose() {
        int width = 256;
        int height = 128;
        byte[] frame = new byte[width * height * 3 / 2];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int diagonal = ((x + y) / 3) % 2 == 0 ? 70 : 180;
                int stripe = (x / 5) % 2 == 0 ? 0 : 30;
                frame[y * width + x] = (byte) (y < height / 2 ? diagonal : 60 + stripe);
            }
        }
        java.util.Arrays.fill(frame, width * height, frame.length, (byte) 128);
        try (H264Encoder encoder = new H264Encoder(width, height, 30, new EncoderSettings(1, 1, 4_000_000L, 1, 22, 22, true))) {
            EncodedFrame key = encoder.encode(frame, SliceEncoder.PRESET_QUALITY);
            assertTrue(key.keyframe());
            byte[] recon = encoder.reconstructionI420();
            double mse = 0.0;
            for (int i = 0; i < width * height; i++) {
                int d = (frame[i] & 0xFF) - (recon[i] & 0xFF);
                mse += d * d;
            }
            double psnr = 10.0 * Math.log10(255.0 * 255.0 / (mse / (width * height)));
            assertTrue(psnr > 38.0, "PSNR " + psnr);
            try (H264Encoder flat = new H264Encoder(width, height, 30, new EncoderSettings(1, 1, 4_000_000L, 1, 22, 22, true))) {
                EncodedFrame fastest = flat.encode(frame, SliceEncoder.PRESET_FASTEST);
                assertTrue(key.sampleSize() < fastest.sampleSize(), key.sampleSize() + " vs " + fastest.sampleSize());
            }
        }
    }
}
