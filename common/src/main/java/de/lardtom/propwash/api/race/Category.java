package de.lardtom.propwash.api.race;

import java.util.Locale;

public enum Category {
    CLEAN,
    ASSISTED;

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
