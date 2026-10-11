package de.lardtom.propwash.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DigitalBreakupTest {
    @Test
    void stagesFollowTheLinkInOrder() {
        assertEquals(DigitalBreakup.Stage.CLEAN, DigitalBreakup.stage(1.0));
        assertEquals(DigitalBreakup.Stage.SOFT, DigitalBreakup.stage(0.75));
        assertEquals(DigitalBreakup.Stage.BLOCKY, DigitalBreakup.stage(0.5));
        assertEquals(DigitalBreakup.Stage.FREEZES, DigitalBreakup.stage(0.2));
        assertEquals(DigitalBreakup.Stage.LOST, DigitalBreakup.stage(0.0));
        DigitalBreakup.Stage previous = DigitalBreakup.Stage.CLEAN;
        for (double q = 1.0; q >= 0.0; q -= 0.005) {
            DigitalBreakup.Stage stage = DigitalBreakup.stage(q);
            assertTrue(stage.ordinal() >= previous.ordinal(), "stage went back at " + q);
            previous = stage;
        }
    }

    @Test
    void pictureDegradesMonotonically() {
        assertEquals(0.0, DigitalBreakup.blockiness(0.9), 1.0E-9);
        assertEquals(0.0, DigitalBreakup.blur(0.9), 1.0E-9);
        assertEquals(1.0, DigitalBreakup.frameArrival(0.9), 1.0E-9);
        assertEquals(0.0, DigitalBreakup.burstStart(0.9), 1.0E-9);
        double blocks = -1.0;
        double arrival = 2.0;
        for (double q = 1.0; q >= 0.0; q -= 0.01) {
            assertTrue(DigitalBreakup.blockiness(q) >= blocks - 1.0E-12);
            assertTrue(DigitalBreakup.frameArrival(q) <= arrival + 1.0E-12);
            blocks = DigitalBreakup.blockiness(q);
            arrival = DigitalBreakup.frameArrival(q);
        }
        assertTrue(DigitalBreakup.blockPx(0.2) > DigitalBreakup.blockPx(0.9));
        assertTrue(DigitalBreakup.posterize(0.3) > 1.0);
    }

    @Test
    void resolutionStepsDownAndUpWithHysteresis() {
        assertEquals(1.0, DigitalBreakup.resolutionScale(DigitalBreakup.resolutionLevel(0.9, 0)), 1.0E-9);
        int level = DigitalBreakup.resolutionLevel(0.2, 0);
        assertEquals(DigitalBreakup.resolutionLevels() - 1, level);
        assertEquals(level, DigitalBreakup.resolutionLevel(0.37, level), "must not step up right at the threshold");
        assertTrue(DigitalBreakup.resolutionLevel(0.45, level) < level);
        assertEquals(0, DigitalBreakup.resolutionLevel(1.0, level));
        int previous = 0;
        for (double q = 1.0; q >= 0.0; q -= 0.01) {
            int next = DigitalBreakup.resolutionLevel(q, previous);
            assertTrue(next >= previous, "resolution went up while the link fell at " + q);
            previous = next;
        }
    }

    @Test
    void lossThresholdsHaveHysteresis() {
        assertTrue(DigitalBreakup.LOSS_EXIT > DigitalBreakup.LOSS_ENTER);
    }
}
