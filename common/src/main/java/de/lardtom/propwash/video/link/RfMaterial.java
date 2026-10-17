package de.lardtom.propwash.video.link;

public enum RfMaterial {
    AIR(0.0, 0.0, 0.0),
    FOLIAGE(0.0, 0.0, 1.0),
    PLANT(0.0, 0.0, Wall.PLANT_DENSITY),
    TRUNK(Wall.perMeter(Wall.LOG_BLOCK_DB, Wall.WOOD), Wall.WOOD.interfaceLossDb(), 0.0, Wall.MAX_WALL_RUN_M),
    TIMBER(Wall.perMeter(Wall.PLANK_BLOCK_DB, Wall.WOOD), Wall.WOOD.interfaceLossDb(), 0.0, Wall.MAX_WALL_RUN_M),
    MASONRY(Wall.perMeter(Wall.MASONRY_BLOCK_DB, Wall.BRICK), Wall.BRICK.interfaceLossDb(), 0.0, Wall.MAX_WALL_RUN_M),
    CONCRETE(Wall.perMeter(Wall.CONCRETE_BLOCK_DB, Wall.CONCRETE), Wall.CONCRETE.interfaceLossDb(), 0.0, Wall.MAX_WALL_RUN_M),
    EARTH(Wall.perMeter(Wall.GROUND_BLOCK_DB, Wall.MEDIUM_DRY_GROUND), Wall.MEDIUM_DRY_GROUND.interfaceLossDb(), 0.0),
    SAND(Wall.perMeter(Wall.SAND_BLOCK_DB, Wall.VERY_DRY_GROUND), Wall.VERY_DRY_GROUND.interfaceLossDb(), 0.0),
    GLASS(0.0, Wall.GLASS.thinSlabLossDb(Wall.WINDOW_PANE_M, RadioBand.FREQUENCY_GHZ), 0.0),
    METAL(Wall.METAL.attenuationDbPerMeter(RadioBand.FREQUENCY_GHZ), 0.0, 0.0),
    METAL_MESH(0.0, Wall.METAL_MESH_DB, 0.0),
    WATER(Wall.WATER.attenuationDbPerMeter(RadioBand.FREQUENCY_GHZ), Wall.WATER.interfaceLossDb(), 0.0),
    ICE(Wall.ICE.attenuationDbPerMeter(RadioBand.FREQUENCY_GHZ), Wall.ICE.interfaceLossDb(), 0.0),
    SNOW(Wall.SNOW.attenuationDbPerMeter(RadioBand.FREQUENCY_GHZ), Wall.SNOW.interfaceLossDb(), 0.0),
    CLOTH(Wall.perMeter(Wall.CLOTH_BLOCK_DB, Wall.FIBRE), Wall.FIBRE.interfaceLossDb(), 0.0, Wall.MAX_WALL_RUN_M);

    private final double dbPerMeter;
    private final double dbPerCrossing;
    private final double foliageDensity;
    private final double maxRunMeters;

    RfMaterial(double dbPerMeter, double dbPerCrossing, double foliageDensity) {
        this(dbPerMeter, dbPerCrossing, foliageDensity, Double.POSITIVE_INFINITY);
    }

    RfMaterial(double dbPerMeter, double dbPerCrossing, double foliageDensity, double maxRunMeters) {
        this.dbPerMeter = dbPerMeter;
        this.dbPerCrossing = dbPerCrossing;
        this.foliageDensity = foliageDensity;
        this.maxRunMeters = maxRunMeters;
    }

    public double dbPerMeter() {
        return this.dbPerMeter;
    }

    public double dbPerCrossing() {
        return this.dbPerCrossing;
    }

    public double maxRunMeters() {
        return this.maxRunMeters;
    }

    public boolean wallScale() {
        return this.maxRunMeters < Double.POSITIVE_INFINITY;
    }

    public double foliageDensity() {
        return this.foliageDensity;
    }

    public boolean foliage() {
        return this.foliageDensity > 0.0;
    }

    public double straightBlockDb() {
        if (this.foliage()) {
            return FoliageLoss.db(this.foliageDensity);
        }
        return this.dbPerMeter + this.dbPerCrossing;
    }

    public static final class Wall {
        public static final double LOG_BLOCK_DB = 12.0;
        public static final double PLANK_BLOCK_DB = 4.5;
        public static final double MASONRY_BLOCK_DB = 7.0;
        public static final double CONCRETE_BLOCK_DB = 9.0;
        public static final double GROUND_BLOCK_DB = 8.0;
        public static final double SAND_BLOCK_DB = 8.0;
        public static final double CLOTH_BLOCK_DB = 2.0;
        public static final double MAX_WALL_RUN_M = 2.0;
        public static final double WINDOW_PANE_M = 0.006;
        public static final double PLANT_DENSITY = 0.5;
        public static final double METAL_MESH_DB = 6.0;
        public static final double WATER_CELSIUS = 15.0;
        public static final double ICE_CELSIUS = -5.0;
        public static final double SNOW_DENSITY = 0.3;
        public static final Dielectric WOOD = Dielectric.p2040(1.99, 0.0, 0.0047, 1.0718, RadioBand.FREQUENCY_GHZ);
        public static final Dielectric BRICK = Dielectric.p2040(3.91, 0.0, 0.0238, 0.16, RadioBand.FREQUENCY_GHZ);
        public static final Dielectric CONCRETE = Dielectric.p2040(5.24, 0.0, 0.0462, 0.7822, RadioBand.FREQUENCY_GHZ);
        public static final Dielectric GLASS = Dielectric.p2040(6.31, 0.0, 0.0036, 1.3394, RadioBand.FREQUENCY_GHZ);
        public static final Dielectric FIBRE = Dielectric.p2040(1.48, 0.0, 0.0011, 1.0750, RadioBand.FREQUENCY_GHZ);
        public static final Dielectric MEDIUM_DRY_GROUND = Dielectric.p2040(15.0, -0.1, 0.035, 1.63, RadioBand.FREQUENCY_GHZ);
        public static final Dielectric VERY_DRY_GROUND = Dielectric.p2040(3.0, 0.0, 0.00015, 2.52, RadioBand.FREQUENCY_GHZ);
        public static final Dielectric METAL = Dielectric.conductor(1.0E7, RadioBand.FREQUENCY_GHZ);
        public static final Dielectric WATER = Dielectric.pureWater(RadioBand.FREQUENCY_GHZ, WATER_CELSIUS);
        public static final Dielectric ICE = Dielectric.pureIce(RadioBand.FREQUENCY_GHZ, ICE_CELSIUS);
        public static final Dielectric SNOW = Dielectric.drySnow(SNOW_DENSITY, RadioBand.FREQUENCY_GHZ, ICE_CELSIUS);

        private Wall() {
        }

        static double perMeter(double blockDb, Dielectric material) {
            return Math.max(0.0, blockDb - material.interfaceLossDb());
        }
    }
}
