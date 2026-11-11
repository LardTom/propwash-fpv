package de.lardtom.propwash.rescue;

import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.SensorDirection;
import de.lardtom.propwash.api.flight.StabilizationMode;
import de.lardtom.propwash.sim.BatteryState;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import org.jspecify.annotations.Nullable;

final class ServerFlightAssist {
    static final double HARD_LIMIT_SECONDS = AutonomyOptions.MAX_DURATION.toMillis() / 1000.0;
    static final double TAKEOFF_TOLERANCE = 0.3;
    static final double TAKEOFF_SETTLE_SPEED = 0.5;
    static final double TAKEOFF_TIMEOUT = 20.0;
    static final double TAKEOFF_DRIFT_SPEED = 1.0;
    static final double HOLD_SPEED = ServerFlightState.DEFAULT_MAX_SPEED;
    static final int LAND_SCAN_DEPTH = 96;
    static final double BATTERY_RESERVE = 0.15;
    static final double TERRAIN_LIMIT_SECONDS = 1.0;

    enum Phase {
        TAKEOFF,
        ASSIST,
        LAND,
        DONE
    }

    private final ServerFlightState state = new ServerFlightState();
    private final ServerFlightControl control = new ServerFlightControl();
    private final RescueAutopilot lander = new RescueAutopilot();
    private final RescueSettings landing = landingSettings();
    private final double[] speed = new double[2];
    private FlightAssist assist;
    private AutonomyOptions options;
    private Phase phase = Phase.DONE;
    private double maxSeconds;
    private double sinceStart;
    private double total;
    private double phaseTime;
    private double takeoffY;
    private double takeoffX;
    private double takeoffZ;
    private double holdX;
    private double holdZ;
    private double holdY;
    private boolean addonActive;
    private boolean landRequested;
    private boolean homeRequested;
    private boolean landingStarted;
    private @Nullable AutonomyEnd ended;
    private @Nullable RuntimeException failure;
    private @Nullable String label;
    private double sensorRange;
    private double terrainHeld;
    private boolean relatch;

    static RescueSettings landingSettings() {
        RescueSettings s = new RescueSettings();
        s.autoLand = true;
        s.stickOverride = false;
        s.sanitize();
        return s;
    }

    void startInFlight(DroneSim sim, double x, double bottom, double z, FlightAssist assist, AutonomyOptions options) {
        this.begin(sim, x, bottom, z, assist, options);
        this.enterAssist();
    }

    void startFromGround(DroneSim sim, double x, double bottom, double z, FlightAssist assist, AutonomyOptions options) {
        this.begin(sim, x, bottom, z, assist, options);
        this.takeoffX = x;
        this.takeoffZ = z;
        this.takeoffY = this.control.comY + options.takeoff().orElse(AutonomyOptions.MIN_TAKEOFF_METRES);
        this.phase = Phase.TAKEOFF;
        this.phaseTime = 0.0;
    }

    private void begin(DroneSim sim, double x, double bottom, double z, FlightAssist assist, AutonomyOptions options) {
        this.control.reset();
        this.control.read(sim, x, bottom, z);
        this.total = 0.0;
        this.ended = null;
        this.failure = null;
        this.landingStarted = false;
        this.attach(assist, options);
        this.latchHold();
    }

    void replace(FlightAssist assist, AutonomyOptions options) {
        this.attach(assist, options);
        if (this.phase != Phase.TAKEOFF) {
            this.enterAssist();
        }
    }

    private void attach(FlightAssist assist, AutonomyOptions options) {
        this.assist = assist;
        this.options = options;
        this.maxSeconds = Math.min(HARD_LIMIT_SECONDS, options.maxDuration().toMillis() / 1000.0);
        this.sinceStart = 0.0;
        this.addonActive = true;
        this.landRequested = false;
        this.homeRequested = false;
        this.label = null;
        this.sensorRange = 0.0;
        this.state.resetOutput();
        this.state.land = false;
        this.state.returnHome = false;
        this.state.terrainLimit = false;
        this.terrainHeld = 0.0;
        this.state.clearObstacles();
    }

    private void enterAssist() {
        this.phase = Phase.ASSIST;
        this.phaseTime = 0.0;
        this.latchHold();
        try {
            this.assist.reset();
        } catch (RuntimeException e) {
            this.fail(e);
        }
    }

    private void latchHold() {
        this.holdX = this.control.x;
        this.holdZ = this.control.z;
        this.holdY = this.control.comY;
    }

    private void fail(RuntimeException e) {
        this.failure = e;
        this.end(AutonomyEnd.ERROR);
    }

    void end(AutonomyEnd reason) {
        if (!this.addonActive) {
            return;
        }
        this.addonActive = false;
        this.ended = reason;
        this.landRequested = false;
        if (this.phase != Phase.DONE) {
            this.phase = Phase.LAND;
            this.phaseTime = 0.0;
        }
    }

    void terrainLimit(boolean held, double dt) {
        boolean limit = held && this.addonActive && this.phase == Phase.ASSIST;
        this.state.terrainLimit = limit;
        if (!limit) {
            this.terrainHeld = Math.max(0.0, this.terrainHeld - Math.max(0.0, dt));
            return;
        }
        this.terrainHeld += Math.max(0.0, dt);
        if (this.terrainHeld >= TERRAIN_LIMIT_SECONDS) {
            this.terrainHeld = 0.0;
            this.state.terrainLimit = false;
            this.end(AutonomyEnd.TERRAIN_LIMIT);
        }
    }

    double sensorRange() {
        if (!this.addonActive || this.phase != Phase.ASSIST) {
            return 0.0;
        }
        try {
            double range = this.assist.sensorRange();
            this.sensorRange = Double.isFinite(range) ? Math.max(0.0, Math.min(64.0, range)) : 0.0;
        } catch (RuntimeException e) {
            this.fail(e);
            this.sensorRange = 0.0;
        }
        return this.sensorRange;
    }

    void obstacle(SensorDirection direction, double metres) {
        this.state.obstacle(direction, metres);
    }

    void update(double dt, DroneSim sim, double x, double bottom, double z, RescueWorld world, PilotInput input) {
        this.control.read(sim, x, bottom, z);
        if (this.relatch) {
            this.relatch = false;
            double dy = this.takeoffY - this.holdY;
            this.latchHold();
            this.takeoffX = this.control.x;
            this.takeoffZ = this.control.z;
            this.takeoffY = this.control.comY + Math.max(0.0, dy);
        }
        Telemetry t = sim.telemetry();
        if (dt > 0.0) {
            this.total += dt;
            this.phaseTime += dt;
            if (this.addonActive && this.phase != Phase.LAND) {
                this.sinceStart += dt;
            }
        }
        if (this.addonActive && this.phase != Phase.LAND) {
            if (batteryLow(t)) {
                this.end(AutonomyEnd.BATTERY);
            } else if (timedOut(this.sinceStart, this.total, this.maxSeconds)) {
                this.end(AutonomyEnd.TIMEOUT);
            }
        }
        input.armSwitch = true;
        input.linkOk = true;
        input.altHold = false;
        input.posHold = false;
        input.mode3d = false;
        input.turtle = false;
        switch (this.phase) {
            case TAKEOFF -> this.takeoff(dt, input);
            case ASSIST -> this.assist(dt, sim, input);
            case LAND -> this.land(dt, sim, x, bottom, z, world, input);
            case DONE -> this.idle(input);
        }
    }

    private void takeoff(double dt, PilotInput input) {
        ServerFlightControl c = this.control;
        double error = this.takeoffY - c.comY;
        double vyd = c.climbToward(error, c.climbRate());
        c.positionSpeed(this.takeoffX - c.x, this.takeoffZ - c.z, TAKEOFF_DRIFT_SPEED, this.speed);
        c.control(dt, this.speed[0], this.speed[1], vyd);
        this.applyControl(input);
        input.yaw = 0.0;
        if (Math.abs(error) < TAKEOFF_TOLERANCE && Math.abs(c.vy) < TAKEOFF_SETTLE_SPEED || this.phaseTime >= TAKEOFF_TIMEOUT) {
            this.enterAssist();
        }
    }

    private void assist(double dt, DroneSim sim, PilotInput input) {
        ServerFlightState s = this.state;
        ServerFlightControl c = this.control;
        s.read(sim, sim.telemetry().time);
        s.resetOutput();
        try {
            this.assist.update(s, s);
        } catch (RuntimeException e) {
            this.fail(e);
            this.idleHold(dt, input);
            return;
        }
        this.label = s.label;
        if (s.land) {
            s.land = false;
            this.landRequested = true;
            this.phase = Phase.LAND;
            this.phaseTime = 0.0;
            this.idleHold(dt, input);
            return;
        }
        if (s.returnHome) {
            s.returnHome = false;
            this.homeRequested = true;
            this.end(AutonomyEnd.STOPPED);
            this.idleHold(dt, input);
            return;
        }
        boolean rawHorizontal = s.horizontal == 0 && s.sticks;
        boolean rawVertical = !s.guidesVertical() && s.sticks;
        double vyd;
        if (s.climb) {
            vyd = s.climbRate;
            this.holdY = c.comY;
        } else if (s.altitude) {
            vyd = c.climbToward(s.altitudeTarget - c.comY, Math.max(c.climbRate(), s.maxSpeed));
            this.holdY = c.comY;
        } else if (rawVertical) {
            vyd = Double.NaN;
            this.holdY = c.comY;
        } else {
            vyd = c.climbToward(this.holdY - c.comY, c.climbRate());
        }
        double vdx = 0.0;
        double vdz = 0.0;
        if (s.horizontal == 1) {
            c.positionSpeed(s.targetX - c.x, s.targetZ - c.z, s.maxSpeed, this.speed);
            vdx = this.speed[0];
            vdz = this.speed[1];
            this.holdX = c.x;
            this.holdZ = c.z;
        } else if (s.horizontal == 2) {
            vdx = s.velocityX;
            vdz = s.velocityZ;
            double v = Math.hypot(vdx, vdz);
            if (v > s.maxSpeed && v > 1.0E-9) {
                vdx *= s.maxSpeed / v;
                vdz *= s.maxSpeed / v;
            }
            this.holdX = c.x;
            this.holdZ = c.z;
        } else if (rawHorizontal) {
            this.holdX = c.x;
            this.holdZ = c.z;
        } else {
            c.positionSpeed(this.holdX - c.x, this.holdZ - c.z, HOLD_SPEED, this.speed);
            vdx = this.speed[0];
            vdz = this.speed[1];
        }
        if (rawHorizontal && rawVertical) {
            input.roll = s.roll;
            input.pitch = s.pitch;
            input.throttle = s.throttle;
            input.mode = s.mode == null ? StabMode.ANGLE : stabMode(s.mode);
        } else {
            c.control(dt, vdx, vdz, vyd);
            input.roll = rawHorizontal ? s.roll : clamp(c.roll(), -1.0, 1.0);
            input.pitch = rawHorizontal ? s.pitch : clamp(c.pitch(), -1.0, 1.0);
            input.throttle = rawVertical ? s.throttle : clamp(c.throttle(), 0.0, 1.0);
            input.mode = rawHorizontal && s.mode != null ? stabMode(s.mode) : StabMode.ANGLE;
        }
        if (s.heading) {
            input.yaw = clamp(c.yawForHeading(s.headingTarget), -1.0, 1.0);
        } else if (s.yawRate) {
            input.yaw = clamp(c.yawForRate(s.yawRateDps), -1.0, 1.0);
        } else {
            input.yaw = s.sticks ? s.yaw : 0.0;
        }
    }

    private void idleHold(double dt, PilotInput input) {
        ServerFlightControl c = this.control;
        c.positionSpeed(this.holdX - c.x, this.holdZ - c.z, HOLD_SPEED, this.speed);
        c.control(dt, this.speed[0], this.speed[1], c.climbToward(this.holdY - c.comY, c.climbRate()));
        this.applyControl(input);
        input.yaw = 0.0;
    }

    private void land(double dt, DroneSim sim, double x, double bottom, double z, RescueWorld world, PilotInput input) {
        if (!this.landingStarted) {
            this.landingStarted = true;
            double ground = WorldProbe.groundBelow(world, x, bottom, z, 0.3, LAND_SCAN_DEPTH);
            this.lander.setAutonomous(true);
            this.lander.start(sim, x, bottom, z, x, Double.isFinite(ground) ? ground : bottom, z, world, this.landing);
            this.lander.landNow(world);
        }
        this.lander.update(dt, sim, x, bottom, z, world, this.landing);
        input.roll = clamp(this.lander.roll(), -1.0, 1.0);
        input.pitch = clamp(this.lander.pitch(), -1.0, 1.0);
        input.yaw = clamp(this.lander.yaw(), -1.0, 1.0);
        input.throttle = clamp(this.lander.throttle(), 0.0, 1.0);
        input.mode = StabMode.ANGLE;
        if (this.lander.phase() == RescuePhase.LANDED) {
            this.phase = Phase.DONE;
        }
    }

    private void idle(PilotInput input) {
        input.roll = 0.0;
        input.pitch = 0.0;
        input.yaw = 0.0;
        input.throttle = 0.0;
        input.mode = StabMode.ANGLE;
    }

    private void applyControl(PilotInput input) {
        input.roll = clamp(this.control.roll(), -1.0, 1.0);
        input.pitch = clamp(this.control.pitch(), -1.0, 1.0);
        input.throttle = clamp(this.control.throttle(), 0.0, 1.0);
        input.mode = StabMode.ANGLE;
    }

    static boolean timedOut(double sinceStart, double total, double maxSeconds) {
        return sinceStart >= Math.min(maxSeconds, HARD_LIMIT_SECONDS) || total >= HARD_LIMIT_SECONDS;
    }

    static boolean batteryLow(Telemetry t) {
        return t.batteryState == BatteryState.CRITICAL || t.batteryState == BatteryState.EMPTY || t.batteryCharge < BATTERY_RESERVE;
    }

    static FollowUp followUp(AutonomyEnd reason, boolean homeRequested, boolean gpsFix) {
        return switch (reason) {
            case PILOT, LANDED, REPLACED -> FollowUp.NONE;
            case BATTERY, TIMEOUT -> FollowUp.HOME;
            case STOPPED -> homeRequested ? FollowUp.HOME : FollowUp.LAND;
            case OUT_OF_RANGE, TERRAIN_LIMIT -> gpsFix ? FollowUp.HOME : FollowUp.LAND;
            case OWNER_OFFLINE, ERROR -> FollowUp.LAND;
        };
    }

    static boolean needsGps(AutonomyEnd reason, boolean homeRequested) {
        return followUp(reason, homeRequested, true) != followUp(reason, homeRequested, false);
    }

    enum FollowUp {
        NONE,
        HOME,
        LAND
    }

    void disarmed(boolean landed) {
        this.phase = Phase.DONE;
        if (!this.addonActive) {
            return;
        }
        this.addonActive = false;
        this.ended = landed && this.landRequested ? AutonomyEnd.LANDED : AutonomyEnd.ERROR;
    }

    void relatch() {
        this.relatch = true;
    }

    boolean wantsDisarm() {
        return this.phase == Phase.DONE || this.phase == Phase.LAND && this.lander.wantsDisarm();
    }

    boolean landed() {
        return this.phase == Phase.DONE || this.lander.phase() == RescuePhase.LANDED;
    }

    Phase phase() {
        return this.phase;
    }

    boolean addonActive() {
        return this.addonActive;
    }

    boolean landRequested() {
        return this.landRequested;
    }

    boolean homeRequested() {
        return this.homeRequested;
    }

    @Nullable AutonomyEnd consumeEnded() {
        AutonomyEnd reason = this.ended;
        this.ended = null;
        return reason;
    }

    @Nullable RuntimeException consumeFailure() {
        RuntimeException e = this.failure;
        this.failure = null;
        return e;
    }

    AutonomyOptions options() {
        return this.options;
    }

    FlightAssist assist() {
        return this.assist;
    }

    String label() {
        String own = this.label;
        if (own != null && !own.isBlank()) {
            return own;
        }
        return this.options == null ? "" : this.options.label();
    }

    double sinceStart() {
        return this.sinceStart;
    }

    double total() {
        return this.total;
    }

    double takeoffX() {
        return this.takeoffX;
    }

    double takeoffZ() {
        return this.takeoffZ;
    }

    static StabMode stabMode(StabilizationMode mode) {
        return switch (mode) {
            case ACRO -> StabMode.ACRO;
            case ANGLE -> StabMode.ANGLE;
            case HORIZON -> StabMode.HORIZON;
        };
    }

    private static double clamp(double v, double min, double max) {
        return Double.isFinite(v) ? Math.max(min, Math.min(max, v)) : 0.0;
    }
}
