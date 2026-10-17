package de.lardtom.propwash.calibration;

import java.util.Arrays;
import java.util.Optional;

public final class BatteryFit {
    public static final double STEP_SECONDS = 0.02;
    public static final double FLIGHT_DUTY = 0.08;
    public static final double[] FAST_TAUS = {0.06, 0.12, 0.25, 0.5, 1.0};
    public static final double[] SLOW_TAUS = {2.0, 4.0, 8.0, 15.0, 30.0};
    public static final int OCV_ORDER = 3;
    public static final int MIN_SAMPLES = 500;
    public static final double LOW_SHARE = 0.25;
    public static final double HIGH_SHARE = 0.6;
    public static final int STEP_MIN_SAMPLES = 8;
    public static final double SPREAD_MIN = 1.5;
    public static final double PEAK_MIN_AMPS = 2.0;

    private BatteryFit() {
    }

    public record Series(double[] seconds, double[] cellVolts, double[] amps) {
        public int size() {
            return this.seconds.length;
        }

        public Series withVolts(double[] volts) {
            return new Series(this.seconds, volts, this.amps);
        }

        public Series scaled(double factor) {
            double[] amps = new double[this.amps.length];
            for (int k = 0; k < amps.length; k++) {
                amps[k] = this.amps[k] * factor;
            }
            return new Series(this.seconds, this.cellVolts, amps);
        }

        public double mah(int from, int to) {
            double sum = 0.0;
            for (int k = Math.max(1, from + 1); k < to; k++) {
                sum += this.amps[k] * (this.seconds[k] - this.seconds[k - 1]);
            }
            return sum / 3.6;
        }
    }

    public record Result(double r0, double r1, double tau1, double r2, double tau2, double ocvStart, double ocvEnd, double rmsVolts, double mah,
                         int samples) {
        public double sum() {
            return this.r0 + this.r1 + this.r2;
        }
    }

    public record Step(double lowAmps, double highAmps, double lowVolts, double highVolts, int samples) {
        public double sag() {
            return this.lowVolts - this.highVolts;
        }

        public double ohms() {
            return this.sag() / Math.max(this.highAmps - this.lowAmps, 1e-9);
        }
    }

    public static Series series(Flight f, int cells) {
        int first = -1;
        int last = -1;
        for (int i = 0; i < f.n; i++) {
            if (f.valid[i] && f.duty[i] > FLIGHT_DUTY) {
                if (first < 0) {
                    first = i;
                }
                last = i;
            }
        }
        if (first < 0 || !f.hasVbat || !f.hasAmps) {
            return new Series(new double[0], new double[0], new double[0]);
        }
        int stride = Math.max(1, (int) Math.round(STEP_SECONDS * f.rate));
        int bins = (last - first) / stride;
        double[] t = new double[bins];
        double[] v = new double[bins];
        double[] a = new double[bins];
        int n = 0;
        for (int b = 0; b < bins; b++) {
            double sv = 0.0;
            double sa = 0.0;
            int c = 0;
            for (int i = first + b * stride; i < first + (b + 1) * stride; i++) {
                if (f.valid[i] && !Double.isNaN(f.vbat[i]) && !Double.isNaN(f.amps[i])) {
                    sv += f.vbat[i];
                    sa += f.amps[i];
                    c++;
                }
            }
            if (c > 0) {
                t[n] = (b + 0.5) * stride / f.rate;
                v[n] = sv / c / cells;
                a[n] = sa / c;
                n++;
            }
        }
        return new Series(Arrays.copyOf(t, n), Arrays.copyOf(v, n), Arrays.copyOf(a, n));
    }

    public static double percentile(double[] x, double p) {
        if (x.length == 0) {
            return Double.NaN;
        }
        double[] sorted = x.clone();
        Arrays.sort(sorted);
        return sorted[(int) Math.min(sorted.length - 1, Math.floor(p * sorted.length))];
    }

    public static boolean usableCurrent(Series s) {
        if (s.size() < MIN_SAMPLES) {
            return false;
        }
        double mid = percentile(s.amps, 0.5);
        double peak = percentile(s.amps, 0.99);
        return peak >= PEAK_MIN_AMPS && peak >= SPREAD_MIN * Math.max(mid, 0.1);
    }

    static double[] lowPass(Series s, double tau) {
        double[] out = new double[s.size()];
        double state = s.amps[0];
        for (int k = 0; k < out.length; k++) {
            double dt = k > 0 ? s.seconds[k] - s.seconds[k - 1] : STEP_SECONDS;
            state += (s.amps[k] - state) * (1.0 - Math.exp(-dt / tau));
            out[k] = state;
        }
        return out;
    }

    static double[] solve(double[][] a, double[] b) {
        int n = b.length;
        double[][] m = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, m[i], 0, n);
            m[i][n] = b[i];
        }
        for (int c = 0; c < n; c++) {
            int pivot = c;
            for (int r = c + 1; r < n; r++) {
                if (Math.abs(m[r][c]) > Math.abs(m[pivot][c])) {
                    pivot = r;
                }
            }
            if (Math.abs(m[pivot][c]) < 1e-13) {
                return null;
            }
            double[] swap = m[c];
            m[c] = m[pivot];
            m[pivot] = swap;
            for (int r = 0; r < n; r++) {
                if (r != c) {
                    double factor = m[r][c] / m[c][c];
                    for (int k = c; k <= n; k++) {
                        m[r][k] -= factor * m[c][k];
                    }
                }
            }
        }
        double[] x = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = m[i][n] / m[i][i];
        }
        return x;
    }

    static double[] leastSquares(double[][] columns, double[] y) {
        int k = columns.length;
        int n = y.length;
        double[][] a = new double[k][k];
        double[] b = new double[k];
        for (int i = 0; i < k; i++) {
            for (int j = i; j < k; j++) {
                double sum = 0.0;
                for (int t = 0; t < n; t++) {
                    sum += columns[i][t] * columns[j][t];
                }
                a[i][j] = sum;
                a[j][i] = sum;
            }
            double sum = 0.0;
            for (int t = 0; t < n; t++) {
                sum += columns[i][t] * y[t];
            }
            b[i] = sum;
        }
        return solve(a, b);
    }

    static double rms(double[][] columns, double[] x, double[] y) {
        double sum = 0.0;
        for (int t = 0; t < y.length; t++) {
            double predicted = 0.0;
            for (int i = 0; i < columns.length; i++) {
                predicted += x[i] * columns[i][t];
            }
            sum += (predicted - y[t]) * (predicted - y[t]);
        }
        return Math.sqrt(sum / y.length);
    }

    public static Optional<Result> fit(Series s) {
        int n = s.size();
        if (n < MIN_SAMPLES) {
            return Optional.empty();
        }
        double[] charge = new double[n];
        for (int k = 1; k < n; k++) {
            charge[k] = charge[k - 1] + s.amps[k] * (s.seconds[k] - s.seconds[k - 1]) / 3.6;
        }
        double total = Math.max(charge[n - 1], 1.0);
        double[][] columns = new double[OCV_ORDER + 4][n];
        double[] minus = new double[n];
        for (int k = 0; k < n; k++) {
            double q = charge[k] / total;
            double power = 1.0;
            for (int order = 0; order <= OCV_ORDER; order++) {
                columns[order][k] = power;
                power *= q;
            }
            minus[k] = -s.amps[k];
        }
        columns[OCV_ORDER + 1] = minus;
        Result best = null;
        for (double fast : FAST_TAUS) {
            double[] quick = lowPass(s, fast);
            for (int k = 0; k < n; k++) {
                quick[k] = -quick[k];
            }
            columns[OCV_ORDER + 2] = quick;
            for (double slow : SLOW_TAUS) {
                double[] late = lowPass(s, slow);
                for (int k = 0; k < n; k++) {
                    late[k] = -late[k];
                }
                columns[OCV_ORDER + 3] = late;
                double[] x = leastSquares(columns, s.cellVolts);
                if (x == null || x[OCV_ORDER + 1] < 0.0 || x[OCV_ORDER + 2] < 0.0 || x[OCV_ORDER + 3] < 0.0) {
                    continue;
                }
                double error = rms(columns, x, s.cellVolts);
                if (best == null || error < best.rmsVolts) {
                    double end = 0.0;
                    for (int order = 0; order <= OCV_ORDER; order++) {
                        end += x[order];
                    }
                    best = new Result(x[OCV_ORDER + 1], x[OCV_ORDER + 2], fast, x[OCV_ORDER + 3], slow, x[0], end, error, charge[n - 1], n);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static Optional<Step> step(Series s, int from, int to, double peakAmps) {
        double lowSum = 0.0;
        double lowVolts = 0.0;
        int lows = 0;
        double highSum = 0.0;
        double highVolts = 0.0;
        int highs = 0;
        for (int k = Math.max(0, from); k < Math.min(to, s.size()); k++) {
            if (s.amps[k] <= LOW_SHARE * peakAmps) {
                lowSum += s.amps[k];
                lowVolts += s.cellVolts[k];
                lows++;
            } else if (s.amps[k] >= HIGH_SHARE * peakAmps) {
                highSum += s.amps[k];
                highVolts += s.cellVolts[k];
                highs++;
            }
        }
        if (lows < STEP_MIN_SAMPLES || highs < STEP_MIN_SAMPLES) {
            return Optional.empty();
        }
        return Optional.of(new Step(lowSum / lows, highSum / highs, lowVolts / lows, highVolts / highs, Math.min(lows, highs)));
    }
}
