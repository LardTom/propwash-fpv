package de.lardtom.propwash.content;

import de.lardtom.propwash.build.BatteryType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChargeMathTest {
    private static final ChargeMath.Cfg CFG = ChargeMath.DEFAULTS;

    @Test
    void referencePackTakesNinetySeconds() {
        assertEquals(90.0, ChargeMath.seconds(BatteryType.B6S_1300, CFG), 1.0E-9);
    }

    @Test
    void capacityScalesWithExponent() {
        assertEquals(90.0 * Math.sqrt(650.0 / 1300.0), ChargeMath.seconds(BatteryType.B4S_650, CFG), 1.0E-9);
        assertEquals(90.0 * Math.sqrt(4000.0 / 1300.0), ChargeMath.seconds(BatteryType.B6S_4000_LION, CFG), 1.0E-9);
        assertEquals(43.0, ChargeMath.seconds(BatteryType.B1S_300_HV, CFG), 1.0);
        assertEquals(158.0, ChargeMath.seconds(BatteryType.B6S_4000_LION, CFG), 1.0);
        ChargeMath.Cfg linear = new ChargeMath.Cfg(90.0, 1.0, 1.0, 2.0);
        assertEquals(90.0 * 650.0 / 1300.0, ChargeMath.seconds(BatteryType.B4S_650, linear), 1.0E-9);
    }

    @Test
    void minimumTimeApplies() {
        ChargeMath.Cfg fast = new ChargeMath.Cfg(10.0, 1.0, 20.0, 2.0);
        assertEquals(20.0, ChargeMath.seconds(BatteryType.B1S_300_HV, fast), 1.0E-9);
    }

    @Test
    void redstoneMultipliesTheStep() {
        float normal = ChargeMath.step(BatteryType.B6S_1300, ChargeMath.INTERVAL, false, CFG);
        float powered = ChargeMath.step(BatteryType.B6S_1300, ChargeMath.INTERVAL, true, CFG);
        assertEquals(2.0F * normal, powered, 1.0E-7F);
        assertEquals(ChargeMath.INTERVAL / (20.0 * 90.0), normal, 1.0E-7);
    }

    @Test
    void summedStepsReachExactlyFull() {
        float charge = 0.0F;
        int intervals = 0;
        while (charge < 1.0F && intervals < 10_000) {
            charge = ChargeMath.advance(charge, BatteryType.B6S_1300, ChargeMath.INTERVAL, false, CFG);
            intervals++;
        }
        assertEquals(1.0F, charge);
        assertEquals(180, intervals, 1);
    }

    @Test
    void remainingTimeFallsMonotonically() {
        int last = Integer.MAX_VALUE;
        for (float charge = 0.0F; charge <= 1.0F; charge += 0.05F) {
            int remaining = ChargeMath.remainingSeconds(charge, BatteryType.B6S_1100, false, CFG);
            assertTrue(remaining <= last, "bei " + charge);
            last = remaining;
        }
        assertEquals(0, ChargeMath.remainingSeconds(1.0F, BatteryType.B6S_1100, false, CFG));
        assertEquals(45, ChargeMath.remainingSeconds(0.0F, BatteryType.B6S_1300, true, CFG));
    }

    @Test
    void invalidConfigValuesAreClamped() {
        ChargeMath.Cfg wild = new ChargeMath.Cfg(Double.NaN, 5.0, -3.0, 0.0);
        assertEquals(90.0, wild.referenceSeconds());
        assertEquals(1.0, wild.capacityExponent());
        assertEquals(1.0, wild.minSeconds());
        assertEquals(1.0, wild.redstoneMultiplier());
        ChargeMath.Cfg huge = new ChargeMath.Cfg(1.0E9, -1.0, 1.0E9, 100.0);
        assertEquals(3600.0, huge.referenceSeconds());
        assertEquals(0.0, huge.capacityExponent());
        assertEquals(3600.0, huge.minSeconds());
        assertEquals(10.0, huge.redstoneMultiplier());
    }
}
