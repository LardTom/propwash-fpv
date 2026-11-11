package de.lardtom.propwash.gpucompat;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

public enum GpuCompatMode {
    AUTO("auto"),
    ON("on"),
    OFF("off");

    private final String key;

    GpuCompatMode(String key) {
        this.key = key;
    }

    public String key() {
        return this.key;
    }

    public String translationKey() {
        return "propwash.gpucompat.mode." + this.key;
    }

    public String tooltipKey() {
        return this.translationKey() + ".tooltip";
    }

    public static @Nullable GpuCompatMode parse(@Nullable String text) {
        if (text == null) {
            return null;
        }
        return switch (text.strip().toLowerCase(Locale.ROOT)) {
            case "auto", "automatic", "automatisch" -> AUTO;
            case "on", "an", "true", "1", "yes", "ja" -> ON;
            case "off", "aus", "false", "0", "no", "nein" -> OFF;
            default -> null;
        };
    }
}
