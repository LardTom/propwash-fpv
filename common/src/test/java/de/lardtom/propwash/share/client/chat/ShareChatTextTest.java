package de.lardtom.propwash.share.client.chat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareChatTextTest {
    private static final List<String> SUBSTITUTES = List.of("fuer", "ueber", "pruef", "ungueltig", "oeffn", "zubehoer", "laesst", "uebernomm",
            "bestaetig", "waehl", "fuehr", "darueber");

    private static Path repo(String path) {
        for (Path candidate : List.of(Path.of(path), Path.of("..", path))) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return Path.of(path);
    }

    private static JsonObject lang(String module, String code) throws IOException {
        return JsonParser.parseString(Files.readString(repo("common/src/lang/" + module + "/" + code + ".json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static Set<String> usedKeys() throws IOException {
        Set<String> out = new TreeSet<>();
        Pattern key = Pattern.compile("KEY \\+ \"([a-z_.]+)\"");
        Pattern literal = Pattern.compile("\"(propwash\\.share\\.chat\\.[a-z_.]+)\"");
        Path dir = repo("common/src/main/java/de/lardtom/propwash/share/client/chat");
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = key.matcher(text);
                while (matcher.find()) {
                    String suffix = matcher.group(1);
                    out.add(ChatPreview.KEY + suffix);
                }
                Matcher full = literal.matcher(text);
                while (full.find()) {
                    out.add(full.group(1));
                }
            }
        }
        return out;
    }

    @Test
    void englishAndGermanHaveTheSameKeys() throws IOException {
        assertEquals(lang("pw3-chat", "en_us").keySet(), lang("pw3-chat", "de_de").keySet());
    }

    @Test
    void everyKeyInTheCodeExists() throws IOException {
        Set<String> keys = lang("pw3-chat", "en_us").keySet();
        for (String used : usedKeys()) {
            if (used.endsWith(".")) {
                continue;
            }
            assertTrue(keys.contains(used) || keys.stream().anyMatch(k -> k.startsWith(used + ".")), "Schlüssel fehlt: " + used);
        }
    }

    @Test
    void noKeyIsSharedWithPw3CodecOrPw3Ui() throws IOException {
        Set<String> mine = lang("pw3-chat", "en_us").keySet();
        for (String module : List.of("pw3-codec", "pw3-ui")) {
            for (String code : List.of("en_us", "de_de")) {
                Set<String> other = lang(module, code).keySet();
                for (String key : mine) {
                    assertFalse(other.contains(key), "Schlüssel doppelt mit " + module + ": " + key);
                }
            }
        }
        for (String key : mine) {
            assertTrue(key.startsWith(ChatPreview.KEY), "Präfix: " + key);
        }
    }

    @Test
    void germanWritesSharecodeAsOneWordInEveryModule() throws IOException {
        int found = 0;
        try (Stream<Path> modules = Files.list(repo("common/src/lang"))) {
            for (Path module : modules.sorted().toList()) {
                Path file = module.resolve("de_de.json");
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                JsonObject german = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                for (String key : german.keySet()) {
                    String text = german.get(key).getAsString();
                    assertFalse(text.toLowerCase(Locale.ROOT).contains("share-code"), module.getFileName() + " " + key + ": " + text);
                    if (text.contains("Sharecode")) {
                        found++;
                    }
                }
            }
        }
        assertTrue(found > 20, "Sharecode nur " + found + "-mal gefunden");
    }

    @Test
    void germanUsesUmlauts() throws IOException {
        JsonObject german = lang("pw3-chat", "de_de");
        for (String key : german.keySet()) {
            String text = german.get(key).getAsString().toLowerCase(Locale.ROOT);
            for (String bad : SUBSTITUTES) {
                assertFalse(text.contains(bad), key + ": " + text);
            }
        }
        assertTrue(german.get("propwash.share.chat.invalid").getAsString().equals("Code ungültig"));
    }
}
