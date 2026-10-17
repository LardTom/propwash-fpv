package de.lardtom.propwash.api.part;

import java.util.Locale;
import java.util.Optional;

public enum AccessorySlot {
    TOP("top"),
    BOTTOM("bottom"),
    FRONT("front");

    private final String key;

    AccessorySlot(String key) {
        this.key = key;
    }

    public String key() {
        return this.key;
    }

    public static Optional<AccessorySlot> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String wanted = key.toLowerCase(Locale.ROOT);
        for (AccessorySlot slot : values()) {
            if (slot.key.equals(wanted)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }
}
