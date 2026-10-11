package de.lardtom.propwash.guide.client.screen;

import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.WidgetState;
import de.lardtom.propwash.ui.client.widget.PanelWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class GuideButton extends PanelWidget {
    private static final int ICON_CENTER = 9;
    private static final int ICON_SPACE = 18;

    private final Supplier<GuideDraw.Icon> icon;
    private final Supplier<Component> label;
    private final Runnable action;
    private final boolean primary;
    private final @Nullable Component caption;
    private Component shownLabel;

    public GuideButton(int x, int y, int width, int height, Supplier<GuideDraw.Icon> icon, Supplier<Component> label, boolean primary, Runnable action) {
        this(x, y, width, height, icon, label, primary, null, action);
    }

    public GuideButton(int x, int y, int width, int height, Supplier<GuideDraw.Icon> icon, Supplier<Component> label, boolean primary,
                       @Nullable Component caption, Runnable action) {
        super(x, y, width, height, label.get());
        this.icon = icon;
        this.label = label;
        this.action = action;
        this.primary = primary;
        this.caption = caption;
        this.shownLabel = label.get();
        this.setTooltip(ThemeText.tip(this.shownLabel));
    }

    public static int width(Font font, Component caption) {
        return ICON_SPACE + font.width(caption) + 6;
    }

    public void press() {
        this.action.run();
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        this.press();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.isActive() && event.isSelection()) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            this.press();
            return true;
        }
        return false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        Component current = this.label.get();
        if (!current.equals(this.shownLabel)) {
            this.shownLabel = current;
            this.setMessage(current);
            this.setTooltip(ThemeText.tip(current));
        }
        WidgetState state = this.state();
        ThemeDraw.ButtonStyle style = this.primary ? ThemeDraw.ButtonStyle.PRIMARY : ThemeDraw.ButtonStyle.NORMAL;
        ThemeDraw.button(g, this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, style, state, false);
        int color = this.primary ? ThemeDraw.buttonText(style, state) : (state.active() ? (state.hot() ? Palette.ACCENT : Palette.TEXT) : Palette.TEXT_DISABLED);
        if (this.caption == null) {
            GuideDraw.icon(g, this.icon.get(), this.getX() + this.width / 2, this.getY() + this.height / 2, color);
            return;
        }
        GuideDraw.icon(g, this.icon.get(), this.getX() + ICON_CENTER, this.getY() + this.height / 2, color);
        Font font = Minecraft.getInstance().font;
        ThemeText.fitted(g, font, this.caption, this.getX() + ICON_SPACE, Metrics.textY(this.getY(), this.height), this.width - ICON_SPACE - 4, color);
    }
}
