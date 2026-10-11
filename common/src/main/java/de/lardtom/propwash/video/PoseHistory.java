package de.lardtom.propwash.video;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;

public final class PoseHistory {
    private static final long NO_CUT = Long.MIN_VALUE;

    private final int capacity;
    private final long[] times;
    private final long[] cuts;
    private final double[] positions;
    private final float[] rotations;
    private int start;
    private int size;
    private long pendingCut = NO_CUT;

    public PoseHistory(int capacity) {
        if (capacity < 2) {
            throw new IllegalArgumentException("capacity must be at least 2: " + capacity);
        }
        this.capacity = capacity;
        this.times = new long[capacity];
        this.cuts = new long[capacity];
        this.positions = new double[capacity * 3];
        this.rotations = new float[capacity * 4];
    }

    public int capacity() {
        return this.capacity;
    }

    public int size() {
        return this.size;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public void clear() {
        this.start = 0;
        this.size = 0;
        this.pendingCut = NO_CUT;
    }

    public void cut(long nanos) {
        if (this.size == 0 || this.pendingCut != NO_CUT) {
            return;
        }
        long newest = this.newestNanos();
        if (nanos > newest) {
            this.pendingCut = nanos;
        } else if (newest < Long.MAX_VALUE) {
            this.pendingCut = newest + 1L;
        }
    }

    public boolean cutPending() {
        return this.pendingCut != NO_CUT;
    }

    public long oldestNanos() {
        return this.size == 0 ? Long.MIN_VALUE : this.times[this.start];
    }

    public long newestNanos() {
        return this.size == 0 ? Long.MIN_VALUE : this.times[this.slot(this.size - 1)];
    }

    public boolean record(long nanos, double x, double y, double z, Quaternionfc orientation) {
        return this.record(nanos, x, y, z, orientation.x(), orientation.y(), orientation.z(), orientation.w());
    }

    public boolean record(long nanos, double x, double y, double z, float qx, float qy, float qz, float qw) {
        if (this.size > 0 && nanos <= this.newestNanos()) {
            return false;
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return false;
        }
        double length = Math.sqrt((double) qx * qx + (double) qy * qy + (double) qz * qz + (double) qw * qw);
        if (!Double.isFinite(length) || length < 1.0E-6) {
            qx = 0.0F;
            qy = 0.0F;
            qz = 0.0F;
            qw = 1.0F;
            length = 1.0;
        }
        int slot;
        if (this.size < this.capacity) {
            slot = this.slot(this.size);
            this.size++;
        } else {
            slot = this.start;
            this.start = this.start + 1 == this.capacity ? 0 : this.start + 1;
        }
        this.times[slot] = nanos;
        this.cuts[slot] = this.pendingCut == NO_CUT ? NO_CUT : Math.min(this.pendingCut, nanos);
        this.pendingCut = NO_CUT;
        this.positions[slot * 3] = x;
        this.positions[slot * 3 + 1] = y;
        this.positions[slot * 3 + 2] = z;
        this.rotations[slot * 4] = (float) (qx / length);
        this.rotations[slot * 4 + 1] = (float) (qy / length);
        this.rotations[slot * 4 + 2] = (float) (qz / length);
        this.rotations[slot * 4 + 3] = (float) (qw / length);
        return true;
    }

    public boolean sample(long nanos, Vector3d outPosition, Quaternionf outOrientation) {
        if (this.size == 0 || (this.pendingCut != NO_CUT && nanos >= this.pendingCut)) {
            return false;
        }
        int last = this.size - 1;
        if (nanos <= this.times[this.start]) {
            this.copy(this.start, outPosition, outOrientation);
            return true;
        }
        if (nanos >= this.times[this.slot(last)]) {
            this.copy(this.slot(last), outPosition, outOrientation);
            return true;
        }
        int low = 0;
        int high = last;
        while (high - low > 1) {
            int mid = (low + high) >>> 1;
            if (this.times[this.slot(mid)] <= nanos) {
                low = mid;
            } else {
                high = mid;
            }
        }
        int a = this.slot(low);
        int b = this.slot(high);
        long cut = this.cuts[b];
        if (cut != NO_CUT) {
            this.copy(nanos >= cut ? b : a, outPosition, outOrientation);
            return true;
        }
        double t = (double) (nanos - this.times[a]) / (double) (this.times[b] - this.times[a]);
        outPosition.set(
                lerp(this.positions[a * 3], this.positions[b * 3], t),
                lerp(this.positions[a * 3 + 1], this.positions[b * 3 + 1], t),
                lerp(this.positions[a * 3 + 2], this.positions[b * 3 + 2], t));
        slerp(this.rotations, a * 4, b * 4, t, outOrientation);
        return true;
    }

    private int slot(int index) {
        int slot = this.start + index;
        return slot >= this.capacity ? slot - this.capacity : slot;
    }

    private void copy(int slot, Vector3d outPosition, Quaternionf outOrientation) {
        outPosition.set(this.positions[slot * 3], this.positions[slot * 3 + 1], this.positions[slot * 3 + 2]);
        outOrientation.set(this.rotations[slot * 4], this.rotations[slot * 4 + 1], this.rotations[slot * 4 + 2], this.rotations[slot * 4 + 3]);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    static void slerp(float[] rotations, int a, int b, double t, Quaternionf out) {
        double ax = rotations[a];
        double ay = rotations[a + 1];
        double az = rotations[a + 2];
        double aw = rotations[a + 3];
        double bx = rotations[b];
        double by = rotations[b + 1];
        double bz = rotations[b + 2];
        double bw = rotations[b + 3];
        double dot = ax * bx + ay * by + az * bz + aw * bw;
        if (dot < 0.0) {
            bx = -bx;
            by = -by;
            bz = -bz;
            bw = -bw;
            dot = -dot;
        }
        double wa;
        double wb;
        if (dot > 0.9995) {
            wa = 1.0 - t;
            wb = t;
        } else {
            double theta = Math.acos(Math.min(dot, 1.0));
            double sin = Math.sin(theta);
            wa = Math.sin((1.0 - t) * theta) / sin;
            wb = Math.sin(t * theta) / sin;
        }
        double x = wa * ax + wb * bx;
        double y = wa * ay + wb * by;
        double z = wa * az + wb * bz;
        double w = wa * aw + wb * bw;
        double length = Math.sqrt(x * x + y * y + z * z + w * w);
        if (length < 1.0E-9) {
            out.set((float) ax, (float) ay, (float) az, (float) aw);
            return;
        }
        out.set((float) (x / length), (float) (y / length), (float) (z / length), (float) (w / length));
    }
}
