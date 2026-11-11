package de.lardtom.propwash.stats.advancement;

public final class FlightFeats {
    public static final double TAKEOFF_HEIGHT = 1.0;
    public static final double RECLIMB = 3.0;
    private boolean armed;
    private boolean airborne;
    private double armY;
    private double peak;
    private double low;

    public Observation update(boolean armed, double y) {
        if (!armed || !Double.isFinite(y)) {
            this.armed = false;
            this.airborne = false;
            return Observation.GROUNDED;
        }
        if (!this.armed) {
            this.armed = true;
            this.airborne = false;
            this.armY = y;
            this.peak = y;
            this.low = y;
        }
        boolean takeoff = false;
        if (!this.airborne && y - this.armY >= TAKEOFF_HEIGHT) {
            this.airborne = true;
            takeoff = true;
        }
        if (y > this.low + RECLIMB) {
            this.peak = y;
            this.low = y;
        } else {
            this.peak = Math.max(this.peak, y);
            this.low = Math.min(this.low, y);
        }
        return new Observation(takeoff, this.airborne, this.peak - this.low);
    }

    public record Observation(boolean takeoff, boolean airborne, double dive) {
        public static final Observation GROUNDED = new Observation(false, false, Double.NaN);
    }
}
