package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmedCrashRulesOffTest {
    static final double FPS = 144.0;
    static final double DT = 1.0 / FPS;
    static final double MEASURE_S = 5.0;
    static final double SETTLED_S = 1.0;
    static final double LATE_S = 2.0;
    static final double MAX_TRAVEL = 1.0;
    static final double MAX_LATE_THRUST = 0.2;
    static final double MAX_ITERM = 0.05;
    static final double LYING_DEG = 70.0;
    static final double LYING_CHECK_S = 1.0;
    static final double BLOCKED_THRUST_SHARE = 0.3;

    enum Impact {
        FLOOR_SOFT("Boden weich", 0.5, -2.0),
        FLOOR_HARD("Boden hart", 4.0, -9.0),
        WALL_SOFT("Wand weich", 3.0, 0.0),
        WALL_HARD("Wand hart", 12.0, -1.0);

        final String label;
        final double forward;
        final double down;

        Impact(String label, double forward, double down) {
            this.label = label;
            this.forward = forward;
            this.down = down;
        }

        boolean wall() {
            return this == WALL_SOFT || this == WALL_HARD;
        }
    }

    record Scenario(DroneBuild build, StabMode mode, boolean airmode, double throttle, Impact impact, double tiltDeg, double azimuthDeg) {
        String key() {
            return String.format(Locale.ROOT, "%s %s %s, Gas %.0f %%, %s, %.0f° Neigung, Achse %.0f°", this.build.frame().key(), this.mode,
                    this.airmode ? "Airmode" : "ohne Airmode", 100.0 * this.throttle, this.impact.label, this.tiltDeg, this.azimuthDeg);
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
        double maxSpeed;
        double finalTilt;

        String row() {
            return String.format(Locale.ROOT, "Weg nach %.0f s %.2f Blöcke, Schub/Gewicht am Ende %.2f, I-Anteil am Ende %.3f, liegend %.1f s mit "
                            + "I-Anteil bis %.3f, schnellstens %.1f m/s, Endlage %.0f°, scharf %s", SETTLED_S, this.travel, this.lateThrust,
                    this.lateIterm, this.lyingSeconds, this.lyingIterm, this.maxSpeed, this.finalTilt, this.armed);
        }
    }

    static RuleValues rulesOffValues() {
        return RuleValues.DEFAULTS.with(WorldRuleSpecs.CRASH_DISARM, 0).with(WorldRuleSpecs.CRASH_DAMAGE, 0)
                .with(WorldRuleSpecs.PROP_STRIKES, 0).with(WorldRuleSpecs.BATTERY_SCALE, 0);
    }

    static SimTuning rulesOff() {
        return PhysicsRules.of(rulesOffValues()).toSimTuning();
    }

    static BoxWorld airborne(Scenario s, SimTuning tuning) {
        FlightControllerConfig config = new FlightControllerConfig();
        config.pid.airmode = s.airmode;
        BoxWorld w = new BoxWorld(new DroneSim(s.build, config, tuning, 42L));
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

    static Result run(Scenario s, SimTuning tuning) {
        return run(s, tuning, true);
    }

    static Result run(Scenario s, SimTuning tuning, boolean armed) {
        BoxWorld w = airborne(s, tuning);
        RigidBody b = w.sim.body;
        Result r = new Result();
        if (s.impact.wall()) {
            double y = 0.6 - w.foot;
            w.y = y;
            w.sim.teleport(w.x, y, w.z, true);
            w.box(w.x + 0.6, 0.0, w.z - 5.0, w.x + 1.6, 3.0, w.z + 5.0);
        } else {
            double y = 0.25 + w.sim.params().hitboxWidth - w.foot;
            w.y = y;
            w.sim.teleport(w.x, y, w.z, true);
        }
        ArmedCrashRestTest.orient(b, s.tiltDeg, s.azimuthDeg);
        b.vx = s.impact.forward;
        b.vy = s.impact.down;
        b.vz = 0.0;
        b.setBodyRates(0.0, 0.0, 0.0);
        w.input.roll = 0.0;
        w.input.pitch = 0.0;
        w.input.yaw = 0.0;
        w.input.throttle = s.throttle;
        w.input.armSwitch = armed;
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
            lying = tel.grounded && tel.tiltDeg > LYING_DEG ? lying + DT : 0.0;
            r.lyingSeconds = Math.max(r.lyingSeconds, lying);
            if (lying > LYING_CHECK_S) {
                r.lyingIterm = Math.max(r.lyingIterm, ArmedCrashRestTest.iterm(w.sim));
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
                r.lateIterm = Math.max(r.lateIterm, ArmedCrashRestTest.iterm(w.sim));
            }
        }
        r.lateThrust = thrustFrames == 0 ? Double.NaN : thrustSum / thrustFrames;
        r.finalTilt = w.sim.telemetry().tiltDeg;
        r.armed = w.sim.telemetry().armed;
        return r;
    }

    static List<Scenario> scenarios(double throttle) {
        List<Scenario> list = new ArrayList<>();
        for (DroneBuild build : DroneBuild.PRESETS) {
            for (StabMode mode : new StabMode[]{StabMode.ACRO, StabMode.ANGLE}) {
                for (boolean airmode : new boolean[]{true, false}) {
                    for (Impact impact : Impact.values()) {
                        double[] tilts = impact.wall() ? new double[]{0.0, 30.0} : new double[]{60.0, 120.0, 180.0};
                        for (double tilt : tilts) {
                            for (double az : new double[]{0.0, 45.0}) {
                                list.add(new Scenario(build, mode, airmode, throttle, impact, tilt, az));
                            }
                        }
                    }
                }
            }
        }
        return list;
    }

    static Map<Scenario, Result> runAll(List<Scenario> list, SimTuning tuning) {
        return runAll(list, tuning, true);
    }

    static Map<Scenario, Result> runAll(List<Scenario> list, SimTuning tuning, boolean armed) {
        Map<Scenario, Result> out = new ConcurrentHashMap<>();
        list.parallelStream().forEach(s -> out.put(s, run(s, tuning.copy(), armed)));
        return out;
    }

    @Test
    void rulesOffReachTheSimulation() {
        SimTuning t = rulesOff();
        assertTrue(!t.crashDisarm && !t.propStrikes && t.infiniteBattery, "Crash-Disarm, Prop-Schläge und Akku kommen nicht in der Simulation an: "
                + "Disarm " + t.crashDisarm + ", Prop-Schläge " + t.propStrikes + ", Akku unendlich " + t.infiniteBattery);
    }

    @Test
    void armedCrashAtIdleThrottleComesToRestWithCrashRulesOff() {
        List<Scenario> list = scenarios(0.0);
        Map<Scenario, Result> results = runAll(list, rulesOff());
        List<Scenario> hard = list.stream().filter(s -> s.impact == Impact.FLOOR_HARD).toList();
        Map<Scenario, Result> unarmed = runAll(hard, rulesOff(), false);
        StringBuilder report = new StringBuilder();
        List<String> failures = new ArrayList<>();
        for (Scenario s : list) {
            Result r = results.get(s);
            report.append(s.key()).append(": ").append(r.row());
            Result off = unarmed.get(s);
            if (off != null) {
                report.append(String.format(Locale.ROOT, ", unscharf %.2f Blöcke", off.travel));
            }
            report.append('\n');
            boolean travelChecked = s.impact != Impact.FLOOR_HARD;
            if (!r.touched || !r.armed) {
                failures.add(s.key() + ": kein Aufprall oder nicht mehr scharf");
            } else if (travelChecked && r.travel > MAX_TRAVEL || r.lateThrust > MAX_LATE_THRUST || r.lateIterm > MAX_ITERM) {
                failures.add(s.key() + ": " + r.row());
            }
        }
        CalibrationHarness.write("crasharm_rules_off_idle.txt", report.toString());
        assertTrue(failures.isEmpty(), failures.size() + " von " + list.size() + " Abstürzen mit Gas 0 ohne Crash-Disarm, Schaden und "
                + "Prop-Schläge, Akku unendlich, kommen nicht zur Ruhe (erlaubt: höchstens " + MAX_TRAVEL + " Block Weg nach " + SETTLED_S
                + " s außer nach hartem Aufprall auf den Boden, Schub/Gewicht in den letzten " + LATE_S + " s höchstens " + MAX_LATE_THRUST
                + ", I-Anteil höchstens " + MAX_ITERM + "):\n" + String.join("\n", failures));
    }

    @Test
    void lyingDroneWithThrottleDoesNotWindUpWithCrashRulesOff() {
        List<Scenario> list = scenarios(0.3);
        Map<Scenario, Result> results = runAll(list, rulesOff());
        StringBuilder report = new StringBuilder();
        List<String> failures = new ArrayList<>();
        int lying = 0;
        for (Scenario s : list) {
            Result r = results.get(s);
            report.append(s.key()).append(": ").append(r.touched ? r.row() : "schlägt mit 30 % Gas nicht auf").append('\n');
            if (!r.touched) {
                continue;
            }
            if (!r.armed) {
                failures.add(s.key() + ": nicht mehr scharf");
                continue;
            }
            if (r.lyingSeconds > LYING_CHECK_S) {
                lying++;
                if (r.lyingIterm > MAX_ITERM) {
                    failures.add(s.key() + ": " + r.row());
                }
            }
        }
        CalibrationHarness.write("crasharm_rules_off_throttle.txt", report.toString());
        assertTrue(lying >= list.size() / 8, "zu wenige Fälle liegen länger als " + LYING_CHECK_S + " s: " + lying + " von " + list.size());
        assertTrue(failures.isEmpty(), failures.size() + " von " + lying + " liegenden Drohnen wickeln mit 30 % Gas ohne Crash-Disarm, Schaden "
                + "und Prop-Schläge den I-Anteil auf:\n" + String.join("\n", failures));
    }

    static double[] thrustOnTheSide(DroneBuild build, double floor, double[] tips, SimTuning tuning) {
        Scenario s = new Scenario(build, StabMode.ACRO, true, 0.0, Impact.FLOOR_SOFT, 90.0, 0.0);
        BoxWorld w = airborne(s, tuning);
        w.boxes.clear();
        w.ground(floor);
        RigidBody b = w.sim.body;
        ArmedCrashRestTest.orient(b, 90.0, 0.0);
        AirframeSpec p = w.sim.params();
        double lowest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            lowest = Math.min(lowest, b.r10 * p.hullX(i) + b.r11 * p.hullY(i) + b.r12 * p.hullZ(i));
        }
        double y = ArmedCrashRestTest.SIDE_CLEARANCE - lowest;
        w.y = y;
        w.sim.teleport(w.x, y, w.z, false);
        ArmedCrashRestTest.orient(b, 90.0, 0.0);
        b.setBodyRates(0.0, 0.0, 0.0);
        w.sim.setDutyOverride(ArmedCrashRestTest.SIDE_DUTY, ArmedCrashRestTest.SIDE_DUTY, ArmedCrashRestTest.SIDE_DUTY,
                ArmedCrashRestTest.SIDE_DUTY);
        for (int f = 0; f < ArmedCrashRestTest.SIDE_FRAMES; f++) {
            w.frame(DT);
        }
        double[] thrust = new double[AirframeSpec.MOTORS];
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            thrust[i] = w.sim.thrust[i];
            if (tips != null) {
                tips[i] = ArmedCrashRestTest.tipHeight(w, i) - floor;
            }
        }
        return thrust;
    }

    @Test
    void propsTouchingTheFloorLoseTheirThrustWithPropStrikesOff() {
        SimTuning tuning = rulesOff();
        List<String> failures = new ArrayList<>();
        int checks = 0;
        for (DroneBuild build : new DroneBuild[]{DroneBuild.FREESTYLE, DroneBuild.RACER, DroneBuild.LONG_RANGE}) {
            double[] tips = new double[AirframeSpec.MOTORS];
            double[] onFloor = thrustOnTheSide(build, 0.0, tips, tuning.copy());
            double[] free = thrustOnTheSide(build, -50.0, null, tuning.copy());
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                if (tips[i] > ArmedCrashRestTest.TIP_CONTACT || !(free[i] > 0.0)) {
                    continue;
                }
                checks++;
                double share = onFloor[i] / free[i];
                if (share > BLOCKED_THRUST_SHARE) {
                    failures.add(String.format(Locale.ROOT, "%s auf der Seite ohne Prop-Schläge, Motor %d mit Prop %.1f mm über dem Boden: %.0f %% "
                            + "des Schubs in der Luft", build.frame().key(), i + 1, 1000.0 * tips[i], 100.0 * share));
                }
            }
        }
        assertTrue(checks > 0, "keine Prop mit Bodenkontakt gesehen");
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }
}
