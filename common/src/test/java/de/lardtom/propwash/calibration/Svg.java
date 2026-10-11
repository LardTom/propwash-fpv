package de.lardtom.propwash.calibration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Svg {
    public static final String REAL = "#1f4e9c";
    public static final String SIM = "#e8731a";
    public static final String SIM_BAND = "#f6c49b";
    static final int WIDTH = 640;
    static final int HEIGHT = 360;
    static final int LEFT = 64;
    static final int RIGHT = 20;
    static final int TOP = 36;
    static final int BOTTOM = 48;

    record Series(String label, String color, double[] x, double[] y, boolean points, double width) {
    }

    private final String title;
    private final String xLabel;
    private final String yLabel;
    private final List<Series> series = new ArrayList<>();
    private final List<double[]> hLines = new ArrayList<>();
    private double xMin = Double.NaN;
    private double xMax = Double.NaN;
    private double yMin = Double.NaN;
    private double yMax = Double.NaN;

    public Svg(String title, String xLabel, String yLabel) {
        this.title = title;
        this.xLabel = xLabel;
        this.yLabel = yLabel;
    }

    public Svg line(String label, String color, double[] x, double[] y) {
        this.series.add(new Series(label, color, x, y, false, 2.0));
        return this;
    }

    public Svg dashed(String label, String color, double[] x, double[] y) {
        this.series.add(new Series(label, color, x, y, false, -2.0));
        return this;
    }

    public Svg points(String label, String color, double[] x, double[] y) {
        this.series.add(new Series(label, color, x, y, true, 3.0));
        return this;
    }

    public Svg yRange(double lo, double hi) {
        this.yMin = lo;
        this.yMax = hi;
        return this;
    }

    public Svg xRange(double lo, double hi) {
        this.xMin = lo;
        this.xMax = hi;
        return this;
    }

    public Svg hLine(double y) {
        this.hLines.add(new double[]{y});
        return this;
    }

    static double nice(double span) {
        double raw = span / 5.0;
        double mag = Math.pow(10.0, Math.floor(Math.log10(raw)));
        double n = raw / mag;
        return (n < 1.5 ? 1.0 : n < 3.5 ? 2.0 : n < 7.5 ? 5.0 : 10.0) * mag;
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String f(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    public String render() {
        double x0 = this.xMin;
        double x1 = this.xMax;
        double y0 = this.yMin;
        double y1 = this.yMax;
        if (Double.isNaN(x0) || Double.isNaN(y0)) {
            double ax = Double.POSITIVE_INFINITY;
            double bx = Double.NEGATIVE_INFINITY;
            double ay = Double.POSITIVE_INFINITY;
            double by = Double.NEGATIVE_INFINITY;
            for (Series s : this.series) {
                for (int i = 0; i < s.x.length; i++) {
                    if (Double.isNaN(s.x[i]) || Double.isNaN(s.y[i])) {
                        continue;
                    }
                    ax = Math.min(ax, s.x[i]);
                    bx = Math.max(bx, s.x[i]);
                    ay = Math.min(ay, s.y[i]);
                    by = Math.max(by, s.y[i]);
                }
            }
            if (Double.isNaN(x0)) {
                x0 = ax;
                x1 = bx;
            }
            if (Double.isNaN(y0)) {
                double pad = 0.08 * Math.max(1e-6, by - ay);
                y0 = ay - pad;
                y1 = by + pad;
            }
        }
        if (!(x1 > x0)) {
            x1 = x0 + 1.0;
        }
        if (!(y1 > y0)) {
            y1 = y0 + 1.0;
        }
        final double fx0 = x0;
        final double fx1 = x1;
        final double fy0 = y0;
        final double fy1 = y1;
        int pw = WIDTH - LEFT - RIGHT;
        int ph = HEIGHT - TOP - BOTTOM;
        java.util.function.DoubleUnaryOperator sx = v -> LEFT + (v - fx0) / (fx1 - fx0) * pw;
        java.util.function.DoubleUnaryOperator sy = v -> TOP + ph - (v - fy0) / (fy1 - fy0) * ph;
        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(WIDTH).append("\" height=\"").append(HEIGHT)
                .append("\" viewBox=\"0 0 ").append(WIDTH).append(' ').append(HEIGHT)
                .append("\" font-family=\"Helvetica, Arial, sans-serif\" font-size=\"12\">\n");
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n");
        sb.append("<text x=\"").append(LEFT).append("\" y=\"22\" font-size=\"14\" font-weight=\"bold\" fill=\"#222\">").append(esc(this.title))
                .append("</text>\n");
        double xs = nice(x1 - x0);
        for (double v = Math.ceil(x0 / xs) * xs; v <= x1 + 1e-9; v += xs) {
            double px = sx.applyAsDouble(v);
            sb.append("<line x1=\"").append(f(px)).append("\" y1=\"").append(TOP).append("\" x2=\"").append(f(px)).append("\" y2=\"")
                    .append(TOP + ph).append("\" stroke=\"#e5e5e5\"/>\n");
            sb.append("<text x=\"").append(f(px)).append("\" y=\"").append(TOP + ph + 16).append("\" text-anchor=\"middle\" fill=\"#555\">")
                    .append(label(v, xs)).append("</text>\n");
        }
        double ys = nice(y1 - y0);
        for (double v = Math.ceil(y0 / ys) * ys; v <= y1 + 1e-9; v += ys) {
            double py = sy.applyAsDouble(v);
            sb.append("<line x1=\"").append(LEFT).append("\" y1=\"").append(f(py)).append("\" x2=\"").append(LEFT + pw).append("\" y2=\"")
                    .append(f(py)).append("\" stroke=\"#e5e5e5\"/>\n");
            sb.append("<text x=\"").append(LEFT - 6).append("\" y=\"").append(f(py + 4)).append("\" text-anchor=\"end\" fill=\"#555\">")
                    .append(label(v, ys)).append("</text>\n");
        }
        for (double[] h : this.hLines) {
            double py = sy.applyAsDouble(h[0]);
            sb.append("<line x1=\"").append(LEFT).append("\" y1=\"").append(f(py)).append("\" x2=\"").append(LEFT + pw).append("\" y2=\"")
                    .append(f(py)).append("\" stroke=\"#999\" stroke-dasharray=\"4 3\"/>\n");
        }
        sb.append("<rect x=\"").append(LEFT).append("\" y=\"").append(TOP).append("\" width=\"").append(pw).append("\" height=\"").append(ph)
                .append("\" fill=\"none\" stroke=\"#888\"/>\n");
        sb.append("<clipPath id=\"plot\"><rect x=\"").append(LEFT).append("\" y=\"").append(TOP).append("\" width=\"").append(pw)
                .append("\" height=\"").append(ph).append("\"/></clipPath>\n<g clip-path=\"url(#plot)\">\n");
        for (Series s : this.series) {
            if (s.points) {
                for (int i = 0; i < s.x.length; i++) {
                    if (Double.isNaN(s.x[i]) || Double.isNaN(s.y[i])) {
                        continue;
                    }
                    sb.append("<circle cx=\"").append(f(sx.applyAsDouble(s.x[i]))).append("\" cy=\"").append(f(sy.applyAsDouble(s.y[i])))
                            .append("\" r=\"").append(f(s.width)).append("\" fill=\"").append(s.color).append("\" fill-opacity=\"0.55\"/>\n");
                }
                continue;
            }
            StringBuilder path = new StringBuilder();
            boolean pen = false;
            for (int i = 0; i < s.x.length; i++) {
                if (Double.isNaN(s.x[i]) || Double.isNaN(s.y[i])) {
                    pen = false;
                    continue;
                }
                path.append(pen ? 'L' : 'M').append(f(sx.applyAsDouble(s.x[i]))).append(',').append(f(sy.applyAsDouble(s.y[i])));
                pen = true;
            }
            sb.append("<path d=\"").append(path).append("\" fill=\"none\" stroke=\"").append(s.color).append("\" stroke-width=\"")
                    .append(f(Math.abs(s.width))).append('"').append(s.width < 0 ? " stroke-dasharray=\"6 4\"" : "").append("/>\n");
        }
        sb.append("</g>\n");
        sb.append("<text x=\"").append(LEFT + pw / 2).append("\" y=\"").append(HEIGHT - 10).append("\" text-anchor=\"middle\" fill=\"#333\">")
                .append(esc(this.xLabel)).append("</text>\n");
        sb.append("<text transform=\"translate(16,").append(TOP + ph / 2).append(") rotate(-90)\" text-anchor=\"middle\" fill=\"#333\">")
                .append(esc(this.yLabel)).append("</text>\n");
        int lx = LEFT + pw - 8;
        int ly = TOP + 14;
        for (Series s : this.series) {
            if (s.label == null || s.label.isEmpty()) {
                continue;
            }
            sb.append("<line x1=\"").append(lx - 150).append("\" y1=\"").append(ly - 4).append("\" x2=\"").append(lx - 130).append("\" y2=\"")
                    .append(ly - 4).append("\" stroke=\"").append(s.color).append("\" stroke-width=\"").append(s.points ? 6 : 2).append('"')
                    .append(s.width < 0 ? " stroke-dasharray=\"6 4\"" : "").append("/>\n");
            sb.append("<text x=\"").append(lx - 124).append("\" y=\"").append(ly).append("\" fill=\"#333\">").append(esc(s.label)).append("</text>\n");
            ly += 16;
        }
        sb.append("</svg>\n");
        return sb.toString();
    }

    static String label(double v, double step) {
        if (Math.abs(v) < step * 1e-6) {
            return "0";
        }
        int decimals = Math.max(0, (int) Math.ceil(-Math.log10(step) + 1e-9));
        return String.format(Locale.ROOT, "%." + decimals + "f", v).replace('.', ',');
    }
}
