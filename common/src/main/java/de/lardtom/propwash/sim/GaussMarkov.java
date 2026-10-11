package de.lardtom.propwash.sim;

final class GaussMarkov {
    private double phi;
    private double drive;
    double value;

    void configure(double tauSeconds, double dt) {
        this.phi = Math.exp(-dt / Math.max(dt, tauSeconds));
        this.drive = Math.sqrt(Math.max(0.0, 1.0 - this.phi * this.phi));
    }

    void randomize(SimRandom rng) {
        this.value = rng.gauss();
    }

    double advance(SimRandom rng) {
        this.value = this.phi * this.value + this.drive * rng.gauss();
        return this.value;
    }
}
