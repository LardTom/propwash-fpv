package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.build.PartWear;

import java.util.Locale;

public enum PropDamage {
    NONE,
    WORN,
    DAMAGED,
    BROKEN;

    public static final float VISIBLE_BELOW = PartWear.DAMAGED_BELOW;
    public static final float HEAVY_BELOW = PartWear.CRITICAL_BELOW;

    public static PropDamage of(float condition) {
        if (!Float.isFinite(condition) || condition >= VISIBLE_BELOW) {
            return NONE;
        }
        if (PartWear.broken(condition)) {
            return BROKEN;
        }
        return condition < HEAVY_BELOW ? DAMAGED : WORN;
    }

    public float representative() {
        return switch (this) {
            case NONE -> PartWear.NEW;
            case WORN -> (VISIBLE_BELOW + HEAVY_BELOW) * 0.5F;
            case DAMAGED -> HEAVY_BELOW * 0.5F;
            case BROKEN -> 0.0F;
        };
    }

    public String suffix() {
        return this == NONE ? "" : "_" + this.name().toLowerCase(Locale.ROOT);
    }
}
