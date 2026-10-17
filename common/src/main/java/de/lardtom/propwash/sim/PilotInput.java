package de.lardtom.propwash.sim;

public final class PilotInput {
    public double roll;
    public double pitch;
    public double yaw;
    public double throttle;
    public StabMode mode = StabMode.ACRO;
    public boolean armSwitch;
    public boolean linkOk = true;
    public boolean altHold;
    public boolean posHold;
    public boolean mode3d;
    public boolean turtle;
    public int actions;

    public PilotInput() {
    }

    public void copyTo(PilotInput dst) {
        dst.roll = this.roll;
        dst.pitch = this.pitch;
        dst.yaw = this.yaw;
        dst.throttle = this.throttle;
        dst.mode = this.mode;
        dst.armSwitch = this.armSwitch;
        dst.linkOk = this.linkOk;
        dst.altHold = this.altHold;
        dst.posHold = this.posHold;
        dst.mode3d = this.mode3d;
        dst.turtle = this.turtle;
        dst.actions = this.actions;
    }
}
