package de.lardtom.propwash.sim;

public final class StickMath {
    private StickMath() {
    }

    public static double angleToStick(double angleDeg, PidProfile pid) {
        return SimMath.clamp(SimMath.finiteOr(angleDeg, 0.0) / pid.angleLimitDeg, -1.0, 1.0);
    }

    public static double stickToAngle(double stick, PidProfile pid) {
        return SimMath.clamp(SimMath.finiteOr(stick, 0.0), -1.0, 1.0) * pid.angleLimitDeg;
    }

    public static double rateToStick(double rateDps, RateProfile rates, Axis axis) {
        return RateCurves.stickFor(rates.type, rates.axis(axis), rateDps);
    }

    public static double throttleToStick(double throttle, PidProfile pid) {
        return ThrottleCurve.inverse(pid, throttle);
    }
}
