package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;

import java.util.Arrays;
import java.util.SplittableRandom;

final class FlightCharacterization {
    static final double FPS = 144.0;
    static final double ROLL_TARGET_DPS = 600.0;
    static final double ANGLE_STEP_DEG = 30.0;
    static final double WASH_SINK = 5.0;
    static final double RESERVE = 0.8;
    static final double CRUISE_ALTITUDE = 300.0;
    static final double TILT_CAP = 84.0;

    private FlightCharacterization() {
    }

    static final class Result {
        String name;
        double massG;
        double maxThrustG;
        double twr;
        double hoverStatic1;
        double hoverStatic05;
        double hoverDynamic1;
        double hoverDynamic05;
        double acroTopKmh;
        double acroTilt;
        double acroTopStaticKmh;
        double angleTopKmh;
        double angleTilt;
        double punchVy1s;
        double climbMax;
        double punchG;
        double rollT90Ms;
        double rollOvershootPct;
        double rollSettleMs;
        double rollT90Ms60;
        double rollOvershootPct60;
        double rollT90Ms50;
        double rollOvershootPct50;
        double rollAccel50;
        boolean deterministic;
        double yawT90Ms;
        double yawMaxDps;
        double yawOvershootPct;
        double yawAccelDps2;
        double angleT90Ms;
        double angleOvershootDeg;
        double angleSettleMs;
        double hoverDriftM;
        double hoverTiltRms;
        double hoverTiltMax;
        double hoverRateRms;
        double hoverAltStd;
        double hoverTimeMin;
        double hoverTimeStaticMin;
        double halfThrottleTimeMin;
        double halfThrottleSpeedKmh;
        double restCell;
        double loadedCell;
        double sagPerCell;
        double fullCurrent;
        double idlePunchCell1;
        double idlePunchCell05;
        boolean idlePunchBrownout;
        double washPeak;
        double washMean;
        double washTiltMax;
        double washRateRms;
        double washRateRmsClean;
        double washVyRms;
        double washAltLoss;
        double impactTolerance;
        String slowImpact;
        String fastImpact;
        double flownImpactKmh;
        String flownImpact;
        boolean flownDisarmed;
        boolean flownSettled;
        double worstHoverTiltRmsAnyDt;
        double worstRollOvershootAnyDt;
        double worstRippleAnyDt;
    }

    static SimTuning quiet(boolean infinite) {
        SimTuning t = Tests.quietTuning();
        t.infiniteBattery = infinite;
        return t;
    }

    static FlatWorld hover(DroneBuild b, FlightControllerConfig cfg, SimTuning t, double altitude, double soc, double fps) {
        FlatWorld w = new FlatWorld(new DroneSim(b, cfg, t, 42L));
        w.place(0.0, altitude, 0.0, 0.0);
        w.sim.setBatteryCharge(soc);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        AltitudeHold hold = new AltitudeHold(altitude);
        double dt = 1.0 / fps;
        for (int f = 0; f < (int) Math.round(3.0 * fps); f++) {
            hold.apply(w, dt);
            w.frame(dt);
        }
        return w;
    }

    static double hoverPercent(DroneBuild b, double soc) {
        FlatWorld w = hover(b, new FlightControllerConfig(), quiet(true), 20.0, soc, FPS);
        AltitudeHold hold = new AltitudeHold(20.0);
        double dt = 1.0 / FPS;
        double sum = 0.0;
        int n = 0;
        for (int f = 0; f < 10 * (int) FPS; f++) {
            hold.apply(w, dt);
            w.frame(dt);
            sum += w.input.throttle;
            n++;
        }
        return 100.0 * sum / n;
    }

    static final class Cruise {
        double speed;
        double tilt;
        double vy;
        double seconds;
        double mAh;
        boolean held;
    }

    static Cruise cruise(DroneBuild b, double throttle, double soc, boolean infinite, double seconds, double fps, boolean untilReserve) {
        FlightControllerConfig cfg = new FlightControllerConfig();
        cfg.pid.angleLimitDeg = 85.0;
        FlatWorld w = hover(b, cfg, quiet(infinite), CRUISE_ALTITUDE, soc, fps);
        double dt = 1.0 / fps;
        double start = w.input.throttle;
        double base = 0.0;
        double t = 0.0;
        double speedSum = 0.0;
        double tiltSum = 0.0;
        double vySum = 0.0;
        int n = 0;
        Cruise c = new Cruise();
        c.held = true;
        double capacity = w.sim.params().capacityMah;
        while (t < seconds) {
            RigidBody body = w.sim.body;
            double err = body.py - CRUISE_ALTITUDE;
            double vyTarget = SimMath.clamp(-0.5 * err, -3.0, 3.0);
            double e = body.vy - vyTarget;
            base = SimMath.clamp(base + 0.8 * e * dt, 0.0, TILT_CAP);
            double tilt = SimMath.clamp(base + 2.0 * e, 0.0, TILT_CAP);
            double ramp = Math.min(1.0, t / 3.0);
            w.input.throttle = start + (throttle - start) * ramp;
            w.input.pitch = tilt / 85.0;
            w.frame(dt);
            t += dt;
            boolean measuring = untilReserve ? t > 10.0 : t > seconds - 3.0;
            if (measuring) {
                speedSum += Math.hypot(body.vx, body.vz);
                tiltSum += w.sim.telemetry().tiltDeg;
                vySum += body.vy;
                n++;
            }
            if (t > 10.0 && err < -20.0) {
                c.held = false;
                break;
            }
            if (untilReserve && w.sim.telemetry().mAhUsed >= RESERVE * capacity) {
                break;
            }
        }
        c.speed = n > 0 ? speedSum / n : 0.0;
        c.tilt = n > 0 ? tiltSum / n : 0.0;
        c.vy = n > 0 ? vySum / n : 0.0;
        c.seconds = t + 3.0;
        c.mAh = w.sim.telemetry().mAhUsed;
        return c;
    }

    static double angleTopSpeed(DroneBuild b, double tiltDeg) {
        FlatWorld w = hover(b, new FlightControllerConfig(), quiet(true), CRUISE_ALTITUDE, BuildAnalysis.TOP_SPEED_SOC, FPS);
        w.sim.setBatteryCharge(BuildAnalysis.TOP_SPEED_SOC);
        AltitudeHold hold = new AltitudeHold(CRUISE_ALTITUDE);
        hold.integralLimit = 1.0;
        hold.kd = 0.12;
        hold.ki = 0.1;
        hold.feedForwardScale = 1.0 / Math.sqrt(Math.cos(Math.toRadians(tiltDeg)));
        w.input.pitch = StickMath.angleToStick(tiltDeg, w.sim.config().pid);
        double dt = 1.0 / FPS;
        double sum = 0.0;
        int n = 0;
        for (int f = 0; f < 20 * (int) FPS; f++) {
            hold.apply(w, dt);
            w.frame(dt);
            if (f >= 17 * (int) FPS) {
                sum += Math.hypot(w.sim.body.vx, w.sim.body.vz);
                n++;
            }
        }
        return sum / n;
    }

    static void punch(DroneBuild b, Result r) {
        FlatWorld w = hover(b, new FlightControllerConfig(), quiet(false), 30.0, 1.0, FPS);
        r.restCell = w.sim.telemetry().cellVoltageResting;
        w.input.throttle = 1.0;
        double dt = 1.0 / FPS;
        double maxVy = 0.0;
        double minCell = Double.POSITIVE_INFINITY;
        double maxCurrent = 0.0;
        for (int f = 0; f < 5 * (int) FPS; f++) {
            w.frame(dt);
            Telemetry tm = w.sim.telemetry();
            maxVy = Math.max(maxVy, w.sim.body.vy);
            if (f == (int) FPS - 1) {
                r.punchVy1s = w.sim.body.vy;
                r.punchG = tm.gLoadMax;
                r.loadedCell = tm.cellVoltage;
            }
            minCell = Math.min(minCell, tm.cellVoltage);
            maxCurrent = Math.max(maxCurrent, tm.totalCurrentA);
        }
        r.climbMax = maxVy;
        r.sagPerCell = r.restCell - minCell;
        r.fullCurrent = maxCurrent;
    }

    static double[] idlePunch(DroneBuild b, double soc) {
        FlatWorld w = hover(b, new FlightControllerConfig(), quiet(false), 100.0, soc, FPS);
        w.input.mode = StabMode.ACRO;
        w.input.throttle = 0.0;
        w.run(0.4, FPS);
        w.input.throttle = 1.0;
        double minCell = Double.POSITIVE_INFINITY;
        boolean brownout = false;
        for (int f = 0; f < (int) FPS; f++) {
            w.frame(1.0 / FPS);
            minCell = Math.min(minCell, w.sim.telemetry().cellVoltage);
            brownout |= w.sim.events().contains(SimEventType.BROWNOUT);
        }
        return new double[]{minCell, brownout || !w.sim.telemetry().armed ? 1.0 : 0.0};
    }

    static double[] rateStep(DroneBuild b, double fps, int axis, double stick, double target, double window) {
        return rateStep(b, fps, axis, stick, target, window, -1.0);
    }

    static double[] rateStep(DroneBuild b, double fps, int axis, double stick, double target, double window, double throttle) {
        FlatWorld w = hover(b, new FlightControllerConfig(), quiet(true), 30.0, 0.5, fps);
        w.input.mode = StabMode.ACRO;
        w.input.throttle = w.sim.telemetry().hoverThrottleStick;
        if (throttle >= 0.0) {
            w.input.throttle = Math.max(throttle, w.input.throttle);
            w.run(0.3, fps);
        }
        if (axis == 0) {
            w.input.roll = stick;
        } else if (axis == 1) {
            w.input.pitch = stick;
        } else {
            w.input.yaw = stick;
        }
        double dt = 1.0 / fps;
        double t = 0.0;
        double t90 = Double.NaN;
        double peak = 0.0;
        double settle = 0.0;
        double prev = 0.0;
        double accel = 0.0;
        double minLate = Double.POSITIVE_INFINITY;
        double maxLate = Double.NEGATIVE_INFINITY;
        while (t < window - 1e-9) {
            w.frame(dt);
            t += dt;
            double r = axis == 0 ? Tests.rollRateDps(w.sim) : (axis == 1 ? Tests.pitchRateDps(w.sim) : Tests.yawRateDps(w.sim));
            if (Double.isNaN(t90) && r >= 0.9 * target) {
                t90 = t;
            }
            peak = Math.max(peak, r);
            accel = Math.max(accel, (r - prev) / dt);
            prev = r;
            if (Math.abs(r - target) > 0.05 * target) {
                settle = t;
            }
            if (t > window - 0.2) {
                minLate = Math.min(minLate, r);
                maxLate = Math.max(maxLate, r);
            }
        }
        return new double[]{t90, peak / target - 1.0, settle, peak, accel, (maxLate - minLate) / target};
    }

    static double[] angleStep(DroneBuild b) {
        FlatWorld w = hover(b, new FlightControllerConfig(), quiet(true), 30.0, 0.5, FPS);
        AltitudeHold hold = new AltitudeHold(30.0);
        double dt = 1.0 / FPS;
        w.input.roll = StickMath.angleToStick(ANGLE_STEP_DEG, w.sim.config().pid);
        double t = 0.0;
        double t90 = Double.NaN;
        double peak = 0.0;
        double settle = 0.0;
        while (t < 2.0 - 1e-9) {
            hold.apply(w, dt);
            w.frame(dt);
            t += dt;
            double roll = Tests.rollDeg(w.sim);
            if (Double.isNaN(t90) && roll >= 0.9 * ANGLE_STEP_DEG) {
                t90 = t;
            }
            peak = Math.max(peak, roll);
            if (Math.abs(roll - ANGLE_STEP_DEG) > 1.0) {
                settle = t;
            }
        }
        return new double[]{t90, peak - ANGLE_STEP_DEG, settle};
    }

    static double[] hoverStability(DroneBuild b, double fps, boolean randomDt) {
        SimTuning t = new SimTuning();
        FlatWorld w = hover(b, new FlightControllerConfig(), t, 20.0, 1.0, fps);
        AltitudeHold hold = new AltitudeHold(20.0);
        double x0 = w.sim.body.px;
        double z0 = w.sim.body.pz;
        double tiltSq = 0.0;
        double tiltMax = 0.0;
        double rateSq = 0.0;
        double altSum = 0.0;
        double altSq = 0.0;
        double drift = 0.0;
        int n = 0;
        SplittableRandom rng = new SplittableRandom(7L);
        double time = 0.0;
        while (time < 10.0) {
            double dt = randomDt ? 0.001 + 0.049 * rng.nextDouble() : 1.0 / fps;
            hold.apply(w, dt);
            w.frame(dt);
            time += dt;
            RigidBody body = w.sim.body;
            double tilt = w.sim.telemetry().tiltDeg;
            tiltSq += tilt * tilt;
            tiltMax = Math.max(tiltMax, tilt);
            double rate = SimMath.hypot3(body.wbx, body.wby, body.wbz) * SimMath.DEG;
            rateSq += rate * rate;
            double alt = body.py - 20.0;
            altSum += alt;
            altSq += alt * alt;
            drift = Math.max(drift, Math.hypot(body.px - x0, body.pz - z0));
            n++;
        }
        double altMean = altSum / n;
        return new double[]{drift, Math.sqrt(tiltSq / n), tiltMax, Math.sqrt(rateSq / n), Math.sqrt(Math.max(0.0, altSq / n - altMean * altMean))};
    }

    static double hoverMinutes(DroneBuild b) {
        double fps = 60.0;
        FlatWorld w = hover(b, new FlightControllerConfig(), quiet(false), 20.0, 1.0, fps);
        AltitudeHold hold = new AltitudeHold(20.0);
        double dt = 1.0 / fps;
        double t = 3.0;
        AirframeSpec p = w.sim.params();
        double usable = p.usableFraction * p.capacityMah;
        while (w.sim.telemetry().batteryMahUsed < usable && w.sim.telemetry().batteryCellVoltage > p.landingCellVoltage && t < 3600.0
                && w.sim.telemetry().armed) {
            hold.apply(w, dt);
            w.frame(dt);
            t += dt;
        }
        return t / 60.0;
    }

    static double[] washDescent(DroneBuild b, boolean wash) {
        SimTuning t = quiet(true);
        t.propWash = wash;
        t.wakeField = wash;
        FlatWorld w = hover(b, new FlightControllerConfig(), t, 200.0, 1.0, FPS);
        double dt = 1.0 / FPS;
        double hoverStick = w.sim.telemetry().hoverThrottleStick;
        double kv = 0.15 * hoverStick;
        double integral = 0.0;
        double peak = 0.0;
        double washSum = 0.0;
        double rateSq = 0.0;
        double vySq = 0.0;
        double tiltMax = 0.0;
        int n = 0;
        double time = 0.0;
        double altAtRecovery = Double.NaN;
        double lowest = Double.POSITIVE_INFINITY;
        boolean recovered = false;
        while (time < 7.0) {
            RigidBody body = w.sim.body;
            double target = time < 5.0 ? -WASH_SINK * Math.min(1.0, time / 1.0) : 0.0;
            double e = target - body.vy;
            integral = SimMath.clamp(integral + kv * e * dt, -0.5, 0.5);
            w.input.throttle = SimMath.clamp01(hoverStick + kv * e + integral);
            w.frame(dt);
            time += dt;
            Telemetry tm = w.sim.telemetry();
            peak = Math.max(peak, tm.propWash);
            if (time >= 2.0 && time < 5.0) {
                washSum += tm.propWash;
                double rate = SimMath.hypot3(body.wbx, body.wby, body.wbz) * SimMath.DEG;
                rateSq += rate * rate;
                double ve = body.vy + WASH_SINK;
                vySq += ve * ve;
                tiltMax = Math.max(tiltMax, tm.tiltDeg);
                n++;
            }
            if (time >= 5.0 && Double.isNaN(altAtRecovery)) {
                altAtRecovery = body.py;
            }
            if (time >= 5.0 && !recovered) {
                lowest = Math.min(lowest, body.py);
                tiltMax = Math.max(tiltMax, tm.tiltDeg);
                if (body.vy >= 0.0) {
                    recovered = true;
                }
            }
        }
        return new double[]{peak, washSum / n, tiltMax, Math.sqrt(rateSq / n), Math.sqrt(vySq / n), altAtRecovery - lowest};
    }

    static String impact(DroneBuild b, double speed) {
        FlatWorld w = new FlatWorld(new DroneSim(b, new FlightControllerConfig(), quiet(false), 42L));
        w.place(0.0, 3.0, 0.0, 0.0);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        AltitudeHold hold = new AltitudeHold(3.0);
        double dt = 1.0 / FPS;
        for (int f = 0; f < 288; f++) {
            hold.apply(w, dt);
            w.frame(dt);
        }
        w.input.throttle = w.sim.telemetry().hoverThrottleStick;
        w.wallX = w.x + w.sim.params().hitboxWidth / 2.0 + 0.001;
        w.sim.body.vx = speed;
        w.sim.body.vz = 0.0;
        for (int f = 0; f < 50; f++) {
            w.frame(1_000_000L);
            if (w.report.hitX) {
                EventQueue q = w.sim.events();
                String armed = w.sim.telemetry().armed ? "armed" : "disarmed " + w.sim.telemetry().lastDisarmReason;
                if (q.contains(SimEventType.CRASH)) {
                    return "CRASH, " + armed;
                }
                if (q.contains(SimEventType.HARD_IMPACT)) {
                    return "HARD_IMPACT, " + armed;
                }
                if (q.contains(SimEventType.BUMP)) {
                    return "BUMP, " + armed;
                }
                return "none, " + armed;
            }
        }
        return "no hit";
    }

    static void flownCrash(DroneBuild b, Result r) {
        FlatWorld w = new FlatWorld(new DroneSim(b, new FlightControllerConfig(), quiet(false), 42L));
        w.place(0.0, 3.0, 0.0, Math.PI / 2.0);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        AltitudeHold hold = new AltitudeHold(3.0);
        hold.integralLimit = 1.0;
        double dt = 1.0 / FPS;
        for (int f = 0; f < 288; f++) {
            hold.apply(w, dt);
            w.frame(dt);
        }
        w.wallX = w.x + 80.0;
        double tilt = Math.min(45.0, r.acroTilt - 5.0);
        w.input.pitch = StickMath.angleToStick(tilt, w.sim.config().pid);
        hold.feedForwardScale = 1.0 / Math.sqrt(Math.cos(Math.toRadians(tilt)));
        int crashes = 0;
        double hitSpeed = 0.0;
        String kind = "no hit";
        for (int f = 0; f < 20 * (int) FPS; f++) {
            if (w.sim.telemetry().armed) {
                hold.apply(w, dt);
            } else {
                w.input.throttle = 0.0;
                w.input.pitch = 0.0;
            }
            double vx = w.sim.body.vx;
            w.frame(dt);
            if (w.report.hitX && hitSpeed == 0.0) {
                hitSpeed = Math.abs(vx);
                EventQueue q = w.sim.events();
                kind = q.contains(SimEventType.CRASH) ? "CRASH" : (q.contains(SimEventType.HARD_IMPACT) ? "HARD_IMPACT"
                        : (q.contains(SimEventType.BUMP) ? "BUMP" : "none"));
            }
            crashes = w.sim.telemetry().crashCount;
        }
        Telemetry tm = w.sim.telemetry();
        r.flownImpactKmh = hitSpeed * 3.6;
        r.flownImpact = kind + " (" + crashes + "x)";
        r.flownDisarmed = !tm.armed && tm.lastDisarmReason == DisarmReason.CRASH;
        RigidBody body = w.sim.body;
        r.flownSettled = Double.isFinite(body.px + body.py + body.qw) && tm.grounded
                && SimMath.hypot3(body.vx, body.vy, body.vz) < 0.05;
    }

    static double[] frameRateSweep(DroneBuild b) {
        double worstTilt = 0.0;
        double worstOvershoot = 0.0;
        double worstRipple = 0.0;
        double stick = RateCurves.stickFor(RatesType.ACTUAL, new RateCurve(), ROLL_TARGET_DPS);
        for (double fps : new double[]{1000.0, 250.0, 120.0, 60.0, 30.0, 20.0}) {
            double[] h = hoverStability(b, fps, false);
            worstTilt = Math.max(worstTilt, h[1]);
            double[] r = rateStep(b, fps, 0, stick, ROLL_TARGET_DPS, 0.8);
            worstOvershoot = Math.max(worstOvershoot, r[1]);
            worstRipple = Math.max(worstRipple, r[5]);
        }
        double[] h = hoverStability(b, 60.0, true);
        worstTilt = Math.max(worstTilt, h[1]);
        return new double[]{worstTilt, worstOvershoot, worstRipple};
    }

    static Result measure(DroneBuild b, String name) {
        Result r = new Result();
        r.name = name;
        AirframeSpec p = AirframeSpec.of(b);
        BuildAnalysis a = BuildAnalysis.of(b);
        r.massG = a.massGrams();
        r.maxThrustG = a.maxThrustPerMotorGrams();
        r.twr = a.thrustToWeight();
        r.hoverStatic1 = 100.0 * OperatingPoints.hoverStick(p, 1.0);
        r.hoverStatic05 = a.hoverThrottlePercent();
        r.hoverDynamic1 = hoverPercent(b, 1.0);
        r.hoverDynamic05 = hoverPercent(b, 0.5);
        r.acroTopStaticKmh = a.topSpeedAcroKmh();
        Cruise top = cruise(b, 1.0, BuildAnalysis.TOP_SPEED_SOC, true, 25.0, FPS, false);
        r.acroTopKmh = top.speed * 3.6;
        r.acroTilt = top.tilt;
        r.angleTilt = Math.min(60.0, r.acroTilt - 1.0);
        r.angleTopKmh = angleTopSpeed(b, r.angleTilt) * 3.6;
        punch(b, r);
        double[] idle1 = idlePunch(b, 1.0);
        double[] idle05 = idlePunch(b, 0.5);
        r.idlePunchCell1 = idle1[0];
        r.idlePunchCell05 = idle05[0];
        r.idlePunchBrownout = idle1[1] > 0.0 || idle05[1] > 0.0;
        double stick = RateCurves.stickFor(RatesType.ACTUAL, new RateCurve(), ROLL_TARGET_DPS);
        double[] roll = rateStep(b, FPS, 0, stick, ROLL_TARGET_DPS, 0.5);
        r.rollT90Ms = roll[0] * 1000.0;
        r.rollOvershootPct = roll[1] * 100.0;
        r.rollSettleMs = roll[2] * 1000.0;
        double[] roll60 = rateStep(b, 60.0, 0, stick, ROLL_TARGET_DPS, 0.5);
        r.rollT90Ms60 = roll60[0] * 1000.0;
        r.rollOvershootPct60 = roll60[1] * 100.0;
        double[] roll50 = rateStep(b, FPS, 0, stick, ROLL_TARGET_DPS, 0.3, 0.5);
        r.rollT90Ms50 = roll50[0] * 1000.0;
        r.rollOvershootPct50 = roll50[1] * 100.0;
        r.rollAccel50 = roll50[4];
        double yawMax = RateCurves.maxRateDps(RatesType.ACTUAL, new RateCurve());
        double[] yaw = rateStep(b, FPS, 2, 1.0, yawMax, 1.0);
        r.yawT90Ms = yaw[0] * 1000.0;
        r.yawMaxDps = yaw[3];
        r.yawOvershootPct = yaw[1] * 100.0;
        r.yawAccelDps2 = yaw[4];
        double[] angle = angleStep(b);
        r.angleT90Ms = angle[0] * 1000.0;
        r.angleOvershootDeg = angle[1];
        r.angleSettleMs = angle[2] * 1000.0;
        double[] hov = hoverStability(b, FPS, false);
        double[] again = hoverStability(b, FPS, false);
        r.deterministic = Arrays.equals(hov, again);
        r.hoverDriftM = hov[0];
        r.hoverTiltRms = hov[1];
        r.hoverTiltMax = hov[2];
        r.hoverRateRms = hov[3];
        r.hoverAltStd = hov[4];
        r.hoverTimeMin = hoverMinutes(b);
        r.hoverTimeStaticMin = a.hoverFlightTimeMin();
        if (r.hoverDynamic1 < 45.0) {
            Cruise half = cruise(b, 0.5, 1.0, false, 3600.0, 60.0, true);
            r.halfThrottleTimeMin = half.held ? half.seconds / 60.0 : Double.NaN;
            r.halfThrottleSpeedKmh = half.speed * 3.6;
        } else {
            r.halfThrottleTimeMin = Double.NaN;
            r.halfThrottleSpeedKmh = Double.NaN;
        }
        double[] wash = washDescent(b, true);
        double[] clean = washDescent(b, false);
        r.washPeak = wash[0];
        r.washMean = wash[1];
        r.washTiltMax = wash[2];
        r.washRateRms = wash[3];
        r.washRateRmsClean = clean[3];
        r.washVyRms = wash[4];
        r.washAltLoss = wash[5];
        r.impactTolerance = p.impactTolerance;
        r.slowImpact = impact(b, 0.7 * p.impactTolerance);
        r.fastImpact = impact(b, 1.3 * p.impactTolerance);
        flownCrash(b, r);
        double[] sweep = frameRateSweep(b);
        r.worstHoverTiltRmsAnyDt = sweep[0];
        r.worstRollOvershootAnyDt = sweep[1] * 100.0;
        r.worstRippleAnyDt = sweep[2] * 100.0;
        return r;
    }
}
