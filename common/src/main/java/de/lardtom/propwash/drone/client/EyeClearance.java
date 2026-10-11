package de.lardtom.propwash.drone.client;

import java.util.Arrays;

public final class EyeClearance {
    public static final double VANILLA_NEAR = 0.05;
    public static final double MIN_NEAR = 0.01;
    public static final double MARGIN = 0.002;
    public static final double MAX_SHIFT = 0.6;
    static final int MAX_AXES = 26;
    static final int VALUES_PER_AXIS = 8;
    static final int PULL_STEPS = 8;
    private static final double STEP_OUT = 1.0E-6;
    private static final int MAX_CANDIDATES = VALUES_PER_AXIS * VALUES_PER_AXIS * VALUES_PER_AXIS + PULL_STEPS;

    @FunctionalInterface
    public interface Solids {
        void collect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, EyeClearance sink);
    }

    private final double[] axes = new double[MAX_AXES * 3];
    private final double[] low = new double[MAX_AXES];
    private final double[] high = new double[MAX_AXES];
    private int axisCount;
    private double[] boxes = new double[6 * 32];
    private int boxCount;
    private final double[] corners = new double[12];
    private final double[] edges = new double[18];
    private final double[][] values = new double[3][2 * 256 + 1];
    private final int[] valueCount = new int[3];
    private final double[] candidates = new double[3 * MAX_CANDIDATES];
    private final long[] order = new long[MAX_CANDIDATES];
    private double x;
    private double y;
    private double z;
    private double near = VANILLA_NEAR;
    private boolean moved;
    private boolean shortened;

    public void view(double fx, double fy, double fz, double ux, double uy, double uz, double lx, double ly, double lz, double tanX, double tanY) {
        double[] corners = this.corners;
        for (int i = 0; i < 4; i++) {
            double sx = (i == 0 || i == 3 ? 1.0 : -1.0) * tanX;
            double sy = (i < 2 ? 1.0 : -1.0) * tanY;
            corners[3 * i] = fx + sx * lx + sy * ux;
            corners[3 * i + 1] = fy + sx * ly + sy * uy;
            corners[3 * i + 2] = fz + sx * lz + sy * uz;
        }
        this.axisCount = 0;
        this.addAxis(1.0, 0.0, 0.0, corners);
        this.addAxis(0.0, 1.0, 0.0, corners);
        this.addAxis(0.0, 0.0, 1.0, corners);
        this.addAxis(fx, fy, fz, corners);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            this.addCross(corners[3 * i], corners[3 * i + 1], corners[3 * i + 2], corners[3 * j], corners[3 * j + 1], corners[3 * j + 2], corners);
        }
        double[] edges = this.edges;
        edges[0] = lx;
        edges[1] = ly;
        edges[2] = lz;
        edges[3] = ux;
        edges[4] = uy;
        edges[5] = uz;
        System.arraycopy(corners, 0, edges, 6, 12);
        for (int k = 0; k < 3; k++) {
            double ex = k == 0 ? 1.0 : 0.0;
            double ey = k == 1 ? 1.0 : 0.0;
            double ez = k == 2 ? 1.0 : 0.0;
            for (int e = 0; e < 6; e++) {
                this.addCross(ex, ey, ez, edges[3 * e], edges[3 * e + 1], edges[3 * e + 2], corners);
            }
        }
    }

    private void addCross(double ax, double ay, double az, double bx, double by, double bz, double[] corners) {
        this.addAxis(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx, corners);
    }

    private void addAxis(double ax, double ay, double az, double[] corners) {
        double length = Math.sqrt(ax * ax + ay * ay + az * az);
        if (length < 1.0E-9 || this.axisCount >= MAX_AXES) {
            return;
        }
        ax /= length;
        ay /= length;
        az /= length;
        double lo = 0.0;
        double hi = 0.0;
        for (int i = 0; i < 4; i++) {
            double d = corners[3 * i] * ax + corners[3 * i + 1] * ay + corners[3 * i + 2] * az;
            lo = Math.min(lo, d);
            hi = Math.max(hi, d);
        }
        int a = this.axisCount++;
        this.axes[3 * a] = ax;
        this.axes[3 * a + 1] = ay;
        this.axes[3 * a + 2] = az;
        this.low[a] = lo;
        this.high[a] = hi;
    }

    public void clearBoxes() {
        this.boxCount = 0;
    }

    public void addBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (!(maxX > minX && maxY > minY && maxZ > minZ)) {
            return;
        }
        if (6 * (this.boxCount + 1) > this.boxes.length) {
            this.boxes = Arrays.copyOf(this.boxes, this.boxes.length * 2);
        }
        int o = 6 * this.boxCount++;
        this.boxes[o] = minX;
        this.boxes[o + 1] = minY;
        this.boxes[o + 2] = minZ;
        this.boxes[o + 3] = maxX;
        this.boxes[o + 4] = maxY;
        this.boxes[o + 5] = maxZ;
    }

    public double clearNear(double px, double py, double pz) {
        double best = Double.POSITIVE_INFINITY;
        for (int b = 0; b < this.boxCount && best > 0.0; b++) {
            best = Math.min(best, this.clearNear(px, py, pz, b));
        }
        return best;
    }

    private double clearNear(double px, double py, double pz, int b) {
        int o = 6 * b;
        double cx = 0.5 * (this.boxes[o] + this.boxes[o + 3]);
        double cy = 0.5 * (this.boxes[o + 1] + this.boxes[o + 4]);
        double cz = 0.5 * (this.boxes[o + 2] + this.boxes[o + 5]);
        double hx = 0.5 * (this.boxes[o + 3] - this.boxes[o]) + MARGIN;
        double hy = 0.5 * (this.boxes[o + 4] - this.boxes[o + 1]) + MARGIN;
        double hz = 0.5 * (this.boxes[o + 5] - this.boxes[o + 2]) + MARGIN;
        double allowed = 0.0;
        for (int a = 0; a < this.axisCount; a++) {
            double ax = this.axes[3 * a];
            double ay = this.axes[3 * a + 1];
            double az = this.axes[3 * a + 2];
            double center = cx * ax + cy * ay + cz * az;
            double radius = hx * Math.abs(ax) + hy * Math.abs(ay) + hz * Math.abs(az);
            double s = px * ax + py * ay + pz * az;
            double gap;
            if (s <= center - radius) {
                gap = this.high[a] > 0.0 ? (center - radius - s) / this.high[a] : Double.POSITIVE_INFINITY;
            } else if (s >= center + radius) {
                gap = this.low[a] < 0.0 ? (s - center - radius) / -this.low[a] : Double.POSITIVE_INFINITY;
            } else {
                gap = 0.0;
            }
            if (gap > allowed) {
                allowed = gap;
                if (allowed == Double.POSITIVE_INFINITY) {
                    break;
                }
            }
        }
        return allowed;
    }

    public boolean inside(double px, double py, double pz) {
        return this.clearNear(px, py, pz) <= 0.0;
    }

    public boolean sightBlocked(double ax, double ay, double az, double px, double py, double pz) {
        double dx = px - ax;
        double dy = py - ay;
        double dz = pz - az;
        for (int b = 0; b < this.boxCount; b++) {
            int o = 6 * b;
            if (ax >= this.boxes[o] && ax <= this.boxes[o + 3] && ay >= this.boxes[o + 1] && ay <= this.boxes[o + 4]
                    && az >= this.boxes[o + 2] && az <= this.boxes[o + 5]) {
                continue;
            }
            double enter = 0.0;
            double exit = 1.0;
            boolean hit = true;
            for (int k = 0; k < 3 && hit; k++) {
                double start = k == 0 ? ax : k == 1 ? ay : az;
                double delta = k == 0 ? dx : k == 1 ? dy : dz;
                double min = this.boxes[o + k];
                double max = this.boxes[o + 3 + k];
                if (Math.abs(delta) < 1.0E-12) {
                    hit = start > min && start < max;
                } else {
                    double t0 = (min - start) / delta;
                    double t1 = (max - start) / delta;
                    enter = Math.max(enter, Math.min(t0, t1));
                    exit = Math.min(exit, Math.max(t0, t1));
                    hit = enter < exit;
                }
            }
            if (hit) {
                return true;
            }
        }
        return false;
    }

    public boolean resolve(double ex, double ey, double ez, double ax, double ay, double az, double preferredNear, double minNear, Solids solids) {
        double wanted = Math.max(preferredNear, 0.0);
        double least = Math.min(Math.max(minNear, 0.0), wanted);
        double reach = this.reach(wanted);
        this.clearBoxes();
        solids.collect(Math.min(ex - reach, ax - MARGIN), Math.min(ey - reach, ay - MARGIN), Math.min(ez - reach, az - MARGIN),
                Math.max(ex + reach, ax + MARGIN), Math.max(ey + reach, ay + MARGIN), Math.max(ez + reach, az + MARGIN), this);
        boolean anchorFree = this.clearNear(ax, ay, az) > 0.0;
        if (anchorFree && this.keepEye(ex, ey, ez, ax, ay, az, wanted, least)) {
            return this.shortened;
        }
        double wide = MAX_SHIFT + reach;
        this.clearBoxes();
        solids.collect(Math.min(ex, ax) - wide, Math.min(ey, ay) - wide, Math.min(ez, az) - wide,
                Math.max(ex, ax) + wide, Math.max(ey, ay) + wide, Math.max(ez, az) + wide, this);
        double sx = ax;
        double sy = ay;
        double sz = az;
        if (!anchorFree) {
            int count = this.gatherCandidates(ax, ay, az, ax, ay, az, 0.0);
            for (int i = 0; i < count; i++) {
                int c = (int) (this.order[i] & 0x3FFL);
                if (this.clearNear(this.candidates[3 * c], this.candidates[3 * c + 1], this.candidates[3 * c + 2]) > 0.0) {
                    sx = this.candidates[3 * c];
                    sy = this.candidates[3 * c + 1];
                    sz = this.candidates[3 * c + 2];
                    break;
                }
            }
            if (this.keepEye(ex, ey, ez, sx, sy, sz, wanted, least)) {
                return this.shortened;
            }
        }
        int count = this.gatherCandidates(ex, ey, ez, sx, sy, sz, least);
        for (int i = 0; i < count; i++) {
            int c = (int) (this.order[i] & 0x3FFL);
            double cx = this.candidates[3 * c];
            double cy = this.candidates[3 * c + 1];
            double cz = this.candidates[3 * c + 2];
            double room = this.clearNear(cx, cy, cz);
            if (room >= least && room > 0.0 && !this.sightBlocked(sx, sy, sz, cx, cy, cz)) {
                this.accept(cx, cy, cz, Math.min(room, wanted), true, room < wanted);
                return true;
            }
        }
        double room = this.clearNear(sx, sy, sz);
        this.accept(sx, sy, sz, Math.max(least, Math.min(room, wanted)), true, true);
        return true;
    }

    private boolean keepEye(double ex, double ey, double ez, double sx, double sy, double sz, double wanted, double least) {
        double free = this.clearNear(ex, ey, ez);
        if (free <= 0.0 || free < least || this.sightBlocked(sx, sy, sz, ex, ey, ez)) {
            return false;
        }
        this.accept(ex, ey, ez, Math.min(free, wanted), false, free < wanted);
        return true;
    }

    private double reach(double nearDistance) {
        double extent = 0.0;
        for (int k = 0; k < 3; k++) {
            extent = Math.max(extent, Math.max(-this.low[k], this.high[k]));
        }
        return nearDistance * extent + MARGIN + STEP_OUT;
    }

    private int gatherCandidates(double ex, double ey, double ez, double ax, double ay, double az, double least) {
        double[] eye = {ex, ey, ez};
        for (int k = 0; k < 3; k++) {
            double[] list = this.values[k];
            int n = 0;
            list[n++] = eye[k];
            for (int b = 0; b < this.boxCount; b++) {
                double min = this.boxes[6 * b + k] - MARGIN - least * this.high[k] - STEP_OUT;
                double max = this.boxes[6 * b + 3 + k] + MARGIN - least * this.low[k] + STEP_OUT;
                n = this.offer(list, n, eye[k], min);
                n = this.offer(list, n, eye[k], max);
            }
            this.valueCount[k] = Math.min(n, VALUES_PER_AXIS);
            sortByDistance(list, n, eye[k]);
        }
        int count = 0;
        for (int i = 0; i < this.valueCount[0]; i++) {
            for (int j = 0; j < this.valueCount[1]; j++) {
                for (int k = 0; k < this.valueCount[2]; k++) {
                    count = this.add(count, this.values[0][i], this.values[1][j], this.values[2][k], ex, ey, ez);
                }
            }
        }
        for (int s = 1; s <= PULL_STEPS; s++) {
            double t = (double) s / PULL_STEPS;
            count = this.add(count, ex + t * (ax - ex), ey + t * (ay - ey), ez + t * (az - ez), ex, ey, ez);
        }
        Arrays.sort(this.order, 0, count);
        return count;
    }

    private int offer(double[] list, int n, double origin, double value) {
        if (Math.abs(value - origin) > MAX_SHIFT || n >= list.length) {
            return n;
        }
        for (int i = 0; i < n; i++) {
            if (Math.abs(list[i] - value) < 1.0E-9) {
                return n;
            }
        }
        list[n] = value;
        return n + 1;
    }

    private static void sortByDistance(double[] list, int n, double origin) {
        for (int i = 1; i < n; i++) {
            double v = list[i];
            double d = Math.abs(v - origin);
            int j = i - 1;
            while (j >= 0 && Math.abs(list[j] - origin) > d) {
                list[j + 1] = list[j];
                j--;
            }
            list[j + 1] = v;
        }
    }

    private int add(int count, double cx, double cy, double cz, double ex, double ey, double ez) {
        if (count >= MAX_CANDIDATES) {
            return count;
        }
        this.candidates[3 * count] = cx;
        this.candidates[3 * count + 1] = cy;
        this.candidates[3 * count + 2] = cz;
        double dx = cx - ex;
        double dy = cy - ey;
        double dz = cz - ez;
        double distance = dx * dx + dy * dy + dz * dz;
        this.order[count] = (Double.doubleToLongBits(distance) & ~0x3FFL) | count;
        return count + 1;
    }

    private void accept(double px, double py, double pz, double nearDistance, boolean shifted, boolean shorter) {
        this.x = px;
        this.y = py;
        this.z = pz;
        this.near = nearDistance;
        this.moved = shifted;
        this.shortened = shorter;
    }

    public double x() {
        return this.x;
    }

    public double y() {
        return this.y;
    }

    public double z() {
        return this.z;
    }

    public double near() {
        return this.near;
    }

    public boolean moved() {
        return this.moved;
    }

    public boolean shortened() {
        return this.shortened;
    }
}
