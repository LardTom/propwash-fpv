package de.lardtom.propwash.trick;

import java.util.Arrays;

public final class TrickSample {
    public static final int PROBES = 4;

    public double time;
    public double x;
    public double y;
    public double z;
    public double vx;
    public double vy;
    public double vz;
    public double qx;
    public double qy;
    public double qz;
    public double qw = 1.0;
    public boolean armed;
    public boolean assisted;
    public int crashCount;
    public int impactCount;
    public boolean grounded;
    public double throttle;
    public boolean mode3d;
    public boolean clearanceKnown;
    public final double[] clearance = new double[PROBES];
    public final double[] clearanceDirY = new double[PROBES];

    public TrickSample() {
        this.clearNearby();
    }

    public void clearNearby() {
        this.clearanceKnown = false;
        Arrays.fill(this.clearance, Double.POSITIVE_INFINITY);
        Arrays.fill(this.clearanceDirY, 0.0);
    }

    public double speed() {
        return Math.sqrt(this.vx * this.vx + this.vy * this.vy + this.vz * this.vz);
    }

    public double horizontalSpeed() {
        return Math.sqrt(this.vx * this.vx + this.vz * this.vz);
    }

    public void copyTo(TrickSample dst) {
        dst.time = this.time;
        dst.x = this.x;
        dst.y = this.y;
        dst.z = this.z;
        dst.vx = this.vx;
        dst.vy = this.vy;
        dst.vz = this.vz;
        dst.qx = this.qx;
        dst.qy = this.qy;
        dst.qz = this.qz;
        dst.qw = this.qw;
        dst.armed = this.armed;
        dst.assisted = this.assisted;
        dst.crashCount = this.crashCount;
        dst.impactCount = this.impactCount;
        dst.grounded = this.grounded;
        dst.throttle = this.throttle;
        dst.mode3d = this.mode3d;
        dst.clearanceKnown = this.clearanceKnown;
        System.arraycopy(this.clearance, 0, dst.clearance, 0, PROBES);
        System.arraycopy(this.clearanceDirY, 0, dst.clearanceDirY, 0, PROBES);
    }
}
