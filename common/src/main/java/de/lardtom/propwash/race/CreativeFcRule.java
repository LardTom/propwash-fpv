package de.lardtom.propwash.race;

import org.jspecify.annotations.Nullable;

public enum CreativeFcRule {
    WORLD("world"),
    ASSIST("assist"),
    LEGAL("legal");

    public static final String KEY = "propwash.race_rules.creative_fc.";
    private static final CreativeFcRule[] VALUES = values();

    private final String key;

    CreativeFcRule(String key) {
        this.key = key;
    }

    public String key() {
        return this.key;
    }

    public String translationKey() {
        return KEY + this.key;
    }

    public @Nullable String stored() {
        return this == WORLD ? null : this.key;
    }

    public boolean legal(boolean world) {
        return this == LEGAL || this == WORLD && world;
    }

    public static CreativeFcRule byKey(@Nullable String key) {
        for (CreativeFcRule rule : VALUES) {
            if (rule.key.equals(key)) {
                return rule;
            }
        }
        return WORLD;
    }

    public static CreativeFcRule byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : WORLD;
    }
}
