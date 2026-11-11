package de.lardtom.propwash.share;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public enum ShareLayer {
    PARTS(0),
    PAINT(1),
    TUNE(2),
    OSD(3),
    NAME(4),
    CREATIVE(5);

    public static final Set<ShareLayer> DEFAULT_EXPORT = Collections.unmodifiableSet(EnumSet.of(PARTS, PAINT, TUNE, NAME));

    private final int bit;

    ShareLayer(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return this.bit;
    }

    public int mask() {
        return 1 << this.bit;
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "propwash.share.layer." + this.key();
    }

    public static Optional<ShareLayer> byBit(int bit) {
        for (ShareLayer layer : values()) {
            if (layer.bit == bit) {
                return Optional.of(layer);
            }
        }
        return Optional.empty();
    }

    public static Optional<ShareLayer> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String wanted = key.trim().toLowerCase(Locale.ROOT);
        for (ShareLayer layer : values()) {
            if (layer.key().equals(wanted)) {
                return Optional.of(layer);
            }
        }
        return Optional.empty();
    }
}
