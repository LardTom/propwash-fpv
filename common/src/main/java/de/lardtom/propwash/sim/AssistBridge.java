package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.flight.ForceSink;
import de.lardtom.propwash.api.flight.SensorDirection;
import de.lardtom.propwash.api.flight.StabilizationMode;

import java.util.Arrays;

final class AssistBridge implements FlightState, AssistOutput, ForceSink {
    static final double YAW_GAIN = 2.5;
    static final double MAX_YAW_RATE = 180.0;

    private final DroneSim sim;
    final double[] obstacles = new double[SensorDirection.values().length];

    boolean sticks;
    double roll;
    double pitch;
    double yaw;
    double throttle;
    StabilizationMode mode;
    boolean altitude;
    double altitudeTarget;
    boolean climb;
    double climbRate;
    int horizontal;
    double targetX;
    double targetZ;
    double velocityX;
    double velocityZ;
    double maxSpeed;
    boolean yawRate;
    double yawRateDps;
    boolean heading;
    double headingTarget;
    String label;
    boolean returnHome;

    double sumFx;
    double sumFy;
    double sumFz;
    double sumTx;
    double sumTy;
    double sumTz;

    AssistBridge(DroneSim sim) {
        this.sim = sim;
        Arrays.fill(this.obstacles, Double.NaN);
    }

    void resetOutput() {
        this.sticks = false;
        this.mode = null;
        this.altitude = false;
        this.climb = false;
        this.horizontal = 0;
        this.maxSpeed = NavHold.MAX_HOLD_SPEED;
        this.yawRate = false;
        this.heading = false;
        this.label = null;
    }

    void resetForces() {
        this.sumFx = 0.0;
        this.sumFy = 0.0;
        this.sumFz = 0.0;
        this.sumTx = 0.0;
        this.sumTy = 0.0;
        this.sumTz = 0.0;
    }

    boolean controlsAttitude() {
        return this.sticks || this.mode != null || this.yawRate || this.heading;
    }

    boolean guidesNav() {
        return this.altitude || this.climb || this.horizontal != 0;
    }

    double yawStick(RigidBody b, RateProfile rates) {
        double rate;
        if (this.heading) {
            double current = compass(b.headingRad() * SimMath.DEG);
            double error = wrap180(this.headingTarget - current);
            rate = SimMath.clamp(YAW_GAIN * error, -MAX_YAW_RATE, MAX_YAW_RATE);
        } else {
            rate = this.yawRateDps;
        }
        double magnitude = Math.min(Math.abs(rate), RateCurves.maxRateDps(rates.type, rates.yaw));
        double stick = magnitude <= 0.0 ? 0.0 : RateCurves.stickFor(rates.type, rates.yaw, magnitude);
        return SimMath.clamp(Math.signum(rate) * stick, -1.0, 1.0);
    }

    static double compass(double simHeadingDeg) {
        double d = (180.0 - simHeadingDeg) % 360.0;
        return d < 0.0 ? d + 360.0 : d;
    }

    static double wrap180(double degrees) {
        double d = degrees % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        } else if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }

    static StabMode stabMode(StabilizationMode mode) {
        return switch (mode) {
            case ACRO -> StabMode.ACRO;
            case ANGLE -> StabMode.ANGLE;
            case HORIZON -> StabMode.HORIZON;
        };
    }

    static StabilizationMode apiMode(StabMode mode) {
        return switch (mode) {
            case ACRO -> StabilizationMode.ACRO;
            case ANGLE -> StabilizationMode.ANGLE;
            case HORIZON -> StabilizationMode.HORIZON;
        };
    }

    private static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void sticks(double roll, double pitch, double yaw, double throttle) {
        if (!finite(roll, pitch, yaw, throttle)) {
            return;
        }
        this.sticks = true;
        this.roll = SimMath.clamp(roll, -1.0, 1.0);
        this.pitch = SimMath.clamp(pitch, -1.0, 1.0);
        this.yaw = SimMath.clamp(yaw, -1.0, 1.0);
        this.throttle = SimMath.clamp(throttle, 0.0, 1.0);
    }

    @Override
    public void mode(StabilizationMode mode) {
        this.mode = mode;
    }

    @Override
    public void holdAltitude(double y) {
        if (finite(y)) {
            this.altitude = true;
            this.climb = false;
            this.altitudeTarget = y;
        }
    }

    @Override
    public void climb(double metresPerSecond) {
        if (finite(metresPerSecond)) {
            this.climb = true;
            this.altitude = false;
            this.climbRate = SimMath.clamp(metresPerSecond, -MAX_CLIMB, MAX_CLIMB);
        }
    }

    @Override
    public void holdPosition(double x, double z) {
        if (finite(x, z)) {
            this.horizontal = 1;
            this.targetX = x;
            this.targetZ = z;
        }
    }

    @Override
    public void velocity(double vx, double vz) {
        if (finite(vx, vz)) {
            this.horizontal = 2;
            this.velocityX = vx;
            this.velocityZ = vz;
        }
    }

    @Override
    public void maxSpeed(double metresPerSecond) {
        if (finite(metresPerSecond)) {
            this.maxSpeed = SimMath.clamp(metresPerSecond, 0.0, MAX_SPEED);
        }
    }

    @Override
    public void yawRate(double degreesPerSecond) {
        if (finite(degreesPerSecond)) {
            this.yawRate = true;
            this.heading = false;
            this.yawRateDps = SimMath.clamp(degreesPerSecond, -RateCurves.RATE_LIMIT_DPS, RateCurves.RATE_LIMIT_DPS);
        }
    }

    @Override
    public void heading(double compassDegrees) {
        if (finite(compassDegrees)) {
            this.heading = true;
            this.yawRate = false;
            this.headingTarget = compassDegrees;
        }
    }

    @Override
    public void label(String text) {
        if (text == null || text.isBlank()) {
            this.label = null;
            return;
        }
        String trimmed = text.strip();
        this.label = trimmed.length() > MAX_LABEL ? trimmed.substring(0, MAX_LABEL) : trimmed;
    }

    @Override
    public void requestReturnHome() {
        this.returnHome = true;
    }

    @Override
    public void addForce(double fx, double fy, double fz) {
        if (finite(fx, fy, fz)) {
            this.sumFx += fx;
            this.sumFy += fy;
            this.sumFz += fz;
        }
    }

    @Override
    public void addForceAt(double fx, double fy, double fz, double frameX, double frameY, double frameZ) {
        if (!finite(fx, fy, fz, frameX, frameY, frameZ)) {
            return;
        }
        RigidBody b = this.sim.body;
        double bx = -frameX;
        double by = frameY;
        double bz = -frameZ;
        double rx = b.r00 * bx + b.r01 * by + b.r02 * bz;
        double ry = b.r10 * bx + b.r11 * by + b.r12 * bz;
        double rz = b.r20 * bx + b.r21 * by + b.r22 * bz;
        this.addForce(fx, fy, fz);
        this.addTorque(ry * fz - rz * fy, rz * fx - rx * fz, rx * fy - ry * fx);
    }

    @Override
    public void addTorque(double tx, double ty, double tz) {
        if (finite(tx, ty, tz)) {
            this.sumTx += tx;
            this.sumTy += ty;
            this.sumTz += tz;
        }
    }

    @Override
    public double time() {
        return this.sim.simTime();
    }

    @Override
    public double massKg() {
        return this.sim.params().massKg;
    }

    @Override
    public double gravity() {
        return this.sim.gravity();
    }

    @Override
    public double x() {
        return this.sim.body.px;
    }

    @Override
    public double y() {
        return this.sim.body.py;
    }

    @Override
    public double z() {
        return this.sim.body.pz;
    }

    @Override
    public double vx() {
        return this.sim.body.vx;
    }

    @Override
    public double vy() {
        return this.sim.body.vy;
    }

    @Override
    public double vz() {
        return this.sim.body.vz;
    }

    @Override
    public double estimatedX() {
        return this.sim.nav.horizValid ? this.sim.nav.xEst : Double.NaN;
    }

    @Override
    public double estimatedY() {
        return this.sim.nav.zEst;
    }

    @Override
    public double estimatedZ() {
        return this.sim.nav.horizValid ? this.sim.nav.zhEst : Double.NaN;
    }

    @Override
    public double estimatedVx() {
        return this.sim.nav.horizValid ? this.sim.nav.vxEst : Double.NaN;
    }

    @Override
    public double estimatedVy() {
        return this.sim.nav.vzEst;
    }

    @Override
    public double estimatedVz() {
        return this.sim.nav.horizValid ? this.sim.nav.vzhEst : Double.NaN;
    }

    @Override
    public boolean positionValid() {
        return this.sim.nav.positionUsable(this.sim.config().hold);
    }

    @Override
    public boolean gpsFix() {
        return this.sim.nav.gps.fix;
    }

    @Override
    public int satellites() {
        return this.sim.nav.gps.satellites;
    }

    @Override
    public double headingDeg() {
        return compass(this.sim.body.headingRad() * SimMath.DEG);
    }

    @Override
    public double pitchDeg() {
        return StrictMath.asin(SimMath.clamp(this.sim.body.r12, -1.0, 1.0)) * SimMath.DEG;
    }

    @Override
    public double rollDeg() {
        return StrictMath.asin(SimMath.clamp(this.sim.body.r10, -1.0, 1.0)) * SimMath.DEG;
    }

    @Override
    public double rollRateDps() {
        return this.sim.body.wbz * SimMath.DEG;
    }

    @Override
    public double pitchRateDps() {
        return -this.sim.body.wbx * SimMath.DEG;
    }

    @Override
    public double yawRateDps() {
        return -this.sim.body.wby * SimMath.DEG;
    }

    @Override
    public double forwardX() {
        return this.sim.body.r02;
    }

    @Override
    public double forwardY() {
        return this.sim.body.r12;
    }

    @Override
    public double forwardZ() {
        return this.sim.body.r22;
    }

    @Override
    public double upX() {
        return this.sim.body.r01;
    }

    @Override
    public double upY() {
        return this.sim.body.r11;
    }

    @Override
    public double upZ() {
        return this.sim.body.r21;
    }

    @Override
    public double rightX() {
        return -this.sim.body.r00;
    }

    @Override
    public double rightY() {
        return -this.sim.body.r10;
    }

    @Override
    public double rightZ() {
        return -this.sim.body.r20;
    }

    @Override
    public double heightAboveGround() {
        return this.sim.heightAboveGroundNow();
    }

    @Override
    public boolean armed() {
        return this.sim.arming.armed;
    }

    @Override
    public boolean grounded() {
        return this.sim.groundedNow();
    }

    @Override
    public boolean failsafe() {
        return this.sim.arming.failsafeStage != 0;
    }

    @Override
    public double hoverThrottle() {
        return this.sim.hoverStickNow();
    }

    @Override
    public double batteryCharge() {
        return Math.max(0.0, this.sim.power.packSoc);
    }

    @Override
    public double stickRoll() {
        return this.sim.pilotInput().roll;
    }

    @Override
    public double stickPitch() {
        return this.sim.pilotInput().pitch;
    }

    @Override
    public double stickYaw() {
        return this.sim.pilotInput().yaw;
    }

    @Override
    public double stickThrottle() {
        return this.sim.pilotInput().throttle;
    }

    @Override
    public StabilizationMode pilotMode() {
        StabMode mode = this.sim.pilotInput().mode;
        return apiMode(mode == null ? StabMode.ACRO : mode);
    }

    @Override
    public boolean action(int number) {
        return number >= 1 && number <= ACTIONS && (this.sim.pilotInput().actions & (1 << (number - 1))) != 0;
    }

    @Override
    public double obstacleDistance(SensorDirection direction) {
        return direction == null ? Double.NaN : this.obstacles[direction.ordinal()];
    }
}
