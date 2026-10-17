package de.lardtom.propwash.accessory;

import de.lardtom.propwash.api.accessory.MountPose;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

public final class MountPoses {
    private MountPoses() {
    }

    public static Vec3 center(QuadcopterEntity drone) {
        return drone.position().add(0.0, -AirframeSpec.of(drone.build()).hitboxFootOffset, 0.0);
    }

    public static Optional<MountPose> of(QuadcopterEntity drone, AccessoryMount mount) {
        DroneBuild build = drone.build();
        if (!build.frame().accessoryMounts.contains(mount)) {
            return Optional.empty();
        }
        AirframeSpec p = AirframeSpec.of(build);
        return Optional.of(pose(center(drone), drone.orientation(), p, AccessoryAnchor.of(build.frame(), mount),
                build.accessories().at(mount).orElse(null)));
    }

    public static MountPose pose(Vec3 com, Quaternionfc orientation, AirframeSpec p, AccessoryAnchor anchor, @Nullable AccessoryType type) {
        AccessoryAnchor tilted = type == null ? anchor
                : new AccessoryAnchor(anchor.x(), anchor.y(), anchor.z(), anchor.tiltFor(type), anchor.lengthMm());
        double[] centerMm = type == null ? new double[]{anchor.x(), anchor.y(), anchor.z()} : anchor.centerOf(type);
        double[] frontMm;
        if (type == null) {
            frontMm = new double[]{anchor.x(), anchor.y(), anchor.z()};
        } else {
            double[] min = type.boxMinMm();
            double[] max = type.boxMaxMm();
            frontMm = tilted.toFrame((min[0] + max[0]) / 2.0, (min[1] + max[1]) / 2.0, min[2]);
        }
        Quaternionf q = new Quaternionf(orientation);
        Vec3 anchorWorld = toWorld(com, q, p, anchor.x(), anchor.y(), anchor.z());
        return new MountPose(anchorWorld, toWorld(com, q, p, centerMm[0], centerMm[1], centerMm[2]),
                toWorld(com, q, p, frontMm[0], frontMm[1], frontMm[2]), direction(q, tilted.rotate(0.0, 0.0, -1.0)),
                direction(q, tilted.rotate(0.0, 1.0, 0.0)), direction(q, new double[]{1.0, 0.0, 0.0}));
    }

    public static Vec3 toWorld(Vec3 com, Quaternionfc q, AirframeSpec p, double frameXmm, double frameYmm, double frameZmm) {
        Vector3f v = new Vector3f((float) (frameXmm / 1000.0 + p.comX), (float) (frameYmm / 1000.0 - p.comHeight),
                (float) (frameZmm / 1000.0 + p.comZ));
        q.transform(v);
        return com.add(v.x, v.y, v.z);
    }

    public static Vec3 toFrameMetres(Vec3 com, Quaternionfc q, AirframeSpec p, Vec3 world) {
        Vector3f v = new Vector3f((float) (world.x - com.x), (float) (world.y - com.y), (float) (world.z - com.z));
        new Quaternionf(q).conjugate().transform(v);
        return new Vec3(v.x - p.comX, v.y + p.comHeight, v.z - p.comZ);
    }

    private static Vec3 direction(Quaternionf q, double[] local) {
        Vector3f v = new Vector3f((float) local[0], (float) local[1], (float) local[2]);
        q.transform(v);
        return new Vec3(v.x, v.y, v.z).normalize();
    }

    public static Optional<AccessoryMount> mountToward(DroneBuild build, AirframeSpec p, Vec3 framePointMetres, double minCosine) {
        Vec3 com = new Vec3(-p.comX, p.comHeight, -p.comZ);
        Vec3 toward = framePointMetres.subtract(com);
        if (toward.lengthSqr() < 1.0E-12) {
            return Optional.empty();
        }
        Vec3 dir = toward.normalize();
        AccessoryMount best = null;
        double bestScore = minCosine;
        for (Map.Entry<AccessoryMount, AccessoryType> entry : build.accessories().byMount().entrySet()) {
            double[] c = AccessoryAnchor.of(build.frame(), entry.getKey()).centerOf(entry.getValue());
            Vec3 offset = new Vec3(c[0] / 1000.0, c[1] / 1000.0, c[2] / 1000.0).subtract(com);
            if (offset.lengthSqr() < 1.0E-12) {
                continue;
            }
            double score = offset.normalize().dot(dir);
            if (score > bestScore) {
                bestScore = score;
                best = entry.getKey();
            }
        }
        return Optional.ofNullable(best);
    }
}
