package de.lardtom.propwash.dev;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationLapCountTest {
    @Test
    void cleanLapCountsOnTheBoard() {
        assertEquals("", MigrationValues.lapCounted(2, 3, null, false, false));
        assertNull(MigrationValues.lapCounted(2, 2, null, false, false), "saubere Runde ohne Eintrag");
    }

    @Test
    void assistedLapCountsWhenSaved() {
        String counted = MigrationValues.lapCounted(0, 0, "[WORLD_PHYSICS]", true, true);
        assertNotNull(counted);
        assertTrue(counted.contains("[WORLD_PHYSICS]"), counted);
        assertNull(MigrationValues.lapCounted(0, 0, null, true, true), "Regel an, aber kein Eintrag mit Flughilfe");
    }

    @Test
    void assistedLapIsAcceptedWithoutSavingWhenTheRuleIsOff() {
        String counted = MigrationValues.lapCounted(0, 0, null, true, false);
        assertNotNull(counted, "geänderte Welt-Regeln in der erzeugten Testwelt: Runde mit Flughilfe, Regel save_assisted_laps aus");
        assertTrue(counted.contains("save_assisted_laps"), counted);
    }
}
