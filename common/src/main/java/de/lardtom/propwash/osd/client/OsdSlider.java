package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.ui.client.config.StepSlider;
import de.lardtom.propwash.ui.client.theme.LabeledValue;
import de.lardtom.propwash.ui.client.theme.Metrics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

final class OsdSlider extends StepSlider implements LabeledValue {
    private final IntSupplier source;
    private final Component name;
    private final IntFunction<Component> value;
    private int sharedLabel;

    OsdSlider(int x, int y, int width, int height, int steps, IntSupplier source, IntConsumer apply, IntFunction<Component> label, Component name,
              IntFunction<Component> value) {
        super(x, y, width, height, 0.0, Math.max(1, steps), 1.0, source.getAsInt(), v -> label.apply((int) Math.round(v)),
                v -> apply.accept((int) Math.round(v)));
        this.source = source;
        this.name = name;
        this.value = value;
    }

    static void shareLabelColumn(OsdSlider... sliders) {
        int widest = 0;
        for (OsdSlider slider : sliders) {
            widest = Math.max(widest, Minecraft.getInstance().font.width(slider.name));
        }
        for (OsdSlider slider : sliders) {
            slider.sharedLabel = widest;
        }
    }

    int index() {
        return (int) Math.round(this.current());
    }

    void sync() {
        this.setCurrent(this.source.getAsInt());
    }

    void select(int newIndex) {
        this.select((double) newIndex);
    }

    boolean labelColumn() {
        return this.columnWidth(this.getWidth()) > 0;
    }

    private int columnWidth(int width) {
        int column = Math.max(this.sharedLabel, Minecraft.getInstance().font.width(this.name)) + Metrics.LABEL_GAP + 2;
        return width - column >= Metrics.CONTROL_MIN ? column : 0;
    }

    @Override
    public Component themeLabel() {
        return this.labelColumn() ? this.name : Component.empty();
    }

    @Override
    public Component themeValue() {
        return this.labelColumn() ? this.value.apply(this.index()) : this.getMessage();
    }

    @Override
    public int themeLabelWidth(int width) {
        return this.columnWidth(width);
    }
}
