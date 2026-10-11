package de.lardtom.propwash.video.record;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThumbnailTest {
    private static byte[] frame(int width, int height, int y, int u, int v) {
        byte[] data = new byte[width * height * 3 / 2];
        Arrays.fill(data, 0, width * height, (byte) y);
        Arrays.fill(data, width * height, width * height + width * height / 4, (byte) u);
        Arrays.fill(data, width * height + width * height / 4, data.length, (byte) v);
        return data;
    }

    @Test
    void limitedRangeWhiteAndBlack() {
        assertEquals(0xFFFFFFFF, Thumbnail.rgb709(235.0, 128.0, 128.0));
        assertEquals(0xFF000000, Thumbnail.rgb709(16.0, 128.0, 128.0));
    }

    @Test
    void bt709RedComesBackRed() {
        int red = Thumbnail.rgb709(63.0, 102.0, 240.0);
        assertTrue(((red >> 16) & 0xFF) > 240, Integer.toHexString(red));
        assertTrue(((red >> 8) & 0xFF) < 12, Integer.toHexString(red));
        assertTrue((red & 0xFF) < 12, Integer.toHexString(red));
    }

    @Test
    void keepsTopRowsOnTop() {
        int width = 64;
        int height = 36;
        byte[] data = frame(width, height, 16, 128, 128);
        for (int row = 0; row < height / 2; row++) {
            Arrays.fill(data, row * width, row * width + width, (byte) 235);
        }
        int[] thumb = Thumbnail.fromI420(data, width, height, 16, 9);
        assertEquals(16 * 9, thumb.length);
        assertEquals(0xFFFFFFFF, thumb[0]);
        assertEquals(0xFFFFFFFF, thumb[3 * 16 + 5]);
        assertEquals(0xFF000000, thumb[8 * 16 + 15]);
    }

    @Test
    void uniformColourStaysUniform() {
        int[] thumb = Thumbnail.fromI420(frame(1280, 720, 120, 90, 150), 1280, 720, Thumbnail.WIDTH, Thumbnail.HEIGHT);
        int first = thumb[0];
        for (int pixel : thumb) {
            assertEquals(first, pixel);
        }
    }

    @Test
    void rejectsShortBuffers() {
        assertThrows(IllegalArgumentException.class, () -> Thumbnail.fromI420(new byte[10], 64, 36, 16, 9));
    }
}
