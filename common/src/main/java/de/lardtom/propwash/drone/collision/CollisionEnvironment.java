package de.lardtom.propwash.drone.collision;

import de.lardtom.propwash.api.drone.CollisionFlags;
import de.lardtom.propwash.sim.Fluid;
import de.lardtom.propwash.sim.SimEnvironment;

public final class CollisionEnvironment {
    private CollisionEnvironment() {
    }

    public static void apply(CollisionFlags flags, SimEnvironment env) {
        if (!flags.blocks()) {
            env.groundDistance = Double.POSITIVE_INFINITY;
            env.solidDistance = Double.POSITIVE_INFINITY;
        }
        if (!flags.fluids()) {
            env.fluid = Fluid.NONE;
            env.waterSurface = Double.NEGATIVE_INFINITY;
        }
    }

    public static void beforeStep(CollisionFlags flags, SimEnvironment env) {
        if (!flags.snags()) {
            env.snag.clear();
        }
    }
}
