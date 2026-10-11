package de.lardtom.propwash.calibration;

public final class Measure {
    public static final double G = 9.81;
    static final double PRE_FROM = -0.15;
    static final double PRE_TO = -0.02;
    static final double PEAK_FROM = 0.05;
    static final double PEAK_TO = 0.50;
    static final double PEAK_PERCENTILE = 0.9;
    static final double RISE_FRACTION = 0.9;
    static final double T63 = 1.0 - Math.exp(-1.0);
    static final double RPM_POST_FROM = 0.25;
    static final double RPM_POST_TO = 0.40;
    static final double EARLY_FROM = 0.12;
    static final double EARLY_TO = 0.30;
    static final double LATE_SPAN = 0.20;
    static final double LATE_MIN_END = 0.80;
    static final double MIN_GROWTH = 0.10;

    private Measure() {
    }

    public record Punch(double preDuty, double postDuty, double preFz, double peakFz, double riseMs, double preRpm, double postRpm,
                        double rpmT63Ms, double preVbat, double minVbat) {
        public double sagVolts() {
            return this.preVbat - this.minVbat;
        }
    }

    public record Cut(double preDuty, double idleDuty, double preFz, double fzEarly, double fzLate, double durationS, double vEnd,
                      double vTerminal, double preRpm, double idleRpm, double rpmT63Ms) {
        public double dropAccel() {
            return G * (1.0 - this.fzEarly);
        }
    }

    static int idx(Trace t, double s) {
        return Math.max(0, Math.min(t.length(), t.at(s)));
    }

    public static final double SHARP_STEP_MS = 60.0;

    public record Transition(int start, int middle, int end, boolean found) {
        public double rampMs(Trace t) {
            return 1000.0 * (this.end - this.start) / t.rate;
        }
    }

    public static Transition transition(Trace t) {
        double pre = Signals.mean(t.duty, idx(t, PRE_FROM), idx(t, PRE_TO));
        double post = Signals.mean(t.duty, idx(t, 0.10), idx(t, 0.30));
        if (Double.isNaN(pre) || Double.isNaN(post) || Math.abs(post - pre) < 1e-6) {
            return new Transition(t.step, t.step, t.step, false);
        }
        boolean up = post > pre;
        int from = idx(t, -0.30);
        int to = idx(t, 0.30);
        int a = crossing(t, from, to, pre + 0.1 * (post - pre), up);
        int m = crossing(t, Math.max(from, a), to, pre + 0.5 * (post - pre), up);
        int b = crossing(t, Math.max(from, m), to, pre + 0.9 * (post - pre), up);
        if (a < 0 || m < 0 || b < 0) {
            return new Transition(t.step, t.step, t.step, false);
        }
        return new Transition(a, m, b, true);
    }

    static int crossing(Trace t, int from, int to, double level, boolean up) {
        for (int i = Math.max(0, from); i < Math.min(t.length(), to); i++) {
            if (up ? t.duty[i] >= level : t.duty[i] <= level) {
                return i;
            }
        }
        return -1;
    }

    static double t63(Trace t, double pre, double post) {
        if (Double.isNaN(pre) || Double.isNaN(post) || Math.abs(post - pre) < 1e-6) {
            return Double.NaN;
        }
        Transition tr = transition(t);
        if (!tr.found() || tr.rampMs(t) > SHARP_STEP_MS) {
            return Double.NaN;
        }
        double target = pre + T63 * (post - pre);
        boolean up = post > pre;
        for (int i = tr.start(); i < t.length(); i++) {
            double v = t.rpm[i];
            if (!Double.isNaN(v) && (up ? v >= target : v <= target)) {
                return 1000.0 * (i - tr.middle()) / t.rate;
            }
        }
        return Double.NaN;
    }

    public static Punch punch(Trace t) {
        double preDuty = Signals.mean(t.duty, idx(t, PRE_FROM), idx(t, PRE_TO));
        double postDuty = Signals.mean(t.duty, idx(t, 0.10), idx(t, 0.45));
        double preFz = Signals.mean(t.fz, idx(t, PRE_FROM), idx(t, PRE_TO));
        double peak = Signals.percentile(t.fz, idx(t, PEAK_FROM), idx(t, PEAK_TO), PEAK_PERCENTILE);
        double rise = Double.NaN;
        if (!Double.isNaN(peak) && !Double.isNaN(preFz) && peak > preFz) {
            double target = preFz + RISE_FRACTION * (peak - preFz);
            for (int i = t.step; i < idx(t, PEAK_TO); i++) {
                if (t.fz[i] >= target) {
                    rise = 1000.0 * t.seconds(i);
                    break;
                }
            }
        }
        double preRpm = Signals.mean(t.rpm, idx(t, PRE_FROM), idx(t, PRE_TO));
        double postRpm = Signals.mean(t.rpm, idx(t, RPM_POST_FROM), idx(t, RPM_POST_TO));
        double preV = Signals.mean(t.vbat, idx(t, PRE_FROM), idx(t, PRE_TO));
        double minV = Signals.percentile(t.vbat, t.step, idx(t, PEAK_TO), 0.05);
        return new Punch(preDuty, postDuty, preFz, peak, rise, preRpm, postRpm, t63(t, preRpm, postRpm), preV, minV);
    }

    public static Cut cut(Trace t) {
        double preDuty = Signals.mean(t.duty, idx(t, PRE_FROM), idx(t, PRE_TO));
        double idle = Signals.mean(t.duty, idx(t, 0.10), t.length());
        double preFz = Signals.mean(t.fz, idx(t, PRE_FROM), idx(t, PRE_TO));
        double duration = t.seconds(t.length());
        double earlyTo = Math.min(EARLY_TO, duration);
        double early = Signals.mean(t.fz, idx(t, EARLY_FROM), idx(t, earlyTo));
        double[] v = new double[t.length()];
        double speed = Double.isNaN(t.startClimb) ? 0.0 : -t.startClimb;
        for (int i = 0; i < t.step; i++) {
            double fz = Double.isNaN(t.fz[i]) ? 1.0 : t.fz[i];
            speed += G * (1.0 - fz) / t.rate;
        }
        for (int i = t.step; i < t.length(); i++) {
            double fz = Double.isNaN(t.fz[i]) ? 1.0 : t.fz[i];
            speed += G * (1.0 - fz) / t.rate;
            v[i] = speed;
        }
        double late = Double.NaN;
        double vTerm = Double.NaN;
        if (duration >= LATE_MIN_END) {
            int lateFrom = idx(t, duration - LATE_SPAN);
            late = Signals.mean(t.fz, lateFrom, t.length());
            double vE = v[idx(t, 0.5 * (EARLY_FROM + earlyTo))];
            double vL = v[Math.min(t.length() - 1, idx(t, duration - 0.5 * LATE_SPAN))];
            double sE = vE * Math.abs(vE);
            double sL = vL * Math.abs(vL);
            double k = (late - early) / (sL - sE);
            double f0 = early - k * sE;
            if (k > 0.0 && f0 < 1.0 && vL > vE && vL > 0.0 && late - early > MIN_GROWTH) {
                vTerm = Math.sqrt((1.0 - f0) / k);
            }
        }
        double preRpm = Signals.mean(t.rpm, idx(t, PRE_FROM), idx(t, PRE_TO));
        double idleRpm = Signals.mean(t.rpm, idx(t, RPM_POST_FROM), idx(t, RPM_POST_TO));
        return new Cut(preDuty, idle, preFz, early, late, duration, speed, vTerm, preRpm, idleRpm, t63(t, preRpm, idleRpm));
    }
}
