package de.lardtom.propwash.build;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public enum PaintSwatch {
    WHITE("white", 0xF9FFFE, "white", false),
    LIGHT_GRAY("light_gray", 0x9D9D97, "light_gray", false),
    GRAY("gray", 0x474F52, "gray", false),
    BLACK("black", 0x1D1D21, "black", false),
    BROWN("brown", 0x835432, "brown", false),
    RED("red", 0xB02E26, "red", false),
    ORANGE("orange", 0xF9801D, "orange", false),
    YELLOW("yellow", 0xFED83D, "yellow", false),
    LIME("lime", 0x80C71F, "lime", false),
    GREEN("green", 0x5E7C16, "green", false),
    CYAN("cyan", 0x169C9C, "cyan", false),
    LIGHT_BLUE("light_blue", 0x3AB3DA, "light_blue", false),
    BLUE("blue", 0x3C44AA, "blue", false),
    PURPLE("purple", 0x8932B8, "purple", false),
    MAGENTA("magenta", 0xC74EBD, "magenta", false),
    PINK("pink", 0xF38BAA, "pink", false),
    CARBON("carbon", 0x2A2C30, "black", true),
    GUNMETAL("gunmetal", 0x4E535B, "gray", true),
    ALUMINIUM("aluminium", 0xB8BEC6, "light_gray", true),
    GOLD("gold", 0xD8A93B, "yellow", true),
    COPPER("copper", 0xB8693A, "orange", true);

    private final String key;
    private final int rgb;
    private final String dye;
    private final boolean finish;

    PaintSwatch(String key, int rgb, String dye, boolean finish) {
        this.key = key;
        this.rgb = rgb;
        this.dye = dye;
        this.finish = finish;
    }

    public String key() {
        return this.key;
    }

    public int rgb() {
        return this.rgb;
    }

    public String dye() {
        return this.dye;
    }

    public boolean finish() {
        return this.finish;
    }

    public String translationKey() {
        return "propwash.paint.swatch." + this.key;
    }

    public static List<PaintSwatch> dyes() {
        List<PaintSwatch> out = new ArrayList<>();
        for (PaintSwatch swatch : values()) {
            if (!swatch.finish) {
                out.add(swatch);
            }
        }
        return out;
    }

    public static List<PaintSwatch> finishes() {
        List<PaintSwatch> out = new ArrayList<>();
        for (PaintSwatch swatch : values()) {
            if (swatch.finish) {
                out.add(swatch);
            }
        }
        return out;
    }

    public static Optional<PaintSwatch> byColor(int rgb) {
        int value = rgb & 0xFFFFFF;
        for (PaintSwatch swatch : values()) {
            if (swatch.rgb == value) {
                return Optional.of(swatch);
            }
        }
        return Optional.empty();
    }

    public static Optional<PaintSwatch> byDye(String dye) {
        for (PaintSwatch swatch : values()) {
            if (!swatch.finish && swatch.dye.equals(dye)) {
                return Optional.of(swatch);
            }
        }
        return Optional.empty();
    }

    public static PaintSwatch nearestDye(int rgb) {
        PaintSwatch best = WHITE;
        long bestDistance = Long.MAX_VALUE;
        for (PaintSwatch swatch : values()) {
            if (swatch.finish) {
                continue;
            }
            long distance = distance(rgb, swatch.rgb);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = swatch;
            }
        }
        return best;
    }

    public static long distance(int a, int b) {
        long dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        long dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        long db = (a & 0xFF) - (b & 0xFF);
        return 2 * dr * dr + 4 * dg * dg + 3 * db * db;
    }
}
