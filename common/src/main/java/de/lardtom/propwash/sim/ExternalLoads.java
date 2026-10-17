package de.lardtom.propwash.sim;

final class ExternalLoads {
    static final int MAX_PUSHES = 16;
    static final double MAX_FORCE = 500.0;
    static final double MAX_TORQUE = 50.0;
    static final double MAX_IMPULSE = 50.0;
    static final double MAX_SECONDS = 30.0;
    static final double MAX_ARM = 2.0;
    static final double WAKE_FORCE = 0.5;
    static final double WAKE_TORQUE = 0.002;

    private final double[] fx = new double[MAX_PUSHES];
    private final double[] fy = new double[MAX_PUSHES];
    private final double[] fz = new double[MAX_PUSHES];
    private final double[] px = new double[MAX_PUSHES];
    private final double[] py = new double[MAX_PUSHES];
    private final double[] pz = new double[MAX_PUSHES];
    private final double[] remaining = new double[MAX_PUSHES];
    private final boolean[] body = new boolean[MAX_PUSHES];
    private int count;

    double providerFx;
    double providerFy;
    double providerFz;
    double providerTx;
    double providerTy;
    double providerTz;
    boolean providerActive;

    double forceX;
    double forceY;
    double forceZ;
    double torqueX;
    double torqueY;
    double torqueZ;

    boolean active() {
        return this.count > 0 || this.providerActive;
    }

    int pushes() {
        return this.count;
    }

    boolean add(double forceX, double forceY, double forceZ, boolean bodyFrame, double pointX, double pointY, double pointZ, double seconds) {
        if (!Double.isFinite(forceX + forceY + forceZ + pointX + pointY + pointZ + seconds) || !(seconds > 0.0)) {
            return false;
        }
        if (this.count >= MAX_PUSHES) {
            return false;
        }
        double magnitude = Math.sqrt(forceX * forceX + forceY * forceY + forceZ * forceZ);
        double scale = magnitude > MAX_FORCE ? MAX_FORCE / magnitude : 1.0;
        double arm = Math.sqrt(pointX * pointX + pointY * pointY + pointZ * pointZ);
        double armScale = arm > MAX_ARM ? MAX_ARM / arm : 1.0;
        int i = this.count++;
        this.fx[i] = forceX * scale;
        this.fy[i] = forceY * scale;
        this.fz[i] = forceZ * scale;
        this.px[i] = pointX * armScale;
        this.py[i] = pointY * armScale;
        this.pz[i] = pointZ * armScale;
        this.body[i] = bodyFrame;
        this.remaining[i] = Math.min(MAX_SECONDS, seconds);
        return true;
    }

    void setProvider(double fx, double fy, double fz, double tx, double ty, double tz) {
        if (!Double.isFinite(fx + fy + fz + tx + ty + tz)) {
            this.clearProvider();
            return;
        }
        double force = Math.sqrt(fx * fx + fy * fy + fz * fz);
        double fs = force > MAX_FORCE ? MAX_FORCE / force : 1.0;
        double torque = Math.sqrt(tx * tx + ty * ty + tz * tz);
        double ts = torque > MAX_TORQUE ? MAX_TORQUE / torque : 1.0;
        this.providerFx = fx * fs;
        this.providerFy = fy * fs;
        this.providerFz = fz * fs;
        this.providerTx = tx * ts;
        this.providerTy = ty * ts;
        this.providerTz = tz * ts;
        this.providerActive = force > 0.0 || torque > 0.0;
    }

    void clearProvider() {
        this.providerFx = 0.0;
        this.providerFy = 0.0;
        this.providerFz = 0.0;
        this.providerTx = 0.0;
        this.providerTy = 0.0;
        this.providerTz = 0.0;
        this.providerActive = false;
    }

    void clear() {
        this.count = 0;
        this.clearProvider();
    }

    void accumulate(RigidBody b, double h) {
        double sx = this.providerFx;
        double sy = this.providerFy;
        double sz = this.providerFz;
        double tx = this.providerTx;
        double ty = this.providerTy;
        double tz = this.providerTz;
        int i = 0;
        while (i < this.count) {
            double wx;
            double wy;
            double wz;
            if (this.body[i]) {
                wx = b.r00 * this.fx[i] + b.r01 * this.fy[i] + b.r02 * this.fz[i];
                wy = b.r10 * this.fx[i] + b.r11 * this.fy[i] + b.r12 * this.fz[i];
                wz = b.r20 * this.fx[i] + b.r21 * this.fy[i] + b.r22 * this.fz[i];
            } else {
                wx = this.fx[i];
                wy = this.fy[i];
                wz = this.fz[i];
            }
            double rx = b.r00 * this.px[i] + b.r01 * this.py[i] + b.r02 * this.pz[i];
            double ry = b.r10 * this.px[i] + b.r11 * this.py[i] + b.r12 * this.pz[i];
            double rz = b.r20 * this.px[i] + b.r21 * this.py[i] + b.r22 * this.pz[i];
            sx += wx;
            sy += wy;
            sz += wz;
            tx += ry * wz - rz * wy;
            ty += rz * wx - rx * wz;
            tz += rx * wy - ry * wx;
            this.remaining[i] -= h;
            if (this.remaining[i] <= 1.0E-12) {
                this.removeAt(i);
            } else {
                i++;
            }
        }
        this.forceX = sx;
        this.forceY = sy;
        this.forceZ = sz;
        this.torqueX = tx;
        this.torqueY = ty;
        this.torqueZ = tz;
    }

    private void removeAt(int i) {
        int last = --this.count;
        if (i == last) {
            return;
        }
        this.fx[i] = this.fx[last];
        this.fy[i] = this.fy[last];
        this.fz[i] = this.fz[last];
        this.px[i] = this.px[last];
        this.py[i] = this.py[last];
        this.pz[i] = this.pz[last];
        this.body[i] = this.body[last];
        this.remaining[i] = this.remaining[last];
    }
}
