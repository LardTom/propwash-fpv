package de.lardtom.propwash.ui;

public record SliderScale(double factor, double offset) {
    public static final SliderScale IDENTITY = new SliderScale(1.0, 0.0);
    public static final SliderScale PERCENT = new SliderScale(100.0, 0.0);
    public static final int MAX_DECIMALS = 4;
    private static final int CONTINUOUS_DECIMALS = 2;
    private static final double TOLERANCE = 1.0E-6;

    public SliderScale {
        if (!Double.isFinite(factor) || factor == 0.0 || !Double.isFinite(offset)) {
            throw new IllegalArgumentException("factor " + factor + ", offset " + offset);
        }
    }

    public static SliderScale of(double factor, double offset) {
        return new SliderScale(factor, offset);
    }

    public double toDisplay(double raw) {
        return raw * this.factor + this.offset;
    }

    public double fromDisplay(double shown) {
        return (shown - this.offset) / this.factor;
    }

    public double displayMin(double rawMin, double rawMax) {
        return Math.min(this.toDisplay(rawMin), this.toDisplay(rawMax));
    }

    public double displayMax(double rawMin, double rawMax) {
        return Math.max(this.toDisplay(rawMin), this.toDisplay(rawMax));
    }

    public double rawFor(double shown, double rawMin, double rawMax) {
        double lo = Math.min(rawMin, rawMax);
        double hi = Math.max(rawMin, rawMax);
        double raw = this.fromDisplay(shown);
        if (!Double.isFinite(raw)) {
            return lo;
        }
        return Math.max(lo, Math.min(hi, raw));
    }

    public int decimals(double rawMin, double rawStep) {
        if (!(rawStep > 0.0) || !Double.isFinite(rawStep)) {
            return CONTINUOUS_DECIMALS;
        }
        return Math.max(decimalsFor(Math.abs(rawStep * this.factor)), decimalsFor(Math.abs(this.toDisplay(rawMin))));
    }

    public static double round(double value, int decimals) {
        if (!Double.isFinite(value)) {
            return value;
        }
        double scale = Math.pow(10.0, Math.max(0, Math.min(MAX_DECIMALS, decimals)));
        return Math.rint(value * scale) / scale;
    }

    public static int decimalsFor(double value) {
        if (!Double.isFinite(value)) {
            return CONTINUOUS_DECIMALS;
        }
        double magnitude = Math.abs(value);
        double scale = 1.0;
        for (int d = 0; d < MAX_DECIMALS; d++) {
            double scaled = magnitude * scale;
            if (Math.abs(scaled - Math.rint(scaled)) <= TOLERANCE * Math.max(1.0, scaled)) {
                return d;
            }
            scale *= 10.0;
        }
        return MAX_DECIMALS;
    }
}
