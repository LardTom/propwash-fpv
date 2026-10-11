package de.lardtom.propwash.drone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PilotBodyRulesTest {
    @Test
    void livingPilotKeepsTheDrone() {
        assertEquals(PilotBodyRules.AfterDeath.KEEP, PilotBodyRules.afterDeath(true, PilotBodyRules.NOT_DEAD, 100L));
    }

    @Test
    void deadPilotGetsAShortGraceForTheFailsafeThenTheServerLetsGo() {
        assertEquals(PilotBodyRules.AfterDeath.WAIT, PilotBodyRules.afterDeath(false, PilotBodyRules.NOT_DEAD, 100L));
        assertEquals(PilotBodyRules.AfterDeath.WAIT, PilotBodyRules.afterDeath(false, 100L, 100L + PilotBodyRules.DEATH_GRACE_TICKS - 1));
        assertEquals(PilotBodyRules.AfterDeath.RELEASE, PilotBodyRules.afterDeath(false, 100L, 100L + PilotBodyRules.DEATH_GRACE_TICKS));
    }

    @Test
    void graceNeverOutlastsOneSecond() {
        assertTrue(PilotBodyRules.DEATH_GRACE_TICKS <= 20);
        assertEquals(PilotBodyRules.AfterDeath.RELEASE, PilotBodyRules.afterDeath(false, 500L, 10L));
    }

    @Test
    void respawnedPilotIsReleasedRightAway() {
        assertEquals(PilotBodyRules.AfterDeath.RELEASE, PilotBodyRules.afterDeath(true, 100L, 101L));
    }

    @Test
    void clientLeavesAfterAFewFramesOrAtTheTimeLimit() {
        assertFalse(PilotBodyRules.leaveAfterDeath(0, 0L));
        assertFalse(PilotBodyRules.leaveAfterDeath(PilotBodyRules.DOWN_FRAMES - 1, PilotBodyRules.DOWN_MAX_NANOS - 1L));
        assertTrue(PilotBodyRules.leaveAfterDeath(PilotBodyRules.DOWN_FRAMES, 0L));
        assertTrue(PilotBodyRules.leaveAfterDeath(0, PilotBodyRules.DOWN_MAX_NANOS));
        assertTrue(PilotBodyRules.leaveAfterDeath(0, -1L));
    }

    @Test
    void clientLeavesWellInsideTheServerGrace() {
        assertTrue(PilotBodyRules.DOWN_MAX_NANOS < PilotBodyRules.DEATH_GRACE_TICKS * 50_000_000L / 2L);
    }

    @Test
    void worldRuleShieldsOnlyPilotsAndNeverAgainstVoidOrKill() {
        assertFalse(PilotBodyRules.shields(false, true, false));
        assertFalse(PilotBodyRules.shields(true, false, false));
        assertFalse(PilotBodyRules.shields(true, true, true));
        assertTrue(PilotBodyRules.shields(true, true, false));
    }

    @Test
    void startIsRefusedOnlyWhileGlidingWithElytra() {
        assertTrue(PilotBodyRules.mayStart(false));
        assertFalse(PilotBodyRules.mayStart(true));
    }
}
