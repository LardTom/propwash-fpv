package de.lardtom.propwash.ui.client.tab;

import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.RateCurve;
import de.lardtom.propwash.sim.RatesType;
import de.lardtom.propwash.ui.RateGraph;
import de.lardtom.propwash.ui.TuneKeys;
import de.lardtom.propwash.ui.TunePresets;
import de.lardtom.propwash.ui.client.TuneModel;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.GraphPanel;
import de.lardtom.propwash.ui.client.widget.ScrollPanel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RatesTab extends UiTab {
    private static final int SECTION = Metrics.SECTION_HEIGHT;
    public static final String ID = "rates";

    private static final List<String> BETAFLIGHT_FIELDS = List.of("rcRate", "superRate", "rcExpo");
    private static final List<String> ACTUAL_FIELDS = List.of("centerRateDps", "maxRateDps", "actualExpo");

    private final PresetPanel presets = new PresetPanel(this, TunePresets.Section.RATES);
    private @Nullable RatesType builtFor;
    private int tableTop;
    private int labelWidth;
    private int columnWidth;
    private int tableWidth;
    private int graphTop;
    private int graphLeft;
    private int graphSize;
    private int deadbandTop;
    private int presetTop;
    private final RateCurve[] cache = {new RateCurve(), new RateCurve(), new RateCurve()};
    private int cacheRevision = -1;
    private RatesType cacheType = RatesType.ACTUAL;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.ui.tab.rates");
    }

    @Override
    public Storage storage() {
        return Storage.DRONE;
    }

    @Override
    public int order() {
        return 310;
    }

    @Override
    protected void build() {
        TuneModel tune = this.tune();
        RatesType type = RateGraph.type(tune.working(), tune.defaults());
        this.builtFor = type;
        ScrollPanel panel = this.beginScroll();
        int w = panel.innerWidth();
        int left = this.x;
        boolean sideBySide = w >= 380;
        this.tableWidth = sideBySide ? Math.max(220, w * 3 / 5) : w;
        int cy = SECTION;
        int formLabel = Math.min(this.tableWidth / 2, Math.max(this.labelWidth(List.of(TuneKeys.RATES_TYPE, TuneKeys.RC_DEADBAND, TuneKeys.YAW_DEADBAND),
                this.tableWidth * 8 / 5), this.font().width(Component.translatable("propwash.ui.rates.preset")) + 8));

        this.choice(TuneKeys.RATES_TYPE, left, this.top() + cy, this.tableWidth, formLabel, this.host::rebuild);
        cy += ROW + 6;

        this.tableTop = cy;
        this.labelWidth = Math.max(34, Math.min(52, this.tableWidth / 6));
        this.columnWidth = (this.tableWidth - this.labelWidth) / 4;
        cy += 11;
        List<String> fields = type == RatesType.BETAFLIGHT ? BETAFLIGHT_FIELDS : ACTUAL_FIELDS;
        for (Axis axis : Axis.values()) {
            for (int f = 0; f < fields.size(); f++) {
                int cx = left + this.labelWidth + f * this.columnWidth;
                this.spinner(TuneKeys.rateKey(axis, fields.get(f)), cx + 1, this.top() + cy, this.columnWidth - 4);
            }
            cy += ROW;
        }
        cy += 8;

        int graphWidth;
        if (sideBySide) {
            this.graphLeft = left + this.tableWidth + 10;
            this.graphTop = SECTION + 2;
            graphWidth = w - this.tableWidth - 10;
            this.graphSize = Math.max(60, Math.min(graphWidth, this.height - 36));
        } else {
            this.graphLeft = left;
            this.graphTop = cy + SECTION + 2;
            graphWidth = w;
            this.graphSize = Math.max(70, Math.min(Math.min(120, w / 2), this.height - this.graphTop - 26));
            cy = this.graphTop + this.graphSize + 26;
        }
        this.add(new GraphPanel(this.graphLeft, this.top() + this.graphTop, graphWidth, this.graphSize,
                Component.translatable("propwash.ui.rates.graph"), -1.0, 1.0, 0.25, this::scale, () -> RateGraph.gridStep(this.scale()), "°/s",
                this::series, this::markers));

        this.deadbandTop = cy;
        cy += 14;
        this.slider(TuneKeys.RC_DEADBAND, left, this.top() + cy, this.tableWidth, formLabel);
        cy += ROW;
        this.slider(TuneKeys.YAW_DEADBAND, left, this.top() + cy, this.tableWidth, formLabel);
        cy += ROW + 8;

        this.presetTop = cy;
        cy += 14;
        cy = this.presets.build(left, cy, this.tableWidth, formLabel) + 6;

        int contentHeight = sideBySide ? Math.max(cy, this.graphTop + this.graphSize + 24) : cy;
        this.endScroll(contentHeight);
    }

    public PresetPanel presets() {
        return this.presets;
    }

    private void refreshCache() {
        TuneModel tune = this.tune();
        if (this.cacheRevision == tune.revision()) {
            return;
        }
        this.cacheRevision = tune.revision();
        this.cacheType = RateGraph.type(tune.working(), tune.defaults());
        for (Axis axis : Axis.values()) {
            this.cache[axis.ordinal()] = RateGraph.axisRates(tune.working(), tune.defaults(), axis);
        }
    }

    private double scale() {
        this.refreshCache();
        double max = 0.0;
        for (RateCurve r : this.cache) {
            max = Math.max(max, RateGraph.maxRate(this.cacheType, r));
        }
        return RateGraph.niceScale(max);
    }

    private List<GraphPanel.Series> series() {
        this.refreshCache();
        List<GraphPanel.Series> out = new ArrayList<>(3);
        for (int a = 2; a >= 0; a--) {
            RateCurve r = this.cache[a];
            RatesType type = this.cacheType;
            out.add(new GraphPanel.Series(axisLabel(a), Palette.axisColor(a), s -> RateGraph.rate(type, r, s)));
        }
        return out;
    }

    private List<List<Integer>> curveGroups() {
        this.refreshCache();
        List<List<Integer>> groups = new ArrayList<>();
        boolean[] used = new boolean[3];
        for (int a = 0; a < 3; a++) {
            if (used[a]) {
                continue;
            }
            List<Integer> group = new ArrayList<>();
            for (int b = a; b < 3; b++) {
                if (!used[b] && this.sameCurve(a, b)) {
                    used[b] = true;
                    group.add(b);
                }
            }
            groups.add(group);
        }
        return groups;
    }

    private boolean sameCurve(int a, int b) {
        if (a == b) {
            return true;
        }
        for (int i = -10; i <= 10; i++) {
            double stick = i / 10.0;
            if (Math.abs(RateGraph.rate(this.cacheType, this.cache[a], stick) - RateGraph.rate(this.cacheType, this.cache[b], stick)) > 0.5) {
                return false;
            }
        }
        return true;
    }

    private List<GraphPanel.Marker> markers() {
        RadioState radio = RadioState.INSTANCE;
        if (!radio.connected()) {
            return List.of();
        }
        this.refreshCache();
        StickPositions sticks = radio.sticks();
        double[] values = {sticks.roll(), sticks.pitch(), sticks.yaw()};
        List<GraphPanel.Marker> out = new ArrayList<>(3);
        for (int a = 0; a < 3; a++) {
            double s = Double.isFinite(values[a]) ? values[a] : 0.0;
            out.add(new GraphPanel.Marker(Palette.axisColor(a), s, RateGraph.rate(this.cacheType, this.cache[a], s)));
        }
        return out;
    }

    @Override
    public void tick() {
        this.presets.tick();
        TuneModel tune = this.tune();
        if (this.builtFor != null && this.builtFor != RateGraph.type(tune.working(), tune.defaults())) {
            this.host.rebuild();
        }
    }

    @Override
    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        this.refreshCache();
        int left = this.x;
        int top = this.top();
        RatesType type = this.builtFor != null ? this.builtFor : this.cacheType;
        List<String> fields = type == RatesType.BETAFLIGHT ? BETAFLIGHT_FIELDS : ACTUAL_FIELDS;
        int ty = top + this.tableTop;
        boolean computedMax = fields.size() < 4 && type == RatesType.BETAFLIGHT;
        for (int f = 0; f < (computedMax ? 4 : fields.size()); f++) {
            int cx = left + this.labelWidth + f * this.columnWidth + (this.columnWidth - 4) / 2 + 1;
            String header = f < fields.size() ? Component.translatable("propwash.ui.rates.column." + fields.get(f)).getString()
                    : Component.translatable("propwash.ui.rates.column.max").getString();
            ThemeText.fittedCentered(g, this.font(), header, cx, ty, this.columnWidth - 2, Palette.TEXT_MUTED);
        }
        g.fill(left, ty + 9, left + this.tableWidth, ty + 10, Palette.BORDER);
        for (int a = 0; a < 3; a++) {
            int ry = ty + 11 + a * ROW;
            g.fill(left, ry, left + 2, ry + FIELD, Palette.axisColor(a));
            ThemeText.fitted(g, this.font(), axisLabel(a), left + 5, ry + 3, this.labelWidth - 8, Palette.TEXT);
            if (computedMax) {
                int mx0 = left + this.labelWidth + 3 * this.columnWidth + 1;
                g.fill(mx0, ry, mx0 + this.columnWidth - 4, ry + FIELD, Palette.PANEL_HEADER);
                String max = String.format(Locale.ROOT, "%.0f", RateGraph.maxRate(this.cacheType, this.cache[a]));
                ThemeText.textCentered(g, this.font(), max, mx0 + (this.columnWidth - 4) / 2, ry + 3, Palette.axisColor(a));
            }
        }
        this.section(g, Component.translatable("propwash.ui.rates.section.rates"), left, top, this.tableWidth);
        this.section(g, Component.translatable("propwash.ui.rates.section.deadband"), left, top + this.deadbandTop, this.tableWidth);
        this.section(g, Component.translatable("propwash.ui.rates.section.presets"), left, top + this.presetTop, this.tableWidth);

        int gy = top + this.graphTop;
        int graphWidth = this.graphLeft == left ? this.innerWidth() : this.innerWidth() - this.tableWidth - 10;
        this.section(g, Component.translatable("propwash.ui.rates.graph"), this.graphLeft, gy - SECTION - 2, graphWidth);
        int ly = gy + this.graphSize + 3;
        int lx = this.graphLeft;
        for (List<Integer> group : this.curveGroups()) {
            StringBuilder name = new StringBuilder();
            for (int i = 0; i < group.size(); i++) {
                if (i > 0) {
                    name.append(" = ");
                }
                name.append(axisLabel(group.get(i)));
            }
            int swatch = 8;
            List<Integer> colors = new ArrayList<>();
            for (int axis : group) {
                colors.add(Palette.axisColor(axis));
            }
            g.fill(lx, ly + 4, lx + swatch, ly + 6, GraphPanel.groupColor(colors));
            ThemeText.text(g, this.font(), name.toString(), lx + swatch + 3, ly + 1, Palette.TEXT_MUTED);
            lx += swatch + 10 + this.font().width(name.toString());
        }
        if (!RadioState.INSTANCE.connected()) {
            ThemeText.fitted(g, this.font(), Component.translatable("propwash.ui.rates.no_sticks"), this.graphLeft, ly + 13, graphWidth, Palette.TEXT_FAINT);
        }
        this.extractScrollbar(g);
    }

    @Override
    public void resetDefaults() {
        this.tune().resetWhere(k -> k.startsWith("rates."));
        this.host.rebuild();
    }
}
