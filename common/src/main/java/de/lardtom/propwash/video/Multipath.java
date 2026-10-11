package de.lardtom.propwash.video;

import java.util.Arrays;

public final class Multipath {
    public static final double RANGE_BLOCKS = 6.0;
    public static final double MIN_REFLECTIVITY = 0.1;
    private static final double STEP_BLOCKS = 0.5;
    private static final double DIAGONAL = Math.sqrt(0.5);
    private static final double[][] DIRECTIONS = {
            {1.0, 0.0, 0.0}, {-1.0, 0.0, 0.0}, {0.0, 0.0, 1.0}, {0.0, 0.0, -1.0}, {0.0, 1.0, 0.0}, {0.0, -1.0, 0.0},
            {DIAGONAL, 0.0, DIAGONAL}, {DIAGONAL, 0.0, -DIAGONAL}, {-DIAGONAL, 0.0, DIAGONAL}, {-DIAGONAL, 0.0, -DIAGONAL}};
    private static final double[] WEIGHTS = {1.0, 1.0, 1.0, 1.0, 1.0, 0.35, 0.7, 0.7, 0.7, 0.7};
    private static final double[] FALLOFF = {1.0, 0.7, 0.5, 0.35, 0.25, 0.18, 0.13, 0.1, 0.07, 0.05};
    private static final double NORMALIZE = 1.5;
    private static final double PARTIAL_SPREAD_DB = 18.0;
    private static final double PARTIAL_SHADOW_DB = 25.0;

    private Multipath() {
    }

    public static double reflectors(double x, double y, double z, Reflectivity blocks) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return 0.0;
        }
        double[] scores = new double[DIRECTIONS.length];
        for (int i = 0; i < DIRECTIONS.length; i++) {
            double[] d = DIRECTIONS[i];
            scores[i] = WEIGHTS[i] * along(x, y, z, d[0], d[1], d[2], blocks);
        }
        Arrays.sort(scores);
        double total = 0.0;
        for (int i = 0; i < scores.length; i++) {
            total += scores[scores.length - 1 - i] * FALLOFF[i];
        }
        return clamp01(total / NORMALIZE);
    }

    public static double partial(double[] lossesDb, int count) {
        int n = Math.min(count, lossesDb.length);
        if (n < 2) {
            return 0.0;
        }
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            double loss = lossesDb[i];
            if (!Double.isFinite(loss)) {
                continue;
            }
            min = Math.min(min, loss);
            max = Math.max(max, loss);
        }
        if (!(max > min)) {
            return 0.0;
        }
        double spread = clamp01((max - min) / PARTIAL_SPREAD_DB);
        double open = clamp01(1.0 - Math.max(min, 0.0) / PARTIAL_SHADOW_DB);
        return spread * open;
    }

    public static double factor(double droneSide, double pilotSide, double partial) {
        double reflect = 1.0 - (1.0 - clamp01(droneSide)) * (1.0 - 0.5 * clamp01(pilotSide));
        return clamp01(0.85 * reflect + 0.6 * clamp01(partial));
    }

    private static double along(double x, double y, double z, double dx, double dy, double dz, Reflectivity blocks) {
        for (double d = STEP_BLOCKS; d <= RANGE_BLOCKS + 1.0E-9; d += STEP_BLOCKS) {
            double reflectivity = Math.min(1.0, blocks.reflectivity(floor(x + dx * d), floor(y + dy * d), floor(z + dz * d)));
            if (reflectivity > 0.0) {
                if (reflectivity < MIN_REFLECTIVITY) {
                    continue;
                }
                double near = 1.0 - d / RANGE_BLOCKS;
                return reflectivity * near * near;
            }
        }
        return 0.0;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.min(Math.max(value, 0.0), 1.0);
    }

    @FunctionalInterface
    public interface Reflectivity {
        double reflectivity(int x, int y, int z);
    }
}
