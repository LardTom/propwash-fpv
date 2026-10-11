package de.lardtom.propwash.race;

import java.util.concurrent.atomic.AtomicLong;

public final class FreeformReach {
    public static final double MIN_REACH = 2.0;
    public static final double MAX_REACH = FreeformRegion.MAX_DIMENSION * 0.75 + 1.0;
    private static final AtomicLong REACH = new AtomicLong(Double.doubleToLongBits(MIN_REACH));

    private FreeformReach() {
    }

    public static double reach() {
        return Double.longBitsToDouble(REACH.get());
    }

    public static double of(FreeformRegion region) {
        return region.halfDiagonal() + 1.0;
    }

    public static void extend(double reach) {
        if (!Double.isFinite(reach)) {
            return;
        }
        double wanted = Math.min(MAX_REACH, reach);
        while (true) {
            long current = REACH.get();
            if (Double.longBitsToDouble(current) >= wanted || REACH.compareAndSet(current, Double.doubleToLongBits(wanted))) {
                return;
            }
        }
    }
}
