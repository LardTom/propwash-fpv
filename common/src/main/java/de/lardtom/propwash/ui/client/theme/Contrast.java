package de.lardtom.propwash.ui.client.theme;

public final class Contrast {
    public static final double READABLE = 4.5;
    public static final double LARGE_READABLE = 3.0;

    private Contrast() {
    }

    public static double luminance(int rgb) {
        return 0.2126 * channel((rgb >> 16) & 0xFF) + 0.7152 * channel((rgb >> 8) & 0xFF) + 0.0722 * channel(rgb & 0xFF);
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    public static int over(int foreground, int background) {
        int a = Palette.alpha(foreground);
        if (a == 0xFF) {
            return foreground;
        }
        return Palette.mix(background | 0xFF000000, foreground | 0xFF000000, a / 255.0F);
    }

    public static double ratio(int foreground, int background) {
        int bg = background | 0xFF000000;
        int fg = over(foreground, bg);
        double lf = luminance(fg);
        double lb = luminance(bg);
        return (Math.max(lf, lb) + 0.05) / (Math.min(lf, lb) + 0.05);
    }

    public static boolean readable(int foreground, int background) {
        return ratio(foreground, background) >= READABLE;
    }

    public static int readableOn(int background) {
        return ratio(Palette.TEXT, background) >= ratio(Palette.ACCENT_TEXT, background) ? Palette.TEXT : Palette.ACCENT_TEXT;
    }
}
