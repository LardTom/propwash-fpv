package de.lardtom.propwash.api.race;

import java.util.Locale;

public enum CourseMode {
    LAPS,
    POINT_TO_POINT;

    private static final CourseMode[] VALUES = values();

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public boolean pointToPoint() {
        return this == POINT_TO_POINT;
    }

    public static CourseMode byKey(String key) {
        for (CourseMode mode : VALUES) {
            if (mode.key().equals(key)) {
                return mode;
            }
        }
        return LAPS;
    }

    public static CourseMode byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : LAPS;
    }
}
