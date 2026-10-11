package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ThermalTest {
    @Test
    void sustainedFullThrottleHeatsMotorsWithoutRunaway() {
        TestStand stand = TestStand.of(DroneBuild.FREESTYLE);
        stand.spinUp();
        stand.duty(1.0);
        stand.substeps(60 * DroneSim.PHYSICS_HZ);
        Telemetry t = stand.sim.telemetry();
        for (int i = 0; i < 4; i++) {
            assertTrue(t.motorTempC[i] > 100.0, "motor " + t.motorTempC[i]);
            assertTrue(t.motorTempC[i] < 150.0, "motor " + t.motorTempC[i]);
            assertTrue(t.escTempC[i] > 40.0 && t.escTempC[i] < 150.0, "esc " + t.escTempC[i]);
        }
        double hot = t.motorTempC[0];
        stand.duty(0.055);
        stand.substeps(60 * DroneSim.PHYSICS_HZ);
        assertTrue(stand.sim.telemetry().motorTempC[0] < hot - 10.0, "cooled to " + stand.sim.telemetry().motorTempC[0]);
    }
}
