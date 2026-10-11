package de.lardtom.propwash.registry.client;

import net.minecraft.client.gui.screens.Screen;

@FunctionalInterface
public interface ConfigScreenFactory {
    Screen create(Screen parent);
}
