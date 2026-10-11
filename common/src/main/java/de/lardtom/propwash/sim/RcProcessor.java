package de.lardtom.propwash.sim;

final class RcProcessor {
    private final Pt2 roll = new Pt2();
    private final Pt2 pitch = new Pt2();
    private final Pt2 yaw = new Pt2();
    private final Pt2 throttle = new Pt2();

    double rollOut;
    double pitchOut;
    double yawOut;
    double throttleStick;
    double throttleOut;

    void configure(double cutoffHz, double dt) {
        this.roll.configure(cutoffHz, dt);
        this.pitch.configure(cutoffHz, dt);
        this.yaw.configure(cutoffHz, dt);
        this.throttle.configure(cutoffHz, dt);
    }

    static double deadband(double x, double band) {
        if (band <= 0.0) {
            return x;
        }
        double a = Math.abs(x) - band;
        if (a <= 0.0) {
            return 0.0;
        }
        return Math.signum(x) * a / (1.0 - band);
    }

    void reset(double r, double p, double y, double t, RateProfile rates, PidProfile pid) {
        this.roll.reset(deadband(r, rates.rcDeadband));
        this.pitch.reset(deadband(p, rates.rcDeadband));
        this.yaw.reset(deadband(y, rates.yawDeadband));
        this.throttle.reset(t);
        this.rollOut = this.roll.value();
        this.pitchOut = this.pitch.value();
        this.yawOut = this.yaw.value();
        this.throttleStick = t;
        this.throttleOut = ThrottleCurve.apply(pid, t);
    }

    void update(double r, double p, double y, double t, RateProfile rates, PidProfile pid) {
        this.rollOut = SimMath.clamp(this.roll.apply(deadband(r, rates.rcDeadband)), -1.0, 1.0);
        this.pitchOut = SimMath.clamp(this.pitch.apply(deadband(p, rates.rcDeadband)), -1.0, 1.0);
        this.yawOut = SimMath.clamp(this.yaw.apply(deadband(y, rates.yawDeadband)), -1.0, 1.0);
        this.throttleStick = SimMath.clamp01(this.throttle.apply(t));
        this.throttleOut = ThrottleCurve.apply(pid, this.throttleStick);
    }
}
