package de.lardtom.propwash.api.client.camera;

import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;

public interface GimbalProvider {
    double MIN_FOV_DEGREES = 20.0;
    double MAX_FOV_DEGREES = 150.0;

    boolean appliesTo(Entity drone);

    void orient(GimbalContext context, Quaternionf rotation);

    default boolean suppressesShake() {
        return true;
    }

    default boolean stabilizesRecording() {
        return false;
    }

    default boolean modelPose(GimbalContext context, Quaternionf rotation) {
        return false;
    }

    default double fovDegrees(GimbalContext context) {
        return Double.NaN;
    }

    default boolean showsProps(GimbalContext context) {
        return true;
    }

    default boolean gimbalRotation(GimbalContext context, Quaternionf rotation) {
        return false;
    }

    default double gimbalFovDegrees(GimbalContext context) {
        return Double.NaN;
    }
}
