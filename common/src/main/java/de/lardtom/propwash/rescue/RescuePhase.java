package de.lardtom.propwash.rescue;

import java.util.Locale;

public enum RescuePhase {
    IDLE,
    CLIMB,
    ESCAPE,
    CRUISE,
    HOLD,
    LAND,
    LANDED;

    public String translationKey() {
        return "propwash.rescue.phase." + this.name().toLowerCase(Locale.ROOT);
    }

    public boolean flying() {
        return this != IDLE && this != LANDED;
    }
}
