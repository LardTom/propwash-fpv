package de.lardtom.propwash.drone.led;

public final class LedAnimator {
    public static final float LOW_BATTERY_CHARGE = 0.2F;
    public static final int DISARMED_RGB = 0x3CFF4A;
    public static final int WARNING_RGB = 0xFF9020;
    public static final double BLINK_HZ = 2.0;
    public static final double WARNING_HZ = 3.0;
    public static final double BREATH_SECONDS = 2.4;
    public static final float BREATH_MIN = 0.3F;
    public static final double RAINBOW_SECONDS = 4.0;
    public static final int MAX_LUMINANCE = 12;

    private LedAnimator() {
    }

    public static int color(LedSettings settings, int index, int count, Input input) {
        if (!input.powered()) {
            return 0;
        }
        int rgb;
        float intensity;
        switch (settings.mode()) {
            case BLINK -> {
                rgb = settings.color().rgb();
                intensity = phase(input.seconds() * BLINK_HZ) < 0.5 ? 1.0F : 0.0F;
            }
            case ARMED -> {
                rgb = input.armed() ? settings.color().rgb() : DISARMED_RGB;
                intensity = input.armed() ? 1.0F : breath(input.seconds());
            }
            case LOW_BATTERY -> {
                boolean low = input.batteryCharge() < LOW_BATTERY_CHARGE;
                rgb = low ? WARNING_RGB : settings.color().rgb();
                intensity = !low || phase(input.seconds() * WARNING_HZ) < 0.5 ? 1.0F : 0.0F;
            }
            case RAINBOW -> {
                double hue = phase((double) index / Math.max(1, count) + input.seconds() / RAINBOW_SECONDS);
                rgb = hueToRgb(hue);
                intensity = 1.0F;
            }
            default -> {
                rgb = settings.color().rgb();
                intensity = 1.0F;
            }
        }
        int alpha = Math.round(255.0F * intensity * settings.brightness());
        if (alpha <= 0) {
            return 0;
        }
        return (Math.min(255, alpha) << 24) | (rgb & 0x00FFFFFF);
    }

    public static Light light(LedSettings settings, int count, Input input) {
        int n = Math.max(1, count);
        double red = 0.0;
        double green = 0.0;
        double blue = 0.0;
        double alphaSum = 0.0;
        for (int i = 0; i < n; i++) {
            int argb = color(settings, i, n, input);
            double alpha = (argb >>> 24) / 255.0;
            if (alpha <= 0.0) {
                continue;
            }
            red += ((argb >>> 16) & 0xFF) / 255.0 * alpha;
            green += ((argb >>> 8) & 0xFF) / 255.0 * alpha;
            blue += (argb & 0xFF) / 255.0 * alpha;
            alphaSum += alpha;
        }
        if (alphaSum <= 0.0) {
            return Light.OFF;
        }
        double max = Math.max(red, Math.max(green, blue));
        if (max <= 0.0) {
            return Light.OFF;
        }
        return new Light((float) (red / max), (float) (green / max), (float) (blue / max), (float) (alphaSum / n));
    }

    public static boolean powered(boolean hasBattery, float batteryCharge, boolean switchedOff) {
        return hasBattery && batteryCharge > 0.0F && !switchedOff;
    }

    public static int luminance(LedSettings settings, int count, Input input) {
        Light light = light(settings, count, input);
        return Math.max(0, Math.min(15, Math.round(MAX_LUMINANCE * light.intensity())));
    }

    public static int hueToRgb(double hue) {
        double h = phase(hue) * 6.0;
        int sector = (int) Math.floor(h);
        double f = h - sector;
        double r;
        double g;
        double b;
        switch (sector) {
            case 0 -> {
                r = 1.0;
                g = f;
                b = 0.0;
            }
            case 1 -> {
                r = 1.0 - f;
                g = 1.0;
                b = 0.0;
            }
            case 2 -> {
                r = 0.0;
                g = 1.0;
                b = f;
            }
            case 3 -> {
                r = 0.0;
                g = 1.0 - f;
                b = 1.0;
            }
            case 4 -> {
                r = f;
                g = 0.0;
                b = 1.0;
            }
            default -> {
                r = 1.0;
                g = 0.0;
                b = 1.0 - f;
            }
        }
        return (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    static float breath(double seconds) {
        double wave = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * phase(seconds / BREATH_SECONDS));
        return (float) (BREATH_MIN + (1.0 - BREATH_MIN) * wave);
    }

    private static double phase(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return value - Math.floor(value);
    }

    private static int channel(double value) {
        return (int) Math.round(Math.max(0.0, Math.min(1.0, value)) * 255.0);
    }

    public record Input(boolean armed, boolean powered, float batteryCharge, double seconds) {
    }

    public record Light(float red, float green, float blue, float intensity) {
        public static final Light OFF = new Light(0.0F, 0.0F, 0.0F, 0.0F);

        public boolean on() {
            return this.intensity > 0.0F;
        }
    }
}
