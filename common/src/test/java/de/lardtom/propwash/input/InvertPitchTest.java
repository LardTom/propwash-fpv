package de.lardtom.propwash.input;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import de.lardtom.propwash.config.JsonConfig;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvertPitchTest {
    private static InputConfig load(String json) {
        InputConfig config = JsonConfig.read(new StringReader(json), InputConfig.class, InputConfig::migrate);
        assertNotNull(config);
        config.sanitize();
        return config;
    }

    @Test
    void theSwitchIsOffByDefaultAndOnlyFlipsTheSign() {
        assertFalse(new InputConfig().invertPitch);
        assertEquals(0.6F, PitchDirection.apply(0.6F, false));
        assertEquals(-0.6F, PitchDirection.apply(0.6F, true));
        assertEquals(0.6F, PitchDirection.apply(-0.6F, true));
        assertEquals(Float.floatToIntBits(0.0F), Float.floatToIntBits(PitchDirection.apply(0.0F, true)));
        assertEquals(Float.floatToIntBits(0.0F), Float.floatToIntBits(PitchDirection.apply(0.0F, false)));
    }

    @Test
    void oldMouseSettingBecomesTheSharedSwitch() {
        InputConfig migrated = load("{\"selectedGuid\":\"mouse\",\"mouse\":{\"invertY\":true,\"dpi\":1600}}");
        assertTrue(migrated.invertPitch);
        assertEquals(1600, migrated.mouse.dpi);
        assertTrue(migrated.mouseSelected());
        assertFalse(load("{\"mouse\":{\"invertY\":false}}").invertPitch);
        assertFalse(load("{\"mouse\":{\"dpi\":800}}").invertPitch);
        assertFalse(load("{}").invertPitch);
    }

    @Test
    void theNewSwitchWinsOverTheOldOneAndBrokenValuesAreIgnored() {
        assertFalse(load("{\"invertPitch\":false,\"mouse\":{\"invertY\":true}}").invertPitch);
        assertTrue(load("{\"invertPitch\":true,\"mouse\":{\"invertY\":false}}").invertPitch);
        assertFalse(load("{\"mouse\":{\"invertY\":\"yes\"}}").invertPitch);
        assertFalse(load("{\"mouse\":{\"invertY\":null}}").invertPitch);
        assertThrows(JsonParseException.class, () -> load("{\"mouse\":7}"));
        assertNotNull(load("{\"mouse\":null}").mouse);
    }

    @Test
    void migrationRemovesTheOldKeyFromTheFile() {
        JsonObject json = JsonParser.parseString("{\"mouse\":{\"invertY\":true,\"dpi\":400}}").getAsJsonObject();
        InputConfig.migrate(json);
        assertFalse(json.getAsJsonObject("mouse").has("invertY"));
        assertTrue(json.get("invertPitch").getAsBoolean());
        assertEquals(400, json.getAsJsonObject("mouse").get("dpi").getAsInt());
        InputConfig config = new InputConfig();
        config.invertPitch = true;
        JsonObject written = new Gson().toJsonTree(config).getAsJsonObject();
        assertTrue(written.get("invertPitch").getAsBoolean());
        assertFalse(written.getAsJsonObject("mouse").has("invertY"));
    }

    @Test
    void readingWithoutMigrationStaysPlain() {
        InputConfig plain = JsonConfig.read(new StringReader("{\"mouse\":{\"invertY\":true}}"), InputConfig.class, null);
        assertNotNull(plain);
        assertFalse(plain.invertPitch);
        assertNull(JsonConfig.read(new StringReader(""), InputConfig.class, InputConfig::migrate));
    }

    @Test
    void copiesAndTheReceiverTabCarryTheSwitch() {
        InputConfig config = new InputConfig();
        config.invertPitch = true;
        assertTrue(config.copy().invertPitch);
        InputConfig saved = new InputConfig();
        saved.mergeReceiverFrom(config);
        assertTrue(saved.invertPitch);
        InputConfig switches = new InputConfig();
        switches.mergeSwitchesFrom(config);
        assertFalse(switches.invertPitch);
    }

    @Test
    void theSwitchBelongsToMouseAndKeyboardNotToARadio() {
        InputConfig config = new InputConfig();
        assertTrue(config.pitchSwitchApplies(false));
        assertFalse(config.pitchSwitchApplies(true));
        config.keyboardFallback = false;
        assertFalse(config.pitchSwitchApplies(false));
        config.selectedGuid = InputConfig.KEYBOARD_GUID;
        assertTrue(config.pitchSwitchApplies(false));
        config.selectedGuid = InputConfig.MOUSE_GUID;
        assertTrue(config.pitchSwitchApplies(false));
        assertFalse(config.pitchSwitchApplies(true));
    }

    @Test
    void mouseDemoAndSticksFollowTheSwitch() {
        MouseSettings rate = new MouseSettings();
        rate.mode = MouseMode.RATE;
        float t = 0.0F;
        for (MouseDemo.Phase phase : MouseDemo.all()) {
            if (phase.cue() == MouseDemo.Cue.FORWARD) {
                t = (phase.start() + phase.end()) * 0.5F;
            }
        }
        float normal = MouseDemo.frame(rate, false, t, false).sticks().pitch();
        float inverted = MouseDemo.frame(rate, false, t, true).sticks().pitch();
        assertTrue(normal > 0.25F);
        assertEquals(-normal, inverted);
        assertEquals(MouseDemo.frame(rate, false, t, false).sticks().roll(), MouseDemo.frame(rate, false, t, true).sticks().roll());
    }
}
