package de.lardtom.propwash.sim;

final class RigidBody {
    static final int MIDPOINT_ITERATIONS = 2;

    double px;
    double py;
    double pz;
    double vx;
    double vy;
    double vz;
    double qw = 1.0;
    double qx;
    double qy;
    double qz;
    double lx;
    double ly;
    double lz;

    double r00 = 1.0;
    double r01;
    double r02;
    double r10;
    double r11 = 1.0;
    double r12;
    double r20;
    double r21;
    double r22 = 1.0;
    double wbx;
    double wby;
    double wbz;
    double wwx;
    double wwy;
    double wwz;

    private double midX;
    private double midY;
    private double midZ;

    double mass = 1.0;
    double ixx = 1.0;
    double iyy = 1.0;
    double izz = 1.0;

    void configure(double mass, double ixx, double iyy, double izz) {
        this.mass = mass;
        this.ixx = ixx;
        this.iyy = iyy;
        this.izz = izz;
        this.updateDerived();
    }

    void setUpright(double headingRad) {
        double half = 0.5 * headingRad;
        this.qw = SimMath.cos(half);
        this.qx = 0.0;
        this.qy = SimMath.sin(half);
        this.qz = 0.0;
        this.updateDerived();
    }

    void updateRotation() {
        double w = this.qw;
        double x = this.qx;
        double y = this.qy;
        double z = this.qz;
        this.r00 = 1.0 - 2.0 * (y * y + z * z);
        this.r01 = 2.0 * (x * y - w * z);
        this.r02 = 2.0 * (x * z + w * y);
        this.r10 = 2.0 * (x * y + w * z);
        this.r11 = 1.0 - 2.0 * (x * x + z * z);
        this.r12 = 2.0 * (y * z - w * x);
        this.r20 = 2.0 * (x * z - w * y);
        this.r21 = 2.0 * (y * z + w * x);
        this.r22 = 1.0 - 2.0 * (x * x + y * y);
    }

    void updateRates() {
        double bx = (this.r00 * this.lx + this.r10 * this.ly + this.r20 * this.lz) / this.ixx;
        double by = (this.r01 * this.lx + this.r11 * this.ly + this.r21 * this.lz) / this.iyy;
        double bz = (this.r02 * this.lx + this.r12 * this.ly + this.r22 * this.lz) / this.izz;
        this.wbx = bx;
        this.wby = by;
        this.wbz = bz;
        this.wwx = this.r00 * bx + this.r01 * by + this.r02 * bz;
        this.wwy = this.r10 * bx + this.r11 * by + this.r12 * bz;
        this.wwz = this.r20 * bx + this.r21 * by + this.r22 * bz;
    }

    void updateDerived() {
        this.updateRotation();
        this.updateRates();
    }

    void setBodyRates(double bx, double by, double bz) {
        double lbx = this.ixx * bx;
        double lby = this.iyy * by;
        double lbz = this.izz * bz;
        this.lx = this.r00 * lbx + this.r01 * lby + this.r02 * lbz;
        this.ly = this.r10 * lbx + this.r11 * lby + this.r12 * lbz;
        this.lz = this.r20 * lbx + this.r21 * lby + this.r22 * lbz;
        this.updateRates();
    }

    void integrate(double h, double ax, double ay, double az, double tx, double ty, double tz) {
        this.vx += h * ax;
        this.vy += h * ay;
        this.vz += h * az;
        double speed2 = this.vx * this.vx + this.vy * this.vy + this.vz * this.vz;
        if (speed2 > ModelConstants.V_MAX * ModelConstants.V_MAX) {
            double s = ModelConstants.V_MAX / Math.sqrt(speed2);
            this.vx *= s;
            this.vy *= s;
            this.vz *= s;
        }
        this.px += h * this.vx;
        this.py += h * this.vy;
        this.pz += h * this.vz;
        this.lx += h * tx;
        this.ly += h * ty;
        this.lz += h * tz;
        this.updateRates();
        double wx = this.wwx;
        double wy = this.wwy;
        double wz = this.wwz;
        for (int k = 0; k < MIDPOINT_ITERATIONS; k++) {
            this.midpointRate(0.25 * h, wx, wy, wz);
            wx = this.midX;
            wy = this.midY;
            wz = this.midZ;
        }
        this.rotate(h, wx, wy, wz);
        this.updateDerived();
        this.clampRates();
    }

    private void midpointRate(double halfH, double wx, double wy, double wz) {
        double w = this.qw;
        double x = this.qx;
        double y = this.qy;
        double z = this.qz;
        double dx = halfH * wx;
        double dy = halfH * wy;
        double dz = halfH * wz;
        double mw = w - dx * x - dy * y - dz * z;
        double mx = x + dx * w + dy * z - dz * y;
        double my = y - dx * z + dy * w + dz * x;
        double mz = z + dx * y - dy * x + dz * w;
        double inv = 1.0 / Math.sqrt(mw * mw + mx * mx + my * my + mz * mz);
        mw *= inv;
        mx *= inv;
        my *= inv;
        mz *= inv;
        double a00 = 1.0 - 2.0 * (my * my + mz * mz);
        double a01 = 2.0 * (mx * my - mw * mz);
        double a02 = 2.0 * (mx * mz + mw * my);
        double a10 = 2.0 * (mx * my + mw * mz);
        double a11 = 1.0 - 2.0 * (mx * mx + mz * mz);
        double a12 = 2.0 * (my * mz - mw * mx);
        double a20 = 2.0 * (mx * mz - mw * my);
        double a21 = 2.0 * (my * mz + mw * mx);
        double a22 = 1.0 - 2.0 * (mx * mx + my * my);
        double bx = (a00 * this.lx + a10 * this.ly + a20 * this.lz) / this.ixx;
        double by = (a01 * this.lx + a11 * this.ly + a21 * this.lz) / this.iyy;
        double bz = (a02 * this.lx + a12 * this.ly + a22 * this.lz) / this.izz;
        this.midX = a00 * bx + a01 * by + a02 * bz;
        this.midY = a10 * bx + a11 * by + a12 * bz;
        this.midZ = a20 * bx + a21 * by + a22 * bz;
    }

    void rotate(double h, double wx, double wy, double wz) {
        double rate = SimMath.hypot3(wx, wy, wz);
        double theta = rate * h;
        double dw;
        double dx;
        double dy;
        double dz;
        if (theta < 1e-9) {
            dw = 1.0;
            dx = 0.5 * h * wx;
            dy = 0.5 * h * wy;
            dz = 0.5 * h * wz;
        } else {
            double s = SimMath.sin(0.5 * theta) / rate;
            dw = SimMath.cos(0.5 * theta);
            dx = s * wx;
            dy = s * wy;
            dz = s * wz;
        }
        double w = this.qw;
        double x = this.qx;
        double y = this.qy;
        double z = this.qz;
        double nw = dw * w - dx * x - dy * y - dz * z;
        double nx = dw * x + dx * w + dy * z - dz * y;
        double ny = dw * y - dx * z + dy * w + dz * x;
        double nz = dw * z + dx * y - dy * x + dz * w;
        double norm = Math.sqrt(nw * nw + nx * nx + ny * ny + nz * nz);
        this.qw = nw / norm;
        this.qx = nx / norm;
        this.qy = ny / norm;
        this.qz = nz / norm;
    }

    void clampRates() {
        double rate2 = this.wbx * this.wbx + this.wby * this.wby + this.wbz * this.wbz;
        if (rate2 > ModelConstants.OMEGA_BODY_MAX * ModelConstants.OMEGA_BODY_MAX) {
            double s = ModelConstants.OMEGA_BODY_MAX / Math.sqrt(rate2);
            this.lx *= s;
            this.ly *= s;
            this.lz *= s;
            this.updateRates();
        }
    }

    double tiltRad() {
        return StrictMath.acos(SimMath.clamp(this.r11, -1.0, 1.0));
    }

    double headingRad() {
        return StrictMath.atan2(this.r02, this.r22);
    }

    double kineticEnergy() {
        double lin = 0.5 * this.mass * (this.vx * this.vx + this.vy * this.vy + this.vz * this.vz);
        double rot = 0.5 * (this.ixx * this.wbx * this.wbx + this.iyy * this.wby * this.wby + this.izz * this.wbz * this.wbz);
        return lin + rot;
    }
}
