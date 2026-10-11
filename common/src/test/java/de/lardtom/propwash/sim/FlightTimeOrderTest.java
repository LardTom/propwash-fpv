package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FlightTimeOrderTest {
    static List<DroneBuild> singleSwaps(DroneBuild b) {
        List<DroneBuild> out = new ArrayList<>();
        out.add(b);
        for (FrameType c : FrameType.all()) {
            out.add(new DroneBuild(c, b.stack(), b.motor(), b.prop(), b.video(), b.battery()));
        }
        for (StackType c : StackType.all()) {
            if (!c.creative()) {
                out.add(new DroneBuild(b.frame(), c, b.motor(), b.prop(), b.video(), b.battery()));
            }
        }
        for (MotorType c : MotorType.all()) {
            out.add(new DroneBuild(b.frame(), b.stack(), c, b.prop(), b.video(), b.battery()));
        }
        for (PropType c : PropType.all()) {
            out.add(new DroneBuild(b.frame(), b.stack(), b.motor(), c, b.video(), b.battery()));
        }
        for (VideoType c : VideoType.all()) {
            out.add(new DroneBuild(b.frame(), b.stack(), b.motor(), b.prop(), c, b.battery()));
        }
        for (BatteryType c : BatteryType.all()) {
            out.add(new DroneBuild(b.frame(), b.stack(), b.motor(), b.prop(), b.video(), c));
        }
        return out;
    }

    static String key(DroneBuild b) {
        return b.frame().definition().id() + "/" + b.stack().definition().id() + "/" + b.motor().definition().id() + "/"
                + b.prop().definition().id() + "/" + b.video().definition().id() + "/" + b.battery().definition().id();
    }

    static Map<String, DroneBuild> variants(List<CalibrationHarness.Entry> presets) {
        Map<String, DroneBuild> out = new LinkedHashMap<>();
        for (CalibrationHarness.Entry preset : presets) {
            for (DroneBuild build : singleSwaps(preset.build())) {
                if (build.buildable()) {
                    out.putIfAbsent(key(build), build);
                }
            }
        }
        return out;
    }

    static List<String> violations(Map<String, DroneBuild> builds) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, DroneBuild> e : builds.entrySet()) {
            BuildAnalysis a = BuildAnalysis.of(e.getValue());
            double hover = a.hoverFlightTimeMin();
            double cruise = a.cruiseFlightTimeMin();
            double mixed = a.mixedFlightTimeMin();
            double aggressive = a.aggressiveFlightTimeMin();
            if (!Double.isFinite(hover)) {
                if (Double.isFinite(cruise) || Double.isFinite(mixed) || Double.isFinite(aggressive)) {
                    out.add(e.getKey() + ": Flugzeit ohne Schweben");
                }
                continue;
            }
            if (!(Double.isFinite(cruise) && Double.isFinite(mixed) && Double.isFinite(aggressive))) {
                out.add(e.getKey() + ": Flugzeit fehlt");
                continue;
            }
            if (!(aggressive <= mixed && mixed <= cruise && cruise <= hover)) {
                out.add(String.format(Locale.ROOT, "%s: aggressiv %.3f, gemischt %.3f, Reisen %.3f, Schweben %.3f", e.getKey(), aggressive, mixed,
                        cruise, hover));
            }
        }
        return out;
    }

    @Test
    void everyBuiltinCombinationKeepsTheFlightTimeOrder() {
        Map<String, DroneBuild> builds = new LinkedHashMap<>();
        for (DroneBuild build : BuiltinRegressionTest.buildableCombinations()) {
            builds.put(key(build), build);
        }
        for (DroneBuild build : BuiltinRegressionTest.presetVariants()) {
            builds.put(key(build) + "+" + build.accessories(), build);
        }
        assertTrue(builds.size() > 500, "Kombinationen " + builds.size());
        assertEquals(List.of(), violations(builds));
    }

    @Test
    void everySingleSwapOfTheBuiltinPresetsKeepsTheFlightTimeOrder() {
        Map<String, DroneBuild> builds = variants(CalibrationHarness.builtins());
        assertTrue(builds.size() >= 40, "Varianten " + builds.size());
        assertEquals(List.of(), violations(builds));
    }

    @Test
    @Tag(CalibrationHarness.ADDON_TAG)
    void everySingleSwapOfTheAddonPresetsKeepsTheFlightTimeOrder() {
        assumeTrue(CalibrationHarness.addonRoot().isPresent(), CalibrationHarness.ADDON_HINT);
        List<CalibrationHarness.Entry> presets = new ArrayList<>(CalibrationHarness.builtins());
        presets.addAll(CalibrationHarness.addons());
        Map<String, DroneBuild> builds = variants(presets);
        assertTrue(builds.size() > 1000, "Varianten " + builds.size());
        assertEquals(List.of(), violations(builds));
    }
}
