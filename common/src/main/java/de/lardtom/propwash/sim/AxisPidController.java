package de.lardtom.propwash.sim;

final class AxisPidController {
    private final Pt1 relaxFilter = new Pt1();
    private final Pt1 ffFilter = new Pt1();
    private final Pt1 dFilter1 = new Pt1();
    private final Pt1 dFilter2 = new Pt1();
    private double prevDGyro;
    private double prevSpFf;

    double kp;
    double ki;
    double kd;
    double kf;
    double iTerm;
    double pOut;
    double dOut;
    double fOut;
    double sum;
    double output;
    boolean sumSaturated;

    void configure(double relaxHz, double ffHz, double d1Hz, double d2Hz, double dt) {
        this.relaxFilter.configure(relaxHz, dt);
        this.ffFilter.configure(ffHz, dt);
        this.dFilter1.configure(d1Hz, dt);
        this.dFilter2.configure(d2Hz, dt);
    }

    void setGains(AxisPid gains, double authority) {
        this.kp = gains.p * PidAutoTune.P_SCALE;
        this.ki = gains.i * PidAutoTune.I_SCALE;
        this.kd = gains.d * PidAutoTune.D_SCALE;
        this.kf = authority > 0.0 ? (gains.ff / 100.0) * SimMath.RAD / authority : 0.0;
    }

    void reset(double gyro, double setpoint) {
        this.relaxFilter.reset(setpoint);
        this.ffFilter.reset(0.0);
        this.dFilter1.reset(gyro);
        this.dFilter2.reset(gyro);
        this.prevDGyro = gyro;
        this.prevSpFf = 0.0;
        this.iTerm = 0.0;
        this.pOut = 0.0;
        this.dOut = 0.0;
        this.fOut = 0.0;
        this.sum = 0.0;
        this.output = 0.0;
        this.sumSaturated = false;
    }

    double update(double sp, double spFf, double gyro, double dt, boolean relaxOn, double relaxThreshold, double antiGravity,
                  double tpaP, double tpaD, boolean iActive, boolean saturatedPrev, double iLimit, double sumLimit,
                  double maxRate, double ffLimitFraction) {
        double err = sp - gyro;
        double relaxed = this.relaxFilter.apply(sp);
        double relax = relaxOn ? Math.max(0.0, 1.0 - Math.abs(sp - relaxed) / relaxThreshold) : 1.0;
        double dI = this.ki * err * relax * antiGravity * dt;
        double i = this.iTerm;
        if (!(saturatedPrev && Math.signum(dI) == Math.signum(i) && i != 0.0)) {
            i = SimMath.clamp(i + dI, -iLimit, iLimit);
        }
        this.iTerm = iActive ? i : 0.0;
        this.pOut = this.kp * err * tpaP;
        double g = this.dFilter2.apply(this.dFilter1.apply(gyro));
        this.dOut = -this.kd * (g - this.prevDGyro) / dt * tpaD;
        this.prevDGyro = g;
        double spd = this.ffFilter.apply((spFf - this.prevSpFf) / dt);
        this.prevSpFf = spFf;
        double f = this.kf * spd;
        if (Math.signum(spd) == Math.signum(sp) && Math.abs(sp) > ffLimitFraction * maxRate && ffLimitFraction < 1.0) {
            f *= SimMath.clamp01((maxRate - Math.abs(sp)) / ((1.0 - ffLimitFraction) * maxRate));
        }
        this.fOut = f;
        double total = this.pOut + this.iTerm + this.dOut + this.fOut;
        this.sum = total;
        this.output = SimMath.clamp(total, -sumLimit, sumLimit);
        this.sumSaturated = Math.abs(total) >= sumLimit;
        return this.output;
    }
}
