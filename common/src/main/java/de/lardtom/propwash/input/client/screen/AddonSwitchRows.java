package de.lardtom.propwash.input.client.screen;

import de.lardtom.propwash.api.input.AxisSpec;
import de.lardtom.propwash.api.input.SwitchSpec;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.ChannelMath;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.StickChannel;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchDetector;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.SwitchSource;
import de.lardtom.propwash.input.SwitchSources;
import de.lardtom.propwash.input.addon.AccessoryActionHints;
import de.lardtom.propwash.input.addon.AddonBindings;
import de.lardtom.propwash.input.addon.AddonInputMath;
import de.lardtom.propwash.input.addon.AddonInputs;
import de.lardtom.propwash.input.addon.AddonSwitchEvaluator;
import de.lardtom.propwash.input.addon.AxisAssignment;
import de.lardtom.propwash.input.addon.client.AddonRadioStage;
import de.lardtom.propwash.input.addon.client.AddonServerFeatures;
import de.lardtom.propwash.input.addon.client.AddonSwitchClient;
import de.lardtom.propwash.input.client.KeyNames;
import de.lardtom.propwash.input.client.RawInput;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedWidgets;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class AddonSwitchRows {
    public static final String PAGE_KEY = "propwash.input.modes.page.addons";
    public static final String INACTIVE_KEY = "propwash.input.modes.addon_inactive";
    public static final String AXIS_KEY = "propwash.input.modes.axis";
    public static final String NAMED_KEY = "propwash.input.modes.accessory_named";
    private static final int HEADER = 12;
    private static final int ROW = 12;
    private static final int WIDGET = 18;
    private static final int GAP = 2;
    private static final int TITLE = 12;
    private static final int NAME_INSET = 14;
    private static final int CLEAR = 20;
    private static final int SPINNER = 46;
    private static final int HINT_GAP = 5;
    private static final int TAB_PAD = 12;
    private static final int COLUMNS = 11;

    private final ModesTab tab;
    private final AddonSwitchEvaluator evaluator = new AddonSwitchEvaluator();
    private int x;
    private int y;
    private int width;
    private int selected;
    private boolean listening;
    private @Nullable SwitchDetector detector;
    private boolean followed;
    private long liveBits;
    private final List<MicrosField> fields = new ArrayList<>();
    private final List<int[]> labels = new ArrayList<>();
    private final List<Component> labelTexts = new ArrayList<>();
    private int detailTop;
    private int textTop;
    private int bottom;
    private boolean builtDevice;

    AddonSwitchRows(ModesTab tab) {
        this.tab = tab;
    }

    public static boolean available() {
        return AddonInputs.any();
    }

    public static Component pageTitle() {
        return Component.translatable(PAGE_KEY);
    }

    private static List<Component> pageTabTitles(boolean shortFlight) {
        return List.of(Component.translatable(shortFlight ? "propwash.input.modes.page.flight.short" : "propwash.input.modes.page.flight"),
                Component.translatable("propwash.input.modes.page.commands"), pageTitle());
    }

    private static boolean shortFlight(Font font, int available, int gaps) {
        int natural = gaps;
        for (Component title : pageTabTitles(false)) {
            natural += font.width(title) + TAB_PAD;
        }
        return natural > available;
    }

    static Component flightTabTitle(Font font, int available, int gaps) {
        return pageTabTitles(shortFlight(font, available, gaps)).getFirst();
    }

    static FlatButton pageTab(int x, int y, int width, int height, Component name, Component shown, BooleanSupplier selected, Runnable action) {
        return name.equals(shown) ? new FlatButton(x, y, width, height, name, FlatButton.Style.TAB, selected, action)
                : new PageTab(x, y, width, height, name, shown, selected, action);
    }

    static int[] pageTabWidths(Font font, int available, int gaps) {
        List<Component> titles = pageTabTitles(shortFlight(font, available, gaps));
        int[] widths = new int[titles.size()];
        int text = 0;
        for (int i = 0; i < titles.size(); i++) {
            widths[i] = font.width(titles.get(i));
            text += widths[i];
        }
        int room = available - gaps - TAB_PAD * titles.size();
        float factor = text <= 0 || room >= text ? 1.0F : Math.max(0.0F, room / (float) text);
        for (int i = 0; i < widths.length; i++) {
            widths[i] = Math.max(16, TAB_PAD + (int) Math.floor(widths[i] * factor));
        }
        return widths;
    }

    public static Component title(@Nullable SwitchFunction function, Component base) {
        if (function == null || !function.accessoryAction()) {
            return base;
        }
        List<String> names = actionNames(function.accessoryNumber());
        if (names.isEmpty()) {
            return base;
        }
        MutableComponent joined = Component.empty();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                joined.append(" / ");
            }
            joined.append(Component.translatable(names.get(i)));
        }
        return Component.translatable(NAMED_KEY, base, joined);
    }

    public static List<String> actionNames(int number) {
        QuadcopterEntity drone = AddonSwitchClient.pilotDrone();
        if (drone != null) {
            return AccessoryActionHints.names(drone.build().accessories().byMount().values(), number);
        }
        return AccessoryActionHints.registeredNames(number);
    }

    public int selectedIndex() {
        return this.selected;
    }

    public void select(Identifier id) {
        List<Entry> entries = entries();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(id)) {
                this.selected = i;
                this.listening = false;
                this.detector = null;
                this.tab.rebuild();
                return;
            }
        }
    }

    public boolean listening() {
        return this.listening;
    }

    record Entry(@Nullable SwitchSpec sw, @Nullable AxisSpec axis) {
        Identifier id() {
            return this.sw != null ? this.sw.id() : this.axis.id();
        }

        Component name() {
            return Component.translatable(this.sw != null ? this.sw.nameKey() : this.axis.nameKey());
        }

        Optional<Component> description() {
            return this.sw == null ? Optional.empty() : this.sw.descriptionKey().map(Component::translatable);
        }

        boolean inactive() {
            return this.sw != null ? AddonServerFeatures.inactive(this.sw) : AddonServerFeatures.inactive(this.axis);
        }
    }

    static List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (SwitchSpec spec : AddonInputs.switches()) {
            out.add(new Entry(spec, null));
        }
        for (AxisSpec spec : AddonInputs.axes()) {
            out.add(new Entry(null, spec));
        }
        return out;
    }

    private @Nullable Entry current(List<Entry> entries) {
        if (entries.isEmpty()) {
            return null;
        }
        this.selected = Math.max(0, Math.min(entries.size() - 1, this.selected));
        return entries.get(this.selected);
    }

    private int nameWidth(Font font, List<Entry> entries) {
        int widest = 0;
        for (Entry entry : entries) {
            widest = Math.max(widest, font.width(entry.name()));
        }
        return Math.min(widest + 12, Math.max(60, this.width * 9 / 20));
    }

    private static Component keyLabel(Entry entry) {
        if (entry.sw != null) {
            Optional<KeyMapping> key = Api17Registries.Client.switchKey(entry.sw.id());
            return key.isPresent() && !key.get().isUnbound() ? KeyNames.of(key.get()) : Component.translatable("propwash.input.key.unbound");
        }
        Optional<Api17Registries.Client.AxisKeys> keys = Api17Registries.Client.axisKeys(entry.axis.id());
        if (keys.isEmpty() || keys.get().increase().isUnbound() && keys.get().decrease().isUnbound()) {
            return Component.translatable("propwash.input.key.unbound");
        }
        return Component.translatable("propwash.input.modes.addon_keys", label(keys.get().increase()), label(keys.get().decrease()));
    }

    private static Component label(KeyMapping key) {
        return key.isUnbound() ? Component.translatable("propwash.input.key.unbound") : KeyNames.of(key);
    }

    private int keyWidth(Font font, List<Entry> entries) {
        int widest = font.width(Component.translatable("propwash.input.modes.header_key"));
        for (Entry entry : entries) {
            widest = Math.max(widest, font.width(keyLabel(entry)));
        }
        return Math.max(28, Math.min(80, widest + 14));
    }

    private int listTop() {
        return this.y + HEADER + COLUMNS;
    }

    private int rowY(int index) {
        return this.listTop() + index * ROW;
    }

    int build(ConfigTabHost host, int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.fields.clear();
        this.labels.clear();
        this.labelTexts.clear();
        Font font = host.font();
        List<Entry> entries = entries();
        Entry entry = this.current(entries);
        RawInput raw = this.tab.raw();
        DeviceProfile profile = this.tab.profile(raw);
        this.detailTop = this.rowY(entries.size()) + 2;
        int top = this.detailTop + TITLE + this.statusLines(font, entry) * Metrics.LINE_HEIGHT;
        int line = top;
        if (entry != null && raw != null && profile != null && !entry.inactive()) {
            line = entry.sw != null ? this.buildSwitch(host, entry.sw, raw, profile, line) : this.buildAxis(host, entry.axis, raw, profile, line);
        }
        this.textTop = line + (line > top ? 3 : 1);
        this.builtDevice = raw != null && profile != null;
        this.bottom = this.textTop + this.textHeight(font, entry, raw != null && profile != null) + HINT_GAP
                + ThemeText.wrappedHeight(font, hint(), Math.max(40, this.width)) + 3;
        return this.bottom;
    }

    private int statusLines(Font font, @Nullable Entry entry) {
        if (entry == null) {
            return 0;
        }
        List<Component> candidates = statusCandidates(entry);
        int widest = 0;
        for (Component candidate : candidates) {
            widest = Math.max(widest, font.width(candidate));
        }
        if (font.width(entry.name()) + 8 + widest <= this.width) {
            return 0;
        }
        int lines = 0;
        for (Component candidate : candidates) {
            lines = Math.max(lines, font.split(candidate, Math.max(20, this.width)).size());
        }
        return lines;
    }

    private static List<Component> statusCandidates(Entry entry) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable(INACTIVE_KEY));
        if (entry.axis != null) {
            out.add(Component.translatable("propwash.input.modes.addon_axis_value", -100));
            return out;
        }
        out.add(Component.translatable("propwash.input.modes.listening"));
        for (StickChannel channel : StickChannel.values()) {
            out.add(Component.translatable("propwash.input.modes.on_stick", Component.translatable(channel.translationKey())));
        }
        out.add(Component.translatable("propwash.input.modes.live_active", 2100));
        out.add(Component.translatable("propwash.input.modes.state_active"));
        out.add(Component.translatable("propwash.input.modes.state_inactive"));
        out.add(Component.translatable("propwash.input.modes.unreadable"));
        return out;
    }

    private int buildSwitch(ConfigTabHost host, SwitchSpec spec, RawInput raw, DeviceProfile profile, int line) {
        Font font = host.font();
        int right = this.x + this.width;
        SwitchAssignment binding = profile.addonBinding(spec.id());
        boolean pad = raw.usesGamepadLayout(profile);
        int axisCount = raw.axes(profile).length;
        int buttonSlots = raw.buttons(profile).length;
        List<SwitchSources.Choice> choices = SwitchSources.choices(profile, axisCount, buttonSlots, binding);
        CycleButton<SwitchSources.Choice> source = CycleButton.builder(
                        (SwitchSources.Choice c) -> InputLabels.source(c, profile, axisCount, pad, raw.buttonCount()), SwitchSources.of(binding))
                .withValues(choices)
                .withTooltip(c -> ThemeText.tip(Component.translatable("propwash.input.modes.source.tooltip")))
                .create(this.x, line, this.width - CLEAR - GAP, WIDGET, Component.translatable("propwash.input.modes.source"),
                        (b, v) -> this.chooseSource(spec, v));
        source.active = !this.listening;
        host.addWidget(source);
        Button clear = host.addWidget(Button.builder(Component.literal("×"), b -> this.chooseSource(spec, SwitchSources.Choice.NONE))
                .bounds(right - CLEAR, line, CLEAR, WIDGET)
                .tooltip(Tooltip.create(Component.translatable("propwash.input.modes.clear")))
                .build());
        clear.active = !this.listening && binding.bound();
        line += WIDGET + GAP;
        int half = (this.width - GAP) / 2;
        host.addWidget(Button.builder(Component.translatable(this.listening ? "propwash.input.modes.listening_short" : "propwash.input.modes.assign"),
                        b -> this.toggleListening(raw, profile))
                .bounds(this.x, line, half, WIDGET)
                .tooltip(Tooltip.create(Component.translatable("propwash.input.modes.assign.tooltip")))
                .build());
        CycleButton<Boolean> invert = CycleButton.onOffBuilder(binding.invert)
                .withTooltip(v -> ThemeText.tip(Component.translatable("propwash.input.modes.header_invert.tooltip")))
                .create(this.x + half + GAP, line, this.width - half - GAP, WIDGET, Component.translatable("propwash.input.modes.invert"), (b, v) -> {
                    binding.invert = v;
                    this.edited();
                });
        invert.active = !this.listening && binding.bound();
        host.addWidget(invert);
        line += WIDGET + GAP;
        if (!binding.bound() || this.listening) {
            return line;
        }
        int cx = this.x;
        if (binding.source == SwitchSource.AXIS) {
            cx = this.spinner(host, font, cx, line, Component.translatable("propwash.input.modes.min"), binding, false);
            cx = this.spinner(host, font, cx + 6, line, Component.translatable("propwash.input.modes.max"), binding, true);
            cx += 8;
        }
        if (spec.toggle()) {
            Component label = Component.translatable("propwash.input.binding.mode");
            int need = font.width(label) + Metrics.LABEL_GAP + 2 + Math.max(font.width(Component.translatable("propwash.input.binding.mode.toggle")),
                    font.width(Component.translatable("propwash.input.binding.mode.hold"))) + 2 * Metrics.ARROW_WIDTH + 8;
            if (cx > this.x && right - cx < Math.min(need, this.width)) {
                line += WIDGET + GAP;
                cx = this.x;
            }
            host.addWidget(CycleButton.booleanBuilder(Component.translatable("propwash.input.binding.mode.toggle"),
                            Component.translatable("propwash.input.binding.mode.hold"), binding.toggle)
                    .withTooltip(v -> Tooltip.create(Component.translatable("propwash.input.binding.mode.tooltip")))
                    .create(cx, line, right - cx, WIDGET, label, (b, v) -> {
                        binding.toggle = v;
                        if (!v) {
                            binding.startOn = false;
                        }
                        this.evaluator.reset();
                        this.edited();
                    }));
            line += WIDGET + GAP;
        } else if (binding.source == SwitchSource.AXIS) {
            line += WIDGET + GAP;
        }
        return line;
    }

    private int spinner(ConfigTabHost host, Font font, int cx, int line, Component label, SwitchAssignment binding, boolean high) {
        this.labels.add(new int[]{cx, line + 5});
        this.labelTexts.add(label);
        cx += font.width(label) + 4;
        MicrosField field = new MicrosField(cx, line, SPINNER, WIDGET, label, () -> high ? binding.activeHighMicros() : binding.activeLowMicros(), v -> {
            if (high) {
                binding.setActiveHighMicros(v);
            } else {
                binding.setActiveLowMicros(v);
            }
            this.tab.markChanged();
        }, ChannelMath.RANGE_STEP_US, ChannelMath.RANGE_MIN_US, ChannelMath.RANGE_MAX_US);
        this.fields.add(field);
        host.addWidget(field);
        return cx + SPINNER;
    }

    private int buildAxis(ConfigTabHost host, AxisSpec spec, RawInput raw, DeviceProfile profile, int line) {
        Font font = host.font();
        int right = this.x + this.width;
        AxisAssignment assignment = profile.addonAxis(spec.id());
        boolean pad = raw.usesGamepadLayout(profile);
        int axisCount = raw.axes(profile).length;
        List<Integer> choices = new ArrayList<>();
        choices.add(-1);
        for (int axis : profile.auxAxes(axisCount)) {
            choices.add(axis);
        }
        int chosen = assignment.onAxis() ? assignment.index : -1;
        if (!choices.contains(chosen)) {
            choices.add(chosen);
        }
        CycleButton<Integer> source = CycleButton.builder((Integer v) -> v < 0 ? Component.translatable("propwash.input.modes.addon_axis_keys")
                        : InputLabels.source(new SwitchSources.Choice(SwitchSource.AXIS, v), profile, axisCount, pad, raw.buttonCount()), chosen)
                .withValues(choices)
                .withTooltip(v -> ThemeText.tip(Component.translatable("propwash.input.modes.addon_axis_source.tooltip")))
                .create(this.x, line, this.width - CLEAR - GAP, WIDGET, Component.translatable("propwash.input.modes.source"), (b, v) -> {
                    AxisAssignment next = v < 0 ? AxisAssignment.keys() : AxisAssignment.axis(v);
                    next.invert = assignment.invert;
                    next.deadband = assignment.deadband;
                    profile.setAddonAxis(spec.id(), next);
                    this.edited();
                    this.tab.rebuild();
                });
        host.addWidget(source);
        Button clear = host.addWidget(Button.builder(Component.literal("×"), b -> {
                    profile.setAddonAxis(spec.id(), null);
                    this.edited();
                    this.tab.rebuild();
                })
                .bounds(right - CLEAR, line, CLEAR, WIDGET)
                .tooltip(Tooltip.create(Component.translatable("propwash.input.modes.clear")))
                .build());
        clear.active = profile.addonAxes.containsKey(spec.id().toString());
        line += WIDGET + GAP;
        if (!assignment.onAxis()) {
            return line;
        }
        int half = (this.width - GAP) / 2;
        host.addWidget(CycleButton.onOffBuilder(assignment.invert)
                .withTooltip(v -> ThemeText.tip(Component.translatable("propwash.input.modes.addon_axis_invert.tooltip")))
                .create(this.x, line, half, WIDGET, Component.translatable("propwash.input.modes.invert"), (b, v) -> {
                    assignment.invert = v;
                    this.edited();
                }));
        int cx = this.x + half + GAP;
        Component label = Component.translatable("propwash.input.modes.addon_deadband");
        this.labels.add(new int[]{cx, line + 5});
        this.labelTexts.add(label);
        int fieldX = Math.min(right - SPINNER, cx + font.width(label) + 4);
        MicrosField deadband = new MicrosField(fieldX, line, right - fieldX, WIDGET, label, () -> Math.round(assignment.deadband * 100.0F), v -> {
            assignment.deadband = Math.max(0.0F, Math.min(AxisAssignment.MAX_DEADBAND, v / 100.0F));
            this.tab.markChanged();
        }, 1, 0, Math.round(AxisAssignment.MAX_DEADBAND * 100.0F));
        this.fields.add(deadband);
        host.addWidget(deadband);
        return line + WIDGET + GAP;
    }

    private void edited() {
        this.tab.markChanged();
    }

    private void chooseSource(SwitchSpec spec, SwitchSources.Choice choice) {
        RawInput raw = this.tab.raw();
        DeviceProfile profile = this.tab.profile(raw);
        if (raw == null || profile == null) {
            return;
        }
        profile.setAddonBinding(spec.id(), AddonBindings.apply(profile.addonBinding(spec.id()), choice, spec, raw.usesGamepadLayout(profile)));
        this.evaluator.reset();
        this.tab.markChanged();
        this.tab.rebuild();
    }

    private void toggleListening(RawInput raw, DeviceProfile profile) {
        if (this.listening) {
            this.stopListening();
            return;
        }
        float[] axes = raw.axes(profile);
        this.listening = true;
        this.detector = new SwitchDetector(axes, raw.buttons(profile), profile.mappedAxes(axes.length));
        TransmitterInput.INSTANCE.requestCapture();
        AddonRadioStage.INSTANCE.capture();
        this.tab.rebuild();
    }

    private void stopListening() {
        this.listening = false;
        this.detector = null;
        this.tab.rebuild();
    }

    void tick() {
        if (this.listening) {
            TransmitterInput.INSTANCE.requestCapture();
            AddonRadioStage.INSTANCE.capture();
        }
    }

    boolean cancelAction() {
        for (MicrosField field : this.fields) {
            if (field.isEditing()) {
                field.cancelEdit();
                return true;
            }
        }
        if (this.listening) {
            this.stopListening();
            return true;
        }
        return false;
    }

    void resetDefaults() {
        RawInput raw = this.tab.raw();
        DeviceProfile profile = this.tab.profile(raw);
        if (profile == null) {
            return;
        }
        for (Entry entry : entries()) {
            if (entry.sw != null) {
                profile.setAddonBinding(entry.sw.id(), null);
            } else {
                profile.setAddonAxis(entry.axis.id(), null);
            }
        }
        this.listening = false;
        this.detector = null;
        this.evaluator.reset();
        this.tab.markChanged();
        this.tab.rebuild();
    }

    boolean mouseClicked(MouseButtonEvent event) {
        if (event.button() > 1 || event.x() < this.x || event.x() >= this.x + this.width || event.y() < this.listTop()) {
            return false;
        }
        int row = (int) ((event.y() - this.listTop()) / ROW);
        List<Entry> entries = entries();
        if (row < 0 || row >= entries.size()) {
            return false;
        }
        if (row != this.selected) {
            this.selected = row;
            this.listening = false;
            this.detector = null;
            this.tab.rebuild();
        }
        return true;
    }

    private static Component hint() {
        return Component.translatable("propwash.input.modes.addons.hint");
    }

    private @Nullable Component status(Entry entry, @Nullable RawInput raw, @Nullable DeviceProfile profile) {
        if (entry.inactive()) {
            return Component.translatable(INACTIVE_KEY);
        }
        if (this.listening && entry.sw != null) {
            return Component.translatable("propwash.input.modes.listening");
        }
        int index = AddonInputs.switchIndex(entry.id());
        if (entry.sw != null) {
            if (raw == null || profile == null) {
                return Component.translatable(this.keyActive(index) ? "propwash.input.modes.state_active" : "propwash.input.modes.state_inactive");
            }
            SwitchAssignment binding = profile.addonBinding(entry.sw.id());
            float[] axes = raw.axes(profile);
            boolean[] buttons = raw.buttons(profile);
            boolean active = AddonSwitchEvaluator.isSet(this.liveBits, index) || this.keyActive(index);
            StickChannel stick = profile.stickConflict(binding);
            if (stick != null) {
                return Component.translatable("propwash.input.modes.on_stick", Component.translatable(stick.translationKey()));
            }
            if (binding.source == SwitchSource.AXIS && binding.readable(axes, buttons)) {
                return Component.translatable(active ? "propwash.input.modes.live_active" : "propwash.input.modes.live",
                        ChannelMath.rangeMicros(axes[binding.index]));
            }
            if (binding.bound() && !binding.readable(axes, buttons)) {
                return Component.translatable("propwash.input.modes.unreadable");
            }
            return Component.translatable(active ? "propwash.input.modes.state_active" : "propwash.input.modes.state_inactive");
        }
        return Component.translatable("propwash.input.modes.addon_axis_value", Math.round(this.axisValue(entry.axis, raw, profile) * 100.0));
    }

    private boolean keyActive(int index) {
        return AddonSwitchEvaluator.isSet(AddonRadioStage.INSTANCE.keyBits(), index);
    }

    private double axisValue(AxisSpec spec, @Nullable RawInput raw, @Nullable DeviceProfile profile) {
        if (raw != null && profile != null) {
            AxisAssignment assignment = profile.addonAxis(spec.id());
            float[] axes = raw.axes(profile);
            if (assignment.readable(axes)) {
                return AddonInputMath.axisValue(assignment, axes[assignment.index]);
            }
        }
        int index = AddonInputs.axisIndex(spec.id());
        float[] values = RadioState.INSTANCE.addonAxes();
        return index >= 0 && index < values.length ? values[index] : spec.center();
    }

    private int textHeight(Font font, @Nullable Entry entry, boolean device) {
        int height = 0;
        for (Component line : this.texts(entry, device)) {
            height += ThemeText.wrappedHeight(font, line, Math.max(20, this.width));
        }
        return height;
    }

    private List<Component> texts(@Nullable Entry entry, boolean device) {
        List<Component> out = new ArrayList<>();
        if (entry == null) {
            return out;
        }
        if (entry.inactive()) {
            out.add(Component.translatable("propwash.input.modes.addon_inactive.detail"));
        }
        if (entry.sw != null) {
            out.add(Component.translatable("propwash.input.modes.addon_key_line", keyLabel(entry),
                    Component.translatable(entry.sw.toggle() ? "propwash.input.modes.addon_toggle" : "propwash.input.modes.addon_momentary")));
        } else {
            out.add(Component.translatable("propwash.input.modes.addon_axis_line", Component.translatable(AXIS_KEY), keyLabel(entry),
                    Math.round(entry.axis.keyRateDps())));
        }
        entry.description().ifPresent(out::add);
        if (!device) {
            out.add(Component.translatable("propwash.input.modes.no_device_short"));
        } else if (entry.sw != null && !entry.inactive() && !this.listening && !this.boundNow(entry)) {
            out.add(Component.translatable("propwash.input.modes.unbound_hint"));
        }
        return out;
    }

    private boolean boundNow(Entry entry) {
        DeviceProfile profile = this.tab.profile(this.tab.raw());
        return profile != null && entry.sw != null && profile.addonBinding(entry.sw.id()).bound();
    }

    int extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        List<Entry> entries = entries();
        Entry entry = this.current(entries);
        RawInput raw = this.tab.raw();
        DeviceProfile profile = this.tab.profile(raw);
        boolean device = raw != null && profile != null;
        float[] axes = device ? raw.axes(profile) : new float[0];
        boolean[] buttons = device ? raw.buttons(profile) : new boolean[0];
        boolean pad = device && raw.usesGamepadLayout(profile);
        if (device) {
            if (!this.followed) {
                this.evaluator.copyFrom(AddonRadioStage.INSTANCE.evaluator());
                this.followed = true;
            }
            this.liveBits = this.evaluator.evaluate(profile, AddonInputs.switches(), axes, buttons);
            this.updateListening(entry, profile, axes, buttons, pad);
        } else {
            this.followed = false;
            this.liveBits = 0L;
        }
        int nameWidth = this.nameWidth(font, entries);
        int keyWidth = this.keyWidth(font, entries);
        int keyX = this.x + NAME_INSET + nameWidth;
        int areaX0 = keyX + keyWidth;
        int areaX1 = this.x + this.width - 2;
        g.fill(this.x, this.y, this.x + this.width, this.y + HEADER - 1, Palette.PANEL_HEADER);
        ThemeDraw.separator(g, this.x, this.x + this.width, this.y + HEADER - 1);
        g.text(font, Component.translatable("propwash.input.modes.header_function"), this.x + NAME_INSET, this.y + HEADER + 1, InputGui.DIM, false);
        g.text(font, Component.translatable("propwash.input.modes.header_key"), keyX, this.y + HEADER + 1, InputGui.DIM, false);
        for (int i = 0; i < entries.size(); i++) {
            Entry row = entries.get(i);
            int rowY = this.rowY(i);
            int ty = rowY + (ROW - 1 - 8) / 2;
            int index = row.sw != null ? AddonInputs.switchIndex(row.id()) : -1;
            boolean active = row.sw != null && !row.inactive() && (AddonSwitchEvaluator.isSet(this.liveBits, index) || this.keyActive(index));
            if (i == this.selected) {
                g.fill(this.x, rowY, this.x + this.width, rowY + ROW - 1, Palette.HOVER_TINT);
                g.verticalLine(this.x, rowY, rowY + ROW - 2, InputGui.ACCENT);
            }
            if (active) {
                g.fill(this.x + 1, rowY, areaX0 - 3, rowY + ROW - 1, Palette.withAlpha(Palette.SUCCESS, 0x28));
            }
            InputGui.led(g, this.x + 2, ty, active);
            ThemeText.fitted(g, font, row.name(), this.x + NAME_INSET, ty, nameWidth - 4, active || i == this.selected ? InputGui.TEXT : InputGui.DIM);
            Component key = keyLabel(row);
            ThemeText.fitted(g, font, key, keyX, ty, keyWidth - 6, InputGui.ACCENT);
            this.extractArea(g, font, row, profile, axes, buttons, pad, raw, areaX0, areaX1, ty);
            if (mouseX >= this.x + NAME_INSET && mouseX < keyX && mouseY >= rowY && mouseY < rowY + ROW) {
                ThemeText.tooltip(g, font, Component.literal(row.id().toString()), mouseX, mouseY);
            }
        }
        if (device != this.builtDevice) {
            this.tab.rebuildLater();
        }
        int hintY = this.extractDetail(g, font, entry, raw, profile) + HINT_GAP;
        int hintHeight = ThemeText.wrapped(g, font, hint(), this.x, hintY, Math.max(40, this.width), InputGui.FAINT);
        return Math.max(this.bottom, hintY + hintHeight + 3);
    }

    private void extractArea(GuiGraphicsExtractor g, Font font, Entry row, @Nullable DeviceProfile profile, float[] axes, boolean[] buttons, boolean pad,
                             @Nullable RawInput raw, int areaX0, int areaX1, int ty) {
        int room = Math.max(10, areaX1 - areaX0);
        if (row.inactive()) {
            ThemeText.fitted(g, font, Component.translatable(INACTIVE_KEY), areaX0, ty, room, InputGui.WARN);
            return;
        }
        if (row.sw != null) {
            if (profile == null || raw == null) {
                ThemeText.fitted(g, font, Component.translatable(row.sw.toggle() ? "propwash.input.modes.addon_toggle" : "propwash.input.modes.addon_momentary"),
                        areaX0, ty, room, InputGui.FAINT);
                return;
            }
            SwitchAssignment binding = profile.addonBinding(row.sw.id());
            if (!binding.bound()) {
                ThemeText.fitted(g, font, Component.translatable("propwash.input.binding.none"), areaX0, ty, room, InputGui.FAINT);
                return;
            }
            Component label = InputLabels.sourceShort(binding, profile, axes.length, pad, raw.buttonCount());
            int labelWidth = Math.min(room, font.width(label) + 6);
            ThemeText.fitted(g, font, label, areaX0, ty, labelWidth, profile.stickConflict(binding) != null ? InputGui.WARN : InputGui.TEXT);
            int barX0 = areaX0 + labelWidth;
            if (binding.source == SwitchSource.AXIS && barX0 + 20 < areaX1) {
                boolean readable = binding.readable(axes, buttons);
                int index = AddonInputs.switchIndex(row.id());
                InputGui.rangeBar(g, barX0, ty + 1, areaX1, ty + 7, binding.activeLow(), binding.activeHigh(), readable ? axes[binding.index] : 0.0F,
                        AddonSwitchEvaluator.isSet(this.liveBits, index), readable);
            } else if (binding.source == SwitchSource.BUTTON && barX0 + 10 < areaX1) {
                boolean pressed = binding.readable(axes, buttons) && buttons[binding.index];
                ThemeText.fitted(g, font, Component.translatable(pressed ? "propwash.input.modes.pressed" : "propwash.input.modes.released"), barX0, ty,
                        areaX1 - barX0, pressed ? InputGui.GOOD : InputGui.DIM);
            }
            return;
        }
        AxisAssignment assignment = profile == null ? AxisAssignment.keys() : profile.addonAxis(row.axis.id());
        Component label = assignment.onAxis() && raw != null && profile != null
                ? InputLabels.sourceShort(SwitchAssignment.axisRange(assignment.index, -1.0F, 1.0F), profile, axes.length, pad, raw.buttonCount())
                : Component.translatable("propwash.input.modes.addon_axis_keys");
        int labelWidth = Math.min(room, font.width(label) + 6);
        ThemeText.fitted(g, font, label, areaX0, ty, labelWidth, InputGui.TEXT);
        int barX0 = areaX0 + labelWidth;
        if (barX0 + 20 < areaX1) {
            float value = (float) this.axisValue(row.axis, raw, profile);
            InputGui.track(g, barX0, ty + 1, areaX1, ty + 7, false);
            g.verticalLine((barX0 + areaX1) / 2, ty + 2, ty + 6, InputGui.BAR_TICK);
            int mx = InputGui.markerX(barX0, areaX1, value);
            g.fill(mx, ty - 1, mx + 2, ty + 9, InputGui.ACCENT);
        }
    }

    private int extractDetail(GuiGraphicsExtractor g, Font font, @Nullable Entry entry, @Nullable RawInput raw, @Nullable DeviceProfile profile) {
        int top = this.detailTop;
        ThemeDraw.separator(g, this.x, this.x + this.width, top - 2);
        if (entry == null) {
            return this.textTop;
        }
        g.text(font, entry.name(), this.x, top + 1, InputGui.HIGHLIGHT, false);
        Component status = this.status(entry, raw, profile);
        int statusColor = entry.inactive() ? InputGui.WARN : this.listening ? InputGui.HIGHLIGHT : InputGui.DIM;
        if (status != null) {
            if (this.statusLines(font, entry) == 0) {
                g.text(font, status, this.x + this.width - font.width(status), top + 1, statusColor, false);
            } else {
                ThemeText.wrapped(g, font, status, this.x, top + TITLE, Math.max(20, this.width), statusColor);
            }
        }
        for (int i = 0; i < this.labels.size(); i++) {
            int[] at = this.labels.get(i);
            g.text(font, this.labelTexts.get(i), at[0], at[1], InputGui.DIM, false);
        }
        int ty = this.textTop;
        boolean device = raw != null && profile != null;
        List<Component> lines = this.texts(entry, device);
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            boolean warn = !device && i == lines.size() - 1 || entry.inactive() && i == 0;
            ty += ThemeText.wrapped(g, font, line, this.x, ty, Math.max(20, this.width), warn ? InputGui.WARN : InputGui.DIM);
        }
        return ty;
    }

    private void updateListening(@Nullable Entry entry, DeviceProfile profile, float[] axes, boolean[] buttons, boolean pad) {
        SwitchDetector current = this.detector;
        if (!this.listening || current == null || entry == null || entry.sw == null) {
            return;
        }
        SwitchAssignment binding = current.update(axes, buttons, Util.getNanos());
        if (binding == null) {
            return;
        }
        AddonBindings.applyButtonDefaults(entry.sw, binding, pad);
        profile.setAddonBinding(entry.sw.id(), binding);
        this.listening = false;
        this.detector = null;
        this.evaluator.reset();
        this.tab.markChanged();
        this.tab.rebuildLater();
    }

    private static final class PageTab extends FlatButton {
        private final Component shown;
        private final BooleanSupplier selected;

        PageTab(int x, int y, int width, int height, Component name, Component shown, BooleanSupplier selected, Runnable action) {
            super(x, y, width, height, name, FlatButton.Style.TAB, selected, action);
            this.shown = shown;
            this.selected = selected;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            ThemedWidgets.tab(g, this.font(), this.getX(), this.getY(), this.width, this.height, this.shown, this.state(), this.selected.getAsBoolean());
        }
    }
}
