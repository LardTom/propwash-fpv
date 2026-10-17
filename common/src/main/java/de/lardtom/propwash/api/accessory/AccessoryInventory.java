package de.lardtom.propwash.api.accessory;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface AccessoryInventory {
    int size();

    ItemStack get(int slot);

    void set(int slot, ItemStack stack);

    ItemStack take(int slot, int count);

    int firstFilled();

    boolean isEmpty();

    List<ItemStack> contents();

    double payloadGrams();
}
