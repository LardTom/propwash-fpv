package de.lardtom.propwash.api.race;

import java.util.Locale;

public enum TimeKind {
    LAP,
    RACE;

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
