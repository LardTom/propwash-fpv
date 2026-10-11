package de.lardtom.propwash.ui.client.widget;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.ui.NumberEntry;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemedWidgets;
import de.lardtom.propwash.ui.client.theme.WidgetState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class SliderTyping {
    private static final int TEXT_PAD = 6;
    private static final int SHIFT_STEPS = 10;

    public interface Target {
        String typingText();

        void applyTyped(double shown);

        void stepTyped(int steps);
    }

    private final NumberEditor editor = new NumberEditor(true);
    private boolean enabled;

    public NumberEditor editor() {
        return this.editor;
    }

    public boolean enabled() {
        return this.enabled;
    }

    public void enable(double displayMin, double displayMax, boolean decimals) {
        this.enabled = true;
        this.editor.range(displayMin, displayMax);
        this.editor.decimals(decimals);
    }

    public void disable() {
        this.enabled = false;
        this.editor.cancel();
    }

    public boolean editing() {
        return this.editor.editing();
    }

    public @Nullable String text() {
        return this.editor.text();
    }

    public void cancel() {
        this.editor.cancel();
    }

    public void start(Target target, boolean selectAll) {
        this.editor.start(target.typingText(), selectAll);
    }

    public void commitPending(Target target) {
        if (!this.editor.editing()) {
            return;
        }
        if (!this.editor.result().usable()) {
            this.editor.cancel();
            return;
        }
        this.commit(target);
    }

    public NumberEntry.Result commit(Target target) {
        NumberEntry.Result result = this.editor.finish();
        if (result.usable() && Double.isFinite(result.value())) {
            target.applyTyped(result.value());
        }
        return result;
    }

    public boolean charTyped(boolean focused, boolean active, int codepoint) {
        return this.enabled && focused && active && this.editor.charTyped(codepoint);
    }

    public boolean keyPressed(Target target, KeyEvent event) {
        if (this.editor.editing()) {
            NumberEditor.Action action = this.editor.keyPressed(event);
            switch (action) {
                case COMMIT -> {
                    NumberEntry.Status status = this.editor.result().status();
                    if (status == NumberEntry.Status.EMPTY) {
                        this.editor.cancel();
                    } else if (status != NumberEntry.Status.INVALID) {
                        this.commit(target);
                    }
                    return true;
                }
                case CANCEL -> {
                    this.editor.cancel();
                    return true;
                }
                case STEP_UP, STEP_DOWN -> {
                    this.commitPending(target);
                    int steps = event.hasShiftDown() ? SHIFT_STEPS : 1;
                    target.stepTyped(action == NumberEditor.Action.STEP_UP ? steps : -steps);
                    return true;
                }
                case IGNORED -> {
                    return false;
                }
                default -> {
                    return true;
                }
            }
        }
        if (!this.enabled) {
            return false;
        }
        if (event.isConfirmation() || event.isSelectAll()) {
            this.start(target, true);
            return true;
        }
        if (event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE) {
            this.start(target, false);
            this.editor.keyPressed(event);
            return true;
        }
        if (event.isPaste()) {
            this.editor.startEmpty();
            this.editor.keyPressed(event);
            return true;
        }
        return false;
    }

    public boolean swallows(KeyEvent event) {
        return this.enabled && NumberEditor.numericKey(event);
    }

    public void focusChanged(GuiEventListener widget, Target target, boolean was, boolean focused) {
        if (!focused) {
            this.commitPending(target);
        }
        if (this.enabled && was != focused) {
            Minecraft.getInstance().onTextInputFocusChange(widget, focused);
        }
    }

    public void caret(Font font, double mouseX, int x0, int x1) {
        this.editor.caretFromMouse(font, mouseX, x0 + (x1 - x0) / 2);
    }

    public void extractField(GuiGraphicsExtractor g, Font font, @Nullable Component label, int labelWidth, int x, int y, int width, int height,
                             WidgetState state, boolean focused) {
        int tx0 = x + Math.max(0, labelWidth);
        int tx1 = x + width;
        ThemedWidgets.label(g, font, labelWidth > 0 ? label : null, x, y, height, labelWidth, state);
        ThemeDraw.field(g, tx0, y, tx1, y + height, state.withFocused(true));
        int frame = this.editor.frameColor();
        if (frame != 0) {
            g.outline(tx0, y, tx1 - tx0, height, frame);
        }
        this.editor.extract(g, font, tx0 + (tx1 - tx0) / 2, Metrics.textY(y, height), tx1 - tx0 - TEXT_PAD);
        if (focused) {
            this.editor.extractHint(g, font, new ScreenRectangle(tx0, y, tx1 - tx0, height));
        }
    }

    public void extractNotice(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        if (!this.editor.editing() && this.editor.noticeActive() && x1 > x0 && y1 > y0) {
            g.outline(x0, y0, x1 - x0, y1 - y0, this.editor.noticeColor());
        }
    }
}
