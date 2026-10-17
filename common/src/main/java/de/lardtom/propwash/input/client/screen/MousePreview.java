package de.lardtom.propwash.input.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.input.MouseAxis;
import de.lardtom.propwash.input.MouseMode;
import de.lardtom.propwash.input.MouseSettings;
import de.lardtom.propwash.input.MouseSticks;
import de.lardtom.propwash.input.StickChannel;
import de.lardtom.propwash.input.StickMode;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.client.MouseCapture;
import de.lardtom.propwash.input.client.StickModes;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class MousePreview {
    public static final int GIMBAL_MIN = 28;
    public static final int GIMBAL_MAX = 56;
    public static final int PAD_MIN = 24;
    public static final int PAD_MAX = 40;
    private static final int LABEL_SPACE = 11;
    private static final int GAP = 4;

    private final MouseSticks sticks = new MouseSticks();
    private MouseSticks.Output output = MouseSticks.Output.CENTER;
    private MouseSticks.@Nullable Output shown;
    private @Nullable StickMode mode;
    private MouseCapture.Frame consumed = MouseCapture.Frame.EMPTY;
    private long lastNanos;
    private boolean dragging;
    private boolean acro;
    private int padX0;
    private int padY0;
    private int padX1;
    private int padY1;
    private int chipX0 = -1;
    private int chipY0;
    private int chipX1;
    private int chipY1;

    public static int height(int width, int maxHeight) {
        int gimbal = gimbalSize(width, maxHeight);
        return gimbal + LABEL_SPACE + LABEL_SPACE + padHeight(maxHeight - gimbal - 2 * LABEL_SPACE - GAP) + GAP;
    }

    private static int gimbalSize(int width, int maxHeight) {
        int byWidth = (width - 3 * GAP) / 2;
        int byHeight = maxHeight - 2 * LABEL_SPACE - GAP - PAD_MIN;
        return Math.clamp(Math.min(byWidth, byHeight), GIMBAL_MIN, GIMBAL_MAX);
    }

    private static int padHeight(int room) {
        return Math.clamp(room, PAD_MIN, PAD_MAX);
    }

    public void reset() {
        this.sticks.reset();
        this.output = MouseSticks.Output.CENTER;
        this.dragging = false;
        this.lastNanos = 0L;
    }

    public boolean dragging() {
        return this.dragging;
    }

    public boolean acro() {
        return this.acro;
    }

    public void setAcro(boolean acro) {
        this.acro = acro;
    }

    public void mode(@Nullable StickMode next) {
        this.mode = next;
    }

    public StickPositions sticks() {
        return new StickPositions(this.output.roll(), this.output.pitch(), this.output.yaw(), 0.0F);
    }

    public boolean overPad(double mouseX, double mouseY) {
        return mouseX >= this.padX0 && mouseX < this.padX1 && mouseY >= this.padY0 && mouseY < this.padY1;
    }

    public int[] padCenter() {
        return new int[]{(this.padX0 + this.padX1) / 2, (this.padY0 + this.padY1) / 2};
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (this.chipX0 >= 0 && mouseX >= this.chipX0 && mouseX < this.chipX1 && mouseY >= this.chipY0 && mouseY < this.chipY1) {
            this.acro = !this.acro;
            this.sticks.reset();
            return true;
        }
        if (this.overPad(mouseX, mouseY)) {
            this.dragging = true;
            this.consumed = MouseCapture.INSTANCE.lastFrame();
            return true;
        }
        return false;
    }

    public void mouseReleased() {
        this.dragging = false;
    }

    public void show(MouseSticks.@Nullable Output sticks) {
        this.shown = sticks;
    }

    public void feed(float dx, float dy, float dt, MouseSettings settings, boolean invertPitch) {
        this.output = this.sticks.update(dx, dy, dt, settings, !this.acro, invertPitch);
    }

    public int extract(GuiGraphicsExtractor g, Font font, int x, int y, int w, int maxHeight, MouseSettings settings, boolean invertPitch, int mouseX,
                       int mouseY) {
        long now = Util.getNanos();
        float dt = this.lastNanos == 0L ? 0.0F : Math.min(0.1F, (now - this.lastNanos) / 1.0E9F);
        this.lastNanos = now;
        MouseCapture.Frame frame = MouseCapture.INSTANCE.lastFrame();
        float dx = 0.0F;
        float dy = 0.0F;
        if (this.dragging && frame != this.consumed) {
            dx = frame.previewX();
            dy = frame.previewY();
        }
        this.consumed = frame;
        if (dt > 0.0F) {
            this.feed(dx, dy, dt, settings, invertPitch);
        }
        int gimbal = gimbalSize(w, maxHeight);
        int pad = padHeight(maxHeight - gimbal - 2 * LABEL_SPACE - GAP);
        int half = w / 2;
        int leftX = x + (half - gimbal) / 2;
        int rightX = x + half + (half - gimbal) / 2;
        boolean yaw = settings.xAxis == MouseAxis.YAW;
        MouseSticks.Output shown = this.shown != null && !this.dragging ? this.shown : this.output;
        StickMode stickMode = this.mode != null ? this.mode : StickModes.current();
        this.stick(g, stickMode, true, leftX, y, gimbal, shown, yaw);
        this.stick(g, stickMode, false, rightX, y, gimbal, shown, yaw);
        int labelY = y + gimbal + 2;
        Component left = stickLabel(stickMode, true, yaw);
        Component right = stickLabel(stickMode, false, yaw);
        ThemeText.fittedCentered(g, font, left, x + half / 2, labelY, half - 2, Palette.TEXT_MUTED);
        ThemeText.fittedCentered(g, font, right, x + half + half / 2, labelY, half - 2, Palette.TEXT_MUTED);
        Component mode = modeLine(settings, shown.holdY());
        ThemeText.fittedCentered(g, font, mode, x + w / 2, labelY + LABEL_SPACE, w, Palette.ACCENT);
        int padTop = labelY + 2 * LABEL_SPACE - 2 + GAP;
        this.drawPad(g, font, x, padTop, w, pad, settings, mouseX, mouseY);
        return padTop + pad - y;
    }

    private void drawPad(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, MouseSettings settings, int mouseX, int mouseY) {
        this.padX0 = x;
        this.padY0 = y;
        this.padX1 = x + w;
        this.padY1 = y + h;
        boolean hover = this.overPad(mouseX, mouseY);
        int border = this.dragging ? Palette.ACCENT : hover ? Palette.BORDER_STRONG : Palette.BORDER;
        ThemeDraw.framedBox(g, x, y, x + w, y + h, this.dragging ? Palette.FIELD_HOVER : Palette.FIELD, border);
        Component text = Component.translatable(this.dragging ? "propwash.input.mouse.pad.active" : "propwash.input.mouse.pad.idle");
        this.chipX0 = -1;
        int textRight = x + w - 4;
        if (settings.mode == MouseMode.AUTO) {
            Component chip = Component.translatable(this.acro ? "propwash.input.mouse.pad.acro" : "propwash.input.mouse.pad.angle");
            int chipWidth = font.width(chip) + 6;
            if (chipWidth < w / 2) {
                this.chipX1 = x + w - 2;
                this.chipX0 = this.chipX1 - chipWidth;
                this.chipY0 = y + 2;
                this.chipY1 = y + 2 + Metrics.LINE_HEIGHT + 1;
                boolean chipHover = mouseX >= this.chipX0 && mouseX < this.chipX1 && mouseY >= this.chipY0 && mouseY < this.chipY1;
                ThemeDraw.framedBox(g, this.chipX0, this.chipY0, this.chipX1, this.chipY1, chipHover ? Palette.BUTTON_HOVER : Palette.BUTTON, Palette.BORDER);
                ThemeText.text(g, font, chip, this.chipX0 + 3, this.chipY0 + 2, Palette.TEXT);
                textRight = this.chipX0 - 2;
            }
        }
        Component brief = Component.translatable(this.dragging ? "propwash.input.mouse.pad.active.short" : "propwash.input.mouse.pad.idle.short");
        int beside = Math.min(2, Math.max(1, (h - 4) / Metrics.LINE_HEIGHT));
        int below = this.chipX0 < 0 ? 0 : Math.min(2, (y + h - this.chipY1 - 2) / Metrics.LINE_HEIGHT);
        int[][] layouts = {{y, textRight, beside}, {this.chipX0 < 0 ? y : this.chipY1, x + w - 4, this.chipX0 < 0 ? beside : below}};
        List<FormattedCharSequence> split = List.of();
        int top = y;
        int right = textRight;
        int lines = 0;
        search:
        for (Component candidate : new Component[]{text, brief}) {
            for (int[] layout : layouts) {
                if (layout[2] < 1) {
                    continue;
                }
                List<FormattedCharSequence> wrapped = font.split(candidate, Math.max(20, layout[1] - (x + 4)));
                if (wrapped.size() <= layout[2]) {
                    split = wrapped;
                    top = layout[0];
                    right = layout[1];
                    lines = layout[2];
                    break search;
                }
            }
        }
        if (split.isEmpty()) {
            split = font.split(brief, Math.max(20, textRight - (x + 4)));
            lines = beside;
        }
        int count = ThemeText.shownLines(brief, split.size(), lines);
        int ty = top + (y + h - top - count * Metrics.LINE_HEIGHT) / 2 + 1;
        for (int i = 0; i < count; i++) {
            int lineWidth = font.width(split.get(i));
            int tx = Math.min(x + (w - lineWidth) / 2, right - lineWidth);
            g.text(font, split.get(i), Math.max(x + 4, tx), ty + i * Metrics.LINE_HEIGHT, this.dragging ? Palette.ACCENT : Palette.TEXT_MUTED, false);
        }
    }

    static Component modeLine(MouseSettings settings, boolean holding) {
        Component how = Component.translatable(holding ? "propwash.input.mouse.preview.hold" : "propwash.input.mouse.preview.rate");
        return Component.translatable("propwash.input.mouse.preview.mode", Component.translatable(settings.mode.translationKey()), how);
    }

    private void stick(GuiGraphicsExtractor g, StickMode mode, boolean left, int x0, int y0, int size, MouseSticks.Output shown, boolean yaw) {
        StickChannel horizontal = mode.channel(left, false);
        StickChannel vertical = mode.channel(left, true);
        gimbal(g, x0, y0, size, value(horizontal, shown), value(vertical, shown), color(horizontal, yaw), color(vertical, yaw));
    }

    private static float value(StickChannel channel, MouseSticks.Output shown) {
        return switch (channel) {
            case ROLL -> shown.roll();
            case PITCH -> shown.pitch();
            case YAW -> shown.yaw();
            case THROTTLE -> -1.0F;
        };
    }

    private static boolean mouseDriven(StickChannel channel, boolean yaw) {
        return switch (channel) {
            case PITCH -> true;
            case ROLL -> !yaw;
            case YAW -> yaw;
            case THROTTLE -> false;
        };
    }

    private static int color(StickChannel channel, boolean yaw) {
        if (channel == StickChannel.THROTTLE) {
            return 0;
        }
        if (channel == StickChannel.PITCH) {
            return Palette.PITCH;
        }
        return mouseDriven(channel, yaw) ? InputGui.channelColor(channel) : Palette.TEXT_FAINT;
    }

    static Component stickLabel(StickMode mode, boolean left, boolean yaw) {
        StickChannel vertical = mode.channel(left, true);
        StickChannel horizontal = mode.channel(left, false);
        boolean first = mouseDriven(vertical, yaw);
        boolean second = mouseDriven(horizontal, yaw);
        String key = second ? "propwash.input.mouse.preview.pair_mouse" : first ? "propwash.input.mouse.preview.pair_first_mouse"
                : "propwash.input.mouse.preview.pair";
        return Component.translatable(key, Component.translatable(vertical.translationKey()), Component.translatable(horizontal.translationKey()));
    }

    static void gimbal(GuiGraphicsExtractor g, int x0, int y0, int size, float x, float y, int colorX, int colorY) {
        ThemeDraw.framedBox(g, x0, y0, x0 + size, y0 + size, Palette.FIELD, Palette.BORDER);
        int cx = x0 + size / 2;
        int cy = y0 + size / 2;
        g.fill(x0 + 3, cy, x0 + size - 3, cy + 1, Palette.GRID);
        g.fill(cx, y0 + 3, cx + 1, y0 + size - 3, Palette.GRID);
        int travel = size / 2 - 5;
        int px = cx + Math.round(clamp(x) * travel);
        int py = cy - Math.round(clamp(y) * travel);
        if (px != cx) {
            g.fill(Math.min(cx, px), y0 + size - 3, Math.max(cx, px) + 1, y0 + size - 1, colorX);
        }
        if (py != cy && colorY != 0) {
            g.fill(x0 + size - 3, Math.min(cy, py), x0 + size - 1, Math.max(cy, py) + 1, colorY);
        }
        g.fill(px - 3, py - 3, px + 4, py + 4, Palette.withAlpha(Palette.SHADOW, 0xC0));
        g.fill(px - 2, py - 2, px + 3, py + 3, Palette.ACCENT);
    }

    private static float clamp(float v) {
        return Float.isNaN(v) ? 0.0F : Math.max(-1.0F, Math.min(1.0F, v));
    }
}
