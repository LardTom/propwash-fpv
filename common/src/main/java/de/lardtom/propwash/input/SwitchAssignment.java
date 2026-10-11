package de.lardtom.propwash.input;

public final class SwitchAssignment {
    public static final int MAX_INDEX = 127;
    public static final float RANGE_HALF_WIDTH = 0.2F;
    public static final float EDGE_SNAP = 0.9F;

    public SwitchSource source = SwitchSource.NONE;
    public int index = -1;
    public float low = 0.5F;
    public float high = 1.0F;
    public boolean invert = false;
    public boolean toggle = false;
    public boolean startOn = false;

    public SwitchAssignment() {
    }

    public static SwitchAssignment none() {
        return new SwitchAssignment();
    }

    public static SwitchAssignment button(int index, boolean invert) {
        SwitchAssignment b = new SwitchAssignment();
        b.source = SwitchSource.BUTTON;
        b.index = index;
        b.invert = invert;
        return b;
    }

    public static SwitchAssignment toggleButton(int index) {
        SwitchAssignment b = button(index, false);
        b.toggle = true;
        return b;
    }

    public SwitchAssignment startingOn() {
        this.startOn = this.toggle;
        return this;
    }

    public static SwitchAssignment axisRange(int index, float low, float high) {
        SwitchAssignment b = new SwitchAssignment();
        b.source = SwitchSource.AXIS;
        b.index = index;
        b.low = Math.min(low, high);
        b.high = Math.max(low, high);
        return b;
    }

    public static SwitchAssignment axisMicros(int index, int lowMicros, int highMicros) {
        return axisRange(index, ChannelMath.fromRangeMicros(ChannelMath.snapRangeMicros(lowMicros)),
                ChannelMath.fromRangeMicros(ChannelMath.snapRangeMicros(highMicros)));
    }

    public static SwitchAssignment axisAround(int index, float value) {
        float low = value - RANGE_HALF_WIDTH;
        float high = value + RANGE_HALF_WIDTH;
        if (high >= EDGE_SNAP) {
            high = ChannelMath.RANGE_LIMIT;
        }
        if (low <= -EDGE_SNAP) {
            low = -ChannelMath.RANGE_LIMIT;
        }
        return axisRange(index, ChannelMath.snapRange(low), ChannelMath.snapRange(high));
    }

    public int lowMicros() {
        return ChannelMath.rangeMicros(this.low);
    }

    public int highMicros() {
        return ChannelMath.rangeMicros(this.high);
    }

    public void setLowMicros(int micros) {
        this.low = ChannelMath.fromRangeMicros(Math.min(ChannelMath.snapRangeMicros(micros), this.highMicros()));
    }

    public void setHighMicros(int micros) {
        this.high = ChannelMath.fromRangeMicros(Math.max(ChannelMath.snapRangeMicros(micros), this.lowMicros()));
    }

    public boolean bound() {
        return this.source != SwitchSource.NONE && this.index >= 0;
    }

    public boolean mirrored() {
        return this.invert && !this.toggle && this.source == SwitchSource.AXIS;
    }

    public boolean flipsOutput() {
        return this.invert && !this.mirrored();
    }

    public float activeLow() {
        return this.mirrored() ? -this.high : this.low;
    }

    public float activeHigh() {
        return this.mirrored() ? -this.low : this.high;
    }

    public int activeLowMicros() {
        return ChannelMath.rangeMicros(this.activeLow());
    }

    public int activeHighMicros() {
        return ChannelMath.rangeMicros(this.activeHigh());
    }

    public void setActiveLowMicros(int micros) {
        if (this.mirrored()) {
            this.setHighMicros(mirrorMicros(micros));
        } else {
            this.setLowMicros(micros);
        }
    }

    public void setActiveHighMicros(int micros) {
        if (this.mirrored()) {
            this.setLowMicros(mirrorMicros(micros));
        } else {
            this.setHighMicros(micros);
        }
    }

    public void setActiveLow(float value) {
        if (this.mirrored()) {
            this.high = Math.max(-value, this.low);
        } else {
            this.low = Math.min(value, this.high);
        }
    }

    public void setActiveHigh(float value) {
        if (this.mirrored()) {
            this.low = Math.min(-value, this.high);
        } else {
            this.high = Math.max(value, this.low);
        }
    }

    public static int mirrorMicros(int micros) {
        return 2 * ChannelMath.MID_US - micros;
    }

    public boolean engaged(float[] axes, boolean[] buttons) {
        if (!this.readable(axes, buttons)) {
            return false;
        }
        return switch (this.source) {
            case BUTTON -> buttons[this.index];
            case AXIS -> {
                float v = axes[this.index];
                yield !Float.isNaN(v) && v >= this.activeLow() && v <= this.activeHigh();
            }
            default -> false;
        };
    }

    public boolean rawActive(float[] axes, boolean[] buttons) {
        return this.readable(axes, buttons) && this.engaged(axes, buttons) != this.flipsOutput();
    }

    public void migrateLegacyInvert() {
        if (!this.invert || this.source == SwitchSource.NONE) {
            return;
        }
        if (this.toggle) {
            this.invert = false;
            return;
        }
        if (this.source != SwitchSource.AXIS) {
            return;
        }
        int lowUs = this.lowMicros();
        int highUs = this.highMicros();
        boolean top = highUs >= ChannelMath.RANGE_MAX_US;
        boolean bottom = lowUs <= ChannelMath.RANGE_MIN_US;
        if (top == bottom) {
            this.invert = false;
            return;
        }
        int activeLow = top ? ChannelMath.RANGE_MIN_US : highUs + ChannelMath.RANGE_STEP_US;
        int activeHigh = top ? lowUs - ChannelMath.RANGE_STEP_US : ChannelMath.RANGE_MAX_US;
        this.low = ChannelMath.fromRangeMicros(mirrorMicros(activeHigh));
        this.high = ChannelMath.fromRangeMicros(mirrorMicros(activeLow));
    }

    public boolean readable(float[] axes, boolean[] buttons) {
        return switch (this.source) {
            case BUTTON -> this.index >= 0 && this.index < buttons.length;
            case AXIS -> this.index >= 0 && this.index < axes.length;
            default -> false;
        };
    }

    public SwitchAssignment copy() {
        SwitchAssignment copy = new SwitchAssignment();
        copy.source = this.source;
        copy.index = this.index;
        copy.low = this.low;
        copy.high = this.high;
        copy.invert = this.invert;
        copy.toggle = this.toggle;
        copy.startOn = this.startOn;
        return copy;
    }

    public void sanitize() {
        if (this.source == null) {
            this.source = SwitchSource.NONE;
        }
        if (this.index < -1 || this.index > MAX_INDEX) {
            this.index = -1;
        }
        if (this.source != SwitchSource.NONE && this.index < 0) {
            this.source = SwitchSource.NONE;
        }
        if (!Float.isFinite(this.low) || !Float.isFinite(this.high)) {
            this.low = 0.5F;
            this.high = 1.0F;
        }
        this.low = ChannelMapping.clamp(this.low, -ChannelMath.RANGE_LIMIT, ChannelMath.RANGE_LIMIT);
        this.high = ChannelMapping.clamp(this.high, -ChannelMath.RANGE_LIMIT, ChannelMath.RANGE_LIMIT);
        if (this.low > this.high) {
            float t = this.low;
            this.low = this.high;
            this.high = t;
        }
        if (this.source == SwitchSource.NONE) {
            this.toggle = false;
            this.invert = false;
        }
        if (!this.toggle) {
            this.startOn = false;
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SwitchAssignment b && b.source == this.source && b.index == this.index && b.invert == this.invert
                && b.toggle == this.toggle && b.startOn == this.startOn && Float.compare(b.low, this.low) == 0 && Float.compare(b.high, this.high) == 0;
    }

    @Override
    public int hashCode() {
        return (this.source.hashCode() * 31 + this.index) * 31 + (this.invert ? 1 : 0) + (this.toggle ? 2 : 0) + (this.startOn ? 4 : 0);
    }

    @Override
    public String toString() {
        return "SwitchAssignment[" + this.source + " " + this.index + " " + this.low + ".." + this.high + (this.invert ? " inv" : "") + (this.toggle ? " toggle" : "") + (this.startOn ? " on" : "") + "]";
    }
}
