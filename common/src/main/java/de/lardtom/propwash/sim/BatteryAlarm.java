package de.lardtom.propwash.sim;

final class BatteryAlarm {
    static final double HYSTERESIS_V = 0.1;

    private long below;
    private long above;
    private boolean active;
    private boolean latched;

    boolean update(double cell, double restingCell, double threshold, long steps) {
        if (cell < threshold) {
            this.below++;
            this.above = 0L;
        } else {
            this.below = 0L;
            this.above = cell > threshold + HYSTERESIS_V ? this.above + 1L : 0L;
        }
        if (!this.active) {
            if (this.below >= steps) {
                this.active = true;
                this.above = 0L;
                this.latched = restingCell <= threshold + HYSTERESIS_V;
                return true;
            }
            return false;
        }
        if (!this.latched && restingCell <= threshold + HYSTERESIS_V) {
            this.latched = true;
        }
        if (!this.latched && this.above >= steps) {
            this.active = false;
            this.below = 0L;
            this.above = 0L;
        }
        return false;
    }

    boolean active() {
        return this.active;
    }

    boolean latched() {
        return this.latched;
    }

    void reset() {
        this.below = 0L;
        this.above = 0L;
        this.active = false;
        this.latched = false;
    }
}
