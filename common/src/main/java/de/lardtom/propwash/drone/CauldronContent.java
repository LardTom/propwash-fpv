package de.lardtom.propwash.drone;

import de.lardtom.propwash.interact.SnagMaterial;
import de.lardtom.propwash.sim.Fluid;
import org.jspecify.annotations.Nullable;

public enum CauldronContent {
    WATER(Fluid.WATER, null),
    LAVA(Fluid.LAVA, null),
    POWDER_SNOW(Fluid.NONE, SnagMaterial.POWDER_SNOW);

    public static final double WALL = 2.0 / 16.0;
    public static final double FLOOR = 4.0 / 16.0;
    public static final double LAVA_TOP = 15.0 / 16.0;
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 3;

    private final Fluid fluid;
    private final @Nullable SnagMaterial snag;

    CauldronContent(Fluid fluid, @Nullable SnagMaterial snag) {
        this.fluid = fluid;
        this.snag = snag;
    }

    public Fluid fluid() {
        return this.fluid;
    }

    public @Nullable SnagMaterial snag() {
        return this.snag;
    }

    public double top(int level) {
        if (this == LAVA) {
            return LAVA_TOP;
        }
        return (6.0 + 3.0 * Math.clamp(level, MIN_LEVEL, MAX_LEVEL)) / 16.0;
    }

    public Fluid fluidAt(int level, double lx, double ly, double lz) {
        return this.fluid != Fluid.NONE && column(lx, lz) && ly >= FLOOR && ly <= this.top(level) ? this.fluid : Fluid.NONE;
    }

    public double waterSurface(int level, double lx, double ly, double lz) {
        return this.fluid == Fluid.WATER && column(lx, lz) ? this.top(level) - ly : Double.NEGATIVE_INFINITY;
    }

    public static boolean column(double lx, double lz) {
        return lx >= WALL && lx <= 1.0 - WALL && lz >= WALL && lz <= 1.0 - WALL;
    }
}
