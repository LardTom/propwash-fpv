package de.lardtom.propwash.sim;

public final class RateCurves {
    public static final double RATE_LIMIT_DPS = 1998.0;
    private static final int BISECTION_STEPS = 40;

    private RateCurves() {
    }

    public static double rateDps(RatesType type, RateCurve r, double stick) {
        double x = SimMath.clamp(SimMath.finiteOr(stick, 0.0), -1.0, 1.0);
        double rate;
        if (type == RatesType.BETAFLIGHT) {
            double a = Math.abs(x);
            double e = r.rcExpo;
            double xe = x * a * a * a * e + x * (1.0 - e);
            double rc = r.rcRate > 2.0 ? r.rcRate + 14.54 * (r.rcRate - 2.0) : r.rcRate;
            rate = 200.0 * rc * xe;
            if (r.superRate > 0.0) {
                rate = rate / SimMath.clamp(1.0 - a * r.superRate, 0.01, 1.0);
            }
        } else {
            double e = r.actualExpo;
            double x5 = x * x * x * x * x;
            rate = x * r.centerRateDps + Math.max(0.0, r.maxRateDps - r.centerRateDps) * Math.abs(x) * (x5 * e + x * (1.0 - e));
        }
        return SimMath.clamp(rate, -RATE_LIMIT_DPS, RATE_LIMIT_DPS);
    }

    public static double maxRateDps(RatesType type, RateCurve r) {
        return rateDps(type, r, 1.0);
    }

    public static double stickFor(RatesType type, RateCurve r, double rateDps) {
        double target = SimMath.finiteOr(rateDps, 0.0);
        double lo = -1.0;
        double hi = 1.0;
        if (target >= rateDps(type, r, hi)) {
            return hi;
        }
        if (target <= rateDps(type, r, lo)) {
            return lo;
        }
        for (int k = 0; k < BISECTION_STEPS; k++) {
            double mid = 0.5 * (lo + hi);
            if (rateDps(type, r, mid) < target) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return SimMath.clamp(0.5 * (lo + hi), -1.0, 1.0);
    }
}
