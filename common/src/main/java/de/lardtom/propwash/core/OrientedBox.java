package de.lardtom.propwash.core;

public record OrientedBox(double cx, double cy, double cz,
                          double ux, double uy, double uz,
                          double vx, double vy, double vz,
                          double wx, double wy, double wz,
                          double hu, double hv, double hw) {
    public static final double NO_HIT = -1.0;
    private static final double ALIGN_EPSILON = 1.0E-9;
    private static final double AXIS_EPSILON = 1.0E-12;

    public static OrientedBox ofBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new OrientedBox((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0,
                (maxX - minX) * 0.5, (maxY - minY) * 0.5, (maxZ - minZ) * 0.5);
    }

    public double extentX() {
        return Math.abs(this.ux) * this.hu + Math.abs(this.vx) * this.hv + Math.abs(this.wx) * this.hw;
    }

    public double extentY() {
        return Math.abs(this.uy) * this.hu + Math.abs(this.vy) * this.hv + Math.abs(this.wy) * this.hw;
    }

    public double extentZ() {
        return Math.abs(this.uz) * this.hu + Math.abs(this.vz) * this.hv + Math.abs(this.wz) * this.hw;
    }

    public double minX() {
        return this.cx - this.extentX();
    }

    public double minY() {
        return this.cy - this.extentY();
    }

    public double minZ() {
        return this.cz - this.extentZ();
    }

    public double maxX() {
        return this.cx + this.extentX();
    }

    public double maxY() {
        return this.cy + this.extentY();
    }

    public double maxZ() {
        return this.cz + this.extentZ();
    }

    public boolean aligned() {
        return single(this.ux, this.uy, this.uz) && single(this.vx, this.vy, this.vz) && single(this.wx, this.wy, this.wz);
    }

    private static boolean single(double x, double y, double z) {
        int zero = 0;
        if (Math.abs(x) < ALIGN_EPSILON) {
            zero++;
        }
        if (Math.abs(y) < ALIGN_EPSILON) {
            zero++;
        }
        if (Math.abs(z) < ALIGN_EPSILON) {
            zero++;
        }
        return zero == 2;
    }

    public double radius(double lx, double ly, double lz) {
        return this.hu * Math.abs(this.ux * lx + this.uy * ly + this.uz * lz)
                + this.hv * Math.abs(this.vx * lx + this.vy * ly + this.vz * lz)
                + this.hw * Math.abs(this.wx * lx + this.wy * ly + this.wz * lz);
    }

    public double axisX(int axis) {
        return axis == 0 ? this.ux : axis == 1 ? this.vx : this.wx;
    }

    public double axisY(int axis) {
        return axis == 0 ? this.uy : axis == 1 ? this.vy : this.wy;
    }

    public double axisZ(int axis) {
        return axis == 0 ? this.uz : axis == 1 ? this.vz : this.wz;
    }

    public double half(int axis) {
        return axis == 0 ? this.hu : axis == 1 ? this.hv : this.hw;
    }

    public OrientedBox moved(double dx, double dy, double dz) {
        return new OrientedBox(this.cx + dx, this.cy + dy, this.cz + dz, this.ux, this.uy, this.uz, this.vx, this.vy, this.vz, this.wx, this.wy,
                this.wz, this.hu, this.hv, this.hw);
    }

    public OrientedBox deflated(double amount) {
        return new OrientedBox(this.cx, this.cy, this.cz, this.ux, this.uy, this.uz, this.vx, this.vy, this.vz, this.wx, this.wy, this.wz,
                Math.max(0.0, this.hu - amount), Math.max(0.0, this.hv - amount), Math.max(0.0, this.hw - amount));
    }

    public boolean contains(double px, double py, double pz) {
        double dx = px - this.cx;
        double dy = py - this.cy;
        double dz = pz - this.cz;
        return Math.abs(dx * this.ux + dy * this.uy + dz * this.uz) < this.hu
                && Math.abs(dx * this.vx + dy * this.vy + dz * this.vz) < this.hv
                && Math.abs(dx * this.wx + dy * this.wy + dz * this.wz) < this.hw;
    }

    public boolean intersects(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return this.intersects(ofBounds(minX, minY, minZ, maxX, maxY, maxZ));
    }

    public boolean intersects(OrientedBox other) {
        double tx = other.cx - this.cx;
        double ty = other.cy - this.cy;
        double tz = other.cz - this.cz;
        for (int i = 0; i < 3; i++) {
            if (separates(this, other, this.axisX(i), this.axisY(i), this.axisZ(i), tx, ty, tz)
                    || separates(this, other, other.axisX(i), other.axisY(i), other.axisZ(i), tx, ty, tz)) {
                return false;
            }
        }
        for (int i = 0; i < 3; i++) {
            double ax = this.axisX(i);
            double ay = this.axisY(i);
            double az = this.axisZ(i);
            for (int j = 0; j < 3; j++) {
                double bx = other.axisX(j);
                double by = other.axisY(j);
                double bz = other.axisZ(j);
                double lx = ay * bz - az * by;
                double ly = az * bx - ax * bz;
                double lz = ax * by - ay * bx;
                if (lx * lx + ly * ly + lz * lz > AXIS_EPSILON && separates(this, other, lx, ly, lz, tx, ty, tz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean separates(OrientedBox a, OrientedBox b, double lx, double ly, double lz, double tx, double ty, double tz) {
        double distance = Math.abs(tx * lx + ty * ly + tz * lz);
        return distance >= a.radius(lx, ly, lz) + b.radius(lx, ly, lz);
    }

    public double clip(double x0, double y0, double z0, double x1, double y1, double z1) {
        double px = x0 - this.cx;
        double py = y0 - this.cy;
        double pz = z0 - this.cz;
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double enter = 0.0;
        double exit = 1.0;
        for (int i = 0; i < 3; i++) {
            double ax = this.axisX(i);
            double ay = this.axisY(i);
            double az = this.axisZ(i);
            double start = px * ax + py * ay + pz * az;
            double speed = dx * ax + dy * ay + dz * az;
            double half = this.half(i);
            if (Math.abs(speed) < AXIS_EPSILON) {
                if (Math.abs(start) > half) {
                    return NO_HIT;
                }
                continue;
            }
            double t0 = (-half - start) / speed;
            double t1 = (half - start) / speed;
            if (t0 > t1) {
                double swap = t0;
                t0 = t1;
                t1 = swap;
            }
            enter = Math.max(enter, t0);
            exit = Math.min(exit, t1);
            if (enter > exit) {
                return NO_HIT;
            }
        }
        return enter;
    }
}
