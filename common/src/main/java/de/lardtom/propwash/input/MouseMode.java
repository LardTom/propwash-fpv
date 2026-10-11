package de.lardtom.propwash.input;

import java.util.Locale;

public enum MouseMode {
    AUTO,
    RATE,
    HOLD;

    public boolean holds(boolean selfLevel) {
        return this == HOLD || this == AUTO && selfLevel;
    }

    public String translationKey() {
        return "propwash.input.mouse.mode." + this.name().toLowerCase(Locale.ROOT);
    }

    public String descriptionKey() {
        return this.translationKey() + ".desc";
    }
}
