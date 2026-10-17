package de.lardtom.propwash.api.accessory;

import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public interface AccessoryRegistry {
    void register(Identifier accessoryId, AccessoryBehavior behavior);

    Optional<AccessoryBehavior> behavior(Identifier accessoryId);

    List<Identifier> registered();

    Optional<Identifier> accessoryAt(Entity drone, AccessorySlot slot);

    List<ItemStack> contents(Entity drone, AccessorySlot slot);

    Optional<AccessoryInventory> inventory(Entity drone, AccessorySlot slot);

    List<ItemStack> contents(ItemStack droneItem, AccessorySlot slot);

    double itemGrams(ItemStack stack);
}
