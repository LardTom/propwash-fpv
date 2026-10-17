package de.lardtom.propwash.api.flight;

public interface ForceSink {
    double MAX_FORCE = 500.0;
    double MAX_TORQUE = 50.0;

    void addForce(double fx, double fy, double fz);

    void addForceAt(double fx, double fy, double fz, double frameX, double frameY, double frameZ);

    void addTorque(double tx, double ty, double tz);
}
