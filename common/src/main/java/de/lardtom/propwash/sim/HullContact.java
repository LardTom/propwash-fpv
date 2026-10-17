package de.lardtom.propwash.sim;

final class HullContact {
    static final double STATIC_SINK = 0.002;
    static final double DAMPING_RATIO = 1.0;
    static final double SLIP_SPEED = 0.05;
    static final double ROLLING_DAMPING = 0.02;

    double fx;
    double fy;
    double fz;
    double tx;
    double ty;
    double tz;
    double normal;
    int points;

    void clear() {
        this.fx = 0.0;
        this.fy = 0.0;
        this.fz = 0.0;
        this.tx = 0.0;
        this.ty = 0.0;
        this.tz = 0.0;
        this.normal = 0.0;
        this.points = 0;
    }

    static double lowest(AirframeSpec p, RigidBody b) {
        double low = Double.POSITIVE_INFINITY;
        for (int k = 0; k < AirframeSpec.HULL_POINTS; k++) {
            low = Math.min(low, b.r10 * p.hullX(k) + b.r11 * p.hullY(k) + b.r12 * p.hullZ(k));
        }
        return low;
    }

    void solve(AirframeSpec p, RigidBody b, double groundY, double mu, double gravity) {
        this.clear();
        double m = p.massKg;
        double k = m * gravity / STATIC_SINK;
        double c = 2.0 * DAMPING_RATIO * Math.sqrt(k * m);
        for (int i = 0; i < AirframeSpec.HULL_POINTS; i++) {
            double hx = p.hullX(i);
            double hy = p.hullY(i);
            double hz = p.hullZ(i);
            double rx = b.r00 * hx + b.r01 * hy + b.r02 * hz;
            double ry = b.r10 * hx + b.r11 * hy + b.r12 * hz;
            double rz = b.r20 * hx + b.r21 * hy + b.r22 * hz;
            double depth = groundY - (b.py + ry);
            if (depth <= 0.0) {
                continue;
            }
            double vx = b.vx + b.wwy * rz - b.wwz * ry;
            double vy = b.vy + b.wwz * rx - b.wwx * rz;
            double vz = b.vz + b.wwx * ry - b.wwy * rx;
            double n = k * depth - c * vy;
            if (n <= 0.0) {
                continue;
            }
            double slide = Math.sqrt(vx * vx + vz * vz);
            double gx = 0.0;
            double gz = 0.0;
            if (slide > 1e-9) {
                double f = mu * n * StrictMath.tanh(slide / SLIP_SPEED) / slide;
                gx = -f * vx;
                gz = -f * vz;
            }
            this.fx += gx;
            this.fy += n;
            this.fz += gz;
            this.tx += ry * gz - rz * n;
            this.ty += rz * gx - rx * gz;
            this.tz += rx * n - ry * gx;
            this.normal += n;
            this.points++;
        }
        if (this.points > 0) {
            double roll = ROLLING_DAMPING * this.normal * p.motorOffset;
            this.tx -= roll * b.wwx;
            this.ty -= roll * b.wwy;
            this.tz -= roll * b.wwz;
        }
    }
}
