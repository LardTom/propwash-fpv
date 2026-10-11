package de.lardtom.propwash.drone.bind;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

public enum BindAccess {
    UNBOUND,
    PILOT,
    OTHER,
    CLEARED;

    private static final BindAccess[] VALUES = values();

    public static BindAccess of(@Nullable ReceiverBind bind, UUID player) {
        if (bind == null) {
            return UNBOUND;
        }
        if (bind.cleared()) {
            return CLEARED;
        }
        if (player.equals(bind.pilot())) {
            return PILOT;
        }
        return OTHER;
    }

    public static BindAccess byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : UNBOUND;
    }

    public boolean connects() {
        return this == UNBOUND || this == PILOT;
    }

    public boolean matches() {
        return this == PILOT;
    }

    public boolean resettable() {
        return this == PILOT || this == OTHER;
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
