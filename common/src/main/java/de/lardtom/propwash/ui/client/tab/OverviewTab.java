package de.lardtom.propwash.ui.client.tab;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.client.render.DronePreview;
import de.lardtom.propwash.content.BuildTexts;
import de.lardtom.propwash.content.Numbers;
import de.lardtom.propwash.core.PartCategory;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.sim.BuildAnalysis;
import de.lardtom.propwash.sim.BuildWarning;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.ui.SpeedUnit;
import de.lardtom.propwash.ui.client.DroneContext;
import de.lardtom.propwash.ui.client.PilotGuard;
import de.lardtom.propwash.ui.client.UiConfig;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.ScrollPanel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OverviewTab extends UiTab {
    public static final String ID = "overview";
    private static final Pattern DECIMAL_POINT = Pattern.compile("(?<=\\d)\\.(?=\\d)");

    private static final int LINE = 10;
    private static final int ROW_GAP = 10;
    private static final int PREVIEW_HEIGHT = 60;
    static final int MOTOR_ROW = 8;
    static final int MOTOR_BAR = 6;
    static final int MOTOR_GAP = 2;
    static final int MOTOR_MIN_WIDTH = 64;
    static final int TELEMETRY_TWO_COLUMNS = 260;
    static final int TELEMETRY_GAP = 10;
    static final int TELEMETRY_ROWS = 5 * LINE;

    private @Nullable DroneBuild analysedBuild;
    private @Nullable BuildAnalysis analysis;
    private int contentHeight;

    private record Row(Component label, String value, int color) {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.ui.tab.overview");
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public boolean hasDefaults() {
        return false;
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    protected void build() {
        this.beginScroll();
        this.contentHeight = this.height;
        this.endScroll(this.contentHeight);
    }

    private @Nullable BuildAnalysis analysis(DroneBuild build) {
        if (!build.equals(this.analysedBuild)) {
            this.analysedBuild = build;
            try {
                this.analysis = BuildAnalysis.of(build);
            } catch (RuntimeException e) {
                this.analysis = null;
            }
        }
        return this.analysis;
    }

    @Override
    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        DroneContext context = this.context();
        DroneBuild build = context.build();
        BuildAnalysis a = this.analysis(build);
        int w = this.innerWidth();
        int left = this.x;
        int top = this.top();
        List<Row> rows = a != null ? this.analysisRows(build, a) : List.of();
        List<Row> parts = partRows(build);
        int half = (w - 8) / 2;
        boolean twoColumns = w >= 300 && this.oneLine(rows, half - 12) && this.oneLine(parts, half - 12)
                && this.font().width(cardTitle(context)) <= half - 10;
        int colWidth = twoColumns ? half : w;
        int analysisHeight = 12 + 4 + Math.max(LINE, this.rowsHeight(rows, colWidth - 12)) + 4;
        int cardFixed = 12 + 4 + 4 + this.rowsHeight(parts, colWidth - 12) + 8;
        int used;
        if (twoColumns) {
            int analysisLeft = left + colWidth + 8;
            this.analysisPanel(g, rows, analysisLeft, top, colWidth, analysisHeight);
            int ry = top + analysisHeight + 8;
            ry = this.warnings(g, build, a, analysisLeft, ry, colWidth);
            ry = this.telemetry(g, context, analysisLeft, ry, colWidth);
            int rightHeight = ry - 8 - top;
            int cardHeight = Math.max(rightHeight, this.height - 4);
            int preview = Math.clamp(cardHeight - cardFixed, PREVIEW_HEIGHT, Math.max(PREVIEW_HEIGHT, colWidth));
            cardHeight = Math.max(rightHeight, cardFixed + preview);
            this.droneCard(g, context, build, parts, left, top, colWidth, cardHeight, preview);
            used = Math.max(cardHeight, rightHeight);
        } else {
            int preview = Math.clamp(w * 2 / 5, PREVIEW_HEIGHT, 120);
            int cardHeight = cardFixed + preview;
            this.droneCard(g, context, build, parts, left, top, w, cardHeight, preview);
            int y = top + cardHeight + 8;
            this.analysisPanel(g, rows, left, y, w, analysisHeight);
            y += analysisHeight + 8;
            y = this.warnings(g, build, a, left, y, w);
            y = this.telemetry(g, context, left, y, w);
            used = y - top;
        }
        if (this.scroll != null && used != this.contentHeight) {
            this.contentHeight = used;
            this.scroll.setContentHeight(used);
            this.scrollOffset = this.scroll.offset();
        }
        this.extractScrollbar(g);
    }

    private void analysisPanel(GuiGraphicsExtractor g, List<Row> rows, int x0, int y0, int width, int height) {
        ThemeDraw.panel(g, x0, y0, x0 + width, y0 + height);
        ThemeDraw.panelTitle(g, this.font(), Component.translatable("propwash.ui.overview.analysis"), x0, y0, x0 + width);
        int ry = y0 + 16;
        if (rows.isEmpty()) {
            ThemeText.text(g, this.font(), Component.translatable("propwash.ui.overview.no_analysis"), x0 + 6, ry, Palette.TEXT_FAINT);
        }
        for (Row row : rows) {
            ry += this.row(g, row, x0 + 6, ry, width - 12);
        }
    }

    private static Component cardTitle(DroneContext context) {
        return context.hasDrone() ? context.name() : Component.translatable("propwash.ui.overview.no_drone");
    }

    private boolean oneLine(List<Row> rows, int width) {
        for (Row row : rows) {
            if (!ThemeText.pairFits(this.font(), row.label(), Component.literal(row.value()), width, ROW_GAP)) {
                return false;
            }
        }
        return true;
    }

    private int rowsHeight(List<Row> rows, int width) {
        int height = 0;
        for (Row row : rows) {
            height += ThemeText.pairHeight(this.font(), row.label(), Component.literal(row.value()), width, ROW_GAP);
        }
        return height;
    }

    private static List<Row> partRows(DroneBuild build) {
        List<Row> rows = new ArrayList<>();
        for (PartCategory category : PartCategory.values()) {
            Component label = Component.translatable(category.translationKey());
            String part;
            try {
                part = category == PartCategory.ACCESSORY ? accessoryNames(build)
                        : shortPart(category, Component.translatable(category.get(build).translationKey()).getString());
            } catch (RuntimeException e) {
                part = "–";
            }
            rows.add(new Row(label, part, Palette.TEXT));
        }
        return rows;
    }

    private void droneCard(GuiGraphicsExtractor g, DroneContext context, DroneBuild build, List<Row> parts, int x0, int y0, int width, int height,
                           int previewHeight) {
        ThemeDraw.panel(g, x0, y0, x0 + width, y0 + height);
        Component title = cardTitle(context);
        ThemeDraw.panelTitle(g, this.font(), title, x0, y0, x0 + width);
        int py0 = y0 + 16;
        ThemeDraw.previewStage(g, x0 + 4, py0, x0 + width - 4, py0 + previewHeight);
        float yaw = (Util.getMillis() % 12000L) / 12000.0F * 360.0F;
        DronePreview.render(g, build, PartWear.PRISTINE, context.paint(), x0 + 4, py0, x0 + width - 4, py0 + previewHeight, yaw);
        if (!context.hasDrone()) {
            ThemeText.wrapped(g, this.font(), Component.translatable("propwash.ui.overview.preview_default"), x0 + 7, py0 + 3, width - 14, Palette.TEXT_FAINT);
        }
        int ly = py0 + previewHeight + 4;
        for (Row row : parts) {
            ly += this.row(g, row, x0 + 6, ly, width - 12);
        }
    }

    static String shortPart(PartCategory category, String name) {
        String key = "propwash.ui.overview.part_word." + category.name().toLowerCase(Locale.ROOT);
        if (!Language.getInstance().has(key)) {
            return name;
        }
        String word = Language.getInstance().getOrDefault(key);
        String shortened = name.replace(word, "").replaceAll("\\s{2,}", " ").trim();
        return shortened.isEmpty() ? name : shortened;
    }

    private static String accessoryNames(DroneBuild build) {
        List<String> names = new ArrayList<>();
        for (AccessoryType type : build.accessories().list()) {
            names.add(Component.translatable(type.translationKey()).getString());
        }
        return names.isEmpty() ? "–" : String.join(", ", names);
    }

    private List<Row> analysisRows(DroneBuild build, BuildAnalysis a) {
        SpeedUnit unit = UiConfig.speedUnit();
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(tr("propwash.ui.overview.dry_mass"), fmt("%.0f g", build.flight().dryMassGrams()), Palette.TEXT));
        rows.add(new Row(tr("propwash.ui.overview.takeoff_mass"), fmt("%.0f g", a.massGrams()), Palette.TEXT));
        int twColor = a.thrustToWeight() >= 4.0 ? Palette.SUCCESS : (a.thrustToWeight() >= 2.0 ? Palette.TEXT : Palette.WARNING);
        rows.add(new Row(tr("propwash.ui.overview.thrust_to_weight"), fmt("%.1f : 1", a.thrustToWeight()), twColor));
        rows.add(new Row(tr("propwash.ui.overview.max_thrust"), fmt("%.0f g", a.maxThrustPerMotorGrams()), Palette.TEXT));
        rows.add(new Row(tr("propwash.ui.overview.hover_throttle"), percent(a.hoverThrottlePercent()), Palette.TEXT));
        rows.add(new Row(tr("propwash.ui.overview.flight_time"), Component.translatable("propwash.ui.format.minutes_pair", Numbers.minutes(a.hoverFlightTimeMin()),
                Numbers.minutes(a.cruiseFlightTimeMin())).getString(), Palette.TEXT));
        rows.add(new Row(tr("propwash.ui.overview.flight_time_style"), Component.translatable("propwash.ui.format.minutes_pair",
                Numbers.minutes(a.mixedFlightTimeMin()), Numbers.minutes(a.aggressiveFlightTimeMin())).getString(), Palette.TEXT));
        rows.add(new Row(tr("propwash.ui.overview.top_speed"), unit.format(a.topSpeedAcroKmh()) + " / " + unit.format(a.topSpeedAngleKmh()), Palette.TEXT));
        rows.add(new Row(tr("propwash.ui.overview.current"), fmt("%.1f / %.1f / %.0f A", a.hoverCurrentA(), a.mixedCurrentA(), a.fullThrottleCurrentA()),
                Palette.TEXT));
        int sagColor = a.warnings().contains(BuildWarning.HEAVY_SAG) ? Palette.WARNING : Palette.TEXT;
        rows.add(new Row(tr("propwash.ui.overview.sag"), fmt("%.2f V", a.fullThrottleCellVoltage()), sagColor));
        int escColor = a.escLoadPercent() > 100.0 ? Palette.WARNING : Palette.TEXT;
        rows.add(new Row(tr("propwash.ui.overview.esc_load"), percent(a.escLoadPercent()), escColor));
        int batteryColor = a.batteryLoadPercent() > 130.0 ? Palette.WARNING : Palette.TEXT;
        rows.add(new Row(tr("propwash.ui.overview.battery_load"), percent(a.batteryLoadPercent()), batteryColor));
        rows.add(new Row(tr("propwash.ui.overview.motor_response"), fmt("%.0f ms", a.motorResponseMs()), Palette.TEXT));
        rows.add(new Row(tr("propwash.ui.overview.roll_accel"), grouped(a.rollAccelDps2()) + " °/s²", Palette.TEXT));
        rows.add(new Row(tr("propwash.ui.overview.crash_speed"), fmt("%.1f m/s", a.crashSpeedMs()), Palette.TEXT));
        return rows;
    }

    private int warnings(GuiGraphicsExtractor g, DroneBuild build, @Nullable BuildAnalysis a, int left, int y, int w) {
        List<Component> problems = new ArrayList<>();
        List<Component> hints = new ArrayList<>();
        for (BuildTexts.Line line : BuildTexts.lines(build, a, ClientWorldRules.INSTANCE.partCompatibility())) {
            (line.severity() == BuildTexts.Severity.PROBLEM ? problems : hints).add(line.text());
        }
        if (problems.isEmpty() && hints.isEmpty()) {
            return y;
        }
        int lines = 0;
        for (Component c : problems) {
            lines += ThemeText.wrappedHeight(this.font(), c, w - 22);
        }
        for (Component c : hints) {
            lines += ThemeText.wrappedHeight(this.font(), c, w - 22);
        }
        int height = 12 + 4 + lines + 2;
        ThemeDraw.panel(g, left, y, left + w, y + height);
        ThemeDraw.panelTitle(g, this.font(), Component.translatable("propwash.ui.overview.warnings"), left, y, left + w);
        int ry = y + 16;
        for (Component c : problems) {
            ThemeDraw.dot(g, left + 8, ry + 4, Palette.ERROR);
            ry += ThemeText.wrapped(g, this.font(), c, left + 14, ry, w - 22, Palette.ERROR);
        }
        for (Component c : hints) {
            ThemeDraw.dot(g, left + 8, ry + 4, Palette.WARNING);
            ry += ThemeText.wrapped(g, this.font(), c, left + 14, ry, w - 22, Palette.WARNING);
        }
        return y + height + 8;
    }

    private int telemetry(GuiGraphicsExtractor g, DroneContext context, int left, int y, int w) {
        Telemetry t = context.telemetry();
        if (!context.piloting()) {
            int textHeight = ThemeText.wrappedHeight(this.font(), Component.translatable("propwash.ui.overview.telemetry_hint"), w - 12);
            int height = 12 + 4 + Math.max(TELEMETRY_ROWS, textHeight) + 6;
            ThemeDraw.panel(g, left, y, left + w, y + height);
            ThemeDraw.panelTitle(g, this.font(), Component.translatable("propwash.ui.overview.telemetry"), left, y, left + w);
            ThemeText.wrapped(g, this.font(), Component.translatable("propwash.ui.overview.telemetry_hint"), left + 6, y + 16, w - 12, Palette.TEXT_FAINT);
            return y + height + 8;
        }
        int motorRow = motorRow(ThemeText.smallLineHeight());
        List<Row> rows = t == null ? List.of() : this.telemetryRows(t);
        int colWidth = telemetryRowsWidth(w, this.widestRow(rows));
        boolean twoColumns = colWidth < w - 12;
        int rowsHeight = Math.max(TELEMETRY_ROWS, this.rowsHeight(rows, colWidth));
        int height = telemetryHeight(twoColumns, motorRow, rowsHeight);
        ThemeDraw.panel(g, left, y, left + w, y + height);
        ThemeDraw.panelTitle(g, this.font(), Component.translatable("propwash.ui.overview.telemetry"), left, y, left + w);
        int ry = y + 16;
        if (t == null) {
            ThemeText.text(g, this.font(), Component.translatable("propwash.ui.overview.telemetry_missing"), left + 6, ry, Palette.TEXT_FAINT);
            return y + height + 8;
        }
        for (Row row : rows) {
            ry += this.row(g, row, left + 6, ry, colWidth);
        }
        int mx = twoColumns ? left + 6 + colWidth + TELEMETRY_GAP : left + 6;
        int my = y + motorBarsTop(twoColumns, rowsHeight);
        int barRight = left + w - 6;
        int labelWidth = ThemeText.smallWidth(this.font(), "M4") + 3;
        int barLeft = Math.min(mx + labelWidth, barRight - 1);
        for (int m = 0; m < 4; m++) {
            int by = my + m * motorRow;
            ThemeText.small(g, this.font(), "M" + (m + 1), mx, by, Palette.TEXT_MUTED);
            ThemeDraw.valueBar(g, barLeft, by, barRight, by + MOTOR_BAR, t.motorOutput[m], Palette.ACCENT);
        }
        return y + height + 8;
    }

    private List<Row> telemetryRows(Telemetry t) {
        SpeedUnit unit = UiConfig.speedUnit();
        List<Row> rows = new ArrayList<>();
        Component state = Component.translatable(t.armed ? "propwash.ui.overview.armed" : "propwash.ui.overview.disarmed");
        if (PilotGuard.lockoutActive()) {
            state = Component.translatable("propwash.ui.overview.locked");
        }
        rows.add(new Row(tr("propwash.ui.overview.state"), state.getString() + " · " + enumName(t.mode).getString(), t.armed ? Palette.ERROR : Palette.SUCCESS));
        int batteryColor = switch (t.batteryState) {
            case OK -> Palette.TEXT;
            default -> Palette.WARNING;
        };
        rows.add(new Row(tr("propwash.ui.overview.battery"), fmt("%.1f V · %.2f V/S · ", t.batteryVoltage, t.batteryCellVoltage) + percent(t.batteryCharge * 100.0),
                batteryColor));
        rows.add(new Row(tr("propwash.ui.overview.consumption"), fmt("%.1f A · %.0f mAh", t.batteryCurrentA, t.batteryMahUsed), Palette.TEXT));
        rows.add(new Row(tr("propwash.ui.overview.speed_height"), unit.format(t.speed * 3.6) + fmt(" · %.1f m", t.heightAboveGround), Palette.TEXT));
        rows.add(new Row(tr("propwash.ui.overview.attitude"), fmt("%.0f° / %.0f° · %.0f°", wholeDegrees(t.rollDeg), wholeDegrees(t.pitchDeg),
                wholeDegrees(t.headingDeg)), Palette.TEXT));
        return rows;
    }

    private int widestRow(List<Row> rows) {
        int widest = 0;
        for (Row row : rows) {
            widest = Math.max(widest, this.font().width(row.label()) + ROW_GAP + this.font().width(row.value()));
        }
        return widest;
    }

    static int telemetryRowsWidth(int width, int widestRow) {
        int single = width - 12;
        if (width < TELEMETRY_TWO_COLUMNS) {
            return single;
        }
        int inner = single - TELEMETRY_GAP;
        int rows = Math.max(inner * 3 / 5, widestRow);
        return inner - rows >= MOTOR_MIN_WIDTH ? rows : single;
    }

    static double wholeDegrees(double degrees) {
        return Double.isFinite(degrees) ? Math.round(degrees) + 0.0 : degrees;
    }

    static int motorBarsTop(boolean twoColumns, int rowsHeight) {
        return twoColumns ? 16 : 16 + rowsHeight + MOTOR_GAP;
    }

    static int motorRow(int smallLineHeight) {
        return Math.max(MOTOR_ROW, smallLineHeight + 1);
    }

    static int motorBarsBottom(boolean twoColumns, int motorRow, int rowsHeight) {
        return motorBarsTop(twoColumns, rowsHeight) + 3 * motorRow + MOTOR_BAR;
    }

    static int telemetryHeight(boolean twoColumns, int motorRow, int rowsHeight) {
        return Math.max(16 + rowsHeight, motorBarsBottom(twoColumns, motorRow, rowsHeight)) + 6;
    }

    private int row(GuiGraphicsExtractor g, Row row, int x, int y, int width) {
        return ThemeText.pair(g, this.font(), row.label(), Component.literal(row.value()), x, y, width, ROW_GAP, Palette.TEXT_MUTED, row.color());
    }

    private static String percent(double value) {
        if (!Double.isFinite(value)) {
            return "–";
        }
        return Component.translatable("propwash.ui.format.percent", Math.round(value)).getString();
    }

    private static String grouped(double value) {
        if (!Double.isFinite(value)) {
            return "–";
        }
        String text = String.format(Locale.ROOT, "%,.0f", value);
        String separator = Numbers.of(0.5, 1).getString().replaceAll("\\d", "");
        return separator.equals(",") ? text.replace(',', '.') : text;
    }

    private static String fmt(String pattern, Object... args) {
        for (Object arg : args) {
            if (arg instanceof Double d && !Double.isFinite(d)) {
                return "–";
            }
        }
        String text = String.format(Locale.ROOT, pattern, args);
        String separator = Numbers.of(0.5, 1).getString().replaceAll("\\d", "");
        return separator.equals(".") ? text : DECIMAL_POINT.matcher(text).replaceAll(Matcher.quoteReplacement(separator));
    }
}
