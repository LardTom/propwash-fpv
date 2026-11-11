package de.lardtom.propwash.drone.operator.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.accessory.client.AddonCamera;
import de.lardtom.propwash.api.client.camera.GimbalContext;
import de.lardtom.propwash.api.client.camera.GimbalProvider;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.FpvView;

public final class GimbalProps {
    private static int hiddenFrames;
    private static boolean failureLogged;

    private GimbalProps() {
    }

    public static boolean shown(QuadcopterEntity drone, float tiltDegrees) {
        GimbalProvider provider = AddonCamera.provider(drone);
        if (provider == null) {
            return true;
        }
        if (shown(provider, AddonCamera.context(drone, drone.orientation(), tiltDegrees, 0.0, FpvView.spectating()))) {
            return true;
        }
        hiddenFrames++;
        return false;
    }

    static boolean shown(GimbalProvider provider, GimbalContext context) {
        try {
            return provider.showsProps(context);
        } catch (RuntimeException e) {
            if (!failureLogged) {
                failureLogged = true;
                Constants.LOG.warn("Gimbal eines Add-ons: showsProps fehlgeschlagen", e);
            }
            return true;
        }
    }

    public static int hiddenFrames() {
        return hiddenFrames;
    }
}
