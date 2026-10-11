package de.lardtom.propwash.sim;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.calibration.Flight;
import de.lardtom.propwash.calibration.Maneuvers;
import de.lardtom.propwash.calibration.Measure;
import de.lardtom.propwash.calibration.Signals;
import de.lardtom.propwash.calibration.Trace;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealLogSimTest {
    static final double RATE = 1000.0;
    static final int GROUND_SAMPLES = 300;
    static final double GROUND_GAP = 30.0;
    static final String WHOOP = """
            {"frame": {"base": "propwash:whoop_65"}, "stack": "propwash:aio_whoop", "motor": "propwash:m0802_22000", "prop": "propwash:p31_4b",
             "video": "propwash:analog_nano", "battery": "propwash:b1s_300_hv"}""";
    static final String FREESTYLE = """
            {"frame": "propwash:freestyle_5", "stack": "propwash:stack_30", "motor": {"base": "propwash:m2207_1750", "set": {"kv": 1800}},
             "prop": "propwash:p51_3b", "video": "propwash:digital_hd", "battery": "propwash:b6s_1300"}""";

    static JsonObject parts(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void buildsMatchTheTargetMassAndKeepTheirParts() {
        double base = AirframeSpec.of(DroneBuild_WHOOP()).massKg * 1000.0;
        RealLogBuilds.Built b = RealLogBuilds.build("whoop", parts(WHOOP), base + 3.0);
        assertEquals(base + 3.0, b.massG(), 0.05);
        assertEquals(AirframeSpec.of(DroneBuild_WHOOP()).kv, AirframeSpec.of(b.build()).kv, 1e-9);
        RealLogBuilds.Built f = RealLogBuilds.build("freestyle", parts(FREESTYLE), 650.0);
        assertEquals(650.0, f.massG(), 0.05);
        assertEquals(1800.0 * 2.0 * Math.PI / 60.0, AirframeSpec.of(f.build()).kv, 1e-6);
    }

    static de.lardtom.propwash.build.DroneBuild DroneBuild_WHOOP() {
        return de.lardtom.propwash.build.DroneBuild.WHOOP;
    }

    record Recorded(Flight flight, double hoverDuty, double voltage) {
    }

    static Recorded fly(RealLogSim sim, double[][] schedule) {
        FlatWorld w = sim.world(1.0);
        OperatingPointsHover hover = OperatingPointsHover.of(sim);
        RealLogSim.hold(w, hover.duty, 1.0);
        int n = 0;
        for (double[] s : schedule) {
            n += (int) Math.round(s[1] * RATE);
        }
        double[] duty = new double[n];
        double[] fz = new double[n];
        double[] rpm = new double[n];
        double[] vbat = new double[n];
        double[] baro = new double[n];
        Signals.Pt2 filter = new Signals.Pt2(DroneSim.PHYSICS_HZ, 25.0);
        filter.apply(RealLogSim.bodyUpSpecific(w.sim));
        double value = 0.0;
        int i = 0;
        for (double[] s : schedule) {
            double d = s[0] < 0.0 ? hover.duty : s[0];
            for (int k = 0; k < (int) Math.round(s[1] * RATE); k++) {
                w.sim.setDutyOverride(d, d, d, d);
                for (int sub = 0; sub < 2; sub++) {
                    RealLogSim.step(w);
                    value = filter.apply(RealLogSim.bodyUpSpecific(w.sim));
                }
                duty[i] = d;
                fz[i] = value;
                rpm[i] = RealLogSim.meanRpm(w.sim);
                vbat[i] = w.sim.power.busVoltage;
                baro[i] = w.y;
                i++;
            }
        }
        Flight f = Flight.synthetic(RATE, duty, fz, rpm, vbat, 25.0);
        for (int k = 0; k < n; k++) {
            f.baro[k] = k < GROUND_SAMPLES ? baro[0] : baro[k] + GROUND_GAP;
        }
        f.hasBaro = true;
        return new Recorded(f, hover.duty, hover.voltage);
    }

    record OperatingPointsHover(double duty, double voltage) {
        static OperatingPointsHover of(RealLogSim sim) {
            OperatingPoints.Point p = new OperatingPoints.Point();
            OperatingPoints.hover(sim.params, 1.0, false, 0.0, true, p);
            return new OperatingPointsHover(p.duty, p.busVoltage);
        }
    }

    @Test
    void theSimReplayingItsOwnFlightMeasuresTheSame() {
        for (String json : List.of(WHOOP, FREESTYLE)) {
            RealLogSim sim = new RealLogSim(RealLogBuilds.build("self", parts(json), json == WHOOP ? 30.0 : 650.0).build());
            Recorded r = fly(sim, new double[][]{{-1, 4.0}, {0.85, 0.5}, {-1, 5.0}, {0.06, 0.8}, {-1, 4.0}});
            Maneuvers.Found m = Maneuvers.find(r.flight());
            assertEquals(1, m.punches.size(), m.rejected.toString());
            assertEquals(1, m.cuts.size(), m.rejected.toString());
            assertTrue(m.hoverSeconds > 1.0, "Schweben " + m.hoverSeconds);
            assertEquals(sim.hoverDuty(m.hoverVbat), m.hoverDuty, 0.03 * m.hoverDuty);
            Maneuvers.Event p = m.punches.getFirst();
            Trace real = Trace.of(r.flight(), p.from(), p.to(), p.step());
            Measure.Punch a = Measure.punch(real);
            Measure.Punch b = Measure.punch(sim.replay(real, 25.0).trace());
            assertEquals(a.peakFz(), b.peakFz(), 0.03 * a.peakFz());
            assertEquals(a.rpmT63Ms(), b.rpmT63Ms(), 3.0);
            assertEquals(a.sagVolts(), b.sagVolts(), 0.05 + 0.1 * a.sagVolts());
            Maneuvers.Event c = m.cuts.getFirst();
            Trace realCut = Trace.of(r.flight(), c.from(), c.to(), c.step());
            Measure.Cut x = Measure.cut(realCut);
            Measure.Cut y = Measure.cut(sim.replay(realCut, 25.0).trace());
            assertEquals(x.fzEarly(), y.fzEarly(), 0.03);
            assertEquals(x.rpmT63Ms(), y.rpmT63Ms(), 3.0);
        }
    }

    @Test
    void sourcesTableIsComplete() throws Exception {
        Path file = Path.of(System.getProperty("user.dir")).resolve("tools/bbcal/sources.json");
        if (!Files.exists(file)) {
            file = Path.of(System.getProperty("user.dir")).getParent().resolve("tools/bbcal/sources.json");
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(file), "tools/bbcal/sources.json nicht gefunden");
        Set<String> ids = new HashSet<>();
        int classes = 0;
        Set<String> seen = new HashSet<>();
        for (RealLogCalibration.Source s : RealLogCalibration.sources(file)) {
            assertTrue(ids.add(s.id()), "doppelt: " + s.id());
            assertTrue(RealLogReport.CLASSES.containsKey(s.sizeClass()), s.id() + ": " + s.sizeClass());
            assertTrue(s.url().startsWith("https://github.com/"), s.id());
            assertTrue(s.auwG() > s.toleranceG() && s.toleranceG() > 0.0, s.id());
            assertTrue(List.of("hoch", "mittel", "niedrig").contains(s.confidence()), s.id());
            if (seen.add(s.sizeClass())) {
                classes++;
            }
            if (!RealLogBuilds.needsAddon(s.parts()) || CalibrationHarness.addonRoot().isPresent()) {
                RealLogBuilds.Built b = RealLogBuilds.build(s.id(), s.parts(), s.auwG());
                assertEquals(s.auwG(), b.massG(), 0.05, s.id());
            }
        }
        assertTrue(ids.size() >= 10, "zu wenige Logs: " + ids.size());
        assertTrue(classes >= 5, "zu wenige Größen: " + classes);
    }
}
