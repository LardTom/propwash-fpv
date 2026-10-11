package de.lardtom.propwash.ui.client.widget;

import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.ThemedWidgets;
import de.lardtom.propwash.ui.client.theme.WidgetState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ChoiceButton<T> extends PanelWidget {
    private final int labelWidth;
    private final List<T> values;
    private final Supplier<T> getter;
    private final Consumer<T> setter;
    private final Function<T, Component> names;
    private final @Nullable Supplier<T> defaultValue;
    private final boolean toggle;

    public ChoiceButton(int x, int y, int width, int height, Component label, int labelWidth, List<T> values, Supplier<T> getter,
                        Consumer<T> setter, Function<T, Component> names, @Nullable Supplier<T> defaultValue) {
        super(x, y, width, height, label);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("ChoiceButton ohne Werte");
        }
        this.labelWidth = Math.max(0, Math.min(labelWidth, width - 24));
        this.values = List.copyOf(values);
        this.getter = getter;
        this.setter = setter;
        this.names = names;
        this.defaultValue = defaultValue;
        this.toggle = this.values.size() == 2 && this.values.contains(Boolean.FALSE) && this.values.contains(Boolean.TRUE);
    }

    public static ChoiceButton<Boolean> toggle(int x, int y, int width, int height, Component label, int labelWidth, Supplier<Boolean> getter,
                                               Consumer<Boolean> setter, Function<Boolean, Component> names, @Nullable Supplier<Boolean> defaultValue) {
        return new ChoiceButton<>(x, y, width, height, label, labelWidth, List.of(Boolean.FALSE, Boolean.TRUE), getter, setter, names, defaultValue);
    }

    public boolean labelFits() {
        return this.labelWidth <= 0 || this.font().width(this.getMessage()) <= this.labelWidth - 4;
    }

    private int index() {
        int i = this.values.indexOf(this.getter.get());
        return Math.max(i, 0);
    }

    private void cycle(int direction) {
        int n = this.values.size();
        int next = ((this.index() + direction) % n + n) % n;
        this.setter.accept(this.values.get(next));
    }

    private boolean modified() {
        return this.defaultValue != null && !Objects.equals(this.defaultValue.get(), this.getter.get());
    }

    @Override
    protected boolean acceptsRightClick() {
        return true;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (isRightClick(event)) {
            if (this.defaultValue != null) {
                this.setter.accept(this.defaultValue.get());
            }
            return;
        }
        int boxLeft = this.getX() + this.labelWidth;
        if (!this.toggle && event.x() < boxLeft + Metrics.ARROW_WIDTH) {
            this.cycle(-1);
        } else {
            this.cycle(1);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isLeft()) {
            this.cycle(-1);
            return true;
        }
        if (event.isRight() || event.isSelection()) {
            this.cycle(1);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.isFocused() || scrollY == 0.0) {
            return false;
        }
        this.cycle(scrollY > 0 ? -1 : 1);
        return true;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        WidgetState state = this.state();
        T value = this.getter.get();
        Component name = this.names.apply(value);
        Component label = this.labelWidth > 0 ? this.getMessage() : null;
        if (this.toggle) {
            ThemedWidgets.toggle(g, this.font(), this.getX(), this.getY(), this.width, this.height, this.labelWidth, label, Boolean.TRUE.equals(value),
                    name, state, this.modified());
            return;
        }
        ThemedWidgets.cycle(g, this.font(), this.getX(), this.getY(), this.width, this.height, this.labelWidth, label, name, state, this.modified());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.empty().append(this.getMessage()).append(": ").append(this.names.apply(this.getter.get())));
    }
}
