package de.lardtom.propwash.core;

import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public interface ExtraCollider {
    double MAX_HALF_EXTENT = 8.0;
    double MAX_REACH = 64.0;
    AtomicLong REACH = new AtomicLong(Double.doubleToLongBits(MAX_HALF_EXTENT));

    void collectColliders(AABB query, List<AABB> out);

    default void collectOrientedColliders(AABB query, List<AABB> aligned, List<OrientedBox> oriented) {
        this.collectColliders(query, aligned);
    }

    static double reach() {
        return Double.longBitsToDouble(REACH.get());
    }

    static void extendReach(double reach) {
        if (!Double.isFinite(reach)) {
            return;
        }
        double wanted = Math.min(MAX_REACH, reach);
        while (true) {
            long current = REACH.get();
            if (Double.longBitsToDouble(current) >= wanted || REACH.compareAndSet(current, Double.doubleToLongBits(wanted))) {
                return;
            }
        }
    }
}
