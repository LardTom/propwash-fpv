package de.lardtom.propwash.video.record;

public final class Backpressure {
    public static final double HALF_RATE = 0.6;
    public static final double THIRD_RATE = 0.8;
    public static final double QUARTER_RATE = 0.9;
    private static final double MEMORY_ONLY_START = 0.5;

    private Backpressure() {
    }

    public static int stride(double fill, boolean memoryOnly) {
        double start = memoryOnly ? MEMORY_ONLY_START : HALF_RATE;
        if (!(fill >= start)) {
            return 1;
        }
        double span = 1.0 - start;
        double inside = (fill - start) / span;
        if (inside < (THIRD_RATE - HALF_RATE) / (1.0 - HALF_RATE)) {
            return 2;
        }
        if (inside < (QUARTER_RATE - HALF_RATE) / (1.0 - HALF_RATE)) {
            return 3;
        }
        return 4;
    }

    public static boolean keep(long slot, int stride) {
        return stride <= 1 || Math.floorMod(slot, stride) == 0;
    }
}
