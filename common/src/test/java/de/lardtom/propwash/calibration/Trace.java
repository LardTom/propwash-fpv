package de.lardtom.propwash.calibration;

public final class Trace {
    public final double rate;
    public final int step;
    public final double[] duty;
    public final double[] fz;
    public final double[] rpm;
    public final double[] vbat;
    public final double[] climb;
    public double startClimb = Double.NaN;

    public Trace(double rate, int step, int length) {
        this.rate = rate;
        this.step = step;
        this.duty = new double[length];
        this.fz = new double[length];
        this.rpm = new double[length];
        this.vbat = new double[length];
        this.climb = new double[length];
        java.util.Arrays.fill(this.climb, Double.NaN);
    }

    public static Trace of(Flight f, int from, int to, int step) {
        Trace t = new Trace(f.rate, step - from, to - from);
        for (int i = from; i < to; i++) {
            t.duty[i - from] = f.duty[i];
            t.fz[i - from] = f.fz[i];
            t.rpm[i - from] = f.rpm[i];
            t.vbat[i - from] = f.vbat[i];
        }
        t.startClimb = f.climbRate(from - (int) (0.3 * f.rate), from + (int) (0.1 * f.rate));
        return t;
    }

    public int length() {
        return this.duty.length;
    }

    public int at(double secondsFromStep) {
        return this.step + (int) Math.round(secondsFromStep * this.rate);
    }

    public double seconds(int index) {
        return (index - this.step) / this.rate;
    }
}
