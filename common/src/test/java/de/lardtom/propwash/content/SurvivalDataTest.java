package de.lardtom.propwash.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.core.PartCategory;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class SurvivalDataTest {
    private static final Set<String> EARLY_GAME = Set.of("minecraft:copper_ingot", "minecraft:copper_nugget", "minecraft:iron_ingot",
            "minecraft:iron_nugget", "minecraft:redstone", "minecraft:redstone_torch", "minecraft:lever", "minecraft:glass_pane",
            "minecraft:dried_kelp", "#minecraft:coals", "minecraft:string", "minecraft:lapis_lazuli", "minecraft:leather",
            "#minecraft:planks", "minecraft:crafting_table", "minecraft:smooth_stone_slab", "minecraft:stick", "minecraft:paper",
            "#minecraft:wool", "minecraft:lightning_rod");
    private static final Set<String> RARE = Set.of("minecraft:gold_ingot", "minecraft:gold_nugget", "minecraft:diamond",
            "minecraft:amethyst_shard", "minecraft:echo_shard", "minecraft:quartz", "minecraft:glowstone_dust", "minecraft:tinted_glass");
    private static final List<String> ENTRY_ITEMS = List.of("drone_workbench", "battery_charger", "remote", "goggles_analog",
            "whoop_65", "aio_whoop", "m0802_22000", "p31_4b", "analog_nano", "b1s_300_hv", "cinewhoop_3", "aio_20", "m1404_4600",
            "p3_3b", "analog_race", "b4s_650", "freestyle_5", "m2207_1750", "p51_3b", "gate_small", "course_tool", "led_strip");
    private static final List<String> HIGH_END = List.of("race_5", "stack_30", "m2306_1860", "digital_lite", "digital_hd",
            "goggles_digital", "b6s_1100", "b6s_1300", "b6s_4000_lion", "hd_chip", "action_cam_light", "action_cam_heavy");
    private static final List<String> TOP_TIER = List.of("digital_hd", "m2306_1860", "b6s_1300", "b6s_4000_lion", "goggles_digital",
            "race_5", "action_cam_heavy");
    private static final Set<String> TOP_TIER_TABLES = Set.of("propwash:inject/stronghold", "propwash:inject/vault",
            "propwash:inject/vault_ominous", "propwash:inject/ancient_city", "propwash:inject/end_city");
    private static final double MAX_FIND_CHANCE = 0.6;

    private static Path common() {
        for (Path candidate : List.of(Path.of("common"), Path.of("."))) {
            if (Files.isDirectory(candidate.resolve("src/main/resources/data/propwash"))) {
                return candidate;
            }
        }
        fail("common-Modul nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static Path neoforge() {
        for (Path candidate : List.of(Path.of("neoforge"), Path.of("../neoforge"))) {
            if (Files.isDirectory(candidate.resolve("src/main/resources"))) {
                return candidate;
            }
        }
        fail("neoforge-Modul nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static Path data() {
        return common().resolve("src/main/resources/data/propwash");
    }

    private static Path assets() {
        return common().resolve("src/main/resources/assets/propwash");
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static List<String> ingredients(String id) throws IOException {
        Path file = data().resolve("recipe/" + id + ".json");
        assertTrue(Files.exists(file), "Rezept fehlt: " + id);
        JsonObject recipe = read(file);
        List<String> out = new ArrayList<>();
        if (recipe.has("key")) {
            for (Map.Entry<String, JsonElement> entry : recipe.getAsJsonObject("key").entrySet()) {
                out.add(entry.getValue().getAsString());
            }
        } else {
            recipe.getAsJsonArray("ingredients").forEach(e -> out.add(e.getAsString()));
        }
        return out;
    }

    private static Set<String> leaves(String id, Set<String> path) throws IOException {
        assertTrue(path.add(id), "Rezeptkreis über " + path);
        Set<String> out = new TreeSet<>();
        for (String ingredient : ingredients(id)) {
            if (ingredient.startsWith("propwash:")) {
                out.addAll(leaves(ingredient.substring("propwash:".length()), new LinkedHashSet<>(path)));
            } else {
                out.add(ingredient);
            }
        }
        return out;
    }

    private static Set<String> materialKeys() {
        Set<String> keys = new HashSet<>();
        for (CraftingMaterial material : CraftingMaterial.values()) {
            keys.add(material.key());
        }
        return keys;
    }

    private static Set<String> lootableItems() {
        Set<String> items = new HashSet<>(materialKeys());
        for (PartCategory category : PartCategory.values()) {
            for (PartInfo part : category.parts()) {
                items.add(part.key());
            }
        }
        items.addAll(List.of("drone", "remote", "goggles_analog", "goggles_digital", "battery_charger", "bando_map"));
        return items;
    }

    @Test
    void materialsHaveRecipeUnlockIconAndTexts() throws IOException {
        Set<String> unlocked = new HashSet<>();
        try (Stream<Path> files = Files.walk(data().resolve("advancement/recipes"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                read(file).getAsJsonObject("rewards").getAsJsonArray("recipes").forEach(e -> unlocked.add(e.getAsString()));
            }
        }
        Map<String, JsonObject> languages = new HashMap<>();
        for (String language : List.of("de_de", "en_us")) {
            languages.put(language, read(common().resolve("src/lang/content/" + language + ".json")));
        }
        for (CraftingMaterial material : CraftingMaterial.values()) {
            String key = material.key();
            assertEquals("propwash:" + key, read(data().resolve("recipe/" + key + ".json")).getAsJsonObject("result").get("id").getAsString());
            assertTrue(unlocked.contains("propwash:" + key), "kein Freischalt-Fortschritt für " + key);
            JsonObject definition = read(assets().resolve("items/" + key + ".json"));
            assertEquals("propwash:item/" + key, definition.getAsJsonObject("model").get("model").getAsString(), key);
            JsonObject model = read(assets().resolve("models/item/" + key + ".json"));
            assertEquals("propwash:item/" + key, model.getAsJsonObject("textures").get("layer0").getAsString(), key);
            BufferedImage texture = ImageIO.read(assets().resolve("textures/item/" + key + ".png").toFile());
            assertNotNull(texture, key);
            assertEquals(16, texture.getWidth(), key);
            assertEquals(16, texture.getHeight(), key);
            for (Map.Entry<String, JsonObject> language : languages.entrySet()) {
                JsonObject lang = language.getValue();
                for (String text : List.of("item.propwash." + key, ItemHelp.PREFIX + key + ".summary", ItemHelp.PREFIX + key + ".what.1",
                        ItemHelp.PREFIX + key + ".use.1", ItemHelp.PREFIX + key + ".fits.1")) {
                    assertTrue(lang.has(text), language.getKey() + " fehlt " + text);
                }
            }
        }
        for (JsonObject lang : languages.values()) {
            assertTrue(lang.has(MaterialItem.SECTION_INGREDIENT_FOR));
            assertTrue(lang.has(MaterialItem.SHARED_PREFIX + ".use.1"));
        }
    }

    @Test
    void recipesOnlyUseVanillaItemsAndCraftableMaterials() throws IOException {
        Set<String> materials = materialKeys();
        try (Stream<Path> files = Files.list(data().resolve("recipe"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String id = file.getFileName().toString().replace(".json", "");
                for (String ingredient : ingredients(id)) {
                    if (ingredient.startsWith("propwash:")) {
                        String material = ingredient.substring("propwash:".length());
                        assertTrue(materials.contains(material), id + " nutzt " + ingredient + ", das kein Zwischenteil ist");
                        assertTrue(Files.exists(data().resolve("recipe/" + material + ".json")), material + " hat kein Rezept");
                    } else {
                        assertTrue(ingredient.startsWith("minecraft:") || ingredient.startsWith("#minecraft:"), id + ": " + ingredient);
                    }
                }
                leaves(id, new LinkedHashSet<>());
            }
        }
    }

    @Test
    void entryGearNeedsOnlyEarlyGameMaterials() throws IOException {
        for (String id : ENTRY_ITEMS) {
            Set<String> leaves = leaves(id, new LinkedHashSet<>());
            Set<String> late = new TreeSet<>(leaves);
            late.removeAll(EARLY_GAME);
            assertTrue(late.isEmpty(), id + " braucht im Überlebensmodus zu früh " + late);
        }
    }

    @Test
    void highEndPartsNeedRareMaterials() throws IOException {
        for (String id : HIGH_END) {
            Set<String> leaves = leaves(id, new LinkedHashSet<>());
            assertTrue(leaves.stream().anyMatch(RARE::contains), id + " braucht kein seltenes Material: " + leaves);
        }
        assertTrue(leaves("digital_hd", new LinkedHashSet<>()).contains("minecraft:echo_shard"), "HD-Video ohne Echo-Splitter");
        assertTrue(leaves("m2306_1860", new LinkedHashSet<>()).contains("minecraft:quartz"), "Race-Motor ohne Quarz-Lager");
    }

    @Test
    void chestLootMatchesNeoForgeModifiersAndInjectTables() throws IOException {
        Path modifiers = neoforge().resolve("src/main/resources/data/propwash/loot_modifiers");
        Set<Path> expectedFiles = new HashSet<>();
        for (ChestLoot.Injection injection : ChestLoot.injections()) {
            assertEquals("minecraft", injection.target().identifier().getNamespace());
            Path file = modifiers.resolve(injection.target().identifier().getPath() + ".json");
            expectedFiles.add(file.normalize());
            assertTrue(Files.exists(file), "NeoForge-Loot-Modifier fehlt: " + file);
            JsonObject modifier = read(file);
            assertEquals("neoforge:add_table", modifier.get("type").getAsString(), file.toString());
            JsonObject condition = modifier.getAsJsonObject("condition");
            assertEquals("neoforge:loot_table_id", condition.get("type").getAsString(), file.toString());
            assertEquals(injection.target().identifier().toString(), condition.get("loot_table_id").getAsString(), file.toString());
            assertEquals(injection.table().identifier().toString(), modifier.get("table").getAsString(), file.toString());
        }
        try (Stream<Path> files = Files.walk(modifiers)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                assertTrue(expectedFiles.contains(file.normalize()), "Loot-Modifier ohne Eintrag in ChestLoot: " + file);
            }
        }
        Set<String> tables = new HashSet<>();
        ChestLoot.tables().forEach(table -> tables.add(table.identifier().getPath()));
        try (Stream<Path> files = Files.list(data().resolve("loot_table/inject"))) {
            for (Path file : files.toList()) {
                String name = ChestLoot.INJECT_PREFIX + file.getFileName().toString().replace(".json", "");
                assertTrue(tables.contains(name), "Beutetabelle ohne Truhe: " + name);
            }
        }
        for (String table : tables) {
            assertTrue(Files.exists(data().resolve("loot_table/" + table + ".json")), "Beutetabelle fehlt: " + table);
        }
    }

    @Test
    void injectedLootIsRareAndWellFormed() throws IOException {
        Set<String> lootable = lootableItems();
        Set<String> dropped = new HashSet<>();
        for (var table : ChestLoot.tables()) {
            String name = table.identifier().toString();
            JsonObject loot = read(data().resolve("loot_table/" + table.identifier().getPath() + ".json"));
            assertEquals("minecraft:chest", loot.get("type").getAsString(), name);
            JsonArray pools = loot.getAsJsonArray("pools");
            assertEquals(1, pools.size(), name);
            JsonObject pool = pools.get(0).getAsJsonObject();
            assertEquals(1, pool.get("rolls").getAsInt(), name);
            int total = 0;
            int nothing = 0;
            for (JsonElement element : pool.getAsJsonArray("entries")) {
                JsonObject entry = element.getAsJsonObject();
                int weight = entry.get("weight").getAsInt();
                assertTrue(weight > 0, name);
                total += weight;
                if (entry.get("type").getAsString().equals("minecraft:empty")) {
                    nothing += weight;
                    continue;
                }
                assertEquals("minecraft:item", entry.get("type").getAsString(), name);
                String item = entry.get("name").getAsString();
                assertTrue(item.startsWith("propwash:") && lootable.contains(item.substring("propwash:".length())), name + ": " + item);
                String key = item.substring("propwash:".length());
                dropped.add(key);
                if (TOP_TIER.contains(key)) {
                    assertTrue(TOP_TIER_TABLES.contains(name), key + " ist zu leicht zu finden in " + name);
                }
                checkComponents(name, key, entry);
            }
            assertTrue(nothing > 0, name + " ohne Leer-Eintrag");
            double chance = 1.0 - nothing / (double) total;
            assertTrue(chance <= MAX_FIND_CHANCE, name + ": Fundchance " + chance);
        }
        for (CraftingMaterial material : CraftingMaterial.values()) {
            assertTrue(dropped.contains(material.key()), material.key() + " ist in keiner Truhe zu finden");
        }
        assertTrue(dropped.contains("drone"), "keine fertige Drohne in Truhen");
        assertFalse(dropped.contains("goggles_creative"));
        assertFalse(dropped.contains("creative_fc"));
    }

    private static void checkComponents(String table, String key, JsonObject entry) {
        Map<String, JsonElement> components = new HashMap<>();
        if (entry.has("modifier")) {
            JsonElement modifier = entry.get("modifier");
            List<JsonObject> functions = new ArrayList<>();
            if (modifier.isJsonArray()) {
                modifier.getAsJsonArray().forEach(e -> functions.add(e.getAsJsonObject()));
            } else if (modifier.isJsonPrimitive()) {
                assertEquals("propwash:" + key, modifier.getAsString(), table + ": " + key + " mit fremder Funktion");
            } else {
                functions.add(modifier.getAsJsonObject());
            }
            for (JsonObject function : functions) {
                if (function.get("type").getAsString().equals("minecraft:set_components")) {
                    function.getAsJsonObject("components").entrySet().forEach(e -> components.put(e.getKey(), e.getValue()));
                }
            }
        }
        boolean battery = false;
        for (BatteryType type : BatteryType.values()) {
            battery |= type.key().equals(key);
        }
        if (battery || key.equals("drone")) {
            assertTrue(components.containsKey("propwash:battery_charge"), table + ": " + key + " ohne Ladestand");
            assertEquals(ChestLoot.FOUND_CHARGE, components.get("propwash:battery_charge").getAsFloat(), 1e-6, table + ": " + key);
        }
        if (key.equals("drone")) {
            JsonObject build = components.get("propwash:drone_build").getAsJsonObject();
            DroneBuild whoop = DroneBuild.WHOOP;
            assertEquals(whoop.frame().key(), build.get("frame").getAsString());
            assertEquals(whoop.stack().key(), build.get("stack").getAsString());
            assertEquals(whoop.motor().key(), build.get("motor").getAsString());
            assertEquals(whoop.prop().key(), build.get("prop").getAsString());
            assertEquals(whoop.video().key(), build.get("video").getAsString());
            assertEquals(whoop.battery().key(), build.get("battery").getAsString());
            assertEquals(DronePresets.translationKey(whoop), components.get("minecraft:item_name").getAsJsonObject().get("translate").getAsString());
        }
    }
}
