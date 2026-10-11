package de.lardtom.propwash.sim;

final class Pt2 {
    static final double CASCADE_CORRECTION = 1.553774;

    private final Pt1 first = new Pt1();
    private final Pt1 second = new Pt1();
    private boolean bypass = true;

    void configure(double cutoffHz, double dt) {
        this.bypass = cutoffHz <= 0.0;
        double f = cutoffHz * CASCADE_CORRECTION;
        this.first.configure(f, dt);
        this.second.configure(f, dt);
    }

    double apply(double input) {
        if (this.bypass) {
            this.first.reset(input);
            this.second.reset(input);
            return input;
        }
        return this.second.apply(this.first.apply(input));
    }

    double value() {
        return this.second.value();
    }

    void reset(double value) {
        this.first.reset(value);
        this.second.reset(value);
    }
}
