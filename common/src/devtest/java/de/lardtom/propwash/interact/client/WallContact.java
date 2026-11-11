package de.lardtom.propwash.interact.client;

import net.minecraft.world.phys.AABB;

import java.util.List;

final class WallContact {
    static final double STUCK = 0.02;

    private WallContact() {
    }

    static double depth(AABB drone, List<AABB> blocks) {
        double depth = 0.0;
        for (AABB block : blocks) {
            double x = Math.min(drone.maxX, block.maxX) - Math.max(drone.minX, block.minX);
            double y = Math.min(drone.maxY, block.maxY) - Math.max(drone.minY, block.minY);
            double z = Math.min(drone.maxZ, block.maxZ) - Math.max(drone.minZ, block.minZ);
            depth = Math.max(depth, Math.min(x, Math.min(y, z)));
        }
        return depth;
    }

    static boolean drifting(double side, double velocity, double limit) {
        return Math.abs(side) > limit && side * velocity > 0.0;
    }
}
