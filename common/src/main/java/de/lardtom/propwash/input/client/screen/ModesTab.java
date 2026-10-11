package de.lardtom.propwash.input.client.screen;

import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.ChannelMath;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.StickChannel;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchCommand;
import de.lardtom.propwash.input.SwitchCommands;
import de.lardtom.propwash.input.SwitchDetector;
import de.lardtom.propwash.input.SwitchEvaluator;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.SwitchSource;
import de.lardtom.propwash.input.SwitchSources;
import de.lardtom.propwash.input.client.FunctionKeys;
import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.input.client.KeyNames;
import de.lardtom.propwash.input.client.RawInput;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.ui.FlowWrap;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ModesTab extends InputTab {
    private int effectRow;
    private int hintLinesWidth = -1;
    private int hintLines = 1;
    private int legendLines = 1;
    private @Nullable Panel panel;
    private int detailExtra;
    private int tailShift;
    private boolean summaryBelow;
    public static final String ID = "modes";

    private static final Row[] ALL_ROWS = Row.values();
    private static final Row[] FLIGHT_ROWS = Arrays.stream(ALL_ROWS).filter(r -> r.page() == Page.FLIGHT).toArray(Row[]::new);
    private static final Row[] COMMAND_ROWS = Arrays.stream(ALL_ROWS).filter(r -> r.page() == Page.COMMANDS).toArray(Row[]::new);
    private static final int NAME_INSET = 14;
    private static final int HEADER = 12;
    private static final int WIDGET = 18;
    private static final int GAP = 2;
    private static final int TITLE = 12;
    private static final int DETAIL_HEIGHT = TITLE + 3 * (WIDGET + GAP);
    private static final int MIN_ROW = 12;
    private static final int TIGHT_ROW = 11;
    private static final int MAX_ROW = 18;
    private static final int HINT_GAP = 5;
    private static final String[] HINT_KEYS = {"propwash.input.modes.key_override", "propwash.input.modes.key_override_3d_on",
            "propwash.input.modes.key_override_3d_off", "propwash.input.modes.commands.hint", "propwash.input.modes.hint",
            "propwash.input.modes.hint_keyboard"};
    private static final int SOURCE_LABEL = 46;
    private static final int SPINNER = 46;
    private static final int ASSIGN = 104;
    private static final int CLEAR = 20;
    private static final int CONTROLS = 112;
    private static final int INVERT_COLUMN = InputGui.INVERT_WIDTH + 8;
    private static final int PAGE_TAB_GAP = 2;
    private static final int STACK_BELOW = 110;

    private final SwitchEvaluator evaluator = new SwitchEvaluator();
    private final List<MicrosField> rangeFields = new ArrayList<>();
    private Row selected = Row.ARM;
    private Row otherSelected = Row.COMMAND_1;
    private Page page = Page.FLIGHT;
    private @Nullable SwitchFunction listening;
    private @Nullable SwitchDetector detector;
    private int dragRow = -1;
    private boolean dragHigh;
    private int liveBits;
    private int minLabelX = -1;
    private int maxLabelX = -1;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.input.tab.modes");
    }

    @Override
    public int order() {
        return 410;
    }

    public @Nullable SwitchFunction selected() {
        return this.selected.function;
    }

    public boolean acroSelected() {
        return this.selected == Row.ACRO;
    }

    public List<String> rowNames() {
        List<String> names = new ArrayList<>();
        for (Row row : this.rows()) {
            names.add(row.name().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    public void select(SwitchFunction function) {
        this.select(Row.of(function));
    }

    public void selectAcro() {
        this.select(Row.ACRO);
    }

    public boolean commandsShown() {
        return this.page == Page.COMMANDS;
    }

    public void showCommands(boolean commands) {
        this.showPage(commands ? Page.COMMANDS : Page.FLIGHT);
    }

    private void showPage(Page next) {
        if (this.page == next) {
            return;
        }
        if (this.listening != null) {
            this.listening = null;
            this.detector = null;
        }
        this.dragRow = -1;
        this.page = next;
        Row previous = this.selected;
        this.selected = this.otherSelected;
        this.otherSelected = previous;
        this.rebuild();
    }

    private void select(Row row) {
        if (row.page() != this.page) {
            this.showPage(row.page());
        }
        if (this.listening != null && this.listening != row.function) {
            this.stopListening();
        }
        if (this.selected != row) {
            this.selected = row;
            this.rebuild();
        }
    }

    private Row[] rows() {
        return this.page == Page.COMMANDS ? COMMAND_ROWS : FLIGHT_ROWS;
    }

    private int listTop() {
        return this.y + HEADER;
    }

    private int detailHeight() {
        return DETAIL_HEIGHT + this.detailExtra;
    }

    private int descriptionWidth() {
        return Math.max(20, this.width - CONTROLS - 6);
    }

    private List<String> descriptionLines(Font font, Row row) {
        int buttons = this.page == Page.COMMANDS || row == Row.MODE_3D ? 2 : 1;
        int beside = FlowWrap.linesBeside(buttons * WIDGET + (buttons - 1) * GAP, 2, Metrics.LINE_HEIGHT);
        return FlowWrap.lines(row.description().getString(), font::width, this.descriptionWidth(), beside, Math.max(20, this.width));
    }

    private static int descriptionRoom(boolean keyboard, boolean command) {
        return keyboard && !command ? 3 * WIDGET + 2 * GAP : 2 * (WIDGET + GAP);
    }

    private static Component noDeviceWarning() {
        return Component.translatable("propwash.input.modes.no_device_short");
    }

    private void measureDetail(Font font, @Nullable RawInput raw, @Nullable DeviceProfile profile) {
        this.detailExtra = 0;
        this.tailShift = 0;
        this.summaryBelow = false;
        boolean keyboard = raw != null || RadioState.INSTANCE.source() == RadioSource.KEYBOARD;
        boolean device = raw != null && profile != null;
        int pair = this.pairWidth(font, true);
        int between = pair < 0 ? 0 : this.width - 2 * pair - 12;
        int extra = 0;
        int shift = 0;
        for (Row row : this.rows()) {
            SwitchFunction function = row.function;
            boolean command = function != null && function.command();
            if (device && function != null) {
                if (command && font.width(commandSummary(profile.command(function))) > between) {
                    this.summaryBelow = true;
                }
                continue;
            }
            int lines = this.descriptionLines(font, row).size();
            shift = Math.max(shift, lines * Metrics.LINE_HEIGHT - descriptionRoom(keyboard, command));
        }
        extra = shift;
        if (!keyboard) {
            extra += (font.split(noDeviceWarning(), Math.max(20, this.width)).size() - 1) * Metrics.LINE_HEIGHT;
        }
        if (device) {
            for (Row row : this.rows()) {
                if (row.function != null) {
                    extra = Math.max(extra, this.panel(font, raw, profile, row).height() - DETAIL_HEIGHT);
                }
            }
        }
        this.tailShift = shift;
        this.detailExtra = extra;
    }

    private record Panel(int statusLines, int sourceWidth, boolean assignBelow, int row1, int assignY, int row2, int hintLines, int toggleX, int toggleY,
                         int keyLines, int keyY, int keyWidth, int row3, int buttonWidth, boolean stacked, int summaryLines, int summaryY, int height) {
    }

    private static int labeledNeed(Font font, Component label, int valueWidth) {
        return font.width(label) + Metrics.LABEL_GAP + 2 + valueWidth + 2 * Metrics.ARROW_WIDTH + 8;
    }

    private static int toggleNeed(Font font, Component label) {
        return font.width(label) + Metrics.LABEL_GAP + 2 + Metrics.TOGGLE_WIDTH + 5
                + Math.max(font.width(ThemedWidgets.onOff(true)), font.width(ThemedWidgets.onOff(false)));
    }

    private int pairWidth(Font font, boolean command) {
        if (2 * CONTROLS + GAP <= this.width) {
            return CONTROLS;
        }
        int half = (this.width - GAP) / 2;
        int controls = font.width(Component.translatable("propwash.input.modes.open_controls")) + 8;
        int other = command ? font.width(Component.translatable("propwash.input.command.edit")) + 8
                : toggleNeed(font, Component.translatable("propwash.input.modes.mode_3d.unlock"));
        return half >= Math.max(controls, other) ? half : -1;
    }

    private static List<Component> statusCandidates(SwitchFunction function) {
        List<Component> out = new ArrayList<>();
        if (function == SwitchFunction.MODE_3D) {
            out.add(Component.translatable("propwash.input.modes.mode_3d.locked"));
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

    private Panel panel(Font font, RawInput raw, DeviceProfile profile, Row row) {
        SwitchFunction function = Objects.requireNonNull(row.function);
        int width = Math.max(20, this.width);
        SwitchAssignment binding = profile.binding(function);
        boolean pad = raw.usesGamepadLayout(profile);
        int axisCount = raw.axes(profile).length;
        boolean locked = function == SwitchFunction.MODE_3D && !this.working().mode3dUnlocked;
        boolean listeningNow = this.listening == function;
        boolean command = function.command();

        int statusLines = 0;
        int statusWidth = 0;
        List<Component> candidates = statusCandidates(function);
        for (Component candidate : candidates) {
            statusWidth = Math.max(statusWidth, font.width(candidate));
        }
        if (font.width(Component.translatable(function.translationKey())) + 8 + statusWidth > width) {
            for (Component candidate : candidates) {
                statusLines = Math.max(statusLines, font.split(candidate, width).size());
            }
        }
        int y = TITLE + (statusLines > 0 ? statusLines * Metrics.LINE_HEIGHT + 2 : 0);

        int row1 = y;
        int choiceWidth = 0;
        for (SwitchSources.Choice choice : SwitchSources.choices(profile, axisCount, raw.buttons(profile).length, binding)) {
            choiceWidth = Math.max(choiceWidth, font.width(InputLabels.source(choice, profile, axisCount, pad, raw.buttonCount())));
        }
        int sourceWidth = Math.max(60, this.width - ASSIGN - CLEAR - 2 * GAP);
        boolean assignBelow = sourceWidth < labeledNeed(font, Component.translatable("propwash.input.modes.source"), choiceWidth)
                && this.width > sourceWidth;
        if (assignBelow) {
            sourceWidth = this.width;
            y += WIDGET + GAP;
        }
        int assignY = y;
        y += WIDGET + GAP;

        int row2 = y;
        int hintLines = 0;
        if (locked) {
            hintLines = font.split(Component.translatable("propwash.input.modes.mode_3d.unlock_first"), width).size();
        } else if (!binding.bound() && !listeningNow) {
            hintLines = font.split(Component.translatable("propwash.input.modes.unbound_hint"), width).size();
        }
        y += Math.max(WIDGET, hintLines == 0 ? 0 : 8 + hintLines * Metrics.LINE_HEIGHT) + GAP;
        int toggleX = -1;
        int toggleY = row2;
        if (binding.bound() && !listeningNow && !locked && function.supportsToggle()) {
            toggleX = 0;
            if (binding.source == SwitchSource.AXIS) {
                toggleX = font.width(Component.translatable("propwash.input.modes.min")) + 4 + SPINNER + 6
                        + font.width(Component.translatable("propwash.input.modes.max")) + 4 + SPINNER + 8;
            }
            int need = labeledNeed(font, Component.translatable("propwash.input.binding.mode"),
                    Math.max(font.width(Component.translatable("propwash.input.binding.mode.toggle")),
                            font.width(Component.translatable("propwash.input.binding.mode.hold"))));
            if (toggleX > 0 && this.width - toggleX < Math.max(40, Math.min(need, this.width))) {
                toggleX = 0;
                toggleY = y;
                y += WIDGET + GAP;
            }
        }

        int buttons = command || function == SwitchFunction.MODE_3D ? 2 : 1;
        int buttonWidth = buttons == 2 ? this.pairWidth(font, command) : Math.min(CONTROLS, this.width);
        boolean stacked = buttonWidth < 0;
        if (stacked) {
            buttonWidth = this.width;
        }
        int keyLines = 0;
        int keyY = y + 5;
        int keyWidth = stacked ? 0 : this.width - buttons * buttonWidth - (buttons - 1) * GAP - 6;
        if (!command) {
            Component keyLine = Component.translatable("propwash.input.modes.key_line", FunctionKeys.keyLabel(function), FunctionKeys.actionLabel(function));
            if (font.width(keyLine) > keyWidth) {
                keyLines = font.split(keyLine, width).size();
                keyWidth = width;
                keyY = y + 1;
                y += keyLines * Metrics.LINE_HEIGHT + 3;
            }
        }
        int row3 = y;
        y += stacked ? 2 * (WIDGET + GAP) : WIDGET + GAP;
        int summaryLines = 0;
        int summaryY = row3 + 5;
        if (command && (this.summaryBelow || stacked || font.width(commandSummary(profile.command(function))) > this.width - 2 * buttonWidth - 12)) {
            summaryLines = font.split(commandSummary(profile.command(function)), width).size();
            summaryY = y + 1;
            y += summaryLines * Metrics.LINE_HEIGHT + 3;
        }
        return new Panel(statusLines, sourceWidth, assignBelow, row1, assignY, row2, hintLines, toggleX, toggleY, keyLines, keyY, keyWidth, row3,
                buttonWidth, stacked, summaryLines, summaryY, y);
    }

    private int hintBlock() {
        return HINT_GAP + (this.longestHintLines() + this.legendLines) * Metrics.LINE_HEIGHT + 3;
    }

    private boolean hintsBelow() {
        return this.height < HEADER + this.rows().length * MIN_ROW + 2 + this.detailHeight() + this.hintBlock();
    }

    private int hintSpace() {
        return this.hintsBelow() ? 0 : this.hintBlock();
    }

    private int longestHintLines() {
        int width = Math.max(40, this.width);
        if (width == this.hintLinesWidth) {
            return this.hintLines;
        }
        Font font = this.host.font();
        int lines = 1;
        for (String key : HINT_KEYS) {
            lines = Math.max(lines, font.split(Component.translatable(key, PilotSession.modeName(StabMode.HORIZON)), width).size());
        }
        this.hintLinesWidth = width;
        this.hintLines = lines;
        this.legendLines = Math.max(1, font.split(Component.translatable("propwash.input.modes.legend.led"), Math.max(20, this.width - NAME_INSET)).size());
        return lines;
    }

    private int minRow() {
        return this.height >= HEADER + this.rows().length * MIN_ROW + 2 + this.detailHeight() ? MIN_ROW : TIGHT_ROW;
    }

    private int pinnedDetailTop() {
        return Math.max(this.listTop() + this.rows().length * Math.max(this.minRow(), this.effectRow) + 2 + this.hintSpace(),
                this.y + this.height - this.detailHeight());
    }

    private void measureEffects(Font font, boolean device) {
        this.effectRow = 0;
        int width = Math.max(20, this.areaX1(device) - this.areaX0(font));
        int lines = 1;
        for (Row row : this.rows()) {
            if (!device || row.function == null) {
                lines = Math.max(lines, font.split(row.effect(device), width).size());
            }
        }
        if (this.stacked(font)) {
            this.effectRow = (1 + lines) * Metrics.LINE_HEIGHT + 3;
        } else if (lines > 1) {
            this.effectRow = lines * Metrics.LINE_HEIGHT + 3;
        }
    }

    private int detailTop() {
        return Math.min(this.pinnedDetailTop(), this.listTop() + this.rows().length * this.rowHeight() + 2 + this.hintSpace());
    }

    private int rowHeight() {
        int fit = (this.pinnedDetailTop() - 2 - this.hintSpace() - this.listTop()) / this.rows().length;
        return Math.max(Math.max(this.minRow(), this.effectRow), Math.min(MAX_ROW, fit));
    }

    private int rowY(int index) {
        return this.listTop() + index * this.rowHeight();
    }

    private boolean stacked(Font font) {
        return this.width - 2 - INVERT_COLUMN - NAME_INSET - this.naturalNameWidth(font) - this.keyWidth(font) < STACK_BELOW;
    }

    private int nameWidth(Font font) {
        return this.stacked(font) ? this.width - NAME_INSET - this.keyWidth(font) : this.naturalNameWidth(font);
    }

    private int areaTextY(Font font, int rowY, int ty) {
        return this.stacked(font) ? rowY + 2 + Metrics.LINE_HEIGHT : ty;
    }

    private int naturalNameWidth(Font font) {
        int width = 0;
        for (Row row : ALL_ROWS) {
            width = Math.max(width, font.width(row.title()));
        }
        return Math.min(width + 12, Math.max(60, this.width * 9 / 20));
    }

    private int keyX(Font font) {
        return this.x + NAME_INSET + this.nameWidth(font);
    }

    private int keyWidth(Font font) {
        int width = font.width(Component.translatable("propwash.input.modes.header_key"));
        for (Row row : ALL_ROWS) {
            width = Math.max(width, font.width(row.keyLabel()));
        }
        return Math.max(28, Math.min(80, width + 14));
    }

    private int areaX0(Font font) {
        return this.stacked(font) ? this.x + NAME_INSET : this.keyX(font) + this.keyWidth(font);
    }

    private int areaX1(boolean device) {
        return this.x + this.width - 2 - (device ? INVERT_COLUMN : 0);
    }

    private int invertX() {
        return this.x + this.width - INVERT_COLUMN + 4;
    }

    private int barX0(Font font) {
        return this.areaX0(font) + SOURCE_LABEL;
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < this.x || mouseX >= this.x + this.width || mouseY < this.listTop()) {
            return -1;
        }
        int row = (int) ((mouseY - this.listTop()) / this.rowHeight());
        return row >= 0 && row < this.rows().length ? row : -1;
    }

    @Override
    protected void build() {
        this.rememberSignature();
        this.minLabelX = -1;
        this.maxLabelX = -1;
        this.rangeFields.clear();
        RawInput raw = this.raw();
        DeviceProfile profile = this.profile(raw);
        Font font = this.host.font();
        this.measureEffects(font, raw != null && profile != null);
        this.measureDetail(font, raw, profile);
        int right = this.x + this.width;
        int top = this.detailTop();
        boolean detailControls = raw != null && profile != null && this.selected.function != null;
        Panel panel = detailControls ? this.panel(font, raw, profile, this.selected) : null;
        this.panel = panel;
        int row1 = top + (panel != null ? panel.row1() : TITLE);
        int row2 = panel != null ? top + panel.row2() : row1 + WIDGET + GAP;
        int row3 = panel != null ? top + panel.row3() : row2 + WIDGET + GAP;
        int pair = panel != null ? panel.buttonWidth() : CONTROLS;
        boolean stacked = panel != null && panel.stacked();
        int second = stacked ? row3 + WIDGET + GAP : row3;
        boolean commandPage = this.page == Page.COMMANDS;
        this.buildPageTabs(font);
        this.host.addWidget(Button.builder(Component.translatable("propwash.input.modes.open_controls"), b -> this.openControls())
                .bounds(right - pair, detailControls ? second : commandPage ? row2 : row1, pair, WIDGET)
                .tooltip(Tooltip.create(Component.translatable("propwash.input.modes.open_controls.tooltip")))
                .build());
        if (commandPage && this.selected.function != null) {
            SwitchFunction edited = this.selected.function;
            this.host.addWidget(Button.builder(Component.translatable("propwash.input.command.edit"), b -> this.editCommand(edited))
                    .bounds(detailControls ? this.x : right - CONTROLS, detailControls ? row3 : row1, pair, WIDGET)
                    .tooltip(Tooltip.create(Component.translatable("propwash.input.command.edit.tooltip")))
                    .build()).active = this.listening == null;
        }
        boolean unlocked = this.working().mode3dUnlocked;
        if (this.selected == Row.MODE_3D) {
            this.host.addWidget(CycleButton.onOffBuilder(unlocked)
                    .withTooltip(v -> Tooltip.create(Component.translatable("propwash.input.modes.mode_3d.unlock.tooltip")))
                    .create(!detailControls ? right - CONTROLS : stacked ? this.x : right - 2 * pair - GAP, detailControls ? row3 : row2, pair, WIDGET,
                            Component.translatable("propwash.input.modes.mode_3d.unlock"), (b, v) -> {
                                this.working().mode3dUnlocked = v;
                                this.markChanged();
                                this.rebuild();
                            }));
        }
        if (raw != null && profile != null) {
            this.buildInvertToggles(profile);
        }
        SwitchFunction function = this.selected.function;
        if (raw == null || profile == null || function == null || panel == null) {
            return;
        }
        SwitchAssignment binding = profile.binding(function);
        boolean pad = raw.usesGamepadLayout(profile);
        int axisCount = raw.axes(profile).length;
        int buttonSlots = raw.buttons(profile).length;
        boolean isListening = this.listening == function;

        int sourceWidth = panel.sourceWidth();
        int assignRow = top + panel.assignY();
        List<SwitchSources.Choice> choices = SwitchSources.choices(profile, axisCount, buttonSlots, binding);
        CycleButton<SwitchSources.Choice> source = CycleButton.builder(
                        (SwitchSources.Choice c) -> InputLabels.source(c, profile, axisCount, pad, raw.buttonCount()), SwitchSources.of(binding))
                .withValues(choices)
                .withTooltip(c -> ThemeText.tip(this.sourceTooltip(profile, c, pad)))
                .create(this.x, row1, sourceWidth, WIDGET, Component.translatable("propwash.input.modes.source"), (b, v) -> this.chooseSource(function, v));
        boolean locked = function == SwitchFunction.MODE_3D && !unlocked;
        source.active = this.listening == null && !locked;
        this.host.addWidget(source);
        int assignX = panel.assignBelow() ? this.x : this.x + sourceWidth + GAP;
        Button assign = this.host.addWidget(Button.builder(
                        Component.translatable(isListening ? "propwash.input.modes.listening_short" : "propwash.input.modes.assign"),
                        b -> this.toggleListening(function))
                .bounds(assignX, assignRow, panel.assignBelow() ? right - CLEAR - GAP - assignX : ASSIGN, WIDGET)
                .tooltip(Tooltip.create(Component.translatable("propwash.input.modes.assign.tooltip")))
                .build());
        assign.active = (this.listening == null || isListening) && !locked;
        Button clear = this.host.addWidget(Button.builder(Component.literal("×"), b -> this.chooseSource(function, SwitchSources.Choice.NONE))
                .bounds(right - CLEAR, assignRow, CLEAR, WIDGET)
                .tooltip(Tooltip.create(Component.translatable("propwash.input.modes.clear")))
                .build());
        clear.active = this.listening == null && binding.bound() && !locked;

        if (!binding.bound() || isListening || locked) {
            return;
        }
        int cx = this.x;
        if (binding.source == SwitchSource.AXIS) {
            this.minLabelX = cx;
            cx += font.width(Component.translatable("propwash.input.modes.min")) + 4;
            this.host.addWidget(this.rangeField(cx, row2, binding, false));
            cx += SPINNER + 6;
            this.maxLabelX = cx;
            cx += font.width(Component.translatable("propwash.input.modes.max")) + 4;
            this.host.addWidget(this.rangeField(cx, row2, binding, true));
        }
        if (panel.toggleX() < 0) {
            return;
        }
        int toggleX = this.x + panel.toggleX();
        this.host.addWidget(CycleButton.booleanBuilder(Component.translatable("propwash.input.binding.mode.toggle"),
                        Component.translatable("propwash.input.binding.mode.hold"), binding.toggle)
                .withTooltip(v -> Tooltip.create(Component.translatable("propwash.input.binding.mode.tooltip")))
                .create(toggleX, top + panel.toggleY(), right - toggleX, WIDGET, Component.translatable("propwash.input.binding.mode"), (b, v) -> {
                    binding.toggle = v;
                    this.evaluator.reset();
                    this.markChanged();
                    this.rebuild();
                }));
    }

    private void buildPageTabs(Font font) {
        Component flight = Component.translatable("propwash.input.modes.page.flight");
        Component commands = Component.translatable("propwash.input.modes.page.commands");
        int available = Math.max(40, this.keyX(font) - 4 - this.x);
        int flightWidth = font.width(flight) + 12;
        int commandsWidth = font.width(commands) + 12;
        int total = flightWidth + PAGE_TAB_GAP + commandsWidth;
        if (total > available) {
            flightWidth = Math.max(16, flightWidth * (available - PAGE_TAB_GAP) / (total - PAGE_TAB_GAP));
            commandsWidth = Math.max(16, available - PAGE_TAB_GAP - flightWidth);
        }
        FlatButton flightTab = new FlatButton(this.x, this.y, flightWidth, HEADER - 1, flight, FlatButton.Style.TAB,
                () -> this.page == Page.FLIGHT, () -> this.showPage(Page.FLIGHT));
        flightTab.setTooltip(Tooltip.create(Component.translatable("propwash.input.modes.page.flight.tooltip")));
        this.host.addWidget(flightTab);
        FlatButton commandsTab = new FlatButton(this.x + flightWidth + PAGE_TAB_GAP, this.y, commandsWidth, HEADER - 1, commands, FlatButton.Style.TAB,
                () -> this.page == Page.COMMANDS, () -> this.showPage(Page.COMMANDS));
        commandsTab.setTooltip(Tooltip.create(Component.translatable("propwash.input.modes.page.commands.tooltip")));
        this.host.addWidget(commandsTab);
    }

    public void editCommand(SwitchFunction function) {
        if (!function.command()) {
            return;
        }
        RawInput raw = this.raw();
        DeviceProfile profile = this.profile(raw);
        String guid = raw == null ? "" : raw.device().guid();
        SwitchCommand current = profile != null ? profile.command(function) : SwitchCommands.get(this.working().keyboard.commands, function);
        Component source;
        if (raw != null && profile != null) {
            SwitchAssignment binding = profile.binding(function);
            source = binding.bound() ? InputLabels.bindingFor(binding, profile, raw) : Component.translatable("propwash.input.binding.none");
        } else {
            source = Component.translatable("propwash.input.command.edit.keyboard");
        }
        Component device = raw != null ? InputLabels.deviceTitle(raw.device()) : Component.translatable("propwash.input.keyboard");
        this.host.minecraft().gui.setScreen(new CommandEditScreen(this.host.screen(), function, current, device, source,
                FunctionKeys.keyLabel(function), edited -> this.applyCommand(guid, function, edited)));
    }

    private void applyCommand(String guid, SwitchFunction function, SwitchCommand edited) {
        DeviceProfile target = guid.isEmpty() ? null : this.working().profile(guid);
        if (target != null) {
            target.setCommand(function, edited);
        } else {
            SwitchCommands.put(this.working().keyboard.commands, function, edited);
        }
        this.markChanged();
        this.rebuildLater();
    }

    private void buildInvertToggles(DeviceProfile profile) {
        boolean stacked = this.stacked(this.host.font());
        int height = stacked ? this.rowHeight() - Metrics.LINE_HEIGHT - 3 : this.rowHeight() - 1;
        int shift = stacked ? Metrics.LINE_HEIGHT + 1 : 0;
        Row[] rows = this.rows();
        for (int i = 0; i < rows.length; i++) {
            Row row = rows[i];
            SwitchFunction function = row.function;
            if (function == null || this.listening == function) {
                continue;
            }
            SwitchAssignment binding = profile.binding(function);
            if (!binding.bound()) {
                continue;
            }
            CycleButton<Boolean> toggle = InputGui.invertToggle(this.invertX(), this.rowY(i) + shift, InputGui.INVERT_WIDTH, height, function, binding,
                    v -> this.setInverted(row, binding, v));
            toggle.active = this.listening == null;
            this.host.addWidget(toggle);
        }
    }

    private void setInverted(Row row, SwitchAssignment binding, boolean inverted) {
        binding.invert = inverted;
        this.markChanged();
        this.select(row);
    }

    private MicrosField rangeField(int x, int y, SwitchAssignment binding, boolean high) {
        MicrosField field = new MicrosField(x, y, SPINNER, WIDGET, Component.translatable(high ? "propwash.input.modes.max" : "propwash.input.modes.min"),
                () -> high ? binding.activeHighMicros() : binding.activeLowMicros(),
                v -> this.setRange(binding, high, v), ChannelMath.RANGE_STEP_US, ChannelMath.RANGE_MIN_US, ChannelMath.RANGE_MAX_US);
        this.rangeFields.add(field);
        return field;
    }

    private Component sourceTooltip(DeviceProfile profile, SwitchSources.Choice choice, boolean pad) {
        StickChannel stick = choice.source() == SwitchSource.AXIS ? profile.stickOnAxis(choice.index()) : null;
        if (stick == null) {
            return Component.translatable("propwash.input.modes.source.tooltip");
        }
        return Component.translatable("propwash.input.modes.on_stick.tooltip", InputLabels.axis(choice.index(), pad), Component.translatable(stick.translationKey()));
    }

    private void setRange(SwitchAssignment binding, boolean high, int micros) {
        if (high) {
            binding.setActiveHighMicros(micros);
        } else {
            binding.setActiveLowMicros(micros);
        }
        this.markChanged();
    }

    private void chooseSource(SwitchFunction function, SwitchSources.Choice choice) {
        RawInput raw = this.raw();
        DeviceProfile profile = this.profile(raw);
        if (raw == null || profile == null) {
            return;
        }
        profile.setBinding(function, SwitchSources.apply(profile.binding(function), choice, function, raw.usesGamepadLayout(profile)));
        this.evaluator.reset();
        this.markChanged();
        this.rebuild();
    }

    private void openControls() {
        if (this.host.screen() instanceof ConfiguratorScreen screen) {
            screen.selectTab(KeysTab.ID);
        }
    }

    private void toggleListening(SwitchFunction function) {
        if (this.listening == function) {
            this.stopListening();
            return;
        }
        RawInput raw = this.raw();
        DeviceProfile profile = this.profile(raw);
        if (raw == null || profile == null) {
            return;
        }
        float[] axes = raw.axes(profile);
        this.selected = Row.of(function);
        this.listening = function;
        this.detector = new SwitchDetector(axes, raw.buttons(profile), profile.mappedAxes(axes.length));
        TransmitterInput.INSTANCE.requestCapture();
        this.rebuild();
    }

    @Override
    public void tick() {
        if (this.listening != null) {
            TransmitterInput.INSTANCE.requestCapture();
        }
        super.tick();
    }

    private void stopListening() {
        this.listening = null;
        this.detector = null;
        this.rebuild();
    }

    @Override
    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int bottom = this.detailTop() + this.detailHeight() + (this.hintsBelow() ? this.hintBlock() : 0);
        this.reportBottom(bottom);
        g.enableScissor(this.x, this.y, this.x + this.width, Math.max(this.y + this.height, bottom));
        try {
            this.extractClipped(g, mouseX, mouseY);
        } finally {
            g.disableScissor();
        }
    }

    private void extractClipped(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Font font = this.host.font();
        RawInput raw = this.raw();
        DeviceProfile profile = this.profile(raw);
        boolean device = raw != null && profile != null;
        float[] axes = device ? raw.axes(profile) : new float[0];
        boolean[] buttons = device ? raw.buttons(profile) : new boolean[0];
        boolean pad = device && raw.usesGamepadLayout(profile);
        int buttonCount = device ? raw.buttonCount() : -1;
        if (device) {
            this.liveBits = this.evaluator.evaluate(profile, axes, buttons);
            this.updateListening(profile, axes, buttons, pad);
        }

        int keyX = this.keyX(font);
        int keyWidth = this.keyWidth(font);
        int areaX0 = this.areaX0(font);
        int areaX1 = this.areaX1(device);
        g.fill(this.x, this.y, this.x + this.width, this.y + HEADER - 1, Palette.PANEL_HEADER);
        ThemeDraw.separator(g, this.x, this.x + this.width, this.y + HEADER - 1);
        g.text(font, Component.translatable("propwash.input.modes.header_key"), keyX, this.y + 2, InputGui.DIM, false);
        boolean stacked = this.stacked(font);
        if (!stacked) {
            g.text(font, Component.translatable(device ? "propwash.input.modes.header_range" : "propwash.input.modes.header_action"),
                    areaX0, this.y + 2, InputGui.DIM, false);
        }
        if (device) {
            int invertX = this.invertX();
            ThemeText.fitted(g, font, Component.translatable("propwash.input.modes.header_invert").getString(), invertX, this.y + 2, this.x + this.width - invertX, InputGui.DIM);
            if (mouseX >= invertX - 4 && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + HEADER) {
                ThemeText.tooltip(g, font, Component.translatable("propwash.input.modes.header_invert.tooltip"), mouseX, mouseY);
            }
        }
        int rowHeight = this.rowHeight();
        int nameWidth = this.nameWidth(font);
        Row[] rows = this.rows();
        for (int i = 0; i < rows.length; i++) {
            Row entry = rows[i];
            SwitchFunction function = entry.function;
            int rowY = this.rowY(i);
            int nameTy = stacked ? rowY + 2 : rowY + (rowHeight - 1 - 8) / 2;
            int ty = this.areaTextY(font, rowY, nameTy);
            boolean active = lit(entry, device, this.liveBits);
            if (entry == this.selected) {
                g.fill(this.x, rowY, this.x + this.width, rowY + rowHeight - 1, Palette.HOVER_TINT);
                g.verticalLine(this.x, rowY, rowY + rowHeight - 2, InputGui.ACCENT);
            }
            if (active) {
                g.fill(this.x + 1, rowY, stacked ? this.x + this.width - 1 : areaX0 - 3, rowY + rowHeight - 1, Palette.withAlpha(Palette.SUCCESS, 0x28));
            }
            InputGui.led(g, this.x + 2, nameTy, active);
            ThemeText.fitted(g, font, entry.title().getString(), this.x + NAME_INSET, nameTy, nameWidth - 4,
                    active || entry == this.selected ? InputGui.TEXT : InputGui.DIM);
            Component key = entry.keyLabel();
            KeyMapping mapping = entry.mapping();
            boolean bound = mapping != null && !mapping.isUnbound();
            ThemeText.fitted(g, font, key.getString(), keyX, nameTy, keyWidth - 6, bound ? InputGui.ACCENT : InputGui.FAINT);
            if (mouseX >= keyX && mouseX < keyX + keyWidth - 4 && mouseY >= rowY && mouseY < rowY + rowHeight) {
                ThemeText.tooltip(g, font, Component.translatable("propwash.input.modes.key_tooltip", key, entry.actionLabel()), mouseX, mouseY);
            }
            if (function != null && function.command()) {
                this.extractCommandRow(g, font, function, profile, device, axes.length, pad, buttonCount, areaX0, areaX1, ty, rowY, rowHeight, mouseX, mouseY);
                continue;
            }
            if (!device || function == null) {
                List<FormattedCharSequence> effect = font.split(entry.effect(device), Math.max(20, areaX1 - areaX0));
                int effectY = effect.size() <= 1 || stacked ? ty : rowY + (rowHeight - 1 - effect.size() * Metrics.LINE_HEIGHT + 2) / 2;
                for (FormattedCharSequence line : effect) {
                    g.text(font, line, areaX0, effectY, InputGui.FAINT, false);
                    effectY += Metrics.LINE_HEIGHT;
                }
                continue;
            }
            if (this.listening == function) {
                g.text(font, Component.translatable("propwash.input.modes.listening"), areaX0, ty, InputGui.HIGHLIGHT, false);
                continue;
            }
            SwitchAssignment binding = profile.binding(function);
            String label = binding.source == SwitchSource.NONE ? ""
                    : InputGui.fit(font, InputLabels.sourceShort(binding, profile, axes.length, pad, buttonCount).getString(), SOURCE_LABEL - 4);
            switch (binding.source) {
                case AXIS -> {
                    StickChannel stick = profile.stickConflict(binding);
                    g.text(font, label, areaX0, ty, stick != null ? InputGui.WARN : InputGui.TEXT, false);
                    if (stick != null && mouseX >= areaX0 && mouseX < this.barX0(font) && mouseY >= rowY && mouseY < rowY + rowHeight) {
                        ThemeText.tooltip(g, font, Component.translatable("propwash.input.modes.on_stick.tooltip",
                                InputLabels.axis(binding.index, pad), Component.translatable(stick.translationKey())), mouseX, mouseY);
                    }
                    int barX0 = this.barX0(font);
                    boolean readable = binding.readable(axes, buttons);
                    float value = readable ? axes[binding.index] : 0.0F;
                    if (barX0 + 20 < areaX1) {
                        InputGui.rangeBar(g, barX0, ty + 1, areaX1, ty + 7, binding.activeLow(), binding.activeHigh(), value, active, readable);
                        if (mouseX >= barX0 && mouseX < areaX1 && mouseY >= rowY && mouseY < rowY + rowHeight) {
                            ThemeText.tooltip(g, font, Component.translatable("propwash.input.modes.range_tooltip",
                                    binding.activeLowMicros(), binding.activeHighMicros(), ChannelMath.rangeMicros(value)), mouseX, mouseY);
                        }
                    }
                }
                case BUTTON -> {
                    g.text(font, label, areaX0, ty, InputGui.TEXT, false);
                    boolean pressed = binding.readable(axes, buttons) && buttons[binding.index];
                    Component state = Component.translatable(pressed ? "propwash.input.modes.pressed" : "propwash.input.modes.released");
                    if (binding.toggle) {
                        state = state.copy().append(" ").append(Component.translatable("propwash.input.binding.toggled"));
                    }
                    ThemeText.fitted(g, font, state.getString(), areaX0 + SOURCE_LABEL, ty, areaX1 - areaX0 - SOURCE_LABEL, pressed ? InputGui.GOOD : InputGui.DIM);
                }
                case NONE -> g.text(font, Component.translatable("propwash.input.binding.none"), areaX0, ty, InputGui.FAINT, false);
            }
        }
        int hintY = (this.hintsBelow() ? this.detailTop() + this.detailHeight() : this.rowY(rows.length)) + HINT_GAP;
        PilotSession session = PilotSession.INSTANCE;
        boolean override = session.keyOverridesModeSwitch();
        boolean override3d = !override && session.keyOverridesMode3dSwitch();
        Component hint = override
                ? Component.translatable("propwash.input.modes.key_override", PilotSession.modeName(session.flightMode()))
                : override3d ? Component.translatable(session.mode3dSelected() ? "propwash.input.modes.key_override_3d_on"
                : "propwash.input.modes.key_override_3d_off")
                : Component.translatable(this.page == Page.COMMANDS ? "propwash.input.modes.commands.hint"
                : device ? "propwash.input.modes.hint" : "propwash.input.modes.hint_keyboard");
        override = this.page == Page.FLIGHT && (override || override3d);
        int hintHeight = ThemeText.wrapped(g, font, hint, this.x, hintY, Math.max(40, this.width), override ? InputGui.HIGHLIGHT : InputGui.FAINT);
        this.extractLegend(g, font, hintY + hintHeight);
        this.extractDetail(g, font, raw, profile, axes, buttons);
    }

    private SwitchCommand commandFor(@Nullable DeviceProfile profile, SwitchFunction function) {
        return profile != null ? profile.command(function) : SwitchCommands.get(this.working().keyboard.commands, function);
    }

    public static Component commandSummary(SwitchCommand command) {
        if (command.empty()) {
            return Component.translatable("propwash.input.command.none");
        }
        if (!command.hasOff()) {
            return Component.literal(command.on);
        }
        if (!command.hasOn()) {
            return Component.translatable("propwash.input.command.summary.off", command.off);
        }
        return Component.translatable("propwash.input.command.summary.on_off", command.on, command.off);
    }

    public static List<Component> commandTooltip(SwitchFunction function, SwitchCommand command) {
        Component none = Component.translatable("propwash.input.key.unbound");
        return List.of(Component.translatable(function.translationKey()),
                Component.translatable("propwash.input.command.tooltip.on", command.hasOn() ? Component.literal(command.on) : none),
                Component.translatable("propwash.input.command.tooltip.off", command.hasOff() ? Component.literal(command.off) : none));
    }

    private void extractCommandRow(GuiGraphicsExtractor g, Font font, SwitchFunction function, @Nullable DeviceProfile profile, boolean device,
                                   int axisCount, boolean pad, int buttonCount, int areaX0, int areaX1, int ty, int rowY, int rowHeight,
                                   int mouseX, int mouseY) {
        SwitchCommand command = this.commandFor(profile, function);
        int textX = areaX0;
        if (device && profile != null) {
            if (this.listening == function) {
                g.text(font, Component.translatable("propwash.input.modes.listening"), areaX0, ty, InputGui.HIGHLIGHT, false);
                return;
            }
            SwitchAssignment binding = profile.binding(function);
            StickChannel stick = profile.stickConflict(binding);
            Component label = binding.bound() ? InputLabels.sourceShort(binding, profile, axisCount, pad, buttonCount)
                    : Component.translatable("propwash.input.key.unbound");
            int color = !binding.bound() ? InputGui.FAINT : stick != null ? InputGui.WARN : InputGui.TEXT;
            ThemeText.fitted(g, font, label.getString(), areaX0, ty, SOURCE_LABEL - 4, color);
            textX = areaX0 + SOURCE_LABEL;
        }
        if (textX + 12 < areaX1) {
            Component summary = commandSummary(command);
            if (command.empty() && font.width(summary) > areaX1 - textX) {
                summary = Component.translatable("propwash.input.command.none_short");
            }
            ThemeText.fitted(g, font, summary.getString(), textX, ty, areaX1 - textX, command.empty() ? InputGui.FAINT : InputGui.DIM);
        }
        if (mouseX >= areaX0 && mouseX < areaX1 && mouseY >= rowY && mouseY < rowY + rowHeight) {
            ThemeText.tooltipLines(g, font, commandTooltip(function, command), mouseX, mouseY);
        }
    }

    private void extractLegend(GuiGraphicsExtractor g, Font font, int y) {
        InputGui.led(g, this.x + 2, y, true);
        int tx = this.x + NAME_INSET;
        ThemeText.wrapped(g, font, Component.translatable("propwash.input.modes.legend.led"), tx, y, Math.max(20, this.x + this.width - tx), InputGui.FAINT);
    }

    private void extractDetail(GuiGraphicsExtractor g, Font font, @Nullable RawInput raw, @Nullable DeviceProfile profile, float[] axes, boolean[] buttons) {
        int top = this.detailTop();
        int row1 = top + TITLE;
        int row2 = row1 + WIDGET + GAP;
        int row3 = row2 + WIDGET + GAP;
        ThemeDraw.separator(g, this.x, this.x + this.width, top - 2);
        Row entry = this.selected;
        SwitchFunction function = entry.function;
        if (raw == null || profile == null || function == null) {
            g.text(font, entry.title(), this.x, top + 1, InputGui.HIGHLIGHT, false);
            Component keyText = Component.translatable("propwash.input.modes.key_short", entry.keyLabel());
            g.text(font, keyText, this.x + this.width - font.width(keyText), top + 1, InputGui.ACCENT, false);
            boolean keyboard = raw != null || RadioState.INSTANCE.source() == RadioSource.KEYBOARD;
            boolean command = function != null && function.command();
            List<String> lines = this.descriptionLines(font, entry);
            int below = Math.max(row3 + 5 + this.tailShift, row1 + lines.size() * Metrics.LINE_HEIGHT + 1);
            for (int i = 0; i < lines.size(); i++) {
                g.text(font, lines.get(i), this.x, row1 + i * Metrics.LINE_HEIGHT, InputGui.DIM, false);
            }
            if (command && keyboard) {
                SwitchCommand current = this.commandFor(profile, function);
                ThemeText.fitted(g, font, commandSummary(current).getString(), this.x, below, this.width, current.empty() ? InputGui.FAINT : InputGui.TEXT);
            }
            if (!keyboard) {
                ThemeText.wrapped(g, font, noDeviceWarning(), this.x, below, Math.max(20, this.width), InputGui.WARN);
            }
            return;
        }
        Panel panel = this.panel != null ? this.panel : this.panel(font, raw, profile, entry);
        row2 = top + panel.row2();
        row3 = top + panel.row3();
        g.text(font, Component.translatable(function.translationKey()), this.x, top + 1, InputGui.HIGHLIGHT, false);
        if (function.command()) {
            SwitchCommand current = profile.command(function);
            int summaryColor = current.empty() ? InputGui.FAINT : InputGui.TEXT;
            if (panel.summaryLines() > 0) {
                ThemeText.wrapped(g, font, commandSummary(current), this.x, top + panel.summaryY(), Math.max(20, this.width), summaryColor);
            } else {
                ThemeText.fitted(g, font, commandSummary(current).getString(), this.x + panel.buttonWidth() + 6, top + panel.summaryY(),
                        this.width - 2 * panel.buttonWidth() - 12, summaryColor);
            }
        } else {
            Component keyLine = Component.translatable("propwash.input.modes.key_line", FunctionKeys.keyLabel(function), FunctionKeys.actionLabel(function));
            if (panel.keyLines() > 0) {
                ThemeText.wrapped(g, font, keyLine, this.x, top + panel.keyY(), panel.keyWidth(), InputGui.DIM);
            } else {
                ThemeText.fitted(g, font, keyLine.getString(), this.x, top + panel.keyY(), panel.keyWidth(), InputGui.DIM);
            }
        }
        SwitchAssignment binding = profile.binding(function);
        Component status = null;
        int statusColor = InputGui.DIM;
        boolean active = SwitchEvaluator.isSet(this.liveBits, function);
        StickChannel stick = profile.stickConflict(binding);
        if (function == SwitchFunction.MODE_3D && !this.working().mode3dUnlocked) {
            status = Component.translatable("propwash.input.modes.mode_3d.locked");
            statusColor = InputGui.WARN;
        } else if (this.listening == function) {
            status = Component.translatable("propwash.input.modes.listening");
            statusColor = InputGui.HIGHLIGHT;
        } else if (stick != null) {
            status = Component.translatable("propwash.input.modes.on_stick", Component.translatable(stick.translationKey()));
            statusColor = InputGui.WARN;
        } else if (binding.source == SwitchSource.AXIS && binding.readable(axes, buttons)) {
            status = Component.translatable(active ? "propwash.input.modes.live_active" : "propwash.input.modes.live", ChannelMath.rangeMicros(axes[binding.index]));
            statusColor = active ? InputGui.GOOD : InputGui.DIM;
        } else if (binding.source == SwitchSource.BUTTON && binding.readable(axes, buttons)) {
            status = Component.translatable(active ? "propwash.input.modes.state_active" : "propwash.input.modes.state_inactive");
            statusColor = active ? InputGui.GOOD : InputGui.DIM;
        } else if (binding.bound()) {
            status = Component.translatable("propwash.input.modes.unreadable");
            statusColor = InputGui.WARN;
        }
        if (status != null) {
            if (panel.statusLines() > 0) {
                ThemeText.wrapped(g, font, status, this.x, top + TITLE, Math.max(20, this.width), statusColor);
            } else {
                int w = font.width(status);
                g.text(font, status, this.x + this.width - w, top + 1, statusColor, false);
            }
        }
        if (this.minLabelX >= 0) {
            g.text(font, Component.translatable("propwash.input.modes.min"), this.minLabelX, row2 + 5, InputGui.DIM, false);
            g.text(font, Component.translatable("propwash.input.modes.max"), this.maxLabelX, row2 + 5, InputGui.DIM, false);
        }
        if (panel.hintLines() > 0) {
            boolean locked = function == SwitchFunction.MODE_3D && !this.working().mode3dUnlocked;
            if (locked) {
                ThemeText.wrapped(g, font, Component.translatable("propwash.input.modes.mode_3d.unlock_first"), this.x, row2 + 5, Math.max(20, this.width),
                        InputGui.WARN);
            } else if (!binding.bound() && this.listening != function) {
                ThemeText.wrapped(g, font, Component.translatable("propwash.input.modes.unbound_hint"), this.x, row2 + 5, Math.max(20, this.width),
                        InputGui.FAINT);
            }
        }
    }

    public static boolean modeLit(StabMode mode, boolean device, int bits) {
        for (Row row : FLIGHT_ROWS) {
            if (row.mode() == mode) {
                return lit(row, device, bits);
            }
        }
        return false;
    }

    public static boolean mode3dLit(boolean device, int bits) {
        return lit(Row.MODE_3D, device, bits);
    }

    private static boolean lit(Row row, boolean device, int bits) {
        StabMode mode = row.mode();
        PilotSession session = PilotSession.INSTANCE;
        if (mode != null && session.isActive()) {
            return session.flightMode() == mode;
        }
        if (row == Row.MODE_3D) {
            return session.isActive() ? session.mode3dActive() : device && deviceActive(row, bits) && InputConfig.get().mode3dUnlocked;
        }
        return device ? deviceActive(row, bits) : keyboardActive(row);
    }

    private static boolean keyboardActive(Row row) {
        PilotSession session = PilotSession.INSTANCE;
        return switch (row) {
            case ARM -> session.isActive() && session.armed();
            case ACRO -> session.isActive() && session.flightMode() == StabMode.ACRO;
            case ANGLE -> session.isActive() && session.flightMode() == StabMode.ANGLE;
            case HORIZON -> session.isActive() && session.flightMode() == StabMode.HORIZON;
            case ALT_HOLD -> session.isActive() && session.altHoldRequested();
            case POS_HOLD -> session.isActive() && session.posHoldRequested();
            default -> row.function != null && RadioState.INSTANCE.isActive(row.function);
        };
    }

    private static boolean deviceActive(Row row, int bits) {
        if (row.function != null) {
            return SwitchEvaluator.isSet(bits, row.function);
        }
        return !SwitchEvaluator.isSet(bits, SwitchFunction.ANGLE) && !SwitchEvaluator.isSet(bits, SwitchFunction.HORIZON);
    }

    private void updateListening(DeviceProfile profile, float[] axes, boolean[] buttons, boolean pad) {
        SwitchDetector current = this.detector;
        SwitchFunction function = this.listening;
        if (current == null || function == null) {
            return;
        }
        SwitchAssignment binding = current.update(axes, buttons, Util.getNanos());
        if (binding == null) {
            return;
        }
        ReceiverWizardScreen.applyButtonDefaults(function, binding, pad);
        profile.setBinding(function, binding);
        this.selected = Row.of(function);
        this.listening = null;
        this.detector = null;
        this.evaluator.reset();
        this.markChanged();
        this.rebuildLater();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 1) {
            return false;
        }
        int row = this.rowAt(event.x(), event.y());
        if (row < 0) {
            return false;
        }
        Row entry = this.rows()[row];
        SwitchFunction function = entry.function;
        if (function != null && function.command() && doubleClick && this.listening == null) {
            this.select(entry);
            this.editCommand(function);
            return true;
        }
        RawInput raw = this.raw();
        DeviceProfile profile = this.profile(raw);
        if (raw != null && profile != null && function != null && !function.command() && this.listening == null) {
            SwitchAssignment binding = profile.binding(function);
            Font font = this.host.font();
            int barX0 = this.barX0(font);
            int barX1 = this.areaX1(true);
            if (binding.source == SwitchSource.AXIS && barX0 + 20 < barX1 && event.x() >= barX0 - 3 && event.x() <= barX1 + 3) {
                float v = InputGui.rangeValueAt(event.x(), barX0, barX1);
                float low = binding.activeLow();
                float high = binding.activeHigh();
                this.dragRow = row;
                this.dragHigh = Math.abs(v - high) < Math.abs(v - low) || (low == high && v > high);
                this.applyDrag(binding, v);
            }
        }
        this.select(entry);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragRow < 0) {
            return false;
        }
        RawInput raw = this.raw();
        DeviceProfile profile = this.profile(raw);
        if (raw == null || profile == null) {
            this.dragRow = -1;
            return false;
        }
        Row[] rows = this.rows();
        SwitchFunction dragged = this.dragRow < rows.length ? rows[this.dragRow].function : null;
        SwitchAssignment binding = dragged == null ? null : profile.binding(dragged);
        if (binding == null || binding.source != SwitchSource.AXIS) {
            this.dragRow = -1;
            return false;
        }
        this.applyDrag(binding, InputGui.rangeValueAt(event.x(), this.barX0(this.host.font()), this.areaX1(true)));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragRow < 0) {
            return false;
        }
        this.dragRow = -1;
        return true;
    }

    private void applyDrag(SwitchAssignment binding, float value) {
        if (this.dragHigh) {
            binding.setActiveHigh(value);
        } else {
            binding.setActiveLow(value);
        }
        this.markChanged();
    }

    @Override
    public boolean cancelAction() {
        for (MicrosField field : this.rangeFields) {
            if (field.isEditing()) {
                field.cancelEdit();
                return true;
            }
        }
        if (this.listening != null) {
            this.stopListening();
            return true;
        }
        return false;
    }

    @Override
    public void resetDefaults() {
        RawInput raw = this.raw();
        DeviceProfile profile = this.profile(raw);
        if (this.page == Page.COMMANDS) {
            this.resetCommands(profile);
            return;
        }
        if (raw == null || profile == null) {
            return;
        }
        DeviceProfile saved = profile.copy();
        DeviceProfile defaults = ProfileDefaults.reset(profile, raw.gamepadCapable(), raw.axisCount());
        profile.copySwitchesFrom(defaults);
        profile.moveSwitchesOffSticks(defaults.stickAxes());
        profile.keepCommandsFrom(saved);
        this.listening = null;
        this.detector = null;
        this.evaluator.reset();
        this.markChanged();
        this.rebuild();
    }

    private void resetCommands(@Nullable DeviceProfile profile) {
        if (profile != null) {
            for (SwitchFunction function : SwitchFunction.commands()) {
                profile.setBinding(function, null);
                profile.setCommand(function, null);
            }
        } else {
            this.working().keyboard.commands.clear();
        }
        this.listening = null;
        this.detector = null;
        this.evaluator.reset();
        this.markChanged();
        this.rebuild();
    }

    private enum Page {
        FLIGHT,
        COMMANDS
    }

    private enum Row {
        ARM(SwitchFunction.ARM, KeyDefaults.ARM),
        ACRO(null, KeyDefaults.MODE_ACRO),
        ANGLE(SwitchFunction.ANGLE, KeyDefaults.MODE_ANGLE),
        HORIZON(SwitchFunction.HORIZON, KeyDefaults.MODE_HORIZON),
        MODE_3D(SwitchFunction.MODE_3D, KeyDefaults.MODE_3D),
        ALT_HOLD(SwitchFunction.ALT_HOLD, KeyDefaults.ALT_HOLD),
        POS_HOLD(SwitchFunction.POS_HOLD, KeyDefaults.POS_HOLD),
        RESCUE(SwitchFunction.RESCUE, KeyDefaults.RESCUE),
        FLIP_OVER(SwitchFunction.FLIP_OVER, KeyDefaults.FLIP_UPRIGHT),
        SET_HOME(SwitchFunction.SET_HOME, KeyDefaults.SET_HOME),
        BEEPER(SwitchFunction.BEEPER, KeyDefaults.BEEPER),
        RACE_RESTART(SwitchFunction.RACE_RESTART, KeyDefaults.RACE_RESTART),
        RECORD(SwitchFunction.RECORD, KeyDefaults.ACTION_CAM_RECORD),
        LEDS_OFF(SwitchFunction.LEDS_OFF, KeyDefaults.LEDS),
        CARGO_DROP(SwitchFunction.CARGO_DROP, KeyDefaults.CARGO_DROP),
        BLACKBOX(SwitchFunction.BLACKBOX, KeyDefaults.BLACKBOX),
        COMMAND_1(SwitchFunction.COMMAND_1, KeyDefaults.COMMANDS.get(0)),
        COMMAND_2(SwitchFunction.COMMAND_2, KeyDefaults.COMMANDS.get(1)),
        COMMAND_3(SwitchFunction.COMMAND_3, KeyDefaults.COMMANDS.get(2)),
        COMMAND_4(SwitchFunction.COMMAND_4, KeyDefaults.COMMANDS.get(3)),
        COMMAND_5(SwitchFunction.COMMAND_5, KeyDefaults.COMMANDS.get(4)),
        COMMAND_6(SwitchFunction.COMMAND_6, KeyDefaults.COMMANDS.get(5)),
        COMMAND_7(SwitchFunction.COMMAND_7, KeyDefaults.COMMANDS.get(6)),
        COMMAND_8(SwitchFunction.COMMAND_8, KeyDefaults.COMMANDS.get(7));

        private final @Nullable SwitchFunction function;
        private final KeyDefaults.Entry key;

        Row(@Nullable SwitchFunction function, KeyDefaults.Entry key) {
            this.function = function;
            this.key = key;
        }

        static Row of(SwitchFunction function) {
            for (Row row : values()) {
                if (row.function == function) {
                    return row;
                }
            }
            return ARM;
        }

        private String id() {
            return this.function != null && this.function.command() ? "command" : this.name().toLowerCase(Locale.ROOT);
        }

        Page page() {
            return this.function != null && this.function.command() ? Page.COMMANDS : Page.FLIGHT;
        }

        @Nullable StabMode mode() {
            return switch (this) {
                case ACRO -> StabMode.ACRO;
                case ANGLE -> StabMode.ANGLE;
                case HORIZON -> StabMode.HORIZON;
                default -> null;
            };
        }

        Component title() {
            return Component.translatable(this.function != null ? this.function.translationKey() : "propwash.input.switch.acro");
        }

        Component effect(boolean device) {
            if (device && this.function == null) {
                return Component.translatable("propwash.input.modes.acro_device");
            }
            return Component.translatable("propwash.input.modes.effect." + this.id());
        }

        Component description() {
            return Component.translatable("propwash.input.modes.desc." + this.id());
        }

        @Nullable KeyMapping mapping() {
            return KeyMapping.get(this.key.mappingName());
        }

        Component keyLabel() {
            KeyMapping mapping = this.mapping();
            if (mapping == null || mapping.isUnbound()) {
                return Component.translatable("propwash.input.key.unbound");
            }
            return KeyNames.of(mapping);
        }

        Component actionLabel() {
            return Component.translatable(this.key.mappingName());
        }
    }
}
