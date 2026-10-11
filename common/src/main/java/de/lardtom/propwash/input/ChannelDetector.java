package de.lardtom.propwash.input;

import java.util.Arrays;

public final class ChannelDetector implements StickDetector {
    public static final float CENTERED_THRESHOLD = 0.5F;
    public static final float LEAD_THRESHOLD = 0.1F;
    public static final float DOMINANCE = 0.6F;
    public static final float REST_ZONE = 0.5F;
    public static final long HOLD_NANOS = 120_000_000L;
    public static final long SETTLE_NANOS = 150_000_000L;
    private static final long UNSET = Long.MIN_VALUE;

    private final float[] rest;
    private final long[] settleSince;
    private final boolean[] excluded;
    private int candidate = -1;
    private boolean candidatePositive;
    private long candidateSince;
    private int leadingAxis = -1;
    private DetectionStage stage = DetectionStage.WAITING;

    private ChannelDetector(float[] baseline, boolean[] excluded) {
        this.rest = new float[baseline.length];
        this.settleSince = new long[baseline.length];
        for (int i = 0; i < baseline.length; i++) {
            this.rest[i] = atRest(baseline[i]) ? baseline[i] : Float.NaN;
            this.settleSince[i] = UNSET;
        }
        this.excluded = excluded == null ? new boolean[0] : excluded.clone();
    }

    public static ChannelDetector centered(float[] baseline, boolean[] excluded) {
        return new ChannelDetector(baseline, excluded);
    }

    private static boolean atRest(float value) {
        return !Float.isNaN(value) && Math.abs(value) <= REST_ZONE;
    }

    public boolean restKnown(int axis) {
        return axis >= 0 && axis < this.rest.length && !Float.isNaN(this.rest[axis]);
    }

    @Override
    public int leadingAxis() {
        return this.leadingAxis;
    }

    @Override
    public DetectionStage stage() {
        return this.stage;
    }

    @Override
    public float progress(long nanos) {
        if (this.candidate < 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (nanos - this.candidateSince) / (float) HOLD_NANOS);
    }

    private boolean skip(int axis) {
        return axis < this.excluded.length && this.excluded[axis];
    }

    private boolean settled(int axis, float value, long nanos) {
        if (!Float.isNaN(this.rest[axis])) {
            return true;
        }
        if (!atRest(value)) {
            this.settleSince[axis] = UNSET;
            return false;
        }
        if (this.settleSince[axis] == UNSET) {
            this.settleSince[axis] = nanos;
            return false;
        }
        if (nanos - this.settleSince[axis] < SETTLE_NANOS) {
            return false;
        }
        this.rest[axis] = value;
        return true;
    }

    @Override
    public Detection update(float[] axes, long nanos) {
        int n = Math.min(axes.length, this.rest.length);
        int best = -1;
        float bestDelta = 0.0F;
        float second = 0.0F;
        for (int i = 0; i < n; i++) {
            if (this.skip(i) || Float.isNaN(axes[i]) || !this.settled(i, axes[i], nanos)) {
                continue;
            }
            float delta = axes[i] - this.rest[i];
            float magnitude = Math.abs(delta);
            if (best < 0 || magnitude > Math.abs(bestDelta)) {
                if (best >= 0) {
                    second = Math.max(second, Math.abs(bestDelta));
                }
                best = i;
                bestDelta = delta;
            } else {
                second = Math.max(second, magnitude);
            }
        }
        float bestMagnitude = Math.abs(bestDelta);
        this.leadingAxis = best >= 0 && bestMagnitude > LEAD_THRESHOLD ? best : -1;
        if (best < 0 || bestMagnitude < CENTERED_THRESHOLD) {
            this.stage = this.leadingAxis >= 0 ? DetectionStage.TOO_SMALL : DetectionStage.WAITING;
            this.candidate = -1;
            return null;
        }
        if (second > bestMagnitude * DOMINANCE) {
            this.stage = DetectionStage.AMBIGUOUS;
            this.candidate = -1;
            return null;
        }
        this.stage = DetectionStage.HOLDING;
        return this.hold(best, bestDelta > 0.0F, nanos);
    }

    private Detection hold(int axis, boolean positive, long nanos) {
        if (axis != this.candidate || positive != this.candidatePositive) {
            this.candidate = axis;
            this.candidatePositive = positive;
            this.candidateSince = nanos;
            return null;
        }
        if (nanos - this.candidateSince >= HOLD_NANOS) {
            return new Detection(axis, !positive);
        }
        return null;
    }

    @Override
    public String toString() {
        return "ChannelDetector[rest=" + Arrays.toString(this.rest) + "]";
    }
}
