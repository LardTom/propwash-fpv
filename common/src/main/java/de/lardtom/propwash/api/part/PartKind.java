package de.lardtom.propwash.api.part;

import java.util.Locale;
import java.util.Optional;

public enum PartKind {
    FRAME("frame", 1, 1),
    STACK("stack", 1, 1),
    MOTOR("motor", 4, 4),
    PROP("prop", 4, 4),
    VIDEO("video", 1, 1),
    BATTERY("battery", 1, 1),
    ACCESSORY("accessory", 0, 2);

    private final String key;
    private final int requiredCount;
    private final int slotCount;

    PartKind(String key, int requiredCount, int slotCount) {
        this.key = key;
        this.requiredCount = requiredCount;
        this.slotCount = slotCount;
    }

    public String key() {
        return this.key;
    }

    public int requiredCount() {
        return this.requiredCount;
    }

    public int slotCount() {
        return this.slotCount;
    }

    public static Optional<PartKind> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String wanted = key.toLowerCase(Locale.ROOT);
        for (PartKind kind : values()) {
            if (kind.key.equals(wanted)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
