package de.lardtom.propwash.sim;

public final class RateCurve {
    public double rcRate = 1.0;
    public double superRate = 0.70;
    public double rcExpo = 0.0;
    public double centerRateDps = 70.0;
    public double maxRateDps = 670.0;
    public double actualExpo = 0.0;

    public RateCurve() {
    }

    public RateCurve copy() {
        RateCurve c = new RateCurve();
        c.rcRate = this.rcRate;
        c.superRate = this.superRate;
        c.rcExpo = this.rcExpo;
        c.centerRateDps = this.centerRateDps;
        c.maxRateDps = this.maxRateDps;
        c.actualExpo = this.actualExpo;
        return c;
    }

    public void sanitize() {
        this.rcRate = SimMath.sanitize(this.rcRate, 0.01, 2.55, 1.0);
        this.superRate = SimMath.sanitize(this.superRate, 0.0, 0.99, 0.70);
        this.rcExpo = SimMath.sanitize(this.rcExpo, 0.0, 1.0, 0.0);
        this.centerRateDps = SimMath.sanitize(this.centerRateDps, 10.0, 1998.0, 70.0);
        this.maxRateDps = SimMath.sanitize(this.maxRateDps, this.centerRateDps, 1998.0, Math.max(670.0, this.centerRateDps));
        this.actualExpo = SimMath.sanitize(this.actualExpo, 0.0, 1.0, 0.0);
    }
}
