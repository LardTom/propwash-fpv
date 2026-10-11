package de.lardtom.propwash.osd;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public enum OsdPalette {
    WHITE(0xFFFFFF),
    GREEN(0x6CFF6C),
    YELLOW(0xFFF35C),
    CYAN(0x5CF2FF),
    ORANGE(0xFFA64D),
    MAGENTA(0xFF7AE0),
    RED(0xFF4A4A);

    public static final List<OsdPalette> PRIMARY = List.of(WHITE, GREEN, YELLOW, CYAN, ORANGE, MAGENTA);
    public static final List<OsdPalette> WARNING = List.of(RED, ORANGE, YELLOW, MAGENTA, WHITE);

    private final int rgb;
    private final String hex;
    private final String key;

    OsdPalette(int rgb) {
        this.rgb = rgb;
        this.hex = OsdColors.hex(rgb);
        this.key = this.name().toLowerCase(Locale.ROOT);
    }

    public int rgb() {
        return this.rgb;
    }

    public String hex() {
        return this.hex;
    }

    public String key() {
        return this.key;
    }

    public String translationKey() {
        return "propwash.osd.color." + this.key;
    }

    public static @Nullable OsdPalette byHex(@Nullable String hex) {
        int rgb = OsdColors.parseRgb(hex);
        if (rgb == OsdColors.INVALID) {
            return null;
        }
        for (OsdPalette entry : values()) {
            if (entry.rgb == rgb) {
                return entry;
            }
        }
        return null;
    }

    public static List<String> hexes(List<OsdPalette> entries) {
        return entries.stream().map(OsdPalette::hex).toList();
    }
}
