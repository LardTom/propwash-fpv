package de.lardtom.propwash.api.build;

import java.util.Locale;

public enum ImportStatus {
    APPLIED,
    UNCHANGED,
    UNKNOWN_PARTS,
    MISSING_PARTS,
    MISSING_DYES,
    NO_TARGET,
    NOT_ALLOWED,
    INVALID,
    BUSY;

    public boolean success() {
        return this == APPLIED || this == UNCHANGED;
    }

    public String translationKey() {
        return "propwash.api.import." + this.name().toLowerCase(Locale.ROOT);
    }
}
