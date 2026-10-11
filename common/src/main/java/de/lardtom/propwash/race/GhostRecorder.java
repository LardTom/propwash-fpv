package de.lardtom.propwash.race;

import org.joml.Quaternionfc;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

public final class GhostRecorder {
    public static final long RAW_SPACING_NANOS = 10_000_000L;
    public static final long IDLE_HISTORY_NANOS = 1_000_000_000L;
    public static final int MAX_RAW = (int) (GhostTrack.MAX_DURATION_NANOS / RAW_SPACING_NANOS) + 64;

    private long[] times = new long[256];
    private double[] positions = new double[256 * 3];
    private float[] rotations = new float[256 * 4];
    private int count;
    private int courseId = LapTracker.NO_COURSE;
    private long lapStart = LapTracker.NONE;
    private boolean overflow;

    public void reset() {
        this.count = 0;
        this.courseId = LapTracker.NO_COURSE;
        this.lapStart = LapTracker.NONE;
        this.overflow = false;
    }

    public boolean recording() {
        return this.lapStart != LapTracker.NONE;
    }

    public int courseId() {
        return this.courseId;
    }

    public int recordedSamples() {
        return this.count;
    }

    public void onLapStart(int courseId, long startNanos) {
        this.courseId = courseId;
        this.lapStart = startNanos;
        this.overflow = false;
        this.dropBefore(startNanos);
    }

    public @Nullable GhostTrack onLapComplete(long lapNanos, long crossingNanos, String frame, int layout) {
        GhostTrack track = null;
        if (this.recording() && !this.overflow && crossingNanos > this.lapStart) {
            track = GhostTrack.resample(this.times, this.positions, this.rotations, this.count, this.lapStart, crossingNanos, lapNanos, frame, layout);
        }
        this.lapStart = crossingNanos;
        this.overflow = false;
        this.dropBefore(crossingNanos);
        return track;
    }

    public @Nullable GhostTrack cut(long startNanos, long endNanos, long lapNanos, String frame, int layout) {
        if (this.overflow || this.count == 0 || this.times[0] > startNanos + RAW_SPACING_NANOS * 10L) {
            return null;
        }
        return GhostTrack.resample(this.times, this.positions, this.rotations, this.count, startNanos, endNanos, lapNanos, frame, layout);
    }

    public void trim(long beforeNanos) {
        this.dropBefore(beforeNanos);
        this.overflow = false;
    }

    public void stop() {
        this.lapStart = LapTracker.NONE;
        this.overflow = false;
    }

    public void sample(long nowNanos, double x, double y, double z, Quaternionfc rotation) {
        if (this.count > 0 && nowNanos - this.times[this.count - 1] < RAW_SPACING_NANOS) {
            return;
        }
        if (!this.recording()) {
            this.dropBefore(nowNanos - IDLE_HISTORY_NANOS);
        } else if (this.count >= MAX_RAW) {
            this.dropBefore(this.times[MAX_RAW / 4]);
            if (this.times[0] > this.lapStart) {
                this.overflow = true;
            }
        }
        this.ensureCapacity(this.count + 1);
        int i = this.count++;
        this.times[i] = nowNanos;
        this.positions[i * 3] = x;
        this.positions[i * 3 + 1] = y;
        this.positions[i * 3 + 2] = z;
        this.rotations[i * 4] = rotation.x();
        this.rotations[i * 4 + 1] = rotation.y();
        this.rotations[i * 4 + 2] = rotation.z();
        this.rotations[i * 4 + 3] = rotation.w();
    }

    private void dropBefore(long nanos) {
        int keepFrom = 0;
        while (keepFrom + 1 < this.count && this.times[keepFrom + 1] <= nanos) {
            keepFrom++;
        }
        if (keepFrom == 0) {
            return;
        }
        int left = this.count - keepFrom;
        System.arraycopy(this.times, keepFrom, this.times, 0, left);
        System.arraycopy(this.positions, keepFrom * 3, this.positions, 0, left * 3);
        System.arraycopy(this.rotations, keepFrom * 4, this.rotations, 0, left * 4);
        this.count = left;
    }

    private void ensureCapacity(int size) {
        if (size <= this.times.length) {
            return;
        }
        int grown = Math.max(size, this.times.length * 2);
        this.times = Arrays.copyOf(this.times, grown);
        this.positions = Arrays.copyOf(this.positions, grown * 3);
        this.rotations = Arrays.copyOf(this.rotations, grown * 4);
    }
}
