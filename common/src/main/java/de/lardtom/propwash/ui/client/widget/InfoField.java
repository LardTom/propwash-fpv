package de.lardtom.propwash.ui.client.widget;

import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedWidgets;
import de.lardtom.propwash.ui.client.theme.WidgetState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public class InfoField extends PanelWidget {
    private final @Nullable Component label;
    private Component value;
    private Component source;

    public InfoField(int x, int y, int width, int height, @Nullable Component label, Component value, Component source) {
        super(x, y, width, height, label == null ? value : label);
        this.label = label;
        this.value = value;
        this.source = source;
    }

    public void set(Component value, Component source) {
        this.value = value;
        this.source = source;
    }

    public Component value() {
        return this.value;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
        return false;
    }

    @Override
    public boolean isFocused() {
        return false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        WidgetState state = this.state();
        int labelWidth = this.label != null && Metrics.hasLabelColumn(this.width) ? ThemedWidgets.labelWidth(this.font(), this.label, this.width) : 0;
        ThemedWidgets.label(g, this.font(), this.label, this.getX(), this.getY(), this.height, labelWidth, state);
        int x0 = this.getX() + labelWidth;
        int x1 = this.getX() + this.width;
        int y1 = this.getY() + this.height;
        g.fill(x0, this.getY(), x1, y1, Palette.PANEL_HEADER);
        g.fill(x0, this.getY(), x0 + 2, y1, Palette.ACCENT_DIM);
        int textY = Metrics.textY(this.getY(), this.height);
        int sourceWidth = this.font().width(this.source);
        int valueRight = x1 - 4;
        if (sourceWidth > 0 && this.font().width(this.value) + sourceWidth + 14 <= x1 - x0) {
            ThemeText.textRight(g, this.font(), this.source, x1 - 4, textY, Palette.TEXT_MUTED);
            valueRight = x1 - 4 - sourceWidth - 6;
        }
        ThemeText.line(g, this.font(), this.value, x0 + 6, valueRight, textY, Palette.TEXT, ThemeText.Align.LEFT, false);
    }
}
