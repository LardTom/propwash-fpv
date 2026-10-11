package de.lardtom.propwash.ui.client.widget;

import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Supplier;

public class GraphPanel extends PanelWidget {
    public record Series(String name, int color, DoubleUnaryOperator function) {
    }

    public record Marker(int color, double x, double y) {
    }

    private final double xMin;
    private final double xMax;
    private final DoubleSupplier yLow;
    private final DoubleSupplier yHigh;
    private final DoubleSupplier yGrid;
    private final Supplier<List<Series>> series;
    private final Supplier<List<Marker>> markers;
    private final String unit;
    private final double xGrid;

    public GraphPanel(int x, int y, int width, int height, Component title, double xMin, double xMax, double xGrid, DoubleSupplier yScale,
                      DoubleSupplier yGrid, String unit, Supplier<List<Series>> series, Supplier<List<Marker>> markers) {
        this(x, y, width, height, title, xMin, xMax, xGrid, () -> -yScale.getAsDouble(), yScale, yGrid, unit, series, markers);
    }

    public GraphPanel(int x, int y, int width, int height, Component title, double xMin, double xMax, double xGrid, DoubleSupplier yLow,
                      DoubleSupplier yHigh, DoubleSupplier yGrid, String unit, Supplier<List<Series>> series, Supplier<List<Marker>> markers) {
        super(x, y, width, height, title);
        this.xMin = xMin;
        this.xMax = xMax;
        this.xGrid = xGrid;
        this.yLow = yLow;
        this.yHigh = yHigh;
        this.yGrid = yGrid;
        this.unit = unit;
        this.series = series;
        this.markers = markers;
    }

    public int xPixel(double x) {
        double t = (x - this.xMin) / (this.xMax - this.xMin);
        t = Double.isFinite(t) ? Math.max(0.0, Math.min(1.0, t)) : 0.0;
        return this.plotLeft() + (int) Math.round(t * (this.plotWidth() - 1));
    }

    public double xAt(int px) {
        double t = (px - this.plotLeft()) / (double) Math.max(1, this.plotWidth() - 1);
        return this.xMin + Math.max(0.0, Math.min(1.0, t)) * (this.xMax - this.xMin);
    }

    public int yPixel(double y) {
        double low = this.yLow.getAsDouble();
        double high = Math.max(low + 1e-9, this.yHigh.getAsDouble());
        double t = Double.isFinite(y) ? (y - low) / (high - low) : (0.0 - low) / (high - low);
        t = Math.max(0.0, Math.min(1.0, t));
        return this.plotTop() + (int) Math.round((1.0 - t) * (this.plotHeight() - 1));
    }

    private int plotLeft() {
        return this.getX() + 1;
    }

    private int plotTop() {
        return this.getY() + 1;
    }

    private int plotWidth() {
        return this.width - 2;
    }

    private int plotHeight() {
        return this.height - 2;
    }

    @Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x0 = this.getX();
        int y0 = this.getY();
        int x1 = x0 + this.width;
        int y1 = y0 + this.height;
        ThemeDraw.framedBox(g, x0, y0, x1, y1, Palette.FIELD, Palette.BORDER);
        double low = this.yLow.getAsDouble();
        double high = this.yHigh.getAsDouble();
        double grid = this.yGrid.getAsDouble();
        if (grid > 0.0 && high > low) {
            for (double v = Math.ceil(low / grid) * grid; v <= high + 1e-9; v += grid) {
                if (Math.abs(v) < 1e-9 || Math.abs(v - low) < 1e-9 || Math.abs(v - high) < 1e-9) {
                    continue;
                }
                int yp = this.yPixel(v);
                g.fill(this.plotLeft(), yp, this.plotLeft() + this.plotWidth(), yp + 1, Palette.GRID);
            }
        }
        if (this.xGrid > 0.0) {
            for (double v = this.xMin + this.xGrid; v < this.xMax - 1e-9; v += this.xGrid) {
                int xp = this.xPixel(v);
                g.fill(xp, this.plotTop(), xp + 1, this.plotTop() + this.plotHeight(), Palette.GRID);
            }
        }
        if (low < 0.0 && high > 0.0) {
            int zeroY = this.yPixel(0.0);
            g.fill(this.plotLeft(), zeroY, this.plotLeft() + this.plotWidth(), zeroY + 1, Palette.BORDER_STRONG);
        }
        if (this.xMin < 0.0 && this.xMax > 0.0) {
            int zeroX = this.xPixel(0.0);
            g.fill(zeroX, this.plotTop(), zeroX + 1, this.plotTop() + this.plotHeight(), Palette.BORDER_STRONG);
        }
        List<Series> all = this.series.get();
        this.extractCurves(g, all);
        this.axisLabel(g, axisText(high, this.unit), x0 + 3, y0 + 3);
        this.axisLabel(g, axisText(low, this.unit), x0 + 3, y1 - 3 - Metrics.LINE_HEIGHT - 1);
        for (Marker m : this.markers.get()) {
            int mx = this.xPixel(m.x());
            int my = this.yPixel(m.y());
            g.fill(mx - 2, my - 2, mx + 3, my + 3, Palette.SHADOW);
            g.fill(mx - 1, my - 1, mx + 2, my + 2, m.color());
        }
        if (this.isHovered() && mouseX >= this.plotLeft() && mouseX < this.plotLeft() + this.plotWidth()) {
            this.extractReadout(g, all, mouseX);
        }
    }

    private void axisLabel(GuiGraphicsExtractor g, String text, int x, int y) {
        int w = this.font().width(text);
        g.fill(x, y, x + w + 5, y + Metrics.LINE_HEIGHT + 1, Palette.PANEL);
        g.outline(x, y, w + 5, Metrics.LINE_HEIGHT + 1, Palette.BORDER);
        ThemeText.text(g, this.font(), text, x + 3, y + 2, Palette.TEXT_MUTED);
    }

    static String axisText(double value, String unit) {
        String number = String.format(Locale.ROOT, "%.0f", Math.abs(value));
        boolean negative = value <= -0.5;
        return (negative ? "\u2212" : "") + number + unit;
    }

    public static int groupColor(List<Integer> colors) {
        return colors.size() == 1 ? colors.getFirst() : Palette.ACCENT;
    }

    private void extractCurves(GuiGraphicsExtractor g, List<Series> all) {
        int width = this.plotWidth();
        int[][] paths = new int[all.size()][];
        for (int k = 0; k < all.size(); k++) {
            paths[k] = this.path(all.get(k), width);
        }
        boolean[] drawn = new boolean[all.size()];
        for (int k = 0; k < all.size(); k++) {
            if (drawn[k]) {
                continue;
            }
            List<Integer> colors = new ArrayList<>();
            for (int other = k; other < all.size(); other++) {
                if (!drawn[other] && Arrays.equals(paths[k], paths[other])) {
                    drawn[other] = true;
                    colors.add(all.get(other).color());
                }
            }
            this.extractPath(g, paths[k], colors);
        }
    }

    private int[] path(Series s, int width) {
        int left = this.plotLeft();
        int[] ys = new int[width];
        for (int i = 0; i < width; i++) {
            ys[i] = this.yPixel(s.function().applyAsDouble(this.xAt(left + i)));
        }
        return ys;
    }

    private void extractPath(GuiGraphicsExtractor g, int[] ys, List<Integer> colors) {
        int left = this.plotLeft();
        for (int i = 0; i < ys.length; i++) {
            int px = left + i;
            int from = i == 0 ? ys[i] : ys[i - 1];
            int lo = Math.min(from, ys[i]);
            int hi = Math.max(from, ys[i]);
            g.fill(px, lo, px + 1, hi + 1, groupColor(colors));
        }
    }

    private void extractReadout(GuiGraphicsExtractor g, List<Series> all, int mouseX) {
        int top = this.plotTop();
        g.fill(mouseX, top, mouseX + 1, top + this.plotHeight(), Palette.CROSSHAIR);
        double x = this.xAt(mouseX);
        int lines = all.size() + 1;
        String header = String.format(Locale.ROOT, this.xMin < 0.0 ? "%+.0f%%" : "%.0f%%", x * 100.0);
        int boxW = this.font().width(header);
        String[] rows = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            Series s = all.get(i);
            rows[i] = String.format(Locale.ROOT, "%s %.0f%s", s.name(), s.function().applyAsDouble(x), this.unit);
            boxW = Math.max(boxW, this.font().width(rows[i]));
        }
        int bx = mouseX + 4 + boxW + 6 > this.getX() + this.width ? mouseX - boxW - 8 : mouseX + 4;
        int by = top + 2;
        int line = Metrics.LINE_HEIGHT;
        ThemeDraw.tooltipFrame(g, bx, by, bx + boxW + 4, by + lines * line + 3);
        ThemeText.text(g, this.font(), header, bx + 2, by + 2, Palette.TEXT);
        for (int i = 0; i < rows.length; i++) {
            ThemeText.text(g, this.font(), rows[i], bx + 2, by + 2 + (i + 1) * line, all.get(i).color());
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.getMessage());
    }
}
