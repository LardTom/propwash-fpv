package de.lardtom.propwash.input.client.screen;

import de.lardtom.propwash.content.Numbers;
import de.lardtom.propwash.input.ChannelMapping;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.KeyboardSettings;
import de.lardtom.propwash.input.MfiHint;
import de.lardtom.propwash.input.MouseAxis;
import de.lardtom.propwash.input.MouseMode;
import de.lardtom.propwash.input.MouseSettings;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.SetupStatus;
import de.lardtom.propwash.input.StickChannel;
import de.lardtom.propwash.input.StickMapper;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.SwitchSources;
import de.lardtom.propwash.input.ThrottleMode;
import de.lardtom.propwash.input.client.DeviceInfo;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.input.client.MacControllers;
import de.lardtom.propwash.input.client.RawInput;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.ui.client.config.StepSlider;
import de.lardtom.propwash.ui.client.search.SearchTags;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.ScrollPanel;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ReceiverTab extends InputTab {
    private static final int FLOW_HEIGHT = 1 << 16;
    public static final String ID = "receiver";
    private static final long NOTICE_MILLIS = 8000L;
    private static final int STATUS_TOOLTIP_WIDTH = 220;
    private static final int PREVIEW_MAX = 132;
    private static final int MOUSE_KEY_ROWS = 7;
    private static final int INVERT_GAP = 2;

    private final StickMapper mapper = new StickMapper();
    private long physicsFrameNanos;
    private int barsX;
    private int barsY;
    private int barsWidth;
    private int statusWidth;
    private int statusLines = 1;
    private int channelsPerRow = 2;
    private @Nullable Component notice;
    private long noticeUntil;
    private int macTitleY = -1;
    private int columnWidth;
    private @Nullable ScrollPanel scroll;
    private double scrollOffset;
    private boolean mouseLayout;
    private final MousePreview mousePreview = new MousePreview();
    private int mouseTitleY = -1;
    private int keyboardTitleY = -1;
    private int previewX;
    private int previewY;
    private int previewWidth;
    private int previewHeight;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.input.tab.receiver");
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    protected boolean requestsPreview() {
        return true;
    }

    @Override
    protected void build() {
        this.rememberSignature();
        RawInput raw = this.raw();
        DeviceProfile profile = this.profile(raw);
        boolean twoColumns = this.width >= 2 * Metrics.COLUMN_MIN + Metrics.GAP;
        int columnWidth = twoColumns ? (this.width - Metrics.GAP) / 2 : this.width;
        this.mouseLayout = raw == null && this.working().mouseSelected();
        this.scroll = this.mouseLayout ? new ScrollPanel(this.x, this.y, this.width, this.height, this.scrollOffset) : null;
        this.mouseTitleY = -1;
        this.keyboardTitleY = -1;
        if (this.scroll != null) {
            columnWidth = twoColumns ? (this.scroll.innerWidth() - Metrics.GAP) / 2 : this.scroll.innerWidth();
        }
        int origin = this.scroll != null ? this.scroll.top() : this.y;
        this.statusWidth = this.scroll != null ? this.scroll.innerWidth() : this.width;
        this.statusLines = statusLineCount(this.host.font(), this.status().text(), this.statusWidth);
        int statusHeight = statusHeight(this.statusLines);
        int bx = this.x;
        int by = origin + statusHeight;
        this.place(Button.builder(Component.translatable("propwash.input.tab.open_wizard"), b -> this.openWizard())
                .bounds(bx, by, columnWidth, Metrics.CONTROL_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("propwash.input.tab.open_wizard.tooltip")))
                .build());
        by += Metrics.ROW_HEIGHT;
        List<Choice> choices = this.choices();
        Choice current = choices.getFirst();
        for (Choice choice : choices) {
            if (choice.guid().equals(this.working().selectedGuid)) {
                current = choice;
            }
        }
        SearchTags.label(this.place(CycleButton.builder(Choice::label, current).withValues(choices)
                .withTooltip(v -> Tooltip.create(Component.translatable("propwash.input.device.tooltip")))
                .create(bx, by, columnWidth, Metrics.CONTROL_HEIGHT, Component.translatable("propwash.input.device"), (b, v) -> {
                    boolean mouseBefore = this.working().mouseSelected();
                    this.working().selectedGuid = v.guid();
                    TransmitterInput.INSTANCE.requestPreview(v.guid());
                    this.markChanged();
                    if (mouseBefore != this.working().mouseSelected()) {
                        this.mousePreview.reset();
                        this.rebuildLater();
                    }
                })), Component.translatable("propwash.input.device"), Component.translatable("propwash.input.mouse_keyboard"),
                Component.translatable("propwash.input.mouse.search"));
        by += Metrics.ROW_HEIGHT;
        if (this.mouseLayout) {
            by = this.buildMouse(bx, by, columnWidth, origin);
        }
        if (raw != null && profile != null) {
            this.host.addWidget(CycleButton.builder((ThrottleMode m) -> Component.translatable(m.translationKey()), profile.throttleMode)
                    .withValues(ThrottleMode.values())
                    .withTooltip(m -> Tooltip.create(Component.translatable(m.descriptionKey())))
                    .create(bx, by, columnWidth, Metrics.CONTROL_HEIGHT, Component.translatable("propwash.input.throttle_mode"), (b, v) -> {
                        profile.throttleMode = v;
                        this.mapper.reset();
                        this.markChanged();
                    }));
            by += Metrics.ROW_HEIGHT;
            this.host.addWidget(new StepSlider(bx, by, columnWidth, Metrics.CONTROL_HEIGHT, 0.0F, DeviceProfile.MAX_DEADBAND, 0.005F, profile.deadband,
                    v -> Component.translatable("propwash.input.deadband", Numbers.fixed(v * 100.0, 1)), v -> {
                        profile.deadband = (float) v;
                        this.markChanged();
                    }).percent());
            by += Metrics.ROW_HEIGHT;
            by = this.channelWidgets(raw, profile, bx, by, columnWidth);
        }
        if (!this.mouseLayout) {
            this.place(CycleButton.onOffBuilder(this.working().keyboardFallback)
                    .withTooltip(v -> Tooltip.create(Component.translatable("propwash.input.keyboard_fallback.tooltip")))
                    .create(bx, by, columnWidth, Metrics.CONTROL_HEIGHT, Component.translatable("propwash.input.keyboard_fallback"), (b, v) -> {
                        this.working().keyboardFallback = v;
                        this.markChanged();
                        this.rebuildLater();
                    }));
            by += Metrics.ROW_HEIGHT;
        } else {
            this.keyboardTitleY = by - origin + Metrics.SECTION_GAP;
            by += Metrics.SECTION_GAP + Metrics.SECTION_HEIGHT;
        }
        if (raw == null && (this.working().keyboardFallback || this.working().keyboardSelected())) {
            KeyboardSettings keyboard = this.working().keyboard;
            this.place(new StepSlider(bx, by, columnWidth, Metrics.CONTROL_HEIGHT, 0.1F, 1.0F, 0.05F, keyboard.rollPitchLimit,
                    v -> Component.translatable("propwash.input.keyboard.limit", Math.round(v * 100.0F)), v -> {
                        keyboard.rollPitchLimit = (float) v;
                        keyboard.yawLimit = (float) v;
                        this.markChanged();
                    }).percent());
            by += Metrics.ROW_HEIGHT;
            this.place(new StepSlider(bx, by, columnWidth, Metrics.CONTROL_HEIGHT, 0.05F, 2.0F, 0.05F, keyboard.throttleRate,
                    v -> Component.translatable("propwash.input.keyboard.throttle_rate", Math.round(v * 100.0F)), v -> {
                        keyboard.throttleRate = (float) v;
                        this.markChanged();
                    }).display(100.0, 0.0));
            by += Metrics.ROW_HEIGHT;
        }
        this.columnWidth = columnWidth;
        this.macTitleY = -1;
        if (MacControllers.optionShown()) {
            by += Metrics.SECTION_GAP;
            this.macTitleY = by - origin;
            by += Metrics.SECTION_HEIGHT;
            MfiHint.Plan plan = MacControllers.shownPlan(this.working());
            Component tooltip = plan.optionLocked()
                    ? Component.translatable("propwash.input.apple_gc.locked", String.valueOf(plan.environment()))
                    : Component.translatable("propwash.input.apple_gc.tooltip");
            CycleButton<Boolean> apple = this.place(CycleButton.onOffBuilder(plan.appleFramework())
                    .withTooltip(v -> Tooltip.create(tooltip))
                    .create(bx, by, columnWidth, Metrics.CONTROL_HEIGHT, Component.translatable("propwash.input.apple_gc"), (b, v) -> {
                        this.working().appleGameController = v;
                        this.markChanged();
                    }));
            apple.active = !plan.optionLocked();
            by += Metrics.ROW_HEIGHT;
        }
        this.barsX = twoColumns ? this.x + columnWidth + Metrics.GAP : this.x;
        this.barsY = twoColumns ? this.y + statusHeight : by + Metrics.SECTION_GAP;
        this.barsWidth = columnWidth;
        if (this.scroll != null) {
            this.finishMouseLayout(twoColumns, columnWidth, by - origin, statusHeight);
        }
    }

    private <T extends AbstractWidget> T place(T widget) {
        this.host.addWidget(widget);
        if (this.scroll != null) {
            this.scroll.add(widget);
        }
        return widget;
    }

    private int buildMouse(int bx, int by, int w, int origin) {
        MouseSettings mouse = this.working().mouse;
        Component keyword = Component.translatable("propwash.input.mouse.search");
        this.mouseTitleY = by - origin + Metrics.SECTION_GAP;
        by += Metrics.SECTION_GAP + Metrics.SECTION_HEIGHT;
        SearchTags.label(this.place(CycleButton.builder((MouseMode m) -> Component.translatable(m.translationKey()), mouse.mode)
                .withValues(MouseMode.values())
                .withTooltip(m -> Tooltip.create(Component.translatable(m.descriptionKey())))
                .create(bx, by, w, Metrics.CONTROL_HEIGHT, Component.translatable("propwash.input.mouse.mode"), (b, v) -> {
                    mouse.mode = v;
                    this.mousePreview.reset();
                    this.markChanged();
                    this.rebuildLater();
                })), Component.translatable("propwash.input.mouse.mode"), keyword);
        by += Metrics.ROW_HEIGHT;
        SearchTags.label(this.place(CycleButton.builder((MouseAxis a) -> Component.translatable(a.translationKey()), mouse.xAxis)
                .withValues(MouseAxis.values())
                .withTooltip(a -> Tooltip.create(Component.translatable("propwash.input.mouse.x_axis.tooltip")))
                .create(bx, by, w, Metrics.CONTROL_HEIGHT, Component.translatable("propwash.input.mouse.x_axis"), (b, v) -> {
                    mouse.xAxis = v;
                    this.mousePreview.reset();
                    this.markChanged();
                    this.rebuildLater();
                })), Component.translatable("propwash.input.mouse.x_axis"), keyword);
        by += Metrics.ROW_HEIGHT;
        by = this.mouseSlider(bx, by, w, MouseSettings.MIN_SENSITIVITY, MouseSettings.MAX_SENSITIVITY, 0.05F, mouse.sensitivity, "sensitivity",
                v -> Component.translatable("propwash.input.mouse.sensitivity", Math.round(v * 100.0F)), v -> mouse.sensitivity = v, true, keyword);
        by = this.mouseSlider(bx, by, w, MouseSettings.MIN_DPI, 12800.0F, 50.0F, mouse.dpi, "dpi",
                v -> Component.translatable("propwash.input.mouse.dpi", Math.round(v)), v -> mouse.dpi = Math.round(v), true, keyword);
        SearchTags.label(this.place(CycleButton.onOffBuilder(mouse.invertY)
                .withTooltip(v -> Tooltip.create(Component.translatable("propwash.input.mouse.invert_y.tooltip")))
                .create(bx, by, w, Metrics.CONTROL_HEIGHT, Component.translatable("propwash.input.mouse.invert_y"), (b, v) -> {
                    mouse.invertY = v;
                    this.mousePreview.reset();
                    this.markChanged();
                })), Component.translatable("propwash.input.mouse.invert_y"), keyword);
        by += Metrics.ROW_HEIGHT;
        boolean rate = mouse.mode != MouseMode.HOLD || mouse.xAxis == MouseAxis.YAW;
        boolean hold = mouse.mode != MouseMode.RATE;
        by = this.mouseSlider(bx, by, w, 0.0F, MouseSettings.MAX_SMOOTHING_MS, 1.0F, mouse.smoothingMs, "smoothing",
                v -> Component.translatable("propwash.input.mouse.smoothing", Math.round(v)), v -> mouse.smoothingMs = v, rate, keyword);
        by = this.mouseSlider(bx, by, w, 0.0F, MouseSettings.MAX_DEADZONE, 0.005F, mouse.deadzone, "deadzone",
                v -> Component.translatable("propwash.input.mouse.deadzone", Numbers.fixed(v * 100.0, 1)), v -> mouse.deadzone = v, true, keyword);
        by = this.mouseSlider(bx, by, w, 0.0F, 1.0F, 0.05F, mouse.expo, "expo",
                v -> Component.translatable("propwash.input.mouse.expo", Math.round(v * 100.0F)), v -> mouse.expo = v, true, keyword);
        by = this.mouseSlider(bx, by, w, 0.0F, MouseSettings.MAX_RETURN_SPEED, 0.05F, mouse.returnSpeed, "return_speed",
                v -> v <= 0.0F ? Component.translatable("propwash.input.mouse.return_speed.off")
                        : Component.translatable("propwash.input.mouse.return_speed", Math.round(v * 100.0F)), v -> mouse.returnSpeed = v, hold, keyword);
        by = this.mouseSlider(bx, by, w, 0.0F, MouseSettings.MAX_WHEEL_STEP, 0.01F, mouse.wheelStep, "wheel",
                v -> v <= 0.0F ? Component.translatable("propwash.input.mouse.wheel.off")
                        : Component.translatable("propwash.input.mouse.wheel", Math.round(v * 100.0F)), v -> mouse.wheelStep = v, true, keyword);
        return by;
    }

    private int mouseSlider(int bx, int by, int w, float min, float max, float step, float value, String key,
                            java.util.function.DoubleFunction<Component> label, FloatSink sink, boolean active, Component keyword) {
        StepSlider slider = new StepSlider(bx, by, w, Metrics.CONTROL_HEIGHT, min, max, step, value, label, v -> {
            sink.accept((float) v);
            this.markChanged();
        });
        slider.setTooltip(Tooltip.create(Component.translatable("propwash.input.mouse." + key + ".tooltip")));
        slider.active = active;
        SearchTags.label(this.place(slider), Component.translatable("propwash.input.mouse." + key + ".label"), keyword);
        return by + Metrics.ROW_HEIGHT;
    }

    private void finishMouseLayout(boolean twoColumns, int columnWidth, int leftBottom, int statusHeight) {
        ScrollPanel panel = this.scroll;
        if (panel == null) {
            return;
        }
        this.previewWidth = columnWidth;
        this.previewX = twoColumns ? this.x + columnWidth + Metrics.GAP : this.x;
        int top = twoColumns ? statusHeight : leftBottom + Metrics.SECTION_GAP;
        this.previewY = top + Metrics.SECTION_HEIGHT;
        this.previewHeight = MousePreview.height(columnWidth, PREVIEW_MAX);
        int keysTop = this.previewY + this.previewHeight + Metrics.SECTION_GAP;
        int rightBottom = keysTop + Metrics.SECTION_HEIGHT + MOUSE_KEY_ROWS * InputGui.ROW_HEIGHT;
        panel.setContentHeight(Math.max(leftBottom, rightBottom) + 2);
        this.scrollOffset = panel.offset();
    }

    @FunctionalInterface
    private interface FloatSink {
        void accept(float value);
    }

    private int channelWidgets(RawInput raw, DeviceProfile profile, int bx, int by, int columnWidth) {
        boolean pad = raw.usesGamepadLayout(profile);
        int axisCount = raw.axes(profile).length;
        var font = this.host.font();
        StickChannel[] channels = StickChannel.values();
        Component invertOn = Component.translatable("propwash.input.invert.short_on");
        Component invertOff = Component.translatable("propwash.input.invert.short_off");
        int invertWidth = cycleWidth(Math.max(font.width(invertOn), font.width(invertOff)));
        List<List<Integer>> axisChoices = new ArrayList<>();
        int axisText = 0;
        for (StickChannel channel : channels) {
            ChannelMapping mapping = profile.channel(channel);
            List<Integer> axes = new ArrayList<>();
            axes.add(-1);
            for (int a = 0; a < axisCount; a++) {
                axes.add(a);
            }
            if (mapping.axis >= axisCount) {
                axes.add(mapping.axis);
            }
            axisChoices.add(axes);
            Component shortName = Component.translatable(channel.shortKey());
            for (int a : axes) {
                axisText = Math.max(axisText, font.width(axisLabel(shortName, a, pad)));
            }
        }
        this.channelsPerRow = channelsPerRow(columnWidth, cycleWidth(axisText), invertWidth);
        int perRow = this.channelsPerRow;
        int pairWidth = (columnWidth - (perRow - 1) * Metrics.LABEL_GAP) / perRow;
        invertWidth = Math.min(invertWidth, Math.max(0, pairWidth - INVERT_GAP) / 2);
        for (int i = 0; i < channels.length; i++) {
            StickChannel channel = channels[i];
            ChannelMapping mapping = profile.channel(channel);
            int cx = bx + (i % perRow) * (pairWidth + Metrics.LABEL_GAP);
            int cy = by + (i / perRow) * Metrics.ROW_HEIGHT;
            Component name = Component.translatable(channel.translationKey());
            Component shortName = Component.translatable(channel.shortKey());
            this.host.addWidget(CycleButton.builder((Integer a) -> axisLabel(shortName, a, pad), mapping.axis)
                    .withValues(axisChoices.get(i))
                    .displayOnlyValue()
                    .withTooltip(a -> Tooltip.create(Component.translatable("propwash.input.channel_axis.tooltip", name,
                            a < 0 ? Component.translatable("propwash.input.binding.none") : InputLabels.axis(a, pad))))
                    .create(cx, cy, pairWidth - invertWidth - INVERT_GAP, Metrics.CONTROL_HEIGHT, name, (b, v) -> {
                        List<SwitchFunction> moved = profile.assignAxis(channel, v);
                        this.noteMovedSwitches(moved, raw, profile);
                        this.mapper.reset();
                        this.markChanged();
                        this.rebuild();
                    }));
            this.host.addWidget(CycleButton.booleanBuilder(invertOn, invertOff, mapping.invert)
                    .displayOnlyValue()
                    .withTooltip(v -> Tooltip.create(Component.translatable("propwash.input.invert.tooltip", name)))
                    .create(cx + pairWidth - invertWidth, cy, invertWidth, Metrics.CONTROL_HEIGHT, Component.translatable("propwash.input.invert"), (b, v) -> {
                        mapping.invert = v;
                        this.mapper.reset();
                        this.markChanged();
                    }));
        }
        return by + ((channels.length + perRow - 1) / perRow) * Metrics.ROW_HEIGHT;
    }

    private static Component axisLabel(Component shortName, int axis, boolean pad) {
        return Component.translatable("propwash.input.channel_axis", shortName,
                axis < 0 ? Component.translatable("propwash.input.axis_none_short") : InputLabels.axisShort(axis, pad));
    }

    static int cycleWidth(int textWidth) {
        return textWidth + 2 * (Metrics.ARROW_WIDTH + 3) + 2;
    }

    static int channelsPerRow(int columnWidth, int axisWidth, int invertWidth) {
        int pairWidth = (columnWidth - Metrics.LABEL_GAP) / 2;
        return pairWidth >= axisWidth + INVERT_GAP + invertWidth ? 2 : 1;
    }

    static int statusLineCount(net.minecraft.client.gui.Font font, Component text, int width) {
        return Math.max(1, ThemeText.balanced(font, text, width).size());
    }

    static int statusHeight(int lines) {
        return Math.max(1, lines) * Metrics.LINE_HEIGHT + Metrics.SECTION_HEIGHT - Metrics.LINE_HEIGHT;
    }

    private void noteMovedSwitches(List<SwitchFunction> affected, RawInput raw, DeviceProfile profile) {
        if (affected.isEmpty()) {
            return;
        }
        boolean pad = raw.usesGamepadLayout(profile);
        int axisCount = raw.axes(profile).length;
        MutableComponent text = Component.translatable("propwash.input.switches_moved");
        for (int i = 0; i < affected.size(); i++) {
            SwitchFunction function = affected.get(i);
            SwitchAssignment binding = profile.binding(function);
            Component where = binding.bound()
                    ? InputLabels.source(SwitchSources.of(binding), profile, axisCount, pad, raw.buttonCount())
                    : Component.translatable("propwash.input.binding.none");
            text.append(i == 0 ? " " : ", ").append(Component.translatable("propwash.input.switch_moved", Component.translatable(function.translationKey()), where));
        }
        this.notice = text;
        this.noticeUntil = Util.getMillis() + NOTICE_MILLIS;
    }

    private List<Choice> choices() {
        List<Choice> list = new ArrayList<>();
        list.add(new Choice("", Component.translatable("propwash.input.device.auto")));
        list.add(new Choice(InputConfig.KEYBOARD_GUID, Component.translatable("propwash.input.keyboard")));
        list.add(new Choice(InputConfig.MOUSE_GUID, Component.translatable("propwash.input.mouse_keyboard.short")));
        String selected = this.working().selectedGuid;
        boolean found = selected.isEmpty() || this.working().keyboardSelected();
        Set<String> seen = new HashSet<>();
        for (DeviceInfo device : TransmitterInput.INSTANCE.devices()) {
            if (seen.add(device.guid())) {
                list.add(new Choice(device.guid(), InputLabels.deviceLabel(device)));
            }
            found |= device.guid().equals(selected);
        }
        if (!found) {
            DeviceProfile p = this.working().profile(selected);
            list.add(new Choice(selected, Component.translatable("propwash.input.device.missing", p != null ? InputLabels.profileName(p, selected) : selected)));
        }
        return list;
    }

    private void openWizard() {
        if (this.dirty()) {
            this.save();
        }
        this.host.minecraft().gui.setScreen(new ReceiverWizardScreen(this.host.screen(), InputConfig.get(), result -> {
            InputConfig.commit(result);
            TransmitterInput.INSTANCE.rescanSoon();
        }));
    }

    @Override
    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.enableScissor(this.x, this.y, this.x + this.width, this.scroll != null ? this.y + this.height : this.y + FLOW_HEIGHT);
        try {
            this.extractClipped(g, mouseX, mouseY);
        } finally {
            g.disableScissor();
        }
    }

    private void extractClipped(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        long nanos = Util.getNanos();
        float dt = this.physicsFrameNanos == 0L ? 0.0F : Math.min(0.1F, (nanos - this.physicsFrameNanos) / 1.0E9F);
        this.physicsFrameNanos = nanos;
        var font = this.host.font();
        if (this.mouseLayout && this.scroll != null) {
            this.extractMouse(g, font, mouseX, mouseY);
            return;
        }
        RawInput raw = this.raw();
        DeviceProfile profile = this.profile(raw);
        RadioSource source = RadioState.INSTANCE.source();
        this.extractStatus(g, font, this.y, mouseX, mouseY);
        if (this.macTitleY >= 0) {
            ThemeDraw.sectionTitle(g, font, Component.translatable("propwash.input.apple_gc.section"), this.x, this.contentTop() + this.macTitleY,
                    this.columnWidth);
        }

        int x0 = this.barsX;
        int y0 = this.barsY;
        int w = this.barsWidth;
        int bottom = this.y + FLOW_HEIGHT;
        ThemeDraw.sectionTitle(g, font, Component.translatable("propwash.input.tab.channels"), x0, y0, w);
        y0 += Metrics.SECTION_HEIGHT;
        if (raw != null && profile != null) {
            float[] axes = raw.axes(profile);
            boolean[] buttons = raw.buttons(profile);
            boolean pad = raw.usesGamepadLayout(profile);
            StickPositions sticks = this.mapper.map(profile, axes, dt);
            y0 += InputGui.stickBars(g, font, x0, y0, w, sticks);
            boolean[] mapped = profile.mappedAxes(axes.length);
            int aux = 0;
            int labelWidth = InputGui.stickLabelWidth(font);
            for (int i = 0; i < axes.length && y0 + InputGui.ROW_HEIGHT <= bottom; i++) {
                if (mapped[i]) {
                    continue;
                }
                aux++;
                Component label = Component.translatable("propwash.input.aux", aux);
                InputGui.rawBar(g, font, x0, y0, w, label, labelWidth, axes[i], InputGui.AUX, false);
                if (mouseX >= x0 && mouseX < x0 + labelWidth && mouseY >= y0 && mouseY < y0 + 10) {
                    ThemeText.tooltip(g, font, InputLabels.axis(i, pad), mouseX, mouseY);
                }
                y0 += InputGui.ROW_HEIGHT;
            }
            if (buttons.length > 0 && y0 + 20 <= bottom) {
                y0 += 3;
                ThemeDraw.sectionTitle(g, font, Component.translatable("propwash.input.wizard.buttons"), x0, y0, w);
                y0 += Metrics.SECTION_HEIGHT;
                y0 += InputGui.buttonGrid(g, font, x0, y0, w, buttons, mouseX, mouseY, i -> InputLabels.button(i, pad, raw.buttonCount()), -1);
            }
            this.reportBottom(y0);
        } else if (source == RadioSource.KEYBOARD) {
            y0 += InputGui.stickBars(g, font, x0, y0, w, RadioState.INSTANCE.sticks()) + 4;
            ThemeDraw.sectionTitle(g, font, Component.translatable("propwash.input.tab.keyboard_keys"), x0, y0, w);
            y0 += Metrics.SECTION_HEIGHT;
            this.reportBottom(y0 + InputGui.keyTable(g, font, x0, y0, w, bottom, List.of(
                    keyRow(StickChannel.THROTTLE, InputKeys.THROTTLE_UP, InputKeys.THROTTLE_DOWN),
                    keyRow(StickChannel.YAW, InputKeys.YAW_LEFT, InputKeys.YAW_RIGHT),
                    keyRow(StickChannel.PITCH, InputKeys.PITCH_FORWARD, InputKeys.PITCH_BACK),
                    keyRow(StickChannel.ROLL, InputKeys.ROLL_LEFT, InputKeys.ROLL_RIGHT),
                    new InputGui.KeyRow(Component.translatable("propwash.input.switch.arm"), InputKeys.label(InputKeys.ARM)),
                    new InputGui.KeyRow(Component.translatable("propwash.input.tab.key_flight_mode"), InputKeys.label(InputKeys.FLIGHT_MODE)))));
        } else {
            y0 += InputGui.stickBars(g, font, x0, y0, w, StickPositions.IDLE) + 4;
            this.reportBottom(y0 + InputGui.wrapped(g, font, TransmitterInput.INSTANCE.available() ? MacControllers.noDeviceHint(this.working(), true)
                    : Component.translatable("propwash.input.wizard.no_sdl"), x0, y0, w, InputGui.DIM));
        }
    }

    private int contentTop() {
        return this.scroll != null ? this.scroll.top() : this.y;
    }

    private void extractMouse(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
        ScrollPanel panel = this.scroll;
        if (panel == null) {
            return;
        }
        int top = panel.top();
        this.extractStatus(g, font, top, mouseX, mouseY);
        if (this.mouseTitleY >= 0) {
            ThemeDraw.sectionTitle(g, font, Component.translatable("propwash.input.mouse.section"), this.x, top + this.mouseTitleY, this.columnWidth);
        }
        if (this.keyboardTitleY >= 0) {
            ThemeDraw.sectionTitle(g, font, Component.translatable("propwash.input.mouse.keyboard_section"), this.x, top + this.keyboardTitleY, this.columnWidth);
        }
        if (this.macTitleY >= 0) {
            ThemeDraw.sectionTitle(g, font, Component.translatable("propwash.input.apple_gc.section"), this.x, top + this.macTitleY, this.columnWidth);
        }
        int py = top + this.previewY;
        ThemeDraw.sectionTitle(g, font, Component.translatable("propwash.input.mouse.preview.title"), this.previewX, py - Metrics.SECTION_HEIGHT,
                this.previewWidth);
        int used = this.mousePreview.extract(g, font, this.previewX, py, this.previewWidth, this.previewHeight, this.working().mouse, mouseX, mouseY);
        int ky = py + used + Metrics.SECTION_GAP;
        ThemeDraw.sectionTitle(g, font, Component.translatable("propwash.input.tab.keyboard_keys"), this.previewX, ky, this.previewWidth);
        ky += Metrics.SECTION_HEIGHT;
        InputGui.keyTable(g, font, this.previewX, ky, this.previewWidth, this.y + this.height, mouseKeyRows(this.working().mouse));
        panel.extractScrollbar(g);
    }

    private record Status(Component text, int color, boolean notice) {
    }

    private Status status() {
        if (this.mouseLayout) {
            boolean active = RadioState.INSTANCE.source() == RadioSource.KEYBOARD && InputConfig.MOUSE_GUID.equals(RadioState.INSTANCE.deviceGuid());
            return new Status(Component.translatable(active ? "propwash.input.tab.status_mouse" : "propwash.input.tab.status_mouse_pending"),
                    active ? InputGui.GOOD : InputGui.WARN, false);
        }
        Component currentNotice = this.notice;
        if (currentNotice != null && Util.getMillis() < this.noticeUntil) {
            return new Status(currentNotice, InputGui.WARN, true);
        }
        RawInput raw = this.raw();
        if (raw != null) {
            boolean done = InputConfig.get().setupStatus(raw.device().guid()) == SetupStatus.COMPLETED;
            return new Status(Component.translatable(done ? "propwash.input.tab.status_device" : "propwash.input.tab.status_device_unset",
                    raw.device().displayName(), InputLabels.deviceTags(raw.device())), done ? InputGui.GOOD : InputGui.WARN, false);
        }
        if (RadioState.INSTANCE.source() == RadioSource.KEYBOARD) {
            boolean chosen = InputConfig.get().keyboardSelected();
            return new Status(Component.translatable(chosen ? "propwash.input.tab.status_keyboard_selected" : "propwash.input.tab.status_keyboard"),
                    chosen ? InputGui.GOOD : InputGui.WARN, false);
        }
        if (TransmitterInput.INSTANCE.linkLost()) {
            return new Status(Component.translatable("propwash.input.tab.status_link_lost"), InputGui.BAD, false);
        }
        return new Status(Component.translatable("propwash.input.no_radio"), InputGui.BAD, false);
    }

    private void extractStatus(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int top, int mouseX, int mouseY) {
        Status status = this.status();
        List<FormattedCharSequence> lines = ThemeText.balanced(font, status.text(), this.statusWidth);
        if (Math.max(1, lines.size()) != this.statusLines) {
            this.rebuildLater();
        }
        int row = top;
        for (int i = 0; i < lines.size() && i < this.statusLines; i++) {
            ThemeText.text(g, font, lines.get(i), this.x, row, status.color());
            row += Metrics.LINE_HEIGHT;
        }
        int bottom = top + this.statusLines * Metrics.LINE_HEIGHT;
        ThemeDraw.separator(g, this.x, this.x + this.statusWidth, bottom);
        boolean hovered = mouseX >= this.x && mouseX < this.x + this.statusWidth && mouseY >= top && mouseY < bottom + 2;
        if (hovered) {
            ThemeText.tooltipWrapped(g, font, statusTooltip(status.text(), status.text().getString(), status.notice()), STATUS_TOOLTIP_WIDTH, mouseX, mouseY);
        }
    }

    static List<InputGui.KeyRow> mouseKeyRows(MouseSettings mouse) {
        boolean yaw = mouse.xAxis == MouseAxis.YAW;
        Component mouseX = Component.translatable("propwash.input.mouse.key.mouse_x");
        Component mouseY = Component.translatable("propwash.input.mouse.key.mouse_y");
        Component throttleKeys = Component.translatable("propwash.input.tab.key_pair", InputKeys.label(InputKeys.THROTTLE_UP), InputKeys.label(InputKeys.THROTTLE_DOWN));
        Component throttle = mouse.wheelThrottle() ? Component.translatable("propwash.input.mouse.key.with_wheel", throttleKeys) : throttleKeys;
        Component sideKeys = Component.translatable("propwash.input.tab.key_pair", InputKeys.label(InputKeys.YAW_LEFT), InputKeys.label(InputKeys.YAW_RIGHT));
        return List.of(
                new InputGui.KeyRow(Component.translatable(StickChannel.THROTTLE.translationKey()), throttle),
                new InputGui.KeyRow(Component.translatable(StickChannel.YAW.translationKey()), yaw ? mouseX : sideKeys),
                new InputGui.KeyRow(Component.translatable(StickChannel.PITCH.translationKey()), mouseY),
                new InputGui.KeyRow(Component.translatable(StickChannel.ROLL.translationKey()), yaw ? sideKeys : mouseX),
                new InputGui.KeyRow(Component.translatable("propwash.input.mouse.key.center"), InputKeys.label(InputKeys.MOUSE_CENTER)),
                new InputGui.KeyRow(Component.translatable("propwash.input.switch.arm"), InputKeys.label(InputKeys.ARM)),
                new InputGui.KeyRow(Component.translatable("propwash.input.tab.key_flight_mode"), InputKeys.label(InputKeys.FLIGHT_MODE)));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.mouseLayout && this.mousePreview.mouseClicked(event.x(), event.y(), event.button())) {
            return true;
        }
        return this.scroll != null && this.scroll.mouseClicked(event.x(), event.y());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.mousePreview.mouseReleased();
        if (this.scroll != null) {
            this.scroll.mouseReleased();
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.mousePreview.dragging()) {
            return true;
        }
        if (this.scroll != null && this.scroll.mouseDragged(event.y())) {
            this.scrollOffset = this.scroll.offset();
            return true;
        }
        return false;
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
    public int clipBottom() {
        return this.scroll != null ? this.scroll.clipBottom() : Integer.MAX_VALUE;
    }

    @Override
    public boolean reveal(AbstractWidget widget) {
        if (this.scroll == null || !this.scroll.reveal(widget)) {
            return false;
        }
        this.scrollOffset = this.scroll.offset();
        return true;
    }

    public MousePreview mousePreview() {
        return this.mousePreview;
    }

    public boolean mouseLayout() {
        return this.mouseLayout;
    }

    static @Nullable Component statusTooltip(Component status, String shown, boolean notice) {
        return notice || ThemeText.cut(status, shown) ? status : null;
    }

    private static InputGui.KeyRow keyRow(StickChannel channel, KeyMapping first, KeyMapping second) {
        return new InputGui.KeyRow(Component.translatable(channel.translationKey()),
                Component.translatable("propwash.input.tab.key_pair", InputKeys.label(first), InputKeys.label(second)));
    }

    @Override
    public void resetDefaults() {
        RawInput raw = this.raw();
        DeviceProfile profile = this.profile(raw);
        if (raw != null && profile != null) {
            int[] sticksBefore = profile.stickAxes();
            profile.copyReceiverFrom(ProfileDefaults.reset(profile, raw.gamepadCapable(), raw.axisCount()));
            this.noteMovedSwitches(profile.moveSwitchesOffSticks(sticksBefore), raw, profile);
        }
        this.working().keyboardFallback = true;
        this.working().appleGameController = false;
        this.working().keyboard = new KeyboardSettings();
        this.working().mouse = new MouseSettings();
        this.mousePreview.reset();
        this.mapper.reset();
        this.markChanged();
        this.rebuild();
    }

    private record Choice(String guid, Component label) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Choice other && other.guid.equals(this.guid);
        }

        @Override
        public int hashCode() {
            return this.guid.hashCode();
        }
    }
}
