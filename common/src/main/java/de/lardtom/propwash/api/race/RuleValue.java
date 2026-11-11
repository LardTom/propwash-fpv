package de.lardtom.propwash.api.race;

import java.util.Objects;

public record RuleValue(String rule, int value, int zone, boolean raceLegalZone) {
    public static final int WORLD = 0;

    public RuleValue {
        Objects.requireNonNull(rule, "rule");
        zone = Math.max(WORLD, zone);
        raceLegalZone = raceLegalZone && zone != WORLD;
    }

    public boolean world() {
        return this.zone == WORLD;
    }

    public boolean counts() {
        return !this.raceLegalZone;
    }
}
