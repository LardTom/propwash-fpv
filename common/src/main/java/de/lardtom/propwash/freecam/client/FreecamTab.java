package de.lardtom.propwash.freecam.client;

import de.lardtom.propwash.content.Numbers;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.freecam.FreecamPreset;
import de.lardtom.propwash.freecam.FreecamRules;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.tab.UiTab;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.ChoiceButton;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.ScrollPanel;
import de.lardtom.propwash.ui.client.widget.ValueSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public final class FreecamTab extends UiTab {
    public static final String ID = ConfigTabs.FREECAM_TAB;
    private static final int CHIP_MIN = 52;

    private final Map<FreecamPreset, FlatButton> chips = new EnumMap<>(FreecamPreset.class);
    private @Nullable ValueSlider uptilt;
    private @Nullable ValueSlider fov;
    private @Nullable ChoiceButton<Boolean> osd;
    private @Nullable FlatButton exit;
    private int presetTop;
    private int cameraTop;
    private int hintTop;
    private int hintWidth;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.freecam.tab");
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public boolean freecam() {
        return true;
    }

    @Override
    public Storage storage() {
        return Storage.MIXED;
    }

    @Override
    public String mixedStorageKey() {
        return "propwash.freecam.footer";
    }

    public @Nullable FlatButton chip(FreecamPreset preset) {
        return this.chips.get(preset);
    }

    public @Nullable ValueSlider uptiltSlider() {
        return this.uptilt;
    }

    public @Nullable ValueSlider fovSlider() {
        return this.fov;
    }

    public @Nullable ChoiceButton<Boolean> osdToggle() {
        return this.osd;
    }

    public @Nullable FlatButton exitButton() {
        return this.exit;
    }

    @Override
    protected void build() {
        ScrollPanel panel = this.beginScroll();
        int w = panel.innerWidth();
        int left = this.x;
        int column = Math.min(w, Metrics.COLUMN_MAX);
        int labelWidth = Math.min(Metrics.LABEL_MAX, column / 2);
        int cy = 0;
        this.chips.clear();
        this.presetTop = cy;
        cy += Metrics.SECTION_HEIGHT;
        FreecamPreset[] presets = FreecamPreset.values();
        int perRow = w >= presets.length * CHIP_MIN + (presets.length - 1) * Metrics.GAP ? presets.length : 2;
        int chipWidth = Math.max(CHIP_MIN, Math.min(90, (w - (perRow - 1) * Metrics.GAP) / perRow));
        for (int i = 0; i < presets.length; i++) {
            FreecamPreset preset = presets[i];
            int col = i % perRow;
            int row = i / perRow;
            FlatButton chip = this.add(new FlatButton(left + col * (chipWidth + Metrics.GAP), this.top() + cy + row * (FIELD + 4), chipWidth, FIELD,
                    Component.translatable(preset.translationKey()), FlatButton.Style.TAB, () -> currentPreset() == preset,
                    () -> this.choose(preset)));
            chip.setTooltip(Tooltip.create(Component.translatable(preset.translationKey() + ".tip")));
            this.chips.put(preset, chip);
        }
        cy += ((presets.length + perRow - 1) / perRow) * (FIELD + 4) + 6;
        this.cameraTop = cy;
        cy += Metrics.SECTION_HEIGHT;
        CameraMount.Range range = CameraMount.range(currentPreset().build());
        this.uptilt = this.add(new ValueSlider(left, this.top() + cy, column, FIELD, Component.translatable("propwash.video.camera_angle"), labelWidth,
                range.minDeg(), range.maxDeg(), 1.0, () -> FreecamConfig.get().uptilt(currentPreset()), value -> {
            FreecamConfig.get().setUptilt(currentPreset(), (float) value);
            FreecamConfig.save();
        }, () -> CameraMount.defaultUptilt(currentPreset().build().frame()), value -> Numbers.of(value, 0).getString() + "°"));
        this.uptilt.setTooltip(Tooltip.create(Component.translatable("propwash.freecam.uptilt.tip")));
        cy += ROW + 2;
        this.fov = this.add(new ValueSlider(left, this.top() + cy, column, FIELD, Component.translatable("propwash.freecam.fov"), labelWidth,
                FreecamConfig.FOV_MIN, FreecamConfig.FOV_MAX, 1.0, () -> FreecamConfig.get().fov(), value -> {
            FreecamConfig.get().fovDeg = FreecamConfig.clampFov((float) value);
            FreecamConfig.save();
        }, () -> FreecamConfig.FOV_DEFAULT, value -> Numbers.of(value, 0).getString() + "°"));
        this.fov.setTooltip(Tooltip.create(Component.translatable("propwash.freecam.fov.tip")));
        cy += ROW + 2;
        this.osd = this.add(ChoiceButton.toggle(left, this.top() + cy, column, FIELD, Component.translatable("propwash.freecam.osd"), labelWidth,
                () -> FreecamConfig.get().osd, value -> {
                    FreecamConfig.get().osd = value;
                    FreecamConfig.save();
                }, value -> Component.translatable(value ? "propwash.ui.value.on" : "propwash.ui.value.off"), () -> Boolean.TRUE));
        this.osd.setTooltip(Tooltip.create(Component.translatable("propwash.freecam.osd.tip")));
        cy += ROW + 6;
        this.hintTop = cy;
        this.hintWidth = w;
        cy += ThemeText.wrappedHeight(this.font(), hint(), w) + 6;
        int exitWidth = Math.min(w, Math.max(100, this.font().width(Component.translatable("propwash.freecam.exit")) + 16));
        this.exit = this.add(new FlatButton(left, this.top() + cy, exitWidth, FIELD, Component.translatable("propwash.freecam.exit"),
                FlatButton.Style.DANGER, this::exit));
        cy += ROW + 4;
        this.endScroll(cy);
    }

    private static FreecamPreset currentPreset() {
        return FreecamSession.active() ? FreecamSession.INSTANCE.preset() : FreecamConfig.get().preset();
    }

    private static Component hint() {
        return Component.translatable("propwash.freecam.hint");
    }

    private void choose(FreecamPreset preset) {
        if (preset == currentPreset()) {
            return;
        }
        this.tune().save();
        if (FreecamSession.active()) {
            FreecamSession.INSTANCE.setPreset(preset);
        } else {
            FreecamConfig.get().preset = preset.key();
            FreecamConfig.save();
        }
        this.flash(Component.translatable("propwash.freecam.preset.changed", Component.translatable(preset.translationKey())), Palette.SUCCESS);
        this.host.rebuild();
    }

    private void exit() {
        FreecamSession.INSTANCE.end(Minecraft.getInstance(), FreecamRules.End.BUTTON);
    }

    @Override
    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int left = this.x;
        int top = this.top();
        int w = this.innerWidth();
        this.section(g, Component.translatable("propwash.freecam.preset"), left, top + this.presetTop, w);
        this.section(g, Component.translatable("propwash.freecam.camera"), left, top + this.cameraTop, w);
        ThemeText.wrapped(g, this.font(), hint(), left, top + this.hintTop, this.hintWidth, Palette.TEXT_MUTED);
        this.extractScrollbar(g);
    }

    @Override
    public void resetDefaults() {
        FreecamConfig config = FreecamConfig.get();
        config.uptilt.remove(currentPreset().key());
        config.fovDeg = FreecamConfig.FOV_DEFAULT;
        config.osd = true;
        FreecamConfig.save();
    }
}
