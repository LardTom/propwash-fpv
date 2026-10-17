package de.lardtom.propwash.video;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;

public final class LinkBudget {
    public static final double ANALOG_LOST_DB = -6.0;
    public static final double ANALOG_CLEAN_DB = 12.0;
    public static final double DIGITAL_LOST_DB = -5.0;
    public static final double DIGITAL_CLEAN_DB = 7.0;
    public static final double MAX_LOSS_DB = 60.0;

    private LinkBudget() {
    }

    public static double marginDb(double distance, double range) {
        if (Double.isInfinite(range)) {
            return Double.POSITIVE_INFINITY;
        }
        if (!(range > 0.0)) {
            return Double.NEGATIVE_INFINITY;
        }
        double d = Double.isNaN(distance) ? Double.POSITIVE_INFINITY : Math.max(distance, 1.0);
        return 20.0 * Math.log10(range / d);
    }

    public static double quality(VideoSystem system, double marginDb) {
        if (Double.isNaN(marginDb)) {
            return 0.0;
        }
        if (marginDb == Double.POSITIVE_INFINITY) {
            return 1.0;
        }
        return system == VideoSystem.DIGITAL
                ? smoothstep(DIGITAL_LOST_DB, DIGITAL_CLEAN_DB, marginDb)
                : smoothstep(ANALOG_LOST_DB, ANALOG_CLEAN_DB, marginDb);
    }

    public static double quality(VideoSystem system, double distance, double range, double obstructionDb) {
        return quality(system, marginDb(distance, range) - Math.max(0.0, obstructionDb));
    }

    public static double quality(VideoType type, double distance, double obstructionDb) {
        return quality(type.system, distance, type.rangeBlocks, obstructionDb);
    }

    public static double lostDb(VideoSystem system) {
        return system == VideoSystem.DIGITAL ? DIGITAL_LOST_DB : ANALOG_LOST_DB;
    }

    public static double maxReachBlocks(VideoSystem system, double range) {
        if (Double.isInfinite(range)) {
            return Double.POSITIVE_INFINITY;
        }
        return range * Math.pow(10.0, -lostDb(system) / 20.0);
    }

    public static double smoothstep(double edge0, double edge1, double x) {
        double t = (x - edge0) / (edge1 - edge0);
        if (t <= 0.0) {
            return 0.0;
        }
        if (t >= 1.0) {
            return 1.0;
        }
        return t * t * (3.0 - 2.0 * t);
    }
}
