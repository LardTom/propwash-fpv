package de.lardtom.propwash.audio.client;

import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.StepSlider;
import de.lardtom.propwash.ui.client.theme.FormLayout;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.DoubleConsumer;

public final class AudioConfigTab extends ConfigTab {
    private static final int WIDGET_HEIGHT = Metrics.CONTROL_HEIGHT;

    private AudioConfig working = AudioConfig.get().copy();
    private boolean edited;
    private int left;
    private int columnWidth;
    private int statusY;
    private @Nullable FormLayout form;

    @Override
    public String id() {
        return "audio";
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.audio.tab");
    }

    @Override
    public int order() {
        return 820;
    }

    @Override
    protected void build() {
        AudioConfig config = this.working;
        FormLayout form = new FormLayout(this.x, this.y, this.width);
        this.form = form;
        this.columnWidth = form.columnWidth();
        this.left = form.left();
        form.section(Component.translatable("propwash.audio.section"));

        StepSlider motor = this.percent(Component.translatable("propwash.audio.motor_volume"),
                config.motorVolume, value -> {
                    this.working.motorVolume = value;
                    this.markEdited();
                }, null);
        motor.setTooltip(Tooltip.create(Component.translatable("propwash.audio.motor_volume.tooltip")));
        this.host.addWidget(form.place(motor));

        StepSlider beeps = this.percent(Component.translatable("propwash.audio.beep_volume"),
                config.beepVolume, value -> {
                    this.working.beepVolume = value;
                    this.markEdited();
                }, this::previewBeep);
        beeps.setTooltip(Tooltip.create(Component.translatable("propwash.audio.beep_volume.tooltip")));
        this.host.addWidget(form.place(beeps));

        CycleButton<Boolean> doppler = CycleButton.onOffBuilder(config.doppler).create(this.left, this.y, this.columnWidth, WIDGET_HEIGHT,
                Component.translatable("propwash.audio.doppler"), (button, value) -> {
                    this.working.doppler = value;
                    this.markEdited();
                });
        doppler.setTooltip(Tooltip.create(Component.translatable("propwash.audio.doppler.tooltip")));
        this.host.addWidget(form.place(doppler));

        CycleButton<Boolean> synthesizer = CycleButton.onOffBuilder(config.synthesizer).create(this.left, this.y, this.columnWidth, WIDGET_HEIGHT,
                Component.translatable("propwash.audio.synthesizer"), (button, value) -> {
                    this.working.synthesizer = value;
                    this.markEdited();
                });
        synthesizer.setTooltip(Tooltip.create(Component.translatable("propwash.audio.synthesizer.tooltip")));
        this.host.addWidget(form.place(synthesizer));

        Component voiceCaption = Component.translatable("propwash.audio.voice_limit");
        StepSlider voices = new StepSlider(this.left, this.y, this.columnWidth, WIDGET_HEIGHT, AudioConfig.MIN_VOICES, AudioConfig.MAX_VOICES, 1.0,
                config.voiceLimit, value -> Options.genericValueLabel(voiceCaption, (int) Math.round(value)), value -> {
                    this.working.voiceLimit = (int) Math.round(value);
                    this.markEdited();
                });
        voices.setTooltip(Tooltip.create(Component.translatable("propwash.audio.voice_limit.tooltip")));
        this.host.addWidget(form.place(voices));
        form.endRow();

        this.statusY = form.bottom() + Metrics.GAP;
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Component status = Component.translatable("propwash.audio.status", MotorVoices.synthVoices(),
                MotorVoices.synthLimit(this.working), MotorVoices.sampleVoices());
        if (this.form != null) {
            this.form.extract(graphics, this.host.font());
        }
        int statusHeight = ThemeText.wrapped(graphics, this.host.font(), status, this.left, this.statusY, Math.max(40, this.width), Palette.TEXT_MUTED);
        int hint = ThemeText.hint(graphics, this.host.font(), Component.translatable("propwash.audio.status.hint"), this.left,
                this.statusY + statusHeight + 4, Math.max(40, this.width));
        this.reportBottom(this.statusY + statusHeight + 4 + hint);
    }

    @Override
    public void resetDefaults() {
        this.working = new AudioConfig();
        this.markEdited();
        this.host.rebuild();
    }

    @Override
    public void save() {
        if (!this.edited) {
            return;
        }
        AudioConfig.CONFIG.set(this.working.copy());
        this.edited = false;
    }

    AudioConfig working() {
        return this.working;
    }

    private void markEdited() {
        this.edited = true;
        this.host.changed();
    }

    private void previewBeep() {
        AudioHooks.preview(Beep.GATE, this.working.beepVolume);
    }

    private StepSlider percent(Component caption, double initial, DoubleConsumer onChange, @Nullable Runnable onRelease) {
        if (onRelease == null) {
            return new StepSlider(this.left, this.y, this.columnWidth, WIDGET_HEIGHT, 0.0, 1.0, 0.01, initial, v -> percentLabel(caption, v), onChange)
                    .percent();
        }
        return new StepSlider(this.left, this.y, this.columnWidth, WIDGET_HEIGHT, 0.0, 1.0, 0.01, initial, v -> percentLabel(caption, v), onChange) {
            @Override
            public void onRelease(MouseButtonEvent event) {
                super.onRelease(event);
                onRelease.run();
            }
        }.percent();
    }

    private static Component percentLabel(Component caption, double value) {
        return Component.translatable("options.percent_value", caption, (int) Math.round(value * 100.0));
    }
}
