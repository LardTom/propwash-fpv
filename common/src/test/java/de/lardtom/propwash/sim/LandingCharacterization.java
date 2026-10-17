package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;

import java.util.Locale;
import java.util.SplittableRandom;

final class LandingCharacterization {
    static final double FPS = 144.0;
    static final double DT = 1.0 / FPS;
    static final double SETTLE_SECONDS = 3.0;
    static final double LATE_TOUCH_SECONDS = 8.0;
    static final double IDLE_SECONDS = 3.0;
    static final double CLEARANCE = 0.03;

    enum Surface {
        FLAT, SLAB_EDGE, STEP
    }

    record Family(String name, double sinkMin, double sinkMax, double speedMin, double speedMax, double tiltMax, double rateMax,
                  boolean disarm) {
    }

    static final Family GENTLE = new Family("sanft senkrecht", 0.2, 0.8, 0.0, 0.3, 8.0, 15.0, true);
    static final Family DRIFT = new Family("sanft mit Fahrt", 0.2, 0.8, 1.0, 3.0, 15.0, 30.0, true);
    static final Family SKID = new Family("schnell rutschend", 0.3, 1.5, 4.0, 8.0, 25.0, 45.0, true);
    static final Family HARD = new Family("hart", 1.5, 3.0, 0.0, 2.0, 20.0, 60.0, true);
    static final Family IDLE = new Family("sanft, scharf liegen lassen", 0.2, 0.8, 0.0, 0.3, 8.0, 15.0, false);
    static final Family CRASH = new Family("Absturz", 3.0, 7.0, 2.0, 10.0, 35.0, 90.0, true);
    static final Family[] FAMILIES = {GENTLE, DRIFT, SKID, HARD, IDLE, CRASH};

    record Touchdown(double sink, double speed, double travelAzimuth, double tiltDeg, double tiltAzimuth, double rx, double ry,
                     double rz, double reaction, double disarmDelay) {
        static Touchdown draw(Family f, SplittableRandom r) {
            return new Touchdown(uniform(r, f.sinkMin, f.sinkMax), uniform(r, f.speedMin, f.speedMax), r.nextDouble() * 2.0 * Math.PI,
                    uniform(r, 0.0, f.tiltMax), r.nextDouble() * 2.0 * Math.PI, uniform(r, -f.rateMax, f.rateMax),
                    uniform(r, -f.rateMax, f.rateMax), uniform(r, -f.rateMax, f.rateMax), uniform(r, 0.05, 0.3),
                    uniform(r, 0.3, 1.5));
        }
    }

    static final class Outcome {
        boolean flipped;
        boolean side;
        double finalTilt;
        double maxTilt;
        int crashes;
        boolean crashDisarmed;
        boolean touched;
    }

    static final class Tally {
        int runs;
        int flips;
        int sides;
        int crashDisarms;
        int crashes;
        double maxTiltSum;

        void add(Outcome o) {
            this.runs++;
            if (o.flipped) {
                this.flips++;
            }
            if (o.side) {
                this.sides++;
            }
            if (o.crashDisarmed) {
                this.crashDisarms++;
            }
            this.crashes += o.crashes;
            this.maxTiltSum += o.maxTilt;
        }

        double flipRate() {
            return this.runs == 0 ? 0.0 : (double) this.flips / this.runs;
        }
    }

    private LandingCharacterization() {
    }

    static double uniform(SplittableRandom r, double lo, double hi) {
        return lo + (hi - lo) * r.nextDouble();
    }

    static SimTuning tuning(boolean crashDisarm) {
        SimTuning t = new SimTuning();
        t.gyroNoiseDps = 0.0;
        t.gyroVibrationDps = 0.0;
        t.crashDisarm = crashDisarm;
        return t;
    }

    static BoxWorld airborne(DroneBuild build, SimTuning tuning, StabMode mode) {
        BoxWorld w = BoxWorld.of(build, tuning);
        w.ground(0.0);
        w.placeBottom(0.0, 0.0, 0.0, 0.0);
        w.sim.setBatteryCharge(0.8);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        AltitudeHold hold = new AltitudeHold(w.y + 1.0);
        for (int f = 0; f < (int) Math.round(2.5 * FPS); f++) {
            w.input.throttle = hold.throttle(w.sim, DT);
            w.frame(DT);
        }
        w.input.mode = mode;
        return w;
    }

    static void surface(BoxWorld w, Surface s, double travelAzimuth) {
        w.boxes.clear();
        w.ground(0.0);
        if (s == Surface.SLAB_EDGE) {
            double ex = Math.cos(travelAzimuth);
            double ez = Math.sin(travelAzimuth);
            double cx = w.x + 0.5 * ex * 1.0;
            double cz = w.z + 0.5 * ez * 1.0;
            w.box(cx - 0.5, 0.0, cz - 0.5, cx + 0.5, 0.5, cz + 0.5);
        } else if (s == Surface.STEP) {
            double ex = Math.cos(travelAzimuth);
            double ez = Math.sin(travelAzimuth);
            double cx = w.x - 0.5 * ex * 1.0 + 0.04 * ex;
            double cz = w.z - 0.5 * ez * 1.0 + 0.04 * ez;
            w.box(cx - 0.5, 0.0, cz - 0.5, cx + 0.5, 0.5, cz + 0.5);
        }
    }

    static Outcome land(DroneBuild build, StabMode mode, boolean crashDisarm, Surface surface, Family family, Touchdown td) {
        return land(build, mode, crashDisarm, surface, family, td, null);
    }

    static Outcome land(DroneBuild build, StabMode mode, boolean crashDisarm, Surface surface, Family family, Touchdown td,
                        StringBuilder trace) {
        BoxWorld w = airborne(build, tuning(crashDisarm), mode);
        Outcome o = new Outcome();
        double top = surface == Surface.SLAB_EDGE ? 0.5 : 0.0;
        double hover = w.sim.telemetry().hoverThrottleStick;
        double half = Math.toRadians(td.tiltDeg) / 2.0;
        RigidBody b = w.sim.body;
        double y = top + CLEARANCE + tiltedDrop(w.sim.params(), td) - w.foot;
        if (surface == Surface.STEP) {
            y = 0.5 + CLEARANCE + tiltedDrop(w.sim.params(), td) - w.foot;
        }
        w.y = y;
        w.sim.teleport(w.x, y, w.z, true);
        surface(w, surface, td.travelAzimuth);
        b.qw = Math.cos(half);
        b.qx = Math.sin(half) * Math.cos(td.tiltAzimuth);
        b.qy = 0.0;
        b.qz = Math.sin(half) * Math.sin(td.tiltAzimuth);
        b.updateDerived();
        b.vx = td.speed * Math.cos(td.travelAzimuth);
        b.vz = td.speed * Math.sin(td.travelAzimuth);
        b.vy = -td.sink;
        b.setBodyRates(Math.toRadians(td.rx), Math.toRadians(td.ry), Math.toRadians(td.rz));
        w.input.roll = 0.0;
        w.input.pitch = 0.0;
        w.input.yaw = 0.0;
        w.input.throttle = hover;
        double touchTime = Double.NaN;
        double t = 0.0;
        int baseCrashes = w.sim.telemetry().crashCount;
        double limit = 2.5 + td.reaction + (family.disarm ? td.disarmDelay : IDLE_SECONDS);
        while (t < limit) {
            if (Double.isNaN(touchTime) && (w.sim.telemetry().grounded || w.sim.telemetry().crashCount > baseCrashes)) {
                touchTime = t;
                o.touched = true;
            }
            if (Double.isNaN(touchTime)) {
                w.input.throttle = t > 2.0 ? 0.0 : SimMath.clamp01(hover + 0.15 * (-td.sink - b.vy));
            } else {
                double since = t - touchTime;
                if (since >= td.reaction) {
                    w.input.throttle = 0.0;
                }
                double off = family.disarm ? td.disarmDelay : IDLE_SECONDS;
                if (since >= off) {
                    w.input.armSwitch = false;
                    break;
                }
                o.maxTilt = Math.max(o.maxTilt, w.sim.telemetry().tiltDeg);
            }
            w.frame(DT);
            t += DT;
            if (trace != null) {
                trace(trace, w, t);
            }
        }
        double lateLimit = t + LATE_TOUCH_SECONDS;
        while (!o.touched && t < lateLimit) {
            w.input.throttle = 0.0;
            w.frame(DT);
            t += DT;
            if (trace != null) {
                trace(trace, w, t);
            }
            o.touched = w.sim.telemetry().grounded || w.sim.telemetry().crashCount > baseCrashes;
        }
        if (!o.touched) {
            throw new IllegalStateException("keine Landung: " + build.frame().key() + " " + mode + " " + surface + " " + td);
        }
        if (w.sim.telemetry().armed == false && w.sim.telemetry().lastDisarmReason == DisarmReason.CRASH) {
            o.crashDisarmed = true;
        }
        w.input.armSwitch = false;
        for (int f = 0; f < (int) Math.round(SETTLE_SECONDS * FPS); f++) {
            w.frame(DT);
            if (trace != null && f < 300) {
                trace(trace, w, t + f * DT);
            }
            o.maxTilt = Math.max(o.maxTilt, w.sim.telemetry().tiltDeg);
        }
        o.crashes = w.sim.telemetry().crashCount - baseCrashes;
        o.finalTilt = w.sim.telemetry().tiltDeg;
        o.flipped = o.finalTilt > 120.0;
        o.side = !o.flipped && o.finalTilt > 60.0;
        return o;
    }

    static void trace(StringBuilder out, BoxWorld w, double t) {
        Telemetry tm = w.sim.telemetry();
        RigidBody b = w.sim.body;
        AirframeSpec p = w.sim.params();
        double low = Double.POSITIVE_INFINITY;
        for (int k = 0; k < AirframeSpec.HULL_POINTS; k++) {
            low = Math.min(low, b.r10 * p.hullX(k) + b.r11 * p.hullY(k) + b.r12 * p.hullZ(k));
        }
        PowerSystem ps = w.sim.power;
        out.append(String.format(Locale.ROOT,
                "%6.3f thr %.2f arm %s gnd %s tilt %6.1f roll %6.1f pitch %6.1f w %7.0f %7.0f %7.0f v %5.2f %5.2f %5.2f foot %6.3f low %6.3f om %4.2f %4.2f %4.2f %4.2f%n",
                t, w.input.throttle, tm.armed ? "j" : "n", tm.grounded ? "j" : "n", tm.tiltDeg, tm.rollDeg, tm.pitchDeg,
                Math.toDegrees(b.wbx), Math.toDegrees(b.wby), Math.toDegrees(b.wbz), b.vx, b.vy, b.vz, w.bottom(), w.y + low,
                ps.omega[0] / p.hoverOmega, ps.omega[1] / p.hoverOmega, ps.omega[2] / p.hoverOmega, ps.omega[3] / p.hoverOmega));
    }

    static double tiltedDrop(AirframeSpec p, Touchdown td) {
        double half = Math.toRadians(td.tiltDeg) / 2.0;
        double qw = Math.cos(half);
        double qx = Math.sin(half) * Math.cos(td.tiltAzimuth);
        double qz = Math.sin(half) * Math.sin(td.tiltAzimuth);
        double r10 = 2.0 * (qx * 0.0 + qw * qz);
        double r11 = 1.0 - 2.0 * (qx * qx + qz * qz);
        double r12 = 2.0 * (0.0 * qz - qw * qx);
        double low = 0.0;
        for (int k = 0; k < AirframeSpec.HULL_POINTS; k++) {
            low = Math.min(low, r10 * p.hullX(k) + r11 * p.hullY(k) + r12 * p.hullZ(k));
        }
        return Math.max(0.0, p.hitboxFootOffset - low);
    }

    static Tally run(DroneBuild build, StabMode mode, boolean crashDisarm, Surface surface, Family family, int runs, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        Tally tally = new Tally();
        for (int i = 0; i < runs; i++) {
            Touchdown td = Touchdown.draw(family, r);
            tally.add(land(build, mode, crashDisarm, surface, family, td));
        }
        return tally;
    }

    static String row(String build, StabMode mode, boolean crashDisarm, Surface surface, Family family, Tally t) {
        return String.format(Locale.ROOT, "%-12s %-6s %-5s %-9s %-28s %4d %5.1f %% %5.1f %% %5d %5d %6.1f%n", build, mode,
                crashDisarm ? "ja" : "nein", surface, family.name, t.runs, 100.0 * t.flipRate(), t.runs == 0 ? 0.0 : 100.0 * t.sides / t.runs,
                t.crashDisarms, t.crashes, t.runs == 0 ? 0.0 : t.maxTiltSum / t.runs);
    }
}
