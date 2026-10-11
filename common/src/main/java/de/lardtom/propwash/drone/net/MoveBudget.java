package de.lardtom.propwash.drone.net;

public final class MoveBudget {
    private double available;
    private long lastNanos;
    private boolean started;
    private long packetTick = Long.MIN_VALUE;
    private int packetsInTick;

    public void restart() {
        this.started = false;
    }

    public boolean admitPacket(long tick) {
        if (tick != this.packetTick) {
            this.packetTick = tick;
            this.packetsInTick = 0;
        }
        this.packetsInTick++;
        return this.packetsInTick <= StateValidation.MAX_PACKETS_PER_TICK;
    }

    public boolean tryConsume(double distance, double maxSpeed, long nanos) {
        if (!Double.isFinite(distance) || distance < 0.0) {
            return false;
        }
        this.refill(maxSpeed, nanos);
        if (distance > this.available) {
            return false;
        }
        this.available -= distance;
        return true;
    }

    public double available(double maxSpeed, long nanos) {
        this.refill(maxSpeed, nanos);
        return this.available;
    }

    private void refill(double maxSpeed, long nanos) {
        double capacity = StateValidation.burstDistance(maxSpeed);
        if (!this.started) {
            this.started = true;
            this.available = capacity;
            this.lastNanos = nanos;
            return;
        }
        double seconds = Math.max(0L, nanos - this.lastNanos) * 1.0E-9;
        this.lastNanos = Math.max(this.lastNanos, nanos);
        this.available = Math.min(capacity, this.available + StateValidation.refillRate(maxSpeed) * seconds);
    }
}
