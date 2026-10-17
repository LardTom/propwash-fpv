package de.lardtom.propwash.race.client;

import de.lardtom.propwash.race.AssistChecklist;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GatePlacement;
import de.lardtom.propwash.race.RaceLine;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.StepSlider;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

public final class RaceConfigTab extends ConfigTab {
    public static final int TWO_COLUMN_WIDTH = 2 * Metrics.COLUMN_MIN + Metrics.GAP;
    private static final int SLIDER_MAX_LAPS = 20;
    private static final int ROW = Metrics.ROW_HEIGHT;
    private static final int WIDGET_HEIGHT = Metrics.CONTROL_HEIGHT;
    private static final int GAP = Metrics.GAP;
    private static final int HEADER_HEIGHT = Metrics.SECTION_HEIGHT;
    private static final int LINE_HEIGHT = Metrics.LINE_HEIGHT;
    private static final int TOOLTIP_WIDTH = 260;
    private static final int DOT_INDENT = 9;
    private static final long CHECK_MILLIS = 250L;

    private RaceConfig working;
    private final List<FormattedCharSequence> helpLines = new ArrayList<>();
    private boolean helpTruncated;
    private int helpTop;
    private int helpBottom;
    private int checkTop;
    private AssistPreflight.@Nullable Result check;
    private long checkAt;
    private int checkRows;

    @Override
    public String id() {
        return "race";
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.race.config.title");
    }

    @Override
    public int order() {
        return 600;
    }

    RaceConfig working() {
        if (this.working == null) {
            this.working = RaceConfig.CONFIG.get().copy();
        }
        return this.working;
    }

    private int innerWidth() {
        return Math.max(40, this.width);
    }

    @Override
    protected void build() {
        RaceConfig config = this.working();
        int columns = this.width >= TWO_COLUMN_WIDTH ? 2 : 1;
        int columnWidth = Metrics.columnWidth(this.width, columns);
        List<AbstractWidget> race = new ArrayList<>();
        List<AbstractWidget> display = new ArrayList<>();
        StepSlider laps = this.slider(columnWidth, 0, SLIDER_MAX_LAPS, Math.min(config.laps, SLIDER_MAX_LAPS),
                v -> v == 0 ? Component.translatable("propwash.race.config.laps_endless") : Component.translatable("propwash.race.config.laps", v),
                v -> this.working().laps = v);
        laps.setTooltip(Tooltip.create(Component.translatable("propwash.race.config.laps.tooltip")));
        race.add(laps);
        StepSlider seconds = this.slider(columnWidth, 1, 10, config.countdownSeconds,
                v -> Component.translatable("propwash.race.config.countdown_seconds", v), v -> this.working().countdownSeconds = v);
        seconds.setTooltip(Tooltip.create(Component.translatable("propwash.race.config.countdown_seconds.tooltip")));
        seconds.active = config.countdown;
        race.add(this.toggle(columnWidth, "countdown", config.countdown, v -> {
            this.working().countdown = v;
            seconds.active = v;
        }));
        race.add(seconds);
        display.add(this.toggle(columnWidth, "start_hint", config.startHint, v -> this.working().startHint = v));
        display.add(this.toggle(columnWidth, "messages", config.showMessages, v -> this.working().showMessages = v));
        race.add(this.toggle(columnWidth, "start_lights", config.startLights, v -> this.working().startLights = v));
        display.add(this.toggle(columnWidth, "ghost", config.ghost, v -> this.working().ghost = v));
        display.add(this.toggle(columnWidth, "record_ghost", config.recordGhost, v -> this.working().recordGhost = v));
        StepSlider lineGates = this.slider(columnWidth, RaceLine.MIN_AHEAD, RaceLine.MAX_AHEAD, config.raceLineGates,
                v -> Component.translatable(v == 1 ? "propwash.race.config.race_line_gates.one" : "propwash.race.config.race_line_gates", v),
                v -> this.working().raceLineGates = v);
        lineGates.setTooltip(Tooltip.create(Component.translatable("propwash.race.config.race_line_gates.tooltip")));
        lineGates.active = config.raceLine;
        race.add(this.toggle(columnWidth, "race_line", config.raceLine, v -> {
            this.working().raceLine = v;
            lineGates.active = v;
        }));
        race.add(lineGates);
        display.add(this.toggle(columnWidth, "report", config.reportResults, v -> this.working().reportResults = v));
        List<Float> steps = new ArrayList<>();
        for (float step : GatePlacement.STEPS) {
            steps.add(step);
        }
        CycleButton<Float> rotateStep = CycleButton.<Float>builder(v -> Component.translatable("propwash.race.config.rotate_step.value",
                        GateMounting.degrees(v)), GatePlacement.validStep(config.gateRotateStep))
                .withValues(steps)
                .create(0, 0, columnWidth, WIDGET_HEIGHT, Component.translatable("propwash.race.config.rotate_step"), (b, v) -> {
                    this.working().gateRotateStep = v;
                    this.host.changed();
                });
        rotateStep.setTooltip(Tooltip.create(Component.translatable("propwash.race.config.rotate_step.tooltip", GateMountKeys.keyName())));
        display.add(rotateStep);
        FlatButton board = new FlatButton(0, 0, columnWidth, WIDGET_HEIGHT, Component.translatable("propwash.race.config.leaderboard"),
                FlatButton.Style.NORMAL, () -> LeaderboardScreen.open(this.host.screen()));
        board.setTooltip(Tooltip.create(Component.translatable("propwash.race.config.leaderboard.tooltip")));
        race.add(board);
        int rows = columns == 2 ? Math.max(race.size(), display.size()) : race.size() + display.size();
        int row = ROW;
        int widgetsHeight = rows * row - (row - WIDGET_HEIGHT);
        this.layoutHelp(Integer.MAX_VALUE / 2);
        int top = this.y + HEADER_HEIGHT + this.helpLines.size() * LINE_HEIGHT + (this.helpLines.isEmpty() ? 0 : GAP);
        int left = this.x;
        this.checkTop = top + widgetsHeight + 2 * GAP;
        this.reportBottom(this.checkTop + HEADER_HEIGHT + LINE_HEIGHT);
        if (columns == 2) {
            this.column(race, left, top, row);
            this.column(display, left + columnWidth + GAP, top, row);
        } else {
            List<AbstractWidget> all = new ArrayList<>(race);
            all.addAll(display);
            this.column(all, left, top, row);
        }
    }

    private void column(List<AbstractWidget> widgets, int left, int top, int row) {
        for (int i = 0; i < widgets.size(); i++) {
            AbstractWidget widget = widgets.get(i);
            widget.setPosition(left, top + i * row);
            this.host.addWidget(widget);
        }
    }

    private void layoutHelp(int availableHeight) {
        this.helpLines.clear();
        Font font = this.host.font();
        int width = this.innerWidth();
        List<FormattedText> lines = font.splitIgnoringLanguage(Component.translatable("propwash.race.config.help"), width);
        int maxLines = Math.max(0, availableHeight / LINE_HEIGHT);
        this.helpTruncated = lines.size() > maxLines;
        int shown = Math.min(lines.size(), maxLines);
        Language language = Language.getInstance();
        for (int i = 0; i < shown; i++) {
            FormattedText line = lines.get(i);
            if (this.helpTruncated && i == shown - 1) {
                int room = Math.max(0, width - font.width(CommonComponents.ELLIPSIS));
                line = FormattedText.composite(font.substrByWidth(line, room), CommonComponents.ELLIPSIS);
            }
            this.helpLines.add(language.getVisualOrder(line));
        }
        this.helpTop = this.y + HEADER_HEIGHT;
        this.helpBottom = this.helpTop + Math.max(LINE_HEIGHT, shown * LINE_HEIGHT);
    }

    private CycleButton<Boolean> toggle(int width, String key, boolean value, Consumer<Boolean> setter) {
        CycleButton<Boolean> button = CycleButton.onOffBuilder(value).create(0, 0, width, WIDGET_HEIGHT,
                Component.translatable("propwash.race.config." + key), (b, v) -> {
                    setter.accept(v);
                    this.host.changed();
                });
        button.setTooltip(Tooltip.create(Component.translatable("propwash.race.config." + key + ".tooltip")));
        return button;
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Font font = this.host.font();
        int left = this.x;
        ThemeDraw.sectionTitle(graphics, font, Component.translatable("propwash.race.config.header"), left, this.y, this.innerWidth());
        int lineY = this.helpTop;
        if (this.helpTruncated) {
            ThemeStats.cut(Component.translatable("propwash.race.config.help").getString(), this.helpLines.size() + " Zeilen");
        }
        for (FormattedCharSequence line : this.helpLines) {
            ThemeText.text(graphics, font, line, left, lineY, Palette.TEXT_MUTED);
            lineY += LINE_HEIGHT;
        }
        this.reportBottom(this.extractCheck(graphics, font, left));
        boolean overHelp = mouseX >= left && mouseX < left + this.innerWidth() && mouseY >= this.y && mouseY < this.helpBottom;
        if (this.helpTruncated && overHelp) {
            ThemeText.tooltipWrapped(graphics, font, Component.translatable("propwash.race.config.help"), TOOLTIP_WIDTH, mouseX, mouseY);
        }
    }

    public AssistPreflight.Result assistCheck() {
        long now = Util.getMillis();
        AssistPreflight.Result result = this.check;
        if (result == null || now - this.checkAt >= CHECK_MILLIS || now < this.checkAt) {
            result = AssistPreflight.check(Minecraft.getInstance());
            this.check = result;
            this.checkAt = now;
        }
        return result;
    }

    public int checkTop() {
        return this.checkTop;
    }

    public int checkRowsDrawn() {
        return this.checkRows;
    }

    private int extractCheck(GuiGraphicsExtractor graphics, Font font, int left) {
        AssistPreflight.Result result = this.assistCheck();
        int width = this.innerWidth();
        int y = this.checkTop;
        ThemeDraw.sectionTitle(graphics, font, Component.translatable("propwash.race.preflight.title"), left, y, width);
        y += HEADER_HEIGHT;
        y += ThemeText.wrapped(graphics, font, Component.translatable("propwash.race.preflight.intro"), left, y, width, Palette.TEXT_FAINT) + 2;
        y += ThemeText.wrapped(graphics, font, result.summary(), left, y, width, result.clean() ? Palette.SUCCESS : Palette.WARNING) + GAP / 2;
        int labelWidth = 0;
        for (AssistPreflight.Row row : result.rows()) {
            labelWidth = Math.max(labelWidth, font.width(row.label()));
        }
        labelWidth = Math.min(labelWidth, Math.max(40, width / 3));
        int textLeft = left + DOT_INDENT + labelWidth + GAP;
        int textWidth = Math.max(40, left + width - textLeft);
        int drawn = 0;
        for (AssistPreflight.Row row : result.rows()) {
            int color = switch (row.status()) {
                case OK -> Palette.SUCCESS;
                case FLAGGED -> Palette.WARNING;
                case INFO -> Palette.TEXT_FAINT;
            };
            ThemeDraw.dot(graphics, left + 3, y + 3, color);
            int labelHeight = ThemeText.wrapped(graphics, font, row.label(), left + DOT_INDENT, y, labelWidth, Palette.TEXT);
            int height = ThemeText.wrapped(graphics, font, row.detail(), textLeft, y, textWidth,
                    row.status() == AssistChecklist.Status.FLAGGED ? Palette.WARNING : Palette.TEXT_MUTED);
            y += Math.max(LINE_HEIGHT, Math.max(labelHeight, height)) + 2;
            drawn++;
        }
        this.checkRows = drawn;
        return y;
    }

    @Override
    public void resetDefaults() {
        this.working = new RaceConfig();
        this.host.changed();
        this.host.rebuild();
    }

    @Override
    public void save() {
        if (this.working != null) {
            RaceConfig.CONFIG.set(this.working.copy());
        }
    }

    private StepSlider slider(int width, int min, int max, int initial, IntFunction<Component> message, IntConsumer onChange) {
        return new StepSlider(0, 0, width, WIDGET_HEIGHT, min, max, 1.0, initial, v -> message.apply((int) Math.round(v)), v -> {
            onChange.accept((int) Math.round(v));
            this.host.changed();
        });
    }
}
