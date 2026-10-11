package de.lardtom.propwash.ui.client.theme;

import de.lardtom.propwash.ui.TextFit;
import de.lardtom.propwash.ui.TooltipPlacement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.tooltip.BelowOrAboveWidgetTooltipPositioner;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ThemeText {
    public enum Align {
        LEFT,
        CENTER,
        RIGHT
    }

    public enum Style {
        TITLE(Palette.TEXT, true, 1.0F),
        SECTION(Palette.ACCENT, false, 1.0F),
        BODY(Palette.TEXT, false, 1.0F),
        LABEL(Palette.TEXT_MUTED, false, 1.0F),
        HINT(Palette.TEXT_FAINT, false, 1.0F),
        ACCENT(Palette.ACCENT, false, 1.0F),
        SMALL(Palette.TEXT_MUTED, false, 0.75F);

        private final int color;
        private final boolean bold;
        private final float scale;

        Style(int color, boolean bold, float scale) {
            this.color = color;
            this.bold = bold;
            this.scale = scale;
        }

        public int color() {
            return this.color;
        }

        public boolean bold() {
            return this.bold;
        }

        public float scale() {
            return this.scale;
        }
    }

    public record Split(Component label, Component value) {
    }

    private static final String ELLIPSIS = "…";
    private static final String LABEL_SEPARATOR = ": ";
    private static final float SMALL_SCALE = 0.75F;
    private static final int DASH_STEP = 3;

    private ThemeText() {
    }

    public static MutableComponent colored(Component text, int color) {
        return text.copy().withColor(color & 0xFFFFFF);
    }

    public static MutableComponent accent(Component text) {
        return colored(text, Palette.ACCENT);
    }

    public static MutableComponent muted(Component text) {
        return colored(text, Palette.TEXT_MUTED);
    }

    public static void outlined(GuiGraphicsExtractor g, Font font, Component s, int x, int y, int color, int outline) {
        g.text(font, s, x - 1, y, outline, false);
        g.text(font, s, x + 1, y, outline, false);
        g.text(font, s, x, y - 1, outline, false);
        g.text(font, s, x, y + 1, outline, false);
        g.text(font, s, x, y, color, false);
    }

    public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.text(font, s, x, y, color, false);
    }

    public static void text(GuiGraphicsExtractor g, Font font, Component s, int x, int y, int color) {
        g.text(font, s, x, y, color, false);
    }

    public static void text(GuiGraphicsExtractor g, Font font, FormattedCharSequence s, int x, int y, int color) {
        g.text(font, s, x, y, color, false);
    }

    public static void text(GuiGraphicsExtractor g, Font font, Component s, int x, int y, Style style) {
        Component shown = style.bold() ? s.copy().withStyle(ChatFormatting.BOLD) : s;
        if (style.scale() == 1.0F) {
            g.text(font, shown, x, y, style.color(), false);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(style.scale(), style.scale());
        g.text(font, shown, 0, 0, style.color(), false);
        g.pose().popMatrix();
    }

    public static void textRight(GuiGraphicsExtractor g, Font font, String s, int right, int y, int color) {
        g.text(font, s, right - font.width(s), y, color, false);
    }

    public static void textRight(GuiGraphicsExtractor g, Font font, Component s, int right, int y, int color) {
        g.text(font, s, right - font.width(s), y, color, false);
    }

    public static void textCentered(GuiGraphicsExtractor g, Font font, String s, int centerX, int y, int color) {
        g.text(font, s, centerX - font.width(s) / 2, y, color, false);
    }

    public static void textCentered(GuiGraphicsExtractor g, Font font, Component s, int centerX, int y, int color) {
        g.text(font, s, centerX - font.width(s) / 2, y, color, false);
    }

    public static String fit(Font font, String s, int width) {
        if (width <= 0) {
            return "";
        }
        if (font.width(s) <= width) {
            return s;
        }
        int ellipsis = font.width(ELLIPSIS);
        String shown = ellipsis >= width ? "" : font.plainSubstrByWidth(s, width - ellipsis) + ELLIPSIS;
        ThemeStats.cut(s, shown);
        return shown;
    }

    public static @Nullable Split splitLabel(Component message) {
        String text = message.getString();
        int at = text.indexOf(LABEL_SEPARATOR);
        if (at <= 0 || at + LABEL_SEPARATOR.length() >= text.length()) {
            return null;
        }
        return new Split(Component.literal(text.substring(0, at)), Component.literal(text.substring(at + LABEL_SEPARATOR.length())));
    }

    public static String fit(Font font, Component s, int width) {
        return fit(font, s.getString(), width);
    }

    public static int shownLines(FormattedText text, int lines, int room) {
        int shown = Math.max(0, Math.min(lines, room));
        if (shown < lines) {
            ThemeStats.cut(text.getString(), shown + " von " + lines + " Zeilen");
        }
        return shown;
    }

    public static boolean breaksWord(Font font, FormattedText text, int width) {
        for (String word : text.getString().split("\\s+")) {
            if (font.width(word) > width) {
                return true;
            }
        }
        return false;
    }

    public static boolean cut(@Nullable Component full, String shown) {
        return full != null && !shown.equals(full.getString());
    }

    public static boolean shows(@Nullable FormattedText text) {
        return text != null && !text.getString().isEmpty();
    }

    public static List<Component> visibleLines(@Nullable List<? extends @Nullable Component> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<Component> shown = new ArrayList<>(lines.size());
        boolean visible = false;
        for (Component line : lines) {
            if (line != null) {
                shown.add(line);
                visible |= !line.getString().isEmpty();
            }
        }
        return visible ? List.copyOf(shown) : List.of();
    }

    public static @Nullable Tooltip tip(@Nullable Component text) {
        return shows(text) ? Tooltip.create(text) : null;
    }

    public static boolean tooltip(GuiGraphicsExtractor g, Font font, @Nullable Component text, int mouseX, int mouseY) {
        return tooltipWrapped(g, font, text, TooltipPlacement.WRAP_WIDTH, mouseX, mouseY);
    }

    private static int screenX(GuiGraphicsExtractor g, int x, int y) {
        return Math.round(g.pose().transformPosition(x, y, new Vector2f()).x);
    }

    private static int screenY(GuiGraphicsExtractor g, int x, int y) {
        return Math.round(g.pose().transformPosition(x, y, new Vector2f()).y);
    }

    public static boolean tooltipLines(GuiGraphicsExtractor g, Font font, @Nullable List<? extends @Nullable Component> lines, int mouseX, int mouseY) {
        List<Component> shown = visibleLines(lines);
        if (shown.isEmpty()) {
            return false;
        }
        int width = TooltipPlacement.wrapWidth(TooltipPlacement.WRAP_WIDTH, g.guiWidth());
        List<FormattedCharSequence> wrapped = new ArrayList<>(shown.size());
        for (Component line : shown) {
            List<FormattedCharSequence> parts = font.split(line, width);
            if (parts.isEmpty()) {
                wrapped.add(FormattedCharSequence.EMPTY);
            } else {
                wrapped.addAll(parts);
            }
        }
        return tooltipSequences(g, font, wrapped, mouseX, mouseY);
    }

    public static boolean tooltipWrapped(GuiGraphicsExtractor g, Font font, @Nullable FormattedText text, int width, int mouseX, int mouseY) {
        if (!shows(text)) {
            return false;
        }
        return tooltipSequences(g, font, font.split(text, TooltipPlacement.wrapWidth(width, g.guiWidth())), mouseX, mouseY);
    }

    public static boolean tooltipBelow(GuiGraphicsExtractor g, Font font, @Nullable FormattedText text, int width, ScreenRectangle widget) {
        if (!shows(text)) {
            return false;
        }
        List<FormattedCharSequence> lines = font.split(text, TooltipPlacement.wrapWidth(width, g.guiWidth()));
        if (lines.isEmpty()) {
            return false;
        }
        ScreenRectangle placed = widget.transformAxisAligned(g.pose());
        g.setTooltipForNextFrame(font, lines, new BelowOrAboveWidgetTooltipPositioner(placed), placed.left(), placed.bottom(), true);
        return true;
    }

    public static boolean tooltipSequences(GuiGraphicsExtractor g, Font font, @Nullable List<? extends @Nullable FormattedCharSequence> lines, int mouseX,
                                           int mouseY) {
        if (lines == null || lines.isEmpty()) {
            return false;
        }
        List<FormattedCharSequence> shown = new ArrayList<>(lines.size());
        for (FormattedCharSequence line : lines) {
            if (line != null) {
                shown.add(line);
            }
        }
        if (shown.isEmpty()) {
            return false;
        }
        g.setTooltipForNextFrame(font, shown, screenX(g, mouseX, mouseY), screenY(g, mouseX, mouseY));
        return true;
    }

    public static void line(GuiGraphicsExtractor g, Font font, Component s, int x0, int x1, int y, int color, Align align, boolean scroll) {
        int available = x1 - x0;
        if (available <= 0) {
            return;
        }
        int width = font.width(s);
        if (width <= available) {
            int x = switch (align) {
                case LEFT -> x0;
                case CENTER -> x0 + (available - width) / 2;
                case RIGHT -> x1 - width;
            };
            g.text(font, s, x, y, color, false);
            return;
        }
        if (TextFit.mode(width, available) == TextFit.Mode.SQUEEZED) {
            squeezed(g, font, s.getVisualOrderText(), x0, y, TextFit.factor(width, available), color);
            return;
        }
        if (!scroll) {
            g.text(font, fit(font, s, available), x0, y, color, false);
            return;
        }
        int travel = width - available;
        double seconds = Util.getMillis() / 1000.0;
        double period = Math.max(travel * 0.5, 3.0);
        double phase = Math.sin(Math.PI / 2.0 * Math.cos(Math.PI * 2.0 * seconds / period)) / 2.0 + 0.5;
        int offset = (int) Math.round(phase * travel);
        g.enableScissor(x0, y - 1, x1, y + font.lineHeight + 1);
        g.text(font, s, x0 - offset, y, color, false);
        g.disableScissor();
    }

    private static void squeezed(GuiGraphicsExtractor g, Font font, FormattedCharSequence s, int x, int y, float factor, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(factor, 1.0F);
        g.text(font, s, 0, 0, color, false);
        g.pose().popMatrix();
    }

    public static void fitted(GuiGraphicsExtractor g, Font font, Component s, int x, int y, int width, int color) {
        int textWidth = font.width(s);
        switch (TextFit.mode(textWidth, width)) {
            case PLAIN -> g.text(font, s, x, y, color, false);
            case SQUEEZED -> squeezed(g, font, s.getVisualOrderText(), x, y, TextFit.factor(textWidth, width), color);
            case CUT -> g.text(font, fit(font, s, width), x, y, color, false);
        }
    }

    public static void fitted(GuiGraphicsExtractor g, Font font, String s, int x, int y, int width, int color) {
        fitted(g, font, Component.literal(s), x, y, width, color);
    }

    public static void fittedRight(GuiGraphicsExtractor g, Font font, Component s, int right, int y, int width, int color) {
        fitted(g, font, s, right - Math.min(width, font.width(s)), y, width, color);
    }

    public static void fittedRight(GuiGraphicsExtractor g, Font font, String s, int right, int y, int width, int color) {
        fittedRight(g, font, Component.literal(s), right, y, width, color);
    }

    public static void fittedCentered(GuiGraphicsExtractor g, Font font, Component s, int centerX, int y, int width, int color) {
        fitted(g, font, s, centerX - Math.min(width, font.width(s)) / 2, y, width, color);
    }

    public static void fittedCentered(GuiGraphicsExtractor g, Font font, String s, int centerX, int y, int width, int color) {
        fittedCentered(g, font, Component.literal(s), centerX, y, width, color);
    }

    public static int fittedWidth(Font font, Component s, int width) {
        int textWidth = font.width(s);
        return switch (TextFit.mode(textWidth, width)) {
            case PLAIN -> textWidth;
            case SQUEEZED -> width;
            case CUT -> font.width(fit(font, s, width));
        };
    }

    public static void small(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        float scale = smallScale();
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(font, s, 0, 0, color, false);
        g.pose().popMatrix();
    }

    public static void smallFitted(GuiGraphicsExtractor g, Font font, String s, int x, int y, int width, int color) {
        float scale = smallScale();
        int textWidth = (int) Math.ceil(font.width(s) * scale);
        switch (TextFit.mode(textWidth, width)) {
            case PLAIN -> small(g, font, s, x, y, color);
            case SQUEEZED -> {
                g.pose().pushMatrix();
                g.pose().translate(x, y);
                g.pose().scale(scale * TextFit.factor(textWidth, width), scale);
                g.text(font, s, 0, 0, color, false);
                g.pose().popMatrix();
            }
            case CUT -> small(g, font, fit(font, s, (int) (width / scale)), x, y, color);
        }
    }

    public static int smallWrapped(GuiGraphicsExtractor g, Font font, String s, int x, int bottom, int width, int color) {
        float scale = smallScale();
        List<FormattedCharSequence> lines = font.split(Component.literal(s), Math.max(1, (int) (width / scale)));
        int step = smallLineHeight();
        int y = bottom - lines.size() * step;
        for (FormattedCharSequence line : lines) {
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(scale, scale);
            g.text(font, line, 0, 0, color, false);
            g.pose().popMatrix();
            y += step;
        }
        return lines.size();
    }

    public static int smallBaseline() {
        return Math.round((Metrics.TEXT_HEIGHT - 1) * (1.0F - smallScale()));
    }

    public static float smallScale() {
        return smallScale(Minecraft.getInstance().getWindow().getGuiScale());
    }

    public static float smallScale(int guiScale) {
        int gui = Math.max(1, guiScale);
        int pixelsPerTexel = Math.max(1, Math.round(gui * SMALL_SCALE));
        return (float) pixelsPerTexel / gui;
    }

    public static int smallWidth(Font font, String s) {
        return (int) Math.ceil(font.width(s) * smallScale());
    }

    public static int smallLineHeight() {
        return (int) Math.ceil(Metrics.TEXT_HEIGHT * smallScale()) + 1;
    }

    public static int hint(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width) {
        return wrapped(g, font, text, x, y, width, Palette.TEXT_FAINT);
    }

    public static int wrapped(GuiGraphicsExtractor g, Font font, FormattedText text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = balanced(font, text, width);
        int row = y;
        for (FormattedCharSequence line : lines) {
            g.text(font, line, x, row, color, false);
            row += Metrics.LINE_HEIGHT;
        }
        return lines.size() * Metrics.LINE_HEIGHT;
    }

    public static int wrappedCentered(GuiGraphicsExtractor g, Font font, FormattedText text, int centerX, int y, int width, int color) {
        List<FormattedCharSequence> lines = balanced(font, text, width);
        int row = y;
        for (FormattedCharSequence line : lines) {
            g.text(font, line, centerX - font.width(line) / 2, row, color, false);
            row += Metrics.LINE_HEIGHT;
        }
        return lines.size() * Metrics.LINE_HEIGHT;
    }

    public static boolean pairFits(Font font, FormattedText label, FormattedText value, int width, int gap) {
        return font.width(label) + gap + font.width(value) <= width;
    }

    public static int pairHeight(Font font, FormattedText label, FormattedText value, int width, int gap) {
        if (pairFits(font, label, value, width, gap)) {
            return Metrics.LINE_HEIGHT;
        }
        int max = Math.max(1, width);
        return (font.split(label, max).size() + font.split(value, max).size()) * Metrics.LINE_HEIGHT;
    }

    public static int pair(GuiGraphicsExtractor g, Font font, FormattedText label, FormattedText value, int x, int y, int width, int gap, int labelColor,
                           int valueColor) {
        if (pairFits(font, label, value, width, gap)) {
            g.text(font, Language.getInstance().getVisualOrder(label), x, y, labelColor, false);
            g.text(font, Language.getInstance().getVisualOrder(value), x + width - font.width(value), y, valueColor, false);
            return Metrics.LINE_HEIGHT;
        }
        int max = Math.max(1, width);
        int row = y;
        for (FormattedCharSequence line : font.split(label, max)) {
            g.text(font, line, x, row, labelColor, false);
            row += Metrics.LINE_HEIGHT;
        }
        for (FormattedCharSequence line : font.split(value, max)) {
            g.text(font, line, x + width - font.width(line), row, valueColor, false);
            row += Metrics.LINE_HEIGHT;
        }
        return row - y;
    }

    public static int wrappedHeight(Font font, FormattedText text, int width) {
        return font.split(text, Math.max(1, width)).size() * Metrics.LINE_HEIGHT;
    }

    public static List<FormattedCharSequence> balanced(Font font, FormattedText text, int width) {
        int max = Math.max(1, width);
        List<FormattedCharSequence> lines = font.split(text, max);
        if (lines.size() < 2 || (!widow(lines) && !dashLeads(lines))) {
            return lines;
        }
        int floor = max * 3 / 4;
        for (int w = max - DASH_STEP; w >= floor; w -= DASH_STEP) {
            List<FormattedCharSequence> candidate = font.split(text, w);
            if (candidate.size() == lines.size() && !widow(candidate) && !dashLeads(candidate)) {
                return candidate;
            }
        }
        return lines;
    }

    private static boolean widow(List<FormattedCharSequence> lines) {
        boolean[] space = {false};
        boolean[] seen = {false};
        lines.getLast().accept((position, style, codepoint) -> {
            if (Character.isWhitespace(codepoint)) {
                space[0] |= seen[0];
            } else {
                seen[0] = true;
            }
            return true;
        });
        return !space[0];
    }

    private static boolean dashLeads(List<FormattedCharSequence> lines) {
        for (int i = 1; i < lines.size(); i++) {
            int[] first = {-1};
            lines.get(i).accept((position, style, codepoint) -> {
                first[0] = codepoint;
                return false;
            });
            if (first[0] == '\u2013' || first[0] == '\u2014') {
                return true;
            }
        }
        return false;
    }
}
