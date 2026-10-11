package de.lardtom.propwash.input;

import java.util.Locale;

public enum RadioSource {
    NONE,
    JOYSTICK,
    GAMEPAD,
    KEYBOARD;

    public String translationKey() {
        return "propwash.input.source." + this.name().toLowerCase(Locale.ROOT);
    }
}
