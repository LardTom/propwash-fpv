package de.lardtom.propwash.server;

import java.util.Arrays;

public final class EdgeFence {
    public static final double STOP_DISTANCE = 4.0;
    public static final double BRAKE = 20.0;
    public static final double WARN_DISTANCE = 32.0;
    public static final double RETURN_RATE = 4.0;
    public static final double RETURN_MAX = 3.0;
    public static final EdgeFence NONE = new EdgeFence(new double[0], 0);
    private static final int CHUNK = 16;
    private static final int STRIDE = 6;
    private static final int PASSES = 2;
    private static final double EPSILON = 1.0E-9;

    private final double[] segments;
    private final int count;

    private EdgeFence(double[] segments, int count) {
        this.segments = segments;
        this.count = count;
    }

    public static EdgeFence of(EdgeMask mask) {
        if (!mask.any()) {
            return NONE;
        }
        double[] out = new double[64 * STRIDE];
        int[] count = {0};
        int r = EdgeMask.RADIUS;
        for (int dz = -r; dz <= r; dz++) {
            out = runs(mask, out, count, dz, true);
        }
        for (int dx = -r; dx <= r; dx++) {
            out = runs(mask, out, count, dx, false);
        }
        return new EdgeFence(Arrays.copyOf(out, count[0] * STRIDE), count[0]);
    }

    private static double[] runs(EdgeMask mask, double[] out, int[] count, int line, boolean alongX) {
        int r = EdgeMask.RADIUS;
        for (int side = -1; side <= 1; side += 2) {
            int start = Integer.MIN_VALUE;
            for (int step = -r; step <= r + 1; step++) {
                boolean exposed = false;
                if (step <= r) {
                    int cx = alongX ? mask.centerX() + step : mask.centerX() + line;
                    int cz = alongX ? mask.centerZ() + line : mask.centerZ() + step;
                    int nx = alongX ? cx : cx + side;
                    int nz = alongX ? cz + side : cz;
                    exposed = mask.blocked(cx, cz) && !mask.blocked(nx, nz);
                }
                if (exposed && start == Integer.MIN_VALUE) {
                    start = step;
                } else if (!exposed && start != Integer.MIN_VALUE) {
                    out = add(out, count, mask, line, start, step, side, alongX);
                    start = Integer.MIN_VALUE;
                }
            }
        }
        return out;
    }

    private static double[] add(double[] out, int[] count, EdgeMask mask, int line, int from, int to, int side, boolean alongX) {
        if ((count[0] + 1) * STRIDE > out.length) {
            out = Arrays.copyOf(out, out.length * 2);
        }
        int base = count[0] * STRIDE;
        if (alongX) {
            int chunkZ = mask.centerZ() + line;
            double z = side < 0 ? (double) chunkZ * CHUNK : (double) (chunkZ + 1) * CHUNK;
            out[base] = (double) (mask.centerX() + from) * CHUNK;
            out[base + 1] = z;
            out[base + 2] = (double) (mask.centerX() + to) * CHUNK;
            out[base + 3] = z;
            out[base + 4] = 0.0;
            out[base + 5] = -side;
        } else {
            int chunkX = mask.centerX() + line;
            double x = side < 0 ? (double) chunkX * CHUNK : (double) (chunkX + 1) * CHUNK;
            out[base] = x;
            out[base + 1] = (double) (mask.centerZ() + from) * CHUNK;
            out[base + 2] = x;
            out[base + 3] = (double) (mask.centerZ() + to) * CHUNK;
            out[base + 4] = -side;
            out[base + 5] = 0.0;
        }
        count[0]++;
        return out;
    }

    public boolean empty() {
        return this.count == 0;
    }

    public int segments() {
        return this.count;
    }

    public static double allowedApproach(double distance) {
        if (Double.isNaN(distance)) {
            return 0.0;
        }
        if (distance < STOP_DISTANCE) {
            return -Math.min(RETURN_MAX, RETURN_RATE * (STOP_DISTANCE - distance));
        }
        return Math.sqrt(2.0 * BRAKE * (distance - STOP_DISTANCE));
    }

    public double distance(double x, double z) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < this.count; i++) {
            double d = this.segmentDistance(i, x, z);
            if (d < best) {
                best = d;
            }
        }
        return best;
    }

    private double segmentDistance(int i, double x, double z) {
        int b = i * STRIDE;
        double[] s = this.segments;
        double offset = (s[b] - x) * s[b + 4] + (s[b + 1] - z) * s[b + 5];
        if (offset < 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        double px = Math.max(Math.min(s[b], s[b + 2]), Math.min(Math.max(s[b], s[b + 2]), x));
        double pz = Math.max(Math.min(s[b + 1], s[b + 3]), Math.min(Math.max(s[b + 1], s[b + 3]), z));
        return Math.hypot(px - x, pz - z);
    }

    public boolean limit(double x, double z, double[] velocity) {
        if (this.count == 0 || !Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(velocity[0]) || !Double.isFinite(velocity[1])) {
            return false;
        }
        boolean limited = false;
        double[] s = this.segments;
        for (int pass = 0; pass < PASSES; pass++) {
            for (int i = 0; i < this.count; i++) {
                int b = i * STRIDE;
                double offset = (s[b] - x) * s[b + 4] + (s[b + 1] - z) * s[b + 5];
                if (offset < 0.0) {
                    continue;
                }
                double px = Math.max(Math.min(s[b], s[b + 2]), Math.min(Math.max(s[b], s[b + 2]), x));
                double pz = Math.max(Math.min(s[b + 1], s[b + 3]), Math.min(Math.max(s[b + 1], s[b + 3]), z));
                double dx = px - x;
                double dz = pz - z;
                double d = Math.hypot(dx, dz);
                double nx;
                double nz;
                if (d < EPSILON) {
                    nx = s[b + 4];
                    nz = s[b + 5];
                } else {
                    nx = dx / d;
                    nz = dz / d;
                }
                double approach = velocity[0] * nx + velocity[1] * nz;
                double allowed = allowedApproach(d);
                if (approach > allowed) {
                    double cut = approach - allowed;
                    velocity[0] -= cut * nx;
                    velocity[1] -= cut * nz;
                    limited = true;
                }
            }
        }
        return limited;
    }
}
