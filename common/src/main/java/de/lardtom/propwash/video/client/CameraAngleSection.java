package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.Numbers;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.ui.client.TuneModel;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.ui.client.config.StepSlider;
import de.lardtom.propwash.ui.client.search.SearchTags;
import de.lardtom.propwash.ui.client.theme.LabeledValue;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.InfoField;
import de.lardtom.propwash.video.FovMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.DoubleConsumer;

public final class CameraAngleSection {
    public static final String KEY = "propwash.video.camera_angle";
    public static final String SECTION_KEY = "propwash.video.camera_section";
    public static final String FOOTER_KEY = "propwash.video.camera_section.footer";
    private static final String SERVICE_KEY = "propwash.drone.service.camera.";
    private static final int BUTTON = Metrics.CONTROL_HEIGHT;
    private static final int BUTTON_GAP = 2;
    private static final float SNEAK_STEP = 5.0F;

    private final @Nullable TuneModel model;
    private @Nullable StepSlider slider;
    private @Nullable AbstractWidget row;
    private @Nullable FlatButton minus;
    private @Nullable FlatButton plus;
    private @Nullable FlatButton reset;
    private int top;
    private int noteY;
    private int bottom;

    public CameraAngleSection(@Nullable TuneModel model) {
        this.model = model;
    }

    public boolean editable() {
        return this.model != null && this.model.live();
    }

    public @Nullable StepSlider slider() {
        return this.slider;
    }

    public @Nullable AbstractWidget row() {
        return this.row;
    }

    public @Nullable FlatButton minusButton() {
        return this.minus;
    }

    public @Nullable FlatButton plusButton() {
        return this.plus;
    }

    public @Nullable FlatButton resetButton() {
        return this.reset;
    }

    public int top() {
        return this.top;
    }

    public int bottom() {
        return this.bottom;
    }

    public CameraMount.@Nullable Range range() {
        return this.editable() && this.model != null ? CameraMount.range(this.model.build()) : null;
    }

    public float uptilt() {
        CameraMount.Range range = this.range();
        if (range == null || this.model == null) {
            return Float.NaN;
        }
        return range.clamp((float) this.model.value(TuneMapping.CAMERA_UPTILT));
    }

    public int build(ConfigTabHost host, int x, int y, int rowWidth, int textWidth) {
        this.top = y;
        int rowY = y + Metrics.SECTION_HEIGHT;
        boolean editable = this.editable();
        CameraMount.Range range = this.range();
        int sliderWidth = Math.max(Metrics.CONTROL_MIN, rowWidth - 3 * (BUTTON + BUTTON_GAP));
        int buttonX = x + sliderWidth + BUTTON_GAP;
        Component none = Component.translatable(KEY + ".none");
        this.slider = null;
        if (editable && range != null && this.model != null) {
            UptiltSlider slider = new UptiltSlider(x, rowY, sliderWidth, Metrics.CONTROL_HEIGHT, range, this.uptilt(), this::change);
            slider.setTooltip(ThemeText.tip(tooltip(range, this.model.build())));
            this.slider = slider;
            this.row = host.addWidget(SearchTags.label(slider, Component.translatable(KEY)));
        } else {
            InfoField field = new InfoField(x, rowY, sliderWidth, Metrics.CONTROL_HEIGHT, Component.translatable(KEY), Component.literal("—"),
                    Component.empty());
            field.active = false;
            field.setTooltip(Tooltip.create(none));
            this.row = host.addWidget(SearchTags.label(field, Component.translatable(KEY)));
        }
        this.minus = host.addWidget(this.button(buttonX, rowY, "−", editable ? Component.translatable(SERVICE_KEY + "minus") : none,
                () -> this.step(-1.0F), editable));
        this.plus = host.addWidget(this.button(buttonX + BUTTON + BUTTON_GAP, rowY, "+", editable ? Component.translatable(SERVICE_KEY + "plus") : none,
                () -> this.step(1.0F), editable));
        this.reset = host.addWidget(this.button(buttonX + 2 * (BUTTON + BUTTON_GAP), rowY, "↺", range != null && editable
                ? Component.translatable(SERVICE_KEY + "reset", Numbers.of(range.defaultDeg(), 0)) : none, this::resetUptilt, editable));
        this.noteY = rowY + Metrics.ROW_HEIGHT + BUTTON_GAP;
        this.bottom = this.noteY + ThemeText.wrappedHeight(host.font(), this.note(), textWidth);
        return this.bottom;
    }

    public int extract(GuiGraphicsExtractor graphics, Font font, int x, int textWidth) {
        ThemeDraw.sectionTitle(graphics, font, Component.translatable(SECTION_KEY), x, this.top, textWidth);
        ThemeText.wrapped(graphics, font, this.note(), x, this.noteY, textWidth, Palette.TEXT_MUTED);
        return this.bottom;
    }

    public void change(double degrees) {
        CameraMount.Range range = this.range();
        if (range == null || this.model == null) {
            return;
        }
        float value = range.clamp(Math.round((float) degrees));
        this.model.set(TuneMapping.CAMERA_UPTILT, value);
        this.show(value);
    }

    public void resetUptilt() {
        CameraMount.Range range = this.range();
        if (range == null || this.model == null) {
            return;
        }
        this.model.reset(TuneMapping.CAMERA_UPTILT);
        this.show(range.defaultDeg());
    }

    private void step(float direction) {
        if (!this.editable()) {
            return;
        }
        float step = Minecraft.getInstance().hasShiftDown() ? SNEAK_STEP * direction : direction;
        this.change(this.uptilt() + step);
    }

    private void show(float value) {
        StepSlider row = this.slider;
        if (row != null && row.current() != value) {
            row.setCurrent(value);
        }
    }

    private Component note() {
        return Component.translatable(this.editable() ? KEY + ".also" : KEY + ".none");
    }

    private FlatButton button(int x, int y, String label, Component tooltip, Runnable action, boolean editable) {
        FlatButton button = new FlatButton(x, y, BUTTON, Metrics.CONTROL_HEIGHT, Component.literal(label), FlatButton.Style.NORMAL, action);
        button.setTooltip(Tooltip.create(tooltip));
        button.active = editable;
        return SearchTags.hide(button);
    }

    private static Component tooltip(CameraMount.Range range, DroneBuild build) {
        return Component.translatable(SERVICE_KEY + "tooltip", Numbers.of(range.minDeg(), 0), Numbers.of(range.maxDeg(), 0),
                Numbers.of(range.defaultDeg(), 0), Numbers.of(FovMath.cameraHorizontalDegrees(build.video()), 0));
    }

    private static final class UptiltSlider extends StepSlider implements LabeledValue {
        private final float standard;

        UptiltSlider(int x, int y, int width, int height, CameraMount.Range range, float initial, DoubleConsumer onChange) {
            super(x, y, width, height, range.minDeg(), range.maxDeg(), 1.0, initial, v -> label(value(v)), onChange);
            this.standard = range.defaultDeg();
        }

        private static Component value(double degrees) {
            return Component.translatable(SERVICE_KEY + "value", Numbers.of(degrees, 0));
        }

        private static Component label(Component value) {
            return Component.empty().append(Component.translatable(KEY)).append(": ").append(value);
        }

        @Override
        public Component themeLabel() {
            return Component.translatable(KEY);
        }

        @Override
        public Component themeValue() {
            return value(this.current());
        }

        @Override
        public boolean themeModified() {
            return this.current() != this.standard;
        }
    }
}
