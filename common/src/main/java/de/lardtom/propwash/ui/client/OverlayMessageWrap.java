package de.lardtom.propwash.ui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class OverlayMessageWrap {
    public static final int MARGIN = 6;
    public static final int LINE_STEP = 12;
    public static final int MIN_LIMIT = 60;
    public static final int EXTRA_TICKS_PER_LINE = 40;
    public static final int MAX_EXTRA_TICKS = 80;
    private static int lastLines;
    private static int lastWidest;
    private static int lastLimit;

    private OverlayMessageWrap() {
    }

    public static int limit(int guiWidth) {
        return guiWidth - 2 * MARGIN;
    }

    public static boolean needed(int textWidth, int guiWidth) {
        int limit = limit(guiWidth);
        return limit >= MIN_LIMIT && textWidth > limit;
    }

    public static List<FormattedCharSequence> lines(Font font, Component text, int guiWidth) {
        return font.split(text, limit(guiWidth));
    }

    public static int extraTicks(Component text) {
        Minecraft mc = Minecraft.getInstance();
        if (text == null || mc.font == null || mc.getWindow() == null) {
            return 0;
        }
        int guiWidth = mc.getWindow().getGuiScaledWidth();
        if (!needed(mc.font.width(text), guiWidth)) {
            return 0;
        }
        return Math.min(MAX_EXTRA_TICKS, (lines(mc.font, text, guiWidth).size() - 1) * EXTRA_TICKS_PER_LINE);
    }

    public static boolean extract(GuiGraphicsExtractor graphics, Font font, Component text, int y, int color) {
        int guiWidth = graphics.guiWidth();
        int width = font.width(text);
        lastLimit = limit(guiWidth);
        if (!needed(width, guiWidth)) {
            lastLines = 1;
            lastWidest = width;
            return false;
        }
        List<FormattedCharSequence> lines = lines(font, text, guiWidth);
        int background = Minecraft.getInstance().options.getBackgroundColor(0.0F);
        int widest = 0;
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            int lineWidth = font.width(line);
            widest = Math.max(widest, lineWidth);
            int lineY = y - (lines.size() - 1 - i) * LINE_STEP;
            if (background != 0) {
                graphics.fill(-lineWidth / 2 - 2, lineY - 2, -lineWidth / 2 + lineWidth + 2, lineY + 9 + 2, ARGB.multiply(background, color));
            }
            graphics.text(font, line, -lineWidth / 2, lineY, color, true);
        }
        lastLines = lines.size();
        lastWidest = widest;
        return true;
    }

    public static int lastLines() {
        return lastLines;
    }

    public static int lastWidest() {
        return lastWidest;
    }

    public static int lastLimit() {
        return lastLimit;
    }
}
