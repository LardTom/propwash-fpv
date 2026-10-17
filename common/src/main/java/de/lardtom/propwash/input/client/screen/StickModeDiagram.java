package de.lardtom.propwash.input.client.screen;

import de.lardtom.propwash.input.StickChannel;
import de.lardtom.propwash.input.StickMode;
import de.lardtom.propwash.input.client.StickModes;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class StickModeDiagram {
    public static final int COMPACT_HEIGHT = 22;
    public static final int CARD_MIN_WIDTH = 64;
    public static final int CARD_MIN_HEIGHT = 58;
    private static final String VERTICAL = "↕ ";
    private static final String HORIZONTAL = "↔ ";

    private StickModeDiagram() {
    }

    public static void extractCompact(GuiGraphicsExtractor g, Font font, StickMode mode, int x, int y, int width) {
        int half = (width - 6) / 2;
        int box = COMPACT_HEIGHT;
        for (int side = 0; side < 2; side++) {
            boolean left = side == 0;
            int x0 = x + side * (half + 6);
            gimbal(g, mode, left, x0, y, box);
            int textX = x0 + box + 4;
            int textWidth = half - box - 4;
            label(g, font, mode, left, true, false, textX, y + 1, textWidth);
            label(g, font, mode, left, false, false, textX, y + 1 + Metrics.LINE_HEIGHT + 1, textWidth);
        }
    }

    public static int cardHeight(int width) {
        int column = (width - 9) / 2;
        return 4 + Metrics.LINE_HEIGHT + 2 + Math.max(10, column - 2) + 2 + 2 * Metrics.LINE_HEIGHT + 4;
    }

    public static void extractCard(GuiGraphicsExtractor g, Font font, StickMode mode, int x0, int y0, int x1, int y1, boolean selected, boolean hovered) {
        int fill = selected ? Palette.ACCENT_DIM : hovered ? Palette.FIELD_HOVER : Palette.FIELD;
        int border = selected ? Palette.ACCENT : hovered ? Palette.BORDER_STRONG : Palette.BORDER;
        ThemeDraw.framedBox(g, x0, y0, x1, y1, fill, border);
        if (selected) {
            ThemeDraw.boxOutline(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, Palette.ACCENT);
        }
        int width = x1 - x0;
        Component title = StickModes.name(mode);
        ThemeText.fittedCentered(g, font, title, (x0 + x1) / 2, y0 + 4, width - 4, selected ? Palette.ACCENT : Palette.TEXT);
        int labels = 2 * Metrics.LINE_HEIGHT + 2;
        int top = y0 + 4 + Metrics.LINE_HEIGHT + 2;
        int column = (width - 9) / 2;
        int box = Math.max(10, Math.min(column - 2, y1 - 4 - labels - top));
        for (int side = 0; side < 2; side++) {
            boolean left = side == 0;
            int cx = x0 + 3 + column / 2 + side * (column + 3);
            gimbal(g, mode, left, cx - box / 2, top, box);
            int labelY = top + box + 2;
            int lx = cx - column / 2;
            label(g, font, mode, left, true, true, lx, labelY, column);
            label(g, font, mode, left, false, true, lx, labelY + Metrics.LINE_HEIGHT, column);
        }
    }

    private static void label(GuiGraphicsExtractor g, Font font, StickMode mode, boolean left, boolean vertical, boolean centered, int x, int y, int width) {
        StickChannel channel = mode.channel(left, vertical);
        Component text = Component.literal(vertical ? VERTICAL : HORIZONTAL).append(Component.translatable(centered ? channel.shortKey() : channel.translationKey()));
        int color = InputGui.channelColor(channel);
        if (centered) {
            ThemeText.fittedCentered(g, font, text, x + width / 2, y, width, color);
        } else {
            ThemeText.fitted(g, font, text, x, y, width, color);
        }
    }

    private static void gimbal(GuiGraphicsExtractor g, StickMode mode, boolean left, int x0, int y0, int size) {
        ThemeDraw.framedBox(g, x0, y0, x0 + size, y0 + size, Palette.FIELD, Palette.BORDER_STRONG);
        int cx = x0 + size / 2;
        int cy = y0 + size / 2;
        int inset = 3;
        int vertical = InputGui.channelColor(mode.channel(left, true));
        int horizontal = InputGui.channelColor(mode.channel(left, false));
        g.fill(x0 + inset, cy, x0 + size - inset, cy + 1, horizontal);
        g.fill(cx, y0 + inset, cx + 1, y0 + size - inset, vertical);
        g.fill(x0 + inset, cy - 1, x0 + inset + 1, cy + 2, horizontal);
        g.fill(x0 + size - inset - 1, cy - 1, x0 + size - inset, cy + 2, horizontal);
        g.fill(cx - 1, y0 + inset, cx + 2, y0 + inset + 1, vertical);
        g.fill(cx - 1, y0 + size - inset - 1, cx + 2, y0 + size - inset, vertical);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, Palette.TEXT);
    }
}
