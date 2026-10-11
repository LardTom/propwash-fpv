package de.lardtom.propwash.input;

import java.util.Locale;

public enum ThrottleMode {
    FULL_RANGE,
    UPPER_HALF,
    INCREMENTAL;

    public String translationKey() {
        return "propwash.input.throttle_mode." + this.name().toLowerCase(Locale.ROOT);
    }

    public String descriptionKey() {
        return this.translationKey() + ".desc";
    }
}
