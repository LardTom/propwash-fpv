package de.lardtom.propwash.sim;

final class WakeField {
    static final int CAPACITY = 32;
    static final int EMIT_INTERVAL = 80;
    static final double MIN_AGE = 0.15;
    static final double VELOCITY_TAU = 0.8;
    static final double STRENGTH_TAU = 1.2;
    static final double GROWTH = 0.8;
    static final double DISCARD = 0.03;

    private final double[] cx = new double[CAPACITY];
    private final double[] cy = new double[CAPACITY];
    private final double[] cz = new double[CAPACITY];
    private final double[] vx = new double[CAPACITY];
    private final double[] vy = new double[CAPACITY];
    private final double[] vz = new double[CAPACITY];
    private final double[] strength = new double[CAPACITY];
    private final double[] radius0 = new double[CAPACITY];
    private final double[] age = new double[CAPACITY];
    private final long[] slotSeq = new long[CAPACITY];
    private final boolean[] alive = new boolean[CAPACITY];
    private long sequence;
    private double velocityDecay = 1.0;
    private double strengthDecay = 1.0;
    private double updateDt;

    void configure(double updateDt) {
        this.updateDt = updateDt;
        this.velocityDecay = StrictMath.exp(-updateDt / VELOCITY_TAU);
        this.strengthDecay = StrictMath.exp(-updateDt / STRENGTH_TAU);
    }

    void reset() {
        for (int k = 0; k < CAPACITY; k++) {
            this.alive[k] = false;
            this.slotSeq[k] = -10;
        }
        this.sequence = 0;
    }

    int aliveCount() {
        int n = 0;
        for (int k = 0; k < CAPACITY; k++) {
            if (this.alive[k]) {
                n++;
            }
        }
        return n;
    }

    void emit(boolean enabled, AirframeSpec p, double rho, double px, double py, double pz, double ux, double uy, double uz,
              double windX, double windY, double windZ, double totalThrust, double meanAxial) {
        long seq = this.sequence++;
        int slot = (int) (seq % CAPACITY);
        this.slotSeq[slot] = seq;
        double weight = p.massKg * ModelConstants.G;
        if (!enabled || totalThrust <= 0.15 * weight) {
            this.alive[slot] = false;
            return;
        }
        double vc = Math.max(0.0, meanAxial);
        double induced = -vc / 2.0 + Math.sqrt(vc * vc / 4.0 + totalThrust / (8.0 * rho * p.discArea));
        double offset = 0.5 * p.propDiameter;
        this.cx[slot] = px - offset * ux;
        this.cy[slot] = py - offset * uy;
        this.cz[slot] = pz - offset * uz;
        this.vx[slot] = windX - 2.0 * induced * ux;
        this.vy[slot] = windY - 2.0 * induced * uy;
        this.vz[slot] = windZ - 2.0 * induced * uz;
        this.strength[slot] = SimMath.clamp(totalThrust / weight, 0.0, 2.0) / 2.0;
        this.radius0[slot] = p.motorOffset + p.propRadius;
        this.age[slot] = 0.0;
        this.alive[slot] = true;
    }

    void advance(double windX, double windY, double windZ) {
        double dt = this.updateDt;
        for (int k = 0; k < CAPACITY; k++) {
            if (!this.alive[k]) {
                continue;
            }
            this.cx[k] += this.vx[k] * dt;
            this.cy[k] += this.vy[k] * dt;
            this.cz[k] += this.vz[k] * dt;
            this.vx[k] = windX + (this.vx[k] - windX) * this.velocityDecay;
            this.vy[k] = windY + (this.vy[k] - windY) * this.velocityDecay;
            this.vz[k] = windZ + (this.vz[k] - windZ) * this.velocityDecay;
            this.age[k] += dt;
            this.strength[k] *= this.strengthDecay;
            if (this.strength[k] < DISCARD) {
                this.alive[k] = false;
            }
        }
    }

    void shift(double dx, double dy, double dz) {
        for (int k = 0; k < CAPACITY; k++) {
            this.cx[k] += dx;
            this.cy[k] += dy;
            this.cz[k] += dz;
        }
    }

    private boolean usable(int k) {
        return this.alive[k] && this.age[k] >= MIN_AGE;
    }

    double query(double px, double py, double pz, double dvx, double dvy, double dvz, double ux, double uy, double uz, double vh) {
        double best = 0.0;
        for (int a = 0; a < CAPACITY; a++) {
            if (!this.usable(a)) {
                continue;
            }
            int b = (a + 1) % CAPACITY;
            boolean pair = this.usable(b) && this.slotSeq[b] == this.slotSeq[a] + 1;
            if (!pair) {
                b = a;
            }
            double ex = this.cx[b] - this.cx[a];
            double ey = this.cy[b] - this.cy[a];
            double ez = this.cz[b] - this.cz[a];
            double len2 = ex * ex + ey * ey + ez * ez;
            double t = 0.0;
            if (len2 > 1e-12) {
                t = SimMath.clamp01(((px - this.cx[a]) * ex + (py - this.cy[a]) * ey + (pz - this.cz[a]) * ez) / len2);
            }
            double qx = px - (this.cx[a] + t * ex);
            double qy = py - (this.cy[a] + t * ey);
            double qz = pz - (this.cz[a] + t * ez);
            double dist = Math.sqrt(qx * qx + qy * qy + qz * qz);
            double r = this.radius0[a] * (1.0 + GROWTH * this.age[a]) * (1.0 - t)
                    + this.radius0[b] * (1.0 + GROWTH * this.age[b]) * t;
            double st = this.strength[a] * (1.0 - t) + this.strength[b] * t;
            double overlap = 1.0 - SimMath.smoothstep(0.6 * r, 1.3 * r, dist);
            if (overlap <= 0.0) {
                continue;
            }
            double wx = this.vx[a] * (1.0 - t) + this.vx[b] * t;
            double wy = this.vy[a] * (1.0 - t) + this.vy[b] * t;
            double wz = this.vz[a] * (1.0 - t) + this.vz[b] * t;
            double rel = ((wx - dvx) * ux + (wy - dvy) * uy + (wz - dvz) * uz) / (0.5 * vh);
            double gamma = 0.25 + 0.75 * SimMath.clamp01(rel);
            double w = st * overlap * gamma;
            if (w > best) {
                best = w;
            }
        }
        return best;
    }
}
