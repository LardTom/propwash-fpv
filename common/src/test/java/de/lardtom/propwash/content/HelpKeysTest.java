package de.lardtom.propwash.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.core.PartCategory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class HelpKeysTest {
    private static final List<String> OTHER_ITEMS = List.of("drone", "remote", "goggles_analog", "goggles_digital", "goggles_creative",
            "drone_workbench", "battery_charger", "gate_small", "gate_medium", "gate_large", "course_tool", "race_flag", "slalom_pole", "dive_cube",
            "freeform_gate", "gate_pole", "bando_map");
    private static final List<String> REPLACEMENT_SPELLINGS = List.of("fuer", "ueber", "moeglich", "Groesse", "laedt", "waehrend");
    private static final List<String> SHARP_S = List.of("gross", "Strasse");
    private static final int MAX_LINE = 48;

    private static Path lang() {
        for (Path candidate : List.of(Path.of("common/src/lang"), Path.of("src/lang"))) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        fail("Sprachquellen nicht gefunden");
        return Path.of(".");
    }

    private static Map<String, String> entries(String language) throws IOException {
        Map<String, String> all = new LinkedHashMap<>();
        try (Stream<Path> modules = Files.list(lang())) {
            for (Path module : modules.toList()) {
                Path file = module.resolve(language + ".json");
                if (Files.exists(file)) {
                    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        json.entrySet().forEach(e -> all.put(e.getKey(), e.getValue().getAsString()));
                    }
                }
            }
        }
        return all;
    }

    private static List<String> itemIds() {
        List<String> ids = new ArrayList<>();
        for (PartCategory category : PartCategory.values()) {
            for (PartInfo part : category.parts()) {
                ids.add(part.key());
            }
        }
        ids.addAll(OTHER_ITEMS);
        return ids;
    }

    @Test
    void everyItemHasASummaryInBothLanguages() throws IOException {
        for (String language : List.of("de_de", "en_us")) {
            Map<String, String> entries = entries(language);
            List<String> missing = new ArrayList<>();
            for (String id : itemIds()) {
                if (!entries.containsKey(ItemHelp.PREFIX + id + ".summary")) {
                    missing.add(id);
                }
            }
            assertTrue(missing.isEmpty(), language + " ohne Kurzzeile: " + missing);
        }
    }

    @Test
    void everyCategoryExplainsWhatAndHow() throws IOException {
        for (String language : List.of("de_de", "en_us")) {
            Map<String, String> entries = entries(language);
            for (PartCategory category : PartCategory.values()) {
                String prefix = ItemHelp.PREFIX + "category." + category.key();
                assertTrue(entries.containsKey(prefix + ".what.1"), language + " " + prefix + ".what.1");
                assertTrue(entries.containsKey(prefix + ".use.1"), language + " " + prefix + ".use.1");
            }
            for (String shared : List.of("goggles", "gate", "remote", "drone", "drone_workbench", "battery_charger", "course_tool")) {
                assertTrue(entries.containsKey(ItemHelp.PREFIX + shared + ".what.1"), language + " " + shared + ".what.1");
            }
        }
    }

    @Test
    void helpLinesFitATooltip() throws IOException {
        for (String language : List.of("de_de", "en_us")) {
            for (Map.Entry<String, String> entry : entries(language).entrySet()) {
                if (entry.getKey().startsWith(ItemHelp.PREFIX) || entry.getKey().startsWith("propwash.race.tool.tooltip.")) {
                    assertTrue(entry.getValue().length() <= MAX_LINE, language + " " + entry.getKey() + " ist zu lang: " + entry.getValue());
                }
            }
        }
    }

    @Test
    void numberedHelpLinesAreAllShown() throws IOException {
        Pattern numbered = Pattern.compile(Pattern.quote(ItemHelp.PREFIX) + "(.+)\\.(what|use|fits)\\.(\\d+)");
        for (String language : List.of("de_de", "en_us")) {
            Map<String, String> entries = entries(language);
            for (String key : entries.keySet()) {
                Matcher matcher = numbered.matcher(key);
                if (!matcher.matches()) {
                    continue;
                }
                int line = Integer.parseInt(matcher.group(3));
                assertTrue(line <= ItemHelp.MAX_LINES, language + " " + key + ": nur " + ItemHelp.MAX_LINES + " Zeilen werden gezeigt");
            }
        }
    }

    @Test
    void germanUsesUmlautsAndSharpS() throws IOException {
        for (Map.Entry<String, String> entry : entries("de_de").entrySet()) {
            String value = entry.getValue();
            for (String word : REPLACEMENT_SPELLINGS) {
                Pattern pattern = Pattern.compile("\\b" + Pattern.quote(word.toLowerCase(Locale.ROOT)), Pattern.CASE_INSENSITIVE);
                assertTrue(!pattern.matcher(value).find(), entry.getKey() + " enthält Ersatzschreibung '" + word + "': " + value);
            }
            for (String word : SHARP_S) {
                Pattern pattern = Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE);
                assertTrue(!pattern.matcher(value).find(), entry.getKey() + " enthält 'ss' statt 'ß': " + value);
            }
        }
    }
}
