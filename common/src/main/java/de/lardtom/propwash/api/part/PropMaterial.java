package de.lardtom.propwash.api.part;

import java.util.Locale;
import java.util.Optional;

public enum PropMaterial {
    POLYCARBONATE("polycarbonate", 1.0, 1.0),
    GLASS_FIBER_NYLON("glass_fiber_nylon", 1.2, 0.8),
    CARBON_FIBER_NYLON("carbon_fiber_nylon", 1.4, 0.9),
    CARBON("carbon", 2.2, 1.4);

    private final String key;
    private final double defaultStiffness;
    private final double defaultDurability;

    PropMaterial(String key, double defaultStiffness, double defaultDurability) {
        this.key = key;
        this.defaultStiffness = defaultStiffness;
        this.defaultDurability = defaultDurability;
    }

    public String key() {
        return this.key;
    }

    public double defaultStiffness() {
        return this.defaultStiffness;
    }

    public double defaultDurability() {
        return this.defaultDurability;
    }

    public static Optional<PropMaterial> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String wanted = key.toLowerCase(Locale.ROOT);
        for (PropMaterial material : values()) {
            if (material.key.equals(wanted)) {
                return Optional.of(material);
            }
        }
        return Optional.empty();
    }
}
