package de.lardtom.propwash.api.accessory;

import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
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

    default boolean triggerAction(Entity drone, int number, boolean pressed, ServerPlayer actor) {
        return false;
    }

    default List<ItemStack> cargo(Entity drone) {
        return List.of();
    }

    default boolean grabCargo(Entity drone, ServerPlayer actor) {
        return false;
    }

    default int releaseCargo(Entity drone, ServerPlayer actor) {
        return 0;
    }
}
