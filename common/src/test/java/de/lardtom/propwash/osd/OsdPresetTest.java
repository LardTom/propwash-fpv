package de.lardtom.propwash.osd;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdPresetTest {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

    @Test
    void defaultsAreTheMinimalPresetWithASubtleStyle() {
        OsdConfig config = OsdConfig.defaults();
        assertSame(OsdPreset.MINIMAL, config.preset);
        assertTrue(OsdPreset.MINIMAL.matches(config));
        assertFalse(config.customized());
        assertEquals(OsdConfig.VERSION, config.version);
        assertEquals(60, config.opacity);
        assertTrue(config.lineOpacity < 100);
        assertSame(OsdConfig.Outline.THIN, config.outline);
        assertSame(OsdConfig.Background.OFF, config.background);
        assertEquals(1.0F, config.scale);
        assertFalse(config.hasElementOverrides());
    }

    @Test
    void minimalShowsOnlyTheEssentialsAndKeepsTheCentreFree() {
        Set<OsdElement> expected = EnumSet.of(OsdElement.CROSSHAIR, OsdElement.BATTERY_VOLTAGE, OsdElement.FLIGHT_TIME,
                OsdElement.LINK_QUALITY, OsdElement.VIDEO_SIGNAL, OsdElement.FLIGHT_MODE, OsdElement.WARNINGS,
                OsdElement.LAP, OsdElement.LAP_TIME, OsdElement.RECORDING, OsdElement.LEDS_OFF, OsdElement.VTX_CHANNEL,
                OsdElement.RELAY, OsdElement.TRICKS, OsdElement.FLASHBACK_BUDGET, OsdElement.VTX_POWER);
        Set<OsdElement> enabled = EnumSet.noneOf(OsdElement.class);
        for (OsdElement element : OsdElement.values()) {
            if (OsdPreset.MINIMAL.enabled(element)) {
                enabled.add(element);
            }
        }
        assertEquals(expected, enabled);
        for (OsdElement element : enabled) {
            if (!element.movable()) {
                continue;
            }
            int col = OsdPreset.MINIMAL.col(element);
            int row = OsdPreset.MINIMAL.row(element);
            boolean inCentre = col >= 8 && col <= 21 && row >= 4 && row <= 11;
            assertFalse(inCentre, element.key() + " liegt in der Bildmitte (" + col + "/" + row + ")");
        }
    }

    @Test
    void presetsGrowFromMinimalToFull() {
        assertTrue(OsdPreset.MINIMAL.enabledCount() < OsdPreset.STANDARD.enabledCount());
        assertTrue(OsdPreset.STANDARD.enabledCount() < OsdPreset.FULL.enabledCount());
        assertEquals(OsdElement.values().length, OsdPreset.FULL.enabledCount());
        assertTrue(OsdPreset.RACE.enabled(OsdElement.LAP_TIME) && OsdPreset.RACE.enabled(OsdElement.BEST_LAP)
                && OsdPreset.RACE.enabled(OsdElement.SPLIT_DELTA) && OsdPreset.RACE.enabled(OsdElement.GATE));
        assertFalse(OsdPreset.RACE.enabled(OsdElement.ARTIFICIAL_HORIZON));
        assertSame(OsdPreset.STANDARD, OsdPreset.MINIMAL.next());
        assertSame(OsdPreset.MINIMAL, OsdPreset.RACE.next());
    }

    @Test
    void presetsPlaceEveryElementOnItsOwnCell() {
        for (OsdPreset preset : OsdPreset.values()) {
            Set<Integer> cells = new HashSet<>();
            for (OsdElement element : OsdElement.values()) {
                int col = preset.col(element);
                int row = preset.row(element);
                assertTrue(col >= 0 && col < OsdGrid.COLS && row >= 0 && row < OsdGrid.ROWS, preset.key() + "/" + element.key());
                if (!element.movable() || element == OsdElement.POST_FLIGHT_STATS) {
                    continue;
                }
                assertTrue(cells.add(row * OsdGrid.COLS + col), preset.key() + ": " + element.key() + " teilt sich die Zelle " + col + "/" + row
                        + (preset.enabled(element) ? "" : " (Standardplatz beim Einschalten)"));
            }
        }
    }

    @Test
    void applyingAPresetSetsLayoutAndKeepsStyle() {
        OsdConfig config = OsdConfig.defaults();
        config.opacity = 40;
        config.color = OsdPalette.CYAN.hex();
        config.slot(OsdElement.BATTERY_VOLTAGE).color = OsdPalette.ORANGE.hex();
        config.applyPreset(OsdPreset.FULL);
        assertSame(OsdPreset.FULL, config.preset);
        assertTrue(OsdPreset.FULL.matches(config));
        for (OsdElement element : OsdElement.values()) {
            assertTrue(config.enabled(element), element.key());
        }
        assertEquals(40, config.opacity);
        assertEquals(OsdPalette.CYAN.hex(), config.color);
        assertEquals(OsdPalette.ORANGE.hex(), config.slot(OsdElement.BATTERY_VOLTAGE).color);
        config.applyPreset(OsdPreset.RACE);
        assertTrue(OsdPreset.RACE.matches(config));
        assertFalse(OsdPreset.FULL.matches(config));
        assertEquals(OsdPreset.RACE.col(OsdElement.LAP), config.slot(OsdElement.LAP).col);
        assertEquals(OsdPreset.RACE.row(OsdElement.LAP), config.slot(OsdElement.LAP).row);
        assertFalse(config.enabled(OsdElement.COMPASS_BAR));
    }

    @Test
    void customisingIsDetectedAndResetRestoresTheActivePreset() {
        OsdConfig config = OsdConfig.defaults();
        config.applyPreset(OsdPreset.STANDARD);
        assertFalse(config.customized());
        config.move(OsdElement.SPEED, 9, 9);
        assertTrue(config.customized());
        config.move(OsdElement.SPEED, OsdPreset.STANDARD.col(OsdElement.SPEED), OsdPreset.STANDARD.row(OsdElement.SPEED));
        assertFalse(config.customized());
        config.slot(OsdElement.COMPASS_BAR).enabled = true;
        assertTrue(config.customized());
        config.slot(OsdElement.COMPASS_BAR).enabled = false;
        config.move(OsdElement.COMPASS_BAR, 3, 3);
        assertFalse(config.customized(), "Position eines ausgeblendeten Elements zählt nicht als Anpassung");
        config.slot(OsdElement.SPEED).scale = 1.5F;
        assertFalse(config.customized(), "Elementgröße ist Darstellung, keine Layout-Anpassung");
        config.move(OsdElement.ALTITUDE, 20, 3);
        config.slot(OsdElement.CLOCK).enabled = true;
        config.resetLayout();
        assertSame(OsdPreset.STANDARD, config.preset);
        assertTrue(OsdPreset.STANDARD.matches(config));
        assertFalse(config.hasElementOverrides());
    }

    @Test
    void untouchedLegacyConfigMovesToMinimalAndKeepsStyleChoices() {
        JsonObject json = legacyJson(false);
        json.addProperty("color", "#FFF35C");
        json.addProperty("scale", 1.25F);
        OsdConfig config = GSON.fromJson(json, OsdConfig.class);
        OsdConfig.sanitize(config);
        assertEquals(OsdConfig.VERSION, config.version);
        assertSame(OsdPreset.MINIMAL, config.preset);
        assertTrue(OsdPreset.MINIMAL.matches(config));
        assertEquals("#FFF35C", config.color);
        assertSame(OsdPalette.YELLOW, OsdPalette.byHex(config.color));
        assertEquals(1.3F, config.scale);
        assertEquals(OsdConfig.DEFAULT_OPACITY, config.opacity);
        assertEquals(OsdConfig.DEFAULT_LINE_OPACITY, config.lineOpacity);
        assertEquals(OsdConfig.DEFAULT_WARNING_COLOR, config.warningColor);
        assertSame(OsdConfig.Outline.THIN, config.outline);
        assertSame(OsdConfig.Background.OFF, config.background);
        assertFalse(config.hasElementOverrides());
    }

    @Test
    void customisedLegacyConfigKeepsItsLayout() {
        JsonObject json = legacyJson(true);
        OsdConfig config = GSON.fromJson(json, OsdConfig.class);
        OsdConfig.sanitize(config);
        assertEquals(OsdConfig.VERSION, config.version);
        assertSame(OsdConfig.MIGRATED_PRESET, config.preset);
        assertEquals(5, config.slot(OsdElement.SPEED).col);
        assertEquals(5, config.slot(OsdElement.SPEED).row);
        for (OsdElement element : OsdElement.values()) {
            assertEquals(element.classicEnabled(), config.enabled(element), element.key());
        }
        assertTrue(config.customized());
        assertSame(OsdConfig.Outline.THIN, config.outline);
        OsdConfig again = GSON.fromJson(GSON.toJson(config), OsdConfig.class);
        OsdConfig.sanitize(again);
        assertSame(config.preset, again.preset);
        assertEquals(5, again.slot(OsdElement.SPEED).col);
    }

    @Test
    void legacyConfigWithMissingElementsCountsAsUntouched() {
        JsonObject json = legacyJson(false);
        JsonObject elements = json.getAsJsonObject("elements");
        elements.remove(OsdElement.CLOCK.key());
        elements.remove(OsdElement.G_FORCE.key());
        OsdConfig config = GSON.fromJson(json, OsdConfig.class);
        OsdConfig.sanitize(config);
        assertSame(OsdPreset.MINIMAL, config.preset);
        assertTrue(OsdPreset.MINIMAL.matches(config));
    }

    @Test
    void migrationRunsOnlyOnce() {
        OsdConfig config = GSON.fromJson(legacyJson(false), OsdConfig.class);
        OsdConfig.sanitize(config);
        config.applyPreset(OsdPreset.FULL);
        config.move(OsdElement.SPEED, 2, 2);
        OsdConfig reloaded = GSON.fromJson(GSON.toJson(config), OsdConfig.class);
        OsdConfig.sanitize(reloaded);
        assertSame(OsdPreset.FULL, reloaded.preset);
        assertEquals(2, reloaded.slot(OsdElement.SPEED).col);
        assertTrue(reloaded.enabled(OsdElement.COMPASS_BAR));
    }

    @Test
    void newFieldsSurviveARoundTripAndBrokenValuesFallBack() {
        OsdConfig config = OsdConfig.defaults();
        config.applyPreset(OsdPreset.RACE);
        config.opacity = 35;
        config.lineOpacity = 15;
        config.scale = 1.7F;
        config.color = OsdPalette.MAGENTA.hex();
        config.warningColor = "#00FFAA";
        config.outline = OsdConfig.Outline.SHADOW;
        config.background = OsdConfig.Background.SOLID;
        config.slot(OsdElement.LAP_TIME).scale = 2.0F;
        config.slot(OsdElement.LAP_TIME).color = OsdPalette.YELLOW.hex();
        String text = GSON.toJson(config);
        OsdConfig loaded = GSON.fromJson(text, OsdConfig.class);
        OsdConfig.sanitize(loaded);
        assertSame(OsdPreset.RACE, loaded.preset);
        assertEquals(35, loaded.opacity);
        assertEquals(15, loaded.lineOpacity);
        assertEquals(1.7F, loaded.scale);
        assertEquals(OsdPalette.MAGENTA.hex(), loaded.color);
        assertEquals("#00FFAA", loaded.warningColor);
        assertSame(OsdConfig.Outline.SHADOW, loaded.outline);
        assertSame(OsdConfig.Background.SOLID, loaded.background);
        assertEquals(2.0F, loaded.slot(OsdElement.LAP_TIME).scale);
        assertEquals(OsdPalette.YELLOW.hex(), loaded.slot(OsdElement.LAP_TIME).color);
        assertTrue(OsdPreset.RACE.matches(loaded));

        JsonObject broken = GSON.fromJson(text, JsonObject.class);
        broken.addProperty("preset", "SPARKLE");
        broken.addProperty("outline", "GLOW");
        broken.addProperty("background", "PLAID");
        broken.addProperty("opacity", 180);
        broken.addProperty("lineOpacity", -20);
        broken.addProperty("warningColor", "rot");
        OsdConfig fixed = GSON.fromJson(broken, OsdConfig.class);
        OsdConfig.sanitize(fixed);
        assertSame(OsdConfig.DEFAULT_PRESET, fixed.preset);
        assertSame(OsdConfig.Outline.THIN, fixed.outline);
        assertSame(OsdConfig.Background.OFF, fixed.background);
        assertEquals(100, fixed.opacity);
        assertEquals(0, fixed.lineOpacity);
        assertEquals(OsdConfig.DEFAULT_WARNING_COLOR, fixed.warningColor);
        assertEquals(OsdPreset.RACE.col(OsdElement.LAP), fixed.slot(OsdElement.LAP).col);
    }

    @Test
    void recordingLeavesTheTopRowInSavedPresetLayouts() {
        for (OsdPreset preset : new OsdPreset[]{OsdPreset.MINIMAL, OsdPreset.STANDARD, OsdPreset.RACE}) {
            assertTrue(preset.row(OsdElement.RECORDING) >= 1, preset.key() + ": REC in der obersten Zeile");
            OsdConfig config = OsdConfig.defaults();
            config.applyPreset(preset);
            JsonObject json = GSON.fromJson(GSON.toJson(config), JsonObject.class);
            json.addProperty("version", 2);
            JsonObject elements = json.getAsJsonObject("elements");
            elements.getAsJsonObject(OsdElement.RECORDING.key()).addProperty("row", 0);
            if (preset == OsdPreset.MINIMAL) {
                elements.getAsJsonObject(OsdElement.FLIGHT_MODE.key()).addProperty("row", 1);
                elements.getAsJsonObject(OsdElement.LAP.key()).addProperty("row", 2);
                elements.getAsJsonObject(OsdElement.LAP_TIME.key()).addProperty("row", 3);
            }
            if (preset == OsdPreset.RACE) {
                elements.getAsJsonObject(OsdElement.LAP.key()).addProperty("row", 1);
                elements.getAsJsonObject(OsdElement.LAP_TIME.key()).addProperty("row", 2);
                elements.getAsJsonObject(OsdElement.LAST_LAP.key()).addProperty("row", 3);
                elements.getAsJsonObject(OsdElement.BEST_LAP.key()).addProperty("row", 4);
            }
            OsdConfig old = GSON.fromJson(json, OsdConfig.class);
            OsdConfig.sanitize(old);
            assertEquals(OsdConfig.VERSION, old.version);
            assertTrue(preset.matches(old), preset.key() + ": alte Vorlage nicht auf die neue Lage gebracht");
            assertFalse(old.customized(), preset.key());
        }
        OsdConfig moved = OsdConfig.defaults();
        moved.move(OsdElement.RECORDING, 10, 0);
        JsonObject json = GSON.fromJson(GSON.toJson(moved), JsonObject.class);
        json.addProperty("version", 2);
        OsdConfig kept = GSON.fromJson(json, OsdConfig.class);
        OsdConfig.sanitize(kept);
        assertEquals(10, kept.slot(OsdElement.RECORDING).col);
        assertEquals(0, kept.slot(OsdElement.RECORDING).row);
    }

    @Test
    void ledIndicatorSitsUnderTheLinkValuesAndCanBeHidden() {
        for (OsdPreset preset : new OsdPreset[]{OsdPreset.MINIMAL, OsdPreset.STANDARD, OsdPreset.RACE, OsdPreset.FULL}) {
            assertTrue(preset.enabled(OsdElement.LEDS_OFF), preset.key());
            assertEquals(1, preset.col(OsdElement.LEDS_OFF), preset.key());
        }
        assertEquals(3, OsdPreset.MINIMAL.row(OsdElement.LEDS_OFF));
        assertSame(OsdElement.Group.STATUS, OsdElement.LEDS_OFF.group());
        assertTrue(OsdElement.LEDS_OFF.movable());
        OsdConfig config = OsdConfig.defaults();
        config.slot(OsdElement.LEDS_OFF).enabled = false;
        OsdConfig loaded = GSON.fromJson(GSON.toJson(config), OsdConfig.class);
        OsdConfig.sanitize(loaded);
        assertFalse(loaded.enabled(OsdElement.LEDS_OFF));
        assertTrue(loaded.customized());

        JsonObject old = GSON.fromJson(GSON.toJson(OsdConfig.defaults()), JsonObject.class);
        old.getAsJsonObject("elements").remove(OsdElement.LEDS_OFF.key());
        OsdConfig upgraded = GSON.fromJson(old, OsdConfig.class);
        OsdConfig.sanitize(upgraded);
        assertTrue(upgraded.enabled(OsdElement.LEDS_OFF));
        assertFalse(upgraded.customized());
    }

    private static JsonObject legacyJson(boolean customised) {
        JsonObject json = new JsonObject();
        json.addProperty("version", 1);
        json.addProperty("color", "#FFFFFF");
        json.addProperty("scale", 1.0F);
        json.addProperty("imperial", false);
        json.addProperty("coloredAlerts", true);
        json.addProperty("hideVanillaHud", true);
        json.addProperty("pitchLadder", true);
        json.addProperty("analogBreakup", true);
        json.addProperty("ladderStepDeg", 10.0F);
        JsonObject elements = new JsonObject();
        for (OsdElement element : OsdElement.values()) {
            JsonObject slot = new JsonObject();
            slot.addProperty("enabled", element.classicEnabled());
            boolean moved = customised && element == OsdElement.SPEED;
            slot.addProperty("col", moved ? 5 : element.baseCol());
            slot.addProperty("row", moved ? 5 : element.baseRow());
            elements.add(element.key(), slot);
        }
        json.add("elements", elements);
        return json;
    }
}
