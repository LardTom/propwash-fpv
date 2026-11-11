package de.lardtom.propwash.drone.collision.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RigArmingTest {
    @Test
    void switchStaysOnOnceArmedEvenWhenTheDroneLeavesTheGround() {
        for (int waited = 0; waited <= 3 * RigArming.REARM_TICKS; waited++) {
            assertTrue(RigArming.armSwitch(waited, true, false), "scharf und nicht am Boden: Schalter aus nach " + waited + " Ticks");
            assertTrue(RigArming.armSwitch(waited, true, true), "scharf am Boden: Schalter aus nach " + waited + " Ticks");
        }
    }

    @Test
    void unarmedSwitchWaitsForRestAndFlipsBackEveryRearmPeriod() {
        assertFalse(RigArming.armSwitch(0, false, true));
        assertFalse(RigArming.armSwitch(RigArming.ARM_DELAY - 1, false, true));
        assertTrue(RigArming.armSwitch(RigArming.ARM_DELAY, false, true));
        assertFalse(RigArming.armSwitch(RigArming.ARM_DELAY, false, false));
        assertFalse(RigArming.armSwitch(RigArming.REARM_TICKS, false, true));
        assertTrue(RigArming.armSwitch(RigArming.REARM_TICKS + 1, false, true));
        assertFalse(RigArming.armSwitch(2 * RigArming.REARM_TICKS, false, true));
    }
}
