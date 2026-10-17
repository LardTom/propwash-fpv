package de.lardtom.propwash.sim;

public final class SimEnvironment {
    public double groundDistance = Double.POSITIVE_INFINITY;
    public double solidDistance = Double.NaN;
    public double windX;
    public double windY;
    public double windZ;
    public double gustIntensity;
    public double airDensity = 1.225;
    public double ambientTempC = 20.0;
    public Fluid fluid = Fluid.NONE;
    public double skyView = 1.0;
    public final SnagField snag = new SnagField();
    public double waterSurface = Double.NEGATIVE_INFINITY;

    public SimEnvironment() {
    }

    public void copyTo(SimEnvironment dst) {
        dst.groundDistance = this.groundDistance;
        dst.solidDistance = this.solidDistance;
        dst.windX = this.windX;
        dst.windY = this.windY;
        dst.windZ = this.windZ;
        dst.gustIntensity = this.gustIntensity;
        dst.airDensity = this.airDensity;
        dst.ambientTempC = this.ambientTempC;
        dst.fluid = this.fluid;
        dst.skyView = this.skyView;
        this.snag.copyTo(dst.snag);
        dst.waterSurface = this.waterSurface;
    }
}
