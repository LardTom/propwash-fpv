package de.lardtom.propwash.sim;

final class GpsReceiver {
    static final double NAV_DT = 0.01;
    static final int SAMPLE_TICKS = 10;
    static final int LATENCY_TICKS = 18;
    static final int FIX_SATELLITES = 5;
    static final double OPEN_SKY_SATELLITES = 18.0;
    static final double ACQUIRE_PER_SECOND = 1.1;
    static final double LOSE_PER_SECOND = 5.0;
    static final double BIAS_TAU = 45.0;
    static final double BIAS_METERS = 1.3;
    static final double NOISE_METERS = 0.4;
    static final double VELOCITY_NOISE = 0.08;
    static final double VERTICAL_FACTOR = 1.6;
    private static final int HISTORY = 32;

    private final double[] hx = new double[HISTORY];
    private final double[] hy = new double[HISTORY];
    private final double[] hz = new double[HISTORY];
    private final double[] hvx = new double[HISTORY];
    private final double[] hvy = new double[HISTORY];
    private final double[] hvz = new double[HISTORY];
    private final GaussMarkov biasX = new GaussMarkov();
    private final GaussMarkov biasY = new GaussMarkov();
    private final GaussMarkov biasZ = new GaussMarkov();
    private final GaussMarkov constellation = new GaussMarkov();
    private int head;
    private int filled;
    private long ticks;
    private double sky = 1.0;

    double tracked;
    int satellites;
    boolean fix;
    double hdop = 99.9;
    boolean sampleReady;
    boolean hasSample;
    double x;
    double y;
    double z;
    double vx;
    double vy;
    double vz;

    GpsReceiver() {
        this.biasX.configure(BIAS_TAU, NAV_DT);
        this.biasY.configure(BIAS_TAU, NAV_DT);
        this.biasZ.configure(BIAS_TAU, NAV_DT);
        this.constellation.configure(150.0, NAV_DT);
    }

    void seed(SimRandom rng) {
        this.biasX.randomize(rng);
        this.biasY.randomize(rng);
        this.biasZ.randomize(rng);
        this.constellation.randomize(rng);
    }

    void clearHistory() {
        this.head = 0;
        this.filled = 0;
        this.hasSample = false;
        this.sampleReady = false;
    }

    void shift(double dx, double dy, double dz) {
        for (int i = 0; i < HISTORY; i++) {
            this.hx[i] += dx;
            this.hy[i] += dy;
            this.hz[i] += dz;
        }
        this.x += dx;
        this.y += dy;
        this.z += dz;
    }

    static double satellitesFor(double skyView, double constellation) {
        double sky = SimMath.clamp01(skyView);
        return Math.max(0.0, (OPEN_SKY_SATELLITES + 1.5 * constellation) * Math.pow(sky, 1.6));
    }

    static double hdopFor(int satellites, double skyView) {
        if (satellites < FIX_SATELLITES - 1) {
            return 99.9;
        }
        double base = 0.55 + 22.0 / (satellites * satellites);
        return base * (1.0 + 1.2 * (1.0 - SimMath.clamp01(skyView)));
    }

    void update(SimRandom rng, double skyView, double px, double py, double pz, double pvx, double pvy, double pvz) {
        this.ticks++;
        this.head = (this.head + 1) % HISTORY;
        this.hx[this.head] = px;
        this.hy[this.head] = py;
        this.hz[this.head] = pz;
        this.hvx[this.head] = pvx;
        this.hvy[this.head] = pvy;
        this.hvz[this.head] = pvz;
        if (this.filled < HISTORY) {
            this.filled++;
        }
        double target = SimMath.finiteOr(skyView, 1.0);
        this.sky += (SimMath.clamp01(target) - this.sky) * Math.min(1.0, NAV_DT / 1.5);
        double visible = satellitesFor(this.sky, this.constellation.advance(rng));
        if (this.tracked < visible) {
            this.tracked = Math.min(visible, this.tracked + ACQUIRE_PER_SECOND * NAV_DT);
        } else {
            this.tracked = Math.max(visible, this.tracked - LOSE_PER_SECOND * NAV_DT);
        }
        this.satellites = (int) Math.floor(this.tracked + 1e-9);
        this.fix = this.satellites >= FIX_SATELLITES;
        this.hdop = hdopFor(this.satellites, this.sky);
        double sigma = BIAS_METERS * Math.min(this.hdop, 6.0);
        this.biasX.advance(rng);
        this.biasY.advance(rng);
        this.biasZ.advance(rng);
        this.sampleReady = false;
        if (!this.fix) {
            this.hasSample = false;
            return;
        }
        if (this.ticks % SAMPLE_TICKS != 0L || this.filled <= LATENCY_TICKS) {
            return;
        }
        int i = Math.floorMod(this.head - LATENCY_TICKS, HISTORY);
        double noise = NOISE_METERS * Math.min(this.hdop, 6.0);
        double vNoise = VELOCITY_NOISE * Math.min(this.hdop, 6.0);
        this.x = this.hx[i] + sigma * this.biasX.value + noise * rng.gauss();
        this.y = this.hy[i] + VERTICAL_FACTOR * (sigma * this.biasY.value + noise * rng.gauss());
        this.z = this.hz[i] + sigma * this.biasZ.value + noise * rng.gauss();
        this.vx = this.hvx[i] + vNoise * rng.gauss();
        this.vy = this.hvy[i] + VERTICAL_FACTOR * vNoise * rng.gauss();
        this.vz = this.hvz[i] + vNoise * rng.gauss();
        this.sampleReady = true;
        this.hasSample = true;
    }
}
