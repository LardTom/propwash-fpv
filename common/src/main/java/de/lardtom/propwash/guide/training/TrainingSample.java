package de.lardtom.propwash.guide.training;

import de.lardtom.propwash.trick.TrickSample;

public final class TrainingSample {
    public double time;
    public double x;
    public double y;
    public double z;
    public double footY;
    public double vx;
    public double vy;
    public double vz;
    public double qx;
    public double qy;
    public double qz;
    public double qw = 1.0;
    public boolean armed;
    public boolean grounded;
    public double heightAboveGround = Double.POSITIVE_INFINITY;
    public int crashCount;
    public int impactCount;
    public boolean assisted;
    public double throttle;
    public double radius = 0.15;

    public TrainingSample() {
    }

    public double forwardX() {
        return 2.0 * (this.qx * this.qz + this.qw * this.qy);
    }

    public double forwardY() {
        return 2.0 * (this.qy * this.qz - this.qw * this.qx);
    }

    public double forwardZ() {
        return 1.0 - 2.0 * (this.qx * this.qx + this.qy * this.qy);
    }

    public double upY() {
        return 1.0 - 2.0 * (this.qx * this.qx + this.qz * this.qz);
    }

    public float yaw() {
        double fx = this.forwardX();
        double fz = this.forwardZ();
        if (Math.hypot(fx, fz) < 1.0E-4) {
            fx = -2.0 * (this.qx * this.qy - this.qw * this.qz);
            fz = -2.0 * (this.qy * this.qz + this.qw * this.qx);
        }
        return TrainingFrame.yawOf(fx, fz);
    }

    public double speed() {
        return Math.sqrt(this.vx * this.vx + this.vy * this.vy + this.vz * this.vz);
    }

    public boolean nearGround() {
        return this.grounded || this.heightAboveGround < 0.35;
    }

    public void setHeading(double yawDegrees) {
        double r = -Math.toRadians(yawDegrees) * 0.5;
        this.qx = 0.0;
        this.qy = Math.sin(r);
        this.qz = 0.0;
        this.qw = Math.cos(r);
    }

    public void fill(TrickSample out) {
        out.time = this.time;
        out.x = this.x;
        out.y = this.y;
        out.z = this.z;
        out.vx = this.vx;
        out.vy = this.vy;
        out.vz = this.vz;
        out.qx = this.qx;
        out.qy = this.qy;
        out.qz = this.qz;
        out.qw = this.qw;
        out.armed = this.armed;
        out.crashCount = this.crashCount;
        out.impactCount = this.impactCount;
        out.grounded = this.grounded;
        out.throttle = this.throttle;
        out.assisted = false;
        out.clearNearby();
    }

    public void copyTo(TrainingSample dst) {
        dst.time = this.time;
        dst.x = this.x;
        dst.y = this.y;
        dst.z = this.z;
        dst.footY = this.footY;
        dst.vx = this.vx;
        dst.vy = this.vy;
        dst.vz = this.vz;
        dst.qx = this.qx;
        dst.qy = this.qy;
        dst.qz = this.qz;
        dst.qw = this.qw;
        dst.armed = this.armed;
        dst.grounded = this.grounded;
        dst.heightAboveGround = this.heightAboveGround;
        dst.crashCount = this.crashCount;
        dst.impactCount = this.impactCount;
        dst.assisted = this.assisted;
        dst.throttle = this.throttle;
        dst.radius = this.radius;
    }
}
