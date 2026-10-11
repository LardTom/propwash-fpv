package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;

import java.util.Arrays;

public final class CameraOcclusion {
    public static final double DEFAULT_FOV_DEG = 120.0;
    public static final double ASPECT = FpvRig.ASPECT;
    public static final int ROWS = 1080;
    static final double MIN_DEPTH_MM = 0.25;

    private CameraOcclusion() {
    }

    public static double percent(DroneBuild build) {
        FpvRig rig = FpvRig.of(build);
        return percent(rig, rig.tiltDeg(), rig.horizontalFovDeg(), ASPECT, ROWS);
    }

    public static double percent(FpvRig rig, double tiltDeg, double horizontalFovDeg, double aspect, int rows) {
        double fov = Double.isFinite(horizontalFovDeg) ? SimMath.clamp(horizontalFovDeg, 1.0, 179.0) : DEFAULT_FOV_DEG;
        double ratio = Double.isFinite(aspect) && aspect > 1.0E-3 ? aspect : ASPECT;
        int count = Math.max(1, rows);
        double tanH = Math.tan(Math.toRadians(fov) / 2.0);
        double tanV = tanH / ratio;
        double tilt = Math.toRadians(Double.isFinite(tiltDeg) ? tiltDeg : 0.0);
        double cos = Math.cos(tilt);
        double sin = Math.sin(tilt);
        double[] lens = rig.lensMm();
        double[][] centers = new double[FpvRig.SLOTS][];
        for (int i = 0; i < FpvRig.SLOTS; i++) {
            double[] c = rig.discCenterMm(i);
            double dx = c[0] - lens[0];
            double dy = c[1] - lens[1];
            double dz = c[2] - lens[2];
            centers[i] = new double[]{dx, dy * cos + dz * sin, -dy * sin + dz * cos};
        }
        double ny = cos;
        double nz = -sin;
        double outer = rig.radiusMm();
        double inner = rig.hubRadiusMm();
        double[] from = new double[4 * FpvRig.SLOTS];
        double[] to = new double[4 * FpvRig.SLOTS];
        double covered = 0.0;
        for (int row = 0; row < count; row++) {
            double v = (1.0 - 2.0 * (row + 0.5) / count) * tanV;
            int n = 0;
            for (double[] c : centers) {
                n = rowIntervals(c, ny, nz, v, outer, inner, tanH, from, to, n);
            }
            covered += union(from, to, n) / (2.0 * tanH);
        }
        return 100.0 * covered / count;
    }

    private static int rowIntervals(double[] c, double ny, double nz, double v, double outer, double inner, double tanH,
                                    double[] from, double[] to, int n) {
        double nn = 1.0 + v * v;
        double nN = ny + v * nz;
        double det = nn - nN * nN;
        if (Math.abs(nz - v * ny) < 1.0E-12 || Math.abs(det) < 1.0E-15) {
            return n;
        }
        double nc = c[1] * ny + c[2] * nz;
        double a = -nN * nc / det;
        double b = nn * nc / det;
        double depth = -(a * v + b * nz);
        if (depth < MIN_DEPTH_MM) {
            return n;
        }
        double wy = a + b * ny - c[1];
        double wz = a * v + b * nz - c[2];
        double side = wy * wy + wz * wz;
        double outerChord = outer * outer - side;
        if (outerChord <= 0.0) {
            return n;
        }
        double half = Math.sqrt(outerChord);
        double innerChord = inner * inner - side;
        if (innerChord > 0.0) {
            double hole = Math.sqrt(innerChord);
            n = interval(c[0] - half, c[0] - hole, depth, tanH, from, to, n);
            return interval(c[0] + hole, c[0] + half, depth, tanH, from, to, n);
        }
        return interval(c[0] - half, c[0] + half, depth, tanH, from, to, n);
    }

    private static int interval(double x0, double x1, double depth, double tanH, double[] from, double[] to, int n) {
        double lo = Math.max(x0 / depth, -tanH);
        double hi = Math.min(x1 / depth, tanH);
        if (hi > lo) {
            from[n] = lo;
            to[n] = hi;
            return n + 1;
        }
        return n;
    }

    private static double union(double[] from, double[] to, int n) {
        if (n == 0) {
            return 0.0;
        }
        if (n == 1) {
            return to[0] - from[0];
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (x, y) -> Double.compare(from[x], from[y]));
        double total = 0.0;
        double lo = from[order[0]];
        double hi = to[order[0]];
        for (int k = 1; k < n; k++) {
            int i = order[k];
            if (from[i] > hi) {
                total += hi - lo;
                lo = from[i];
                hi = to[i];
            } else {
                hi = Math.max(hi, to[i]);
            }
        }
        return total + hi - lo;
    }
}
