package de.lardtom.propwash.video.client;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoConfigLedLightingTest {
    @Test
    void ledLightingIsOnByDefaultAlsoForOlderConfigFiles() {
        assertTrue(new VideoConfig().ledLighting);
        VideoConfig old = new Gson().fromJson("{\"effectStrength\": 0.4, \"reduceFlicker\": false}", VideoConfig.class);
        old.validate();
        assertTrue(old.ledLighting, "Konfiguration aus 0.4.0 ohne das Feld behält die LED-Ausleuchtung");
        assertEquals(0.4F, old.effectStrength, 1.0E-6F);
    }

    @Test
    void switchedOffStaysOffThroughCopyAndSave() {
        VideoConfig config = new VideoConfig();
        config.ledLighting = false;
        assertFalse(config.copy().ledLighting);
        Gson gson = new Gson();
        VideoConfig loaded = gson.fromJson(gson.toJson(config), VideoConfig.class);
        loaded.validate();
        assertFalse(loaded.ledLighting);
    }
}
