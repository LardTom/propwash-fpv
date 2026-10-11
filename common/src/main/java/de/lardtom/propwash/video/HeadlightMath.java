package de.lardtom.propwash.video;

public final class HeadlightMath {
    public static final double RANGE_BLOCKS = 24.0;
    public static final double FALLOFF_BLOCKS = 7.5;
    public static final double CONE_INNER_DEG = 11.0;
    public static final double CONE_OUTER_DEG = 34.0;
    public static final double SPILL = 0.02;
    public static final double CONE_EDGE_POWER = 2.0;
    public static final double HOTSPOT_DEG = 7.0;
    public static final double HOTSPOT_BOOST = 1.1;
    public static final double WARM_RED = 1.0;
    public static final double WARM_GREEN = 0.9;
    public static final double WARM_BLUE = 0.74;
    public static final double LED_TINT = 0.35;
    public static final double WARM_EDGE = 0.0;
    public static final double WARM_CORE = 0.5;
    public static final double GAIN = 12.0;
    public static final double LAMBERT_WEIGHT = 0.7;
    public static final double LIFT_PER_GAIN = 0.004;
    public static final double HUE_TRUST_FROM = 0.03;
    public static final double HUE_TRUST_TO = 0.14;
    public static final double DARK_LIGHT = 2.0;
    public static final double BRIGHT_LIGHT = 11.0;
    public static final double GAMMA_RELIEF = 0.35;
    public static final double SMOOTH_SECONDS = 0.35;
    public static final double MIN_GAIN = 0.02;
    public static final int OVERLAY_LAYERS = 3;
    public static final double OVERLAY_LIFT = 0.035;

    private HeadlightMath() {
    }

    public static double darkness(int rawLight, double gamma) {
        double bright = smoothstep(DARK_LIGHT, BRIGHT_LIGHT, rawLight);
        double relief = 1.0 - GAMMA_RELIEF * clamp(gamma, 0.0, 1.0);
        return (1.0 - bright) * relief;
    }

    public static double smooth(double current, double target, double dtSeconds) {
        if (!Double.isFinite(current)) {
            return target;
        }
        if (dtSeconds <= 0.0) {
            return current;
        }
        double alpha = 1.0 - Math.exp(-dtSeconds / SMOOTH_SECONDS);
        return current + (target - current) * alpha;
    }

    public static double gain(double intensity, double darkness) {
        double g = GAIN * clamp(intensity, 0.0, 1.0) * clamp(darkness, 0.0, 1.0);
        return g < MIN_GAIN ? 0.0 : g;
    }

    public static double attenuation(double distance) {
        if (!(distance >= 0.0) || distance >= RANGE_BLOCKS) {
            return 0.0;
        }
        double ratio = distance / FALLOFF_BLOCKS;
        double base = 1.0 / (1.0 + ratio * ratio);
        double cut = 1.0 - smoothstep(RANGE_BLOCKS * 0.7, RANGE_BLOCKS, distance);
        return base * cut;
    }

    public static double cone(double cosAngle) {
        double inner = Math.cos(Math.toRadians(CONE_INNER_DEG));
        double outer = Math.cos(Math.toRadians(CONE_OUTER_DEG));
        return SPILL + (1.0 - SPILL) * Math.pow(smoothstep(outer, inner, cosAngle), CONE_EDGE_POWER);
    }

    public static float[] tint(float red, float green, float blue) {
        double r = WARM_RED + (clamp(red, 0.0, 1.0) - WARM_RED) * LED_TINT;
        double g = WARM_GREEN + (clamp(green, 0.0, 1.0) - WARM_GREEN) * LED_TINT;
        double b = WARM_BLUE + (clamp(blue, 0.0, 1.0) - WARM_BLUE) * LED_TINT;
        double max = Math.max(r, Math.max(g, b));
        if (max <= 1.0E-6) {
            return new float[]{(float) WARM_RED, (float) WARM_GREEN, (float) WARM_BLUE};
        }
        return new float[]{(float) (r / max), (float) (g / max), (float) (b / max)};
    }

    public static double hotspotBlend(double cosAngle) {
        return smoothstep(Math.cos(Math.toRadians(CONE_INNER_DEG)), Math.cos(Math.toRadians(HOTSPOT_DEG)), cosAngle);
    }

    public static double hotspot(double cosAngle) {
        return 1.0 + HOTSPOT_BOOST * hotspotBlend(cosAngle);
    }

    public static double warmth(double cosAngle) {
        return WARM_EDGE + (WARM_CORE - WARM_EDGE) * hotspotBlend(cosAngle);
    }

    public static double[] warmTint(double cosAngle) {
        double w = warmth(cosAngle);
        return new double[]{1.0 + (WARM_RED - 1.0) * w, 1.0 + (WARM_GREEN - 1.0) * w, 1.0 + (WARM_BLUE - 1.0) * w};
    }

    public static double boost(double luma, double gain, double cone, double attenuation) {
        double headroom = 1.0 - smoothstep(0.05, 0.85, luma);
        return 1.0 + gain * cone * attenuation * headroom;
    }

    public static double lift(double gain, double cone, double attenuation) {
        return LIFT_PER_GAIN * gain * cone * attenuation;
    }

    public static double hueTrust(double luma) {
        return smoothstep(HUE_TRUST_FROM, HUE_TRUST_TO, luma);
    }

    public static double overlayRadius(double verticalFovDeg, double halfHeight) {
        double fov = Double.isFinite(verticalFovDeg) && verticalFovDeg > 1.0 ? verticalFovDeg : 70.0;
        double half = Math.tan(Math.toRadians(Math.min(fov, 170.0) * 0.5));
        return Math.tan(Math.toRadians(CONE_OUTER_DEG)) / half * halfHeight;
    }

    public static double overlayLayerGain(double gain) {
        double target = Math.max(0.0, gain) / GAIN * (Math.pow(2.0, OVERLAY_LAYERS) - 1.0);
        return clamp(Math.pow(1.0 + target, 1.0 / OVERLAY_LAYERS) - 1.0, 0.0, 1.0);
    }

    public static double smoothstep(double edge0, double edge1, double x) {
        if (edge0 == edge1) {
            return x < edge0 ? 0.0 : 1.0;
        }
        double t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
