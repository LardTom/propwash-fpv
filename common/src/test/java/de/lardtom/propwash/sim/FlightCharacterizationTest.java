package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightCharacterizationTest {
    static final DroneBuild[] PRESETS = DerivationTest.PRESETS;
    static final String[] NAMES = {"WHOOP", "CINEWHOOP", "FREESTYLE", "RACER", "LONG_RANGE"};
    static final int WHOOP = 0;
    static final int CINEWHOOP = 1;
    static final int FREESTYLE = 2;
    static final int RACER = 3;
    static final int LONG_RANGE = 4;

    private static FlightCharacterization.Result[] results;

    static synchronized FlightCharacterization.Result[] results() {
        if (results == null) {
            FlightCharacterization.Result[] r = new FlightCharacterization.Result[PRESETS.length];
            for (int k = 0; k < PRESETS.length; k++) {
                long start = System.nanoTime();
                r[k] = FlightCharacterization.measure(PRESETS[k], NAMES[k]);
                System.out.printf(Locale.ROOT, "characterized %s in %.1f s%n", NAMES[k], (System.nanoTime() - start) * 1e-9);
            }
            results = r;
        }
        return results;
    }

    record Check(String preset, String metric, String target, double value, double lo, double hi) {
        boolean ok() {
            return this.value >= this.lo && this.value <= this.hi;
        }
    }

    static List<Check> checks() {
        FlightCharacterization.Result[] r = results();
        List<Check> c = new ArrayList<>();
        double[][] twr = {{3.0, 4.0}, {3.5, 7.0}, {8.0, 12.0}, {0.0, 0.0}, {3.5, 7.0}};
        double[][] hover = {{45.0, 60.0}, {35.0, 45.0}, {20.0, 30.0}, {0.0, 0.0}, {25.0, 35.0}};
        double[][] top = {{30.0, 52.0}, {80.0, 100.0}, {130.0, 150.0}, {140.0, 160.0}, {100.0, 120.0}};
        for (int k = 0; k < r.length; k++) {
            FlightCharacterization.Result x = r[k];
            String n = NAMES[k];
            if (k != RACER) {
                c.add(new Check(n, "Schub/Gewicht", range(twr[k], ""), x.twr, twr[k][0], twr[k][1]));
                c.add(new Check(n, "Schwebegas soc 0,5 [%]", range(hover[k], ""), x.hoverDynamic05, hover[k][0], hover[k][1]));
            }
            c.add(new Check(n, "Topspeed ACRO [km/h]", range(top[k], ""), x.acroTopKmh, top[k][0], top[k][1]));
            c.add(new Check(n, "Roll 600 °/s t90 [ms]", "≤ 150", x.rollT90Ms, 0.0, 150.0));
            c.add(new Check(n, "Roll Überschwingen 144/60 fps [%]", "≤ 8", Math.max(x.rollOvershootPct, x.rollOvershootPct60), -1.0, 8.0));
            c.add(new Check(n, "Gier voller Stick t90 [ms]", "≤ 150", x.yawT90Ms, 0.0, 150.0));
            c.add(new Check(n, "Gier Überschwingen [%]", "≤ 10", x.yawOvershootPct, -1.0, 10.0));
            c.add(new Check(n, "ANGLE 30° t90 [ms]", "≤ 300", x.angleT90Ms, 0.0, 300.0));
            c.add(new Check(n, "ANGLE 30° Überschwingen [°]", "≤ 1", x.angleOvershootDeg, -1.0, 1.0));
            c.add(new Check(n, "ANGLE 30° ±1° erreicht [ms]", "≤ 800", x.angleSettleMs, 0.0, 800.0));
            c.add(new Check(n, "Schweben: Drift 10 s [m]", "≤ 0,5", x.hoverDriftM, 0.0, 0.5));
            c.add(new Check(n, "Schweben: Neigung RMS [°]", "≤ 0,5", x.hoverTiltRms, 0.0, 0.5));
            c.add(new Check(n, "Schweben: Drehrate RMS [°/s]", "≤ 3", x.hoverRateRms, 0.0, 3.0));
            c.add(new Check(n, "Flugzeit dyn./stat.", "0,95–1,05", x.hoverTimeMin / x.hoverTimeStaticMin, 0.95, 1.05));
            c.add(new Check(n, "Zelle nach 1 s Vollgas [V]", "≥ Abschaltspannung",
                    x.loadedCell, PRESETS[k].battery().chemistry.cutoffCellVoltage, 5.0));
            c.add(new Check(n, "Punch aus Gas 0, soc 1,0 und 0,5: Brownout", "nein", x.idlePunchBrownout ? 1 : 0, 0, 0));
            c.add(new Check(n, "Prop-Wash Spitze", "≥ 0,7", x.washPeak, 0.7, 1.0));
            c.add(new Check(n, "Prop-Wash Drehrate RMS [°/s]", "≥ 5", x.washRateRms, 5.0, 1000.0));
            c.add(new Check(n, "Prop-Wash max. Neigung [°]", "≤ 15", x.washTiltMax, 0.0, 15.0));
            c.add(new Check(n, "Wand 0,7 × Crash-Schwelle", "kein CRASH, armed", x.slowImpact.contains("CRASH") || !x.slowImpact.endsWith(", armed") ? 0 : 1, 1, 1));
            c.add(new Check(n, "Wand 1,3 × Crash-Schwelle", "CRASH, Disarm", x.fastImpact.equals("CRASH, disarmed CRASH") ? 1 : 0, 1, 1));
            boolean fastEnough = x.flownImpactKmh / 3.6 > 1.2 * x.impactTolerance;
            if (fastEnough) {
                c.add(new Check(n, "Geflogener Crash: Disarm + Ruhe", "ja", x.flownDisarmed && x.flownSettled ? 1 : 0, 1, 1));
            }
            c.add(new Check(n, "Frame-dt 1–50 ms: Neigung RMS [°]", "≤ 0,5", x.worstHoverTiltRmsAnyDt, 0.0, 0.5));
            c.add(new Check(n, "Frame-dt 1–50 ms: Roll-Überschw. [%]", "≤ 10", x.worstRollOvershootAnyDt, -1.0, 10.0));
            c.add(new Check(n, "Frame-dt 1–50 ms: Restwelligkeit [%]", "≤ 5", x.worstRippleAnyDt, 0.0, 5.0));
            c.add(new Check(n, "Determinismus", "bitgleich", x.deterministic ? 1 : 0, 1, 1));
        }
        FlightCharacterization.Result f = r[FREESTYLE];
        FlightCharacterization.Result q = r[RACER];
        c.add(new Check("RACER", "Masse / FREESTYLE", "< 1", q.massG / f.massG, 0.0, 0.999));
        c.add(new Check("RACER", "Schub/Gewicht / FREESTYLE", "> 1", q.twr / f.twr, 1.001, 10.0));
        c.add(new Check("RACER", "Schwebegas soc 0,5 [%]", "< FREESTYLE", q.hoverDynamic05, 0.0, f.hoverDynamic05 - 0.1));
        c.add(new Check("RACER", "Topspeed ACRO / FREESTYLE", "≥ 1", q.acroTopKmh / f.acroTopKmh, 1.0, 10.0));
        c.add(new Check("RACER", "Punch v_y 1 s / FREESTYLE", "> 1", q.punchVy1s / f.punchVy1s, 1.001, 10.0));
        c.add(new Check("RACER", "Roll-Beschleunigung bei 50 % Gas / FREESTYLE", "> 1", q.rollAccel50 / f.rollAccel50, 1.001, 10.0));
        FlightCharacterization.Result l = r[LONG_RANGE];
        c.add(new Check("LONG_RANGE", "Flugzeit Schweben [min]", "≥ 20", l.hoverTimeMin, 20.0, 1000.0));
        c.add(new Check("LONG_RANGE", "Flugzeit 50 % Gas [min]", "≥ 2 × FREESTYLE", l.halfThrottleTimeMin / f.halfThrottleTimeMin, 2.0, 1000.0));
        return c;
    }

    private static String range(double[] r, String unit) {
        return String.format(Locale.ROOT, "%s–%s%s", trim(r[0]), trim(r[1]), unit);
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static void row(StringBuilder sb, String label, Function<FlightCharacterization.Result, String> f) {
        sb.append("| ").append(label);
        for (FlightCharacterization.Result r : results()) {
            sb.append(" | ").append(f.apply(r));
        }
        sb.append(" |\n");
    }

    private static String f0(double v) {
        return Double.isFinite(v) ? String.format(Locale.ROOT, "%.0f", v) : "–";
    }

    private static String f1(double v) {
        return Double.isFinite(v) ? String.format(Locale.ROOT, "%.1f", v) : "–";
    }

    private static String f2(double v) {
        return Double.isFinite(v) ? String.format(Locale.ROOT, "%.2f", v) : "–";
    }

    static String table() {
        StringBuilder sb = new StringBuilder();
        sb.append("| Kennwert");
        for (String n : NAMES) {
            sb.append(" | ").append(n);
        }
        sb.append(" |\n|---|---|---|---|---|---|\n");
        row(sb, "Masse [g]", r -> f1(r.massG));
        row(sb, "Max. Schub pro Motor [g]", r -> f0(r.maxThrustG));
        row(sb, "Schub/Gewicht", r -> f2(r.twr));
        row(sb, "Schwebegas statisch soc 1,0 / 0,5 [%]", r -> f1(r.hoverStatic1) + " / " + f1(r.hoverStatic05));
        row(sb, "Schwebegas geflogen soc 1,0 / 0,5 [%]", r -> f1(r.hoverDynamic1) + " / " + f1(r.hoverDynamic05));
        row(sb, "Topspeed ACRO geflogen [km/h] (Neigung)", r -> f0(r.acroTopKmh) + " (" + f1(r.acroTilt) + "°)");
        row(sb, "Topspeed ACRO stationär [km/h]", r -> f0(r.acroTopStaticKmh));
        row(sb, "Topspeed ANGLE geflogen [km/h] (Neigung)", r -> f0(r.angleTopKmh) + " (" + f1(r.angleTilt) + "°)");
        row(sb, "Steigen 0→100 %: v_y nach 1 s / max. [m/s]", r -> f1(r.punchVy1s) + " / " + f1(r.climbMax));
        row(sb, "Steigen: Spitzen-g", r -> f2(r.punchG));
        row(sb, "Roll 0→600 °/s: t90 [ms] 144 / 60 fps", r -> f0(r.rollT90Ms) + " / " + f0(r.rollT90Ms60));
        row(sb, "Roll Überschwingen [%] 144 / 60 fps", r -> f1(r.rollOvershootPct) + " / " + f1(r.rollOvershootPct60));
        row(sb, "Roll ±5 % erreicht [ms]", r -> f0(r.rollSettleMs));
        row(sb, "Roll 0→600 °/s bei 50 % Gas: t90 [ms] / Überschw. [%]", r -> f0(r.rollT90Ms50) + " / " + f1(r.rollOvershootPct50));
        row(sb, "Roll bei 50 % Gas: max. Beschleunigung [°/s²]", r -> f0(r.rollAccel50));
        row(sb, "Gier voller Stick (670 °/s): t90 [ms]", r -> f0(r.yawT90Ms));
        row(sb, "Gier max. Rate [°/s] / Überschw. [%]", r -> f0(r.yawMaxDps) + " / " + f1(r.yawOvershootPct));
        row(sb, "Gier max. Beschleunigung [°/s²]", r -> f0(r.yawAccelDps2));
        row(sb, "ANGLE 0→30°: t90 [ms]", r -> f0(r.angleT90Ms));
        row(sb, "ANGLE 30°: Überschwingen [°] / ±1° erreicht [ms]", r -> f2(r.angleOvershootDeg) + " / " + f0(r.angleSettleMs));
        row(sb, "Schweben ANGLE 10 s, StickPositions 0: Drift [m]", r -> f2(r.hoverDriftM));
        row(sb, "Schweben: Neigung RMS / max [°]", r -> f2(r.hoverTiltRms) + " / " + f2(r.hoverTiltMax));
        row(sb, "Schweben: Drehrate RMS [°/s] / Höhe σ [cm]", r -> f2(r.hoverRateRms) + " / " + f1(100.0 * r.hoverAltStd));
        row(sb, "Flugzeit Schweben geflogen / stationär [min]", r -> f1(r.hoverTimeMin) + " / " + f1(r.hoverTimeStaticMin));
        row(sb, "Flugzeit 50 % Gas geradeaus [min] (Fahrt [km/h])", r -> f1(r.halfThrottleTimeMin) + " (" + f0(r.halfThrottleSpeedKmh) + ")");
        row(sb, "Zelle Ruhe / nach 1 s Vollgas [V]", r -> f2(r.restCell) + " / " + f2(r.loadedCell));
        row(sb, "Sag Vollgas max. [V/Zelle] / Spitzenstrom [A]", r -> f2(r.sagPerCell) + " / " + f0(r.fullCurrent));
        row(sb, "Punch aus Gas 0: min. Zelle soc 1,0 / 0,5 [V], Brownout", r -> f2(r.idlePunchCell1) + " / " + f2(r.idlePunchCell05)
                + ", " + (r.idlePunchBrownout ? "ja" : "nein"));
        row(sb, "Prop-Wash 5 m/s senkrecht sinken: Spitze / Mittel", r -> f2(r.washPeak) + " / " + f2(r.washMean));
        row(sb, "Prop-Wash: Drehrate RMS mit / ohne [°/s]", r -> f1(r.washRateRms) + " / " + f1(r.washRateRmsClean));
        row(sb, "Prop-Wash: max. Neigung [°] / v_y-Fehler RMS [m/s]", r -> f1(r.washTiltMax) + " / " + f2(r.washVyRms));
        row(sb, "Prop-Wash: Höhenverlust beim Abfangen [m]", r -> f2(r.washAltLoss));
        row(sb, "Crash-Schwelle [m/s]", r -> f1(r.impactTolerance));
        row(sb, "Wand mit 0,7 × Schwelle", r -> r.slowImpact);
        row(sb, "Wand mit 1,3 × Schwelle", r -> r.fastImpact);
        row(sb, "Geflogen gegen Wand [km/h]: Ergebnis (CRASH-Events inkl. Bodenaufschlag)", r -> f0(r.flownImpactKmh) + ": " + r.flownImpact);
        row(sb, "… danach disarmed (CRASH) / in Ruhe am Boden", r -> r.flownDisarmed + " / " + r.flownSettled);
        row(sb, "Frame-dt 1–50 ms: Neigung RMS max [°]", r -> f2(r.worstHoverTiltRmsAnyDt));
        row(sb, "Frame-dt 1–50 ms: Roll-Überschw. / Restwelligkeit max [%]",
                r -> f1(r.worstRollOvershootAnyDt) + " / " + f1(r.worstRippleAnyDt));
        row(sb, "Determinismus (gleicher Seed, bitgleich)", r -> Boolean.toString(r.deterministic));
        return sb.toString();
    }

    static String checkTable(List<Check> checks) {
        StringBuilder sb = new StringBuilder("| Preset | Kennwert | Ziel | Ist | |\n|---|---|---|---|---|\n");
        for (Check c : checks) {
            sb.append(String.format(Locale.ROOT, "| %s | %s | %s | %s | %s |%n", c.preset(), c.metric(), c.target(),
                    Math.abs(c.value()) >= 100.0 ? f0(c.value()) : f2(c.value()), c.ok() ? "ok" : "**außerhalb**"));
        }
        return sb.toString();
    }

    @Test
    void everyPresetMeetsItsFlightTargets() {
        String table = table();
        List<Check> checks = checks();
        System.out.println(table);
        System.out.println(checkTable(checks));
        List<String> failed = new ArrayList<>();
        for (Check c : checks) {
            if (!c.ok()) {
                failed.add(c.preset() + " " + c.metric() + " = " + c.value() + " (Ziel " + c.target() + ")");
            }
        }
        assertTrue(failed.isEmpty(), String.join("\n", failed));
    }
}
