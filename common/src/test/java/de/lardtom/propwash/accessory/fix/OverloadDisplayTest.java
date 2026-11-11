package de.lardtom.propwash.accessory.fix;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.accessory.client.AccessoryScreen;
import de.lardtom.propwash.drone.QuadcopterItem;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdWarnings;
import de.lardtom.propwash.testutil.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverloadDisplayTest {
    private static JsonObject lang(String locale) throws IOException {
        String text = Files.readString(TestPaths.lang("api-acc-fix").resolve(locale + ".json"), StandardCharsets.UTF_8);
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void osdWarnsWhenOverloaded() {
        OsdData data = new OsdData();
        data.active = true;
        data.radioConnected = true;
        data.linkStrength = 1.0;
        List<OsdWarnings.Warning> out = new ArrayList<>();
        OsdWarnings.collect(data, out);
        assertFalse(out.contains(OsdWarnings.Warning.OVERLOADED));
        data.overloaded = true;
        OsdWarnings.collect(data, out);
        assertEquals(List.of(OsdWarnings.Warning.OVERLOADED), out);
        assertEquals(OsdWarnings.Severity.WARNING, OsdWarnings.Warning.OVERLOADED.severity());
        assertTrue(OsdWarnings.Warning.OVERLOADED.blinks());
        OsdData copy = new OsdData();
        data.copyTo(copy);
        assertTrue(copy.overloaded);
        copy.reset();
        assertFalse(copy.overloaded);
        data.active = false;
        OsdWarnings.collect(data, out);
        assertTrue(out.isEmpty());
    }

    @Test
    void everyPlaceHasTextInBothLanguages() throws IOException {
        JsonObject en = lang("en_us");
        JsonObject de = lang("de_de");
        List<String> keys = List.of(OsdWarnings.Warning.OVERLOADED.translationKey(), AccessoryScreen.OVERLOADED_KEY, AccessoryScreen.OVERLOADED_HINT_KEY,
                QuadcopterItem.OVERLOADED_KEY);
        for (String key : keys) {
            assertTrue(en.has(key), "en_us: " + key);
            assertTrue(de.has(key), "de_de: " + key);
        }
        assertEquals(en.keySet(), de.keySet());
        assertEquals("ÜBERLADEN", de.get(OsdWarnings.Warning.OVERLOADED.translationKey()).getAsString());
        assertEquals("OVERLOADED", en.get(OsdWarnings.Warning.OVERLOADED.translationKey()).getAsString());
        assertTrue(de.get(AccessoryScreen.OVERLOADED_KEY).getAsString().contains("überladen"));
        assertEquals(2, de.get(AccessoryScreen.OVERLOADED_HINT_KEY).getAsString().split("%s", -1).length - 1);
        assertEquals(2, en.get(AccessoryScreen.OVERLOADED_HINT_KEY).getAsString().split("%s", -1).length - 1);
    }
}
