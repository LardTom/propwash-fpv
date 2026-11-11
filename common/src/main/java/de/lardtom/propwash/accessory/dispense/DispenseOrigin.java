package de.lardtom.propwash.accessory.dispense;

import de.lardtom.propwash.accessory.MountPoses;
import de.lardtom.propwash.api.accessory.MountPose;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;

import java.util.Optional;

public record DispenseOrigin(Vec3 muzzle, double floorY, double liftMetres) {
    public static final double SUPPORT_PROBE = 0.05;
    public static final double NO_FLOOR = Double.NEGATIVE_INFINITY;

    public static DispenseOrigin of(ServerLevel level, QuadcopterEntity drone, AccessoryMount mount, MountPose pose) {
        Vec3 front = pose.front();
        DroneBuild build = drone.build();
        Optional<AccessoryType> type = build.accessories().at(mount);
        if (type.isEmpty() || !resting(level, drone)) {
            return new DispenseOrigin(front, NO_FLOOR, 0.0);
        }
        double support = drone.getBoundingBox().minY;
        double lowest = lowestY(MountPoses.center(drone), drone.orientation(), AirframeSpec.of(build), AccessoryAnchor.of(build.frame(), mount),
                type.get());
        return standing(front, support, lowest);
    }

    public static DispenseOrigin standing(Vec3 front, double supportY, double lowestBoxY) {
        double lift = lift(supportY, lowestBoxY);
        return new DispenseOrigin(front.add(0.0, lift, 0.0), supportY, lift);
    }

    public static double lift(double supportY, double lowestBoxY) {
        double lift = supportY - lowestBoxY;
        return Double.isFinite(lift) && lift > 0.0 ? lift : 0.0;
    }

    public static double lowestY(Vec3 com, Quaternionfc orientation, AirframeSpec p, AccessoryAnchor anchor, AccessoryType type) {
        double lowest = Double.POSITIVE_INFINITY;
        for (double[] corner : anchor.corners(type)) {
            lowest = Math.min(lowest, MountPoses.toWorld(com, orientation, p, corner[0], corner[1], corner[2]).y);
        }
        return lowest;
    }

    public static boolean resting(ServerLevel level, QuadcopterEntity drone) {
        if (drone.onGround()) {
            return true;
        }
        AABB box = drone.getBoundingBox();
        AABB below = new AABB(box.minX, box.minY - SUPPORT_PROBE, box.minZ, box.maxX, box.minY, box.maxZ);
        return !level.noBlockCollision(drone, below);
    }

    public double above(double y) {
        return Math.max(y, this.floorY);
    }
}
