package de.lardtom.propwash.sim;

final class Mixer {
    final double[] authority = new double[AirframeSpec.MOTORS];
    final double[] output = new double[AirframeSpec.MOTORS];
    double throttle;
    boolean saturated;

    void mix(AirframeSpec p, double roll, double pitch, double yaw, double thr, boolean airmodeActive, boolean propsOut) {
        double yawSign = propsOut ? -1.0 : 1.0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double a = p.mixRoll(i) * roll + p.mixPitch(i) * pitch + yawSign * p.mixYaw(i) * yaw;
            this.authority[i] = a;
            min = Math.min(min, a);
            max = Math.max(max, a);
        }
        boolean sat = false;
        double range = max - min;
        if (range > 1.0) {
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                this.authority[i] /= range;
            }
            min /= range;
            max /= range;
            sat = true;
        }
        if (!p.uniformThrottle) {
            this.mixWeighted(p, thr, airmodeActive, sat);
            return;
        }
        double t;
        if (airmodeActive) {
            t = SimMath.clamp(thr, -min, 1.0 - max);
        } else {
            t = thr > 0.5 ? Math.min(thr, 1.0 - max) : thr;
        }
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double o = t + this.authority[i];
            if (o < 0.0 || o > 1.0) {
                sat = true;
                o = SimMath.clamp01(o);
            }
            this.output[i] = o;
        }
        this.throttle = t;
        this.saturated = sat;
    }

    private void mixWeighted(AirframeSpec p, double thr, boolean airmodeActive, boolean saturated) {
        boolean sat = saturated;
        double lower = Double.NEGATIVE_INFINITY;
        double upper = Double.POSITIVE_INFINITY;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double w = p.mixThrottle(i);
            lower = Math.max(lower, -this.authority[i] / w);
            upper = Math.min(upper, (1.0 - this.authority[i]) / w);
        }
        double t;
        if (airmodeActive) {
            t = lower > upper ? upper : SimMath.clamp(thr, lower, upper);
        } else {
            t = thr > 0.5 ? Math.min(thr, upper) : thr;
        }
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double o = t * p.mixThrottle(i) + this.authority[i];
            if (o < 0.0 || o > 1.0) {
                sat = true;
                o = SimMath.clamp01(o);
            }
            this.output[i] = o;
        }
        this.throttle = t;
        this.saturated = sat;
    }
}
