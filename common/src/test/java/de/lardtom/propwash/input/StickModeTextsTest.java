package de.lardtom.propwash.input;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.ui.SettingsSearch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StickModeTextsTest {
    private static final String LABEL = "propwash.input.stick_mode";

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
        SettingsSearch.Entry entry = new SettingsSearch.Entry("receiver#stick_mode", SettingsSearch.Kind.SETTING, "receiver", "Receiver",
                lang.get(LABEL).getAsString(), List.of(), List.of(lang.get(LABEL + ".tooltip").getAsString()),
                List.of(lang.get(LABEL + ".search").getAsString()));
        return !SettingsSearch.search(query, List.of(entry), SettingsSearch.Synonyms.NONE, 8).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(StickMode.class)
    void everyPromptNamesThePhysicalStick(StickMode mode) throws IOException {
        JsonObject en = lang("en_us");
        JsonObject de = lang("de_de");
        for (StickChannel channel : StickChannel.values()) {
            String key = mode.sideKey(channel.promptKey(), channel);
            boolean left = mode.left(channel);
            String english = en.get(key).getAsString();
            String german = de.get(key).getAsString();
            assertTrue(english.contains(left ? "LEFT stick" : "RIGHT stick"), key + ": " + english);
            assertFalse(english.contains(left ? "RIGHT stick" : "LEFT stick"), key);
            assertTrue(english.contains("(" + channel.name() + ")"), key + ": " + english);
            assertTrue(german.contains(left ? "LINKE" : "RECHTE"), key + ": " + german);
            assertFalse(german.contains(left ? "RECHTE" : "LINKE"), key);
        }
        assertFalse(en.has(StickChannel.PITCH.promptKey()), "alter Text ohne Seite ist noch da");
    }

    @ParameterizedTest
    @EnumSource(StickMode.class)
    void layoutLinesMatchTheTable(StickMode mode) throws IOException {
        String english = lang("en_us").get(mode.layoutKey()).getAsString();
        String[] halves = english.split(", ");
        assertEquals(2, halves.length, english);
        assertTrue(halves[0].startsWith("left ") && halves[1].startsWith("right "), english);
        for (StickChannel channel : StickChannel.values()) {
            String word = channel.name().toLowerCase(Locale.ROOT);
            assertTrue(halves[mode.left(channel) ? 0 : 1].contains(word), mode + " " + word);
        }
        assertEquals("Mode " + mode.number(), lang("en_us").get(mode.translationKey()).getAsString());
        assertEquals("Mode " + mode.number(), lang("de_de").get(mode.translationKey()).getAsString());
        String german = lang("de_de").get(mode.layoutKey()).getAsString();
        assertTrue(german.startsWith("links ") && german.contains(", rechts "), german);
    }

    @Test
    void searchFindsTheStickModeInBothLanguages() throws IOException {
        JsonObject en = lang("en_us");
        for (String word : List.of("stick mode", "mode 1", "mode 4", "mode", "layout", "throttle left")) {
            assertTrue(finds(en, word), word);
        }
        assertFalse(finds(en, "deadband"));
        JsonObject de = lang("de_de");
        for (String word : List.of("Knüppel-Mode", "mode 2", "Mode 3", "Steuermodus", "Belegung", "links")) {
            assertTrue(finds(de, word), word);
        }
        assertFalse(finds(de, "Totzone"));
    }
}
