package de.lardtom.propwash.drone.coating;

import com.mojang.serialization.MapCodec;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.core.NonNullList;
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

public final class WaterproofCoatingRecipe extends CustomRecipe {
    public static final String TYPE = "waterproof_coating";
    public static final ResourceKey<Recipe<?>> KEY = ResourceKey.create(Registries.RECIPE, Constants.id("special/" + TYPE));
    public static final WaterproofCoatingRecipe INSTANCE = new WaterproofCoatingRecipe();
    public static final MapCodec<WaterproofCoatingRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, WaterproofCoatingRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    public static final RegHolder<RecipeSerializer<WaterproofCoatingRecipe>> SERIALIZER = PropwashRegistries.recipeSerializer(TYPE,
            MAP_CODEC, STREAM_CODEC);

    private WaterproofCoatingRecipe() {
    }

    static void bootstrap() {
    }

    record Parts(int drone, int spray) {
    }

    static Parts find(CraftingInput input) {
        if (input.ingredientCount() != 2) {
            return null;
        }
        int drone = -1;
        int spray = -1;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(DroneModule.DRONE_ITEM.get()) && drone < 0) {
                drone = i;
            } else if (stack.is(DroneModule.WATERPROOF_SPRAY.get()) && spray < 0) {
                spray = i;
            } else {
                return null;
            }
        }
        if (drone < 0 || spray < 0) {
            return null;
        }
        ItemStack droneStack = input.getItem(drone);
        if (CoatingComponents.coated(droneStack) || droneStack.get(CoreComponents.DRONE_BUILD.get()) == null
                || CoatingRules.usedUp(input.getItem(spray).getDamageValue())) {
            return null;
        }
        return new Parts(drone, spray);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return find(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        Parts parts = find(input);
        if (parts == null) {
            return ItemStack.EMPTY;
        }
        ItemStack drone = input.getItem(parts.drone()).copyWithCount(1);
        CoatingComponents.coat(drone, drone.get(CoreComponents.DRONE_BUILD.get()), true);
        return drone;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        Parts parts = find(input);
        if (parts == null) {
            return remaining;
        }
        ItemStack spray = input.getItem(parts.spray()).copyWithCount(1);
        int damage = CoatingRules.damageAfterUse(spray.getDamageValue());
        if (!CoatingRules.usedUp(damage)) {
            spray.setDamageValue(damage);
            remaining.set(parts.spray(), spray);
        }
        return remaining;
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.EQUIPMENT;
    }

    @Override
    public RecipeSerializer<WaterproofCoatingRecipe> getSerializer() {
        return SERIALIZER.get();
    }
}
