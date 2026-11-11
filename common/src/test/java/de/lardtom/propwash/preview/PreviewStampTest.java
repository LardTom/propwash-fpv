package de.lardtom.propwash.preview;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewStampTest {
    private static final PreviewFont.Raster TEXT = PreviewFont.raster(PreviewBuild.watermark("0.6.0-rc.1"));
    private static final int GREY_LUMA = 100;
    private static final int TINTED_CHROMA = 90;

    private static ByteBuffer i420(int width, int height) {
        int luma = width * height;
        ByteBuffer frame = ByteBuffer.allocate(luma * 3 / 2);
        for (int i = 0; i < luma; i++) {
            frame.put(i, (byte) GREY_LUMA);
        }
        for (int i = luma; i < frame.capacity(); i++) {
            frame.put(i, (byte) TINTED_CHROMA);
        }
        return frame;
    }

    @Test
    void maskIsReadableButSmall() {
        for (int height : new int[]{360, 720, 1080, 2160}) {
            int width = height * 16 / 9;
            PreviewStamp.Mask mask = PreviewStamp.mask(TEXT, height);
            int ink = 0;
            for (int y = 0; y < mask.height(); y++) {
                for (int x = 0; x < mask.width(); x++) {
                    if (mask.text(x, y) > 128) {
                        ink++;
                    }
                }
            }
            assertTrue(ink > 100, height + "p: zu wenig Schrift (" + ink + " Bildpunkte)");
            assertTrue(mask.width() < width / 2, height + "p: Wasserzeichen " + mask.width() + " breit");
            assertTrue(mask.height() < height / 16, height + "p: Wasserzeichen " + mask.height() + " hoch");
            assertTrue(mask.height() >= (PreviewFont.HEIGHT + 1) * PreviewStamp.MIN_SCALE, height + "p: Schrift zu klein");
        }
    }

    @Test
    void fixedColoursAndMinimumSize() {
        assertEquals(0xB4FFFFFF, PreviewStamp.TEXT_ARGB);
        assertEquals(0xB4000000, PreviewStamp.SHADOW_ARGB);
        assertEquals(1, PreviewStamp.MIN_SCALE);
        for (int height : new int[]{Integer.MIN_VALUE, -1, 0, 1, 36, 269}) {
            assertEquals(PreviewStamp.MIN_SCALE, PreviewStamp.screenScale(height), height + " px Fensterhöhe");
            assertEquals(PreviewStamp.MIN_SCALE, PreviewStamp.stampScale(height), height + " px Bildhöhe");
        }
        assertEquals(1, PreviewStamp.stampScale(360));
        assertEquals(2, PreviewStamp.stampScale(720));
        assertEquals(3, PreviewStamp.stampScale(1080));
        assertEquals(6, PreviewStamp.stampScale(2160));
    }

    @Test
    void screenPlacementSitsBottomRightInsideTheMargin() {
        for (int[] size : new int[][]{{854, 480}, {1708, 960}, {1920, 1080}, {3840, 2160}}) {
            int width = size[0];
            int height = size[1];
            int scale = PreviewStamp.screenScale(height);
            PreviewStamp.Placement at = PreviewStamp.place(TEXT, width, height, 0);
            assertEquals(scale, at.scale());
            assertEquals(width - PreviewStamp.MARGIN * scale, at.left() + (TEXT.width() + 1) * scale);
            assertEquals(height - PreviewStamp.MARGIN * scale, at.top() + (TEXT.height() + 1) * scale);
            assertTrue(at.left() > width / 2 && at.top() > height * 9 / 10, width + "x" + height + ": " + at);
            PreviewStamp.Placement lifted = PreviewStamp.place(TEXT, width, height, 30);
            assertEquals(at.top() - 30, lifted.top());
        }
    }

    @Test
    void screenTextGrowsWithTheWindowNotWithTheGuiScale() {
        assertEquals(1, PreviewStamp.screenScale(240));
        assertEquals(1, PreviewStamp.screenScale(480));
        assertEquals(1, PreviewStamp.screenScale(720));
        assertEquals(1, PreviewStamp.screenScale(809));
        assertEquals(2, PreviewStamp.screenScale(810));
        assertEquals(2, PreviewStamp.screenScale(960));
        assertEquals(2, PreviewStamp.screenScale(1080));
        assertEquals(3, PreviewStamp.screenScale(1440));
        assertEquals(4, PreviewStamp.screenScale(2160));
    }

    @Test
    void i420StampMarksOnlyTheBottomRightCorner() {
        int width = 1280;
        int height = 720;
        ByteBuffer frame = i420(width, height);
        PreviewStamp.Mask mask = PreviewStamp.mask(TEXT, height);
        int changed = PreviewStamp.stampI420(frame, width, height, mask);
        assertTrue(changed > 100, "nur " + changed + " Bildpunkte geändert");
        int right = width - PreviewStamp.margin(height);
        int bottom = height - PreviewStamp.margin(height);
        int left = right - mask.width();
        int top = bottom - mask.height();
        int brightest = 0;
        int darkest = 255;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = frame.get(y * width + x) & 0xFF;
                boolean inside = x >= left && x < right && y >= top && y < bottom;
                if (!inside) {
                    assertEquals(GREY_LUMA, value, "Luma außerhalb des Wasserzeichens geändert bei " + x + "," + y);
                } else {
                    brightest = Math.max(brightest, value);
                    darkest = Math.min(darkest, value);
                }
            }
        }
        assertTrue(brightest >= 180, "Schrift zu dunkel: " + brightest);
        assertTrue(darkest <= 80, "Schatten zu hell: " + darkest);
        int luma = width * height;
        int chromaWidth = width / 2;
        int neutralised = 0;
        for (int cy = 0; cy < height / 2; cy++) {
            for (int cx = 0; cx < chromaWidth; cx++) {
                int u = frame.get(luma + cy * chromaWidth + cx) & 0xFF;
                int v = frame.get(luma + luma / 4 + cy * chromaWidth + cx) & 0xFF;
                boolean inside = cx * 2 + 1 >= left && cx * 2 < right && cy * 2 + 1 >= top && cy * 2 < bottom;
                if (!inside) {
                    assertEquals(TINTED_CHROMA, u, "U außerhalb geändert bei " + cx + "," + cy);
                    assertEquals(TINTED_CHROMA, v, "V außerhalb geändert bei " + cx + "," + cy);
                } else if (u > TINTED_CHROMA) {
                    neutralised++;
                }
            }
        }
        assertTrue(neutralised > 0, "Schrift ohne neutrale Farbe");
    }

    @Test
    void argbStampKeepsAlphaAndOutside() {
        int width = 640;
        int height = 360;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, 0x80336699);
        PreviewStamp.Mask mask = PreviewStamp.mask(TEXT, height);
        int changed = PreviewStamp.stampArgb(new PreviewStamp.Pixels() {
            @Override
            public int get(int x, int y) {
                return pixels[y * width + x];
            }

            @Override
            public void set(int x, int y, int argb) {
                pixels[y * width + x] = argb;
            }
        }, width, height, mask);
        assertTrue(changed > 50, "nur " + changed + " Bildpunkte geändert");
        int right = width - PreviewStamp.margin(height);
        int bottom = height - PreviewStamp.margin(height);
        int left = right - mask.width();
        int top = bottom - mask.height();
        int brightest = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = pixels[y * width + x];
                assertEquals(0x80, argb >>> 24, "Alpha geändert bei " + x + "," + y);
                boolean inside = x >= left && x < right && y >= top && y < bottom;
                if (!inside) {
                    assertEquals(0x80336699, argb, "Bild außerhalb geändert bei " + x + "," + y);
                } else {
                    brightest = Math.max(brightest, argb & 0xFF);
                }
            }
        }
        assertTrue(brightest >= 200, "Schrift zu dunkel: " + brightest);
    }

    @Test
    void tinyFramesAreSafe() {
        assertDoesNotThrow(() -> {
            PreviewStamp.Mask mask = PreviewStamp.mask(TEXT, 36);
            PreviewStamp.stampI420(i420(64, 36), 64, 36, mask);
            PreviewStamp.stampI420(i420(2, 2), 2, 2, mask);
        });
    }
}
