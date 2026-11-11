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

class ArmedCrashWallSweepTest {
    static final double FPS = ArmedCrashRestTest.FPS;
    static final double DT = ArmedCrashRestTest.DT;
    static final double MEASURE_S = 5.0;
    static final double TWITCH_S = 0.3;
    static final double NEW_IMPACT_GAP_S = 0.05;
    static final double MEMORY_S = 0.3;
    static final double THRUST_MEAN_S = 0.05;
    static final double SETTLED_S = 1.0;
    static final double LATE_S = 2.0;
    static final double MAX_THRUST = 0.2;
    static final double MAX_ITERM = 0.05;
    static final double MAX_TRAVEL = 1.0;
    static final double WALL_GAP = 0.6;
    static final double START_HEIGHT = 0.6;
    static final double[] SPEEDS = {0.5, 1.0, 2.0, 3.0, 4.0, 6.0};
    static final double[] TILTS = {0.0, 6.0, 12.0, 18.0, 24.0};
    static final double AXIS_STEP = 45.0;
    static final double TAP_S = 0.1;
    static final StabMode[] MODES = {StabMode.ACRO, StabMode.ANGLE, StabMode.HORIZON};

    enum Rules {
        ALL_OFF("crash_disarm, crash_damage, prop_strikes aus, battery_scale 0"),
        DISARM_OFF("nur crash_disarm aus"),
        DISARM_AND_STRIKES_OFF("crash_disarm und prop_strikes aus"),
        DISARM_AND_DAMAGE_OFF("crash_disarm und crash_damage aus");

        final String label;

        Rules(String label) {
            this.label = label;
        }

        SimTuning tuning() {
            RuleValues v = RuleValues.DEFAULTS.with(WorldRuleSpecs.CRASH_DISARM, 0);
            v = switch (this) {
                case ALL_OFF -> v.with(WorldRuleSpecs.CRASH_DAMAGE, 0).with(WorldRuleSpecs.PROP_STRIKES, 0).with(WorldRuleSpecs.BATTERY_SCALE, 0);
                case DISARM_AND_STRIKES_OFF -> v.with(WorldRuleSpecs.PROP_STRIKES, 0);
                case DISARM_AND_DAMAGE_OFF -> v.with(WorldRuleSpecs.CRASH_DAMAGE, 0);
                case DISARM_OFF -> v;
            };
            return PhysicsRules.of(v).toSimTuning();
        }
    }

    record Scenario(DroneBuild build, StabMode mode, Rules rules, double speed, double tiltDeg, double axisDeg) {
        String key() {
            return String.format(Locale.ROOT, "%s %s, %s, %.1f m/s gegen die Wand, %.0f° Neigung, Achse %.0f°", this.build.frame().key(), this.mode,
                    this.rules.label, this.speed, this.tiltDeg, this.axisDeg);
        }
    }

    static final class Result {
        boolean touched;
        boolean armed;
        double contactThrust;
        double contactThrustAt;
        double contactIterm;
        double lateThrust;
        double lateIterm;
        double travel;
        double finalTilt;

        boolean ok() {
            return this.touched && this.armed && this.contactThrust <= MAX_THRUST && this.contactIterm <= MAX_ITERM && this.lateThrust <= MAX_THRUST
                    && this.lateIterm <= MAX_ITERM && this.travel <= MAX_TRAVEL;
        }

        String row() {
            return String.format(Locale.ROOT, "am Block ab %.1f s nach dem Aufprall Schub bis %.2f × Gewicht (bei %.2f s) und I-Anteil bis %.3f; "
                            + "letzte %.0f s Schub %.2f × Gewicht, I-Anteil %.3f; Weg ab %.0f s %.2f Blöcke; Endlage %.0f°; scharf %s", TWITCH_S,
                    this.contactThrust, this.contactThrustAt, this.contactIterm, LATE_S, this.lateThrust, this.lateIterm, SETTLED_S, this.travel,
                    this.finalTilt, this.armed);
        }
    }

    static BoxWorld atTheWall(DroneBuild build, StabMode mode, SimTuning tuning, double speed, double tiltDeg, double axisDeg) {
        BoxWorld w = ArmedCrashRulesOffTest.airborne(new ArmedCrashRulesOffTest.Scenario(build, mode, true, 0.0,
                ArmedCrashRulesOffTest.Impact.WALL_SOFT, 0.0, 0.0), tuning);
        RigidBody b = w.sim.body;
        double y = START_HEIGHT - w.foot;
        w.y = y;
        w.sim.teleport(w.x, y, w.z, true);
        w.box(w.x + WALL_GAP, 0.0, w.z - 15.0, w.x + WALL_GAP + 1.0, 3.0, w.z + 15.0);
        ArmedCrashRestTest.orient(b, tiltDeg, axisDeg);
        b.vx = speed;
        b.vy = 0.0;
        b.vz = 0.0;
        b.setBodyRates(0.0, 0.0, 0.0);
        w.input.roll = 0.0;
        w.input.pitch = 0.0;
        w.input.yaw = 0.0;
        w.input.throttle = 0.0;
        w.input.armSwitch = true;
        return w;
    }

    static Result run(Scenario s) {
        BoxWorld w = atTheWall(s.build, s.mode, s.rules.tuning(), s.speed, s.tiltDeg, s.axisDeg);
        RigidBody b = w.sim.body;
        Result r = new Result();
        int window = (int) Math.round(THRUST_MEAN_S * FPS);
        double[] recent = new double[window];
        double recentSum = 0.0;
        int filled = 0;
        double touch = Double.NaN;
        double lastContact = Double.NEGATIVE_INFINITY;
        double impact = Double.NaN;
        int impacts = 0;
        int crashes = 0;
        double settledX = Double.NaN;
        double settledZ = Double.NaN;
        double lateSum = 0.0;
        int lateFrames = 0;
        double t = 0.0;
        while (t < 3.0 + MEASURE_S) {
            w.frame(DT);
            t += DT;
            Telemetry tel = w.sim.telemetry();
            boolean contact = w.sim.fc.contact || tel.impactCount > impacts || tel.crashCount > crashes;
            impacts = tel.impactCount;
            crashes = tel.crashCount;
            if (Double.isNaN(touch) && (contact || tel.grounded)) {
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
            int slot = filled % window;
            recentSum += tel.thrustToWeight - recent[slot];
            recent[slot] = tel.thrustToWeight;
            filled++;
            if (contact) {
                if (t - lastContact > NEW_IMPACT_GAP_S) {
                    impact = t;
                }
                lastContact = t;
            }
            if (t - lastContact <= MEMORY_S && t - impact >= TWITCH_S) {
                double thrust = recentSum / Math.min(filled, window);
                if (thrust > r.contactThrust) {
                    r.contactThrust = thrust;
                    r.contactThrustAt = since;
                }
                r.contactIterm = Math.max(r.contactIterm, ArmedCrashRestTest.iterm(w.sim));
            }
            if (since >= SETTLED_S) {
                if (Double.isNaN(settledX)) {
                    settledX = w.x;
                    settledZ = w.z;
                }
                r.travel = Math.max(r.travel, Math.hypot(w.x - settledX, w.z - settledZ));
            }
            if (since >= MEASURE_S - LATE_S) {
                lateSum += tel.thrustToWeight;
                lateFrames++;
                r.lateIterm = Math.max(r.lateIterm, ArmedCrashRestTest.iterm(w.sim));
            }
        }
        r.lateThrust = lateFrames == 0 ? Double.NaN : lateSum / lateFrames;
        r.finalTilt = w.sim.telemetry().tiltDeg;
        r.armed = w.sim.telemetry().armed;
        return r;
    }

    static List<Scenario> sweep(Rules rules) {
        List<Scenario> list = new ArrayList<>();
        for (DroneBuild build : DroneBuild.PRESETS) {
            for (StabMode mode : MODES) {
                for (double speed : SPEEDS) {
                    for (double tilt : TILTS) {
                        for (double axis = 0.0; axis < 360.0; axis += AXIS_STEP) {
                            list.add(new Scenario(build, mode, rules, speed, tilt, axis));
                        }
                    }
                }
            }
        }
        return list;
    }

    static void check(Rules rules) {
        List<Scenario> list = sweep(rules);
        Map<Scenario, Result> results = new ConcurrentHashMap<>();
        list.parallelStream().forEach(s -> results.put(s, run(s)));
        StringBuilder report = new StringBuilder();
        List<String> failures = new ArrayList<>();
        for (Scenario s : list) {
            Result r = results.get(s);
            report.append(s.key()).append(": ").append(r.row()).append('\n');
            if (!r.ok()) {
                failures.add(s.key() + ": " + r.row());
            }
        }
        CalibrationHarness.write("crasharm_wall_" + rules.name().toLowerCase(Locale.ROOT) + ".txt", report.toString());
        assertTrue(failures.isEmpty(), failures.size() + " von " + list.size() + " fast aufrechten Wandaufprallen mit Gas 0 (" + rules.label
                + ") bauen am Block Schub auf (erlaubt: ab " + TWITCH_S + " s nach jedem Aufprall, solange der letzte Kontakt höchstens " + MEMORY_S
                + " s her ist, Schub im Mittel über " + THRUST_MEAN_S + " s höchstens " + MAX_THRUST + " × Gewicht und I-Anteil höchstens "
                + MAX_ITERM + "; in den letzten " + LATE_S + " s dasselbe; Weg ab " + SETTLED_S + " s höchstens " + MAX_TRAVEL
                + " Block; weiter scharf):\n" + String.join("\n", failures));
    }

    @Test
    void wallCrashAtIdleWithAllCrashRulesOffBuildsNoThrust() {
        check(Rules.ALL_OFF);
    }

    @Test
    void wallCrashAtIdleWithOnlyCrashDisarmOffBuildsNoThrust() {
        check(Rules.DISARM_OFF);
    }

    @Test
    void wallCrashAtIdleWithCrashDisarmAndPropStrikesOffBuildsNoThrust() {
        check(Rules.DISARM_AND_STRIKES_OFF);
    }

    @Test
    void wallCrashAtIdleWithCrashDisarmAndDamageOffBuildsNoThrust() {
        check(Rules.DISARM_AND_DAMAGE_OFF);
    }

    @Test
    void takeoffAfterRestingArmedOnTheGroundIsUnchanged() {
        List<String> failures = new ArrayList<>();
        for (DroneBuild build : DroneBuild.PRESETS) {
            double[] rested = takeoff(build, 1.5);
            double[] quick = takeoff(build, 0.05);
            if (Math.abs(rested[0] - quick[0]) > 0.02 || Math.abs(rested[1] - quick[1]) > 1.0 || rested[0] < 0.5) {
                failures.add(String.format(Locale.ROOT, "%s: nach 1,5 s scharf am Boden %.3f m hoch und %.1f° geneigt, nach 0,05 s %.3f m und %.1f°",
                        build.frame().key(), rested[0], rested[1], quick[0], quick[1]));
            }
        }
        assertTrue(failures.isEmpty(), "Abheben mit Gas nach dem Warten im Leerlauf am Boden weicht ab (erlaubt: 2 cm Höhe, 1° Neigung):\n"
                + String.join("\n", failures));
    }

    static double[] takeoff(DroneBuild build, double restS) {
        FlightControllerConfig config = new FlightControllerConfig();
        BoxWorld w = new BoxWorld(new DroneSim(build, config, ArmedCrashRestTest.tuning(), 42L));
        w.ground(0.0);
        w.placeBottom(0.0, 0.0, 0.0, 0.0);
        w.sim.setBatteryCharge(0.8);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        w.input.throttle = 0.0;
        for (int f = 0; f < (int) Math.round(restS * FPS); f++) {
            w.frame(DT);
        }
        double hover = w.sim.telemetry().hoverThrottleStick;
        double tilt = 0.0;
        for (int f = 0; f < (int) Math.round(1.5 * FPS); f++) {
            w.input.throttle = Math.min(hover + 0.15, (f + 1) / FPS);
            w.frame(DT);
            tilt = Math.max(tilt, w.sim.telemetry().tiltDeg);
        }
        return new double[]{w.y + w.foot, tilt};
    }

    @Test
    void wallTapAtIdleKeepsTheControllerAwake() {
        List<String> failures = new ArrayList<>();
        int taps = 0;
        for (DroneBuild build : DroneBuild.PRESETS) {
            BoxWorld w = ArmedCrashRulesOffTest.airborne(new ArmedCrashRulesOffTest.Scenario(build, StabMode.ACRO, true, 0.0,
                    ArmedCrashRulesOffTest.Impact.WALL_SOFT, 0.0, 0.0), ArmedCrashRulesOffTest.rulesOff());
            RigidBody b = w.sim.body;
            double reach = w.halfWidth + 0.05;
            w.box(w.x + reach, w.y - 3.0, w.z - 5.0, w.x + reach + 1.0, w.y + 3.0, w.z + 5.0);
            b.vx = 0.8;
            b.vy = 0.0;
            b.vz = 0.0;
            b.setBodyRates(0.0, 0.0, 0.0);
            w.input.throttle = 0.0;
            double touched = 0.0;
            double weakest = 1.0;
            boolean knocked = false;
            for (int f = 0; f < (int) Math.round(0.4 * FPS); f++) {
                w.frame(DT);
                if (w.sim.fc.contact) {
                    touched += DT;
                }
                knocked |= w.sim.fc.knocked;
                if (!knocked) {
                    weakest = Math.min(weakest, w.sim.fc.contactAuthority);
                }
            }
            if (touched <= 0.0) {
                continue;
            }
            taps++;
            if (touched < TAP_S && weakest < 1.0) {
                failures.add(String.format(Locale.ROOT, "%s: %.3f s Kontakt, Regler-Wirkung bis %.2f", build.frame().key(), touched, weakest));
            }
        }
        assertTrue(taps > 0, "kein Preset berührt die Wand");
        assertTrue(failures.isEmpty(), "kurzes Antippen der Wand mit Gas 0 schwächt den Regler, ohne dass die Drohne abgeräumt ist:\n" + String.join("\n", failures));
    }
}
