package de.lardtom.propwash.api.part;

import java.util.Locale;
import java.util.Optional;

public enum FrameShape {
    TRUE_X("true_x"),
    STRETCHED_X("stretched_x"),
    SQUASHED_X("squashed_x"),
    DEADCAT("deadcat"),
    H_FRAME("h_frame"),
    CUSTOM("custom");

    private final String key;

    FrameShape(String key) {
        this.key = key;
    }

    public String key() {
        return this.key;
    }

    public static Optional<FrameShape> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String wanted = key.toLowerCase(Locale.ROOT);
        for (FrameShape shape : values()) {
            if (shape.key.equals(wanted)) {
                return Optional.of(shape);
            }
        }
        return Optional.empty();
    }
}
