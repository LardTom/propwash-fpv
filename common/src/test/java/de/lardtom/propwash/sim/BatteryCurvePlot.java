package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

class BatteryCurvePlot {
    static final int WIDTH = 1000;
    static final int HEIGHT = 760;
    static final int PANEL_W = 440;
    static final int PANEL_H = 250;
    static final String NEW = "#d4312a";
    static final String OLD = "#7a7f87";
    static final String SHAPE = "#1f6fd1";
    static final String[] RATES = {"#2e9e5b", "#1f6fd1", "#d4312a", "#7b3fb8"};

    record Panel(double left, double top, double x0, double x1, double y0, double y1) {
        double px(double x) {
            return this.left + (x - this.x0) / (this.x1 - this.x0) * PANEL_W;
        }

        double py(double y) {
            return this.top + PANEL_H - (y - this.y0) / (this.y1 - this.y0) * PANEL_H;
        }
    }

    static String f(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    static void frame(StringBuilder s, Panel p, String title, String xLabel, String yLabel, double xStep, double yStep, double xScale, String xUnit) {
        s.append("<text x=\"").append(f(p.left)).append("\" y=\"").append(f(p.top - 14)).append("\" class=\"t\">").append(title).append("</text>\n");
        for (double x = p.x0; x <= p.x1 + 1e-9; x += xStep) {
            s.append("<line x1=\"").append(f(p.px(x))).append("\" y1=\"").append(f(p.top)).append("\" x2=\"").append(f(p.px(x)))
                    .append("\" y2=\"").append(f(p.top + PANEL_H)).append("\" class=\"g\"/>\n");
            s.append("<text x=\"").append(f(p.px(x))).append("\" y=\"").append(f(p.top + PANEL_H + 16)).append("\" class=\"a\" text-anchor=\"middle\">")
                    .append(String.format(Locale.ROOT, "%.0f", x * xScale)).append(xUnit).append("</text>\n");
        }
        for (double y = p.y0; y <= p.y1 + 1e-9; y += yStep) {
            s.append("<line x1=\"").append(f(p.left)).append("\" y1=\"").append(f(p.py(y))).append("\" x2=\"").append(f(p.left + PANEL_W))
                    .append("\" y2=\"").append(f(p.py(y))).append("\" class=\"g\"/>\n");
            s.append("<text x=\"").append(f(p.left - 6)).append("\" y=\"").append(f(p.py(y) + 4)).append("\" class=\"a\" text-anchor=\"end\">")
                    .append(String.format(Locale.ROOT, yStep < 1.0 ? "%.1f" : "%.0f", y).replace('.', ',')).append("</text>\n");
        }
        s.append("<rect x=\"").append(f(p.left)).append("\" y=\"").append(f(p.top)).append("\" width=\"").append(PANEL_W).append("\" height=\"")
                .append(PANEL_H).append("\" class=\"f\"/>\n");
        s.append("<text x=\"").append(f(p.left + PANEL_W / 2.0)).append("\" y=\"").append(f(p.top + PANEL_H + 34)).append("\" class=\"a\" text-anchor=\"middle\">")
                .append(xLabel).append("</text>\n");
        s.append("<text transform=\"translate(").append(f(p.left - 50)).append(' ').append(f(p.top + PANEL_H / 2.0)).append(") rotate(-90)\" class=\"a\" text-anchor=\"middle\">")
                .append(yLabel).append("</text>\n");
    }

    static void line(StringBuilder s, Panel p, double[] xs, double[] ys, String color, double width, boolean dashed) {
        s.append("<polyline fill=\"none\" stroke=\"").append(color).append("\" stroke-width=\"").append(f(width)).append('"');
        if (dashed) {
            s.append(" stroke-dasharray=\"6 4\"");
        }
        s.append(" points=\"");
        for (int k = 0; k < xs.length; k++) {
            double y = Math.clamp(ys[k], p.y0, p.y1);
            s.append(f(p.px(xs[k]))).append(',').append(f(p.py(y))).append(' ');
        }
        s.append("\"/>\n");
    }

    static void dot(StringBuilder s, Panel p, double x, double y, String color) {
        s.append("<circle cx=\"").append(f(p.px(x))).append("\" cy=\"").append(f(p.py(y))).append("\" r=\"3.5\" fill=\"").append(color).append("\"/>\n");
    }

    static void legend(StringBuilder s, Panel p, int row, String color, boolean dashed, String text) {
        legend(s, p, row, color, dashed, text, false);
    }

    static void legend(StringBuilder s, Panel p, int row, String color, boolean dashed, String text, boolean bottom) {
        double x = p.left + 12;
        double y = bottom ? p.top + PANEL_H - 12 - (3 - row) * 16 : p.top + 16 + row * 16;
        s.append("<line x1=\"").append(f(x)).append("\" y1=\"").append(f(y - 4)).append("\" x2=\"").append(f(x + 22)).append("\" y2=\"").append(f(y - 4))
                .append("\" stroke=\"").append(color).append("\" stroke-width=\"2.5\"").append(dashed ? " stroke-dasharray=\"6 4\"" : "").append("/>\n");
        s.append("<text x=\"").append(f(x + 28)).append("\" y=\"").append(f(y)).append("\" class=\"l\">").append(text).append("</text>\n");
    }

    static double[] column(List<double[]> rows, int index) {
        double[] out = new double[rows.size()];
        for (int k = 0; k < out.length; k++) {
            out[k] = rows.get(k)[index];
        }
        return out;
    }

    static String svg() throws Exception {
        StringBuilder s = new StringBuilder();
        s.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(WIDTH).append("\" height=\"").append(HEIGHT)
                .append("\" viewBox=\"0 0 ").append(WIDTH).append(' ').append(HEIGHT).append("\" font-family=\"Helvetica, Arial, sans-serif\">\n");
        s.append("<style>.t{font-size:15px;font-weight:bold;fill:#1d2129}.a{font-size:11px;fill:#4b5058}.l{font-size:11.5px;fill:#1d2129}"
                + ".g{stroke:#e3e5e8;stroke-width:1}.f{fill:none;stroke:#9aa0a8;stroke-width:1}</style>\n");
        s.append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n");

        Panel shape = new Panel(70, 50, 0.0, 1.0, 18.0, 25.5);
        frame(s, shape, "6S-LiPo, Entladetest mit 15 C", "entnommen bis 3,1 V je Zelle", "Packspannung [V]", 0.1, 1.0, 100, " %");
        double[] described = {0.0, 0.05, 0.9, 1.0};
        double[] describedV = {25.2, 24.0, 21.5, 18.6};
        double[] tol = {0.3, 0.3, 0.4, 0.1};
        for (int k = 0; k < described.length; k++) {
            s.append("<line x1=\"").append(f(shape.px(described[k]))).append("\" y1=\"").append(f(shape.py(describedV[k] - tol[k])))
                    .append("\" x2=\"").append(f(shape.px(described[k]))).append("\" y2=\"").append(f(shape.py(describedV[k] + tol[k])))
                    .append("\" stroke=\"").append(SHAPE).append("\" stroke-width=\"2\"/>\n");
            dot(s, shape, described[k], describedV[k], SHAPE);
        }
        List<double[]> old = DischargeCurveTest.previous("kurve-15c-6s");
        double[] oldV = column(old, 1);
        for (int k = 0; k < oldV.length; k++) {
            oldV[k] *= 6.0;
        }
        line(s, shape, column(old, 0), oldV, OLD, 2.0, true);
        BatteryCurves.Curve now = BatteryCurves.discharge(AirframeSpec.of(DroneBuild.FREESTYLE), 15.0, 3.1);
        double[] xs = new double[101];
        double[] ys = new double[101];
        for (int k = 0; k <= 100; k++) {
            xs[k] = k / 100.0;
            ys[k] = 6.0 * now.at(xs[k]);
        }
        line(s, shape, xs, ys, NEW, 2.5, false);
        legend(s, shape, 1, SHAPE, false, "Playtest: Start, Abfall bis 5 %, Beginn des Knies, Schluss", true);
        legend(s, shape, 2, OLD, true, "Propwash 0.5.1 (6S 1300)", true);
        legend(s, shape, 3, NEW, false, "Propwash 0.5.2 (6S 1300)", true);

        Panel rates = new Panel(560, 50, 0.0, 1.1, 2.9, 4.3);
        frame(s, rates, "6S 1300 LiPo bei 1 / 5 / 15 / 30 C", "entnommen [% der Nennkapazität]", "Zellspannung unter Last [V]", 0.1, 0.2, 100, "");
        double[] cs = {1.0, 5.0, 15.0, 30.0};
        for (int r = 0; r < cs.length; r++) {
            BatteryCurves.Curve c = BatteryCurves.discharge(AirframeSpec.of(DroneBuild.FREESTYLE), cs[r], 3.0);
            int n = c.used().length;
            int step = Math.max(1, n / 300);
            int m = (n - 1) / step + 1;
            double[] ux = new double[m];
            double[] uy = new double[m];
            for (int k = 0; k < m; k++) {
                int i = Math.min(n - 1, k * step);
                ux[k] = c.used()[i];
                uy[k] = c.cell()[i];
            }
            line(s, rates, ux, uy, RATES[r], 2.0, false);
            legend(s, rates, r, RATES[r], false, String.format(Locale.ROOT, "%.0f C – %.0f %% bis 3,1 V", cs[r],
                    100.0 * BatteryCurves.discharge(AirframeSpec.of(DroneBuild.FREESTYLE), cs[r], 3.1).capacity()), true);
        }

        Panel rest = new Panel(70, 420, 0.0, 1.0, 2.9, 4.4);
        frame(s, rest, "Ruhespannung je Zelle", "Ladung", "Ruhespannung [V]", 0.1, 0.2, 100, " %");
        BatteryType.Chemistry[] chems = {BatteryType.Chemistry.LIPO, BatteryType.Chemistry.LIHV, BatteryType.Chemistry.LION};
        String[] chemColors = {NEW, "#7b3fb8", "#1a8f8b"};
        String[] chemNames = {"LiPo", "LiHV", "Li-Ion"};
        for (int c = 0; c < chems.length; c++) {
            double[] sx = new double[101];
            double[] sy = new double[101];
            for (int k = 0; k <= 100; k++) {
                sx[k] = k / 100.0;
                sy[k] = CellCurve.ocv(chems[c], sx[k]);
            }
            line(s, rest, sx, sy, chemColors[c], 2.0, false);
            legend(s, rest, c + 1, chemColors[c], false, chemNames[c] + (c == 0 ? " (Punkte: Richtwerte aus Tabellen und Datenblättern)" : ""), true);
        }
        for (DischargeCurveTest.Fixture fx : DischargeCurveTest.fixtures()) {
            if (!fx.resting()) {
                continue;
            }
            BatteryType.Chemistry c = DischargeCurveTest.chemistry(fx.target());
            String color = c == BatteryType.Chemistry.LIPO ? NEW : c == BatteryType.Chemistry.LIHV ? "#7b3fb8" : "#1a8f8b";
            for (DischargeCurveTest.Point pt : fx.points()) {
                dot(s, rest, pt.x(), pt.volts(), color);
            }
        }

        Panel punch = new Panel(560, 420, 0.0, 4.0, 12.0, 23.0);
        frame(s, punch, "5″ Freestyle: 1 s Vollgas aus dem Schweben", "Zeit [s]", "Packspannung [V]", 0.5, 1.0, 1, "");
        BatteryCurves.Trace half = BatteryCurves.punch(DroneBuild.FREESTYLE, 0.5, 0.25, 1.0, 3.0);
        BatteryCurves.Trace end = BatteryCurves.punch(DroneBuild.FREESTYLE, 0.06, 0.25, 1.0, 3.0);
        List<double[]> oldEnd = DischargeCurveTest.previous("punch-06");
        line(s, punch, column(oldEnd, 0), column(oldEnd, 1), OLD, 2.0, true);
        line(s, punch, half.seconds(), half.pack(), "#1f6fd1", 2.0, false);
        line(s, punch, end.seconds(), end.pack(), NEW, 2.5, false);
        legend(s, punch, 1, "#1f6fd1", false, "0.5.2 bei 50 % Ladung", true);
        legend(s, punch, 2, NEW, false, "0.5.2 bei 6 % Ladung (Ende des Packs)", true);
        legend(s, punch, 3, OLD, true, "0.5.1 bei 6 % Ladung", true);
        s.append("</svg>\n");
        return s.toString();
    }

    @Test
    void writePlot() throws Exception {
        String target = System.getenv("PROPWASH_BATTERY_PLOT");
        Assumptions.assumeTrue(target != null && !target.isBlank(), "PROPWASH_BATTERY_PLOT nicht gesetzt");
        Path out = Path.of(target);
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, svg(), StandardCharsets.UTF_8);
    }
}
