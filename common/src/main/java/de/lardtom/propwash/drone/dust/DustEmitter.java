package de.lardtom.propwash.drone.dust;

public final class DustEmitter {
    public static final double START_CARRY = 0.5;

    private double carry = START_CARRY;

    public int take(double ratePerSecond, double seconds) {
        if (!(ratePerSecond > 0.0) || !(seconds > 0.0) || !Double.isFinite(ratePerSecond) || !Double.isFinite(seconds)) {
            this.carry = START_CARRY;
            return 0;
        }
        this.carry += ratePerSecond * seconds;
        int count = (int) Math.floor(this.carry);
        this.carry -= count;
        return Math.min(count, DustModel.MAX_PER_TICK);
    }

    public void reset() {
        this.carry = START_CARRY;
    }

    public double carry() {
        return this.carry;
    }
}
