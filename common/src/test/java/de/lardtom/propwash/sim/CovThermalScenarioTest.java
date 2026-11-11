package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CovThermalScenarioTest {
    static final double FPS = 100.0;
    static final double DT = 1.0 / FPS;
    static final double ALTITUDE = 60.0;
    static final double SHARE = 0.9;
    static final double MIN_SHARE = 0.6;
    static final double SHARE_STEP = 0.01;
    static final double GRAVITY_MAX_PERCENT = 1000.0;
    static final double PROBE_S = 60.0;
    static final double HOLD_TOLERANCE_M = 2.0;
    static final double MIN_HOVER_THROTTLE = 0.85;
    static final double SETTLE_S = 5.0;
    static final double FULL_THROTTLE = 0.95;
    static final double GROUND_HOVER_M = 10.0;
    static final double HOT_EARLIEST_S = 90.0;
    static final double HOT_LATEST_S = 150.0;
    static final DroneBuild LIPO_SEVEN = DroneBuild.LONG_RANGE.withBattery(BatteryType.B6S_1300);
    static final String OUT_FILE = "cov_flight_thermal.txt";

    record Flight(String name, double gravityPercent, double seconds, double escDerateAt, double motorHotAt, double minLimit, double minTorque,
                  double heightAtDerate, double finalHeight, double minHeight, double lateThrottle, double maxEscC, double maxMotorC,
                  double hoverThrottle) {
        String line() {
            return String.format(Locale.ROOT, "%-34s Schwerkraft %4.0f %%, Schwebegas %3.0f %%, %4.0f s: ESC regelt ab nach %s, Motor heiß nach %s, "
                            + "Grenze min %.2f, Drehmoment min %.2f, ESC max %.0f °C, Motor max %.0f °C, Höhe bei Abregelung %s, Ende %+.1f m, "
                            + "tiefste %+.1f m, Gas zuletzt %.0f %%", this.name, this.gravityPercent, 100.0 * this.hoverThrottle, this.seconds,
                    time(this.escDerateAt), time(this.motorHotAt), this.minLimit, this.minTorque, this.maxEscC, this.maxMotorC,
                    Double.isFinite(this.heightAtDerate) ? String.format(Locale.ROOT, "%+.1f m", this.heightAtDerate) : "–", this.finalHeight,
                    this.minHeight, 100.0 * this.lateThrottle);
        }
    }

    static String time(double seconds) {
        return Double.isFinite(seconds) ? String.format(Locale.ROOT, "%.0f s", seconds) : "nie";
    }

    static double gravityPercent(DroneBuild build, double share) {
        return Math.min(GRAVITY_MAX_PERCENT, Math.round(share * BuildAnalysis.of(build).thrustToWeight() * 100.0));
    }

    static boolean hovers(Flight flight) {
        return flight.minHeight() > -HOLD_TOLERANCE_M && flight.hoverThrottle() >= MIN_HOVER_THROTTLE;
    }

    static double hoverableGravity(DroneBuild build) {
        for (double share = SHARE; share >= MIN_SHARE - 1.0E-9; share -= SHARE_STEP) {
            double gravity = gravityPercent(build, share);
            if (hovers(hoverAt("Suche", build, gravity, PROBE_S))) {
                return gravity;
            }
        }
        return Double.NaN;
    }

    static double takeOffGravity(DroneBuild build) {
        for (double share = SHARE; share >= MIN_SHARE - 1.0E-9; share -= SHARE_STEP) {
            double gravity = gravityPercent(build, share);
            if (hovers(hoverAt("Suche", build, gravity, PROBE_S, true))) {
                return gravity;
            }
        }
        return Double.NaN;
    }

    static Flight hoverNearFullThrottle(String name, DroneBuild build, double seconds) {
        double gravity = hoverableGravity(build);
        assertTrue(Double.isFinite(gravity), name + ": keine Schwerkraft zwischen " + MIN_SHARE + " und " + SHARE + " × S/G zum Schweben");
        return hoverAt(name, build, gravity, seconds);
    }

    static String recipe(DroneBuild build) {
        return recipe(build, false);
    }

    static String recipe(DroneBuild build, boolean fromGround) {
        double gravity = gravityPercent(build, SHARE);
        double wanted = Math.round(SHARE * BuildAnalysis.of(build).thrustToWeight() * 100.0);
        Flight probe = hoverAt("Prüfplan", build, gravity, PROBE_S, fromGround);
        String limit = wanted > gravity ? String.format(Locale.ROOT, " (Regelgrenze %.0f %%)", gravity) : "";
        return String.format(Locale.ROOT, "S/G %.2f; %.2f × S/G = %.0f %% Schwerkraft%s%s: %s (Schwebegas %.0f %%, tiefste %+.1f m in %.0f s)",
                BuildAnalysis.of(build).thrustToWeight(), SHARE, wanted, limit, fromGround ? ", Start vom Boden" : "", hovers(probe) ? "schwebt" : "schwebt nicht",
                100.0 * probe.hoverThrottle(), probe.minHeight(), PROBE_S);
    }

    static Flight hoverAt(String name, DroneBuild build, double gravity, double seconds) {
        return hoverAt(name, build, gravity, seconds, false);
    }

    static Flight hoverAt(String name, DroneBuild build, double gravity, double seconds, boolean fromGround) {
        RuleValues rules = RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, (int) gravity).with(WorldRuleSpecs.BATTERY_SCALE, 0);
        SimTuning tuning = PhysicsRules.of(rules).toSimTuning();
        assertTrue(tuning.infiniteBattery, "Akkulaufzeit 0 % ist unendlich");
        assertTrue(tuning.thermalLimits, "Hardware-Grenzen an (Standard)");
        tuning.gyroNoiseDps = 0.0;
        tuning.gyroVibrationDps = 0.0;
        FlatWorld w = new FlatWorld(new DroneSim(build, new FlightControllerConfig(), tuning, 42L));
        double target = fromGround ? GROUND_HOVER_M : ALTITUDE;
        if (fromGround) {
            w.placeOnGround(0.0, 0.0, 0.0);
        } else {
            w.place(0.0, ALTITUDE, 0.0, 0.0);
        }
        w.sim.thermal.reset(BuildAnalysis.REFERENCE_AMBIENT_C);
        w.env.ambientTempC = BuildAnalysis.REFERENCE_AMBIENT_C;
        w.input.mode = StabMode.ANGLE;
        w.arm();
        AltitudeHold hold = new AltitudeHold(target);
        hold.integralLimit = 1.0;
        double escAt = Double.NaN;
        double hotAt = Double.NaN;
        double heightAtDerate = Double.NaN;
        double minLimit = 1.0;
        double minTorque = 1.0;
        double maxEsc = 0.0;
        double maxMotor = 0.0;
        double minHeight = Double.POSITIVE_INFINITY;
        double lateThrottle = 0.0;
        int lateFrames = 0;
        double hoverSum = 0.0;
        int hoverFrames = 0;
        int frames = (int) Math.round(seconds * FPS);
        for (int f = 1; f <= frames; f++) {
            hold.apply(w, DT);
            w.frame(DT);
            double t = f * DT;
            Telemetry tel = w.sim.telemetry();
            double height = w.sim.body.py - target;
            EventQueue events = w.sim.events();
            for (int e = 0; e < events.size(); e++) {
                if (events.value(e) > 0.5 && events.type(e) == SimEventType.ESC_DERATE && Double.isNaN(escAt)) {
                    escAt = t;
                    heightAtDerate = height;
                }
                if (events.value(e) > 0.5 && events.type(e) == SimEventType.MOTOR_HOT && Double.isNaN(hotAt)) {
                    hotAt = t;
                }
            }
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                minLimit = Math.min(minLimit, tel.motorPowerLimit[i]);
                minTorque = Math.min(minTorque, w.sim.thermal.torque[i]);
                maxEsc = Math.max(maxEsc, tel.escTempC[i]);
                maxMotor = Math.max(maxMotor, tel.motorTempC[i]);
            }
            if (t >= SETTLE_S) {
                minHeight = Math.min(minHeight, height);
            }
            if (t >= SETTLE_S && t < SETTLE_S + 10.0) {
                hoverSum += w.input.throttle;
                hoverFrames++;
            }
            if (t >= seconds - 10.0) {
                lateThrottle += w.input.throttle;
                lateFrames++;
            }
        }
        return new Flight(name, gravity, seconds, escAt, hotAt, minLimit, minTorque, heightAtDerate, w.sim.body.py - target, minHeight,
                lateFrames == 0 ? Double.NaN : lateThrottle / lateFrames, maxEsc, maxMotor, hoverFrames == 0 ? Double.NaN : hoverSum / hoverFrames);
    }

    @Test
    void cinewhoopHoverAtNinetyPercentDerates() {
        Flight flight = hoverNearFullThrottle("propwash:cinewhoop_3", DroneBuild.CINEWHOOP, 180.0);
        String line = flight.line() + "; Prüfplan-Rezept: " + recipe(DroneBuild.CINEWHOOP);
        System.out.println(line);
        assertTrue(flight.hoverThrottle() > 0.8, "fast Vollgas zum Schweben: " + line);
        assertTrue(flight.escDerateAt() >= 60.0 && flight.escDerateAt() <= 120.0, "ESC_DERATE nach 60–120 s: " + line);
        assertTrue(flight.minLimit() < 1.0, "Leistungsgrenze unter 1: " + line);
        assertTrue(flight.lateThrottle() >= FULL_THROTTLE, "Vollgas am Ende: " + line);
        assertTrue(flight.finalHeight() < flight.heightAtDerate() - 2.0 && flight.finalHeight() < -2.0, "Höhe sinkt trotz Vollgas: " + line);
    }

    @Test
    void longRangeLipoHoverMotorHotEscCool() {
        double gravity = takeOffGravity(LIPO_SEVEN);
        assertTrue(Double.isFinite(gravity), "propwash:long_range_7+b6s_1300: hebt zwischen " + MIN_SHARE + " und " + SHARE + " × S/G nicht ab");
        Flight flight = hoverAt("propwash:long_range_7+b6s_1300", LIPO_SEVEN, gravity, HOT_LATEST_S, true);
        String line = flight.line() + "; Prüfplan-Rezept: " + recipe(LIPO_SEVEN, true) + "; ohne Start vom Boden: " + recipe(LIPO_SEVEN);
        System.out.println(line);
        assertTrue(flight.hoverThrottle() > 0.8, "fast Vollgas zum Schweben: " + line);
        assertTrue(flight.motorHotAt() >= HOT_EARLIEST_S && flight.motorHotAt() <= HOT_LATEST_S, "MOTOR_HOT nach rund zwei Minuten: " + line);
        assertFalse(Double.isFinite(flight.escDerateAt()), "kein ESC_DERATE: " + line);
        assertEquals(1.0, flight.minLimit(), 1.0E-9, "keine Leistungsgrenze: " + line);
        assertTrue(flight.minTorque() >= 1.0 - Thermal.MAX_TORQUE_LOSS - 1.0E-9 && flight.minTorque() < 1.0, "Schubverlust höchstens 10 %: " + line);
        assertTrue(flight.minHeight() > -1.5, "kein Absacken: " + line);
    }

    record Band(String preset, double low, double high) {
    }

    @Test
    @Tag(CalibrationHarness.ADDON_TAG)
    void bigCoptersWithJustMorePartsDerateInTheirBands() {
        assumeTrue(CalibrationHarness.addonRoot().isPresent(), CalibrationHarness.ADDON_HINT);
        Map<String, Band> bands = Map.of(
                "justmoreparts:xclass10_6s", new Band("X-Class 10″", 40.0, 90.0),
                "justmoreparts:lr7_8s", new Band("7″ 8S", 90.0, 150.0),
                "justmoreparts:xclass12_8s", new Band("X-Class 12″", 180.0, Double.POSITIVE_INFINITY));
        List<String> lines = new ArrayList<>();
        List<String> missed = new ArrayList<>();
        for (CalibrationHarness.Entry e : CalibrationHarness.addons()) {
            Band band = bands.get(e.name());
            if (band == null) {
                continue;
            }
            Flight flight = hoverNearFullThrottle(e.name(), e.build(), 300.0);
            String recipe = recipe(e.build());
            double at = flight.escDerateAt();
            boolean met = Double.isFinite(band.high()) ? at >= band.low() && at <= band.high() : !(at < band.low());
            String verdict = met ? "erfüllt" : "verfehlt";
            lines.add(String.format(Locale.ROOT, "%-13s %-9s Band %s: %s", band.preset(), verdict,
                    Double.isFinite(band.high()) ? String.format(Locale.ROOT, "%.0f–%.0f s", band.low(), band.high())
                            : String.format(Locale.ROOT, "über %.0f s", band.low()), flight.line() + "; Prüfplan-Rezept: " + recipe));
            if (!met) {
                missed.add(band.preset() + " " + time(at));
            }
        }
        assertEquals(bands.size(), lines.size(), "alle drei Presets aus den Add-on-Daten: " + lines);
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT, "Zeit bis ESC_DERATE beim Schweben mit fast Vollgas (Schwerkraft: höchster Anteil von S/G ab %.0f %% in "
                + "Schritten von %.0f %%, bei dem die Drohne %.0f s lang die Höhe hält), Akkulaufzeit unendlich, Hardware-Grenzen an%n", SHARE * 100.0,
                SHARE_STEP * 100.0, PROBE_S));
        for (String line : lines) {
            text.append(line).append(System.lineSeparator());
        }
        CalibrationHarness.write(OUT_FILE, text.toString());
        System.out.print(text);
        assertEquals(List.of(), missed, "Zeitbänder für große Kopter (Prüfplan 1.6) verfehlt");
    }
}
