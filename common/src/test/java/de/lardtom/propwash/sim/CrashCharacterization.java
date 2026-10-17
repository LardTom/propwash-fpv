package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;

import java.util.Locale;
import java.util.SplittableRandom;

final class CrashCharacterization {
    static final double FPS = System.getenv("PROPWASH_CRASH_FPS") == null ? 144.0 : Double.parseDouble(System.getenv("PROPWASH_CRASH_FPS"));
    static final double DT = 1.0 / FPS;
    static final double SETTLE_SECONDS = 4.0;
    static final double APPROACH_LIMIT = 4.0;
    static final double UPSIDE_DOWN_DEG = 120.0;
    static final double SIDE_DEG = 60.0;
    static final double LEANING_GAP = 0.15;

    enum Kind {
        GENTLE, LOW_DROP, STEP, SLAB, DIVE, BANK, WALL, INVERTED, TUMBLE
    }

    record Scenario(String name, Kind kind, double speedMin, double speedMax, double angleMin, double angleMax) {
    }

    static final Scenario GENTLE = new Scenario("sanfte Landung", Kind.GENTLE, 0.0, 0.0, 0.0, 0.0);
    static final Scenario LOW_DROP = new Scenario("Fall aus 15-60 cm, Motoren aus", Kind.LOW_DROP, 0.0, 1.0, 0.0, 20.0);
    static final Scenario STEP = new Scenario("von einer Stufe gerutscht", Kind.STEP, 0.0, 0.0, 0.0, 0.0);
    static final Scenario SLAB = new Scenario("Halbblock-Kante", Kind.SLAB, 0.0, 0.0, 0.0, 0.0);
    static final Scenario DIVE_FLAT_5 = new Scenario("Boden flach 10-30 Grad, 5 m/s", Kind.DIVE, 4.0, 6.0, 10.0, 30.0);
    static final Scenario DIVE_FLAT_10 = new Scenario("Boden flach 10-30 Grad, 10 m/s", Kind.DIVE, 8.0, 12.0, 10.0, 30.0);
    static final Scenario DIVE_FLAT_20 = new Scenario("Boden flach 10-30 Grad, 20 m/s", Kind.DIVE, 17.0, 23.0, 10.0, 30.0);
    static final Scenario DIVE_STEEP_5 = new Scenario("Boden steil 45-80 Grad, 5 m/s", Kind.DIVE, 4.0, 6.0, 45.0, 80.0);
    static final Scenario DIVE_STEEP_10 = new Scenario("Boden steil 45-80 Grad, 10 m/s", Kind.DIVE, 8.0, 12.0, 45.0, 80.0);
    static final Scenario DIVE_STEEP_20 = new Scenario("Boden steil 45-80 Grad, 20 m/s", Kind.DIVE, 17.0, 23.0, 45.0, 80.0);
    static final Scenario BANK = new Scenario("Kurve, Prop in den Boden, 5-15 m/s", Kind.BANK, 5.0, 15.0, 50.0, 90.0);
    static final Scenario WALL_5 = new Scenario("Wand 5 m/s", Kind.WALL, 4.0, 6.0, 0.0, 40.0);
    static final Scenario WALL_10 = new Scenario("Wand 10 m/s", Kind.WALL, 8.0, 12.0, 0.0, 40.0);
    static final Scenario WALL_20 = new Scenario("Wand 20 m/s", Kind.WALL, 17.0, 23.0, 0.0, 40.0);
    static final Scenario INVERTED = new Scenario("kopfüber aufgeschlagen 1-5 m/s", Kind.INVERTED, 1.0, 5.0, 150.0, 180.0);
    static final Scenario TUMBLE = new Scenario("Taumeln nach Prop-Schlag", Kind.TUMBLE, 2.0, 10.0, 10.0, 25.0);
    static final Scenario[] ALL = {GENTLE, LOW_DROP, STEP, SLAB, DIVE_FLAT_5, DIVE_FLAT_10, DIVE_FLAT_20, DIVE_STEEP_5, DIVE_STEEP_10,
            DIVE_STEEP_20, BANK, WALL_5, WALL_10, WALL_20, INVERTED, TUMBLE};
    static final Scenario[] CRASHES = {DIVE_FLAT_5, DIVE_FLAT_10, DIVE_FLAT_20, DIVE_STEEP_5, DIVE_STEEP_10, DIVE_STEEP_20, BANK, WALL_5,
            WALL_10, WALL_20, TUMBLE};
    static final Scenario[] SHORT = {GENTLE, LOW_DROP, STEP, SLAB, DIVE_FLAT_10, DIVE_STEEP_10, BANK, WALL_10, INVERTED, TUMBLE};

    static final class Outcome {
        double finalTilt;
        double maxRate;
        int crashes;
        boolean leaning;

        boolean upsideDown() {
            return this.finalTilt > UPSIDE_DOWN_DEG;
        }

        boolean side() {
            return !this.upsideDown() && this.finalTilt > SIDE_DEG;
        }
    }

    static final class Tally {
        int runs;
        int upsideDown;
        int side;
        int leaning;
        int crashes;
        double maxRateSum;

        synchronized void add(Outcome o) {
            this.runs++;
            if (o.upsideDown()) {
                this.upsideDown++;
            }
            if (o.side()) {
                this.side++;
                if (o.leaning) {
                    this.leaning++;
                }
            }
            this.crashes += o.crashes;
            this.maxRateSum += o.maxRate;
        }

        double upsideDownRate() {
            return this.runs == 0 ? 0.0 : (double) this.upsideDown / this.runs;
        }

        double sideRate() {
            return this.runs == 0 ? 0.0 : (double) this.side / this.runs;
        }

        double freeSideRate() {
            return this.runs == 0 ? 0.0 : (double) (this.side - this.leaning) / this.runs;
        }

        double uprightRate() {
            return 1.0 - this.upsideDownRate() - this.sideRate();
        }
    }

    private CrashCharacterization() {
    }

    static double uniform(SplittableRandom r, double lo, double hi) {
        return lo + (hi - lo) * r.nextDouble();
    }

    static double[] multiply(double[] a, double[] b) {
        return new double[]{a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3], a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2],
                a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1], a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0]};
    }

    static double[] axisAngle(double ax, double ay, double az, double angle) {
        double s = Math.sin(0.5 * angle);
        return new double[]{Math.cos(0.5 * angle), ax * s, ay * s, az * s};
    }

    static double[] attitude(double heading, double pitchDown, double roll) {
        return multiply(multiply(axisAngle(0.0, 1.0, 0.0, heading), axisAngle(1.0, 0.0, 0.0, pitchDown)), axisAngle(0.0, 0.0, 1.0, roll));
    }

    static double[] randomAttitude(SplittableRandom r) {
        double u1 = r.nextDouble();
        double u2 = r.nextDouble() * 2.0 * Math.PI;
        double u3 = r.nextDouble() * 2.0 * Math.PI;
        double a = Math.sqrt(1.0 - u1);
        double b = Math.sqrt(u1);
        return new double[]{a * Math.sin(u2), a * Math.cos(u2), b * Math.sin(u3), b * Math.cos(u3)};
    }

    static void orient(RigidBody b, double[] q) {
        double n = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        b.qw = q[0] / n;
        b.qx = q[1] / n;
        b.qy = q[2] / n;
        b.qz = q[3] / n;
        b.updateDerived();
    }

    static double lowestOffset(AirframeSpec p, RigidBody b) {
        double low = 0.0;
        for (int k = 0; k < AirframeSpec.HULL_POINTS; k++) {
            low = Math.min(low, b.r10 * p.hullX(k) + b.r11 * p.hullY(k) + b.r12 * p.hullZ(k));
        }
        return low;
    }

    static boolean contact(BoxWorld w, int baseCrashes, int baseImpacts) {
        Telemetry tm = w.sim.telemetry();
        return tm.grounded || tm.crashCount > baseCrashes || tm.impactCount > baseImpacts || w.report.hitX || w.report.hitY
                || w.report.hitZ;
    }

    static Outcome run(DroneBuild build, Scenario s, SplittableRandom r) {
        return run(build, s, r, null);
    }

    static Outcome run(DroneBuild build, Scenario s, SplittableRandom r, StringBuilder trace) {
        if (s.kind == Kind.GENTLE || s.kind == Kind.STEP || s.kind == Kind.SLAB) {
            LandingCharacterization.Family family = s.kind == Kind.GENTLE ? LandingCharacterization.GENTLE : LandingCharacterization.DRIFT;
            LandingCharacterization.Surface surface = s.kind == Kind.GENTLE ? LandingCharacterization.Surface.FLAT
                    : s.kind == Kind.STEP ? LandingCharacterization.Surface.STEP : LandingCharacterization.Surface.SLAB_EDGE;
            StabMode mode = r.nextBoolean() ? StabMode.ACRO : StabMode.ANGLE;
            LandingCharacterization.Touchdown td = LandingCharacterization.Touchdown.draw(family, r);
            LandingCharacterization.Outcome lo = LandingCharacterization.land(build, mode, true, surface, family, td);
            Outcome o = new Outcome();
            o.finalTilt = lo.finalTilt;
            o.crashes = lo.crashes;
            return o;
        }
        BoxWorld w = LandingCharacterization.airborne(build, LandingCharacterization.tuning(true),
                System.getenv("PROPWASH_CRASH_MODE") == null ? StabMode.ACRO : StabMode.valueOf(System.getenv("PROPWASH_CRASH_MODE")));
        AirframeSpec p = w.sim.params();
        RigidBody b = w.sim.body;
        double hover = w.sim.telemetry().hoverThrottleStick;
        double speed = uniform(r, s.speedMin, s.speedMax);
        double angle = Math.toRadians(uniform(r, s.angleMin, s.angleMax));
        double azimuth = r.nextDouble() * 2.0 * Math.PI;
        double throttle = 0.0;
        boolean armed = true;
        double vx;
        double vy;
        double vz;
        double height;
        double wallFace = Double.POSITIVE_INFINITY;
        double[] q;
        double[] rates = {Math.toRadians(uniform(r, -60.0, 60.0)), Math.toRadians(uniform(r, -60.0, 60.0)),
                Math.toRadians(uniform(r, -60.0, 60.0))};
        w.boxes.clear();
        w.ground(0.0);
        switch (s.kind) {
            case LOW_DROP -> {
                q = attitude(azimuth, Math.toRadians(uniform(r, -1.0, 1.0) * s.angleMax), Math.toRadians(uniform(r, -1.0, 1.0) * s.angleMax));
                vx = speed * Math.sin(azimuth);
                vz = speed * Math.cos(azimuth);
                vy = 0.0;
                height = uniform(r, 0.15, 0.6);
                armed = false;
                rates = new double[]{Math.toRadians(uniform(r, -30.0, 30.0)), Math.toRadians(uniform(r, -30.0, 30.0)),
                        Math.toRadians(uniform(r, -30.0, 30.0))};
            }
            case DIVE -> {
                double pitch = angle + Math.toRadians(uniform(r, 0.0, 30.0));
                q = attitude(azimuth + Math.toRadians(uniform(r, -15.0, 15.0)), pitch, Math.toRadians(uniform(r, -20.0, 20.0)));
                vx = speed * Math.cos(angle) * Math.sin(azimuth);
                vz = speed * Math.cos(angle) * Math.cos(azimuth);
                vy = -speed * Math.sin(angle);
                height = 0.6;
                throttle = uniform(r, 0.15, hover);
            }
            case BANK -> {
                double bank = angle * (r.nextBoolean() ? 1.0 : -1.0);
                q = attitude(azimuth, Math.toRadians(uniform(r, 0.0, 30.0)), bank);
                vx = speed * Math.sin(azimuth);
                vz = speed * Math.cos(azimuth);
                vy = -uniform(r, 0.5, 2.0);
                height = 0.12;
                throttle = uniform(r, 0.15, hover);
            }
            case WALL -> {
                double yaw = angle * (r.nextBoolean() ? 1.0 : -1.0);
                q = attitude(yaw, Math.toRadians(uniform(r, 15.0, 50.0)), Math.toRadians(uniform(r, -15.0, 15.0)));
                vx = speed * Math.sin(yaw);
                vz = speed * Math.cos(yaw);
                vy = uniform(r, -1.0, 1.0);
                height = uniform(r, 0.5, 2.5);
                throttle = uniform(r, hover, 0.7);
                double front = w.z + 0.6 + 0.5 * p.hitboxWidth;
                wallFace = front;
                w.box(-50.0, 0.0, front, 50.0, 6.0, front + 1.0);
            }
            case INVERTED -> {
                q = attitude(azimuth, Math.toRadians(uniform(r, -1.0, 1.0) * (180.0 - Math.toDegrees(angle))), Math.PI);
                q = multiply(q, axisAngle(0.0, 0.0, 1.0, Math.toRadians(uniform(r, -1.0, 1.0) * (180.0 - Math.toDegrees(angle)))));
                double forward = uniform(r, 0.0, 6.0);
                vx = forward * Math.sin(azimuth);
                vz = forward * Math.cos(azimuth);
                vy = -speed;
                height = 0.4;
                rates = new double[]{Math.toRadians(uniform(r, -90.0, 90.0)), Math.toRadians(uniform(r, -90.0, 90.0)),
                        Math.toRadians(uniform(r, -90.0, 90.0))};
            }
            case TUMBLE -> {
                q = randomAttitude(r);
                vx = speed * Math.sin(azimuth);
                vz = speed * Math.cos(azimuth);
                vy = uniform(r, -2.0, 1.0);
                height = uniform(r, 1.0, 3.0);
                armed = false;
                double ax = r.nextDouble() * 2.0 - 1.0;
                double ay = r.nextDouble() * 2.0 - 1.0;
                double az = r.nextDouble() * 2.0 - 1.0;
                double n = Math.sqrt(ax * ax + ay * ay + az * az) + 1.0e-9;
                double rate = Math.toDegrees(angle);
                rates = new double[]{rate * ax / n, rate * ay / n, rate * az / n};
            }
            default -> throw new IllegalArgumentException(s.kind.name());
        }
        if (!armed) {
            w.input.armSwitch = false;
            w.frame(DT);
        }
        orient(b, q);
        double y = height - lowestOffset(p, b) - w.foot;
        w.y = y;
        w.sim.teleport(w.x, y, w.z, true);
        orient(b, q);
        b.vx = vx;
        b.vy = vy;
        b.vz = vz;
        b.setBodyRates(rates[0], rates[1], rates[2]);
        w.input.roll = 0.0;
        w.input.pitch = 0.0;
        w.input.yaw = 0.0;
        w.input.throttle = throttle;
        Outcome o = new Outcome();
        int baseCrashes = w.sim.telemetry().crashCount;
        int baseImpacts = w.sim.telemetry().impactCount;
        double reaction = uniform(r, 0.1, 0.4);
        double disarm = reaction + uniform(r, 0.2, 0.8);
        double t = 0.0;
        double touch = Double.NaN;
        while (Double.isNaN(touch) || t - touch < disarm) {
            w.frame(DT);
            t += DT;
            if (Double.isNaN(touch) && contact(w, baseCrashes, baseImpacts)) {
                touch = t;
            }
            if (!Double.isNaN(touch) && t - touch >= reaction) {
                w.input.throttle = 0.0;
            }
            if (!Double.isNaN(touch)) {
                o.maxRate = Math.max(o.maxRate, Math.sqrt(b.wbx * b.wbx + b.wby * b.wby + b.wbz * b.wbz));
            }
            if (trace != null) {
                LandingCharacterization.trace(trace, w, t);
            }
            if (Double.isNaN(touch) && t > APPROACH_LIMIT) {
                throw new IllegalStateException("kein Aufprall: " + build.frame().key() + " " + s.name);
            }
        }
        w.input.armSwitch = false;
        for (int f = 0; f < (int) Math.round(SETTLE_SECONDS * FPS); f++) {
            w.frame(DT);
            o.maxRate = Math.max(o.maxRate, Math.sqrt(b.wbx * b.wbx + b.wby * b.wby + b.wbz * b.wbz));
            if (trace != null && f < 300) {
                LandingCharacterization.trace(trace, w, t + f * DT);
            }
        }
        o.crashes = w.sim.telemetry().crashCount - baseCrashes;
        o.finalTilt = w.sim.telemetry().tiltDeg;
        o.leaning = s.kind == Kind.WALL && wallFace - (w.z + 0.5 * p.hitboxWidth) < LEANING_GAP;
        return o;
    }

    static SplittableRandom random(DroneBuild build, Scenario s, long seed) {
        return new SplittableRandom(seed ^ s.name.hashCode() * 31L ^ build.frame().key().hashCode());
    }

    static Tally run(DroneBuild build, Scenario s, int runs, long seed) {
        SplittableRandom r = random(build, s, seed);
        Tally tally = new Tally();
        for (int i = 0; i < runs; i++) {
            tally.add(run(build, s, r.split()));
        }
        return tally;
    }

    static String row(String build, Scenario s, Tally t) {
        return String.format(Locale.ROOT, "%-12s %-38s %4d  oben %5.1f %%  Seite %5.1f %%  Kopf %5.1f %%  an der Wand %3d  Crashs %4d  max %5.1f rad/s%n", build,
                s.name, t.runs, 100.0 * t.uprightRate(), 100.0 * t.sideRate(), 100.0 * t.upsideDownRate(), t.leaning, t.crashes,
                t.runs == 0 ? 0.0 : t.maxRateSum / t.runs);
    }
}
