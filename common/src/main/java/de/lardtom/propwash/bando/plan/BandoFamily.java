package de.lardtom.propwash.bando.plan;

import java.util.Locale;
import java.util.Optional;

public enum BandoFamily {
    FIELD(Terrain.LAND, 7),
    DESERT(Terrain.LAND, 12),
    SNOW(Terrain.LAND, 7),
    JUNGLE(Terrain.LAND, 12),
    COAST(Terrain.SHORE, 0),
    MOUNTAIN(Terrain.LAND, 22),
    SWAMP(Terrain.WETLAND, 8);

    private final Terrain terrain;
    private final int maxSlope;

    BandoFamily(Terrain terrain, int maxSlope) {
        this.terrain = terrain;
        this.maxSlope = maxSlope;
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public Terrain terrain() {
        return this.terrain;
    }

    public int maxSlope() {
        return this.maxSlope;
    }

    public static Optional<BandoFamily> byKey(String key) {
        for (BandoFamily family : values()) {
            if (family.key().equals(key)) {
                return Optional.of(family);
            }
        }
        return Optional.empty();
    }

    public enum Terrain {
        LAND,
        SHORE,
        WETLAND
    }
}
