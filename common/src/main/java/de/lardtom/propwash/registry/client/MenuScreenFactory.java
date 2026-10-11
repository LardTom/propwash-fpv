package de.lardtom.propwash.registry.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

@FunctionalInterface
public interface MenuScreenFactory<M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> {
    U create(M menu, Inventory inventory, Component title);
}
