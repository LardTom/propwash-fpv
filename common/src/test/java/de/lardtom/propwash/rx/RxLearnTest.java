package de.lardtom.propwash.rx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RxLearnTest {
    private static RxLearn.Sample sample(double time, float throttle, float axis6, boolean button2) {
        float[] axes = new float[8];
        axes[2] = throttle;
        axes[6] = axis6;
        boolean[] buttons = new boolean[4];
        buttons[2] = button2;
        return new RxLearn.Sample(time, new float[]{0.0F, 0.0F, 0.0F, throttle}, axes, buttons);
    }

    @Test
    void throttleMovementPicksTheLogicalStick() {
        RxLearn learn = new RxLearn();
        RxSource found = null;
        for (int i = 0; i <= 20 && found == null; i++) {
            found = learn.sample(sample(i * 0.05, -1.0F + i * 0.1F, -1.0F, false));
        }
        assertEquals(RxSource.GAS, found);
        assertTrue(learn.done());
    }

    @Test
    void auxSwitchPicksTheRawAxis() {
        RxLearn learn = new RxLearn();
        assertNull(learn.sample(sample(0.0, -1.0F, -1.0F, false)));
        assertEquals(RxSource.axis(6), learn.sample(sample(0.1, -1.0F, 1.0F, false)));
    }

    @Test
    void buttonPressPicksTheButton() {
        RxLearn learn = new RxLearn();
        assertNull(learn.sample(sample(0.0, -1.0F, -1.0F, false)));
        assertEquals(RxSource.button(2), learn.sample(sample(0.1, -1.0F, -1.0F, true)));
    }

    @Test
    void smallWigglesAreIgnored() {
        RxLearn learn = new RxLearn();
        for (int i = 0; i < 40; i++) {
            assertNull(learn.sample(sample(i * 0.05, -1.0F + (i % 2) * 0.5F, -1.0F, false)));
        }
        assertFalse(learn.done());
        assertTrue(RxLearn.travel(java.util.List.of(sample(0.0, -1.0F, 0.0F, false), sample(0.1, -0.5F, 0.0F, false)), RxSource.GAS) < RxLearn.MIN_TRAVEL);
    }

    @Test
    void slowMovesOutsideTheWindowDoNotAddUp() {
        RxLearn learn = new RxLearn();
        RxSource found = null;
        for (int i = 0; i <= 100 && found == null; i++) {
            found = learn.sample(sample(i * 0.1, -1.0F + i * 0.02F, -1.0F, false));
        }
        assertNull(found, "langsame Bewegung über 10 s darf nicht als Weg in 1,5 s zählen");
    }

    @Test
    void givesUpAfterTheTimeout() {
        RxLearn learn = new RxLearn();
        for (int i = 0; i < 300; i++) {
            learn.sample(sample(i * 0.05, -1.0F, -1.0F, false));
        }
        assertTrue(learn.timedOut());
        assertNull(learn.result());
        learn.reset();
        assertFalse(learn.done());
    }
}
