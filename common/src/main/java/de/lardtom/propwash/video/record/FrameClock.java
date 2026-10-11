package de.lardtom.propwash.video.record;

public final class FrameClock {
    private static final double PHASE_GAIN = 0.1;

    private final int fps;
    private final long frameNanos;
    private long startNanos;
    private long pausedNanos;
    private long pauseStart = -1L;
    private long lastSlot = -1L;
    private double phaseNanos;
    private boolean started;

    public FrameClock(int fps) {
        this.fps = Math.max(1, fps);
        this.frameNanos = 1_000_000_000L / this.fps;
    }

    public void start(long nanos) {
        this.startNanos = nanos;
        this.pausedNanos = 0L;
        this.pauseStart = -1L;
        this.lastSlot = -1L;
        this.phaseNanos = this.frameNanos * 0.5;
        this.started = true;
    }

    public boolean started() {
        return this.started;
    }

    public boolean paused() {
        return this.pauseStart >= 0L;
    }

    public void pause(long nanos) {
        if (this.started && this.pauseStart < 0L) {
            this.pauseStart = nanos;
        }
    }

    public void resume(long nanos) {
        if (this.pauseStart >= 0L) {
            this.pausedNanos += Math.max(0L, nanos - this.pauseStart);
            this.pauseStart = -1L;
        }
    }

    public long elapsedNanos(long nanos) {
        if (!this.started) {
            return 0L;
        }
        long end = this.pauseStart >= 0L ? this.pauseStart : nanos;
        return Math.max(0L, end - this.startNanos - this.pausedNanos);
    }

    public long slotAt(long nanos) {
        return (long) Math.floor((this.elapsedNanos(nanos) + this.phaseNanos) * this.fps / 1.0E9);
    }

    public boolean due(long nanos) {
        return this.started && !this.paused() && this.slotAt(nanos) > this.lastSlot;
    }

    public long take(long nanos) {
        double position = (this.elapsedNanos(nanos) + this.phaseNanos) * this.fps / 1.0E9;
        long slot = (long) Math.floor(position);
        if (slot <= this.lastSlot) {
            return -1L;
        }
        double inside = position - slot;
        this.phaseNanos += (0.5 - inside) * this.frameNanos * PHASE_GAIN;
        this.phaseNanos = Math.max(0.0, Math.min(this.frameNanos, this.phaseNanos));
        this.lastSlot = slot;
        return slot;
    }

    public long lastSlot() {
        return this.lastSlot;
    }

    public long endSlot(long nanos) {
        return Math.max(this.lastSlot + 1L, this.slotAt(nanos));
    }

    public int fps() {
        return this.fps;
    }
}
