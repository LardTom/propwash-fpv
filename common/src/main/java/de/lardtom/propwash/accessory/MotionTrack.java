package de.lardtom.propwash.accessory;

import java.util.Arrays;

public final class MotionTrack {
    public static final long WINDOW_NANOS = 300_000_000L;
    public static final long MIN_SPAN_NANOS = 40_000_000L;
    public static final long STALE_NANOS = 500_000_000L;
    public static final long BURST_NANOS = 2_000_000L;
    public static final int CAPACITY = 32;
    private static final double SAME_POSITION = 1.0E-7;

    private final long[] times = new long[CAPACITY];
    private final double[] xs = new double[CAPACITY];
    private final double[] ys = new double[CAPACITY];
    private final double[] zs = new double[CAPACITY];
    private final double[] slopeX = new double[CAPACITY * (CAPACITY - 1) / 2];
    private final double[] slopeY = new double[CAPACITY * (CAPACITY - 1) / 2];
    private final double[] slopeZ = new double[CAPACITY * (CAPACITY - 1) / 2];
    private int newest = -1;
    private int size;

    public void reset() {
        this.newest = -1;
        this.size = 0;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public int size() {
        return this.size;
    }

    public long newestNanos() {
        return this.size == 0 ? Long.MIN_VALUE : this.times[this.newest];
    }

    public boolean endsAt(double x, double y, double z) {
        if (this.size == 0) {
            return false;
        }
        return Math.abs(this.xs[this.newest] - x) <= SAME_POSITION && Math.abs(this.ys[this.newest] - y) <= SAME_POSITION
                && Math.abs(this.zs[this.newest] - z) <= SAME_POSITION;
    }

    public void add(long nanos, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            this.reset();
            return;
        }
        long at = this.size == 0 ? nanos : Math.max(nanos, this.times[this.newest]);
        if (this.size == 0 || at - this.times[this.newest] >= BURST_NANOS) {
            this.newest = (this.newest + 1) % CAPACITY;
            this.size = Math.min(CAPACITY, this.size + 1);
        }
        this.times[this.newest] = at;
        this.xs[this.newest] = x;
        this.ys[this.newest] = y;
        this.zs[this.newest] = z;
    }

    public boolean fresh(long now) {
        return this.size > 0 && now - this.times[this.newest] <= STALE_NANOS;
    }

    public static void limit(double[] velocity, double maxSpeed) {
        double speed = Math.sqrt(velocity[0] * velocity[0] + velocity[1] * velocity[1] + velocity[2] * velocity[2]);
        if (!(maxSpeed >= 0.0) || !(speed > maxSpeed)) {
            return;
        }
        double scale = maxSpeed / speed;
        velocity[0] *= scale;
        velocity[1] *= scale;
        velocity[2] *= scale;
    }

    public boolean estimate(long now, double[] out) {
        if (this.size < 2 || !this.fresh(now)) {
            return false;
        }
        long end = this.times[this.newest];
        int count = 1;
        for (int back = 1; back < this.size; back++) {
            int index = Math.floorMod(this.newest - back, CAPACITY);
            long span = end - this.times[index];
            if (span > STALE_NANOS || span > WINDOW_NANOS && end - this.times[Math.floorMod(this.newest - count + 1, CAPACITY)] >= MIN_SPAN_NANOS) {
                break;
            }
            count++;
        }
        int pairs = 0;
        for (int a = 0; a < count; a++) {
            int i = Math.floorMod(this.newest - a, CAPACITY);
            for (int b = a + 1; b < count; b++) {
                int j = Math.floorMod(this.newest - b, CAPACITY);
                long span = this.times[i] - this.times[j];
                if (span < MIN_SPAN_NANOS) {
                    continue;
                }
                double seconds = span * 1.0E-9;
                this.slopeX[pairs] = (this.xs[i] - this.xs[j]) / seconds;
                this.slopeY[pairs] = (this.ys[i] - this.ys[j]) / seconds;
                this.slopeZ[pairs] = (this.zs[i] - this.zs[j]) / seconds;
                pairs++;
            }
        }
        if (pairs == 0) {
            return false;
        }
        out[0] = median(this.slopeX, pairs);
        out[1] = median(this.slopeY, pairs);
        out[2] = median(this.slopeZ, pairs);
        return true;
    }

    private static double median(double[] values, int count) {
        Arrays.sort(values, 0, count);
        int middle = count / 2;
        return count % 2 == 1 ? values[middle] : 0.5 * (values[middle - 1] + values[middle]);
    }
}
