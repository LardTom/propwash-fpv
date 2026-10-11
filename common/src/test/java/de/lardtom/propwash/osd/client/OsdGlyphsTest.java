package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdCoverage;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdGlyphsTest {
    private static final OsdGlyphs.Options DEFAULT = OsdGlyphs.Options.of(false, false);
    private static final OsdGlyphs.Options UNIFORM = OsdGlyphs.Options.of(true, false);
    private static final Executor DIRECT = Runnable::run;
    private static final List<String> HUD_TEXTS = List.of("12.6V", "4.20V", "LQ 100", "RSSI -63", "ALT 12m", "0:42", "45 km/h", "ARMED", "N", "NE");

    @Test
    void persistentWidthMismatchNeverReloadsTheGlyphTable() {
        OsdTestFont font = new OsdTestFont();
        OsdGlyphs glyphs = new OsdGlyphs(font, DIRECT);
        OsdTextCache cache = new OsdTextCache(glyphs, OsdTextCache.FRAME_BUDGET);
        AtomicInteger widthCalls = new AtomicInteger();
        glyphs.update(DEFAULT);
        int generation = glyphs.generation();
        for (int frame = 0; frame < 20_000; frame++) {
            glyphs.update(DEFAULT);
            cache.nextFrame();
            for (String text : HUD_TEXTS) {
                assertNull(cache.text(text, OsdConfig.Outline.THIN, 0.5F, OsdTestFont.skewed(widthCalls)));
            }
        }
        assertEquals(1, font.loads.get(), "Glyphentabelle wurde erneut geladen");
        assertEquals(1, glyphs.loads());
        assertEquals(generation, glyphs.generation(), "Generation hat sich durch die Breiten-Abweichung geändert");
        assertEquals(0, cache.clears(), "Text-Cache wurde geleert");
        assertEquals(HUD_TEXTS.size(), widthCalls.get(), "jeder abweichende Text wird nur einmal gemessen");
        assertEquals(HUD_TEXTS.size(), cache.unsupported());
        assertEquals(0, cache.computed());
        assertTrue(glyphs.ready());
    }

    @Test
    void mismatchAndMatchSitSideBySideWithoutTouchingEachOther() {
        OsdGlyphs glyphs = new OsdGlyphs(new OsdTestFont(), DIRECT);
        OsdTextCache cache = new OsdTextCache(glyphs, OsdTextCache.FRAME_BUDGET);
        glyphs.update(DEFAULT);
        AtomicInteger skewedCalls = new AtomicInteger();
        for (int frame = 0; frame < 1000; frame++) {
            cache.nextFrame();
            assertNull(cache.text("12.6V", OsdConfig.Outline.THIN, 0.5F, OsdTestFont.skewed(skewedCalls)));
            assertNotNull(cache.text("LQ 100", OsdConfig.Outline.THIN, 0.5F, OsdTestFont.matching()));
        }
        assertEquals(1, skewedCalls.get());
        assertEquals(1, cache.computed());
        assertEquals(1, cache.unsupported());
        assertEquals(1, glyphs.generation());
    }

    @Test
    void missingGlyphIsRememberedLikeAMismatch() {
        OsdGlyphs glyphs = new OsdGlyphs(new OsdTestFont(), DIRECT);
        OsdTextCache cache = new OsdTextCache(glyphs, OsdTextCache.FRAME_BUDGET);
        glyphs.update(DEFAULT);
        assertSame(OsdGlyphs.UNSUPPORTED, glyphs.layout("高度 12m", OsdTestFont.matching()));
        for (int frame = 0; frame < 100; frame++) {
            cache.nextFrame();
            assertNull(cache.text("高度 12m", OsdConfig.Outline.THICK, 1.0F, OsdTestFont.matching()));
        }
        assertEquals(1, cache.unsupported());
        assertEquals(1, cache.size());
    }

    @Test
    void matchingTextIsComputedOnceAndKeptAcrossFrames() {
        OsdGlyphs glyphs = new OsdGlyphs(new OsdTestFont(), DIRECT);
        OsdTextCache cache = new OsdTextCache(glyphs, OsdTextCache.FRAME_BUDGET);
        glyphs.update(DEFAULT);
        cache.nextFrame();
        OsdTextCache.Shape first = cache.text("ALT 12m", OsdConfig.Outline.THIN, 0.5F, OsdTestFont.matching());
        assertNotNull(first);
        for (int frame = 0; frame < 5000; frame++) {
            glyphs.update(DEFAULT);
            cache.nextFrame();
            assertSame(first, cache.text("ALT 12m", OsdConfig.Outline.THIN, 0.5F, OsdTestFont.matching()));
        }
        assertEquals(1, cache.computed());
        assertEquals(0, cache.clears());
    }

    @Test
    void cachedShapeKeepsTheCoverageOfTheGlyphMask() {
        OsdGlyphs glyphs = new OsdGlyphs(new OsdTestFont(), DIRECT);
        OsdTextCache cache = new OsdTextCache(glyphs, OsdTextCache.FRAME_BUDGET);
        glyphs.update(DEFAULT);
        OsdGlyphs.Bitmap bitmap = glyphs.layout("4.20V", OsdTestFont.matching());
        assertNotNull(bitmap);
        assertEquals(OsdTestFont.advances("4.20V"), bitmap.width());
        OsdCoverage.Shapes direct = OsdCoverage.mask(bitmap.mask(), bitmap.width(), bitmap.height(), OsdConfig.Outline.THIN, 0.25F);
        OsdTextCache.Shape shape = cache.text("4.20V", OsdConfig.Outline.THIN, 0.25F, OsdTestFont.matching());
        assertNotNull(shape);
        assertEquals(direct.outlineCount(), shape.outlineCount());
        assertEquals(direct.fillCount(), shape.fillCount());
        float[] expected = new float[(direct.outlineCount() + direct.fillCount()) * 4];
        System.arraycopy(direct.outline(), 0, expected, 0, direct.outlineCount() * 4);
        System.arraycopy(direct.fill(), 0, expected, direct.outlineCount() * 4, direct.fillCount() * 4);
        assertArrayEquals(expected, shape.quads());
        assertEquals(bitmap.top(), shape.top());
    }

    @Test
    void fontOptionChangeRebuildsOnceAndClearsTheCacheOnce() {
        OsdTestFont font = new OsdTestFont();
        OsdGlyphs glyphs = new OsdGlyphs(font, DIRECT);
        OsdTextCache cache = new OsdTextCache(glyphs, OsdTextCache.FRAME_BUDGET);
        glyphs.update(DEFAULT);
        cache.nextFrame();
        assertNotNull(cache.text("0:42", OsdConfig.Outline.THIN, 0.5F, OsdTestFont.matching()));
        for (int frame = 0; frame < 300; frame++) {
            glyphs.update(UNIFORM);
            cache.nextFrame();
            assertNull(cache.text("0:42", OsdConfig.Outline.THIN, 0.5F, OsdTestFont.matching()));
        }
        assertEquals(2, font.loads.get());
        assertEquals(1, cache.clears());
        assertEquals(UNIFORM, glyphs.table().options());
        int generation = glyphs.generation();
        for (int frame = 0; frame < 300; frame++) {
            glyphs.update(UNIFORM);
        }
        assertEquals(generation, glyphs.generation());
        assertEquals(2, font.loads.get());
    }

    @Test
    void tableIsBuiltOffTheRenderThreadAndTheGameFontIsUsedUntilItIsReady() throws InterruptedException {
        OsdTestFont font = new OsdTestFont();
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        OsdGlyphs glyphs = new OsdGlyphs(font, queue::add);
        OsdTextCache cache = new OsdTextCache(glyphs, OsdTextCache.FRAME_BUDGET);
        for (int frame = 0; frame < 50; frame++) {
            glyphs.update(DEFAULT);
            cache.nextFrame();
            assertNull(cache.text("45 km/h", OsdConfig.Outline.THIN, 0.5F, OsdTestFont.matching()));
        }
        assertFalse(glyphs.ready());
        assertTrue(glyphs.building());
        assertEquals(1, queue.size(), "nur ein Aufbau je Schrift-Einstellung");
        assertEquals(0, font.loads.get(), "der Aufbau darf nicht im Zeichen-Thread laufen");
        assertEquals(0, cache.size(), "nicht fertige Tabelle darf nicht als Abweichung gemerkt werden");
        Thread worker = new Thread(queue.poll(), "osd-glyph-test");
        worker.start();
        worker.join();
        glyphs.update(DEFAULT);
        cache.nextFrame();
        assertTrue(glyphs.ready());
        assertSame(worker, glyphs.lastBuildThread());
        assertNotNull(cache.text("45 km/h", OsdConfig.Outline.THIN, 0.5F, OsdTestFont.matching()));
        assertEquals(1, font.loads.get());
    }

    @Test
    void resourceReloadReplacesAPendingBuild() {
        OsdTestFont font = new OsdTestFont();
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        OsdGlyphs glyphs = new OsdGlyphs(font, queue::add);
        glyphs.update(DEFAULT);
        OsdGlyphs.Table reloaded = glyphs.build(DEFAULT, options -> Map.of((int) 'X', OsdTestFont.glyph('X')));
        glyphs.install(reloaded);
        int generation = glyphs.generation();
        queue.poll().run();
        for (int frame = 0; frame < 100; frame++) {
            glyphs.update(DEFAULT);
        }
        assertSame(reloaded, glyphs.table());
        assertEquals(generation, glyphs.generation());
        assertTrue(queue.isEmpty());
    }

    @Test
    void failingLoaderInstallsAnEmptyTableWithoutRetrying() {
        AtomicInteger attempts = new AtomicInteger();
        OsdGlyphs glyphs = new OsdGlyphs(options -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("kaputte Schrift");
        }, DIRECT);
        OsdTextCache cache = new OsdTextCache(glyphs, OsdTextCache.FRAME_BUDGET);
        for (int frame = 0; frame < 1000; frame++) {
            glyphs.update(DEFAULT);
            cache.nextFrame();
            assertNull(cache.text("LQ 100", OsdConfig.Outline.THIN, 0.5F, OsdTestFont.matching()));
        }
        assertEquals(1, attempts.get());
        assertTrue(glyphs.ready());
        assertTrue(glyphs.table().glyphs().isEmpty());
    }

    @Test
    void blankTextIsDrawnAsNothingAndNotAsFallback() {
        OsdGlyphs glyphs = new OsdGlyphs(new OsdTestFont(), DIRECT);
        OsdTextCache cache = new OsdTextCache(glyphs, OsdTextCache.FRAME_BUDGET);
        glyphs.update(DEFAULT);
        OsdGlyphs.Bitmap blank = glyphs.layout("   ", OsdTestFont.matching());
        assertNotNull(blank);
        assertNotSame(OsdGlyphs.UNSUPPORTED, blank);
        OsdTextCache.Shape shape = cache.text("   ", OsdConfig.Outline.THIN, 0.5F, OsdTestFont.matching());
        assertNotNull(shape);
        assertTrue(shape.isEmpty());
    }
}
