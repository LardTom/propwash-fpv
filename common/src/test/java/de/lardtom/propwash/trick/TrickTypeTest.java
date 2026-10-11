package de.lardtom.propwash.trick;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrickTypeTest {
    @Test
    void autoModeIsOnForFreestyleAndWhoopsOnly() {
        assertTrue(TrickMode.AUTO.enabledFor(DroneBuild.FREESTYLE));
        assertTrue(TrickMode.AUTO.enabledFor(DroneBuild.WHOOP));
        assertTrue(TrickMode.AUTO.enabledFor(DroneBuild.CINEWHOOP));
        assertFalse(TrickMode.AUTO.enabledFor(DroneBuild.RACER));
        assertFalse(TrickMode.AUTO.enabledFor(DroneBuild.LONG_RANGE));
        assertFalse(TrickMode.AUTO.enabledFor(null));
        assertTrue(TrickMode.ON.enabledFor(DroneBuild.RACER));
        assertFalse(TrickMode.OFF.enabledFor(DroneBuild.FREESTYLE));
    }

    @Test
    void configDefaultsToAutoAndRepairsNull() {
        TrickConfig config = new TrickConfig();
        assertEquals(TrickMode.AUTO, config.mode);
        config.mode = null;
        TrickConfig.sanitize(config);
        assertEquals(TrickMode.AUTO, config.mode);
        config.mode = TrickMode.OFF;
        assertEquals(TrickMode.OFF, config.copy().mode);
    }

    @Test
    void harderTricksAreWorthMore() {
        assertTrue(TrickType.DOUBLE_FLIP.basePoints() > 2 * TrickType.FLIP.basePoints());
        assertTrue(TrickType.TRIPLE_FLIP.basePoints() > TrickType.DOUBLE_FLIP.basePoints());
        assertTrue(TrickType.MATTY_FLIP.basePoints() > TrickType.POWER_LOOP.basePoints());
        assertTrue(TrickType.POWER_LOOP.basePoints() > TrickType.FLIP.basePoints());
        assertEquals(TrickType.FLIP, TrickType.flips(1));
        assertEquals(TrickType.DOUBLE_FLIP, TrickType.flips(2));
        assertEquals(TrickType.TRIPLE_FLIP, TrickType.flips(5));
        assertEquals(TrickType.DOUBLE_ROLL, TrickType.rolls(2));
    }

    @Test
    void scaledTricksStayWithinTheirCaps() {
        assertEquals(150.0, TrickType.DIVE.points(10.0, Double.POSITIVE_INFINITY));
        assertEquals(200.0, TrickType.DIVE.points(20.0, Double.POSITIVE_INFINITY));
        assertEquals(400.0, TrickType.DIVE.points(500.0, Double.POSITIVE_INFINITY));
        assertEquals(120.0, TrickType.INVERTED_HANG.points(0.9, Double.POSITIVE_INFINITY));
        assertEquals(120.0 + 80.0 * 2.1, TrickType.INVERTED_HANG.points(30.0, Double.POSITIVE_INFINITY), 1.0E-9);
        assertEquals(200.0, TrickType.GAP.points(2.2, 1.1));
        assertEquals(320.0, TrickType.GAP.points(0.5, 0.25), 1.0E-9);
        assertEquals(100.0, TrickType.PROXIMITY.points(0.35, 0.9));
        assertEquals(400.0 * 1.5, TrickType.PROXIMITY.points(10.0, 0.1), 1.0E-9);
        assertEquals(100.0, TrickType.FLIP.points(Double.NaN, Double.NaN));
    }

    @Test
    void keysAreUniqueAndLowerCase() {
        Set<String> keys = new HashSet<>();
        for (TrickType type : TrickType.values()) {
            assertTrue(keys.add(type.key()));
            assertEquals(type.key(), type.key().toLowerCase(java.util.Locale.ROOT));
            assertTrue(type.translationKey().startsWith("propwash.trick.type."));
        }
    }

    @Test
    void turnsAreCountedWithTolerance() {
        assertEquals(0, TrickDetector.turns(200.0));
        assertEquals(1, TrickDetector.turns(300.0));
        assertEquals(1, TrickDetector.turns(560.0));
        assertEquals(2, TrickDetector.turns(650.0));
        assertEquals(3, TrickDetector.turns(1000.0));
        assertEquals(3, TrickDetector.turns(5000.0));
    }
}
