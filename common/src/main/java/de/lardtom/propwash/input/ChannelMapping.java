package de.lardtom.propwash.input;

public final class ChannelMapping {
    public static final int MAX_AXIS = 63;
    public static final float MIN_SPAN = 0.05F;

    public int axis = -1;
    public boolean invert = false;
    public float min = -1.0F;
    public float center = 0.0F;
    public float max = 1.0F;

    public ChannelMapping() {
    }

    public ChannelMapping(int axis, boolean invert) {
        this.axis = axis;
        this.invert = invert;
    }

    public static ChannelMapping unassigned() {
        return new ChannelMapping();
    }

    public boolean assigned() {
        return this.axis >= 0;
    }

    public boolean readable(float[] axes) {
        return this.axis >= 0 && this.axis < axes.length;
    }

    public float raw(float[] axes) {
        return this.readable(axes) ? axes[this.axis] : 0.0F;
    }

    public float centered(float raw) {
        float v;
        if (raw >= this.center) {
            float span = this.max - this.center;
            v = span > MIN_SPAN ? (raw - this.center) / span : 0.0F;
        } else {
            float span = this.center - this.min;
            v = span > MIN_SPAN ? (raw - this.center) / span : 0.0F;
        }
        v = clamp(v, -1.0F, 1.0F);
        return this.invert ? -v : v;
    }

    public float linear(float raw) {
        float span = this.max - this.min;
        float t = span > MIN_SPAN ? (raw - this.min) / span : 0.0F;
        t = clamp(t, 0.0F, 1.0F);
        return this.invert ? 1.0F - t : t;
    }

    public void resetCalibration() {
        this.min = -1.0F;
        this.center = 0.0F;
        this.max = 1.0F;
    }

    public boolean calibrated() {
        return this.min != -1.0F || this.center != 0.0F || this.max != 1.0F;
    }

    public ChannelMapping copy() {
        ChannelMapping copy = new ChannelMapping(this.axis, this.invert);
        copy.min = this.min;
        copy.center = this.center;
        copy.max = this.max;
        return copy;
    }

    public void sanitize() {
        if (this.axis < -1 || this.axis > MAX_AXIS) {
            this.axis = -1;
        }
        if (!Float.isFinite(this.min) || !Float.isFinite(this.center) || !Float.isFinite(this.max)
                || this.max - this.min < MIN_SPAN * 2.0F || this.min < -1.5F || this.max > 1.5F) {
            this.resetCalibration();
        }
        if (this.center <= this.min || this.center >= this.max) {
            this.center = (this.min + this.max) * 0.5F;
        }
    }

    static float clamp(float v, float lo, float hi) {
        if (Float.isNaN(v)) {
            return lo < 0.0F ? 0.0F : lo;
        }
        return v < lo ? lo : Math.min(v, hi);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ChannelMapping m && m.axis == this.axis && m.invert == this.invert
                && Float.compare(m.min, this.min) == 0 && Float.compare(m.center, this.center) == 0 && Float.compare(m.max, this.max) == 0;
    }

    @Override
    public int hashCode() {
        return ((this.axis * 31 + (this.invert ? 1 : 0)) * 31 + Float.hashCode(this.min)) * 31 + Float.hashCode(this.max);
    }
}
