package de.lardtom.propwash.trick;

final class ManeuverLog {
    static final int CAPACITY = 2048;
    static final double MIN_SLOT = 1.0 / 250.0;

    final double[] time = new double[CAPACITY];
    final double[] dt = new double[CAPACITY];
    final double[] pitch = new double[CAPACITY];
    final double[] roll = new double[CAPACITY];
    final double[] yaw = new double[CAPACITY];
    final double[] up = new double[CAPACITY];
    final double[] upX = new double[CAPACITY];
    final double[] upZ = new double[CAPACITY];
    final double[] fwdX = new double[CAPACITY];
    final double[] fwdY = new double[CAPACITY];
    final double[] fwdZ = new double[CAPACITY];
    final double[] x = new double[CAPACITY];
    final double[] y = new double[CAPACITY];
    final double[] z = new double[CAPACITY];
    final double[] vx = new double[CAPACITY];
    final double[] vy = new double[CAPACITY];
    final double[] vz = new double[CAPACITY];
    final double[] throttle = new double[CAPACITY];
    final boolean[] mode3d = new boolean[CAPACITY];
    final double[] nearest = new double[CAPACITY];
    final boolean[] contact = new boolean[CAPACITY];
    int size;

    ManeuverLog() {
    }

    void clear() {
        this.size = 0;
    }

    boolean full() {
        return this.size >= CAPACITY;
    }

    double start(int i) {
        return this.time[i] - this.dt[i];
    }

    double duration() {
        return this.size == 0 ? 0.0 : this.time[this.size - 1] - this.start(0);
    }

    void add(TrickSample s, double frameDt, double dPitch, double dRoll, double dYaw, double ux, double upY, double uz, double fx, double fy,
             double fz, double near, boolean touched) {
        int i;
        if (this.size >= 2 && frameDt > 0.0 && this.dt[this.size - 1] < MIN_SLOT) {
            i = this.size - 1;
            this.dt[i] += frameDt;
            this.pitch[i] += dPitch;
            this.roll[i] += dRoll;
            this.yaw[i] += dYaw;
            this.nearest[i] = Math.min(this.nearest[i], near);
            this.contact[i] |= touched;
        } else {
            if (this.full()) {
                return;
            }
            i = this.size++;
            this.dt[i] = frameDt;
            this.pitch[i] = dPitch;
            this.roll[i] = dRoll;
            this.yaw[i] = dYaw;
            this.nearest[i] = near;
            this.contact[i] = touched;
        }
        this.time[i] = s.time;
        this.up[i] = upY;
        this.upX[i] = ux;
        this.upZ[i] = uz;
        this.fwdX[i] = fx;
        this.fwdY[i] = fy;
        this.fwdZ[i] = fz;
        this.x[i] = s.x;
        this.y[i] = s.y;
        this.z[i] = s.z;
        this.vx[i] = s.vx;
        this.vy[i] = s.vy;
        this.vz[i] = s.vz;
        this.throttle[i] = s.throttle;
        this.mode3d[i] = s.mode3d;
    }

    double speed(int i) {
        return Math.sqrt(this.vx[i] * this.vx[i] + this.vy[i] * this.vy[i] + this.vz[i] * this.vz[i]);
    }

    double horizontalSpeed(int i) {
        return Math.hypot(this.vx[i], this.vz[i]);
    }
}
