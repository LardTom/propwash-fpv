package de.lardtom.propwash.zone;

import java.util.Locale;

public enum ZoneShape {
    BOX,
    CYLINDER;

    private final String key = this.name().toLowerCase(Locale.ROOT);

    public String key() {
        return this.key;
    }

    public String translationKey() {
        return "propwash.zone.shape." + this.key;
    }

    public static ZoneShape byKey(String key) {
        for (ZoneShape shape : values()) {
            if (shape.key.equals(key)) {
                return shape;
            }
        }
        return BOX;
    }
}
