package de.lardtom.propwash.ui.client.tab;

import de.lardtom.propwash.ui.TuneKeys;
import de.lardtom.propwash.ui.TuneParam;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.DroneContext;
import de.lardtom.propwash.ui.client.TuneHost;
import de.lardtom.propwash.ui.client.TuneModel;
import de.lardtom.propwash.ui.client.UiConfig;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.ChoiceButton;
import de.lardtom.propwash.ui.client.widget.NumberSpinner;
import de.lardtom.propwash.ui.client.widget.ScrollPanel;
import de.lardtom.propwash.ui.client.widget.ValueSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public abstract class UiTab extends ConfigTab {
    protected static final int ROW = Metrics.ROW_HEIGHT;
    protected static final int FIELD = Metrics.CONTROL_HEIGHT;
    private static final int LABEL_PADDING = Metrics.PADDING;

    private @Nullable TuneModel fallbackTune;
    private @Nullable DroneContext fallbackContext;
    protected @Nullable ScrollPanel scroll;
    protected double scrollOffset;

    protected TuneModel tune() {
        if (this.host instanceof TuneHost screen) {
            return screen.tune();
        }
        if (this.fallbackTune == null) {
            this.fallbackTune = TuneModel.detached(this.context().build(), UiConfig.get().defaultTune);
        }
        return this.fallbackTune;
    }

    protected DroneContext context() {
        if (this.host instanceof TuneHost screen) {
            return screen.context();
        }
        if (this.fallbackContext == null) {
            this.fallbackContext = DroneContext.resolve(Minecraft.getInstance());
        }
        return this.fallbackContext;
    }

    protected Font font() {
        return this.host.font();
    }

    void requestRebuild() {
        this.host.rebuild();
    }

    protected void flash(Component message, int color) {
        if (this.host instanceof ConfiguratorScreen screen) {
            screen.flash(message, color);
        }
    }

    protected ScrollPanel beginScroll() {
        this.scroll = new ScrollPanel(this.x, this.y, this.width, this.height, this.scrollOffset);
        return this.scroll;
    }

    protected void endScroll(int contentHeight) {
        if (this.scroll != null) {
            this.scroll.setContentHeight(contentHeight);
            this.scrollOffset = this.scroll.offset();
        }
    }

    @Override
    public boolean scrollsItself() {
        return this.scroll != null;
    }

    @Override
    public boolean contentAbove() {
        return this.scroll != null && this.scroll.offset() > 0;
    }

    @Override
    public boolean contentBelow() {
        return this.scroll != null && this.scroll.offset() < this.scroll.maxOffset();
    }

    @Override
    public boolean rowSnapped() {
        return this.scroll != null && this.scroll.snapped();
    }

    @Override
    public int clipBottom() {
        return this.scroll != null ? this.scroll.clipBottom() : Integer.MAX_VALUE;
    }

    protected int top() {
        return this.scroll != null ? this.scroll.top() : this.y;
    }

    protected int innerWidth() {
        return this.scroll != null ? this.scroll.innerWidth() : this.width;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.scroll != null && this.scroll.rect().containsPoint((int) mouseX, (int) mouseY) && this.scroll.scroll(scrollY)) {
            this.scrollOffset = this.scroll.offset();
            return true;
        }
        return false;
    }

    @Override
    public boolean reveal(AbstractWidget widget) {
        if (this.scroll == null || !this.scroll.reveal(widget)) {
            return false;
        }
        this.scrollOffset = this.scroll.offset();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return this.scroll != null && this.scroll.mouseClicked(event.x(), event.y());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.scroll != null && this.scroll.mouseDragged(event.y())) {
            this.scrollOffset = this.scroll.offset();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.scroll != null) {
            this.scroll.mouseReleased();
        }
        return false;
    }

    protected void extractScrollbar(GuiGraphicsExtractor g) {
        if (this.scroll != null) {
            this.scroll.extractScrollbar(g);
        }
    }

    protected <T extends AbstractWidget> T add(T widget) {
        this.host.addWidget(widget);
        if (this.scroll != null) {
            this.scroll.add(widget);
        }
        return widget;
    }

    protected static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    public static net.minecraft.network.chat.MutableComponent label(String tuneKey) {
        return Component.translatable("propwash.ui.tune." + tuneKey);
    }

    public static @Nullable Tooltip tip(String tuneKey) {
        String key = "propwash.ui.tune." + tuneKey + ".tip";
        if (!Language.getInstance().has(key)) {
            return null;
        }
        return Tooltip.create(Component.empty().append(ThemeText.accent(label(tuneKey))).append("\n")
                .append(Component.translatable(key)).append("\n")
                .append(ThemeText.muted(Component.translatable("propwash.ui.hint.reset"))));
    }

    public static Component enumName(Enum<?> constant) {
        return Component.translatable("propwash.ui.enum." + constant.getDeclaringClass().getSimpleName().toLowerCase(Locale.ROOT)
                + "." + constant.name().toLowerCase(Locale.ROOT));
    }

    public static String format(TuneParam p, double value) {
        if (p.kind() == TuneParam.Kind.ENUM) {
            List<? extends Enum<?>> constants = p.constants();
            int i = (int) Math.max(0, Math.min(constants.size() - 1, Math.rint(value)));
            return constants.isEmpty() ? "" : enumName(constants.get(i)).getString();
        }
        if (p.kind() == TuneParam.Kind.BOOL) {
            return Component.translatable(value >= 0.5 ? "propwash.ui.value.on" : "propwash.ui.value.off").getString();
        }
        if (p.isOff(value)) {
            return Component.translatable("propwash.ui.value.off").getString();
        }
        String number = p.formatNumber(value);
        String unit = p.unit();
        if (unit.isEmpty()) {
            return number;
        }
        return unit.equals("%") || unit.equals("°") ? number + unit : number + " " + unit;
    }

    protected int labelWidth(List<String> tuneKeys, int columnWidth) {
        int widest = 0;
        for (String key : tuneKeys) {
            widest = Math.max(widest, this.font().width(label(key)));
        }
        return Math.min(widest + LABEL_PADDING, columnWidth * 5 / 8);
    }

    protected ValueSlider slider(String key, int x, int y, int width, int labelWidth) {
        TuneParam p = TuneModel.param(key);
        ValueSlider s = new ValueSlider(x, y, width, FIELD, label(key), labelWidth, p.sliderMin(), p.max(), p.step(),
                () -> {
                    double v = this.tune().value(key);
                    return p.isOff(v) ? p.sliderMin() : v;
                },
                v -> this.tune().set(key, v),
                () -> {
                    double d = this.tune().defaultValue(key);
                    return p.isOff(d) ? p.sliderMin() : d;
                },
                v -> format(p, p.sanitize(v)));
        s.typed(p::fromDisplay, p.display(p.allowOff() ? 0.0 : p.min()), p.display(p.max()), p.displayDecimals() > 0);
        s.setTooltip(tip(key));
        return this.add(s);
    }

    protected NumberSpinner spinner(String key, int x, int y, int width) {
        TuneParam p = TuneModel.param(key);
        NumberSpinner s = new NumberSpinner(x, y, width, FIELD, label(key), () -> this.tune().value(key), n -> this.tune().step(key, n),
                v -> this.tune().set(key, v), () -> this.tune().defaultValue(key), v -> p.formatNumber(v), p::fromDisplay, p.step() * 1e-3);
        s.range(p.display(p.min()), p.display(p.max()));
        if (p.displayDecimals() == 0) {
            s.integers();
        }
        s.setTooltip(tip(key));
        return this.add(s);
    }

    protected ChoiceButton<Integer> choice(String key, int x, int y, int width, int labelWidth, Runnable afterChange) {
        TuneParam p = TuneModel.param(key);
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i <= (int) p.max(); i++) {
            values.add(i);
        }
        ChoiceButton<Integer> c = new ChoiceButton<>(x, y, width, FIELD, label(key), labelWidth, values,
                () -> this.tune().choice(key),
                v -> {
                    this.tune().set(key, v);
                    afterChange.run();
                },
                v -> Component.literal(format(p, v)),
                () -> (int) Math.rint(this.tune().defaultValue(key)));
        c.setTooltip(tip(key));
        return this.add(c);
    }

    protected ChoiceButton<Boolean> toggle(String key, int x, int y, int width, int labelWidth) {
        ChoiceButton<Boolean> c = ChoiceButton.toggle(x, y, width, FIELD, label(key), labelWidth,
                () -> this.tune().flag(key),
                v -> this.tune().set(key, v ? 1.0 : 0.0),
                v -> Component.translatable(v ? "propwash.ui.value.on" : "propwash.ui.value.off"),
                () -> this.tune().defaultValue(key) >= 0.5);
        c.setTooltip(tip(key));
        return this.add(c);
    }

    protected void section(GuiGraphicsExtractor g, Component title, int x, int y, int width) {
        ThemeDraw.sectionTitle(g, this.font(), title, x, y, width);
    }

    protected static String axisLabel(int axis) {
        return Component.translatable("propwash.ui.axis." + TuneKeys.axisName(de.lardtom.propwash.sim.Axis.values()[axis])).getString();
    }
}
