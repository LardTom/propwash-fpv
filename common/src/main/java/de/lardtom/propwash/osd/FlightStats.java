package de.lardtom.propwash.osd;

public final class FlightStats {
    public double flightSeconds;
    public double maxSpeed;
    public double maxAltitude;
    public double maxDistance;
    public double minCellVoltage = Double.NaN;
    public double maxCurrent;
    public double mAhUsed = Double.NaN;
    public double maxG;
    public int samples;
    public int crashes;
    public boolean tricksOn;
    public int trickScore;
    public int bestCombo;
    public int bestComboPoints;
    private double startMah = Double.NaN;
    private int crashBase = -1;

    public FlightStats() {
    }

    public void reset() {
        this.flightSeconds = 0.0;
        this.maxSpeed = 0.0;
        this.maxAltitude = 0.0;
        this.maxDistance = 0.0;
        this.minCellVoltage = Double.NaN;
        this.maxCurrent = 0.0;
        this.mAhUsed = Double.NaN;
        this.maxG = 0.0;
        this.samples = 0;
        this.startMah = Double.NaN;
        this.crashes = 0;
        this.crashBase = -1;
        this.tricksOn = false;
        this.trickScore = 0;
        this.bestCombo = 0;
        this.bestComboPoints = 0;
    }

    public void trackCrashes(OsdData data) {
        if (this.crashBase >= 0) {
            this.crashes = Math.max(0, data.crashCount - this.crashBase);
        }
    }

    public boolean hasData() {
        return this.samples > 0;
    }

    public void accumulate(OsdData data, double dtSeconds) {
        if (!data.armed) {
            return;
        }
        if (Double.isFinite(dtSeconds) && dtSeconds > 0.0) {
            this.flightSeconds += dtSeconds;
        }
        this.samples++;
        if (this.crashBase < 0) {
            this.crashBase = data.crashCount;
        }
        this.maxSpeed = maxFinite(this.maxSpeed, data.speed);
        this.maxAltitude = maxFinite(this.maxAltitude, data.altitude);
        this.maxDistance = maxFinite(this.maxDistance, data.homeDistance());
        this.maxCurrent = maxFinite(this.maxCurrent, data.currentA);
        this.maxG = maxFinite(this.maxG, data.gLoad);
        this.tricksOn |= data.tricksOn;
        if (data.tricksOn) {
            this.trickScore = data.trickScore;
            this.bestCombo = data.trickBestCombo;
            this.bestComboPoints = data.trickBestComboPoints;
        }
        if (Double.isFinite(data.cellVoltage) && data.cellVoltage > 0.0) {
            this.minCellVoltage = Double.isFinite(this.minCellVoltage) ? Math.min(this.minCellVoltage, data.cellVoltage) : data.cellVoltage;
        }
        if (Double.isFinite(data.mAhUsed)) {
            if (!Double.isFinite(this.startMah)) {
                this.startMah = data.mAhUsed;
            }
            this.mAhUsed = Math.max(0.0, data.mAhUsed - this.startMah);
        }
    }

    private static double maxFinite(double current, double value) {
        return Double.isFinite(value) ? Math.max(current, value) : current;
    }

    public void copyTo(FlightStats dst) {
        dst.flightSeconds = this.flightSeconds;
        dst.maxSpeed = this.maxSpeed;
        dst.maxAltitude = this.maxAltitude;
        dst.maxDistance = this.maxDistance;
        dst.minCellVoltage = this.minCellVoltage;
        dst.maxCurrent = this.maxCurrent;
        dst.mAhUsed = this.mAhUsed;
        dst.maxG = this.maxG;
        dst.samples = this.samples;
        dst.startMah = this.startMah;
        dst.crashes = this.crashes;
        dst.crashBase = this.crashBase;
        dst.tricksOn = this.tricksOn;
        dst.trickScore = this.trickScore;
        dst.bestCombo = this.bestCombo;
        dst.bestComboPoints = this.bestComboPoints;
    }
}
