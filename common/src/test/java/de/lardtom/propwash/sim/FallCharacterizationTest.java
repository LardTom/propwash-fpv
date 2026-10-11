package de.lardtom.propwash.sim;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FallCharacterizationTest {
    static final Set<String> ADDON_FIVE_INCH = Set.of("justmoreparts:bash5_6s", "justmoreparts:race5_6s", "justmoreparts:whoop75_1s",
            "justmoreparts:whoop85_2s", "justmoreparts:lr7_8s");

    static List<CalibrationHarness.Entry> entries() {
        List<CalibrationHarness.Entry> out = new ArrayList<>(CalibrationHarness.builtins());
        for (CalibrationHarness.Entry e : CalibrationHarness.addons()) {
            if (ADDON_FIVE_INCH.contains(e.name())) {
                out.add(e);
            }
        }
        return out;
    }

    @Test
    void fallBehaviourReport() {
        List<CalibrationHarness.Entry> entries = entries();
        StringBuilder out = new StringBuilder(FallCharacterization.header());
        List<FallCharacterization.Result> base = entries.parallelStream()
                .map(e -> FallCharacterization.measure(e.name(), e.build(), 1.0)).toList();
        List<FallCharacterization.Result> heavy = entries.parallelStream()
                .map(e -> FallCharacterization.measure(e.name(), e.build(), 1.2)).toList();
        for (FallCharacterization.Result r : base) {
            out.append(FallCharacterization.row(r));
        }
        for (FallCharacterization.Result r : heavy) {
            out.append(FallCharacterization.row(r));
        }
        CalibrationHarness.write("fall.txt", out.toString());
        System.out.print(out);
        for (int k = 0; k < base.size(); k++) {
            FallCharacterization.Result one = base.get(k);
            FallCharacterization.Result more = heavy.get(k);
            assertTrue(one.dropHeight[1] > 0.0 && one.dropHeight[1] < 0.5 * ModelConstants.G, one.name);
            assertTrue(more.dropHeight[1] > one.dropHeight[1], one.name);
            assertTrue(more.bellyIdle > one.bellyIdle, one.name);
            assertTrue(more.climbFull < one.climbFull, one.name);
        }
        FallCharacterization.Result whoop = base.getFirst();
        assertTrue(whoop.name.equals("propwash:whoop_65"), whoop.name);
        assertTrue(whoop.dropSpeed[1] >= 6.9 && whoop.dropSpeed[1] <= 7.9, "Whoop nach 1 s: " + whoop.dropSpeed[1]);
        assertTrue(whoop.bellyIdle >= 8.9 && whoop.bellyIdle <= 11.2, "Whoop Endgeschwindigkeit: " + whoop.bellyIdle);
    }
}
