package de.lardtom.propwash.dev;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfTestSelectionTest {
    private static final List<String> ORDER = List.of(
            "dev: Basis", "eins: a", "eins: b", "zwei: vorbereiten", "zwei: x", "zwei: y", "drei: Deutsch", "drei: z",
            "drei: zurück", "vier: w", "eins: nach dem Verlassen", "vier: nach dem Verlassen");
    private static final Set<String> FRAMING = Set.of("dev: Basis", "zwei: vorbereiten", "drei: Deutsch", "drei: zurück");

    @BeforeAll
    static void register() {
        if (SelfTest.registeredCount() > 0) {
            return;
        }
        SelfTest.ClientStep pass = ctx -> SelfTest.StepResult.pass();
        SelfTest.baseStep("dev: Basis", 1, pass);
        SelfTest.clientStep("eins: a", 1, pass);
        SelfTest.clientStep("eins: b", 1, pass);
        SelfTest.setupStep("zwei: vorbereiten", 1, pass);
        SelfTest.clientStep("zwei: x", 1, pass);
        SelfTest.clientStep("zwei: y", 1, pass);
        SelfTest.switchStep("drei: Deutsch", "drei: zurück", 1, pass);
        SelfTest.clientStep("drei: z", 1, pass);
        SelfTest.clientStep("drei: zurück", 1, pass);
        SelfTest.clientStep("vier: w", 1, pass);
        SelfTest.afterLeaveStep("eins: nach dem Verlassen", 1, pass);
        SelfTest.afterLeaveStep("vier: nach dem Verlassen", 1, pass);
    }

    @Test
    void registeredOrderIsTheListPosition() {
        assertEquals(ORDER, SelfTest.selectedNames(null));
        assertEquals(ORDER.size(), SelfTest.registeredCount());
    }

    @Test
    void rangeSelectsPositionsAndBaseSteps() {
        assertEquals(List.of("dev: Basis", "eins: a", "eins: b"), SelfTest.selectedNames("#2-3"));
        assertEquals(List.of("dev: Basis", "eins: b"), SelfTest.selectedNames("# 3"));
        assertEquals(List.of("dev: Basis", "eins: a", "eins: b"), SelfTest.selectedNames("#3-2"));
    }

    @Test
    void rangeKeepsSetupAndSwitchStepsOfItsGroups() {
        assertEquals(List.of("dev: Basis", "zwei: vorbereiten", "zwei: y"), SelfTest.selectedNames("#6"));
        assertEquals(List.of("dev: Basis", "drei: Deutsch", "drei: z", "drei: zurück"), SelfTest.selectedNames("#8"));
    }

    @Test
    void rangeReachesStepsAfterLeavingTheWorld() {
        assertEquals(List.of("dev: Basis", "vier: w", "eins: nach dem Verlassen", "vier: nach dem Verlassen"),
                SelfTest.selectedNames("#10-12"));
    }

    @Test
    void rangesAndPrefixesCombine() {
        assertEquals(List.of("dev: Basis", "eins: a", "vier: w", "vier: nach dem Verlassen"), SelfTest.selectedNames("vier:,#2"));
        assertEquals(List.of("dev: Basis", "eins: a", "eins: b", "eins: nach dem Verlassen"), SelfTest.selectedNames("#2-3|#11"));
    }

    @Test
    void excludedRangeAndPrefixWin() {
        assertEquals(List.of("dev: Basis", "eins: a", "eins: b", "zwei: vorbereiten", "drei: Deutsch", "drei: z", "drei: zurück",
                "vier: w", "eins: nach dem Verlassen", "vier: nach dem Verlassen"), SelfTest.selectedNames("!#5-6"));
        assertEquals(List.of("dev: Basis", "eins: b"), SelfTest.selectedNames("!eins: a,#2-3"));
        assertEquals(List.of(), SelfTest.selectedNames("!eins:,#2"));
        assertEquals(List.of("dev: Basis", "drei: Deutsch", "drei: z", "drei: zurück", "vier: w", "eins: nach dem Verlassen",
                "vier: nach dem Verlassen"), SelfTest.selectedNames("!eins: a,!eins: b,!zwei:"));
    }

    @Test
    void rangeOutsideTheListSelectsNothing() {
        assertEquals(List.of(), SelfTest.selectedNames("#99"));
        assertEquals(List.of(), SelfTest.selectedNames("#0"));
        assertEquals(List.of(), SelfTest.selectedNames("#zwei"));
    }

    @Test
    void contiguousShardsCoverEveryStepOnceAndRepeatOnlyFramingSteps() {
        List<String> shards = List.of("#1-6", "#7-9,#11", "#10,#12");
        Map<String, Integer> seen = new HashMap<>();
        for (String shard : shards) {
            for (String name : SelfTest.selectedNames(shard)) {
                seen.merge(name, 1, Integer::sum);
            }
        }
        List<String> wrong = new ArrayList<>();
        for (String name : ORDER) {
            int count = seen.getOrDefault(name, 0);
            if (count == 0 || count > 1 && !FRAMING.contains(name)) {
                wrong.add(name + " × " + count);
            }
        }
        assertTrue(wrong.isEmpty(), wrong.toString());
        assertEquals(3, seen.get("dev: Basis"));
    }
}
