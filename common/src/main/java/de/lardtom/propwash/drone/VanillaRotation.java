package de.lardtom.propwash.drone;

import de.lardtom.propwash.sim.SimSnapshot;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.Set;

public final class VanillaRotation {
    private VanillaRotation() {
    }

    public static Quaternionf attitude(float yRot, float xRot, Quaternionf dest) {
        double yaw = Math.toRadians(yRot);
        double pitch = Math.toRadians(Mth.clamp(xRot, -90.0F, 90.0F));
        return dest.rotationYXZ((float) (Math.PI - yaw), (float) -pitch, 0.0F);
    }

    public static boolean turns(PositionMoveRotation change, Set<Relative> relatives) {
        return turns(change.yRot(), relatives.contains(Relative.Y_ROT), change.xRot(), relatives.contains(Relative.X_ROT));
    }

    public static boolean turns(float yRot, boolean relativeY, float xRot, boolean relativeX) {
        return !(relativeY && relativeX && yRot == 0.0F && xRot == 0.0F);
    }

    public static void carryMotion(SimSnapshot snapshot, Quaterniondc target) {
        Quaterniond from = new Quaterniond(snapshot.qx, snapshot.qy, snapshot.qz, snapshot.qw);
        double lengthSquared = from.lengthSquared();
        if (lengthSquared > 1.0E-12 && Double.isFinite(lengthSquared)) {
            Quaterniond turn = new Quaterniond(target).mul(from.normalize().conjugate());
            Vector3d momentum = turn.transform(new Vector3d(snapshot.lx, snapshot.ly, snapshot.lz));
            snapshot.lx = momentum.x;
            snapshot.ly = momentum.y;
            snapshot.lz = momentum.z;
        }
        snapshot.qw = target.w();
        snapshot.qx = target.x();
        snapshot.qy = target.y();
        snapshot.qz = target.z();
    }
}
