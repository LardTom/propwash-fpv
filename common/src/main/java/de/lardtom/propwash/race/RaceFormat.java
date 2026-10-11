package de.lardtom.propwash.race;

public final class RaceFormat {
    public static final int DEFAULT_DECIMALS = 3;
    private static final int MAX_DECIMALS = 3;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private RaceFormat() {
    }

    public static String time(long nanos) {
        return time(nanos, DEFAULT_DECIMALS);
    }

    public static String delta(long nanos) {
        return delta(nanos, DEFAULT_DECIMALS);
    }

    public static String time(long nanos, int decimals) {
        int digits = clampDecimals(decimals);
        StringBuilder out = new StringBuilder(12);
        if (nanos < 0L) {
            out.append("--");
            if (digits > 0) {
                out.append('.').repeat('-', digits);
            }
            return out.toString();
        }
        long perSecond = pow10(digits);
        long ticks = roundedTicks(nanos, digits);
        long totalSeconds = ticks / perSecond;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes > 0L) {
            out.append(minutes).append(':');
            if (seconds < 10L) {
                out.append('0');
            }
        }
        out.append(seconds);
        appendFraction(out, ticks % perSecond, digits);
        return out.toString();
    }

    public static String delta(long nanos, int decimals) {
        int digits = clampDecimals(decimals);
        long perSecond = pow10(digits);
        long ticks = roundedTicks(Math.abs(nanos), digits);
        StringBuilder out = new StringBuilder(10);
        out.append(nanos < 0L ? '-' : '+').append(ticks / perSecond);
        appendFraction(out, ticks % perSecond, digits);
        return out.toString();
    }

    public static long roundedTicks(long nanos, int decimals) {
        long unit = NANOS_PER_SECOND / pow10(clampDecimals(decimals));
        return Math.floorDiv(nanos + unit / 2L, unit);
    }

    private static void appendFraction(StringBuilder out, long fraction, int digits) {
        if (digits == 0) {
            return;
        }
        out.append('.');
        for (long scale = pow10(digits - 1); scale > 0L; scale /= 10L) {
            out.append((char) ('0' + fraction / scale % 10L));
        }
    }

    private static int clampDecimals(int decimals) {
        return Math.clamp(decimals, 0, MAX_DECIMALS);
    }

    private static long pow10(int digits) {
        long value = 1L;
        for (int i = 0; i < digits; i++) {
            value *= 10L;
        }
        return value;
    }
}
