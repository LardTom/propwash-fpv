package de.lardtom.propwash.content;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

public final class RecycleRecipes {
    private static final Map<Item, Optional<Recycling.Materials>> CACHE = new HashMap<>();
    private static @Nullable RecipeManager cachedFor;

    private RecycleRecipes() {
    }

    public static synchronized Optional<Recycling.Materials> find(RecipeManager recipes, Item item) {
        if (cachedFor != recipes) {
            CACHE.clear();
            cachedFor = recipes;
        }
        return CACHE.computeIfAbsent(item, key -> scan(recipes.getRecipes(), key).map(Found::materials));
    }

    public static Optional<Found> scan(Collection<RecipeHolder<?>> holders, Item item) {
        List<RecipeHolder<?>> sorted = new ArrayList<>(holders);
        sorted.sort(Comparator.comparing(holder -> holder.id().identifier().toString()));
        for (RecipeHolder<?> holder : sorted) {
            if (!(holder.value() instanceof CraftingRecipe crafting)) {
                continue;
            }
            OptionalInt count = resultCount(crafting, item);
            if (count.isEmpty() || count.getAsInt() < 1) {
                continue;
            }
            List<Identifier> ingredients = ingredients(crafting, item);
            if (!ingredients.isEmpty()) {
                return Optional.of(new Found(holder.id().identifier(), Recycling.Materials.of(ingredients, count.getAsInt())));
            }
        }
        return Optional.empty();
    }

    static OptionalInt resultCount(CraftingRecipe recipe, Item item) {
        for (RecipeDisplay display : recipe.display()) {
            SlotDisplay result = switch (display) {
                case ShapedCraftingRecipeDisplay shaped -> shaped.result();
                case ShapelessCraftingRecipeDisplay shapeless -> shapeless.result();
                default -> null;
            };
            if (result instanceof SlotDisplay.ItemStackSlotDisplay stack && stack.stack().item().value() == item) {
                return OptionalInt.of(stack.stack().count());
            }
            if (result instanceof SlotDisplay.ItemSlotDisplay single && single.item().value() == item) {
                return OptionalInt.of(1);
            }
        }
        return OptionalInt.empty();
    }

    static List<Identifier> ingredients(CraftingRecipe recipe, Item result) {
        PlacementInfo placement = recipe.placementInfo();
        if (placement.isImpossibleToPlace()) {
            return List.of();
        }
        List<Identifier> out = new ArrayList<>();
        for (Ingredient ingredient : placement.ingredients()) {
            Optional<Holder<Item>> first = ingredient.items().findFirst();
            if (first.isEmpty() || ingredient.items().anyMatch(holder -> holder.value() == result)) {
                return List.of();
            }
            Item item = first.get().value();
            if (item.getCraftingRemainder() == null) {
                out.add(BuiltInRegistries.ITEM.getKey(item));
            }
        }
        return out;
    }

    public record Found(Identifier recipe, Recycling.Materials materials) {
    }
}
