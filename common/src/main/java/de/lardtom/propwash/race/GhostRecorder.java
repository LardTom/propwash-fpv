package de.lardtom.propwash.race;

import org.joml.Quaternionfc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Arrays;

public final class GhostRecorder {
    public static final long RAW_SPACING_NANOS = 10_000_000L;
    public static final long LEAD_NANOS = 1_000_000_000L;
    public static final long IDLE_HISTORY_NANOS = LEAD_NANOS;
    public static final int MAX_RAW = (int) ((GhostTrack.MAX_DURATION_NANOS + LEAD_NANOS) / RAW_SPACING_NANOS) + 64;
    public static final long STEADY_GAP_NANOS = 200_000_000L;
    public static final int STEADY_HALF_WINDOW = 4;
    public static final long UPLOAD_INTERVAL_NANOS = RAW_SPACING_NANOS;
    public static final int KEPT_LAPS = 4;

    public record Raw(long[] times, double[] positions) {
        public int count() {
            return this.times.length;
        }
    }

    private record Kept(int courseId, long lapNanos, GhostTrack track) {
    }

    private long[] times = new long[256];
    private double[] positions = new double[256 * 3];
    private float[] rotations = new float[256 * 4];
    private int count;
    private int courseId = LapTracker.NO_COURSE;
    private long lapStart = LapTracker.NONE;
    private boolean overflow;
    private final ArrayDeque<Kept> kept = new ArrayDeque<>();

    public void reset() {
        this.kept.clear();
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
        this.dropBefore(startNanos - LEAD_NANOS);
    }

    public @Nullable GhostTrack onLapComplete(long lapNanos, long crossingNanos, String frame, int layout) {
        GhostTrack track = null;
        if (this.recording() && !this.overflow && crossingNanos > this.lapStart) {
            long lead = this.leadBefore(this.lapStart, LEAD_NANOS);
            track = GhostTrack.resample(this.times, this.positions, this.rotations, this.count, this.lapStart, crossingNanos, lapNanos, frame, layout,
                    GhostTrack.INTERVAL_NANOS, lead);
            GhostTrack fine = GhostTrack.resample(this.times, this.positions, this.rotations, this.count, this.lapStart, crossingNanos, lapNanos, frame,
                    layout, UPLOAD_INTERVAL_NANOS, lead);
            if (fine != null) {
                this.keep(new Kept(this.courseId, lapNanos, fine));
            }
        }
        this.lapStart = crossingNanos;
        this.overflow = false;
        this.dropBefore(crossingNanos - LEAD_NANOS);
        return track;
    }

    private void keep(Kept lap) {
        this.kept.removeIf(other -> other.courseId() == lap.courseId() && other.lapNanos() == lap.lapNanos());
        this.kept.addLast(lap);
        while (this.kept.size() > KEPT_LAPS) {
            this.kept.pollFirst();
        }
    }

    public @Nullable GhostTrack keptLap(int courseId, long lapNanos) {
        for (Kept lap : this.kept) {
            if (lap.courseId() == courseId && lap.lapNanos() == lapNanos) {
                return lap.track();
            }
        }
        return null;
    }

    public int keptLaps() {
        return this.kept.size();
    }

    public Raw window(long fromNanos, long toNanos) {
        int first = 0;
        while (first < this.count && this.times[first] < fromNanos) {
            first++;
        }
        int last = first;
        while (last < this.count && this.times[last] <= toNanos) {
            last++;
        }
        return new Raw(Arrays.copyOfRange(this.times, first, last), Arrays.copyOfRange(this.positions, first * 3, last * 3));
    }

    public @Nullable GhostTrack cut(long startNanos, long endNanos, long lapNanos, String frame, int layout) {
        if (this.overflow || this.count == 0 || this.times[0] > startNanos + RAW_SPACING_NANOS * 10L) {
            return null;
        }
        return GhostTrack.resample(this.times, this.positions, this.rotations, this.count, startNanos, endNanos, lapNanos, frame, layout);
    }

    public @Nullable GhostTrack cut(long startNanos, long endNanos, long lapNanos, String frame, int layout, long intervalNanos) {
        return this.cut(startNanos, endNanos, lapNanos, frame, layout, intervalNanos, 0L);
    }

    public @Nullable GhostTrack cut(long startNanos, long endNanos, long lapNanos, String frame, int layout, long intervalNanos, long leadNanos) {
        if (this.overflow || this.count == 0 || this.times[0] > startNanos + RAW_SPACING_NANOS * 10L) {
            return null;
        }
        long[] steady = steady(this.times, this.count);
        return GhostTrack.resample(steady, this.positions, this.rotations, this.count, startNanos, endNanos, lapNanos, frame, layout, intervalNanos,
                this.leadBefore(startNanos, leadNanos));
    }

    public long leadBefore(long gateNanos, long maxNanos) {
        int k = this.count - 1;
        while (k >= 0 && this.times[k] > gateNanos) {
            k--;
        }
        if (k < 0 || maxNanos <= 0L || k + 1 < this.count && !this.joined(k, k + 1)) {
            return 0L;
        }
        int first = k;
        while (first > 0 && gateNanos - this.times[first] < maxNanos && this.joined(first - 1, first)) {
            first--;
        }
        return Math.min(maxNanos, gateNanos - this.times[first]);
    }

    private boolean joined(int a, int b) {
        if (this.times[b] - this.times[a] > STEADY_GAP_NANOS) {
            return false;
        }
        double dx = this.positions[b * 3] - this.positions[a * 3];
        double dy = this.positions[b * 3 + 1] - this.positions[a * 3 + 1];
        double dz = this.positions[b * 3 + 2] - this.positions[a * 3 + 2];
        return dx * dx + dy * dy + dz * dz <= LapTracker.MAX_SEGMENT * LapTracker.MAX_SEGMENT;
    }

    public static long[] steady(long[] times, int count) {
        long[] out = Arrays.copyOf(times, Math.max(count, 0));
        int start = 0;
        for (int k = 1; k <= count; k++) {
            if (k == count || times[k] - times[k - 1] > STEADY_GAP_NANOS) {
                fit(times, start, k, out);
                start = k;
            }
        }
        for (int k = 1; k < count; k++) {
            if (out[k] <= out[k - 1]) {
                out[k] = out[k - 1] + 1L;
            }
        }
        return out;
    }

    private static void fit(long[] times, int from, int to, long[] out) {
        if (to - from < 3) {
            return;
        }
        for (int k = from; k < to; k++) {
            int a = Math.max(from, k - STEADY_HALF_WINDOW);
            int b = Math.min(to - 1, k + STEADY_HALF_WINDOW);
            double n = b - a + 1;
            double meanX = (a + b) / 2.0;
            long base = times[a];
            double meanY = 0.0;
            for (int i = a; i <= b; i++) {
                meanY += times[i] - base;
            }
            meanY /= n;
            double cov = 0.0;
            double var = 0.0;
            for (int i = a; i <= b; i++) {
                double dx = i - meanX;
                cov += dx * (times[i] - base - meanY);
                var += dx * dx;
            }
            double slope = var > 0.0 ? cov / var : 0.0;
            out[k] = base + Math.round(meanY + slope * (k - meanX));
        }
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
