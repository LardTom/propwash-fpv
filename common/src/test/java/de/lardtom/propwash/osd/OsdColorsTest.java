package de.lardtom.propwash.osd;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdColorsTest {
    @Test
    void parsesLongAndShortHex() {
        assertEquals(0x12AB34, OsdColors.parseRgb("#12ab34"));
        assertEquals(0x12AB34, OsdColors.parseRgb("12AB34"));
        assertEquals(0xFFAA00, OsdColors.parseRgb("#fa0"));
        assertEquals(0xFFFFFF, OsdColors.parseRgb("  #FFFFFF "));
        assertEquals(0x000000, OsdColors.parseRgb("#000000"));
        assertEquals("#12AB34", OsdColors.hex(0xFF12AB34));
        assertEquals("#00000F", OsdColors.hex(0x0F));
    }

    @Test
    void rejectsInvalidHex() {
        for (String invalid : List.of("", "#", "#12", "#1234", "#12345", "#1234567", "#GGGGGG", "xyz", "#12 345", "+12345", "#-12345")) {
            assertEquals(OsdColors.INVALID, OsdColors.parseRgb(invalid), invalid);
            assertFalse(OsdColors.isValid(invalid), invalid);
        }
        assertEquals(OsdColors.INVALID, OsdColors.parseRgb(null));
        assertEquals("#FFFFFF", OsdColors.normalize("nope", "#FFFFFF"));
        assertEquals("#ABCDEF", OsdColors.normalize("abcdef", "#FFFFFF"));
        assertEquals(0x123456, OsdColors.rgbOr("broken", 0xFF123456));
    }

    @Test
    void alphaBytesAreRoundedAndClamped() {
        assertEquals(0, OsdColors.alphaByte(0.0F));
        assertEquals(255, OsdColors.alphaByte(1.0F));
        assertEquals(128, OsdColors.alphaByte(0.5F));
        assertEquals(191, OsdColors.alphaByte(0.75F));
        assertEquals(0, OsdColors.alphaByte(-0.3F));
        assertEquals(0, OsdColors.alphaByte(Float.NaN));
        assertEquals(255, OsdColors.alphaByte(3.0F));
        assertEquals(255, OsdColors.alphaByte(Float.POSITIVE_INFINITY));
    }

    @Test
    void withAlphaReplacesAndScaleAlphaMultiplies() {
        assertEquals(0xBFFFFFFF, OsdColors.withAlpha(0xFFFFFF, 0.75F));
        assertEquals(0x00FF4A4A, OsdColors.withAlpha(0xFFFF4A4A, 0.0F));
        assertEquals(0xFF6CFF6C, OsdColors.withAlpha(0x006CFF6C, 1.0F));
        assertEquals(0x80123456, OsdColors.scaleAlpha(0xFF123456, 0.5F));
        assertEquals(0x40123456, OsdColors.scaleAlpha(0x80123456, 0.5F));
        assertEquals(0x00123456, OsdColors.scaleAlpha(0xFF123456, 0.0F));
        assertEquals(0x00123456, OsdColors.scaleAlpha(0xFF123456, Float.NaN));
        assertEquals(0xFF123456, OsdColors.scaleAlpha(0xFF123456, 4.0F));
        assertEquals(0xFF, OsdColors.alpha(0xFF000000));
        assertEquals(0x7F, OsdColors.alpha(0x7FFFFFFF));
    }

    @Test
    void shadeFollowsTheTextAlpha() {
        assertEquals(0xFF000000, OsdColors.shade(0xFFFFFFFF, 1.0F));
        assertEquals(0x40000000, OsdColors.shade(0x80FFFFFF, 0.5F));
        assertEquals(0, OsdColors.alpha(OsdColors.shade(0x00FFFFFF, 1.0F)));
        assertEquals(0, OsdColors.alpha(OsdColors.shade(0xFFFFFFFF, OsdConfig.Outline.NONE.alphaFactor())));
        int thin = OsdColors.alpha(OsdColors.shade(0xBFFFFFFF, OsdConfig.Outline.THIN.alphaFactor()));
        int thick = OsdColors.alpha(OsdColors.shade(0xBFFFFFFF, OsdConfig.Outline.THICK.alphaFactor()));
        assertTrue(thin < thick && thick == 0xBF, thin + " / " + thick);
        assertEquals(0, OsdColors.alpha(OsdColors.shade(0xFFFFFFFF, OsdConfig.Background.OFF.alphaFactor())));
        assertTrue(OsdConfig.Background.SUBTLE.alphaFactor() < OsdConfig.Background.SOLID.alphaFactor());
    }

    @Test
    void percentFractionsAreClamped() {
        assertEquals(0.75F, OsdColors.fraction(75));
        assertEquals(1.0F, OsdColors.fraction(150));
        assertEquals(0.0F, OsdColors.fraction(-5));
    }

    @Test
    void elementAlphaCombinesGlobalAndLineOpacity() {
        OsdConfig config = OsdConfig.defaults();
        config.opacity = 50;
        config.lineOpacity = 40;
        assertEquals(0.5F, config.elementAlpha(OsdElement.FLIGHT_TIME), 1.0E-6F);
        assertEquals(0.2F, config.elementAlpha(OsdElement.CROSSHAIR), 1.0E-6F);
        assertEquals(0.2F, config.elementAlpha(OsdElement.ARTIFICIAL_HORIZON), 1.0E-6F);
        assertEquals(0.2F, config.elementAlpha(OsdElement.HORIZON_SIDEBARS), 1.0E-6F);
        assertEquals(0.5F, config.elementAlpha(OsdElement.HOME_MARKER), 1.0E-6F);
        config.opacity = 0;
        for (OsdElement element : OsdElement.values()) {
            assertEquals(0, OsdColors.alphaByte(config.elementAlpha(element)), element.key());
        }
        config.opacity = 100;
        config.lineOpacity = 100;
        assertEquals(255, OsdColors.alphaByte(config.elementAlpha(OsdElement.CROSSHAIR)));
    }

    @Test
    void elementOverridesReplaceColorAndScale() {
        OsdConfig config = OsdConfig.defaults();
        config.color = OsdPalette.GREEN.hex();
        config.scale = 1.2F;
        assertEquals(OsdPalette.GREEN.rgb(), config.elementRgb(OsdElement.FLIGHT_TIME));
        assertEquals(1.2F, config.elementScale(OsdElement.FLIGHT_TIME));
        OsdConfig.Slot slot = config.slot(OsdElement.FLIGHT_TIME);
        slot.color = "#ff00ff";
        slot.scale = 0.7F;
        OsdConfig.sanitize(config);
        slot = config.slot(OsdElement.FLIGHT_TIME);
        assertEquals("#FF00FF", slot.color);
        assertEquals(0xFF00FF, config.elementRgb(OsdElement.FLIGHT_TIME));
        assertEquals(0.7F, config.elementScale(OsdElement.FLIGHT_TIME));
        assertEquals(OsdPalette.GREEN.rgb(), config.elementRgb(OsdElement.BATTERY_VOLTAGE));
        assertTrue(config.hasElementOverrides());
        slot.color = "nope";
        slot.scale = 7.0F;
        config.slot(OsdElement.LAP).scale = -2.0F;
        config.slot(OsdElement.LAP_TIME).scale = Float.NaN;
        OsdConfig.sanitize(config);
        assertEquals("", config.slot(OsdElement.FLIGHT_TIME).color);
        assertEquals(OsdConfig.MAX_SCALE, config.slot(OsdElement.FLIGHT_TIME).scale);
        assertEquals(0.0F, config.slot(OsdElement.LAP).scale);
        assertFalse(config.slot(OsdElement.LAP_TIME).hasScale());
    }

    @Test
    void scaleStepsCoverHalfToDouble() {
        assertEquals(15, OsdConfig.SCALE_STEPS);
        assertEquals(0.5F, OsdConfig.scaleAt(0));
        assertEquals(1.0F, OsdConfig.scaleAt(5));
        assertEquals(2.0F, OsdConfig.scaleAt(OsdConfig.SCALE_STEPS));
        assertEquals(2.0F, OsdConfig.scaleAt(99));
        assertEquals(5, OsdConfig.scaleStep(1.0F));
        assertEquals(0, OsdConfig.scaleStep(0.1F));
        assertEquals(1.3F, OsdConfig.stepScale(1.26F, 1.0F));
        assertEquals(1.0F, OsdConfig.stepScale(Float.NaN, 1.0F));
        for (int step = 0; step <= OsdConfig.SCALE_STEPS; step++) {
            assertEquals(step, OsdConfig.scaleStep(OsdConfig.scaleAt(step)));
        }
    }

    @Test
    void paletteLooksUpByHex() {
        assertSame(OsdPalette.WHITE, OsdPalette.byHex("#fff"));
        assertSame(OsdPalette.RED, OsdPalette.byHex(OsdConfig.DEFAULT_WARNING_COLOR));
        assertNull(OsdPalette.byHex("#123456"));
        assertNull(OsdPalette.byHex("bad"));
        assertEquals(6, OsdPalette.PRIMARY.size());
        assertEquals(List.of(OsdPalette.WHITE, OsdPalette.GREEN, OsdPalette.YELLOW, OsdPalette.CYAN, OsdPalette.ORANGE, OsdPalette.MAGENTA),
                OsdPalette.PRIMARY);
        assertTrue(OsdPalette.WARNING.contains(OsdPalette.RED));
    }
}
