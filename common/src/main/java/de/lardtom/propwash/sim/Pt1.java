package de.lardtom.propwash.sim;

final class Pt1 {
    private double gain = 1.0;
    private double state;

    void configure(double cutoffHz, double dt) {
        this.gain = SimMath.pt1Gain(cutoffHz, dt);
    }

    double apply(double input) {
        this.state += this.gain * (input - this.state);
        return this.state;
    }

    double value() {
        return this.state;
    }

    void reset(double value) {
        this.state = value;
    }
}
