package de.lardtom.propwash.join;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinLangTest {
    private static final Pattern TEXT = Pattern.compile("\\bt\\(\"([a-z_.]+)\",\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern SCREEN = Pattern.compile("KEY \\+ \"([a-z_.]+)\",\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern FULL = Pattern.compile("translatableWithFallback\\((DISCONNECT_KEY), \"((?:[^\"\\\\]|\\\\.)*)\"");

    private static Path root() {
        for (Path candidate : List.of(Path.of("common"), Path.of("."))) {
            if (Files.isDirectory(candidate.resolve("src/lang/join"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("common/src/lang/join nicht gefunden");
    }

    private static JsonObject lang(String code) throws IOException {
        return JsonParser.parseString(Files.readString(root().resolve("src/lang/join/" + code + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Map<String, String> fallbacks() throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        Path dir = root().resolve("src/main/java/de/lardtom/propwash/join");
        String text = Files.readString(dir.resolve("JoinText.java"), StandardCharsets.UTF_8);
        collect(TEXT, text, JoinText.KEY, out);
        Matcher full = FULL.matcher(text);
        while (full.find()) {
            out.put(JoinText.DISCONNECT_KEY, full.group(2).replace("\\\"", "\""));
        }
        collect(SCREEN, Files.readString(dir.resolve("client/JoinMismatchScreen.java"), StandardCharsets.UTF_8), JoinText.KEY + "screen.", out);
        return out;
    }

    private static void collect(Pattern pattern, String source, String prefix, Map<String, String> out) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            String key = prefix + matcher.group(1);
            String value = matcher.group(2).replace("\\\"", "\"");
            String previous = out.put(key, value);
            if (previous != null) {
                assertEquals(previous, value, "zwei Rückfalltexte für " + key);
            }
        }
    }

    @Test
    void englishMatchesTheFallbacksInCode() throws IOException {
        Map<String, String> fallbacks = fallbacks();
        assertTrue(fallbacks.size() > 40, "zu wenige Rückfalltexte gefunden: " + fallbacks.size());
        JsonObject en = lang("en_us");
        for (Map.Entry<String, String> entry : fallbacks.entrySet()) {
            assertTrue(en.has(entry.getKey()), "en_us fehlt " + entry.getKey());
            assertEquals(entry.getValue(), en.get(entry.getKey()).getAsString(), entry.getKey());
        }
        assertEquals(fallbacks.keySet(), en.keySet());
    }

    @Test
    void germanHasEveryKeyWithTheSamePlaceholders() throws IOException {
        JsonObject en = lang("en_us");
        JsonObject de = lang("de_de");
        assertEquals(en.keySet(), de.keySet());
        for (String key : en.keySet()) {
            String english = en.get(key).getAsString();
            String german = de.get(key).getAsString();
            assertEquals(count(english), count(german), key);
        }
    }

    private static int count(String text) {
        int count = 0;
        for (int i = text.indexOf("%s"); i >= 0; i = text.indexOf("%s", i + 2)) {
            count++;
        }
        return count;
    }
}
