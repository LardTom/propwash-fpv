package de.lardtom.propwash.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SurvivalRecipeBookTest {
    private static final String UNLOCK_ALL = "has_propwash_item";
    private static final String HAS_RECIPE = "has_the_recipe";
    private static final String INVENTORY_CHANGED = "minecraft:inventory_changed";
    private static final String RECIPE_UNLOCKED = "minecraft:recipe_unlocked";
    private static final Map<String, String> FOLDERS = Map.of("misc", "misc", "equipment", "tools", "redstone", "redstone",
            "building", "building_blocks");
    private static final Set<String> MISC_ITEMS = Set.of("drone_workbench", "battery_charger");
    private static final Set<String> BUILDING_ITEMS = Set.of("gate_small", "gate_medium", "gate_large", "race_flag", "slalom_pole", "dive_cube",
            "freeform_gate", "gate_pole", "signal_repeater");
    private static final Set<String> NOT_CRAFTABLE = Set.of("goggles_creative", "creative_fc", "drone", "bando_map");

    private static Path root() {
        for (Path candidate : List.of(Path.of("."), Path.of(".."))) {
            if (Files.isDirectory(candidate.resolve("common/src/main/resources/data/propwash"))) {
                return candidate;
            }
        }
        fail("Projektwurzel nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static Path data() {
        return root().resolve("common/src/main/resources/data/propwash");
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Set<String> propwashItems() throws IOException {
        Set<String> items = new TreeSet<>();
        try (Stream<Path> files = Files.list(root().resolve("common/src/main/resources/assets/propwash/items"))) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> items.add("propwash:" + p.getFileName().toString().replace(".json", "")));
        }
        return items;
    }

    private static Map<String, JsonObject> recipes() throws IOException {
        Map<String, JsonObject> out = new HashMap<>();
        try (Stream<Path> files = Files.list(data().resolve("recipe"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                out.put(file.getFileName().toString().replace(".json", ""), read(file));
            }
        }
        return out;
    }

    private static Map<String, Path> advancements() throws IOException {
        Map<String, Path> out = new HashMap<>();
        try (Stream<Path> files = Files.walk(data().resolve("advancement/recipes"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonArray rewards = read(file).getAsJsonObject("rewards").getAsJsonArray("recipes");
                assertEquals(1, rewards.size(), file.toString());
                String recipe = rewards.get(0).getAsString();
                assertTrue(recipe.startsWith("propwash:"), file.toString());
                assertFalse(out.containsKey(recipe), "zwei Fortschritte für " + recipe);
                out.put(recipe.substring("propwash:".length()), file);
            }
        }
        return out;
    }

    private static List<String> ingredients(JsonObject recipe) {
        Set<String> out = new LinkedHashSet<>();
        if (recipe.has("key")) {
            recipe.getAsJsonObject("key").entrySet().forEach(e -> out.add(e.getValue().getAsString()));
        } else {
            recipe.getAsJsonArray("ingredients").forEach(e -> out.add(e.getAsString()));
        }
        return new ArrayList<>(out);
    }

    private static Set<String> itemsOf(JsonObject criterion) {
        Set<String> out = new TreeSet<>();
        JsonArray predicates = criterion.getAsJsonObject("conditions").getAsJsonArray("items");
        assertEquals(1, predicates.size());
        JsonElement items = predicates.get(0).getAsJsonObject().get("items");
        if (items.isJsonArray()) {
            items.getAsJsonArray().forEach(e -> out.add(e.getAsString()));
        } else {
            out.add(items.getAsString());
        }
        return out;
    }

    private static String expectedCategory(String id) {
        Set<String> materials = new HashSet<>();
        for (CraftingMaterial material : CraftingMaterial.values()) {
            materials.add(material.key());
        }
        if (materials.contains(id) || MISC_ITEMS.contains(id)) {
            return "misc";
        }
        return BUILDING_ITEMS.contains(id) ? "building" : "equipment";
    }

    @Test
    void everyRecipeUnlocksFromAnyIngredientAndFromAnyPropwashItem() throws IOException {
        Set<String> allItems = propwashItems();
        Map<String, Path> advancements = advancements();
        Map<String, JsonObject> recipes = recipes();
        assertEquals(new TreeSet<>(recipes.keySet()), new TreeSet<>(advancements.keySet()), "Rezepte ohne bzw. Fortschritte ohne Rezept");
        for (Map.Entry<String, JsonObject> entry : recipes.entrySet()) {
            String id = entry.getKey();
            JsonObject advancement = read(advancements.get(id));
            assertEquals("minecraft:recipes/root", advancement.get("parent").getAsString(), id);
            JsonObject criteria = advancement.getAsJsonObject("criteria");
            Set<String> seenIngredients = new HashSet<>();
            Set<String> names = new TreeSet<>(criteria.keySet());
            for (Map.Entry<String, JsonElement> criterion : criteria.entrySet()) {
                JsonObject value = criterion.getValue().getAsJsonObject();
                String trigger = value.get("trigger").getAsString();
                if (criterion.getKey().equals(HAS_RECIPE)) {
                    assertEquals(RECIPE_UNLOCKED, trigger, id);
                    assertEquals("propwash:" + id, value.getAsJsonObject("conditions").get("recipes").getAsString(), id);
                } else if (criterion.getKey().equals(UNLOCK_ALL)) {
                    assertEquals(INVENTORY_CHANGED, trigger, id);
                    assertEquals(allItems, itemsOf(value), id + ": nicht jedes Propwash-Item schaltet das Rezept frei");
                } else {
                    assertEquals(INVENTORY_CHANGED, trigger, id + " " + criterion.getKey());
                    seenIngredients.addAll(itemsOf(value));
                }
            }
            assertTrue(names.contains(HAS_RECIPE) && names.contains(UNLOCK_ALL), id + ": " + names);
            assertEquals(new TreeSet<>(ingredients(entry.getValue())), new TreeSet<>(seenIngredients),
                    id + ": nicht jede Zutat schaltet das Rezept frei");
            JsonArray requirements = advancement.getAsJsonArray("requirements");
            assertEquals(1, requirements.size(), id + ": Kriterien müssen mit ODER verknüpft sein");
            Set<String> any = new TreeSet<>();
            requirements.get(0).getAsJsonArray().forEach(e -> any.add(e.getAsString()));
            assertEquals(names, any, id + ": nicht jedes Kriterium reicht allein");
        }
    }

    @Test
    void workbenchAloneUnlocksTheWholeRecipeSet() throws IOException {
        Map<String, Path> advancements = advancements();
        for (Map.Entry<String, Path> entry : advancements.entrySet()) {
            JsonObject criteria = read(entry.getValue()).getAsJsonObject("criteria");
            assertTrue(itemsOf(criteria.getAsJsonObject(UNLOCK_ALL)).contains("propwash:drone_workbench"), entry.getKey());
        }
        Set<String> craftable = new TreeSet<>();
        for (String item : propwashItems()) {
            craftable.add(item.substring("propwash:".length()));
        }
        craftable.removeAll(NOT_CRAFTABLE);
        assertEquals(craftable, new TreeSet<>(advancements.keySet()), "nicht jedes herstellbare Item hat ein freischaltbares Rezept");
    }

    @Test
    void recipeBookTabsGroupDroneGearTogether() throws IOException {
        Map<String, Path> advancements = advancements();
        for (Map.Entry<String, JsonObject> entry : recipes().entrySet()) {
            String id = entry.getKey();
            String category = entry.getValue().get("category").getAsString();
            assertEquals(expectedCategory(id), category, id + ": Rezeptbuch-Reiter");
            assertEquals(FOLDERS.get(category), advancements.get(id).getParent().getFileName().toString(), id + ": Ordner des Fortschritts");
        }
        Map<String, JsonObject> recipes = recipes();
        for (PartCategory category : PartCategory.values()) {
            for (PartInfo part : category.parts()) {
                if (NOT_CRAFTABLE.contains(part.key())) {
                    continue;
                }
                assertEquals("equipment", recipes.get(part.key()).get("category").getAsString(), part.key());
            }
        }
    }

    @Test
    void generatorCheckPasses() throws IOException, InterruptedException {
        Path script = root().resolve("tools/data/survival.py");
        assumeTrue(Files.exists(script), "Generator fehlt");
        Process process;
        try {
            process = new ProcessBuilder("python3", script.toString(), "--check").redirectErrorStream(true).start();
        } catch (IOException missing) {
            assumeTrue(false, "python3 nicht verfügbar");
            return;
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Generator hängt");
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("SURVIVAL DATA OK"), output);
    }
}
