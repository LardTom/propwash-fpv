package de.lardtom.propwash.drone.operator.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.accessory.client.AddonCamera;
import de.lardtom.propwash.api.client.camera.GimbalContext;
import de.lardtom.propwash.api.client.camera.GimbalProvider;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.FpvView;
import org.joml.Quaternionfc;
import org.jspecify.annotations.Nullable;

public final class GimbalFov {
    private static double lastDegrees = Double.NaN;
    private static boolean failureLogged;
    private static boolean pathFailureLogged;

    private GimbalFov() {
    }

    public static double horizontal(@Nullable QuadcopterEntity drone, double fallback) {
        GimbalProvider provider = drone == null ? null : AddonCamera.provider(drone);
        if (provider == null) {
            lastDegrees = Double.NaN;
            return fallback;
        }
        double wanted;
        try {
            wanted = provider.fovDegrees(AddonCamera.context(drone, drone.orientation(), CameraMount.uptilt(drone.build(), drone.tune().values()), 0.0,
                    FpvView.spectating()));
        } catch (RuntimeException e) {
            if (!failureLogged) {
                failureLogged = true;
                Constants.LOG.warn("Brennweite eines Add-on-Gimbals fehlgeschlagen", e);
            }
            lastDegrees = Double.NaN;
            return fallback;
        }
        lastDegrees = clamp(wanted);
        return Double.isNaN(lastDegrees) ? fallback : lastDegrees;
    }

    public static double path(@Nullable QuadcopterEntity drone, Quaternionfc body, float uptiltDegrees) {
        GimbalProvider provider = drone == null ? null : AddonCamera.provider(drone);
        return provider == null ? Double.NaN : camera(provider, AddonCamera.context(drone, body, uptiltDegrees, 0.0, false));
    }

    static double camera(GimbalProvider provider, GimbalContext context) {
        try {
            return clamp(provider.gimbalFovDegrees(context));
        } catch (RuntimeException e) {
            if (!pathFailureLogged) {
                pathFailureLogged = true;
                Constants.LOG.warn("Gimbal eines Add-ons: gimbalFovDegrees fehlgeschlagen", e);
            }
            return Double.NaN;
        }
    }

    public static double clamp(double degrees) {
        if (!Double.isFinite(degrees)) {
            return Double.NaN;
        }
        return Math.max(GimbalProvider.MIN_FOV_DEGREES, Math.min(GimbalProvider.MAX_FOV_DEGREES, degrees));
    }

    public static double lastDegrees() {
        return lastDegrees;
    }
}
