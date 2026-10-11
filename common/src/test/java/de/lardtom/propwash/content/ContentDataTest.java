package de.lardtom.propwash.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MountSize;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.core.PartCategory;
import de.lardtom.propwash.sim.BuildWarning;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ContentDataTest {
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

    private static List<String> craftableIds() {
        List<String> ids = new ArrayList<>();
        for (PartCategory category : PartCategory.values()) {
            for (PartInfo part : category.parts()) {
                if (!(part instanceof StackType stack && stack.creative())) {
                    ids.add(part.key());
                }
            }
        }
        ids.add("goggles_analog");
        ids.add("goggles_digital");
        ids.add("drone_workbench");
        ids.add("battery_charger");
        ids.add("remote");
        for (CraftingMaterial material : CraftingMaterial.values()) {
            ids.add(material.key());
        }
        return ids;
    }

    private static boolean allowedIngredient(String ingredient) {
        if (ingredient.startsWith("minecraft:") || ingredient.startsWith("#minecraft:")) {
            return true;
        }
        for (CraftingMaterial material : CraftingMaterial.values()) {
            if (ingredient.equals("propwash:" + material.key())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void everyCraftableItemHasAValidRecipe() throws IOException {
        Path dir = common().resolve("src/main/resources/data/propwash/recipe");
        for (String id : craftableIds()) {
            Path file = dir.resolve(id + ".json");
            assertTrue(Files.exists(file), "Rezept fehlt: " + id);
            JsonObject recipe = read(file);
            assertEquals("propwash:" + id, recipe.getAsJsonObject("result").get("id").getAsString(), id);
            String type = recipe.get("type").getAsString();
            List<String> ingredients = new ArrayList<>();
            if (type.equals("minecraft:crafting_shaped")) {
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
                    ingredients.add(entry.getValue().getAsString());
                }
            } else if (type.equals("minecraft:crafting_shapeless")) {
                JsonArray list = recipe.getAsJsonArray("ingredients");
                assertTrue(list.size() >= 1 && list.size() <= 9, id);
                list.forEach(e -> ingredients.add(e.getAsString()));
            } else {
                fail("unerwarteter Rezepttyp " + type + " in " + id);
            }
            for (String ingredient : ingredients) {
                assertTrue(allowedIngredient(ingredient), "Rezept " + id + " nutzt weder Vanilla-Zutat noch Zwischenteil: " + ingredient);
            }
        }
    }

    @Test
    void creativeGogglesHaveNoSurvivalRecipe() {
        assertFalse(Files.exists(common().resolve("src/main/resources/data/propwash/recipe/goggles_creative.json")));
        assertFalse(Files.exists(common().resolve("src/main/resources/data/propwash/recipe/creative_fc.json")));
        assertFalse(Files.exists(common().resolve("src/main/resources/data/propwash/advancement/recipes/tools/creative_fc.json")));
    }

    @Test
    void everyRecipeIsUnlockedByAnAdvancement() throws IOException {
        Path dir = common().resolve("src/main/resources/data/propwash/advancement/recipes");
        Set<String> unlocked = new HashSet<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                read(file).getAsJsonObject("rewards").getAsJsonArray("recipes").forEach(e -> unlocked.add(e.getAsString()));
            }
        }
        for (String id : craftableIds()) {
            assertTrue(unlocked.contains("propwash:" + id), "kein Freischalt-Fortschritt für " + id);
        }
    }

    @Test
    void workbenchDropsItself() throws IOException {
        JsonObject loot = read(common().resolve("src/main/resources/data/propwash/loot_table/blocks/drone_workbench.json"));
        String name = loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject()
                .get("name").getAsString();
        assertEquals("propwash:drone_workbench", name);
    }

    @Test
    void languageFilesCoverEveryContentKey() throws IOException {
        List<String> keys = new ArrayList<>();
        for (PartCategory category : PartCategory.values()) {
            for (PartInfo part : category.parts()) {
                keys.add(part.translationKey());
            }
            keys.add("propwash.content.tooltip.category." + category.key());
            keys.add("propwash.content.workbench.slot." + category.key());
            keys.add("propwash.content.workbench.slot_hint." + category.key());
            keys.add("propwash.content.workbench.mixed." + category.key());
        }
        for (FrameType frame : FrameType.values()) {
            keys.add("propwash.content.frame_short." + frame.key());
        }
        for (MountSize.Motor mount : MountSize.Motor.values()) {
            if (PartIndex.DEFAULT_NAMESPACE.equals(mount.id().getNamespace())) {
                keys.add("propwash.content.mount.motor." + mount.name().toLowerCase(Locale.ROOT));
            }
        }
        for (MountSize.Stack mount : MountSize.Stack.values()) {
            if (PartIndex.DEFAULT_NAMESPACE.equals(mount.id().getNamespace())) {
                keys.add("propwash.content.mount.stack." + mount.name().toLowerCase(Locale.ROOT));
            }
        }
        for (VideoSystem system : VideoSystem.values()) {
            String name = system.name().toLowerCase(Locale.ROOT);
            keys.add("propwash.content.video_system." + name);
            keys.add("propwash.content.goggles." + name + ".description");
            keys.add("item.propwash.goggles_" + name);
        }
        for (BatteryType.Chemistry chemistry : BatteryType.Chemistry.values()) {
            keys.add("propwash.content.tooltip.chemistry." + chemistry.name().toLowerCase(Locale.ROOT));
        }
        for (BuildWarning warning : BuildWarning.values()) {
            keys.add("propwash.content.workbench.warning." + warning.name().toLowerCase(Locale.ROOT));
        }
        for (String preset : List.of("whoop", "cinewhoop", "freestyle", "racer", "long_range", "custom")) {
            keys.add("propwash.content.preset." + preset);
        }
        for (String position : List.of("front_left", "front_right", "rear_left", "rear_right")) {
            keys.add("propwash.content.workbench.position." + position);
        }
        keys.add("block.propwash.drone_workbench");
        keys.add("container.propwash.drone_workbench");
        keys.add("itemGroup.propwash.main");
        keys.add("itemGroup.propwash.extension");
        for (String lang : List.of("de_de", "en_us")) {
            JsonObject json = read(common().resolve("src/lang/content/" + lang + ".json"));
            for (String key : keys) {
                assertTrue(json.has(key), lang + " fehlt " + key);
            }
        }
    }
}
