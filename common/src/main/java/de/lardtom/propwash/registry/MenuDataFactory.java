package de.lardtom.propwash.registry;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

@FunctionalInterface
public interface MenuDataFactory<T extends AbstractContainerMenu, D> {
    T create(int containerId, Inventory inventory, D data);
}
