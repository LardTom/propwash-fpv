package de.lardtom.propwash.input;

import java.util.Locale;

public enum StickChannel {
    ROLL,
    PITCH,
    YAW,
    THROTTLE;

    public String translationKey() {
        return "propwash.input.channel." + this.name().toLowerCase(Locale.ROOT);
    }

    public String shortKey() {
        return "propwash.input.channel_short." + this.name().toLowerCase(Locale.ROOT);
    }

    public String promptKey() {
        return "propwash.input.wizard.move." + this.name().toLowerCase(Locale.ROOT);
    }

    public boolean centered() {
        return this != THROTTLE;
    }
}
