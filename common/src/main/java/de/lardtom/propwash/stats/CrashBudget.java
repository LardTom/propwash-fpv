package de.lardtom.propwash.stats;

public final class CrashBudget {
    public static final int CAPACITY = 8;
    public static final int REFILL_TICKS = 5;
    private int tokens = CAPACITY;
    private long refilled = Long.MIN_VALUE;

    public int admit(long tick, int requested) {
        if (requested <= 0) {
            return 0;
        }
        if (this.refilled == Long.MIN_VALUE || tick < this.refilled) {
            this.refilled = tick;
        } else {
            long gained = (tick - this.refilled) / REFILL_TICKS;
            if (gained > 0L) {
                this.tokens = (int) Math.min(CAPACITY, this.tokens + gained);
                this.refilled = this.tokens >= CAPACITY ? tick : this.refilled + gained * REFILL_TICKS;
            }
        }
        int granted = Math.min(requested, this.tokens);
        this.tokens -= granted;
        return granted;
    }

    public int tokens() {
        return this.tokens;
    }
}
