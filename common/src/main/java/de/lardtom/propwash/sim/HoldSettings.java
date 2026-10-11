package de.lardtom.propwash.sim;

public final class HoldSettings {
    public double altP = 50;
    public double altI = 30;
    public double altD = 40;
    public double climbRateCms = 150;
    public double throttleDeadbandPercent = 10;
    public boolean midThrottle = false;
    public double posP = 30;
    public double posI = 20;
    public double posD = 40;
    public double posMaxAngleDeg = 20;
    public double stickDeadbandPercent = 8;
    public double minSatellites = 8;

    public HoldSettings() {
    }

    public HoldSettings copy() {
        HoldSettings c = new HoldSettings();
        c.altP = this.altP;
        c.altI = this.altI;
        c.altD = this.altD;
        c.climbRateCms = this.climbRateCms;
        c.throttleDeadbandPercent = this.throttleDeadbandPercent;
        c.midThrottle = this.midThrottle;
        c.posP = this.posP;
        c.posI = this.posI;
        c.posD = this.posD;
        c.posMaxAngleDeg = this.posMaxAngleDeg;
        c.stickDeadbandPercent = this.stickDeadbandPercent;
        c.minSatellites = this.minSatellites;
        return c;
    }

    public void sanitize() {
        this.altP = SimMath.sanitize(this.altP, 0, 200, 50);
        this.altI = SimMath.sanitize(this.altI, 0, 200, 30);
        this.altD = SimMath.sanitize(this.altD, 0, 200, 40);
        this.climbRateCms = SimMath.sanitize(this.climbRateCms, 20, 1000, 150);
        this.throttleDeadbandPercent = SimMath.sanitize(this.throttleDeadbandPercent, 2, 40, 10);
        this.posP = SimMath.sanitize(this.posP, 0, 200, 30);
        this.posI = SimMath.sanitize(this.posI, 0, 200, 20);
        this.posD = SimMath.sanitize(this.posD, 0, 200, 40);
        this.posMaxAngleDeg = SimMath.sanitize(this.posMaxAngleDeg, 5, 45, 20);
        this.stickDeadbandPercent = SimMath.sanitize(this.stickDeadbandPercent, 2, 30, 8);
        this.minSatellites = Math.round(SimMath.sanitize(this.minSatellites, 5, 12, 8));
    }
}
