package de.lardtom.propwash.bando.plan;

import java.util.ArrayDeque;

public final class BandoChecks {
    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private BandoChecks() {
    }

    public static int ungrounded(BandoPlan plan) {
        return ungroundedCells(plan, 0).size();
    }

    public static java.util.List<String> ungroundedCells(BandoPlan plan, int limit) {
        boolean groundHolds = plan.variant().family() != BandoFamily.COAST;
        int sx = plan.sizeX();
        int sy = plan.sizeY();
        int sz = plan.sizeZ();
        boolean[] seen = new boolean[sx * sy * sz];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    Mat mat = plan.mat(x, y, z);
                    if (!mat.holdsUp()) {
                        continue;
                    }
                    boolean seed = (groundHolds && y <= plan.ground()) || (y == 0 && plan.fill(x, z) != Mat.EMPTY)
                            || (groundHolds && y == plan.ground() + 1 && plan.mat(x, y - 1, z) == Mat.EMPTY);
                    if (seed) {
                        seen[(y * sz + z) * sx + x] = true;
                        queue.add(new int[]{x, y, z});
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            Mat current = plan.mat(c[0], c[1], c[2]);
            boolean step = current == Mat.ROOF_STEP;
            boolean attached = current.kind() == Mat.Kind.ATTACHED;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int axes = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
                        if (axes == 0 || axes == 3 || axes == 2 && (!step || dy == 0)) {
                            continue;
                        }
                        int x = c[0] + dx;
                        int y = c[1] + dy;
                        int z = c[2] + dz;
                        if (!plan.inside(x, y, z)) {
                            continue;
                        }
                        int i = (y * sz + z) * sx + x;
                        Mat mat = plan.mat(x, y, z);
                        if (seen[i] || !mat.holdsUp() || attached && mat.kind() == Mat.Kind.STRUCTURE
                                || axes == 2 && mat != Mat.ROOF_STEP && mat != Mat.ROOF) {
                            continue;
                        }
                        seen[i] = true;
                        queue.add(new int[]{x, y, z});
                    }
                }
            }
        }
        java.util.List<String> cells = new java.util.ArrayList<>();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    if (plan.mat(x, y, z).holdsUp() && !seen[(y * sz + z) * sx + x]) {
                        cells.add(plan.mat(x, y, z) + "@" + x + "," + y + "," + z);
                    }
                }
            }
        }
        return limit > 0 && cells.size() > limit ? cells.subList(0, limit) : cells;
    }

    public static int throughGaps(BandoPlan plan) {
        int gaps = 0;
        for (int axis = 0; axis < 3; axis++) {
            gaps += gaps(plan, axis);
        }
        return gaps;
    }

    public static int verticalGaps(BandoPlan plan) {
        return gaps(plan, Meta.AXIS_Y);
    }

    private static final int REACH = 4;

    private static int gaps(BandoPlan plan, int axis) {
        int g = plan.ground();
        int[] size = {plan.sizeX(), plan.sizeY(), plan.sizeZ()};
        int along = axis == Meta.AXIS_X ? 0 : axis == Meta.AXIS_Y ? 1 : 2;
        int u = along == 0 ? 1 : 0;
        int v = along == 2 ? 1 : 2;
        int from = along == 1 ? g + 1 : 0;
        int lowU = u == 1 ? g + 1 : 0;
        int lowV = v == 1 ? g + 1 : 0;
        java.util.List<int[]> openings = new java.util.ArrayList<>();
        for (int a = lowU; a < size[u] - 1; a++) {
            for (int b = lowV; b < size[v] - 1; b++) {
                for (int t = from; t < size[along]; t++) {
                    if (framed(plan, along, u, v, a, b, t) && clear(plan, along, u, v, a, b, t - REACH, t + REACH, g)) {
                        openings.add(new int[]{a, b, t});
                    }
                }
            }
        }
        boolean[] done = new boolean[openings.size()];
        int clusters = 0;
        for (int i = 0; i < openings.size(); i++) {
            if (done[i]) {
                continue;
            }
            clusters++;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(i);
            done[i] = true;
            while (!queue.isEmpty()) {
                int[] c = openings.get(queue.poll());
                for (int j = 0; j < openings.size(); j++) {
                    int[] o = openings.get(j);
                    if (!done[j] && Math.abs(o[0] - c[0]) <= 2 && Math.abs(o[1] - c[1]) <= 2 && Math.abs(o[2] - c[2]) <= 2) {
                        done[j] = true;
                        queue.add(j);
                    }
                }
            }
        }
        return clusters;
    }

    private static boolean clear(BandoPlan plan, int along, int u, int v, int a, int b, int t0, int t1, int g) {
        int[] p = new int[3];
        for (int t = t0; t <= t1; t++) {
            if (along == 1 && t <= g) {
                return false;
            }
            p[along] = t;
            for (int da = 0; da < 2; da++) {
                for (int db = 0; db < 2; db++) {
                    p[u] = a + da;
                    p[v] = b + db;
                    if (plan.blocksFlight(p[0], p[1], p[2])) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static final int FRAME = 6;

    private static boolean framed(BandoPlan plan, int along, int u, int v, int a, int b, int t) {
        int[] p = new int[3];
        p[along] = t;
        int sides = 0;
        for (int side = 0; side < 4; side++) {
            boolean found = false;
            for (int d = 1; d <= FRAME && !found; d++) {
                for (int k = 0; k < 2 && !found; k++) {
                    p[u] = side == 0 ? a - d : side == 1 ? a + 1 + d : a + k;
                    p[v] = side == 2 ? b - d : side == 3 ? b + 1 + d : b + k;
                    found = plan.mat(p[0], p[1], p[2]).kind() == Mat.Kind.STRUCTURE;
                }
            }
            if (found) {
                sides++;
            }
        }
        return sides >= 3;
    }

    public static boolean chestOpen(BandoPlan plan) {
        if (!plan.hasChest()) {
            return true;
        }
        int x = plan.chestX();
        int y = plan.chestY();
        int z = plan.chestZ();
        int facing = plan.chestFacing();
        return !plan.blocksFlight(x, y + 1, z) && !plan.blocksFlight(x + Meta.dx(facing), y, z + Meta.dz(facing));
    }

    public static boolean chestReachable(BandoPlan plan) {
        if (!plan.hasChest()) {
            return true;
        }
        int sx = plan.sizeX();
        int sy = plan.sizeY();
        int sz = plan.sizeZ();
        boolean[] seen = new boolean[sx * sy * sz];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                int y = sy - 1;
                if (!plan.blocksFlight(x, y, z)) {
                    seen[(y * sz + z) * sx + x] = true;
                    queue.add(new int[]{x, y, z});
                }
            }
        }
        int fx = plan.chestX() + Meta.dx(plan.chestFacing());
        int fz = plan.chestZ() + Meta.dz(plan.chestFacing());
        int fy = plan.chestY();
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            if (c[0] == fx && c[1] == fy && c[2] == fz) {
                return true;
            }
            for (int[] d : FACES) {
                int x = c[0] + d[0];
                int y = c[1] + d[1];
                int z = c[2] + d[2];
                if (!plan.inside(x, y, z)) {
                    continue;
                }
                int i = (y * sz + z) * sx + x;
                if (!seen[i] && !plan.blocksFlight(x, y, z) && (y > plan.ground() || plan.mat(x, y, z) != Mat.EMPTY)) {
                    seen[i] = true;
                    queue.add(new int[]{x, y, z});
                }
            }
        }
        return false;
    }
}
