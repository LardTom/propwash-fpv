package de.lardtom.propwash.build;

import de.lardtom.propwash.build.BatteryFit.Result;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatteryFitTest {
    private static Map<BatteryType, Result> row(Result b1s, Result b4s, Result b6s1100, Result b6s1300, Result b6s4000) {
        Map<BatteryType, Result> row = new LinkedHashMap<>();
        row.put(BatteryType.B1S_300_HV, b1s);
        row.put(BatteryType.B4S_650, b4s);
        row.put(BatteryType.B6S_1100, b6s1100);
        row.put(BatteryType.B6S_1300, b6s1300);
        row.put(BatteryType.B6S_4000_LION, b6s4000);
        return row;
    }

    private static Map<FrameType, Map<BatteryType, Result>> table() {
        Map<FrameType, Map<BatteryType, Result>> table = new LinkedHashMap<>();
        table.put(FrameType.WHOOP_65, row(Result.OK, Result.CELLS_HIGH, Result.CELLS_HIGH, Result.CELLS_HIGH, Result.CELLS_HIGH));
        table.put(FrameType.CINEWHOOP_3, row(Result.CELLS_LOW, Result.OK, Result.CELLS_HIGH, Result.CELLS_HIGH, Result.CELLS_HIGH));
        table.put(FrameType.FREESTYLE_5, row(Result.CELLS_LOW, Result.WEAK, Result.OK, Result.OK, Result.TOO_HEAVY));
        table.put(FrameType.RACE_5, row(Result.CELLS_LOW, Result.WEAK, Result.OK, Result.OK, Result.TOO_HEAVY));
        table.put(FrameType.LONG_RANGE_7, row(Result.CELLS_LOW, Result.CELLS_LOW, Result.WEAK, Result.OK, Result.OK));
        return table;
    }

    @Test
    void fullMatrixMatchesTheDesignTable() {
        Map<FrameType, Map<BatteryType, Result>> table = table();
        assertEquals(FrameType.values().length, table.size());
        for (FrameType frame : FrameType.values()) {
            for (BatteryType battery : BatteryType.values()) {
                assertEquals(table.get(frame).get(battery), BatteryFit.check(frame, battery), frame + " + " + battery);
            }
        }
    }

    @Test
    void everyFrameHasAnOkBattery() {
        for (FrameType frame : FrameType.values()) {
            assertFalse(BatteryFit.fitting(frame, Result.OK).isEmpty(), frame.name());
        }
    }

    @Test
    void allPresetsAreOk() {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            assertEquals(Result.OK, BatteryFit.check(preset.frame(), preset.battery()), preset.toString());
        }
    }

    @Test
    void fittingAndFramesForAreConsistent() {
        for (FrameType frame : FrameType.values()) {
            List<BatteryType> fitting = BatteryFit.fitting(frame);
            for (BatteryType battery : BatteryType.values()) {
                boolean allowed = BatteryFit.allowed(BatteryFit.check(frame, battery));
                assertEquals(allowed, fitting.contains(battery), frame + " / " + battery);
                assertEquals(allowed, BatteryFit.framesFor(battery).contains(frame), battery + " / " + frame);
            }
        }
        assertEquals(List.of(BatteryType.B6S_1100, BatteryType.B6S_1300), BatteryFit.fitting(FrameType.FREESTYLE_5, Result.OK));
        assertEquals(List.of(BatteryType.B4S_650), BatteryFit.fitting(FrameType.FREESTYLE_5, Result.WEAK));
    }

    @Test
    void reasonsAreCheckedInOrder() {
        BatteryFit.Rule whoop = BatteryFit.rule(FrameType.WHOOP_65);
        assertTrue(BatteryType.B6S_4000_LION.massGrams() > whoop.maxMassGrams());
        assertEquals(Result.CELLS_HIGH, BatteryFit.check(FrameType.WHOOP_65, BatteryType.B6S_4000_LION));
        assertEquals(Result.CELLS_LOW, BatteryFit.check(FrameType.LONG_RANGE_7, BatteryType.B4S_650));
        assertEquals(BatteryFit.WeakReason.CELLS, BatteryFit.weakReason(FrameType.FREESTYLE_5, BatteryType.B4S_650));
        assertEquals(BatteryFit.WeakReason.CAPACITY, BatteryFit.weakReason(FrameType.LONG_RANGE_7, BatteryType.B6S_1100));
        assertEquals(BatteryFit.WeakReason.NONE, BatteryFit.weakReason(FrameType.LONG_RANGE_7, BatteryType.B6S_1300));
        assertTrue(Result.WEAK.allowed());
        assertTrue(Result.OK.allowed());
        assertFalse(Result.TOO_HEAVY.allowed());
    }
}
