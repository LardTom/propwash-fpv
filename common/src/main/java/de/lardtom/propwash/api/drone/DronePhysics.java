package de.lardtom.propwash.api.drone;

import net.minecraft.world.phys.Vec3;

public interface DronePhysics {
    double MAX_IMPULSE = 50.0;
    double MAX_FORCE = 500.0;
    double MAX_SECONDS = 30.0;
    double MAX_ARM = 2.0;

    boolean impulse(Vec3 impulse, Vec3 framePoint);

    boolean push(Vec3 force, boolean frameRelative, Vec3 framePoint, double seconds);

    default boolean impulse(Vec3 impulse) {
        return this.impulse(impulse, Vec3.ZERO);
    }
}
