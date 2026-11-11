package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThermalCharacterizationTest {
    static final double AMBIENT_C = BuildAnalysis.REFERENCE_AMBIENT_C;
    static final double WARM_C = 45.0;
    static final double HOT_C = 55.0;
    static final double BELOW_THRESHOLD_K = 20.0;
    static final double CRUISE_MARGIN_K = 40.0;
    static final double CRUISE_STICK = 0.5;
    static final double HEAVY_LOAD = 1.5;
    static final double SEVEN_INCH_M = 0.17;
    static final double DT = 0.5;
    static final double HORIZON_S = 7200.0;
    static final double STAND_S = 600.0;
    static final double STAND_TIME_TOLERANCE = 0.05;
    static final double STAND_TIME_SLACK_S = 2.0;
    static final double STAND_TEMP_TOLERANCE_K = 1.5;
    static final int STICK_STEPS = 30;
    static final int TILT_STEPS = 50;
    static final double RECIPE_SHARE = 0.9;
    static final double HOVER_HEIGHT_M = 10.0;
    static final double HOVER_SETTLE_S = 5.0;
    static final double HOVER_DT = 0.01;
    static final double HOT_EARLIEST_S = 90.0;
    static final double HOT_LATEST_S = 150.0;
    static final double NEAR_FULL_THROTTLE = 0.9;
    static final double HOLD_TOLERANCE_M = 1.5;
    static final DroneBuild LIPO_SEVEN = DroneBuild.LONG_RANGE.withBattery(BatteryType.B6S_1300);
    static final DroneBuild SMALL_ESC_SEVEN = new DroneBuild(DroneBuild.LONG_RANGE.frame(), StackType.AIO_20, DroneBuild.LONG_RANGE.motor(),
            DroneBuild.LONG_RANGE.prop(), DroneBuild.LONG_RANGE.video(), BatteryType.B6S_1300);
    static final List<MotorType> BUILTIN_MOTORS = List.of(MotorType.M0802_22000, MotorType.M1404_4600, MotorType.M2004_3000,
            MotorType.M2207_1750, MotorType.M2306_1860, MotorType.M2807_1300);

    record Row(String motor, String base, boolean swapped, DroneBuild build) {
        String label() {
            return this.base + (this.swapped ? " ~" : "");
        }
    }

    record Datasheet(String build, DroneBuild drone, String source, double minRiseK, double maxRiseK) {
    }

    static final List<Datasheet> DATASHEETS = List.of(
            new Datasheet("propwash:whoop_65", DroneBuild.WHOOP, "T-Motor F1203/F1204", 31.0, 46.0),
            new Datasheet("propwash:cinewhoop_3", DroneBuild.CINEWHOOP, "T-Motor F1404", 25.0, 44.0),
            new Datasheet("propwash:freestyle_5", DroneBuild.FREESTYLE, "T-Motor V2207 V2", 73.0, 82.0),
            new Datasheet("propwash:race_5", DroneBuild.RACER, "T-Motor V2306 V2", 64.0, 83.0),
            new Datasheet("propwash:long_range_7+b6s_1300", LIPO_SEVEN, "T-Motor F90", 65.0, 77.0),
            new Datasheet("propwash:long_range_7", DroneBuild.LONG_RANGE, "T-Motor F90, Li-Ion bricht ein: nur Obergrenze", 0.0, 77.0));

    record Heat(double motorC, double escC, double motorSeconds, double escSeconds) {
        boolean motorHot(AirframeSpec p) {
            return this.motorC >= p.motorDerateStartC;
        }

        boolean escDerates() {
            return this.escC >= Thermal.ESC_LIMIT_C;
        }

        boolean limit(AirframeSpec p) {
            return this.motorHot(p) || this.escDerates();
        }
    }

    record Forward(double speedKmh, boolean level, double omega, double current, Heat heat) {
    }

    record Stand(double derateSeconds, double minLimit, double maxLimitWhileCold, double finalMotorC, double finalEscC, double minTorque) {
    }

    record Pack(double motorC, double escC) {
    }

    record Hover(int gravityPercent, double throttle, double motorHotSeconds, double escDerateSeconds, double maxMotorC, double maxEscC,
                 double minTorque, double minHeight) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "Schwerkraft %d %%, Gas %.0f %%, Motor heiß nach %s, ESC regelt ab nach %s, Motor max %.0f °C, "
                            + "ESC max %.0f °C, Drehmoment min %.3f, tiefste %+.2f m", this.gravityPercent, 100.0 * this.throttle,
                    after(this.motorHotSeconds), after(this.escDerateSeconds), this.maxMotorC, this.maxEscC, this.minTorque, this.minHeight);
        }

        static String after(double s) {
            return Double.isFinite(s) ? String.format(Locale.ROOT, "%.0f s", s) : "nie";
        }
    }

    record Result(Row row, AirframeSpec p, Heat hover, Forward forward, Heat full, Heat heavy, double stickToLimit, Pack aggressive,
                  Pack full_, Stand stand) {
        String name() {
            return this.row.motor() + "@" + this.row.base();
        }

        double threshold() {
            return this.p.motorDerateStartC;
        }
    }

    enum Verdict {
        MET("erfüllt"),
        MISSED("verfehlt"),
        UNCHECKED("nicht prüfbar");

        final String label;

        Verdict(String label) {
            this.label = label;
        }
    }

    record Band(String id, String text, Verdict verdict, String detail) {
    }

    record Report(List<Result> results, List<String> missing) {
    }

    private static Report report;

    static List<CalibrationHarness.Entry> bases() {
        List<CalibrationHarness.Entry> out = new ArrayList<>(CalibrationHarness.builtins());
        out.addAll(CalibrationHarness.addons());
        return out;
    }

    static List<MotorType> motors() {
        List<MotorType> out = new ArrayList<>(BUILTIN_MOTORS);
        if (!CalibrationHarness.addons().isEmpty()) {
            MotorType.all().stream().filter(m -> m.id().getNamespace().equals("justmoreparts"))
                    .sorted(Comparator.comparing(m -> m.id().toString())).forEach(out::add);
        }
        return out;
    }

    static Row typical(MotorType motor, List<CalibrationHarness.Entry> bases) {
        Row best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (CalibrationHarness.Entry e : bases) {
            DroneBuild b = e.build();
            int cells = b.battery().cells;
            if (!b.motor().mount.equals(motor.mount) || cells < motor.minCells || cells > motor.maxCells) {
                continue;
            }
            DroneBuild candidate = new DroneBuild(b.frame(), b.stack(), motor, b.prop(), b.video(), b.battery());
            if (!candidate.buildable()) {
                continue;
            }
            BuildAnalysis a = BuildAnalysis.of(candidate);
            if (a.warnings().contains(BuildWarning.CANNOT_HOVER) || a.warnings().contains(BuildWarning.UNDERPOWERED)) {
                continue;
            }
            double score = Math.abs(Math.log(motor.massGrams() / b.motor().massGrams())) + Math.abs(Math.log((double) motor.kv / b.motor().kv));
            if (score < bestScore) {
                bestScore = score;
                best = new Row(motor.id().toString(), e.name(), true, candidate);
            }
        }
        return best;
    }

    static synchronized Report report() {
        if (report != null) {
            return report;
        }
        List<CalibrationHarness.Entry> bases = bases();
        List<Row> rows = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (CalibrationHarness.Entry e : bases) {
            rows.add(new Row(e.build().motor().id().toString(), e.name(), false, e.build()));
        }
        rows.add(new Row(LIPO_SEVEN.motor().id().toString(), "propwash:long_range_7+b6s_1300", false, LIPO_SEVEN));
        for (MotorType motor : motors()) {
            if (bases.stream().anyMatch(e -> e.build().motor() == motor)) {
                continue;
            }
            Row row = typical(motor, bases);
            if (row == null) {
                missing.add(motor.id().toString());
            } else {
                rows.add(row);
            }
        }
        report = new Report(rows.parallelStream().map(ThermalCharacterizationTest::measure).toList(), List.copyOf(missing));
        return report;
    }

    static Heat heat(AirframeSpec p, double omega, double current, double busVoltage, double airspeed) {
        Thermal thermal = new Thermal();
        thermal.reset(AMBIENT_C);
        PowerSystem power = new PowerSystem();
        power.busVoltage = busVoltage;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            power.omega[i] = omega;
            power.current[i] = current;
        }
        double motorAt = Double.NaN;
        double escAt = Double.NaN;
        int steps = (int) Math.round(HORIZON_S / DT);
        for (int k = 1; k <= steps; k++) {
            thermal.advance(p, power, AMBIENT_C, airspeed, DT, false);
            if (Double.isNaN(motorAt) && thermal.motorTemp[0] >= p.motorDerateStartC) {
                motorAt = k * DT;
            }
            if (Double.isNaN(escAt) && thermal.escTemp[0] >= Thermal.ESC_LIMIT_C) {
                escAt = k * DT;
            }
        }
        return new Heat(thermal.motorTemp[0], thermal.escTemp[0], motorAt, escAt);
    }

    static double duty(double stick) {
        return stick >= 1.0 ? 1.0 : ModelConstants.IDLE_REFERENCE + stick * (1.0 - ModelConstants.IDLE_REFERENCE);
    }

    static Heat hover(AirframeSpec p, double load) {
        double omega = p.hoverOmega * Math.sqrt(load);
        double current = OperatingPoints.hoverCurrent(p, load);
        OperatingPoints.Point pt = new OperatingPoints.Point();
        OperatingPoints.steadyMotor(p, omega, current, 0.5, true, 0.0, true, pt);
        return pt.feasible ? heat(p, omega, current, pt.busVoltage, 0.0) : null;
    }

    static Forward forward(AirframeSpec p) {
        double target = duty(CRUISE_STICK);
        OperatingPoints.Point pt = new OperatingPoints.Point();
        OperatingPoints.hover(p, 0.5, true, 0.0, true, pt);
        if (pt.duty >= target) {
            double current = OperatingPoints.hoverCurrent(p);
            return new Forward(0.0, false, p.hoverOmega, current, heat(p, p.hoverOmega, current, pt.busVoltage, 0.0));
        }
        double lo = 0.0;
        double hi = OperatingPoints.MAX_TILT;
        for (int k = 0; k < TILT_STEPS; k++) {
            double mid = 0.5 * (lo + hi);
            if (levelDuty(p, mid, pt) < target) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double speed = OperatingPoints.speedAtTilt(p, lo);
        double omega = OperatingPoints.levelFlightOmega(p, speed, lo);
        double current = levelCurrent(p, speed, lo, omega);
        levelDuty(p, lo, pt);
        return new Forward(speed * 3.6, true, omega, current, heat(p, omega, current, pt.busVoltage, speed));
    }

    static double levelCurrent(AirframeSpec p, double speed, double tilt, double omega) {
        return RotorAero.airTorque(p, omega, speed * StrictMath.sin(tilt), 1.0) / p.kt + p.idleCurrent;
    }

    static double levelDuty(AirframeSpec p, double tilt, OperatingPoints.Point pt) {
        double speed = OperatingPoints.speedAtTilt(p, tilt);
        double omega = OperatingPoints.levelFlightOmega(p, speed, tilt);
        OperatingPoints.steadyMotor(p, omega, levelCurrent(p, speed, tilt, omega), 0.5, true, 0.0, true, pt);
        return pt.duty;
    }

    static OperatingPoints.Point groundPoint(AirframeSpec p, double stick) {
        OperatingPoints.Point pt = new OperatingPoints.Point();
        OperatingPoints.uniformDuty(p, duty(stick), 1.0, true, 0.0, 0.0, pt);
        return pt;
    }

    static Heat ground(AirframeSpec p, double stick) {
        OperatingPoints.Point pt = groundPoint(p, stick);
        return heat(p, pt.omega, pt.phaseCurrent, pt.busVoltage, 0.0);
    }

    static double stickToLimit(AirframeSpec p, Heat full) {
        if (!full.limit(p)) {
            return Double.NaN;
        }
        double lo = 0.0;
        double hi = 1.0;
        for (int k = 0; k < STICK_STEPS; k++) {
            double mid = 0.5 * (lo + hi);
            if (ground(p, mid).limit(p)) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return hi;
    }

    static EnergyModel.Points points(AirframeSpec p) {
        double topTilt = OperatingPoints.topSpeedTilt(p, BuildAnalysis.TOP_SPEED_SOC);
        double topSpeed = topTilt > 0.0 ? OperatingPoints.speedAtTilt(p, topTilt) : 0.0;
        return new EnergyModel.Points(p, topSpeed, topTilt);
    }

    record Segment(double seconds, double heat, double escHeat, double omega) {
    }

    static List<Segment> aggressiveSegments(AirframeSpec p, EnergyModel.Points pts) {
        EnergyModel.Mix mix = EnergyModel.mix(p, EnergyModel.Profile.AGGRESSIVE);
        double omega = mix.cruise() * pts.cruiseOmega + (1.0 - mix.cruise()) * p.hoverOmega;
        double capacity = p.capacityMah;
        double step = p.usableFraction * capacity / EnergyModel.SEGMENTS;
        double landing = p.landingCellVoltage * p.cells;
        double cutoff = p.chemistry.cutoffCellVoltage * p.cells;
        double used = 0.0;
        List<Segment> out = new ArrayList<>();
        for (int k = 0; k < EnergyModel.SEGMENTS; k++) {
            double start = 1.0 - used / capacity;
            double end = 1.0 - (used + step) / capacity;
            pts.evaluate(EnergyModel.Profile.AGGRESSIVE, 0.5 * (start + end));
            double current = Math.max(pts.pack, 1e-6);
            double part = Math.min(
                    EnergyModel.reach(EnergyModel.loadedVoltage(p, start, pts.calm), EnergyModel.loadedVoltage(p, end, pts.calm), landing),
                    EnergyModel.reach(EnergyModel.loadedVoltage(p, start, pts.motion), EnergyModel.loadedVoltage(p, end, pts.motion), cutoff));
            out.add(new Segment(part * step * 3.6 / current, pts.heat, pts.escHeat, omega));
            used += part * step;
            if (part < 1.0) {
                break;
            }
        }
        return out;
    }

    static List<Segment> fullSegments(AirframeSpec p) {
        OperatingPoints.Point pt = new OperatingPoints.Point();
        double step = p.capacityMah / EnergyModel.HEAT_STEPS;
        List<Segment> out = new ArrayList<>();
        for (int k = 0; k < EnergyModel.HEAT_STEPS; k++) {
            OperatingPoints.uniformDuty(p, 1.0, 1.0 - (k + 0.5) / EnergyModel.HEAT_STEPS, true, 0.0, 0.0, pt);
            out.add(new Segment(step * 3.6 / Math.max(EnergyModel.packCurrent(p, pt), 1e-3), EnergyModel.motorHeat(p, pt),
                    Thermal.escLoss(p, pt.phaseCurrent, pt.busVoltage), pt.omega));
        }
        return out;
    }

    static Pack run(AirframeSpec p, List<Segment> segments, double airspeed, boolean peak) {
        double motor = AMBIENT_C;
        double esc = AMBIENT_C;
        double motorMax = AMBIENT_C;
        double escMax = AMBIENT_C;
        double air = Thermal.airflow(airspeed);
        double escC = Thermal.escHeatCapacity(p);
        for (Segment s : segments) {
            double g = Thermal.motorCooling(p, s.omega()) * air;
            double inf = AMBIENT_C + s.heat() / g;
            motor = inf + (motor - inf) * StrictMath.exp(-s.seconds() * g / p.motorHeatCapacity);
            double ge = Thermal.escCooling(p, s.omega()) * air;
            double infE = AMBIENT_C + s.escHeat() / ge;
            esc = infE + (esc - infE) * StrictMath.exp(-s.seconds() * ge / escC);
            motorMax = Math.max(motorMax, motor);
            escMax = Math.max(escMax, esc);
        }
        return peak ? new Pack(motorMax, escMax) : new Pack(motor, esc);
    }

    static Pack aggressivePack(AirframeSpec p) {
        EnergyModel.Points pts = points(p);
        return run(p, aggressiveSegments(p, pts), pts.cruiseSpeed, true);
    }

    static Pack fullPack(AirframeSpec p) {
        return run(p, fullSegments(p), 0.0, true);
    }

    static Stand stand(DroneBuild build) {
        SimTuning tuning = Tests.quietTuning();
        tuning.infiniteBattery = true;
        TestStand stand = new TestStand(build, tuning);
        stand.env.ambientTempC = AMBIENT_C;
        stand.sim.thermal.reset(AMBIENT_C);
        stand.spinUp();
        stand.sim.thermal.reset(AMBIENT_C);
        stand.duty(1.0);
        double derate = Double.NaN;
        double minLimit = 1.0;
        double coldLimit = 1.0;
        double minTorque = 1.0;
        int total = (int) Math.round(STAND_S * DroneSim.PHYSICS_HZ);
        for (int k = 1; k <= total; k++) {
            stand.substeps(1);
            Thermal th = stand.sim.thermal;
            if (Double.isNaN(derate) && th.escDerating[0]) {
                derate = (double) k / DroneSim.PHYSICS_HZ;
            }
            minLimit = Math.min(minLimit, th.limit[0]);
            if (th.escTemp[0] <= Thermal.ESC_LIMIT_C) {
                coldLimit = Math.min(coldLimit, th.limit[0]);
            }
            minTorque = Math.min(minTorque, th.torque[0]);
        }
        return new Stand(derate, minLimit, coldLimit, stand.sim.thermal.motorTemp[0], stand.sim.thermal.escTemp[0], minTorque);
    }

    static Result measure(Row row) {
        AirframeSpec p = AirframeSpec.of(row.build());
        Heat full = ground(p, 1.0);
        return new Result(row, p, hover(p, 1.0), forward(p), full, hover(p, HEAVY_LOAD), stickToLimit(p, full), aggressivePack(p),
                fullPack(p), stand(row.build()));
    }

    static boolean whoop(Result r) {
        return r.p.role == FrameRole.WHOOP;
    }

    static boolean fiveInch(Result r) {
        return r.p.role == FrameRole.FREESTYLE || r.p.role == FrameRole.RACE;
    }

    static boolean sevenInch(Result r) {
        return r.p.role == FrameRole.LONG_RANGE && r.p.propDiameter >= SEVEN_INCH_M;
    }

    static boolean liIon(Result r) {
        return r.p.chemistry == BatteryType.Chemistry.LION;
    }

    static boolean xClass(Result r) {
        return r.p.role == FrameRole.X_CLASS;
    }

    static boolean cinelifter(Result r) {
        return r.p.role == FrameRole.CINELIFTER;
    }

    static String list(List<Result> results, Predicate<Result> fail, Function<Result, String> value) {
        StringBuilder s = new StringBuilder();
        for (Result r : results) {
            if (fail.test(r)) {
                if (!s.isEmpty()) {
                    s.append(", ");
                }
                s.append(r.name()).append(' ').append(value.apply(r));
            }
        }
        return s.toString();
    }

    static String span(List<Result> results, ToDoubleFunction<Result> value) {
        double lo = results.stream().mapToDouble(value).min().orElse(Double.NaN);
        double hi = results.stream().mapToDouble(value).max().orElse(Double.NaN);
        return temp(lo) + "–" + temp(hi) + " °C";
    }

    static Band band(String id, String text, List<Result> group, Predicate<Result> fail, Function<Result, String> value,
                     ToDoubleFunction<Result> spanValue) {
        if (group.isEmpty()) {
            return new Band(id, text, Verdict.UNCHECKED, "keine Baupläne dieser Klasse");
        }
        String failing = list(group, fail, value);
        String range = span(group, spanValue);
        return failing.isEmpty() ? new Band(id, text, Verdict.MET, range) : new Band(id, text, Verdict.MISSED, range + "; verfehlt: " + failing);
    }

    static String limitText(Result r, Heat h) {
        return "Motor " + temp(h.motorC) + "/" + temp(r.threshold()) + ", ESC " + temp(h.escC);
    }

    static List<Band> bands(List<Result> all) {
        List<Band> out = new ArrayList<>();
        List<Result> five = all.stream().filter(r -> r.p.role == FrameRole.FREESTYLE).toList();
        out.add(band("B1", "5″ Freestyle nach aggressivem Akku warm bis heiß (" + temp(WARM_C) + " °C bis Schwelle − " + temp(BELOW_THRESHOLD_K)
                        + " K)", five, r -> r.aggressive.motorC < WARM_C || r.aggressive.motorC > r.threshold() - BELOW_THRESHOLD_K,
                r -> temp(r.aggressive.motorC) + "/" + temp(r.threshold()), r -> r.aggressive.motorC));
        List<Result> seven = all.stream().filter(ThermalCharacterizationTest::sevenInch).toList();
        out.add(band("B2a", "7″ im Reiseflug (50 % Gas, mit Fahrtwind) mindestens " + temp(CRUISE_MARGIN_K) + " K unter der Schwelle", seven,
                r -> r.forward.heat.motorC > r.threshold() - CRUISE_MARGIN_K, r -> temp(r.forward.heat.motorC) + "/" + temp(r.threshold()),
                r -> r.forward.heat.motorC));
        List<Result> sevenLipo = seven.stream().filter(r -> !liIon(r)).toList();
        out.add(band("B2b", "7″ mit LiPo bei Dauervollgas am Boden oder " + decimal(HEAVY_LOAD) + "-facher Last an der Hitzegrenze (Motor über der"
                        + " Schwelle oder ESC über " + temp(Thermal.ESC_LIMIT_C) + " °C; Li-Ion bricht unter Vollgas so weit ein, dass beide kühl bleiben)",
                sevenLipo, r -> !r.full.limit(r.p) && (r.heavy == null || !r.heavy.limit(r.p)), r -> limitText(r, r.full), r -> r.full.motorC));
        List<Result> xc = all.stream().filter(r -> (xClass(r) || cinelifter(r)) && !r.row.swapped()).toList();
        out.add(band("B3", "X-Class und Cinelifter (Presets) erreichen bei Dauervollgas am Boden die Hitzegrenze", xc,
                r -> !r.full.limit(r.p), r -> limitText(r, r.full) + " Gas " + percent(r.stickToLimit), r -> r.full.motorC));
        List<Result> whoops = all.stream().filter(ThermalCharacterizationTest::whoop).toList();
        out.add(band("B4", "Whoops bei Dauervollgas heiß (mindestens " + temp(HOT_C) + " °C)", whoops, r -> r.full.motorC < HOT_C,
                r -> temp(r.full.motorC), r -> r.full.motorC));
        List<Result> fives = all.stream().filter(ThermalCharacterizationTest::fiveInch).toList();
        out.add(band("R2a", "Regel 2: 5″ mit einem normalen Akku (aggressiv und Vollgas bis leer) ohne Hitzegrenze: Motor unter der Schwelle, ESC unter "
                        + temp(Thermal.ESC_LIMIT_C) + " °C", fives,
                r -> Math.max(r.aggressive.motorC, r.full_.motorC) >= r.threshold() || Math.max(r.aggressive.escC, r.full_.escC) >= Thermal.ESC_LIMIT_C,
                r -> "Motor " + temp(Math.max(r.aggressive.motorC, r.full_.motorC)) + "/" + temp(r.threshold()) + ", ESC "
                        + temp(Math.max(r.aggressive.escC, r.full_.escC)), r -> Math.max(r.aggressive.motorC, r.full_.motorC)));
        out.add(band("R2b", "Regel 2: gedrosselt wird nur vom ESC über " + temp(Thermal.ESC_LIMIT_C) + " °C (Kennlinie BLHeli_32), ein heißer Motor allein"
                        + " drosselt nie", all, r -> r.stand.maxLimitWhileCold < 1.0,
                r -> "Grenze bei kaltem ESC " + String.format(Locale.ROOT, "%.2f", r.stand.maxLimitWhileCold), r -> r.full.escC));
        return out;
    }

    static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value).replace('.', ',');
    }

    static String percent(double fraction) {
        return Double.isFinite(fraction) ? String.format(Locale.ROOT, "%.0f %%", 100.0 * fraction) : "–";
    }

    static String temp(double c) {
        return Double.isFinite(c) ? String.format(Locale.ROOT, "%.0f", c) : "–";
    }

    static String seconds(double s) {
        if (!Double.isFinite(s)) {
            return "–";
        }
        return s < 600.0 ? String.format(Locale.ROOT, "%.0f", s) : String.format(Locale.ROOT, "%.0fm", s / 60.0);
    }

    static String header() {
        return String.format(Locale.ROOT, "%-27s %-31s %-20s %6s %4s | %-13s | %-17s | %-18s | %-7s | %4s | %-7s | %-7s | %-14s%n", "Motor",
                "Bauplan", "Klasse", "g", "Thr", "Schw. M/E/t", "50% km/h M/E/t", "Voll M/E/tM/tE", "Last M", "Gas", "Akku M/E", "leer M/E",
                "Stand t/Gr/Dr");
    }

    static String row(Result r) {
        Heat h = r.hover;
        Forward f = r.forward;
        Heat g = r.full;
        String forward = (f.level ? String.format(Locale.ROOT, "%3.0f", f.speedKmh) : "  *") + " " + temp(f.heat.motorC) + "/"
                + temp(f.heat.escC) + "/" + seconds(f.heat.motorSeconds);
        return String.format(Locale.ROOT, "%-27s %-31s %-20s %6.0f %4.0f | %-13s | %-17s | %-18s | %-7s | %4s | %-7s | %-7s | %-14s%n",
                r.row.motor(), r.row.label(), CalibrationHarness.Kind.of(r.row.build()).label,
                r.p.massKg * 1000.0, r.threshold(), temp(h.motorC) + "/" + temp(h.escC) + "/" + seconds(h.motorSeconds), forward,
                temp(g.motorC) + "/" + temp(g.escC) + "/" + seconds(g.motorSeconds) + "/" + seconds(g.escSeconds),
                r.heavy == null ? "–" : temp(r.heavy.motorC), Double.isFinite(r.stickToLimit) ? temp(100.0 * r.stickToLimit) : "–",
                temp(r.aggressive.motorC) + "/" + temp(r.aggressive.escC), temp(r.full_.motorC) + "/" + temp(r.full_.escC),
                seconds(r.stand.derateSeconds) + "/" + String.format(Locale.ROOT, "%.2f", r.stand.minLimit) + "/"
                        + String.format(Locale.ROOT, "%.2f", r.stand.minTorque));
    }

    static String text(Report rep, List<Band> bands) {
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "Motor-Hitze (Phase B), Umgebung %.0f °C, ESC regelt ab %.0f °C ab (bis %.0f %% bei +%.0f K). Spalten: Schwelle "
                        + "(derate_start_c), Schweben, %.0f %% Gas im Vorwärtsflug mit Fahrtwind, Vollgas am Boden je Beharrung Motor/ESC °C und Zeit "
                        + "bis zur Schwelle (Motor) und bis zur Abregelung (ESC) in s, %s-fache Last im Schweben, kleinstes Dauergas bis zur "
                        + "Hitzegrenze, aggressiver Akku und Vollgas bis leer je Motor/ESC (Spitze), Prüfstand im Flugmodell (Abregelung nach s, "
                        + "kleinste Grenze, kleinster Drehmomentfaktor in %.0f s). ~ = Motor in den Bauplan getauscht, * = %.0f %% Gas hält die "
                        + "Höhe nicht (Wert = Schweben).%n", AMBIENT_C, Thermal.ESC_LIMIT_C, 100.0 * Thermal.ESC_MIN_POWER_LIMIT,
                Thermal.ESC_DERATE_SPAN_K, 100.0 * CRUISE_STICK, decimal(HEAVY_LOAD), STAND_S, 100.0 * CRUISE_STICK));
        out.append(header());
        for (Result r : rep.results()) {
            out.append(row(r));
        }
        for (String m : rep.missing()) {
            out.append(String.format(Locale.ROOT, "%-27s kein typischer Bauplan%n", m));
        }
        out.append(String.format(Locale.ROOT, "%nZielbänder und Regel 2%n"));
        for (Band b : bands) {
            out.append(String.format(Locale.ROOT, "%-4s %-13s %s: %s%n", b.id(), b.verdict().label, b.text(), b.detail()));
        }
        return out.toString();
    }

    static Map<String, Verdict> verdicts(List<Band> bands) {
        Map<String, Verdict> out = new LinkedHashMap<>();
        for (Band b : bands) {
            out.put(b.id(), b.verdict());
        }
        return out;
    }

    @Test
    void thermalReport() {
        Report rep = report();
        List<Band> bands = bands(rep.results());
        String text = text(rep, bands);
        CalibrationHarness.write("thermal.txt", text);
        System.out.print(text);
        boolean addons = !CalibrationHarness.addons().isEmpty();
        Map<String, Verdict> expected = new LinkedHashMap<>();
        expected.put("B1", Verdict.MET);
        expected.put("B2a", Verdict.MET);
        expected.put("B2b", Verdict.MET);
        expected.put("B3", addons ? Verdict.MET : Verdict.UNCHECKED);
        expected.put("B4", Verdict.MET);
        expected.put("R2a", Verdict.MET);
        expected.put("R2b", Verdict.MET);
        assertEquals(expected, verdicts(bands), "Zielbänder weichen von docs/design/realism/motor-heat.md ab");
        assertEquals(List.of(), rep.missing());
    }

    @Test
    void quasiStaticHeatMatchesTheTestStand() {
        List<String> out = new ArrayList<>();
        for (Result r : report().results()) {
            Heat full = r.full;
            Stand stand = r.stand;
            boolean hotMotor = full.motorHot(r.p);
            if (full.escDerates()) {
                double tolerance = Math.max(STAND_TIME_SLACK_S, STAND_TIME_TOLERANCE * full.escSeconds);
                if (Double.isNaN(stand.derateSeconds) || (!hotMotor && Math.abs(stand.derateSeconds - full.escSeconds) > tolerance)
                        || stand.derateSeconds > full.escSeconds + tolerance) {
                    out.add(r.name() + " Abregelung nach " + stand.derateSeconds + " s statt " + full.escSeconds + " s");
                }
            } else if (!hotMotor) {
                if (!Double.isNaN(stand.derateSeconds) || stand.minLimit < 1.0) {
                    out.add(r.name() + " regelt im Prüfstand ab trotz ESC-Beharrung " + full.escC + " °C");
                }
                if (!(Math.abs(stand.finalMotorC - full.motorC) <= STAND_TEMP_TOLERANCE_K)) {
                    out.add(r.name() + " Prüfstand " + stand.finalMotorC + " °C statt " + full.motorC + " °C");
                }
                if (!(Math.abs(stand.finalEscC - full.escC) <= STAND_TEMP_TOLERANCE_K)) {
                    out.add(r.name() + " ESC im Prüfstand " + stand.finalEscC + " °C statt " + full.escC + " °C");
                }
            }
            if (!Double.isNaN(stand.derateSeconds) && !(stand.minLimit < 1.0 && stand.minLimit >= Thermal.ESC_MIN_POWER_LIMIT)) {
                out.add(r.name() + " Grenze " + stand.minLimit);
            }
            boolean motorFirst = !full.escDerates() || full.motorSeconds < full.escSeconds;
            if (motorFirst && hotMotor != (stand.minTorque < 1.0)) {
                out.add(r.name() + " Drehmomentfaktor " + stand.minTorque + " bei Motor-Beharrung " + full.motorC + " °C");
            }
            if (!(stand.minTorque >= 1.0 - Thermal.MAX_TORQUE_LOSS)) {
                out.add(r.name() + " Drehmomentfaktor " + stand.minTorque);
            }
        }
        assertEquals(List.of(), out);
    }

    @Test
    void workbenchMinuteMatchesTheDatasheets() {
        List<String> out = new ArrayList<>();
        for (Datasheet d : DATASHEETS) {
            double rise = BuildAnalysis.of(d.drone()).motorTempMinuteC() - AMBIENT_C;
            if (!(rise >= d.minRiseK() && rise <= d.maxRiseK())) {
                out.add(d.build() + ": " + temp(rise) + " K nach 60 s Vollgas im Stand, " + d.source() + " " + temp(d.minRiseK()) + "–"
                        + temp(d.maxRiseK()) + " K");
            }
        }
        assertEquals(List.of(), out, "Werkbank-Wert T60 weicht vom Herstellerprüfstand ab (docs/design/realism/motors-props.md §6.1)");
    }

    @Test
    void noPresetWarnsOfHeat() {
        List<String> out = new ArrayList<>();
        for (DroneBuild build : DroneBuild.PRESETS) {
            BuildAnalysis a = BuildAnalysis.of(build);
            if (a.warnings().contains(BuildWarning.MOTOR_THERMAL)) {
                out.add(build.frame().key() + " Motor " + temp(a.sustainedMotorTempC()) + " °C");
            }
        }
        assertEquals(List.of(), out, "Werkbank warnt bei einem Preset vor Hitze");
    }

    @Test
    void sevenInchReachesItsHeatLimitWithBuiltinParts() {
        List<String> out = new ArrayList<>();
        BuildAnalysis preset = BuildAnalysis.of(DroneBuild.LONG_RANGE);
        if (preset.warnings().contains(BuildWarning.MOTOR_THERMAL)) {
            out.add("7″-Preset mit Li-Ion warnt vor Hitze, Motor " + temp(preset.sustainedMotorTempC()) + " °C");
        }
        AirframeSpec small = AirframeSpec.of(SMALL_ESC_SEVEN);
        EnergyModel.Heat pack = EnergyModel.fullThrottleMotorTemp(small, AMBIENT_C, BuildAnalysis.MOTOR_TEMP_WINDOW_S);
        if (!(pack.escSustained() >= Thermal.ESC_LIMIT_C) || !BuildAnalysis.of(SMALL_ESC_SEVEN).warnings().contains(BuildWarning.MOTOR_THERMAL)) {
            out.add("7″ mit 20-A-AIO und 6S-1300-LiPo: ESC nach einem Akku Vollgas im Stand nur " + temp(pack.escSustained()) + " °C");
        }
        Stand stand = stand(SMALL_ESC_SEVEN);
        if (!(stand.derateSeconds() <= BuildAnalysis.MOTOR_TEMP_WINDOW_S && stand.minLimit() < 1.0)) {
            out.add("7″ mit 20-A-AIO regelt im Prüfstand nicht binnen " + temp(BuildAnalysis.MOTOR_TEMP_WINDOW_S) + " s ab: " + stand);
        }
        AirframeSpec lipo = AirframeSpec.of(LIPO_SEVEN);
        Heat full = ground(lipo, 1.0);
        if (!full.motorHot(lipo)) {
            out.add("7″ mit 6S-1300-LiPo: Motor bei Dauervollgas nur " + temp(full.motorC()) + " °C");
        }
        assertEquals(List.of(), out, "7″ mit eingebauten Teilen erreicht seine Hitzegrenze nicht wie in motor-heat.md §8.7");
    }

    static Hover hoverFromGround(DroneBuild build, int gravityPercent, double seconds) {
        RuleValues rules = RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, gravityPercent).with(WorldRuleSpecs.BATTERY_SCALE, 0);
        SimTuning tuning = PhysicsRules.of(rules).toSimTuning();
        tuning.gyroNoiseDps = 0.0;
        tuning.gyroVibrationDps = 0.0;
        FlatWorld w = FlatWorld.of(build, tuning);
        w.placeOnGround(0.0, 0.0, 0.0);
        w.sim.thermal.reset(AMBIENT_C);
        w.env.ambientTempC = AMBIENT_C;
        w.input.mode = StabMode.ANGLE;
        w.arm();
        AltitudeHold hold = new AltitudeHold(w.groundY + HOVER_HEIGHT_M);
        hold.integralLimit = 1.0;
        Thermal th = w.sim.thermal;
        double hotAt = Double.NaN;
        double escAt = Double.NaN;
        double maxMotor = 0.0;
        double maxEsc = 0.0;
        double minTorque = 1.0;
        double minHeight = Double.POSITIVE_INFINITY;
        double throttle = 0.0;
        int settled = 0;
        int frames = (int) Math.round(seconds / HOVER_DT);
        for (int f = 1; f <= frames; f++) {
            hold.apply(w, HOVER_DT);
            w.frame(HOVER_DT);
            double t = f * HOVER_DT;
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                if (Double.isNaN(hotAt) && th.motorHot[i]) {
                    hotAt = t;
                }
                if (Double.isNaN(escAt) && th.escDerating[i]) {
                    escAt = t;
                }
                maxMotor = Math.max(maxMotor, th.motorTemp[i]);
                maxEsc = Math.max(maxEsc, th.escTemp[i]);
                minTorque = Math.min(minTorque, th.torque[i]);
            }
            if (t >= HOVER_SETTLE_S) {
                minHeight = Math.min(minHeight, w.sim.body.py - hold.target);
                throttle += w.input.throttle;
                settled++;
            }
        }
        return new Hover(gravityPercent, settled == 0 ? Double.NaN : throttle / settled, hotAt, escAt, maxMotor, maxEsc, minTorque, minHeight);
    }

    @Test
    void sevenInchLipoGetsHotHoveringAtTheGravityLimit() {
        int recipe = (int) Math.round(RECIPE_SHARE * BuildAnalysis.of(LIPO_SEVEN).thrustToWeight() * 100.0);
        int gravity = Math.min(recipe, WorldRuleSpecs.GRAVITY_SCALE.max());
        Hover h = hoverFromGround(LIPO_SEVEN, gravity, HOT_LATEST_S);
        String text = "7″ mit 6S-1300-LiPo, " + RECIPE_SHARE + " × S/G = " + recipe + " %, Start vom Boden: " + h;
        List<String> out = new ArrayList<>();
        if (!(h.minHeight() > -HOLD_TOLERANCE_M && h.throttle() >= NEAR_FULL_THROTTLE)) {
            out.add("schwebt nicht mit fast Vollgas");
        }
        if (!(h.motorHotSeconds() >= HOT_EARLIEST_S && h.motorHotSeconds() <= HOT_LATEST_S)) {
            out.add("MOTOR_HOT nicht nach rund zwei Minuten");
        }
        if (!Double.isNaN(h.escDerateSeconds()) || !(h.maxEscC() < Thermal.ESC_LIMIT_C)) {
            out.add("ESC regelt ab");
        }
        if (!(h.minTorque() < 1.0 && h.minTorque() >= 1.0 - Thermal.MAX_TORQUE_LOSS)) {
            out.add("Drehmoment nicht leicht gesunken");
        }
        assertEquals(List.of(), out, "Prüfplan 1.6 „Heißer Motor, kühler ESC“ wie in motor-heat.md §8.8: " + text);
    }

    @Test
    void segmentsMatchTheWorkbench() {
        for (DroneBuild build : DroneBuild.PRESETS) {
            AirframeSpec p = AirframeSpec.of(build);
            BuildAnalysis a = BuildAnalysis.of(build);
            EnergyModel.Points pts = points(p);
            EnergyModel.Flight flight = EnergyModel.fly(pts, EnergyModel.Profile.AGGRESSIVE, AMBIENT_C);
            assertEquals(flight.motorTempC(), run(p, aggressiveSegments(p, points(p)), pts.cruiseSpeed, false).motorC(), 1e-9, p.buildKey);
            assertEquals(a.sustainedMotorTempC(), fullPack(p).motorC(), 1e-9, p.buildKey);
        }
    }
}
