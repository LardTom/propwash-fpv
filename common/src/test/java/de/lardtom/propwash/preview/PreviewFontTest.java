package de.lardtom.propwash.preview;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewFontTest {
    private static final String VERSION_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.+-_";
    private static final String WATERMARK = "Propwash Preview 0.6.0-rc.1 · not final";

    private static String bitmap(PreviewFont.Raster raster) {
        StringBuilder out = new StringBuilder();
        for (int y = 0; y < raster.height(); y++) {
            for (int x = 0; x < raster.width(); x++) {
                out.append(raster.ink(x, y) ? '#' : '.');
            }
            out.append('\n');
        }
        return out.toString();
    }

    @Test
    void everyWatermarkAndVersionCharacterHasItsOwnGlyph() {
        for (char c : (WATERMARK + VERSION_CHARACTERS + "?").toCharArray()) {
            assertTrue(PreviewFont.covers(c), "kein Zeichen für „" + c + "“");
        }
        Map<String, Character> seen = new HashMap<>();
        for (char c : (VERSION_CHARACTERS + "·?").toCharArray()) {
            PreviewFont.Raster raster = PreviewFont.raster(String.valueOf(c));
            assertTrue(raster.inkCount() > 0, "„" + c + "“ ist leer");
            assertNull(seen.put(bitmap(raster), c), "„" + c + "“ sieht aus wie „" + seen.get(bitmap(raster)) + "“");
        }
    }

    @Test
    void capitalsAndDigitsStayAboveTheBaselineAndDescendersGoBelow() {
        for (char c : "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()) {
            PreviewFont.Raster raster = PreviewFont.raster(String.valueOf(c));
            for (int y = PreviewFont.ASCENT; y < PreviewFont.HEIGHT; y++) {
                for (int x = 0; x < raster.width(); x++) {
                    assertFalse(raster.ink(x, y), "„" + c + "“ unter der Grundlinie");
                }
            }
            boolean top = false;
            for (int x = 0; x < raster.width(); x++) {
                top |= raster.ink(x, 0);
            }
            assertTrue(top, "„" + c + "“ nicht in voller Höhe");
        }
        for (char c : "gjpqy".toCharArray()) {
            PreviewFont.Raster raster = PreviewFont.raster(String.valueOf(c));
            boolean below = false;
            for (int x = 0; x < raster.width(); x++) {
                below |= raster.ink(x, PreviewFont.HEIGHT - 1);
            }
            assertTrue(below, "„" + c + "“ ohne Unterlänge");
        }
    }

    @Test
    void watermarkHasAFixedSize() {
        PreviewFont.Raster raster = PreviewFont.raster(WATERMARK);
        assertEquals(192, raster.width());
        assertEquals(PreviewFont.HEIGHT, raster.height());
        assertEquals(PreviewFont.width(WATERMARK), raster.width());
        assertTrue(raster.inkCount() > 300, "zu wenig Schrift: " + raster.inkCount());
    }

    @Test
    void unknownCharactersShowTheFallback() {
        assertFalse(PreviewFont.covers('€'));
        assertFalse(PreviewFont.covers(0x1F600));
        assertEquals(bitmap(PreviewFont.raster("?")), bitmap(PreviewFont.raster("€")));
        assertEquals(bitmap(PreviewFont.raster("a?b")), bitmap(PreviewFont.raster("a😀b")));
        assertEquals(0, PreviewFont.raster("").width());
    }

    @Test
    void rectanglesCoverExactlyTheInk() {
        PreviewFont.Raster raster = PreviewFont.raster(WATERMARK);
        int[] covered = new int[raster.width() * raster.height()];
        for (int i = 0; i < raster.rectCount(); i++) {
            int x0 = raster.rect(i, 0);
            int y0 = raster.rect(i, 1);
            int x1 = raster.rect(i, 2);
            int y1 = raster.rect(i, 3);
            assertTrue(x0 < x1 && y0 < y1, "leeres Rechteck " + i);
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    covered[y * raster.width() + x]++;
                }
            }
        }
        for (int y = 0; y < raster.height(); y++) {
            for (int x = 0; x < raster.width(); x++) {
                assertEquals(raster.ink(x, y) ? 1 : 0, covered[y * raster.width() + x], "Bildpunkt " + x + "," + y);
            }
        }
        assertTrue(raster.rectCount() < raster.inkCount() / 2, raster.rectCount() + " Rechtecke für " + raster.inkCount() + " Bildpunkte");
    }
}
