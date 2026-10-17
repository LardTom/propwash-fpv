package de.lardtom.propwash.sim;

public final class FcSample {
    public long substep;
    public int loopDivider = 1;
    public boolean armed;
    public StabMode mode = StabMode.ACRO;
    public boolean mode3d;
    public boolean turtle;
    public boolean airmode;
    public int failsafeStage;
    public boolean linkOk = true;
    public HoldMode hold = HoldMode.OFF;
    public final double[] stick = new double[4];
    public final double[] setpoint = new double[3];
    public double mixerThrottle;
    public final double[] gyro = new double[3];
    public final double[] gyroRaw = new double[3];
    public final double[] pTerm = new double[3];
    public final double[] iTerm = new double[3];
    public final double[] dTerm = new double[3];
    public final double[] fTerm = new double[3];
    public final double[] motorDuty = new double[AirframeSpec.MOTORS];
    public final double[] motorRpm = new double[AirframeSpec.MOTORS];
    public double voltage;
    public double current;
    public double baroAltitude;
    public int gpsSatellites;
    public boolean gpsFix;
    public double gpsX;
    public double gpsY;
    public double gpsZ;
    public double gpsVx;
    public double gpsVz;

    public FcSample() {
    }

    public double timeSeconds() {
        return this.substep * DroneSim.H;
    }
}
