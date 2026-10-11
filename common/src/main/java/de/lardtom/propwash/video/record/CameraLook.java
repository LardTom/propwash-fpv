package de.lardtom.propwash.video.record;

import de.lardtom.propwash.build.ActionCamSpec;

public final class CameraLook {
    public static final double GRAIN_SCALE = 0.025;
    public static final double GRAIN_GONE_ABOVE = 0.2;

    private CameraLook() {
    }

    public static float sharpness(ActionCamSpec spec) {
        return (float) Math.max(-0.6, Math.min(0.4, (spec.imageQuality() - 0.9) * 3.5));
    }

    public static float grain(ActionCamSpec spec, boolean enabled) {
        return enabled ? (float) (spec.lowLightNoise() * GRAIN_SCALE) : 0.0F;
    }

    public static double grainWeight(double luma) {
        double t = Math.max(0.0, Math.min(1.0, luma / GRAIN_GONE_ABOVE));
        return 1.0 - t * t * (3.0 - 2.0 * t);
    }
}
