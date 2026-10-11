package de.lardtom.propwash.content;

import de.lardtom.propwash.build.BatteryType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatteriesMathTest {
    @Test
    void quantizeRoundsToThousandths() {
        assertEquals(0.123F, Batteries.quantize(0.12345F), 1.0E-6F);
        assertEquals(0.5F, Batteries.quantize(0.5004F), 1.0E-6F);
        assertEquals(1.0F, Batteries.quantize(0.999F));
        assertEquals(1.0F, Batteries.quantize(0.9995F));
        assertEquals(0.998F, Batteries.quantize(0.998F), 1.0E-6F);
    }

    @Test
    void clampHandlesOutOfRangeAndNaN() {
        assertEquals(0.0F, Batteries.clamp(-0.5F));
        assertEquals(1.0F, Batteries.clamp(3.0F));
        assertEquals(0.0F, Batteries.clamp(Float.NaN));
        assertEquals(0.0F, Batteries.quantize(Float.NaN));
        assertTrue(Batteries.isFull(0.9991F));
    }

    @Test
    void restingVoltageFollowsCellCurve() {
        for (BatteryType battery : BatteryType.values()) {
            double full = Batteries.openCircuitVoltage(battery, 1.0F);
            double empty = Batteries.openCircuitVoltage(battery, 0.0F);
            assertEquals(battery.cells * battery.chemistry.fullCellVoltage, full, 1.0E-9, battery.name());
            assertEquals(battery.cells * battery.chemistry.emptyCellVoltage, empty, 1.0E-9, battery.name());
            double half = Batteries.openCircuitVoltage(battery, 0.5F);
            assertTrue(half > empty && half < full, battery.name());
            assertTrue(Batteries.openCircuitVoltage(battery, 0.6F) > half, battery.name());
            assertTrue(full > empty);
        }
        assertEquals(25.2, Batteries.openCircuitVoltage(BatteryType.B6S_1300, 1.0F), 1.0E-9);
        assertEquals(4.35, Batteries.openCircuitVoltage(BatteryType.B1S_300_HV, 1.0F), 1.0E-9);
        BatteryType.Chemistry lipo = BatteryType.B6S_1300.chemistry;
        assertEquals(6 * (lipo.emptyCellVoltage + (lipo.fullCellVoltage - lipo.emptyCellVoltage) * 0.43), Batteries.openCircuitVoltage(BatteryType.B6S_1300, 0.5F), 1.0E-9);
    }

    @Test
    void percentAndBarColor() {
        assertEquals(74, Batteries.percent(0.74F));
        assertEquals(0x55FF55, Batteries.barColor(0.8F));
        assertEquals(0xFFFF55, Batteries.barColor(0.3F));
        assertEquals(0xFF5555, Batteries.barColor(0.1F));
    }
}
