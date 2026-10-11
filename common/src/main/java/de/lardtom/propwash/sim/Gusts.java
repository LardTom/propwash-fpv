package de.lardtom.propwash.sim;

final class Gusts {
    static final double TAU = 1.2;

    double x;
    double y;
    double z;
    private double gain;
    private double scale;

    void configure(double updateDt) {
        this.gain = updateDt / (updateDt + TAU);
        this.scale = Math.sqrt((2.0 - this.gain) / this.gain);
    }

    void reset() {
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
    }

    void advance(SimRandom rng, double intensity, double windSpeed, double windScale) {
        double sigma = intensity * (0.5 + 0.3 * windSpeed) * windScale;
        double tx = 0.0;
        double ty = 0.0;
        double tz = 0.0;
        if (sigma > 0.0) {
            double s = sigma * this.scale;
            tx = s * rng.gauss();
            ty = 0.5 * s * rng.gauss();
            tz = s * rng.gauss();
        }
        this.x += this.gain * (tx - this.x);
        this.y += this.gain * (ty - this.y);
        this.z += this.gain * (tz - this.z);
    }
}
