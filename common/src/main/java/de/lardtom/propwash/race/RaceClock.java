package de.lardtom.propwash.race;

public final class RaceClock {
    private static final long RUNNING = Long.MIN_VALUE;

    private long pausedTotal;
    private long pausedAt = RUNNING;

    public long now(long realNanos) {
        long paused = this.pausedTotal + (this.pausedAt == RUNNING ? 0L : Math.max(0L, realNanos - this.pausedAt));
        return realNanos - paused;
    }

    public void setPaused(boolean paused, long realNanos) {
        if (paused && this.pausedAt == RUNNING) {
            this.pausedAt = realNanos;
        } else if (!paused && this.pausedAt != RUNNING) {
            this.pausedTotal += Math.max(0L, realNanos - this.pausedAt);
            this.pausedAt = RUNNING;
        }
    }

    public boolean paused() {
        return this.pausedAt != RUNNING;
    }
}
