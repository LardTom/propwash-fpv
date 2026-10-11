package de.lardtom.propwash.registry;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

@FunctionalInterface
public interface SimpleMenuFactory<T extends AbstractContainerMenu> {
    T create(int containerId, Inventory inventory);
}
