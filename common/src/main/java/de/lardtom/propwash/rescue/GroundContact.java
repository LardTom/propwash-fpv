package de.lardtom.propwash.rescue;

import de.lardtom.propwash.drone.DroneScale;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.sim.AirframeSpec;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

public final class GroundContact {
    public static final double PROBE = 0.3;
    public static final double TOUCH = 0.02;
    public static final double REST_SPEED = 0.25;
    private static final double TICKS_PER_SECOND = 20.0;

    @FunctionalInterface
    public interface Column {
        double free(double dx, double dz, double reach);
    }

    private GroundContact() {
    }

    public static boolean grounded(QuadcopterEntity drone) {
        return grounded(gap(drone, PROBE), drone.getDeltaMovement().length() * TICKS_PER_SECOND);
    }

    public static boolean grounded(double gap, double speed) {
        return gap < TOUCH && speed < REST_SPEED;
    }

    public static double gap(QuadcopterEntity drone, double range) {
        double hitbox = drone.supportGap(range);
        if (drone.noPhysics || !(hitbox > 0.0)) {
            return hitbox;
        }
        float scale = drone.scale();
        double size = DroneScale.scaled(scale) ? scale : 1.0;
        double x = drone.getX();
        double z = drone.getZ();
        return gap(AirframeSpec.of(drone.build()), drone.orientation(), size, hitbox, range,
                (dx, dz, reach) -> drone.columnGap(x + dx, z + dz, reach));
    }

    public static double gap(AirframeSpec spec, Quaternionfc orientation, double size, double hitboxGap, double range, Column column) {
        double gap = hitboxGap;
        Vector3f point = new Vector3f();
        for (int k = 0; k < spec.groundCount(); k++) {
            if (!spec.groundUsed(k)) {
                continue;
            }
            point.set((float) -spec.groundX(k), (float) spec.groundY(k), (float) -spec.groundZ(k));
            orientation.transform(point);
            double depth = size * (spec.hitboxFootOffset - point.y);
            if (!(depth > 0.0)) {
                continue;
            }
            gap = Math.min(gap, column.free(size * point.x, size * point.z, depth + range) - depth);
        }
        return Math.max(0.0, gap);
    }
}
