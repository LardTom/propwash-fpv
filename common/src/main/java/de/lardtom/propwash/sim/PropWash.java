package de.lardtom.propwash.sim;

final class PropWash {
    final double[] noise = new double[AirframeSpec.MOTORS];
    final double[] weight = new double[AirframeSpec.MOTORS];
    final double[] vrs = new double[AirframeSpec.MOTORS];
    private double noiseGain;
    private double noiseScale;

    void configure(AirframeSpec p, double h) {
        this.noiseGain = h / (h + 1.0 / (SimMath.TWO_PI * p.washNoiseHz));
        this.noiseScale = Math.sqrt((2.0 - this.noiseGain) / this.noiseGain);
    }

    void reset() {
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            this.noise[i] = 0.0;
            this.weight[i] = 0.0;
            this.vrs[i] = 0.0;
        }
    }

    void advanceNoise(SimRandom rng) {
        double k = this.noiseGain;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            this.noise[i] += k * (this.noiseScale * rng.gauss() - this.noise[i]);
        }
    }

    double maxOf() {
        return Math.max(Math.max(this.weight[0], this.weight[1]), Math.max(this.weight[2], this.weight[3]));
    }

    static double vortexRingWeight(double previousThrust, double hoverThrust, double axialSpeed, double crossSpeed,
                                   double rho, double area, double ductGain) {
        double thrust = previousThrust > 1e-4 ? previousThrust : 1e-4;
        double vh = Math.sqrt(thrust / (2.0 * rho * area * ductGain));
        double sink = -axialSpeed / vh;
        double cross = crossSpeed / vh;
        double a = (sink - 1.0) / ModelConstants.WASH_SINK_WIDTH;
        double b = cross / ModelConstants.WASH_CROSS_WIDTH;
        double load = previousThrust > 0.0 ? Math.min(1.0, previousThrust / (0.25 * hoverThrust)) : 0.0;
        return StrictMath.exp(-(a * a) - b * b) * load;
    }

    double factor(int i, double vrsWeight, double wakeWeight, double scale, double loss, double noiseAmp) {
        double w = SimMath.clamp01(scale * (1.0 - (1.0 - vrsWeight) * (1.0 - wakeWeight)));
        this.weight[i] = w;
        this.vrs[i] = vrsWeight;
        return (1.0 - loss * w) * (1.0 + noiseAmp * w * this.noise[i]);
    }
}
