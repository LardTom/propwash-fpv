package de.lardtom.propwash.api.part;

import net.minecraft.resources.Identifier;

import java.util.Objects;

public record RecycleYield(Identifier item, int count) {
    public static final RecycleYield NONE = new RecycleYield(Identifier.withDefaultNamespace("air"), 0);

    public RecycleYield {
        Objects.requireNonNull(item, "item");
        if (count < 0 || count > 64) {
            throw new IllegalArgumentException("Recycling-Menge muss zwischen 0 und 64 liegen: " + count);
        }
    }

    public boolean none() {
        return this.count == 0;
    }
}
