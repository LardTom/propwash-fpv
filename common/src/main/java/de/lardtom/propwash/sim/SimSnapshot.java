package de.lardtom.propwash.sim;

public final class SimSnapshot {
    public double px;
    public double py;
    public double pz;
    public double vx;
    public double vy;
    public double vz;
    public double qw;
    public double qx;
    public double qy;
    public double qz;
    public double lx;
    public double ly;
    public double lz;
    public final double[] rotorOmega = new double[4];
    public final double[] motorTempC = new double[4];
    public final double[] escTempC = new double[4];
    public double soc;
    public double mAhUsed;
    public double polarizationVoltage;
    public double fastPolarizationVoltage;
    public double lastBatteryCurrent;
    public double batteryHeatCurrent;
    public double lastBusVoltage;
    public double packSoc = Double.NaN;
    public double packMahUsed = Double.NaN;
    public boolean armed;
    public boolean sleeping;
    public int groundedFrames;
    public long rngState;
    public long accumulatorNanos;

    public SimSnapshot() {
    }

    public void copyTo(SimSnapshot dst) {
        dst.px = this.px;
        dst.py = this.py;
        dst.pz = this.pz;
        dst.vx = this.vx;
        dst.vy = this.vy;
        dst.vz = this.vz;
        dst.qw = this.qw;
        dst.qx = this.qx;
        dst.qy = this.qy;
        dst.qz = this.qz;
        dst.lx = this.lx;
        dst.ly = this.ly;
        dst.lz = this.lz;
        System.arraycopy(this.rotorOmega, 0, dst.rotorOmega, 0, 4);
        System.arraycopy(this.motorTempC, 0, dst.motorTempC, 0, 4);
        System.arraycopy(this.escTempC, 0, dst.escTempC, 0, 4);
        dst.soc = this.soc;
        dst.mAhUsed = this.mAhUsed;
        dst.polarizationVoltage = this.polarizationVoltage;
        dst.fastPolarizationVoltage = this.fastPolarizationVoltage;
        dst.lastBatteryCurrent = this.lastBatteryCurrent;
        dst.batteryHeatCurrent = this.batteryHeatCurrent;
        dst.lastBusVoltage = this.lastBusVoltage;
        dst.packSoc = this.packSoc;
        dst.packMahUsed = this.packMahUsed;
        dst.armed = this.armed;
        dst.sleeping = this.sleeping;
        dst.groundedFrames = this.groundedFrames;
        dst.rngState = this.rngState;
        dst.accumulatorNanos = this.accumulatorNanos;
    }
}
