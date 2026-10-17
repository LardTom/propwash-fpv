package de.lardtom.propwash.osd;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdParkingTest {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    @Test
    void speedSwitchedOnInMinimalLandsBelowTheVtxPower() {
        OsdConfig config = OsdConfig.defaults();
        assertFalse(config.enabled(OsdElement.SPEED));
        OsdConfig.Slot speed = config.slot(OsdElement.SPEED);
        OsdConfig.Slot power = config.slot(OsdElement.VTX_POWER);
        assertTrue(power.enabled);
        assertFalse(speed.col == power.col && speed.row == power.row, "Geschwindigkeit auf dem Platz der VTX-Leistung");
        assertEquals(1, speed.col);
        assertEquals(8, speed.row);
        speed.enabled = true;
        assertTrue(config.customized());
    }

    @Test
    void parkedElementsDoNotChangeWhatAPresetShows() {
        for (OsdPreset preset : OsdPreset.values()) {
            for (OsdElement element : OsdElement.values()) {
                if (preset.parked(element)) {
                    assertFalse(preset.enabled(element), preset.key() + "/" + element.key());
                    assertFalse(preset.col(element) == element.baseCol() && preset.row(element) == element.baseRow(), preset.key() + "/" + element.key());
                }
            }
        }
    }

    @Test
    void disabledElementOnItsOldSpotMovesToTheNewOne() {
        JsonObject json = savedVersion3(OsdPreset.MINIMAL);
        OsdConfig config = load(json);
        assertEquals(OsdConfig.VERSION, config.version);
        assertEquals(1, config.slot(OsdElement.SPEED).col);
        assertEquals(8, config.slot(OsdElement.SPEED).row);
        assertEquals(6, config.slot(OsdElement.RSSI_DBM).col);
        assertEquals(1, config.slot(OsdElement.RSSI_DBM).row);
        assertFalse(config.customized());
    }

    @Test
    void enabledSpeedOnTheVtxPowerSpotMovesAway() {
        JsonObject json = savedVersion3(OsdPreset.MINIMAL);
        JsonObject speed = json.getAsJsonObject("elements").getAsJsonObject(OsdElement.SPEED.key());
        speed.addProperty("enabled", true);
        OsdConfig config = load(json);
        assertTrue(config.enabled(OsdElement.SPEED));
        assertTrue(config.enabled(OsdElement.VTX_POWER));
        assertEquals(1, config.slot(OsdElement.VTX_POWER).col);
        assertEquals(7, config.slot(OsdElement.VTX_POWER).row);
        assertEquals(1, config.slot(OsdElement.SPEED).col);
        assertEquals(8, config.slot(OsdElement.SPEED).row);
    }

    @Test
    void oldConfigWithoutVtxPowerGetsItWithoutCovering() {
        JsonObject json = savedVersion3(OsdPreset.MINIMAL);
        json.getAsJsonObject("elements").remove(OsdElement.VTX_POWER.key());
        json.getAsJsonObject("elements").getAsJsonObject(OsdElement.SPEED.key()).addProperty("enabled", true);
        OsdConfig config = load(json);
        OsdConfig.Slot power = config.slot(OsdElement.VTX_POWER);
        OsdConfig.Slot speed = config.slot(OsdElement.SPEED);
        assertTrue(power.enabled && speed.enabled);
        assertFalse(power.col == speed.col && power.row == speed.row);
    }

    @Test
    void elementsThePlayerMovedStayPut() {
        JsonObject json = savedVersion3(OsdPreset.STANDARD);
        JsonObject current = json.getAsJsonObject("elements").getAsJsonObject(OsdElement.CURRENT.key());
        current.addProperty("col", 9);
        current.addProperty("row", 9);
        JsonObject clock = json.getAsJsonObject("elements").getAsJsonObject(OsdElement.CLOCK.key());
        clock.addProperty("enabled", true);
        OsdConfig config = load(json);
        assertEquals(9, config.slot(OsdElement.CURRENT).col);
        assertEquals(9, config.slot(OsdElement.CURRENT).row);
        assertEquals(28, config.slot(OsdElement.CLOCK).col);
        assertEquals(15, config.slot(OsdElement.CLOCK).row);
        OsdConfig again = load(GSON.fromJson(GSON.toJson(config), JsonObject.class));
        assertEquals(28, again.slot(OsdElement.CLOCK).col);
        assertEquals(15, again.slot(OsdElement.CLOCK).row);
    }

    private static JsonObject savedVersion3(OsdPreset preset) {
        OsdConfig config = OsdConfig.defaults();
        config.applyPreset(preset);
        for (OsdElement element : OsdElement.values()) {
            if (preset.parked(element)) {
                OsdConfig.Slot slot = config.slot(element);
                slot.col = element.baseCol();
                slot.row = element.baseRow();
            }
        }
        JsonObject json = GSON.fromJson(GSON.toJson(config), JsonObject.class);
        json.addProperty("version", 3);
        return json;
    }

    private static OsdConfig load(JsonObject json) {
        OsdConfig config = GSON.fromJson(json, OsdConfig.class);
        OsdConfig.sanitize(config);
        return config;
    }
}
