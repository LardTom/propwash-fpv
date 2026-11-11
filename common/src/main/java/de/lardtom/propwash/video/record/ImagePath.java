package de.lardtom.propwash.video.record;

import java.util.Locale;

public enum ImagePath {
    GPU,
    READ,
    CPU,
    NONE;

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public boolean replacement() {
        return this == READ || this == CPU;
    }
}
