package de.lardtom.propwash.rescue;

import java.util.Arrays;

public final class RoutePlanner {
    public static final int RADIUS = 20;
    public static final int UP = 12;
    public static final int DOWN = 6;
    public static final int MAX_EXPANSIONS = 2500;
    public static final int MAX_WAYPOINTS = 32;
    public static final double CELL_LIFT = 0.3;
    public static final double MAX_VERTICAL_INFLATION = 0.25;
    private static final int SIZE_XZ = 2 * RADIUS + 1;
    private static final int SIZE_Y = UP + DOWN + 1;
    private static final int CELLS = SIZE_XZ * SIZE_XZ * SIZE_Y;
    private static final byte UNKNOWN = 0;
    private static final byte FREE = 1;
    private static final byte BLOCKED = 2;
    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1, 0, 0};
    private static final int[] DY = {0, 0, 0, 0, 0, 0, 0, 0, 1, -1};
    private static final double[] STEP_COST = {1.0, 1.0, 1.0, 1.0, Math.sqrt(2.0), Math.sqrt(2.0), Math.sqrt(2.0), Math.sqrt(2.0), 1.0, 1.0};
    private static final int SHORTCUT_LOOKAHEAD = 12;

    public interface Goal {
        double estimate(double x, double bottom, double z);

        boolean reached(RescueWorld world, double x, double bottom, double z);
    }

    private final byte[] occupancy = new byte[CELLS];
    private final float[] cost = new float[CELLS];
    private final int[] parent = new int[CELLS];
    private final boolean[] closed = new boolean[CELLS];
    private final long[] heap = new long[MAX_EXPANSIONS * DX.length + 2];
    private final int[] trail = new int[CELLS];
    private final double[] wx = new double[MAX_WAYPOINTS];
    private final double[] wy = new double[MAX_WAYPOINTS];
    private final double[] wz = new double[MAX_WAYPOINTS];
    private int heapSize;
    private int waypoints;
    private int expansions;
    private boolean complete;
    private RescueWorld world;
    private double halfWidth;
    private double height;
    private double inflateH;
    private double inflateV;
    private int ox;
    private int oy;
    private int oz;

    public RoutePlanner() {
    }

    public boolean plan(RescueWorld world, double x, double bottom, double z, double halfWidth, double height, double inflation,
                        double lowestBottom, double highestBottom, double minGain, Goal goal) {
        this.world = world;
        this.halfWidth = halfWidth;
        this.height = height;
        this.inflateH = Math.max(0.0, inflation);
        this.inflateV = Math.min(MAX_VERTICAL_INFLATION, 0.5 * this.inflateH);
        this.ox = WorldProbe.floor(x);
        this.oz = WorldProbe.floor(z);
        this.oy = WorldProbe.floor(bottom - CELL_LIFT + 0.5);
        this.waypoints = 0;
        this.expansions = 0;
        this.complete = false;
        Arrays.fill(this.occupancy, UNKNOWN);
        Arrays.fill(this.closed, false);
        Arrays.fill(this.cost, Float.POSITIVE_INFINITY);
        this.heapSize = 0;
        int kLow = Math.max(0, Math.min(SIZE_Y - 1, (int) Math.ceil(lowestBottom - CELL_LIFT) - this.oy + DOWN));
        int kHigh = Math.max(0, Math.min(SIZE_Y - 1, WorldProbe.floor(highestBottom - CELL_LIFT) - this.oy + DOWN));
        int kStart = Math.max(kLow, Math.min(kHigh, DOWN));
        if (kLow > kHigh) {
            return false;
        }
        int start = index(RADIUS, RADIUS, kStart);
        this.occupancy[start] = FREE;
        this.cost[start] = 0.0F;
        this.parent[start] = -1;
        double startEstimate = goal.estimate(this.cellX(start), this.cellBottom(start), this.cellZ(start));
        this.push(startEstimate, start);
        int best = start;
        double bestEstimate = startEstimate;
        int end = -1;
        while (this.heapSize > 0 && this.expansions < MAX_EXPANSIONS) {
            int node = this.pop();
            if (this.closed[node]) {
                continue;
            }
            this.closed[node] = true;
            this.expansions++;
            double cx = this.cellX(node);
            double cb = this.cellBottom(node);
            double cz = this.cellZ(node);
            if (node != start && goal.reached(world, cx, cb, cz)) {
                end = node;
                this.complete = true;
                break;
            }
            double estimate = goal.estimate(cx, cb, cz);
            if (estimate < bestEstimate) {
                bestEstimate = estimate;
                best = node;
            }
            int i = node % SIZE_XZ;
            int j = (node / SIZE_XZ) % SIZE_XZ;
            int k = node / (SIZE_XZ * SIZE_XZ);
            for (int n = 0; n < DX.length; n++) {
                int ni = i + DX[n];
                int nj = j + DZ[n];
                int nk = k + DY[n];
                if (ni < 0 || nj < 0 || ni >= SIZE_XZ || nj >= SIZE_XZ || nk < kLow || nk > kHigh) {
                    continue;
                }
                int next = index(ni, nj, nk);
                if (this.closed[next] || !this.free(next)) {
                    continue;
                }
                if (DX[n] != 0 && DZ[n] != 0 && (!this.free(index(ni, j, k)) || !this.free(index(i, nj, k)))) {
                    continue;
                }
                float g = (float) (this.cost[node] + STEP_COST[n]);
                if (g < this.cost[next]) {
                    this.cost[next] = g;
                    this.parent[next] = node;
                    this.push(g + goal.estimate(this.cellX(next), this.cellBottom(next), this.cellZ(next)), next);
                }
            }
        }
        if (end < 0) {
            if (best == start || startEstimate - bestEstimate < minGain) {
                return false;
            }
            end = best;
        }
        this.buildRoute(x, bottom, z, start, end);
        return this.waypoints > 0;
    }

    public int size() {
        return this.waypoints;
    }

    public double x(int i) {
        return this.wx[i];
    }

    public double bottom(int i) {
        return this.wy[i];
    }

    public double z(int i) {
        return this.wz[i];
    }

    public int expansions() {
        return this.expansions;
    }

    public boolean complete() {
        return this.complete;
    }

    private void buildRoute(double x, double bottom, double z, int start, int end) {
        int count = 0;
        for (int node = end; node >= 0 && count < this.trail.length; node = this.parent[node]) {
            this.trail[count++] = node;
            if (node == start) {
                break;
            }
        }
        for (int a = 0, b = count - 1; a < b; a++, b--) {
            int t = this.trail[a];
            this.trail[a] = this.trail[b];
            this.trail[b] = t;
        }
        double ax = x;
        double ay = bottom;
        double az = z;
        int anchor = 0;
        while (anchor < count - 1 && this.waypoints < MAX_WAYPOINTS) {
            int limit = Math.min(count - 1, anchor + SHORTCUT_LOOKAHEAD);
            int chosen = anchor + 1;
            for (int c = limit; c > anchor + 1; c--) {
                if (this.clearLine(ax, ay, az, this.trail[c])) {
                    chosen = c;
                    break;
                }
            }
            int cell = this.trail[chosen];
            ax = this.cellX(cell);
            ay = this.cellBottom(cell);
            az = this.cellZ(cell);
            this.wx[this.waypoints] = ax;
            this.wy[this.waypoints] = ay;
            this.wz[this.waypoints] = az;
            this.waypoints++;
            anchor = chosen;
        }
    }

    private boolean clearLine(double ax, double ay, double az, int cell) {
        double dx = this.cellX(cell) - ax;
        double dy = this.cellBottom(cell) - ay;
        double dz = this.cellZ(cell) - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) {
            return true;
        }
        double free = WorldProbe.sweep(this.world, ax, ay, az, this.halfWidth, this.height, this.inflateH, this.inflateV, dx, dy, dz, len);
        return free >= len - 1e-6;
    }

    private boolean free(int cell) {
        byte state = this.occupancy[cell];
        if (state == UNKNOWN) {
            boolean blocked = WorldProbe.droneBlocked(this.world, this.cellX(cell), this.cellBottom(cell), this.cellZ(cell),
                    this.halfWidth, this.height, this.inflateH, this.inflateV);
            state = blocked ? BLOCKED : FREE;
            this.occupancy[cell] = state;
        }
        return state == FREE;
    }

    private double cellX(int cell) {
        return this.ox + (cell % SIZE_XZ) - RADIUS + 0.5;
    }

    private double cellZ(int cell) {
        return this.oz + (cell / SIZE_XZ) % SIZE_XZ - RADIUS + 0.5;
    }

    private double cellBottom(int cell) {
        return this.oy + cell / (SIZE_XZ * SIZE_XZ) - DOWN + CELL_LIFT;
    }

    private static int index(int i, int j, int k) {
        return (k * SIZE_XZ + j) * SIZE_XZ + i;
    }

    private void push(double priority, int cell) {
        if (this.heapSize >= this.heap.length) {
            return;
        }
        long entry = ((long) Float.floatToIntBits((float) Math.max(0.0, priority)) << 32) | (cell & 0xFFFFFFFFL);
        int i = this.heapSize++;
        this.heap[i] = entry;
        while (i > 0) {
            int p = (i - 1) >>> 1;
            if (this.heap[p] <= entry) {
                break;
            }
            this.heap[i] = this.heap[p];
            i = p;
        }
        this.heap[i] = entry;
    }

    private int pop() {
        long top = this.heap[0];
        long last = this.heap[--this.heapSize];
        int i = 0;
        int half = this.heapSize >>> 1;
        while (i < half) {
            int c = 2 * i + 1;
            int r = c + 1;
            if (r < this.heapSize && this.heap[r] < this.heap[c]) {
                c = r;
            }
            if (last <= this.heap[c]) {
                break;
            }
            this.heap[i] = this.heap[c];
            i = c;
        }
        if (this.heapSize > 0) {
            this.heap[i] = last;
        }
        return (int) top;
    }
}
