package de.lardtom.propwash.ui.client.widget;

import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedWidgets;
import de.lardtom.propwash.ui.client.theme.WidgetState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;

public class FlatButton extends PanelWidget {
    public enum Style {
        NORMAL,
        PRIMARY,
        DANGER,
        TAB
    }

    private final Runnable action;
    private final Style style;
    private final BooleanSupplier selected;
    private boolean placeholderWhenInactive;
    private @Nullable Identifier icon;

    public FlatButton(int x, int y, int width, int height, Component message, Style style, Runnable action) {
        this(x, y, width, height, message, style, () -> false, action);
    }

    public FlatButton(int x, int y, int width, int height, Component message, Style style, BooleanSupplier selected, Runnable action) {
        super(x, y, width, height, message);
        this.style = style;
        this.selected = selected;
        this.action = action;
    }

    public FlatButton icon(@Nullable Identifier sprite) {
        this.icon = sprite;
        return this;
    }

    public @Nullable Identifier icon() {
        return this.icon;
    }

    public FlatButton placeholderWhenInactive() {
        this.placeholderWhenInactive = true;
        return this;
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
        WidgetState state = this.state();
        if (this.placeholderWhenInactive && !this.isActive()) {
            ThemeText.textCentered(g, this.font(), "—", this.getX() + this.width / 2, Metrics.textY(this.getY(), this.height), Palette.TEXT_DISABLED);
            return;
        }
        boolean on = this.selected.getAsBoolean();
        if (this.style == Style.TAB) {
            ThemedWidgets.tab(g, this.font(), this.getX(), this.getY(), this.width, this.height, this.getMessage(), state, on);
            return;
        }
        ThemeDraw.ButtonStyle face = switch (this.style) {
            case PRIMARY -> ThemeDraw.ButtonStyle.PRIMARY;
            case DANGER -> ThemeDraw.ButtonStyle.DANGER;
            default -> ThemeDraw.ButtonStyle.NORMAL;
        };
        Identifier sprite = this.icon;
        if (sprite != null) {
            ThemedWidgets.iconButton(g, sprite, this.getX(), this.getY(), this.width, this.height, face, state, on);
            return;
        }
        ThemedWidgets.button(g, this.font(), this.getX(), this.getY(), this.width, this.height, this.getMessage(), face, state, on);
    }
}
