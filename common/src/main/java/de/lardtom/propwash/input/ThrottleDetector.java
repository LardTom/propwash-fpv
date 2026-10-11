package de.lardtom.propwash.input;

import java.util.Arrays;

public final class ThrottleDetector implements StickDetector {
    public static final float RANGE_THRESHOLD = 1.2F;
    public static final float MOVED = 0.2F;
    public static final float DOMINANCE = 0.6F;
    public static final float REVERSAL = 0.3F;
    public static final float FULL_STROKE = 0.8F;
    public static final float LEAD_IN = 0.4F;
    public static final float END_TOLERANCE = 0.15F;
    public static final long HOLD_NANOS = 1_500_000_000L;
    public static final long STUCK_NANOS = 1_500_000_000L;
    private static final long UNSET = Long.MIN_VALUE;

    private final boolean[] excluded;
    private final float[] startValue;
    private final int expectedAxis;
    private final boolean expectedInvert;
    private final boolean[] seen;
    private final float[] lo;
    private final float[] hi;
    private final float[] pivot;
    private final float[] peak;
    private final float[] leadFrom;
    private final int[] direction;
    private final int[] strokes;
    private int leadingAxis = -1;
    private int candidate = -1;
    private DetectionStage stage = DetectionStage.WAITING;
    private long holdSince = UNSET;
    private long restSince = UNSET;
    private boolean stuck;
    private boolean pendingInvert;

    public ThrottleDetector(float[] baseline, boolean[] excluded) {
        this(baseline, excluded, -1, false);
    }

    public ThrottleDetector(float[] baseline, boolean[] excluded, int expectedAxis, boolean expectedInvert) {
        int n = baseline.length;
        this.excluded = excluded == null ? new boolean[0] : excluded.clone();
        this.startValue = baseline.clone();
        this.expectedAxis = expectedAxis;
        this.expectedInvert = expectedInvert;
        this.seen = new boolean[n];
        this.lo = new float[n];
        this.hi = new float[n];
        this.pivot = new float[n];
        this.peak = new float[n];
        this.leadFrom = new float[n];
        this.direction = new int[n];
        this.strokes = new int[n];
        Arrays.fill(this.leadFrom, Float.NaN);
        for (int i = 0; i < n; i++) {
            if (!Float.isNaN(baseline[i])) {
                this.start(i, baseline[i]);
            }
        }
    }

    private void start(int axis, float value) {
        this.seen[axis] = true;
        this.lo[axis] = value;
        this.hi[axis] = value;
        this.pivot[axis] = value;
        this.peak[axis] = value;
        this.direction[axis] = 0;
        this.strokes[axis] = 0;
    }

    @Override
    public int leadingAxis() {
        return this.leadingAxis;
    }

    public int candidate() {
        return this.candidate;
    }

    @Override
    public DetectionStage stage() {
        return this.stage;
    }

    public boolean stuck() {
        return this.stuck;
    }

    @Override
    public float progress(long nanos) {
        if (this.stage == DetectionStage.CONFIRM) {
            return 1.0F;
        }
        if (this.stage != DetectionStage.HOLDING || this.holdSince == UNSET) {
            return 0.0F;
        }
        return Math.min(1.0F, (nanos - this.holdSince) / (float) HOLD_NANOS);
    }

    public float min(int axis) {
        return this.valid(axis) ? this.lo[axis] : 0.0F;
    }

    public float max(int axis) {
        return this.valid(axis) ? this.hi[axis] : 0.0F;
    }

    public float travel(int axis) {
        return this.valid(axis) ? Math.min(1.0F, (this.hi[axis] - this.lo[axis]) * 0.5F) : 0.0F;
    }

    public float downEnd() {
        return this.holding() && this.valid(this.candidate) ? this.peak[this.candidate] : Float.NaN;
    }

    public float upEnd() {
        return this.holding() && this.valid(this.candidate) ? this.pivot[this.candidate] : Float.NaN;
    }

    private boolean holding() {
        return this.stage == DetectionStage.HOLDING || this.stage == DetectionStage.CONFIRM;
    }

    public Detection confirm(boolean heldEndIsDown) {
        if (this.stage != DetectionStage.CONFIRM || !this.valid(this.candidate)) {
            return null;
        }
        return new Detection(this.candidate, heldEndIsDown == this.pendingInvert);
    }

    public boolean startedAtEnd(int axis) {
        return this.startEnd(axis) != 0;
    }

    private int startEnd(int axis) {
        if (!this.valid(axis) || axis >= this.startValue.length || Float.isNaN(this.startValue[axis])) {
            return 0;
        }
        float range = this.hi[axis] - this.lo[axis];
        if (range < RANGE_THRESHOLD) {
            return 0;
        }
        float tolerance = range * END_TOLERANCE;
        float start = this.startValue[axis];
        boolean low = start <= this.lo[axis] + tolerance;
        boolean high = start >= this.hi[axis] - tolerance;
        if (low == high) {
            return 0;
        }
        return high ? 1 : -1;
    }

    boolean conflicts(int axis, boolean invert) {
        int start = this.startEnd(axis);
        if (start != 0 && (start > 0) != invert) {
            return true;
        }
        return axis == this.expectedAxis && invert != this.expectedInvert;
    }

    private boolean valid(int axis) {
        return axis >= 0 && axis < this.seen.length && this.seen[axis];
    }

    private boolean skip(int axis) {
        return axis < this.excluded.length && this.excluded[axis];
    }

    private void track(int axis, float value) {
        if (!this.seen[axis]) {
            this.start(axis, value);
            return;
        }
        this.lo[axis] = Math.min(this.lo[axis], value);
        this.hi[axis] = Math.max(this.hi[axis], value);
        int dir = this.direction[axis];
        if (dir == 0) {
            if (value - this.lo[axis] >= REVERSAL) {
                this.direction[axis] = 1;
                this.pivot[axis] = this.lo[axis];
                this.peak[axis] = value;
                this.strokes[axis] = 1;
            } else if (this.hi[axis] - value >= REVERSAL) {
                this.direction[axis] = -1;
                this.pivot[axis] = this.hi[axis];
                this.peak[axis] = value;
                this.strokes[axis] = 1;
            }
            return;
        }
        if (dir * (value - this.peak[axis]) > 0.0F) {
            this.peak[axis] = value;
        } else if (Math.abs(this.peak[axis] - value) >= REVERSAL) {
            this.leadFrom[axis] = this.pivot[axis];
            this.pivot[axis] = this.peak[axis];
            this.peak[axis] = value;
            this.direction[axis] = -dir;
            this.strokes[axis]++;
        }
    }

    @Override
    public Detection update(float[] axes, long nanos) {
        int n = Math.min(axes.length, this.seen.length);
        int best = -1;
        float bestRange = 0.0F;
        float second = 0.0F;
        for (int i = 0; i < n; i++) {
            if (this.skip(i) || Float.isNaN(axes[i])) {
                continue;
            }
            this.track(i, axes[i]);
            float range = this.hi[i] - this.lo[i];
            if (range > bestRange) {
                second = Math.max(second, bestRange);
                bestRange = range;
                best = i;
            } else {
                second = Math.max(second, range);
            }
        }
        this.leadingAxis = best >= 0 && bestRange > MOVED ? best : -1;
        if (this.leadingAxis < 0) {
            return this.idle(DetectionStage.WAITING);
        }
        if (second > MOVED && second > bestRange * DOMINANCE) {
            return this.idle(DetectionStage.AMBIGUOUS);
        }
        if (bestRange < RANGE_THRESHOLD) {
            return this.idle(DetectionStage.TOO_SMALL);
        }
        this.candidate = best;
        float value = axes[best];
        float tolerance = bestRange * END_TOLERANCE;
        boolean fullStroke = this.direction[best] != 0 && Math.abs(this.peak[best] - this.pivot[best]) >= bestRange * FULL_STROKE;
        float leadNeeded = this.strokes[best] == 2 ? LEAD_IN : FULL_STROKE;
        boolean leadIn = !Float.isNaN(this.leadFrom[best]) && Math.abs(this.pivot[best] - this.leadFrom[best]) >= bestRange * leadNeeded;
        boolean atEnd = Math.abs(value - this.peak[best]) <= tolerance;
        if (fullStroke && leadIn && atEnd) {
            this.restSince = UNSET;
            this.stuck = false;
            if (this.holdSince == UNSET) {
                this.holdSince = nanos;
            }
            if (nanos - this.holdSince < HOLD_NANOS) {
                this.stage = DetectionStage.HOLDING;
                return null;
            }
            boolean invert = this.direction[best] > 0;
            if (this.conflicts(best, invert)) {
                this.stage = DetectionStage.CONFIRM;
                this.pendingInvert = invert;
                return null;
            }
            this.stage = DetectionStage.HOLDING;
            return new Detection(best, invert);
        }
        this.stage = DetectionStage.ONE_END;
        this.holdSince = UNSET;
        boolean resting = value <= this.lo[best] + tolerance || value >= this.hi[best] - tolerance;
        if (!resting) {
            this.restSince = UNSET;
            this.stuck = false;
        } else if (this.restSince == UNSET) {
            this.restSince = nanos;
        } else {
            this.stuck = nanos - this.restSince >= STUCK_NANOS;
        }
        return null;
    }

    private Detection idle(DetectionStage next) {
        this.stage = next;
        this.candidate = -1;
        this.holdSince = UNSET;
        this.restSince = UNSET;
        this.stuck = false;
        return null;
    }

    @Override
    public String toString() {
        return "ThrottleDetector[stage=" + this.stage + " candidate=" + this.candidate + "]";
    }
}
