package de.lardtom.propwash.stats.advancement;

import net.minecraft.advancements.predicates.MinMaxBounds;

import java.util.Optional;

final class Bounds {
    private Bounds() {
    }

    static boolean within(Optional<MinMaxBounds.Doubles> bounds, double value) {
        return bounds.isEmpty() || Double.isFinite(value) && bounds.get().matches(value);
    }

    static boolean within(Optional<MinMaxBounds.Ints> bounds, int value) {
        return bounds.isEmpty() || bounds.get().matches(value);
    }
}
