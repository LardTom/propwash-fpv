package de.lardtom.propwash.trick;

import java.util.List;

final class TrickWorld {
    private TrickWorld() {
    }

    static double cast(List<double[]> boxes, double x, double y, double z, double dx, double dy, double dz, double range) {
        double best = Double.POSITIVE_INFINITY;
        for (double[] b : boxes) {
            double t0 = 0.0;
            double t1 = range;
            double[] o = {x, y, z};
            double[] d = {dx, dy, dz};
            boolean hit = true;
            for (int a = 0; a < 3 && hit; a++) {
                double min = b[a];
                double max = b[a + 3];
                if (Math.abs(d[a]) < 1.0E-12) {
                    if (o[a] < min || o[a] > max) {
                        hit = false;
                    }
                    continue;
                }
                double ta = (min - o[a]) / d[a];
                double tb = (max - o[a]) / d[a];
                t0 = Math.max(t0, Math.min(ta, tb));
                t1 = Math.min(t1, Math.max(ta, tb));
                if (t0 > t1) {
                    hit = false;
                }
            }
            if (hit) {
                best = Math.min(best, t0);
            }
        }
        return best;
    }
}
