package de.lardtom.propwash.input;

public final class ChannelMath {
    public static final int MIN_US = 1000;
    public static final int MID_US = 1500;
    public static final int MAX_US = 2000;
    public static final int RANGE_MIN_US = 900;
    public static final int RANGE_MAX_US = 2100;
    public static final int RANGE_STEP_US = 25;
    public static final float RANGE_LIMIT = (RANGE_MAX_US - MID_US) / 500.0F;

    private ChannelMath() {
    }

    public static int centeredMicros(float value) {
        if (Float.isNaN(value)) {
            return MID_US;
        }
        return Math.round(MID_US + 500.0F * ChannelMapping.clamp(value, -1.0F, 1.0F));
    }

    public static int throttleMicros(float throttle) {
        if (Float.isNaN(throttle)) {
            return MIN_US;
        }
        return Math.round(MIN_US + 1000.0F * ChannelMapping.clamp(throttle, 0.0F, 1.0F));
    }

    public static float fromMicros(int micros) {
        return ChannelMapping.clamp((micros - MID_US) / 500.0F, -1.0F, 1.0F);
    }

    public static float fraction(int micros) {
        return ChannelMapping.clamp((micros - MIN_US) / 1000.0F, 0.0F, 1.0F);
    }

    public static int rangeMicros(float value) {
        if (Float.isNaN(value)) {
            return MID_US;
        }
        return clampRange(Math.round(MID_US + 500.0F * ChannelMapping.clamp(value, -RANGE_LIMIT, RANGE_LIMIT)));
    }

    public static int snapRangeMicros(int micros) {
        return clampRange(Math.round(micros / (float) RANGE_STEP_US) * RANGE_STEP_US);
    }

    public static float fromRangeMicros(int micros) {
        return (clampRange(micros) - MID_US) / 500.0F;
    }

    public static float snapRange(float value) {
        return fromRangeMicros(snapRangeMicros(rangeMicros(value)));
    }

    private static int clampRange(int micros) {
        return Math.max(RANGE_MIN_US, Math.min(RANGE_MAX_US, micros));
    }
}
