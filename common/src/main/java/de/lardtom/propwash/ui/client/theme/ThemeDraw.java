package de.lardtom.propwash.ui.client.theme;

import de.lardtom.propwash.ui.ChromeLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class ThemeDraw {
    public enum ButtonStyle {
        NORMAL,
        PRIMARY,
        DANGER
    }

    public static final int MORE_BELOW_HEIGHT = Metrics.LINE_HEIGHT;

    private ThemeDraw() {
    }

    public static void box(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        if (x1 - x0 < 3 || y1 - y0 < 3) {
            g.fill(x0, y0, x1, y1, color);
            return;
        }
        g.fill(x0 + 1, y0, x1 - 1, y1, color);
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
    }

    public static void boxOutline(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        if (x1 - x0 < 3 || y1 - y0 < 3) {
            g.outline(x0, y0, x1 - x0, y1 - y0, color);
            return;
        }
        g.fill(x0 + 1, y0, x1 - 1, y0 + 1, color);
        g.fill(x0 + 1, y1 - 1, x1 - 1, y1, color);
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
    }

    public static void framedBox(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int fill, int border) {
        box(g, x0, y0, x1, y1, border);
        if (x1 - x0 > 2 && y1 - y0 > 2) {
            g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
        }
    }

    public static void notice(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int accent, float alpha) {
        int a = Math.max(16, Math.round(Math.clamp(alpha, 0.0F, 1.0F) * 255.0F));
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, Palette.withAlpha(Palette.PANEL, Math.round(a * 0.94F)));
        g.outline(x0, y0, x1 - x0, y1 - y0, Palette.withAlpha(Palette.BORDER_STRONG, a));
        g.fill(x0 + 1, y0 + 1, x0 + 3, y1 - 1, Palette.withAlpha(accent, a));
    }

    public static void iconTile(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        box(g, x0, y0, x1, y1, Palette.SHADOW);
        if (x1 - x0 > 2 && y1 - y0 > 2) {
            g.fillGradient(x0 + 1, y0 + 1, x1 - 1, y1 - 1, Palette.ICON_TILE_TOP, Palette.ICON_TILE_BOTTOM);
            g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, Palette.ICON_TILE_HIGHLIGHT);
        }
    }

    public static void backdrop(GuiGraphicsExtractor g, int width, int height) {
        g.fill(0, 0, width, height, Palette.BACKDROP);
    }

    public static void window(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, Palette.SHADOW);
        g.fill(x0, y0, x1, y1, Palette.BACKGROUND);
    }

    public static void windowBorder(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        g.outline(x0, y0, x1 - x0, y1 - y0, Palette.BORDER);
    }

    public static void headerBar(GuiGraphicsExtractor g, int x0, int y0, int x1, int height) {
        g.fill(x0, y0, x1, y0 + height, Palette.HEADER);
        g.fill(x0, y0 + height - 1, x1, y0 + height, Palette.ACCENT);
    }

    public static void subHeaderBar(GuiGraphicsExtractor g, int x0, int y0, int x1, int height) {
        g.fill(x0, y0, x1, y0 + height, Palette.SIDEBAR);
        g.fill(x0, y0 + height - 1, x1, y0 + height, Palette.BORDER);
    }

    public static void footerBar(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, Palette.FOOTER);
        g.fill(x0, y0, x1, y0 + 1, Palette.BORDER);
    }

    public static int footerLines(Font font, FormattedText text, int limit) {
        return Math.max(1, font.split(text, Math.max(1, limit)).size());
    }

    public static void footerText(GuiGraphicsExtractor g, Font font, FormattedText text, int x, int footerTop, int footerHeight, int limit, int color) {
        List<FormattedCharSequence> lines = ThemeText.balanced(font, text, Math.max(1, limit));
        int top = ChromeLayout.statusTop(footerTop, footerHeight, lines.size());
        for (int i = 0; i < lines.size(); i++) {
            ThemeText.text(g, font, lines.get(i), x, top + i * ChromeLayout.LINE, color);
        }
    }

    public static void sidebar(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, Palette.SIDEBAR);
        g.fill(x1 - 1, y0, x1, y1, Palette.BORDER);
    }

    public static int brandWidth(Font font) {
        return font.width(Component.literal("PROPWASH").withStyle(ChatFormatting.BOLD)) + 3 + font.width("FPV");
    }

    public static int brandedTitleX(Font font, int x0) {
        return x0 + 8 + brandWidth(font) + 10;
    }

    public static int brand(GuiGraphicsExtractor g, Font font, int x, int y) {
        Component name = Component.literal("PROPWASH").withStyle(ChatFormatting.BOLD);
        ThemeText.text(g, font, name, x, y, Palette.ACCENT);
        int right = x + font.width(name) + 3;
        ThemeText.text(g, font, "FPV", right, y, Palette.TEXT_MUTED);
        return right + font.width("FPV");
    }

    public static int brandedHeader(GuiGraphicsExtractor g, Font font, int x0, int y0, int x1, int height, Component title, int titleRight) {
        headerBar(g, x0, y0, x1, height);
        int textY = Metrics.textY(y0, height - 1);
        int titleX = brand(g, font, x0 + 8, textY) + 10;
        int inset = Math.max(3, Math.min(5, (height - 8) / 2));
        verticalSeparator(g, titleX - 5, y0 + inset, y0 + height - 1 - inset);
        int room = Math.max(0, titleRight - titleX);
        ThemeText.fitted(g, font, title, titleX, textY, room, Palette.TEXT);
        return titleX + ThemeText.fittedWidth(font, title, room);
    }

    public static int headerSubtitle(GuiGraphicsExtractor g, Font font, int x, int y0, int height, Component subtitle, int right) {
        int inset = Math.max(3, Math.min(5, (height - 8) / 2));
        verticalSeparator(g, x + 5, y0 + inset, y0 + height - 1 - inset);
        int room = Math.max(0, right - x - 10);
        ThemeText.fitted(g, font, subtitle, x + 10, Metrics.textY(y0, height - 1), room, Palette.TEXT_FAINT);
        return x + 10 + ThemeText.fittedWidth(font, subtitle, room);
    }

    public static void panel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, Palette.PANEL);
        g.outline(x0, y0, x1 - x0, y1 - y0, Palette.BORDER);
    }

    public static void panelTitle(GuiGraphicsExtractor g, Font font, Component title, int x0, int y0, int x1) {
        int h = Metrics.PANEL_TITLE_HEIGHT;
        g.fill(x0, y0, x1, y0 + h, Palette.PANEL_HEADER);
        g.fill(x0, y0, x0 + 2, y0 + h, Palette.ACCENT);
        g.outline(x0, y0, x1 - x0, h, Palette.BORDER);
        ThemeText.fitted(g, font, title, x0 + 6, y0 + 2, x1 - x0 - 10, Palette.TEXT);
    }

    public static void sectionTitle(GuiGraphicsExtractor g, Font font, Component title, int x, int y, int width) {
        ThemeText.fitted(g, font, title, x, y, width, Palette.ACCENT);
        separator(g, x, x + width, y + 10);
    }

    public static void separator(GuiGraphicsExtractor g, int x0, int x1, int y) {
        g.fill(x0, y, x1, y + 1, Palette.BORDER);
    }

    public static void verticalSeparator(GuiGraphicsExtractor g, int x, int y0, int y1) {
        g.fill(x, y0, x + 1, y1, Palette.BORDER_STRONG);
    }

    public static int buttonFace(ButtonStyle style, WidgetState state, boolean selected) {
        return switch (style) {
            case PRIMARY -> !state.active() ? Palette.ACCENT_DIM
                    : state.pressed() ? Palette.ACCENT_PRESSED : (state.hovered() ? Palette.ACCENT_HOVER : Palette.ACCENT);
            case DANGER -> !state.active() ? Palette.DISABLED
                    : state.pressed() ? Palette.DANGER_PRESSED : (state.hovered() ? Palette.DANGER_HOVER : Palette.DANGER);
            case NORMAL -> !state.active() ? Palette.DISABLED
                    : state.pressed() ? Palette.BUTTON_PRESSED
                    : selected ? Palette.ACCENT_DIM : (state.hovered() ? Palette.BUTTON_HOVER : Palette.BUTTON);
        };
    }

    public static int buttonText(ButtonStyle style, WidgetState state) {
        if (style == ButtonStyle.PRIMARY) {
            return state.active() ? Palette.ACCENT_TEXT : Palette.withAlpha(Palette.ACCENT_TEXT, 0xB0);
        }
        return state.active() ? Palette.TEXT : Palette.TEXT_DISABLED;
    }

    public static void button(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, ButtonStyle style, WidgetState state, boolean selected) {
        if (!state.active()) {
            boxOutline(g, x0, y0, x1, y1, style == ButtonStyle.PRIMARY ? Palette.ACCENT_DIM : Palette.BORDER_STRONG);
            if (x1 - x0 > 2 && y1 - y0 > 2) {
                g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, buttonFace(style, state, selected));
            }
            return;
        }
        box(g, x0, y0, x1, y1, buttonFace(style, state, selected));
        if (state.active() && !state.pressed() && style == ButtonStyle.NORMAL && y1 - y0 > 4) {
            g.fill(x0 + 1, y0, x1 - 1, y0 + 1, state.hovered() ? Palette.withAlpha(0xFFFFFF, 0x24) : Palette.withAlpha(0xFFFFFF, 0x14));
        }
        if (state.focused() && state.active()) {
            boxOutline(g, x0, y0, x1, y1, style == ButtonStyle.PRIMARY ? Palette.TEXT : Palette.ACCENT);
        }
    }

    public static void field(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, WidgetState state) {
        int fill = fieldFill(state);
        int border = state.focused() && state.active() ? Palette.ACCENT : (state.hovered() && state.active() ? Palette.BORDER_STRONG : Palette.BORDER);
        framedBox(g, x0, y0, x1, y1, fill, border);
    }

    public static void cycleArrows(GuiGraphicsExtractor g, Font font, int x0, int x1, int textY, WidgetState state) {
        int color = !state.active() ? Palette.TEXT_DISABLED : (state.hot() ? Palette.ACCENT : Palette.TEXT_FAINT);
        ThemeText.text(g, font, "‹", x0 + 3, textY, color);
        ThemeText.textRight(g, font, "›", x1 - 3, textY, color);
    }

    private static int fieldFill(WidgetState state) {
        return !state.active() ? Palette.DISABLED : (state.hovered() ? Palette.FIELD_HOVER : Palette.FIELD);
    }

    private static int sliderFill(WidgetState state) {
        return !state.active() ? Palette.BORDER : (state.focused() ? Palette.ACCENT_FOCUS : Palette.ACCENT_DIM);
    }

    public static void sliderTrack(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, double fraction, WidgetState state) {
        field(g, x0, y0, x1, y1, state.withFocused(false));
        int center = Metrics.sliderHandleCenter(x0, x1 - x0, fraction);
        if (center > x0 + 1) {
            g.fill(x0 + 1, y0 + 1, center, y1 - 1, sliderFill(state));
        }
        int half = Metrics.SLIDER_HANDLE / 2;
        int hx0 = Math.max(x0, center - half);
        int hx1 = Math.min(x1, center + half);
        int handle = !state.active() ? Palette.TEXT_DISABLED : (state.hot() || state.pressed() ? Palette.ACCENT_HOVER : Palette.ACCENT);
        g.fill(hx0, y0, hx1, y1, handle);
        if (state.focused() && state.active()) {
            boxOutline(g, x0, y0, x1, y1, Palette.ACCENT);
        }
    }

    public static void toggle(GuiGraphicsExtractor g, int x, int y, boolean on, WidgetState state) {
        int w = Metrics.TOGGLE_WIDTH;
        int h = Metrics.TOGGLE_HEIGHT;
        int track = !state.active() ? Palette.DISABLED : (on ? Palette.ACCENT : Palette.FIELD);
        int border = state.focused() && state.active() ? Palette.ACCENT : (on && state.active() ? Palette.ACCENT : Palette.BORDER_STRONG);
        framedBox(g, x, y, x + w, y + h, track, border);
        int knob = on ? x + w - h + 1 : x + 1;
        int knobColor = !state.active() ? Palette.TEXT_DISABLED : (on ? Palette.ACCENT_TEXT : (state.hot() ? Palette.TEXT : Palette.TEXT_MUTED));
        box(g, knob, y + 1, knob + h - 2, y + h - 1, knobColor);
    }

    public static void checkbox(GuiGraphicsExtractor g, int x, int y, int size, boolean checked, WidgetState state) {
        int border = state.focused() && state.active() ? Palette.ACCENT : (state.hot() ? Palette.TEXT_MUTED : Palette.BORDER_STRONG);
        framedBox(g, x, y, x + size, y + size, state.active() ? Palette.FIELD : Palette.DISABLED, border);
        if (checked) {
            int inset = Math.max(2, size / 4);
            g.fill(x + inset, y + inset, x + size - inset, y + size - inset, state.active() ? Palette.ACCENT : Palette.TEXT_DISABLED);
        }
    }

    public static void scrollbar(GuiGraphicsExtractor g, int x, int y, int height, int contentHeight, double offset, boolean dragging) {
        if (contentHeight <= height || height <= 0) {
            return;
        }
        int w = Metrics.SCROLLBAR_WIDTH;
        g.fill(x, y, x + w, y + height, Palette.FIELD);
        int thumb = Math.max(12, (int) ((long) height * height / contentHeight));
        double max = contentHeight - height;
        int thumbY = y + (int) Math.round((height - thumb) * Math.clamp(offset / max, 0.0, 1.0));
        g.fill(x, thumbY, x + w, thumbY + thumb, dragging ? Palette.ACCENT : Palette.BORDER_STRONG);
    }

    public static void valueBar(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, double fraction, int color) {
        g.fill(x0, y0, x1, y1, Palette.FIELD);
        double f = Double.isFinite(fraction) ? Math.clamp(fraction, 0.0, 1.0) : 0.0;
        int fill = x0 + (int) Math.round((x1 - x0) * f);
        if (fill > x0) {
            g.fill(x0, y0, fill, y1, color);
        }
    }

    public static void track(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, boolean highlight) {
        g.fill(x0, y0, x1, y1, Palette.FIELD);
        g.outline(x0, y0, x1 - x0, y1 - y0, highlight ? Palette.ACCENT : Palette.BORDER_STRONG);
    }

    public static void tooltipFrame(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, Palette.TOOLTIP);
        g.outline(x0, y0, x1 - x0, y1 - y0, Palette.BORDER_STRONG);
        g.fill(x0 + 1, y0, x1 - 1, y0 + 1, Palette.ACCENT_DIM);
    }

    public static void previewStage(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        int width = x1 - x0;
        int height = y1 - y0;
        if (width < 8 || height < 8) {
            g.fill(x0, y0, x1, y1, Palette.FIELD);
            return;
        }
        g.fillGradient(x0, y0, x1, y1, Palette.STAGE_TOP, Palette.STAGE_BOTTOM);
        int horizon = y0 + height * 2 / 3;
        int depth = y1 - 1 - horizon;
        float[] rows = {0.0F, 0.15F, 0.35F, 0.6F, 0.9F};
        for (float row : rows) {
            int y = horizon + Math.round(depth * row);
            g.fill(x0 + 1, y, x1 - 1, y + 1, Palette.STAGE_GRID);
        }
        g.enableScissor(x0 + 1, horizon, x1 - 1, y1 - 1);
        try {
            float vx = (x0 + x1) * 0.5F;
            float spacing = Math.max(12.0F, width * 0.18F);
            int spokes = (int) Math.ceil(width * 0.5F / spacing) + 1;
            for (int i = -spokes; i <= spokes; i++) {
                float topX = vx + i * spacing * 0.25F;
                float bottomX = vx + i * spacing;
                float dx = bottomX - topX;
                float dy = y1 - 1 - horizon;
                float length = (float) Math.sqrt(dx * dx + dy * dy);
                g.pose().pushMatrix();
                g.pose().translate(topX, horizon);
                g.pose().rotate((float) Math.atan2(dy, dx));
                g.fill(0, 0, (int) Math.ceil(length), 1, Palette.STAGE_GRID);
                g.pose().popMatrix();
            }
        } finally {
            g.disableScissor();
        }
        g.fill(x0, y0, x1, y0 + 1, Palette.STAGE_EDGE_DARK);
        g.fill(x0, y0, x0 + 1, y1, Palette.STAGE_EDGE_DARK);
        g.fill(x0 + 1, y1 - 1, x1, y1, Palette.STAGE_EDGE_LIGHT);
        g.fill(x1 - 1, y0 + 1, x1, y1, Palette.STAGE_EDGE_LIGHT);
    }

    public static void dot(GuiGraphicsExtractor g, int cx, int cy, int color) {
        g.fill(cx - 2, cy - 1, cx + 2, cy + 1, color);
        g.fill(cx - 1, cy - 2, cx + 1, cy + 2, color);
    }

    public static void lamp(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
        g.fill(x, y, x + width, y + height, Palette.BUTTON);
        if (width > 2 && height > 2) {
            g.fill(x + 1, y + 1, x + width - 1, y + height - 1, color != 0 ? color : Palette.FIELD);
        }
    }

    public static int moreBelow(GuiGraphicsExtractor g, Font font, int x, int y) {
        return moreBelow(g, font, x, y, Integer.MAX_VALUE);
    }

    public static int moreBelow(GuiGraphicsExtractor g, Font font, int x, int y, int width) {
        for (int i = 0; i < 3; i++) {
            g.fill(x + i, y + 3 + i, x + 5 - i, y + 4 + i, Palette.TEXT_MUTED);
        }
        Component text = Component.translatable("propwash.ui.scroll.more");
        int natural = font.width(text);
        if (width - 8 >= natural) {
            ThemeText.text(g, font, text, x + 8, y, Palette.TEXT_MUTED);
            return 8 + natural;
        }
        int room = Math.max(8, width - 8);
        ThemeText.fitted(g, font, text, x + 8, y, room, Palette.TEXT_MUTED);
        return 8 + Math.min(natural, room);
    }

    public static void led(GuiGraphicsExtractor g, int x, int y, boolean on) {
        int s = Metrics.LED_SIZE;
        int top = y + (Metrics.TEXT_HEIGHT - s) / 2;
        if (on) {
            g.fill(x + 1, top - 1, x + s - 1, top + s + 1, Palette.withAlpha(Palette.SUCCESS, 0x30));
            g.fill(x - 1, top + 1, x + s + 1, top + s - 1, Palette.withAlpha(Palette.SUCCESS, 0x30));
        }
        g.fill(x + 2, top, x + s - 2, top + s, Palette.LED_BEZEL);
        g.fill(x + 1, top + 1, x + s - 1, top + s - 1, Palette.LED_BEZEL);
        g.fill(x, top + 2, x + s, top + s - 2, Palette.LED_BEZEL);
        int core = on ? Palette.SUCCESS : Palette.LED_OFF;
        g.fill(x + 2, top + 1, x + s - 2, top + s - 1, core);
        g.fill(x + 1, top + 2, x + s - 1, top + s - 2, core);
        g.fill(x + 2, top + 2, x + 3, top + 3, on ? Palette.SUCCESS_BORDER : Palette.LED_OFF_GLINT);
    }

    public static int badge(GuiGraphicsExtractor g, Font font, Component text, int x, int y0, int y1, int background, int foreground) {
        int w = font.width(text) + 8;
        box(g, x, y0, x + w, y1, background);
        ThemeText.text(g, font, text, x + 4, Metrics.textY(y0, y1 - y0), foreground);
        return w;
    }

    public static void selection(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, boolean selected, boolean hovered) {
        if (selected) {
            g.fill(x0, y0, x1, y1, Palette.SELECTION);
            g.fill(x0, y0, x0 + 3, y1, Palette.ACCENT);
        } else if (hovered) {
            g.fill(x0, y0, x1, y1, Palette.HOVER_SOFT);
        }
    }
}
