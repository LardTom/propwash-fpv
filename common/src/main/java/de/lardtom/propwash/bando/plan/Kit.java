package de.lardtom.propwash.bando.plan;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

public final class Kit {
    public static final Set<Mat> CLADDING = EnumSet.of(Mat.WALL, Mat.WALL_ALT, Mat.ROOF, Mat.ROOF_STEP, Mat.FLOOR, Mat.CATWALK, Mat.WINDOW, Mat.TRIM);
    public static final Set<Mat> CLIMBABLE = EnumSet.of(Mat.COLUMN, Mat.WALL, Mat.WALL_ALT, Mat.TANK, Mat.STACK, Mat.FOUNDATION);
    private static final int[][] NEIGHBORS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int[][] EDGES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {0, 1, 1}, {0, 1, -1}, {0, -1, 1}, {0, -1, -1}};

    private final BandoPlan plan;
    private final Random random;

    public Kit(BandoPlan plan, Random random) {
        this.plan = plan;
        this.random = random;
    }

    public BandoPlan plan() {
        return this.plan;
    }

    public Random random() {
        return this.random;
    }

    public int g() {
        return this.plan.ground();
    }

    public boolean chance(double p) {
        return this.random.nextDouble() < p;
    }

    public int range(int lo, int hi) {
        return hi <= lo ? lo : lo + this.random.nextInt(hi - lo + 1);
    }

    public int odd(int lo, int hi) {
        int v = this.range(lo, hi);
        return (v & 1) == 0 ? (v + 1 <= hi ? v + 1 : v - 1) : v;
    }

    public void box(int x0, int y0, int z0, int x1, int y1, int z1, Mat mat, int meta) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                    this.plan.set(x, y, z, mat, meta);
                }
            }
        }
    }

    public void box(int x0, int y0, int z0, int x1, int y1, int z1, Mat mat) {
        this.box(x0, y0, z0, x1, y1, z1, mat, 0);
    }

    public void boxIfFree(int x0, int y0, int z0, int x1, int y1, int z1, Mat mat, int meta) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                    this.plan.setIfFree(x, y, z, mat, meta);
                }
            }
        }
    }

    public void clear(int x0, int y0, int z0, int x1, int y1, int z1) {
        this.box(x0, y0, z0, x1, y1, z1, Mat.AIR, 0);
    }

    public void column(int x, int z, int y0, int y1, Mat mat) {
        this.box(x, y0, z, x, y1, z, mat, Meta.AXIS_Y);
    }

    public void beamX(int x0, int x1, int y, int z, Mat mat) {
        this.box(x0, y, z, x1, y, z, mat, Meta.AXIS_X);
    }

    public void beamZ(int x, int y, int z0, int z1, Mat mat) {
        this.box(x, y, z0, x, y, z1, mat, Meta.AXIS_Z);
    }

    public void line(int x0, int y0, int z0, int x1, int y1, int z1, Mat mat, int meta) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        int dz = z1 - z0;
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        int px = x0;
        int py = y0;
        int pz = z0;
        this.plan.set(x0, y0, z0, mat, meta);
        for (int i = 1; i <= steps; i++) {
            int x = x0 + Math.round((float) dx * i / steps);
            int y = y0 + Math.round((float) dy * i / steps);
            int z = z0 + Math.round((float) dz * i / steps);
            if (x != px && (y != py || z != pz)) {
                this.plan.set(x, py, pz, mat, meta);
            }
            if (z != pz && y != py) {
                this.plan.set(x, py, z, mat, meta);
            }
            this.plan.set(x, y, z, mat, meta);
            px = x;
            py = y;
            pz = z;
        }
    }

    public void cylinder(double cx, double cz, double radius, int y0, int y1, Mat mat, boolean hollow) {
        int minX = (int) Math.floor(cx - radius);
        int maxX = (int) Math.ceil(cx + radius);
        int minZ = (int) Math.floor(cz - radius);
        int maxZ = (int) Math.ceil(cz + radius);
        double outer = radius * radius;
        double inner = (radius - 1.0) * (radius - 1.0);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double d = (x - cx) * (x - cx) + (z - cz) * (z - cz);
                if (d > outer + 0.25) {
                    continue;
                }
                boolean shell = d > inner + 0.25;
                for (int y = y0; y <= y1; y++) {
                    if (!hollow || shell) {
                        this.plan.set(x, y, z, mat, Meta.AXIS_Y);
                    } else {
                        this.plan.set(x, y, z, Mat.AIR, 0);
                    }
                }
            }
        }
    }

    public void disc(double cx, double cz, double radius, int y, Mat mat, int meta) {
        int minX = (int) Math.floor(cx - radius);
        int maxX = (int) Math.ceil(cx + radius);
        int minZ = (int) Math.floor(cz - radius);
        int maxZ = (int) Math.ceil(cz + radius);
        double outer = radius * radius + 0.25;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if ((x - cx) * (x - cx) + (z - cz) * (z - cz) <= outer) {
                    this.plan.set(x, y, z, mat, meta);
                }
            }
        }
    }

    public void ringFree(double cx, double cz, double radius, int y, Mat mat, int meta) {
        int minX = (int) Math.floor(cx - radius);
        int maxX = (int) Math.ceil(cx + radius);
        int minZ = (int) Math.floor(cz - radius);
        int maxZ = (int) Math.ceil(cz + radius);
        double outer = radius * radius + 0.25;
        double inner = (radius - 1.0) * (radius - 1.0) + 0.25;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double d = (x - cx) * (x - cx) + (z - cz) * (z - cz);
                if (d <= outer && d > inner) {
                    this.plan.setIfFree(x, y, z, mat, meta);
                }
            }
        }
    }

    public void underFill(int x0, int z0, int x1, int z1, Mat mat) {
        for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                this.plan.setFill(x, z, mat);
            }
        }
    }

    public void ladder(int x, int z, int y0, int y1, int facing) {
        for (int y = y0; y <= y1; y++) {
            this.plan.set(x, y, z, Mat.LADDER, facing & 3);
        }
    }

    public void chain(int x, int z, int y0, int y1) {
        this.box(x, y0, z, x, y1, z, Mat.CHAIN, Meta.AXIS_Y);
    }

    public void railX(int x0, int x1, int y, int z, double gaps) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            if (!this.chance(gaps)) {
                this.plan.setIfFree(x, y, z, Mat.RAIL, 0);
            }
        }
    }

    public void railZ(int x, int y, int z0, int z1, double gaps) {
        for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            if (!this.chance(gaps)) {
                this.plan.setIfFree(x, y, z, Mat.RAIL, 0);
            }
        }
    }

    public void decay(int x0, int y0, int z0, int x1, int y1, int z1, Set<Mat> which, double chance) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                    if (which.contains(this.plan.mat(x, y, z)) && this.chance(chance)) {
                        this.plan.set(x, y, z, Mat.AIR);
                    }
                }
            }
        }
    }

    public void patches(int x0, int y0, int z0, int x1, int y1, int z1, Set<Mat> which, int count, int minSize, int maxSize) {
        int sx = Math.abs(x1 - x0) + 1;
        int sy = Math.abs(y1 - y0) + 1;
        int sz = Math.abs(z1 - z0) + 1;
        for (int i = 0; i < count; i++) {
            int w = this.range(minSize, maxSize);
            int h = this.range(minSize, maxSize);
            int bx = Math.min(x0, x1) + (sx > w ? this.random.nextInt(sx - w + 1) : 0);
            int by = Math.min(y0, y1) + (sy > h ? this.random.nextInt(sy - h + 1) : 0);
            int bz = Math.min(z0, z1) + (sz > w ? this.random.nextInt(sz - w + 1) : 0);
            int ex = sx == 1 ? bx : bx + w - 1;
            int ey = sy == 1 ? by : by + h - 1;
            int ez = sz == 1 ? bz : (sy == 1 ? bz + h - 1 : bz + w - 1);
            for (int y = by; y <= ey; y++) {
                for (int z = bz; z <= ez; z++) {
                    for (int x = bx; x <= ex; x++) {
                        if (which.contains(this.plan.mat(x, y, z))) {
                            this.plan.set(x, y, z, Mat.AIR);
                        }
                    }
                }
            }
        }
    }

    public void scatter(int x0, int z0, int x1, int z1, int y, int count, Mat mat) {
        for (int i = 0; i < count; i++) {
            int x = this.range(Math.min(x0, x1), Math.max(x0, x1));
            int z = this.range(Math.min(z0, z1), Math.max(z0, z1));
            int top = this.surface(x, y, z);
            if (top >= 0 && this.bears(x, top, z)) {
                this.plan.setIfFree(x, top + 1, z, mat, 0);
            }
        }
    }

    public boolean bears(int x, int y, int z) {
        Mat mat = this.plan.mat(x, y, z);
        return mat.carries() || mat == Mat.EMPTY && y <= this.g();
    }

    public int surface(int x, int fromY, int z) {
        for (int y = Math.min(fromY, this.plan.sizeY() - 1); y >= 0; y--) {
            Mat mat = this.plan.mat(x, y, z);
            if (mat.holdsUp()) {
                return y;
            }
            if (y <= this.g() && mat == Mat.EMPTY) {
                return y;
            }
        }
        return -1;
    }

    public void vines(int x0, int y0, int z0, int x1, int y1, int z1, double chance, int maxLength) {
        BandoPlan p = this.plan;
        for (int y = Math.max(y0, y1); y >= Math.min(y0, y1); y--) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                    Mat here = p.mat(x, y, z);
                    if (here != Mat.AIR && here != Mat.EMPTY) {
                        continue;
                    }
                    for (int facing = 0; facing < 4; facing++) {
                        if (!CLIMBABLE.contains(p.mat(x + Meta.dx(facing), y, z + Meta.dz(facing))) || !this.chance(chance)) {
                            continue;
                        }
                        int length = this.range(1, maxLength);
                        for (int k = 0; k < length; k++) {
                            int vy = y - k;
                            Mat at = p.mat(x, vy, z);
                            if (vy <= this.g() || (at != Mat.AIR && at != Mat.EMPTY && at != Mat.VINE)
                                    || !CLIMBABLE.contains(p.mat(x + Meta.dx(facing), vy, z + Meta.dz(facing)))) {
                                break;
                            }
                            int faces = at == Mat.VINE ? p.meta(x, vy, z) : 0;
                            p.set(x, vy, z, Mat.VINE, faces | Meta.face(facing));
                        }
                        break;
                    }
                }
            }
        }
    }

    public void moss(int x0, int y0, int z0, int x1, int y1, int z1, Set<Mat> on, double chance) {
        BandoPlan p = this.plan;
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                    Mat above = p.mat(x, y + 1, z);
                    if (on.contains(p.mat(x, y, z)) && p.mat(x, y, z).carries() && (above == Mat.AIR || above == Mat.EMPTY) && p.inside(x, y + 1, z)
                            && this.chance(chance)) {
                        p.set(x, y + 1, z, Mat.MOSS);
                    }
                }
            }
        }
    }

    public void foliage(int x0, int y0, int z0, int x1, int y1, int z1, Set<Mat> on, double chance, int radius) {
        BandoPlan p = this.plan;
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                    if (!on.contains(p.mat(x, y, z)) || !p.mat(x, y, z).flatTop() || !this.chance(chance)) {
                        continue;
                    }
                    int r = this.range(0, radius);
                    for (int dy = 1; dy <= 1 + (r > 0 ? 1 : 0); dy++) {
                        for (int dx = -r; dx <= r; dx++) {
                            for (int dz = -r; dz <= r; dz++) {
                                Mat below = p.mat(x + dx, y + dy - 1, z + dz);
                                boolean rests = dy == 1 ? below.flatTop() : below == Mat.FOLIAGE;
                                if (rests && dx * dx + dz * dz + (dy - 1) * (dy - 1) <= r * r + 1) {
                                    p.setIfFree(x + dx, y + dy, z + dz, Mat.FOLIAGE, 0);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean chest(int x, int y, int z, int facing) {
        BandoPlan p = this.plan;
        Mat below = p.mat(x, y - 1, z);
        boolean supported = this.bears(x, y - 1, z);
        Mat here = p.mat(x, y, z);
        Mat above = p.mat(x, y + 1, z);
        Mat front = p.mat(x + Meta.dx(facing), y, z + Meta.dz(facing));
        if (!supported || (here != Mat.AIR && here != Mat.EMPTY && here != Mat.MOSS) || above.blocksFlight() || front.blocksFlight()) {
            return false;
        }
        p.placeChest(x, y, z, facing);
        return true;
    }

    public void settle(boolean groundHolds) {
        this.cleanDecor();
        BandoPlan p = this.plan;
        int sx = p.sizeX();
        int sy = p.sizeY();
        int sz = p.sizeZ();
        boolean[] grounded = new boolean[sx * sy * sz];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int z = 0; z < sz; z++) {
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    Mat mat = p.mat(x, y, z);
                    if (!mat.holdsUp()) {
                        continue;
                    }
                    boolean seed = (groundHolds && y <= p.ground()) || (y == 0 && p.fill(x, z) != Mat.EMPTY)
                            || (groundHolds && y == p.ground() + 1 && p.mat(x, y - 1, z) == Mat.EMPTY);
                    if (seed) {
                        grounded[(y * sz + z) * sx + x] = true;
                        queue.add(new int[]{x, y, z});
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            Mat from = p.mat(c[0], c[1], c[2]);
            boolean step = from == Mat.ROOF_STEP;
            boolean attached = from.kind() == Mat.Kind.ATTACHED;
            for (int[] d : step ? EDGES : NEIGHBORS) {
                int x = c[0] + d[0];
                int y = c[1] + d[1];
                int z = c[2] + d[2];
                if (!p.inside(x, y, z)) {
                    continue;
                }
                int i = (y * sz + z) * sx + x;
                Mat mat = p.mat(x, y, z);
                if (!grounded[i] && mat.holdsUp() && !(attached && mat.kind() == Mat.Kind.STRUCTURE)
                        && (d[1] == 0 || d[0] == 0 && d[2] == 0 || mat == Mat.ROOF_STEP || mat == Mat.ROOF)) {
                    grounded[i] = true;
                    queue.add(new int[]{x, y, z});
                }
            }
        }
        for (int z = 0; z < sz; z++) {
            for (int x = 0; x < sx; x++) {
                int fallen = 0;
                for (int y = sy - 1; y >= 0; y--) {
                    Mat mat = p.mat(x, y, z);
                    if (mat.holdsUp() && !grounded[(y * sz + z) * sx + x]) {
                        p.set(x, y, z, Mat.AIR);
                        if (mat.kind() == Mat.Kind.STRUCTURE) {
                            fallen++;
                        }
                    }
                }
                if (fallen > 0 && this.chance(0.5)) {
                    int top = this.surface(x, sy - 1, z);
                    if (top >= 0 && top < sy - 1 && (groundHolds ? this.bears(x, top, z) : p.mat(x, top, z).carries())) {
                        p.setIfFree(x, top + 1, z, fallen > 2 && this.chance(0.4) ? Mat.DEBRIS : Mat.DEBRIS_LOW, 0);
                    }
                }
            }
        }
        this.cleanDecor();
    }

    public void anchor() {
        BandoPlan p = this.plan;
        int g = this.g();
        for (int z = 0; z < p.sizeZ(); z++) {
            for (int x = 0; x < p.sizeX(); x++) {
                if (p.fill(x, z) != Mat.EMPTY) {
                    continue;
                }
                for (int y = 0; y <= g + 1 && y < p.sizeY(); y++) {
                    Mat mat = p.mat(x, y, z);
                    if (!mat.holdsUp() || mat == Mat.YARD) {
                        continue;
                    }
                    Mat under = p.mat(x, g, z);
                    if (mat.kind() == Mat.Kind.STRUCTURE && (y <= g || under == Mat.EMPTY || under == Mat.YARD)) {
                        p.setFill(x, z, mat == Mat.COLUMN || mat == Mat.RAIL ? mat : Mat.FOUNDATION);
                    }
                    break;
                }
            }
        }
    }

    public void solidify() {
        BandoPlan p = this.plan;
        for (int y = 0; y < p.sizeY(); y++) {
            for (int z = 0; z < p.sizeZ(); z++) {
                for (int x = 0; x < p.sizeX(); x++) {
                    Mat mat = p.mat(x, y, z);
                    if (!mat.slab()) {
                        continue;
                    }
                    Mat above = p.mat(x, y + 1, z);
                    Mat below = p.mat(x, y - 1, z);
                    boolean solid = mat == Mat.CATWALK ? below.kind() == Mat.Kind.STRUCTURE
                            : above.holdsUp() || above.kind() == Mat.Kind.DECOR && above != Mat.VINE;
                    if (solid) {
                        p.set(x, y, z, mat, p.meta(x, y, z) | Meta.SOLID);
                    }
                }
            }
        }
    }

    public void cleanDecor() {
        BandoPlan p = this.plan;
        for (int y = p.sizeY() - 1; y >= 0; y--) {
            for (int z = 0; z < p.sizeZ(); z++) {
                for (int x = 0; x < p.sizeX(); x++) {
                    Mat mat = p.mat(x, y, z);
                    if (mat == Mat.VINE) {
                        int faces = p.meta(x, y, z);
                        int kept = 0;
                        for (int facing = 0; facing < 4; facing++) {
                            if ((faces & Meta.face(facing)) != 0 && CLIMBABLE.contains(p.mat(x + Meta.dx(facing), y, z + Meta.dz(facing)))) {
                                kept |= Meta.face(facing);
                            }
                        }
                        if (kept == 0) {
                            p.set(x, y, z, Mat.AIR);
                        } else {
                            p.set(x, y, z, Mat.VINE, kept);
                        }
                    } else if (mat == Mat.MOSS) {
                        if (!this.bears(x, y - 1, z)) {
                            p.set(x, y, z, Mat.AIR);
                        }
                    } else if (mat == Mat.LADDER) {
                        int facing = p.meta(x, y, z);
                        if (!CLIMBABLE.contains(p.mat(x - Meta.dx(facing), y, z - Meta.dz(facing)))) {
                            p.set(x, y, z, Mat.AIR);
                        }
                    }
                }
            }
        }
    }
}
