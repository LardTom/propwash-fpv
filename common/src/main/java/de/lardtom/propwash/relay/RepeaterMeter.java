package de.lardtom.propwash.relay;

public final class RepeaterMeter {
    public static final int BARS = 4;
    public static final double NOTHING = 0.02;
    public static final int GREEN = 0xFF3BE05A;
    public static final int YELLOW = 0xFFFFD23A;
    public static final int RED = 0xFFFF4A3A;
    public static final int DARK = 0xFF2A2D33;
    private static volatile float shown = -1.0F;

    private RepeaterMeter() {
    }

    public static void show(double quality) {
        shown = (float) Math.clamp(quality, 0.0, 1.0);
    }

    public static void hide() {
        shown = -1.0F;
    }

    public static boolean active() {
        return shown >= 0.0F;
    }

    public static float quality() {
        return Math.max(0.0F, shown);
    }

    public static int bars(double quality) {
        if (!(quality > NOTHING)) {
            return 0;
        }
        return Math.clamp((int) Math.ceil(quality * BARS - 1.0E-9), 1, BARS);
    }

    public static int color(int bars) {
        return bars >= 3 ? GREEN : bars == 2 ? YELLOW : bars == 1 ? RED : DARK;
    }
}
