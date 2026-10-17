package de.lardtom.propwash.video.vtx;

public final class VtxAuto {
    public static final double DIGITAL_TARGET_DB = 10.0;
    public static final double ANALOG_TARGET_DB = 15.0;
    public static final double DOWN_HYSTERESIS_DB = 3.0;
    public static final double UP_DELAY_S = 0.25;
    public static final double DOWN_DELAY_S = 2.0;

    private int index = -1;
    private double upTimer;
    private double downTimer;

    public void reset(int start) {
        this.index = start;
        this.upTimer = 0.0;
        this.downTimer = 0.0;
    }

    public int index() {
        return this.index;
    }

    public int update(double dt, double ratedMarginDb, VtxTable table) {
        if (this.index < 0) {
            this.index = table.ratedIndex();
        }
        this.index = table.clamp(this.index);
        double step = Double.isFinite(dt) && dt > 0.0 ? dt : 0.0;
        if (Double.isNaN(ratedMarginDb)) {
            this.upTimer = 0.0;
            this.downTimer = 0.0;
            return this.index;
        }
        double target = targetDb(table);
        int needed = lowestReaching(table, ratedMarginDb, target);
        if (needed > this.index) {
            this.downTimer = 0.0;
            this.upTimer += step;
            if (this.upTimer >= UP_DELAY_S) {
                this.index = needed;
                this.upTimer = 0.0;
            }
            return this.index;
        }
        this.upTimer = 0.0;
        int relaxed = lowestReaching(table, ratedMarginDb, target + DOWN_HYSTERESIS_DB);
        if (relaxed < this.index) {
            this.downTimer += step;
            if (this.downTimer >= DOWN_DELAY_S) {
                this.index--;
                this.downTimer = 0.0;
            }
        } else {
            this.downTimer = 0.0;
        }
        return this.index;
    }

    public static double targetDb(VtxTable table) {
        return table.digital() ? DIGITAL_TARGET_DB : ANALOG_TARGET_DB;
    }

    static int lowestReaching(VtxTable table, double ratedMarginDb, double targetDb) {
        for (int i = 0; i < table.count(); i++) {
            if (ratedMarginDb + table.gainDb(i) >= targetDb) {
                return i;
            }
        }
        return table.maxIndex();
    }
}
