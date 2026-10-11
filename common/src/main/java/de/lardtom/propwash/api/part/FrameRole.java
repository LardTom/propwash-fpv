package de.lardtom.propwash.api.part;

import java.util.Locale;
import java.util.Optional;

public enum FrameRole {
    WHOOP("whoop", true),
    TOOTHPICK("toothpick", true),
    CINEWHOOP("cinewhoop", true),
    FREESTYLE("freestyle", true),
    RACE("race", false),
    LONG_RANGE("long_range", false),
    CINELIFTER("cinelifter", false),
    X_CLASS("x_class", false);

    private final String key;
    private final boolean tricksByDefault;

    FrameRole(String key, boolean tricksByDefault) {
        this.key = key;
        this.tricksByDefault = tricksByDefault;
    }

    public String key() {
        return this.key;
    }

    public boolean tricksByDefault() {
        return this.tricksByDefault;
    }

    public static Optional<FrameRole> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String wanted = key.toLowerCase(Locale.ROOT);
        for (FrameRole role : values()) {
            if (role.key.equals(wanted)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
