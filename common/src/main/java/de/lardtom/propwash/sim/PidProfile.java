package de.lardtom.propwash.sim;

public final class PidProfile {
    public PidSource source = PidSource.DERIVED;
    public String derivedForBuild = "";
    public String derivedForFilters = "";
    public AxisPid roll = new AxisPid(42, 49, 31, 120);
    public AxisPid pitch = new AxisPid(43, 51, 32, 120);
    public AxisPid yaw = new AxisPid(51, 67, 0, 120);
    public double pidSumLimit = 500;
    public double pidSumLimitYaw = 400;
    public double itermLimit = 400;
    public double feedforwardSmoothingHz = 30;
    public double ffMaxRateLimitPercent = 90;
    public double angleStrength = 107;
    public double angleLimitDeg = 60;
    public double angleFeedforwardPercent = 100;
    public double angleMaxRateDps = 500;
    public double angleEarthRefPercent = 100;
    public double horizonStrength = 50;
    public double horizonStickTransitionPercent = 75;
    public double horizonLimitDeg = 135;
    public ItermRelaxMode itermRelax = ItermRelaxMode.RP;
    public double itermRelaxCutoffHz = 15;
    public double itermRelaxThresholdDps = 40;
    public double antiGravityGain = 80;
    public TpaMode tpaMode = TpaMode.D;
    public double tpaRatePercent = 65;
    public double tpaBreakpoint = 0.35;
    public boolean airmode = true;
    public double airmodeStartThrottlePercent = 25;
    public double motorIdlePercent = 5.5;
    public double thrustLinearPercent = 0;
    public double vbatSagCompensationPercent = 0;
    public double motorOutputLimitPercent = 100;
    public boolean propsOut = false;
    public double throttleMid = 0.5;
    public double throttleExpo = 0.0;
    public ThrottleLimitType throttleLimitType = ThrottleLimitType.OFF;
    public double throttleLimitPercent = 100;

    public PidProfile() {
    }

    public AxisPid axis(Axis axis) {
        return switch (axis) {
            case ROLL -> this.roll;
            case PITCH -> this.pitch;
            case YAW -> this.yaw;
        };
    }

    public PidProfile copy() {
        PidProfile c = new PidProfile();
        c.source = this.source;
        c.derivedForBuild = this.derivedForBuild;
        c.derivedForFilters = this.derivedForFilters;
        c.roll = this.roll == null ? null : this.roll.copy();
        c.pitch = this.pitch == null ? null : this.pitch.copy();
        c.yaw = this.yaw == null ? null : this.yaw.copy();
        c.pidSumLimit = this.pidSumLimit;
        c.pidSumLimitYaw = this.pidSumLimitYaw;
        c.itermLimit = this.itermLimit;
        c.feedforwardSmoothingHz = this.feedforwardSmoothingHz;
        c.ffMaxRateLimitPercent = this.ffMaxRateLimitPercent;
        c.angleStrength = this.angleStrength;
        c.angleLimitDeg = this.angleLimitDeg;
        c.angleFeedforwardPercent = this.angleFeedforwardPercent;
        c.angleMaxRateDps = this.angleMaxRateDps;
        c.angleEarthRefPercent = this.angleEarthRefPercent;
        c.horizonStrength = this.horizonStrength;
        c.horizonStickTransitionPercent = this.horizonStickTransitionPercent;
        c.horizonLimitDeg = this.horizonLimitDeg;
        c.itermRelax = this.itermRelax;
        c.itermRelaxCutoffHz = this.itermRelaxCutoffHz;
        c.itermRelaxThresholdDps = this.itermRelaxThresholdDps;
        c.antiGravityGain = this.antiGravityGain;
        c.tpaMode = this.tpaMode;
        c.tpaRatePercent = this.tpaRatePercent;
        c.tpaBreakpoint = this.tpaBreakpoint;
        c.airmode = this.airmode;
        c.airmodeStartThrottlePercent = this.airmodeStartThrottlePercent;
        c.motorIdlePercent = this.motorIdlePercent;
        c.thrustLinearPercent = this.thrustLinearPercent;
        c.vbatSagCompensationPercent = this.vbatSagCompensationPercent;
        c.motorOutputLimitPercent = this.motorOutputLimitPercent;
        c.propsOut = this.propsOut;
        c.throttleMid = this.throttleMid;
        c.throttleExpo = this.throttleExpo;
        c.throttleLimitType = this.throttleLimitType;
        c.throttleLimitPercent = this.throttleLimitPercent;
        return c;
    }

    public void sanitize() {
        PidProfile d = DEFAULTS;
        if (this.source == null) {
            this.source = PidSource.DERIVED;
        }
        if (this.derivedForBuild == null) {
            this.derivedForBuild = "";
        }
        if (this.derivedForFilters == null) {
            this.derivedForFilters = "";
        }
        if (this.roll == null) {
            this.roll = d.roll.copy();
        }
        if (this.pitch == null) {
            this.pitch = d.pitch.copy();
        }
        if (this.yaw == null) {
            this.yaw = d.yaw.copy();
        }
        this.roll.sanitize(d.roll);
        this.pitch.sanitize(d.pitch);
        this.yaw.sanitize(d.yaw);
        this.pidSumLimit = SimMath.sanitize(this.pidSumLimit, 100, 1000, d.pidSumLimit);
        this.pidSumLimitYaw = SimMath.sanitize(this.pidSumLimitYaw, 100, 1000, d.pidSumLimitYaw);
        this.itermLimit = SimMath.sanitize(this.itermLimit, 0, 1000, d.itermLimit);
        this.feedforwardSmoothingHz = SimMath.sanitize(this.feedforwardSmoothingHz, 5, 200, d.feedforwardSmoothingHz);
        this.ffMaxRateLimitPercent = SimMath.sanitize(this.ffMaxRateLimitPercent, 0, 100, d.ffMaxRateLimitPercent);
        this.angleStrength = SimMath.sanitize(this.angleStrength, 0, 200, d.angleStrength);
        this.angleLimitDeg = SimMath.sanitize(this.angleLimitDeg, 10, 85, d.angleLimitDeg);
        this.angleFeedforwardPercent = SimMath.sanitize(this.angleFeedforwardPercent, 0, 200, d.angleFeedforwardPercent);
        this.angleMaxRateDps = SimMath.sanitize(this.angleMaxRateDps, 100, 1998, d.angleMaxRateDps);
        this.angleEarthRefPercent = SimMath.sanitize(this.angleEarthRefPercent, 0, 100, d.angleEarthRefPercent);
        this.horizonStrength = SimMath.sanitize(this.horizonStrength, 0, 200, d.horizonStrength);
        this.horizonStickTransitionPercent = SimMath.sanitize(this.horizonStickTransitionPercent, 10, 100, d.horizonStickTransitionPercent);
        this.horizonLimitDeg = SimMath.sanitize(this.horizonLimitDeg, 10, 180, d.horizonLimitDeg);
        if (this.itermRelax == null) {
            this.itermRelax = d.itermRelax;
        }
        this.itermRelaxCutoffHz = SimMath.sanitize(this.itermRelaxCutoffHz, 1, 50, d.itermRelaxCutoffHz);
        this.itermRelaxThresholdDps = SimMath.sanitize(this.itermRelaxThresholdDps, 5, 200, d.itermRelaxThresholdDps);
        this.antiGravityGain = SimMath.sanitize(this.antiGravityGain, 0, 250, d.antiGravityGain);
        if (this.tpaMode == null) {
            this.tpaMode = d.tpaMode;
        }
        this.tpaRatePercent = SimMath.sanitize(this.tpaRatePercent, 0, 100, d.tpaRatePercent);
        this.tpaBreakpoint = SimMath.sanitize(this.tpaBreakpoint, 0, 0.95, d.tpaBreakpoint);
        this.airmodeStartThrottlePercent = SimMath.sanitize(this.airmodeStartThrottlePercent, 0, 50, d.airmodeStartThrottlePercent);
        this.motorIdlePercent = SimMath.sanitize(this.motorIdlePercent, 0, 20, d.motorIdlePercent);
        this.thrustLinearPercent = SimMath.sanitize(this.thrustLinearPercent, 0, 100, d.thrustLinearPercent);
        this.vbatSagCompensationPercent = SimMath.sanitize(this.vbatSagCompensationPercent, 0, 100, d.vbatSagCompensationPercent);
        this.motorOutputLimitPercent = SimMath.sanitize(this.motorOutputLimitPercent, 25, 100, d.motorOutputLimitPercent);
        this.throttleMid = SimMath.sanitize(this.throttleMid, 0.05, 0.95, d.throttleMid);
        this.throttleExpo = SimMath.sanitize(this.throttleExpo, 0, 1, d.throttleExpo);
        if (this.throttleLimitType == null) {
            this.throttleLimitType = d.throttleLimitType;
        }
        this.throttleLimitPercent = SimMath.sanitize(this.throttleLimitPercent, 25, 100, d.throttleLimitPercent);
    }

    private static final PidProfile DEFAULTS = new PidProfile();
}
