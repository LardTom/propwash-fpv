package de.lardtom.propwash.input.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.content.Numbers;
import de.lardtom.propwash.input.CalibrationRecorder;
import de.lardtom.propwash.input.ChannelMapping;
import de.lardtom.propwash.input.ChannelMath;
import de.lardtom.propwash.input.DetectionStage;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.RemoteDemo;
import de.lardtom.propwash.input.SetupStatus;
import de.lardtom.propwash.input.StickChannel;
import de.lardtom.propwash.input.StickDetector;
import de.lardtom.propwash.input.StickMapper;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchDetector;
import de.lardtom.propwash.input.SwitchEvaluator;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.SwitchSource;
import de.lardtom.propwash.input.ThrottleDetector;
import de.lardtom.propwash.input.ThrottleMode;
import de.lardtom.propwash.input.client.DeviceInfo;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.input.client.MacControllers;
import de.lardtom.propwash.input.client.RawInput;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.input.client.screen.remote.RemoteDemoView;
import de.lardtom.propwash.ui.TextFit;
import de.lardtom.propwash.ui.client.config.StepSlider;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.ScrollArea;
import de.lardtom.propwash.ui.client.widget.NumberInput;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class ReceiverWizardScreen extends Screen implements ThemedScreen {
    private static final int HEADER_SPACE = 36;
    private static final int DEVICE_ROW = 26;
    private static final int STATUS_SPACE = 18;
    private static final int FOOTER_SPACE = 30;
    private static final int DEVICE_LIVE_TOP = 72;
    private static final int FOOTER_GROUP_GAP = 16;
    private static final int MIN_WINDOW = 140;
    private static final StickChannel[] CHANNELS = StickChannel.values();
    private static final SwitchFunction[] ALL_FUNCTIONS = SwitchFunction.flightFunctions().stream()
            .filter(f -> f != SwitchFunction.BLACKBOX).toArray(SwitchFunction[]::new);
    private static final SwitchFunction[] FUNCTIONS_WITHOUT_3D = Arrays.stream(ALL_FUNCTIONS)
            .filter(f -> f != SwitchFunction.MODE_3D).toArray(SwitchFunction[]::new);
    private static final long STATUS_MILLIS = 4000L;
    private static final int NO_ARM_TEXT_WIDTH = 300;
    private static final int NO_ARM_GAP = 6;
    private static final long CONFIRM_MILLIS = 3000L;
    private static final int CHECK_ROW = 16;
    private static final int TRAVEL_ROW = 12;
    private static final int END_LABEL_ROW = 10;
    private static final int COLUMN_GAP = 10;
    private static final int DEMO_GAP = 6;
    private static final int DEMO_MAX_HEIGHT = 170;
    private static final int DEMO_TALL_HEIGHT = 240;
    private static final int DEMO_SIDE_MIN = 96;
    private static final int DEMO_SIDE_MAX = 170;
    private static final int SWITCH_ROW_MIN = 12;
    private static final int SWITCH_ROW_WITH_DEMO = 14;
    private static final int SWITCH_ROW_MAX = 22;
    private static final int SWITCH_ROW_STACKED = 20;
    private static final int SWITCH_BINDING_MIN = 90;
    private static final int SWITCH_BINDING_ROOM = 74;
    private static final int IDLE_DEMO_MIN = 76;
    private static final int CARD_MAX_WIDTH = 210;
    private static final int CARD_MAX_HEIGHT = 160;
    private static final int CONFIRM_BUTTON_HEIGHT = 16;

    private final @Nullable Screen parent;
    private final Consumer<InputConfig> onDone;
    private final InputConfig working;
    private Runnable onCancel = () -> {
    };
    private String previewGuid;
    private boolean finished;
    private boolean steppingAside;
    private Page page = Page.DEVICE;
    private String activeGuid = "";
    private String deviceSignature = "";

    private int channelIndex;
    private @Nullable StickDetector detector;
    private boolean[] claimedAxes = new boolean[0];
    private final EnumMap<StickChannel, Component> channelResults = new EnumMap<>(StickChannel.class);

    private Calibration calibration = Calibration.CENTER;
    private @Nullable CalibrationRecorder recorder;

    private @Nullable SwitchFunction listening;
    private @Nullable SwitchDetector switchDetector;

    private final StickMapper previewMapper = new StickMapper();
    private final SwitchEvaluator previewSwitches = new SwitchEvaluator();
    private long physicsFrameNanos;
    private StickPositions previewSticks = StickPositions.IDLE;
    private int previewBits;

    private final RemoteDemoView demo = new RemoteDemoView();
    private boolean confirming;
    private boolean confirmNoArm;

    private Component status = Component.empty();
    private int statusColor = InputGui.DIM;
    private long statusUntil;
    private boolean switchGuideCut;
    private long resetConfirmUntil;

    private int left;
    private int panelWidth;
    private int contentTop;
    private int footerY;
    private int windowTop;
    private int windowBottom;

    private final ScrollArea deviceArea = new ScrollArea();
    private boolean deviceAreaShown;

    public ReceiverWizardScreen(@Nullable Screen parent, InputConfig base, Consumer<InputConfig> onDone) {
        super(Component.translatable("propwash.input.wizard.title"));
        this.parent = parent;
        this.working = base.copy();
        this.onDone = onDone;
        this.previewGuid = this.working.selectedGuid;
    }

    public static ReceiverWizardScreen forDevice(@Nullable Screen parent, String guid, Consumer<InputConfig> onDone, Runnable onCancel) {
        ReceiverWizardScreen screen = new ReceiverWizardScreen(parent, InputConfig.get(), onDone);
        if (!screen.working.selectedGuid.isEmpty()) {
            screen.working.selectedGuid = guid;
        }
        screen.previewGuid = guid;
        screen.onCancel = onCancel;
        TransmitterInput.INSTANCE.requestPreview(guid);
        return screen;
    }

    public String previewGuid() {
        return this.previewGuid;
    }

    public static ReceiverWizardScreen standalone(@Nullable Screen parent) {
        return new ReceiverWizardScreen(parent, InputConfig.get(), result -> {
            InputConfig.commit(result);
            TransmitterInput.INSTANCE.rescanSoon();
        });
    }

    public InputConfig working() {
        return this.working;
    }

    public Page page() {
        return this.page;
    }

    public @Nullable StickChannel currentChannel() {
        return this.page == Page.CHANNELS && this.channelIndex < CHANNELS.length ? CHANNELS[this.channelIndex] : null;
    }

    public @Nullable DetectionStage detectionStage() {
        StickDetector current = this.detector;
        return current == null ? null : current.stage();
    }

    public StickPositions previewSticks() {
        return this.previewSticks;
    }

    public RemoteDemoView demo() {
        return this.demo;
    }

    public boolean confirmingThrottle() {
        return this.confirming;
    }

    public boolean switchGuideCut() {
        return this.switchGuideCut;
    }

    public String calibrationStep() {
        return this.calibration.name();
    }

    public @Nullable SwitchFunction listening() {
        return this.listening;
    }

    public boolean switchDemoRoom() {
        return this.switchArea() >= IDLE_DEMO_MIN;
    }

    public void clearStatus() {
        this.statusUntil = 0L;
    }

    public boolean statusShown() {
        return Util.getMillis() < this.statusUntil;
    }

    @Override
    protected void init() {
        this.steppingAside = false;
        this.panelWidth = Math.min(this.width - 16, 440);
        this.left = (this.width - this.panelWidth) / 2;
        RawInput raw = TransmitterInput.INSTANCE.raw();
        this.activeGuid = raw == null ? "" : raw.device().guid();
        this.deviceSignature = this.signature();
        DeviceProfile profile = this.profile(raw);
        int windowHeight = this.height - 4;
        if (this.page == Page.DEVICE && (raw == null || profile == null)) {
            windowHeight = Math.clamp(HEADER_SPACE + DEVICE_ROW + this.noDeviceTextHeight() + STATUS_SPACE + FOOTER_SPACE, MIN_WINDOW, windowHeight);
        }
        this.windowTop = (this.height - windowHeight) / 2;
        this.windowBottom = this.windowTop + windowHeight;
        this.contentTop = this.windowTop + HEADER_SPACE;
        this.footerY = this.windowBottom - FOOTER_SPACE + 6;
        switch (this.page) {
            case DEVICE -> this.initDevice(raw, profile);
            case CHANNELS -> this.initChannels(profile);
            case CALIBRATE -> this.initCalibrate(profile);
            case SWITCHES -> this.initSwitches(raw, profile);
        }
    }

    private int noDeviceTextHeight() {
        if (this.working.mouseSelected()) {
            return ThemeText.wrappedHeight(this.font, mouseIntro(), this.panelWidth) + 6;
        }
        int height = ThemeText.wrappedHeight(this.font, Component.translatable(TransmitterInput.INSTANCE.available()
                ? "propwash.input.wizard.no_device" : "propwash.input.wizard.no_sdl"), this.panelWidth) + 6;
        height += ThemeText.wrappedHeight(this.font, MacControllers.noDeviceHint(this.working, false), this.panelWidth) + 6;
        if (this.working.keyboardFallback || this.working.keyboardSelected()) {
            height += ThemeText.wrappedHeight(this.font, Component.translatable("propwash.input.wizard.keyboard_hint",
                    InputKeys.label(InputKeys.THROTTLE_UP), InputKeys.label(InputKeys.ARM)), this.panelWidth);
        }
        return height;
    }

    private @Nullable DeviceProfile profile(@Nullable RawInput raw) {
        if (raw == null) {
            return null;
        }
        DeviceInfo device = raw.device();
        return this.working.profileOrCreate(device.guid(), device.name(), device.gamepadByDefault(), raw.axisCount());
    }

    private Button footer(int slot, Component label, Button.OnPress press) {
        int w = (this.panelWidth - 4 - FOOTER_GROUP_GAP) / 3;
        int x = slot == 0 ? this.left : this.left + this.panelWidth - (3 - slot) * w - (2 - slot) * 4;
        return this.addRenderableWidget(Button.builder(label, press).bounds(x, this.footerY, w, Metrics.BUTTON_HEIGHT).build());
    }

    private void initDevice(@Nullable RawInput raw, @Nullable DeviceProfile profile) {
        int y = this.contentTop;
        List<DeviceChoice> choices = this.deviceChoices();
        DeviceChoice current = choices.getFirst();
        for (DeviceChoice choice : choices) {
            if (choice.guid().equals(this.working.selectedGuid)) {
                current = choice;
            }
        }
        this.addRenderableWidget(CycleButton.builder(DeviceChoice::label, current).withValues(choices)
                .withTooltip(v -> Tooltip.create(Component.translatable("propwash.input.device.tooltip")))
                .create(this.left, y, this.panelWidth, Metrics.BUTTON_HEIGHT, Component.translatable("propwash.input.device"), (b, v) -> this.selectDevice(v.guid())));
        y += 24;
        if (raw != null && profile != null) {
            int half = (this.panelWidth - 4) / 2;
            CycleButton<Boolean> layout = CycleButton.booleanBuilder(Component.translatable("propwash.input.layout.gamepad"),
                            Component.translatable("propwash.input.layout.joystick"), raw.usesGamepadLayout(profile))
                    .withTooltip(v -> Tooltip.create(Component.translatable("propwash.input.layout.tooltip")))
                    .create(this.left, y, half, Metrics.BUTTON_HEIGHT, Component.translatable("propwash.input.layout"), (b, v) -> this.setLayout(v));
            layout.active = raw.gamepadCapable();
            this.addRenderableWidget(layout);
            this.addRenderableWidget(CycleButton.builder((ThrottleMode m) -> Component.translatable(m.translationKey()), profile.throttleMode)
                    .withValues(ThrottleMode.values())
                    .withTooltip(m -> Tooltip.create(Component.translatable(m.descriptionKey())))
                    .create(this.left + half + 4, y, half, Metrics.BUTTON_HEIGHT, Component.translatable("propwash.input.throttle_mode"), (b, v) -> {
                        profile.throttleMode = v;
                        this.previewMapper.reset();
                        this.rebuildWidgets();
                    }));
            y += 24;
            this.addRenderableWidget(new StepSlider(this.left, y, half, Metrics.BUTTON_HEIGHT, 0.0F, DeviceProfile.MAX_DEADBAND, 0.005F, profile.deadband,
                    v -> Component.translatable("propwash.input.deadband", Numbers.fixed(v * 100.0, 1)), v -> profile.deadband = (float) v)
                    .percent());
            StepSlider rate = this.addRenderableWidget(new StepSlider(this.left + half + 4, y, half, Metrics.BUTTON_HEIGHT, 0.1F, 3.0F, 0.05F, profile.throttleRate,
                    v -> Component.translatable("propwash.input.throttle_rate", Math.round(v * 100.0F)), v -> profile.throttleRate = (float) v)
                    .display(100.0, 0.0));
            rate.active = profile.throttleMode == ThrottleMode.INCREMENTAL;
        }
        boolean confirming = Util.getMillis() < this.resetConfirmUntil;
        Button reset = this.footer(0, Component.translatable(confirming ? "propwash.input.wizard.reset_confirm" : "propwash.input.wizard.reset"), b -> this.resetProfile());
        reset.active = profile != null;
        reset.setTooltip(Tooltip.create(Component.translatable("propwash.input.wizard.reset.tooltip")));
        this.footer(1, CommonComponents.GUI_CANCEL, b -> this.onClose());
        boolean mouse = this.working.mouseSelected() && raw == null;
        Button next = this.footer(2, Component.translatable("propwash.input.wizard.next"), b -> {
            if (this.working.mouseSelected() && TransmitterInput.INSTANCE.raw() == null) {
                this.openMouseStep();
            } else {
                this.goTo(Page.CHANNELS);
            }
        });
        next.active = profile != null || mouse;
    }

    private void initChannels(@Nullable DeviceProfile profile) {
        this.footer(0, Component.translatable("propwash.input.wizard.back"), b -> this.goTo(Page.DEVICE));
        if (this.confirming && profile != null && this.channelIndex < CHANNELS.length) {
            int half = this.columnWidth();
            int y = this.confirmButtonsTop(CHANNELS[this.channelIndex], half);
            int w = (half - 4) / 2;
            Tooltip why = Tooltip.create(Component.translatable("propwash.input.wizard.throttle.confirm.tooltip"));
            Button down = this.addRenderableWidget(Button.builder(Component.translatable("propwash.input.wizard.throttle.confirm_down"),
                    b -> this.confirmThrottle(true)).bounds(this.left, y, w, CONFIRM_BUTTON_HEIGHT).build());
            down.setTooltip(why);
            Button up = this.addRenderableWidget(Button.builder(Component.translatable("propwash.input.wizard.throttle.confirm_up"),
                    b -> this.confirmThrottle(false)).bounds(this.left + half - w, y, w, CONFIRM_BUTTON_HEIGHT).build());
            up.setTooltip(why);
        }
        if (this.channelIndex < CHANNELS.length) {
            Button skip = this.footer(1, Component.translatable("propwash.input.wizard.skip_channel"), b -> this.skipChannel());
            skip.active = profile != null;
            skip.setTooltip(Tooltip.create(Component.translatable("propwash.input.wizard.skip_channel.tooltip")));
        } else {
            Button again = this.footer(1, Component.translatable("propwash.input.wizard.restart_channels"), b -> this.goTo(Page.CHANNELS));
            again.setTooltip(Tooltip.create(Component.translatable("propwash.input.wizard.restart_channels.tooltip")));
            if (profile != null) {
                this.initCheckToggles(profile);
            }
        }
        this.footer(2, Component.translatable("propwash.input.wizard.to_calibration"), b -> this.goTo(Page.CALIBRATE));
    }

    private int columnWidth() {
        return (this.panelWidth - COLUMN_GAP) / 2;
    }

    private int throttleFeedbackTop(StickChannel channel, int width) {
        int y = this.contentTop + ThemeText.wrappedHeight(this.font, Component.translatable(channel.promptKey()), width) + 4;
        if (channel == StickChannel.THROTTLE) {
            y += TRAVEL_ROW + END_LABEL_ROW;
        }
        return y + 9;
    }

    private int confirmButtonsTop(StickChannel channel, int width) {
        return this.throttleFeedbackTop(channel, width) + ThemeText.wrappedHeight(this.font,
                Component.translatable("propwash.input.wizard.throttle.confirm"), width) + 3;
    }

    private int checkToggleWidth() {
        int text = Math.max(this.font.width(Component.translatable("propwash.input.invert.short_on")),
                this.font.width(Component.translatable("propwash.input.invert.short_off")));
        return text + 2 * Metrics.ARROW_WIDTH + 14;
    }

    private int checkRowsTop() {
        return this.contentTop + ThemeText.wrappedHeight(this.font, Component.translatable("propwash.input.wizard.check"), this.columnWidth()) + 4;
    }

    private void initCheckToggles(DeviceProfile profile) {
        int toggleWidth = this.checkToggleWidth();
        int x = this.left + this.columnWidth() - toggleWidth;
        int y = this.checkRowsTop();
        for (StickChannel channel : CHANNELS) {
            ChannelMapping mapping = profile.channel(channel);
            Component name = Component.translatable(channel.translationKey());
            CycleButton<Boolean> toggle = this.addRenderableWidget(CycleButton.booleanBuilder(Component.translatable("propwash.input.invert.short_on"),
                            Component.translatable("propwash.input.invert.short_off"), mapping.invert)
                    .displayOnlyValue()
                    .withTooltip(v -> Tooltip.create(Component.translatable("propwash.input.invert.tooltip", name)))
                    .create(x, y + 1, toggleWidth, Metrics.CONTROL_HEIGHT, Component.translatable("propwash.input.invert"), (b, v) -> {
                        profile.channel(channel).invert = v;
                        this.previewMapper.reset();
                        this.setStatus(Component.translatable(v ? "propwash.input.wizard.inverted_on" : "propwash.input.wizard.inverted_off", name), InputGui.GOOD);
                    }));
            toggle.active = mapping.assigned();
            y += CHECK_ROW;
        }
    }

    static int throttleCenterMicros(ChannelMapping mapping) {
        float span = mapping.max - mapping.min;
        float fraction = span > ChannelMapping.MIN_SPAN ? (mapping.center - mapping.min) / span : 0.5F;
        if (mapping.invert) {
            fraction = 1.0F - fraction;
        }
        return ChannelMath.throttleMicros(fraction);
    }

    private static SwitchFunction[] functions() {
        return InputConfig.get().mode3dUnlocked ? ALL_FUNCTIONS : FUNCTIONS_WITHOUT_3D;
    }

    private void initCalibrate(@Nullable DeviceProfile profile) {
        this.footer(0, Component.translatable("propwash.input.wizard.back"), b -> this.goTo(Page.CHANNELS));
        switch (this.calibration) {
            case CENTER -> {
                this.footer(1, Component.translatable("propwash.input.wizard.skip"), b -> this.goTo(Page.SWITCHES));
                Button capture = this.footer(2, Component.translatable("propwash.input.wizard.capture_center"), b -> this.captureCenter());
                capture.active = profile != null;
            }
            case EXTREMES -> {
                this.footer(1, Component.translatable("propwash.input.wizard.skip"), b -> this.goTo(Page.SWITCHES));
                this.footer(2, Component.translatable("propwash.input.wizard.apply_calibration"), b -> this.applyCalibration());
            }
            case REVIEW -> {
                this.footer(1, Component.translatable("propwash.input.wizard.again"), b -> {
                    this.calibration = Calibration.CENTER;
                    this.recorder = null;
                    this.rebuildWidgets();
                });
                this.footer(2, Component.translatable("propwash.input.wizard.next"), b -> this.goTo(Page.SWITCHES));
            }
        }
    }

    private void initSwitches(@Nullable RawInput raw, @Nullable DeviceProfile profile) {
        if (this.confirmNoArm && (profile == null || profile.binding(SwitchFunction.ARM).bound())) {
            this.confirmNoArm = false;
        }
        if (this.confirmNoArm) {
            this.footer(0, Component.translatable("propwash.input.wizard.back"), b -> {
                this.confirmNoArm = false;
                this.rebuildWidgets();
            });
            this.footer(1, CommonComponents.GUI_CANCEL, b -> this.onClose());
            this.footer(2, Component.translatable("propwash.input.wizard.finish_no_arm"), b -> this.finish());
            return;
        }
        int rowHeight = this.switchRowHeight();
        int right = this.switchRowsRight();
        SwitchFunction[] functions = functions();
        for (int i = 0; i < functions.length; i++) {
            SwitchFunction function = functions[i];
            int rowY = this.contentTop + i * rowHeight;
            boolean isListening = this.listening == function;
            Button assign = this.addRenderableWidget(Button.builder(
                            Component.translatable(isListening ? "propwash.input.modes.listening_short" : "propwash.input.wizard.assign"),
                            b -> this.toggleListening(function))
                    .bounds(right - this.assignWidth() - 22, rowY, this.assignWidth(), rowHeight - 2).build());
            assign.active = profile != null && (this.listening == null || isListening);
            Button clear = this.addRenderableWidget(Button.builder(Component.literal("×"), b -> {
                if (profile != null) {
                    profile.setBinding(function, SwitchAssignment.none());
                    this.rebuildWidgets();
                }
            }).bounds(right - 20, rowY, 20, rowHeight - 2).build());
            clear.setTooltip(Tooltip.create(Component.translatable("propwash.input.modes.clear")));
            clear.active = profile != null && this.listening == null && profile.binding(function).bound();
            if (profile != null && !isListening && profile.binding(function).bound()) {
                SwitchAssignment binding = profile.binding(function);
                CycleButton<Boolean> invert = this.addRenderableWidget(InputGui.invertToggle(this.invertX(), rowY, this.invertWidth(), rowHeight - 2,
                        function, binding, v -> {
                            binding.invert = v;
                            this.setStatus(Component.translatable(v ? "propwash.input.wizard.inverted_on" : "propwash.input.wizard.inverted_off",
                                    Component.translatable(function.translationKey())), InputGui.GOOD);
                        }));
                invert.active = this.listening == null;
            }
        }
        this.footer(0, Component.translatable("propwash.input.wizard.back"), b -> this.goTo(Page.CALIBRATE));
        this.footer(1, CommonComponents.GUI_CANCEL, b -> this.onClose());
        Button finish = this.footer(2, Component.translatable("propwash.input.wizard.finish"), b -> this.finishSwitches(profile));
        finish.active = this.listening == null;
    }

    private int switchRowsRight() {
        return this.left + this.panelWidth;
    }

    private int widestSwitchName() {
        int widest = 0;
        for (SwitchFunction function : functions()) {
            widest = Math.max(widest, this.font.width(Component.translatable(function.translationKey())));
        }
        return widest;
    }

    private int assignText() {
        return Math.max(this.font.width(Component.translatable("propwash.input.wizard.assign")),
                this.font.width(Component.translatable("propwash.input.modes.listening_short")));
    }

    private boolean switchCramped() {
        int widest = this.widestSwitchName();
        int name = Math.min(widest + 11 + 8, Math.round(this.panelWidth * 0.36F));
        int invert = InputGui.INVERT_WIDTH + 3 + this.font.width(Component.translatable("propwash.input.modes.header_invert"));
        int binding = this.panelWidth - Math.min(64, this.assignText() + 12) - 22 - 8 - invert - 6 - name;
        return binding < SWITCH_BINDING_ROOM || name - 19 < widest * 7 / 10;
    }

    private int switchNameWidth() {
        int widest = this.widestSwitchName();
        int share = Math.round(this.panelWidth * 0.36F);
        if (this.switchCramped()) {
            int room = this.panelWidth - this.assignWidth() - 22 - 8 - this.invertWidth() - 6 - SWITCH_BINDING_ROOM;
            share = Math.max(share, Math.min(room, widest * 3 / 4 + 19));
        }
        return Math.min(widest + 11 + 8, share);
    }

    private void finishSwitches(@Nullable DeviceProfile profile) {
        if (profile == null || profile.binding(SwitchFunction.ARM).bound()) {
            this.finish();
            return;
        }
        this.confirmNoArm = true;
        this.statusUntil = 0L;
        this.rebuildWidgets();
    }

    private int invertWidth() {
        if (this.switchCramped()) {
            return InputGui.INVERT_WIDTH;
        }
        return InputGui.INVERT_WIDTH + 3 + this.font.width(Component.translatable("propwash.input.modes.header_invert"));
    }

    private int invertX() {
        return this.switchRowsRight() - this.assignWidth() - 22 - 8 - this.invertWidth();
    }

    private int assignWidth() {
        int text = this.assignText();
        return this.switchCramped() ? Math.min(64, text + 6) : Math.min(64, text + 12);
    }

    private int switchRowHeight() {
        int available = this.footerY - 26 - this.contentTop;
        int count = functions().length;
        if (available - count * SWITCH_ROW_WITH_DEMO >= IDLE_DEMO_MIN) {
            return Math.clamp((available - IDLE_DEMO_MIN) / count, SWITCH_ROW_WITH_DEMO, SWITCH_ROW_MAX);
        }
        return Math.clamp(available / count, SWITCH_ROW_MIN, SWITCH_ROW_MAX);
    }

    private List<DeviceChoice> deviceChoices() {
        List<DeviceChoice> list = new ArrayList<>();
        list.add(new DeviceChoice("", Component.translatable("propwash.input.device.auto")));
        list.add(new DeviceChoice(InputConfig.KEYBOARD_GUID, Component.translatable("propwash.input.keyboard")));
        list.add(new DeviceChoice(InputConfig.MOUSE_GUID, Component.translatable("propwash.input.mouse_keyboard")));
        String selected = this.working.selectedGuid;
        boolean found = selected.isEmpty() || this.working.keyboardSelected();
        Set<String> seen = new HashSet<>();
        for (DeviceInfo device : TransmitterInput.INSTANCE.devices()) {
            if (seen.add(device.guid())) {
                list.add(new DeviceChoice(device.guid(), InputLabels.deviceLabel(device)));
            }
            found |= device.guid().equals(selected);
        }
        if (!found) {
            DeviceProfile p = this.working.profile(selected);
            list.add(new DeviceChoice(selected, Component.translatable("propwash.input.device.missing", p != null ? InputLabels.profileName(p, selected) : selected)));
        }
        return list;
    }

    private String signature() {
        StringBuilder sb = new StringBuilder();
        for (DeviceInfo device : TransmitterInput.INSTANCE.devices()) {
            sb.append(device.id()).append(':').append(device.guid()).append(';');
        }
        DeviceInfo open = TransmitterInput.INSTANCE.openDevice();
        sb.append('|').append(open == null ? "" : open.guid());
        return sb.toString();
    }

    private void selectDevice(String guid) {
        boolean mouseBefore = this.working.mouseSelected();
        this.working.selectedGuid = guid;
        this.previewGuid = guid;
        TransmitterInput.INSTANCE.requestPreview(guid);
        if (mouseBefore != this.working.mouseSelected()) {
            this.rebuildWidgets();
        }
    }

    public void openMouseStep() {
        this.steppingAside = true;
        this.minecraft.gui.setScreen(new MouseSetupScreen(this, this.working));
    }

    void finishFromMouse() {
        this.finish();
    }

    static Component mouseIntro() {
        return Component.translatable("propwash.input.mouse.wizard.intro", InputKeys.label(InputKeys.THROTTLE_UP), InputKeys.label(InputKeys.THROTTLE_DOWN),
                InputKeys.label(InputKeys.YAW_LEFT), InputKeys.label(InputKeys.YAW_RIGHT), InputKeys.label(InputKeys.ARM));
    }

    private void setLayout(boolean gamepadLayout) {
        RawInput raw = TransmitterInput.INSTANCE.raw();
        DeviceProfile profile = this.profile(raw);
        if (raw == null || profile == null) {
            return;
        }
        DeviceProfile fresh = gamepadLayout ? ProfileDefaults.gamepad(profile.name) : ProfileDefaults.radio(profile.name, raw.axisCount());
        this.working.profiles.put(raw.device().guid(), fresh);
        this.previewMapper.reset();
        this.previewSwitches.reset();
        this.setStatus(Component.translatable("propwash.input.wizard.layout_reset"), InputGui.WARN);
        this.rebuildWidgets();
    }

    private void resetProfile() {
        RawInput raw = TransmitterInput.INSTANCE.raw();
        DeviceProfile profile = this.profile(raw);
        if (raw == null || profile == null) {
            return;
        }
        long now = Util.getMillis();
        if (now >= this.resetConfirmUntil) {
            this.resetConfirmUntil = now + CONFIRM_MILLIS;
            this.rebuildWidgets();
            return;
        }
        this.resetConfirmUntil = 0L;
        DeviceProfile fresh = ProfileDefaults.reset(profile, raw.gamepadCapable(), raw.axisCount());
        fresh.keepCommandsFrom(profile);
        this.working.profiles.put(raw.device().guid(), fresh);
        this.previewMapper.reset();
        this.previewSwitches.reset();
        this.setStatus(Component.translatable("propwash.input.wizard.reset_done"), InputGui.GOOD);
        this.rebuildWidgets();
    }

    public void showPage(Page next) {
        this.goTo(next);
    }

    private void goTo(Page next) {
        this.page = next;
        this.confirmNoArm = false;
        this.deviceArea.reset();
        this.listening = null;
        this.switchDetector = null;
        switch (next) {
            case CHANNELS -> {
                this.channelIndex = 0;
                this.channelResults.clear();
                this.claimedAxes = new boolean[0];
                this.detector = null;
                this.confirming = false;
            }
            case CALIBRATE -> {
                this.calibration = Calibration.CENTER;
                this.recorder = null;
            }
            default -> {
            }
        }
        this.rebuildWidgets();
    }

    private void restartPage() {
        if (this.page == Page.CHANNELS || this.page == Page.CALIBRATE || this.page == Page.SWITCHES) {
            this.goTo(this.page);
        }
    }

    private void skipChannel() {
        RawInput raw = TransmitterInput.INSTANCE.raw();
        DeviceProfile profile = this.profile(raw);
        if (profile == null || this.channelIndex >= CHANNELS.length) {
            return;
        }
        StickChannel channel = CHANNELS[this.channelIndex];
        ChannelMapping mapping = profile.channel(channel);
        this.claim(mapping.axis);
        this.channelResults.put(channel, Component.translatable("propwash.input.wizard.kept"));
        this.advanceChannel();
    }

    private void claim(int axis) {
        if (axis < 0) {
            return;
        }
        if (axis >= this.claimedAxes.length) {
            this.claimedAxes = Arrays.copyOf(this.claimedAxes, axis + 1);
        }
        this.claimedAxes[axis] = true;
    }

    private void advanceChannel() {
        this.channelIndex++;
        this.detector = null;
        this.confirming = false;
        this.previewMapper.reset();
        this.rebuildWidgets();
    }

    private void accept(RawInput raw, DeviceProfile profile, StickDetector.Detection detection, boolean confirmed) {
        StickChannel channel = CHANNELS[this.channelIndex];
        int[] sticksBefore = profile.stickAxes();
        profile.setChannel(channel, detection.toMapping(profile.channel(channel)));
        profile.moveSwitchesOffSticks(sticksBefore);
        this.claim(detection.axis());
        Component axisLabel = InputLabels.axis(detection.axis(), raw.usesGamepadLayout(profile));
        Component result = detection.invert() ? Component.translatable("propwash.input.wizard.assigned_inverted", axisLabel) : axisLabel;
        this.channelResults.put(channel, result);
        Component status = confirmed ? Component.translatable("propwash.input.wizard.throttle.confirmed", result)
                : Component.translatable("propwash.input.wizard.channel_done", Component.translatable(channel.translationKey()), result);
        this.setStatus(status, InputGui.GOOD);
        this.advanceChannel();
    }

    private void confirmThrottle(boolean heldEndIsDown) {
        RawInput raw = TransmitterInput.INSTANCE.raw();
        DeviceProfile profile = this.profile(raw);
        if (raw == null || profile == null || !(this.detector instanceof ThrottleDetector throttle)) {
            return;
        }
        StickDetector.Detection detection = throttle.confirm(heldEndIsDown);
        if (detection != null) {
            this.accept(raw, profile, detection, true);
        }
    }

    public boolean answerThrottle(boolean heldEndIsDown) {
        int before = this.channelIndex;
        this.confirmThrottle(heldEndIsDown);
        return this.channelIndex != before;
    }

    private void captureCenter() {
        RawInput raw = TransmitterInput.INSTANCE.raw();
        DeviceProfile profile = this.profile(raw);
        if (raw == null || profile == null) {
            return;
        }
        float[] axes = raw.axes(profile);
        CalibrationRecorder rec = new CalibrationRecorder(axes.length);
        rec.captureCenter(axes);
        this.recorder = rec;
        this.calibration = Calibration.EXTREMES;
        this.rebuildWidgets();
    }

    private void applyCalibration() {
        RawInput raw = TransmitterInput.INSTANCE.raw();
        DeviceProfile profile = this.profile(raw);
        CalibrationRecorder rec = this.recorder;
        if (raw == null || profile == null || rec == null) {
            return;
        }
        List<Component> missing = new ArrayList<>();
        for (StickChannel channel : CHANNELS) {
            ChannelMapping mapping = profile.channel(channel);
            if (!mapping.assigned()) {
                continue;
            }
            if (!rec.apply(mapping, channel.centered())) {
                missing.add(Component.translatable(channel.translationKey()));
            }
        }
        if (missing.isEmpty()) {
            this.setStatus(Component.translatable("propwash.input.wizard.calibrated"), InputGui.GOOD);
        } else {
            this.setStatus(Component.translatable("propwash.input.wizard.not_moved", join(missing)), InputGui.WARN);
        }
        this.previewMapper.reset();
        this.calibration = Calibration.REVIEW;
        this.rebuildWidgets();
    }

    private void toggleListening(SwitchFunction function) {
        if (this.listening == function) {
            this.listening = null;
            this.switchDetector = null;
            this.rebuildWidgets();
            return;
        }
        RawInput raw = TransmitterInput.INSTANCE.raw();
        DeviceProfile profile = this.profile(raw);
        if (raw == null || profile == null) {
            return;
        }
        float[] axes = raw.axes(profile);
        this.listening = function;
        this.switchDetector = new SwitchDetector(axes, raw.buttons(profile), profile.mappedAxes(axes.length));
        this.rebuildWidgets();
    }

    private void finish() {
        if (!this.activeGuid.isEmpty()) {
            this.working.markSetupDone(this.activeGuid);
        }
        this.finished = true;
        this.onDone.accept(this.working);
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void removed() {
        if (!this.finished && !this.steppingAside) {
            this.finished = true;
            this.onCancel.run();
        }
    }

    private void setStatus(Component message, int color) {
        this.status = message;
        this.statusColor = color;
        this.statusUntil = Util.getMillis() + STATUS_MILLIS;
    }

    private static Component join(List<Component> parts) {
        var out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(parts.get(i));
        }
        return out;
    }

    public static void applyButtonDefaults(SwitchFunction function, SwitchAssignment binding, boolean gamepadLayout) {
        if (binding.source == SwitchSource.BUTTON && gamepadLayout && function.latchesOnGamepad()) {
            binding.toggle = true;
            binding.invert = false;
            binding.startOn = function == SwitchFunction.ANGLE;
        }
    }

    @Override
    public void tick() {
        TransmitterInput.INSTANCE.requestPreview(this.previewGuid);
        RawInput raw = TransmitterInput.INSTANCE.raw();
        String guid = raw == null ? "" : raw.device().guid();
        if (!guid.equals(this.activeGuid)) {
            if (raw == null && this.page != Page.DEVICE) {
                this.setStatus(Component.translatable("propwash.input.wizard.device_lost"), InputGui.BAD);
                this.page = Page.DEVICE;
                this.confirmNoArm = false;
            }
            this.previewMapper.reset();
            this.previewSwitches.reset();
            this.restartPage();
            this.rebuildWidgets();
        } else if (!this.signature().equals(this.deviceSignature)) {
            this.rebuildWidgets();
        }
        if (this.resetConfirmUntil != 0L && Util.getMillis() >= this.resetConfirmUntil) {
            this.resetConfirmUntil = 0L;
            this.rebuildWidgets();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && this.listening != null) {
            this.toggleListening(this.listening);
            return true;
        }
        if (event.isEscape() && NumberInput.cancelEditing(this.getFocused())) {
            return true;
        }
        return super.keyPressed(event) || this.deviceAreaShown && this.deviceArea.key(event.key());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        NumberInput.releaseOnClickAway(this, event.x(), event.y());
        if (this.deviceAreaShown && event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.deviceArea.mouseClicked(event.x(), event.y())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return this.deviceArea.mouseDragged(event.y()) || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.deviceArea.mouseReleased();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.deviceAreaShown && this.deviceArea.wheel(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        long nanos = Util.getNanos();
        float dt = this.physicsFrameNanos == 0L ? 0.0F : Math.min(0.1F, (nanos - this.physicsFrameNanos) / 1.0E9F);
        this.physicsFrameNanos = nanos;
        RawInput raw = TransmitterInput.INSTANCE.raw();
        DeviceProfile profile = this.profile(raw);
        if (raw != null && profile != null) {
            float[] axes = raw.axes(profile);
            boolean[] buttons = raw.buttons(profile);
            this.previewSticks = this.previewMapper.map(profile, axes, dt);
            this.previewBits = this.previewSwitches.evaluate(profile, axes, buttons);
            this.updateDetection(raw, profile, axes, buttons, nanos);
            raw = TransmitterInput.INSTANCE.raw();
            profile = this.profile(raw);
        }
        ThemeStats.frame(this);
        this.deviceAreaShown = false;
        this.demo.beginFrame();
        int wx0 = this.left - 8;
        int wx1 = this.left + this.panelWidth + 8;
        int wy0 = this.windowTop;
        int wy1 = this.windowBottom;
        ThemeDraw.window(g, wx0, wy0, wx1, wy1);
        ThemeDraw.headerBar(g, wx0, wy0, wx1, this.contentTop - 6 - wy0);
        ThemeDraw.footerBar(g, wx0, this.footerY - 4, wx1, wy1);
        int titleX = ThemeDraw.brand(g, this.font, wx0 + 8, wy0 + 6) + 10;
        ThemeDraw.verticalSeparator(g, titleX - 5, wy0 + 3, wy0 + 16);
        ThemeText.text(g, this.font, this.title, titleX, wy0 + 6, Palette.TEXT);
        this.drawSteps(g, wx0 + 8);
        switch (this.page) {
            case DEVICE -> this.drawDevice(g, raw, profile, mouseX, mouseY);
            case CHANNELS -> this.drawChannels(g, raw, profile, nanos);
            case CALIBRATE -> this.drawCalibrate(g, raw, profile);
            case SWITCHES -> this.drawSwitches(g, raw, profile, mouseX, mouseY);
        }
        if (Util.getMillis() < this.statusUntil) {
            ThemeText.fittedCentered(g, this.font, this.status, this.width / 2, this.footerY - 16, this.panelWidth, this.statusColor);
        }
        super.extractRenderState(g, mouseX, mouseY, a);
        if (this.page == Page.SWITCHES && raw != null && profile != null && !this.confirmNoArm) {
            this.drawSwitchCard(g, raw, profile, mouseX, mouseY);
        }
        ThemeDraw.windowBorder(g, wx0, wy0, wx1, wy1);
    }

    private void updateDetection(RawInput raw, DeviceProfile profile, float[] axes, boolean[] buttons, long nanos) {
        switch (this.page) {
            case CHANNELS -> {
                if (this.channelIndex >= CHANNELS.length) {
                    return;
                }
                StickDetector current = this.detector;
                if (current == null) {
                    if (this.claimedAxes.length < axes.length) {
                        this.claimedAxes = Arrays.copyOf(this.claimedAxes, axes.length);
                    }
                    StickChannel channel = CHANNELS[this.channelIndex];
                    this.detector = StickDetector.forChannel(channel, axes, this.claimedAxes, profile.channel(channel));
                    return;
                }
                StickDetector.Detection detection = current.update(axes, nanos);
                if (detection != null) {
                    this.accept(raw, profile, detection, false);
                    return;
                }
                boolean asking = current.stage() == DetectionStage.CONFIRM;
                if (asking != this.confirming) {
                    this.confirming = asking;
                    this.rebuildWidgets();
                }
            }
            case CALIBRATE -> {
                CalibrationRecorder rec = this.recorder;
                if (rec != null && this.calibration == Calibration.EXTREMES) {
                    rec.observe(axes);
                }
            }
            case SWITCHES -> {
                SwitchDetector current = this.switchDetector;
                SwitchFunction function = this.listening;
                if (current == null || function == null) {
                    return;
                }
                SwitchAssignment binding = current.update(axes, buttons, nanos);
                if (binding != null) {
                    applyButtonDefaults(function, binding, raw.usesGamepadLayout(profile));
                    profile.setBinding(function, binding);
                    this.setStatus(Component.translatable("propwash.input.wizard.switch_done", Component.translatable(function.translationKey()),
                            InputLabels.bindingFor(binding, profile, raw)), InputGui.GOOD);
                    this.listening = null;
                    this.switchDetector = null;
                    this.previewSwitches.reset();
                    this.rebuildWidgets();
                }
            }
            default -> {
            }
        }
    }

    private void drawSteps(GuiGraphicsExtractor g, int left) {
        if (this.page == Page.DEVICE && this.working.mouseSelected()) {
            Component device = Page.DEVICE.label(1);
            g.text(this.font, device, left, this.windowTop + 19, InputGui.HIGHLIGHT, false);
            int x = left + this.font.width(device);
            Component separator = Component.literal("  ›  ");
            g.text(this.font, separator, x, this.windowTop + 19, InputGui.FAINT, false);
            x += this.font.width(separator);
            g.text(this.font, Component.translatable("propwash.input.mouse.wizard.step", 2), x, this.windowTop + 19, InputGui.FAINT, false);
            return;
        }
        Page[] pages = Page.values();
        Component separator = Component.literal("  ›  ");
        int x = left;
        for (int i = 0; i < pages.length; i++) {
            if (i > 0) {
                g.text(this.font, separator, x, this.windowTop + 19, InputGui.FAINT, false);
                x += this.font.width(separator);
            }
            Component label = pages[i].label(i + 1);
            int color = pages[i] == this.page ? InputGui.HIGHLIGHT : (pages[i].ordinal() < this.page.ordinal() ? InputGui.GOOD : InputGui.FAINT);
            g.text(this.font, label, x, this.windowTop + 19, color, false);
            x += this.font.width(label);
        }
    }

    private void drawDevice(GuiGraphicsExtractor g, @Nullable RawInput raw, @Nullable DeviceProfile profile, int mouseX, int mouseY) {
        int y = this.contentTop + (profile != null ? DEVICE_LIVE_TOP + 2 : 26);
        if ((raw == null || profile == null) && this.working.mouseSelected()) {
            InputGui.wrapped(g, this.font, mouseIntro(), this.left, y, this.panelWidth, InputGui.ACCENT);
            return;
        }
        if (raw == null || profile == null) {
            y += InputGui.wrapped(g, this.font, Component.translatable(TransmitterInput.INSTANCE.available() ? "propwash.input.wizard.no_device" : "propwash.input.wizard.no_sdl"),
                    this.left, y, this.panelWidth, InputGui.WARN) + 6;
            y += InputGui.wrapped(g, this.font, MacControllers.noDeviceHint(this.working, false), this.left, y, this.panelWidth, InputGui.DIM) + 6;
            if (this.working.keyboardFallback || this.working.keyboardSelected()) {
                InputGui.wrapped(g, this.font, Component.translatable("propwash.input.wizard.keyboard_hint",
                        InputKeys.label(InputKeys.THROTTLE_UP), InputKeys.label(InputKeys.ARM)), this.left, y, this.panelWidth, InputGui.DIM);
            }
            return;
        }
        int viewTop = this.contentTop + DEVICE_LIVE_TOP;
        this.deviceArea.view(this.left, viewTop, this.left + this.panelWidth + ScrollArea.GUTTER,
                this.footerY - (Util.getMillis() < this.statusUntil ? STATUS_SPACE : 6));
        this.deviceAreaShown = true;
        this.deviceArea.begin(g);
        int hoverY = this.deviceArea.hoverY(mouseX, mouseY);
        y = viewTop + 2;
        DeviceInfo device = raw.device();
        y += InputGui.wrapped(g, this.font, Component.translatable("propwash.input.wizard.device_info", device.guid(), raw.axisCount(), raw.buttonCount(),
                raw.hatCount(), device.usbId()), this.left, y, this.panelWidth, InputGui.DIM) + 3;
        boolean done = this.working.setupStatus(device.guid()) == SetupStatus.COMPLETED;
        y += InputGui.wrapped(g, this.font, setupLine(done, raw.usesGamepadLayout(profile)), this.left, y, this.panelWidth, done ? InputGui.GOOD : InputGui.ACCENT) + 4;
        boolean pad = raw.usesGamepadLayout(profile);
        float[] axes = raw.axes(profile);
        boolean[] buttons = raw.buttons(profile);
        int half = (this.panelWidth - 10) / 2;
        int right = this.left + half + 10;
        Component channels = Component.translatable("propwash.input.wizard.channels");
        g.text(this.font, Component.translatable("propwash.input.wizard.raw_axes"), this.left, y, InputGui.TEXT, false);
        g.text(this.font, channels, right, y, InputGui.TEXT, false);
        if (device.edgeTxOrOpenTx()) {
            Component badge = InputLabels.badge();
            int badgeWidth = this.font.width(badge);
            if (this.font.width(channels) + 6 + badgeWidth > half) {
                y += Metrics.LINE_HEIGHT;
            }
            g.text(this.font, badge, this.left + this.panelWidth - badgeWidth, y, InputGui.ACCENT, false);
        }
        y += 11;
        int leftBottom = y + InputGui.axisGrid(g, this.font, this.left, y, half, axes, -1, new boolean[0], i -> axisLabel(profile, i, pad));
        int rightBottom = y + InputGui.stickBars(g, this.font, right, y, half, this.previewSticks);
        if (buttons.length > 0) {
            rightBottom += 4;
            g.text(this.font, Component.translatable("propwash.input.wizard.buttons"), right, rightBottom, InputGui.TEXT, false);
            rightBottom += 11;
            rightBottom += InputGui.buttonGrid(g, this.font, right, rightBottom, half, buttons, mouseX, hoverY, i -> InputLabels.button(i, pad, raw.buttonCount()), -1);
        }
        this.deviceArea.end(g, Math.max(leftBottom, rightBottom) + 2);
    }

    public static Component setupLine(boolean done, boolean gamepadLayout) {
        if (done) {
            return Component.translatable("propwash.input.wizard.setup_done");
        }
        return Component.translatable(gamepadLayout ? "propwash.input.wizard.prefill_gamepad" : "propwash.input.wizard.prefill_radio");
    }

    static Component axisLabel(DeviceProfile profile, int index, boolean pad) {
        Component label = InputLabels.axisShort(index, pad);
        for (StickChannel channel : CHANNELS) {
            if (profile.channel(channel).axis == index) {
                return Component.translatable("propwash.input.axis_mapped", label, Component.translatable(channel.translationKey()));
            }
        }
        return label;
    }

    private void drawChannels(GuiGraphicsExtractor g, @Nullable RawInput raw, @Nullable DeviceProfile profile, long nanos) {
        if (raw == null || profile == null) {
            InputGui.wrapped(g, this.font, Component.translatable("propwash.input.wizard.no_device"), this.left, this.contentTop, this.panelWidth, InputGui.WARN);
            return;
        }
        boolean pad = raw.usesGamepadLayout(profile);
        float[] axes = raw.axes(profile);
        int half = this.columnWidth();
        int y = this.contentTop;
        StickDetector current = this.detector;
        if (this.channelIndex < CHANNELS.length) {
            StickChannel channel = CHANNELS[this.channelIndex];
            this.demo.show(RemoteDemo.forChannel(channel));
            y += InputGui.wrapped(g, this.font, Component.translatable(channel.promptKey()), this.left, y, half, InputGui.HIGHLIGHT) + 4;
            if (channel == StickChannel.THROTTLE) {
                y = this.drawThrottleTravel(g, current instanceof ThrottleDetector throttle ? throttle : null, axes, pad, y, half);
            }
            float progress = current == null ? 0.0F : current.progress(nanos);
            InputGui.track(g, this.left, y, this.left + half, y + 5, false);
            g.fill(this.left + 1, y + 1, this.left + 1 + Math.round((half - 2) * progress), y + 4,
                    this.confirming ? InputGui.WARN : InputGui.GOOD);
            y += 9;
            Feedback feedback = feedback(channel, current);
            if (feedback != null) {
                InputGui.wrapped(g, this.font, feedback.text(), this.left, y, half, feedback.color());
            }
            y += this.feedbackHeight(channel, half) + 4;
            if (this.confirming) {
                y = Math.max(y, this.confirmButtonsTop(channel, half) + CONFIRM_BUTTON_HEIGHT + 4);
            }
            y = this.drawChannelList(g, profile, pad, y, false) + 4;
            y = this.hintIfRoom(g, Component.translatable("propwash.input.wizard.channels_hint"), y, half, InputGui.DIM);
            this.hintIfRoom(g, Component.translatable("propwash.input.wizard.channels_prefilled"), y, half, InputGui.FAINT);
        } else {
            this.demo.show(RemoteDemo.Motion.CHECK);
            InputGui.wrapped(g, this.font, Component.translatable("propwash.input.wizard.check"), this.left, y, half, InputGui.HIGHLIGHT);
            y = this.checkRowsTop();
            int labelWidth = InputGui.stickLabelWidth(this.font);
            int barWidth = half - this.checkToggleWidth() - 6;
            StickChannel shown = this.demo.lastFrame().channel();
            for (StickChannel channel : CHANNELS) {
                InputGui.microsBar(g, this.font, this.left, y + 3, barWidth, Component.translatable(channel.translationKey()), labelWidth,
                        InputGui.stickMicros(this.previewSticks, channel), InputGui.channelColor(channel), channel == shown);
                y += CHECK_ROW;
            }
            y += 6;
            this.drawChannelList(g, profile, pad, y, true);
        }
        int right = this.left + half + COLUMN_GAP;
        int bottom = this.footerY - 18;
        int axesRows = Math.max(1, axes.length);
        int axesNeeded = 11 + axesRows * InputGui.ROW_HEIGHT;
        int compact = 11 + Math.min(axesRows, 4) * InputGui.ROW_HEIGHT;
        int room = bottom - this.contentTop;
        int maxHeight = Math.max(DEMO_MAX_HEIGHT, Math.min(DEMO_TALL_HEIGHT, half));
        int demoHeight = Math.min(maxHeight, room - axesNeeded - DEMO_GAP);
        if (demoHeight < RemoteDemoView.MIN_HEIGHT + 30) {
            demoHeight = Math.min(maxHeight, room - compact - DEMO_GAP);
        }
        int axesTop = this.contentTop;
        if (RemoteDemoView.fits(half, demoHeight)) {
            this.demo.extract(g, this.font, right, this.contentTop, right + half, this.contentTop + demoHeight);
            axesTop = this.contentTop + demoHeight + DEMO_GAP;
        }
        g.text(this.font, Component.translatable("propwash.input.wizard.raw_axes"), right, axesTop, InputGui.TEXT, false);
        int highlight = current == null || this.channelIndex >= CHANNELS.length ? -1 : current.leadingAxis();
        InputGui.axisBars(g, this.font, right, axesTop + 11, half, bottom - axesTop - 11, axes, highlight,
                this.claimedAxes, i -> InputLabels.axisShort(i, pad));
    }

    private int hintIfRoom(GuiGraphicsExtractor g, Component text, int y, int width, int color) {
        if (y + ThemeText.wrappedHeight(this.font, text, width) > this.footerY - 18) {
            return y;
        }
        return y + InputGui.wrapped(g, this.font, text, this.left, y, width, color) + 4;
    }

    private int drawChannelList(GuiGraphicsExtractor g, DeviceProfile profile, boolean pad, int y, boolean fromProfile) {
        for (int i = 0; i < CHANNELS.length; i++) {
            StickChannel channel = CHANNELS[i];
            Component result = this.channelResults.get(channel);
            ChannelMapping mapping = profile.channel(channel);
            int color;
            Component value;
            if (fromProfile) {
                Component axis = mapping.assigned() ? InputLabels.axis(mapping.axis, pad) : Component.translatable("propwash.input.binding.none");
                value = mapping.assigned() && mapping.invert ? Component.translatable("propwash.input.wizard.assigned_inverted", axis) : axis;
                color = result != null ? InputGui.GOOD : InputGui.DIM;
            } else if (result != null) {
                color = InputGui.GOOD;
                value = result;
            } else if (i == this.channelIndex) {
                color = InputGui.HIGHLIGHT;
                value = Component.translatable("propwash.input.wizard.waiting");
            } else {
                color = InputGui.FAINT;
                value = mapping.assigned() ? InputLabels.axis(mapping.axis, pad) : Component.translatable("propwash.input.binding.none");
            }
            g.text(this.font, Component.translatable("propwash.input.wizard.channel_line", Component.translatable(channel.translationKey()), value),
                    this.left, y, color, false);
            y += 11;
        }
        return y;
    }

    private int drawThrottleTravel(GuiGraphicsExtractor g, @Nullable ThrottleDetector throttle, float[] axes, boolean pad, int y, int half) {
        int axis = throttle == null ? -1 : (throttle.candidate() >= 0 ? throttle.candidate() : throttle.leadingAxis());
        boolean known = axis >= 0 && axis < axes.length;
        Component label = known ? InputLabels.axisShort(axis, pad) : Component.translatable("propwash.input.axis_none_short");
        int labelWidth = this.font.width(label);
        for (int i = 0; i < axes.length; i++) {
            labelWidth = Math.max(labelWidth, this.font.width(InputLabels.axisShort(i, pad)));
        }
        labelWidth += 6;
        int x0 = this.left + labelWidth;
        int x1 = this.left + half;
        g.text(this.font, label, this.left, y + 1, known ? InputGui.HIGHLIGHT : InputGui.FAINT, false);
        InputGui.track(g, x0, y, x1, y + 9, known);
        g.verticalLine((x0 + x1) / 2, y, y + 8, InputGui.BAR_TICK);
        if (known) {
            DetectionStage stage = throttle.stage();
            boolean ends = stage == DetectionStage.HOLDING || stage == DetectionStage.CONFIRM;
            boolean enough = stage == DetectionStage.ONE_END || ends;
            int lo = InputGui.markerX(x0, x1, throttle.min(axis));
            int hi = InputGui.markerX(x0, x1, throttle.max(axis)) + 1;
            g.fill(lo, y + 1, Math.max(lo + 1, hi), y + 8, Palette.withAlpha(enough ? Palette.SUCCESS : Palette.WARNING, 0x80));
            int vx = InputGui.markerX(x0, x1, axes[axis]);
            g.fill(vx, y - 1, vx + 2, y + 10, InputGui.channelColor(StickChannel.THROTTLE));
            if (ends) {
                this.endLabel(g, Component.translatable("propwash.input.wizard.throttle.end_down"), InputGui.markerX(x0, x1, throttle.downEnd()), x0, x1,
                        y + TRAVEL_ROW, InputGui.ACCENT);
                this.endLabel(g, Component.translatable("propwash.input.wizard.throttle.end_up"), InputGui.markerX(x0, x1, throttle.upEnd()), x0, x1,
                        y + TRAVEL_ROW, InputGui.DIM);
            } else {
                int percent = Math.round(throttle.travel(axis) * 100.0F);
                g.text(this.font, Component.translatable(enough ? "propwash.input.wizard.travel_ok" : "propwash.input.wizard.travel", percent),
                        x0, y + TRAVEL_ROW, enough ? InputGui.GOOD : InputGui.WARN, false);
            }
        }
        return y + TRAVEL_ROW + END_LABEL_ROW;
    }

    private void endLabel(GuiGraphicsExtractor g, Component text, int x, int x0, int x1, int y, int color) {
        int width = this.font.width(text);
        int tx = Math.clamp(x - width / 2, x0, Math.max(x0, x1 - width));
        g.text(this.font, text, tx, y, color, false);
    }

    private int feedbackHeight(StickChannel channel, int width) {
        int height = 0;
        for (Component text : feedbackTexts(channel)) {
            height = Math.max(height, ThemeText.wrappedHeight(this.font, text, width));
        }
        return height;
    }

    private static List<Component> feedbackTexts(StickChannel channel) {
        if (channel == StickChannel.THROTTLE) {
            return List.of(Component.translatable("propwash.input.wizard.throttle.waiting"),
                    Component.translatable("propwash.input.wizard.throttle.too_small", 100),
                    Component.translatable("propwash.input.wizard.ambiguous"),
                    Component.translatable("propwash.input.wizard.throttle.one_end"),
                    Component.translatable("propwash.input.wizard.throttle.stuck"),
                    Component.translatable("propwash.input.wizard.throttle.holding"),
                    Component.translatable("propwash.input.wizard.throttle.confirm"));
        }
        return List.of(Component.translatable("propwash.input.wizard.too_small"), Component.translatable("propwash.input.wizard.ambiguous"));
    }

    static @Nullable Feedback feedback(StickChannel channel, @Nullable StickDetector detector) {
        if (detector == null) {
            return channel == StickChannel.THROTTLE ? new Feedback(Component.translatable("propwash.input.wizard.throttle.waiting"), InputGui.DIM) : null;
        }
        DetectionStage stage = detector.stage();
        if (detector instanceof ThrottleDetector throttle) {
            return switch (stage) {
                case WAITING -> new Feedback(Component.translatable("propwash.input.wizard.throttle.waiting"), InputGui.DIM);
                case TOO_SMALL -> new Feedback(Component.translatable("propwash.input.wizard.throttle.too_small",
                        Math.round(throttle.travel(throttle.leadingAxis()) * 100.0F)), InputGui.WARN);
                case AMBIGUOUS -> new Feedback(Component.translatable("propwash.input.wizard.ambiguous"), InputGui.WARN);
                case ONE_END -> throttle.stuck()
                        ? new Feedback(Component.translatable("propwash.input.wizard.throttle.stuck"), InputGui.WARN)
                        : new Feedback(Component.translatable("propwash.input.wizard.throttle.one_end"), InputGui.ACCENT);
                case HOLDING -> new Feedback(Component.translatable("propwash.input.wizard.throttle.holding"), InputGui.ACCENT);
                case CONFIRM -> new Feedback(Component.translatable("propwash.input.wizard.throttle.confirm"), InputGui.WARN);
            };
        }
        return switch (stage) {
            case TOO_SMALL -> new Feedback(Component.translatable("propwash.input.wizard.too_small"), InputGui.WARN);
            case AMBIGUOUS -> new Feedback(Component.translatable("propwash.input.wizard.ambiguous"), InputGui.WARN);
            default -> null;
        };
    }

    record Feedback(Component text, int color) {
    }

    private void drawCalibrate(GuiGraphicsExtractor g, @Nullable RawInput raw, @Nullable DeviceProfile profile) {
        if (raw == null || profile == null) {
            InputGui.wrapped(g, this.font, Component.translatable("propwash.input.wizard.no_device"), this.left, this.contentTop, this.panelWidth, InputGui.WARN);
            return;
        }
        int y = this.contentTop;
        boolean threeD = InputConfig.get().mode3dUnlocked;
        this.demo.show(switch (this.calibration) {
            case CENTER -> threeD ? RemoteDemo.Motion.CENTER_3D : RemoteDemo.Motion.CENTER;
            case EXTREMES -> RemoteDemo.Motion.EXTREMES;
            case REVIEW -> RemoteDemo.Motion.REVIEW;
        });
        int demoWidth = this.sideDemoWidth(220);
        int width = demoWidth > 0 ? this.panelWidth - demoWidth - COLUMN_GAP : this.panelWidth;
        if (demoWidth > 0) {
            this.extractSideDemo(g, demoWidth);
        }
        String key = switch (this.calibration) {
            case CENTER -> "propwash.input.wizard.calibrate_center";
            case EXTREMES -> "propwash.input.wizard.calibrate_extremes";
            case REVIEW -> "propwash.input.wizard.calibrate_review";
        };
        y += InputGui.wrapped(g, this.font, Component.translatable(key), this.left, y, width, InputGui.HIGHLIGHT) + 8;
        if (threeD && this.calibration == Calibration.CENTER) {
            y += InputGui.wrapped(g, this.font, Component.translatable("propwash.input.wizard.calibrate_center_3d"), this.left, y, width,
                    InputGui.WARN) + 6;
        }
        if (this.calibration == Calibration.REVIEW) {
            if (threeD) {
                y += InputGui.wrapped(g, this.font, Component.translatable("propwash.input.wizard.calibrate_review_3d",
                        throttleCenterMicros(profile.throttle)), this.left, y, width, InputGui.ACCENT) + 6;
            }
            y += InputGui.stickBars(g, this.font, this.left, y, width, this.previewSticks) + 6;
            if (demoWidth == 0) {
                this.extractBottomDemo(g, y);
            }
            return;
        }
        float[] axes = raw.axes(profile);
        boolean pad = raw.usesGamepadLayout(profile);
        int labelWidth = InputGui.stickLabelWidth(this.font);
        CalibrationRecorder rec = this.recorder;
        for (StickChannel channel : CHANNELS) {
            ChannelMapping mapping = profile.channel(channel);
            Component label = Component.translatable(channel.translationKey());
            if (!mapping.readable(axes)) {
                g.text(this.font, label, this.left, y + 1, InputGui.FAINT, false);
                g.text(this.font, Component.translatable("propwash.input.binding.none"), this.left + labelWidth, y + 1, InputGui.FAINT, false);
                y += 14;
                continue;
            }
            float value = axes[mapping.axis];
            int x0 = this.left + labelWidth;
            int x1 = this.left + width - 92;
            g.text(this.font, label, this.left, y + 1, InputGui.TEXT, false);
            InputGui.track(g, x0, y, x1, y + 9, false);
            if (rec != null && rec.travel(mapping.axis) > 0.0F) {
                int mx0 = InputGui.markerX(x0, x1, rec.min(mapping.axis));
                int mx1 = InputGui.markerX(x0, x1, rec.max(mapping.axis)) + 1;
                g.fill(mx0, y + 1, Math.max(mx0 + 1, mx1), y + 8, Palette.withAlpha(Palette.SUCCESS, 0x80));
                int cx = InputGui.markerX(x0, x1, rec.center(mapping.axis));
                g.verticalLine(cx, y - 1, y + 9, InputGui.ACCENT);
            }
            int vx = InputGui.markerX(x0, x1, value);
            g.fill(vx, y - 1, vx + 2, y + 10, InputGui.channelColor(channel));
            Component info;
            int color;
            if (rec == null) {
                info = InputLabels.axisShort(mapping.axis, pad);
                color = InputGui.DIM;
            } else if (rec.moved(mapping.axis)) {
                info = Component.translatable("propwash.input.wizard.travel_ok", Math.round(rec.travel(mapping.axis) * 50.0F));
                color = InputGui.GOOD;
            } else {
                info = Component.translatable("propwash.input.wizard.travel", Math.round(rec.travel(mapping.axis) * 50.0F));
                color = InputGui.WARN;
            }
            g.text(this.font, info, x1 + 6, y + 1, color, false);
            y += 14;
        }
        if (demoWidth == 0) {
            this.extractBottomDemo(g, y + 4);
        }
    }

    private int sideDemoWidth(int minRest) {
        int width = Math.min(DEMO_SIDE_MAX, Math.round(this.panelWidth * 0.36F));
        if (width < DEMO_SIDE_MIN || this.panelWidth - width - COLUMN_GAP < minRest) {
            return 0;
        }
        return width;
    }

    private void extractSideDemo(GuiGraphicsExtractor g, int width) {
        int x1 = this.left + this.panelWidth;
        int height = Math.min(DEMO_MAX_HEIGHT, this.footerY - 18 - this.contentTop);
        this.demo.extract(g, this.font, x1 - width, this.contentTop, x1, this.contentTop + height);
    }

    private void extractBottomDemo(GuiGraphicsExtractor g, int top) {
        int bottom = this.footerY - 18;
        int height = Math.min(DEMO_MAX_HEIGHT, bottom - top);
        int width = Math.min(this.panelWidth, Math.max(RemoteDemoView.MIN_WIDTH, height * 4 / 3));
        int x0 = this.left + (this.panelWidth - width) / 2;
        this.demo.extract(g, this.font, x0, top, x0 + width, top + height);
    }

    private int switchArea() {
        return this.footerY - 18 - (this.contentTop + functions().length * this.switchRowHeight() + 3);
    }

    private @Nullable SwitchFunction hoveredAssign(int mouseX, int mouseY) {
        int rowHeight = this.switchRowHeight();
        int x0 = this.switchRowsRight() - this.assignWidth() - 22;
        if (mouseX < x0 || mouseX >= x0 + this.assignWidth()) {
            return null;
        }
        SwitchFunction[] functions = functions();
        for (int i = 0; i < functions.length; i++) {
            int rowY = this.contentTop + i * rowHeight;
            if (mouseY >= rowY && mouseY < rowY + rowHeight - 2) {
                return functions[i];
            }
        }
        return null;
    }

    private @Nullable SwitchFunction switchDemoFunction(int mouseX, int mouseY) {
        if (this.listening != null) {
            return this.listening;
        }
        return this.switchDemoRoom() ? null : this.hoveredAssign(mouseX, mouseY);
    }

    private void drawNoArmConfirm(GuiGraphicsExtractor g) {
        Component title = Component.translatable("propwash.input.wizard.finish_no_arm.title");
        Component text = Component.translatable("propwash.input.wizard.finish_no_arm.text", InputKeys.label(InputKeys.ARM),
                Component.translatable("propwash.input.wizard.back"), Component.translatable("propwash.input.wizard.finish_no_arm"));
        int width = Math.min(this.panelWidth, NO_ARM_TEXT_WIDTH);
        int height = ThemeText.wrappedHeight(this.font, title, width) + NO_ARM_GAP + ThemeText.wrappedHeight(this.font, text, width);
        int room = this.footerY - 18 - this.contentTop;
        int y = this.contentTop + Math.max(0, (room - height) / 3);
        y += ThemeText.wrappedCentered(g, this.font, title, this.width / 2, y, width, InputGui.WARN) + NO_ARM_GAP;
        ThemeText.wrappedCentered(g, this.font, text, this.width / 2, y, width, InputGui.TEXT);
    }

    public boolean confirmingNoArm() {
        return this.confirmNoArm;
    }

    private void drawSwitches(GuiGraphicsExtractor g, @Nullable RawInput raw, @Nullable DeviceProfile profile, int mouseX, int mouseY) {
        if (raw == null || profile == null) {
            InputGui.wrapped(g, this.font, Component.translatable("propwash.input.wizard.no_device"), this.left, this.contentTop, this.panelWidth, InputGui.WARN);
            return;
        }
        if (this.confirmNoArm) {
            this.drawNoArmConfirm(g);
            return;
        }
        boolean pad = raw.usesGamepadLayout(profile);
        SwitchFunction listeningTo = this.listening;
        SwitchFunction shown = this.switchDemoFunction(mouseX, mouseY);
        this.demo.show(shown != null ? RemoteDemo.forSwitch(shown, pad)
                : new RemoteDemo.Target(pad ? RemoteDemo.Motion.PAD_ARM_AND_MODE : RemoteDemo.Motion.ARM_AND_MODE, 0));
        int rowHeight = this.switchRowHeight();
        int rowsRight = this.switchRowsRight();
        int rowsWidth = rowsRight - this.left;
        int invertX = this.invertX();
        int textX = this.left + this.switchNameWidth();
        int textWidth = invertX - 6 - textX;
        Component invertLabel = Component.translatable("propwash.input.modes.header_invert");
        SwitchFunction[] functions = functions();
        boolean armBound = profile.binding(SwitchFunction.ARM).bound();
        int widestName = 0;
        for (SwitchFunction function : functions) {
            widestName = Math.max(widestName, this.font.width(Component.translatable(function.translationKey())));
        }
        boolean cramped = this.switchCramped();
        boolean stacked = rowHeight >= SWITCH_ROW_STACKED && (textWidth < SWITCH_BINDING_MIN || widestName > textX - this.left - 15);
        int stackedWidth = Math.max(0, invertX - 6 - (this.left + 11));
        for (int i = 0; i < functions.length; i++) {
            SwitchFunction function = functions[i];
            int rowY = this.contentTop + i * rowHeight;
            int ty = rowY + (rowHeight - 2 - 8) / 2;
            int nameY = stacked ? rowY + (rowHeight - 2 - 18) / 2 : ty;
            boolean active = SwitchEvaluator.isSet(this.previewBits, function);
            if (function == SwitchFunction.ARM) {
                g.fill(this.left - 3, rowY - 1, rowsRight + 2, rowY + rowHeight - 1, Palette.withAlpha(Palette.ACCENT, armBound ? 0x10 : 0x22));
                g.outline(this.left - 3, rowY - 1, rowsRight - this.left + 5, rowHeight, armBound ? Palette.withAlpha(Palette.ACCENT, 0x60) : Palette.ACCENT);
            }
            InputGui.led(g, this.left, nameY, active);
            ThemeText.fitted(g, this.font, Component.translatable(function.translationKey()), this.left + 11, nameY,
                    stacked ? stackedWidth : textX - this.left - 15, function == SwitchFunction.ARM ? InputGui.HIGHLIGHT : InputGui.TEXT);
            Component text;
            int color;
            if (this.listening == function) {
                text = Component.translatable("propwash.input.modes.listening");
                color = InputGui.HIGHLIGHT;
            } else {
                SwitchAssignment binding = profile.binding(function);
                text = InputLabels.binding(binding, profile, raw.axes(profile).length, pad, raw.buttonCount(), false);
                if (binding.bound() && this.font.width(text) > (stacked ? stackedWidth : textWidth)) {
                    text = compactBinding(binding, profile, raw.axes(profile).length, pad, raw.buttonCount());
                }
                color = binding.bound() ? InputGui.DIM : (function == SwitchFunction.ARM ? InputGui.WARN : InputGui.FAINT);
                if (binding.bound() && !cramped) {
                    g.text(this.font, invertLabel, invertX + InputGui.INVERT_WIDTH + 3, ty, binding.invert ? InputGui.ACCENT : InputGui.DIM, false);
                }
            }
            if (stacked) {
                ThemeText.fitted(g, this.font, text, this.left + 11, nameY + 9, stackedWidth, color);
            } else {
                ThemeText.fitted(g, this.font, text, textX, ty, Math.max(0, textWidth), color);
            }
        }
        int areaTop = this.contentTop + functions.length * rowHeight + 3;
        int areaBottom = this.footerY - 18;
        Component guide = Component.translatable(armBound ? "propwash.input.wizard.switches_arm_ok" : "propwash.input.wizard.switches_arm");
        int guideColor = armBound ? InputGui.DIM : InputGui.HIGHLIGHT;
        Component hint = Component.translatable("propwash.input.wizard.switches_hint");
        int textLeft = this.left;
        int textRight = this.left + rowsWidth;
        int areaHeight = areaBottom - areaTop;
        if (areaHeight >= IDLE_DEMO_MIN) {
            int demoWidth = Math.min(Math.round(areaHeight * 1.35F), Math.round(this.panelWidth * 0.42F));
            this.demo.extract(g, this.font, this.left, areaTop, this.left + demoWidth, areaBottom);
            textLeft = this.left + demoWidth + 8;
        }
        int y = areaTop;
        int width = textRight - textLeft;
        this.switchGuideCut = false;
        if (listeningTo == null && areaHeight < Metrics.LINE_HEIGHT) {
            if (Util.getMillis() >= this.statusUntil) {
                String line = Component.translatable(armBound ? "propwash.input.wizard.switches_arm_ok_short" : "propwash.input.wizard.switches_arm_short").getString();
                this.switchGuideCut = this.font.width(line) > this.panelWidth;
                ThemeText.fittedCentered(g, this.font, line, this.width / 2, this.footerY - 16, this.panelWidth, guideColor);
            }
            return;
        }
        if (listeningTo != null && areaHeight >= IDLE_DEMO_MIN) {
            y = this.linesIfRoom(g, Component.translatable("propwash.input.wizard.switches_listening",
                    Component.translatable(listeningTo.translationKey())), textLeft, y, width, areaBottom, InputGui.HIGHLIGHT);
        } else if (listeningTo == null) {
            if (!this.fits(guide, width, areaBottom - y)) {
                guide = Component.translatable(armBound ? "propwash.input.wizard.switches_arm_ok_short" : "propwash.input.wizard.switches_arm_short");
                this.switchGuideCut = !this.fits(guide, width, areaBottom - y);
                if (this.switchGuideCut && areaBottom - y >= Metrics.LINE_HEIGHT
                        && TextFit.mode(this.font.width(guide), width) != TextFit.Mode.CUT) {
                    ThemeText.fitted(g, this.font, guide, textLeft, y, width, guideColor);
                    this.switchGuideCut = false;
                    return;
                }
            }
            y = this.linesIfRoom(g, guide, textLeft, y, width, areaBottom, guideColor);
        }
        if (this.fits(hint, width, areaBottom - y - 2)) {
            this.linesIfRoom(g, hint, textLeft, y + 2, width, areaBottom, InputGui.FAINT);
        }
    }

    private boolean fits(Component text, int width, int height) {
        return width > 0 && ThemeText.balanced(this.font, text, width).size() * Metrics.LINE_HEIGHT <= height;
    }

    private static String plain(FormattedCharSequence line) {
        StringBuilder out = new StringBuilder();
        line.accept((index, style, codePoint) -> {
            out.appendCodePoint(codePoint);
            return true;
        });
        return out.toString();
    }

    private static Component compactBinding(SwitchAssignment binding, DeviceProfile profile, int axisCount, boolean pad, int buttonCount) {
        MutableComponent text = InputLabels.sourceShort(binding, profile, axisCount, pad, buttonCount).copy();
        if (binding.source == SwitchSource.AXIS) {
            text.append(" ").append(Component.translatable("propwash.input.binding.range", binding.activeLowMicros(), binding.activeHighMicros()));
        }
        if (binding.toggle) {
            text.append(" ").append(Component.translatable("propwash.input.binding.toggled"));
        }
        return text;
    }

    private int linesIfRoom(GuiGraphicsExtractor g, Component text, int x, int y, int width, int bottom, int color) {
        List<FormattedCharSequence> lines = ThemeText.balanced(this.font, text, width);
        int max = Math.max(0, (bottom - y) / Metrics.LINE_HEIGHT);
        int shown = ThemeText.shownLines(text, lines.size(), max);
        for (int i = 0; i < shown; i++) {
            int lineY = y + i * Metrics.LINE_HEIGHT;
            if (i == shown - 1 && shown < lines.size()) {
                ThemeText.fitted(g, this.font, plain(lines.get(i)).stripTrailing() + "…", x, lineY, width, color);
            } else {
                g.text(this.font, lines.get(i), x, lineY, color, false);
            }
        }
        return y + shown * Metrics.LINE_HEIGHT;
    }

    private void drawSwitchCard(GuiGraphicsExtractor g, RawInput raw, DeviceProfile profile, int mouseX, int mouseY) {
        if (this.switchDemoRoom()) {
            return;
        }
        SwitchFunction function = this.switchDemoFunction(mouseX, mouseY);
        if (function == null) {
            return;
        }
        SwitchFunction[] functions = functions();
        int index = Arrays.asList(functions).indexOf(function);
        if (index < 0) {
            return;
        }
        int rowHeight = this.switchRowHeight();
        int rowY = this.contentTop + index * rowHeight;
        int right = this.switchRowsRight() - 90;
        int width = Math.min(CARD_MAX_WIDTH, Math.round(this.panelWidth * 0.46F));
        Component text = this.listening == function
                ? Component.translatable("propwash.input.wizard.switches_listening", Component.translatable(function.translationKey()))
                : Component.translatable("propwash.input.wizard.switches_card", Component.translatable(function.translationKey()));
        int textHeight = ThemeText.wrappedHeight(this.font, text, width - 12);
        int below = this.footerY - 18 - (rowY + rowHeight + 2);
        int above = rowY - 2 - this.contentTop;
        boolean down = below >= above;
        int height = Math.min(CARD_MAX_HEIGHT, down ? below : above);
        if (height < textHeight + RemoteDemoView.MIN_HEIGHT + 14) {
            return;
        }
        int y0 = down ? rowY + rowHeight + 2 : rowY - 2 - height;
        int x0 = right - width;
        g.nextStratum();
        ThemeDraw.tooltipFrame(g, x0, y0, right, y0 + height);
        InputGui.wrapped(g, this.font, text, x0 + 6, y0 + 5, width - 12, InputGui.HIGHLIGHT);
        this.demo.extract(g, this.font, x0 + 4, y0 + textHeight + 9, right - 4, y0 + height - 4);
    }

    public enum Page {
        DEVICE,
        CHANNELS,
        CALIBRATE,
        SWITCHES;

        Component label(int number) {
            return Component.translatable("propwash.input.wizard.step." + this.name().toLowerCase(java.util.Locale.ROOT), number);
        }
    }

    private enum Calibration {
        CENTER,
        EXTREMES,
        REVIEW
    }

    private record DeviceChoice(String guid, Component label) {
        @Override
        public boolean equals(Object o) {
            return o instanceof DeviceChoice other && other.guid.equals(this.guid);
        }

        @Override
        public int hashCode() {
            return this.guid.hashCode();
        }
    }
}
