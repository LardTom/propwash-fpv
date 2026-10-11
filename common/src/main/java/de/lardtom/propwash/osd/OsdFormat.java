package de.lardtom.propwash.osd;

import de.lardtom.propwash.race.RaceFormat;
import de.lardtom.propwash.ui.SpeedUnit;

public final class OsdFormat {
    public static final String UNKNOWN = "--";
    public static final long NONE = Long.MIN_VALUE;
    public static final int LAP_DECIMALS = 2;
    private static final long NANOS_PER_LAP_TICK = 10_000_000L;
    private static final double ALTITUDE_DECIMAL_LIMIT = 100.0;
    private static final double LONG_DISTANCE_METERS = 1000.0;
    private static final double LONG_DISTANCE_FEET = 10_000.0;
    private static final double VARIO_DEADBAND = 0.05;
    private static final long[] POWERS = {1L, 10L, 100L, 1000L, 10_000L};
    private static final int DISTANCE_METERS = 0;
    private static final int DISTANCE_KILOMETERS = 1;
    private static final int DISTANCE_FEET = 2;
    private static final int DISTANCE_MILES = 3;
    private static final int ALTITUDE_DECIMETERS = 0;
    private static final int ALTITUDE_METERS = 1;
    private static final int ALTITUDE_FEET = 2;
    private static final int VARIO_LEVEL = 0;
    private static final int VARIO_UP = 1;
    private static final int VARIO_DOWN = 2;
    private static final int KEY_FLAGS = 8;

    private OsdFormat() {
    }

    public static long quantize(double value, int digits) {
        if (!Double.isFinite(value)) {
            return NONE;
        }
        double scaled = Math.floor(Math.abs(value) * power(digits) + 0.5);
        if (scaled >= Long.MAX_VALUE / KEY_FLAGS) {
            return NONE;
        }
        long magnitude = (long) scaled;
        return value < 0.0 ? -magnitude : magnitude;
    }

    public static String fixed(long quantized, int digits) {
        if (quantized == NONE) {
            return UNKNOWN;
        }
        return appendFixed(new StringBuilder(12), quantized, digits).toString();
    }

    public static StringBuilder appendFixed(StringBuilder out, long quantized, int digits) {
        if (quantized == NONE) {
            return out.append(UNKNOWN);
        }
        int d = clampDigits(digits);
        long factor = POWERS[d];
        if (quantized < 0L) {
            out.append('-');
        }
        long magnitude = Math.abs(quantized);
        out.append(magnitude / factor);
        if (d > 0) {
            out.append('.');
            long fraction = magnitude % factor;
            for (long scale = factor / 10L; scale > 0L; scale /= 10L) {
                out.append((char) ('0' + fraction / scale % 10L));
            }
        }
        return out;
    }

    public static String decimal(double value, int digits) {
        return fixed(quantize(value, digits), digits);
    }

    public static String integer(double value) {
        return fixed(quantize(value, 0), 0);
    }

    public static long durationKey(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0.0 || seconds >= Long.MAX_VALUE / 2.0) {
            return NONE;
        }
        return (long) Math.floor(seconds);
    }

    public static String durationText(long totalSeconds) {
        if (totalSeconds == NONE || totalSeconds < 0L) {
            return "--:--";
        }
        long minutes = totalSeconds / 60L;
        long secs = totalSeconds % 60L;
        StringBuilder out = new StringBuilder(8);
        if (minutes >= 100L) {
            out.append(minutes / 60L).append(':');
            appendTwoDigits(out, minutes % 60L);
        } else {
            appendTwoDigits(out, minutes);
        }
        out.append(':');
        appendTwoDigits(out, secs);
        return out.toString();
    }

    public static String recordingText(long totalSeconds) {
        if (totalSeconds == NONE || totalSeconds < 0L) {
            return "-:--";
        }
        long hours = totalSeconds / 3600L;
        long minutes = totalSeconds / 60L % 60L;
        long secs = totalSeconds % 60L;
        StringBuilder out = new StringBuilder(8);
        if (hours > 0L) {
            out.append(hours).append(':');
            appendTwoDigits(out, minutes);
        } else {
            out.append(minutes);
        }
        out.append(':');
        appendTwoDigits(out, secs);
        return out.toString();
    }

    public static String duration(double seconds) {
        return durationText(durationKey(seconds));
    }

    public static long lapKey(long nanos) {
        return nanos < 0L ? NONE : RaceFormat.roundedTicks(nanos, LAP_DECIMALS);
    }

    public static String lapText(long key) {
        return RaceFormat.time(key == NONE ? -1L : key * NANOS_PER_LAP_TICK, LAP_DECIMALS);
    }

    public static long deltaKey(long nanos) {
        long ticks = RaceFormat.roundedTicks(Math.abs(nanos), LAP_DECIMALS);
        return nanos < 0L ? ~ticks : ticks;
    }

    public static String deltaText(long key) {
        long nanos = key < 0L ? -(~key * NANOS_PER_LAP_TICK) - 1L : key * NANOS_PER_LAP_TICK;
        return RaceFormat.delta(nanos, LAP_DECIMALS);
    }

    public static long clockKey(int hour, int minute) {
        return Math.floorMod(hour, 24) * 60L + Math.floorMod(minute, 60);
    }

    public static String clockText(long key) {
        StringBuilder out = new StringBuilder(5);
        appendTwoDigits(out, Math.floorMod(key, 1440L) / 60L);
        out.append(':');
        appendTwoDigits(out, Math.floorMod(key, 60L));
        return out.toString();
    }

    public static String clock(int hour, int minute) {
        return clockText(clockKey(hour, minute));
    }

    public static double wrap360(double degrees) {
        if (!Double.isFinite(degrees)) {
            return degrees;
        }
        double wrapped = degrees % 360.0;
        if (wrapped < 0.0) {
            wrapped += 360.0;
        }
        return wrapped >= 360.0 ? 0.0 : wrapped;
    }

    public static double wrap180(double degrees) {
        double wrapped = wrap360(degrees);
        return wrapped > 180.0 ? wrapped - 360.0 : wrapped;
    }

    public static double compassFromSimHeading(double simHeadingDeg) {
        return wrap360(180.0 - simHeadingDeg);
    }

    public static double compassFromYRot(double yRotDeg) {
        return wrap360(yRotDeg + 180.0);
    }

    public static double bearing(double dx, double dz) {
        if (dx == 0.0 && dz == 0.0) {
            return 0.0;
        }
        return wrap360(Math.toDegrees(Math.atan2(dx, -dz)));
    }

    public static long headingKey(double compassDeg) {
        if (!Double.isFinite(compassDeg)) {
            return NONE;
        }
        return Math.round(wrap360(compassDeg)) % 360L;
    }

    public static String headingText(long key) {
        if (key == NONE) {
            return "---";
        }
        StringBuilder out = new StringBuilder(3);
        if (key < 100L) {
            out.append('0');
        }
        if (key < 10L) {
            out.append('0');
        }
        return out.append(key).toString();
    }

    public static String heading(double compassDeg) {
        return headingText(headingKey(compassDeg));
    }

    public static int signedPercent(double fraction) {
        if (!Double.isFinite(fraction)) {
            return 0;
        }
        return (int) Math.round(Math.clamp(fraction, -1.0, 1.0) * 100.0);
    }

    public static String signedPercentText(long percent) {
        return percent > 0 ? "+" + percent : Long.toString(percent);
    }

    public static int percent(double fraction) {
        if (!Double.isFinite(fraction)) {
            return 0;
        }
        return (int) Math.round(Math.clamp(fraction, 0.0, 1.0) * 100.0);
    }

    public static int rssiDbm(double linkStrength) {
        double lq = Double.isFinite(linkStrength) ? Math.clamp(linkStrength, 0.0, 1.0) : 0.0;
        return (int) Math.round(-115.0 + 65.0 * Math.sqrt(lq));
    }

    public static double speed(double metersPerSecond, SpeedUnit unit) {
        return unit.fromMetersPerSecond(metersPerSecond);
    }

    public static String speedUnit(SpeedUnit unit) {
        return unit.imperial() ? "MPH" : "KM/H";
    }

    public static long distanceKey(double meters, SpeedUnit unit) {
        if (!Double.isFinite(meters)) {
            return NONE;
        }
        if (unit.imperial()) {
            double feet = unit.lengthFromMeters(meters);
            return Math.abs(feet) >= LONG_DISTANCE_FEET
                    ? withFlag(quantize(unit.longDistanceFromMeters(meters), 1), DISTANCE_MILES)
                    : withFlag(quantize(feet, 0), DISTANCE_FEET);
        }
        return Math.abs(meters) >= LONG_DISTANCE_METERS
                ? withFlag(quantize(unit.longDistanceFromMeters(meters), 1), DISTANCE_KILOMETERS)
                : withFlag(quantize(meters, 0), DISTANCE_METERS);
    }

    public static String distanceText(long key) {
        if (key == NONE) {
            return UNKNOWN;
        }
        long value = valueOf(key);
        return switch (flagOf(key)) {
            case DISTANCE_KILOMETERS -> appendFixed(new StringBuilder(10), value, 1).append("KM").toString();
            case DISTANCE_FEET -> appendFixed(new StringBuilder(10), value, 0).append("FT").toString();
            case DISTANCE_MILES -> appendFixed(new StringBuilder(10), value, 1).append("MI").toString();
            default -> appendFixed(new StringBuilder(10), value, 0).append('M').toString();
        };
    }

    public static String distance(double meters, SpeedUnit unit) {
        return distanceText(distanceKey(meters, unit));
    }

    public static long altitudeKey(double meters, SpeedUnit unit) {
        if (!Double.isFinite(meters)) {
            return NONE;
        }
        if (unit.imperial()) {
            return withFlag(quantize(unit.lengthFromMeters(meters), 0), ALTITUDE_FEET);
        }
        return Math.abs(meters) < ALTITUDE_DECIMAL_LIMIT
                ? withFlag(quantize(meters, 1), ALTITUDE_DECIMETERS)
                : withFlag(quantize(meters, 0), ALTITUDE_METERS);
    }

    public static String altitudeText(long key) {
        if (key == NONE) {
            return UNKNOWN;
        }
        long value = valueOf(key);
        return switch (flagOf(key)) {
            case ALTITUDE_DECIMETERS -> appendFixed(new StringBuilder(10), value, 1).append('M').toString();
            case ALTITUDE_FEET -> appendFixed(new StringBuilder(10), value, 0).append("FT").toString();
            default -> appendFixed(new StringBuilder(10), value, 0).append('M').toString();
        };
    }

    public static String altitude(double meters, SpeedUnit unit) {
        return altitudeText(altitudeKey(meters, unit));
    }

    public static long varioKey(double metersPerSecond, SpeedUnit unit) {
        if (!Double.isFinite(metersPerSecond)) {
            return NONE;
        }
        double value = unit.lengthFromMeters(metersPerSecond);
        int direction = value > VARIO_DEADBAND ? VARIO_UP : value < -VARIO_DEADBAND ? VARIO_DOWN : VARIO_LEVEL;
        return withFlag(quantize(Math.abs(value), 1), direction * 2 + (unit.imperial() ? 1 : 0));
    }

    public static String varioText(long key) {
        if (key == NONE) {
            return UNKNOWN;
        }
        int flag = flagOf(key);
        int direction = flag / 2;
        StringBuilder out = new StringBuilder(10);
        out.append(direction == VARIO_UP ? '↑' : direction == VARIO_DOWN ? '↓' : ' ');
        appendFixed(out, valueOf(key), 1);
        return out.append((flag & 1) != 0 ? "F/S" : "M/S").toString();
    }

    public static String vario(double metersPerSecond, SpeedUnit unit) {
        return varioText(varioKey(metersPerSecond, unit));
    }

    public static long angleKey(double degrees) {
        return Double.isFinite(degrees) ? Math.round(degrees) : NONE;
    }

    public static String signedAngleText(long key) {
        if (key == NONE) {
            return UNKNOWN;
        }
        StringBuilder out = new StringBuilder(5);
        out.append(key > 0L ? '+' : key < 0L ? '-' : ' ');
        appendTwoDigits(out, Math.abs(key));
        return out.toString();
    }

    public static String signedAngle(double degrees) {
        return signedAngleText(angleKey(degrees));
    }

    public static String coordinates(double x, double y, double z) {
        return new StringBuilder(24).append(Math.round(x)).append(' ').append(Math.round(y)).append(' ').append(Math.round(z)).toString();
    }

    public static int batteryBarSegments(double chargeFraction, int segments) {
        if (!Double.isFinite(chargeFraction) || segments <= 0) {
            return 0;
        }
        return (int) Math.ceil(Math.clamp(chargeFraction, 0.0, 1.0) * segments - 1.0E-9);
    }

    public static StringBuilder appendTwoDigits(StringBuilder out, long value) {
        if (value >= 0L && value < 10L) {
            out.append('0');
        }
        return out.append(value);
    }

    private static long withFlag(long quantized, int flag) {
        return quantized == NONE ? NONE : quantized * KEY_FLAGS + flag;
    }

    private static long valueOf(long key) {
        return Math.floorDiv(key, KEY_FLAGS);
    }

    private static int flagOf(long key) {
        return (int) Math.floorMod(key, KEY_FLAGS);
    }

    private static double power(int digits) {
        return POWERS[clampDigits(digits)];
    }

    private static int clampDigits(int digits) {
        return Math.clamp(digits, 0, POWERS.length - 1);
    }
}
