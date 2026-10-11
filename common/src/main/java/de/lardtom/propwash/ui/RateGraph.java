package de.lardtom.propwash.ui;

import de.lardtom.propwash.sim.RateCurve;
import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.RateCurves;
import de.lardtom.propwash.sim.RatesType;

import java.util.Map;

public final class RateGraph {
    private static final double[] NICE_STEPS = {100, 200, 300, 400, 500, 600, 800, 1000, 1200, 1500, 2000};

    private RateGraph() {
    }

    public static RatesType type(Map<String, Float> tune, Map<String, Float> defaults) {
        RatesType[] all = RatesType.values();
        int i = (int) Math.rint(TuneEdits.value(tune, defaults, TuneKeys.RATES_TYPE));
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }

    public static RateCurve axisRates(Map<String, Float> tune, Map<String, Float> defaults, Axis axis) {
        RateCurve r = new RateCurve();
        r.rcRate = TuneEdits.value(tune, defaults, TuneKeys.rateKey(axis, "rcRate"));
        r.superRate = TuneEdits.value(tune, defaults, TuneKeys.rateKey(axis, "superRate"));
        r.rcExpo = TuneEdits.value(tune, defaults, TuneKeys.rateKey(axis, "rcExpo"));
        r.centerRateDps = TuneEdits.value(tune, defaults, TuneKeys.rateKey(axis, "centerRateDps"));
        r.maxRateDps = TuneEdits.value(tune, defaults, TuneKeys.rateKey(axis, "maxRateDps"));
        r.actualExpo = TuneEdits.value(tune, defaults, TuneKeys.rateKey(axis, "actualExpo"));
        r.sanitize();
        return r;
    }

    public static double rate(RatesType type, RateCurve rates, double stick) {
        return RateCurves.rateDps(type, rates, stick);
    }

    public static double maxRate(RatesType type, RateCurve rates) {
        return Math.abs(RateCurves.maxRateDps(type, rates));
    }

    public static double niceScale(double maxAbsDps) {
        double target = Double.isFinite(maxAbsDps) ? Math.abs(maxAbsDps) * 1.05 : 0.0;
        for (double step : NICE_STEPS) {
            if (step >= target) {
                return step;
            }
        }
        return NICE_STEPS[NICE_STEPS.length - 1];
    }

    public static double gridStep(double scale) {
        if (scale <= 200) {
            return 50;
        }
        if (scale <= 600) {
            return 100;
        }
        if (scale <= 1200) {
            return 250;
        }
        return 500;
    }

    public static int xPixel(double stick, int left, int width) {
        double s = Math.max(-1.0, Math.min(1.0, Double.isFinite(stick) ? stick : 0.0));
        int px = left + (int) Math.round((s + 1.0) * 0.5 * (width - 1));
        return Math.max(left, Math.min(left + width - 1, px));
    }

    public static double stickAt(int px, int left, int width) {
        if (width <= 1) {
            return 0.0;
        }
        return Math.max(-1.0, Math.min(1.0, 2.0 * (px - left) / (double) (width - 1) - 1.0));
    }

    public static int yPixel(double rateDps, double scale, int top, int height) {
        double r = Double.isFinite(rateDps) ? rateDps : 0.0;
        double half = (height - 1) * 0.5;
        double normalized = scale > 0.0 ? Math.max(-1.0, Math.min(1.0, r / scale)) : 0.0;
        int py = top + (int) Math.round(half - normalized * half);
        return Math.max(top, Math.min(top + height - 1, py));
    }

    public static double[] samples(RatesType type, RateCurve rates, int count) {
        int n = Math.max(2, count);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            double stick = -1.0 + 2.0 * i / (n - 1);
            out[i] = rate(type, rates, stick);
        }
        return out;
    }
}
