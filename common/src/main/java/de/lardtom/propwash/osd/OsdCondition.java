package de.lardtom.propwash.osd;

import de.lardtom.propwash.build.PartWear;

import java.util.Locale;

public final class OsdCondition {
    public static final long INTACT = -1L;

    private OsdCondition() {
    }

    public static long key(PartWear wear) {
        if (wear == null || wear.isNew()) {
            return INTACT;
        }
        Part worst = Part.PROPS;
        float min = Float.MAX_VALUE;
        for (Part part : Part.values()) {
            float value = part.condition(wear);
            if (value < min) {
                min = value;
                worst = part;
            }
        }
        return worst.ordinal() * 1000L + percent(min);
    }

    public static Part part(long key) {
        return Part.values()[(int) Math.floorDiv(key, 1000L) % Part.values().length];
    }

    public static int percent(long key) {
        return (int) Math.floorMod(key, 1000L);
    }

    public static int percent(float condition) {
        if (!Float.isFinite(condition)) {
            return 100;
        }
        return Math.round(Math.max(0.0F, Math.min(1.0F, condition)) * 100.0F);
    }

    public enum Part {
        PROPS,
        MOTORS,
        FRAME,
        CAMERA,
        FC,
        BATTERY;

        public float condition(PartWear wear) {
            return switch (this) {
                case PROPS -> wear.worstProp();
                case MOTORS -> wear.worstMotor();
                case FRAME -> wear.frame();
                case CAMERA -> wear.video();
                case FC -> wear.stack();
                case BATTERY -> wear.battery();
            };
        }

        public String translationKey() {
            return "propwash.osd.stats.part." + this.name().toLowerCase(Locale.ROOT);
        }
    }
}
