package de.lardtom.propwash.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MouseDemoTest {
    private static float middle(MouseDemo.Cue cue) {
        for (MouseDemo.Phase phase : MouseDemo.all()) {
            if (phase.cue() == cue) {
                return (phase.start() + phase.end()) * 0.5F;
            }
        }
        throw new IllegalArgumentException(cue.name());
    }

    private static float end(MouseDemo.Cue cue) {
        for (MouseDemo.Phase phase : MouseDemo.all()) {
            if (phase.cue() == cue) {
                return phase.end() - 0.02F;
            }
        }
        throw new IllegalArgumentException(cue.name());
    }

    @Test
    void phasesCoverThePeriodWithoutGaps() {
        float t = 0.0F;
        for (MouseDemo.Phase phase : MouseDemo.all()) {
            assertEquals(t, phase.start(), 1.0E-5F);
            assertTrue(phase.end() > phase.start());
            t = phase.end();
        }
        assertEquals(MouseDemo.PERIOD, t, 1.0E-5F);
    }

    @Test
    void theMouseReturnsToTheCenterOfThePad() {
        MouseDemo.Frame last = MouseDemo.frame(new MouseSettings(), true, MouseDemo.PERIOD - 0.01F);
        assertEquals(0.0F, last.mouseX(), 0.05F);
        assertEquals(0.0F, last.mouseY(), 0.05F);
        MouseDemo.Frame right = MouseDemo.frame(new MouseSettings(), true, end(MouseDemo.Cue.RIGHT));
        assertTrue(right.mouseX() > 4.0F);
    }

    @Test
    void speedModeSpringsBackAndHoldModeStays() {
        MouseSettings rate = new MouseSettings();
        rate.mode = MouseMode.RATE;
        assertTrue(MouseDemo.frame(rate, false, middle(MouseDemo.Cue.RIGHT)).sticks().roll() > 0.25F);
        assertEquals(0.0F, MouseDemo.frame(rate, false, middle(MouseDemo.Cue.STOP_SIDE)).sticks().roll(), 1.0E-4F);
        assertTrue(MouseDemo.frame(rate, false, middle(MouseDemo.Cue.FORWARD)).sticks().pitch() > 0.25F);

        MouseSettings hold = new MouseSettings();
        hold.mode = MouseMode.HOLD;
        hold.returnSpeed = 0.0F;
        float held = MouseDemo.frame(hold, true, middle(MouseDemo.Cue.STOP_SIDE)).sticks().roll();
        assertTrue(held > 0.5F, "gehalten: " + held);
        assertEquals(held, MouseDemo.frame(hold, true, end(MouseDemo.Cue.STOP_SIDE)).sticks().roll(), 1.0E-4F);
    }

    @Test
    void invertedYTurnsForwardIntoNoseUp() {
        MouseSettings inverted = new MouseSettings();
        inverted.mode = MouseMode.RATE;
        inverted.invertY = true;
        assertTrue(MouseDemo.frame(inverted, false, middle(MouseDemo.Cue.FORWARD)).sticks().pitch() < -0.25F);
    }

    @Test
    void demoIgnoresTheUsersSensitivityAndDpi() {
        MouseSettings a = new MouseSettings();
        MouseSettings b = new MouseSettings();
        b.sensitivity = 4.0F;
        b.dpi = 3200;
        float t = middle(MouseDemo.Cue.FORWARD);
        assertEquals(MouseDemo.frame(a, true, t).sticks().pitch(), MouseDemo.frame(b, true, t).sticks().pitch(), 1.0E-5F);
    }
}
