package de.lardtom.propwash.drone;

public final class LosAim {
    public static final double TAU_SECONDS = 0.025;
    public static final double HOLD_DISTANCE = 0.75;
    public static final float MAX_PITCH = 89.0F;

    public record Angles(float yaw, float pitch) {
    }

    private LosAim() {
    }

    public static float yawTo(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    public static float pitchTo(double dx, double dy, double dz) {
        return clampPitch((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    public static float clampPitch(float pitch) {
        if (Float.isNaN(pitch)) {
            return 0.0F;
        }
        return Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch));
    }

    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    public static float approach(float current, float target, double dtSeconds, double tau) {
        if (!Double.isFinite(dtSeconds) || dtSeconds <= 0.0) {
            return current;
        }
        double alpha = tau <= 0.0 ? 1.0 : 1.0 - Math.exp(-dtSeconds / tau);
        return (float) (current + wrapDegrees(target - current) * alpha);
    }

    public static Angles aim(float currentYaw, float currentPitch, double dx, double dy, double dz, double dtSeconds) {
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(distance >= HOLD_DISTANCE)) {
            return new Angles(currentYaw, currentPitch);
        }
        float yaw = approach(currentYaw, yawTo(dx, dz), dtSeconds, TAU_SECONDS);
        float pitch = clampPitch(approach(currentPitch, pitchTo(dx, dy, dz), dtSeconds, TAU_SECONDS));
        return new Angles(yaw, pitch);
    }
}
