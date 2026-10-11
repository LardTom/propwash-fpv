package de.lardtom.propwash.drone;

import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.PropType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropServiceTest {
    private static final PartWear DAMAGED = PartWear.PRISTINE.withProp(0, 0.5F).withProp(1, 0.0F).withProp(2, 0.95F).withProp(3, 0.85F)
            .withMotor(1, 0.7F);

    @Test
    void sameTypeSwapsOneAtATime() {
        assertEquals(PropService.Fit.SWAP_ONE, PropService.fit(FrameType.FREESTYLE_5, PropType.P51_3B, PropType.P51_3B, 1));
        PartWear swapped = PropService.swapOne(DAMAGED, 1, PartWear.NEW);
        assertEquals(1.0F, swapped.prop(1));
        assertEquals(0.5F, swapped.prop(0));
        assertEquals(0.7F, swapped.motor(1));
    }

    @Test
    void frameLimitsThePropSizeLikeTheWorkbench() {
        for (FrameType frame : FrameType.values()) {
            for (PropType prop : PropType.values()) {
                boolean fits = prop.diameterMm <= frame.maxPropMm;
                assertEquals(fits, PropService.fitsFrame(frame, prop), frame + " " + prop);
                if (!fits) {
                    assertEquals(PropService.Fit.TOO_LARGE, PropService.fit(frame, PropType.P31_4B, prop, 64));
                }
            }
        }
        assertEquals(PropService.Fit.TOO_LARGE, PropService.fit(FrameType.FREESTYLE_5, PropType.P51_3B, PropType.P7_2B, 4));
    }

    @Test
    void otherFittingTypeNeedsAllFour() {
        assertEquals(PropService.Fit.NEED_FOUR, PropService.fit(FrameType.FREESTYLE_5, PropType.P51_3B, PropType.P3_3B, 3));
        assertEquals(PropService.Fit.SWAP_ALL, PropService.fit(FrameType.FREESTYLE_5, PropType.P51_3B, PropType.P3_3B, 4));
        assertFalse(PropService.allowed(PropService.Fit.NEED_FOUR));
        assertTrue(PropService.allowed(PropService.Fit.SWAP_ALL));
        PartWear all = PropService.swapAll(DAMAGED, 0.8F);
        for (int position = 0; position < PartWear.POSITIONS; position++) {
            assertEquals(0.8F, all.prop(position));
        }
        assertEquals(0.7F, all.motor(1));
    }

    @Test
    void quickSwapTargetsTheWorstDamagedProp() {
        assertEquals(1, PropService.worstReplaceable(DAMAGED, PartWear.NEW));
        assertEquals(0, PropService.worstReplaceable(DAMAGED.withProp(1, 1.0F), PartWear.NEW));
        assertEquals(3, PropService.worstReplaceable(PartWear.PRISTINE.withProp(3, 0.85F), PartWear.NEW));
        assertEquals(PropService.NONE, PropService.worstReplaceable(PartWear.PRISTINE.withProp(2, 0.95F), PartWear.NEW));
        assertEquals(PropService.NONE, PropService.worstReplaceable(PartWear.PRISTINE.withProp(0, 0.5F), 0.4F));
    }
}
