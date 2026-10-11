package de.lardtom.propwash.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropRepairTest {
    @Test
    void bonusIsFivePercentOfANewProp() {
        assertEquals(50, PropRepair.BONUS_STEPS);
        assertEquals(PartWear.fromSteps(PropRepair.BONUS_STEPS), PropRepair.BONUS, 1.0E-6F);
    }

    @Test
    void conditionsAddUpWithBonus() {
        assertEquals(0.85F, PropRepair.combine(0.35F, 0.45F));
        assertEquals(0.716F, PropRepair.combine(0.333F, 0.333F));
        assertEquals(0.051F, PropRepair.combine(0.0F, 0.001F));
    }

    @Test
    void twoBrokenPropsGiveTheBonusOnly() {
        assertEquals(PropRepair.BONUS, PropRepair.combine(0.0F, 0.0F));
        assertTrue(PropRepair.combinable(0.0F, 0.0F));
    }

    @Test
    void resultIsCappedAtNew() {
        assertEquals(PartWear.NEW, PropRepair.combine(0.6F, 0.5F));
        assertEquals(PartWear.NEW, PropRepair.combine(0.95F, 0.999F));
        assertEquals(PartWear.NEW, PropRepair.combine(0.5F, 0.45F));
        assertEquals(0.999F, PropRepair.combine(0.5F, 0.449F));
    }

    @Test
    void orderDoesNotMatter() {
        for (int a = 0; a < PartWear.STEPS; a += 37) {
            for (int b = 0; b < PartWear.STEPS; b += 41) {
                float x = PartWear.fromSteps(a);
                float y = PartWear.fromSteps(b);
                assertEquals(PropRepair.combine(x, y), PropRepair.combine(y, x));
                assertTrue(PropRepair.combine(x, y) >= Math.max(x, y));
                assertEquals(PartWear.quantize(PropRepair.combine(x, y)), PropRepair.combine(x, y));
            }
        }
    }

    @Test
    void onlyDamagedPropsCombine() {
        assertFalse(PropRepair.repairable(PartWear.NEW));
        assertFalse(PropRepair.repairable(1.5F));
        assertFalse(PropRepair.repairable(Float.NaN));
        assertFalse(PropRepair.repairable(Float.POSITIVE_INFINITY));
        assertFalse(PropRepair.repairable(0.9999F));
        assertTrue(PropRepair.repairable(0.999F));
        assertTrue(PropRepair.repairable(0.0F));
        assertTrue(PropRepair.repairable(-0.2F));
        assertFalse(PropRepair.combinable(PartWear.NEW, 0.3F));
        assertFalse(PropRepair.combinable(0.3F, PartWear.NEW));
        assertTrue(PropRepair.combinable(0.3F, 0.7F));
    }

    @Test
    void invalidConditionsCountAsBroken() {
        assertEquals(PropRepair.BONUS + 0.4F, PropRepair.combine(Float.NaN, 0.4F), 1.0E-6F);
        assertEquals(PropRepair.combine(0.0F, 0.4F), PropRepair.combine(-1.0F, 0.4F));
    }
}
