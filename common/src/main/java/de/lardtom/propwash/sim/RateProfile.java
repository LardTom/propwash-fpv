package de.lardtom.propwash.sim;

public final class RateProfile {
    public RatesType type = RatesType.ACTUAL;
    public RateCurve roll = new RateCurve();
    public RateCurve pitch = new RateCurve();
    public RateCurve yaw = new RateCurve();
    public double rcDeadband = 0.0;
    public double yawDeadband = 0.0;

    public RateProfile() {
    }

    public RateCurve axis(Axis axis) {
        return switch (axis) {
            case ROLL -> this.roll;
            case PITCH -> this.pitch;
            case YAW -> this.yaw;
        };
    }

    public RateProfile copy() {
        RateProfile c = new RateProfile();
        c.type = this.type;
        c.roll = this.roll == null ? null : this.roll.copy();
        c.pitch = this.pitch == null ? null : this.pitch.copy();
        c.yaw = this.yaw == null ? null : this.yaw.copy();
        c.rcDeadband = this.rcDeadband;
        c.yawDeadband = this.yawDeadband;
        return c;
    }

    public void sanitize() {
        if (this.type == null) {
            this.type = RatesType.ACTUAL;
        }
        if (this.roll == null) {
            this.roll = new RateCurve();
        }
        if (this.pitch == null) {
            this.pitch = new RateCurve();
        }
        if (this.yaw == null) {
            this.yaw = new RateCurve();
        }
        this.roll.sanitize();
        this.pitch.sanitize();
        this.yaw.sanitize();
        this.rcDeadband = SimMath.sanitize(this.rcDeadband, 0.0, 0.2, 0.0);
        this.yawDeadband = SimMath.sanitize(this.yawDeadband, 0.0, 0.2, 0.0);
    }
}
