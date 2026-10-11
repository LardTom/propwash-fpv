package de.lardtom.propwash.sim;

final class AttitudeController {
    private final Pt1 ffPitch = new Pt1();
    private final Pt1 ffRoll = new Pt1();
    private double prevRollTarget;
    private double prevPitchTarget;
    private boolean primed;
    private double lastTwistCos = 1.0;
    private double lastTwistSin;

    double errorBodyX;
    double errorBodyZ;
    double errorDeg;

    void configure(double ffHz, double dt) {
        this.ffPitch.configure(ffHz, dt);
        this.ffRoll.configure(ffHz, dt);
    }

    void reset() {
        this.primed = false;
        this.ffPitch.reset(0.0);
        this.ffRoll.reset(0.0);
        this.errorBodyX = 0.0;
        this.errorBodyZ = 0.0;
        this.errorDeg = 0.0;
    }

    static double twistHeading(RigidBody b, double fallback) {
        double n = b.qy * b.qy + b.qw * b.qw;
        if (n < 1e-12) {
            return fallback;
        }
        return SimMath.wrapPi(2.0 * StrictMath.atan2(b.qy, b.qw));
    }

    void levelError(RigidBody b, double rollTargetDeg, double pitchTargetDeg) {
        double phi = rollTargetDeg * SimMath.RAD;
        double theta = pitchTargetDeg * SimMath.RAD;
        double hx;
        double hy;
        double hz;
        if (phi == 0.0 && theta == 0.0) {
            hx = 0.0;
            hy = 1.0;
            hz = 0.0;
        } else {
            double cp = SimMath.cos(phi);
            hx = -SimMath.sin(phi);
            hy = cp * SimMath.cos(theta);
            hz = cp * SimMath.sin(theta);
        }
        double tx;
        double ty = hy;
        double tz;
        if (hx == 0.0 && hz == 0.0) {
            tx = 0.0;
            tz = 0.0;
        } else {
            double n = b.qw * b.qw + b.qy * b.qy;
            if (n >= 1e-12) {
                this.lastTwistCos = (b.qw * b.qw - b.qy * b.qy) / n;
                this.lastTwistSin = 2.0 * b.qw * b.qy / n;
            }
            double c = this.lastTwistCos;
            double s = this.lastTwistSin;
            tx = c * hx + s * hz;
            tz = -s * hx + c * hz;
        }
        double ux = b.r01;
        double uy = b.r11;
        double uz = b.r21;
        double cx = uy * tz - uz * ty;
        double cy = uz * tx - ux * tz;
        double cz = ux * ty - uy * tx;
        double cn = Math.sqrt(cx * cx + cy * cy + cz * cz);
        double dot = ux * tx + uy * ty + uz * tz;
        double alpha = StrictMath.atan2(cn, dot);
        double ex;
        double ey;
        double ez;
        if (cn > 1e-9) {
            double k = alpha / cn;
            ex = cx * k;
            ey = cy * k;
            ez = cz * k;
        } else if (dot < 0.0) {
            ex = b.r02 * Math.PI;
            ey = b.r12 * Math.PI;
            ez = b.r22 * Math.PI;
        } else {
            ex = 0.0;
            ey = 0.0;
            ez = 0.0;
        }
        this.errorBodyX = b.r00 * ex + b.r10 * ey + b.r20 * ez;
        this.errorBodyZ = b.r02 * ex + b.r12 * ey + b.r22 * ez;
        this.errorDeg = alpha * SimMath.DEG;
    }

    void angle(RigidBody b, double stickRoll, double stickPitch, double yawRateDps, PidProfile pid, double dt, double[] sp) {
        double rollTarget = stickRoll * pid.angleLimitDeg;
        double pitchTarget = stickPitch * pid.angleLimitDeg;
        if (!this.primed) {
            this.prevRollTarget = rollTarget;
            this.prevPitchTarget = pitchTarget;
            this.primed = true;
        }
        double dRoll = this.ffRoll.apply((rollTarget - this.prevRollTarget) / dt);
        double dPitch = this.ffPitch.apply((pitchTarget - this.prevPitchTarget) / dt);
        this.prevRollTarget = rollTarget;
        this.prevPitchTarget = pitchTarget;
        this.levelError(b, rollTarget, pitchTarget);
        double ka = pid.angleStrength / 10.0;
        double ffScale = pid.angleFeedforwardPercent / 100.0;
        double yaw = -yawRateDps * SimMath.RAD;
        double k = pid.angleEarthRefPercent / 100.0;
        double wx = ka * this.errorBodyX + ffScale * dPitch * SimMath.RAD + k * b.r10 * yaw;
        double wy = k * b.r11 * yaw + (1.0 - k) * yaw;
        double wz = ka * this.errorBodyZ + ffScale * dRoll * SimMath.RAD + k * b.r12 * yaw;
        double limit = pid.angleMaxRateDps * SimMath.RAD;
        wx = SimMath.clamp(wx, -limit, limit);
        wz = SimMath.clamp(wz, -limit, limit);
        sp[FcAxes.ROLL] = FcAxes.rollDps(wz);
        sp[FcAxes.PITCH] = FcAxes.pitchDps(wx);
        sp[FcAxes.YAW] = FcAxes.yawDps(wy);
    }

    void horizon(RigidBody b, double stickRoll, double stickPitch, PidProfile pid, double[] sp) {
        this.primed = false;
        this.levelError(b, stickRoll * pid.angleLimitDeg, stickPitch * pid.angleLimitDeg);
        double stick = Math.max(Math.abs(stickRoll), Math.abs(stickPitch));
        double tiltDeg = b.tiltRad() * SimMath.DEG;
        double w = (pid.horizonStrength / 100.0)
                * SimMath.clamp01(1.0 - stick / (pid.horizonStickTransitionPercent / 100.0))
                * SimMath.clamp01(1.0 - tiltDeg / pid.horizonLimitDeg);
        double ka = pid.angleStrength / 10.0;
        sp[FcAxes.ROLL] += w * ka * FcAxes.rollDps(this.errorBodyZ);
        sp[FcAxes.PITCH] += w * ka * FcAxes.pitchDps(this.errorBodyX);
    }
}
