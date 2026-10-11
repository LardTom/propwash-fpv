package de.lardtom.propwash.ui.client.tab;

import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.PidProfile;
import de.lardtom.propwash.sim.ThrottleCurve;
import de.lardtom.propwash.ui.TuneKeys;
import de.lardtom.propwash.ui.TunePresets;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.GraphPanel;
import de.lardtom.propwash.ui.client.widget.ScrollPanel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class PidTab extends UiTab {
    public static final String ID = "pid";

    private static final List<String> MODES = List.of(TuneKeys.ANGLE_STRENGTH, TuneKeys.ANGLE_LIMIT, TuneKeys.HORIZON_STRENGTH);
    private static final List<String> CONTROLLER = List.of(TuneKeys.ITERM_RELAX, TuneKeys.ANTI_GRAVITY, TuneKeys.TPA_MODE, TuneKeys.TPA_RATE,
            TuneKeys.TPA_BREAKPOINT);
    private static final List<String> MOTORS = List.of(TuneKeys.AIRMODE, TuneKeys.MOTOR_IDLE, TuneKeys.THROTTLE_MID, TuneKeys.THROTTLE_EXPO,
            TuneKeys.THROTTLE_LIMIT_TYPE, TuneKeys.THROTTLE_LIMIT, TuneKeys.THRUST_LINEAR, TuneKeys.VBAT_SAG);
    private static final List<String> FILTERS = List.of(TuneKeys.PID_LOOP, TuneKeys.GYRO_LPF, TuneKeys.DTERM_LPF1, TuneKeys.DTERM_LPF2,
            TuneKeys.RC_SMOOTHING);

    private static final List<String> ALT_HOLD = List.of(TuneKeys.HOLD_ALT_P, TuneKeys.HOLD_ALT_I, TuneKeys.HOLD_ALT_D,
            TuneKeys.HOLD_CLIMB_RATE, TuneKeys.HOLD_THROTTLE_DEADBAND, TuneKeys.HOLD_MID_THROTTLE);
    private static final List<String> POS_HOLD = List.of(TuneKeys.HOLD_POS_P, TuneKeys.HOLD_POS_I, TuneKeys.HOLD_POS_D,
            TuneKeys.HOLD_MAX_ANGLE, TuneKeys.HOLD_STICK_DEADBAND, TuneKeys.HOLD_MIN_SATS);

    private static final List<String> GROUPED = Stream.of(MODES, CONTROLLER, MOTORS, FILTERS, ALT_HOLD, POS_HOLD).flatMap(List::stream).toList();

    private final List<Heading> headings = new ArrayList<>();
    private final PresetPanel presets = new PresetPanel(this, TunePresets.Section.PID);
    private int groupLabelWidth;
    private int tableTop;
    private int tableLabelWidth;
    private int columnWidth;
    private int sourceTop;
    private FlatButton resetButton;

    private record Heading(Component title, int contentY) {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.ui.tab.pid");
    }

    @Override
    public Storage storage() {
        return Storage.DRONE;
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    protected void build() {
        this.headings.clear();
        ScrollPanel panel = this.beginScroll();
        int w = panel.innerWidth();
        int left = this.x;
        int cy = 0;

        this.headings.add(new Heading(Component.translatable("propwash.ui.pid.section.gains"), cy));
        cy += 14;
        panel.stop(cy);
        this.sourceTop = cy;
        Component resetLabel = Component.translatable("propwash.ui.pid.reset_derived");
        int resetWidth = Math.min(w, this.font().width(resetLabel) + 14);
        int badgeNeed = this.font().width(Component.translatable("propwash.ui.pid.source")) + 6 + 8 + Metrics.GAP
                + Math.max(this.font().width(Component.translatable("propwash.ui.pid.source.manual")),
                this.font().width(Component.translatable("propwash.ui.pid.source.derived")));
        boolean below = badgeNeed + resetWidth > w;
        if (below) {
            cy += ROW + 2;
        }
        FlatButton reset = this.resetButton = this.add(new FlatButton(below ? left : left + w - resetWidth, this.top() + cy, resetWidth, FIELD, resetLabel,
                FlatButton.Style.NORMAL, () -> this.tune().resetPidToDerived()));
        reset.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("propwash.ui.pid.reset_derived.tip")));
        cy += ROW + 4;

        panel.stop(cy);
        this.tableTop = cy;
        this.tableLabelWidth = Math.max(40, Math.min(60, w / 7));
        this.columnWidth = (w - this.tableLabelWidth) / 4;
        cy += 11;
        for (Axis axis : Axis.values()) {
            for (int t = 0; t < TuneKeys.PID_TERMS.size(); t++) {
                String key = TuneKeys.pidKey(axis, TuneKeys.PID_TERMS.get(t));
                int cx = left + this.tableLabelWidth + t * this.columnWidth;
                this.spinner(key, cx + 1, this.top() + cy, this.columnWidth - 4).active = this.tune().live();
            }
            panel.stop(cy);
            cy += ROW;
        }
        panel.stop(cy);
        int hintHeight = Math.max(ThemeText.wrappedHeight(this.font(), Component.translatable("propwash.ui.pid.derived_hint"), w),
                ThemeText.wrappedHeight(this.font(), Component.translatable("propwash.ui.pid.template_hint"), w));
        cy += Math.max(18, hintHeight + 8);

        int colWidth = Metrics.columns(w) >= 2 ? (w - 10) / 2 : w;
        this.groupLabelWidth = this.labelWidth(GROUPED, colWidth);
        cy = this.group(Component.translatable("propwash.ui.pid.section.modes"), MODES, left, cy, w);
        cy = this.group(Component.translatable("propwash.ui.pid.section.controller"), CONTROLLER, left, cy, w);
        cy = this.group(Component.translatable("propwash.ui.pid.section.motors"), MOTORS, left, cy, w);
        Component curveTitle = Component.translatable("propwash.ui.pid.section.throttle_curve");
        panel.stop(cy);
        this.headings.add(new Heading(curveTitle, cy));
        cy += 14;
        int graphHeight = Math.max(40, Math.min(70, w / 5));
        this.add(new GraphPanel(left, this.top() + cy, w, graphHeight, curveTitle, 0.0, 1.0, 0.25, () -> 0.0, () -> 100.0, () -> 25.0, "%",
                this::throttleSeries, this::throttleMarkers));
        cy += graphHeight + 8;
        cy = this.group(Component.translatable("propwash.ui.pid.section.filters"), FILTERS, left, cy, w);
        cy = this.group(Component.translatable("propwash.ui.pid.section.alt_hold"), ALT_HOLD, left, cy, w);
        cy = this.group(Component.translatable("propwash.ui.pid.section.pos_hold"), POS_HOLD, left, cy, w);
        panel.stop(cy);
        this.headings.add(new Heading(Component.translatable("propwash.ui.rates.section.presets"), cy));
        cy += 14;
        int presetLabel = Math.min(w / 2, this.font().width(Component.translatable("propwash.ui.rates.preset")) + 8);
        int presetTop = cy;
        cy = this.presets.build(left, cy, w, Math.max(presetLabel, this.groupLabelWidth));
        for (int row = 0; presetTop + row * ROW < cy; row++) {
            panel.stop(presetTop + row * ROW);
        }
        panel.stop(cy);
        cy += 6;
        this.endScroll(cy);
    }

    private int group(Component title, List<String> keys, int left, int cy, int w) {
        if (this.scroll != null) {
            this.scroll.stop(cy);
        }
        this.headings.add(new Heading(title, cy));
        cy += 14;
        boolean twoColumns = Metrics.columns(w) >= 2;
        int colWidth = twoColumns ? (w - 10) / 2 : w;
        int labelWidth = this.groupLabelWidth;
        for (int i = 0; i < keys.size(); i++) {
            int col = twoColumns ? i % 2 : 0;
            int row = twoColumns ? i / 2 : i;
            int x = left + col * (colWidth + 10);
            int y = this.top() + cy + row * ROW;
            String key = keys.get(i);
            switch (TuneKeys.param(key).kind()) {
                case ENUM -> this.choice(key, x, y, colWidth, labelWidth, () -> { });
                case BOOL -> this.toggle(key, x, y, colWidth, labelWidth);
                default -> this.slider(key, x, y, colWidth, labelWidth);
            }
        }
        int rows = twoColumns ? (keys.size() + 1) / 2 : keys.size();
        if (this.scroll != null) {
            for (int row = 0; row <= rows; row++) {
                this.scroll.stop(cy + row * ROW);
            }
        }
        return cy + rows * ROW + 6;
    }

    private PidProfile profile() {
        return this.tune().config().pid;
    }

    private List<GraphPanel.Series> throttleSeries() {
        PidProfile p = this.profile();
        return List.of(
                new GraphPanel.Series(Component.translatable("propwash.ui.pid.throttle_linear").getString(), Palette.BORDER_STRONG, s -> s * 100.0),
                new GraphPanel.Series(Component.translatable("propwash.ui.pid.throttle_out").getString(), Palette.ACCENT,
                        s -> ThrottleCurve.apply(p, s) * 100.0));
    }

    private List<GraphPanel.Marker> throttleMarkers() {
        RadioState radio = RadioState.INSTANCE;
        if (!radio.connected()) {
            return List.of();
        }
        double stick = radio.sticks().throttle();
        if (!Double.isFinite(stick)) {
            return List.of();
        }
        return List.of(new GraphPanel.Marker(Palette.TEXT, stick, ThrottleCurve.apply(this.profile(), stick) * 100.0));
    }

    @Override
    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int left = this.x;
        int w = this.innerWidth();
        int top = this.top();
        boolean manual = this.tune().manualPid();
        if (this.resetButton != null) {
            this.resetButton.active = manual;
            this.resetButton.visible = this.tune().live() && this.resetButton.getY() + FIELD > this.y && this.resetButton.getY() < this.y + this.height;
        }
        int sy = top + this.sourceTop + 3;
        ThemeText.text(g, this.font(), Component.translatable("propwash.ui.pid.source"), left, sy, Palette.TEXT_MUTED);
        Component badge = Component.translatable(manual ? "propwash.ui.pid.source.manual" : "propwash.ui.pid.source.derived");
        int bx = left + this.font().width(Component.translatable("propwash.ui.pid.source")) + 6;
        ThemeDraw.badge(g, this.font(), badge, bx, sy - 3, sy + 10, manual ? Palette.ACCENT : Palette.SUCCESS_BG,
                manual ? Palette.ACCENT_TEXT : Palette.SUCCESS);

        int ty = top + this.tableTop;
        for (int t = 0; t < TuneKeys.PID_TERMS.size(); t++) {
            int cx = left + this.tableLabelWidth + t * this.columnWidth + (this.columnWidth - 4) / 2 + 1;
            String header = Component.translatable("propwash.ui.pid.term." + TuneKeys.PID_TERMS.get(t)).getString();
            ThemeText.textCentered(g, this.font(), header, cx, ty, Palette.TEXT_MUTED);
        }
        g.fill(left, ty + 9, left + w, ty + 10, Palette.BORDER);
        for (int a = 0; a < 3; a++) {
            int ry = ty + 11 + a * ROW;
            g.fill(left, ry, left + 2, ry + FIELD, Palette.axisColor(a));
            ThemeText.fitted(g, this.font(), axisLabel(a), left + 5, ry + 3, this.tableLabelWidth - 8, Palette.TEXT);
        }
        if (!manual || !this.tune().live()) {
            int ny = ty + 11 + 3 * ROW + 1;
            String hint = this.tune().live() ? "propwash.ui.pid.derived_hint" : "propwash.ui.pid.template_hint";
            ThemeText.wrapped(g, this.font(), Component.translatable(hint), left, ny, w, Palette.TEXT_FAINT);
        }
        for (Heading h : this.headings) {
            this.section(g, h.title(), left, top + h.contentY(), w);
        }
        this.extractScrollbar(g);
    }

    public PresetPanel presets() {
        return this.presets;
    }

    @Override
    public void tick() {
        this.presets.tick();
    }

    @Override
    public void resetDefaults() {
        this.tune().resetWhere(k -> k.startsWith("pid.") && !k.equals(TuneKeys.ARM_MAX_ANGLE) || k.startsWith("filters.") || k.startsWith("hold."));
    }
}
