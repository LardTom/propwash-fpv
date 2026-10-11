package de.lardtom.propwash.ui.client.theme;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public interface LabeledValue {
    Component themeLabel();

    Component themeValue();

    default int themeLabelWidth(int width) {
        return ThemedWidgets.labelWidth(Minecraft.getInstance().font, this.themeLabel(), width,
                Minecraft.getInstance().font.width(this.themeValue()) + 8);
    }

    default boolean themeModified() {
        return false;
    }
}
