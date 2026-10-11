package de.lardtom.propwash.ui.client.tab;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.client.render.DronePreview;
import de.lardtom.propwash.compat.iris.client.IrisCompat;
import de.lardtom.propwash.compat.lambdynamiclights.LambDynamicLightsCompat;
import de.lardtom.propwash.drone.led.LedAnimator;
import de.lardtom.propwash.drone.led.LedColor;
import de.lardtom.propwash.drone.led.LedMode;
import de.lardtom.propwash.drone.led.LedSettings;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.FunctionKeys;
import de.lardtom.propwash.ui.client.DroneContext;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedWidgets;
import de.lardtom.propwash.ui.client.theme.WidgetState;
import de.lardtom.propwash.ui.client.widget.ChoiceButton;
import de.lardtom.propwash.ui.client.widget.PanelWidget;
import de.lardtom.propwash.ui.client.widget.ScrollPanel;
import de.lardtom.propwash.ui.client.widget.ValueSlider;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class LedTab extends UiTab {
    public static final String ID = "led_strip";
    private static final int PREVIEW_MIN = 90;
    private static final int PREVIEW_TWO_COLUMNS = 150;
    private static final int TOGGLE_SPACE = 48;
    private static final int STRIP_ROW = 26;
    private static final int STRIP_LAMPS = 12;
    private static final int SWATCH = 12;

    private boolean previewArmed;
    private boolean twoColumns;
    private int column;
    private int statusTop;
    private int statusHeight;
    private int descTop;
    private int worldTop;
    private int worldTextTop;
    private int previewX0;
    private int previewX1;
    private int previewTop;
    private int previewHeight;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.ui.tab.led_strip");
    }

    @Override
    public int order() {
        return 520;
    }

    @Override
    public Storage storage() {
        return Storage.DRONE;
    }

    public LedSettings settings() {
        return LedSettings.of(this.tune().working());
    }

    public void edit(String key, float value) {
        float clean = LedSettings.sanitize(key, value);
        this.tune().update(t -> {
            Map<String, Float> out = new TreeMap<>(t);
            if (clean == LedSettings.defaultValue(key)) {
                out.remove(key);
            } else {
                out.put(key, clean);
            }
            return out;
        }, false);
    }

    @Override
    protected void build() {
        ScrollPanel panel = this.beginScroll();
        int w = panel.innerWidth();
        this.twoColumns = w >= 400;
        this.column = this.twoColumns ? Math.min(Metrics.COLUMN_MAX, w - PREVIEW_TWO_COLUMNS - 2 * Metrics.GAP) : Math.min(w, Metrics.COLUMN_MAX);
        int left = this.x;
        int widest = 0;
        for (String key : new String[]{"propwash.ui.led.mode", "propwash.ui.led.color", "propwash.ui.led.brightness",
                "propwash.ui.led.headlight", "propwash.ui.led.preview_armed"}) {
            widest = Math.max(widest, this.font().width(Component.translatable(key)));
        }
        int labelWidth = Math.min(this.column / 2, Math.max(Metrics.LABEL_MIN, widest + Metrics.PADDING));
        int cy = Metrics.SECTION_HEIGHT;
        this.statusTop = cy;
        this.statusHeight = ThemeText.wrappedHeight(this.font(), this.status(), this.column);
        cy += this.statusHeight + 6;

        ChoiceButton<LedMode> mode = this.add(new ChoiceButton<>(left, this.top() + cy, this.column, FIELD,
                Component.translatable("propwash.ui.led.mode"), labelWidth, Arrays.asList(LedMode.values()),
                () -> this.settings().mode(),
                v -> {
                    this.edit(LedSettings.MODE, v.ordinal());
                    this.host.rebuild();
                },
                v -> Component.translatable(v.translationKey()), () -> LedSettings.DEFAULT.mode()));
        mode.setTooltip(Tooltip.create(Component.translatable("propwash.ui.led.mode.tip")));
        cy += ROW;

        ColorRow colors = this.add(new ColorRow(left, this.top() + cy, this.column, FIELD, Component.translatable("propwash.ui.led.color"),
                labelWidth, () -> this.settings().color(), v -> this.edit(LedSettings.COLOR, v.ordinal())));
        colors.setTooltip(Tooltip.create(Component.translatable("propwash.ui.led.color.tip")));
        colors.active = this.settings().mode() != LedMode.RAINBOW;
        cy += ROW;

        ValueSlider brightness = this.add(new ValueSlider(left, this.top() + cy, this.column, FIELD,
                Component.translatable("propwash.ui.led.brightness"), labelWidth, LedSettings.MIN_BRIGHTNESS, LedSettings.MAX_BRIGHTNESS,
                LedSettings.BRIGHTNESS_STEP, () -> this.settings().brightnessPercent(), v -> this.edit(LedSettings.BRIGHTNESS, (float) v),
                () -> LedSettings.DEFAULT.brightnessPercent(), v -> Math.round(v) + "%"))
                .typed(v -> v, LedSettings.MIN_BRIGHTNESS, LedSettings.MAX_BRIGHTNESS, false);
        brightness.setTooltip(Tooltip.create(Component.translatable("propwash.ui.led.brightness.tip")));
        cy += ROW;

        ChoiceButton<Boolean> headlight = this.add(ChoiceButton.toggle(left, this.top() + cy, this.column, FIELD,
                Component.translatable("propwash.ui.led.headlight"), labelWidth, () -> this.settings().headlight(),
                v -> {
                    this.edit(LedSettings.HEADLIGHT, v ? 1.0F : 0.0F);
                    this.host.rebuild();
                },
                v -> ThemedWidgets.onOff(v), () -> LedSettings.DEFAULT.headlight()));
        headlight.setTooltip(Tooltip.create(Component.translatable("propwash.ui.led.headlight.tip")));
        cy += ROW + 4;

        this.descTop = cy;
        cy += ThemeText.wrappedHeight(this.font(), this.description(), this.column) + 8;

        this.worldTop = cy;
        cy += Metrics.SECTION_HEIGHT;
        this.worldTextTop = cy;
        cy += ThemeText.wrappedHeight(this.font(), this.worldText(), this.column) + 8;

        if (this.twoColumns) {
            this.previewX0 = left + this.column + 2 * Metrics.GAP;
            this.previewX1 = left + w;
            this.previewTop = 0;
            this.previewHeight = Math.max(PREVIEW_MIN, Math.min(cy - Metrics.SECTION_HEIGHT - STRIP_ROW - ROW - 12,
                    this.previewX1 - this.previewX0));
        } else {
            this.previewX0 = left;
            this.previewX1 = left + Math.min(w, Metrics.COLUMN_MAX);
            this.previewTop = cy;
            this.previewHeight = Math.max(PREVIEW_MIN, Math.min(140, (this.previewX1 - this.previewX0) * 3 / 5));
        }
        int toggleTop = this.previewTop + Metrics.SECTION_HEIGHT + this.previewHeight + STRIP_ROW + 4;
        int previewWidth = this.previewX1 - this.previewX0;
        Component armedLabel = Component.translatable("propwash.ui.led.preview_armed");
        int armedLabelWidth = Math.max(0, Math.min(this.font().width(armedLabel) + Metrics.PADDING, previewWidth - TOGGLE_SPACE));
        ChoiceButton<Boolean> armed = this.add(ChoiceButton.toggle(this.previewX0, this.top() + toggleTop, previewWidth, FIELD,
                armedLabel, armedLabelWidth, () -> this.previewArmed,
                v -> this.previewArmed = v, v -> ThemedWidgets.onOff(v), null));
        armed.setTooltip(Tooltip.create(Component.translatable("propwash.ui.led.preview_armed.tip")));
        int previewBottom = toggleTop + ROW;
        this.endScroll(Math.max(cy, previewBottom) + 4);
    }

    @Override
    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int left = this.x;
        int top = this.top();
        LedSettings settings = this.settings();
        this.section(g, Component.translatable("propwash.ui.led.section.strip"), left, top, this.column);
        boolean missing = this.stripMissing();
        ThemeText.wrapped(g, this.font(), this.status(), left, top + this.statusTop, this.column, missing ? Palette.WARNING : Palette.TEXT_MUTED);
        ThemeText.wrapped(g, this.font(), this.description(), left, top + this.descTop, this.column, Palette.TEXT_FAINT);
        this.section(g, Component.translatable("propwash.ui.led.section.world"), left, top + this.worldTop, this.column);
        ThemeText.wrapped(g, this.font(), this.worldText(), left, top + this.worldTextTop, this.column, Palette.TEXT_MUTED);

        int px0 = this.previewX0;
        int px1 = this.previewX1;
        int py = top + this.previewTop;
        this.section(g, Component.translatable("propwash.ui.led.section.preview"), px0, py, px1 - px0);
        int stageTop = py + Metrics.SECTION_HEIGHT;
        int stageBottom = stageTop + this.previewHeight;
        ThemeDraw.previewStage(g, px0, stageTop, px1, stageBottom);
        double seconds = Util.getMillis() / 1000.0;
        boolean armed = this.previewArmed;
        boolean lit = !this.stripMissing();
        DronePreview.render(g, this.previewBuild(), px0, stageTop, px1, stageBottom, (float) (seconds * 18.0 % 360.0), settings, armed,
                seconds);
        this.stripLamps(g, settings, armed, lit, seconds, px0, stageBottom + 4, px1);
        this.extractScrollbar(g);
    }

    private void stripLamps(GuiGraphicsExtractor g, LedSettings settings, boolean armed, boolean lit, double seconds, int x0, int y, int x1) {
        boolean installed = lit;
        int count = this.strip().map(type -> type.ledCount).orElse(AccessoryType.LED_STRIP.ledCount);
        LedAnimator.Input input = new LedAnimator.Input(armed, true, 1.0F, seconds);
        Component label = Component.translatable(installed ? "propwash.ui.led.strip_label" : "propwash.ui.led.strip_label_missing");
        ThemeText.fitted(g, this.font(), label, x0, y, x1 - x0, installed ? Palette.TEXT_MUTED : Palette.TEXT_DISABLED);
        y += STRIP_LAMPS;
        int gap = 3;
        int width = Math.max(4, Math.min(14, (x1 - x0 - gap * (count - 1)) / Math.max(1, count)));
        int total = count * width + (count - 1) * gap;
        int x = x0 + (x1 - x0 - total) / 2;
        for (int i = 0; i < count; i++) {
            int argb = lit ? LedAnimator.color(settings, i, count, input) : 0;
            int color = lampColor(argb);
            if (color != 0) {
                int glow = Palette.withAlpha(color, Math.round(0x60 * (argb >>> 24) / 255.0F));
                g.fill(x - 2, y, x + width + 2, y + 8, glow);
                g.fill(x, y - 2, x + width, y + 10, glow);
            }
            ThemeDraw.lamp(g, x, y, width, 8, color);
            x += width + gap;
        }
    }

    static int lampColor(int argb) {
        float level = (argb >>> 24) / 255.0F;
        if (level <= 0.0F) {
            return 0;
        }
        int off = Palette.FIELD;
        return ARGB.color(255,
                Math.round(ARGB.red(off) + (ARGB.red(argb) - ARGB.red(off)) * level),
                Math.round(ARGB.green(off) + (ARGB.green(argb) - ARGB.green(off)) * level),
                Math.round(ARGB.blue(off) + (ARGB.blue(argb) - ARGB.blue(off)) * level));
    }

    private boolean stripMissing() {
        return this.strip().isEmpty() && (this.context().hasDrone() || this.tune().live());
    }

    private DroneBuild previewBuild() {
        DroneBuild build = this.context().build();
        if (!this.stripMissing() && LedSettings.strip(build).isEmpty() && build.frame().accessoryMounts.contains(AccessoryType.LED_STRIP.mount)
                && build.accessories().at(AccessoryType.LED_STRIP.mount).isEmpty()) {
            return build.withAccessory(AccessoryType.LED_STRIP);
        }
        return build;
    }

    private Optional<AccessoryType> strip() {
        return LedSettings.strip(this.context().build());
    }

    private Component status() {
        DroneContext context = this.context();
        if (!context.hasDrone() && !this.tune().live()) {
            return Component.translatable("propwash.ui.led.no_drone");
        }
        return this.strip()
                .<Component>map(type -> Component.translatable("propwash.ui.led.installed",
                        Component.translatable(type.translationKey()), type.ledCount))
                .orElseGet(() -> Component.translatable("propwash.ui.led.missing"));
    }

    private Component description() {
        LedSettings settings = this.settings();
        Component mode = Component.translatable("propwash.ui.led.desc." + settings.mode().key());
        Component headlight = Component.translatable(settings.headlight() ? "propwash.ui.led.headlight_desc" : "propwash.ui.led.headlight_off");
        Component toggle = Component.translatable("propwash.ui.led.switch_hint", Component.translatable(SwitchFunction.LEDS_OFF.translationKey()),
                FunctionKeys.keyLabel(SwitchFunction.LEDS_OFF));
        return Component.empty().append(mode).append("\n\n").append(headlight).append("\n\n").append(toggle);
    }

    private Component worldText() {
        Component real = Component.translatable(LambDynamicLightsCompat.present() ? "propwash.ui.led.world.ldl_present"
                : "propwash.ui.led.world.ldl_missing");
        if (IrisCompat.shaderPackActive(Util.getNanos())) {
            return Component.empty().append(real).append("\n\n").append(Component.translatable("propwash.ui.led.world.shaders"));
        }
        return real;
    }

    @Override
    public void resetDefaults() {
        this.tune().update(t -> {
            Map<String, Float> out = new TreeMap<>(t);
            LedSettings.KEYS.forEach(out::remove);
            return out;
        }, false);
        this.host.rebuild();
    }

    static final class ColorRow extends PanelWidget {
        private final int labelWidth;
        private final Supplier<LedColor> getter;
        private final Consumer<LedColor> setter;

        ColorRow(int x, int y, int width, int height, Component label, int labelWidth, Supplier<LedColor> getter, Consumer<LedColor> setter) {
            super(x, y, width, height, label);
            this.labelWidth = Math.max(0, Math.min(labelWidth, width - 24));
            this.getter = getter;
            this.setter = setter;
        }

        private int cell() {
            int available = this.width - this.labelWidth;
            return Math.max(6, Math.min(SWATCH + 2, available / LedColor.values().length));
        }

        int indexAt(double mouseX) {
            int cell = this.cell();
            int index = (int) Math.floor((mouseX - this.getX() - this.labelWidth) / cell);
            return index >= 0 && index < LedColor.values().length ? index : -1;
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            int index = this.indexAt(event.x());
            if (index >= 0) {
                this.setter.accept(LedColor.values()[index]);
            }
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            LedColor[] values = LedColor.values();
            int current = this.getter.get().ordinal();
            if (event.isLeft()) {
                this.setter.accept(values[(current + values.length - 1) % values.length]);
                return true;
            }
            if (event.isRight()) {
                this.setter.accept(values[(current + 1) % values.length]);
                return true;
            }
            return false;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            WidgetState state = this.state();
            ThemedWidgets.label(g, this.font(), this.getMessage(), this.getX(), this.getY(), this.height, this.labelWidth, state);
            int cell = this.cell();
            int size = Math.min(cell - 2, this.height - 2);
            int y0 = this.getY() + (this.height - size) / 2;
            LedColor selected = this.getter.get();
            int hovered = this.isHovered() ? this.indexAt(mouseX) : -1;
            for (LedColor color : LedColor.values()) {
                int x0 = this.getX() + this.labelWidth + color.ordinal() * cell;
                boolean chosen = color == selected;
                int border = !this.active ? Palette.BORDER : chosen ? Palette.ACCENT : hovered == color.ordinal() ? Palette.HOVER_OUTLINE : Palette.BORDER_STRONG;
                g.fill(x0, y0, x0 + size, y0 + size, border);
                int fill = this.active ? (0xFF000000 | color.rgb()) : Palette.DISABLED;
                int inset = chosen ? 2 : 1;
                g.fill(x0 + inset, y0 + inset, x0 + size - inset, y0 + size - inset, fill);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, Component.empty().append(this.getMessage()).append(": ")
                    .append(Component.translatable(this.getter.get().translationKey())));
        }
    }
}
