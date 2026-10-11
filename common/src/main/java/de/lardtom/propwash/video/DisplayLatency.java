package de.lardtom.propwash.video;

public final class DisplayLatency {
    public static final long MAX_SAMPLE_NANOS = 50_000_000L;
    public static final double SMOOTHING_SECONDS = 1.0;
    public static final double DEFAULT_REFRESH_HZ = 60.0;
    public static final double MIN_REFRESH_HZ = 24.0;
    public static final double MAX_REFRESH_HZ = 1000.0;
    private static final long NONE = Long.MIN_VALUE;

    private long frameStartNanos = NONE;
    private long lastFrameNanos = NONE;
    private long lastPresentNanos = NONE;
    private long frameCompensationNanos;
    private double pipelineNanos;
    private boolean primed;
    private double refreshHz = DEFAULT_REFRESH_HZ;

    public void reset() {
        this.frameStartNanos = NONE;
        this.lastFrameNanos = NONE;
        this.lastPresentNanos = NONE;
        this.frameCompensationNanos = 0L;
        this.pipelineNanos = 0.0;
        this.primed = false;
    }

    public void frameStart(long nanos) {
        if (nanos == this.lastFrameNanos) {
            return;
        }
        this.lastFrameNanos = nanos;
        this.frameStartNanos = nanos;
        this.frameCompensationNanos = this.compensationNanos();
    }

    public long frameCompensationNanos() {
        return this.lastFrameNanos == NONE ? this.compensationNanos() : this.frameCompensationNanos;
    }

    public void presented(long nanos) {
        long start = this.frameStartNanos;
        if (start == NONE || nanos < start) {
            return;
        }
        this.frameStartNanos = NONE;
        double sample = Math.min(nanos - start, MAX_SAMPLE_NANOS);
        if (!this.primed) {
            this.pipelineNanos = sample;
            this.primed = true;
        } else {
            double dt = this.lastPresentNanos == NONE ? 0.0 : Math.max(0L, nanos - this.lastPresentNanos) * 1.0E-9;
            double alpha = 1.0 - Math.exp(-Math.min(dt, 0.25) / SMOOTHING_SECONDS);
            this.pipelineNanos += (sample - this.pipelineNanos) * alpha;
        }
        this.lastPresentNanos = nanos;
    }

    public void refreshHz(double hz) {
        this.refreshHz = Double.isFinite(hz) && hz >= MIN_REFRESH_HZ ? Math.min(hz, MAX_REFRESH_HZ) : DEFAULT_REFRESH_HZ;
    }

    public double refreshHz() {
        return this.refreshHz;
    }

    public boolean primed() {
        return this.primed;
    }

    public long pipelineNanos() {
        return this.primed ? Math.round(this.pipelineNanos) : 0L;
    }

    public long scanoutNanos() {
        return Math.round(0.5E9 / this.refreshHz);
    }

    public long compensationNanos() {
        return this.pipelineNanos() + this.scanoutNanos();
    }

    public long applied(long videoLatencyNanos) {
        return appliedLatency(videoLatencyNanos, this.frameCompensationNanos());
    }

    public static long appliedLatency(long videoLatencyNanos, long compensationNanos) {
        return Math.max(0L, videoLatencyNanos - Math.max(0L, compensationNanos));
    }
}
