package de.lardtom.propwash.rx;

public final class RxMapping {
    public static final int MAX = 15;
    public static final int CENTRE = 8;
    public static final int NONE = -1;

    private RxMapping() {
    }

    public static float level(float value, float low, float high) {
        if (!Float.isFinite(value) || !Float.isFinite(low) || !Float.isFinite(high)) {
            return 0.0F;
        }
        if (low == high) {
            return value >= low ? MAX : 0.0F;
        }
        float t = Math.clamp((value - low) / (high - low), 0.0F, 1.0F);
        if (t <= 0.5F) {
            return 2.0F * CENTRE * t;
        }
        return CENTRE + 2.0F * (MAX - CENTRE) * (t - 0.5F);
    }

    public static int strength(float value, float low, float high) {
        return strength(value, low, high, NONE);
    }

    public static int strength(float value, float low, float high, int previous) {
        if (!Float.isFinite(value) || !Float.isFinite(low) || !Float.isFinite(high)) {
            return previous >= 0 && previous <= MAX ? previous : 0;
        }
        if (low == high) {
            return value >= low ? MAX : 0;
        }
        return Math.clamp(Math.round(level(value, low, high)), 0, MAX);
    }

    public static float percent(float value, float low, float high) {
        return level(value, low, high) / MAX;
    }
}
