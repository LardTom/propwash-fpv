package de.lardtom.propwash.guide.training;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MouseTipDirectionTest {
    private static final Map<String, List<String>> PITCH_WORDS = Map.of(
            "en_us", List.of("forward", "back"),
            "de_de", List.of("vorn", "hinten", "zurück"));

    private static JsonObject lang(String language) throws IOException {
        for (Path dir : List.of(Path.of("common/src/lang/guide"), Path.of("src/lang/guide"))) {
            Path file = dir.resolve(language + ".json");
            if (Files.isRegularFile(file)) {
                return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
        throw new IOException("Sprachdatei der Flugschule fehlt: " + language);
    }

    private static boolean namesPitchDirection(String language, String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return PITCH_WORDS.get(language).stream().anyMatch(lower::contains);
    }

    @Test
    void invertedPitchAsksForTheInvertedTipFirst() {
        Lesson lesson = Curriculum.lessons().getFirst();
        assertEquals(List.of(lesson.tipKey() + "_mouse"), lesson.mouseTipKeys(false));
        assertEquals(List.of(lesson.tipKey() + "_mouse_inverted", lesson.tipKey() + "_mouse"), lesson.mouseTipKeys(true));
    }

    @Test
    void everyMouseTipThatNamesForwardOrBackHasAnInvertedTwin() throws IOException {
        for (String language : PITCH_WORDS.keySet()) {
            JsonObject lang = lang(language);
            int twins = 0;
            for (Lesson lesson : Curriculum.lessons()) {
                String key = lesson.tipKey() + "_mouse";
                String twin = key + "_inverted";
                if (!lang.has(key)) {
                    assertFalse(lang.has(twin), language + " " + twin + " ohne Maus-Tipp");
                    continue;
                }
                String text = lang.get(key).getAsString();
                if (namesPitchDirection(language, text)) {
                    assertTrue(lang.has(twin), language + " " + key + " nennt vor/zurück, aber der umgekehrte Tipp fehlt");
                    String inverted = lang.get(twin).getAsString();
                    assertNotEquals(text, inverted, language + " " + twin);
                    assertTrue(namesPitchDirection(language, inverted), language + " " + twin + " nennt keine Richtung");
                    assertTrue(Math.abs(inverted.length() - text.length()) <= 24, language + " " + twin + " ist deutlich länger");
                    twins++;
                } else {
                    assertFalse(lang.has(twin), language + " " + twin + " ist überflüssig");
                }
            }
            assertEquals(3, twins, language);
        }
    }
}
