package de.lardtom.propwash.content;

import com.mojang.serialization.MapCodec;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class PropRepairRecipe extends CustomRecipe {
    public static final String TYPE = "prop_repair";
    public static final ResourceKey<Recipe<?>> KEY = ResourceKey.create(Registries.RECIPE, Constants.id("special/" + TYPE));
    public static final PropRepairRecipe INSTANCE = new PropRepairRecipe();
    public static final MapCodec<PropRepairRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, PropRepairRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    public static final RegHolder<RecipeSerializer<PropRepairRecipe>> SERIALIZER = PropwashRegistries.recipeSerializer(TYPE, MAP_CODEC,
            STREAM_CODEC);

    private PropRepairRecipe() {
    }

    static void bootstrap() {
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !PropRepairs.combine(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return PropRepairs.combine(input);
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.EQUIPMENT;
    }

    @Override
    public RecipeSerializer<PropRepairRecipe> getSerializer() {
        return SERIALIZER.get();
    }
}
