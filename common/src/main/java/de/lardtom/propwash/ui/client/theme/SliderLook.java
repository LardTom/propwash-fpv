package de.lardtom.propwash.ui.client.theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public record SliderLook(@Nullable Component label, Component value, int labelWidth, boolean modified) {
    public static SliderLook of(AbstractWidget slider) {
        if (slider instanceof LabeledValue labeled) {
            return new SliderLook(labeled.themeLabel(), labeled.themeValue(), labeled.themeLabelWidth(slider.getWidth()), labeled.themeModified());
        }
        Component shown = slider.getMessage();
        ThemeText.Split split = ThemeText.splitLabel(shown);
        if (split != null && Metrics.hasLabelColumn(slider.getWidth())) {
            Font font = Minecraft.getInstance().font;
            return new SliderLook(split.label(), split.value(),
                    ThemedWidgets.labelWidth(font, split.label(), slider.getWidth(), font.width(split.value()) + 8), false);
        }
        return new SliderLook(null, shown, 0, false);
    }

    public static Component valueOf(AbstractWidget slider) {
        if (slider instanceof LabeledValue labeled) {
            return labeled.themeValue();
        }
        ThemeText.Split split = ThemeText.splitLabel(slider.getMessage());
        return split != null ? split.value() : slider.getMessage();
    }
}
