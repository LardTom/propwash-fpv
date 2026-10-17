package de.lardtom.propwash.video.vtx;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VtxPowerSettingsTest {
    private static final VtxTable ANALOG = VtxTable.of(false, 400, List.of(25, 200, 400, 800, 1600), 3.5, 8.0);
    private static final VtxTable DIGITAL = VtxTable.of(true, 700, List.of(25, 100, 400, 700, 1200), 9.0, 23.0);

    @Test
    void emptyTuneSelectsAutoOnEverySystem() {
        for (VtxTable table : List.of(ANALOG, DIGITAL)) {
            VtxPowerSettings.Selection selection = VtxPowerSettings.selection(Map.of(), table);
            assertTrue(selection.auto());
            assertEquals(table.ratedIndex(), selection.index());
            assertFalse(VtxPowerSettings.manual(Map.of()));
        }
        VtxTable single = VtxTable.of(false, 25, List.of(), 1.0, 3.0);
        assertFalse(VtxPowerSettings.selection(Map.of(), single).auto());
        assertEquals(VtxPowerSettings.LowPower.OFF, VtxPowerSettings.lowPower(Map.of()));
    }

    @Test
    void explicitLevelsStayManualEvenAtTheRatedLevel() {
        Map<String, Float> rated = VtxPowerSettings.withLevel(Map.of(), ANALOG.ratedMw());
        assertTrue(VtxPowerSettings.manual(rated));
        assertFalse(VtxPowerSettings.selection(rated, ANALOG).auto());
        assertEquals(ANALOG.ratedIndex(), VtxPowerSettings.selection(rated, ANALOG).index());
        Map<String, Float> copied = new TreeMap<>();
        VtxPowerSettings.copyClean(rated, copied);
        assertEquals(rated, copied);
        Map<String, Float> broken = new TreeMap<>(Map.of(VtxPowerSettings.POWER, Float.NaN));
        assertTrue(VtxPowerSettings.selection(broken, DIGITAL).auto());
    }

    @Test
    void storedLevelSnapsToTheCurrentModule() {
        Map<String, Float> tune = VtxPowerSettings.withLevel(Map.of(), 1600);
        assertEquals(4, VtxPowerSettings.selection(tune, ANALOG).index());
        VtxTable nano = VtxTable.of(false, 200, List.of(25, 100, 200, 400), 1.9, 4.0);
        assertEquals(3, VtxPowerSettings.selection(tune, nano).index());
    }

    @Test
    void autoWorksOnAnalogAndDigitalModules() {
        Map<String, Float> tune = VtxPowerSettings.withAuto(VtxPowerSettings.withLevel(Map.of(), 25));
        assertTrue(VtxPowerSettings.selection(tune, DIGITAL).auto());
        VtxPowerSettings.Selection analog = VtxPowerSettings.selection(tune, ANALOG);
        assertTrue(analog.auto());
        assertEquals(ANALOG.ratedIndex(), analog.index());
        assertFalse(tune.containsKey(VtxPowerSettings.POWER));
    }

    @Test
    void choosingALevelLeavesAuto() {
        Map<String, Float> tune = VtxPowerSettings.withLevel(VtxPowerSettings.withAuto(Map.of()), 100);
        assertFalse(VtxPowerSettings.auto(tune));
        assertEquals(1, VtxPowerSettings.selection(tune, DIGITAL).index());
    }

    @Test
    void switchCyclesThroughTheTableAndAuto() {
        Map<String, Float> tune = new TreeMap<>();
        StringBuilder seen = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            tune = VtxPowerSettings.next(tune, DIGITAL);
            VtxPowerSettings.Selection s = VtxPowerSettings.selection(tune, DIGITAL);
            seen.append(s.auto() ? "A" : String.valueOf(DIGITAL.levelMw(s.index()))).append(' ');
        }
        assertEquals("25 100 400 700 1200 A 25 ", seen.toString());
        Map<String, Float> analog = new TreeMap<>();
        StringBuilder analogSeen = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            analog = VtxPowerSettings.next(analog, ANALOG);
            VtxPowerSettings.Selection s = VtxPowerSettings.selection(analog, ANALOG);
            analogSeen.append(s.auto() ? "A" : String.valueOf(ANALOG.levelMw(s.index()))).append(' ');
        }
        assertEquals("25 200 400 800 1600 A 25 ", analogSeen.toString());
    }

    @Test
    void lowPowerModesRoundTrip() {
        for (VtxPowerSettings.LowPower mode : VtxPowerSettings.LowPower.values()) {
            Map<String, Float> tune = VtxPowerSettings.withLowPower(Map.of(), mode);
            assertEquals(mode, VtxPowerSettings.lowPower(tune));
        }
        assertFalse(VtxPowerSettings.withLowPower(Map.of(), VtxPowerSettings.LowPower.OFF).containsKey(VtxPowerSettings.LOW_POWER));
    }

    @Test
    void copyCleanKeepsOnlySaneValues() {
        Map<String, Float> dirty = new TreeMap<>();
        dirty.put(VtxPowerSettings.POWER, 99999.0F);
        dirty.put(VtxPowerSettings.AUTO, 0.0F);
        dirty.put(VtxPowerSettings.LOW_POWER, 7.0F);
        Map<String, Float> out = new TreeMap<>();
        VtxPowerSettings.copyClean(dirty, out);
        assertEquals(Map.of(VtxPowerSettings.POWER, (float) VtxTable.MAX_MW), out);
        Map<String, Float> nan = new TreeMap<>();
        nan.put(VtxPowerSettings.POWER, Float.NaN);
        nan.put(VtxPowerSettings.AUTO, 1.0F);
        nan.put(VtxPowerSettings.LOW_POWER, 2.0F);
        Map<String, Float> cleaned = new TreeMap<>();
        VtxPowerSettings.copyClean(nan, cleaned);
        assertEquals(Map.of(VtxPowerSettings.AUTO, 1.0F, VtxPowerSettings.LOW_POWER, 2.0F), cleaned);
    }
}
