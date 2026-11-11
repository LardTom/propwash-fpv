package de.lardtom.propwash.stats;

import java.util.Objects;
import java.util.UUID;

public final class FlightTally {
    public static final int MAX_GAP_TICKS = 20;
    public static final double MAX_STEP_BLOCKS = 64.0;
    private final UUID pilot;
    private long lastTick;
    private boolean armed;
    private float charge;
    private double centimeters;
    private int bestSpeed;

    public FlightTally(UUID pilot, long tick, boolean armed, float charge) {
        this.pilot = Objects.requireNonNull(pilot, "pilot");
        this.lastTick = tick;
        this.armed = armed;
        this.charge = Float.isNaN(charge) ? 0.0F : charge;
    }

    public UUID pilot() {
        return this.pilot;
    }

    public boolean armed() {
        return this.armed;
    }

    public Step update(long tick, boolean armed, double blocks, double metersPerSecond, float charge) {
        float now = Float.isNaN(charge) ? 0.0F : charge;
        boolean wasArmed = this.armed;
        long gap = tick - this.lastTick;
        int ticks = wasArmed && gap > 0L && gap <= MAX_GAP_TICKS ? (int) gap : 0;
        if (gap > 0L) {
            this.lastTick = tick;
        }
        int centimeters = 0;
        if (wasArmed && Double.isFinite(blocks) && blocks > 0.0 && blocks <= MAX_STEP_BLOCKS) {
            this.centimeters += blocks * 100.0;
            centimeters = (int) this.centimeters;
            this.centimeters -= centimeters;
        }
        boolean armedNow = armed && !wasArmed;
        boolean depleted = (armed || wasArmed) && this.charge > 0.0F && now <= 0.0F;
        int topSpeed = 0;
        if (armed) {
            int speed = StatFormats.tenthsKmh(metersPerSecond);
            if (speed > this.bestSpeed) {
                this.bestSpeed = speed;
                topSpeed = speed;
            }
        }
        this.armed = armed;
        this.charge = now;
        return new Step(ticks, centimeters, armedNow, depleted, topSpeed);
    }

    public record Step(int ticks, int centimeters, boolean armed, boolean depleted, int topSpeed) {
        public boolean empty() {
            return this.ticks == 0 && this.centimeters == 0 && !this.armed && !this.depleted && this.topSpeed == 0;
        }
    }
}
