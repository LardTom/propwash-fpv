package de.lardtom.propwash.ui.client.theme;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaletteContrastTest {
    private static final Map<String, Integer> SURFACES = Map.of(
            "BACKGROUND", Palette.BACKGROUND,
            "PANEL", Palette.PANEL,
            "PANEL_HEADER", Palette.PANEL_HEADER,
            "HEADER", Palette.HEADER,
            "SIDEBAR", Palette.SIDEBAR,
            "FOOTER", Palette.FOOTER,
            "FIELD", Palette.FIELD,
            "FIELD_HOVER", Palette.FIELD_HOVER,
            "TOOLTIP", Palette.TOOLTIP);

    private static final Map<String, Integer> TEXTS = Map.ofEntries(
            Map.entry("TEXT", Palette.TEXT),
            Map.entry("TEXT_MUTED", Palette.TEXT_MUTED),
            Map.entry("TEXT_FAINT", Palette.TEXT_FAINT),
            Map.entry("ACCENT", Palette.ACCENT),
            Map.entry("SUCCESS", Palette.SUCCESS),
            Map.entry("WARNING", Palette.WARNING),
            Map.entry("ERROR", Palette.ERROR),
            Map.entry("INFO", Palette.INFO),
            Map.entry("ROLL", Palette.ROLL),
            Map.entry("PITCH", Palette.PITCH),
            Map.entry("YAW", Palette.YAW),
            Map.entry("THROTTLE", Palette.THROTTLE),
            Map.entry("AUX", Palette.AUX));

    @Test
    void textColorsAreReadableOnEverySurface() {
        for (Map.Entry<String, Integer> text : TEXTS.entrySet()) {
            for (Map.Entry<String, Integer> surface : SURFACES.entrySet()) {
                double ratio = Contrast.ratio(text.getValue(), surface.getValue());
                assertTrue(ratio >= Contrast.READABLE, text.getKey() + " auf " + surface.getKey() + ": " + ratio);
            }
        }
    }

    @Test
    void buttonLabelsAreReadableInEveryState() {
        int[] faces = {Palette.BUTTON, Palette.BUTTON_HOVER, Palette.BUTTON_PRESSED, Palette.DANGER, Palette.DANGER_HOVER,
                Palette.DANGER_PRESSED};
        for (int face : faces) {
            assertTrue(Contrast.readable(Palette.TEXT, face), Integer.toHexString(face));
        }
        assertTrue(Contrast.readable(Palette.TEXT_MUTED, Palette.BUTTON));
        assertTrue(Contrast.readable(Palette.TEXT_MUTED, Palette.BUTTON_HOVER));
        for (int face : new int[]{Palette.ACCENT, Palette.ACCENT_HOVER, Palette.ACCENT_PRESSED}) {
            assertTrue(Contrast.readable(Palette.ACCENT_TEXT, face), Integer.toHexString(face));
            assertEquals(Palette.ACCENT_TEXT, Contrast.readableOn(face));
        }
        assertEquals(Palette.TEXT, Contrast.readableOn(Palette.PANEL));
    }

    @Test
    void statusColorsAreReadableOnTheirBackgrounds() {
        assertTrue(Contrast.readable(Palette.SUCCESS, Palette.SUCCESS_BG));
        assertTrue(Contrast.readable(Palette.WARNING, Palette.WARNING_BG));
        assertTrue(Contrast.readable(Palette.ERROR, Palette.ERROR_BG));
        assertTrue(Contrast.readable(Palette.TEXT, Palette.SUCCESS_BG));
        assertTrue(Contrast.readable(Palette.TEXT, Palette.WARNING_BG));
        assertTrue(Contrast.readable(Palette.TEXT, Palette.ERROR_BG));
    }

    @Test
    void disabledTextStaysVisibleButRecedes() {
        double disabled = Contrast.ratio(Palette.TEXT_DISABLED, Palette.DISABLED);
        assertTrue(disabled >= 2.0, "zu schwach: " + disabled);
        assertTrue(disabled < Contrast.ratio(Palette.TEXT_MUTED, Palette.DISABLED));
    }

    @Test
    void accentIsTheAmberOfTheBrand() {
        int rgb = Palette.ACCENT & 0xFFFFFF;
        assertTrue(Math.abs(((rgb >> 16) & 0xFF) - 0xFF) <= 8);
        assertTrue(Math.abs(((rgb >> 8) & 0xFF) - 0xBB) <= 16);
        assertTrue((rgb & 0xFF) <= 0x20);
    }

    @Test
    void everyPaletteColorCarriesAlpha() throws IllegalAccessException {
        int count = 0;
        for (Field field : Palette.class.getDeclaredFields()) {
            if (field.getType() == int.class && Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers())) {
                int color = field.getInt(null);
                assertTrue(Palette.alpha(color) > 0, field.getName() + " ohne Alpha");
                count++;
            }
        }
        assertTrue(count > 30);
    }

    @Test
    void surfacesAreOpaqueAndDark() {
        for (Map.Entry<String, Integer> surface : SURFACES.entrySet()) {
            int color = surface.getValue();
            assertTrue(Palette.alpha(color) >= 0xF0, surface.getKey());
            assertTrue(Contrast.luminance(color & 0xFFFFFF) < 0.05, surface.getKey());
        }
    }

    @Test
    void contrastRatioMatchesWcagReferencePoints() {
        assertEquals(21.0, Contrast.ratio(0xFFFFFFFF, 0xFF000000), 1.0E-9);
        assertEquals(1.0, Contrast.ratio(0xFF777777, 0xFF777777), 1.0E-9);
        assertEquals(Contrast.ratio(Palette.TEXT, Palette.PANEL), Contrast.ratio(Palette.PANEL, Palette.TEXT), 1.0E-9);
        assertEquals(4.48, Contrast.ratio(0xFF777777, 0xFFFFFFFF), 0.01);
    }

    @Test
    void translucentForegroundIsBlendedOverTheSurface() {
        assertEquals(Palette.PANEL, Contrast.over(Palette.withAlpha(Palette.TEXT, 0), Palette.PANEL));
        assertEquals(Palette.TEXT, Contrast.over(Palette.TEXT, Palette.PANEL));
        double half = Contrast.ratio(Palette.withAlpha(Palette.TEXT, 0x80), Palette.PANEL);
        assertTrue(half > 1.0 && half < Contrast.ratio(Palette.TEXT, Palette.PANEL));
    }

    @Test
    void mixAndAlphaHelpers() {
        assertEquals(0xFF000000, Palette.mix(0xFF000000, 0xFFFFFFFF, 0.0F));
        assertEquals(0xFFFFFFFF, Palette.mix(0xFF000000, 0xFFFFFFFF, 1.0F));
        assertEquals(0xFF808080, Palette.mix(0xFF000000, 0xFFFFFFFF, 0.5F));
        assertEquals(0x80FFBB00, Palette.withAlpha(Palette.ACCENT, 0x80));
        assertEquals(0xFFFFBB00, Palette.withAlpha(Palette.ACCENT, 999));
        assertEquals(0xC0, Palette.alpha(0xC0123456));
    }

    @Test
    void labelColumnLeavesRoomForTheControl() {
        for (int width = 0; width <= 600; width += 7) {
            int label = Metrics.labelWidth(width);
            assertTrue(label >= 0 && label <= Metrics.LABEL_MAX, "Breite " + width);
            if (Metrics.hasLabelColumn(width)) {
                assertTrue(width - label >= Metrics.CONTROL_MIN, "Breite " + width);
            }
        }
    }

    @Test
    void sliderHandleAndMouseMappingAreInverse() {
        int left = 40;
        int width = 120;
        for (double f = 0.0; f <= 1.0; f += 0.125) {
            int center = Metrics.sliderHandleCenter(left, width, f);
            assertEquals(f, Metrics.sliderFraction(center, left, width), 1.0 / (width - 2.0 * Metrics.SLIDER_INSET));
        }
        assertEquals(0.0, Metrics.sliderFraction(-100, left, width));
        assertEquals(1.0, Metrics.sliderFraction(10_000, left, width));
        assertEquals(left + Metrics.SLIDER_INSET, Metrics.sliderHandleCenter(left, width, Double.NaN));
    }

    @Test
    void columnsFitTheAvailableWidth() {
        assertEquals(1, Metrics.columns(2 * Metrics.COLUMN_MIN + Metrics.GAP - 1));
        assertEquals(2, Metrics.columns(2 * Metrics.COLUMN_MIN + Metrics.GAP));
        for (int width = 50; width <= 800; width += 13) {
            int columns = Metrics.columns(width);
            int columnWidth = Metrics.columnWidth(width, columns);
            assertTrue(columns * columnWidth + (columns - 1) * Metrics.GAP <= Math.max(width, 1), "Breite " + width);
            assertTrue(columnWidth <= Metrics.COLUMN_MAX);
        }
    }
}
