package de.lardtom.propwash.sim;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CalibrationHarnessTest {
    @Test
    void report() {
        List<CalibrationHarness.Entry> all = new ArrayList<>(CalibrationHarness.builtins());
        all.addAll(CalibrationHarness.addons());
        all.addAll(CalibrationHarness.owner());
        String text = CalibrationHarness.report(all);
        CalibrationHarness.write("calibration.txt", text);
        System.out.println(text);
    }

    @Test
    void builtinPresetsFlyWithinTheirClassReferences() {
        List<String> out = new ArrayList<>();
        for (CalibrationHarness.Entry e : CalibrationHarness.builtins()) {
            out.addAll(CalibrationHarness.violations(e));
        }
        assertEquals(List.of(), out);
    }

    @Test
    void ownerWhoopMatchesTheReferenceAnswer() {
        List<CalibrationHarness.Entry> owner = CalibrationHarness.owner();
        BuildAnalysis base = BuildAnalysis.of(owner.getFirst().build());
        List<String> out = new ArrayList<>();
        check(out, "Schweben (digital)", base.hoverFlightTimeMin(), 3.0, 10.0 / 3.0);
        check(out, "Reisen", base.cruiseFlightTimeMin(), 3.0, 3.5);
        check(out, "Gemischt", base.mixedFlightTimeMin(), 2.0, 2.5);
        check(out, "Aggressiv", base.aggressiveFlightTimeMin(), 1.5, 2.0);
        check(out, "Landespannung", base.landingCellVoltage(), 3.3, 3.5);
        check(out, "Gewicht", base.massGrams(), 25.0, 30.0);
        check(out, "Vollgasstrom", base.fullThrottleCurrentA(), 12.0, 16.0);
        BuildAnalysis lowKv = BuildAnalysis.of(owner.get(1).build());
        BuildAnalysis threeBlade = BuildAnalysis.of(owner.get(2).build());
        BuildAnalysis twoBlade = BuildAnalysis.of(owner.get(3).build());
        BuildAnalysis analog = BuildAnalysis.of(owner.get(4).build());
        check(out, "23000 KV gemischt länger", lowKv.mixedFlightTimeMin() / base.mixedFlightTimeMin(), 1.15, 1.25);
        check(out, "23000 KV aggressiv länger", lowKv.aggressiveFlightTimeMin() / base.aggressiveFlightTimeMin(), 1.15, 1.25);
        check(out, "23000 KV weniger Strom", base.mixedCurrentA() - lowKv.mixedCurrentA(), 0.3, 3.0);
        for (CalibrationHarness.Style style : CalibrationHarness.Style.values()) {
            check(out, "3-Blatt " + style.label + " länger", CalibrationHarness.minutes(threeBlade, style) / CalibrationHarness.minutes(base, style), 1.10,
                    1.20);
            check(out, "2-Blatt " + style.label + " länger", CalibrationHarness.minutes(twoBlade, style) / CalibrationHarness.minutes(base, style), 1.10,
                    style == CalibrationHarness.Style.HOVER || style == CalibrationHarness.Style.CRUISE ? 1.20 : 1.30);
        }
        check(out, "3-Blatt gemischt länger [s]", 60.0 * (threeBlade.mixedFlightTimeMin() - base.mixedFlightTimeMin()), 15.0, 30.0);
        check(out, "2-Blatt gemischt länger [s]", 60.0 * (twoBlade.mixedFlightTimeMin() - base.mixedFlightTimeMin()), 15.0, 30.0);
        check(out, "2-Blatt aggressiv länger [s]", 60.0 * (twoBlade.aggressiveFlightTimeMin() - base.aggressiveFlightTimeMin()), 15.0, 30.0);
        check(out, "2-Blatt länger als 3-Blatt", twoBlade.mixedFlightTimeMin() - threeBlade.mixedFlightTimeMin(), 0.0, 1.0);
        check(out, "digital statt analog kürzer schweben", 1.0 - base.hoverFlightTimeMin() / analog.hoverFlightTimeMin(), 0.30, 0.35);
        assertEquals(List.of(), out);
    }

    @Test
    void ownerWhoopKeepsItsFlightPhysics() {
        BuildAnalysis base = BuildAnalysis.of(CalibrationHarness.owner().getFirst().build());
        assertEquals(4.1, base.thrustToWeight(), 0.05);
        assertEquals(43.8, base.hoverThrottlePercent(), 0.1);
    }

    private static void check(List<String> out, String what, double value, double low, double high) {
        if (!(value >= low && value <= high)) {
            out.add(what + " " + value + " außerhalb " + low + "–" + high);
        }
    }

    @Test
    @Tag(CalibrationHarness.ADDON_TAG)
    void addonPresetsFlyWithinTheirClassReferences() {
        assumeTrue(CalibrationHarness.addonRoot().isPresent(), CalibrationHarness.ADDON_HINT);
        List<String> out = new ArrayList<>();
        for (CalibrationHarness.Entry e : CalibrationHarness.addons()) {
            out.addAll(CalibrationHarness.violations(e));
        }
        assertEquals(List.of(), out);
    }
}
