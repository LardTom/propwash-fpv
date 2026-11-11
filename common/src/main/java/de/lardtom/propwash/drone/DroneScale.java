package de.lardtom.propwash.drone;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class DroneScale {
    public static final float MIN = 0.0625F;
    public static final float MAX = 16.0F;
    public static final float NORMAL = 1.0F;
    public static final float TOLERANCE = 1.0E-4F;

    private DroneScale() {
    }

    public static float sanitize(double value) {
        if (!Double.isFinite(value)) {
            return NORMAL;
        }
        return (float) Math.clamp(value, MIN, MAX);
    }

    public static boolean scaled(float scale) {
        return Math.abs(scale - NORMAL) > TOLERANCE;
    }

    public static float of(@Nullable Entity entity) {
        return entity instanceof QuadcopterEntity drone ? drone.scale() : NORMAL;
    }

    public static int percent(float scale) {
        return Math.round(sanitize(scale) * 100.0F);
    }

    public static EntityDimensions dimensions(double width, double height, float scale) {
        if (!scaled(scale)) {
            return EntityDimensions.fixed((float) width, (float) height);
        }
        return EntityDimensions.fixed((float) width * scale, (float) height * scale);
    }

    public static double raise(double comAboveFoot, float scale) {
        return scaled(scale) ? comAboveFoot * (scale - NORMAL) : 0.0;
    }

    public static Vector3d eye(Vector3dc com, Vector3fc lens, double comAboveFoot, float scale, Vector3d out) {
        if (!scaled(scale)) {
            return out.set(com.x() + lens.x(), com.y() + lens.y(), com.z() + lens.z());
        }
        return out.set(com.x() + lens.x() * scale, com.y() + lens.y() * scale + raise(comAboveFoot, scale), com.z() + lens.z() * scale);
    }
}
