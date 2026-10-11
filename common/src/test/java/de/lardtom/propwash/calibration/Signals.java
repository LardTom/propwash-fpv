package de.lardtom.propwash.calibration;

import java.util.Arrays;

public final class Signals {
    private Signals() {
    }

    public static double[] pt1(double[] x, double rate, double hz) {
        double[] out = new double[x.length];
        double k = 1.0 - Math.exp(-2.0 * Math.PI * hz / rate);
        double y = Double.NaN;
        for (int i = 0; i < x.length; i++) {
            double v = x[i];
            if (Double.isNaN(v)) {
                out[i] = Double.NaN;
                y = Double.NaN;
                continue;
            }
            y = Double.isNaN(y) ? v : y + k * (v - y);
            out[i] = y;
        }
        return out;
    }

    public static final class Pt2 {
        private final double k;
        private double a = Double.NaN;
        private double b;

        public Pt2(double rate, double hz) {
            double order = 1.0 / Math.sqrt(Math.pow(2.0, 1.0 / 2.0) - 1.0);
            double rc = 1.0 / (2.0 * Math.PI * hz * order);
            double dt = 1.0 / rate;
            this.k = dt / (rc + dt);
        }

        public double apply(double v) {
            if (Double.isNaN(this.a)) {
                this.a = v;
                this.b = v;
            }
            this.a += this.k * (v - this.a);
            this.b += this.k * (this.a - this.b);
            return this.b;
        }
    }

    public static double mean(double[] x, int from, int to) {
        double s = 0.0;
        int n = 0;
        for (int i = Math.max(0, from); i < Math.min(x.length, to); i++) {
            if (!Double.isNaN(x[i])) {
                s += x[i];
                n++;
            }
        }
        return n == 0 ? Double.NaN : s / n;
    }

    public static double std(double[] x, int from, int to) {
        double m = mean(x, from, to);
        double s = 0.0;
        int n = 0;
        for (int i = Math.max(0, from); i < Math.min(x.length, to); i++) {
            if (!Double.isNaN(x[i])) {
                s += (x[i] - m) * (x[i] - m);
                n++;
            }
        }
        return n < 2 ? Double.NaN : Math.sqrt(s / (n - 1));
    }

    public static double max(double[] x, int from, int to) {
        double m = Double.NEGATIVE_INFINITY;
        for (int i = Math.max(0, from); i < Math.min(x.length, to); i++) {
            if (!Double.isNaN(x[i])) {
                m = Math.max(m, x[i]);
            }
        }
        return m == Double.NEGATIVE_INFINITY ? Double.NaN : m;
    }

    public static double min(double[] x, int from, int to) {
        double m = Double.POSITIVE_INFINITY;
        for (int i = Math.max(0, from); i < Math.min(x.length, to); i++) {
            if (!Double.isNaN(x[i])) {
                m = Math.min(m, x[i]);
            }
        }
        return m == Double.POSITIVE_INFINITY ? Double.NaN : m;
    }

    public static double percentile(double[] x, int from, int to, double p) {
        int a = Math.max(0, from);
        int b = Math.min(x.length, to);
        double[] v = new double[Math.max(0, b - a)];
        int n = 0;
        for (int i = a; i < b; i++) {
            if (!Double.isNaN(x[i])) {
                v[n++] = x[i];
            }
        }
        if (n == 0) {
            return Double.NaN;
        }
        double[] s = Arrays.copyOf(v, n);
        Arrays.sort(s);
        double pos = p * (n - 1);
        int lo = (int) Math.floor(pos);
        int hi = Math.min(n - 1, lo + 1);
        return s[lo] + (pos - lo) * (s[hi] - s[lo]);
    }

    public static double median(double[] values) {
        return percentile(values, 0, values.length, 0.5);
    }

    public static boolean allValid(boolean[] valid, int from, int to) {
        if (from < 0 || to > valid.length) {
            return false;
        }
        for (int i = from; i < to; i++) {
            if (!valid[i]) {
                return false;
            }
        }
        return true;
    }

    public static int firstCrossing(double[] x, int from, int to, double level, boolean upward) {
        for (int i = Math.max(1, from); i < Math.min(x.length, to); i++) {
            if (upward ? x[i - 1] < level && x[i] >= level : x[i - 1] > level && x[i] <= level) {
                return i;
            }
        }
        return -1;
    }

    public static double[] linearFit(double[] x, double[] y) {
        int n = 0;
        double sx = 0.0;
        double sy = 0.0;
        double sxx = 0.0;
        double sxy = 0.0;
        double syy = 0.0;
        for (int i = 0; i < x.length; i++) {
            if (Double.isNaN(x[i]) || Double.isNaN(y[i])) {
                continue;
            }
            n++;
            sx += x[i];
            sy += y[i];
            sxx += x[i] * x[i];
            sxy += x[i] * y[i];
            syy += y[i] * y[i];
        }
        double d = n * sxx - sx * sx;
        if (n < 3 || d == 0.0) {
            return new double[]{Double.NaN, Double.NaN, Double.NaN};
        }
        double slope = (n * sxy - sx * sy) / d;
        double icept = (sy - slope * sx) / n;
        double vy = n * syy - sy * sy;
        double r2 = vy <= 0.0 ? Double.NaN : Math.pow(n * sxy - sx * sy, 2) / (d * vy);
        return new double[]{slope, icept, r2};
    }
}
