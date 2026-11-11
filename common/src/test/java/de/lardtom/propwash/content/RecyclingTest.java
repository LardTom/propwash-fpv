package de.lardtom.propwash.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.api.part.RecycleYield;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.core.PartCategory;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class RecyclingTest {
    private static final int[] TIERS = {Recycling.FULL, Recycling.HALF, Recycling.QUARTER};
    private static final double EPSILON = 1.0E-9;

    @Test
    void conditionPicksOneOfThreeTiers() {
        assertEquals(Recycling.FULL, Recycling.tier(PartWear.NEW));
        assertEquals(Recycling.HALF, Recycling.tier(PartWear.NEW - PartWear.STEP));
        assertEquals(Recycling.HALF, Recycling.tier(0.9F));
        assertEquals(Recycling.HALF, Recycling.tier(0.6F));
        assertEquals(Recycling.HALF, Recycling.tier(0.595F));
        assertEquals(Recycling.QUARTER, Recycling.tier(0.594F));
        assertEquals(Recycling.QUARTER, Recycling.tier(0.59F));
        assertEquals(Recycling.QUARTER, Recycling.tier(0.3F));
        assertEquals(Recycling.QUARTER, Recycling.tier(PartWear.STEP));
        assertEquals(Recycling.QUARTER, Recycling.tier(0.0F));
        for (int step = 0; step <= PartWear.STEPS; step++) {
            float condition = step / (float) PartWear.STEPS;
            int percent = PartCondition.percent(condition);
            int expected = percent == 100 ? Recycling.FULL : percent >= Recycling.HALF_FROM ? Recycling.HALF : Recycling.QUARTER;
            assertEquals(expected, Recycling.tier(condition), "Zustand " + condition + " = " + percent + " %");
        }
    }

    @Test
    void everyCorePartExceptTheCreativeControllerHasACraftingRecipe() {
        Map<String, Recipe> recipes = coreRecipes();
        for (PartCategory category : PartCategory.values()) {
            for (PartInfo part : category.parts()) {
                if (part instanceof StackType stack && stack.creative()) {
                    assertFalse(recipes.containsKey(part.id().toString()), part.key());
                } else {
                    assertTrue(recipes.containsKey(part.id().toString()), "kein Rezept für " + part.key());
                }
            }
        }
    }

    @Test
    void undamagedPartsGiveBackExactlyTheIngredientsPerPiece() {
        for (Recipe recipe : partRecipes()) {
            Recycling.Materials materials = Recycling.Materials.of(recipe.ingredients(), recipe.count());
            Map<Identifier, Integer> used = count(recipe.ingredients());
            assertEquals(used.size(), materials.entries().size(), recipe.id());
            for (int i = 0; i < materials.entries().size(); i++) {
                Recycling.Material entry = materials.entries().get(i);
                int needed = used.get(entry.item());
                assertEquals(needed, entry.count(), recipe.id() + " " + entry.item());
                assertEquals(needed / (double) recipe.count(), materials.expected(i, 1, Recycling.FULL), EPSILON, recipe.id() + " je Stück");
                assertTrue(materials.exact(i, recipe.count(), Recycling.FULL), recipe.id());
                assertEquals(needed, materials.least(i, recipe.count(), Recycling.FULL), recipe.id() + " ganze Ergebnismenge");
                assertEquals(needed, materials.most(i, recipe.count(), Recycling.FULL), recipe.id() + " ganze Ergebnismenge");
            }
        }
    }

    @Test
    void craftingAndRecyclingNeverMultipliesAnIngredient() {
        for (Recipe recipe : partRecipes()) {
            Recycling.Materials materials = Recycling.Materials.of(recipe.ingredients(), recipe.count());
            Map<Identifier, Integer> used = count(recipe.ingredients());
            for (int tier : TIERS) {
                for (int pieces = 1; pieces <= 64; pieces++) {
                    for (int i = 0; i < materials.entries().size(); i++) {
                        Recycling.Material entry = materials.entries().get(i);
                        double spent = used.get(entry.item()) * pieces / (double) recipe.count();
                        double back = materials.expected(i, pieces, tier);
                        assertTrue(back <= spent + EPSILON, recipe.id() + " " + entry.item() + " bei " + tier + " %, " + pieces + " Stück: " + back + " > "
                                + spent);
                        assertEquals(spent * tier / 100.0, back, EPSILON, recipe.id() + " " + entry.item() + " bei " + tier + " %");
                        assertTrue(materials.least(i, pieces, tier) <= back && back <= materials.most(i, pieces, tier), recipe.id());
                        assertTrue(materials.most(i, pieces, tier) - materials.least(i, pieces, tier) <= 1, recipe.id());
                    }
                }
            }
        }
    }

    @Test
    void everyIngredientShrinksByTheSameShareSoNoIngredientCanBeSwapped() {
        for (Recipe recipe : partRecipes()) {
            Recycling.Materials materials = Recycling.Materials.of(recipe.ingredients(), recipe.count());
            for (int tier : TIERS) {
                for (int i = 0; i < materials.entries().size(); i++) {
                    double perPiece = materials.entries().get(i).count() / (double) recipe.count();
                    assertEquals(tier / 100.0, materials.expected(i, 1, tier) / perPiece, EPSILON, recipe.id());
                }
            }
        }
    }

    @Test
    void rollsGiveTheWholeAmountFixedAndTheRestByChance() {
        Recycling.Materials motor = Recycling.Materials.of(List.of(id("minecraft:iron_ingot"), Constants.id("copper_coil"), id("minecraft:iron_ingot"),
                Constants.id("copper_coil"), id("minecraft:redstone"), Constants.id("copper_coil")), 2);
        assertEquals(List.of(new Recycling.Material(id("minecraft:iron_ingot"), 2), new Recycling.Material(Constants.id("copper_coil"), 3),
                new Recycling.Material(id("minecraft:redstone"), 1)), motor.entries());
        RandomSource random = RandomSource.create(42L);
        long[] sums = new long[3];
        int rolls = 40000;
        for (int roll = 0; roll < rolls; roll++) {
            int[] amounts = motor.roll(1, Recycling.FULL, random);
            assertEquals(1, amounts[0]);
            assertTrue(amounts[1] == 1 || amounts[1] == 2, "Spulen " + amounts[1]);
            assertTrue(amounts[2] == 0 || amounts[2] == 1, "Redstone " + amounts[2]);
            for (int i = 0; i < 3; i++) {
                sums[i] += amounts[i];
            }
        }
        assertEquals(1.0, sums[0] / (double) rolls, EPSILON);
        assertEquals(1.5, sums[1] / (double) rolls, 0.02);
        assertEquals(0.5, sums[2] / (double) rolls, 0.02);
        int[] quarter = motor.roll(16, Recycling.QUARTER, random);
        assertEquals(4, quarter[0]);
        assertEquals(6, quarter[1]);
        assertEquals(2, quarter[2]);
        double half = 0.0;
        for (int roll = 0; roll < rolls; roll++) {
            half += motor.roll(1, Recycling.HALF, random)[1];
        }
        assertEquals(0.75, half / rolls, 0.02);
    }

    @Test
    void partsWithoutARecipeFallBackToTheirYieldOrTheCategory() {
        PartInfo motor = PartIndex.detached(motor("m4214_400").build());
        assertEquals(Optional.of(Recycling.Materials.single(Constants.id(CraftingMaterial.COPPER_COIL.key()), 1)), Recycling.fallback(motor));
        PartInfo rich = PartIndex.detached(motor("m4214_rich").basics(PartBasics.of(128.0)
                .withRecycle(new RecycleYield(Constants.id(CraftingMaterial.CARBON_PLATE.key()), 3))).build());
        assertEquals(Optional.of(Recycling.Materials.single(Constants.id(CraftingMaterial.CARBON_PLATE.key()), 3)), Recycling.fallback(rich));
        Recycling.Materials three = Recycling.fallback(rich).orElseThrow();
        assertEquals(3.0, three.expected(0, 1, Recycling.FULL), EPSILON);
        assertEquals(1.5, three.expected(0, 1, Recycling.HALF), EPSILON);
        assertEquals(0.75, three.expected(0, 1, Recycling.QUARTER), EPSILON);
        PartInfo scrap = PartIndex.detached(motor("m4214_scrap").basics(PartBasics.of(128.0).withRecycle(RecycleYield.NONE)).build());
        assertEquals(Optional.empty(), Recycling.fallback(scrap));
        PartInfo ring = PartIndex.detached(AccessoryDefinition.builder(id("recycletest:ring")).basics(PartBasics.of(8.0)
                        .withRecycle(new RecycleYield(Constants.id(CraftingMaterial.CIRCUIT_BOARD.key()), 1)))
                .slot(AccessorySlot.TOP).size(50.0, 4.0, 50.0).flightBatteryWatts(2.0).frontalArea(0.0002).leds(4).build());
        assertTrue(Recycling.fallback(ring).isPresent());
        PartInfo plainRing = PartIndex.detached(AccessoryDefinition.builder(id("recycletest:plain_ring")).basics(PartBasics.of(8.0))
                .slot(AccessorySlot.TOP).size(50.0, 4.0, 50.0).flightBatteryWatts(2.0).frontalArea(0.0002).leds(4).build());
        assertEquals(Optional.empty(), Recycling.fallback(plainRing));
        PartInfo prop = PartIndex.detached(PropDefinition.builder(id("recycletest:p13")).mass(38.0).size(330, 7.0, 2).coefficients(0.1, 0.04).build());
        assertEquals(Optional.empty(), Recycling.fallback(prop));
        PartInfo carbonProp = PartIndex.detached(PropDefinition.builder(id("recycletest:p13_carbon")).basics(PartBasics.of(38.0)
                        .withRecycle(new RecycleYield(Constants.id(CraftingMaterial.CARBON_PLATE.key()), 1)))
                .size(330, 7.0, 2).coefficients(0.1, 0.04).build());
        assertTrue(Recycling.fallback(carbonProp).isPresent());
        assertEquals(Optional.empty(), Recycling.fallback(StackType.CREATIVE_FC));
        assertEquals(Optional.empty(), Recycling.fallback(FrameType.WHOOP_65));
        assertEquals(Optional.empty(), Recycling.fallback(AccessoryType.LED_STRIP));
        assertEquals(Optional.empty(), Recycling.fallback(PartIndex.missing(PartKind.MOTOR, id("recycletest:gone"))));
    }

    @Test
    void materialsAreMergedPerItemInRecipeOrder() {
        Recycling.Materials frame = Recycling.Materials.of(List.of(Constants.id("carbon_plate"), Constants.id("carbon_plate"), id("minecraft:iron_ingot"),
                Constants.id("carbon_plate")), 1);
        assertEquals(List.of(new Recycling.Material(Constants.id("carbon_plate"), 3), new Recycling.Material(id("minecraft:iron_ingot"), 1)),
                frame.entries());
    }

    private static List<Recipe> partRecipes() {
        Map<String, Recipe> recipes = coreRecipes();
        List<Recipe> out = new ArrayList<>();
        for (PartCategory category : PartCategory.values()) {
            for (PartInfo part : category.parts()) {
                Recipe recipe = recipes.get(part.id().toString());
                if (recipe != null) {
                    out.add(recipe);
                }
            }
        }
        assertTrue(out.size() > 30, "nur " + out.size() + " Teile-Rezepte gefunden");
        return out;
    }

    private static Map<String, Recipe> coreRecipes() {
        Path dir = common().resolve("src/main/resources/data/propwash/recipe");
        Map<String, Recipe> out = new LinkedHashMap<>();
        try (var files = Files.list(dir)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                JsonObject json = read(file);
                String type = json.get("type").getAsString();
                if (!type.equals("minecraft:crafting_shaped") && !type.equals("minecraft:crafting_shapeless")) {
                    continue;
                }
                JsonObject result = json.getAsJsonObject("result");
                int count = result.has("count") ? result.get("count").getAsInt() : 1;
                List<Identifier> ingredients = new ArrayList<>();
                if (json.has("pattern")) {
                    JsonObject key = json.getAsJsonObject("key");
                    for (JsonElement row : json.getAsJsonArray("pattern")) {
                        for (char symbol : row.getAsString().toCharArray()) {
                            if (symbol != ' ') {
                                ingredients.add(ingredient(key.get(String.valueOf(symbol))));
                            }
                        }
                    }
                } else {
                    JsonArray list = json.getAsJsonArray("ingredients");
                    for (JsonElement entry : list) {
                        ingredients.add(ingredient(entry));
                    }
                }
                String id = result.get("id").getAsString();
                out.putIfAbsent(id, new Recipe(file.getFileName().toString(), ingredients, count));
            }
        } catch (IOException e) {
            fail("Rezepte nicht lesbar: " + e);
        }
        return out;
    }

    private static Identifier ingredient(JsonElement element) {
        String value = element.isJsonArray() ? element.getAsJsonArray().get(0).getAsString() : element.getAsString();
        return id(value.startsWith("#") ? value.substring(1) : value);
    }

    private static Map<Identifier, Integer> count(List<Identifier> ingredients) {
        Map<Identifier, Integer> out = new LinkedHashMap<>();
        for (Identifier ingredient : ingredients) {
            out.merge(ingredient, 1, Integer::sum);
        }
        return out;
    }

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

    private static MotorDefinition.Builder motor(String path) {
        return MotorDefinition.builder(id("recycletest:" + path)).mass(128.0).stator("4214").statorSize(42.5, 14.0).kv(400)
                .mount(id("recycletest:m42")).maxCurrent(60.0).resistance(0.045).cells(6, 12).propMount(id("recycletest:m5_shaft"));
    }

    private static Identifier id(String value) {
        return Identifier.parse(value);
    }

    private record Recipe(String id, List<Identifier> ingredients, int count) {
    }
}
