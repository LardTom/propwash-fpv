package de.lardtom.propwash.sim;

final class FlightController {
    static final double ANTI_GRAVITY_HZ = 5.0;
    static final double SAG_FILTER_HZ = 1.0;
    static final double REVERSAL_ITERM_HOLD_S = 0.25;

    final RcProcessor rc = new RcProcessor();
    final AttitudeController attitude = new AttitudeController();
    final AxisPidController[] axes = {new AxisPidController(), new AxisPidController(), new AxisPidController()};
    final Mixer mixer = new Mixer();
    private final Pt1[] gyroFilter = {new Pt1(), new Pt1(), new Pt1()};
    private final Pt1 antiGravityFilter = new Pt1();
    private final Pt1 sagFilter = new Pt1();

    final double[] gyro = new double[3];
    final double[] setpoint = new double[3];
    final double[] setpointFf = new double[3];
    final double[] duty = new double[AirframeSpec.MOTORS];
    final double[] motorOutput = new double[AirframeSpec.MOTORS];

    private AirframeSpec params;
    private FilterSettings filters;
    private RateProfile rates;
    private PidProfile pid;
    double dt;
    int divider = 1;
    boolean airmodeLatched;
    boolean airmodeJustLatched;
    boolean saturatedPrev;
    double throttleOut;
    StabMode activeMode = StabMode.ACRO;
    boolean mode3dActive;
    int direction3d = 1;
    double reversalHoldS;
    private boolean antiGravity3dPrimed;

    void configure(AirframeSpec params, FlightControllerConfig config, FilterSettings filters) {
        this.params = params;
        this.filters = filters;
        this.rates = config.rates;
        this.pid = config.pid;
        this.divider = filters.pidLoop.divider();
        this.dt = this.divider / (double) DroneSim.PHYSICS_HZ;
        this.rc.configure(filters.rcSmoothingHz, this.dt);
        this.attitude.configure(this.pid.feedforwardSmoothingHz, this.dt);
        for (int a = 0; a < 3; a++) {
            this.gyroFilter[a].configure(filters.gyroLpfHz, this.dt);
            this.axes[a].configure(this.pid.itermRelaxCutoffHz, this.pid.feedforwardSmoothingHz, filters.dtermLpf1Hz,
                    filters.dtermLpf2Hz, this.dt);
        }
        this.antiGravityFilter.configure(ANTI_GRAVITY_HZ, this.dt);
        this.sagFilter.configure(SAG_FILTER_HZ, this.dt);
        this.applyGains();
    }

    void applyGains() {
        this.axes[FcAxes.ROLL].setGains(this.pid.roll, this.params.authorityRoll);
        this.axes[FcAxes.PITCH].setGains(this.pid.pitch, this.params.authorityPitch);
        this.axes[FcAxes.YAW].setGains(this.pid.yaw, this.params.authorityYawInstant);
    }

    void updateProfiles(FlightControllerConfig config) {
        this.rates = config.rates;
        this.pid = config.pid;
        this.attitude.configure(this.pid.feedforwardSmoothingHz, this.dt);
        for (int a = 0; a < 3; a++) {
            this.axes[a].configure(this.pid.itermRelaxCutoffHz, this.pid.feedforwardSmoothingHz, this.filters.dtermLpf1Hz,
                    this.filters.dtermLpf2Hz, this.dt);
        }
        this.applyGains();
    }

    void reset(double[] gyroRaw, double busVoltage, double r, double p, double y, double t) {
        for (int a = 0; a < 3; a++) {
            this.gyroFilter[a].reset(gyroRaw[a]);
            this.gyro[a] = gyroRaw[a];
            this.axes[a].reset(gyroRaw[a], 0.0);
            this.setpoint[a] = 0.0;
            this.setpointFf[a] = 0.0;
        }
        this.rc.reset(r, p, y, t, this.rates, this.pid);
        this.attitude.reset();
        this.antiGravityFilter.reset(this.rc.throttleOut);
        this.sagFilter.reset(busVoltage);
        this.airmodeLatched = false;
        this.airmodeJustLatched = false;
        this.saturatedPrev = false;
        this.throttleOut = 0.0;
        this.mode3dActive = false;
        this.direction3d = 1;
        this.reversalHoldS = 0.0;
        this.antiGravity3dPrimed = false;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            this.duty[i] = 0.0;
            this.motorOutput[i] = 0.0;
        }
    }

    void update(double r, double p, double y, double t, StabMode mode, boolean armed, RigidBody body, double[] gyroRaw,
                double busVoltage) {
        this.update(r, p, y, t, mode, armed, body, gyroRaw, busVoltage, false, FlightControllerConfig.DEADBAND_3D);
    }

    void update(double r, double p, double y, double t, StabMode mode, boolean armed, RigidBody body, double[] gyroRaw,
                double busVoltage, boolean mode3d, double deadband3d) {
        PidProfile pid = this.pid;
        RateProfile rates = this.rates;
        double dt = this.dt;
        this.rc.update(r, p, y, t, rates, pid);
        double thr = this.rc.throttleOut;
        if (mode3d) {
            thr = ThrottleCurve.apply(pid, this.magnitude3d(this.rc.throttleStick, deadband3d, armed));
        } else {
            this.direction3d = 1;
            this.reversalHoldS = 0.0;
            this.antiGravity3dPrimed = false;
        }
        this.mode3dActive = mode3d;
        double signed = mode3d && this.direction3d < 0 ? -thr : thr;
        for (int a = 0; a < 3; a++) {
            this.gyro[a] = this.gyroFilter[a].apply(gyroRaw[a]);
        }
        double busFiltered = this.sagFilter.apply(busVoltage);
        double rollStick = this.rc.rollOut;
        double pitchStick = this.rc.pitchOut;
        double yawStick = this.rc.yawOut;
        RatesType type = rates.type;
        this.activeMode = mode;
        switch (mode) {
            case ANGLE -> {
                double yawRate = RateCurves.rateDps(type, rates.yaw, yawStick);
                this.attitude.angle(body, rollStick, pitchStick, yawRate, pid, dt, this.setpoint);
                this.setpointFf[FcAxes.ROLL] = 0.0;
                this.setpointFf[FcAxes.PITCH] = 0.0;
                this.setpointFf[FcAxes.YAW] = this.setpoint[FcAxes.YAW];
            }
            case HORIZON -> {
                this.setpoint[FcAxes.ROLL] = RateCurves.rateDps(type, rates.roll, rollStick);
                this.setpoint[FcAxes.PITCH] = RateCurves.rateDps(type, rates.pitch, pitchStick);
                this.setpoint[FcAxes.YAW] = RateCurves.rateDps(type, rates.yaw, yawStick);
                for (int a = 0; a < 3; a++) {
                    this.setpointFf[a] = this.setpoint[a];
                }
                this.attitude.horizon(body, rollStick, pitchStick, pid, this.setpoint);
            }
            default -> {
                this.setpoint[FcAxes.ROLL] = RateCurves.rateDps(type, rates.roll, rollStick);
                this.setpoint[FcAxes.PITCH] = RateCurves.rateDps(type, rates.pitch, pitchStick);
                this.setpoint[FcAxes.YAW] = RateCurves.rateDps(type, rates.yaw, yawStick);
                for (int a = 0; a < 3; a++) {
                    this.setpointFf[a] = this.setpoint[a];
                }
                this.attitude.reset();
            }
        }

        this.airmodeJustLatched = false;
        if (armed && pid.airmode && !this.airmodeLatched && thr >= pid.airmodeStartThrottlePercent / 100.0) {
            this.airmodeLatched = true;
            this.airmodeJustLatched = true;
        }
        boolean airmodeActive = armed && pid.airmode && this.airmodeLatched;
        boolean iActive = armed && (pid.airmode ? this.airmodeLatched : thr > 0.05);
        double ag;
        if (mode3d) {
            if (this.reversalHoldS > 0.0) {
                this.reversalHoldS -= dt;
                iActive = false;
            }
            if (!this.antiGravity3dPrimed) {
                this.antiGravityFilter.reset(signed);
                this.antiGravity3dPrimed = true;
            }
            double agSigned = this.antiGravityFilter.apply(signed);
            ag = 1.0 + (pid.antiGravityGain / 20.0) * SimMath.clamp01(8.0 * Math.abs(signed - agSigned));
        } else {
            double agThr = this.antiGravityFilter.apply(thr);
            ag = 1.0 + (pid.antiGravityGain / 20.0) * SimMath.clamp01(8.0 * Math.abs(thr - agThr));
        }
        double tpa = 1.0 - (pid.tpaRatePercent / 100.0)
                * SimMath.clamp01((thr - pid.tpaBreakpoint) / Math.max(1e-6, 1.0 - pid.tpaBreakpoint));
        double tpaP = pid.tpaMode == TpaMode.PD ? tpa : 1.0;
        double iLimit = pid.itermLimit / 1000.0;
        double ffLimit = pid.ffMaxRateLimitPercent / 100.0;
        ItermRelaxMode relaxMode = pid.itermRelax;
        boolean anySumSat = false;
        for (int a = 0; a < 3; a++) {
            boolean yaw = a == FcAxes.YAW;
            boolean relaxOn = relaxMode == ItermRelaxMode.RPY || (relaxMode == ItermRelaxMode.RP && !yaw);
            double sumLimit = (yaw ? pid.pidSumLimitYaw : pid.pidSumLimit) / 1000.0;
            RateCurve axisRates = a == FcAxes.ROLL ? rates.roll : (a == FcAxes.PITCH ? rates.pitch : rates.yaw);
            double maxRate = Math.max(1.0, RateCurves.maxRateDps(type, axisRates));
            this.axes[a].update(this.setpoint[a], this.setpointFf[a], this.gyro[a], dt, relaxOn, pid.itermRelaxThresholdDps,
                    yaw ? 1.0 : ag, tpaP, tpa, iActive, this.saturatedPrev, iLimit, sumLimit, maxRate, ffLimit);
            anySumSat |= this.axes[a].sumSaturated;
        }
        double tl = pid.thrustLinearPercent / 100.0;
        boolean reversed = mode3d && this.direction3d < 0;
        if (reversed) {
            this.mixer.mix(this.params, -this.axes[FcAxes.ROLL].output, -this.axes[FcAxes.PITCH].output, -this.axes[FcAxes.YAW].output,
                    compensateThrustLinear(thr, tl), airmodeActive, pid.propsOut);
        } else {
            this.mixer.mix(this.params, this.axes[FcAxes.ROLL].output, this.axes[FcAxes.PITCH].output, this.axes[FcAxes.YAW].output,
                    compensateThrustLinear(thr, tl), airmodeActive, pid.propsOut);
        }
        this.saturatedPrev = anySumSat || this.mixer.saturated;
        this.throttleOut = this.mixer.throttle;
        double idle = pid.motorIdlePercent / 100.0;
        double range = (1.0 - idle) * (pid.motorOutputLimitPercent / 100.0) * (1.0 - this.sagAttenuation(busFiltered));
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double o = linearizeThrust(this.mixer.output[i], tl);
            this.motorOutput[i] = o;
            if (reversed) {
                this.duty[i] = armed ? -(idle + range * o) : 0.0;
            } else {
                this.duty[i] = armed ? idle + range * o : 0.0;
            }
        }
    }

    double magnitude3d(double stick, double deadband, boolean armed) {
        double low = 0.5 - deadband;
        double high = 0.5 + deadband;
        int previous = this.direction3d;
        double magnitude;
        if (stick <= low) {
            this.direction3d = -1;
            magnitude = low > 0.0 ? (low - stick) / low : 1.0;
        } else if (stick >= high) {
            this.direction3d = 1;
            magnitude = high < 1.0 ? (stick - high) / (1.0 - high) : 1.0;
        } else {
            if (!armed) {
                this.direction3d = 1;
            }
            magnitude = 0.0;
        }
        if (armed && previous != this.direction3d) {
            this.reversalHoldS = REVERSAL_ITERM_HOLD_S;
        }
        return SimMath.clamp01(magnitude);
    }

    double signedThrottleOut() {
        return this.mode3dActive && this.direction3d < 0 ? -this.throttleOut : this.throttleOut;
    }

    static double compensateThrustLinear(double throttle, double tl) {
        return tl > 0.0 ? tl * throttle * throttle + (1.0 - tl) * throttle : throttle;
    }

    static double linearizeThrust(double output, double tl) {
        if (tl <= 0.0) {
            return output;
        }
        double b = (1.0 - tl) / (2.0 * tl);
        return Math.sqrt(output / tl + b * b) - b;
    }

    double sagAttenuation(double busVoltage) {
        double comp = this.pid.vbatSagCompensationPercent / 100.0;
        if (comp <= 0.0 || !(busVoltage > 0.5)) {
            return 0.0;
        }
        double full = this.params.chemistry.fullCellVoltage;
        double span = full - this.params.chemistry.emptyCellVoltage;
        if (span <= 0.0) {
            return 0.0;
        }
        double goodness = SimMath.clamp01((busVoltage / this.params.cells - this.params.chemistry.emptyCellVoltage) / span);
        return comp * span / full * goodness;
    }

    double outputForDuty(double duty, double busVoltage) {
        PidProfile pid = this.pid;
        double idle = pid.motorIdlePercent / 100.0;
        double range = (1.0 - idle) * (pid.motorOutputLimitPercent / 100.0) * (1.0 - this.sagAttenuation(busVoltage));
        return (duty - idle) / range;
    }

    PidProfile pid() {
        return this.pid;
    }

    RateProfile rates() {
        return this.rates;
    }
}
