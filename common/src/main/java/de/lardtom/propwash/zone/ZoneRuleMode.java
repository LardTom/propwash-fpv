package de.lardtom.propwash.zone;

import java.util.Locale;

public enum ZoneRuleMode {
    BELOW,
    WORLD,
    OWN;

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
