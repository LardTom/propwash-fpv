package de.lardtom.propwash.compat.flashback.client;

import net.minecraft.world.entity.Entity;
import org.joml.Vector3fc;

public final class ReplayView {
    public static final double CORE_RADIUS = 0.5;
    public static final double LAG_TICKS = 2.0;
    public static final double IN_FRONT = 0.5;

    private ReplayView() {
    }

    public static boolean hides(Entity drone, double camX, double camY, double camZ, Vector3fc forward) {
        if (!FlashbackBridge.ready() || !FlashbackBridge.inReplay()) {
            return false;
        }
        return inside(drone.getX() - camX, drone.getY() - camY, drone.getZ() - camZ, drone.getX() - drone.xo, drone.getY() - drone.yo,
                drone.getZ() - drone.zo, forward.x(), forward.y(), forward.z());
    }

    public static boolean inside(double dx, double dy, double dz, double tickX, double tickY, double tickZ, double fx, double fy, double fz) {
        double distanceSq = dx * dx + dy * dy + dz * dz;
        if (distanceSq < CORE_RADIUS * CORE_RADIUS) {
            return true;
        }
        double radius = CORE_RADIUS + Math.sqrt(tickX * tickX + tickY * tickY + tickZ * tickZ) * LAG_TICKS;
        if (distanceSq >= radius * radius) {
            return false;
        }
        double ahead = (dx * fx + dy * fy + dz * fz) / Math.sqrt(distanceSq);
        return ahead < IN_FRONT;
    }
}
