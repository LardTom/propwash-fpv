package de.lardtom.propwash.input;

public final class CalibrationRecorder {
    public static final float MIN_TRAVEL = 0.3F;
    public static final float CENTER_MARGIN = 0.1F;
    public static final float THROTTLE_CENTER_TOLERANCE = 0.15F;

    private final float[] min;
    private final float[] max;
    private final float[] center;
    private boolean centerCaptured;

    public CalibrationRecorder(int axisCount) {
        int n = Math.max(0, axisCount);
        this.min = new float[n];
        this.max = new float[n];
        this.center = new float[n];
        for (int i = 0; i < n; i++) {
            this.min[i] = Float.POSITIVE_INFINITY;
            this.max[i] = Float.NEGATIVE_INFINITY;
        }
    }

    public int axisCount() {
        return this.min.length;
    }

    public void captureCenter(float[] axes) {
        int n = Math.min(axes.length, this.center.length);
        for (int i = 0; i < n; i++) {
            this.center[i] = axes[i];
        }
        this.centerCaptured = true;
        this.observe(axes);
    }

    public boolean centerCaptured() {
        return this.centerCaptured;
    }

    public void observe(float[] axes) {
        int n = Math.min(axes.length, this.min.length);
        for (int i = 0; i < n; i++) {
            float v = axes[i];
            if (Float.isNaN(v)) {
                continue;
            }
            this.min[i] = Math.min(this.min[i], v);
            this.max[i] = Math.max(this.max[i], v);
        }
    }

    public float min(int axis) {
        return axis >= 0 && axis < this.min.length && Float.isFinite(this.min[axis]) ? this.min[axis] : 0.0F;
    }

    public float max(int axis) {
        return axis >= 0 && axis < this.max.length && Float.isFinite(this.max[axis]) ? this.max[axis] : 0.0F;
    }

    public float center(int axis) {
        return axis >= 0 && axis < this.center.length ? this.center[axis] : 0.0F;
    }

    public float travel(int axis) {
        if (axis < 0 || axis >= this.min.length || !Float.isFinite(this.min[axis]) || !Float.isFinite(this.max[axis])) {
            return 0.0F;
        }
        return this.max[axis] - this.min[axis];
    }

    public boolean moved(int axis) {
        return this.travel(axis) >= MIN_TRAVEL;
    }

    public boolean apply(ChannelMapping mapping, boolean centeredChannel) {
        int axis = mapping.axis;
        if (!this.moved(axis)) {
            return false;
        }
        float lo = this.min[axis];
        float hi = this.max[axis];
        float span = hi - lo;
        float half = (lo + hi) * 0.5F;
        float mid = this.centerCaptured ? this.center[axis] : half;
        if (centeredChannel ? mid < lo + span * CENTER_MARGIN || mid > hi - span * CENTER_MARGIN
                : !this.centerCaptured || Math.abs(mid - half) > span * THROTTLE_CENTER_TOLERANCE) {
            mid = half;
        }
        mapping.min = round(lo);
        mapping.max = round(hi);
        mapping.center = round(mid);
        mapping.sanitize();
        return true;
    }

    private static float round(float v) {
        return Math.round(v * 1000.0F) / 1000.0F;
    }
}
