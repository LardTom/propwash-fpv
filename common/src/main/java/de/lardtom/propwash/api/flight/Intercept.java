package de.lardtom.propwash.api.flight;

import java.util.Optional;

public final class Intercept {
    public static final int ITERATIONS = 8;

    private Intercept() {
    }

    public static Optional<Aim> aim(double shooterX, double shooterY, double shooterZ, double targetX, double targetY, double targetZ,
                                    double targetVx, double targetVy, double targetVz, double projectileSpeed, double gravity, double maxTime) {
        double[] values = {shooterX, shooterY, shooterZ, targetX, targetY, targetZ, targetVx, targetVy, targetVz, projectileSpeed, gravity, maxTime};
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return Optional.empty();
            }
        }
        if (!(projectileSpeed > 0.0) || gravity < 0.0 || !(maxTime > 0.0)) {
            return Optional.empty();
        }
        double dx = targetX - shooterX;
        double dy = targetY - shooterY;
        double dz = targetZ - shooterZ;
        double t = Math.sqrt(dx * dx + dy * dy + dz * dz) / projectileSpeed;
        double previous = -1.0;
        for (int i = 0; i < ITERATIONS; i++) {
            double px = dx + targetVx * t;
            double py = dy + targetVy * t + 0.5 * gravity * t * t;
            double pz = dz + targetVz * t;
            previous = t;
            t = Math.sqrt(px * px + py * py + pz * pz) / projectileSpeed;
            if (!Double.isFinite(t) || t > maxTime) {
                return Optional.empty();
            }
        }
        if (Math.abs(t - previous) > 0.05 * Math.max(t, 1.0E-3)) {
            return Optional.empty();
        }
        double px = dx + targetVx * t;
        double py = dy + targetVy * t + 0.5 * gravity * t * t;
        double pz = dz + targetVz * t;
        double length = Math.sqrt(px * px + py * py + pz * pz);
        if (!(length > 1.0E-9)) {
            return Optional.empty();
        }
        return Optional.of(new Aim(px / length, py / length, pz / length, t));
    }

    public record Aim(double dx, double dy, double dz, double time) {
    }
}
