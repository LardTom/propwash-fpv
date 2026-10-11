package de.lardtom.propwash.drone;

import de.lardtom.propwash.drone.net.MoveBudget;
import de.lardtom.propwash.drone.net.StateValidation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateValidationTest {
    @Test
    void maxSpeedUsesSmallerOfRuleAndPhysics() {
        assertEquals(StateValidation.PHYSICS_MAX_SPEED, StateValidation.effectiveMaxSpeed(Double.POSITIVE_INFINITY));
        assertEquals(30.0, StateValidation.effectiveMaxSpeed(30.0));
        assertEquals(StateValidation.PHYSICS_MAX_SPEED, StateValidation.effectiveMaxSpeed(Double.NaN));
        assertEquals(0.0, StateValidation.effectiveMaxSpeed(-5.0));
    }

    @Test
    void budgetAllowsBurstThenLimitsToMaxSpeed() {
        MoveBudget budget = new MoveBudget();
        double burst = StateValidation.burstDistance(30.0);
        assertTrue(budget.tryConsume(burst - 0.01, 30.0, 0L));
        assertFalse(budget.tryConsume(1.0, 30.0, 0L));
        long second = 1_000_000_000L;
        assertTrue(budget.tryConsume(StateValidation.refillRate(30.0) - 0.01, 30.0, second));
        assertFalse(budget.tryConsume(1.0, 30.0, second));
    }

    @Test
    void budgetCannotBeInflatedBySpamOrBadInput() {
        MoveBudget budget = new MoveBudget();
        double burst = StateValidation.burstDistance(30.0);
        long nanos = 0L;
        double moved = 0.0;
        for (int i = 0; i < 1000; i++) {
            nanos += 1_000_000L;
            if (budget.tryConsume(5.0, 30.0, nanos)) {
                moved += 5.0;
            }
        }
        assertTrue(moved <= burst + StateValidation.refillRate(30.0) * 1.0 + 1.0E-6);
        assertFalse(budget.tryConsume(Double.NaN, 30.0, nanos));
        assertFalse(budget.tryConsume(-1.0, 30.0, nanos));
        assertFalse(budget.tryConsume(burst, 30.0, nanos - 5_000_000_000L));
    }

    @Test
    void packetsPerTickAreCapped() {
        MoveBudget budget = new MoveBudget();
        for (int i = 0; i < StateValidation.MAX_PACKETS_PER_TICK; i++) {
            assertTrue(budget.admitPacket(7L));
        }
        assertFalse(budget.admitPacket(7L));
        assertTrue(budget.admitPacket(8L));
    }

    @Test
    void orientationChecks() {
        assertTrue(StateValidation.orientationUsable(0.0F, 0.0F, 0.0F, 1.0F));
        assertTrue(StateValidation.orientationUsable(0.5F, 0.5F, 0.5F, 0.5F));
        assertTrue(StateValidation.orientationUsable(0.0F, 0.0F, 0.0F, 1.05F));
        assertFalse(StateValidation.orientationUsable(0.0F, 0.0F, 0.0F, 0.0F));
        assertFalse(StateValidation.orientationUsable(2.0F, 0.0F, 0.0F, 0.0F));
        assertFalse(StateValidation.orientationUsable(Float.NaN, 0.0F, 0.0F, 1.0F));
    }

    @Test
    void speedChecks() {
        assertTrue(StateValidation.speedPlausible(20.0F, 0.0F, 0.0F, 30.0));
        assertFalse(StateValidation.speedPlausible(60.0F, 0.0F, 0.0F, 30.0));
        assertFalse(StateValidation.speedPlausible(Float.NaN, 0.0F, 0.0F, 30.0));
    }

    @Test
    void batteryCannotChargeWhileFlying() {
        assertEquals(0.5F, StateValidation.acceptBattery(0.5F, 0.6F));
        assertEquals(0.6F, StateValidation.acceptBattery(0.9F, 0.6F));
        assertEquals(0.0F, StateValidation.acceptBattery(Float.NaN, 0.6F));
        assertEquals(0.0F, StateValidation.acceptBattery(-1.0F, 0.6F));
        assertEquals(1.0F, StateValidation.acceptBattery(1.0F, 1.0F));
    }

    @Test
    void motorPackingRoundTrip() {
        int packed = StateValidation.packMotors(0.0F, 0.25F, 0.5F, 1.0F);
        assertEquals(0.0F, StateValidation.unpackMotor(packed, 0), 1.0E-6F);
        assertEquals(0.25F, StateValidation.unpackMotor(packed, 1), 0.004F);
        assertEquals(0.5F, StateValidation.unpackMotor(packed, 2), 0.004F);
        assertEquals(1.0F, StateValidation.unpackMotor(packed, 3), 1.0E-6F);
        assertEquals(0.4375F, StateValidation.averageMotor(packed), 0.004F);
        assertEquals(0.0F, StateValidation.unpackMotor(packed, 4));
        int clamped = StateValidation.packMotors(-1.0F, 2.0F, Float.NaN, 0.5F);
        assertEquals(0.0F, StateValidation.unpackMotor(clamped, 0));
        assertEquals(1.0F, StateValidation.unpackMotor(clamped, 1));
        assertEquals(0.0F, StateValidation.unpackMotor(clamped, 2));
    }
}
