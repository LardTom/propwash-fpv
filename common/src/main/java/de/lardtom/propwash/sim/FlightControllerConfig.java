package de.lardtom.propwash.sim;

public final class FlightControllerConfig {
    public static final double DEADBAND_3D = 0.05;
    public static final double ARM_ANGLE_MIN = 0.0;
    public static final double ARM_ANGLE_MAX = 180.0;
    public static final double ARM_ANGLE_DEFAULT = 25.0;

    public int version = 1;
    public RateProfile rates = new RateProfile();
    public PidProfile pid = new PidProfile();
    public FilterSettings filters = new FilterSettings();
    public HoldSettings hold = new HoldSettings();
    public double armMaxTiltDeg = ARM_ANGLE_DEFAULT;
    public double armThrottleMax = 0.05;
    public double failsafeDelayS = 0.3;
    public double failsafeLandingS = 1.5;
    public double deadband3d = DEADBAND_3D;

    public FlightControllerConfig() {
    }

    public static FlightControllerConfig defaultsFor(AirframeSpec params) {
        FlightControllerConfig c = new FlightControllerConfig();
        c.pid = PidAutoTune.derive(params, c.filters, c.pid);
        return c;
    }

    public FlightControllerConfig copy() {
        FlightControllerConfig c = new FlightControllerConfig();
        c.version = this.version;
        c.rates = this.rates == null ? null : this.rates.copy();
        c.pid = this.pid == null ? null : this.pid.copy();
        c.filters = this.filters == null ? null : this.filters.copy();
        c.hold = this.hold == null ? null : this.hold.copy();
        c.armMaxTiltDeg = this.armMaxTiltDeg;
        c.armThrottleMax = this.armThrottleMax;
        c.failsafeDelayS = this.failsafeDelayS;
        c.failsafeLandingS = this.failsafeLandingS;
        c.deadband3d = this.deadband3d;
        return c;
    }

    public void sanitize() {
        if (this.version <= 0) {
            this.version = 1;
        }
        if (this.rates == null) {
            this.rates = new RateProfile();
        }
        if (this.pid == null) {
            this.pid = new PidProfile();
        }
        if (this.filters == null) {
            this.filters = new FilterSettings();
        }
        if (this.hold == null) {
            this.hold = new HoldSettings();
        }
        this.rates.sanitize();
        this.pid.sanitize();
        this.filters.sanitize();
        this.hold.sanitize();
        this.armMaxTiltDeg = SimMath.sanitize(this.armMaxTiltDeg, ARM_ANGLE_MIN, ARM_ANGLE_MAX, ARM_ANGLE_DEFAULT);
        this.armThrottleMax = SimMath.sanitize(this.armThrottleMax, 0, 0.3, 0.05);
        this.failsafeDelayS = SimMath.sanitize(this.failsafeDelayS, 0.05, 10, 0.3);
        this.failsafeLandingS = SimMath.sanitize(this.failsafeLandingS, 0, 60, 1.5);
        this.deadband3d = SimMath.sanitize(this.deadband3d, 0.0, 0.2, DEADBAND_3D);
    }
}
