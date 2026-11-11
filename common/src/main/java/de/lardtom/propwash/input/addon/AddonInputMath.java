package de.lardtom.propwash.input.addon;

public final class AddonInputMath {
    public static final double FULL_SCALE_DEGREES = 90.0;
    public static final double QUANTUM = 1.0 / Short.MAX_VALUE;

    private AddonInputMath() {
    }

    public static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(-1.0, Math.min(1.0, value));
    }

    public static double deadband(double raw, double deadband) {
        double value = clamp(raw);
        double band = Math.max(0.0, Math.min(AxisAssignment.MAX_DEADBAND, deadband));
        double magnitude = Math.abs(value);
        if (magnitude <= band) {
            return 0.0;
        }
        return Math.copySign((magnitude - band) / (1.0 - band), value);
    }

    public static double axisValue(AxisAssignment assignment, float raw) {
        double value = deadband(raw, assignment.deadband);
        return clamp(assignment.invert ? -value : value);
    }

    public static double unitsPerSecond(double keyRateDps) {
        return Math.max(0.0, keyRateDps) / FULL_SCALE_DEGREES;
    }

    public static double keyStep(double value, boolean increase, boolean decrease, double keyRateDps, double seconds) {
        double current = clamp(value);
        if (increase == decrease || !(seconds > 0.0)) {
            return current;
        }
        double step = unitsPerSecond(keyRateDps) * Math.min(seconds, 0.25);
        return clamp(current + (increase ? step : -step));
    }

    public static short quantize(double value) {
        return (short) Math.round(clamp(value) * Short.MAX_VALUE);
    }

    public static double dequantize(short value) {
        return clamp(value / (double) Short.MAX_VALUE);
    }
}
