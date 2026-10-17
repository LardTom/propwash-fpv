package de.lardtom.propwash.api.accessory;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.OptionalDouble;

public interface AccessoryBehavior {
    int MAX_SLOTS = 9;

    default int slots() {
        return 0;
    }

    default boolean accepts(int slot, ItemStack stack) {
        return true;
    }

    default int maxStackSize(int slot, ItemStack stack) {
        return stack.getMaxStackSize();
    }

    default OptionalDouble payloadGrams(List<ItemStack> contents) {
        return OptionalDouble.empty();
    }

    default void onAction(AccessoryAction action) {
    }

    default void tick(AccessoryContext context) {
    }
}
