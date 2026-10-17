package de.lardtom.propwash.sim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.calibration.BatteryFit;
import de.lardtom.propwash.calibration.Flight;
import de.lardtom.propwash.calibration.LogData;
import de.lardtom.propwash.calibration.LogHeader;
import de.lardtom.propwash.calibration.Maneuvers;
import de.lardtom.propwash.calibration.Measure;
import de.lardtom.propwash.calibration.Signals;
import de.lardtom.propwash.calibration.Svg;
import de.lardtom.propwash.calibration.Trace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

public final class RealLogCalibration {
    static final double MIN_SECONDS = 10.0;
    static final int MAX_PLOTTED_EVENTS = 1;
    static final double STEADY_STRIDE = 0.05;
    static final double RATE_STICK_MIN = 0.2;
    static final double RATE_STICK_STEADY = 0.01;
    static final double AXIAL_MIN = 1.5;
    static final double STATIC_CLIMB = 1.0;
    static final double AXIAL_TILT = 15.0;
    static final double AXIAL_GROUND_SPEED = 2.0;

    public record Source(String id, String sizeClass, String label, String page, String url, String member, int index, JsonObject parts,
                         double auwG, double toleranceG, String auwSource, String build, String notes, String confidence) {
        public boolean estimated() {
            return this.confidence.equals("niedrig");
        }
    }

    public static final class Row {
        public final Source source;
        public final List<String> problems = new ArrayList<>();
        public final Map<String, Double> real = new LinkedHashMap<>();
        public final Map<String, Double> sim = new LinkedHashMap<>();
        public final Map<String, Double> simLow = new LinkedHashMap<>();
        public final Map<String, Double> simHigh = new LinkedHashMap<>();
        public final Map<String, Integer> rejected = new LinkedHashMap<>();
        public final List<String> plots = new ArrayList<>();
        public final List<double[]> axial = new ArrayList<>();
        public String status = "verworfen";
        public String firmware = "";
        public String craft = "";
        public String rates = "";
        public String pids = "";
        public double seconds;
        public int punches;
        public int cuts;
        public int levelCuts;
        public int hovers;
        public boolean acc;
        public boolean rpm;
        public String massNote = "";

        Row(Source source) {
            this.source = source;
        }

        public double real(String k) {
            return this.real.getOrDefault(k, Double.NaN);
        }

        public double sim(String k) {
            return this.sim.getOrDefault(k, Double.NaN);
        }
    }

    private RealLogCalibration() {
    }

    public static List<Source> sources(Path json) {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(json)).getAsJsonObject();
            List<Source> out = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("logs")) {
                JsonObject o = e.getAsJsonObject();
                JsonObject b = o.getAsJsonObject("build");
                out.add(new Source(o.get("id").getAsString(), o.get("class").getAsString(), o.get("label").getAsString(),
                        o.get("page").getAsString(), o.get("url").getAsString(), text(o, "member"), o.has("index") ? o.get("index").getAsInt() : 1,
                        b.getAsJsonObject("parts"), b.get("auw_g").getAsDouble(), b.get("auw_tolerance_g").getAsDouble(),
                        text(b, "auw_source"), text(b, "text"), text(o, "notes"), b.has("confidence") ? b.get("confidence").getAsString() : "niedrig"));
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String text(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "";
    }

    public static Optional<Path> csvFor(Path decoded, Source s) {
        Path dir = decoded.resolve(s.id());
        for (String name : new String[]{"log.csv.gz", "log.csv"}) {
            if (Files.exists(dir.resolve(name))) {
                return Optional.of(dir.resolve(name));
            }
        }
        return Optional.empty();
    }

    public static Row analyze(Source s, Path decoded, Path out) {
        Row row = new Row(s);
        Optional<Path> csv = csvFor(decoded, s);
        Path headers = decoded.resolve(s.id()).resolve("log.headers.csv");
        if (csv.isEmpty() || !Files.exists(headers)) {
            row.problems.add("Log nicht im Cache (tools/blackbox_calibration.sh fetch)");
            return row;
        }
        LogHeader h;
        Flight f;
        try {
            h = LogHeader.readCsv(headers);
            f = Flight.of(h, LogData.readCsv(csv.get()));
        } catch (IOException e) {
            row.problems.add("Lesefehler: " + e.getMessage());
            return row;
        }
        return analyze(s, h, f, out, row);
    }

    public static Row analyze(Source s, LogHeader h, Flight f, Path out, Row row) {
        row.firmware = h.firmware();
        row.craft = h.craft();
        row.rates = h.rateText();
        row.pids = h.pidText();
        row.seconds = f.durationSeconds();
        row.acc = f.hasAcc;
        row.rpm = f.hasRpm;
        if (!h.betaflight() || (h.version()[0] < 4)) {
            row.problems.add("Firmware nicht unterstützt (nur Betaflight ab 4.0): " + h.firmware());
        }
        if (f.motors != 4) {
            row.problems.add("kein Quadcopter (" + f.motors + " Motoren)");
        }
        if (!f.hasVbat) {
            row.problems.add("keine Akkuspannung im Log");
        }
        if (Math.abs(h.gyroScale() - 1.0) > 1e-6) {
            row.problems.add("unbekannte Gyro-Skalierung " + h.gyroScale());
        }
        if (f.durationSeconds() < MIN_SECONDS) {
            row.problems.add(String.format(Locale.ROOT, "zu kurz (%.0f s)", f.durationSeconds()));
        }
        if (!row.problems.isEmpty()) {
            return row;
        }
        if (!h.dshot()) {
            row.problems.add("kein DShot: Gas → Duty nur geschätzt (1000–2000 µs)");
        }
        if (!f.hasAcc) {
            row.problems.add("kein Beschleunigungssensor (accSmooth fehlt): nur Motor- und Drehzahlwerte");
        }
        if (!f.hasRpm) {
            row.problems.add("keine Motordrehzahl (eRPM fehlt): keine Motorverzögerung");
        }
        Maneuvers.Found m = Maneuvers.find(f);
        row.rejected.putAll(m.rejected);
        row.hovers = m.hovers.size();
        row.punches = m.punches.size();
        row.cuts = m.cuts.size();
        row.levelCuts = m.levelCuts.size();
        double[][] steady = Maneuvers.steadySamples(f, STEADY_STRIDE);
        boolean useful = !Double.isNaN(m.hoverDuty) || !m.punches.isEmpty() || !m.cuts.isEmpty() || (f.hasRpm && steady.length > 20);
        if (!useful) {
            row.problems.add("keine verwertbaren Abschnitte (Schweben, Punch, Gas weg, gleichmäßige Drehzahl)");
            return row;
        }
        Optional<String> addon = RealLogBuilds.missingAddon(s.parts());
        if (addon.isPresent()) {
            row.problems.add(addon.get());
            return row;
        }
        RealLogSim nominal;
        RealLogSim low;
        RealLogSim high;
        try {
            RealLogBuilds.Built nb = RealLogBuilds.build(s.id(), s.parts(), s.auwG());
            nominal = new RealLogSim(nb.build());
            low = new RealLogSim(RealLogBuilds.build(s.id(), s.parts(), s.auwG() - s.toleranceG()).build());
            high = new RealLogSim(RealLogBuilds.build(s.id(), s.parts(), s.auwG() + s.toleranceG()).build());
            row.massNote = nb.frameNote();
        } catch (RuntimeException e) {
            row.problems.add("Drohne nicht aus Teilen baubar: " + e.getMessage());
            return row;
        }
        row.status = "verwendet";
        hover(row, f, m, nominal, low, high);
        steady(row, steady, nominal, low, high, f.hasAcc);
        try {
            events(row, f, m, nominal, low, high, out);
        } catch (IllegalStateException e) {
            row.problems.add(e.getMessage());
        }
        rates(row, h, f);
        battery(row, f, nominal);
        if (out != null) {
            plots(row, f, m, steady, nominal, out);
        }
        return row;
    }

    static void put(Row row, String key, double real, RealLogSim n, RealLogSim lo, RealLogSim hi, ToDoubleFunction<RealLogSim> sim) {
        if (!Double.isNaN(real)) {
            row.real.put(key, real);
        }
        double a = sim.applyAsDouble(n);
        double b = sim.applyAsDouble(lo);
        double c = sim.applyAsDouble(hi);
        row.sim.put(key, a);
        row.simLow.put(key, Math.min(b, c));
        row.simHigh.put(key, Math.max(b, c));
    }

    static void hover(Row row, Flight f, Maneuvers.Found m, RealLogSim n, RealLogSim lo, RealLogSim hi) {
        if (Double.isNaN(m.hoverDuty)) {
            return;
        }
        double v = m.hoverVbat;
        row.real.put("hover_s", m.hoverSeconds);
        row.real.put("hover_vbat", v);
        row.real.put("hover_throttle", m.hoverThrottle);
        put(row, "hover_duty", m.hoverDuty, n, lo, hi, s -> s.hoverDuty(v));
        put(row, "hover_rpm", m.hoverRpm, n, lo, hi, RealLogSim::hoverRpm);
    }

    static void steady(Row row, double[][] rows, RealLogSim n, RealLogSim lo, RealLogSim hi, boolean acc) {
        List<Double> rpmRatio = new ArrayList<>();
        List<Double> thrustRatio = new ArrayList<>();
        List<Double> thrustLow = new ArrayList<>();
        List<Double> thrustHigh = new ArrayList<>();
        List<double[]> axial = new ArrayList<>();
        for (double[] r : rows) {
            double d = r[0];
            double v = r[1];
            double climb = r[4];
            if (!Double.isNaN(climb) && Math.abs(climb) >= AXIAL_MIN && acc && r[2] > 0.5 && r[2] < 1.5 && d > 0.08) {
                if (r.length > 6 && (r[5] > AXIAL_TILT || r[6] > AXIAL_GROUND_SPEED)) {
                    continue;
                }
                double s = n.thrustToWeightAt(d, v, climb);
                if (s > 0.05) {
                    axial.add(new double[]{climb, r[2] / s});
                }
                continue;
            }
            if (!Double.isNaN(climb) && Math.abs(climb) > STATIC_CLIMB) {
                continue;
            }
            if (!Double.isNaN(r[3]) && r[3] > 0.0) {
                double s = n.staticRpm(d, v);
                if (s > 0.0) {
                    rpmRatio.add(r[3] / s);
                }
            }
            if (acc && r[2] > 0.7 && r[2] < 1.4 && d > 0.08) {
                double s = n.staticThrustToWeight(d, v);
                if (s > 0.05) {
                    thrustRatio.add(r[2] / s);
                    thrustLow.add(r[2] / lo.staticThrustToWeight(d, v));
                    thrustHigh.add(r[2] / hi.staticThrustToWeight(d, v));
                }
            }
        }
        if (rpmRatio.size() >= 20) {
            row.real.put("rpm_ratio", median(rpmRatio));
            row.real.put("rpm_ratio_n", (double) rpmRatio.size());
        }
        double staticRatio = thrustRatio.size() >= 20 ? median(thrustRatio) : Double.NaN;
        for (int sign : new int[]{1, -1}) {
            List<Double> ratio = new ArrayList<>();
            List<Double> speed = new ArrayList<>();
            for (double[] a : axial) {
                if (Math.signum(a[0]) == sign) {
                    ratio.add(a[1]);
                    speed.add(Math.abs(a[0]));
                }
            }
            if (ratio.size() >= 20 && !Double.isNaN(staticRatio)) {
                String key = sign > 0 ? "climb" : "descent";
                row.real.put(key + "_rel", median(ratio) / staticRatio);
                row.real.put(key + "_speed", median(speed));
                row.real.put(key + "_n", (double) ratio.size());
                row.axial.addAll(axial.stream().filter(a -> Math.signum(a[0]) == sign).map(a -> new double[]{a[0], a[1] / staticRatio}).toList());
            }
        }
        if (thrustRatio.size() >= 20) {
            double a = median(thrustLow);
            double b = median(thrustHigh);
            row.real.put("thrust_ratio", median(thrustRatio));
            row.real.put("thrust_ratio_low", Math.min(a, b));
            row.real.put("thrust_ratio_high", Math.max(a, b));
            row.real.put("thrust_ratio_n", (double) thrustRatio.size());
        }
    }

    static final int BATTERY_ALIGN_STEPS = 30;
    static final int BATTERY_PARTS = 3;
    static final double BATTERY_SCALE_MIN_USED = 0.15;
    static final double BATTERY_SCALE_TOLERANCE = 0.1;
    static final double BATTERY_SCALE_MIN = 0.4;
    static final double BATTERY_SCALE_MAX = 2.5;
    static final int BATTERY_SOC_STEPS = 40;

    static double socForOcv(AirframeSpec p, double cellVolts) {
        double lo = 0.0;
        double hi = 1.0;
        for (int k = 0; k < BATTERY_SOC_STEPS; k++) {
            double mid = 0.5 * (lo + hi);
            if (CellCurve.ocv(p.chemistry, mid) < cellVolts) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return 0.5 * (lo + hi);
    }

    static double[] batteryTrace(AirframeSpec p, BatteryFit.Series s, double startSoc, double[] socOut) {
        PowerSystem power = new PowerSystem();
        power.configure(p, BatteryFit.STEP_SECONDS);
        power.setStateOfCharge(startSoc);
        double[] volts = new double[s.size()];
        for (int k = 0; k < volts.length; k++) {
            double dt = k > 0 ? s.seconds()[k] - s.seconds()[k - 1] : BatteryFit.STEP_SECONDS;
            power.forceBatteryCurrent(Math.max(0.0, s.amps()[k]), dt, true, false);
            volts[k] = power.busVoltage / p.cells;
            if (socOut != null) {
                socOut[k] = power.soc;
            }
        }
        return volts;
    }

    static double batteryStartSoc(AirframeSpec p, BatteryFit.Series s) {
        double target = Signals.mean(s.cellVolts(), 0, s.size());
        double lo = 0.0;
        double hi = 1.0;
        for (int k = 0; k < BATTERY_ALIGN_STEPS; k++) {
            double mid = 0.5 * (lo + hi);
            double[] sim = batteryTrace(p, s, mid, null);
            if (Signals.mean(sim, 0, sim.length) < target) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return 0.5 * (lo + hi);
    }

    static void putFit(Map<String, Double> target, BatteryFit.Result r) {
        target.put("bat_r0", r.r0());
        target.put("bat_r1", r.r1());
        target.put("bat_tau1", r.tau1());
        target.put("bat_r2", r.r2());
        target.put("bat_tau2", r.tau2());
        target.put("bat_sum", r.sum());
        target.put("bat_fit_rms", r.rmsVolts());
    }

    static void battery(Row row, Flight f, RealLogSim n) {
        AirframeSpec p = n.params;
        row.real.put("bat_cells", (double) p.cells);
        row.real.put("bat_capacity", p.capacityMah);
        if (!f.hasAmps) {
            row.problems.add("kein Strom im Log: Akku-Vergleich nur über den Punch-Einbruch");
            return;
        }
        BatteryFit.Series real = BatteryFit.series(f, p.cells);
        if (!BatteryFit.usableCurrent(real)) {
            row.problems.add("Stromsensor nicht verwertbar (Strom ändert sich kaum mit dem Gas): Akku-Vergleich nur über den Punch-Einbruch");
            return;
        }
        Optional<BatteryFit.Result> raw = BatteryFit.fit(real);
        double scale = 1.0;
        if (raw.isPresent()) {
            double used = socForOcv(p, raw.get().ocvStart()) - socForOcv(p, raw.get().ocvEnd());
            if (used >= BATTERY_SCALE_MIN_USED) {
                double seen = raw.get().mah() / used;
                row.real.put("bat_capacity_seen", seen);
                double k = p.capacityMah / seen;
                if (k < BATTERY_SCALE_MIN || k > BATTERY_SCALE_MAX) {
                    row.problems.add(String.format(Locale.ROOT, "Strom und Spannungsverlauf passen nicht zum Akku der Quellentabelle (%.0f mAh aus "
                            + "der Spannung, %.0f mAh im Aufbau): kein Akku-Vergleich", seen, p.capacityMah));
                    return;
                }
                if (Math.abs(k - 1.0) > BATTERY_SCALE_TOLERANCE) {
                    scale = k;
                }
            }
        }
        row.real.put("bat_scale", scale);
        if (scale != 1.0) {
            real = real.scaled(scale);
        }
        double peak = BatteryFit.percentile(real.amps(), 0.99);
        row.real.put("bat_amps_mid", BatteryFit.percentile(real.amps(), 0.5));
        row.real.put("bat_amps_peak", peak);
        row.real.put("bat_mah", real.mah(0, real.size()));
        BatteryFit.fit(real).ifPresent(r -> putFit(row.real, r));
        double startSoc = batteryStartSoc(p, real);
        double[] soc = new double[real.size()];
        BatteryFit.Series sim = real.withVolts(batteryTrace(p, real, startSoc, soc));
        BatteryFit.fit(sim).ifPresent(r -> putFit(row.sim, r));
        double sum = 0.0;
        for (int k = 0; k < real.size(); k++) {
            double d = sim.cellVolts()[k] - real.cellVolts()[k];
            sum += d * d;
        }
        row.sim.put("bat_trace_rms", Math.sqrt(sum / real.size()));
        row.sim.put("bat_soc_start", startSoc);
        row.sim.put("bat_soc_end", soc[soc.length - 1]);
        row.real.put("bat_v_end", Signals.mean(real.cellVolts(), real.size() - 100, real.size()));
        row.sim.put("bat_v_end", Signals.mean(sim.cellVolts(), sim.size() - 100, sim.size()));
        for (int part = 0; part < BATTERY_PARTS; part++) {
            int from = part * real.size() / BATTERY_PARTS;
            int to = (part + 1) * real.size() / BATTERY_PARTS;
            Optional<BatteryFit.Step> a = BatteryFit.step(real, from, to, peak);
            Optional<BatteryFit.Step> b = BatteryFit.step(sim, from, to, peak);
            if (a.isEmpty() || b.isEmpty()) {
                continue;
            }
            String key = "bat_sag" + (part + 1);
            row.real.put(key, a.get().sag());
            row.real.put(key + "_lo", a.get().lowAmps());
            row.real.put(key + "_hi", a.get().highAmps());
            row.sim.put(key, b.get().sag());
            row.sim.put(key + "_soc", soc[(from + to) / 2]);
        }
    }

    static double median(List<Double> v) {
        double[] a = v.stream().mapToDouble(Double::doubleValue).toArray();
        return Signals.median(a);
    }

    record EventPair(Trace real, Trace sim, double scale) {
    }

    static final Map<String, List<EventPair>> PLOT_EVENTS = new LinkedHashMap<>();

    static void events(Row row, Flight f, Maneuvers.Found m, RealLogSim n, RealLogSim lo, RealLogSim hi, Path out) {
        List<double[]> punchReal = new ArrayList<>();
        List<double[]> punchSim = new ArrayList<>();
        List<double[]> punchLo = new ArrayList<>();
        List<double[]> punchHi = new ArrayList<>();
        List<EventPair> plotted = new ArrayList<>();
        for (Maneuvers.Event e : m.punches) {
            Trace real = Trace.of(f, e.from(), e.to(), e.step());
            RealLogSim.Replay rs = n.replay(real, f.accLpfHz, m.hoverDuty, m.hoverVbat);
            noteDisarm(row, rs);
            Measure.Punch a = Measure.punch(real);
            Measure.Punch b = Measure.punch(rs.trace());
            Measure.Punch c = Measure.punch(lo.replay(real, f.accLpfHz, m.hoverDuty, m.hoverVbat).trace());
            Measure.Punch d = Measure.punch(hi.replay(real, f.accLpfHz, m.hoverDuty, m.hoverVbat).trace());
            punchReal.add(new double[]{a.peakFz(), a.riseMs(), a.rpmT63Ms(), a.sagVolts(), a.postDuty()});
            punchSim.add(new double[]{b.peakFz(), b.riseMs(), b.rpmT63Ms(), b.sagVolts(), b.postDuty()});
            punchLo.add(new double[]{Math.min(c.peakFz(), d.peakFz())});
            punchHi.add(new double[]{Math.max(c.peakFz(), d.peakFz())});
            if (plotted.size() < MAX_PLOTTED_EVENTS) {
                plotted.add(new EventPair(real, rs.trace(), rs.dutyScale()));
            }
        }
        if (!punchReal.isEmpty()) {
            row.real.put("punch_fz", col(punchReal, 0));
            row.sim.put("punch_fz", col(punchSim, 0));
            row.simLow.put("punch_fz", col(punchLo, 0));
            row.simHigh.put("punch_fz", col(punchHi, 0));
            row.real.put("punch_duty", col(punchReal, 4));
            row.real.put("punch_rise_ms", col(punchReal, 1));
            row.sim.put("punch_rise_ms", col(punchSim, 1));
            row.real.put("punch_sag_v", col(punchReal, 3));
            row.sim.put("punch_sag_v", col(punchSim, 3));
            if (f.hasRpm) {
                row.real.put("lag_up_ms", col(punchReal, 2));
                row.sim.put("lag_up_ms", col(punchSim, 2));
            }
        }
        List<double[]> cutReal = new ArrayList<>();
        List<double[]> cutSim = new ArrayList<>();
        List<double[]> cutLo = new ArrayList<>();
        List<double[]> cutHi = new ArrayList<>();
        List<Double> idle = new ArrayList<>();
        List<Double> preV = new ArrayList<>();
        int cutPlots = 0;
        for (Maneuvers.Event e : m.cuts) {
            Trace real = Trace.of(f, e.from(), e.to(), e.step());
            RealLogSim.Replay rs = n.replay(real, f.accLpfHz, m.hoverDuty, m.hoverVbat);
            noteDisarm(row, rs);
            Measure.Cut a = Measure.cut(real);
            Measure.Cut b = Measure.cut(rs.trace());
            Measure.Cut c = Measure.cut(lo.replay(real, f.accLpfHz, m.hoverDuty, m.hoverVbat).trace());
            Measure.Cut d = Measure.cut(hi.replay(real, f.accLpfHz, m.hoverDuty, m.hoverVbat).trace());
            boolean level = m.levelCuts.contains(e);
            cutReal.add(new double[]{a.fzEarly(), level ? a.vTerminal() : Double.NaN, a.rpmT63Ms(), a.idleDuty(), a.durationS(), a.vEnd()});
            cutSim.add(new double[]{b.fzEarly(), level ? b.vTerminal() : Double.NaN, b.rpmT63Ms(), b.idleDuty(), b.durationS(), b.vEnd()});
            cutLo.add(new double[]{Math.min(c.fzEarly(), d.fzEarly()), Math.min(c.vEnd(), d.vEnd())});
            cutHi.add(new double[]{Math.max(c.fzEarly(), d.fzEarly()), Math.max(c.vEnd(), d.vEnd())});
            idle.add(a.idleDuty());
            preV.add(Signals.mean(real.vbat, 0, real.step));
            if (cutPlots < MAX_PLOTTED_EVENTS) {
                plotted.add(new EventPair(real, rs.trace(), rs.dutyScale()));
                cutPlots++;
            }
        }
        if (!cutReal.isEmpty()) {
            row.real.put("cut_fz", col(cutReal, 0));
            row.sim.put("cut_fz", col(cutSim, 0));
            row.simLow.put("cut_fz", col(cutLo, 0));
            row.simHigh.put("cut_fz", col(cutHi, 0));
            row.real.put("drop_accel", Measure.G * (1.0 - col(cutReal, 0)));
            row.sim.put("drop_accel", Measure.G * (1.0 - col(cutSim, 0)));
            row.simLow.put("drop_accel", Measure.G * (1.0 - col(cutHi, 0)));
            row.simHigh.put("drop_accel", Measure.G * (1.0 - col(cutLo, 0)));
            row.real.put("cut_v_end", col(cutReal, 5));
            row.sim.put("cut_v_end", col(cutSim, 5));
            row.simLow.put("cut_v_end", col(cutLo, 1));
            row.simHigh.put("cut_v_end", col(cutHi, 1));
            row.real.put("cut_duty", col(cutReal, 3));
            row.real.put("cut_s", col(cutReal, 4));
            double vReal = col(cutReal, 1);
            double vSim = col(cutSim, 1);
            if (!Double.isNaN(vReal)) {
                row.real.put("v_term_fit", vReal);
            }
            if (!Double.isNaN(vSim)) {
                row.sim.put("v_term_fit", vSim);
            }
            double idleDuty = median(idle);
            double v = median(preV);
            put(row, "v_term_sim", Double.NaN, n, lo, hi, s -> s.terminalDescent(idleDuty, v));
            if (f.hasRpm) {
                row.real.put("lag_down_ms", col(cutReal, 2));
                row.sim.put("lag_down_ms", col(cutSim, 2));
            }
        }
        PLOT_EVENTS.put(row.source.id(), plotted);
    }

    static void noteDisarm(Row row, RealLogSim.Replay rs) {
        String note = "Sim beim Nachfliegen entschärft: " + rs.disarm();
        if (!rs.disarm().isEmpty() && !row.problems.contains(note)) {
            row.problems.add(note);
        }
    }

    static double col(List<double[]> rows, int c) {
        double[] v = new double[rows.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = rows.get(i)[c];
        }
        return Signals.median(v);
    }

    static void rates(Row row, LogHeader h, Flight f) {
        String type = h.ratesType();
        int[] rc = h.rcRates();
        int[] sr = h.superRates();
        int[] ex = h.rcExpo();
        if (rc.length < 1 || sr.length < 1 || !(type.equals("BETAFLIGHT") || type.equals("ACTUAL"))) {
            return;
        }
        RateCurve curve = new RateCurve();
        RatesType t = type.equals("ACTUAL") ? RatesType.ACTUAL : RatesType.BETAFLIGHT;
        if (t == RatesType.ACTUAL) {
            curve.centerRateDps = rc[0] * 10.0;
            curve.maxRateDps = sr[0] * 10.0;
            curve.actualExpo = ex.length > 0 ? ex[0] / 100.0 : 0.0;
        } else {
            curve.rcRate = rc[0] / 100.0;
            curve.superRate = sr[0] / 100.0;
            curve.rcExpo = ex.length > 0 ? ex[0] / 100.0 : 0.0;
        }
        List<Double> err = new ArrayList<>();
        for (int i = 1; i < f.n; i++) {
            double s = f.stickRoll[i];
            if (Double.isNaN(s) || Double.isNaN(f.setpointRoll[i]) || Math.abs(s) < RATE_STICK_MIN || Math.abs(s - f.stickRoll[i - 1]) > RATE_STICK_STEADY
                    || !(f.selfLevel[i] == 0.0)) {
                continue;
            }
            err.add(Math.abs(RateCurves.rateDps(t, curve, s) - f.setpointRoll[i]));
        }
        row.real.put("rate_max_dps", RateCurves.maxRateDps(t, curve));
        if (err.size() > 50) {
            double[] a = err.stream().mapToDouble(Double::doubleValue).toArray();
            row.real.put("rate_err_dps", Signals.median(a));
            row.real.put("rate_err_p95_dps", Signals.percentile(a, 0, a.length, 0.95));
        }
    }

    static void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void plots(Row row, Flight f, Maneuvers.Found m, double[][] steady, RealLogSim n, Path out) {
        String id = row.source.id();
        if (!m.hovers.isEmpty()) {
            double[] hv = m.hovers.stream().mapToDouble(Maneuvers.Hover::vbat).toArray();
            double[] hd = m.hovers.stream().mapToDouble(Maneuvers.Hover::dutyAt1g).toArray();
            double vMin = Signals.min(hv, 0, hv.length) - 0.1;
            double vMax = Signals.max(hv, 0, hv.length) + 0.1;
            double[] lx = new double[21];
            double[] ly = new double[21];
            for (int i = 0; i <= 20; i++) {
                lx[i] = vMin + (vMax - vMin) * i / 20.0;
                ly[i] = 100.0 * n.hoverDuty(lx[i]);
            }
            double[] hdp = new double[hd.length];
            for (int i = 0; i < hd.length; i++) {
                hdp[i] = 100.0 * hd[i];
            }
            String svg = new Svg(row.source.label() + ": Schwebe-Gas gegen Akkuspannung", "Akkuspannung unter Last [V]", "Motor-Duty beim Schweben [%]")
                    .points("echt (Schwebeabschnitte)", Svg.REAL, hv, hdp).line("Propwash", Svg.SIM, lx, ly).render();
            write(out.resolve("svg").resolve(id + "_hover.svg"), svg);
            row.plots.add("svg/" + id + "_hover.svg");
        }
        if (f.hasAcc && steady.length > 20) {
            double vMed = Signals.median(java.util.Arrays.stream(steady).mapToDouble(r -> r[1]).toArray());
            int bins = 16;
            double[] bx = new double[bins];
            double[] by = new double[bins];
            for (int b = 0; b < bins; b++) {
                double lo = b / (double) bins;
                double hi = (b + 1) / (double) bins;
                List<Double> vals = new ArrayList<>();
                for (double[] r : steady) {
                    if (r[0] >= lo && r[0] < hi && !Double.isNaN(r[2])) {
                        vals.add(r[2] * Math.pow(vMed / r[1], 2.0));
                    }
                }
                bx[b] = 100.0 * (lo + hi) / 2.0;
                by[b] = vals.size() >= 5 ? median(vals) : Double.NaN;
            }
            double[] sx = new double[41];
            double[] sy = new double[41];
            for (int i = 0; i <= 40; i++) {
                sx[i] = 2.5 * i;
                sy[i] = n.staticThrustToWeight(i / 40.0, vMed);
            }
            String svg = new Svg(row.source.label() + String.format(Locale.ROOT, ": Schub/Gewicht bei %.1f V", vMed).replace('.', ','),
                    "Motor-Duty [%]", "Schub / Gewicht (accSmooth Z)")
                    .points("echt (Median je Gasstufe)", Svg.REAL, bx, by).line("Propwash (Stand, ohne Fahrt)", Svg.SIM, sx, sy).hLine(1.0).render();
            write(out.resolve("svg").resolve(id + "_thrust.svg"), svg);
            row.plots.add("svg/" + id + "_thrust.svg");
        }
        if (!row.axial.isEmpty()) {
            double[] cx = row.axial.stream().mapToDouble(a -> a[0]).toArray();
            double[] cy = row.axial.stream().mapToDouble(a -> a[1]).toArray();
            String svg = new Svg(row.source.label() + ": Schub im Steig- und Sinkflug", "Steigrate (Baro) [m/s]", "Schub echt / Sim (relativ zum Schweben)")
                    .points("ruhige Abschnitte", Svg.REAL, cx, cy).hLine(1.0).yRange(0.0, 2.5).render();
            write(out.resolve("svg").resolve(id + "_axial.svg"), svg);
            row.plots.add("svg/" + id + "_axial.svg");
        }
        int k = 0;
        for (EventPair p : PLOT_EVENTS.getOrDefault(id, List.of())) {
            Trace r = p.real();
            Trace s = p.sim();
            double[] t = new double[r.length()];
            double[] dr = new double[r.length()];
            for (int i = 0; i < t.length; i++) {
                t[i] = 1000.0 * r.seconds(i);
                dr[i] = r.duty[i];
            }
            boolean punch = Signals.mean(r.duty, r.at(0.1), r.length()) > Signals.mean(r.duty, 0, r.step);
            String kind = punch ? "Punch" : "Gas weg";
            if (f.hasAcc) {
                Svg plot = new Svg(row.source.label() + ": " + kind + " – Beschleunigung", "Zeit ab Gasstufe [ms]", "Schub / Gewicht [g]")
                        .line("echt accSmooth Z", Svg.REAL, t, r.fz).line("Propwash (gleiche Duty)", Svg.SIM, t, s.fz)
                        .dashed("Motor-Duty (0–1)", "#777777", t, dr).hLine(1.0);
                write(out.resolve("svg").resolve(id + "_event" + k + "_acc.svg"), plot.render());
                row.plots.add("svg/" + id + "_event" + k + "_acc.svg");
            }
            if (f.hasRpm) {
                Svg plot = new Svg(row.source.label() + ": " + kind + " – Motordrehzahl", "Zeit ab Gasstufe [ms]", "Drehzahl [1/min]")
                        .line("echt (eRPM)", Svg.REAL, t, r.rpm).line("Propwash", Svg.SIM, t, s.rpm);
                write(out.resolve("svg").resolve(id + "_event" + k + "_rpm.svg"), plot.render());
                row.plots.add("svg/" + id + "_event" + k + "_rpm.svg");
            }
            k++;
        }
    }

    public static JsonArray toJson(List<Row> rows) {
        JsonArray arr = new JsonArray();
        for (Row r : rows) {
            JsonObject o = new JsonObject();
            o.addProperty("id", r.source.id());
            o.addProperty("class", r.source.sizeClass());
            o.addProperty("status", r.status);
            JsonArray problems = new JsonArray();
            r.problems.forEach(problems::add);
            o.add("problems", problems);
            o.add("real", map(r.real));
            o.add("sim", map(r.sim));
            o.add("sim_low", map(r.simLow));
            o.add("sim_high", map(r.simHigh));
            arr.add(o);
        }
        return arr;
    }

    static JsonObject map(Map<String, Double> m) {
        JsonObject o = new JsonObject();
        m.forEach((k, v) -> {
            if (!Double.isNaN(v) && !Double.isInfinite(v)) {
                o.addProperty(k, Math.round(v * 10000.0) / 10000.0);
            }
        });
        return o;
    }
}
