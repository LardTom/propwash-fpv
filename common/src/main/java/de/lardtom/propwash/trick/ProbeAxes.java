package de.lardtom.propwash.trick;

public final class ProbeAxes {
    public static final double MIN_TRAVEL_SPEED = 1.0;
    private static final double VERTICAL_LIMIT = 0.2;

    private ProbeAxes() {
    }

    public static void compute(TrickSample s, double[] out) {
        double speed = s.speed();
        double dx;
        double dy;
        double dz;
        if (speed >= MIN_TRAVEL_SPEED) {
            dx = s.vx / speed;
            dy = s.vy / speed;
            dz = s.vz / speed;
        } else {
            double fx = 2.0 * (s.qx * s.qz + s.qw * s.qy);
            double fz = 1.0 - 2.0 * (s.qx * s.qx + s.qy * s.qy);
            double h = Math.sqrt(fx * fx + fz * fz);
            dx = h > 1.0E-6 ? fx / h : 0.0;
            dy = 0.0;
            dz = h > 1.0E-6 ? fz / h : 1.0;
        }
        double ex = -dz;
        double ey = 0.0;
        double ez = dx;
        double len = Math.sqrt(ex * ex + ez * ez);
        if (len < VERTICAL_LIMIT) {
            ex = 1.0;
            ez = 0.0;
        } else {
            ex /= len;
            ez /= len;
        }
        double ux = ey * dz - ez * dy;
        double uy = ez * dx - ex * dz;
        double uz = ex * dy - ey * dx;
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1.0E-9) {
            ux = 0.0;
            uy = 0.0;
            uz = 1.0;
        } else {
            ux /= ul;
            uy /= ul;
            uz /= ul;
        }
        if (uy < 0.0 || (uy == 0.0 && uz < 0.0)) {
            ux = -ux;
            uy = -uy;
            uz = -uz;
        }
        set(out, 0, ex, ey, ez);
        set(out, 1, -ex, -ey, -ez);
        set(out, 2, ux, uy, uz);
        set(out, 3, -ux, -uy, -uz);
    }

    private static void set(double[] out, int index, double x, double y, double z) {
        out[index * 3] = x;
        out[index * 3 + 1] = y;
        out[index * 3 + 2] = z;
    }
}
