package de.lardtom.propwash.drone.led;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record LedSettings(LedMode mode, LedColor color, int brightnessPercent, boolean headlight) {
    public static final String MODE = "led.mode";
    public static final String COLOR = "led.color";
    public static final String BRIGHTNESS = "led.brightnessPercent";
    public static final String HEADLIGHT = "led.headlight";
    public static final List<String> KEYS = List.of(MODE, COLOR, BRIGHTNESS, HEADLIGHT);
    public static final int MIN_BRIGHTNESS = 10;
    public static final int MAX_BRIGHTNESS = 100;
    public static final int BRIGHTNESS_STEP = 5;
    public static final LedSettings DEFAULT = new LedSettings(LedMode.STATIC, LedColor.WHITE, MAX_BRIGHTNESS, true);

    public LedSettings {
        mode = mode == null ? LedMode.STATIC : mode;
        color = color == null ? LedColor.WHITE : color;
        brightnessPercent = snapBrightness(brightnessPercent);
    }

    public static boolean isKey(String key) {
        return KEYS.contains(key);
    }

    public static float defaultValue(String key) {
        return DEFAULT.values().getOrDefault(key, 0.0F);
    }

    public static float sanitize(String key, float value) {
        return switch (key) {
            case MODE -> LedMode.byIndex(value).ordinal();
            case COLOR -> LedColor.byIndex(value).ordinal();
            case BRIGHTNESS -> Float.isFinite(value) ? snapBrightness(Math.round(value)) : MAX_BRIGHTNESS;
            case HEADLIGHT -> !Float.isFinite(value) || value >= 0.5F ? 1.0F : 0.0F;
            default -> value;
        };
    }

    public static LedSettings of(Map<String, Float> tune) {
        if (tune == null || tune.isEmpty()) {
            return DEFAULT;
        }
        return new LedSettings(
                LedMode.byIndex(value(tune, MODE)),
                LedColor.byIndex(value(tune, COLOR)),
                Math.round(value(tune, BRIGHTNESS)),
                value(tune, HEADLIGHT) >= 0.5F);
    }

    public static Optional<AccessoryType> strip(DroneBuild build) {
        if (build == null) {
            return Optional.empty();
        }
        for (AccessoryType type : build.accessories().list()) {
            if (type.isLight()) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public Map<String, Float> values() {
        Map<String, Float> out = new LinkedHashMap<>();
        out.put(MODE, (float) this.mode.ordinal());
        out.put(COLOR, (float) this.color.ordinal());
        out.put(BRIGHTNESS, (float) this.brightnessPercent);
        out.put(HEADLIGHT, this.headlight ? 1.0F : 0.0F);
        return out;
    }

    public float brightness() {
        return this.brightnessPercent / 100.0F;
    }

    public LedSettings withMode(LedMode next) {
        return new LedSettings(next, this.color, this.brightnessPercent, this.headlight);
    }

    public LedSettings withColor(LedColor next) {
        return new LedSettings(this.mode, next, this.brightnessPercent, this.headlight);
    }

    public LedSettings withBrightness(int percent) {
        return new LedSettings(this.mode, this.color, percent, this.headlight);
    }

    public LedSettings withHeadlight(boolean on) {
        return new LedSettings(this.mode, this.color, this.brightnessPercent, on);
    }

    private static float value(Map<String, Float> tune, String key) {
        Float value = tune.get(key);
        return value != null && Float.isFinite(value) ? sanitize(key, value) : defaultValue(key);
    }

    private static int snapBrightness(int percent) {
        int clamped = Math.max(MIN_BRIGHTNESS, Math.min(MAX_BRIGHTNESS, percent));
        int steps = Math.round((clamped - MIN_BRIGHTNESS) / (float) BRIGHTNESS_STEP);
        return Math.min(MAX_BRIGHTNESS, MIN_BRIGHTNESS + steps * BRIGHTNESS_STEP);
    }
}
