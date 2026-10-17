package de.lardtom.propwash.api.flight;

import java.util.Objects;

public final class GimbalModel {
    private Mode mode = Mode.FOLLOW;
    private double pitchMinDeg = -90.0;
    private double pitchMaxDeg = 30.0;
    private double relativePitchMinDeg = -135.0;
    private double relativePitchMaxDeg = 60.0;
    private double relativeRollLimitDeg = 45.0;
    private double relativeYawLimitDeg = 100.0;
    private double followTimeSeconds = 0.6;
    private double maxYawRateDps = 120.0;
    private double maxPitchRateDps = 90.0;
    private double commandedPitchDeg;
    private double lockedHeadingDeg;
    private boolean initialized;
    private double headingDeg;
    private double pitchDeg;
    private double rollDeg;
    private double qx;
    private double qy;
    private double qz;
    private double qw = 1.0;
    private boolean limited;

    public GimbalModel mode(Mode value) {
        Mode next = Objects.requireNonNull(value, "mode");
        if (next == Mode.LOCK && this.mode != Mode.LOCK) {
            this.lockedHeadingDeg = this.headingDeg;
        }
        this.mode = next;
        return this;
    }

    public Mode mode() {
        return this.mode;
    }

    public GimbalModel pitchRange(double minDeg, double maxDeg) {
        if (!(minDeg < maxDeg) || minDeg < -90.0 || maxDeg > 90.0) {
            throw new IllegalArgumentException("Nickbereich ungültig: " + minDeg + ".." + maxDeg);
        }
        this.pitchMinDeg = minDeg;
        this.pitchMaxDeg = maxDeg;
        return this;
    }

    public GimbalModel mechanicalLimits(double relativePitchMinDeg, double relativePitchMaxDeg, double relativeRollLimitDeg,
                                        double relativeYawLimitDeg) {
        if (!(relativePitchMinDeg < relativePitchMaxDeg) || !(relativeRollLimitDeg >= 0.0) || !(relativeYawLimitDeg >= 0.0)) {
            throw new IllegalArgumentException("Mechanische Grenzen ungültig");
        }
        this.relativePitchMinDeg = relativePitchMinDeg;
        this.relativePitchMaxDeg = relativePitchMaxDeg;
        this.relativeRollLimitDeg = relativeRollLimitDeg;
        this.relativeYawLimitDeg = relativeYawLimitDeg;
        return this;
    }

    public GimbalModel smoothing(double followTimeSeconds, double maxYawRateDps, double maxPitchRateDps) {
        if (!(followTimeSeconds >= 0.0) || !(maxYawRateDps > 0.0) || !(maxPitchRateDps > 0.0)) {
            throw new IllegalArgumentException("Glättung ungültig");
        }
        this.followTimeSeconds = followTimeSeconds;
        this.maxYawRateDps = maxYawRateDps;
        this.maxPitchRateDps = maxPitchRateDps;
        return this;
    }

    public GimbalModel pitch(double degrees) {
        if (Double.isFinite(degrees)) {
            this.commandedPitchDeg = clamp(degrees, this.pitchMinDeg, this.pitchMaxDeg);
        }
        return this;
    }

    public double commandedPitch() {
        return this.commandedPitchDeg;
    }

    public GimbalModel lockHeading(double compassDegrees) {
        if (Double.isFinite(compassDegrees)) {
            this.lockedHeadingDeg = wrap360(compassDegrees);
        }
        return this;
    }

    public void reset() {
        this.initialized = false;
    }

    public void update(double dt, double frameQx, double frameQy, double frameQz, double frameQw) {
        double n = Math.sqrt(frameQx * frameQx + frameQy * frameQy + frameQz * frameQz + frameQw * frameQw);
        if (!(n > 1.0E-9) || !Double.isFinite(n) || !Double.isFinite(dt)) {
            return;
        }
        double x = frameQx / n;
        double y = frameQy / n;
        double z = frameQz / n;
        double w = frameQw / n;
        double[] forward = rotate(x, y, z, w, 0.0, 0.0, -1.0);
        double[] right = rotate(x, y, z, w, 1.0, 0.0, 0.0);
        double frameHeading = wrap360(Math.toDegrees(Math.atan2(forward[0], -forward[2])));
        double framePitch = Math.toDegrees(Math.asin(clamp(forward[1], -1.0, 1.0)));
        double frameRoll = Math.toDegrees(Math.asin(clamp(-right[1], -1.0, 1.0)));
        double step = clamp(dt, 0.0, 0.25);
        if (!this.initialized) {
            this.initialized = true;
            this.headingDeg = frameHeading;
            this.pitchDeg = clamp(this.commandedPitchDeg, framePitch + this.relativePitchMinDeg, framePitch + this.relativePitchMaxDeg);
            this.rollDeg = 0.0;
            this.lockedHeadingDeg = frameHeading;
        }
        double targetHeading = switch (this.mode) {
            case FOLLOW -> frameHeading;
            case LOCK -> this.lockedHeadingDeg;
            case FPV -> frameHeading;
        };
        double headingError = wrap180(targetHeading - this.headingDeg);
        double headingStep;
        if (this.mode == Mode.FPV || this.followTimeSeconds <= 0.0) {
            headingStep = headingError;
        } else {
            headingStep = headingError * (1.0 - Math.exp(-step / this.followTimeSeconds));
            double maxStep = this.maxYawRateDps * step;
            headingStep = clamp(headingStep, -maxStep, maxStep);
        }
        double heading = wrap360(this.headingDeg + headingStep);
        double pitchError = this.commandedPitchDeg - this.pitchDeg;
        double maxPitchStep = this.maxPitchRateDps * step;
        double pitch = this.pitchDeg + clamp(pitchError, -maxPitchStep, maxPitchStep);
        double roll = this.mode == Mode.FPV ? frameRoll : 0.0;
        boolean hitLimit = false;
        double relativeYaw = wrap180(heading - frameHeading);
        if (Math.abs(relativeYaw) > this.relativeYawLimitDeg) {
            heading = wrap360(frameHeading + Math.signum(relativeYaw) * this.relativeYawLimitDeg);
            hitLimit = true;
        }
        double relativePitch = pitch - framePitch;
        if (relativePitch < this.relativePitchMinDeg || relativePitch > this.relativePitchMaxDeg) {
            pitch = framePitch + clamp(relativePitch, this.relativePitchMinDeg, this.relativePitchMaxDeg);
            hitLimit = true;
        }
        double relativeRoll = roll - frameRoll;
        if (Math.abs(relativeRoll) > this.relativeRollLimitDeg) {
            roll = frameRoll + Math.signum(relativeRoll) * this.relativeRollLimitDeg;
            hitLimit = true;
        }
        this.headingDeg = heading;
        this.pitchDeg = clamp(pitch, -90.0, 90.0);
        this.rollDeg = clamp(roll, -180.0, 180.0);
        this.limited = hitLimit;
        this.compose();
    }

    private void compose() {
        double a = -Math.toRadians(this.headingDeg);
        double b = Math.toRadians(this.pitchDeg);
        double c = -Math.toRadians(this.rollDeg);
        double ya = Math.sin(a / 2.0);
        double wa = Math.cos(a / 2.0);
        double xb = Math.sin(b / 2.0);
        double wb = Math.cos(b / 2.0);
        double zc = Math.sin(c / 2.0);
        double wc = Math.cos(c / 2.0);
        double x1 = wa * xb;
        double y1 = ya * wb;
        double z1 = -ya * xb;
        double w1 = wa * wb;
        this.qx = x1 * wc + y1 * zc;
        this.qy = y1 * wc - x1 * zc;
        this.qz = z1 * wc + w1 * zc;
        this.qw = w1 * wc - z1 * zc;
    }

    private static double[] rotate(double x, double y, double z, double w, double vx, double vy, double vz) {
        double tx = 2.0 * (y * vz - z * vy);
        double ty = 2.0 * (z * vx - x * vz);
        double tz = 2.0 * (x * vy - y * vx);
        return new double[]{vx + w * tx + (y * tz - z * ty), vy + w * ty + (z * tx - x * tz), vz + w * tz + (x * ty - y * tx)};
    }

    public double headingDeg() {
        return this.headingDeg;
    }

    public double pitchDeg() {
        return this.pitchDeg;
    }

    public double rollDeg() {
        return this.rollDeg;
    }

    public boolean atLimit() {
        return this.limited;
    }

    public double qx() {
        return this.qx;
    }

    public double qy() {
        return this.qy;
    }

    public double qz() {
        return this.qz;
    }

    public double qw() {
        return this.qw;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double wrap180(double degrees) {
        double d = degrees % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        } else if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }

    private static double wrap360(double degrees) {
        double d = degrees % 360.0;
        return d < 0.0 ? d + 360.0 : d;
    }

    public enum Mode {
        FOLLOW,
        LOCK,
        FPV
    }
}
