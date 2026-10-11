package de.lardtom.propwash.osd;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdLayoutCodecTest {
    private static OsdConfig customized() {
        OsdConfig config = OsdConfig.defaults();
        config.applyPreset(OsdPreset.RACE);
        config.opacity = 80;
        config.lineOpacity = 35;
        config.scale = 1.3F;
        config.color = "#00FF88";
        config.warningColor = "#FFAA00";
        config.outline = OsdConfig.Outline.THICK;
        config.background = OsdConfig.Background.SUBTLE;
        config.coloredAlerts = false;
        config.pitchLadder = false;
        config.ladderStepDeg = 15.0F;
        config.move(OsdElement.SPEED, 3, 12);
        config.slot(OsdElement.ALTITUDE).enabled = true;
        config.slot(OsdElement.BATTERY_VOLTAGE).color = "#ff0000";
        config.slot(OsdElement.FLIGHT_TIME).scale = 1.5F;
        config.hideVanillaHud = false;
        config.analogBreakup = false;
        OsdConfig.sanitize(config);
        return config;
    }

    @Test
    void encodesTheDocumentedSchema() {
        JsonObject root = JsonParser.parseString(OsdLayoutCodec.encode(customized())).getAsJsonObject();
        assertEquals(1, root.get("v").getAsInt());
        assertEquals("race", root.get("preset").getAsString());
        JsonObject style = root.getAsJsonObject("style");
        assertEquals(80, style.get("opacity").getAsInt());
        assertEquals(35, style.get("line_opacity").getAsInt());
        assertEquals("#00ff88", style.get("color").getAsString());
        assertEquals("thick", style.get("outline").getAsString());
        assertEquals("subtle", style.get("background").getAsString());
        assertFalse(style.get("pitch_ladder").getAsBoolean());
        JsonObject speed = root.getAsJsonObject("elements").getAsJsonObject("speed");
        assertEquals(3, speed.get("col").getAsInt());
        assertEquals(12, speed.get("row").getAsInt());
        assertEquals("#ff0000", root.getAsJsonObject("elements").getAsJsonObject("battery_voltage").get("color").getAsString());
        assertFalse(root.getAsJsonObject("elements").getAsJsonObject("crosshair").has("col"));
        assertFalse(root.has("hide_vanilla_hud"));
        assertFalse(style.has("analog_breakup"));
        assertEquals(OsdElement.values().length, root.getAsJsonObject("elements").size());
    }

    @Test
    void roundTripKeepsEveryLayoutValue() {
        OsdConfig source = customized();
        String json = OsdLayoutCodec.encode(source);
        OsdConfig decoded = OsdLayoutCodec.decode(json, OsdConfig.defaults());
        assertEquals(json, OsdLayoutCodec.encode(decoded));
        assertEquals(OsdPreset.RACE, decoded.preset);
        assertEquals(3, decoded.slot(OsdElement.SPEED).col);
        assertEquals("#FF0000", decoded.slot(OsdElement.BATTERY_VOLTAGE).color);
        assertEquals(1.5F, decoded.slot(OsdElement.FLIGHT_TIME).scale);
        String defaults = OsdLayoutCodec.encode(OsdConfig.defaults());
        assertEquals(defaults, OsdLayoutCodec.encode(OsdLayoutCodec.decode(defaults, source)));
    }

    @Test
    void playerPreferencesComeFromTheBase() {
        OsdConfig base = OsdConfig.defaults();
        base.hideVanillaHud = false;
        base.analogBreakup = false;
        OsdConfig decoded = OsdLayoutCodec.decode(OsdLayoutCodec.encode(OsdConfig.defaults()), base);
        assertFalse(decoded.hideVanillaHud);
        assertFalse(decoded.analogBreakup);
        OsdConfig other = OsdLayoutCodec.decode(OsdLayoutCodec.encode(customized()), OsdConfig.defaults());
        assertTrue(other.hideVanillaHud);
        assertTrue(other.analogBreakup);
    }

    @Test
    void unknownEntriesAreSkippedAndMissingOnesFollowThePreset() {
        String json = "{\"v\":1,\"preset\":\"standard\",\"future_field\":3,\"style\":{\"opacity\":150,\"glow\":true},"
                + "\"elements\":{\"warp_drive\":{\"on\":true},\"speed\":{\"on\":true,\"col\":999,\"row\":-4,\"color\":\"lila\",\"scale\":9}}}";
        OsdConfig decoded = OsdLayoutCodec.decode(json, OsdConfig.defaults());
        assertEquals(OsdPreset.STANDARD, decoded.preset);
        assertEquals(100, decoded.opacity);
        assertEquals(OsdGrid.clampCol(999), decoded.slot(OsdElement.SPEED).col);
        assertEquals(OsdGrid.clampRow(-4), decoded.slot(OsdElement.SPEED).row);
        assertEquals("", decoded.slot(OsdElement.SPEED).color);
        assertEquals(OsdConfig.MAX_SCALE, decoded.slot(OsdElement.SPEED).scale);
        assertEquals(OsdPreset.STANDARD.col(OsdElement.ALTITUDE), decoded.slot(OsdElement.ALTITUDE).col);
        assertEquals(OsdPreset.STANDARD.enabled(OsdElement.ALTITUDE), decoded.slot(OsdElement.ALTITUDE).enabled);
        assertEquals(new OsdConfig().lineOpacity, decoded.lineOpacity);
    }

    @Test
    void unknownPresetFallsBackToTheDefault() {
        OsdConfig decoded = OsdLayoutCodec.decode("{\"v\":1,\"preset\":\"cinematic\"}", OsdConfig.defaults());
        assertEquals(OsdConfig.DEFAULT_PRESET, decoded.preset);
    }

    @Test
    void brokenInputIsRejected() {
        OsdConfig base = OsdConfig.defaults();
        assertThrows(IllegalArgumentException.class, () -> OsdLayoutCodec.decode("", base));
        assertThrows(IllegalArgumentException.class, () -> OsdLayoutCodec.decode("{kaputt", base));
        assertThrows(IllegalArgumentException.class, () -> OsdLayoutCodec.decode("[1,2]", base));
        assertThrows(IllegalArgumentException.class, () -> OsdLayoutCodec.decode("{\"preset\":\"race\"}", base));
        assertThrows(IllegalArgumentException.class, () -> OsdLayoutCodec.decode("{\"v\":0}", base));
        assertThrows(IllegalArgumentException.class, () -> OsdLayoutCodec.decode("{\"v\":1,\"x\":\"" + "a".repeat(9000) + "\"}", base));
    }

    @Test
    void encodedLayoutsFitTheApiLimit() {
        OsdConfig config = customized();
        for (OsdElement element : OsdElement.values()) {
            config.slot(element).enabled = true;
            config.slot(element).color = "#123456";
            config.slot(element).scale = 1.9F;
        }
        assertTrue(OsdLayoutCodec.encode(config).length() < OsdLayoutCodec.MAX_LENGTH);
    }
}
