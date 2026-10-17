package de.lardtom.propwash.drone;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.video.VtxSettings;
import de.lardtom.propwash.video.vtx.VtxPowerSettings;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VtxPowerTuneTest {
    private static final AirframeSpec PARAMS = AirframeSpec.of(DroneBuild.FREESTYLE);

    @Test
    void powerKeysAreDroneSettings() {
        for (String key : VtxPowerSettings.KEYS) {
            assertTrue(TuneMapping.isKnownKey(key), key);
            assertTrue(TuneMapping.isDroneSetting(key), key);
            assertTrue(VtxSettings.isKey(key), key);
        }
    }

    @Test
    void editsSurviveTheServerPath() {
        Map<String, Float> edited = TuneMapping.effective(Map.of(), PARAMS);
        edited = VtxPowerSettings.withLowPower(VtxPowerSettings.withLevel(edited, 1200), VtxPowerSettings.LowPower.UNTIL_ARMED);
        Map<String, Float> stored = TuneMapping.applyEdit(Map.of(), edited, PARAMS);
        assertEquals(1200.0F, stored.get(VtxPowerSettings.POWER));
        assertEquals(VtxPowerSettings.LowPower.UNTIL_ARMED, VtxPowerSettings.lowPower(stored));
        assertFalse(stored.containsKey(VtxPowerSettings.AUTO));
        Map<String, Float> auto = TuneMapping.applyEdit(stored, VtxPowerSettings.withAuto(TuneMapping.effective(stored, PARAMS)), PARAMS);
        assertTrue(VtxPowerSettings.auto(auto));
        Map<String, Float> cleared = TuneMapping.applyEdit(auto, VtxPowerSettings.withLevel(TuneMapping.effective(auto, PARAMS), 0), PARAMS);
        assertFalse(cleared.containsKey(VtxPowerSettings.POWER));
        assertFalse(cleared.containsKey(VtxPowerSettings.AUTO));
    }

    @Test
    void defaultTuneHasNoPowerKeys() {
        Map<String, Float> normalized = TuneMapping.normalize(Map.of(), PARAMS);
        for (String key : VtxPowerSettings.KEYS) {
            assertFalse(normalized.containsKey(key), key);
        }
    }
}
