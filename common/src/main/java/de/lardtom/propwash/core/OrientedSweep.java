package de.lardtom.propwash.core;

import java.util.List;

public final class OrientedSweep {
    public static final double SKIN = 1.0E-5;
    public static final int MAX_PASSES = 4;
    private static final double AXIS_EPSILON = 1.0E-12;
    private static final double MOTION_EPSILON = 1.0E-12;

    private OrientedSweep() {
    }

    public record Hit(double time, double nx, double ny, double nz, boolean inside) {
    }

    public static final class Result {
        public double dx;
        public double dy;
        public double dz;
        public boolean hit;
        public double nx;
        public double ny;
        public double nz;
        public int contacts;

        public void clear() {
            this.dx = 0.0;
            this.dy = 0.0;
            this.dz = 0.0;
            this.hit = false;
            this.nx = 0.0;
            this.ny = 0.0;
            this.nz = 0.0;
            this.contacts = 0;
        }
    }

    public static Hit sweep(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double dx, double dy, double dz,
                            OrientedBox target) {
        double hx = (maxX - minX) * 0.5;
        double hy = (maxY - minY) * 0.5;
        double hz = (maxZ - minZ) * 0.5;
        double sx = (minX + maxX) * 0.5 - target.cx();
        double sy = (minY + maxY) * 0.5 - target.cy();
        double sz = (minZ + maxZ) * 0.5 - target.cz();
        double enter = Double.NEGATIVE_INFINITY;
        double exit = Double.POSITIVE_INFINITY;
        double enterX = 0.0;
        double enterY = 0.0;
        double enterZ = 0.0;
        double leastDepth = Double.POSITIVE_INFINITY;
        double leastX = 0.0;
        double leastY = 0.0;
        double leastZ = 0.0;
        for (int k = 0; k < 15; k++) {
            double lx;
            double ly;
            double lz;
            if (k < 3) {
                lx = k == 0 ? 1.0 : 0.0;
                ly = k == 1 ? 1.0 : 0.0;
                lz = k == 2 ? 1.0 : 0.0;
            } else if (k < 6) {
                lx = target.axisX(k - 3);
                ly = target.axisY(k - 3);
                lz = target.axisZ(k - 3);
            } else {
                int world = (k - 6) / 3;
                int own = (k - 6) % 3;
                double ax = target.axisX(own);
                double ay = target.axisY(own);
                double az = target.axisZ(own);
                lx = world == 0 ? 0.0 : world == 1 ? az : -ay;
                ly = world == 0 ? -az : world == 1 ? 0.0 : ax;
                lz = world == 0 ? ay : world == 1 ? -ax : 0.0;
                double length = lx * lx + ly * ly + lz * lz;
                if (length < AXIS_EPSILON) {
                    continue;
                }
                double inverse = 1.0 / Math.sqrt(length);
                lx *= inverse;
                ly *= inverse;
                lz *= inverse;
            }
            double reach = hx * Math.abs(lx) + hy * Math.abs(ly) + hz * Math.abs(lz) + target.radius(lx, ly, lz);
            double offset = sx * lx + sy * ly + sz * lz;
            double speed = dx * lx + dy * ly + dz * lz;
            double depth = reach - Math.abs(offset);
            if (depth < leastDepth) {
                leastDepth = depth;
                double sign = offset >= 0.0 ? 1.0 : -1.0;
                leastX = lx * sign;
                leastY = ly * sign;
                leastZ = lz * sign;
            }
            if (Math.abs(speed) < MOTION_EPSILON) {
                if (depth <= 0.0) {
                    return null;
                }
                continue;
            }
            double t0 = (-reach - offset) / speed;
            double t1 = (reach - offset) / speed;
            double in = Math.min(t0, t1);
            double out = Math.max(t0, t1);
            if (in > enter) {
                enter = in;
                double sign = speed > 0.0 ? -1.0 : 1.0;
                enterX = lx * sign;
                enterY = ly * sign;
                enterZ = lz * sign;
            }
            exit = Math.min(exit, out);
            if (enter >= exit || exit <= 0.0 || enter >= 1.0) {
                return null;
            }
        }
        if (enter >= exit || exit <= 0.0 || enter >= 1.0) {
            return null;
        }
        if (enter < 0.0) {
            if (leastDepth <= 0.0 || dx * leastX + dy * leastY + dz * leastZ >= 0.0) {
                return null;
            }
            return new Hit(0.0, leastX, leastY, leastZ, true);
        }
        return new Hit(enter, enterX, enterY, enterZ, false);
    }

    public static void clip(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double dx, double dy, double dz,
                            List<OrientedBox> boxes, Result out) {
        out.clear();
        double ox = 0.0;
        double oy = 0.0;
        double oz = 0.0;
        double rx = dx;
        double ry = dy;
        double rz = dz;
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            if (rx * rx + ry * ry + rz * rz < MOTION_EPSILON) {
                rx = 0.0;
                ry = 0.0;
                rz = 0.0;
                break;
            }
            Hit best = null;
            for (OrientedBox box : boxes) {
                Hit hit = sweep(minX + ox, minY + oy, minZ + oz, maxX + ox, maxY + oy, maxZ + oz, rx, ry, rz, box);
                if (hit != null && (best == null || hit.time() < best.time())) {
                    best = hit;
                }
            }
            if (best == null) {
                ox += rx;
                oy += ry;
                oz += rz;
                rx = 0.0;
                ry = 0.0;
                rz = 0.0;
                break;
            }
            if (!out.hit) {
                out.hit = true;
                out.nx = best.nx();
                out.ny = best.ny();
                out.nz = best.nz();
            }
            out.contacts++;
            double approach = -(rx * best.nx() + ry * best.ny() + rz * best.nz());
            double fraction = best.time();
            if (approach > 0.0 && !best.inside()) {
                fraction = Math.max(0.0, fraction - SKIN / approach);
            }
            ox += rx * fraction;
            oy += ry * fraction;
            oz += rz * fraction;
            rx *= 1.0 - fraction;
            ry *= 1.0 - fraction;
            rz *= 1.0 - fraction;
            double into = rx * best.nx() + ry * best.ny() + rz * best.nz();
            if (into < 0.0) {
                rx -= best.nx() * into;
                ry -= best.ny() * into;
                rz -= best.nz() * into;
            }
            if (pass == MAX_PASSES - 1) {
                rx = 0.0;
                ry = 0.0;
                rz = 0.0;
            }
        }
        out.dx = ox + rx;
        out.dy = oy + ry;
        out.dz = oz + rz;
    }
}
