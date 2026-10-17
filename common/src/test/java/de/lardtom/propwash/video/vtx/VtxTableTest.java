package de.lardtom.propwash.video.vtx;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VtxTableTest {
    @Test
    void declaredLevelsAreUsedAndKeepTheRatedLevel() {
        VtxTable race = VtxTable.of(false, 400, List.of(25, 200, 400, 800, 1600), 3.5, 8.0);
        assertArrayEquals(new int[]{25, 200, 400, 800, 1600}, race.levels());
        assertEquals(2, race.ratedIndex());
        assertEquals(400, race.ratedMw());
        assertEquals(1600, race.maxMw());
        assertTrue(race.supportsAuto());
    }

    @Test
    void ratedLevelKeepsTheOldBehaviourBitForBit() {
        VtxTable hd = VtxTable.of(true, 700, List.of(25, 100, 400, 700, 1200), 9.0, 23.0);
        int rated = hd.ratedIndex();
        assertEquals(0.0, hd.gainDb(rated));
        assertEquals(1.0, hd.reachFactor(rated));
        assertEquals(0.0, hd.extraW(rated));
        assertEquals(Double.doubleToRawLongBits(9.0), Double.doubleToRawLongBits(hd.supplyW(rated)));
    }

    @Test
    void derivedLaddersFollowRealVtxTables() {
        assertArrayEquals(new int[]{25, 200, 400, 800}, VtxTable.of(false, 800, List.of(), 5.0, 9.5).levels());
        assertArrayEquals(new int[]{25, 200, 400, 800, 1600}, VtxTable.of(false, 1600, List.of(), 8.5, 16.0).levels());
        assertArrayEquals(new int[]{25}, VtxTable.of(false, 25, List.of(), 0.7, 2.2).levels());
        assertArrayEquals(new int[]{25, 100, 200}, VtxTable.of(true, 200, List.of(), 5.1, 4.8).levels());
        assertArrayEquals(new int[]{25, 100, 400, 500}, VtxTable.of(true, 500, List.of(), 5.8, 9.8).levels());
        assertArrayEquals(new int[]{25, 100, 400, 700, 1200}, VtxTable.of(true, 1200, List.of(), 10.0, 19.0).levels());
        assertArrayEquals(new int[]{25, 200, 400, 800, 1600, 2000}, VtxTable.of(false, 2000, List.of(), 9.0, 20.0).levels());
    }

    @Test
    void missingRatedPowerFallsBackToTwoHundredMilliwatts() {
        VtxTable table = VtxTable.of(false, 0, List.of(), 2.0, 4.0);
        assertEquals(VtxTable.DEFAULT_RATED_MW, table.ratedMw());
        assertArrayEquals(new int[]{25, 100, 200}, table.levels());
    }

    @Test
    void gainAndReachFollowFreeSpace() {
        VtxTable race = VtxTable.of(false, 400, List.of(25, 200, 400, 800, 1600), 3.5, 8.0);
        assertEquals(6.02, race.gainDb(4), 0.01);
        assertEquals(-12.04, race.gainDb(0), 0.01);
        assertEquals(2.0, race.reachFactor(4), 1.0E-9);
        assertEquals(0.25, race.reachFactor(0), 1.0E-9);
        assertEquals(2.0, race.maxReachFactor(), 1.0E-9);
    }

    @Test
    void supplyFollowsTheMeasuredSlopes() {
        VtxTable race = VtxTable.of(false, 400, List.of(25, 200, 400, 800, 1600), 3.5, 8.0);
        assertEquals(3.5 + 4.3 * 1.2, race.supplyW(4), 1.0E-9);
        assertEquals(3.5 - 4.3 * 0.375, race.supplyW(0), 1.0E-9);
        assertEquals(4.3 * 1.2, race.extraW(4), 1.0E-9);
        VtxTable hd = VtxTable.of(true, 700, List.of(25, 100, 400, 700, 1200), 9.0, 23.0);
        assertEquals(9.0 - 2.5 * 0.675, hd.supplyW(0), 1.0E-9);
        assertEquals(9.0 + 2.5 * 0.5, hd.supplyW(4), 1.0E-9);
        assertTrue(hd.heatW(4) > hd.heatW(0));
        assertTrue(race.heatW(0) < race.supplyW(0));
    }

    @Test
    void supplyNeverDropsBelowAFloor() {
        VtxTable tiny = VtxTable.of(false, 400, List.of(25, 400), 0.5, 2.0);
        assertEquals(VtxTable.MIN_SUPPLY_W, tiny.supplyW(0), 1.0E-9);
        assertTrue(tiny.heatW(0) >= 0.0);
    }

    @Test
    void nearestIndexSnapsOnALogScale() {
        VtxTable race = VtxTable.of(false, 400, List.of(25, 200, 400, 800, 1600), 3.5, 8.0);
        assertEquals(3, race.nearestIndex(700));
        assertEquals(4, race.nearestIndex(2000));
        assertEquals(0, race.nearestIndex(10));
        assertEquals(race.ratedIndex(), race.nearestIndex(Double.NaN));
        VtxTable nano = VtxTable.of(false, 200, List.of(25, 100, 200, 400), 1.9, 4.0);
        assertEquals(3, nano.nearestIndex(1600));
    }

    @Test
    void invalidDeclaredLevelsAreIgnoredAndTheRatedLevelIsAdded() {
        VtxTable table = VtxTable.of(false, 300, List.of(-5, 25, 25, 20000, 600), 3.0, 8.0);
        assertArrayEquals(new int[]{25, 300, 600}, table.levels());
        assertEquals(1, table.ratedIndex());
    }

    @Test
    void autoNeedsMoreThanOneLevel() {
        assertTrue(VtxTable.of(true, 700, List.of(), 9.0, 23.0).supportsAuto());
        assertFalse(VtxTable.of(true, 25, List.of(), 4.0, 5.0).supportsAuto());
        assertTrue(VtxTable.of(false, 800, List.of(), 5.0, 9.0).supportsAuto());
        assertFalse(VtxTable.of(false, 25, List.of(), 1.0, 3.0).supportsAuto());
    }
}
