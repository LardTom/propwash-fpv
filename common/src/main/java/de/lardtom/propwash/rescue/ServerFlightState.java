package de.lardtom.propwash.rescue;

import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.flight.SensorDirection;
import de.lardtom.propwash.api.flight.StabilizationMode;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.Telemetry;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Arrays;

final class ServerFlightState implements FlightState, AssistOutput {
    static final int SATELLITES = 18;
    static final double NEUTRAL_THROTTLE = 0.5;
    static final double DEFAULT_MAX_SPEED = 4.0;

    private final Vector3d position = new Vector3d();
    private final Vector3d velocity = new Vector3d();
    private final Quaterniond attitude = new Quaterniond();
    private final double[] obstacles = new double[SensorDirection.values().length];
    private DroneSim sim;
    private Telemetry telemetry;
    private double time;
    private double r00 = 1.0;
    private double r01;
    private double r02;
    private double r10;
    private double r11 = 1.0;
    private double r12;
    private double r20;
    private double r21;
    private double r22 = 1.0;

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
    double maxSpeed = DEFAULT_MAX_SPEED;
    boolean yawRate;
    double yawRateDps;
    boolean heading;
    double headingTarget;
    String label;
    boolean returnHome;
    boolean land;
    boolean terrainLimit;

    ServerFlightState() {
        Arrays.fill(this.obstacles, Double.NaN);
    }

    void read(DroneSim sim, double time) {
        this.sim = sim;
        this.telemetry = sim.telemetry();
        this.time = time;
        sim.getPosition(this.position);
        sim.getVelocity(this.velocity);
        sim.getAttitude(this.attitude);
        double w = this.attitude.w;
        double x = this.attitude.x;
        double y = this.attitude.y;
        double z = this.attitude.z;
        this.r00 = 1.0 - 2.0 * (y * y + z * z);
        this.r01 = 2.0 * (x * y - w * z);
        this.r02 = 2.0 * (x * z + w * y);
        this.r10 = 2.0 * (x * y + w * z);
        this.r11 = 1.0 - 2.0 * (x * x + z * z);
        this.r12 = 2.0 * (y * z - w * x);
        this.r20 = 2.0 * (x * z - w * y);
        this.r21 = 2.0 * (y * z + w * x);
        this.r22 = 1.0 - 2.0 * (x * x + y * y);
    }

    void resetOutput() {
        this.sticks = false;
        this.mode = null;
        this.altitude = false;
        this.climb = false;
        this.horizontal = 0;
        this.maxSpeed = DEFAULT_MAX_SPEED;
        this.yawRate = false;
        this.heading = false;
        this.label = null;
    }

    boolean guidesVertical() {
        return this.altitude || this.climb;
    }

    void obstacle(SensorDirection direction, double metres) {
        this.obstacles[direction.ordinal()] = Double.isNaN(metres) ? Double.NaN : Math.max(0.0, metres);
    }

    void clearObstacles() {
        Arrays.fill(this.obstacles, Double.NaN);
    }

    @Override
    public boolean terrainLimit() {
        return this.terrainLimit;
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
        this.roll = Math.max(-1.0, Math.min(1.0, roll));
        this.pitch = Math.max(-1.0, Math.min(1.0, pitch));
        this.yaw = Math.max(-1.0, Math.min(1.0, yaw));
        this.throttle = Math.max(0.0, Math.min(1.0, throttle));
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
            this.climbRate = Math.max(-MAX_CLIMB, Math.min(MAX_CLIMB, metresPerSecond));
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
            this.maxSpeed = Math.max(0.0, Math.min(MAX_SPEED, metresPerSecond));
        }
    }

    @Override
    public void yawRate(double degreesPerSecond) {
        if (finite(degreesPerSecond)) {
            this.yawRate = true;
            this.heading = false;
            this.yawRateDps = degreesPerSecond;
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
    public void land() {
        this.land = true;
    }

    @Override
    public double time() {
        return this.time;
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
        return this.position.x;
    }

    @Override
    public double y() {
        return this.position.y;
    }

    @Override
    public double z() {
        return this.position.z;
    }

    @Override
    public double vx() {
        return this.velocity.x;
    }

    @Override
    public double vy() {
        return this.velocity.y;
    }

    @Override
    public double vz() {
        return this.velocity.z;
    }

    @Override
    public double estimatedX() {
        return this.position.x;
    }

    @Override
    public double estimatedY() {
        return this.position.y;
    }

    @Override
    public double estimatedZ() {
        return this.position.z;
    }

    @Override
    public double estimatedVx() {
        return this.velocity.x;
    }

    @Override
    public double estimatedVy() {
        return this.velocity.y;
    }

    @Override
    public double estimatedVz() {
        return this.velocity.z;
    }

    @Override
    public boolean positionValid() {
        return true;
    }

    @Override
    public boolean gpsFix() {
        return true;
    }

    @Override
    public int satellites() {
        return SATELLITES;
    }

    @Override
    public double headingDeg() {
        return ServerFlightControl.compass(Math.toDegrees(StrictMath.atan2(this.r02, this.r22)));
    }

    @Override
    public double pitchDeg() {
        return Math.toDegrees(StrictMath.asin(Math.max(-1.0, Math.min(1.0, this.r12))));
    }

    @Override
    public double rollDeg() {
        return Math.toDegrees(StrictMath.asin(Math.max(-1.0, Math.min(1.0, this.r10))));
    }

    @Override
    public double rollRateDps() {
        return this.telemetry.gyroDps[0];
    }

    @Override
    public double pitchRateDps() {
        return -this.telemetry.gyroDps[1];
    }

    @Override
    public double yawRateDps() {
        return this.telemetry.gyroDps[2];
    }

    @Override
    public double forwardX() {
        return this.r02;
    }

    @Override
    public double forwardY() {
        return this.r12;
    }

    @Override
    public double forwardZ() {
        return this.r22;
    }

    @Override
    public double upX() {
        return this.r01;
    }

    @Override
    public double upY() {
        return this.r11;
    }

    @Override
    public double upZ() {
        return this.r21;
    }

    @Override
    public double rightX() {
        return -this.r00;
    }

    @Override
    public double rightY() {
        return -this.r10;
    }

    @Override
    public double rightZ() {
        return -this.r20;
    }

    @Override
    public double heightAboveGround() {
        return this.telemetry.heightAboveGround;
    }

    @Override
    public boolean armed() {
        return this.telemetry.armed;
    }

    @Override
    public boolean grounded() {
        return this.telemetry.grounded;
    }

    @Override
    public boolean failsafe() {
        return this.telemetry.failsafeStage != 0;
    }

    @Override
    public double hoverThrottle() {
        return this.telemetry.hoverThrottleStick;
    }

    @Override
    public double batteryCharge() {
        return Math.max(0.0, this.telemetry.batteryCharge);
    }

    @Override
    public double stickRoll() {
        return 0.0;
    }

    @Override
    public double stickPitch() {
        return 0.0;
    }

    @Override
    public double stickYaw() {
        return 0.0;
    }

    @Override
    public double stickThrottle() {
        return NEUTRAL_THROTTLE;
    }

    @Override
    public StabilizationMode pilotMode() {
        return StabilizationMode.ANGLE;
    }

    @Override
    public boolean action(int number) {
        return false;
    }

    @Override
    public double obstacleDistance(SensorDirection direction) {
        return direction == null ? Double.NaN : this.obstacles[direction.ordinal()];
    }

    @Override
    public boolean addonSwitch(String id) {
        return this.sim != null && this.sim.addonSwitch(id);
    }

    @Override
    public double addonAxis(String id) {
        return this.sim == null ? 0.0 : this.sim.addonAxis(id);
    }

    @Override
    public double operatorAxis(int index) {
        return this.sim == null ? 0.0 : this.sim.operatorAxis(index);
    }

    @Override
    public double angleLimitDeg() {
        return this.sim == null ? DEFAULT_ANGLE_LIMIT_DEG : this.sim.config().pid.angleLimitDeg;
    }
}
