package de.lardtom.propwash.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneBatteryTest {
    private static double charge(BatteryWarningTest.Bench bench) {
        return bench.sim.telemetry().batteryCharge;
    }

    @Test
    void chargeContinuesAfterLeavingAnInfiniteBatteryZone() {
        BatteryWarningTest.Bench bench = new BatteryWarningTest.Bench(BatteryWarningTest.tuning(false), 0.8).run(0.5, 2.0);
        double entered = charge(bench);
        assertTrue(entered < 0.8, "Testaufbau verbraucht nichts: " + entered);
        bench.sim.setTuning(BatteryWarningTest.tuning(true));
        bench.run(0.5, 8.0);
        assertEquals(entered, charge(bench), 1.0E-9, "unendlicher Akku zieht trotzdem Ladung ab");
        bench.sim.setTuning(BatteryWarningTest.tuning(false));
        bench.sim.keepBatteryCharge();
        bench.run(0.5, 0.1);
        double left = charge(bench);
        assertTrue(entered - left < 0.002, "Ladung springt beim Verlassen: " + entered + " -> " + left);
        assertTrue(left < entered, "nach dem Verlassen wird wieder verbraucht");
    }

    @Test
    void withoutKeepingTheChargeTheZoneTimeWouldCount() {
        BatteryWarningTest.Bench bench = new BatteryWarningTest.Bench(BatteryWarningTest.tuning(false), 0.8).run(0.5, 2.0);
        double entered = charge(bench);
        bench.sim.setTuning(BatteryWarningTest.tuning(true));
        bench.run(0.5, 8.0);
        bench.sim.setTuning(BatteryWarningTest.tuning(false));
        bench.run(0.5, 0.1);
        assertTrue(entered - charge(bench) > 0.01, "Testaufbau zeigt den Sprung nicht: " + entered + " -> " + charge(bench));
    }
}
