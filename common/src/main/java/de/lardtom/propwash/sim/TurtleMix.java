package de.lardtom.propwash.sim;

final class TurtleMix {
    static final double STICK_DEADBAND = 0.1;
    static final double EXPO = 0.35;
    static final double DIAGONAL_COS = Math.sqrt(3.0) / 2.0;
    static final double UPRIGHT_TILT_DEG = 60.0;
    static final double EASE_FROM_DEG = 135.0;
    static final double EASE_TO_DEG = 75.0;
    static final double RATE_LIMIT = 4.0;
    static final double LIFT_LIMIT = 0.5;

    final double[] duty = new double[AirframeSpec.MOTORS];
    private final double[] share = new double[AirframeSpec.MOTORS];
    double power;

    void stop() {
        this.power = 0.0;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            this.duty[i] = 0.0;
        }
    }

    static double expo(double stick) {
        double x = SimMath.clamp01(stick);
        return (1.0 - EXPO) * x + EXPO * x * x * x;
    }

    static double governor(double tiltDeg, double rate, double climb) {
        double ease = SimMath.clamp01((tiltDeg - EASE_TO_DEG) / (EASE_FROM_DEG - EASE_TO_DEG));
        double spin = SimMath.clamp01(2.0 - rate / RATE_LIMIT);
        double lift = SimMath.clamp01(1.5 - climb / LIFT_LIMIT);
        return ease * spin * lift;
    }

    void update(AirframeSpec p, double roll, double pitch, double yaw, double throttle, double idle, boolean propsOut) {
        this.update(p, roll, pitch, yaw, throttle, idle, propsOut, 1.0);
    }

    void update(AirframeSpec p, double roll, double pitch, double yaw, double throttle, double idle, boolean propsOut, double governor) {
        double ar = Math.abs(roll);
        double ap = Math.abs(pitch);
        double ay = Math.abs(yaw);
        double sr = Math.signum(roll);
        double sp = Math.signum(pitch);
        double sy = Math.signum(yaw);
        double stick;
        if (ay > Math.max(ar, ap)) {
            sr = 0.0;
            sp = 0.0;
            stick = ay;
        } else {
            sy = 0.0;
            double length = Math.hypot(ar, ap);
            stick = Math.min(1.0, length);
            if (length > 0.0 && (ar + ap) / (Math.sqrt(2.0) * length) < DIAGONAL_COS) {
                if (ar > ap) {
                    sp = 0.0;
                } else {
                    sr = 0.0;
                }
            }
        }
        if (stick < STICK_DEADBAND) {
            this.stop();
            return;
        }
        double yawSign = propsOut ? -1.0 : 1.0;
        double strongest = 0.0;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double a = -(p.mixRoll(i) * sr + p.mixPitch(i) * sp + yawSign * p.mixYaw(i) * sy);
            this.share[i] = a > 1e-9 ? a : 0.0;
            strongest = Math.max(strongest, this.share[i]);
        }
        if (strongest <= 0.0) {
            this.stop();
            return;
        }
        double level = Math.max(expo(stick), SimMath.clamp01(throttle)) * SimMath.clamp01(governor);
        if (level <= 0.0) {
            this.stop();
            return;
        }
        this.power = level;
        double floor = SimMath.clamp01(idle);
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double out = Math.min(1.0, level * this.share[i] / strongest);
            this.duty[i] = out > 0.0 ? -(floor + (1.0 - floor) * out) : 0.0;
        }
    }
}
