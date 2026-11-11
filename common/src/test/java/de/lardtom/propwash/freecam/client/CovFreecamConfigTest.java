package de.lardtom.propwash.freecam.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.freecam.FreecamPreset;
import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.ui.TuneKeys;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovFreecamConfigTest {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
    private static final String RC_RATE = TuneKeys.rateKey(Axis.ROLL, "rcRate");
    private static final String MAX_RATE = TuneKeys.rateKey(Axis.PITCH, "maxRateDps");

    private static float mid(FreecamPreset preset, float share) {
        CameraMount.Range range = CameraMount.range(preset.build());
        return Math.round(range.minDeg() + (range.maxDeg() - range.minDeg()) * share);
    }

    private static FreecamConfig roundTrip(FreecamConfig config) {
        String json = GSON.toJson(config, FreecamConfig.class);
        FreecamConfig read = JsonConfig.read(new StringReader(json), FreecamConfig.class, null);
        assertNotNull(read, json);
        FreecamConfig.validate(read);
        return read;
    }

    private static FreecamConfig fromFile(String json) {
        FreecamConfig read = JsonConfig.read(new StringReader(json), FreecamConfig.class, null);
        FreecamConfig result = read == null ? new FreecamConfig() : read;
        FreecamConfig.validate(result);
        return result;
    }

    @Test
    void defaultsAreFiveInchFieldOfView120AndOsdOn() {
        FreecamConfig config = new FreecamConfig();
        FreecamConfig.validate(config);
        assertEquals(FreecamPreset.FIVE, config.preset());
        assertEquals(120.0F, FreecamConfig.FOV_DEFAULT);
        assertEquals(120.0F, config.fov());
        assertTrue(config.osd);
        assertTrue(config.tunes.isEmpty());
        assertTrue(config.uptilt.isEmpty());
        for (FreecamPreset preset : FreecamPreset.values()) {
            assertTrue(config.tune(preset).isEmpty(), preset.key());
            assertEquals(CameraMount.defaultUptilt(preset.build().frame()), config.uptilt(preset), preset.key());
        }
    }

    @Test
    void ratesAreKeptPerSize() {
        FreecamConfig config = new FreecamConfig();
        config.setTune(FreecamPreset.WHOOP, Map.of(RC_RATE, 1.6F));
        assertEquals(1.6F, config.tune(FreecamPreset.WHOOP).get(RC_RATE), 1.0E-6F);
        for (FreecamPreset other : new FreecamPreset[]{FreecamPreset.THREE_FIVE, FreecamPreset.FIVE, FreecamPreset.SEVEN}) {
            assertTrue(config.tune(other).isEmpty(), "Rates des Whoop landen bei " + other.key());
        }
        config.setTune(FreecamPreset.SEVEN, Map.of(RC_RATE, 0.8F, MAX_RATE, 500.0F));
        assertEquals(1.6F, config.tune(FreecamPreset.WHOOP).get(RC_RATE), 1.0E-6F);
        assertEquals(0.8F, config.tune(FreecamPreset.SEVEN).get(RC_RATE), 1.0E-6F);
        assertNull(config.tune(FreecamPreset.WHOOP).get(MAX_RATE));
        config.setTune(FreecamPreset.WHOOP, Map.of());
        assertTrue(config.tune(FreecamPreset.WHOOP).isEmpty());
        assertFalse(config.tunes.containsKey(FreecamPreset.WHOOP.key()));
        assertEquals(0.8F, config.tune(FreecamPreset.SEVEN).get(RC_RATE), 1.0E-6F);
    }

    @Test
    void ratesNeverCarryTheCameraAngleOrUnknownKeys() {
        FreecamConfig config = new FreecamConfig();
        Map<String, Float> values = new TreeMap<>();
        values.put(RC_RATE, 1.2F);
        values.put(TuneMapping.CAMERA_UPTILT, 33.0F);
        values.put("rates.unknown.field", 4.0F);
        values.put(MAX_RATE, Float.NaN);
        config.setTune(FreecamPreset.FIVE, values);
        Map<String, Float> stored = config.tune(FreecamPreset.FIVE);
        assertEquals(Map.of(RC_RATE, 1.2F), stored);
        assertEquals(CameraMount.defaultUptilt(FreecamPreset.FIVE.build().frame()), config.uptilt(FreecamPreset.FIVE));
    }

    @Test
    void cameraAngleIsKeptPerSize() {
        FreecamConfig config = new FreecamConfig();
        float whoop = mid(FreecamPreset.WHOOP, 0.8F);
        float seven = mid(FreecamPreset.SEVEN, 0.2F);
        assertNotEquals(whoop, seven);
        config.setUptilt(FreecamPreset.WHOOP, whoop);
        assertEquals(whoop, config.uptilt(FreecamPreset.WHOOP), 1.0E-4F);
        assertEquals(CameraMount.defaultUptilt(FreecamPreset.SEVEN.build().frame()), config.uptilt(FreecamPreset.SEVEN), 1.0E-4F);
        config.setUptilt(FreecamPreset.SEVEN, seven);
        assertEquals(whoop, config.uptilt(FreecamPreset.WHOOP), 1.0E-4F);
        assertEquals(seven, config.uptilt(FreecamPreset.SEVEN), 1.0E-4F);
        assertEquals(CameraMount.defaultUptilt(FreecamPreset.FIVE.build().frame()), config.uptilt(FreecamPreset.FIVE), 1.0E-4F);
        config.setUptilt(FreecamPreset.WHOOP, Float.NaN);
        assertEquals(whoop, config.uptilt(FreecamPreset.WHOOP), 1.0E-4F);
    }

    @Test
    void fieldOfViewIsOneValueForAllSizes() {
        FreecamConfig config = new FreecamConfig();
        config.preset = FreecamPreset.WHOOP.key();
        assertEquals(120.0F, config.fov());
        config.fovDeg = 100.0F;
        for (FreecamPreset preset : FreecamPreset.values()) {
            config.preset = preset.key();
            assertEquals(100.0F, config.fov(), preset.key());
        }
        assertEquals(FreecamConfig.FOV_MAX, FreecamConfig.clampFov(300.0F));
        assertEquals(FreecamConfig.FOV_MIN, FreecamConfig.clampFov(10.0F));
        assertEquals(FreecamConfig.FOV_DEFAULT, FreecamConfig.clampFov(Float.NaN));
        assertEquals(FreecamConfig.FOV_DEFAULT, FreecamConfig.clampFov(Float.POSITIVE_INFINITY));
    }

    @Test
    void savingAndReadingKeepsEveryValue() {
        FreecamConfig config = new FreecamConfig();
        config.preset = FreecamPreset.SEVEN.key();
        config.setTune(FreecamPreset.WHOOP, Map.of(RC_RATE, 1.7F));
        config.setTune(FreecamPreset.SEVEN, Map.of(RC_RATE, 0.9F, MAX_RATE, 480.0F));
        config.setUptilt(FreecamPreset.WHOOP, mid(FreecamPreset.WHOOP, 0.7F));
        config.setUptilt(FreecamPreset.SEVEN, mid(FreecamPreset.SEVEN, 0.3F));
        config.fovDeg = 135.0F;
        config.osd = false;
        FreecamConfig.validate(config);
        FreecamConfig read = roundTrip(config);
        assertEquals(FreecamPreset.SEVEN, read.preset());
        assertEquals(config.tunes, read.tunes);
        assertEquals(config.uptilt, read.uptilt);
        assertEquals(135.0F, read.fovDeg);
        assertFalse(read.osd);
        for (FreecamPreset preset : FreecamPreset.values()) {
            assertEquals(config.tune(preset), read.tune(preset), preset.key());
            assertEquals(config.uptilt(preset), read.uptilt(preset), 1.0E-6F, preset.key());
        }
        FreecamConfig again = roundTrip(read);
        assertEquals(GSON.toJson(read), GSON.toJson(again));
    }

    @Test
    void aMissingOrEmptyFileGivesTheDefaults() {
        FreecamConfig defaults = new FreecamConfig();
        FreecamConfig.validate(defaults);
        for (String json : new String[]{"", "{}", "null"}) {
            FreecamConfig read = fromFile(json);
            assertEquals(GSON.toJson(defaults), GSON.toJson(read), "Datei „" + json + "“");
        }
        FreecamConfig partial = fromFile("{\"preset\":\"whoop\"}");
        assertEquals(FreecamPreset.WHOOP, partial.preset());
        assertEquals(FreecamConfig.FOV_DEFAULT, partial.fov());
        assertTrue(partial.osd);
        assertTrue(partial.tunes.isEmpty());
        assertTrue(partial.uptilt.isEmpty());
    }

    @Test
    void brokenValuesFromTheFileAreCleanedUp() {
        String json = "{\"preset\":\"9\",\"tunes\":{\"5\":{\"" + RC_RATE + "\":1.1,\"camera.uptiltDeg\":30.0},\"unknown\":{\"" + RC_RATE
                + "\":2.0},\"7\":null},\"uptilt\":{\"whoop\":500.0,\"x\":10.0},\"fovDeg\":999.0,\"osd\":false}";
        FreecamConfig read = fromFile(json);
        assertEquals(FreecamPreset.DEFAULT, read.preset());
        assertEquals(Map.of(FreecamPreset.FIVE.key(), Map.of(RC_RATE, 1.1F)), read.tunes);
        assertEquals(Map.of(FreecamPreset.WHOOP.key(), TuneMapping.CAMERA_UPTILT_MAX), read.uptilt);
        assertEquals(FreecamConfig.FOV_MAX, read.fovDeg);
        assertFalse(read.osd);
        FreecamConfig nulls = fromFile("{\"tunes\":null,\"uptilt\":null,\"preset\":null}");
        assertEquals(FreecamPreset.DEFAULT, nulls.preset());
        assertTrue(nulls.tunes.isEmpty());
        assertTrue(nulls.uptilt.isEmpty());
    }
}
