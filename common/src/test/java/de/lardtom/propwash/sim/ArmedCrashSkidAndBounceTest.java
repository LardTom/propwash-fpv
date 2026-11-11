package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmedCrashSkidAndBounceTest {
    static final double FPS = ArmedCrashRestTest.FPS;
    static final double DT = ArmedCrashRestTest.DT;
    static final double MEASURE_S = 5.0;
    static final double TWITCH_S = 0.5;
    static final double SETTLED_S = 1.0;
    static final double THRUST_SHARE = 0.3;
    static final double MAX_THRUST_S = 0.25;
    static final double MAX_ITERM = 0.05;
    static final double MAX_EXTRA_TRAVEL = 3.0;
    static final double MAX_MEAN_EXTRA_TRAVEL = 0.1;
    static final double MAX_EXTRA_HEIGHT = 0.5;
    static final StabMode[] MODES = {StabMode.ACRO, StabMode.ANGLE, StabMode.HORIZON};

    record Scenario(DroneBuild build, StabMode mode, boolean airmode, double sink, double forward, double tiltDeg, double azimuthDeg) {
        String key() {
            return String.format(Locale.ROOT, "%s %s %s, %.1f m/s ab, %.0f m/s vor, %.0f° Neigung, Achse %.0f°", this.build.frame().key(),
                    this.mode, this.airmode ? "Airmode" : "ohne Airmode", this.sink, this.forward, this.tiltDeg, this.azimuthDeg);
        }
    }

    static final class Result {
        boolean touched;
        boolean armed;
        double travel;
        double height;
        double thrustSeconds;
        double iterm;

        String row() {
            return String.format(Locale.ROOT, "Weg nach %.0f s %.2f Blöcke, höchstens %.2f m über dem Boden, ab %.1f s %.2f s mit Schub über %.1f × "
                    + "Gewicht, I-Anteil bis %.3f", SETTLED_S, this.travel, this.height, TWITCH_S, this.thrustSeconds, THRUST_SHARE, this.iterm);
        }
    }

    record Pair(Result armed, Result passive) {
        String row() {
            return String.format(Locale.ROOT, "scharf: %s; unscharf ab dem Aufprall: Weg %.2f Blöcke, höchstens %.2f m", this.armed.row(),
                    this.passive.travel, this.passive.height);
        }
    }

    static Result run(Scenario s, boolean passive) {
        BoxWorld w = ArmedCrashRestTest.airborne(new ArmedCrashRestTest.Scenario(s.build, s.mode, s.airmode, 0.0,
                ArmedCrashRestTest.Impact.FLOOR, s.tiltDeg, s.azimuthDeg));
        RigidBody b = w.sim.body;
        double y = 0.25 + w.sim.params().hitboxWidth - w.foot;
        w.y = y;
        w.sim.teleport(w.x, y, w.z, true);
        ArmedCrashRestTest.orient(b, s.tiltDeg, s.azimuthDeg);
        b.vx = s.forward;
        b.vz = 0.0;
        b.vy = -s.sink;
        b.setBodyRates(0.0, 0.0, 0.0);
        w.input.roll = 0.0;
        w.input.pitch = 0.0;
        w.input.yaw = 0.0;
        w.input.throttle = 0.0;
        Result r = new Result();
        double touch = Double.NaN;
        double settledX = Double.NaN;
        double settledZ = Double.NaN;
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
            if (passive) {
                w.input.armSwitch = false;
            }
            double since = t - touch;
            if (since > MEASURE_S) {
                break;
            }
            r.height = Math.max(r.height, w.y + w.foot);
            if (since >= TWITCH_S) {
                if (tel.thrustToWeight > THRUST_SHARE) {
                    r.thrustSeconds += DT;
                }
                r.iterm = Math.max(r.iterm, ArmedCrashRestTest.iterm(w.sim));
            }
            if (since >= SETTLED_S) {
                if (Double.isNaN(settledX)) {
                    settledX = w.x;
                    settledZ = w.z;
                }
                r.travel = Math.max(r.travel, Math.hypot(w.x - settledX, w.z - settledZ));
            }
        }
        r.armed = w.sim.telemetry().armed;
        return r;
    }

    static Map<Scenario, Pair> runAll(List<Scenario> list) {
        Map<Scenario, Pair> out = new ConcurrentHashMap<>();
        list.parallelStream().forEach(s -> out.put(s, new Pair(run(s, false), run(s, true))));
        return out;
    }

    static List<String> check(List<Scenario> list, String file) {
        Map<Scenario, Pair> results = runAll(list);
        StringBuilder report = new StringBuilder();
        List<String> failures = new ArrayList<>();
        double extra = 0.0;
        for (Scenario s : list) {
            Pair p = results.get(s);
            report.append(s.key()).append(": ").append(p.row()).append('\n');
            Result a = p.armed;
            extra += a.travel - p.passive.travel;
            if (!a.touched || !a.armed) {
                failures.add(s.key() + ": kein Aufprall oder nicht mehr scharf");
            } else if (a.thrustSeconds > MAX_THRUST_S || a.iterm > MAX_ITERM || a.travel > p.passive.travel + MAX_EXTRA_TRAVEL
                    || a.height > p.passive.height + MAX_EXTRA_HEIGHT) {
                failures.add(s.key() + ": " + p.row());
            }
        }
        double mean = extra / list.size();
        if (mean > MAX_MEAN_EXTRA_TRAVEL) {
            failures.add(String.format(Locale.ROOT, "im Mittel %.2f Blöcke Weg ab %.0f s mehr als unscharf", mean, SETTLED_S));
        }
        report.append(String.format(Locale.ROOT, "Mittel: %.3f Blöcke Weg ab %.0f s mehr als unscharf%n", mean, SETTLED_S));
        CalibrationHarness.write(file, report.toString());
        return failures;
    }

    static String limits() {
        return String.format(Locale.ROOT, "erlaubt: ab %.1f s nach dem Aufprall höchstens %.2f s mit Schub über %.1f × Gewicht und I-Anteil "
                        + "höchstens %.2f, Höhe höchstens %.1f m und Weg ab %.0f s höchstens %.1f Blöcke mehr als unscharf, im Mittel höchstens "
                        + "%.1f Blöcke", TWITCH_S, MAX_THRUST_S, THRUST_SHARE, MAX_ITERM, MAX_EXTRA_HEIGHT, SETTLED_S, MAX_EXTRA_TRAVEL,
                MAX_MEAN_EXTRA_TRAVEL);
    }

    @Test
    void uprightSkidAtIdleThrottleDoesNotDriveTheDroneOn() {
        List<Scenario> list = new ArrayList<>();
        for (DroneBuild build : DroneBuild.PRESETS) {
            for (StabMode mode : MODES) {
                for (double sink : new double[]{1.0, 3.0}) {
                    for (double forward : new double[]{4.0, 8.0, 12.0}) {
                        for (double tilt : new double[]{0.0, 15.0, 25.0}) {
                            for (double az : new double[]{0.0, 90.0}) {
                                list.add(new Scenario(build, mode, true, sink, forward, tilt, az));
                            }
                        }
                    }
                }
            }
        }
        List<String> failures = check(list, "crasharm_skid.txt");
        assertTrue(failures.isEmpty(), failures.size() + " von " + list.size() + " fast aufrechten Rutschern mit Gas 0 und Airmode schieben "
                + "sich mit Schub weiter (" + limits() + "):\n" + String.join("\n", failures));
    }

    @Test
    void bounceAtIdleThrottleDoesNotThrowTheDrone() {
        List<Scenario> list = new ArrayList<>();
        for (DroneBuild build : DroneBuild.PRESETS) {
            double tolerance = AirframeSpec.of(build).impactTolerance;
            for (StabMode mode : MODES) {
                for (boolean airmode : new boolean[]{true, false}) {
                    for (double share : new double[]{0.3, 0.6, 0.9}) {
                        for (double forward : new double[]{4.0, 8.0}) {
                            for (double tilt : new double[]{45.0, 90.0, 135.0, 180.0}) {
                                for (double az : new double[]{0.0, 45.0}) {
                                    list.add(new Scenario(build, mode, airmode, share * tolerance, forward, tilt, az));
                                }
                            }
                        }
                    }
                }
            }
        }
        List<String> failures = check(list, "crasharm_bounce.txt");
        assertTrue(failures.isEmpty(), failures.size() + " von " + list.size() + " Abprallern mit Gas 0 und Vorwärtstempo werfen die Drohne "
                + "mit Schub weiter oder höher (" + limits() + "):\n" + String.join("\n", failures));
    }

    @Test
    void throttleAfterABounceWakesTheController() {
        Scenario s = new Scenario(DroneBuild.FREESTYLE, StabMode.ANGLE, true, 0.3 * AirframeSpec.of(DroneBuild.FREESTYLE).impactTolerance, 8.0,
                135.0, 45.0);
        BoxWorld w = ArmedCrashRestTest.airborne(new ArmedCrashRestTest.Scenario(s.build, s.mode, s.airmode, 0.0,
                ArmedCrashRestTest.Impact.FLOOR, s.tiltDeg, s.azimuthDeg));
        RigidBody b = w.sim.body;
        double y = 0.25 + w.sim.params().hitboxWidth - w.foot;
        w.y = y;
        w.sim.teleport(w.x, y, w.z, true);
        ArmedCrashRestTest.orient(b, s.tiltDeg, s.azimuthDeg);
        b.vx = s.forward;
        b.vy = -s.sink;
        b.setBodyRates(0.0, 0.0, 0.0);
        w.input.throttle = 0.0;
        double t = 0.0;
        while (t < 3.0 && w.sim.telemetry().impactCount == 0 && !w.sim.telemetry().grounded) {
            w.frame(DT);
            t += DT;
        }
        for (int f = 0; f < (int) Math.round(0.4 * FPS); f++) {
            w.frame(DT);
        }
        w.input.throttle = 0.5;
        double strongest = 0.0;
        for (int f = 0; f < (int) Math.round(0.2 * FPS); f++) {
            w.frame(DT);
            strongest = Math.max(strongest, w.sim.telemetry().thrustToWeight);
        }
        assertTrue(w.sim.telemetry().armed, "nach dem Abpraller nicht mehr scharf");
        assertTrue(strongest > 1.0, String.format(Locale.ROOT, "Gas nach dem Abpraller weckt den Regler nicht: Schub höchstens %.2f × Gewicht",
                strongest));
    }
}
