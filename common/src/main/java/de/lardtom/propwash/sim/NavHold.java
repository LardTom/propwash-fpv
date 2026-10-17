package de.lardtom.propwash.sim;

final class NavHold {
    static final int DIVIDER = 20;
    static final double DT = GpsReceiver.NAV_DT;
    static final double G = ModelConstants.G;
    static final double BARO_POS_GAIN = 2.4;
    static final double BARO_VEL_GAIN = 1.44;
    static final double GPS_ALT_WEIGHT = 0.2;
    static final double GPS_POS_GAIN = 0.15;
    static final double GPS_VEL_GAIN = 0.15;
    static final double ACCEL_NOISE = 0.25;
    static final double ACCEL_BIAS = 0.08;
    static final double HOVER_BIAS = 0.06;
    static final double MAX_HOLD_SPEED = 4.0;
    static final double BRAKE_SPEED = 0.4;
    static final double BRAKE_TIMEOUT = 3.0;
    static final double GPS_TIMEOUT = 1.0;
    static final double REF_MARGIN = 0.08;
    static final double INTERFERENCE_CHANCE = 0.1;

    final GpsReceiver gps = new GpsReceiver();
    final Barometer baro = new Barometer();
    private final SimRandom rng;
    private final GaussMarkov accelBiasX = new GaussMarkov();
    private final GaussMarkov accelBiasY = new GaussMarkov();
    private final GaussMarkov accelBiasZ = new GaussMarkov();
    private final GaussMarkov headingWander = new GaussMarkov();
    double headingOffsetRad;
    double gravity = G;
    final double hoverBias;

    HoldMode mode = HoldMode.OFF;
    HoldMode requested = HoldMode.OFF;
    boolean posDenied;
    double headingErrorRad;
    double zEst;
    double vzEst;
    private boolean vertPrimed;
    double xEst;
    double zhEst;
    double vxEst;
    double vzhEst;
    boolean horizValid;
    private double noFixTime;
    double targetAlt;
    double throttleRef;
    private double altI;
    double targetX;
    double targetZ;
    private double posIx;
    private double posIz;
    private boolean braking;
    private double brakeTime;
    private boolean pilotSteering;
    double throttleStick;
    double rollStick;
    double pitchStick;
    boolean attitudeOverride;

    NavHold(long seed) {
        this.rng = new SimRandom(seed * 0x9E3779B97F4A7C15L + 0x6A09E667F3BCC909L);
        this.accelBiasX.configure(60.0, DT);
        this.accelBiasY.configure(60.0, DT);
        this.accelBiasZ.configure(60.0, DT);
        this.headingWander.configure(40.0, DT);
        this.accelBiasX.randomize(this.rng);
        this.accelBiasY.randomize(this.rng);
        this.accelBiasZ.randomize(this.rng);
        this.headingWander.randomize(this.rng);
        this.gps.seed(this.rng);
        this.baro.seed(this.rng);
        double u = this.rng.nextDouble();
        double sign = this.rng.nextDouble() < 0.5 ? -1.0 : 1.0;
        double interference = this.rng.nextDouble() < INTERFERENCE_CHANCE ? 20.0 + 25.0 * this.rng.nextDouble() : 0.0;
        this.headingOffsetRad = sign * (1.5 + 6.0 * u * u + interference) * SimMath.RAD;
        this.hoverBias = (2.0 * this.rng.nextDouble() - 1.0) * HOVER_BIAS;
    }

    void reset() {
        this.mode = HoldMode.OFF;
        this.requested = HoldMode.OFF;
        this.posDenied = false;
        this.vertPrimed = false;
        this.horizValid = false;
        this.noFixTime = 0.0;
        this.baro.reset();
        this.gps.clearHistory();
        this.attitudeOverride = false;
        this.pilotSteering = false;
        this.braking = false;
    }

    void shift(double dx, double dy, double dz) {
        this.gps.shift(dx, dy, dz);
        this.baro.shift(dy);
        this.zEst += dy;
        this.targetAlt += dy;
        this.xEst += dx;
        this.zhEst += dz;
        this.targetX += dx;
        this.targetZ += dz;
    }

    boolean positionUsable(HoldSettings s) {
        return this.gps.fix && this.gps.satellites >= s.minSatellites && this.horizValid;
    }

    void update(HoldSettings s, boolean armed, boolean failsafe, boolean grounded, PilotInput in, double skyView, RigidBody b,
                double sfWx, double sfWy, double sfWz, double airspeed, double heightAboveGround, double thrustRatio,
                double propWash, double vibration, double hoverStick, PidProfile pid) {
        this.update(s, armed, failsafe, grounded, in, skyView, b, sfWx, sfWy, sfWz, airspeed, heightAboveGround, thrustRatio, propWash,
                vibration, hoverStick, pid, null);
    }

    void update(HoldSettings s, boolean armed, boolean failsafe, boolean grounded, PilotInput in, double skyView, RigidBody b,
                double sfWx, double sfWy, double sfWz, double airspeed, double heightAboveGround, double thrustRatio,
                double propWash, double vibration, double hoverStick, PidProfile pid, AssistBridge guide) {
        SimRandom r = this.rng;
        this.gps.update(r, skyView, b.px, b.py, b.pz, b.vx, b.vy, b.vz);
        double baroAlt = this.baro.update(r, b.py, airspeed, heightAboveGround, thrustRatio, propWash);
        this.headingErrorRad = this.headingOffsetRad + 1.5 * SimMath.RAD * this.headingWander.advance(r);
        double noise = ACCEL_NOISE * (1.0 + 2.0 * Math.max(0.0, vibration));
        double ce = SimMath.cos(this.headingErrorRad);
        double se = SimMath.sin(this.headingErrorRad);
        double axm = ce * sfWx + se * sfWz + ACCEL_BIAS * this.accelBiasX.advance(r) + noise * r.gauss();
        double azm = -se * sfWx + ce * sfWz + ACCEL_BIAS * this.accelBiasZ.advance(r) + noise * r.gauss();
        double aym = sfWy - this.gravity + ACCEL_BIAS * this.accelBiasY.advance(r) + noise * r.gauss();
        this.estimateVertical(baroAlt, aym);
        this.estimateHorizontal(axm, azm);

        this.requested = in.posHold ? HoldMode.POSHOLD : (in.altHold ? HoldMode.ALTHOLD : HoldMode.OFF);
        boolean posOk = this.positionUsable(s);
        this.posDenied = this.requested == HoldMode.POSHOLD && !posOk;
        HoldMode next;
        if (!armed || failsafe) {
            next = HoldMode.OFF;
        } else if (this.requested == HoldMode.POSHOLD) {
            next = posOk ? HoldMode.POSHOLD : HoldMode.ALTHOLD;
        } else {
            next = this.requested;
        }
        if (next != HoldMode.OFF && this.mode == HoldMode.OFF) {
            this.engageAltitude(s, in.throttle);
        }
        if (next == HoldMode.POSHOLD && this.mode != HoldMode.POSHOLD) {
            this.engagePosition();
        }
        this.mode = next;
        this.attitudeOverride = false;
        this.throttleStick = in.throttle;
        this.rollStick = in.roll;
        this.pitchStick = in.pitch;
        if (next == HoldMode.OFF) {
            return;
        }
        if (guide != null) {
            this.guided(s, guide, grounded, b, hoverStick, pid, next);
            return;
        }
        if (grounded && climbCommand(in.throttle, this.throttleRef, s.throttleDeadbandPercent / 100.0, 1.0) <= 0.0) {
            this.targetAlt = this.zEst;
            this.altI = 0.0;
            this.posIx = 0.0;
            this.posIz = 0.0;
            this.targetX = this.xEst;
            this.targetZ = this.zhEst;
            this.braking = true;
            this.brakeTime = 0.0;
            return;
        }
        this.altitudeControl(s, in.throttle, b, hoverStick, pid);
        if (next == HoldMode.POSHOLD) {
            this.positionControl(s, in, b, pid);
        }
    }

    private void guided(HoldSettings s, AssistBridge g, boolean grounded, RigidBody b, double hoverStick, PidProfile pid, HoldMode next) {
        double wantedClimb = g.climb ? g.climbRate : (g.altitude ? g.altitudeTarget - this.zEst : 0.0);
        if (grounded && wantedClimb <= 0.0) {
            this.targetAlt = this.zEst;
            this.altI = 0.0;
            this.posIx = 0.0;
            this.posIz = 0.0;
            this.targetX = this.xEst;
            this.targetZ = this.zhEst;
            this.braking = false;
            return;
        }
        double maxClimb = Math.max(s.climbRateCms / 100.0, g.climb ? Math.abs(g.climbRate) : 0.0);
        double climb = 0.0;
        if (g.climb) {
            climb = g.climbRate;
            this.targetAlt = SimMath.clamp(this.targetAlt + climb * DT, this.zEst - 1.0, this.zEst + 1.0);
        } else if (g.altitude) {
            this.targetAlt = g.altitudeTarget;
        }
        this.altitudeLoop(s, climb, maxClimb, b, hoverStick, pid);
        if (next != HoldMode.POSHOLD) {
            return;
        }
        double velX;
        double velZ;
        double kPos = 0.5 * s.posP / 30.0;
        if (g.horizontal == 1) {
            this.targetX = g.targetX;
            this.targetZ = g.targetZ;
            velX = kPos * (this.targetX - this.xEst);
            velZ = kPos * (this.targetZ - this.zhEst);
        } else if (g.horizontal == 2) {
            velX = g.velocityX;
            velZ = g.velocityZ;
            this.targetX = this.xEst;
            this.targetZ = this.zhEst;
        } else {
            velX = kPos * (this.targetX - this.xEst);
            velZ = kPos * (this.targetZ - this.zhEst);
        }
        double v = Math.hypot(velX, velZ);
        double limit = g.maxSpeed;
        if (v > limit) {
            velX *= limit / Math.max(v, 1.0E-9);
            velZ *= limit / Math.max(v, 1.0E-9);
        }
        this.braking = false;
        this.pilotSteering = false;
        this.velocityLoop(s, velX, velZ, b, pid);
    }

    private void altitudeLoop(HoldSettings s, double climb, double maxClimb, RigidBody b, double hoverStick, PidProfile pid) {
        double kPos = 0.9 * s.altP / 50.0;
        double kVel = 2.2 * s.altD / 40.0;
        double kI = 0.9 * s.altI / 30.0;
        double velCmd = SimMath.clamp(climb + kPos * (this.targetAlt - this.zEst), -maxClimb, maxClimb);
        double ev = velCmd - this.vzEst;
        this.altI = SimMath.clamp(this.altI + kI * ev * DT, -4.0, 4.0);
        double g = SimTuning.referenceGravity(this.gravity);
        double acc = SimMath.clamp(kVel * ev + this.altI, -0.5 * g, 0.8 * g);
        double cosTilt = Math.max(0.5, b.r11);
        double factor = Math.max(0.05, (1.0 + acc / g) / cosTilt);
        double hoverRef = SimMath.clamp(hoverStick * (1.0 + this.hoverBias), 0.05, 0.95);
        double hoverOut = ThrottleCurve.apply(pid, hoverRef);
        double out = SimMath.clamp01(hoverOut * Math.sqrt(factor));
        this.throttleStick = ThrottleCurve.inverse(pid, out);
    }

    private void velocityLoop(HoldSettings s, double velX, double velZ, RigidBody b, PidProfile pid) {
        double kVel = 1.4 * s.posD / 40.0;
        double kI = 0.35 * s.posI / 20.0;
        double evx = velX - this.vxEst;
        double evz = velZ - this.vzhEst;
        double aMax = SimTuning.referenceGravity(this.gravity) * Math.tan(s.posMaxAngleDeg * SimMath.RAD);
        if (Math.hypot(kVel * evx + this.posIx, kVel * evz + this.posIz) < aMax) {
            this.posIx += kI * evx * DT;
            this.posIz += kI * evz * DT;
            double iMag = Math.hypot(this.posIx, this.posIz);
            if (iMag > 2.5) {
                this.posIx *= 2.5 / iMag;
                this.posIz *= 2.5 / iMag;
            }
        }
        double ax = kVel * evx + this.posIx;
        double az = kVel * evz + this.posIz;
        double a = Math.hypot(ax, az);
        if (a > aMax) {
            ax *= aMax / a;
            az *= aMax / a;
        }
        double h = b.headingRad() + this.headingErrorRad;
        double sh = SimMath.sin(h);
        double ch = SimMath.cos(h);
        double aFwd = ax * sh + az * ch;
        double aRight = -ax * ch + az * sh;
        double limit = Math.max(1.0, pid.angleLimitDeg);
        double g = SimTuning.referenceGravity(this.gravity);
        this.pitchStick = SimMath.clamp(StrictMath.atan2(aFwd, g) * SimMath.DEG / limit, -1.0, 1.0);
        this.rollStick = SimMath.clamp(StrictMath.atan2(aRight, g) * SimMath.DEG / limit, -1.0, 1.0);
        this.attitudeOverride = true;
    }

    private void estimateVertical(double baroAlt, double aym) {
        double measured = baroAlt;
        if (this.gps.hasSample) {
            measured = (1.0 - GPS_ALT_WEIGHT) * baroAlt + GPS_ALT_WEIGHT * this.gps.y;
        }
        if (!this.vertPrimed) {
            this.zEst = measured;
            this.vzEst = 0.0;
            this.vertPrimed = true;
            return;
        }
        this.vzEst += aym * DT;
        this.zEst += this.vzEst * DT;
        double res = measured - this.zEst;
        this.zEst += BARO_POS_GAIN * res * DT;
        this.vzEst += BARO_VEL_GAIN * res * DT;
    }

    private void estimateHorizontal(double axm, double azm) {
        GpsReceiver g = this.gps;
        if (!g.fix) {
            this.noFixTime += DT;
            if (this.noFixTime > GPS_TIMEOUT) {
                this.horizValid = false;
            }
        } else {
            this.noFixTime = 0.0;
        }
        if (this.horizValid) {
            this.vxEst += axm * DT;
            this.vzhEst += azm * DT;
            this.xEst += this.vxEst * DT;
            this.zhEst += this.vzhEst * DT;
        }
        if (g.sampleReady) {
            if (!this.horizValid) {
                this.xEst = g.x;
                this.zhEst = g.z;
                this.vxEst = g.vx;
                this.vzhEst = g.vz;
                this.horizValid = true;
            } else {
                this.xEst += GPS_POS_GAIN * (g.x - this.xEst);
                this.zhEst += GPS_POS_GAIN * (g.z - this.zhEst);
                this.vxEst += GPS_VEL_GAIN * (g.vx - this.vxEst);
                this.vzhEst += GPS_VEL_GAIN * (g.vz - this.vzhEst);
            }
        }
    }

    private void engageAltitude(HoldSettings s, double throttle) {
        this.targetAlt = this.zEst;
        this.altI = 0.0;
        double band = s.throttleDeadbandPercent / 100.0;
        this.throttleRef = s.midThrottle ? 0.5 : SimMath.clamp(throttle, band + REF_MARGIN, 1.0 - band - REF_MARGIN);
    }

    private void engagePosition() {
        this.targetX = this.xEst;
        this.targetZ = this.zhEst;
        this.posIx = 0.0;
        this.posIz = 0.0;
        this.braking = true;
        this.brakeTime = 0.0;
        this.pilotSteering = false;
    }

    static double climbCommand(double throttle, double ref, double deadband, double maxClimb) {
        double hi = ref + deadband;
        double lo = ref - deadband;
        if (throttle > hi) {
            return maxClimb * SimMath.clamp01((throttle - hi) / Math.max(0.05, 1.0 - hi));
        }
        if (throttle < lo) {
            return -maxClimb * SimMath.clamp01((lo - throttle) / Math.max(0.05, lo));
        }
        return 0.0;
    }

    private void altitudeControl(HoldSettings s, double throttle, RigidBody b, double hoverStick, PidProfile pid) {
        double maxClimb = s.climbRateCms / 100.0;
        double climb = climbCommand(throttle, this.throttleRef, s.throttleDeadbandPercent / 100.0, maxClimb);
        if (climb != 0.0) {
            this.targetAlt = SimMath.clamp(this.targetAlt + climb * DT, this.zEst - 1.0, this.zEst + 1.0);
        }
        double kPos = 0.9 * s.altP / 50.0;
        double kVel = 2.2 * s.altD / 40.0;
        double kI = 0.9 * s.altI / 30.0;
        double velCmd = SimMath.clamp(climb + kPos * (this.targetAlt - this.zEst), -maxClimb, maxClimb);
        double ev = velCmd - this.vzEst;
        this.altI = SimMath.clamp(this.altI + kI * ev * DT, -4.0, 4.0);
        double g = SimTuning.referenceGravity(this.gravity);
        double acc = SimMath.clamp(kVel * ev + this.altI, -0.5 * g, 0.8 * g);
        double cosTilt = Math.max(0.5, b.r11);
        double factor = Math.max(0.05, (1.0 + acc / g) / cosTilt);
        double hoverRef = SimMath.clamp(hoverStick * (1.0 + this.hoverBias), 0.05, 0.95);
        double hoverOut = ThrottleCurve.apply(pid, hoverRef);
        double out = SimMath.clamp01(hoverOut * Math.sqrt(factor));
        this.throttleStick = ThrottleCurve.inverse(pid, out);
    }

    private void positionControl(HoldSettings s, PilotInput in, RigidBody b, PidProfile pid) {
        double db = s.stickDeadbandPercent / 100.0;
        boolean steering = Math.abs(in.roll) > db || Math.abs(in.pitch) > db;
        if (steering) {
            this.pilotSteering = true;
            this.braking = false;
            this.targetX = this.xEst;
            this.targetZ = this.zhEst;
            return;
        }
        if (this.pilotSteering) {
            this.pilotSteering = false;
            this.braking = true;
            this.brakeTime = 0.0;
        }
        double velX;
        double velZ;
        if (this.braking) {
            this.brakeTime += DT;
            velX = 0.0;
            velZ = 0.0;
            double speed = Math.hypot(this.vxEst, this.vzhEst);
            if (speed < BRAKE_SPEED || this.brakeTime > BRAKE_TIMEOUT) {
                this.braking = false;
                this.targetX = this.xEst;
                this.targetZ = this.zhEst;
            }
        } else {
            double kPos = 0.5 * s.posP / 30.0;
            velX = kPos * (this.targetX - this.xEst);
            velZ = kPos * (this.targetZ - this.zhEst);
            double v = Math.hypot(velX, velZ);
            if (v > MAX_HOLD_SPEED) {
                velX *= MAX_HOLD_SPEED / v;
                velZ *= MAX_HOLD_SPEED / v;
            }
        }
        double kVel = 1.4 * s.posD / 40.0;
        double kI = 0.35 * s.posI / 20.0;
        double evx = velX - this.vxEst;
        double evz = velZ - this.vzhEst;
        double aMax = SimTuning.referenceGravity(this.gravity) * Math.tan(s.posMaxAngleDeg * SimMath.RAD);
        if (!this.braking && Math.hypot(kVel * evx + this.posIx, kVel * evz + this.posIz) < aMax) {
            this.posIx += kI * evx * DT;
            this.posIz += kI * evz * DT;
            double iMag = Math.hypot(this.posIx, this.posIz);
            if (iMag > 2.5) {
                this.posIx *= 2.5 / iMag;
                this.posIz *= 2.5 / iMag;
            }
        }
        double ax = kVel * evx + this.posIx;
        double az = kVel * evz + this.posIz;
        double a = Math.hypot(ax, az);
        if (a > aMax) {
            ax *= aMax / a;
            az *= aMax / a;
        }
        double h = b.headingRad() + this.headingErrorRad;
        double sh = SimMath.sin(h);
        double ch = SimMath.cos(h);
        double aFwd = ax * sh + az * ch;
        double aRight = -ax * ch + az * sh;
        double limit = Math.max(1.0, pid.angleLimitDeg);
        double g = SimTuning.referenceGravity(this.gravity);
        this.pitchStick = SimMath.clamp(StrictMath.atan2(aFwd, g) * SimMath.DEG / limit, -1.0, 1.0);
        this.rollStick = SimMath.clamp(StrictMath.atan2(aRight, g) * SimMath.DEG / limit, -1.0, 1.0);
        this.attitudeOverride = true;
    }
}
