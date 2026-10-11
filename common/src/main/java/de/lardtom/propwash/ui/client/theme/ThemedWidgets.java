package de.lardtom.propwash.ui.client.theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class ThemedWidgets {
    private ThemedWidgets() {
    }

    public static int labelColor(WidgetState state) {
        if (!state.active()) {
            return Palette.TEXT_DISABLED;
        }
        return state.hot() ? Palette.TEXT : Palette.TEXT_MUTED;
    }

    public static int valueColor(WidgetState state, boolean modified) {
        if (!state.active()) {
            return Palette.TEXT_DISABLED;
        }
        return modified ? Palette.ACCENT : Palette.TEXT;
    }

    public static Component onOff(boolean on) {
        return Component.translatable(on ? "propwash.ui.value.on" : "propwash.ui.value.off");
    }

    public static int labelWidth(Font font, @Nullable Component label, int rowWidth) {
        return label == null ? Metrics.labelWidth(rowWidth) : Metrics.labelWidth(rowWidth, font.width(label));
    }

    public static int labelWidth(Font font, @Nullable Component label, int rowWidth, int controlNeed) {
        return label == null ? Metrics.labelWidth(rowWidth) : Metrics.labelWidth(rowWidth, font.width(label), controlNeed);
    }

    public static void label(GuiGraphicsExtractor g, Font font, @Nullable Component label, int x, int y, int height, int labelWidth, WidgetState state) {
        if (label == null || labelWidth <= Metrics.LABEL_GAP) {
            return;
        }
        ThemeText.line(g, font, label, x, x + labelWidth - Metrics.LABEL_GAP, Metrics.textY(y, height), labelColor(state), ThemeText.Align.LEFT,
                state.hot());
    }

    public static void button(GuiGraphicsExtractor g, Font font, int x, int y, int width, int height, Component message,
                              ThemeDraw.ButtonStyle style, WidgetState state, boolean selected) {
        ThemeDraw.button(g, x, y, x + width, y + height, style, state, selected);
        int pad = 3;
        int textY = Metrics.textY(y, height) + (state.pressed() ? 1 : 0);
        ThemeText.line(g, font, message, x + pad, x + width - pad, textY, ThemeDraw.buttonText(style, state), ThemeText.Align.CENTER, state.hot());
    }

    public static void iconButton(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int width, int height, ThemeDraw.ButtonStyle style,
                                  WidgetState state, boolean selected) {
        ThemeDraw.button(g, x, y, x + width, y + height, style, state, selected);
        int size = Math.min(Metrics.ICON_SPRITE, Math.min(width, height));
        int ix = x + (width - size) / 2;
        int iy = y + (height - size) / 2 + (state.pressed() ? 1 : 0);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, ix, iy, size, size, ThemeDraw.buttonText(style, state));
    }

    public static void tab(GuiGraphicsExtractor g, Font font, int x, int y, int width, int height, Component message, WidgetState state,
                           boolean selected) {
        int bg = selected ? Palette.PANEL_HEADER : (state.hot() ? Palette.BUTTON : Palette.SIDEBAR);
        g.fill(x, y, x + width, y + height, bg);
        if (selected) {
            g.fill(x, y, x + 2, y + height, Palette.ACCENT);
        } else if (state.focused()) {
            g.fill(x, y, x + 1, y + height, Palette.ACCENT_DIM);
        }
        int color = selected ? Palette.ACCENT : (state.hot() ? Palette.TEXT : Palette.TEXT_MUTED);
        ThemeText.line(g, font, message, x + 8, x + width - 4, Metrics.textY(y, height), color, ThemeText.Align.LEFT, state.hot());
    }

    public static void cycle(GuiGraphicsExtractor g, Font font, int x, int y, int width, int height, int labelWidth, @Nullable Component label,
                             Component value, WidgetState state, boolean modified) {
        label(g, font, label, x, y, height, labelWidth, state);
        int bx0 = x + Math.max(0, labelWidth);
        int bx1 = x + width;
        ThemeDraw.field(g, bx0, y, bx1, y + height, state);
        int textY = Metrics.textY(y, height);
        ThemeDraw.cycleArrows(g, font, bx0, bx1, textY, state);
        int arrow = Metrics.ARROW_WIDTH;
        ThemeText.line(g, font, value, bx0 + arrow + 3, bx1 - arrow - 3, textY, valueColor(state, modified), ThemeText.Align.CENTER, state.hot());
    }

    public static void toggle(GuiGraphicsExtractor g, Font font, int x, int y, int width, int height, int labelWidth, @Nullable Component label,
                              boolean on, Component value, WidgetState state, boolean modified) {
        label(g, font, label, x, y, height, labelWidth, state);
        int px = x + Math.max(0, labelWidth);
        int py = y + Math.max(0, (height - Metrics.TOGGLE_HEIGHT) / 2);
        ThemeDraw.toggle(g, px, py, on, state);
        int textX = px + Metrics.TOGGLE_WIDTH + 5;
        int color = !state.active() ? Palette.TEXT_DISABLED : (modified ? Palette.ACCENT : (on ? Palette.TEXT : Palette.TEXT_MUTED));
        ThemeText.line(g, font, value, textX, x + width, Metrics.textY(y, height), color, ThemeText.Align.LEFT, false);
    }

    public static void slider(GuiGraphicsExtractor g, Font font, int x, int y, int width, int height, int labelWidth, @Nullable Component label,
                              Component value, double fraction, WidgetState state, boolean modified) {
        label(g, font, label, x, y, height, labelWidth, state);
        int tx0 = x + Math.max(0, labelWidth);
        int tx1 = x + width;
        ThemeDraw.sliderTrack(g, tx0, y, tx1, y + height, fraction, state);
        int textX0 = tx0 + 3;
        int textX1 = tx1 - 3;
        int textY = Metrics.textY(y, height);
        int color = valueColor(state, modified);
        int textWidth = font.width(value);
        if (textWidth > textX1 - textX0) {
            ThemeText.line(g, font, value, textX0, textX1, textY, color, ThemeText.Align.CENTER, state.hot());
            return;
        }
        int center = Metrics.sliderHandleCenter(tx0, tx1 - tx0, fraction);
        int textX = sliderValueX(textX0, textX1, textWidth);
        if (handleCover(textX, textWidth, center, tx0, tx1) != null) {
            g.nextStratum();
            ThemeText.outlined(g, font, value, textX, textY, color, Palette.SHADOW);
            return;
        }
        ThemeText.text(g, font, value, textX, textY, color);
    }

    public static int sliderValueX(int x0, int x1, int textWidth) {
        return x0 + (x1 - x0 - textWidth) / 2;
    }

    public static int @Nullable [] handleCover(int textX, int textWidth, int handleCenter, int trackX0, int trackX1) {
        int half = Metrics.SLIDER_HANDLE / 2;
        int hx0 = Math.max(trackX0, handleCenter - half);
        int hx1 = Math.min(trackX1, handleCenter + half);
        if (hx1 <= textX || hx0 >= textX + textWidth) {
            return null;
        }
        return new int[]{hx0, hx1};
    }

    public static void checkbox(GuiGraphicsExtractor g, Font font, int x, int y, int width, int height, int boxSize, Component label, boolean checked,
                                WidgetState state) {
        int size = Math.min(Metrics.CHECK_SIZE, boxSize);
        int by = y + (boxSize - size) / 2;
        int bx = x + (boxSize - size) / 2;
        ThemeDraw.checkbox(g, bx, by, size, checked, state);
        int textX = x + boxSize + 4;
        int textWidth = Math.max(1, x + width - textX);
        List<FormattedCharSequence> lines = font.split(label, textWidth);
        int shown = ThemeText.shownLines(label, lines.size(), 2);
        int textY = y + boxSize / 2 - shown * font.lineHeight / 2 + 1;
        int color = state.active() ? Palette.TEXT : Palette.TEXT_DISABLED;
        for (int i = 0; i < shown; i++) {
            ThemeText.text(g, font, lines.get(i), textX, textY + i * font.lineHeight, color);
        }
    }
}
