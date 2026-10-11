package de.lardtom.propwash.rescue;

import java.util.Locale;

public enum FailsafeAction {
    RETURN_HOME,
    DROP;

    public String translationKey() {
        return "propwash.rescue.failsafe." + this.name().toLowerCase(Locale.ROOT);
    }
}
