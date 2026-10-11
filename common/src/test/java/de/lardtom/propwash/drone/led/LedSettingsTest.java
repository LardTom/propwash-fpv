package de.lardtom.propwash.drone.led;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.sim.AirframeSpec;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedSettingsTest {
    @Test
    void missingKeysGiveTheDefaults() {
        assertEquals(LedSettings.DEFAULT, LedSettings.of(Map.of()));
        assertEquals(LedSettings.DEFAULT, LedSettings.of(Map.of("pid.roll.p", 50.0F)));
        assertEquals(LedMode.STATIC, LedSettings.DEFAULT.mode());
        assertEquals(LedColor.WHITE, LedSettings.DEFAULT.color());
        assertEquals(100, LedSettings.DEFAULT.brightnessPercent());
        assertTrue(LedSettings.DEFAULT.headlight());
    }

    @Test
    void valuesRoundTrip() {
        LedSettings settings = new LedSettings(LedMode.RAINBOW, LedColor.MAGENTA, 35, false);
        assertEquals(settings, LedSettings.of(settings.values()));
        assertEquals(LedSettings.KEYS.size(), settings.values().size());
    }

    @Test
    void sanitizeClampsAndSnaps() {
        assertEquals(LedMode.values().length - 1, LedSettings.sanitize(LedSettings.MODE, 99.0F));
        assertEquals(0.0F, LedSettings.sanitize(LedSettings.MODE, -3.0F));
        assertEquals(0.0F, LedSettings.sanitize(LedSettings.COLOR, Float.NaN));
        assertEquals(LedSettings.MIN_BRIGHTNESS, LedSettings.sanitize(LedSettings.BRIGHTNESS, 0.0F));
        assertEquals(LedSettings.MAX_BRIGHTNESS, LedSettings.sanitize(LedSettings.BRIGHTNESS, 400.0F));
        assertEquals(45.0F, LedSettings.sanitize(LedSettings.BRIGHTNESS, 46.0F));
        assertEquals(0.0F, LedSettings.sanitize(LedSettings.HEADLIGHT, 0.2F));
        assertEquals(1.0F, LedSettings.sanitize(LedSettings.HEADLIGHT, 0.7F));
        assertEquals(40, new LedSettings(LedMode.BLINK, LedColor.RED, 41, true).brightnessPercent());
    }

    @Test
    void stripComesFromTheAccessories() {
        assertTrue(LedSettings.strip(DroneBuild.FREESTYLE).isEmpty());
        DroneBuild withStrip = DroneBuild.FREESTYLE.withAccessory(AccessoryType.LED_STRIP);
        assertEquals(AccessoryType.LED_STRIP, LedSettings.strip(withStrip).orElseThrow());
        DroneBuild withCamera = DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_LIGHT);
        assertTrue(LedSettings.strip(withCamera).isEmpty());
    }

    @Test
    void tuneKeepsOnlyChangedLedSettings() {
        AirframeSpec params = AirframeSpec.of(DroneBuild.FREESTYLE);
        for (String key : LedSettings.KEYS) {
            assertTrue(TuneMapping.isKnownKey(key), key);
            assertTrue(TuneMapping.isDroneSetting(key), key);
        }
        assertTrue(TuneMapping.normalize(LedSettings.DEFAULT.values(), params).isEmpty());
        Map<String, Float> edited = new HashMap<>(TuneMapping.effective(Map.of(), params));
        edited.putAll(LedSettings.DEFAULT.withColor(LedColor.RED).withMode(LedMode.BLINK).values());
        Map<String, Float> stored = TuneMapping.applyEdit(Map.of(), edited, params);
        assertEquals(2, stored.size(), stored.toString());
        assertEquals(LedColor.RED.ordinal(), stored.get(LedSettings.COLOR));
        assertEquals(LedMode.BLINK.ordinal(), stored.get(LedSettings.MODE));
        LedSettings back = LedSettings.of(TuneMapping.effective(stored, params));
        assertEquals(LedColor.RED, back.color());
        assertEquals(LedMode.BLINK, back.mode());
        Map<String, Float> cleared = TuneMapping.applyEdit(stored, TuneMapping.effective(Map.of(), params), params);
        assertFalse(cleared.containsKey(LedSettings.COLOR));
    }
}
