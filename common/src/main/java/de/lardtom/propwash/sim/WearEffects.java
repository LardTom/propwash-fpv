package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.PartWear;

public final class WearEffects {
    public static final double BENT_PROP_THRUST_LOSS = 0.2;
    public static final double CRACKED_PROP_THRUST_LOSS = 0.1;
    public static final double WORN_PROP_LOAD_LOSS = 0.08;
    public static final double BROKEN_PROP_THRUST = 0.005;
    public static final double BROKEN_PROP_LOAD = 0.08;
    public static final double BENT_MOTOR_THRUST_LOSS = 0.06;
    public static final double CRACKED_ARM_THRUST_LOSS = 0.03;
    public static final double BROKEN_ARM_THRUST = 0.05;
    public static final double BENT_PROP_VIBRATION = 0.7;
    public static final double CRACKED_PROP_VIBRATION = 1.2;
    public static final double BROKEN_PROP_VIBRATION = 1.0;
    public static final double MOTOR_VIBRATION = 0.7;
    public static final double ARM_VIBRATION = 0.3;
    public static final double GYRO_DPS_PER_VIBRATION = 3.0;
    public static final double STACK_NOISE_BELOW = 0.3;
    public static final double STACK_NOISE_DPS = 3.0;
    public static final double BATTERY_RESISTANCE_GAIN = 2.0;
    public static final double MAX_VIBRATION = 4.0;

    private WearEffects() {
    }

    public static double propThrust(float condition, double brittleness) {
        if (PartWear.broken(condition)) {
            return BROKEN_PROP_THRUST;
        }
        double b = share(brittleness);
        double loss = (1.0 - b) * BENT_PROP_THRUST_LOSS + b * CRACKED_PROP_THRUST_LOSS;
        return 1.0 - loss * (1.0 - unit(condition));
    }

    public static double propVibration(float condition, double brittleness) {
        if (PartWear.broken(condition)) {
            return BROKEN_PROP_VIBRATION;
        }
        double b = share(brittleness);
        double lost = 1.0 - unit(condition);
        return (1.0 - b) * BENT_PROP_VIBRATION * Math.pow(lost, 1.5) + b * CRACKED_PROP_VIBRATION * lost;
    }

    public static double propLoad(float condition) {
        if (PartWear.broken(condition)) {
            return BROKEN_PROP_LOAD;
        }
        return 1.0 - WORN_PROP_LOAD_LOSS * (1.0 - unit(condition));
    }

    public static double motorThrust(float condition) {
        return 1.0 - BENT_MOTOR_THRUST_LOSS * (1.0 - unit(condition));
    }

    public static boolean motorDead(float condition) {
        return PartWear.broken(condition);
    }

    public static double armThrust(float condition) {
        if (PartWear.broken(condition)) {
            return BROKEN_ARM_THRUST;
        }
        return 1.0 - CRACKED_ARM_THRUST_LOSS * (1.0 - unit(condition));
    }

    public static double thrust(PartWear wear, int position, double brittleness) {
        return propThrust(wear.prop(position), brittleness) * motorThrust(wear.motor(position)) * armThrust(wear.arm(position));
    }

    public static double vibration(PartWear wear, int position, double brittleness) {
        double v = propVibration(wear.prop(position), brittleness);
        v += MOTOR_VIBRATION * (1.0 - unit(wear.motor(position)));
        v += ARM_VIBRATION * (1.0 - unit(wear.arm(position)));
        return v;
    }

    public static double vibration(PartWear wear, double brittleness) {
        double sum = 0.0;
        for (int p = 0; p < PartWear.POSITIONS; p++) {
            sum += vibration(wear, p, brittleness);
        }
        return Math.min(MAX_VIBRATION, sum);
    }

    public static double gyroNoiseDps(PartWear wear) {
        float stack = wear.stack();
        if (stack >= STACK_NOISE_BELOW) {
            return 0.0;
        }
        return STACK_NOISE_DPS * (STACK_NOISE_BELOW - unit(stack)) / STACK_NOISE_BELOW;
    }

    public static double batteryResistance(PartWear wear) {
        return 1.0 + BATTERY_RESISTANCE_GAIN * (1.0 - unit(wear.battery()));
    }

    public static boolean hardwareFault(PartWear wear) {
        return PartWear.broken(wear.stack()) || PartWear.broken(wear.battery());
    }

    private static double share(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }

    private static double unit(float condition) {
        if (!Float.isFinite(condition)) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, condition));
    }
}
