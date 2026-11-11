package de.lardtom.propwash.sim;

import java.util.Arrays;

final class GroundSupport {
    static final int SIDE = SimEnvironment.SUPPORT_SIDE;
    static final int CELLS = SimEnvironment.SUPPORT_CELLS;
    static final double MATCH = 0.03;
    static final double EDGE_DROP = 0.03;
    static final double MAX_SPACING = 1.0;

    private final double[] height = new double[CELLS];
    final double[] planes = new double[AirframeSpec.GROUND_POINTS];
    private final double[] angles = new double[CELLS];
    private final double[] pointHeight = new double[SimEnvironment.POINT_SUPPORT];
    private final double[] pointX = new double[SimEnvironment.POINT_SUPPORT];
    private final double[] pointZ = new double[SimEnvironment.POINT_SUPPORT];
    private int points;
    private double spacing;
    private double originX;
    private double originZ;
    double edgeX;
    double edgeZ;
    double edgeLevel;

    static void sanitize(SimEnvironment src, SimEnvironment dst) {
        double s = src.supportSpacing;
        if (!(s > 0.0) || !(s <= MAX_SPACING) || !Double.isFinite(src.supportX) || !Double.isFinite(src.supportZ)) {
            dst.clearSupport();
            return;
        }
        dst.supportSpacing = s;
        dst.supportX = src.supportX;
        dst.supportZ = src.supportZ;
        for (int k = 0; k < CELLS; k++) {
            double h = src.support[k];
            dst.support[k] = h == Double.POSITIVE_INFINITY ? Double.NaN : h;
        }
        int n = src.pointCount;
        dst.pointCount = 0;
        if (!(n > 0 && n <= SimEnvironment.POINT_SUPPORT)) {
            return;
        }
        for (int j = 0; j < n; j++) {
            double x = src.pointX[j];
            double z = src.pointZ[j];
            if (!Double.isFinite(x) || !Double.isFinite(z)) {
                return;
            }
            double h = src.pointSupport[j];
            dst.pointX[j] = x;
            dst.pointZ[j] = z;
            dst.pointSupport[j] = h == Double.POSITIVE_INFINITY ? Double.NaN : h;
        }
        dst.pointCount = n;
    }

    void load(SimEnvironment env) {
        this.spacing = env.supportSpacing;
        if (!(this.spacing > 0.0)) {
            return;
        }
        this.originX = env.supportX;
        this.originZ = env.supportZ;
        System.arraycopy(env.support, 0, this.height, 0, CELLS);
        this.points = env.pointCount;
        System.arraycopy(env.pointSupport, 0, this.pointHeight, 0, this.points);
        System.arraycopy(env.pointX, 0, this.pointX, 0, this.points);
        System.arraycopy(env.pointZ, 0, this.pointZ, 0, this.points);
    }

    void forget() {
        this.spacing = 0.0;
    }

    boolean valid() {
        return this.spacing > 0.0;
    }

    double at(double x, double z) {
        long ix = Math.round((x - this.originX) / this.spacing) + SIDE / 2;
        long iz = Math.round((z - this.originZ) / this.spacing) + SIDE / 2;
        if (ix < -1L || ix > SIDE || iz < -1L || iz > SIDE) {
            return Double.NaN;
        }
        int cx = (int) Math.max(0L, Math.min(SIDE - 1L, ix));
        int cz = (int) Math.max(0L, Math.min(SIDE - 1L, iz));
        return this.height[SimEnvironment.supportCell(cx, cz)];
    }

    private double under(int point, double x, double z) {
        double near = 0.5 * this.spacing;
        if (point < this.points && Math.abs(x - this.pointX[point]) <= near && Math.abs(z - this.pointZ[point]) <= near) {
            return this.pointHeight[point];
        }
        return this.at(x, z);
    }

    private static double pick(double plane, double support) {
        if (Double.isNaN(support) || Math.abs(support - plane) <= MATCH) {
            return plane;
        }
        return support;
    }

    double[] groundPlanes(AirframeSpec p, RigidBody b, double plane, boolean overhang) {
        int corners = p.accessoryPointCount();
        if (!overhang && (corners == 0 || !this.valid())) {
            return null;
        }
        int first = overhang ? 0 : AirframeSpec.ACCESSORY_GROUND;
        Arrays.fill(this.planes, 0, first, plane);
        double floor = b.py + p.hitboxFootOffset - SimEnvironment.SUPPORT_RANGE;
        for (int k = first; k < p.groundCount(); k++) {
            double x = b.px + b.r00 * p.groundX(k) + b.r01 * p.groundY(k) + b.r02 * p.groundZ(k);
            double y = b.py + b.r10 * p.groundX(k) + b.r11 * p.groundY(k) + b.r12 * p.groundZ(k);
            double z = b.pz + b.r20 * p.groundX(k) + b.r21 * p.groundY(k) + b.r22 * p.groundZ(k);
            double support = k < AirframeSpec.ACCESSORY_GROUND ? this.at(x, z) : this.under(k - AirframeSpec.ACCESSORY_GROUND, x, z);
            if (!overhang && support == Double.NEGATIVE_INFINITY && y - GroundSolver.REACH < floor) {
                support = Double.NaN;
            }
            this.planes[k] = pick(plane, support);
        }
        if (corners > 0) {
            this.capStandLegs(p, corners);
        }
        return this.planes;
    }

    private void capStandLegs(AirframeSpec p, int corners) {
        int legs = AirframeSpec.ACCESSORY_GROUND + corners;
        if (legs >= p.groundCount()) {
            return;
        }
        double level = p.groundY(legs);
        double reach = Double.NEGATIVE_INFINITY;
        for (int k = AirframeSpec.ACCESSORY_GROUND; k < legs; k++) {
            reach = Math.max(reach, this.planes[k] - (p.groundY(k) - level));
        }
        for (int k = legs; k < p.groundCount(); k++) {
            this.planes[k] = Math.min(this.planes[k], reach);
        }
    }

    double[] hullPlanes(AirframeSpec p, RigidBody b, double plane) {
        for (int k = 0; k < AirframeSpec.HULL_POINTS; k++) {
            double x = b.px + b.r00 * p.hullX(k) + b.r01 * p.hullY(k) + b.r02 * p.hullZ(k);
            double z = b.pz + b.r20 * p.hullX(k) + b.r21 * p.hullY(k) + b.r22 * p.hullZ(k);
            this.planes[k] = pick(plane, this.at(x, z));
        }
        return this.planes;
    }

    boolean overhang(RigidBody b, double level) {
        if (!this.valid()) {
            return false;
        }
        double under = this.at(b.px, b.pz);
        if (Double.isNaN(under) || under >= level - EDGE_DROP) {
            return false;
        }
        double half = 0.5 * this.spacing;
        double best = Double.POSITIVE_INFINITY;
        int n = 0;
        for (int iz = 0; iz < SIDE; iz++) {
            for (int ix = 0; ix < SIDE; ix++) {
                double h = this.height[SimEnvironment.supportCell(ix, iz)];
                if (!(h >= level - EDGE_DROP) || !(h <= level + MATCH)) {
                    continue;
                }
                double cx = this.originX + SimEnvironment.supportOffset(ix, this.spacing);
                double cz = this.originZ + SimEnvironment.supportOffset(iz, this.spacing);
                double dx = cx - b.px;
                double dz = cz - b.pz;
                if (dx * dx + dz * dz < 1.0E-12) {
                    return false;
                }
                this.angles[n++] = StrictMath.atan2(dz, dx);
                double ex = SimMath.clamp(b.px, cx - half, cx + half);
                double ez = SimMath.clamp(b.pz, cz - half, cz + half);
                double d = (ex - b.px) * (ex - b.px) + (ez - b.pz) * (ez - b.pz);
                if (d < best) {
                    best = d;
                    this.edgeX = ex;
                    this.edgeZ = ez;
                    this.edgeLevel = h;
                }
            }
        }
        if (!(best > 1.0E-12) || best == Double.POSITIVE_INFINITY) {
            return false;
        }
        return !surrounded(this.angles, n);
    }

    static boolean surrounded(double[] angles, int n) {
        if (n < 2) {
            return false;
        }
        Arrays.sort(angles, 0, n);
        double gap = angles[0] + 2.0 * Math.PI - angles[n - 1];
        for (int i = 1; i < n; i++) {
            gap = Math.max(gap, angles[i] - angles[i - 1]);
        }
        return gap <= Math.PI + 1.0E-9;
    }
}
