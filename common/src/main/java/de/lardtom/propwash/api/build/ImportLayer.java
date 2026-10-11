package de.lardtom.propwash.api.build;

import java.util.Locale;

public enum ImportLayer {
    PARTS,
    PAINT,
    TUNE,
    NAME;

    public String translationKey() {
        return "propwash.api.import.layer." + this.name().toLowerCase(Locale.ROOT);
    }
}
