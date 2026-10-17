package de.lardtom.propwash.osd;

public final class VoltageDisplay {
    public static final double TAU_SECONDS = 0.48;
    public static final double HYSTERESIS_V = 0.03;
    public static final int NONE = 0;
    public static final int WARNING = 1;
    public static final int CRITICAL = 2;

    private double pack = Double.NaN;
    private double cell = Double.NaN;
    private int alert = NONE;

    public void reset() {
        this.pack = Double.NaN;
        this.cell = Double.NaN;
        this.alert = NONE;
    }

    public void apply(OsdData d, double dt) {
        if (!d.hasSim || !Double.isFinite(d.packVoltage) || !Double.isFinite(d.cellVoltage)) {
            this.reset();
            d.voltageAlert = NONE;
            return;
        }
        if (!Double.isFinite(this.pack) || !Double.isFinite(this.cell)) {
            this.pack = d.packVoltage;
            this.cell = d.cellVoltage;
        } else {
            double k = 1.0 - Math.exp(-Math.max(0.0, dt) / TAU_SECONDS);
            this.pack += (d.packVoltage - this.pack) * k;
            this.cell += (d.cellVoltage - this.cell) * k;
        }
        d.packVoltage = this.pack;
        d.cellVoltage = this.cell;
        this.alert = next(this.alert, this.cell, d.warnCellVoltage, d.criticalCellVoltage);
        d.voltageAlert = this.alert;
    }

    static int next(int current, double cell, double warn, double critical) {
        if (!Double.isFinite(cell) || !Double.isFinite(warn) || !Double.isFinite(critical)) {
            return NONE;
        }
        if (cell < critical) {
            return CRITICAL;
        }
        if (current == CRITICAL && cell < critical + HYSTERESIS_V) {
            return CRITICAL;
        }
        if (cell < warn) {
            return WARNING;
        }
        if (current != NONE && cell < warn + HYSTERESIS_V) {
            return WARNING;
        }
        return NONE;
    }
}
