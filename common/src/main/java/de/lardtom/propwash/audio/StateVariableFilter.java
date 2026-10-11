package de.lardtom.propwash.audio;

public final class StateVariableFilter {
    private final int sampleRate;
    private float a1;
    private float a2;
    private float a3;
    private float k;
    private float ic1;
    private float ic2;
    private float low;
    private float band;
    private float high;

    public StateVariableFilter(int sampleRate) {
        this.sampleRate = sampleRate;
        this.setup(1000.0, 0.707);
    }

    public void setup(double cutoffHz, double q) {
        double fc = Math.max(10.0, Math.min(cutoffHz, this.sampleRate * 0.45));
        double g = Math.tan(Math.PI * fc / this.sampleRate);
        double kk = 1.0 / Math.max(0.1, q);
        double aa1 = 1.0 / (1.0 + g * (g + kk));
        double aa2 = g * aa1;
        this.k = (float) kk;
        this.a1 = (float) aa1;
        this.a2 = (float) aa2;
        this.a3 = (float) (g * aa2);
    }

    public void process(float input) {
        float v3 = input - this.ic2;
        float v1 = this.a1 * this.ic1 + this.a2 * v3;
        float v2 = this.ic2 + this.a2 * this.ic1 + this.a3 * v3;
        this.ic1 = 2.0F * v1 - this.ic1;
        this.ic2 = 2.0F * v2 - this.ic2;
        if (!(Math.abs(this.ic1) < 1.0e6F) || !(Math.abs(this.ic2) < 1.0e6F)) {
            this.ic1 = 0.0F;
            this.ic2 = 0.0F;
        }
        this.band = v1;
        this.low = v2;
        this.high = input - this.k * v1 - v2;
    }

    public float low() {
        return this.low;
    }

    public float band() {
        return this.band;
    }

    public float high() {
        return this.high;
    }

    public void reset() {
        this.ic1 = 0.0F;
        this.ic2 = 0.0F;
        this.low = 0.0F;
        this.band = 0.0F;
        this.high = 0.0F;
    }
}
