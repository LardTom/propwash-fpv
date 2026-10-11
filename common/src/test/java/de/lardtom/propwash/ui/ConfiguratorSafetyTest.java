package de.lardtom.propwash.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguratorSafetyTest {
    @Test
    void opensFreelyWhenNotFlying() {
        assertEquals(ConfiguratorSafety.Decision.OPEN, ConfiguratorSafety.decide(false, true, false, false));
        assertEquals(ConfiguratorSafety.Decision.OPEN, ConfiguratorSafety.decide(true, false, false, true));
    }

    @Test
    void neverOpensWhileArmedInTheAir() {
        assertEquals(ConfiguratorSafety.Decision.REFUSE_AIRBORNE, ConfiguratorSafety.decide(true, true, false, true));
        assertFalse(ConfiguratorSafety.decide(true, true, false, true).opens());
    }

    @Test
    void disarmsOnTheGroundWhenPossible() {
        assertEquals(ConfiguratorSafety.Decision.DISARM_THEN_OPEN, ConfiguratorSafety.decide(true, true, true, true));
        assertTrue(ConfiguratorSafety.Decision.DISARM_THEN_OPEN.opens());
        assertEquals(ConfiguratorSafety.Decision.REFUSE_ARMED, ConfiguratorSafety.decide(true, true, true, false));
    }

    @Test
    void armingIsLockedOutWhileOpen() {
        assertTrue(ConfiguratorSafety.lockoutDisarm(true, true, true));
        assertFalse(ConfiguratorSafety.lockoutDisarm(false, true, true));
        assertFalse(ConfiguratorSafety.lockoutDisarm(true, false, true));
        assertFalse(ConfiguratorSafety.lockoutDisarm(true, true, false));
    }
}
