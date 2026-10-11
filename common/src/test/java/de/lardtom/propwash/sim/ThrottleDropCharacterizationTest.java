package de.lardtom.propwash.sim;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ThrottleDropCharacterizationTest {
    static final String BEFORE = "whoop75 Umriss 0,011";
    static final double T10_SLACK_S = 0.08;
    static final double POWERED_TOLERANCE = 0.10;

    @Test
    void throttleDropReport() {
        List<ThrottleDropCharacterization.Entry> entries = ThrottleDropCharacterization.entries();
        StringBuilder out = new StringBuilder(ThrottleDropCharacterization.header());
        List<ThrottleDropCharacterization.Result> results = entries.parallelStream()
                .map(e -> ThrottleDropCharacterization.measure(e, new SimTuning())).toList();
        for (ThrottleDropCharacterization.Result r : results) {
            out.append(ThrottleDropCharacterization.rows(r));
        }
        CalibrationHarness.write("throttle_drop.txt", out.toString());
        System.out.print(out);
        List<String> failures = new ArrayList<>();
        for (ThrottleDropCharacterization.Result r : results) {
            ThrottleDropCharacterization.Step cut = r.steps.getFirst();
            ThrottleDropCharacterization.Expected cutRef = r.expected.getFirst();
            if (r.name.equals(BEFORE)) {
                check(failures, cut.steady < cutRef.steadyLow, r.name, "fällt mit dem alten Umriss-Wert nicht langsamer als die Referenz", cut.steady);
                continue;
            }
            check(failures, Math.abs(r.hoverStick - r.referenceHoverStick) < 0.02, r.name, "Schwebegas", 100.0 * r.hoverStick);
            check(failures, cut.steady >= cutRef.steadyLow && cut.steady <= cutRef.steadyHigh, r.name, "Endgeschwindigkeit bei 0 %", cut.steady);
            check(failures, Math.abs(cut.t10 - cutRef.t10) <= T10_SLACK_S, r.name, "10 m bei 0 %", cut.t10);
            for (int k = 1; k < r.steps.size(); k++) {
                ThrottleDropCharacterization.Step s = r.steps.get(k);
                ThrottleDropCharacterization.Expected e = r.expected.get(k);
                if (e.steadySink > ThrottleDropCharacterization.measuredSinkLimit()) {
                    continue;
                }
                String what = String.format(Locale.ROOT, "%.0f %% Gas", 100.0 * s.stick);
                check(failures, Math.abs(s.steady / e.steady - 1.0) <= POWERED_TOLERANCE, r.name, "Sinken bei " + what, s.steady);
                check(failures, Math.abs(s.t10 / e.t10 - 1.0) <= POWERED_TOLERANCE, r.name, "10 m bei " + what, s.t10);
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    static void check(List<String> failures, boolean ok, String name, String what, double value) {
        if (!ok) {
            failures.add(String.format(Locale.ROOT, "%s: %s %.2f", name, what, value));
        }
    }
}
