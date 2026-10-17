package de.lardtom.propwash.api.flight;

public interface AssistOutput {
    double MAX_SPEED = 20.0;
    double MAX_CLIMB = 8.0;
    int MAX_LABEL = 8;

    void sticks(double roll, double pitch, double yaw, double throttle);

    void mode(StabilizationMode mode);

    void holdAltitude(double y);

    void climb(double metresPerSecond);

    void holdPosition(double x, double z);

    void velocity(double vx, double vz);

    void maxSpeed(double metresPerSecond);

    void yawRate(double degreesPerSecond);

    void heading(double compassDegrees);

    void label(String text);

    void requestReturnHome();
}
