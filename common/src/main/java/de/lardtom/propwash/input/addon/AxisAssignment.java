package de.lardtom.propwash.input.addon;

import de.lardtom.propwash.input.SwitchAssignment;

public final class AxisAssignment {
    public static final float DEFAULT_DEADBAND = 0.05F;
    public static final float MAX_DEADBAND = 0.5F;

    public enum Source {
        KEYS,
        AXIS
    }

    public Source source = Source.KEYS;
    public int index = -1;
    public boolean invert = false;
    public float deadband = DEFAULT_DEADBAND;

    public AxisAssignment() {
    }

    public static AxisAssignment keys() {
        return new AxisAssignment();
    }

    public static AxisAssignment axis(int index) {
        AxisAssignment assignment = new AxisAssignment();
        assignment.source = Source.AXIS;
        assignment.index = index;
        return assignment;
    }

    public boolean onAxis() {
        return this.source == Source.AXIS && this.index >= 0;
    }

    public boolean readable(float[] axes) {
        return this.onAxis() && axes != null && this.index < axes.length && !Float.isNaN(axes[this.index]);
    }

    public AxisAssignment copy() {
        AxisAssignment copy = new AxisAssignment();
        copy.source = this.source;
        copy.index = this.index;
        copy.invert = this.invert;
        copy.deadband = this.deadband;
        return copy;
    }

    public void sanitize() {
        if (this.source == null) {
            this.source = Source.KEYS;
        }
        if (this.index < -1 || this.index > SwitchAssignment.MAX_INDEX) {
            this.index = -1;
        }
        if (this.source == Source.AXIS && this.index < 0) {
            this.source = Source.KEYS;
        }
        if (this.source == Source.KEYS) {
            this.index = -1;
        }
        if (!Float.isFinite(this.deadband)) {
            this.deadband = DEFAULT_DEADBAND;
        }
        this.deadband = Math.max(0.0F, Math.min(MAX_DEADBAND, this.deadband));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AxisAssignment a && a.source == this.source && a.index == this.index && a.invert == this.invert
                && Float.compare(a.deadband, this.deadband) == 0;
    }

    @Override
    public int hashCode() {
        return ((this.source.hashCode() * 31 + this.index) * 31 + (this.invert ? 1 : 0)) * 31 + Float.hashCode(this.deadband);
    }

    @Override
    public String toString() {
        return "AxisAssignment[" + this.source + " " + this.index + (this.invert ? " inv" : "") + " db " + this.deadband + "]";
    }
}
