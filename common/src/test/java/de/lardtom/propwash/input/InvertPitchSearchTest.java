package de.lardtom.propwash.input;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.ui.SettingsSearch;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvertPitchSearchTest {
    private static final String LABEL = "propwash.input.invert_pitch";

    private static JsonObject lang(String name) throws IOException {
        for (Path dir : List.of(Path.of("common/src/lang/input"), Path.of("src/lang/input"))) {
            Path file = dir.resolve(name + ".json");
            if (Files.isRegularFile(file)) {
                return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
        throw new IOException("Sprachdatei " + name + " nicht gefunden");
    }

    private static boolean finds(JsonObject lang, String query) {
        SettingsSearch.Entry entry = new SettingsSearch.Entry("receiver#invert", SettingsSearch.Kind.SETTING, "receiver", "Receiver",
                lang.get(LABEL).getAsString(), List.of(), List.of(lang.get(LABEL + ".tooltip").getAsString()),
                List.of(lang.get(LABEL + ".search").getAsString()));
        return !SettingsSearch.search(query, List.of(entry), SettingsSearch.Synonyms.NONE, 8).isEmpty();
    }

    @Test
    void englishSearchFindsTheSwitch() throws IOException {
        JsonObject en = lang("en_us");
        assertEquals("Invert pitch", en.get(LABEL).getAsString());
        for (String word : List.of("invert", "pitch", "reverse", "inverted", "mouse y")) {
            assertTrue(finds(en, word), word);
        }
        assertFalse(finds(en, "throttle"));
    }

    @Test
    void germanSearchFindsTheSwitch() throws IOException {
        JsonObject de = lang("de_de");
        for (String word : List.of("umkehren", "pitch", "invert", "nick", "invertieren", "Maus-Y")) {
            assertTrue(finds(de, word), word);
        }
        assertFalse(finds(de, "gas"));
    }

    @Test
    void theOldMouseOnlySwitchIsGoneFromBothLanguages() throws IOException {
        for (String name : List.of("en_us", "de_de")) {
            JsonObject lang = lang(name);
            assertFalse(lang.has("propwash.input.mouse.invert_y"), name);
            assertFalse(lang.has("propwash.input.mouse.invert_y.tooltip"), name);
            assertTrue(lang.has("propwash.input.mouse.key.mouse_y_inverted"), name);
            assertTrue(lang.get(LABEL + ".tooltip").getAsString().length() > 60, name);
        }
    }
}
