package de.lardtom.propwash.api.race;

import java.util.Locale;

public enum ChangeKind {
    LAYOUT,
    NAME,
    LAPS,
    OWNER;

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
