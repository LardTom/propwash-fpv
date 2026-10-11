package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdCoverage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdTextCacheTest {
    private static final OsdGlyphs.Options DEFAULT = OsdGlyphs.Options.of(false, false);
    private static final Executor DIRECT = Runnable::run;
    private static final List<String> LABELS = List.of("ARMED", "DISARMED", "LQ", "RSSI", "VTX R1", "ALT", "DIST", "HOME", "FLY",
            "N", "NE", "E", "SE", "S", "SW", "W", "NW", "SAT 12", "GPS", "ANGLE", "ACRO", "HORIZON", "REC", "PITCH");

    private static OsdTextCache ready(OsdGlyphs glyphs, int budget) {
        glyphs.update(DEFAULT);
        return new OsdTextCache(glyphs, budget);
    }

    @Test
    void newCoverageComputationsPerFrameAreCappedByTheBudget() {
        OsdGlyphs glyphs = new OsdGlyphs(new OsdTestFont(), DIRECT);
        OsdTextCache cache = ready(glyphs, OsdTextCache.FRAME_BUDGET);
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            texts.add("ALT " + i + "m");
        }
        int frames = 0;
        int drawn;
        do {
            cache.nextFrame();
            int before = cache.computed();
            drawn = 0;
            for (String text : texts) {
                if (cache.text(text, OsdConfig.Outline.THIN, 0.5F, OsdTestFont.matching()) != null) {
                    drawn++;
                }
            }
            assertTrue(cache.computed() - before <= OsdTextCache.FRAME_BUDGET, "mehr neue Konturen als erlaubt in einem Frame");
            frames++;
        } while (drawn < texts.size() && frames < 100);
        assertEquals(texts.size(), drawn);
        assertEquals((texts.size() + OsdTextCache.FRAME_BUDGET - 1) / OsdTextCache.FRAME_BUDGET, frames);
        assertEquals(texts.size(), cache.computed());
        cache.nextFrame();
        for (String text : texts) {
            assertNotNull(cache.text(text, OsdConfig.Outline.THIN, 0.5F, OsdTestFont.matching()));
        }
        assertEquals(texts.size(), cache.computed());
    }

    @Test
    void configuratorPreviewWithChangingValuesStaysWithinTheFrameBudget() {
        OsdGlyphs glyphs = new OsdGlyphs(new OsdTestFont(), DIRECT);
        OsdTextCache cache = ready(glyphs, OsdTextCache.FRAME_BUDGET);
        int frames = 1200;
        int warmup = 200;
        long[] nanos = new long[frames - warmup];
        int[] sidebars = sidebarRects();
        int maxNew = 0;
        int fallbackFrames = 0;
        for (int frame = 0; frame < frames; frame++) {
            long start = System.nanoTime();
            cache.nextFrame();
            int before = cache.computed();
            int fallback = 0;
            for (String label : LABELS) {
                if (cache.text(label, OsdConfig.Outline.THIN, 0.5F, OsdTestFont.matching()) == null) {
                    fallback++;
                }
            }
            for (String value : values(frame)) {
                if (cache.text(value, OsdConfig.Outline.THIN, 0.5F, OsdTestFont.matching()) == null) {
                    fallback++;
                }
            }
            cache.rects(sidebars, sidebars.length / 4, OsdConfig.Outline.THIN, 0.5F);
            cache.rects(new int[]{-7, 0, -2, 1, 3, 0, 8, 1, 0, 0, 1, 1, 0, -4, 1, -2}, 4, OsdConfig.Outline.THIN, 0.5F);
            long elapsed = System.nanoTime() - start;
            maxNew = Math.max(maxNew, cache.computed() - before);
            if (frame >= warmup) {
                nanos[frame - warmup] = elapsed;
                if (fallback > 0) {
                    fallbackFrames++;
                }
            }
        }
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        long median = sorted[sorted.length / 2];
        long p95 = sorted[(int) (sorted.length * 0.95)];
        long max = sorted[sorted.length - 1];
        System.out.printf(Locale.ROOT, "OSD-Vorschau: %d Frames, Median %.3f ms, p95 %.3f ms, Maximum %.3f ms, höchstens %d neue Konturen je Frame%n",
                nanos.length, median / 1.0e6, p95 / 1.0e6, max / 1.0e6, maxNew);
        assertTrue(maxNew <= OsdTextCache.FRAME_BUDGET);
        assertEquals(0, fallbackFrames, "wechselnde Werte der Vorschau passen in das Budget und fallen nicht auf die Spielschrift zurück");
        assertEquals(0, cache.clears());
        assertTrue(p95 < 4_000_000L, "p95 je Frame " + p95 / 1.0e6 + " ms");
        assertTrue(max < 50_000_000L, "Maximum je Frame " + max / 1.0e6 + " ms");
    }

    private static List<String> values(int frame) {
        int t = frame / 2;
        return List.of(
                String.format(Locale.ROOT, "%.1fV", 16.8 - t * 0.01),
                String.format(Locale.ROOT, "%.2fV", 4.2 - t * 0.001),
                String.format(Locale.ROOT, "%.1fA", 12.0 + (t % 50) * 0.3),
                t % 100 + " km/h",
                "ALT " + t % 80 + "m",
                t / 60 + ":" + String.format(Locale.ROOT, "%02d", t % 60),
                t % 360 + "°");
    }

    private static int[] sidebarRects() {
        int[] rects = new int[56];
        int n = 0;
        for (int i = -3; i <= 3; i++) {
            int y = i * 11;
            int length = i == 0 ? 5 : 3;
            rects[n++] = -40 - length;
            rects[n++] = y;
            rects[n++] = -40;
            rects[n++] = y + 1;
            rects[n++] = 41;
            rects[n++] = y;
            rects[n++] = 41 + length;
            rects[n++] = y + 1;
        }
        return rects;
    }

    @Test
    void textOverBudgetFallsBackAndIsDrawnInALaterFrame() {
        OsdGlyphs glyphs = new OsdGlyphs(new OsdTestFont(), DIRECT);
        OsdTextCache cache = ready(glyphs, 1);
        cache.nextFrame();
        assertNotNull(cache.text("LQ 100", OsdConfig.Outline.THIN, 0.5F, OsdTestFont.matching()));
        assertEquals(null, cache.text("RSSI -63", OsdConfig.Outline.THIN, 0.5F, OsdTestFont.matching()));
        assertEquals(1, cache.deferred());
        assertNotNull(cache.text("LQ 100", OsdConfig.Outline.THIN, 0.5F, OsdTestFont.matching()));
        cache.nextFrame();
        assertNotNull(cache.text("RSSI -63", OsdConfig.Outline.THIN, 0.5F, OsdTestFont.matching()));
    }

    @Test
    void rectShapesAreComputedOncePerDistinctRectSet() {
        OsdGlyphs glyphs = new OsdGlyphs(new OsdTestFont(), DIRECT);
        OsdTextCache cache = ready(glyphs, OsdTextCache.FRAME_BUDGET);
        int[] cross = {0, 0, 10, 2, 4, -4, 6, 6};
        OsdTextCache.Shape first = cache.rects(cross.clone(), 2, OsdConfig.Outline.THIN, 0.5F);
        assertSame(first, cache.rects(cross.clone(), 2, OsdConfig.Outline.THIN, 0.5F));
        int[] buffer = new int[64];
        System.arraycopy(cross, 0, buffer, 0, cross.length);
        buffer[20] = 99;
        assertSame(first, cache.rects(buffer, 2, OsdConfig.Outline.THIN, 0.5F));
        assertNotSame(first, cache.rects(cross.clone(), 2, OsdConfig.Outline.THICK, 0.5F));
        assertNotSame(first, cache.rects(cross.clone(), 2, OsdConfig.Outline.THIN, 0.25F));
        assertNotSame(first, cache.rects(cross.clone(), 1, OsdConfig.Outline.THIN, 0.5F));
        OsdCoverage.Shapes direct = OsdCoverage.rects(cross, 2, OsdConfig.Outline.THIN, 0.5F);
        float[] expected = new float[(direct.outlineCount() + direct.fillCount()) * 4];
        System.arraycopy(direct.outline(), 0, expected, 0, direct.outlineCount() * 4);
        System.arraycopy(direct.fill(), 0, expected, direct.outlineCount() * 4, direct.fillCount() * 4);
        assertArrayEquals(expected, first.quads());
        assertEquals(direct.outlineCount(), first.outlineCount());
    }

    @Test
    void shapeBoundsMatchTheSubmittedQuads() {
        OsdCoverage.Shapes shapes = OsdCoverage.rects(new int[]{0, 0, 10, 2}, 1, OsdConfig.Outline.THIN, 0.5F);
        OsdTextCache.Shape shape = OsdTextCache.Shape.of(shapes, 0);
        assertArrayEquals(new int[]{0, 0, 10, 2}, shape.fillBounds());
        assertArrayEquals(new int[]{-1, -1, 12, 4}, shape.allBounds());
    }

    @Test
    void edgesAreQuantizedLikeTheTextOutline() {
        assertEquals(0.5F, OsdTextCache.quantizeEdge(0.5F));
        assertEquals(Math.round(1.0F / 3.0F * 1024.0F) / 1024.0F, OsdTextCache.quantizeEdge(1.0F / 3.0F));
        assertEquals(OsdTextCache.quantizeEdge(0.33333334F), OsdTextCache.quantizeEdge(0.33333328F));
    }
}
