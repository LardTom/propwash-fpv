package de.lardtom.propwash.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartWearTest {
    @Test
    void newPartsAreThePristineInstance() {
        assertTrue(PartWear.PRISTINE.isNew());
        assertSame(PartWear.PRISTINE, PartWear.uniform(1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F));
        assertSame(PartWear.PRISTINE, PartWear.PRISTINE.withProp(2, 0.9995F));
        assertEquals(1.0F, PartWear.PRISTINE.worst());
    }

    @Test
    void conditionsAreQuantizedAndClamped() {
        assertEquals(0.0F, PartWear.quantize(-3.0F));
        assertEquals(0.0F, PartWear.quantize(Float.NaN));
        assertEquals(1.0F, PartWear.quantize(1.7F));
        assertEquals(0.123F, PartWear.quantize(0.12321F), 1.0E-6F);
        assertTrue(PartWear.broken(0.0F));
        assertTrue(PartWear.damaged(0.85F));
        assertFalse(PartWear.damaged(0.95F));
        assertTrue(PartWear.critical(0.3F));
    }

    @Test
    void slotsFollowTheWorkbenchLayout() {
        PartWear wear = PartWear.PRISTINE.withArm(1, 0.5F).withStack(0.8F).withMotor(3, 0.7F).withProp(0, 0.2F).withVideo(0.6F)
                .withBattery(0.4F);
        assertEquals(0.5F, wear.slot(PartWear.SLOT_FRAME));
        assertEquals(0.8F, wear.slot(PartWear.SLOT_STACK));
        assertEquals(0.7F, wear.slot(PartWear.SLOT_MOTOR + 3));
        assertEquals(0.2F, wear.slot(PartWear.SLOT_PROP));
        assertEquals(0.6F, wear.slot(PartWear.SLOT_VIDEO));
        assertEquals(0.4F, wear.slot(PartWear.SLOT_BATTERY));
        assertEquals(12, PartWear.SLOTS);
        float[] slots = wear.slots();
        PartWear back = PartWear.fromSlots(slots);
        assertArrayEquals(slots, back.slots());
        assertEquals(0.5F, back.arm(3));
        assertThrows(IllegalArgumentException.class, () -> PartWear.fromSlots(new float[3]));
        assertThrows(IndexOutOfBoundsException.class, () -> wear.slot(PartWear.SLOTS));
    }

    @Test
    void positionsMapToSimMotorsBothWays() {
        for (int position = 0; position < PartWear.POSITIONS; position++) {
            assertEquals(position, PartWear.positionOfSimMotor(PartWear.simMotor(position)));
        }
        assertEquals(3, PartWear.simMotor(PartWear.FRONT_LEFT));
        assertEquals(1, PartWear.simMotor(PartWear.FRONT_RIGHT));
        assertEquals(2, PartWear.simMotor(PartWear.REAR_LEFT));
        assertEquals(0, PartWear.simMotor(PartWear.REAR_RIGHT));
    }

    @Test
    void countsAndEquality() {
        PartWear wear = PartWear.PRISTINE.withProp(0, 0.0F).withProp(1, 0.5F).withProp(2, 0.95F);
        assertEquals(2, wear.damagedProps());
        assertEquals(1, wear.brokenProps());
        assertEquals(0.0F, wear.worstProp());
        assertEquals(wear, PartWear.PRISTINE.withProp(0, 0.0F).withProp(1, 0.5F).withProp(2, 0.95F));
        assertNotEquals(wear, PartWear.PRISTINE);
        float[] props = wear.props();
        props[0] = 1.0F;
        assertEquals(0.0F, wear.prop(0));
    }
}
