package de.lardtom.propwash.ui.client.widget;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.content.Numbers;
import de.lardtom.propwash.ui.NumberEntry;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.function.DoubleFunction;

public final class NumberEditor {
    private static final long NOTICE_MILLIS = 1600L;
    private static final int RANGE_DECIMALS = 3;
    private static final int KEY_KP_MINUS = 86;
    private static final int KEY_KP_PERIOD = 99;

    public enum Action {
        IGNORED,
        HANDLED,
        COMMIT,
        CANCEL,
        STEP_UP,
        STEP_DOWN
    }

    private boolean decimals;
    private double min = Double.NEGATIVE_INFINITY;
    private double max = Double.POSITIVE_INFINITY;
    private DoubleFunction<String> rangeFormat = v -> Numbers.of(v, RANGE_DECIMALS).getString();
    private @Nullable NumberEntry entry;
    private long noticeUntil;
    private int noticeColor = Palette.WARNING;

    public NumberEditor(boolean decimals) {
        this.decimals = decimals;
    }

    public void decimals(boolean decimals) {
        this.decimals = decimals;
    }

    public boolean allowsDecimals() {
        return this.decimals;
    }

    public void range(double min, double max) {
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
    }

    public void rangeFormat(DoubleFunction<String> format) {
        this.rangeFormat = format;
    }

    public boolean hasRange() {
        return Double.isFinite(this.min) && Double.isFinite(this.max);
    }

    public double min() {
        return this.min;
    }

    public double max() {
        return this.max;
    }

    public boolean editing() {
        return this.entry != null;
    }

    public @Nullable String text() {
        return this.entry == null ? null : this.entry.text();
    }

    public @Nullable NumberEntry entry() {
        return this.entry;
    }

    public static char separator() {
        return NumberEntry.separatorOf(Component.translatable("propwash.content.number.decimal", "1", "5").getString());
    }

    public void start(String formatted, boolean selectAll) {
        char separator = separator();
        this.entry = new NumberEntry(NumberEntry.numericPart(formatted, separator), separator, this.decimals, !Double.isFinite(this.min) || this.min < 0.0);
        if (selectAll) {
            this.entry.selectAll();
        }
    }

    public void startEmpty() {
        this.start("", false);
    }

    public void cancel() {
        this.entry = null;
    }

    public NumberEntry.Result result() {
        return this.entry == null ? new NumberEntry.Result(NumberEntry.Status.EMPTY, Double.NaN) : this.entry.evaluate(this.min, this.max);
    }

    public NumberEntry.Result finish() {
        NumberEntry.Result result = this.result();
        this.entry = null;
        if (result.status() == NumberEntry.Status.CLAMPED) {
            this.notice(Component.translatable("propwash.ui.number.clamped", this.rangeFormat.apply(result.value()),
                    this.rangeFormat.apply(this.min), this.rangeFormat.apply(this.max)), Palette.WARNING);
        }
        return result;
    }

    private void notice(Component message, int color) {
        this.noticeUntil = Util.getMillis() + NOTICE_MILLIS;
        this.noticeColor = color;
        ConfiguratorScreen screen = ConfiguratorScreen.active();
        if (screen != null) {
            screen.flash(message, color);
        }
    }

    public boolean noticeActive() {
        return Util.getMillis() < this.noticeUntil;
    }

    public int noticeColor() {
        return this.noticeColor;
    }

    public boolean charTyped(int codepoint) {
        if (!NumberEntry.numericChar(codepoint)) {
            return false;
        }
        if (this.entry == null) {
            this.startEmpty();
        }
        this.entry.type(codepoint);
        return true;
    }

    public static boolean numericKey(KeyEvent event) {
        int key = event.key();
        return key >= InputConstants.KEY_1 && key <= InputConstants.KEY_0
                || key >= InputConstants.KEY_NUMPAD1 && key <= InputConstants.KEY_NUMPAD0
                || key == InputConstants.KEY_MINUS || key == InputConstants.KEY_PERIOD || key == InputConstants.KEY_COMMA
                || key == KEY_KP_MINUS || key == KEY_KP_PERIOD || key == InputConstants.KEY_NUMPADCOMMA;
    }

    public Action keyPressed(KeyEvent event) {
        NumberEntry e = this.entry;
        if (e == null) {
            return Action.IGNORED;
        }
        boolean shift = event.hasShiftDown();
        boolean word = event.hasControlDownWithQuirk();
        if (event.isConfirmation()) {
            return Action.COMMIT;
        }
        if (event.isEscape()) {
            return Action.CANCEL;
        }
        if (event.isUp()) {
            return Action.STEP_UP;
        }
        if (event.isDown()) {
            return Action.STEP_DOWN;
        }
        if (event.isCycleFocus()) {
            return Action.IGNORED;
        }
        if (event.isSelectAll()) {
            e.selectAll();
            return Action.HANDLED;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (event.isCopy()) {
            if (e.hasSelection()) {
                minecraft.keyboardHandler.setClipboard(e.selectedText());
            }
            return Action.HANDLED;
        }
        if (event.isCut()) {
            if (e.hasSelection()) {
                minecraft.keyboardHandler.setClipboard(e.cut());
            }
            return Action.HANDLED;
        }
        if (event.isPaste()) {
            e.insert(minecraft.keyboardHandler.getClipboard());
            return Action.HANDLED;
        }
        switch (event.key()) {
            case InputConstants.KEY_BACKSPACE -> e.backspace(word);
            case InputConstants.KEY_DELETE -> e.delete(word);
            case InputConstants.KEY_LEFT -> {
                if (word) {
                    e.home(shift);
                } else {
                    e.left(shift);
                }
            }
            case InputConstants.KEY_RIGHT -> {
                if (word) {
                    e.end(shift);
                } else {
                    e.right(shift);
                }
            }
            case InputConstants.KEY_HOME -> e.home(shift);
            case InputConstants.KEY_END -> e.end(shift);
            default -> {
            }
        }
        return Action.HANDLED;
    }

    public void caretFromMouse(Font font, double mouseX, int centerX) {
        NumberEntry e = this.entry;
        if (e == null) {
            return;
        }
        String text = e.text();
        int left = centerX - font.width(text) / 2;
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i <= text.length(); i++) {
            double distance = Math.abs(left + font.width(text.substring(0, i)) - mouseX);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        e.moveTo(best, false);
    }

    public void extract(GuiGraphicsExtractor g, Font font, int centerX, int textY, int budget) {
        NumberEntry e = this.entry;
        if (e == null) {
            return;
        }
        String text = e.text();
        if (font.width(text) > budget) {
            text = ThemeText.fit(font, text, budget);
        }
        int left = centerX - font.width(text) / 2;
        int length = text.length();
        int selStart = Math.min(length, e.selectionStart());
        int selEnd = Math.min(length, e.selectionEnd());
        if (selEnd > selStart) {
            int x0 = left + font.width(text.substring(0, selStart));
            int x1 = left + font.width(text.substring(0, selEnd));
            g.fill(x0, textY - 1, x1 + 1, textY + 9, Palette.ACCENT_DIM);
        }
        NumberEntry.Result result = this.result();
        int color = result.status() == NumberEntry.Status.INVALID ? Palette.ERROR
                : result.status() == NumberEntry.Status.CLAMPED ? Palette.WARNING : Palette.TEXT;
        ThemeText.text(g, font, text, left, textY, color);
        boolean caret = (Util.getMillis() / 400L) % 2L == 0L;
        if (caret) {
            int caretX = left + font.width(text.substring(0, Math.min(length, e.caret()))) - (e.caret() == 0 ? 1 : 0);
            g.fill(caretX, textY - 1, caretX + 1, textY + 9, Palette.ACCENT);
        }
    }

    public int frameColor() {
        if (this.entry == null) {
            return this.noticeActive() ? this.noticeColor : 0;
        }
        return switch (this.result().status()) {
            case INVALID -> Palette.ERROR;
            case CLAMPED -> Palette.WARNING;
            default -> Palette.ACCENT;
        };
    }

    public @Nullable Component hint() {
        if (this.entry == null) {
            return null;
        }
        NumberEntry.Result result = this.result();
        Component range = this.hasRange() ? Component.translatable("propwash.ui.number.range", this.rangeFormat.apply(this.min),
                this.rangeFormat.apply(this.max)) : null;
        Component keys = Component.translatable("propwash.ui.number.keys");
        return switch (result.status()) {
            case INVALID -> ThemeText.colored(Component.translatable("propwash.ui.number.invalid"), Palette.ERROR);
            case CLAMPED -> ThemeText.colored(Component.translatable("propwash.ui.number.will_clamp", this.rangeFormat.apply(result.value()),
                    this.rangeFormat.apply(this.min), this.rangeFormat.apply(this.max)), Palette.WARNING);
            default -> range == null ? ThemeText.muted(keys) : Component.empty().append(range).append("\n").append(ThemeText.muted(keys));
        };
    }

    public void extractHint(GuiGraphicsExtractor g, Font font, ScreenRectangle widget) {
        ThemeText.tooltipBelow(g, font, this.hint(), 170, widget);
    }
}
