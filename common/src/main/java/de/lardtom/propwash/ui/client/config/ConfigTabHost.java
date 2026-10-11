package de.lardtom.propwash.ui.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

public interface ConfigTabHost {
    <T extends AbstractWidget> T addWidget(T widget);

    Font font();

    Minecraft minecraft();

    Screen screen();

    void rebuild();

    void changed();

    default int contentShift() {
        return 0;
    }
}
