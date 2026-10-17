package de.lardtom.propwash.sim;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import de.lardtom.propwash.calibration.Signals;
import de.lardtom.propwash.calibration.Svg;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public final class RealLogReport {
    static final Map<String, String> CLASSES = new LinkedHashMap<>();

    static {
        CLASSES.put("whoop65", "1S-Whoop 65 mm");
        CLASSES.put("whoop75", "1S-Whoop 75 mm");
        CLASSES.put("cinewhoop2", "2″-Cinewhoop (2S/3S)");
        CLASSES.put("toothpick25", "2,5″ Micro/Toothpick (3S)");
        CLASSES.put("cinewhoop3", "3″-Cinewhoop (4S/6S)");
        CLASSES.put("freestyle5", "5″ Freestyle (6S)");
        CLASSES.put("longrange5", "5″ Long Range (4S Li-Ion)");
        CLASSES.put("longrange7", "7″ Long Range (6S)");
    }

    record Metric(String key, String label, String unit, double scale, int decimals) {
    }

    static final List<Metric> TABLE = List.of(
            new Metric("hover_duty", "Schwebe-Duty", "%", 100.0, 1),
            new Metric("hover_rpm", "Schwebe-Drehzahl", "1/min", 1.0, 0),
            new Metric("thrust_ratio", "Schub echt/Sim bei gleicher Duty+Spannung", "×", 1.0, 2),
            new Metric("rpm_ratio", "Drehzahl echt/Sim bei gleicher Duty+Spannung", "×", 1.0, 2),
            new Metric("climb_rel", "Schub im Steigflug echt/Sim (relativ)", "×", 1.0, 2),
            new Metric("descent_rel", "Schub im Sinkflug echt/Sim (relativ)", "×", 1.0, 2),
            new Metric("drop_accel", "Fallbeschleunigung nach Gas weg", "m/s²", 1.0, 1),
            new Metric("cut_v_end", "Sinken am Ende von Gas weg", "m/s", 1.0, 1),
            new Metric("v_term_fit", "Endgeschwindigkeit Fall (Fit)", "m/s", 1.0, 1),
            new Metric("v_term_sim", "Endgeschwindigkeit Fall (Sim, lang)", "m/s", 1.0, 1),
            new Metric("punch_fz", "Punch Schub/Gewicht", "g", 1.0, 2),
            new Metric("punch_rise_ms", "Punch Anstieg auf 90 %", "ms", 1.0, 0),
            new Metric("lag_up_ms", "Motor hoch t63", "ms", 1.0, 0),
            new Metric("lag_down_ms", "Motor runter t63", "ms", 1.0, 0));

    private RealLogReport() {
    }

    static String n(double v, int decimals) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "–";
        }
        return String.format(Locale.ROOT, "%." + decimals + "f", v).replace('.', ',');
    }

    static String pair(RealLogCalibration.Row r, Metric m) {
        double a = r.real(m.key()) * m.scale();
        double b = r.sim(m.key()) * m.scale();
        if (m.key().endsWith("_rel")) {
            String speed = m.key().replace("_rel", "_speed");
            return Double.isNaN(a) ? "–" : n(a, 2) + " (" + n(r.real(speed), 1) + " m/s)";
        }
        if (m.key().endsWith("_ratio")) {
            double lo = r.real(m.key() + "_low");
            double hi = r.real(m.key() + "_high");
            return Double.isNaN(a) ? "–" : n(a, m.decimals()) + (Double.isNaN(lo) ? "" : " (" + n(lo, 2) + "–" + n(hi, 2) + ")");
        }
        if (m.key().equals("v_term_sim")) {
            double lo = r.simLow.getOrDefault(m.key(), Double.NaN);
            double hi = r.simHigh.getOrDefault(m.key(), Double.NaN);
            return Double.isNaN(b) ? "–" : n(b, m.decimals()) + " (" + n(lo, 1) + "–" + n(hi, 1) + ")";
        }
        if (Double.isNaN(a) && Double.isNaN(b)) {
            return "–";
        }
        String range = "";
        double lo = r.simLow.getOrDefault(m.key(), Double.NaN) * m.scale();
        double hi = r.simHigh.getOrDefault(m.key(), Double.NaN) * m.scale();
        if (!Double.isNaN(lo) && Math.abs(hi - lo) > Math.pow(10, -m.decimals())) {
            range = " (" + n(lo, m.decimals()) + "–" + n(hi, m.decimals()) + ")";
        }
        return n(a, m.decimals()) + " / " + n(b, m.decimals()) + range;
    }

    static double classMedian(List<RealLogCalibration.Row> rows, Function<RealLogCalibration.Row, Double> f) {
        double[] v = rows.stream().map(f).filter(x -> x != null && !Double.isNaN(x)).mapToDouble(Double::doubleValue).toArray();
        return v.length == 0 ? Double.NaN : Signals.median(v);
    }

    public record Summary(String sizeClass, boolean estimated, int logs, double hoverDutyReal, double hoverDutySim, double hoverRpmReal, double hoverRpmSim,
                          double thrustRatio, double rpmRatio, double climbRel, double descentRel, double dropReal, double dropSim, double sinkEndReal, double sinkEndSim, double vTermReal, double vTermFitSim, double vTermSim,
                          double punchReal, double punchSim, double lagUpReal, double lagUpSim, double lagDownReal, double lagDownSim) {
    }

    public static List<Summary> summaries(List<RealLogCalibration.Row> all) {
        List<Summary> out = new ArrayList<>();
        for (String c : CLASSES.keySet()) {
            List<RealLogCalibration.Row> used = all.stream().filter(r -> r.source.sizeClass().equals(c) && r.status.equals("verwendet")).toList();
            if (used.isEmpty()) {
                continue;
            }
            List<RealLogCalibration.Row> known = used.stream().filter(r -> !r.source.estimated()).toList();
            boolean estimated = known.isEmpty();
            List<RealLogCalibration.Row> rows = estimated ? used : known;
            out.add(new Summary(c, estimated, rows.size(), classMedian(rows, r -> r.real("hover_duty")), classMedian(rows, r -> r.sim("hover_duty")),
                    classMedian(rows, r -> r.real("hover_rpm")), classMedian(rows, r -> r.sim("hover_rpm")),
                    classMedian(rows, r -> r.real("thrust_ratio")), classMedian(rows, r -> r.real("rpm_ratio")),
                    classMedian(rows, r -> r.real("climb_rel")), classMedian(rows, r -> r.real("descent_rel")),
                    classMedian(rows, r -> r.real("drop_accel")), classMedian(rows, r -> r.sim("drop_accel")),
                    classMedian(rows, r -> r.real("cut_v_end")), classMedian(rows, r -> r.sim("cut_v_end")),
                    classMedian(rows, r -> r.real("v_term_fit")), classMedian(rows, r -> Double.isNaN(r.real("v_term_fit")) ? Double.NaN : r.sim("v_term_fit")),
                    classMedian(rows, r -> r.sim("v_term_sim")),
                    classMedian(rows, r -> r.real("punch_fz")), classMedian(rows, r -> r.sim("punch_fz")),
                    classMedian(rows, r -> r.real("lag_up_ms")), classMedian(rows, r -> r.sim("lag_up_ms")),
                    classMedian(rows, r -> r.real("lag_down_ms")), classMedian(rows, r -> r.sim("lag_down_ms"))));
        }
        return out;
    }

    static String summaryTable(List<Summary> sums) {
        StringBuilder sb = new StringBuilder();
        sb.append("| Größe | Logs | Schwebe-Duty echt / Sim | Schwebe-Drehzahl echt / Sim | Schub echt/Sim | Drehzahl echt/Sim | Steigflug-Schub echt/Sim rel. | Sinkflug-Schub echt/Sim rel. | Fall nach Gas weg echt / Sim [m/s²] | Sinken am Ende von Gas weg echt / Sim [m/s] | Endgeschw. Fall echt (Fit) / Sim (Fit) / Sim (lang) [m/s] | Punch echt / Sim [g] | Motor hoch t63 echt / Sim [ms] | Motor runter t63 echt / Sim [ms] |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Summary s : sums) {
            sb.append("| ").append(CLASSES.get(s.sizeClass())).append(s.estimated() ? " (nur geschätzte Bauten)" : "").append(" | ").append(s.logs())
                    .append(" | ").append(n(100 * s.hoverDutyReal(), 1)).append(" % / ").append(n(100 * s.hoverDutySim(), 1)).append(" %")
                    .append(" | ").append(n(s.hoverRpmReal(), 0)).append(" / ").append(n(s.hoverRpmSim(), 0))
                    .append(" | ").append(n(s.thrustRatio(), 2)).append(" | ").append(n(s.rpmRatio(), 2))
                    .append(" | ").append(n(s.climbRel(), 2)).append(" | ").append(n(s.descentRel(), 2))
                    .append(" | ").append(n(s.dropReal(), 1)).append(" / ").append(n(s.dropSim(), 1))
                    .append(" | ").append(n(s.sinkEndReal(), 1)).append(" / ").append(n(s.sinkEndSim(), 1))
                    .append(" | ").append(n(s.vTermReal(), 1)).append(" / ").append(n(s.vTermFitSim(), 1)).append(" / ").append(n(s.vTermSim(), 1))
                    .append(" | ").append(n(s.punchReal(), 2)).append(" / ").append(n(s.punchSim(), 2))
                    .append(" | ").append(n(s.lagUpReal(), 0)).append(" / ").append(n(s.lagUpSim(), 0))
                    .append(" | ").append(n(s.lagDownReal(), 0)).append(" / ").append(n(s.lagDownSim(), 0)).append(" |\n");
        }
        return sb.toString();
    }

    public static List<String> suggestions(List<Summary> sums) {
        List<String> out = new ArrayList<>();
        for (Summary s : sums) {
            String c = CLASSES.get(s.sizeClass()) + (s.estimated() ? " (unsicher, Bau geschätzt)" : "");
            if (!Double.isNaN(s.thrustRatio())) {
                out.add(String.format(Locale.ROOT, "%s: Schub bei gleicher Duty und Spannung echt/Sim %.2f → Schubbeiwert (kThrust bzw. Prop-ct) ×%.2f "
                        + "oder gleichwertig die Antriebsleistung anpassen.", c, s.thrustRatio(), s.thrustRatio()));
            }
            if (!Double.isNaN(s.hoverRpmReal()) && !Double.isNaN(s.hoverRpmSim())) {
                double k = Math.pow(s.hoverRpmSim() / s.hoverRpmReal(), 2.0);
                out.add(String.format(Locale.ROOT, "%s: Schwebe-Drehzahl echt %.0f, Sim %.0f 1/min → Schubbeiwert je Drehzahl² ×%.2f (bei richtigem Gewicht).",
                        c, s.hoverRpmReal(), s.hoverRpmSim(), k));
            }
            if (!Double.isNaN(s.rpmRatio())) {
                out.add(String.format(Locale.ROOT, "%s: Drehzahl bei gleicher Duty echt/Sim %.2f → Lastmoment der Props (cp) ×%.2f oder KV-Verlust anpassen.",
                        c, s.rpmRatio(), Math.pow(s.rpmRatio(), -2.0)));
            }
            if (!Double.isNaN(s.climbRel())) {
                out.add(String.format(Locale.ROOT, "%s: Schub im Steigflug echt/Sim %.2f (relativ zum Schweben) → Schubverlust durch Anströmung von oben "
                        + "(axialThrustFactor, zeroThrustPerOmega) %s.", c, s.climbRel(), s.climbRel() > 1.0 ? "in der Sim zu stark" : "in der Sim zu schwach"));
            }
            if (!Double.isNaN(s.descentRel())) {
                out.add(String.format(Locale.ROOT, "%s: Schub im Sinkflug echt/Sim %.2f (relativ zum Schweben) → Schubgewinn beim Sinken (DESCENT_GAIN, "
                        + "Q_DESCENT, Rumpfwiderstand) %s.", c, s.descentRel(), s.descentRel() < 1.0 ? "in der Sim zu hoch (Sim schwebt beim Sinken)" : "in der Sim zu niedrig"));
            }
            if (!Double.isNaN(s.dropReal()) && !Double.isNaN(s.dropSim())) {
                double d = (s.dropReal() - s.dropSim()) / Signals_G;
                out.add(String.format(Locale.ROOT, "%s: Fallbeschleunigung nach Gas weg echt %.1f, Sim %.1f m/s² → Restkraft (Leerlaufschub + Fallwiderstand) "
                        + "in der Sim um %.0f %% des Gewichts %s.", c, s.dropReal(), s.dropSim(), Math.abs(100.0 * d), d > 0 ? "zu hoch (Sim schwebt)" : "zu niedrig"));
            }
            if (!Double.isNaN(s.vTermReal()) && !Double.isNaN(s.vTermFitSim())) {
                double k = Math.pow(s.vTermFitSim() / s.vTermReal(), 2.0);
                out.add(String.format(Locale.ROOT, "%s: Endgeschwindigkeit im Fall (gleicher Fit) echt %.1f, Sim %.1f m/s → Widerstand beim Sinken ×%.2f.",
                        c, s.vTermReal(), s.vTermFitSim(), k));
            }
            if (!Double.isNaN(s.punchReal()) && !Double.isNaN(s.punchSim())) {
                out.add(String.format(Locale.ROOT, "%s: Punch-Beschleunigung echt %.2f g, Sim %.2f g → Vollgasschub ×%.2f.", c, s.punchReal(), s.punchSim(),
                        s.punchReal() / s.punchSim()));
            }
            if (!Double.isNaN(s.lagUpReal()) && !Double.isNaN(s.lagUpSim()) && s.lagUpSim() > 0) {
                out.add(String.format(Locale.ROOT, "%s: Motor hochdrehen t63 echt %.0f ms, Sim %.0f ms → Rotorträgheit (PROP_J/BELL_J) bzw. Motorzeitkonstante ×%.2f.",
                        c, s.lagUpReal(), s.lagUpSim(), s.lagUpReal() / s.lagUpSim()));
            }
            if (!Double.isNaN(s.lagDownReal()) && !Double.isNaN(s.lagDownSim()) && s.lagDownSim() > 0) {
                out.add(String.format(Locale.ROOT, "%s: Motor abbremsen t63 echt %.0f ms, Sim %.0f ms → Bremsen (aktives Bremsen der ESC/Propmoment) ×%.2f.",
                        c, s.lagDownReal(), s.lagDownSim(), s.lagDownReal() / s.lagDownSim()));
            }
        }
        return out;
    }

    static final double Signals_G = 9.81;

    static final double[] AXIAL_BINS = {-8.0, -5.0, -3.5, -1.5, 1.5, 3.5, 8.0};
    static final int AXIAL_BIN_MIN = 10;

    static String axialTable(List<RealLogCalibration.Row> all) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Schub im Sink- und Steigflug nach Geschwindigkeit\n\nSchub echt/Sim relativ zum Schweben, gemessen an ruhigen, waagerechten "
                + "Abschnitten (Neigung bis 15°, mit GPS höchstens 2 m/s über Grund) gegen das Flugmodell im Windkanal (gleiche Duty, Spannung und "
                + "Steiggeschwindigkeit, Prop-Wash wie im Spiel); in Klammern die Anzahl der Werte. Unter 1 heißt: die Sim hat dort mehr Schub als echt.\n\n| Größe |");
        for (int k = 0; k + 1 < AXIAL_BINS.length; k++) {
            if (AXIAL_BINS[k] < 0.0 && AXIAL_BINS[k + 1] > 0.0) {
                continue;
            }
            sb.append(' ').append(n(AXIAL_BINS[k], 1)).append(" … ").append(n(AXIAL_BINS[k + 1], 1)).append(" m/s |");
        }
        sb.append("\n|---|---|---|---|---|---|\n");
        for (String c : CLASSES.keySet()) {
            List<RealLogCalibration.Row> used = all.stream().filter(r -> r.source.sizeClass().equals(c) && r.status.equals("verwendet")).toList();
            List<RealLogCalibration.Row> known = used.stream().filter(r -> !r.source.estimated()).toList();
            List<RealLogCalibration.Row> rows = known.isEmpty() ? used : known;
            StringBuilder line = new StringBuilder();
            boolean any = false;
            for (int k = 0; k + 1 < AXIAL_BINS.length; k++) {
                if (AXIAL_BINS[k] < 0.0 && AXIAL_BINS[k + 1] > 0.0) {
                    continue;
                }
                double lo = AXIAL_BINS[k];
                double hi = AXIAL_BINS[k + 1];
                double[] v = rows.stream().flatMap(r -> r.axial.stream()).filter(a -> a[0] >= lo && a[0] < hi).mapToDouble(a -> a[1]).toArray();
                if (v.length >= AXIAL_BIN_MIN) {
                    line.append(' ').append(n(Signals.median(v), 2)).append(" (").append(v.length).append(") |");
                    any = true;
                } else {
                    line.append(" – |");
                }
            }
            if (any) {
                sb.append("| ").append(CLASSES.get(c)).append(known.isEmpty() ? " (nur geschätzte Bauten)" : "").append(" |").append(line).append('\n');
            }
        }
        return sb.toString();
    }

    static final String[] BATTERY_PARTS = {"Anfang", "Mitte", "Ende"};

    static String ohms(RealLogCalibration.Row r, String key) {
        double a = r.real(key) * 1000.0;
        double b = r.sim(key) * 1000.0;
        return Double.isNaN(a) ? "–" : n(a, 1) + " / " + n(b, 1);
    }

    static String withTau(RealLogCalibration.Row r, String key, String tau) {
        double a = r.real(key) * 1000.0;
        double b = r.sim(key) * 1000.0;
        return Double.isNaN(a) ? "–" : n(a, 1) + " (" + n(r.real(tau), 2) + " s) / " + n(b, 1) + " (" + n(r.sim(tau), 2) + " s)";
    }

    static String batteryTables(List<RealLogCalibration.Row> all) {
        List<RealLogCalibration.Row> rows = all.stream().filter(r -> r.status.equals("verwendet") && !Double.isNaN(r.real("bat_amps_peak"))).toList();
        StringBuilder sb = new StringBuilder("## Akku: Spannungseinbruch echt gegen Sim\n\n");
        if (rows.isEmpty()) {
            return sb.append("Kein Log mit verwertbarem Strom.\n").toString();
        }
        sb.append("Für jeden Log mit verwertbarem Stromsensor: Das Akku-Modell von Propwash bekommt den gemessenen Strom und startet bei dem "
                + "Ladezustand, bei dem die mittlere Spannung über den Flug zur gemessenen passt. An echte und simulierte Spannung wird dasselbe Ersatzschaltbild "
                + "angepasst (Ruhespannung als Polynom der entnommenen Ladung, Sofort-Widerstand, ein schnelles und ein langsames RC-Glied). Widerstände "
                + "in mΩ je Zelle, echt / Sim; beim echten Akku sind Kabel und Stecker enthalten. Der Sim-Akku ist der aus der Quellentabelle. "
                + "„Strom ד: Passt die entnommene Ladung um mehr als 10 % nicht zum Abfall der Ruhespannung (ab 15 % Ladung), wird der gemessene "
                + "Strom so skaliert, dass sie zur Kapazität des Sim-Akkus passt – ein größerer echter Akku oder ein verstellter Stromsensor wird so "
                + "bei gleicher C-Rate verglichen.\n\n");
        sb.append("| Log | Akku | Strom × | Strom Median / p99 [A] | Sofort | Schnell (τ) | Langsam (τ) | Summe | Fit-Rest echt [mV] | Sim-Verlauf gegen echt [mV RMS] | "
                + "Spannung am Ende echt / Sim [V/Zelle] |\n|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (RealLogCalibration.Row r : rows) {
            sb.append("| ").append(r.source.id()).append(" | ").append(n(r.real("bat_cells"), 0)).append("S ").append(n(r.real("bat_capacity"), 0))
                    .append(" mAh | ").append(n(r.real("bat_scale"), 2)).append(" | ").append(n(r.real("bat_amps_mid"), 1)).append(" / ").append(n(r.real("bat_amps_peak"), 1)).append(" | ")
                    .append(ohms(r, "bat_r0")).append(" | ").append(withTau(r, "bat_r1", "bat_tau1")).append(" | ")
                    .append(withTau(r, "bat_r2", "bat_tau2")).append(" | ").append(ohms(r, "bat_sum")).append(" | ")
                    .append(n(r.real("bat_fit_rms") * 1000.0, 1)).append(" | ").append(n(r.sim("bat_trace_rms") * 1000.0, 0)).append(" | ")
                    .append(n(r.real("bat_v_end"), 2)).append(" / ").append(n(r.sim("bat_v_end"), 2)).append(" |\n");
        }
        sb.append("\nEinbruch je Zelle zwischen wenig Strom (bis 25 % des p99-Stroms) und viel Strom (ab 60 %), je Flugdrittel. Ladezustand aus dem "
                + "Sim-Akku.\n\n| Log | Drittel | Ladung Sim [%] | Strom wenig → viel [A] | C-Rate viel | Einbruch echt [V/Zelle] | Einbruch Sim [V/Zelle] | "
                + "Sim/echt |\n|---|---|---|---|---|---|---|---|\n");
        for (RealLogCalibration.Row r : rows) {
            for (int part = 1; part <= BATTERY_PARTS.length; part++) {
                String key = "bat_sag" + part;
                if (Double.isNaN(r.real(key))) {
                    continue;
                }
                sb.append("| ").append(r.source.id()).append(" | ").append(BATTERY_PARTS[part - 1]).append(" | ")
                        .append(n(r.sim(key + "_soc") * 100.0, 0)).append(" | ").append(n(r.real(key + "_lo"), 1)).append(" → ")
                        .append(n(r.real(key + "_hi"), 1)).append(" | ").append(n(r.real(key + "_hi") / (r.real("bat_capacity") / 1000.0), 0))
                        .append(" C | ").append(n(r.real(key), 2)).append(" | ").append(n(r.sim(key), 2)).append(" | ")
                        .append(n(r.sim(key) / r.real(key), 2)).append(" |\n");
            }
        }
        sb.append("\nJe Größe (Median über die Logs): Sofort-Widerstand und Summe echt / Sim in mΩ je Zelle, Einbruch Sim/echt über alle Drittel, "
                + "Punch-Einbruch des ganzen Packs echt / Sim aus dem Nachflug mit der echten Duty (auch ohne Stromsensor).\n\n"
                + "| Größe | Logs mit Strom | Sofort | Summe | Einbruch Sim/echt | Punch-Einbruch [V] |\n|---|---|---|---|---|---|\n");
        for (Map.Entry<String, String> c : CLASSES.entrySet()) {
            List<RealLogCalibration.Row> used = all.stream().filter(r -> r.status.equals("verwendet") && r.source.sizeClass().equals(c.getKey())).toList();
            List<RealLogCalibration.Row> amps = used.stream().filter(r -> !Double.isNaN(r.real("bat_r0"))).toList();
            double punchReal = classMedian(used, r -> r.real("punch_sag_v"));
            if (amps.isEmpty() && Double.isNaN(punchReal)) {
                continue;
            }
            List<Double> ratios = new ArrayList<>();
            for (RealLogCalibration.Row r : amps) {
                for (int part = 1; part <= BATTERY_PARTS.length; part++) {
                    double ratio = r.sim("bat_sag" + part) / r.real("bat_sag" + part);
                    if (!Double.isNaN(ratio)) {
                        ratios.add(ratio);
                    }
                }
            }
            ratios.sort(Double::compare);
            double ratio = ratios.isEmpty() ? Double.NaN : ratios.get(ratios.size() / 2);
            sb.append("| ").append(c.getValue()).append(" | ").append(amps.size()).append(" | ")
                    .append(n(classMedian(amps, r -> r.real("bat_r0")) * 1000.0, 1)).append(" / ").append(n(classMedian(amps, r -> r.sim("bat_r0")) * 1000.0, 1))
                    .append(" | ").append(n(classMedian(amps, r -> r.real("bat_sum")) * 1000.0, 1)).append(" / ")
                    .append(n(classMedian(amps, r -> r.sim("bat_sum")) * 1000.0, 1)).append(" | ").append(n(ratio, 2)).append(" | ")
                    .append(n(punchReal, 2)).append(" / ").append(n(classMedian(used, r -> r.sim("punch_sag_v")), 2)).append(" |\n");
        }
        return sb.toString();
    }

    public static String markdown(List<RealLogCalibration.Row> rows, List<Summary> sums, String header) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Echte Blackbox-Logs gegen Propwash\n\n").append(header).append("\n\n");
        sb.append("## Ergebnis je Größe\n\nWerte sind Mediane über die verwendeten Logs. „Schub echt/Sim“ und „Drehzahl echt/Sim“ vergleichen jeden ruhigen "
                + "Abschnitt bei gleicher Motor-Duty und gleicher Akkuspannung (über 1 = echte Drohne stärker bzw. schneller).\n\n");
        sb.append(summaryTable(sums)).append('\n');
        sb.append(axialTable(rows)).append('\n');
        sb.append(batteryTables(rows)).append('\n');
        sb.append("## Vorschläge für das Flugmodell\n\n");
        for (String s : suggestions(sums)) {
            sb.append("- ").append(s).append('\n');
        }
        sb.append("\n## Logs\n\n| Log | Größe | Drohne | Gewicht | Bau bekannt | Firmware | Dauer | Status | Gründe / Hinweise |\n|---|---|---|---|---|---|---|---|---|\n");
        for (RealLogCalibration.Row r : rows) {
            RealLogCalibration.Source s = r.source;
            sb.append("| [").append(s.id()).append("](").append(s.page()).append(") | ").append(CLASSES.getOrDefault(s.sizeClass(), s.sizeClass()))
                    .append(" | ").append(s.label()).append(" | ").append(n(s.auwG(), 0)).append(" ± ").append(n(s.toleranceG(), 0)).append(" g")
                    .append(" | ").append(s.confidence()).append(" | ").append(r.firmware.replace("|", "/")).append(" | ").append(n(r.seconds, 0)).append(" s | ").append(r.status)
                    .append(" | ").append(String.join("; ", r.problems).replace("|", "/")).append(" |\n");
        }
        sb.append("\n## Werte je Log (echt / Sim, in Klammern Sim bei Gewicht ± Unsicherheit)\n\n| Log |");
        for (Metric m : TABLE) {
            sb.append(' ').append(m.label()).append(" [").append(m.unit()).append("] |");
        }
        sb.append(" Abschnitte |\n|---|");
        sb.append("---|".repeat(TABLE.size())).append("---|\n");
        for (RealLogCalibration.Row r : rows) {
            if (!r.status.equals("verwendet")) {
                continue;
            }
            sb.append("| ").append(r.source.id()).append(" |");
            for (Metric m : TABLE) {
                sb.append(' ').append(pair(r, m)).append(" |");
            }
            sb.append(String.format(Locale.ROOT, " %d× Schweben (%.0f s), %d Punches, %d× Gas weg (%d ruhig) |\n", r.hovers, r.real("hover_s"), r.punches, r.cuts,
                    r.levelCuts));
        }
        sb.append("\n## Rates und PIDs aus dem Kopf\n\n| Log | Rates (Typ RC/Super/Expo) | Max. Rate Propwash | Sollwert-Abweichung Propwash-Kurve (Median / p95) | PIDs Roll / Nick / Gier |\n|---|---|---|---|---|\n");
        for (RealLogCalibration.Row r : rows) {
            if (r.rates.isEmpty()) {
                continue;
            }
            sb.append("| ").append(r.source.id()).append(" | ").append(r.rates).append(" | ").append(n(r.real("rate_max_dps"), 0)).append(" °/s | ")
                    .append(n(r.real("rate_err_dps"), 1)).append(" / ").append(n(r.real("rate_err_p95_dps"), 1)).append(" °/s | ").append(r.pids).append(" |\n");
        }
        sb.append("\n## Verworfene Abschnitte je Log\n\n");
        for (RealLogCalibration.Row r : rows) {
            if (r.rejected.isEmpty()) {
                continue;
            }
            sb.append("- ").append(r.source.id()).append(": ");
            List<String> parts = new ArrayList<>();
            r.rejected.forEach((k, v) -> parts.add(k + " ×" + v));
            sb.append(String.join(", ", parts)).append('\n');
        }
        sb.append("\n## Diagramme\n\n");
        for (RealLogCalibration.Row r : rows) {
            for (String p : r.plots) {
                sb.append("![").append(r.source.id()).append("](").append(p).append(")\n");
            }
        }
        return sb.toString();
    }

    public static String overviewSvg(List<RealLogCalibration.Row> rows, String key, String title, String axis, double scale) {
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        for (RealLogCalibration.Row r : rows) {
            double a = r.real(key) * scale;
            double b = r.sim(key) * scale;
            if (!Double.isNaN(a) && !Double.isNaN(b)) {
                xs.add(a);
                ys.add(b);
            }
        }
        double max = 0.0;
        for (int i = 0; i < xs.size(); i++) {
            max = Math.max(max, Math.max(xs.get(i), ys.get(i)));
        }
        max = max <= 0.0 ? 1.0 : max * 1.1;
        return new Svg(title, "echt " + axis, "Propwash " + axis).xRange(0.0, max).yRange(0.0, max)
                .line("gleich", "#999999", new double[]{0.0, max}, new double[]{0.0, max})
                .points("ein Log", Svg.SIM, xs.stream().mapToDouble(Double::doubleValue).toArray(), ys.stream().mapToDouble(Double::doubleValue).toArray())
                .render();
    }

    public static void write(List<RealLogCalibration.Row> rows, Path out, String header) {
        List<Summary> sums = summaries(rows);
        RealLogCalibration.write(out.resolve("svg").resolve("overview_hover_duty.svg"),
                overviewSvg(rows, "hover_duty", "Schwebe-Duty je Log", "Schwebe-Duty [%]", 100.0));
        RealLogCalibration.write(out.resolve("svg").resolve("overview_drop.svg"),
                overviewSvg(rows, "drop_accel", "Fallbeschleunigung nach Gas weg je Log", "Fallbeschleunigung [m/s²]", 1.0));
        RealLogCalibration.write(out.resolve("svg").resolve("overview_punch.svg"),
                overviewSvg(rows, "punch_fz", "Punch Schub/Gewicht je Log", "Schub/Gewicht [g]", 1.0));
        String md = markdown(rows, sums, header + "\n\n![Schwebe-Duty](svg/overview_hover_duty.svg) ![Fall](svg/overview_drop.svg) ![Punch](svg/overview_punch.svg)");
        RealLogCalibration.write(out.resolve("report.md"), md);
        JsonObject root = new JsonObject();
        root.add("logs", RealLogCalibration.toJson(rows));
        com.google.gson.JsonArray s = new com.google.gson.JsonArray();
        for (Summary x : sums) {
            s.add(new GsonBuilder().serializeSpecialFloatingPointValues().create().toJsonTree(x));
        }
        root.add("classes", s);
        com.google.gson.JsonArray sug = new com.google.gson.JsonArray();
        suggestions(sums).forEach(sug::add);
        root.add("suggestions", sug);
        RealLogCalibration.write(out.resolve("results.json"), new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(root));
    }
}
