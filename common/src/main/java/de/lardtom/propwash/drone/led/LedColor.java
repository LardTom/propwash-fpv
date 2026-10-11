package de.lardtom.propwash.drone.led;

public enum LedColor {
    WHITE("white", 0xFFFFFF),
    WARM_WHITE("warm_white", 0xFFD49E),
    RED("red", 0xFF2A1F),
    ORANGE("orange", 0xFF8A10),
    YELLOW("yellow", 0xFFE02A),
    GREEN("green", 0x3CFF4A),
    CYAN("cyan", 0x2AF0FF),
    BLUE("blue", 0x3A6BFF),
    VIOLET("violet", 0x9A4DFF),
    MAGENTA("magenta", 0xFF3ADF),
    PINK("pink", 0xFF7FB0);

    private final String key;
    private final int rgb;

    LedColor(String key, int rgb) {
        this.key = key;
        this.rgb = rgb;
    }

    public String key() {
        return this.key;
    }

    public int rgb() {
        return this.rgb;
    }

    public String translationKey() {
        return "propwash.led.color." + this.key;
    }

    public static LedColor byIndex(double raw) {
        LedColor[] values = values();
        if (!Double.isFinite(raw)) {
            return values[0];
        }
        long index = Math.round(raw);
        return values[(int) Math.max(0, Math.min(values.length - 1, index))];
    }
}
