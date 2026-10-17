package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DischargeCurveTest {
    static final String FIXTURES = "de/lardtom/propwash/sim/discharge_curves.txt";
    static final String PREVIOUS = "de/lardtom/propwash/sim/battery_curves_0_5_1.txt";

    record Point(double x, double volts, double tolerance) {
    }

    record Fixture(String id, String target, double cRate, double cutoff, List<Point> points) {
        boolean resting() {
            return this.cRate <= 0.0;
        }
    }

    static Path resource(String name) {
        for (Path candidate : List.of(Path.of("common/src/test/resources", name), Path.of("src/test/resources", name))) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return Path.of("common/src/test/resources", name);
    }

    static List<Fixture> fixtures() throws IOException {
        List<Fixture> out = new ArrayList<>();
        for (String line : Files.readAllLines(resource(FIXTURES), StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.trim().split("\\s+");
            List<Point> points = new ArrayList<>();
            for (int k = 4; k < f.length; k++) {
                String[] xy = f[k].split("=");
                String[] vt = xy[1].split("±");
                points.add(new Point(Double.parseDouble(xy[0]), Double.parseDouble(vt[0]), Double.parseDouble(vt[1])));
            }
            out.add(new Fixture(f[0], f[1], Double.parseDouble(f[2]), f[3].equals("-") ? Double.NaN : Double.parseDouble(f[3]), points));
        }
        return out;
    }

    static List<double[]> previous(String id) throws IOException {
        List<double[]> out = new ArrayList<>();
        for (String line : Files.readAllLines(resource(PREVIOUS), StandardCharsets.UTF_8)) {
            String[] f = line.trim().split("\\s+");
            if (f.length == 3 && f[0].equals(id)) {
                out.add(new double[]{Double.parseDouble(f[1]), Double.parseDouble(f[2])});
            }
        }
        return out;
    }

    static BatteryType.Chemistry chemistry(String id) {
        for (BatteryType.Chemistry c : new BatteryType.Chemistry[]{BatteryType.Chemistry.LIPO, BatteryType.Chemistry.LIHV, BatteryType.Chemistry.LION}) {
            if (c.definition().id().toString().equals(id)) {
                return c;
            }
        }
        throw new IllegalArgumentException(id);
    }

    static DroneBuild build(String name) {
        return switch (name) {
            case "WHOOP" -> DroneBuild.WHOOP;
            case "CINEWHOOP" -> DroneBuild.CINEWHOOP;
            case "FREESTYLE" -> DroneBuild.FREESTYLE;
            case "RACER" -> DroneBuild.RACER;
            case "LONG_RANGE" -> DroneBuild.LONG_RANGE;
            default -> throw new IllegalArgumentException(name);
        };
    }

    static String text(String name, double value) {
        return String.format(Locale.ROOT, "%s %.3f", name, value);
    }

    @Test
    void fixturesAreMet() throws IOException {
        List<Fixture> all = fixtures();
        assertFalse(all.isEmpty());
        List<String> misses = new ArrayList<>();
        for (Fixture f : all) {
            BatteryCurves.Curve curve = f.resting() ? null : BatteryCurves.discharge(AirframeSpec.of(build(f.target())), f.cRate(), f.cutoff());
            for (Point pt : f.points()) {
                double v = f.resting() ? CellCurve.ocv(chemistry(f.target()), pt.x()) : curve.at(pt.x());
                if (Math.abs(v - pt.volts()) > pt.tolerance()) {
                    misses.add(String.format(Locale.ROOT, "%s %s bei %.2f: %.3f V statt %.2f ± %.2f V", f.id(), f.target(), pt.x(), v, pt.volts(),
                            pt.tolerance()));
                }
            }
        }
        assertTrue(misses.isEmpty(), String.join("\n", misses));
    }

    @Test
    void sixCellPackAtFifteenCHasTheDescribedShape() {
        for (DroneBuild build : new DroneBuild[]{DroneBuild.FREESTYLE, DroneBuild.RACER}) {
            AirframeSpec p = AirframeSpec.of(build);
            BatteryCurves.Curve c = BatteryCurves.discharge(p, 15.0, 3.1);
            String name = build.frame().name();
            double start = c.slope(0.0, 0.05);
            double middle = c.slope(0.3, 0.8);
            double knee = c.slope(0.9, 1.0);
            assertTrue(start > 2.0 * middle, name + text(" Anfang", start) + text(" Mitte", middle));
            assertTrue(middle < 0.6, name + text(" Mitte fällt zu schnell", middle));
            assertTrue(knee > 6.0 * middle, name + text(" Knie", knee) + text(" Mitte", middle));
            assertTrue(c.slope(0.95, 1.0) > c.slope(0.9, 0.95), name + " Knie wird zum Ende nicht steiler");
            assertTrue(c.capacity() > 0.92 && c.capacity() < 1.0, name + text(" Kapazität bis 3,1 V", c.capacity()));
        }
    }

    @Test
    void kneeIsLaterAndSharperThanInTheReleasedModel() throws IOException {
        List<double[]> old = previous("kurve-15c-6s");
        assertEquals(101, old.size());
        BatteryCurves.Curve c = BatteryCurves.discharge(AirframeSpec.of(DroneBuild.FREESTYLE), 15.0, 3.1);
        double oldKnee = (old.get(90)[1] - old.get(100)[1]) / 0.1;
        double oldMiddle = (old.get(30)[1] - old.get(80)[1]) / 0.5;
        assertTrue(c.slope(0.9, 1.0) / c.slope(0.3, 0.8) > 1.5 * oldKnee / oldMiddle,
                text("Knie/Mitte jetzt", c.slope(0.9, 1.0) / c.slope(0.3, 0.8)) + text(" in 0.5.1", oldKnee / oldMiddle));
        assertTrue(Math.abs(c.at(0.9) - 3.58) < Math.abs(old.get(90)[1] - 3.58), text("bei 90 %", c.at(0.9)));
    }

    @Test
    void higherCRateSagsMoreAndDeliversLess() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.FREESTYLE);
        double[] rates = {1.0, 5.0, 15.0, 30.0};
        double lastCapacity = Double.POSITIVE_INFINITY;
        double lastMiddle = Double.POSITIVE_INFINITY;
        for (double rate : rates) {
            BatteryCurves.Curve c = BatteryCurves.discharge(p, rate, 3.1);
            assertTrue(c.capacity() < lastCapacity, text("Kapazität bei C", rate));
            assertTrue(c.at(0.5) < lastMiddle, text("Mitte bei C", rate));
            lastCapacity = c.capacity();
            lastMiddle = c.at(0.5);
        }
        assertTrue(BatteryCurves.discharge(p, 1.0, 3.1).capacity() >= 1.0);
        double high = BatteryCurves.discharge(p, 30.0, 3.1).capacity();
        assertTrue(high > 0.85 && high < 0.97, text("Kapazität bei 30 C", high));
    }

    @Test
    void liIonDeliversItsCapacityAtLowAndModerateRates() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.LONG_RANGE);
        BatteryCurves.Curve one = BatteryCurves.discharge(p, 1.0, 2.5);
        BatteryCurves.Curve five = BatteryCurves.discharge(p, 5.0, 2.5);
        assertTrue(one.capacity() >= 1.0, text("1 C", one.capacity()));
        assertTrue(five.capacity() > 0.88, text("5 C", five.capacity()));
        assertTrue(one.at(0.5) > 3.45 && one.at(0.5) < 3.65, text("Mitte bei 1 C", one.at(0.5)));
        assertTrue(one.slope(0.3, 0.8) > 0.45, text("Li-Ion fällt gleichmäßiger als LiPo", one.slope(0.3, 0.8)));
    }

    @Test
    void endOfPackPunchDropsTwoToThreeVoltsMoreAndGivesItBack() {
        BatteryCurves.Trace half = BatteryCurves.punch(DroneBuild.FREESTYLE, 0.5, 0.25, 1.0, 3.0);
        BatteryCurves.Trace end = BatteryCurves.punch(DroneBuild.FREESTYLE, 0.06, 0.25, 1.0, 3.0);
        assertTrue(half.armed() && end.armed(), "schaltet im Punch ab");
        assertTrue(end.before() / 6.0 > 3.45 && end.before() / 6.0 < 3.6, text("Schweben am Ende je Zelle", end.before() / 6.0));
        double halfDip = half.before() - half.at(1.0);
        double endDip = end.before() - end.at(1.0);
        assertTrue(endDip - halfDip > 1.5 && endDip - halfDip < 4.0, text("mehr Einbruch am Ende", endDip - halfDip) + text(" halb", halfDip));
        double back = end.at(2.0) - end.at(1.0);
        assertTrue(back > 2.0 && back < endDip, text("1 s nach dem Loslassen zurück", back) + text(" von", endDip));
        assertTrue(end.before() - end.at(4.0) < 0.3 * endDip, text("nach 3 s fehlen", end.before() - end.at(4.0)));
    }
}
