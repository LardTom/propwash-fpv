package de.lardtom.propwash.drone.trail;

public final class TrailModel {
    public static final int SAMPLES_PER_TRAIL = 100;
    public static final int MAX_TRAILS = 64;
    public static final double SAMPLE_INTERVAL = 1.0 / 40.0;
    public static final double MIN_SPACING = 0.03;
    public static final double MAX_SPEED = 100.0;
    public static final double MIN_JUMP = 4.0;
    public static final double SPEED_TAU = 0.25;
    public static final float MIN_LIFE = 1.0F;
    public static final float MAX_LIFE = 2.0F;
    public static final double SLOW_SPEED = 4.0;
    public static final double FAST_SPEED = 24.0;
    public static final double LANDED_SPEED = 1.0;
    public static final double MAX_REWIND = 1.0;
    public static final float CORE_HALF_WIDTH = 0.03F;
    public static final float GLOW_HALF_WIDTH = 0.08F;
    public static final double CORE_MIN_ANGLE = 0.0014;
    public static final double GLOW_MIN_ANGLE = 0.0036;
    public static final float TAIL_WIDTH = 0.3F;
    public static final float CORE_ALPHA = 0.9F;
    public static final float GLOW_ALPHA = 0.45F;
    public static final double NEAR_CAMERA = 0.5;
    public static final double NEAR_FADE = 1.5;
    public static final double FAR_FADE = 24.0;
    public static final double CORE_PULL = 0.004;
    public static final double MAX_CORE_PULL = 0.2;

    private TrailModel() {
    }

    public static float life(double speed) {
        if (!Double.isFinite(speed)) {
            return MIN_LIFE;
        }
        double t = clamp01((speed - SLOW_SPEED) / (FAST_SPEED - SLOW_SPEED));
        return (float) (MIN_LIFE + (MAX_LIFE - MIN_LIFE) * t);
    }

    public static double smoothSpeed(double previous, double measured, double dt) {
        if (!Double.isFinite(measured) || measured < 0.0) {
            return previous;
        }
        if (!Double.isFinite(previous) || dt <= 0.0) {
            return measured;
        }
        double k = 1.0 - Math.exp(-dt / SPEED_TAU);
        return previous + (measured - previous) * k;
    }

    public static boolean isJump(double distance, double dt) {
        return distance > MIN_JUMP && distance > MAX_SPEED * Math.max(0.0, dt);
    }

    public static boolean emits(boolean armed, boolean landed) {
        return armed && !landed;
    }

    public static boolean landed(boolean groundBelow, double speed) {
        return groundBelow && speed < LANDED_SPEED;
    }

    public static float freshness(double age, float life) {
        if (!(life > 0.0F) || !Double.isFinite(age)) {
            return 0.0F;
        }
        return (float) clamp01(1.0 - age / life);
    }

    public static float alpha(float freshness) {
        float rest = 1.0F - (float) clamp01(freshness);
        return 1.0F - rest * rest;
    }

    public static float widthScale(float freshness) {
        return TAIL_WIDTH + (1.0F - TAIL_WIDTH) * (float) clamp01(freshness);
    }

    public static float halfWidth(float base, double minAngle, double distance, float freshness) {
        return (float) Math.max(base * widthScale(freshness), Math.max(0.0, distance) * minAngle);
    }

    public static float nearFade(double distance) {
        return (float) smooth((distance - NEAR_CAMERA) / NEAR_FADE);
    }

    public static float farFade(double distance, double range) {
        if (!(range > 0.0)) {
            return 0.0F;
        }
        double fade = Math.min(FAR_FADE, range * 0.5);
        return (float) (1.0 - smooth((distance - (range - fade)) / fade));
    }

    public static double corePull(double distance) {
        return Math.min(MAX_CORE_PULL, Math.max(0.0, distance) * CORE_PULL);
    }

    static double smooth(double t) {
        double c = clamp01(t);
        return c * c * (3.0 - 2.0 * c);
    }

    static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
