package de.lardtom.propwash.drone;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;

public final class GroundSettle {
    public static final float STEP = (float) Math.toRadians(24.0);
    public static final float FLAT = (float) Math.cos(Math.toRadians(0.5));

    private GroundSettle() {
    }

    public static float upY(Quaternionfc q) {
        return 1.0F - 2.0F * (q.x() * q.x() + q.z() * q.z());
    }

    public static boolean flat(Quaternionfc orientation) {
        float lengthSquared = orientation.lengthSquared();
        if (!Float.isFinite(lengthSquared) || lengthSquared < 1.0E-6F) {
            return true;
        }
        return Math.abs(upY(orientation)) / lengthSquared >= FLAT;
    }

    public static boolean step(Quaternionfc orientation, Quaternionf out) {
        out.set(orientation);
        if (flat(orientation)) {
            return false;
        }
        out.normalize();
        float ux = 2.0F * (out.x * out.y - out.w * out.z);
        float uy = upY(out);
        float uz = 2.0F * (out.y * out.z + out.w * out.x);
        float side = uy >= 0.0F ? 1.0F : -1.0F;
        float ax = -uz * side;
        float az = ux * side;
        float lean = (float) Math.sqrt(ax * ax + az * az);
        if (!(lean > 1.0E-6F)) {
            return false;
        }
        float angle = (float) Math.atan2(lean, Math.abs(uy));
        float turn = Math.min(angle, STEP);
        out.premul(new Quaternionf().rotationAxis(turn, ax / lean, 0.0F, az / lean)).normalize();
        return true;
    }
}
