package de.lardtom.propwash.sim;

public final class SimEnvironment {
    public double groundDistance = Double.POSITIVE_INFINITY;
    public double windX;
    public double windY;
    public double windZ;
    public double gustIntensity;
    public double airDensity = 1.225;
    public double ambientTempC = 20.0;
    public Fluid fluid = Fluid.NONE;
    public double skyView = 1.0;

    public SimEnvironment() {
    }

    public void copyTo(SimEnvironment dst) {
        dst.groundDistance = this.groundDistance;
        dst.windX = this.windX;
        dst.windY = this.windY;
        dst.windZ = this.windZ;
        dst.gustIntensity = this.gustIntensity;
        dst.airDensity = this.airDensity;
        dst.ambientTempC = this.ambientTempC;
        dst.fluid = this.fluid;
        dst.skyView = this.skyView;
    }
}
