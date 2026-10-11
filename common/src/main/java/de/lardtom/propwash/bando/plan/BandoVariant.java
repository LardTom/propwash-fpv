package de.lardtom.propwash.bando.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum BandoVariant {
    BARN(BandoFamily.FIELD, 3),
    WAREHOUSE(BandoFamily.FIELD, 3),
    FACTORY(BandoFamily.DESERT, 3),
    WATER_TOWER(BandoFamily.DESERT, 2),
    REFINERY(BandoFamily.DESERT, 2),
    COLLAPSED_HALL(BandoFamily.SNOW, 1),
    CONCRETE_FRAME(BandoFamily.JUNGLE, 1),
    PIER(BandoFamily.COAST, 0),
    PLATFORM(BandoFamily.COAST, 0),
    MINE_HEAD(BandoFamily.MOUNTAIN, 1),
    SUNKEN_WAREHOUSE(BandoFamily.SWAMP, 1);

    private final BandoFamily family;
    private final int weight;

    BandoVariant(BandoFamily family, int weight) {
        this.family = family;
        this.weight = weight;
    }

    public BandoFamily family() {
        return this.family;
    }

    public int weight() {
        return this.weight;
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static Optional<BandoVariant> byKey(String key) {
        for (BandoVariant variant : values()) {
            if (variant.key().equals(key)) {
                return Optional.of(variant);
            }
        }
        return Optional.empty();
    }

    public static List<BandoVariant> of(BandoFamily family) {
        List<BandoVariant> list = new ArrayList<>();
        for (BandoVariant variant : values()) {
            if (variant.family == family) {
                list.add(variant);
            }
        }
        return List.copyOf(list);
    }

    public static BandoVariant pick(BandoFamily family, int roll) {
        List<BandoVariant> options = of(family);
        int total = 0;
        for (BandoVariant variant : options) {
            total += Math.max(variant.weight, 0);
        }
        if (total <= 0) {
            return options.get(Math.floorMod(roll, options.size()));
        }
        int r = Math.floorMod(roll, total);
        for (BandoVariant variant : options) {
            r -= Math.max(variant.weight, 0);
            if (r < 0) {
                return variant;
            }
        }
        return options.getLast();
    }
}
