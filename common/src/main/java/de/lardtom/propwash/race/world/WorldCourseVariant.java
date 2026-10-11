package de.lardtom.propwash.race.world;

import java.util.Locale;
import java.util.Optional;

public enum WorldCourseVariant {
    FIELD_SLALOM,
    CANYON_RUN,
    FOREST_WEAVE,
    DRONE_ARENA;

    public static final String NAME_PREFIX = "propwash.race.world.";

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public String nameKey() {
        return NAME_PREFIX + this.key();
    }

    public static Optional<WorldCourseVariant> byKey(String key) {
        for (WorldCourseVariant variant : values()) {
            if (variant.key().equals(key)) {
                return Optional.of(variant);
            }
        }
        return Optional.empty();
    }
}
