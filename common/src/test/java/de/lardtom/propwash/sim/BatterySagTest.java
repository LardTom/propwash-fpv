package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatterySagTest {
    static final long FRAME = 10_000_000L;
    static final DroneBuild[] PRESETS = {DroneBuild.WHOOP, DroneBuild.CINEWHOOP, DroneBuild.FREESTYLE, DroneBuild.RACER, DroneBuild.LONG_RANGE};

    record Punch(double before, double[] cell, double[] thrust, double[] current, double[] after, boolean armed) {
        double sag(double seconds) {
            return this.before - this.cell[index(seconds)];
        }

        double missing(double seconds) {
            return this.before - this.after[index(seconds)];
        }

        static int index(double seconds) {
            return (int) Math.round(seconds * 100.0) - 1;
        }
    }

    static Punch punch(DroneBuild build, double charge, double hoverStick, double punchSeconds) {
        return punch(build, charge, hoverStick, punchSeconds, new SimTuning());
    }

    static Punch punch(DroneBuild build, double charge, double hoverStick, double punchSeconds, SimTuning tuning) {
        DroneSim sim = new DroneSim(build, new FlightControllerConfig(), tuning, 7L);
        PilotInput input = new PilotInput();
        SimEnvironment env = new SimEnvironment();
        StepResult result = new StepResult();
        sim.reset(0.0, 200.0, 0.0, 0.0);
        sim.setBatteryCharge(charge);
        sim.step(0L, input, env, result);
        input.armSwitch = true;
        sim.step(0L, input, env, result);
        assertTrue(sim.telemetry().armed, "nicht scharf bei Ladung " + charge);
        input.throttle = hoverStick;
        for (int k = 0; k < 300; k++) {
            sim.step(FRAME, input, env, result);
        }
        double before = sim.telemetry().batteryCellVoltage;
        int steps = (int) Math.round(punchSeconds * 100.0);
        double[] cell = new double[steps];
        double[] thrust = new double[steps];
        double[] current = new double[steps];
        input.throttle = 1.0;
        for (int k = 0; k < steps; k++) {
            sim.step(FRAME, input, env, result);
            Telemetry t = sim.telemetry();
            cell[k] = t.batteryCellVoltage;
            thrust[k] = t.totalThrustN;
            current[k] = t.batteryCurrentA;
        }
        boolean armed = sim.telemetry().armed;
        input.throttle = hoverStick;
        double[] after = new double[1500];
        for (int k = 0; k < after.length; k++) {
            sim.step(FRAME, input, env, result);
            after[k] = sim.telemetry().batteryCellVoltage;
        }
        return new Punch(before, cell, thrust, current, after, armed);
    }

    static String text(String name, double value) {
        return String.format(Locale.ROOT, "%s %.3f", name, value);
    }

    @Test
    void report() {
        double[] hover = {0.5, 0.35, 0.25, 0.22, 0.3};
        double[] charges = {1.0, 0.5, 0.2, 0.15, 0.1, 0.07, 0.05};
        StringBuilder out = new StringBuilder("Vollgas aus dem Schweben im freien Flug: Einbruch je Zelle nach 0,1 / 0,5 / 1,5 s, Strom und Schub nach "
                + "0,5 s, danach fehlen nach 0,1 / 1 / 5 s noch\n");
        for (int k = 0; k < PRESETS.length; k++) {
            for (double charge : charges) {
                Punch p = punch(PRESETS[k], charge, hover[k], 1.5);
                if (!p.armed()) {
                    out.append(String.format(Locale.ROOT, "%-13s %3.0f %%  vorher %.2f V  schaltet im Punch ab (Unterspannung)%n", PRESETS[k].frame().name(),
                            charge * 100.0, p.before()));
                    continue;
                }
                out.append(String.format(Locale.ROOT, "%-13s %3.0f %%  vorher %.2f V  Einbruch %.2f / %.2f / %.2f V  %5.1f A  %5.1f N  danach %.2f / %.2f / %.2f V%n",
                        PRESETS[k].frame().name(), charge * 100.0, p.before(), p.sag(0.1), p.sag(0.5), p.sag(1.5), p.current()[49], p.thrust()[49],
                        p.missing(0.1), p.missing(1.0), p.missing(5.0)));
            }
        }
        for (DroneBuild build : PRESETS) {
            FlatWorld w = Tests.hovering(build, Tests.quietTuning(), 20.0, StabMode.ANGLE, 1.0);
            w.sim.setBatteryCharge(1.0);
            double v0 = w.sim.body.vy;
            w.input.throttle = 1.0;
            w.run(1.0, 144.0);
            out.append(String.format(Locale.ROOT, "Punch %-13s nach 1 s %.2f m/s, Spitze %.2f g%n", build.frame().name(), w.sim.body.vy - v0,
                    w.sim.telemetry().gLoadMax));
        }
        CalibrationHarness.write("battery_sag.txt", out.toString());
    }

    @Test
    void lowPackSagsMuchMoreThanAFreshOne() {
        Punch fresh = punch(DroneBuild.FREESTYLE, 1.0, 0.25, 1.0);
        Punch half = punch(DroneBuild.FREESTYLE, 0.5, 0.25, 1.0);
        Punch low = punch(DroneBuild.FREESTYLE, 0.1, 0.25, 1.0);
        Punch empty = punch(DroneBuild.FREESTYLE, 0.05, 0.25, 1.0);
        assertTrue(fresh.sag(0.3) > 0.3 && fresh.sag(0.3) < 0.52, text("voller Akku", fresh.sag(0.3)));
        assertTrue(half.sag(0.3) > 0.25 && half.sag(0.3) < 0.47, text("halber Akku", half.sag(0.3)));
        assertTrue(low.sag(0.5) > 0.5 && low.sag(0.5) < 0.8, text("fast leerer Akku", low.sag(0.5)));
        assertTrue(low.sag(0.5) > 1.35 * half.sag(0.5), text("fast leer", low.sag(0.5)) + text(" gegen halb", half.sag(0.5)));
        assertTrue(empty.sag(0.5) > low.sag(0.5), text("leer", empty.sag(0.5)) + text(" gegen fast leer", low.sag(0.5)));
        assertTrue(low.current()[49] < half.current()[49], "leerer Akku liefert mehr Strom als ein halber");
    }

    static double bench(PowerSystem power, double amps, double seconds) {
        for (int n = 0; n < Math.round(seconds * DroneSim.PHYSICS_HZ); n++) {
            power.forceBatteryCurrent(amps, DroneSim.H, true, true);
        }
        return power.busVoltage;
    }

    @Test
    void sagBuildsUpDuringThePunchAndComesBackInTwoStages() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        PowerSystem power = new PowerSystem();
        power.configure(p, DroneSim.H);
        power.setStateOfCharge(0.1);
        double before = bench(power, 5.0, 60.0);
        double first = before - bench(power, 60.0, 0.02);
        double built = before - bench(power, 60.0, 1.48);
        assertTrue(built > 1.3 * first, text("nach 1,5 s", built) + text(" im ersten Moment", first));
        assertTrue(built / p.cells > 0.4 && built / p.cells < 0.8, text("Einbruch je Zelle", built / p.cells));
        double instant = before - bench(power, 5.0, 0.02);
        assertTrue(instant > 0.25 * built && instant < 0.6 * built, text("gleich nach dem Loslassen fehlen", instant) + text(" von", built));
        double quick = before - bench(power, 5.0, 1.98);
        assertTrue(quick < 0.4 * instant, text("nach 2 s fehlen", quick));
        assertTrue(quick > 0.03 * built, text("langsamer Anteil fehlt, nach 2 s fehlen", quick));
        double late = before - bench(power, 5.0, 18.0);
        assertTrue(late < 0.2 * quick, text("nach 20 s fehlen", late));
    }

    @Test
    void punchOutsGetWeakerAsThePackEmpties() {
        Punch fresh = punch(DroneBuild.FREESTYLE, 1.0, 0.25, 1.0);
        Punch half = punch(DroneBuild.FREESTYLE, 0.5, 0.25, 1.0);
        Punch low = punch(DroneBuild.FREESTYLE, 0.1, 0.25, 1.0);
        double a = fresh.thrust()[29];
        double b = half.thrust()[29];
        double c = low.thrust()[29];
        assertTrue(b < 0.95 * a && b > 0.8 * a, text("halb/voll", b / a));
        assertTrue(c < 0.8 * a && c > 0.5 * a, text("fast leer/voll", c / a));
    }

    @Test
    void everyPresetSagsMoreOnALowPackAndKeepsFlying() {
        double[] hover = {0.5, 0.35, 0.25, 0.22, 0.3};
        double[] charge = {0.1, 0.08, 0.08, 0.08, 0.06};
        for (int k = 0; k < PRESETS.length; k++) {
            String name = PRESETS[k].frame().name();
            Punch half = punch(PRESETS[k], 0.5, hover[k], 0.8);
            Punch low = punch(PRESETS[k], charge[k], hover[k], 0.8);
            assertTrue(half.armed() && low.armed(), name + " schaltet im Punch ab");
            assertTrue(low.sag(0.5) > 1.15 * half.sag(0.5), name + text(" fast leer", low.sag(0.5)) + text(" halb", half.sag(0.5)));
            assertTrue(low.thrust()[49] < half.thrust()[49], name + " Schub");
        }
    }

    @Test
    void withoutTheSagRuleALowPackStaysAsStiffAsAFullOne() {
        SimTuning off = new SimTuning();
        off.batterySag = false;
        Punch full = punch(DroneBuild.FREESTYLE, 1.0, 0.25, 1.0, off);
        Punch low = punch(DroneBuild.FREESTYLE, 0.08, 0.25, 1.0, off);
        assertEquals(full.before(), low.before(), 0.01);
        assertEquals(full.sag(0.5), low.sag(0.5), 0.03 * full.sag(0.5));
        assertEquals(full.thrust()[49], low.thrust()[49], 0.02 * full.thrust()[49]);
        assertTrue(full.sag(0.5) > 0.3, text("Einbruch unter Last fehlt ohne die Regel", full.sag(0.5)));
    }

    @Test
    void steadyResistanceAboveTheKneeIsWhatTheFlightTimesWereCalibratedWith() {
        for (DroneBuild build : PRESETS) {
            AirframeSpec p = AirframeSpec.of(build);
            for (double soc : new double[]{1.0, 0.8, 0.5, 0.3, ModelConstants.LOW_SOC_KNEE}) {
                for (double current : new double[]{0.0, 0.3 * p.continuousCurrent, p.continuousCurrent, 2.0 * p.continuousCurrent}) {
                    double rate = 1.0 + ModelConstants.C_RATE_PENALTY * Math.max(0.0, current / p.continuousCurrent - 1.0);
                    assertEquals(p.packResistance * rate, OperatingPoints.sustainedResistance(p, current, soc));
                    assertEquals(p.packResistance * rate + p.polarizationResistance, OperatingPoints.dcResistance(p, current, soc));
                }
            }
        }
    }

    @Test
    void branchesAddUpToTheSustainedResistance() {
        for (DroneBuild build : PRESETS) {
            AirframeSpec p = AirframeSpec.of(build);
            for (double soc = -0.1; soc <= 1.0; soc += 0.01) {
                for (double current : new double[]{1.0, p.continuousCurrent, 1.7 * p.continuousCurrent}) {
                    double sum = OperatingPoints.internalResistance(p, current, soc) + OperatingPoints.fastResistance(p, soc);
                    assertEquals(OperatingPoints.sustainedResistance(p, current, soc), sum, 1e-12 * sum);
                }
            }
        }
    }

    @Test
    void resistanceRisesSteeplyBelowTheKneeAndStopsAtEmpty() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        double previous = OperatingPoints.dcResistance(p, 10.0, ModelConstants.LOW_SOC_KNEE);
        for (double soc = ModelConstants.LOW_SOC_KNEE - 0.01; soc >= -1e-9; soc -= 0.01) {
            double r = OperatingPoints.dcResistance(p, 10.0, soc);
            assertTrue(r > previous, "nicht steigend bei " + soc);
            previous = r;
        }
        double mid = OperatingPoints.dcResistance(p, 10.0, 0.5);
        assertTrue(OperatingPoints.dcResistance(p, 10.0, 0.2) < 1.1 * mid);
        assertTrue(OperatingPoints.dcResistance(p, 10.0, 0.1) > 1.5 * mid);
        assertTrue(OperatingPoints.dcResistance(p, 10.0, 0.0) > 3.5 * mid);
        assertTrue(OperatingPoints.fastResistance(p, 0.05) / OperatingPoints.fastResistance(p, 0.5)
                > OperatingPoints.internalResistance(p, 10.0, 0.05) / OperatingPoints.internalResistance(p, 10.0, 0.5));
        assertEquals(OperatingPoints.dcResistance(p, 10.0, 0.0), OperatingPoints.dcResistance(p, 10.0, -0.08));
    }

    @Test
    void restingVoltageHasAKneeAtTheEnd() {
        for (BatteryType.Chemistry chemistry : new BatteryType.Chemistry[]{BatteryType.Chemistry.LIPO, BatteryType.Chemistry.LIHV,
                BatteryType.Chemistry.LION}) {
            double previous = CellCurve.ocv(chemistry, -0.1);
            for (double soc = -0.09; soc <= 1.0; soc += 0.01) {
                double v = CellCurve.ocv(chemistry, soc);
                assertTrue(v >= previous, chemistry.name() + " fällt bei " + soc);
                previous = v;
            }
            double plateau = (CellCurve.ocv(chemistry, 0.5) - CellCurve.ocv(chemistry, 0.3)) / 0.2;
            double end = (CellCurve.ocv(chemistry, 0.05) - CellCurve.ocv(chemistry, 0.0)) / 0.05;
            double below = (CellCurve.ocv(chemistry, 0.0) - CellCurve.ocv(chemistry, -0.05)) / 0.05;
            assertTrue(end > 1.7 * plateau, chemistry.name() + text(" Knie", end / plateau));
            assertTrue(below > 1.7 * plateau, chemistry.name() + " kein Abriss unter leer");
            if (chemistry != BatteryType.Chemistry.LION) {
                assertTrue(below > end, chemistry.name() + " Abriss unter leer flacher als das Knie");
            }
        }
        double plateau = (CellCurve.ocv(BatteryType.Chemistry.LIPO, 0.5) - CellCurve.ocv(BatteryType.Chemistry.LIPO, 0.3)) / 0.2;
        double end = (CellCurve.ocv(BatteryType.Chemistry.LIPO, 0.05) - CellCurve.ocv(BatteryType.Chemistry.LIPO, 0.0)) / 0.05;
        assertTrue(end > 5.0 * plateau, text("LiPo-Knie", end / plateau));
        assertEquals(3.73, CellCurve.ocv(BatteryType.Chemistry.LIPO, 0.2), 0.005);
        assertEquals(3.69, CellCurve.ocv(BatteryType.Chemistry.LIPO, 0.1), 0.005);
        assertEquals(3.80, CellCurve.ocv(BatteryType.Chemistry.LIPO, 0.5), 0.005);
    }

    @Test
    void wornPackSagsMoreInBothQuickBranches() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        double[] drop = new double[2];
        for (int k = 0; k < 2; k++) {
            PowerSystem power = new PowerSystem();
            power.configure(p, DroneSim.H);
            power.setStateOfCharge(0.6);
            power.batteryResistanceScale = k == 0 ? 1.0 : 1.5;
            for (int n = 0; n < 2 * DroneSim.PHYSICS_HZ; n++) {
                power.forceBatteryCurrent(60.0, DroneSim.H, true, true);
            }
            drop[k] = p.cells * CellCurve.ocv(p.chemistry, 0.6) - power.busVoltage - power.polarization;
        }
        assertEquals(1.5, drop[1] / drop[0], 0.02);
    }
}
