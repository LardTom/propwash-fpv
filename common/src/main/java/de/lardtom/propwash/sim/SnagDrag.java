package de.lardtom.propwash.sim;

final class SnagDrag {
    static final double MAX_STEP = 0.5;
    static final double STICK_SPEED = 0.05;

    double ax;
    double ay;
    double az;
    double tx;
    double ty;
    double tz;

    void compute(RigidBody b, AirframeSpec p, SnagField f, double h, double freeX, double freeY, double freeZ) {
        this.ax = 0.0;
        this.ay = 0.0;
        this.az = 0.0;
        this.tx = 0.0;
        this.ty = 0.0;
        this.tz = 0.0;
        double vx = b.vx;
        double vy = b.vy;
        double vz = b.vz;
        double wx = b.wwx;
        double wy = b.wwy;
        double wz = b.wwz;
        double rate = 0.0;
        double spin = 0.0;
        for (int i = 0; i < SnagField.POINTS; i++) {
            double lin = f.linear[i];
            double quad = f.quadratic[i];
            if (lin <= 0.0 && quad <= 0.0) {
                continue;
            }
            double rx = 0.0;
            double ry = 0.0;
            double rz = 0.0;
            if (i != SnagField.CENTER) {
                double mx = p.motorX(i);
                double my = p.motorY(i);
                double mz = p.motorZ(i);
                rx = b.r00 * mx + b.r01 * my + b.r02 * mz;
                ry = b.r10 * mx + b.r11 * my + b.r12 * mz;
                rz = b.r20 * mx + b.r21 * my + b.r22 * mz;
            }
            double pvx = vx + (wy * rz - wz * ry);
            double pvy = vy + (wz * rx - wx * rz);
            double pvz = vz + (wx * ry - wy * rx);
            double speed = Math.sqrt(pvx * pvx + pvy * pvy + pvz * pvz);
            double k = lin + quad * speed;
            rate += k;
            spin += k * (rx * rx + ry * ry + rz * rz);
            double fx = -k * pvx;
            double fy = -k * pvy;
            double fz = -k * pvz;
            this.ax += fx;
            this.ay += fy;
            this.az += fz;
            this.tx += ry * fz - rz * fy;
            this.ty += rz * fx - rx * fz;
            this.tz += rx * fy - ry * fx;
        }
        double m = p.massKg;
        if (rate * h > MAX_STEP) {
            double scale = MAX_STEP / (rate * h);
            this.ax *= scale;
            this.ay *= scale;
            this.az *= scale;
        }
        double inertia = Math.max(1.0E-9, Math.min(p.inertiaXx, Math.min(p.inertiaYy, p.inertiaZz)));
        double angular = spin * m / inertia;
        double torqueScale = angular * h > MAX_STEP ? MAX_STEP / (angular * h) : 1.0;
        this.tx *= m * torqueScale;
        this.ty *= m * torqueScale;
        this.tz *= m * torqueScale;
        double hold = f.hold;
        if (hold <= 0.0) {
            return;
        }
        double sx = freeX + this.ax;
        double sy = freeY + this.ay;
        double sz = freeZ + this.az;
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (speed > STICK_SPEED) {
            double along = speed + h * (sx * vx + sy * vy + sz * vz) / speed;
            double dec = Math.min(hold, Math.max(0.0, along) / h);
            this.ax -= dec * vx / speed;
            this.ay -= dec * vy / speed;
            this.az -= dec * vz / speed;
            return;
        }
        double free = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (free <= hold) {
            this.ax = -freeX - vx / h;
            this.ay = -freeY - vy / h;
            this.az = -freeZ - vz / h;
            return;
        }
        this.ax -= hold * sx / free;
        this.ay -= hold * sy / free;
        this.az -= hold * sz / free;
    }
}
