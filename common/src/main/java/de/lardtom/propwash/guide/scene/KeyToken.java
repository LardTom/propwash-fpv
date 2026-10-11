package de.lardtom.propwash.guide.scene;

import java.util.Objects;

public record KeyToken(Kind kind, String value) {
    public enum Kind {
        MAPPING,
        MOUSE
    }

    public static final String MOUSE_LEFT = "left";
    public static final String MOUSE_RIGHT = "right";
    public static final String MOUSE_MIDDLE = "middle";

    public KeyToken {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(value, "value");
    }

    public static KeyToken mapping(String keyMappingName) {
        return new KeyToken(Kind.MAPPING, keyMappingName);
    }

    public static KeyToken mouse(String button) {
        return new KeyToken(Kind.MOUSE, button);
    }

    public static KeyToken sneak() {
        return mapping("key.sneak");
    }

    public static KeyToken use() {
        return mapping("key.use");
    }

    public static KeyToken attack() {
        return mapping("key.attack");
    }
}
