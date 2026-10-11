package de.lardtom.propwash.ui.client.theme;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jspecify.annotations.Nullable;

public interface WidgetViews {
    @Nullable ScreenRectangle widgetView(AbstractWidget widget);
}
