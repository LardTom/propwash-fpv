package de.lardtom.propwash.compat.flashback.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayViewTest {
    @Test
    void hidesTheDroneTheCameraSitsIn() {
        assertTrue(ReplayView.inside(0.1, -0.05, 0.08, 0.0, 0.0, 0.0, 0.0, 0.0, -1.0));
        assertTrue(ReplayView.inside(0.0, 0.0, -0.3, 0.0, 0.0, 0.0, 0.0, 0.0, -1.0));
    }

    @Test
    void hidesTheDroneTrailingBehindTheCameraAtSpeed() {
        assertTrue(ReplayView.inside(0.0, 0.0, 1.2, 0.0, 0.0, -1.0, 0.0, 0.0, -1.0));
        assertTrue(ReplayView.inside(0.3, 0.0, 0.9, 0.0, 0.0, -0.8, 0.0, 0.0, -1.0));
    }

    @Test
    void keepsADroneTheCameraLooksAt() {
        assertFalse(ReplayView.inside(0.0, 0.0, -1.2, 0.0, 0.0, -1.0, 0.0, 0.0, -1.0));
        assertFalse(ReplayView.inside(0.0, 0.0, -0.8, 0.0, 0.0, 0.0, 0.0, 0.0, -1.0));
    }

    @Test
    void keepsDronesFarAway() {
        assertFalse(ReplayView.inside(0.0, 0.0, 3.0, 0.0, 0.0, -1.0, 0.0, 0.0, -1.0));
        assertFalse(ReplayView.inside(1.0, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -1.0));
    }
}
