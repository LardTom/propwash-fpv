package de.lardtom.propwash.race;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class RaceDataTest {
    private static final List<String> CRAFTABLE = List.of("gate_small", "gate_medium", "gate_large", "course_tool", "race_flag", "slalom_pole",
            "dive_cube", "freeform_gate", "gate_pole");

    private static Path common() {
        for (Path candidate : List.of(Path.of("common"), Path.of("."))) {
            if (Files.isDirectory(candidate.resolve("src/main/resources/data/propwash"))) {
                return candidate;
            }
        }
        fail("common-Modul nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void gatesAndCourseToolHaveShapedVanillaRecipes() throws IOException {
        Path dir = common().resolve("src/main/resources/data/propwash/recipe");
        for (String id : CRAFTABLE) {
            Path file = dir.resolve(id + ".json");
            assertTrue(Files.exists(file), "Rezept fehlt: " + id);
            JsonObject recipe = read(file);
            assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(), id);
            assertEquals("propwash:" + id, recipe.getAsJsonObject("result").get("id").getAsString(), id);
            JsonArray pattern = recipe.getAsJsonArray("pattern");
            JsonObject key = recipe.getAsJsonObject("key");
            assertTrue(pattern.size() >= 1 && pattern.size() <= 3, id);
            int width = pattern.get(0).getAsString().length();
            Set<Character> used = new HashSet<>();
            for (JsonElement row : pattern) {
                String line = row.getAsString();
                assertEquals(width, line.length(), "ungleiche Zeilenbreite in " + id);
                assertTrue(width <= 3, id);
                for (char c : line.toCharArray()) {
                    if (c != ' ') {
                        used.add(c);
                        assertTrue(key.has(String.valueOf(c)), "Zeichen " + c + " fehlt im Schlüssel von " + id);
                    }
                }
            }
            for (Map.Entry<String, JsonElement> entry : key.entrySet()) {
                assertTrue(used.contains(entry.getKey().charAt(0)), "Schlüssel " + entry.getKey() + " unbenutzt in " + id);
                String ingredient = entry.getValue().getAsString();
                assertTrue(ingredient.startsWith("minecraft:") || ingredient.startsWith("#minecraft:"),
                        "Rezept " + id + " nutzt keine Vanilla-Zutat: " + ingredient);
            }
        }
    }

    @Test
    void everyRaceRecipeIsUnlockedByAnAdvancement() throws IOException {
        Path dir = common().resolve("src/main/resources/data/propwash/advancement/recipes");
        Set<String> unlocked = new HashSet<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                read(file).getAsJsonObject("rewards").getAsJsonArray("recipes").forEach(e -> unlocked.add(e.getAsString()));
            }
        }
        for (String id : CRAFTABLE) {
            assertTrue(unlocked.contains("propwash:" + id), "kein Freischalt-Fortschritt für " + id);
        }
    }

    @Test
    void languageFilesHaveTheSameKeys() throws IOException {
        JsonObject german = read(common().resolve("src/lang/race/de_de.json"));
        JsonObject english = read(common().resolve("src/lang/race/en_us.json"));
        assertEquals(german.keySet(), english.keySet());
    }
}
