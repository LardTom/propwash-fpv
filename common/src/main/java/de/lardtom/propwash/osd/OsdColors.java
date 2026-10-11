package de.lardtom.propwash.osd;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

public final class OsdColors {
    public static final int INVALID = -1;
    public static final int RGB_MASK = 0x00FFFFFF;
    public static final int SHADE_RGB = 0x000000;

    private OsdColors() {
    }

    public static int parseRgb(@Nullable String text) {
        if (text == null) {
            return INVALID;
        }
        String hex = text.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 3 && hex.length() != 6) {
            return INVALID;
        }
        for (int i = 0; i < hex.length(); i++) {
            if (Character.digit(hex.charAt(i), 16) < 0) {
                return INVALID;
            }
        }
        if (hex.length() == 3) {
            StringBuilder wide = new StringBuilder(6);
            for (int i = 0; i < 3; i++) {
                wide.append(hex.charAt(i)).append(hex.charAt(i));
            }
            hex = wide.toString();
        }
        return Integer.parseInt(hex, 16) & RGB_MASK;
    }

    public static boolean isValid(@Nullable String text) {
        return parseRgb(text) != INVALID;
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & RGB_MASK);
    }

    public static String normalize(@Nullable String text, String fallback) {
        int rgb = parseRgb(text);
        return rgb == INVALID ? fallback : hex(rgb);
    }

    public static int rgbOr(@Nullable String text, int fallback) {
        int rgb = parseRgb(text);
        return rgb == INVALID ? fallback & RGB_MASK : rgb;
    }

    public static int alpha(int argb) {
        return argb >>> 24;
    }

    public static int alphaByte(float alpha) {
        if (!(alpha > 0.0F)) {
            return 0;
        }
        return Math.round(Math.min(alpha, 1.0F) * 255.0F);
    }

    public static int withAlpha(int rgb, float alpha) {
        return alphaByte(alpha) << 24 | rgb & RGB_MASK;
    }

    public static int scaleAlpha(int argb, float factor) {
        if (!(factor > 0.0F)) {
            return argb & RGB_MASK;
        }
        int alpha = Math.round(alpha(argb) * Math.min(factor, 1.0F));
        return alpha << 24 | argb & RGB_MASK;
    }

    public static int shade(int argb, float factor) {
        return scaleAlpha(0xFF000000 | SHADE_RGB, alpha(argb) / 255.0F * factor);
    }

    public static float fraction(int percent) {
        return Math.clamp(percent, 0, 100) / 100.0F;
    }
}
