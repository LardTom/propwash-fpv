package de.lardtom.propwash.sim;

public final class AxisPid {
    public double p;
    public double i;
    public double d;
    public double ff;

    public AxisPid() {
    }

    public AxisPid(double p, double i, double d, double ff) {
        this.p = p;
        this.i = i;
        this.d = d;
        this.ff = ff;
    }

    public AxisPid copy() {
        return new AxisPid(this.p, this.i, this.d, this.ff);
    }

    public void sanitize(AxisPid fallback) {
        this.p = SimMath.sanitize(this.p, 0.0, 250.0, fallback.p);
        this.i = SimMath.sanitize(this.i, 0.0, 250.0, fallback.i);
        this.d = SimMath.sanitize(this.d, 0.0, 200.0, fallback.d);
        this.ff = SimMath.sanitize(this.ff, 0.0, 500.0, fallback.ff);
    }
}
