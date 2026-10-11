package de.lardtom.propwash.rescue;

public enum Ground {
    NONE,
    SOLID,
    FOLIAGE,
    WATER,
    LAVA,
    HAZARD,
    UNKNOWN;

    public boolean landable() {
        return this == SOLID;
    }

    public boolean lethal() {
        return this == LAVA || this == HAZARD;
    }
}
