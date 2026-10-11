package de.lardtom.propwash.sim;

final class Barometer {
    static final double NOISE_METERS = 0.3;
    static final double DRIFT_METERS = 0.35;
    static final double DRIFT_TAU = 30.0;
    static final double LAG_SECONDS = 0.1;
    static final double DYNAMIC_PRESSURE_GAIN = 0.004;
    static final double GROUND_BIAS_METERS = 0.45;
    static final double GROUND_SCALE_HEIGHT = 0.5;
    static final double GROUND_NOISE_METERS = 0.2;
    static final double WASH_NOISE_METERS = 0.3;

    private final GaussMarkov drift = new GaussMarkov();
    private boolean primed;
    double raw;
    double altitude;

    Barometer() {
        this.drift.configure(DRIFT_TAU, GpsReceiver.NAV_DT);
    }

    void seed(SimRandom rng) {
        this.drift.randomize(rng);
    }

    void reset() {
        this.primed = false;
    }

    void shift(double dy) {
        this.raw += dy;
        this.altitude += dy;
    }

    static double groundError(double heightAboveGround, double thrustRatio) {
        if (!(heightAboveGround < 4.0)) {
            return 0.0;
        }
        double h = Math.max(0.0, heightAboveGround);
        return -GROUND_BIAS_METERS * SimMath.clamp(thrustRatio, 0.0, 2.0) * Math.exp(-h / GROUND_SCALE_HEIGHT);
    }

    static double dynamicPressureError(double airspeed) {
        double v = Math.min(60.0, Math.max(0.0, airspeed));
        return DYNAMIC_PRESSURE_GAIN * v * v;
    }

    double update(SimRandom rng, double trueY, double airspeed, double heightAboveGround, double thrustRatio, double propWash) {
        double h = Double.isFinite(heightAboveGround) ? Math.max(0.0, heightAboveGround) : Double.POSITIVE_INFINITY;
        double nearGround = Double.isFinite(h) ? Math.exp(-h / 1.0) * SimMath.clamp01(thrustRatio) : 0.0;
        double sigma = NOISE_METERS + GROUND_NOISE_METERS * nearGround + WASH_NOISE_METERS * SimMath.clamp01(propWash);
        this.raw = trueY + DRIFT_METERS * this.drift.advance(rng) + groundError(h, thrustRatio)
                + dynamicPressureError(airspeed) + sigma * rng.gauss();
        if (!this.primed) {
            this.altitude = this.raw;
            this.primed = true;
        } else {
            this.altitude += (this.raw - this.altitude) * (GpsReceiver.NAV_DT / (LAG_SECONDS + GpsReceiver.NAV_DT));
        }
        return this.altitude;
    }
}
