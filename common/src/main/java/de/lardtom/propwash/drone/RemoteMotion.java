package de.lardtom.propwash.drone;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class RemoteMotion {
    public static final long TICK_NANOS = 50_000_000L;
    public static final long MIN_STEP_NANOS = 10_000_000L;
    public static final long MIN_DELAY_NANOS = 100_000_000L;
    public static final long MAX_DELAY_NANOS = 200_000_000L;
    public static final long BASE_DELAY_NANOS = 75_000_000L;
    public static final double JITTER_FACTOR = 2.5;
    public static final long JITTER_CAP_NANOS = 250_000_000L;
    public static final long PAUSE_NANOS = 1_000_000_000L;
    public static final long MAX_EXTRAPOLATION_NANOS = 250_000_000L;
    public static final long BLEND_NANOS = 120_000_000L;
    public static final double DELAY_SLEW = 0.05;
    public static final double SNAP_DISTANCE = 16.0;
    public static final long SNAP_SLACK_NANOS = 250_000_000L;
    public static final double SNAP_PACE_FACTOR = 1.5;
    public static final long MOTION_MATCH_NANOS = 10_000_000L;
    public static final long VELOCITY_BASE_NANOS = 120_000_000L;
    public static final long HOLD_GAP_NANOS = 250_000_000L;
    public static final long CONTINUOUS_NANOS = 500_000_000L;
    public static final long RESYNC_NANOS = 400_000_000L;
    public static final long DRIFT_DIVISOR = 8L;
    public static final double MIN_PACE = 1.5;
    public static final double MIN_MOVE = 0.02;
    public static final long PACE_MIN_GAP_NANOS = 30_000_000L;
    public static final long PACE_MAX_GAP_NANOS = 120_000_000L;
    public static final int CAPACITY = 64;

    @FunctionalInterface
    interface Ground {
        double floor(double x, double z, double fromY, double toY);
    }

    private @Nullable Ground ground;

    private final long[] posStamps = new long[CAPACITY];
    private final double[] positions = new double[CAPACITY * 3];
    private final boolean[] posHolds = new boolean[CAPACITY];
    private int posCount;
    private int posHead;
    private long lastPosArrival = Long.MIN_VALUE;
    private double lastSpeed = -1.0;
    private double paceEstimate = -1.0;
    private long late;

    private final long[] rotStamps = new long[CAPACITY];
    private final float[] rotations = new float[CAPACITY * 4];
    private final boolean[] rotHolds = new boolean[CAPACITY];
    private int rotCount;
    private int rotHead;

    private boolean hasMotion;
    private long motionArrival;
    private double vx;
    private double vy;
    private double vz;

    private double jitter;
    private long delay = MIN_DELAY_NANOS;
    private long lastSample = Long.MIN_VALUE;

    private double errX;
    private double errY;
    private double errZ;
    private final Quaternionf rotError = new Quaternionf();
    private boolean shownPosition;
    private final double[] shown = new double[3];
    private boolean shownRotation;
    private final Quaternionf shownRot = new Quaternionf();
    private final double[] target = new double[3];
    private final Quaternionf targetRot = new Quaternionf();
    private final Quaternionf scratch = new Quaternionf();
    private final Quaternionf scratch2 = new Quaternionf();
    private final Vector3f axis = new Vector3f();

    void ground(@Nullable Ground ground) {
        this.ground = ground;
    }

    public void clear() {
        this.posCount = 0;
        this.posHead = 0;
        this.rotCount = 0;
        this.rotHead = 0;
        this.lastPosArrival = Long.MIN_VALUE;
        this.lastSpeed = -1.0;
        this.paceEstimate = -1.0;
        this.late = 0L;
        this.hasMotion = false;
        this.jitter = 0.0;
        this.delay = MIN_DELAY_NANOS;
        this.lastSample = Long.MIN_VALUE;
        this.errX = 0.0;
        this.errY = 0.0;
        this.errZ = 0.0;
        this.rotError.identity();
        this.shownPosition = false;
        this.shownRotation = false;
    }

    public boolean empty() {
        return this.posCount == 0;
    }

    public boolean hasRotation() {
        return this.rotCount > 0;
    }

    public long delayNanos() {
        return this.delay;
    }

    public double jitterNanos() {
        return this.jitter;
    }

    public long targetDelayNanos() {
        return Math.clamp(BASE_DELAY_NANOS + Math.round(JITTER_FACTOR * this.jitter), MIN_DELAY_NANOS, MAX_DELAY_NANOS);
    }

    public boolean position(long arrival, double x, double y, double z) {
        if (this.posCount > 0) {
            int last = this.posIndex(this.posCount - 1);
            double dx = x - this.positions[last * 3];
            double dy = y - this.positions[last * 3 + 1];
            double dz = z - this.positions[last * 3 + 2];
            double reach = this.snapDistance(arrival);
            if (dx * dx + dy * dy + dz * dz > reach * reach) {
                this.clear();
            }
        }
        boolean fresh = this.posCount == 0;
        long stamp = arrival;
        double speed = this.hasMotion && this.motionArrival >= this.lastPosArrival - MOTION_MATCH_NANOS
                ? Math.sqrt(this.vx * this.vx + this.vy * this.vy + this.vz * this.vz) : -1.0;
        if (!fresh) {
            int last = this.posIndex(this.posCount - 1);
            long lastStamp = this.posStamps[last];
            long gap = Math.max(0L, arrival - this.lastPosArrival);
            double moved = Math.sqrt(sq(x - this.positions[last * 3]) + sq(y - this.positions[last * 3 + 1]) + sq(z - this.positions[last * 3 + 2]));
            if (gap >= PACE_MIN_GAP_NANOS && gap <= PACE_MAX_GAP_NANOS) {
                double observed = moved / (gap * 1.0E-9);
                this.paceEstimate = this.paceEstimate < 0.0 ? observed : this.paceEstimate + (observed - this.paceEstimate) / 8.0;
            }
            double pace = speed >= 0.0 && this.lastSpeed >= 0.0 ? 0.5 * (speed + this.lastSpeed) : speed >= 0.0 ? speed : this.paceEstimate;
            long step = pace >= MIN_PACE && moved > MIN_MOVE ? Math.round(moved / pace * 1.0E9) : gap;
            step = Math.clamp(step, MIN_STEP_NANOS, PAUSE_NANOS);
            long predicted = lastStamp + step;
            long error = arrival - predicted;
            if (Math.abs(error) > RESYNC_NANOS) {
                stamp = Math.max(arrival, lastStamp + MIN_STEP_NANOS);
            } else {
                stamp = Math.max(lastStamp + MIN_STEP_NANOS, predicted + error / DRIFT_DIVISOR);
            }
            this.jitter += (Math.min(JITTER_CAP_NANOS, Math.abs(error)) - this.jitter) / 16.0;
            this.late = Math.clamp(error, 0L, PAUSE_NANOS);
        }
        this.lastSpeed = speed;
        this.lastPosArrival = arrival;
        if (!fresh && !this.moving()) {
            int last = this.posIndex(this.posCount - 1);
            long lastStamp = this.posStamps[last];
            if (stamp - lastStamp > HOLD_GAP_NANOS) {
                this.pushPosition(Math.max(lastStamp + 1L, stamp - TICK_NANOS), this.positions[last * 3], this.positions[last * 3 + 1],
                        this.positions[last * 3 + 2], true);
            }
        }
        this.pushPosition(stamp, x, y, z, false);
        if (!fresh) {
            this.anchorPosition();
        }
        return fresh;
    }

    double snapDistance(long arrival) {
        double pace = Math.max(this.paceEstimate, this.lastSpeed);
        if (this.hasMotion) {
            pace = Math.max(pace, Math.sqrt(this.vx * this.vx + this.vy * this.vy + this.vz * this.vz));
        }
        if (pace <= 0.0 || this.posCount == 0 || this.lastPosArrival == Long.MIN_VALUE || this.stoppedBefore(arrival)) {
            return SNAP_DISTANCE;
        }
        long gap = Math.clamp(arrival - this.lastPosArrival + this.late, 0L, PAUSE_NANOS);
        return Math.max(SNAP_DISTANCE, pace * (gap + SNAP_SLACK_NANOS) * 1.0E-9 * SNAP_PACE_FACTOR);
    }

    private boolean stoppedBefore(long arrival) {
        return this.hasMotion && !this.moving() && this.motionArrival < arrival - MOTION_MATCH_NANOS;
    }

    public void seed(long arrival, double x, double y, double z, double blocksPerSecondX, double blocksPerSecondY, double blocksPerSecondZ) {
        if (!this.hasMotion) {
            this.motion(arrival, blocksPerSecondX, blocksPerSecondY, blocksPerSecondZ);
        }
        this.pushPosition(arrival, x, y, z, true);
    }

    public void motion(long arrival, double blocksPerSecondX, double blocksPerSecondY, double blocksPerSecondZ) {
        this.hasMotion = true;
        this.motionArrival = arrival;
        this.vx = blocksPerSecondX;
        this.vy = blocksPerSecondY;
        this.vz = blocksPerSecondZ;
        this.anchorPosition();
    }

    private void anchorPosition() {
        if (!this.shownPosition || this.lastSample == Long.MIN_VALUE || this.posCount == 0) {
            return;
        }
        this.rawPosition(this.lastSample - this.delay, this.target);
        this.errX = this.shown[0] - this.target[0];
        this.errY = this.shown[1] - this.target[1];
        this.errZ = this.shown[2] - this.target[2];
    }

    public void orientation(long arrival, Quaternionfc rotation) {
        long stamp = this.rotCount == 0 ? arrival : Math.max(arrival, this.rotStamps[this.rotIndex(this.rotCount - 1)] + MIN_STEP_NANOS);
        if (this.posCount > 0 && Math.abs(arrival - this.lastPosArrival) <= MOTION_MATCH_NANOS) {
            long paired = this.posStamps[this.posIndex(this.posCount - 1)];
            stamp = this.rotCount == 0 ? paired : Math.max(paired, this.rotStamps[this.rotIndex(this.rotCount - 1)] + 1L);
        }
        boolean fresh = this.rotCount == 0;
        if (!fresh && !this.moving()) {
            int last = this.rotIndex(this.rotCount - 1);
            long lastStamp = this.rotStamps[last];
            if (stamp - lastStamp > HOLD_GAP_NANOS) {
                this.pushRotation(Math.max(lastStamp + 1L, stamp - TICK_NANOS), this.loadRotation(last, new Quaternionf()), true);
            }
        }
        this.pushRotation(stamp, rotation, false);
        if (!fresh && this.shownRotation && this.lastSample != Long.MIN_VALUE) {
            this.rawRotation(this.lastSample - this.delay, this.targetRot);
            this.rotError.set(this.shownRot).mul(this.scratch2.set(this.targetRot).conjugate()).normalize();
        }
    }

    public boolean sample(long now, double[] position, Quaternionf rotation) {
        if (this.posCount == 0 && this.rotCount == 0) {
            return false;
        }
        long dt = this.lastSample == Long.MIN_VALUE ? 0L : Math.max(0L, now - this.lastSample);
        long wanted = this.targetDelayNanos();
        long slew = Math.max(1L, Math.round(dt * DELAY_SLEW));
        this.delay += Math.clamp(wanted - this.delay, -slew, slew);
        this.lastSample = now;
        long playout = now - this.delay;
        double decay = dt <= 0L ? 1.0 : Math.exp(-(double) dt / BLEND_NANOS);
        this.errX *= decay;
        this.errY *= decay;
        this.errZ *= decay;
        this.rotError.slerp(this.scratch.identity(), (float) (1.0 - decay)).normalize();
        boolean any = false;
        if (this.posCount > 0) {
            this.rawPosition(playout, this.target);
            position[0] = this.target[0] + this.errX;
            position[1] = this.target[1] + this.errY;
            position[2] = this.target[2] + this.errZ;
            if (this.ground != null) {
                int last = this.posIndex(this.posCount - 1);
                double from = playout >= this.posStamps[last] ? this.positions[last * 3 + 1] : this.target[1];
                if (position[1] < from) {
                    position[1] = Math.max(position[1], this.ground.floor(position[0], position[2], from, position[1]));
                }
            }
            this.shown[0] = position[0];
            this.shown[1] = position[1];
            this.shown[2] = position[2];
            this.shownPosition = true;
            any = true;
        }
        if (this.rotCount > 0) {
            this.rawRotation(playout, this.targetRot);
            rotation.set(this.rotError).mul(this.targetRot).normalize();
            this.shownRot.set(rotation);
            this.shownRotation = true;
            any = true;
        }
        return any;
    }

    public boolean active(long now) {
        if (this.posCount == 0) {
            return false;
        }
        long last = this.posStamps[this.posIndex(this.posCount - 1)];
        return now - this.delay < last + MAX_EXTRAPOLATION_NANOS + 3L * BLEND_NANOS;
    }

    public void latest(double[] out) {
        int last = this.posIndex(this.posCount - 1);
        out[0] = this.positions[last * 3];
        out[1] = this.positions[last * 3 + 1];
        out[2] = this.positions[last * 3 + 2];
    }

    private void rawPosition(long time, double[] out) {
        int n = this.posCount;
        int first = this.posIndex(0);
        if (n == 1 || time <= this.posStamps[first]) {
            copy(this.positions, first, out);
            return;
        }
        int lastIdx = this.posIndex(n - 1);
        long lastStamp = this.posStamps[lastIdx];
        if (time >= lastStamp) {
            long ahead = Math.min(time - lastStamp, MAX_EXTRAPOLATION_NANOS);
            double seconds = ahead * 1.0E-9;
            double mx;
            double my;
            double mz;
            if (this.hasMotion && this.motionArrival >= this.lastPosArrival - MOTION_MATCH_NANOS) {
                mx = this.vx;
                my = this.vy;
                mz = this.vz;
            } else {
                int base = this.baseline(this.posStamps, this.posHolds, this.posHead, n);
                if (base < 0 || !this.moving()) {
                    copy(this.positions, lastIdx, out);
                    return;
                }
                int prevIdx = this.posIndex(base);
                double span = Math.max(1L, lastStamp - this.posStamps[prevIdx]) * 1.0E-9;
                mx = (this.positions[lastIdx * 3] - this.positions[prevIdx * 3]) / span;
                my = (this.positions[lastIdx * 3 + 1] - this.positions[prevIdx * 3 + 1]) / span;
                mz = (this.positions[lastIdx * 3 + 2] - this.positions[prevIdx * 3 + 2]) / span;
            }
            out[0] = this.positions[lastIdx * 3] + mx * seconds;
            out[1] = this.positions[lastIdx * 3 + 1] + my * seconds;
            out[2] = this.positions[lastIdx * 3 + 2] + mz * seconds;
            return;
        }
        for (int k = n - 2; k >= 0; k--) {
            int a = this.posIndex(k);
            if (this.posStamps[a] <= time) {
                int b = this.posIndex(k + 1);
                double f = (time - this.posStamps[a]) / (double) Math.max(1L, this.posStamps[b] - this.posStamps[a]);
                for (int c = 0; c < 3; c++) {
                    out[c] = this.positions[a * 3 + c] + (this.positions[b * 3 + c] - this.positions[a * 3 + c]) * f;
                }
                return;
            }
        }
        copy(this.positions, first, out);
    }

    private void rawRotation(long time, Quaternionf out) {
        int n = this.rotCount;
        int first = this.rotIndex(0);
        if (n == 1 || time <= this.rotStamps[first]) {
            this.loadRotation(first, out);
            return;
        }
        int lastIdx = this.rotIndex(n - 1);
        long lastStamp = this.rotStamps[lastIdx];
        if (time >= lastStamp) {
            int base = this.baseline(this.rotStamps, this.rotHolds, this.rotHead, n);
            if (base < 0 || !this.moving()) {
                this.loadRotation(lastIdx, out);
                return;
            }
            int prevIdx = this.rotIndex(base);
            long span = Math.max(1L, lastStamp - this.rotStamps[prevIdx]);
            long ahead = Math.min(time - lastStamp, MAX_EXTRAPOLATION_NANOS);
            this.loadRotation(lastIdx, out);
            Quaternionf prev = this.loadRotation(prevIdx, this.scratch);
            Quaternionf delta = new Quaternionf(out).mul(prev.conjugate()).normalize();
            if (delta.w < 0.0F) {
                delta.set(-delta.x, -delta.y, -delta.z, -delta.w);
            }
            float angle = 2.0F * (float) Math.acos(Math.clamp(delta.w, -1.0F, 1.0F));
            float sin = (float) Math.sqrt(Math.max(0.0F, 1.0F - delta.w * delta.w));
            if (angle > 1.0E-5F && sin > 1.0E-6F) {
                this.axis.set(delta.x / sin, delta.y / sin, delta.z / sin);
                float turned = angle * ahead / (float) span;
                out.premul(new Quaternionf().fromAxisAngleRad(this.axis, turned)).normalize();
            }
            return;
        }
        for (int k = n - 2; k >= 0; k--) {
            int a = this.rotIndex(k);
            if (this.rotStamps[a] <= time) {
                int b = this.rotIndex(k + 1);
                float f = (float) ((time - this.rotStamps[a]) / (double) Math.max(1L, this.rotStamps[b] - this.rotStamps[a]));
                this.loadRotation(a, out);
                out.slerp(this.loadRotation(b, this.scratch), f).normalize();
                return;
            }
        }
        this.loadRotation(first, out);
    }

    private boolean moving() {
        if (this.hasMotion) {
            return this.vx * this.vx + this.vy * this.vy + this.vz * this.vz >= MIN_PACE * MIN_PACE;
        }
        return this.paceEstimate >= MIN_PACE;
    }

    private static int baseline(long[] stamps, boolean[] holds, int head, int n) {
        int k = n - 2;
        if (k < 0 || holds[(head + k) % CAPACITY]) {
            return -1;
        }
        long last = stamps[(head + n - 1) % CAPACITY];
        if (last - stamps[(head + k) % CAPACITY] > CONTINUOUS_NANOS) {
            return -1;
        }
        while (k > 0 && last - stamps[(head + k) % CAPACITY] < VELOCITY_BASE_NANOS && !holds[(head + k - 1) % CAPACITY]
                && stamps[(head + k) % CAPACITY] - stamps[(head + k - 1) % CAPACITY] < CONTINUOUS_NANOS) {
            k--;
        }
        return k;
    }

    private static double sq(double value) {
        return value * value;
    }

    private static void copy(double[] values, int index, double[] out) {
        out[0] = values[index * 3];
        out[1] = values[index * 3 + 1];
        out[2] = values[index * 3 + 2];
    }

    private Quaternionf loadRotation(int index, Quaternionf out) {
        return out.set(this.rotations[index * 4], this.rotations[index * 4 + 1], this.rotations[index * 4 + 2], this.rotations[index * 4 + 3]);
    }

    private int posIndex(int k) {
        return (this.posHead + k) % CAPACITY;
    }

    private int rotIndex(int k) {
        return (this.rotHead + k) % CAPACITY;
    }

    private void pushPosition(long stamp, double x, double y, double z, boolean hold) {
        if (this.posCount == CAPACITY) {
            this.posHead = (this.posHead + 1) % CAPACITY;
            this.posCount--;
        }
        int i = this.posIndex(this.posCount++);
        this.posStamps[i] = stamp;
        this.posHolds[i] = hold;
        this.positions[i * 3] = x;
        this.positions[i * 3 + 1] = y;
        this.positions[i * 3 + 2] = z;
    }

    private void pushRotation(long stamp, Quaternionfc rotation, boolean hold) {
        if (this.rotCount == CAPACITY) {
            this.rotHead = (this.rotHead + 1) % CAPACITY;
            this.rotCount--;
        }
        int i = this.rotIndex(this.rotCount++);
        float sign = 1.0F;
        if (this.rotCount > 1) {
            int prev = this.rotIndex(this.rotCount - 2);
            float dot = this.rotations[prev * 4] * rotation.x() + this.rotations[prev * 4 + 1] * rotation.y() + this.rotations[prev * 4 + 2] * rotation.z()
                    + this.rotations[prev * 4 + 3] * rotation.w();
            sign = dot < 0.0F ? -1.0F : 1.0F;
        }
        this.rotStamps[i] = stamp;
        this.rotHolds[i] = hold;
        this.rotations[i * 4] = sign * rotation.x();
        this.rotations[i * 4 + 1] = sign * rotation.y();
        this.rotations[i * 4 + 2] = sign * rotation.z();
        this.rotations[i * 4 + 3] = sign * rotation.w();
    }
}
