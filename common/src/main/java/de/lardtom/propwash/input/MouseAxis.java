package de.lardtom.propwash.input;

import java.util.Locale;

public enum MouseAxis {
    ROLL,
    YAW;

    public String translationKey() {
        return "propwash.input.mouse.x_axis." + this.name().toLowerCase(Locale.ROOT);
    }
}
