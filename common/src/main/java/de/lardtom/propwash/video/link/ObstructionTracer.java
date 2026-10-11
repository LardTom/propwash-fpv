package de.lardtom.propwash.video.link;

public final class ObstructionTracer {
    public static final int MAX_STEPS = 4096;
    private static final double MIN_METERS = 1.0E-6;

    private ObstructionTracer() {
    }

    public static double traceDb(double x0, double y0, double z0, double x1, double y1, double z1, World world, double capDb) {
        RayLoss loss = new RayLoss();
        segment(x0, y0, z0, x1, y1, z1, world, loss, capDb);
        return loss.totalDb(capDb);
    }

    public static void trace(double[] points, int count, World world, RayLoss out, double capDb) {
        for (int i = 0; i + 1 < count; i++) {
            int a = 3 * i;
            int b = a + 3;
            segment(points[a], points[a + 1], points[a + 2], points[b], points[b + 1], points[b + 2], world, out, capDb);
            if (out.reached(capDb)) {
                return;
            }
        }
    }

    static void segment(double x0, double y0, double z0, double x1, double y1, double z1, World world, RayLoss out, double capDb) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(length > 1.0E-9) || !Double.isFinite(length) || !Double.isFinite(x0) || !Double.isFinite(y0) || !Double.isFinite(z0)) {
            return;
        }
        int x = floor(x0);
        int y = floor(y0);
        int z = floor(z0);
        int stepX = dx > 0.0 ? 1 : (dx < 0.0 ? -1 : 0);
        int stepY = dy > 0.0 ? 1 : (dy < 0.0 ? -1 : 0);
        int stepZ = dz > 0.0 ? 1 : (dz < 0.0 ? -1 : 0);
        double deltaX = stepX != 0 ? Math.abs(1.0 / dx) : Double.POSITIVE_INFINITY;
        double deltaY = stepY != 0 ? Math.abs(1.0 / dy) : Double.POSITIVE_INFINITY;
        double deltaZ = stepZ != 0 ? Math.abs(1.0 / dz) : Double.POSITIVE_INFINITY;
        double nextX = boundary(x0, x, stepX, dx);
        double nextY = boundary(y0, y, stepY, dy);
        double nextZ = boundary(z0, z, stepZ, dz);
        double t = 0.0;
        for (int step = 0; step < MAX_STEPS; step++) {
            double next = Math.min(Math.min(nextX, nextY), Math.min(nextZ, 1.0));
            if (next > t) {
                out.countCell();
                Medium medium = world.medium(x, y, z);
                if (medium.empty()) {
                    out.breakRun();
                } else {
                    double[] boxes = medium.boxes();
                    double meters = boxes == null ? (next - t) * length
                            : insideBoxes(boxes, x, y, z, x0, y0, z0, dx, dy, dz, t, next) * length;
                    if (meters > MIN_METERS) {
                        out.add(medium.material(), meters);
                        if (out.reached(capDb)) {
                            return;
                        }
                    } else {
                        out.breakRun();
                    }
                }
            }
            if (next >= 1.0) {
                return;
            }
            t = next;
            if (nextX <= nextY && nextX <= nextZ) {
                x += stepX;
                nextX += deltaX;
            } else if (nextY <= nextZ) {
                y += stepY;
                nextY += deltaY;
            } else {
                z += stepZ;
                nextZ += deltaZ;
            }
        }
    }

    static double insideBoxes(double[] boxes, int cx, int cy, int cz, double x0, double y0, double z0,
                              double dx, double dy, double dz, double from, double to) {
        double total = 0.0;
        for (int i = 0; i + 5 < boxes.length; i += 6) {
            double lo = from;
            double hi = to;
            if (Math.abs(dx) < 1.0E-12) {
                if (x0 < cx + boxes[i] || x0 > cx + boxes[i + 3]) {
                    continue;
                }
            } else {
                double t1 = (cx + boxes[i] - x0) / dx;
                double t2 = (cx + boxes[i + 3] - x0) / dx;
                lo = Math.max(lo, Math.min(t1, t2));
                hi = Math.min(hi, Math.max(t1, t2));
            }
            if (Math.abs(dy) < 1.0E-12) {
                if (y0 < cy + boxes[i + 1] || y0 > cy + boxes[i + 4]) {
                    continue;
                }
            } else {
                double t1 = (cy + boxes[i + 1] - y0) / dy;
                double t2 = (cy + boxes[i + 4] - y0) / dy;
                lo = Math.max(lo, Math.min(t1, t2));
                hi = Math.min(hi, Math.max(t1, t2));
            }
            if (Math.abs(dz) < 1.0E-12) {
                if (z0 < cz + boxes[i + 2] || z0 > cz + boxes[i + 5]) {
                    continue;
                }
            } else {
                double t1 = (cz + boxes[i + 2] - z0) / dz;
                double t2 = (cz + boxes[i + 5] - z0) / dz;
                lo = Math.max(lo, Math.min(t1, t2));
                hi = Math.min(hi, Math.max(t1, t2));
            }
            if (hi > lo) {
                total += hi - lo;
            }
        }
        return total;
    }

    private static double boundary(double origin, int cell, int step, double delta) {
        if (step > 0) {
            return (cell + 1 - origin) / delta;
        }
        if (step < 0) {
            return (cell - origin) / delta;
        }
        return Double.POSITIVE_INFINITY;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    @FunctionalInterface
    public interface World {
        Medium medium(int x, int y, int z);
    }
}
