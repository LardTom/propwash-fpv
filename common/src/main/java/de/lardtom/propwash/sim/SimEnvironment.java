package de.lardtom.propwash.sim;

import java.util.Arrays;

public final class SimEnvironment {
    public static final int SUPPORT_SIDE = 5;
    public static final int SUPPORT_CELLS = SUPPORT_SIDE * SUPPORT_SIDE;
    public static final double SUPPORT_RANGE = 0.5;
    public static final double SUPPORT_RIM = 1.0E-6;
    public static final int POINT_SUPPORT = AirframeSpec.ACCESSORY_POINTS + AirframeSpec.STAND_LEGS;

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
    public final double[] support = new double[SUPPORT_CELLS];
    public double supportX;
    public double supportZ;
    public double supportSpacing;
    public final double[] pointSupport = new double[POINT_SUPPORT];
    public final double[] pointX = new double[POINT_SUPPORT];
    public final double[] pointZ = new double[POINT_SUPPORT];
    public int pointCount;

    public SimEnvironment() {
    }

    public static double supportSpacing(double width) {
        return Math.max(0.0, width - 2.0 * SUPPORT_RIM) / (SUPPORT_SIDE - 1);
    }

    public static double supportOffset(int index, double spacing) {
        return (index - SUPPORT_SIDE / 2) * spacing;
    }

    public static int supportCell(int ix, int iz) {
        return iz * SUPPORT_SIDE + ix;
    }

    public void clearSupport() {
        this.supportSpacing = 0.0;
        Arrays.fill(this.support, Double.NaN);
        this.pointCount = 0;
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
        System.arraycopy(this.support, 0, dst.support, 0, SUPPORT_CELLS);
        dst.supportX = this.supportX;
        dst.supportZ = this.supportZ;
        dst.supportSpacing = this.supportSpacing;
        System.arraycopy(this.pointSupport, 0, dst.pointSupport, 0, POINT_SUPPORT);
        System.arraycopy(this.pointX, 0, dst.pointX, 0, POINT_SUPPORT);
        System.arraycopy(this.pointZ, 0, dst.pointZ, 0, POINT_SUPPORT);
        dst.pointCount = this.pointCount;
    }
}
