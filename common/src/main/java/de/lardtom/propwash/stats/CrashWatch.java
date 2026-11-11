package de.lardtom.propwash.stats;

public final class CrashWatch {
    public static final int NONE = -1;
    private int droneId = NONE;
    private int seen;

    public int observe(boolean active, int droneId, int crashCount) {
        if (!active) {
            this.reset();
            return 0;
        }
        if (droneId != this.droneId) {
            this.droneId = droneId;
            this.seen = 0;
        }
        if (crashCount <= this.seen) {
            this.seen = Math.max(0, crashCount);
            return 0;
        }
        int delta = crashCount - this.seen;
        this.seen = crashCount;
        return delta;
    }

    public void reset() {
        this.droneId = NONE;
        this.seen = 0;
    }
}
