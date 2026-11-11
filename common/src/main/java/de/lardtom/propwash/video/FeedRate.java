package de.lardtom.propwash.video;

public final class FeedRate {
    private final double perMilli;
    private final int burst;
    private double tokens;
    private long last = Long.MIN_VALUE;
    private int refused;

    public FeedRate(int perSecond, int burst) {
        if (perSecond <= 0 || burst <= 0) {
            throw new IllegalArgumentException("Rate und Vorrat müssen positiv sein");
        }
        this.perMilli = perSecond / 1000.0;
        this.burst = burst;
        this.tokens = burst;
    }

    public boolean accept(long nowMillis) {
        if (this.last != Long.MIN_VALUE && nowMillis > this.last) {
            this.tokens = Math.min(this.burst, this.tokens + (nowMillis - this.last) * this.perMilli);
        }
        if (this.last == Long.MIN_VALUE || nowMillis > this.last) {
            this.last = nowMillis;
        }
        if (this.tokens >= 1.0) {
            this.tokens -= 1.0;
            return true;
        }
        this.refused++;
        return false;
    }

    public int refused() {
        return this.refused;
    }
}
