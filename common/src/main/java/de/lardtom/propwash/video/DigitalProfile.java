package de.lardtom.propwash.video;

public enum DigitalProfile {
    ADAPTIVE(40_000_000L, 700_000_000L, true, 0.3),
    FIXED_RATE(0L, 250_000_000L, false, 0.6);

    public static final int FIXED_RATE_MAX_LATENCY_MS = 18;

    private final long maxExtraLatencyNanos;
    private final long lossGraceNanos;
    private final boolean adaptiveBitrate;
    private final double minFrameArrival;

    DigitalProfile(long maxExtraLatencyNanos, long lossGraceNanos, boolean adaptiveBitrate, double minFrameArrival) {
        this.maxExtraLatencyNanos = maxExtraLatencyNanos;
        this.lossGraceNanos = lossGraceNanos;
        this.adaptiveBitrate = adaptiveBitrate;
        this.minFrameArrival = minFrameArrival;
    }

    public static DigitalProfile of(int latencyMs) {
        return latencyMs <= FIXED_RATE_MAX_LATENCY_MS ? FIXED_RATE : ADAPTIVE;
    }

    public long maxExtraLatencyNanos() {
        return this.maxExtraLatencyNanos;
    }

    public long extraLatencyNanos(double link) {
        return (long) (this.maxExtraLatencyNanos * DigitalBreakup.latencyRise(link));
    }

    public long lossGraceNanos() {
        return this.lossGraceNanos;
    }

    public boolean adaptiveBitrate() {
        return this.adaptiveBitrate;
    }

    public double frameArrival(double link) {
        return DigitalBreakup.frameArrival(link, this.minFrameArrival);
    }
}
