package de.lardtom.propwash.rescue.client;

import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.theme.ThemedWidgets;
import de.lardtom.propwash.ui.client.theme.WidgetState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

final class RescueCycleButton<T> extends AbstractButton {
    private final Component label;
    private final List<T> values;
    private final Function<T, Component> names;
    private final Function<T, Tooltip> tooltips;
    private final Consumer<T> onChange;
    private int index;

    RescueCycleButton(int width, int height, Component label, List<T> values, T initial, Function<T, Component> names,
                      Function<T, Tooltip> tooltips, Consumer<T> onChange) {
        super(0, 0, width, height, Component.empty());
        if (values.isEmpty()) {
            throw new IllegalArgumentException("RescueCycleButton needs at least one value");
        }
        this.label = label;
        this.values = List.copyOf(values);
        this.names = names;
        this.tooltips = tooltips;
        this.onChange = onChange;
        this.index = Math.max(0, this.values.indexOf(initial));
        this.refresh();
    }

    static RescueCycleButton<Boolean> onOff(int width, int height, Component label, boolean initial, Tooltip tooltip,
                                            Consumer<Boolean> onChange) {
        return new RescueCycleButton<>(width, height, label, List.of(Boolean.TRUE, Boolean.FALSE), initial,
                ThemedWidgets::onOff, v -> tooltip, onChange);
    }

    T value() {
        return this.values.get(this.index);
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.cycle(input.hasShiftDown() ? -1 : 1);
    }

    private void cycle(int delta) {
        this.index = Math.floorMod(this.index + delta, this.values.size());
        this.refresh();
        this.onChange.accept(this.value());
    }

    private Component labelFor(T value) {
        return CommonComponents.optionNameValue(this.label, this.names.apply(value));
    }

    private void refresh() {
        this.setMessage(this.labelFor(this.value()));
        this.setTooltip(this.tooltips.apply(this.value()));
    }

    int neededWidth(Font font) {
        int control = this.controlNeed(font);
        return control == 0 ? 0 : font.width(this.label) + Metrics.LABEL_GAP + 2 + control;
    }

    private int controlNeed(Font font) {
        if (this.value() instanceof Boolean) {
            return 0;
        }
        int widest = 0;
        for (T value : this.values) {
            widest = Math.max(widest, font.width(this.names.apply(value)));
        }
        return widest + 2 * (Metrics.ARROW_WIDTH + 3) + 2;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (ThemedScreen.active()) {
            Font font = Minecraft.getInstance().font;
            int labelWidth = Metrics.hasLabelColumn(this.getWidth())
                    ? ThemedWidgets.labelWidth(font, this.label, this.getWidth(), this.controlNeed(font)) : 0;
            Component label = labelWidth > 0 ? this.label : null;
            Component valueText = this.names.apply(this.value());
            WidgetState state = WidgetState.of(this);
            if (this.value() instanceof Boolean on) {
                ThemedWidgets.toggle(graphics, font, this.getX(), this.getY(), this.getWidth(), this.getHeight(), labelWidth, label, on,
                        labelWidth > 0 ? valueText : this.getMessage(), state, false);
            } else {
                ThemedWidgets.cycle(graphics, font, this.getX(), this.getY(), this.getWidth(), this.getHeight(), labelWidth, label,
                        labelWidth > 0 ? valueText : this.getMessage(), state, false);
            }
            return;
        }
        this.extractDefaultSprite(graphics);
        this.extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.createNarrationMessage());
        if (this.active) {
            Component next = this.labelFor(this.values.get(Math.floorMod(this.index + 1, this.values.size())));
            output.add(NarratedElementType.USAGE, Component.translatable(this.isFocused()
                    ? "narration.cycle_button.usage.focused" : "narration.cycle_button.usage.hovered", next));
        }
    }
}
