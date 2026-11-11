package de.lardtom.propwash.stats;

import net.minecraft.stats.StatFormatter;

import java.util.Locale;

public final class StatFormats {
    public static final double KMH_PER_MS = 3.6;
    public static final StatFormatter SPEED = StatFormats::speed;

    private StatFormats() {
    }

    public static String speed(int tenthsKmh) {
        return String.format(Locale.ROOT, "%.1f km/h", tenthsKmh / 10.0);
    }

    public static int tenthsKmh(double metersPerSecond) {
        if (!Double.isFinite(metersPerSecond) || metersPerSecond <= 0.0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.round(metersPerSecond * KMH_PER_MS * 10.0));
    }
}
