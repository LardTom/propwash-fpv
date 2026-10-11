package de.lardtom.propwash.server.rules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldRulesExportTest {
    @Test
    void everyRuleHasNameDescriptionAndValueLabelsInBothLanguages() throws IOException {
        List<String> missing = WorldRulesExport.missingTexts(WorldRulesExport.languages());
        assertTrue(missing.isEmpty(), "fehlende Texte: " + missing);
    }

    @Test
    void germanTextsUseRealUmlauts() throws IOException {
        List<String> transliterated = List.of("fuer", "ueber", "aendern", "groesser", "koennen", "faellt", "laenger", "hoeher", "staerke",
                "staerker", "schwaecher", "naeher", "muessen", "duerfen", "gross", "strasse", "schliessen", "heiss", "heisse");
        Map<String, String> german = WorldRulesExport.languages().get("de_de");
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            for (String key : List.of(spec.translationKey(), spec.descriptionKey())) {
                for (String word : german.getOrDefault(key, "").split("[^\\p{L}]+")) {
                    assertFalse(transliterated.contains(word.toLowerCase(Locale.ROOT)), key + ": " + word);
                }
            }
        }
    }

    @Test
    void exportListsEveryRuleForTheWebsite() throws IOException {
        JsonObject json = WorldRulesExport.build(WorldRulesExport.languages());
        assertEquals(WorldRulesExport.FORMAT, json.get("format").getAsInt());
        assertEquals(WorldRuleSpecs.all().size(), json.getAsJsonArray("rules").size());
        for (JsonElement element : json.getAsJsonArray("rules")) {
            JsonObject rule = element.getAsJsonObject();
            String name = rule.get("name").getAsString();
            RuleSpec spec = WorldRuleSpecs.byName(name).orElseThrow();
            assertEquals(spec.id(), rule.get("id").getAsString());
            assertFalse(rule.getAsJsonObject("title").get("de").getAsString().isBlank(), name);
            assertFalse(rule.getAsJsonObject("title").get("en").getAsString().isBlank(), name);
            assertFalse(rule.getAsJsonObject("description").get("de").getAsString().isBlank(), name);
            assertFalse(rule.getAsJsonObject("description").get("en").getAsString().isBlank(), name);
            if (spec.kind() == RuleSpec.Kind.BOOL) {
                assertEquals("boolean", rule.get("type").getAsString());
                assertEquals(spec.defaultOn(), rule.get("default").getAsBoolean());
            } else {
                assertEquals("integer", rule.get("type").getAsString());
                assertEquals(spec.defaultValue(), rule.get("default").getAsInt());
                assertEquals(spec.min(), rule.get("min").getAsInt());
                assertEquals(spec.max(), rule.get("max").getAsInt());
            }
        }
        assertTrue(json.getAsJsonArray("rules").get(0).getAsJsonObject().get("changes_physics").getAsBoolean());
        assertEquals(RulePreset.values().length, json.getAsJsonArray("presets").size());
        for (JsonElement element : json.getAsJsonArray("presets")) {
            JsonObject preset = element.getAsJsonObject();
            RulePreset known = RulePreset.byId(preset.get("id").getAsString()).orElseThrow();
            assertFalse(preset.getAsJsonObject("title").get("de").getAsString().isBlank(), known.id());
            assertFalse(preset.getAsJsonObject("title").get("en").getAsString().isBlank(), known.id());
            assertFalse(preset.getAsJsonObject("description").get("de").getAsString().isBlank(), known.id());
            assertFalse(preset.getAsJsonObject("description").get("en").getAsString().isBlank(), known.id());
            JsonObject values = preset.getAsJsonObject("values");
            assertEquals(WorldRuleSpecs.all().size(), values.size(), known.id());
            for (RuleSpec spec : WorldRuleSpecs.all()) {
                int value = spec.kind() == RuleSpec.Kind.BOOL ? values.get(spec.name()).getAsBoolean() ? 1 : 0 : values.get(spec.name()).getAsInt();
                assertEquals(known.value(spec), value, known.id() + " " + spec.name());
            }
            assertEquals(known.differences().size(), preset.getAsJsonArray("differs_from_default").size(), known.id());
            assertEquals(known != RulePreset.REALISTIC, preset.get("changes_physics").getAsBoolean(), known.id());
        }
        Path written = WorldRulesExport.write(json);
        assertTrue(Files.readString(written, StandardCharsets.UTF_8).contains("\"propwash:gravity_scale\""));
    }
}
