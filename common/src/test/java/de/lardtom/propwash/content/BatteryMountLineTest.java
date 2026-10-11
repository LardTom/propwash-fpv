package de.lardtom.propwash.content;

import de.lardtom.propwash.build.FrameType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatteryMountLineTest {
    @Test
    void onlyFramesThatCarryTheBatteryUnderInsteadOfOnTopSayIt() {
        assertTrue(PartDataLines.batteryUnderInsteadOfOnTop(FrameType.RACE_5.definition()));
        assertFalse(PartDataLines.batteryUnderInsteadOfOnTop(FrameType.WHOOP_65.definition()));
        assertFalse(PartDataLines.batteryUnderInsteadOfOnTop(FrameType.FREESTYLE_5.definition()));
        assertFalse(PartDataLines.batteryUnderInsteadOfOnTop(FrameType.LONG_RANGE_7.definition()));
    }
}
