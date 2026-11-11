package de.lardtom.propwash.drone.operator.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.accessory.client.AddonCamera;
import de.lardtom.propwash.api.client.camera.GimbalContext;
import de.lardtom.propwash.api.client.camera.GimbalProvider;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.video.record.CameraLook;
import de.lardtom.propwash.video.record.Stabilizer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

public final class GimbalRecording {
    private static boolean failureLogged;
    private static boolean rotationFailureLogged;

    private GimbalRecording() {
    }

    public static boolean stabilized(@Nullable QuadcopterEntity drone) {
        GimbalProvider provider = drone == null ? null : AddonCamera.provider(drone);
        if (provider == null) {
            return false;
        }
        try {
            return provider.stabilizesRecording();
        } catch (RuntimeException e) {
            if (!failureLogged) {
                failureLogged = true;
                Constants.LOG.warn("Gimbal eines Add-ons: stabilizesRecording fehlgeschlagen", e);
            }
            return false;
        }
    }

    public static Stabilizer.Mode mode(@Nullable QuadcopterEntity drone, Stabilizer.Mode configured) {
        return CameraLook.recordingMode(configured, stabilized(drone));
    }

    public static Quaternionf path(@Nullable QuadcopterEntity drone, Quaternionf body, float uptiltDegrees) {
        GimbalProvider provider = drone == null ? null : AddonCamera.provider(drone);
        Quaternionf gimbal = provider == null ? null : camera(provider, AddonCamera.context(drone, body, uptiltDegrees, 0.0, false));
        Minecraft minecraft = Minecraft.getInstance();
        if (gimbal == null && drone != null && minecraft.getCameraEntity() == drone && stabilized(drone)) {
            Camera camera = minecraft.gameRenderer.mainCamera();
            Quaternionf rotation = new Quaternionf(camera.rotation());
            if (Float.isFinite(rotation.x + rotation.y + rotation.z + rotation.w) && rotation.lengthSquared() > 1.0E-6F) {
                gimbal = rotation.normalize();
            }
        }
        return CameraLook.pathRotation(body, uptiltDegrees, gimbal, body);
    }

    static @Nullable Quaternionf camera(GimbalProvider provider, GimbalContext context) {
        Quaternionf rotation = new Quaternionf();
        try {
            if (!provider.gimbalRotation(context, rotation)) {
                return null;
            }
        } catch (RuntimeException e) {
            if (!rotationFailureLogged) {
                rotationFailureLogged = true;
                Constants.LOG.warn("Gimbal eines Add-ons: gimbalRotation fehlgeschlagen", e);
            }
            return null;
        }
        if (!Float.isFinite(rotation.x + rotation.y + rotation.z + rotation.w) || rotation.lengthSquared() < 1.0E-6F) {
            return null;
        }
        return rotation.normalize();
    }
}
