package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmedCrashRestTest {
    static final double FPS = 144.0;
    static final double DT = 1.0 / FPS;
    static final double MEASURE_S = 5.0;
    static final double SETTLED_S = 1.0;
    static final double LATE_S = 2.0;
    static final double MAX_TRAVEL = 1.0;
    static final double MAX_LATE_THRUST = 0.2;
    static final double MAX_LATE_ITERM = 0.05;
    static final double LYING_DEG = 70.0;
    static final double LYING_CHECK_S = 1.0;
    static final double BLOCKED_THRUST_SHARE = 0.3;
    static final double TIP_CONTACT = 0.004;
    static final double TIP_FREE = 0.02;
    static final int TIP_FRAMES = 5;
    static final double SIDE_CLEARANCE = 0.001;
    static final double SIDE_DUTY = 0.4;
    static final int SIDE_FRAMES = 4;

    enum Impact {
        FLOOR, WALL
    }

    record Scenario(DroneBuild build, StabMode mode, boolean airmode, double throttle, Impact impact, double tiltDeg, double azimuthDeg) {
        String key() {
            return String.format(Locale.ROOT, "%s %s %s, Gas %.0f %%, %s, %.0f° Neigung, Achse %.0f°", this.build.frame().key(), this.mode,
                    this.airmode ? "Airmode" : "ohne Airmode", 100.0 * this.throttle, this.impact == Impact.FLOOR ? "Boden" : "Wand",
                    this.tiltDeg, this.azimuthDeg);
        }
    }

    static final class Result {
        boolean touched;
        boolean armed;
        double travel;
        double lateThrust;
        double lateIterm;
        double lyingIterm;
        double lyingSeconds;
        int blockedChecks;
        double worstBlockedShare;
        double maxSpeed;
        double finalTilt;

        String row() {
            return String.format(Locale.ROOT, "Weg nach %.0f s %.2f Blöcke, Schub/Gewicht am Ende %.2f, I-Anteil am Ende %.3f, liegend %.1f s mit "
                            + "I-Anteil bis %.3f, schnellstens %.1f m/s, Endlage %.0f°, scharf %s", SETTLED_S, this.travel, this.lateThrust,
                    this.lateIterm, this.lyingSeconds, this.lyingIterm, this.maxSpeed, this.finalTilt, this.armed);
        }
    }

    static SimTuning tuning() {
        SimTuning t = new SimTuning();
        t.gyroNoiseDps = 0.0;
        t.gyroVibrationDps = 0.0;
        t.crashDisarm = false;
        return t;
    }

    static BoxWorld airborne(Scenario s) {
        FlightControllerConfig config = new FlightControllerConfig();
        config.pid.airmode = s.airmode;
        BoxWorld w = new BoxWorld(new DroneSim(s.build, config, tuning(), 42L));
        w.ground(0.0);
        w.placeBottom(0.0, 0.0, 0.0, 0.0);
        w.sim.setBatteryCharge(0.8);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        AltitudeHold hold = new AltitudeHold(w.y + 1.5);
        for (int f = 0; f < (int) Math.round(2.5 * FPS); f++) {
            w.input.throttle = hold.throttle(w.sim, DT);
            w.frame(DT);
        }
        w.input.mode = s.mode;
        return w;
    }

    static void orient(RigidBody b, double tiltDeg, double azimuthDeg) {
        double half = Math.toRadians(tiltDeg) / 2.0;
        double az = Math.toRadians(azimuthDeg);
        b.qw = Math.cos(half);
        b.qx = Math.sin(half) * Math.cos(az);
        b.qy = 0.0;
        b.qz = Math.sin(half) * Math.sin(az);
        b.updateDerived();
    }

    static double iterm(DroneSim sim) {
        double i = 0.0;
        for (int a = 0; a < 3; a++) {
            i = Math.max(i, Math.abs(sim.fc.axes[a].iTerm));
        }
        return i;
    }

    static double tipHeight(BoxWorld w, int i) {
        RigidBody b = w.sim.body;
        AirframeSpec p = w.sim.params();
        return w.y + b.r10 * p.hullX(i) + b.r11 * p.hullY(i) + b.r12 * p.hullZ(i);
    }

    static void checkBlockedProps(BoxWorld w, int[] touching, Result r) {
        DroneSim sim = w.sim;
        double hover = sim.params().hoverOmega;
        double free = Double.NaN;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double tip = tipHeight(w, i);
            touching[i] = tip < TIP_CONTACT ? touching[i] + 1 : 0;
            double rpm = Math.abs(sim.power.omega[i]);
            if (tip > TIP_FREE && rpm > 0.2 * hover) {
                double share = sim.thrust[i] / (rpm * rpm);
                free = Double.isNaN(free) ? share : Math.max(free, share);
            }
        }
        if (Double.isNaN(free) || !(free > 0.0) || sim.body.r11 > CollisionSolver.STRIKE_NORMAL_LIMIT) {
            return;
        }
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double rpm = Math.abs(sim.power.omega[i]);
            if (touching[i] >= TIP_FRAMES && rpm > 0.2 * hover) {
                r.blockedChecks++;
                r.worstBlockedShare = Math.max(r.worstBlockedShare, sim.thrust[i] / (rpm * rpm) / free);
            }
        }
    }

    static Result run(Scenario s) {
        return run(s, null);
    }

    static Result run(Scenario s, double[] duty) {
        BoxWorld w = airborne(s);
        RigidBody b = w.sim.body;
        Result r = new Result();
        if (s.impact == Impact.FLOOR) {
            double y = 0.25 + w.sim.params().hitboxWidth - w.foot;
            w.y = y;
            w.sim.teleport(w.x, y, w.z, true);
            orient(b, s.tiltDeg, s.azimuthDeg);
            b.vx = 0.5;
            b.vz = 0.0;
            b.vy = -2.0;
        } else {
            double y = 0.6 - w.foot;
            w.y = y;
            w.sim.teleport(w.x, y, w.z, true);
            w.box(w.x + 0.6, 0.0, w.z - 5.0, w.x + 1.6, 3.0, w.z + 5.0);
            orient(b, s.tiltDeg, s.azimuthDeg);
            b.vx = 3.0;
            b.vz = 0.0;
            b.vy = 0.0;
        }
        b.setBodyRates(0.0, 0.0, 0.0);
        w.input.roll = 0.0;
        w.input.pitch = 0.0;
        w.input.yaw = 0.0;
        w.input.throttle = s.throttle;
        if (duty != null) {
            w.sim.setDutyOverride(duty[0], duty[1], duty[2], duty[3]);
        }
        int[] touching = new int[AirframeSpec.MOTORS];
        double touch = Double.NaN;
        double settledX = Double.NaN;
        double settledZ = Double.NaN;
        double lying = 0.0;
        double thrustSum = 0.0;
        int thrustFrames = 0;
        double t = 0.0;
        while (t < 3.0 + MEASURE_S) {
            w.frame(DT);
            t += DT;
            Telemetry tel = w.sim.telemetry();
            if (Double.isNaN(touch) && (tel.grounded || tel.impactCount > 0 || tel.crashCount > 0)) {
                touch = t;
                r.touched = true;
            }
            if (Double.isNaN(touch)) {
                continue;
            }
            double since = t - touch;
            if (since > MEASURE_S) {
                break;
            }
            if (since >= SETTLED_S) {
                checkBlockedProps(w, touching, r);
            }
            lying = tel.grounded && tel.tiltDeg > LYING_DEG ? lying + DT : 0.0;
            r.lyingSeconds = Math.max(r.lyingSeconds, lying);
            if (lying > LYING_CHECK_S) {
                r.lyingIterm = Math.max(r.lyingIterm, iterm(w.sim));
            }
            if (since >= SETTLED_S) {
                if (Double.isNaN(settledX)) {
                    settledX = w.x;
                    settledZ = w.z;
                }
                r.travel = Math.max(r.travel, Math.hypot(w.x - settledX, w.z - settledZ));
                r.maxSpeed = Math.max(r.maxSpeed, Math.hypot(b.vx, b.vz));
            }
            if (since >= MEASURE_S - LATE_S) {
                thrustSum += tel.thrustToWeight;
                thrustFrames++;
                r.lateIterm = Math.max(r.lateIterm, iterm(w.sim));
            }
        }
        r.lateThrust = thrustFrames == 0 ? Double.NaN : thrustSum / thrustFrames;
        r.finalTilt = w.sim.telemetry().tiltDeg;
        r.armed = w.sim.telemetry().armed;
        return r;
    }

    static List<Scenario> idleScenarios() {
        List<Scenario> list = new ArrayList<>();
        for (DroneBuild build : DroneBuild.PRESETS) {
            for (StabMode mode : new StabMode[]{StabMode.ACRO, StabMode.ANGLE}) {
                for (boolean airmode : new boolean[]{true, false}) {
                    for (double tilt : new double[]{60.0, 90.0, 120.0, 150.0, 180.0}) {
                        for (double az : new double[]{0.0, 45.0, 90.0}) {
                            list.add(new Scenario(build, mode, airmode, 0.0, Impact.FLOOR, tilt, az));
                        }
                    }
                    for (double tilt : new double[]{0.0, 20.0, 40.0}) {
                        for (double az : new double[]{0.0, 45.0}) {
                            list.add(new Scenario(build, mode, airmode, 0.0, Impact.WALL, tilt, az));
                        }
                    }
                }
            }
        }
        return list;
    }

    static Map<Scenario, Result> runAll(List<Scenario> list) {
        Map<Scenario, Result> out = new ConcurrentHashMap<>();
        list.parallelStream().forEach(s -> out.put(s, run(s)));
        return out;
    }

    @Test
    void armedCrashAtIdleThrottleComesToRestWithoutThrust() {
        List<Scenario> list = idleScenarios();
        Map<Scenario, Result> results = runAll(list);
        StringBuilder report = new StringBuilder();
        List<String> failures = new ArrayList<>();
        for (Scenario s : list) {
            Result r = results.get(s);
            report.append(s.key()).append(": ").append(r.row()).append('\n');
            if (!r.touched || !r.armed) {
                failures.add(s.key() + ": kein Aufprall oder nicht mehr scharf");
            } else if (r.travel > MAX_TRAVEL || r.lateThrust > MAX_LATE_THRUST || r.lateIterm > MAX_LATE_ITERM) {
                failures.add(s.key() + ": " + r.row());
            }
        }
        CalibrationHarness.write("crasharm_idle.txt", report.toString());
        assertTrue(failures.isEmpty(), failures.size() + " von " + list.size() + " Abstürzen ohne Disarm mit Gas 0 kommen nicht zur Ruhe "
                + "(erlaubt: höchstens " + MAX_TRAVEL + " Block Weg nach " + SETTLED_S + " s, Schub/Gewicht in den letzten " + LATE_S + " s "
                + "höchstens " + MAX_LATE_THRUST + ", I-Anteil höchstens " + MAX_LATE_ITERM + "):\n" + String.join("\n", failures));
    }

    @Test
    void itermDoesNotWindUpWhileLyingOnTheFloorWithThrottle() {
        List<Scenario> list = new ArrayList<>();
        for (DroneBuild build : DroneBuild.PRESETS) {
            for (StabMode mode : new StabMode[]{StabMode.ACRO, StabMode.ANGLE}) {
                for (double tilt : new double[]{90.0, 120.0, 180.0}) {
                    for (double az : new double[]{0.0, 45.0}) {
                        list.add(new Scenario(build, mode, true, 0.3, Impact.FLOOR, tilt, az));
                    }
                }
            }
        }
        Map<Scenario, Result> results = runAll(list);
        List<String> failures = new ArrayList<>();
        int lying = 0;
        for (Scenario s : list) {
            Result r = results.get(s);
            if (r.lyingSeconds > LYING_CHECK_S) {
                lying++;
                if (r.lyingIterm > MAX_LATE_ITERM) {
                    failures.add(s.key() + ": " + r.row());
                }
            }
        }
        assertTrue(lying >= list.size() / 4, "zu wenige Fälle liegen länger als " + LYING_CHECK_S + " s: " + lying + " von " + list.size());
        assertTrue(failures.isEmpty(), failures.size() + " von " + lying + " liegenden Drohnen wickeln mit 30 % Gas den I-Anteil auf:\n"
                + String.join("\n", failures));
    }

    static double[] thrustOnTheSide(DroneBuild build, double floor, double[] tips) {
        Scenario s = new Scenario(build, StabMode.ACRO, true, 0.0, Impact.FLOOR, 90.0, 0.0);
        BoxWorld w = airborne(s);
        w.boxes.clear();
        w.ground(floor);
        RigidBody b = w.sim.body;
        orient(b, 90.0, 0.0);
        AirframeSpec p = w.sim.params();
        double lowest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            lowest = Math.min(lowest, b.r10 * p.hullX(i) + b.r11 * p.hullY(i) + b.r12 * p.hullZ(i));
        }
        double y = SIDE_CLEARANCE - lowest;
        w.y = y;
        w.sim.teleport(w.x, y, w.z, false);
        orient(b, 90.0, 0.0);
        b.setBodyRates(0.0, 0.0, 0.0);
        w.sim.setDutyOverride(SIDE_DUTY, SIDE_DUTY, SIDE_DUTY, SIDE_DUTY);
        for (int f = 0; f < SIDE_FRAMES; f++) {
            w.frame(DT);
        }
        double[] thrust = new double[AirframeSpec.MOTORS];
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            thrust[i] = w.sim.thrust[i];
            if (tips != null) {
                tips[i] = tipHeight(w, i) - floor;
            }
        }
        return thrust;
    }

    @Test
    void propsTouchingTheFloorLoseTheirThrust() {
        List<String> failures = new ArrayList<>();
        int checks = 0;
        for (DroneBuild build : new DroneBuild[]{DroneBuild.FREESTYLE, DroneBuild.RACER, DroneBuild.LONG_RANGE}) {
            double[] tips = new double[AirframeSpec.MOTORS];
            double[] onFloor = thrustOnTheSide(build, 0.0, tips);
            double[] free = thrustOnTheSide(build, -50.0, null);
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                if (tips[i] > TIP_CONTACT || !(free[i] > 0.0)) {
                    continue;
                }
                checks++;
                double share = onFloor[i] / free[i];
                if (share > BLOCKED_THRUST_SHARE) {
                    failures.add(String.format(Locale.ROOT, "%s auf der Seite, Motor %d mit Prop %.1f mm über dem Boden: %.0f %% des Schubs in der Luft",
                            build.frame().key(), i + 1, 1000.0 * tips[i], 100.0 * share));
                }
            }
        }
        assertTrue(checks > 0, "keine Prop mit Bodenkontakt gesehen");
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void sticksStillWakeTheControllerOfADroneLyingOnItsBack() {
        Scenario s = new Scenario(DroneBuild.FREESTYLE, StabMode.ANGLE, true, 0.0, Impact.FLOOR, 180.0, 0.0);
        BoxWorld w = airborne(s);
        RigidBody b = w.sim.body;
        double y = 0.25 + w.sim.params().hitboxWidth - w.foot;
        w.y = y;
        w.sim.teleport(w.x, y, w.z, true);
        orient(b, 180.0, 0.0);
        b.vy = -2.0;
        b.setBodyRates(0.0, 0.0, 0.0);
        w.input.throttle = 0.0;
        for (int f = 0; f < (int) Math.round(3.0 * FPS); f++) {
            w.frame(DT);
        }
        double tilt = w.sim.telemetry().tiltDeg;
        w.input.roll = 0.8;
        double strongest = 0.0;
        for (int f = 0; f < (int) Math.round(0.5 * FPS); f++) {
            w.frame(DT);
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                strongest = Math.max(strongest, w.sim.telemetry().motorOutput[i]);
            }
        }
        assertTrue(w.sim.telemetry().armed, "nach dem Aufprall nicht mehr scharf");
        assertTrue(strongest > 0.3, String.format(Locale.ROOT, "Rollknüppel nach 3 s auf dem Rücken (%.0f°) bewegt die Motoren nicht: "
                + "stärkster Motor %.2f", tilt, strongest));
    }
}
