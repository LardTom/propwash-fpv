package de.lardtom.propwash.build;

import de.lardtom.propwash.api.part.AccessorySlot;

import java.util.Locale;
import java.util.Optional;

public enum AccessoryMount {
    TOP("top", true),
    BOTTOM("bottom", false),
    FRONT("front", false);

    private final String key;
    private final boolean hasBase;

    AccessoryMount(String key, boolean hasBase) {
        this.key = key;
        this.hasBase = hasBase;
    }

    public String key() {
        return this.key;
    }

    public boolean hasBase() {
        return this.hasBase;
    }

    public AccessoryAnchor anchor(FrameType frame) {
        return AccessoryAnchor.of(frame, this);
    }

    public AccessorySlot slot() {
        return switch (this) {
            case TOP -> AccessorySlot.TOP;
            case BOTTOM -> AccessorySlot.BOTTOM;
            case FRONT -> AccessorySlot.FRONT;
        };
    }

    public static AccessoryMount of(AccessorySlot slot) {
        return switch (slot) {
            case TOP -> TOP;
            case BOTTOM -> BOTTOM;
            case FRONT -> FRONT;
        };
    }

    public static Optional<AccessoryMount> byKey(String key) {
        String wanted = key.toLowerCase(Locale.ROOT);
        for (AccessoryMount mount : values()) {
            if (mount.key.equals(wanted)) {
                return Optional.of(mount);
            }
        }
        return Optional.empty();
    }
}
