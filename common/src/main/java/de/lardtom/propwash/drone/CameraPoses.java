package de.lardtom.propwash.drone;

import de.lardtom.propwash.accessory.MountPoses;
import de.lardtom.propwash.api.drone.CameraPose;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.FpvRig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Optional;

public final class CameraPoses {
    private CameraPoses() {
    }

    public static Optional<CameraPose> of(Entity entity) {
        if (!(entity instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            return Optional.empty();
        }
        return of(drone.build(), drone.tune().values(), MountPoses.center(drone), drone.orientation(), drone.scale());
    }

    public static Optional<CameraPose> of(DroneBuild build, Map<String, Float> tune, Vec3 com, Quaternionfc orientation) {
        return of(build, tune, com, orientation, DroneScale.NORMAL);
    }

    public static Optional<CameraPose> of(DroneBuild build, Map<String, Float> tune, Vec3 com, Quaternionfc orientation, float scale) {
        if (build.video().missing() || !com.isFinite()) {
            return Optional.empty();
        }
        AirframeSpec spec = AirframeSpec.of(build);
        double uptilt = CameraMount.uptilt(build, tune);
        double fov = verticalFovDeg(FpvRig.of(build).horizontalFovDeg(), FpvRig.ASPECT);
        if (!(fov > 0.0 && fov < 180.0)) {
            return Optional.empty();
        }
        if (DroneScale.scaled(scale)) {
            Vec3 raised = com.add(0.0, DroneScale.raise(-spec.hitboxFootOffset, scale), 0.0);
            return Optional.of(pose(raised, orientation, spec.lensRight * scale, spec.lensUp * scale, spec.lensForward * scale, uptilt, fov));
        }
        return Optional.of(pose(com, orientation, spec.lensRight, spec.lensUp, spec.lensForward, uptilt, fov));
    }

    public static CameraPose pose(Vec3 com, Quaternionfc orientation, double lensRight, double lensUp, double lensForward, double uptiltDeg,
                                  double verticalFovDeg) {
        Quaternionf body = new Quaternionf(orientation).normalize();
        Vector3f lens = body.transform(new Vector3f((float) lensRight, (float) lensUp, (float) -lensForward));
        Quaternionf camera = new Quaternionf(body).rotateX((float) Math.toRadians(uptiltDeg));
        Vector3f look = camera.transform(new Vector3f(0.0F, 0.0F, -1.0F)).normalize();
        Vector3f up = camera.transform(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
        return new CameraPose(com.add(lens.x, lens.y, lens.z), new Vec3(look.x, look.y, look.z), new Vec3(up.x, up.y, up.z), verticalFovDeg);
    }

    public static double verticalFovDeg(double horizontalFovDeg, double aspect) {
        return Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians(horizontalFovDeg) / 2.0) / aspect));
    }
}
