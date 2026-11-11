package de.lardtom.propwash.share;

import java.util.Locale;

public enum ShareStatus {
    OK,
    EMPTY,
    INVALID_PREFIX,
    BAD_CHARACTER,
    TOO_LONG,
    TRUNCATED,
    CHECKSUM,
    UNSUPPORTED_VERSION,
    TOO_LARGE,
    CORRUPT,
    OTHER_CODE,
    NOT_CONVERTIBLE,
    UNKNOWN_ENTRIES;

    public boolean ok() {
        return this == OK;
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "propwash.share.status." + this.key();
    }
}
