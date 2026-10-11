package de.lardtom.propwash.rescue;

import java.util.Locale;

public enum HomeMode {
    FIRST_ARM,
    EVERY_ARM,
    PILOT,
    MANUAL;

    public String translationKey() {
        return "propwash.rescue.home_mode." + this.name().toLowerCase(Locale.ROOT);
    }
}
