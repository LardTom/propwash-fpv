package de.lardtom.propwash.ui.client.config;

import de.lardtom.propwash.content.Numbers;
import de.lardtom.propwash.ui.SliderScale;
import de.lardtom.propwash.ui.client.theme.SliderLook;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.theme.WidgetState;
import de.lardtom.propwash.ui.client.widget.NumberEditor;
import de.lardtom.propwash.ui.client.widget.NumberInput;
import de.lardtom.propwash.ui.client.widget.SliderTyping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

public class StepSlider extends AbstractSliderButton implements NumberInput, SliderTyping.Target {
    private final double min;
    private final double max;
    private final double step;
    private final DoubleFunction<Component> message;
    private final DoubleConsumer onChange;
    private final SliderTyping typing = new SliderTyping();
    private double lastApplied;
    private SliderScale scale = SliderScale.IDENTITY;
    private double typedMin;
    private double typedMax;
    private boolean typeable = true;

    public StepSlider(int x, int y, int width, int height, double min, double max, double step, double initial,
                      DoubleFunction<Component> message, DoubleConsumer onChange) {
        super(x, y, width, height, Component.empty(), 0.0);
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
        this.step = step;
        this.message = message;
        this.onChange = onChange;
        this.typedMin = this.min;
        this.typedMax = this.max;
        this.value = this.fraction(initial);
        this.lastApplied = this.current();
        this.updateMessage();
        this.updateTyping();
    }

    public static double snap(double raw, double min, double max, double step) {
        double lo = Math.min(min, max);
        double hi = Math.max(min, max);
        if (!Double.isFinite(raw)) {
            return lo;
        }
        double clamped = Math.max(lo, Math.min(hi, raw));
        if (step <= 0.0) {
            return clamped;
        }
        double snapped = Math.max(lo, Math.min(hi, lo + Math.round((clamped - lo) / step) * step));
        return hi - clamped < Math.abs(clamped - snapped) ? hi : snapped;
    }

    public StepSlider display(double factor, double offset) {
        this.scale = SliderScale.of(factor, offset);
        this.updateTyping();
        return this;
    }

    public StepSlider percent() {
        return this.display(SliderScale.PERCENT.factor(), SliderScale.PERCENT.offset()).percentRange();
    }

    public StepSlider percentRange() {
        this.typing.editor().rangeFormat(v -> Component.translatable("propwash.ui.number.percent",
                Numbers.of(v, this.scale.decimals(this.typedMin, this.step))).getString());
        return this;
    }

    public StepSlider typedRange(double rawMin, double rawMax) {
        this.typedMin = Math.max(this.min, Math.min(rawMin, rawMax));
        this.typedMax = Math.min(this.max, Math.max(rawMin, rawMax));
        this.updateTyping();
        return this;
    }

    public StepSlider untyped() {
        this.typeable = false;
        this.typing.disable();
        return this;
    }

    public SliderScale scale() {
        return this.scale;
    }

    private void updateTyping() {
        if (this.typeable) {
            int decimals = this.scale.decimals(this.typedMin, this.step);
            this.typing.enable(SliderScale.round(this.scale.displayMin(this.typedMin, this.typedMax), decimals),
                    SliderScale.round(this.scale.displayMax(this.typedMin, this.typedMax), decimals), decimals > 0);
        }
    }

    public double current() {
        return snap(this.min + this.value * (this.max - this.min), this.min, this.max, this.step);
    }

    public void setCurrent(double newValue) {
        this.value = this.fraction(newValue);
        this.lastApplied = this.current();
        this.updateMessage();
    }

    public void select(double newValue) {
        this.value = this.fraction(newValue);
        this.updateMessage();
        this.applyValue();
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

    public String editText() {
        String text = this.typing.text();
        return text == null ? "" : text;
    }

    @Override
    public String shownValue() {
        return SliderLook.valueOf(this).getString();
    }

    @Override
    public void cancelEdit() {
        this.typing.cancel();
    }

    @Override
    public void commitPending() {
        this.typing.commitPending(this);
    }

    @Override
    public String typingText() {
        return Numbers.of(this.scale.toDisplay(this.current()), this.scale.decimals(this.typedMin, this.step)).getString();
    }

    @Override
    public void applyTyped(double shown) {
        this.select(this.scale.rawFor(shown, this.typedMin, this.typedMax));
    }

    @Override
    public void stepTyped(int steps) {
        if (this.step > 0.0) {
            this.select(this.current() + steps * this.step);
        }
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
        if (this.canChangeValue && this.step > 0.0 && (event.isLeft() || event.isRight())) {
            this.select(this.current() + (event.isLeft() ? -this.step : this.step));
            return true;
        }
        return super.keyPressed(event) || this.typing.swallows(event);
    }

    @Override
    public void setFocused(boolean focused) {
        boolean was = this.isFocused();
        super.setFocused(focused);
        this.typing.focusChanged(this, this, was, focused);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (this.typing.editing()) {
            int left = this.trackLeft();
            if (event.x() >= left) {
                this.typing.caret(Minecraft.getInstance().font, event.x(), left, this.getX() + this.getWidth());
                return;
            }
            this.typing.cancel();
        }
        super.onClick(event, doubleClick);
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        if (!this.typing.editing()) {
            super.onDrag(event, dx, dy);
        }
    }

    private int labelWidth() {
        return ThemedScreen.active() ? SliderLook.of(this).labelWidth() : 0;
    }

    private int trackLeft() {
        return this.getX() + this.labelWidth();
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (this.typing.editing()) {
            SliderLook look = SliderLook.of(this);
            int labelWidth = ThemedScreen.active() ? look.labelWidth() : 0;
            this.typing.extractField(graphics, Minecraft.getInstance().font, look.label(), labelWidth, this.getX(), this.getY(), this.getWidth(),
                    this.getHeight(), WidgetState.of(this), this.isFocused());
            return;
        }
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
        this.typing.extractNotice(graphics, this.trackLeft(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight());
    }

    private double fraction(double v) {
        return this.max > this.min ? (snap(v, this.min, this.max, this.step) - this.min) / (this.max - this.min) : 0.0;
    }

    @Override
    protected void updateMessage() {
        if (this.message != null) {
            this.setMessage(this.message.apply(this.current()));
        }
    }

    @Override
    protected void applyValue() {
        double v = this.current();
        if (v != this.lastApplied) {
            this.lastApplied = v;
            this.onChange.accept(v);
        }
    }
}
