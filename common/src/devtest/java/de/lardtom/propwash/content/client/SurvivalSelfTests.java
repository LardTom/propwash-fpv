package de.lardtom.propwash.content.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.ChestLoot;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.content.CraftingMaterial;
import de.lardtom.propwash.content.DronePresets;
import de.lardtom.propwash.content.MaterialItem;
import de.lardtom.propwash.content.PropRepairRecipe;
import de.lardtom.propwash.content.TooltipShift;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.PartCategory;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.AtlasIds;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class SurvivalSelfTests {
    private static final String FUTURE = "future";
    private static final String RESULT = "result";
    private static final int INJECT_ROLLS = 3000;
    private static final int TARGET_ROLLS = 400;
    private static final double MIN_FIND_RATE = 0.05;
    private static final double MAX_FIND_RATE = 0.7;
    private static final float CHARGE_TOLERANCE = 0.001F;
    private static final List<Identifier> OTHER_CRAFTABLE = List.of(ContentIds.DRONE_WORKBENCH, ContentIds.BATTERY_CHARGER,
            ContentIds.GOGGLES_ANALOG, ContentIds.GOGGLES_DIGITAL, ContentIds.REMOTE, ContentIds.GATE_SMALL, ContentIds.GATE_MEDIUM,
            ContentIds.GATE_LARGE, ContentIds.RACE_FLAG, ContentIds.SLALOM_POLE, ContentIds.DIVE_CUBE, ContentIds.FREEFORM_GATE,
            ContentIds.GATE_POLE, ContentIds.COURSE_TOOL, ContentIds.SIGNAL_REPEATER);
    private static final List<Identifier> NOT_CRAFTABLE = List.of(ContentIds.GOGGLES_CREATIVE, ContentIds.DRONE, StackType.CREATIVE_FC.id());

    private SurvivalSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("survival: Zwischenteile mit Modell, Name und Tooltip", 40, ctx -> materialsStep());
        SelfTest.clientStep("survival: jedes Rezept lädt, passt eindeutig und liefert sein Item", 200,
                ctx -> serverStep(ctx, SurvivalSelfTests::checkRecipes));
        SelfTest.clientStep("survival: Truhen-Beute in allen Strukturen", 600, ctx -> serverStep(ctx, SurvivalSelfTests::checkLoot));
        SurvivalRecipeBookSelfTest.register();
    }

    public static List<Identifier> craftableIds() {
        List<Identifier> ids = new ArrayList<>();
        for (CraftingMaterial material : CraftingMaterial.values()) {
            ids.add(Constants.id(material.key()));
        }
        for (PartCategory category : PartCategory.values()) {
            for (PartInfo part : category.parts()) {
                if (part.builtin() && !(part instanceof StackType stack && stack.creative())) {
                    ids.add(part.id());
                }
            }
        }
        ids.addAll(OTHER_CRAFTABLE);
        return ids;
    }

    private static StepResult materialsStep() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return StepResult.pending();
        }
        List<String> problems = new ArrayList<>();
        TextureAtlas atlas = mc.getAtlasManager().getAtlasOrThrow(AtlasIds.ITEMS);
        Language language = Language.getInstance();
        Item.TooltipContext context = Item.TooltipContext.of(mc.level);
        List<ItemStack> tab = ContentTab.contents();
        for (CraftingMaterial material : CraftingMaterial.values()) {
            Identifier id = Constants.id(material.key());
            Optional<Item> item = ContentIds.item(id);
            if (item.isEmpty() || !(item.get() instanceof MaterialItem)) {
                problems.add(id + " nicht als Zwischenteil registriert");
                continue;
            }
            if (mc.getModelManager().getItemModel(id) instanceof MissingItemModel) {
                problems.add(id + " ohne Item-Modell");
            }
            if (atlas.getSprite(Constants.id("item/" + material.key())) == atlas.missingSprite()) {
                problems.add(id + " ohne Textur im Item-Atlas");
            }
            if (!language.has("item." + Constants.MOD_ID + "." + material.key())) {
                problems.add(id + " ohne Namen");
            }
            if (tab.stream().noneMatch(stack -> stack.is(item.get()))) {
                problems.add(id + " fehlt im Creative-Tab");
            }
            int ingredientFor = ingredientLines(item.get(), context);
            if (ingredientFor < 1) {
                problems.add(id + ": Shift-Tooltip ohne Abschnitt „Zutat für“");
            }
        }
        return StepResult.check(problems.isEmpty(), String.join("; ", problems));
    }

    private static int ingredientLines(Item item, Item.TooltipContext context) {
        List<Component> lines = new ArrayList<>();
        TooltipShift.override(true);
        try {
            item.appendHoverText(new ItemStack(item), context, TooltipDisplay.DEFAULT, lines::add, TooltipFlag.NORMAL);
        } finally {
            TooltipShift.override(null);
        }
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getContents() instanceof TranslatableContents translatable
                    && MaterialItem.SECTION_INGREDIENT_FOR.equals(translatable.getKey())) {
                return lines.size() - i - 1;
            }
        }
        return 0;
    }

    @FunctionalInterface
    private interface ServerCheck {
        List<String> run(MinecraftServer server);
    }

    @SuppressWarnings("unchecked")
    private static StepResult serverStep(SelfTest.StepContext ctx, ServerCheck check) {
        if (ctx.tick() == 0) {
            AtomicReference<List<String>> result = new AtomicReference<>();
            ctx.state().put(RESULT, result);
            ctx.state().put(FUTURE, ctx.onServer(server -> result.set(check.run(server))));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Prüfung fehlgeschlagen: " + future.exceptionNow());
        }
        List<String> problems = ((AtomicReference<List<String>>) ctx.state().get(RESULT)).get();
        return StepResult.check(problems.isEmpty(), String.join("; ", problems));
    }

    private static List<String> checkRecipes(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        RecipeManager recipes = server.getRecipeManager();
        ServerLevel level = server.overworld();
        Map<ResourceKey<Recipe<?>>, Identifier> unlockedBy = new HashMap<>();
        for (AdvancementHolder advancement : server.getAdvancements().getAllAdvancements()) {
            for (ResourceKey<Recipe<?>> recipe : advancement.value().rewards().recipes()) {
                unlockedBy.put(recipe, advancement.id());
            }
        }
        Set<Identifier> expected = new LinkedHashSet<>(craftableIds());
        for (Identifier id : expected) {
            ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
            Optional<RecipeHolder<?>> holder = recipes.byKey(key);
            if (holder.isEmpty()) {
                problems.add("Rezept fehlt: " + id);
                continue;
            }
            if (!(holder.get().value() instanceof CraftingRecipe crafting)) {
                problems.add(id + " ist kein Werkbank-Rezept");
                continue;
            }
            CraftingInput input = sampleInput(crafting);
            if (input == null) {
                problems.add(id + ": unerwarteter Rezepttyp " + crafting.getClass().getSimpleName());
                continue;
            }
            Optional<RecipeHolder<CraftingRecipe>> found = recipes.getRecipeFor(RecipeType.CRAFTING, input, level);
            if (found.isEmpty()) {
                problems.add(id + ": die eigenen Zutaten ergeben kein Rezept");
            } else if (!found.get().id().equals(key)) {
                problems.add(id + ": dieselben Zutaten ergeben " + found.get().id().identifier());
            }
            ItemStack result = crafting.assemble(input);
            if (result.isEmpty() || !ContentIds.is(result, id)) {
                problems.add(id + " liefert " + result);
            } else if (Batteries.isBattery(result) && !Batteries.isFull(Batteries.charge(result))) {
                problems.add(id + " kommt nicht voll geladen aus dem Rezept");
            }
            Identifier unlock = unlockedBy.get(key);
            if (unlock == null || !Constants.MOD_ID.equals(unlock.getNamespace())) {
                problems.add(id + ": kein Freischalt-Fortschritt fürs Rezeptbuch");
            }
        }
        for (Identifier id : NOT_CRAFTABLE) {
            if (recipes.byKey(ResourceKey.create(Registries.RECIPE, id)).isPresent()) {
                problems.add(id + " darf kein Rezept haben");
            }
        }
        for (RecipeHolder<?> holder : recipes.getRecipes()) {
            Identifier id = holder.id().identifier();
            if (Constants.MOD_ID.equals(id.getNamespace()) && !expected.contains(id) && !holder.id().equals(PropRepairRecipe.KEY)) {
                problems.add("unerwartetes Rezept " + id);
            }
        }
        return problems;
    }

    private static @Nullable CraftingInput sampleInput(CraftingRecipe recipe) {
        List<ItemStack> items = new ArrayList<>();
        if (recipe instanceof ShapedRecipe shaped) {
            for (Optional<Ingredient> ingredient : shaped.getIngredients()) {
                items.add(ingredient.map(SurvivalSelfTests::sample).orElse(ItemStack.EMPTY));
            }
            return CraftingInput.of(shaped.getWidth(), shaped.getHeight(), items);
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            List<Ingredient> ingredients = shapeless.placementInfo().ingredients();
            for (int i = 0; i < 9; i++) {
                items.add(i < ingredients.size() ? sample(ingredients.get(i)) : ItemStack.EMPTY);
            }
            return CraftingInput.of(3, 3, items);
        }
        return null;
    }

    private static ItemStack sample(Ingredient ingredient) {
        return ingredient.items().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static List<String> checkLoot(MinecraftServer server) {
        Set<String> problems = new LinkedHashSet<>();
        ServerLevel level = server.overworld();
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
        Map<ResourceKey<LootTable>, Set<Identifier>> droppable = new HashMap<>();
        for (ResourceKey<LootTable> table : ChestLoot.tables()) {
            LootTable loot = server.reloadableRegistries().getLootTable(table);
            if (loot == LootTable.EMPTY) {
                problems.add("Beutetabelle fehlt: " + table.identifier());
                continue;
            }
            Set<Identifier> declared = declaredItems(server, table, problems);
            Set<Identifier> seen = new TreeSet<>();
            for (int seed = 1; seed <= INJECT_ROLLS; seed++) {
                for (ItemStack stack : loot.getRandomItems(params, seed)) {
                    seen.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                    checkStack(table, stack, problems);
                }
            }
            if (declared.isEmpty() || !seen.equals(declared)) {
                problems.add(table.identifier() + ": eingetragen " + declared + ", gewürfelt " + seen);
            }
            droppable.put(table, seen);
        }
        for (ChestLoot.Injection injection : ChestLoot.injections()) {
            LootTable target = server.reloadableRegistries().getLootTable(injection.target());
            if (target == LootTable.EMPTY) {
                problems.add("Truhen-Tabelle fehlt: " + injection.target().identifier());
                continue;
            }
            Set<Identifier> seen = new TreeSet<>();
            int finds = 0;
            for (int seed = 1; seed <= TARGET_ROLLS; seed++) {
                boolean found = false;
                for (ItemStack stack : target.getRandomItems(params, seed)) {
                    Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (Constants.MOD_ID.equals(id.getNamespace())) {
                        found = true;
                        seen.add(id);
                        checkStack(injection.target(), stack, problems);
                    }
                }
                if (found) {
                    finds++;
                }
            }
            double rate = finds / (double) TARGET_ROLLS;
            Set<Identifier> allowed = droppable.getOrDefault(injection.table(), Set.of());
            if (finds == 0) {
                problems.add(injection.target().identifier() + ": keine Propwash-Beute in " + TARGET_ROLLS + " Truhen");
            } else if (!allowed.containsAll(seen)) {
                problems.add(injection.target().identifier() + ": Beute " + seen + " nicht aus " + injection.table().identifier());
            }
            if (rate < MIN_FIND_RATE || rate > MAX_FIND_RATE) {
                problems.add(String.format(Locale.ROOT, "%s: Fundrate %.1f %% außerhalb %.0f–%.0f %%", injection.target().identifier(),
                        rate * 100.0, MIN_FIND_RATE * 100.0, MAX_FIND_RATE * 100.0));
            }
        }
        return new ArrayList<>(problems);
    }

    private static void checkStack(ResourceKey<LootTable> table, ItemStack stack, Set<String> problems) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (ContentIds.DRONE.equals(id)) {
            DroneBuild build = stack.get(CoreComponents.DRONE_BUILD.get());
            Float charge = stack.get(CoreComponents.BATTERY_CHARGE.get());
            Component name = stack.get(DataComponents.ITEM_NAME);
            if (!DroneBuild.WHOOP.equals(build)) {
                problems.add(table.identifier() + ": Drohne ohne Whoop-Bauplan (" + build + ")");
            }
            if (charge == null || Math.abs(charge - ChestLoot.FOUND_CHARGE) > CHARGE_TOLERANCE) {
                problems.add(table.identifier() + ": Drohne mit Akkustand " + charge);
            }
            if (name == null || !(name.getContents() instanceof TranslatableContents translatable)
                    || !DronePresets.translationKey(DroneBuild.WHOOP).equals(translatable.getKey())) {
                problems.add(table.identifier() + ": Drohne ohne Preset-Namen");
            }
        } else if (Batteries.isBattery(stack) && Math.abs(Batteries.charge(stack) - ChestLoot.FOUND_CHARGE) > CHARGE_TOLERANCE) {
            problems.add(table.identifier() + ": " + id + " mit Ladestand " + Batteries.charge(stack) + " statt Lagerladung");
        }
    }

    private static Set<Identifier> declaredItems(MinecraftServer server, ResourceKey<LootTable> table, Set<String> problems) {
        Set<Identifier> items = new TreeSet<>();
        Identifier file = Identifier.fromNamespaceAndPath(table.identifier().getNamespace(), "loot_table/" + table.identifier().getPath() + ".json");
        Optional<Resource> resource = server.getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            problems.add("Datei fehlt: " + file);
            return items;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (JsonElement pool : json.getAsJsonArray("pools")) {
                for (JsonElement entry : pool.getAsJsonObject().getAsJsonArray("entries")) {
                    JsonObject object = entry.getAsJsonObject();
                    if ("minecraft:item".equals(object.get("type").getAsString())) {
                        items.add(Identifier.parse(object.get("name").getAsString()));
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            problems.add(file + " unlesbar: " + e.getMessage());
        }
        return items;
    }
}
