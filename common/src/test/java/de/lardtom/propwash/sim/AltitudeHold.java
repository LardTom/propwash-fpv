package de.lardtom.propwash.sim;

final class AltitudeHold {
    double target;
    double kp = 0.08;
    double kd = 0.06;
    double ki = 0.05;
    double feedForwardScale = 1.0;
    double integralLimit = 0.3;
    private double integral;

    AltitudeHold(double target) {
        this.target = target;
    }

    double throttle(DroneSim sim, double dt) {
        RigidBody b = sim.body;
        double err = this.target - b.py;
        double base = sim.telemetry().hoverThrottleStick * this.feedForwardScale;
        double raw = base + this.kp * err - this.kd * b.vy + this.integral;
        if (raw > 0.0 && raw < 1.0) {
            this.integral += this.ki * err * dt;
        }
        this.integral = SimMath.clamp(this.integral, -this.integralLimit, this.integralLimit);
        return SimMath.clamp01(raw);
    }

    void apply(FlatWorld world, double dt) {
        world.input.throttle = this.throttle(world.sim, dt);
    }
}
