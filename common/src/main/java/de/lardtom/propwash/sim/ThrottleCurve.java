package de.lardtom.propwash.sim;

public final class ThrottleCurve {
    private static final int BISECTION_STEPS = 40;

    private ThrottleCurve() {
    }

    public static double apply(PidProfile p, double stick) {
        double t = SimMath.clamp01(SimMath.finiteOr(stick, 0.0));
        double m = p.throttleMid;
        double e = p.throttleExpo;
        double delta = t - m;
        double y = delta > 0.0 ? 1.0 - m : (delta < 0.0 ? m : 1.0);
        double shaped = m + delta * (1.0 - e + e * delta * delta / (y * y));
        double limit = p.throttleLimitPercent / 100.0;
        ThrottleLimitType type = p.throttleLimitType == null ? ThrottleLimitType.OFF : p.throttleLimitType;
        double out = switch (type) {
            case OFF -> shaped;
            case SCALE -> shaped * limit;
            case CLIP -> Math.min(shaped, limit);
        };
        return SimMath.clamp01(out);
    }

    public static double inverse(PidProfile p, double throttle) {
        double target = SimMath.finiteOr(throttle, 0.0);
        if (target <= apply(p, 0.0)) {
            return 0.0;
        }
        if (target >= apply(p, 1.0)) {
            return 1.0;
        }
        double lo = 0.0;
        double hi = 1.0;
        for (int k = 0; k < BISECTION_STEPS; k++) {
            double mid = 0.5 * (lo + hi);
            if (apply(p, mid) < target) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return SimMath.clamp01(0.5 * (lo + hi));
    }
}
