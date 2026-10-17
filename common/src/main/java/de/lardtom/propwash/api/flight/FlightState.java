package de.lardtom.propwash.api.flight;

public interface FlightState {
    int ACTIONS = 4;

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
}
