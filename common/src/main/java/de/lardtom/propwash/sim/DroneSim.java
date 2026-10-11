package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class DroneSim {
    public static final int PHYSICS_HZ = 2000;
    public static final long SUBSTEP_NANOS = 500_000L;
    public static final int MAX_SUBSTEPS = 100;
    public static final double MAX_FRAME_DT = 0.05;

    static final double H = 1.0 / PHYSICS_HZ;
    static final long MAX_FRAME_NANOS = 50_000_000L;
    static final long MAX_DT_NANOS = 200_000_000L;
    static final int SLOW_DIVIDER = 8;
    static final int HOVER_ESTIMATE_DIVIDER = 200;
    static final double SLOW_DT = SLOW_DIVIDER * H;
    static final long BROWNOUT_SUBSTEPS = 10;
    static final double BROWNOUT_RELEASE = 1.05;
    static final long WATER_SUBSTEPS = 600;
    static final long SLEEP_SUBSTEPS = 1000;
    static final long WARN_SLOW_STEPS = 500;
    static final long ESC_EVENT_SUBSTEPS = 1000;
    static final double WATER_DRAG = 400.0;
    static final double SLEEP_SPEED = 0.05;
    static final double SLEEP_RATE = 0.1;
    static final double RIGHTING_RATE = 4.0;
    static final double CONTACT_RATE = 0.2;
    static final double GROUND_TOLERANCE = 0.001;
    static final double RESTING_TOLERANCE = 0.002;
    static final double WAKE_THRESHOLD = 0.02;
    static final double SUPPORT_TOLERANCE = 0.002;
    static final int MAX_FAULTS_PER_SECOND = 3;
    static final int CONTACT_MEMORY_FRAMES = 2;
    static final double OBLIQUE_SAME_CONTACT = 0.95;
    static final double OBLIQUE_GROUND = 0.7;
    static final double IMPACT_RECORD_SPEED = 1.0;

    private AirframeSpec params;
    private FlightControllerConfig config;
    private FilterSettings activeFilters;
    private boolean filtersPending;
    private SimTuning tuning;

    final RigidBody body = new RigidBody();
    final PowerSystem power = new PowerSystem();
    final FlightController fc = new FlightController();
    final GyroModel gyroModel = new GyroModel();
    final PropWash wash = new PropWash();
    final WakeField wake = new WakeField();
    final Gusts gusts = new Gusts();
    final Thermal thermal = new Thermal();
    final BodyAero bodyAero = new BodyAero();
    final CollisionSolver collision = new CollisionSolver();
    final BreakSolver breakSolver = new BreakSolver();
    final Arming arming = new Arming();
    final SimRandom rng;
    final NavHold nav;
    private final Telemetry telemetry = new Telemetry();
    private final EventQueue events = new EventQueue();
    private final ImpactLog impacts = new ImpactLog();
    private final SimSnapshot rollback = new SimSnapshot();
    private final SimSnapshot incoming = new SimSnapshot();
    private final PilotInput input = new PilotInput();
    private final SimEnvironment env = new SimEnvironment();
    private final OperatingPoints.Point hoverPoint = new OperatingPoints.Point();

    final double[] dutyCur = new double[AirframeSpec.MOTORS];
    final double[] dutyNext = new double[AirframeSpec.MOTORS];
    private final double[] lambda = new double[AirframeSpec.MOTORS];
    private final double[] axial = new double[AirframeSpec.MOTORS];
    private final double[] crossX = new double[AirframeSpec.MOTORS];
    private final double[] crossZ = new double[AirframeSpec.MOTORS];
    private final double[] cross = new double[AirframeSpec.MOTORS];
    final double[] thrust = new double[AirframeSpec.MOTORS];
    private final double[] prevThrust = new double[AirframeSpec.MOTORS];
    private final double[] groundFactor = new double[AirframeSpec.MOTORS];
    private final long[] escEventCooldown = new long[AirframeSpec.MOTORS];
    private final double[] overrideDuty = new double[AirframeSpec.MOTORS];
    final double[] wearThrust = {1.0, 1.0, 1.0, 1.0};
    final boolean[] motorDead = new boolean[AirframeSpec.MOTORS];
    private PartWear wear = PartWear.PRISTINE;
    private double wearVibration;
    private double wearGyroNoise;
    private boolean hardwareFault;
    private boolean escBurned;

    private double fromRoll;
    private double fromPitch;
    private double fromYaw;
    private double fromThrottle;
    private double appliedRoll;
    private double appliedPitch;
    private double appliedYaw;
    private double appliedThrottle;
    private boolean rampInitialized;

    private long accumulatorNanos;
    private long substepCount;
    private long fcTick;
    private double time;
    private double frameStartY;
    private double visX;
    private double visY;
    private double visZ;

    boolean sleeping;
    private long sleepCounter;
    int groundedFrames;
    private final int[] contactFrames = new int[3];
    private final double[] contactSign = new double[3];
    private int obliqueFrames;
    private double obliqueX;
    private double obliqueY;
    private double obliqueZ;
    private double frameStartVx;
    private double frameStartVy;
    private double frameStartVz;
    private double groundHeight;
    private double groundGrip = 1.0;
    private double normalForce;

    private long brownoutCounter;
    private boolean brownoutLatched;
    private long waterCounter;
    private final BatteryAlarm lowAlarm = new BatteryAlarm();
    private final BatteryAlarm criticalAlarm = new BatteryAlarm();
    private boolean emptyFired;
    private BatteryState batteryState = BatteryState.OK;
    private final Pt1 cellFilter = new Pt1();
    private final Pt1 gLoadFilter = new Pt1();
    private final Pt1 washFilter = new Pt1();
    private double gLoadMax;
    private double specificX;
    private double specificY;
    private double specificZ;
    private double totalThrust;
    private double maxGroundFactor = 1.0;
    private double wakeWeight;
    private double spinSign = 1.0;
    private double hoverStick;
    private double windX;
    private double windY;
    private double windZ;
    private int faults;
    private int crashCount;
    private int impactCount;
    private int propStrikeCount;
    private int breakCount;
    private BreakReport lastBreak;
    private double lastImpactSpeed;
    private double faultWindowStart = Double.NEGATIVE_INFINITY;
    private int faultsInWindow;

    private double gravity = ModelConstants.G;
    boolean poseLocked;
    boolean dutyOverrideActive;
    private boolean injectFault;
    private boolean injectThrustFault;
    private boolean frameAborted;
    private boolean reversibleActive;
    private FcListener fcListener;
    private final FcSample fcSample = new FcSample();

    public DroneSim(DroneBuild build, FlightControllerConfig config, SimTuning tuning, long seed) {
        this(AirframeSpec.of(build), config, tuning, seed);
    }

    public DroneSim(AirframeSpec params, FlightControllerConfig config, SimTuning tuning, long seed) {
        this.rng = new SimRandom(seed);
        this.nav = new NavHold(seed);
        this.tuning = tuning == null ? new SimTuning() : tuning.copy();
        this.tuning.sanitize();
        this.gravity = this.tuning.gravity();
        this.nav.gravity = this.gravity;
        this.applyParams(params);
        this.power.setStateOfCharge(1.0);
        this.applyConfig(config);
        this.reset(0.0, 0.0, 0.0, 0.0);
    }

    private void applyParams(AirframeSpec p) {
        this.params = p;
        this.body.configure(p.massKg, p.inertiaXx, p.inertiaYy, p.inertiaZz);
        this.power.configure(p, H);
        this.wash.configure(p, H);
        this.gusts.configure(SLOW_DT);
        this.wake.configure(SLOW_DT);
        this.gyroModel.configure(H);
        this.cellFilter.configure(1.0, SLOW_DT);
        this.gLoadFilter.configure(20.0, H);
        this.washFilter.configure(10.0, H);
    }

    private void applyConfig(FlightControllerConfig source) {
        FlightControllerConfig c = source == null ? new FlightControllerConfig() : source.copy();
        c.sanitize();
        if (c.pid.source == PidSource.DERIVED && !PidAutoTune.isCurrent(this.params, c.filters, c.pid)) {
            c.pid = PidAutoTune.derive(this.params, c.filters, c.pid);
        }
        this.config = c;
        this.spinSign = c.pid.propsOut ? -1.0 : 1.0;
        if (this.activeFilters == null || !this.arming.armed) {
            this.activeFilters = c.filters.copy();
            this.filtersPending = false;
            this.fc.configure(this.params, c, this.activeFilters);
        } else {
            this.filtersPending = !c.filters.sameAs(this.activeFilters);
            FlightControllerConfig live = c;
            if (this.filtersPending && c.pid.source == PidSource.DERIVED) {
                live = c.copy();
                live.pid = PidAutoTune.derive(this.params, this.activeFilters, c.pid);
            }
            this.fc.updateProfiles(live);
        }
    }

    public void setFcListener(FcListener listener) {
        this.fcListener = listener;
    }

    public FcListener fcListener() {
        return this.fcListener;
    }

    public PidProfile activePid() {
        return this.fc.pid().copy();
    }

    public RateProfile activeRates() {
        return this.fc.rates().copy();
    }

    public FilterSettings activeFilters() {
        return this.activeFilters.copy();
    }

    private void publishFcSample(double r, double p, double y, double thr, StabMode mode, boolean mode3d) {
        FcSample s = this.fcSample;
        FlightController fc = this.fc;
        Arming a = this.arming;
        RateProfile rates = fc.rates();
        s.substep = this.substepCount;
        s.loopDivider = fc.divider;
        s.armed = a.armed;
        s.mode = mode;
        s.mode3d = mode3d;
        s.airmode = a.armed && fc.pid().airmode && fc.airmodeLatched;
        s.failsafeStage = a.failsafeStage;
        s.linkOk = this.input.linkOk;
        s.hold = this.nav.mode;
        s.stick[0] = RcProcessor.deadband(r, rates.rcDeadband);
        s.stick[1] = RcProcessor.deadband(p, rates.rcDeadband);
        s.stick[2] = RcProcessor.deadband(y, rates.yawDeadband);
        s.stick[3] = thr;
        s.mixerThrottle = fc.signedThrottleOut();
        for (int i = 0; i < 3; i++) {
            AxisPidController axis = fc.axes[i];
            s.setpoint[i] = fc.setpoint[i];
            s.gyro[i] = fc.gyro[i];
            s.gyroRaw[i] = this.gyroModel.raw[i];
            s.pTerm[i] = axis.pOut * 1000.0;
            s.iTerm[i] = axis.iTerm * 1000.0;
            s.dTerm[i] = axis.dOut * 1000.0;
            s.fTerm[i] = axis.fOut * 1000.0;
        }
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            s.motorDuty[i] = this.dutyNext[i];
            s.motorRpm[i] = this.power.omega[i] * 60.0 / SimMath.TWO_PI;
        }
        s.voltage = this.power.busVoltage;
        s.current = this.power.batteryCurrent;
        GpsReceiver gps = this.nav.gps;
        s.baroAltitude = this.nav.baro.altitude;
        s.gpsSatellites = gps.satellites;
        s.gpsFix = gps.fix;
        s.gpsX = gps.x;
        s.gpsY = gps.y;
        s.gpsZ = gps.z;
        s.gpsVx = gps.vx;
        s.gpsVz = gps.vz;
        this.fcListener.onFcSample(this, s);
    }

    public AirframeSpec params() {
        return this.params;
    }

    public FlightControllerConfig config() {
        return this.config.copy();
    }

    public SimTuning tuning() {
        return this.tuning.copy();
    }

    public double gravity() {
        return this.gravity;
    }

    public void reset(double x, double y, double z, double headingRad) {
        RigidBody b = this.body;
        b.px = SimMath.finiteOr(x, 0.0);
        b.py = SimMath.finiteOr(y, 0.0);
        b.pz = SimMath.finiteOr(z, 0.0);
        b.vx = 0.0;
        b.vy = 0.0;
        b.vz = 0.0;
        b.lx = 0.0;
        b.ly = 0.0;
        b.lz = 0.0;
        b.setUpright(SimMath.finiteOr(headingRad, 0.0));
        this.power.stopRotors();
        this.arming.reset();
        this.nav.reset();
        this.clearDuties();
        this.clearTransientState();
        this.thermal.reset(this.env.ambientTempC);
        this.fc.reset(this.gyroModel.raw, this.power.busVoltage, 0.0, 0.0, 0.0, 0.0);
        this.accumulatorNanos = 0L;
        this.rampInitialized = false;
        this.appliedRoll = 0.0;
        this.appliedPitch = 0.0;
        this.appliedYaw = 0.0;
        this.appliedThrottle = 0.0;
        this.visX = b.px;
        this.visY = b.py;
        this.visZ = b.pz;
        this.frameStartY = b.py;
        this.groundedFrames = 0;
        this.clearContacts();
        this.sleeping = false;
        this.sleepCounter = 0L;
        this.brownoutCounter = 0L;
        this.brownoutLatched = false;
        this.waterCounter = 0L;
        this.arming.crashCooldownSubsteps = 0L;
        this.arming.rebootSubsteps = 0L;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            this.escEventCooldown[i] = 0L;
        }
        this.gLoadMax = 0.0;
        this.updateHoverEstimate();
        this.fillTelemetry(0);
    }

    private void clearTransientState() {
        this.wash.reset();
        this.wake.reset();
        this.gusts.reset();
        this.gyroModel.reset();
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            this.prevThrust[i] = 0.0;
            this.thrust[i] = 0.0;
            this.groundFactor[i] = 1.0;
        }
        this.gLoadFilter.reset(0.0);
        this.washFilter.reset(0.0);
        this.wakeWeight = 0.0;
        this.totalThrust = 0.0;
        this.maxGroundFactor = 1.0;
        this.specificX = 0.0;
        this.specificY = 0.0;
        this.specificZ = 0.0;
        this.normalForce = 0.0;
        this.windX = 0.0;
        this.windY = 0.0;
        this.windZ = 0.0;
    }

    private void clearContacts() {
        for (int a = 0; a < 3; a++) {
            this.contactFrames[a] = 0;
            this.contactSign[a] = 0.0;
        }
        this.obliqueFrames = 0;
    }

    public void setBatteryCharge(double chargeFraction) {
        this.power.setStateOfCharge(chargeFraction);
        this.brownoutCounter = 0L;
        this.brownoutLatched = false;
        this.clearBatteryAlarms();
        this.power.extraPower = EnergyModel.extraPower(this.params, this.power.omega, this.arming.armed);
        this.power.fullLoad = EnergyModel.fullLoad(this.params, this.power.omega, this.arming.armed);
        this.cellFilter.reset(this.power.packVoltage(this.tuning.batterySag) / this.params.cells);
        this.updateHoverEstimate();
        this.fillTelemetry(0);
    }

    public boolean setBuild(DroneBuild build) {
        if (this.arming.armed || build == null) {
            return false;
        }
        double soc = this.power.soc;
        this.escBurned = false;
        this.applyParams(AirframeSpec.of(build));
        this.power.stopRotors();
        this.power.setStateOfCharge(Math.max(0.0, soc));
        this.activeFilters = null;
        this.applyConfig(this.config);
        this.clearDuties();
        this.wakeUp();
        this.updateHoverEstimate();
        this.applyWear(this.wear);
        this.fillTelemetry(0);
        return true;
    }

    public boolean setAirframe(AirframeSpec params) {
        if (params == null || params == this.params) {
            return false;
        }
        this.applyParams(params);
        this.power.extraPower = EnergyModel.extraPower(this.params, this.power.omega, this.arming.armed);
        this.power.fullLoad = EnergyModel.fullLoad(this.params, this.power.omega, this.arming.armed);
        this.updateHoverEstimate();
        this.applyWear(this.wear);
        this.fillTelemetry(this.telemetry.substeps);
        return true;
    }

    public PartWear wear() {
        return this.wear;
    }

    public void setWear(PartWear wear) {
        this.applyWear(wear);
        if (this.hardwareFault && this.arming.armed) {
            this.disarm(DisarmReason.HARDWARE);
        }
        this.fillTelemetry(this.telemetry.substeps);
    }

    private void applyWear(PartWear wear) {
        PartWear w = wear == null ? PartWear.PRISTINE : wear;
        this.wear = w;
        for (int position = 0; position < PartWear.POSITIONS; position++) {
            int motor = PartWear.simMotor(position);
            this.wearThrust[motor] = WearEffects.thrust(w, position, this.params.propBrittleness);
            this.power.loadScale[motor] = WearEffects.propLoad(w.prop(position));
            this.motorDead[motor] = WearEffects.motorDead(w.motor(position));
        }
        this.wearVibration = WearEffects.vibration(w, this.params.propBrittleness);
        this.wearGyroNoise = WearEffects.gyroNoiseDps(w);
        this.power.batteryResistanceScale = WearEffects.batteryResistance(w);
        this.hardwareFault = WearEffects.hardwareFault(w);
    }

    public void setConfig(FlightControllerConfig config) {
        this.applyConfig(config);
        this.updateHoverEstimate();
    }

    public void setTuning(SimTuning tuning) {
        SimTuning t = tuning == null ? new SimTuning() : tuning.copy();
        t.sanitize();
        this.tuning = t;
        double g = t.gravity();
        if (g != this.gravity) {
            this.gravity = g;
            this.nav.gravity = g;
            this.updateHoverEstimate();
        }
    }

    public void step(double frameDtSeconds, PilotInput input, SimEnvironment env, StepResult out) {
        this.events.clear();
        this.impacts.clear();
        double dt = Double.isFinite(frameDtSeconds) && frameDtSeconds >= 0.0 ? frameDtSeconds : 0.0;
        long nanos = Math.round(Math.min(dt, MAX_DT_NANOS * 1e-9) * 1e9);
        this.advance(nanos, dt, input, env, out);
    }

    public void step(long frameNanos, PilotInput input, SimEnvironment env, StepResult out) {
        this.events.clear();
        this.impacts.clear();
        long nanos = Math.max(frameNanos, 0L);
        this.advance(nanos, nanos * 1e-9, input, env, out);
    }

    private void advance(long frameNanos, double originalDt, PilotInput in, SimEnvironment environment, StepResult out) {
        long nanos = frameNanos;
        if (nanos > MAX_FRAME_NANOS) {
            this.emit(SimEventType.FRAME_HITCH, originalDt, -1);
            nanos = MAX_FRAME_NANOS;
        }
        this.sanitizeInput(in);
        this.sanitizeEnvironment(environment);
        this.accumulatorNanos += nanos;
        long n = this.accumulatorNanos / SUBSTEP_NANOS;
        if (n > MAX_SUBSTEPS) {
            n = MAX_SUBSTEPS;
            this.accumulatorNanos = 0L;
        } else {
            this.accumulatorNanos -= n * SUBSTEP_NANOS;
        }
        int substeps = (int) n;
        this.copyStateTo(this.rollback);
        PilotInput pi = this.input;
        if (!this.rampInitialized) {
            this.appliedRoll = pi.roll;
            this.appliedPitch = pi.pitch;
            this.appliedYaw = pi.yaw;
            this.appliedThrottle = pi.throttle;
            this.rampInitialized = true;
        }
        this.fromRoll = this.appliedRoll;
        this.fromPitch = this.appliedPitch;
        this.fromYaw = this.appliedYaw;
        this.fromThrottle = this.appliedThrottle;
        this.frameArming();
        this.frameStartY = this.body.py;
        this.frameStartVx = this.body.vx;
        this.frameStartVy = this.body.vy;
        this.frameStartVz = this.body.vz;
        this.frameAborted = false;
        for (int k = 0; k < substeps; k++) {
            this.substep(k, substeps);
            if (this.frameAborted) {
                break;
            }
        }
        this.finishFrame(substeps, out);
    }

    private void sanitizeInput(PilotInput in) {
        PilotInput d = this.input;
        if (in == null) {
            d.roll = 0.0;
            d.pitch = 0.0;
            d.yaw = 0.0;
            d.throttle = 0.0;
            d.mode = StabMode.ACRO;
            d.armSwitch = false;
            d.linkOk = true;
            d.altHold = false;
            d.posHold = false;
            d.mode3d = false;
            return;
        }
        d.roll = SimMath.clamp(SimMath.finiteOr(in.roll, 0.0), -1.0, 1.0);
        d.pitch = SimMath.clamp(SimMath.finiteOr(in.pitch, 0.0), -1.0, 1.0);
        d.yaw = SimMath.clamp(SimMath.finiteOr(in.yaw, 0.0), -1.0, 1.0);
        d.throttle = SimMath.clamp01(SimMath.finiteOr(in.throttle, 0.0));
        d.mode = in.mode == null ? StabMode.ACRO : in.mode;
        d.armSwitch = in.armSwitch;
        d.linkOk = in.linkOk;
        d.altHold = in.altHold;
        d.posHold = in.posHold;
        d.mode3d = in.mode3d;
    }

    private void sanitizeEnvironment(SimEnvironment e) {
        SimEnvironment d = this.env;
        if (e == null) {
            d.groundDistance = Double.POSITIVE_INFINITY;
            d.windX = 0.0;
            d.windY = 0.0;
            d.windZ = 0.0;
            d.gustIntensity = 0.0;
            d.airDensity = ModelConstants.RHO0;
            d.ambientTempC = 20.0;
            d.fluid = Fluid.NONE;
            d.skyView = 1.0;
            return;
        }
        d.groundDistance = Double.isNaN(e.groundDistance) ? Double.POSITIVE_INFINITY : Math.max(0.0, e.groundDistance);
        d.windX = SimMath.clamp(SimMath.finiteOr(e.windX, 0.0), -100.0, 100.0);
        d.windY = SimMath.clamp(SimMath.finiteOr(e.windY, 0.0), -100.0, 100.0);
        d.windZ = SimMath.clamp(SimMath.finiteOr(e.windZ, 0.0), -100.0, 100.0);
        d.gustIntensity = SimMath.sanitize(e.gustIntensity, 0.0, 1.0, 0.0);
        d.airDensity = SimMath.sanitize(e.airDensity, 0.3, 1.5, ModelConstants.RHO0);
        d.ambientTempC = SimMath.sanitize(e.ambientTempC, -40.0, 60.0, 20.0);
        d.fluid = e.fluid == null ? Fluid.NONE : e.fluid;
        d.skyView = SimMath.sanitize(e.skyView, 0.0, 1.0, 1.0);
    }

    public boolean buildable() {
        return this.tuning.partCompatibility ? this.params.buildable : this.params.assemblable;
    }

    private void frameArming() {
        Arming a = this.arming;
        PilotInput in = this.input;
        boolean switchOn = in.armSwitch;
        int conditions = in.mode3d
                ? Arming.conditions3d(in, this.config, this.body.tiltRad() * SimMath.DEG, this.power.soc, this.buildable(),
                a.crashCooldownSubsteps > 0L, a.rebootSubsteps > 0L, this.env.fluid, this.hardwareFault || this.escBurned)
                : Arming.conditions(in, this.config, this.body.tiltRad() * SimMath.DEG, this.power.soc,
                this.buildable(), a.crashCooldownSubsteps > 0L, a.rebootSubsteps > 0L, this.env.fluid, this.hardwareFault || this.escBurned);
        if (a.armed && this.env.fluid == Fluid.LAVA) {
            this.disarm(DisarmReason.FLUID);
        }
        if (a.pendingSwitchCheck) {
            a.pendingSwitchCheck = false;
            if (switchOn && !a.armed) {
                a.switchNeedsReset = true;
                this.emit(SimEventType.ARM_BLOCKED, conditions | ArmBlocker.ARM_SWITCH_NOT_RESET.bit(), -1);
            }
            a.lastSwitch = switchOn;
        }
        if (!switchOn) {
            a.switchNeedsReset = false;
        }
        boolean rising = switchOn && !a.lastSwitch;
        if (a.armed) {
            if (!switchOn) {
                this.disarm(DisarmReason.SWITCH);
            }
        } else if (rising) {
            int mask = conditions | (a.switchNeedsReset ? ArmBlocker.ARM_SWITCH_NOT_RESET.bit() : 0);
            if (mask == 0 && this.tuning.escOvervoltage && this.params.escOvervoltage) {
                this.escBurned = true;
                this.emit(SimEventType.ESC_BURNED, this.params.cells, -1);
                conditions |= ArmBlocker.HARDWARE_FAULT.bit();
                mask = conditions;
            }
            if (mask == 0) {
                this.arm();
            } else {
                this.emit(SimEventType.ARM_BLOCKED, mask, -1);
                a.switchNeedsReset = true;
            }
        }
        a.lastSwitch = switchOn;
        a.blockers = conditions | (a.switchNeedsReset && switchOn && !a.armed ? ArmBlocker.ARM_SWITCH_NOT_RESET.bit() : 0);
    }

    private void arm() {
        this.arming.armed = true;
        this.arming.failsafeStage = 0;
        this.arming.landingSubsteps = 0L;
        this.gyroModel.measure(this.body.wbx, this.body.wby, this.body.wbz);
        this.fc.reset(this.gyroModel.raw, this.power.busVoltage, this.appliedRoll, this.appliedPitch, this.appliedYaw,
                this.appliedThrottle);
        this.gLoadMax = 0.0;
        this.wakeUp();
        this.emit(SimEventType.ARMED, 0.0, -1);
    }

    private void disarm(DisarmReason reason) {
        Arming a = this.arming;
        if (!a.armed) {
            return;
        }
        a.armed = false;
        a.lastDisarmReason = reason;
        a.failsafeStage = reason == DisarmReason.FAILSAFE ? 2 : 0;
        a.landingSubsteps = 0L;
        if (reason != DisarmReason.SWITCH && this.input.armSwitch) {
            a.switchNeedsReset = true;
        }
        if (reason == DisarmReason.CRASH) {
            a.crashCooldownSubsteps = Math.round(Arming.CRASH_COOLDOWN_S * PHYSICS_HZ);
        }
        this.clearDuties();
        if (this.filtersPending) {
            this.filtersPending = false;
            this.activeFilters = this.config.filters.copy();
            this.fc.configure(this.params, this.config, this.activeFilters);
        }
        this.fc.reset(this.gyroModel.raw, this.power.busVoltage, this.appliedRoll, this.appliedPitch, this.appliedYaw,
                this.appliedThrottle);
        this.emit(SimEventType.DISARMED, reason.ordinal(), -1);
    }

    private void clearDuties() {
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            this.dutyCur[i] = 0.0;
            this.dutyNext[i] = 0.0;
        }
    }

    public void forceDisarm(DisarmReason reason) {
        this.disarm(reason == null || reason == DisarmReason.NONE ? DisarmReason.FORCED : reason);
    }

    public void requireArmSwitchReset() {
        if (!this.arming.armed) {
            this.arming.pendingSwitchCheck = true;
        }
    }

    private void wakeUp() {
        this.sleeping = false;
        this.sleepCounter = 0L;
    }

    private void emit(SimEventType type, double value, int motor) {
        this.events.add(type, this.time, value, motor);
    }

    private void substep(int k, int n) {
        RigidBody b = this.body;
        AirframeSpec p = this.params;
        SimTuning t = this.tuning;
        Arming a = this.arming;
        double h = H;
        double frac = (k + 1) / (double) n;
        PilotInput in = this.input;
        this.appliedRoll = this.fromRoll + (in.roll - this.fromRoll) * frac;
        this.appliedPitch = this.fromPitch + (in.pitch - this.fromPitch) * frac;
        this.appliedYaw = this.fromYaw + (in.yaw - this.fromYaw) * frac;
        this.appliedThrottle = this.fromThrottle + (in.throttle - this.fromThrottle) * frac;

        if (a.crashCooldownSubsteps > 0L) {
            a.crashCooldownSubsteps--;
        }
        if (a.rebootSubsteps > 0L) {
            a.rebootSubsteps--;
        }
        this.failsafeTick();
        if (this.substepCount % NavHold.DIVIDER == 0L) {
            this.navTick();
        }

        boolean washOn = t.propWash && t.propWashScale > 0.0;
        double washNoise = p.washNoise * t.turbulenceScale;
        if (washOn && washNoise > 0.0) {
            this.wash.advanceNoise(this.rng);
        }
        this.gyroModel.sample(this.rng, b.wbx, b.wby, b.wbz, t.gyroNoiseDps + this.wearGyroNoise,
                t.gyroVibrationDps + WearEffects.GYRO_DPS_PER_VIBRATION * this.wearVibration, this.power.omega, p.hoverOmega,
                this.washFilter.value(), this.fc.divider);

        if (this.fcTick++ % this.fc.divider == 0) {
            double r = this.appliedRoll;
            double pt = this.appliedPitch;
            double y = this.appliedYaw;
            double thr = this.appliedThrottle;
            StabMode mode = in.mode;
            boolean mode3d = in.mode3d;
            if (a.failsafeStage == 1) {
                mode3d = false;
                r = 0.0;
                pt = 0.0;
                y = 0.0;
                thr = Math.max(0.0, this.hoverStick - 0.05);
                mode = StabMode.ANGLE;
            } else if (this.nav.mode != HoldMode.OFF) {
                mode3d = false;
                mode = StabMode.ANGLE;
                thr = this.nav.throttleStick;
                if (this.nav.attitudeOverride) {
                    double band = this.config.rates.rcDeadband;
                    r = undeadband(this.nav.rollStick, band);
                    pt = undeadband(this.nav.pitchStick, band);
                }
            }
            if (mode3d) {
                this.fc.update(r, pt, y, thr, mode, a.armed, b, this.gyroModel.raw, this.power.busVoltage, true, this.config.deadband3d);
            } else {
                this.fc.update(r, pt, y, thr, mode, a.armed, b, this.gyroModel.raw, this.power.busVoltage);
            }
            if (this.fc.airmodeJustLatched) {
                this.emit(SimEventType.AIRMODE_ACTIVE, 0.0, -1);
            }
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                double duty = a.armed && !this.motorDead[i] ? (this.dutyOverrideActive ? this.overrideDuty[i] : this.fc.duty[i]) : 0.0;
                double limit = this.thermal.limit[i];
                if (duty < 0.0) {
                    this.dutyNext[i] = limit < 1.0 ? Math.max(duty, -limit) : duty;
                } else {
                    this.dutyNext[i] = limit < 1.0 ? Math.min(duty, limit) : duty;
                }
            }
            if (this.fcListener != null) {
                this.publishFcSample(r, pt, y, thr, mode, mode3d);
            }
        }
        boolean reversible = in.mode3d || this.power.anyReversed() || anyNegative(this.dutyCur);
        this.reversibleActive = reversible;
        this.power.reversible = reversible;

        double rho = this.env.airDensity;
        double rhoRatio = rho / ModelConstants.RHO0;
        this.windX = t.windScale * this.env.windX + this.gusts.x;
        this.windY = t.windScale * this.env.windY + this.gusts.y;
        this.windZ = t.windScale * this.env.windZ + this.gusts.z;
        double ax = b.vx - this.windX;
        double ay = b.vy - this.windY;
        double az = b.vz - this.windZ;
        double ubx = b.r00 * ax + b.r10 * ay + b.r20 * az;
        double uby = b.r01 * ax + b.r11 * ay + b.r21 * az;
        double ubz = b.r02 * ax + b.r12 * ay + b.r22 * az;
        double wbx = b.wbx;
        double wby = b.wby;
        double wbz = b.wbz;
        double axialSum = 0.0;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double rx = p.motorX(i);
            double ry = p.motorY(i);
            double rz = p.motorZ(i);
            double uix = ubx + (wby * rz - wbz * ry);
            double uiy = uby + (wbz * rx - wbx * rz);
            double uiz = ubz + (wbx * ry - wby * rx);
            this.axial[i] = uiy;
            this.crossX[i] = uix;
            this.crossZ[i] = uiz;
            this.cross[i] = Math.sqrt(uix * uix + uiz * uiz);
            if (reversible) {
                double w = this.power.omega[i];
                this.lambda[i] = w < 0.0 ? RotorAero.inflowRatio(-uiy, p.zeroThrustPerOmega * p.reversePitch, -w)
                        : RotorAero.inflowRatio(uiy, p.zeroThrustPerOmega, w);
            } else {
                this.lambda[i] = RotorAero.inflowRatio(uiy, p.zeroThrustPerOmega, this.power.omega[i]);
            }
            axialSum += uiy;
        }

        boolean inWater = this.env.fluid == Fluid.WATER;
        if (inWater) {
            this.waterCounter++;
        } else {
            this.waterCounter = 0L;
        }
        boolean drowned = inWater && this.waterCounter >= WATER_SUBSTEPS;
        if (drowned && a.armed) {
            this.disarm(DisarmReason.FLUID);
        }
        boolean active = a.armed;
        this.power.solveBus(this.dutyCur, active, t.batterySag);
        this.power.stepRotors(h, this.lambda, rhoRatio, active);
        if (active && this.power.anyLimited) {
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                if (this.power.atHardLimit(i) && this.escEventCooldown[i] == 0L) {
                    this.emit(SimEventType.ESC_CURRENT_LIMIT, this.power.current[i], i);
                    this.escEventCooldown[i] = ESC_EVENT_SUBSTEPS;
                }
            }
        }
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            if (this.escEventCooldown[i] > 0L) {
                this.escEventCooldown[i]--;
            }
        }

        double upY = b.r11;
        boolean groundOn = t.groundEffect && upY > ModelConstants.GE_MIN_UP && this.env.groundDistance < Double.POSITIVE_INFINITY;
        double dyFrame = b.py - this.frameStartY;
        double spacing = p.motorSpacing;
        double maxGe = 1.0;
        boolean groundRule = t.groundEffect && this.env.groundDistance < Double.POSITIVE_INFINITY;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double f = 1.0;
            boolean rotorGround = reversible ? groundRule && (this.power.omega[i] < 0.0 ? -upY : upY) > ModelConstants.GE_MIN_UP : groundOn;
            if (rotorGround) {
                double height = this.env.groundDistance - dyFrame + (b.r10 * p.motorX(i) + b.r11 * p.motorY(i) + b.r12 * p.motorZ(i));
                f = 1.0 + t.groundEffectScale * (GroundEffect.factor(p.propRadius, height, spacing) - 1.0);
            }
            this.groundFactor[i] = f;
            maxGe = Math.max(maxGe, f);
        }
        this.maxGroundFactor = maxGe;

        double hoverThrust = p.hoverThrust();
        double fx = 0.0;
        double fy = 0.0;
        double fz = 0.0;
        double tx = 0.0;
        double ty = 0.0;
        double tz = 0.0;
        double sumThrust = 0.0;
        double rotorMomentum = 0.0;
        double thrustScale = drowned ? 0.0 : 1.0;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            if (reversible) {
                double reaction = this.reversibleRotor(i, p, t, rho, rhoRatio, hoverThrust, washOn, washNoise, thrustScale);
                double ti = this.thrust[i];
                double rx = p.motorX(i);
                double ry = p.motorY(i);
                double rz = p.motorZ(i);
                double hx = this.rotorHx;
                double hz = this.rotorHz;
                sumThrust += ti;
                fx += hx;
                fy += ti;
                fz += hz;
                tx += ry * hz - rz * ti;
                ty += rz * hx - rx * hz;
                tz += rx * ti - ry * hx;
                double spin = this.spinSign * p.spin(i);
                ty -= spin * reaction;
                rotorMomentum += spin * p.rotorInertiaEff * this.power.omega[i];
                continue;
            }
            double w = this.power.omega[i];
            double lam = this.lambda[i];
            double fw = 1.0;
            if (washOn) {
                double vrs = PropWash.vortexRingWeight(this.prevThrust[i], hoverThrust, this.axial[i], this.cross[i], rho,
                        p.discArea, p.ductGain);
                fw = this.wash.factor(i, vrs, t.wakeField ? this.wakeWeight : 0.0, t.propWashScale, p.washLoss, washNoise);
            } else {
                this.wash.weight[i] = 0.0;
                this.wash.vrs[i] = 0.0;
            }
            double tl = RotorAero.translationalLiftFactor(this.cross[i], p.hoverInducedVelocity, p.translationalLiftGain,
                    t.translationalLiftScale);
            double axialFactor = RotorAero.axialThrustFactor(p, lam, w, this.cross[i]);
            double ti = thrustScale * p.kThrust * rhoRatio * RotorAero.ductFactor(p.ducted, p.ductGain, lam) * axialFactor
                    * tl * this.groundFactor[i] * Math.max(0.0, fw) * w * w * this.wearThrust[i];
            if (p.propFlex != 0.0) {
                ti *= RotorAero.flexFactor(p, w);
            }
            if (this.injectThrustFault && i == 0) {
                this.injectThrustFault = false;
                ti = Double.NaN;
            }
            this.thrust[i] = ti;
            this.prevThrust[i] = Double.isFinite(ti) ? ti : 0.0;
            sumThrust += ti;
            double hx = 0.0;
            double hz = 0.0;
            if (this.cross[i] > 1e-9) {
                double hk = -ModelConstants.KAPPA_H * ti / (w * p.propRadius + ModelConstants.H_TIP_OFFSET);
                hx = hk * this.crossX[i];
                hz = hk * this.crossZ[i];
            }
            fx += hx;
            fy += ti;
            fz += hz;
            double rx = p.motorX(i);
            double ry = p.motorY(i);
            double rz = p.motorZ(i);
            tx += ry * hz - rz * ti;
            ty += rz * hx - rx * hz;
            tz += rx * ti - ry * hx;
            double spin = this.spinSign * p.spin(i);
            ty -= spin * this.power.reactionTorque[i];
            rotorMomentum += spin * p.rotorInertiaEff * w;
        }
        this.totalThrust = sumThrust;
        tx += wbz * rotorMomentum;
        tz -= wbx * rotorMomentum;
        double crot = p.rotationalDamping * rhoRatio;
        tx -= crot * wbx * Math.abs(wbx);
        ty -= crot * wby * Math.abs(wby);
        tz -= crot * wbz * Math.abs(wbz);
        this.bodyAero.drag(p, rho, t.dragScale * (inWater ? WATER_DRAG : 1.0), ubx, uby, ubz, h,
                reversible ? 1.0 : descentTopScale(p, rho, uby, sumThrust));
        fx += this.bodyAero.fx;
        fy += this.bodyAero.fy;
        fz += this.bodyAero.fz;

        double m = p.massKg;
        double g = this.gravity;
        double fwx = b.r00 * fx + b.r01 * fy + b.r02 * fz;
        double fwy = b.r10 * fx + b.r11 * fy + b.r12 * fz;
        double fwz = b.r20 * fx + b.r21 * fy + b.r22 * fz;
        double accX = fwx / m;
        double accY = fwy / m - g;
        double accZ = fwz / m;
        double twx = b.r00 * tx + b.r01 * ty + b.r02 * tz;
        double twy = b.r10 * tx + b.r11 * ty + b.r12 * tz;
        double twz = b.r20 * tx + b.r21 * ty + b.r22 * tz;

        this.normalForce = 0.0;
        if (this.groundedFrames > 0 && !this.sleeping) {
            double normal = Math.max(0.0, m * g - sumThrust * b.r11);
            this.normalForce = normal;
            if (b.vy <= 0.0 && b.py <= this.groundHeight - p.hitboxFootOffset + GROUND_TOLERANCE) {
                b.vy = 0.0;
                if (accY < 0.0) {
                    accY = 0.0;
                }
            }
            double mu = p.friction * this.groundGrip;
            double vxz = Math.sqrt(b.vx * b.vx + b.vz * b.vz);
            if (vxz > 0.0) {
                double dv = Math.min(vxz, mu * normal / m * h);
                b.vx -= dv * b.vx / vxz;
                b.vz -= dv * b.vz / vxz;
            }
            double rate = SimMath.hypot3(b.wbx, b.wby, b.wbz);
            if (rate < RIGHTING_RATE && Math.abs(sumThrust) < 0.5 * m * g) {
                double target = b.r11 >= 0.0 ? 1.0 : -1.0;
                double kr = m * g * p.motorOffset;
                double cr = 2.0 * Math.sqrt(kr * p.inertiaXx);
                double edge = a.armed || !t.restFlat ? 1.0 : GroundRest.gain(b.r01, b.r21);
                double ux = b.r01 * edge;
                double uz = b.r21 * edge;
                twx += kr * (-uz * target) - cr * b.wwx;
                twz += kr * (ux * target) - cr * b.wwz;
            }
            double contactTorque = mu * normal * p.motorOffset;
            twx -= contactTorque * StrictMath.tanh(b.wwx / CONTACT_RATE);
            twy -= contactTorque * StrictMath.tanh(b.wwy / CONTACT_RATE);
            twz -= contactTorque * StrictMath.tanh(b.wwz / CONTACT_RATE);
        }

        this.specificX = fx / m + b.r10 * this.normalForce / m;
        this.specificY = fy / m + b.r11 * this.normalForce / m;
        this.specificZ = fz / m + b.r12 * this.normalForce / m;

        if (this.injectFault) {
            this.injectFault = false;
            b.vx = Double.NaN;
        }
        if (!this.sleeping && !this.poseLocked) {
            b.integrate(h, accX, accY, accZ, twx, twy, twz);
        } else if (this.poseLocked) {
            b.vx = 0.0;
            b.vy = 0.0;
            b.vz = 0.0;
            b.lx = 0.0;
            b.ly = 0.0;
            b.lz = 0.0;
            b.updateRates();
        }

        this.power.integrateBattery(h, t.infiniteBattery, t.batteryScale);
        if (this.power.busVoltage < p.brownoutVoltage || this.power.packSoc <= ModelConstants.SOC_MIN) {
            this.brownoutCounter++;
            if (this.brownoutCounter > BROWNOUT_SUBSTEPS) {
                this.brownoutCounter = 0L;
                if (!this.brownoutLatched || a.armed) {
                    this.emit(SimEventType.BROWNOUT, this.power.busVoltage, -1);
                }
                this.brownoutLatched = true;
                this.disarm(DisarmReason.BROWNOUT);
                a.rebootSubsteps = Math.max(a.rebootSubsteps, Math.round(Arming.REBOOT_S * PHYSICS_HZ));
            }
        } else {
            this.brownoutCounter = 0L;
            if (this.brownoutLatched && this.power.busVoltage > BROWNOUT_RELEASE * p.brownoutVoltage) {
                this.brownoutLatched = false;
            }
        }

        this.time += h;
        this.substepCount++;
        if (this.substepCount % SLOW_DIVIDER == 0L) {
            this.slowTick();
        }
        if (this.substepCount % HOVER_ESTIMATE_DIVIDER == 0L) {
            this.updateHoverEstimate();
        }

        this.gLoadFilter.apply(this.specificY);
        this.washFilter.apply(this.wash.maxOf());
        double gTotal = SimMath.hypot3(this.specificX, this.specificY, this.specificZ) / ModelConstants.G;
        if (a.armed && gTotal > this.gLoadMax) {
            this.gLoadMax = gTotal;
        }
        this.updateSleep();

        double check = b.px + b.py + b.pz + b.vx + b.vy + b.vz + b.lx + b.ly + b.lz + b.qw + b.qx + b.qy + b.qz
                + this.power.busVoltage + this.power.omega[0] + this.power.omega[1] + this.power.omega[2] + this.power.omega[3];
        if (!Double.isFinite(check)) {
            this.handleFault();
            return;
        }
        System.arraycopy(this.dutyNext, 0, this.dutyCur, 0, AirframeSpec.MOTORS);
    }

    private double rotorHx;
    private double rotorHz;

    private double reversibleRotor(int i, AirframeSpec p, SimTuning t, double rho, double rhoRatio, double hoverThrust, boolean washOn,
                                   double washNoise, double thrustScale) {
        double w = this.power.omega[i];
        boolean reverse = w < 0.0;
        double s = reverse ? -1.0 : 1.0;
        double aw = Math.abs(w);
        double lam = this.lambda[i];
        double axialDir = s * this.axial[i];
        double fw = 1.0;
        if (washOn) {
            double vrs = PropWash.vortexRingWeight(Math.abs(this.prevThrust[i]), hoverThrust, axialDir, this.cross[i], rho, p.discArea,
                    reverse ? 1.0 : p.ductGain);
            fw = this.wash.factor(i, vrs, t.wakeField ? this.wakeWeight : 0.0, t.propWashScale, p.washLoss, washNoise);
        } else {
            this.wash.weight[i] = 0.0;
            this.wash.vrs[i] = 0.0;
        }
        double tl = RotorAero.translationalLiftFactor(this.cross[i], p.hoverInducedVelocity, p.translationalLiftGain,
                t.translationalLiftScale);
        double kT = reverse ? p.kThrust * p.reverseThrust : p.kThrust;
        double duct = reverse ? 1.0 : RotorAero.ductFactor(p.ducted, p.ductGain, lam);
        double axialFactor = reverse ? RotorAero.axialThrustFactor(lam) : RotorAero.axialThrustFactor(p, lam, aw, this.cross[i]);
        double magnitude = thrustScale * kT * rhoRatio * duct * axialFactor * tl * this.groundFactor[i]
                * Math.max(0.0, fw) * aw * aw * this.wearThrust[i];
        if (p.propFlex != 0.0) {
            magnitude *= RotorAero.flexFactor(p, aw);
        }
        if (this.injectThrustFault && i == 0) {
            this.injectThrustFault = false;
            magnitude = Double.NaN;
        }
        double ti = s * magnitude;
        this.thrust[i] = ti;
        this.prevThrust[i] = Double.isFinite(ti) ? ti : 0.0;
        double hx = 0.0;
        double hz = 0.0;
        if (this.cross[i] > 1e-9) {
            double hk = -ModelConstants.KAPPA_H * magnitude / (aw * p.propRadius + ModelConstants.H_TIP_OFFSET);
            hx = hk * this.crossX[i];
            hz = hk * this.crossZ[i];
        }
        this.rotorHx = hx;
        this.rotorHz = hz;
        return this.power.reactionTorque[i];
    }

    boolean reversibleActive() {
        return this.reversibleActive;
    }

    private static boolean anyNegative(double[] values) {
        for (double v : values) {
            if (v < 0.0) {
                return true;
            }
        }
        return false;
    }

    private void navTick() {
        RigidBody b = this.body;
        AirframeSpec p = this.params;
        double sx = this.specificX;
        double sy = this.specificY;
        double sz = this.specificZ;
        double wx = b.r00 * sx + b.r01 * sy + b.r02 * sz;
        double wy = b.r10 * sx + b.r11 * sy + b.r12 * sz;
        double wz = b.r20 * sx + b.r21 * sy + b.r22 * sz;
        double airspeed = SimMath.hypot3(b.vx - this.windX, b.vy - this.windY, b.vz - this.windZ);
        double height = this.env.groundDistance - (b.py - this.frameStartY) + p.hitboxFootOffset;
        double thrustRatio = this.totalThrust / (p.massKg * SimTuning.referenceGravity(this.gravity));
        this.nav.update(this.config.hold, this.arming.armed, this.arming.failsafeStage == 1, this.groundedFrames > 0, this.input,
                this.env.skyView, b,
                wx, wy, wz, airspeed, height, thrustRatio, this.washFilter.value(), this.wearVibration, this.hoverStick,
                this.fc.pid());
    }

    static double undeadband(double stick, double band) {
        if (band <= 0.0 || stick == 0.0) {
            return stick;
        }
        return Math.signum(stick) * Math.min(1.0, band + Math.abs(stick) * (1.0 - band));
    }

    private void failsafeTick() {
        Arming a = this.arming;
        if (!this.input.linkOk) {
            a.linkLostSubsteps++;
        } else {
            a.linkLostSubsteps = 0L;
            if (a.failsafeStage == 1) {
                a.failsafeStage = 0;
                a.landingSubsteps = 0L;
            } else if (a.failsafeStage == 2 && !a.armed) {
                a.failsafeStage = 0;
            }
        }
        long delay = Math.round(this.config.failsafeDelayS * PHYSICS_HZ);
        if (a.armed && a.failsafeStage == 0 && a.linkLostSubsteps > delay) {
            a.failsafeStage = 1;
            a.landingSubsteps = 0L;
            this.emit(SimEventType.FAILSAFE, 1.0, -1);
        }
        if (a.failsafeStage == 1) {
            a.landingSubsteps++;
            if (a.landingSubsteps >= Math.round(this.config.failsafeLandingS * PHYSICS_HZ)) {
                this.emit(SimEventType.FAILSAFE, 2.0, -1);
                this.disarm(DisarmReason.FAILSAFE);
                a.failsafeStage = 2;
            }
        }
    }

    private void slowTick() {
        AirframeSpec p = this.params;
        SimTuning t = this.tuning;
        RigidBody b = this.body;
        this.gusts.advance(this.rng, this.env.gustIntensity,
                SimMath.hypot3(this.env.windX, this.env.windY, this.env.windZ), t.windScale * t.turbulenceScale);
        boolean wakeOn = t.wakeField && t.propWash;
        if (wakeOn) {
            this.wake.advance(this.windX, this.windY, this.windZ);
            if (this.reversibleActive && this.totalThrust < 0.0) {
                if (this.substepCount % WakeField.EMIT_INTERVAL == 0L) {
                    double meanAxial = 0.25 * (this.axial[0] + this.axial[1] + this.axial[2] + this.axial[3]);
                    this.wake.emit(true, p, this.env.airDensity, b.px, b.py, b.pz, -b.r01, -b.r11, -b.r21, this.windX, this.windY,
                            this.windZ, -this.totalThrust, -meanAxial);
                }
                this.wakeWeight = this.wake.query(b.px, b.py, b.pz, b.vx, b.vy, b.vz, -b.r01, -b.r11, -b.r21, p.hoverInducedVelocity);
            } else {
                if (this.substepCount % WakeField.EMIT_INTERVAL == 0L) {
                    double meanAxial = 0.25 * (this.axial[0] + this.axial[1] + this.axial[2] + this.axial[3]);
                    this.wake.emit(true, p, this.env.airDensity, b.px, b.py, b.pz, b.r01, b.r11, b.r21, this.windX, this.windY,
                            this.windZ, this.totalThrust, meanAxial);
                }
                this.wakeWeight = this.wake.query(b.px, b.py, b.pz, b.vx, b.vy, b.vz, b.r01, b.r11, b.r21, p.hoverInducedVelocity);
            }
        } else {
            this.wakeWeight = 0.0;
        }
        this.thermal.advance(p, this.power, this.env.ambientTempC, SLOW_DT, t.thermalEffects);
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double temp = this.thermal.motorTemp[i];
            if (!this.thermal.overheated[i] && temp > p.motorMaxTempC) {
                this.thermal.overheated[i] = true;
                this.emit(SimEventType.MOTOR_OVERHEAT, temp, i);
            } else if (this.thermal.overheated[i] && temp < p.motorMaxTempC - Thermal.HYSTERESIS_C) {
                this.thermal.overheated[i] = false;
            }
            this.power.resistance[i] = t.thermalEffects ? this.thermal.motorResistance(p, i) + p.escResistance : p.phaseResistance;
            this.thermalLimits(p, t, i, temp);
        }
        this.power.extraPower = EnergyModel.extraPower(p, this.power.omega, this.arming.armed);
        this.power.fullLoad = EnergyModel.fullLoad(p, this.power.omega, this.arming.armed);
        double cell = this.cellFilter.apply(this.power.packVoltage(t.batterySag) / p.cells);
        if (t.infiniteBattery) {
            this.clearBatteryAlarms();
            return;
        }
        double resting = CellCurve.ocv(p.chemistry, PowerSystem.restingSoc(this.power.packSoc, t.batterySag));
        if (this.lowAlarm.update(cell, resting, p.chemistry.emptyCellVoltage, WARN_SLOW_STEPS)) {
            this.emit(SimEventType.BATTERY_LOW, cell, -1);
        }
        if (this.criticalAlarm.update(cell, resting, p.chemistry.cutoffCellVoltage, WARN_SLOW_STEPS)) {
            this.emit(SimEventType.BATTERY_CRITICAL, cell, -1);
        }
        if (!this.emptyFired && this.power.packSoc <= 0.0) {
            this.emptyFired = true;
            this.emit(SimEventType.PACK_DEPLETED, cell, -1);
        }
        this.batteryState = this.emptyFired ? BatteryState.EMPTY
                : this.criticalAlarm.active() ? BatteryState.CRITICAL
                : this.lowAlarm.active() ? BatteryState.LOW : BatteryState.OK;
    }

    private void thermalLimits(AirframeSpec p, SimTuning t, int i, double temp) {
        Thermal th = this.thermal;
        if (!t.thermalLimits) {
            th.limit[i] = 1.0;
            if (th.derating[i]) {
                th.derating[i] = false;
                this.emit(SimEventType.MOTOR_DERATE, 0.0, i);
            }
            return;
        }
        th.limit[i] = Thermal.powerLimit(p, temp);
        if (!th.derating[i] && temp > p.motorDerateStartC) {
            th.derating[i] = true;
            this.emit(SimEventType.MOTOR_DERATE, 1.0, i);
        } else if (th.derating[i] && temp < p.motorDerateStartC - Thermal.HYSTERESIS_C) {
            th.derating[i] = false;
            this.emit(SimEventType.MOTOR_DERATE, 0.0, i);
        }
        double rate = Thermal.wearRate(p, temp);
        if (rate > 0.0) {
            th.wear[i] += rate * SLOW_DT;
            if (th.wear[i] >= Thermal.WEAR_EVENT_STEP) {
                this.emit(SimEventType.MOTOR_THERMAL_WEAR, th.wear[i], i);
                th.wear[i] = 0.0;
            }
        }
    }

    private void clearBatteryAlarms() {
        this.lowAlarm.reset();
        this.criticalAlarm.reset();
        this.emptyFired = false;
        this.batteryState = BatteryState.OK;
    }

    static double descentTopScale(AirframeSpec p, double rho, double axialSpeed, double totalThrust) {
        if (!(axialSpeed < 0.0)) {
            return 1.0;
        }
        double induced = Math.sqrt(Math.max(totalThrust, 0.0) / AirframeSpec.MOTORS / (2.0 * rho * p.discArea * p.ductGain));
        double windmill = induced > 1e-6
                ? SimMath.smoothstep(ModelConstants.WINDMILL_SINK_LOW, ModelConstants.WINDMILL_SINK_HIGH, -axialSpeed / induced) : 1.0;
        return 1.0 - ModelConstants.BLADE_DRAG_SHARE * (1.0 - windmill);
    }

    private void updateHoverEstimate() {
        AirframeSpec p = this.params;
        double rhoRatio = this.env.airDensity / ModelConstants.RHO0;
        double scale = this.tuning.gravityScale;
        double omega = p.hoverOmega * Math.sqrt(scale) / Math.sqrt(rhoRatio);
        double current = OperatingPoints.hoverCurrent(p, scale);
        OperatingPoints.steadyMotor(p, omega, current, this.power.soc, false, this.power.polarization, this.tuning.batterySag,
                this.hoverPoint);
        double output = this.fc.outputForDuty(this.hoverPoint.duty, this.hoverPoint.busVoltage);
        this.hoverStick = ThrottleCurve.inverse(this.fc.pid(), SimMath.clamp01(output));
    }

    private void updateSleep() {
        RigidBody b = this.body;
        if (this.sleeping) {
            return;
        }
        boolean calm = !this.arming.armed && this.groundedFrames > 0 && this.tuning.sleepEnabled
                && b.vx * b.vx + b.vy * b.vy + b.vz * b.vz < SLEEP_SPEED * SLEEP_SPEED
                && b.wbx * b.wbx + b.wby * b.wby + b.wbz * b.wbz < SLEEP_RATE * SLEEP_RATE;
        if (calm) {
            this.sleepCounter++;
            if (this.sleepCounter >= SLEEP_SUBSTEPS) {
                this.sleeping = true;
                b.vx = 0.0;
                b.vy = 0.0;
                b.vz = 0.0;
                b.lx = 0.0;
                b.ly = 0.0;
                b.lz = 0.0;
                b.updateRates();
            }
        } else {
            this.sleepCounter = 0L;
        }
    }

    private void handleFault() {
        this.restorePhysical(this.rollback);
        RigidBody b = this.body;
        b.vx = 0.0;
        b.vy = 0.0;
        b.vz = 0.0;
        b.lx = 0.0;
        b.ly = 0.0;
        b.lz = 0.0;
        if (!Double.isFinite(b.px + b.py + b.pz)) {
            b.px = SimMath.finiteOr(this.visX, 0.0);
            b.py = SimMath.finiteOr(this.visY, 0.0);
            b.pz = SimMath.finiteOr(this.visZ, 0.0);
        }
        if (!Double.isFinite(b.qw + b.qx + b.qy + b.qz)) {
            b.setUpright(0.0);
        }
        b.updateDerived();
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            if (!Double.isFinite(this.power.omega[i])) {
                this.power.omega[i] = 0.0;
            }
            if (!Double.isFinite(this.thermal.motorTemp[i] + this.thermal.escTemp[i])) {
                this.thermal.motorTemp[i] = this.env.ambientTempC;
                this.thermal.escTemp[i] = this.env.ambientTempC;
            }
            this.power.resistance[i] = this.tuning.thermalEffects
                    ? this.thermal.motorResistance(this.params, i) + this.params.escResistance : this.params.phaseResistance;
        }
        if (!Double.isFinite(this.power.lastBusVoltage + this.power.soc + this.power.mAhUsed + this.power.polarization
                + this.power.heatCurrent)) {
            this.power.setStateOfCharge(Math.max(0.0, SimMath.finiteOr(this.power.soc, 1.0)));
        }
        if (!Double.isFinite(this.power.packSoc + this.power.packMah + this.power.packCurrent
                + this.power.extraPower)) {
            double pack = Math.max(0.0, SimMath.finiteOr(this.power.packSoc, this.power.soc));
            this.power.packSoc = pack;
            this.power.packMah = (1.0 - pack) * this.params.capacityMah;
            this.power.packCurrent = 0.0;
            this.power.extraPower = 0.0;
            this.power.fullLoad = 0.0;
        }
        this.clearTransientState();
        this.cellFilter.reset(this.power.busVoltage / this.params.cells);
        this.faults++;
        if (this.time - this.faultWindowStart > 1.0) {
            this.faultWindowStart = this.time;
            this.faultsInWindow = 0;
        }
        this.faultsInWindow++;
        this.emit(SimEventType.SIM_FAULT, 0.0, -1);
        this.disarm(DisarmReason.SIM_FAULT);
        this.clearDuties();
        if (this.faultsInWindow > MAX_FAULTS_PER_SECOND) {
            double heading = b.headingRad();
            b.setUpright(Double.isFinite(heading) ? heading : 0.0);
            this.power.stopRotors();
            this.faultsInWindow = 0;
        }
        this.fc.reset(this.gyroModel.raw, this.power.busVoltage, this.appliedRoll, this.appliedPitch, this.appliedYaw,
                this.appliedThrottle);
        this.updateHoverEstimate();
        this.frameAborted = true;
    }

    private void finishFrame(int substeps, StepResult out) {
        RigidBody b = this.body;
        double tau = this.accumulatorNanos * 1e-9;
        double visX = b.px + b.vx * tau;
        double visY = b.py + b.vy * tau;
        double visZ = b.pz + b.vz * tau;
        if (this.sleeping || this.poseLocked) {
            visX = b.px;
            visY = b.py;
            visZ = b.pz;
        }
        if (out != null) {
            out.dx = visX - this.visX;
            out.dy = visY - this.visY;
            out.dz = visZ - this.visZ;
            double rate = SimMath.hypot3(b.wwx, b.wwy, b.wwz);
            double theta = rate * tau;
            double qw = b.qw;
            double qx = b.qx;
            double qy = b.qy;
            double qz = b.qz;
            if (theta > 1e-12 && !this.sleeping) {
                double s = SimMath.sin(0.5 * theta) / rate;
                double dw = SimMath.cos(0.5 * theta);
                double dx = s * b.wwx;
                double dy = s * b.wwy;
                double dz = s * b.wwz;
                qw = dw * b.qw - dx * b.qx - dy * b.qy - dz * b.qz;
                qx = dw * b.qx + dx * b.qw + dy * b.qz - dz * b.qy;
                qy = dw * b.qy - dx * b.qz + dy * b.qw + dz * b.qx;
                qz = dw * b.qz + dx * b.qy - dy * b.qx + dz * b.qw;
            }
            out.orientation.set(qx, qy, qz, qw);
            out.substeps = substeps;
            out.armed = this.arming.armed;
            out.sleeping = this.sleeping;
        }
        this.visX = visX;
        this.visY = visY;
        this.visZ = visZ;
        this.fillTelemetry(substeps);
    }

    public void afterMove(MoveReport report) {
        if (report == null) {
            return;
        }
        double cx = report.actualX - report.requestedX;
        double cy = report.actualY - report.requestedY;
        double cz = report.actualZ - report.requestedZ;
        if (!Double.isFinite(report.requestedX) || !Double.isFinite(report.requestedY) || !Double.isFinite(report.requestedZ)
                || !Double.isFinite(cx) || !Double.isFinite(cy) || !Double.isFinite(cz)) {
            this.faults++;
            this.telemetry.faults = this.faults;
            return;
        }
        RigidBody b = this.body;
        AirframeSpec p = this.params;
        b.px += cx;
        b.py += cy;
        b.pz += cz;
        this.visX += cx;
        this.visY += cy;
        this.visZ += cz;
        double grip = SimMath.sanitize(report.surfaceGrip, 0.0, 4.0, 1.0);
        boolean supportKnown = !Double.isNaN(report.supportGap);
        boolean unsupported = supportKnown && report.supportGap > SUPPORT_TOLERANCE;
        double restitution = Double.isNaN(report.surfaceRestitution) || report.surfaceRestitution < 0.0 ? -1.0
                : Math.min(report.surfaceRestitution, 1.0);
        boolean hitGround = false;
        double groundImpact = 0.0;
        boolean wasGrounded = this.groundedFrames > 0;
        double ax = this.axisSpeed(report.hitX, report.requestedX, report.impactVelocityX, b.vx);
        double ay = this.axisSpeed(report.hitY, report.requestedY, report.impactVelocityY, b.vy);
        double az = this.axisSpeed(report.hitZ, report.requestedZ, report.impactVelocityZ, b.vz);
        int touched = 0;
        for (int pass = 0; pass < 3; pass++) {
            int axis = -1;
            double best = -1.0;
            if (ax > best) {
                best = ax;
                axis = 0;
            }
            if (ay > best) {
                best = ay;
                axis = 1;
            }
            if (az > best) {
                best = az;
                axis = 2;
            }
            if (axis < 0 || best < 0.0) {
                break;
            }
            double requested;
            double impact;
            if (axis == 0) {
                ax = -1.0;
                requested = report.requestedX;
                impact = report.impactVelocityX;
            } else if (axis == 1) {
                ay = -1.0;
                requested = report.requestedY;
                impact = report.impactVelocityY;
            } else {
                az = -1.0;
                requested = report.requestedZ;
                impact = report.impactVelocityZ;
            }
            double sign = -Math.signum(requested);
            double nx = axis == 0 ? sign : 0.0;
            double ny = axis == 1 ? sign : 0.0;
            double nz = axis == 2 ? sign : 0.0;
            double approach = Double.POSITIVE_INFINITY;
            if (this.contactFrames[axis] > 0 && this.contactSign[axis] == sign) {
                double startNormal = axis == 0 ? this.frameStartVx : (axis == 1 ? this.frameStartVy : this.frameStartVz);
                approach = Math.max(0.0, -sign * startNormal);
            }
            touched |= 1 << axis;
            this.contactSign[axis] = sign;
            double saved = 0.0;
            boolean useImpact = Double.isFinite(impact);
            if (useImpact) {
                if (axis == 0) {
                    saved = b.vx;
                    b.vx = impact;
                } else if (axis == 1) {
                    saved = b.vy;
                    b.vy = impact;
                } else {
                    saved = b.vz;
                    b.vz = impact;
                }
            }
            this.collision.resolve(p, b, this.power, this.tuning, this.spinSign, nx, ny, nz, grip, restitution, approach);
            if (useImpact && !this.collision.resolved) {
                if (axis == 0) {
                    b.vx = saved;
                } else if (axis == 1) {
                    b.vy = saved;
                } else {
                    b.vz = saved;
                }
            }
            if (ny > 0.0) {
                hitGround = true;
                groundImpact = Math.max(groundImpact, this.collision.impactSpeed);
            }
            if (this.collision.resolved) {
                this.classifyImpact(Math.min(this.collision.impactSpeed, approach), nx, ny, nz);
                if (Math.abs(this.collision.normalImpulse) > 0.05 * p.massKg) {
                    this.wakeUp();
                }
            }
        }
        for (int a = 0; a < 3; a++) {
            if ((touched & (1 << a)) != 0) {
                this.contactFrames[a] = CONTACT_MEMORY_FRAMES;
            } else if (this.contactFrames[a] > 0) {
                this.contactFrames[a]--;
            }
        }
        if (report.obliqueContact()) {
            double length = Math.sqrt(report.contactNormalX * report.contactNormalX + report.contactNormalY * report.contactNormalY
                    + report.contactNormalZ * report.contactNormalZ);
            if (length > 1.0E-6) {
                double nx = report.contactNormalX / length;
                double ny = report.contactNormalY / length;
                double nz = report.contactNormalZ / length;
                double approach = Double.POSITIVE_INFINITY;
                if (this.obliqueFrames > 0 && nx * this.obliqueX + ny * this.obliqueY + nz * this.obliqueZ > OBLIQUE_SAME_CONTACT) {
                    approach = Math.max(0.0, -(nx * this.frameStartVx + ny * this.frameStartVy + nz * this.frameStartVz));
                }
                this.obliqueFrames = CONTACT_MEMORY_FRAMES;
                this.obliqueX = nx;
                this.obliqueY = ny;
                this.obliqueZ = nz;
                this.collision.resolve(p, b, this.power, this.tuning, this.spinSign, nx, ny, nz, grip, restitution, approach);
                if (ny > OBLIQUE_GROUND) {
                    hitGround = true;
                    groundImpact = Math.max(groundImpact, this.collision.impactSpeed);
                }
                if (this.collision.resolved) {
                    this.classifyImpact(Math.min(this.collision.impactSpeed, approach), nx, ny, nz);
                    if (Math.abs(this.collision.normalImpulse) > 0.05 * p.massKg) {
                        this.wakeUp();
                    }
                }
            }
        } else if (this.obliqueFrames > 0) {
            this.obliqueFrames--;
        }
        if (hitGround) {
            this.groundedFrames = 2;
            this.groundHeight = this.visY + p.hitboxFootOffset;
            this.groundGrip = grip;
            if (!wasGrounded && groundImpact < 1.5 && b.tiltRad() * SimMath.DEG < 30.0) {
                this.emit(SimEventType.LANDED, -groundImpact, -1);
            }
        } else if (!this.sleeping && this.groundedFrames > 0) {
            boolean resting = Math.abs(this.visY + p.hitboxFootOffset - this.groundHeight) < RESTING_TOLERANCE && b.vy <= 1e-6;
            if (resting && !unsupported) {
                this.groundedFrames = 2;
            } else if (resting) {
                this.leaveGround(b);
            } else {
                this.groundedFrames--;
                if (this.groundedFrames == 0) {
                    this.emit(SimEventType.TAKEOFF, b.vy, -1);
                }
            }
        }
        if (this.sleeping) {
            double clearance = this.env.groundDistance + p.hitboxFootOffset;
            if (unsupported) {
                this.wakeUp();
                this.leaveGround(b);
            } else if (!supportKnown && clearance > WAKE_THRESHOLD) {
                this.wakeUp();
            }
        }
        this.fillTelemetry(this.telemetry.substeps);
    }

    private void leaveGround(RigidBody b) {
        if (this.groundedFrames > 0) {
            this.groundedFrames = 0;
            this.emit(SimEventType.TAKEOFF, b.vy, -1);
        }
    }

    private double axisSpeed(boolean hit, double requested, double impact, double velocity) {
        if (!hit || requested == 0.0) {
            return -1.0;
        }
        return Math.abs(Double.isFinite(impact) ? impact : velocity);
    }

    private void classifyImpact(double v, double nx, double ny, double nz) {
        CollisionSolver c = this.collision;
        double vcr = this.params.impactTolerance * this.tuning.crashSpeedScale;
        this.recordImpact(v, nx, ny, nz);
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            if (c.struck[i]) {
                this.propStrikeCount++;
                this.emit(SimEventType.PROP_STRIKE, c.struckRpm[i], i);
            }
        }
        if (v >= 1.0) {
            this.lastImpactSpeed = v;
        }
        boolean crash = v >= vcr || (c.tangentialSpeed >= 1.5 * vcr && v >= 0.5);
        if (crash || v >= 1.0) {
            this.impactCount++;
        }
        if (crash) {
            this.crashCount++;
            this.lastImpactSpeed = Math.max(this.lastImpactSpeed, v);
            this.emit(SimEventType.CRASH, v, -1);
            if (this.arming.armed && this.tuning.crashDisarm) {
                this.disarm(DisarmReason.CRASH);
            }
        } else if (v >= 0.5 * vcr) {
            this.emit(SimEventType.HARD_IMPACT, v, -1);
        } else if (v >= 1.0) {
            this.emit(SimEventType.BUMP, v, -1);
        }
    }

    private void recordImpact(double v, double nx, double ny, double nz) {
        CollisionSolver c = this.collision;
        int struck = 0;
        double spin = 0.0;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            if (c.struck[i]) {
                struck |= 1 << i;
                spin = Math.max(spin, c.struckRpm[i] * SimMath.TWO_PI / 60.0 / this.params.hoverOmega);
            }
        }
        if (v < IMPACT_RECORD_SPEED && struck == 0) {
            return;
        }
        int contacts = 0;
        for (int k = 0; k < AirframeSpec.HULL_POINTS; k++) {
            if (c.hull.inContact[k]) {
                contacts |= 1 << k;
            }
        }
        RigidBody b = this.body;
        double bx = b.r00 * nx + b.r10 * ny + b.r20 * nz;
        double by = b.r01 * nx + b.r11 * ny + b.r21 * nz;
        double bz = b.r02 * nx + b.r12 * ny + b.r22 * nz;
        this.impacts.add(v, c.tangentialSpeed, bx, by, bz, contacts, struck, spin);
    }

    public void teleport(double x, double y, double z, boolean keepVelocity) {
        if (!Double.isFinite(x + y + z)) {
            return;
        }
        RigidBody b = this.body;
        double dx = x - b.px;
        double dy = y - b.py;
        double dz = z - b.pz;
        this.wake.shift(dx, dy, dz);
        this.nav.shift(dx, dy, dz);
        b.px = x;
        b.py = y;
        b.pz = z;
        if (!keepVelocity) {
            b.vx = 0.0;
            b.vy = 0.0;
            b.vz = 0.0;
            b.lx = 0.0;
            b.ly = 0.0;
            b.lz = 0.0;
            b.updateRates();
        }
        this.visX = x;
        this.visY = y;
        this.visZ = z;
        this.frameStartY = y;
        this.groundedFrames = 0;
        this.clearContacts();
        this.wakeUp();
    }

    public void setHorizontalVelocity(double vx, double vz) {
        if (!Double.isFinite(vx) || !Double.isFinite(vz)) {
            return;
        }
        RigidBody b = this.body;
        b.vx = vx;
        b.vz = vz;
    }

    public double breakThrough(double nx, double ny, double nz, double absorbedJoules) {
        return this.breakThrough(nx, ny, nz, absorbedJoules, BreakContact.NONE);
    }

    public double breakThrough(double nx, double ny, double nz, double absorbedJoules, BreakContact contact) {
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (!Double.isFinite(length) || length < 1.0E-6 || !Double.isFinite(absorbedJoules) || absorbedJoules <= 0.0) {
            return 0.0;
        }
        double ux = nx / length;
        double uy = ny / length;
        double uz = nz / length;
        RigidBody b = this.body;
        double approach = -(ux * b.vx + uy * b.vy + uz * b.vz);
        if (!(approach > 0.0) || !(this.params.massKg > 0.0)) {
            return 0.0;
        }
        double lost = approach - BreakImpulse.residual(this.params.massKg, approach, absorbedJoules);
        BreakSolver solver = this.breakSolver;
        solver.solve(this.params, b, this.power, this.tuning, this.spinSign, this.params.massKg, ux, uy, uz, approach - lost, lost,
                contact == null ? BreakContact.NONE : contact);
        double total = lost + solver.lostShards;
        b.vx += ux * total;
        b.vy += uy * total;
        b.vz += uz * total;
        this.wakeUp();
        boolean crash = this.classifyBreak(lost, ux, uy, uz);
        this.breakCount++;
        this.lastBreak = new BreakReport(this.breakCount, approach, lost, solver.lostShards, solver.plug, solver.area, solver.share,
                solver.rateX, solver.rateY, solver.rateZ, solver.contacts, solver.struck, crash);
        return total;
    }

    public int breakCount() {
        return this.breakCount;
    }

    public BreakReport lastBreak() {
        return this.lastBreak;
    }

    private boolean classifyBreak(double v, double nx, double ny, double nz) {
        BreakSolver solver = this.breakSolver;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            if ((solver.struck & 1 << i) != 0) {
                this.propStrikeCount++;
                this.emit(SimEventType.PROP_STRIKE, solver.struckRpm[i], i);
            }
        }
        if (v >= IMPACT_RECORD_SPEED || solver.struck != 0) {
            RigidBody b = this.body;
            double bx = b.r00 * nx + b.r10 * ny + b.r20 * nz;
            double by = b.r01 * nx + b.r11 * ny + b.r21 * nz;
            double bz = b.r02 * nx + b.r12 * ny + b.r22 * nz;
            this.impacts.add(v, 0.0, bx, by, bz, solver.contacts, solver.struck, solver.spin);
        }
        double vcr = this.params.impactTolerance * this.tuning.crashSpeedScale;
        boolean crash = v >= vcr;
        if (v >= 1.0) {
            this.lastImpactSpeed = v;
        }
        if (crash || v >= 1.0) {
            this.impactCount++;
        }
        if (crash) {
            this.crashCount++;
            this.emit(SimEventType.CRASH, v, -1);
            if (this.arming.armed && this.tuning.crashDisarm) {
                this.disarm(DisarmReason.CRASH);
            }
        } else if (v >= 0.5 * vcr) {
            this.emit(SimEventType.HARD_IMPACT, v, -1);
        } else if (v >= 1.0) {
            this.emit(SimEventType.BUMP, v, -1);
        }
        return crash;
    }

    public long pendingNanos() {
        return this.accumulatorNanos;
    }

    public Telemetry telemetry() {
        return this.telemetry;
    }

    public EventQueue events() {
        return this.events;
    }

    public ImpactLog impacts() {
        return this.impacts;
    }

    public void copyStateTo(SimSnapshot dst) {
        RigidBody b = this.body;
        dst.px = b.px;
        dst.py = b.py;
        dst.pz = b.pz;
        dst.vx = b.vx;
        dst.vy = b.vy;
        dst.vz = b.vz;
        dst.qw = b.qw;
        dst.qx = b.qx;
        dst.qy = b.qy;
        dst.qz = b.qz;
        dst.lx = b.lx;
        dst.ly = b.ly;
        dst.lz = b.lz;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            dst.rotorOmega[i] = this.power.omega[i];
            dst.motorTempC[i] = this.thermal.motorTemp[i];
            dst.escTempC[i] = this.thermal.escTemp[i];
        }
        dst.soc = this.power.soc;
        dst.mAhUsed = this.power.mAhUsed;
        dst.polarizationVoltage = this.power.polarization;
        dst.lastBatteryCurrent = this.power.lastBatteryCurrent;
        dst.batteryHeatCurrent = this.power.heatCurrent;
        dst.lastBusVoltage = this.power.lastBusVoltage;
        dst.packSoc = this.power.packSoc;
        dst.packMahUsed = this.power.packMah;
        dst.armed = this.arming.armed;
        dst.sleeping = this.sleeping;
        dst.groundedFrames = this.groundedFrames;
        dst.rngState = this.rng.state();
        dst.accumulatorNanos = this.accumulatorNanos;
    }

    private void restorePhysical(SimSnapshot src) {
        RigidBody b = this.body;
        b.px = src.px;
        b.py = src.py;
        b.pz = src.pz;
        b.vx = src.vx;
        b.vy = src.vy;
        b.vz = src.vz;
        b.qw = src.qw;
        b.qx = src.qx;
        b.qy = src.qy;
        b.qz = src.qz;
        b.lx = src.lx;
        b.ly = src.ly;
        b.lz = src.lz;
        double norm = Math.sqrt(b.qw * b.qw + b.qx * b.qx + b.qy * b.qy + b.qz * b.qz);
        if (norm > 1e-9 && Double.isFinite(norm)) {
            b.qw /= norm;
            b.qx /= norm;
            b.qy /= norm;
            b.qz /= norm;
        }
        b.updateDerived();
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            this.power.omega[i] = SimMath.clamp(src.rotorOmega[i], -this.params.omegaMax, this.params.omegaMax);
            this.thermal.motorTemp[i] = src.motorTempC[i];
            this.thermal.escTemp[i] = src.escTempC[i];
        }
        this.power.soc = src.soc;
        this.power.mAhUsed = src.mAhUsed;
        this.power.polarization = src.polarizationVoltage;
        this.power.lastBatteryCurrent = src.lastBatteryCurrent;
        this.power.heatCurrent = src.batteryHeatCurrent;
        this.power.lastBusVoltage = src.lastBusVoltage;
        this.power.busVoltage = src.lastBusVoltage;
        this.power.packSoc = src.packSoc;
        this.power.packMah = src.packMahUsed;
        this.sleeping = src.sleeping;
        this.groundedFrames = src.groundedFrames;
        this.rng.setState(src.rngState);
    }

    public void copyStateFrom(SimSnapshot src) {
        if (src == null) {
            return;
        }
        src.copyTo(this.incoming);
        if (this.repairSnapshot(this.incoming)) {
            this.faults++;
        }
        double beforeX = this.body.px;
        double beforeY = this.body.py;
        double beforeZ = this.body.pz;
        this.restorePhysical(this.incoming);
        this.nav.shift(this.body.px - beforeX, this.body.py - beforeY, this.body.pz - beforeZ);
        this.accumulatorNanos = Math.max(0L, Math.min(src.accumulatorNanos, SUBSTEP_NANOS - 1));
        boolean wantArmed = src.armed;
        if (this.arming.armed && !wantArmed) {
            this.disarm(DisarmReason.FORCED);
        }
        this.arming.armed = wantArmed;
        this.gyroModel.measure(this.body.wbx, this.body.wby, this.body.wbz);
        this.fc.reset(this.gyroModel.raw, this.power.busVoltage, this.appliedRoll, this.appliedPitch, this.appliedYaw,
                this.appliedThrottle);
        if (!wantArmed) {
            this.clearDuties();
        }
        double tau = this.accumulatorNanos * 1e-9;
        this.visX = this.body.px + this.body.vx * tau;
        this.visY = this.body.py + this.body.vy * tau;
        this.visZ = this.body.pz + this.body.vz * tau;
        this.frameStartY = this.body.py;
        this.updateHoverEstimate();
        this.fillTelemetry(0);
    }

    private boolean repairSnapshot(SimSnapshot s) {
        RigidBody b = this.body;
        boolean repaired = false;
        if (!Double.isFinite(s.px) || !Double.isFinite(s.py) || !Double.isFinite(s.pz)) {
            s.px = SimMath.finiteOr(b.px, 0.0);
            s.py = SimMath.finiteOr(b.py, 0.0);
            s.pz = SimMath.finiteOr(b.pz, 0.0);
            repaired = true;
        }
        if (!Double.isFinite(s.vx + s.vy + s.vz)) {
            s.vx = 0.0;
            s.vy = 0.0;
            s.vz = 0.0;
            repaired = true;
        }
        if (!Double.isFinite(s.lx + s.ly + s.lz)) {
            s.lx = 0.0;
            s.ly = 0.0;
            s.lz = 0.0;
            repaired = true;
        }
        double qn = s.qw * s.qw + s.qx * s.qx + s.qy * s.qy + s.qz * s.qz;
        if (!Double.isFinite(qn) || qn < 1e-12) {
            s.qw = b.qw;
            s.qx = b.qx;
            s.qy = b.qy;
            s.qz = b.qz;
            repaired = true;
        }
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            if (!Double.isFinite(s.rotorOmega[i])) {
                s.rotorOmega[i] = 0.0;
                repaired = true;
            }
            if (!Double.isFinite(s.motorTempC[i])) {
                s.motorTempC[i] = this.env.ambientTempC;
                repaired = true;
            }
            if (!Double.isFinite(s.escTempC[i])) {
                s.escTempC[i] = this.env.ambientTempC;
                repaired = true;
            }
        }
        if (!Double.isFinite(s.soc)) {
            s.soc = this.power.soc;
            repaired = true;
        }
        s.soc = SimMath.clamp(s.soc, ModelConstants.SOC_MIN, 1.0);
        if (!Double.isFinite(s.mAhUsed)) {
            s.mAhUsed = (1.0 - s.soc) * this.params.capacityMah;
            repaired = true;
        }
        if (!Double.isFinite(s.polarizationVoltage + s.lastBatteryCurrent + s.batteryHeatCurrent)) {
            s.polarizationVoltage = 0.0;
            s.lastBatteryCurrent = 0.0;
            s.batteryHeatCurrent = 0.0;
            repaired = true;
        }
        if (!(s.lastBusVoltage > 0.0) || !Double.isFinite(s.lastBusVoltage)) {
            s.lastBusVoltage = OperatingPoints.emf(this.params, s.soc);
            repaired = true;
        }
        if (!Double.isFinite(s.packSoc)) {
            s.packSoc = s.soc;
            repaired = true;
        }
        s.packSoc = SimMath.clamp(s.packSoc, ModelConstants.SOC_MIN, 1.0);
        if (!Double.isFinite(s.packMahUsed)) {
            s.packMahUsed = (1.0 - s.packSoc) * this.params.capacityMah;
            repaired = true;
        }
        s.groundedFrames = Math.max(0, Math.min(s.groundedFrames, 2));
        return repaired;
    }

    public void getPosition(Vector3d dst) {
        dst.set(this.body.px, this.body.py, this.body.pz);
    }

    public void getVelocity(Vector3d dst) {
        dst.set(this.body.vx, this.body.vy, this.body.vz);
    }

    public void getAttitude(Quaterniond dst) {
        dst.set(this.body.qx, this.body.qy, this.body.qz, this.body.qw);
    }

    void setPoseLocked(boolean locked) {
        this.poseLocked = locked;
    }

    void setDutyOverride(double d0, double d1, double d2, double d3) {
        this.overrideDuty[0] = d0;
        this.overrideDuty[1] = d1;
        this.overrideDuty[2] = d2;
        this.overrideDuty[3] = d3;
        this.dutyOverrideActive = true;
    }

    void clearDutyOverride() {
        this.dutyOverrideActive = false;
    }

    void injectFaultNextSubstep() {
        this.injectFault = true;
    }

    void injectThrustFaultNextSubstep() {
        this.injectThrustFault = true;
    }

    double specificForceWorld(int axis) {
        RigidBody b = this.body;
        double x = this.specificX;
        double y = this.specificY;
        double z = this.specificZ;
        return switch (axis) {
            case 0 -> b.r00 * x + b.r01 * y + b.r02 * z;
            case 1 -> b.r10 * x + b.r11 * y + b.r12 * z;
            default -> b.r20 * x + b.r21 * y + b.r22 * z;
        };
    }

    private void fillTelemetry(int substeps) {
        Telemetry tm = this.telemetry;
        RigidBody b = this.body;
        AirframeSpec p = this.params;
        PowerSystem pw = this.power;
        tm.time = this.time;
        tm.substeps = substeps;
        tm.armBlockers = this.arming.blockers;
        tm.failsafeStage = this.arming.failsafeStage;
        tm.faults = this.faults;
        tm.crashCount = this.crashCount;
        tm.impactCount = this.impactCount;
        tm.propStrikeCount = this.propStrikeCount;
        tm.armed = this.arming.armed;
        tm.airmodeActive = this.arming.armed && this.fc.pid().airmode && this.fc.airmodeLatched;
        tm.grounded = this.groundedFrames > 0;
        tm.sleeping = this.sleeping;
        tm.motorSaturated = this.arming.armed && this.fc.mixer.saturated;
        tm.inGroundEffect = this.maxGroundFactor > 1.02;
        tm.mode = this.arming.failsafeStage == 1 ? StabMode.ANGLE : this.input.mode;
        tm.mode3d = this.input.mode3d && this.arming.failsafeStage != 1 && this.nav.mode == HoldMode.OFF;
        tm.lastDisarmReason = this.arming.lastDisarmReason;
        tm.batteryState = this.batteryState;
        double sumThrust = 0.0;
        double maxVrs = 0.0;
        boolean derating = false;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            tm.motorRpm[i] = pw.omega[i] * 60.0 / SimMath.TWO_PI;
            tm.motorOutput[i] = this.arming.armed ? this.fc.motorOutput[i] : 0.0;
            tm.motorDuty[i] = this.dutyCur[i];
            tm.motorCurrentA[i] = pw.current[i];
            tm.motorThrustN[i] = this.thrust[i];
            tm.motorTempC[i] = this.thermal.motorTemp[i];
            tm.escTempC[i] = this.thermal.escTemp[i];
            tm.motorPowerLimit[i] = this.thermal.limit[i];
            derating |= this.thermal.derating[i];
            sumThrust += this.thrust[i];
            maxVrs = Math.max(maxVrs, this.wash.vrs[i]);
        }
        tm.motorDerating = derating;
        tm.escBurned = this.escBurned;
        tm.totalCurrentA = pw.batteryCurrent;
        tm.packVoltage = pw.busVoltage;
        tm.cellVoltage = pw.busVoltage / p.cells;
        tm.cellVoltageResting = CellCurve.ocv(p.chemistry, PowerSystem.restingSoc(pw.soc, this.tuning.batterySag));
        tm.mAhUsed = pw.mAhUsed;
        tm.chargeFraction = Math.max(0.0, pw.soc);
        tm.powerW = pw.busVoltage * pw.batteryCurrent;
        double packVolts = pw.packVoltage(this.tuning.batterySag);
        tm.batteryCurrentA = pw.packCurrent;
        tm.batteryVoltage = packVolts;
        tm.batteryCellVoltage = packVolts / p.cells;
        tm.batteryCellResting = CellCurve.ocv(p.chemistry, PowerSystem.restingSoc(pw.packSoc, this.tuning.batterySag));
        tm.batteryMahUsed = pw.packMah;
        tm.batteryCharge = Math.max(0.0, pw.packSoc);
        tm.batteryPowerW = packVolts * pw.packCurrent;
        tm.propWash = this.washFilter.value();
        tm.propWashVrs = maxVrs;
        tm.propWashWake = this.wakeWeight;
        tm.groundEffectFactor = this.maxGroundFactor;
        tm.totalThrustN = sumThrust;
        tm.thrustToWeight = sumThrust / (p.massKg * SimTuning.referenceGravity(this.gravity));
        tm.gLoad = this.gLoadFilter.value() / ModelConstants.G;
        tm.gLoadTotal = SimMath.hypot3(this.specificX, this.specificY, this.specificZ) / ModelConstants.G;
        tm.gLoadMax = this.gLoadMax;
        tm.speed = SimMath.hypot3(b.vx, b.vy, b.vz);
        tm.airspeed = SimMath.hypot3(b.vx - this.windX, b.vy - this.windY, b.vz - this.windZ);
        tm.verticalSpeed = b.vy;
        tm.heightAboveGround = this.env.groundDistance - (b.py - this.frameStartY) + p.hitboxFootOffset;
        tm.rollDeg = StrictMath.asin(SimMath.clamp(b.r10, -1.0, 1.0)) * SimMath.DEG;
        tm.pitchDeg = StrictMath.asin(SimMath.clamp(-b.r12, -1.0, 1.0)) * SimMath.DEG;
        tm.headingDeg = b.headingRad() * SimMath.DEG;
        tm.tiltDeg = b.tiltRad() * SimMath.DEG;
        for (int a = 0; a < 3; a++) {
            AxisPidController c = this.fc.axes[a];
            tm.gyroDps[a] = this.fc.gyro[a];
            tm.setpointDps[a] = this.fc.setpoint[a];
            tm.pTerm[a] = c.pOut * 1000.0;
            tm.iTerm[a] = c.iTerm * 1000.0;
            tm.dTerm[a] = c.dOut * 1000.0;
            tm.fTerm[a] = c.fOut * 1000.0;
        }
        tm.throttleOut = this.arming.armed ? this.fc.signedThrottleOut() : 0.0;
        tm.hoverThrottleStick = this.hoverStick;
        tm.attitudeErrorDeg = this.fc.attitude.errorDeg;
        tm.lastImpactSpeed = this.lastImpactSpeed;
        double ratio = p.hoverOmega > 0.0
                ? 0.25 * (Math.abs(pw.omega[0]) + Math.abs(pw.omega[1]) + Math.abs(pw.omega[2]) + Math.abs(pw.omega[3])) / p.hoverOmega : 0.0;
        tm.vibration = Math.min(WearEffects.MAX_VIBRATION, this.wearVibration * ratio * ratio);
        NavHold nav = this.nav;
        tm.holdMode = nav.mode;
        tm.holdRequested = nav.requested;
        tm.posHoldDenied = nav.posDenied;
        tm.posHoldReady = nav.positionUsable(this.config.hold);
        tm.gpsSatellites = nav.gps.satellites;
        tm.gpsFix = nav.gps.fix;
        tm.gpsHdop = nav.gps.hdop;
        tm.altitudeEstimate = nav.zEst;
        tm.baroAltitude = nav.baro.altitude;
        tm.holdTargetAltitude = nav.mode == HoldMode.OFF ? nav.zEst : nav.targetAlt;
        tm.holdPositionError = nav.mode == HoldMode.POSHOLD ? Math.hypot(nav.targetX - b.px, nav.targetZ - b.pz) : 0.0;
        tm.gpsPositionError = nav.horizValid ? Math.hypot(nav.xEst - b.px, nav.zhEst - b.pz) : 0.0;
        tm.headingErrorDeg = nav.headingErrorRad * SimMath.DEG;
    }
}
