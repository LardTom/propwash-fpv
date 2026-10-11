package de.lardtom.propwash.drone.led;

public enum LedMode {
    STATIC("static"),
    BLINK("blink"),
    ARMED("armed"),
    LOW_BATTERY("low_battery"),
    RAINBOW("rainbow");

    private final String key;

    LedMode(String key) {
        this.key = key;
    }

    public String key() {
        return this.key;
    }

    public String translationKey() {
        return "propwash.led.mode." + this.key;
    }

    public static LedMode byIndex(double raw) {
        LedMode[] values = values();
        if (!Double.isFinite(raw)) {
            return values[0];
        }
        long index = Math.round(raw);
        return values[(int) Math.max(0, Math.min(values.length - 1, index))];
    }
}
