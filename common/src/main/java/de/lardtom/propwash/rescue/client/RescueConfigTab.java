package de.lardtom.propwash.rescue.client;

import de.lardtom.propwash.content.Numbers;
import de.lardtom.propwash.input.client.KeyNames;
import de.lardtom.propwash.rescue.FailsafeAction;
import de.lardtom.propwash.rescue.HomeMode;
import de.lardtom.propwash.rescue.RescueSettings;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.StepSlider;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

public final class RescueConfigTab extends ConfigTab {
    public static final String ID = "rescue";
    private static final int STATUS_LINE = Metrics.LINE_HEIGHT + 2;

    private final List<AbstractWidget> widgets = new ArrayList<>();
    private RescueSettings edit;
    private RescueTabLayout layout;
    private int scroll;
    private boolean draggingScrollbar;

    public RescueConfigTab() {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.rescue.config.title");
    }

    @Override
    public int order() {
        return 700;
    }

    @Override
    protected void build() {
        if (this.edit == null) {
            this.edit = RescueConfigs.get().copy();
        }
        RescueSettings e = this.edit;
        this.widgets.clear();
        this.draggingScrollbar = false;

        this.widgets.add(new RescueCycleButton<>(0, RescueTabLayout.WIDGET_HEIGHT, Component.translatable("propwash.rescue.config.failsafe"),
                List.of(FailsafeAction.values()), e.failsafeAction, v -> Component.translatable(v.translationKey()),
                v -> Tooltip.create(Component.translatable(v.translationKey() + ".tooltip")),
                v -> this.set(s -> s.failsafeAction = v)));
        this.widgets.add(new RescueCycleButton<>(0, RescueTabLayout.WIDGET_HEIGHT, Component.translatable("propwash.rescue.config.home_mode"),
                List.of(HomeMode.values()), e.homeMode, v -> Component.translatable(v.translationKey()),
                v -> Tooltip.create(Component.translatable(v.translationKey() + ".tooltip")),
                v -> this.set(s -> s.homeMode = v)));
        this.slider("altitude", RescueSettings.MIN_ALTITUDE, RescueSettings.MAX_ALTITUDE, 1.0, e.altitude,
                v -> meters(v, 0), v -> e.altitude = v);
        this.slider("speed", RescueSettings.MIN_SPEED, RescueSettings.MAX_SPEED, 0.5, e.speed,
                v -> Component.translatable("propwash.rescue.unit.mps", format(v, 1)), v -> e.speed = v);
        this.slider("descent", RescueSettings.MIN_DESCENT, RescueSettings.MAX_DESCENT, 0.1, e.descentRate,
                v -> Component.translatable("propwash.rescue.unit.mps", format(v, 1)), v -> e.descentRate = v);
        this.slider("min_distance", RescueSettings.MIN_DISTANCE_LIMIT, RescueSettings.MAX_DISTANCE_LIMIT, 1.0, e.minDistance,
                v -> meters(v, 0), v -> e.minDistance = v);
        this.toggle("obstacles", e.obstacleAvoidance, v -> e.obstacleAvoidance = v);
        this.slider("clearance", RescueSettings.MIN_CLEARANCE, RescueSettings.MAX_CLEARANCE, 0.1, e.clearance,
                v -> meters(v, 1), v -> e.clearance = v);
        this.toggle("stick_override", e.stickOverride, v -> e.stickOverride = v);
        this.slider("override_threshold", RescueSettings.MIN_OVERRIDE * 100.0, RescueSettings.MAX_OVERRIDE * 100.0, 5.0,
                e.stickOverrideThreshold * 100.0,
                v -> Component.translatable("propwash.rescue.unit.percent", format(v, 0)), v -> e.stickOverrideThreshold = v / 100.0)
                .percentRange();
        this.toggle("auto_land", e.autoLand, v -> e.autoLand = v);

        int needed = RescueTabLayout.MIN_COLUMN_WIDTH;
        for (AbstractWidget widget : this.widgets) {
            if (widget instanceof RescueCycleButton<?> cycle) {
                needed = Math.max(needed, cycle.neededWidth(Minecraft.getInstance().font));
            }
        }
        this.layout = new RescueTabLayout(this.width, this.height, this.widgets.size(), needed);
        for (AbstractWidget widget : this.widgets) {
            widget.setWidth(this.layout.columnWidth());
            this.host.addWidget(widget);
        }
        this.applyScroll(this.scroll);
    }

    List<AbstractWidget> widgets() {
        return List.copyOf(this.widgets);
    }

    int scroll() {
        return this.scroll;
    }

    int maxScroll() {
        return this.layout == null ? 0 : this.layout.maxScroll();
    }

    int columns() {
        return this.layout == null ? 0 : this.layout.columns();
    }

    boolean statusVisible() {
        return this.layout != null && this.layout.statusVisible(this.scroll);
    }

    void scrollTo(int row) {
        if (this.layout != null) {
            this.applyScroll(row);
        }
    }

    @Override
    public boolean reveal(AbstractWidget widget) {
        int index = this.widgets.indexOf(widget);
        if (index < 0 || this.layout == null) {
            return false;
        }
        if (!this.layout.visible(index, this.scroll)) {
            int row = index / this.layout.columns();
            this.applyScroll(row < this.scroll ? row : row - this.layout.visibleRows() + 1);
        }
        return true;
    }

    private void applyScroll(int row) {
        this.scroll = this.layout.clampScroll(row);
        Screen screen = this.host.screen();
        GuiEventListener focused = screen.getFocused();
        for (int i = 0; i < this.widgets.size(); i++) {
            AbstractWidget widget = this.widgets.get(i);
            widget.setPosition(this.x + this.layout.left(i), this.y + this.layout.top(i, this.scroll));
            widget.visible = this.layout.visible(i, this.scroll);
            if (!widget.visible && widget == focused) {
                screen.setFocused(null);
            }
        }
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (this.host == null || this.layout == null) {
            return;
        }
        if (this.layout.scrollable()) {
            int trackX = this.x + this.layout.scrollbarLeft();
            graphics.fill(trackX, this.y, trackX + RescueTabLayout.SCROLLBAR_TRACK, this.y + this.height, Palette.FIELD);
            int thumbY = this.y + this.layout.thumbTop(this.height, this.scroll);
            graphics.fill(trackX, thumbY, trackX + RescueTabLayout.SCROLLBAR_TRACK, thumbY + this.layout.thumbHeight(this.height),
                    this.draggingScrollbar ? Palette.ACCENT : Palette.BORDER_STRONG);
        }
        int sectionWidth = this.layout.columns() * this.layout.columnWidth() + (this.layout.columns() - 1) * RescueTabLayout.GAP;
        ThemeDraw.sectionTitle(graphics, this.host.font(), Component.translatable("propwash.rescue.config.section"), this.x, this.y, sectionWidth);
        if (!this.layout.statusVisible(this.scroll)) {
            return;
        }
        int left = this.x;
        int top = this.y + this.layout.statusTop(this.scroll);
        Vec3 home = RescueState.INSTANCE.home();
        Component homeText = home == null
                ? Component.translatable("propwash.rescue.config.status.no_home")
                : Component.translatable("propwash.rescue.config.status.home", Math.round(home.x), Math.round(home.y), Math.round(home.z));
        ThemeText.text(graphics, this.host.font(), homeText, left, top, Palette.TEXT);
        Component keys = Component.translatable("propwash.rescue.config.status.keys",
                KeyNames.of(RescueKeys.RESCUE), KeyNames.of(RescueKeys.SET_HOME));
        ThemeText.text(graphics, this.host.font(), keys, left, top + STATUS_LINE, Palette.TEXT_MUTED);
    }

    @Override
    public boolean scrollsItself() {
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.layout == null || !this.layout.scrollable() || scrollY == 0.0 || !this.inside(mouseX, mouseY)) {
            return false;
        }
        int rows = this.layout.scrollRows(scrollY);
        this.applyScroll(this.scroll + (scrollY > 0.0 ? -rows : rows));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.layout == null || !this.layout.overScrollbar(event.x() - this.x, event.y() - this.y, this.height)) {
            return false;
        }
        this.draggingScrollbar = true;
        this.applyScroll(this.layout.scrollAt(event.y() - this.y, this.height));
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!this.draggingScrollbar || this.layout == null) {
            return false;
        }
        this.applyScroll(this.layout.scrollAt(event.y() - this.y, this.height));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!this.draggingScrollbar) {
            return false;
        }
        this.draggingScrollbar = false;
        return true;
    }

    private boolean inside(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
    }

    @Override
    public void resetDefaults() {
        this.edit = new RescueSettings();
        if (this.host != null) {
            this.host.changed();
            this.host.rebuild();
        }
    }

    @Override
    public void save() {
        if (this.edit == null) {
            return;
        }
        RescueSettings copy = this.edit.copy();
        copy.sanitize();
        RescueConfigs.CONFIG.set(copy);
    }

    private void set(Consumer<RescueSettings> change) {
        change.accept(this.edit);
        if (this.host != null) {
            this.host.changed();
        }
    }

    private StepSlider slider(String key, double min, double max, double step, double value, DoubleFunction<Component> format,
                              DoubleConsumer setter) {
        Component label = Component.translatable("propwash.rescue.config." + key);
        StepSlider slider = new StepSlider(0, 0, RescueTabLayout.MIN_COLUMN_WIDTH, RescueTabLayout.WIDGET_HEIGHT, min, max, step, value,
                v -> CommonComponents.optionNameValue(label, format.apply(v)), v -> this.set(s -> setter.accept(v)));
        slider.setTooltip(Tooltip.create(Component.translatable("propwash.rescue.config." + key + ".tooltip")));
        this.widgets.add(slider);
        return slider;
    }

    private void toggle(String key, boolean value, Consumer<Boolean> setter) {
        Tooltip tooltip = Tooltip.create(Component.translatable("propwash.rescue.config." + key + ".tooltip"));
        this.widgets.add(RescueCycleButton.onOff(0, RescueTabLayout.WIDGET_HEIGHT, Component.translatable("propwash.rescue.config." + key),
                value, tooltip, v -> this.set(s -> setter.accept(v))));
    }

    private static Component meters(double v, int decimals) {
        return Component.translatable("propwash.rescue.unit.meters", format(v, decimals));
    }

    private static String format(double v, int decimals) {
        return Numbers.fixed(v, decimals).getString();
    }
}
