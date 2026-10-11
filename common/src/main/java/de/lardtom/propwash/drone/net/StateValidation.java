package de.lardtom.propwash.drone.net;

public final class StateValidation {
    public static final double PHYSICS_MAX_SPEED = 150.0;
    public static final double SPEED_TOLERANCE = 1.1;
    public static final double BURST_SECONDS = 1.0;
    public static final double DISTANCE_SLACK = 0.5;
    public static final int MAX_PACKETS_PER_TICK = 4;
    public static final double ORIENTATION_TOLERANCE = 0.2;
    public static final int MOTORS = 4;

    private StateValidation() {
    }

    public static boolean finite(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    public static boolean finite(float x, float y, float z, float w) {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z) && Float.isFinite(w);
    }

    public static double effectiveMaxSpeed(double ruleMaxSpeed) {
        if (Double.isNaN(ruleMaxSpeed)) {
            return PHYSICS_MAX_SPEED;
        }
        if (ruleMaxSpeed <= 0.0) {
            return 0.0;
        }
        return Math.min(ruleMaxSpeed, PHYSICS_MAX_SPEED);
    }

    public static double refillRate(double maxSpeed) {
        double speed = Double.isFinite(maxSpeed) ? Math.max(0.0, maxSpeed) : PHYSICS_MAX_SPEED;
        return speed * SPEED_TOLERANCE;
    }

    public static double burstDistance(double maxSpeed) {
        return refillRate(maxSpeed) * BURST_SECONDS + DISTANCE_SLACK;
    }

    public static boolean orientationUsable(float qx, float qy, float qz, float qw) {
        if (!finite(qx, qy, qz, qw)) {
            return false;
        }
        double lengthSq = (double) qx * qx + (double) qy * qy + (double) qz * qz + (double) qw * qw;
        return Math.abs(lengthSq - 1.0) <= ORIENTATION_TOLERANCE;
    }

    public static boolean speedPlausible(float vx, float vy, float vz, double maxSpeed) {
        if (!Float.isFinite(vx) || !Float.isFinite(vy) || !Float.isFinite(vz)) {
            return false;
        }
        double speed = Double.isFinite(maxSpeed) ? Math.max(0.0, maxSpeed) : PHYSICS_MAX_SPEED;
        double limit = speed * 1.25 + 1.0;
        return (double) vx * vx + (double) vy * vy + (double) vz * vz <= limit * limit;
    }

    public static float acceptBattery(float reported, float previous) {
        return Math.min(unit(reported), unit(previous));
    }

    public static float unit(float value) {
        if (Float.isNaN(value)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public static int packMotors(float m0, float m1, float m2, float m3) {
        return toByte(m0) | toByte(m1) << 8 | toByte(m2) << 16 | toByte(m3) << 24;
    }

    public static float unpackMotor(int packed, int motor) {
        if (motor < 0 || motor >= MOTORS) {
            return 0.0F;
        }
        return ((packed >>> (motor * 8)) & 0xFF) / 255.0F;
    }

    public static float averageMotor(int packed) {
        float sum = 0.0F;
        for (int i = 0; i < MOTORS; i++) {
            sum += unpackMotor(packed, i);
        }
        return sum / MOTORS;
    }

    private static int toByte(float value) {
        return Math.round(unit(value) * 255.0F) & 0xFF;
    }
}
