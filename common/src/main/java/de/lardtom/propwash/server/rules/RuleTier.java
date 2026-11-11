package de.lardtom.propwash.server.rules;

import java.util.Locale;
import java.util.Optional;

public enum RuleTier {
    FIXED,
    ZONES,
    PLAYERS;

    public static final RuleTier DEFAULT = ZONES;

    private final String key = this.name().toLowerCase(Locale.ROOT);

    public String key() {
        return this.key;
    }

    public String translationKey() {
        return "propwash.rules.tier." + this.key;
    }

    public boolean zones() {
        return this != FIXED;
    }

    public boolean players() {
        return this == PLAYERS;
    }

    public static Optional<RuleTier> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String wanted = key.trim().toLowerCase(Locale.ROOT);
        for (RuleTier tier : values()) {
            if (tier.key.equals(wanted)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }
}
