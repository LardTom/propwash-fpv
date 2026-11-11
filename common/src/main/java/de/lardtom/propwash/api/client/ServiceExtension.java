package de.lardtom.propwash.api.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public interface ServiceExtension {
    int MAX_VISIBLE_BUTTONS = 3;

    Identifier id();

    Component label();

    default boolean visible(Entity drone) {
        return true;
    }

    void open(Entity drone, @Nullable Screen parent);
}
