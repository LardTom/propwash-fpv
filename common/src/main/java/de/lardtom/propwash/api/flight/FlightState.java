package de.lardtom.propwash.api.flight;

public interface FlightState {
    int ACTIONS = 4;
    double COMPASS_DISTURBED_DEG = 15.0;
    double DEFAULT_ANGLE_LIMIT_DEG = 60.0;

    double time();

    double massKg();

    double gravity();

    double x();

    double y();

    double z();

    double vx();

    double vy();

    double vz();

    double estimatedX();

    double estimatedY();

    double estimatedZ();

    double estimatedVx();

    double estimatedVy();

    double estimatedVz();

    boolean positionValid();

    boolean gpsFix();

    int satellites();

    double headingDeg();

    double pitchDeg();

    double rollDeg();

    double rollRateDps();

    double pitchRateDps();

    double yawRateDps();

    double forwardX();

    double forwardY();

    double forwardZ();

    double upX();

    double upY();

    double upZ();

    double rightX();

    double rightY();

    double rightZ();

    double heightAboveGround();

    boolean armed();

    boolean grounded();

    boolean failsafe();

    double hoverThrottle();

    double batteryCharge();

    double stickRoll();

    double stickPitch();

    double stickYaw();

    double stickThrottle();

    StabilizationMode pilotMode();

    boolean action(int number);

    double obstacleDistance(SensorDirection direction);

    default boolean addonSwitch(String id) {
        return false;
    }

    default double addonAxis(String id) {
        return 0.0;
    }

    default double operatorAxis(int index) {
        return 0.0;
    }

    default boolean terrainLimit() {
        return false;
    }

    default double angleLimitDeg() {
        return DEFAULT_ANGLE_LIMIT_DEG;
    }

    default double compassHeadingDeg() {
        return this.headingDeg();
    }

    default double compassErrorDeg() {
        double d = (this.compassHeadingDeg() - this.headingDeg()) % 360.0;
        if (d > 180.0) {
            return d - 360.0;
        }
        return d < -180.0 ? d + 360.0 : d;
    }

    default boolean compassHealthy() {
        return Math.abs(this.compassErrorDeg()) < COMPASS_DISTURBED_DEG;
    }
}
