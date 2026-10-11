package de.lardtom.propwash.ui.client.widget;

import de.lardtom.propwash.ui.NumberEntry;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.ThemedWidgets;
import de.lardtom.propwash.ui.client.theme.WidgetState;
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

public class ValueSlider extends PanelWidget implements NumberInput, SliderTyping.Target {
    private final int labelWidth;
    private final double min;
    private final double max;
    private final double step;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final @Nullable DoubleSupplier defaultValue;
    private final DoubleFunction<String> formatter;
    private final SliderTyping typing = new SliderTyping();
    private DoubleUnaryOperator fromDisplay = DoubleUnaryOperator.identity();
    private boolean dragging;

    public ValueSlider(int x, int y, int width, int height, Component label, int labelWidth, double min, double max, double step,
                       DoubleSupplier getter, DoubleConsumer setter, @Nullable DoubleSupplier defaultValue, DoubleFunction<String> formatter) {
        super(x, y, width, height, label);
        this.labelWidth = Math.max(0, Math.min(labelWidth, width - 30));
        this.min = min;
        this.max = Math.max(min + step, max);
        this.step = step;
        this.getter = getter;
        this.setter = setter;
        this.defaultValue = defaultValue;
        this.formatter = formatter;
    }

    public ValueSlider typed(DoubleUnaryOperator fromDisplay, double displayMin, double displayMax, boolean decimals) {
        this.fromDisplay = fromDisplay;
        this.typing.enable(displayMin, displayMax, decimals);
        return this;
    }

    @Override
    public NumberEditor editor() {
        return this.typing.editor();
    }

    @Override
    public boolean typeable() {
        return this.typing.enabled();
    }

    @Override
    public boolean isEditing() {
        return this.typing.editing();
    }

    public @Nullable String editText() {
        return this.typing.text();
    }

    @Override
    public String shownValue() {
        return this.formatter.apply(this.value());
    }

    @Override
    public void cancelEdit() {
        this.typing.cancel();
    }

    @Override
    public void commitPending() {
        this.typing.commitPending(this);
    }

    public NumberEntry.Result commitEdit() {
        return this.typing.commit(this);
    }

    public void startEdit(boolean selectAll) {
        this.typing.start(this, selectAll);
    }

    @Override
    public String typingText() {
        return this.formatter.apply(this.value());
    }

    @Override
    public void applyTyped(double shown) {
        double raw = this.fromDisplay.applyAsDouble(shown);
        if (Double.isFinite(raw)) {
            this.setter.accept(Math.max(this.min, Math.min(this.max, raw)));
        }
    }

    @Override
    public void stepTyped(int steps) {
        this.stepBy(steps);
    }

    public boolean labelFits() {
        return this.labelWidth <= 0 || this.font().width(this.getMessage()) <= this.labelWidth - 4;
    }

    public double value() {
        return this.getter.getAsDouble();
    }

    private int trackLeft() {
        return this.getX() + this.labelWidth;
    }

    private int trackWidth() {
        return this.width - this.labelWidth;
    }

    private double fraction() {
        double v = this.value();
        if (!Double.isFinite(v)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (v - this.min) / (this.max - this.min)));
    }

    private void setFromMouse(double mouseX) {
        double t = Metrics.sliderFraction(mouseX, this.trackLeft(), this.trackWidth());
        double raw = this.min + t * (this.max - this.min);
        double snapped = this.min + Math.rint((raw - this.min) / this.step) * this.step;
        this.set(snapped);
    }

    private void set(double v) {
        double clamped = Math.max(this.min, Math.min(this.max, v));
        if (Math.abs(clamped - this.value()) > this.step * 1e-3) {
            this.setter.accept(clamped);
        }
    }

    private void stepBy(int steps) {
        this.set(this.value() + steps * this.step);
    }

    private boolean modified() {
        return this.defaultValue != null && Math.abs(this.value() - this.defaultValue.getAsDouble()) > this.step * 1e-3;
    }

    @Override
    protected boolean acceptsRightClick() {
        return true;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (this.typing.editing()) {
            if (isRightClick(event) || event.x() < this.trackLeft()) {
                this.typing.cancel();
            } else {
                this.typing.caret(this.font(), event.x(), this.trackLeft(), this.getX() + this.width);
                return;
            }
        }
        if (isRightClick(event)) {
            if (this.defaultValue != null) {
                this.setter.accept(this.defaultValue.getAsDouble());
            }
            return;
        }
        if (event.x() < this.trackLeft()) {
            return;
        }
        this.dragging = true;
        this.setFromMouse(event.x());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        if (this.dragging) {
            this.setFromMouse(event.x());
        }
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        this.dragging = false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return this.typing.charTyped(this.isFocused(), this.active, event.codepoint());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.active) {
            return false;
        }
        boolean wasEditing = this.typing.editing();
        if (this.typing.keyPressed(this, event)) {
            return true;
        }
        if (wasEditing) {
            return false;
        }
        if (event.isLeft() || event.isDown()) {
            this.stepBy(-stepMultiplier(event.hasShiftDown()));
            return true;
        }
        if (event.isRight() || event.isUp()) {
            this.stepBy(stepMultiplier(event.hasShiftDown()));
            return true;
        }
        return this.typing.swallows(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.isFocused() || scrollY == 0.0) {
            return false;
        }
        this.commitPending();
        this.stepBy((scrollY > 0 ? 1 : -1) * stepMultiplier(shiftDown()));
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        boolean was = this.isFocused();
        super.setFocused(focused);
        if (!focused) {
            this.dragging = false;
        }
        this.typing.focusChanged(this, this, was, focused);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        WidgetState state = this.state();
        if (this.typing.editing()) {
            this.typing.extractField(g, this.font(), this.getMessage(), this.labelWidth, this.getX(), this.getY(), this.width, this.height, state,
                    this.isFocused());
            return;
        }
        ThemedWidgets.slider(g, this.font(), this.getX(), this.getY(), this.width, this.height, this.labelWidth,
                this.labelWidth > 0 ? this.getMessage() : null, Component.literal(this.formatter.apply(this.value())), this.fraction(),
                new WidgetState(state.active(), state.hovered(), state.focused(), this.dragging), this.modified());
        this.typing.extractNotice(g, this.trackLeft(), this.getY(), this.getX() + this.width, this.getY() + this.height);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.slider",
                Component.empty().append(this.getMessage()).append(" ").append(this.formatter.apply(this.value()))));
    }
}
