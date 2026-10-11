package de.lardtom.propwash.ui.client.widget;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.ui.NumberEntry;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedWidgets;
import de.lardtom.propwash.ui.client.theme.WidgetState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntConsumer;

public class NumberSpinner extends PanelWidget implements NumberInput {
    private static final int ARROW = 8;
    private static final int DRAG_PIXELS_PER_STEP = 4;

    private final DoubleSupplier getter;
    private final IntConsumer stepper;
    private final DoubleConsumer setter;
    private final @Nullable DoubleSupplier defaultValue;
    private final DoubleFunction<String> formatter;
    private final DoubleUnaryOperator fromDisplay;
    private final double compareEpsilon;
    private final NumberEditor editor = new NumberEditor(true);
    private double dragAccumulator;
    private boolean dragging;
    private boolean dragged;

    public NumberSpinner(int x, int y, int width, int height, Component label, DoubleSupplier getter, IntConsumer stepper,
                         DoubleConsumer setter, @Nullable DoubleSupplier defaultValue, DoubleFunction<String> formatter,
                         DoubleUnaryOperator fromDisplay, double compareEpsilon) {
        super(x, y, width, height, label);
        this.getter = getter;
        this.stepper = stepper;
        this.setter = setter;
        this.defaultValue = defaultValue;
        this.formatter = formatter;
        this.fromDisplay = fromDisplay;
        this.compareEpsilon = compareEpsilon;
    }

    public NumberSpinner range(double displayMin, double displayMax) {
        this.editor.range(displayMin, displayMax);
        return this;
    }

    public NumberSpinner integers() {
        this.editor.decimals(false);
        return this;
    }

    public NumberSpinner rangeFormat(DoubleFunction<String> format) {
        this.editor.rangeFormat(format);
        return this;
    }

    @Override
    public NumberEditor editor() {
        return this.editor;
    }

    @Override
    public boolean isEditing() {
        return this.editor.editing();
    }

    @Override
    public String shownValue() {
        return this.formatter.apply(this.getter.getAsDouble());
    }

    public @Nullable String editText() {
        return this.editor.text();
    }

    @Override
    public void cancelEdit() {
        this.editor.cancel();
    }

    @Override
    public void commitPending() {
        this.finishOrCancel();
    }

    public void startEdit(boolean selectAll) {
        this.editor.start(this.formatter.apply(this.getter.getAsDouble()), selectAll);
    }

    public NumberEntry.Result commitEdit() {
        if (!this.editor.editing()) {
            return new NumberEntry.Result(NumberEntry.Status.EMPTY, Double.NaN);
        }
        NumberEntry.Result result = this.editor.finish();
        if (result.usable()) {
            double raw = this.fromDisplay.applyAsDouble(result.value());
            if (Double.isFinite(raw)) {
                this.setter.accept(raw);
            }
        }
        return result;
    }

    private boolean modified() {
        return this.defaultValue != null && Math.abs(this.getter.getAsDouble() - this.defaultValue.getAsDouble()) > this.compareEpsilon;
    }

    @Override
    protected boolean acceptsRightClick() {
        return true;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (isRightClick(event)) {
            this.editor.cancel();
            if (this.defaultValue != null) {
                this.setter.accept(this.defaultValue.getAsDouble());
            }
            return;
        }
        int mult = stepMultiplier(event.hasShiftDown());
        if (event.x() < this.getX() + ARROW) {
            this.commitEdit();
            this.stepper.accept(-mult);
            return;
        }
        if (event.x() >= this.getX() + this.width - ARROW) {
            this.commitEdit();
            this.stepper.accept(mult);
            return;
        }
        if (this.editor.editing()) {
            if (doubleClick && this.editor.entry() != null) {
                this.editor.entry().selectAll();
            } else {
                this.editor.caretFromMouse(this.font(), event.x(), this.getX() + this.width / 2);
            }
            return;
        }
        if (doubleClick) {
            this.startEdit(true);
            return;
        }
        this.dragging = true;
        this.dragged = false;
        this.dragAccumulator = 0.0;
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        if (!this.dragging) {
            return;
        }
        this.dragAccumulator += dx;
        int steps = (int) (this.dragAccumulator / DRAG_PIXELS_PER_STEP);
        if (steps != 0) {
            this.dragAccumulator -= steps * DRAG_PIXELS_PER_STEP;
            this.dragged = true;
            this.stepper.accept(steps * stepMultiplier(event.hasShiftDown()));
        }
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        if (this.dragging && !this.dragged && this.isFocused() && !this.editor.editing()) {
            this.startEdit(true);
        }
        this.dragging = false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!this.isFocused() || !this.active) {
            return false;
        }
        return this.editor.charTyped(event.codepoint());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.active) {
            return false;
        }
        if (this.editor.editing()) {
            NumberEditor.Action action = this.editor.keyPressed(event);
            switch (action) {
                case COMMIT -> {
                    NumberEntry.Result result = this.editor.result();
                    if (result.status() == NumberEntry.Status.INVALID) {
                        return true;
                    }
                    if (result.status() == NumberEntry.Status.EMPTY) {
                        this.editor.cancel();
                    } else {
                        this.commitEdit();
                    }
                    return true;
                }
                case CANCEL -> {
                    this.editor.cancel();
                    return true;
                }
                case STEP_UP, STEP_DOWN -> {
                    this.finishOrCancel();
                    this.stepper.accept((action == NumberEditor.Action.STEP_UP ? 1 : -1) * stepMultiplier(event.hasShiftDown()));
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
        if (event.isUp() || event.isRight()) {
            this.stepper.accept(stepMultiplier(event.hasShiftDown()));
            return true;
        }
        if (event.isDown() || event.isLeft()) {
            this.stepper.accept(-stepMultiplier(event.hasShiftDown()));
            return true;
        }
        if (event.isConfirmation() || event.isSelectAll()) {
            this.startEdit(true);
            return true;
        }
        if (event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE) {
            this.startEdit(false);
            this.editor.keyPressed(event);
            return true;
        }
        if (event.isPaste()) {
            this.editor.startEmpty();
            this.editor.keyPressed(event);
            return true;
        }
        return NumberEditor.numericKey(event);
    }

    private void finishOrCancel() {
        NumberEntry.Result result = this.editor.result();
        if (result.usable()) {
            this.commitEdit();
        } else {
            this.editor.cancel();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.isFocused() || scrollY == 0.0) {
            return false;
        }
        this.finishOrCancel();
        this.stepper.accept((scrollY > 0 ? 1 : -1) * stepMultiplier(shiftDown()));
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        boolean was = this.isFocused();
        super.setFocused(focused);
        if (!focused) {
            this.finishOrCancel();
            this.dragging = false;
        }
        if (was != focused) {
            Minecraft.getInstance().onTextInputFocusChange(this, focused);
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x0 = this.getX();
        int y0 = this.getY();
        int x1 = x0 + this.width;
        int y1 = y0 + this.height;
        WidgetState state = this.state();
        boolean editing = this.editor.editing();
        ThemeDraw.field(g, x0, y0, x1, y1, state.withFocused(state.focused() || editing));
        int frame = this.editor.frameColor();
        if (frame != 0 && (editing || this.editor.noticeActive())) {
            g.outline(x0, y0, this.width, this.height, frame);
        }
        int textY = Metrics.textY(y0, this.height);
        if (this.active && (this.isHovered() || editing)) {
            boolean overLeft = this.isHovered() && mouseX < x0 + ARROW;
            boolean overRight = this.isHovered() && mouseX >= x1 - ARROW;
            ThemeText.text(g, this.font(), "‹", x0 + 2, textY, overLeft ? Palette.ACCENT : Palette.TEXT_MUTED);
            ThemeText.textRight(g, this.font(), "›", x1 - 2, textY, overRight ? Palette.ACCENT : Palette.TEXT_MUTED);
        }
        if (editing) {
            this.editor.extract(g, this.font(), x0 + this.width / 2, textY, this.width - 2 * ARROW - 4);
            if (this.isFocused()) {
                this.editor.extractHint(g, this.font(), this.getRectangle());
            }
            return;
        }
        int budget = this.active && this.isHovered() ? this.width - 2 * ARROW - 2 : this.width - 4;
        String text = ThemeText.fit(this.font(), this.formatter.apply(this.getter.getAsDouble()), budget);
        int color = ThemedWidgets.valueColor(state, this.modified());
        ThemeText.textCentered(g, this.font(), text, x0 + this.width / 2, textY, color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.empty().append(this.getMessage()).append(" ")
                .append(this.formatter.apply(this.getter.getAsDouble())));
    }
}
