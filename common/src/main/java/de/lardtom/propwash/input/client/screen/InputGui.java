package de.lardtom.propwash.input.client.screen;

import de.lardtom.propwash.input.ChannelMath;
import de.lardtom.propwash.input.StickChannel;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.SwitchSource;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

public final class InputGui {
    public static final int TEXT = Palette.TEXT;
    public static final int DIM = Palette.TEXT_MUTED;
    public static final int FAINT = Palette.TEXT_FAINT;
    public static final int GOOD = Palette.SUCCESS;
    public static final int WARN = Palette.WARNING;
    public static final int BAD = Palette.ERROR;
    public static final int ACCENT = Palette.ACCENT;
    public static final int BAR_TICK = Palette.CROSSHAIR;
    public static final int AUX = Palette.AUX;
    public static final int HIGHLIGHT = Palette.ACCENT;
    public static final int ROW_HEIGHT = 11;
    public static final int VALUE_WIDTH = 28;
    public static final int AXIS_BAR_MIN = 16;
    public static final int INVERT_WIDTH = Metrics.TOGGLE_WIDTH;

    private InputGui() {
    }

    public static int channelColor(StickChannel channel) {
        return switch (channel) {
            case ROLL -> Palette.ROLL;
            case PITCH -> Palette.PITCH;
            case YAW -> Palette.YAW;
            case THROTTLE -> Palette.THROTTLE;
        };
    }

    public static float stickValue(StickPositions sticks, StickChannel channel) {
        return switch (channel) {
            case ROLL -> sticks.roll();
            case PITCH -> sticks.pitch();
            case YAW -> sticks.yaw();
            case THROTTLE -> sticks.throttle();
        };
    }

    public static int stickMicros(StickPositions sticks, StickChannel channel) {
        return channel == StickChannel.THROTTLE ? ChannelMath.throttleMicros(sticks.throttle()) : ChannelMath.centeredMicros(stickValue(sticks, channel));
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        ThemeDraw.panel(g, x, y, x + w, y + h);
    }

    public static void microsBar(GuiGraphicsExtractor g, Font font, int x, int y, int w, Component label, int labelWidth, int micros, int color, boolean highlight) {
        int labelColor = highlight ? HIGHLIGHT : TEXT;
        ThemeText.fitted(g, font, label, x, y + 1, Math.max(1, labelWidth - 2), labelColor);
        int barX0 = x + labelWidth;
        int barX1 = x + w - VALUE_WIDTH - 2;
        if (barX1 - barX0 < 10) {
            barX1 = barX0 + 10;
        }
        track(g, barX0, y, barX1, y + 9, highlight);
        float fraction = ChannelMath.fraction(micros);
        int fillEnd = barX0 + 1 + Math.round((barX1 - barX0 - 2) * fraction);
        g.fill(barX0 + 1, y + 1, fillEnd, y + 8, color);
        int mid = (barX0 + barX1) / 2;
        g.verticalLine(mid, y, y + 8, BAR_TICK);
        String value = Integer.toString(micros);
        g.text(font, value, x + w - font.width(value), y + 1, highlight ? HIGHLIGHT : DIM, false);
    }

    public static void rawBar(GuiGraphicsExtractor g, Font font, int x, int y, int w, Component label, int labelWidth, float value, int color, boolean highlight) {
        microsBar(g, font, x, y, w, label, labelWidth, ChannelMath.centeredMicros(value), color, highlight);
    }

    public static void track(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, boolean highlight) {
        ThemeDraw.track(g, x0, y0, x1, y1, highlight);
    }

    public static int markerX(int x0, int x1, float centeredValue) {
        float fraction = (Math.max(-1.0F, Math.min(1.0F, centeredValue)) + 1.0F) * 0.5F;
        return x0 + 1 + Math.round((x1 - x0 - 3) * fraction);
    }

    public static int rangeX(int x0, int x1, float value) {
        float limit = ChannelMath.RANGE_LIMIT;
        float v = Float.isNaN(value) ? 0.0F : Math.max(-limit, Math.min(limit, value));
        float fraction = (v + limit) / (2.0F * limit);
        return x0 + 1 + Math.round((x1 - x0 - 3) * fraction);
    }

    public static float rangeValueAt(double mouseX, int x0, int x1) {
        float limit = ChannelMath.RANGE_LIMIT;
        float fraction = (float) ((mouseX - x0 - 1) / Math.max(1.0, x1 - x0 - 3));
        return ChannelMath.snapRange(fraction * 2.0F * limit - limit);
    }

    public static void rangeBar(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float low, float high, float value, boolean active, boolean hasValue) {
        track(g, x0, y0, x1, y1, false);
        for (int micros = ChannelMath.MIN_US; micros <= ChannelMath.MAX_US; micros += 500) {
            int tx = rangeX(x0, x1, ChannelMath.fromRangeMicros(micros));
            g.verticalLine(tx, y0 + 1, y1 - 1, Palette.withAlpha(Palette.TEXT, 0x40));
        }
        int lowX = rangeX(x0, x1, low);
        int highX = rangeX(x0, x1, high) + 1;
        g.fill(lowX, y0 + 1, Math.max(lowX + 1, highX), y1 - 1, active ? Palette.withAlpha(Palette.SUCCESS, 0xC0) : Palette.withAlpha(Palette.WARNING, 0xA0));
        g.verticalLine(lowX, y0 - 1, y1, TEXT);
        g.verticalLine(highX, y0 - 1, y1, TEXT);
        if (hasValue) {
            int vx = rangeX(x0, x1, value);
            g.fill(vx, y0 - 2, vx + 2, y1 + 2, active ? GOOD : TEXT);
        }
    }

    public static String fit(Font font, String text, int width) {
        return ThemeText.fit(font, text, width);
    }

    public static void led(GuiGraphicsExtractor g, int x, int y, boolean on) {
        ThemeDraw.led(g, x, y, on);
    }

    public static int buttonGrid(GuiGraphicsExtractor g, Font font, int x, int y, int w, boolean[] buttons, int mouseX, int mouseY, IntFunction<Component> label, int highlight) {
        if (buttons.length == 0) {
            return 0;
        }
        int cell = 10;
        int perRow = Math.max(1, w / cell);
        int rows = (buttons.length + perRow - 1) / perRow;
        for (int i = 0; i < buttons.length; i++) {
            int cx = x + (i % perRow) * cell;
            int cy = y + (i / perRow) * cell;
            boolean on = buttons[i];
            ThemeDraw.framedBox(g, cx, cy, cx + 8, cy + 8, on ? Palette.SUCCESS : Palette.BUTTON,
                    i == highlight ? Palette.ACCENT : (on ? Palette.SUCCESS_BORDER : Palette.BORDER_STRONG));
            if (mouseX >= cx && mouseX < cx + 8 && mouseY >= cy && mouseY < cy + 8) {
                ThemeText.tooltip(g, font, label.apply(i), mouseX, mouseY);
            }
        }
        return rows * cell;
    }

    public static int stickLabelWidth(Font font) {
        int width = 0;
        for (StickChannel channel : StickChannel.values()) {
            width = Math.max(width, font.width(Component.translatable(channel.translationKey())));
        }
        return width + 8;
    }

    public static int stickBars(GuiGraphicsExtractor g, Font font, int x, int y, int w, StickPositions sticks) {
        int labelWidth = stickLabelWidth(font);
        int row = y;
        for (StickChannel channel : StickChannel.values()) {
            microsBar(g, font, x, row, w, Component.translatable(channel.translationKey()), labelWidth, stickMicros(sticks, channel), channelColor(channel), false);
            row += ROW_HEIGHT;
        }
        return row - y;
    }

    public static int axisBars(GuiGraphicsExtractor g, Font font, int x, int y, int w, int maxHeight, float[] axes, int highlight,
                               boolean[] dimmed, IntFunction<Component> label) {
        int count = axes.length;
        if (count == 0) {
            return 0;
        }
        int rows = Math.max(1, maxHeight / ROW_HEIGHT);
        int columns = (count + rows - 1) / rows;
        int maxColumns = Math.max(1, (w + 6) / 96);
        if (columns > maxColumns) {
            columns = maxColumns;
            count = Math.min(count, rows * columns);
        }
        rows = Math.min(rows, count);
        int columnWidth = (w - (columns - 1) * 6) / columns;
        int labelWidth = 0;
        for (int i = 0; i < count; i++) {
            labelWidth = Math.max(labelWidth, font.width(label.apply(i)));
        }
        labelWidth = Math.min(labelWidth + 4, columnWidth / 2);
        for (int i = 0; i < count; i++) {
            int column = i / rows;
            int row = i % rows;
            int bx = x + column * (columnWidth + 6);
            int by = y + row * ROW_HEIGHT;
            boolean dim = i < dimmed.length && dimmed[i];
            int color = i == highlight ? HIGHLIGHT : (dim ? FAINT : AUX);
            rawBar(g, font, bx, by, columnWidth, label.apply(i), labelWidth, axes[i], color, i == highlight);
        }
        return rows * ROW_HEIGHT;
    }

    public static int axisGrid(GuiGraphicsExtractor g, Font font, int x, int y, int w, float[] axes, int highlight, boolean[] dimmed,
                               IntFunction<Component> label) {
        int count = axes.length;
        if (count == 0) {
            return 0;
        }
        int labelWidth = 0;
        for (int i = 0; i < count; i++) {
            labelWidth = Math.max(labelWidth, font.width(label.apply(i)));
        }
        labelWidth += 4;
        int columns = axisColumns(w, labelWidth, count);
        int rows = (count + columns - 1) / columns;
        int columnWidth = (w - (columns - 1) * 6) / columns;
        labelWidth = Math.min(labelWidth, Math.max(columnWidth / 2, columnWidth - VALUE_WIDTH - 2 - AXIS_BAR_MIN));
        for (int i = 0; i < count; i++) {
            int bx = x + (i / rows) * (columnWidth + 6);
            int by = y + (i % rows) * ROW_HEIGHT;
            boolean dim = i < dimmed.length && dimmed[i];
            int color = i == highlight ? HIGHLIGHT : (dim ? FAINT : AUX);
            rawBar(g, font, bx, by, columnWidth, label.apply(i), labelWidth, axes[i], color, i == highlight);
        }
        return rows * ROW_HEIGHT;
    }

    public static int axisColumns(int width, int labelWidth, int count) {
        int columns = Math.max(1, Math.min(count, (width + 6) / 96));
        while (columns > 1 && (width - (columns - 1) * 6) / columns < labelWidth + AXIS_BAR_MIN + VALUE_WIDTH + 2) {
            columns--;
        }
        return columns;
    }

    public static int wrapped(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int w, int color) {
        return ThemeText.wrapped(g, font, text, x, y, w, color);
    }

    public static int keyTable(GuiGraphicsExtractor g, Font font, int x, int y, int w, int bottom, List<KeyRow> rows) {
        int labelWidth = 0;
        for (KeyRow row : rows) {
            labelWidth = Math.max(labelWidth, font.width(row.label()));
        }
        int keysWidth = 0;
        for (KeyRow row : rows) {
            keysWidth = Math.max(keysWidth, font.width(row.keys()));
        }
        labelWidth = Math.min(labelWidth + 8, Math.max(w / 2, w - keysWidth));
        int ty = y;
        for (KeyRow row : rows) {
            if (ty + Metrics.TEXT_HEIGHT > bottom) {
                break;
            }
            if (font.width(row.label()) <= labelWidth - 4 && font.width(row.keys()) <= w - labelWidth) {
                g.text(font, row.label(), x, ty, DIM, false);
                g.text(font, row.keys(), x + labelWidth, ty, ACCENT, false);
                ty += ROW_HEIGHT;
                continue;
            }
            for (FormattedCharSequence line : font.split(row.label(), Math.max(1, w))) {
                g.text(font, line, x, ty, DIM, false);
                ty += ROW_HEIGHT;
            }
            for (FormattedCharSequence line : font.split(row.keys(), Math.max(1, w - 8))) {
                g.text(font, line, x + 8, ty, ACCENT, false);
                ty += ROW_HEIGHT;
            }
        }
        return ty - y;
    }

    public static CycleButton<Boolean> invertToggle(int x, int y, int width, int height, SwitchFunction function, SwitchAssignment binding,
                                                    Consumer<Boolean> onChange) {
        return CycleButton.onOffBuilder(binding.invert)
                .displayState(CycleButton.DisplayState.HIDE)
                .withTooltip(v -> ThemeText.tip(invertTooltip(function, binding)))
                .create(x, y, Math.max(INVERT_WIDTH, width), height, Component.translatable("propwash.input.modes.invert"), (b, v) -> onChange.accept(v));
    }

    public static Component invertTooltip(SwitchFunction function, SwitchAssignment binding) {
        String kind = binding.toggle ? "toggle" : binding.source == SwitchSource.AXIS ? "axis" : "button";
        MutableComponent text = Component.translatable("propwash.input.modes.invert.tooltip_" + kind);
        if (function == SwitchFunction.ARM) {
            text.append(" ").append(Component.translatable("propwash.input.modes.invert.tooltip_arm"));
        }
        return text;
    }

    public record KeyRow(Component label, Component keys) {
    }
}
