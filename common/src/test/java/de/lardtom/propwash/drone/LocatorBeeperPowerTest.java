package de.lardtom.propwash.drone;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocatorBeeperPowerTest {
    @Test
    void aBeepingDroneRunsItsElectronicsAndEmptiesTheBatterySlowly() {
        double racer = LocatorBeeperPower.secondsFromFull(DroneBuild.RACER) / 3600.0;
        double whoop = LocatorBeeperPower.secondsFromFull(DroneBuild.WHOOP) / 60.0;
        assertTrue(racer > 3.0 && racer < 9.0, "5-Zoll mit 6S 1100 mAh: " + racer + " h");
        assertTrue(whoop > 12.0 && whoop < 45.0, "Whoop mit 1S 300 mAh: " + whoop + " min");
        assertTrue(LocatorBeeperPower.watts(DroneBuild.RACER) > LocatorBeeperPower.BUZZER_W);
    }

    @Test
    void drainPerTickMatchesTheRateAndStaysBelowTheSyncStep() {
        for (DroneBuild build : new DroneBuild[]{DroneBuild.WHOOP, DroneBuild.CINEWHOOP, DroneBuild.FREESTYLE, DroneBuild.RACER, DroneBuild.LONG_RANGE}) {
            double perTick = LocatorBeeperPower.drainPerTick(build);
            assertEquals(LocatorBeeperPower.drainPerSecond(build) * 0.05, perTick, 1.0E-15);
            assertTrue(perTick > 0.0 && perTick < LocatorBeeperPower.SYNC_STEP, build.frame().key() + ": " + perTick);
        }
    }
}
