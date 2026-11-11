package de.lardtom.propwash.race;

import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostAccessTest {
    @Test
    void worldRuleIsANeutralLevelRuleDefaultingToAll() {
        assertEquals("foreign_ghosts", WorldRuleSpecs.FOREIGN_GHOSTS.name());
        assertEquals(GhostAccess.ALL, WorldRuleSpecs.FOREIGN_GHOSTS.defaultValue());
        assertFalse(WorldRuleSpecs.FOREIGN_GHOSTS.raceRelevant());
        assertTrue(WorldRuleSpecs.FOREIGN_GHOSTS.valid(GhostAccess.OFF));
        assertFalse(WorldRuleSpecs.FOREIGN_GHOSTS.valid(3));
    }

    @Test
    void ownTimesAreAlwaysAvailable() {
        for (int rule = GhostAccess.OFF; rule <= GhostAccess.ALL; rule++) {
            for (GhostAccess.Purpose purpose : GhostAccess.Purpose.values()) {
                assertTrue(GhostAccess.allowed(rule, true, false, purpose));
            }
        }
    }

    @Test
    void allLetsEveryoneReplayAndRace() {
        assertTrue(GhostAccess.allowed(GhostAccess.ALL, false, false, GhostAccess.Purpose.REPLAY));
        assertTrue(GhostAccess.allowed(GhostAccess.ALL, false, false, GhostAccess.Purpose.OPPONENT));
        assertEquals(GhostAccess.REPLAY | GhostAccess.OPPONENT, GhostAccess.flags(GhostAccess.ALL, false));
    }

    @Test
    void adminsLimitsForeignGhostsToOperators() {
        assertFalse(GhostAccess.allowed(GhostAccess.ADMINS, false, false, GhostAccess.Purpose.REPLAY));
        assertFalse(GhostAccess.allowed(GhostAccess.ADMINS, false, false, GhostAccess.Purpose.OPPONENT));
        assertTrue(GhostAccess.allowed(GhostAccess.ADMINS, false, true, GhostAccess.Purpose.REPLAY));
        assertTrue(GhostAccess.allowed(GhostAccess.ADMINS, false, true, GhostAccess.Purpose.OPPONENT));
        assertEquals(0, GhostAccess.flags(GhostAccess.ADMINS, false));
    }

    @Test
    void offStillLetsOperatorsCheckReplays() {
        assertFalse(GhostAccess.allowed(GhostAccess.OFF, false, false, GhostAccess.Purpose.REPLAY));
        assertFalse(GhostAccess.allowed(GhostAccess.OFF, false, false, GhostAccess.Purpose.OPPONENT));
        assertTrue(GhostAccess.allowed(GhostAccess.OFF, false, true, GhostAccess.Purpose.REPLAY));
        assertFalse(GhostAccess.allowed(GhostAccess.OFF, false, true, GhostAccess.Purpose.OPPONENT));
        assertEquals(GhostAccess.REPLAY | GhostAccess.REMOVE, GhostAccess.flags(GhostAccess.OFF, true));
    }

    @Test
    void onlyOperatorsMayRemoveTimes() {
        assertTrue(GhostAccess.mayRemove(true));
        assertFalse(GhostAccess.mayRemove(false));
        assertEquals(0, GhostAccess.flags(GhostAccess.ALL, false) & GhostAccess.REMOVE);
        assertEquals(GhostAccess.REMOVE, GhostAccess.flags(GhostAccess.ALL, true) & GhostAccess.REMOVE);
    }

    @Test
    void unknownPurposeFallsBackToReplay() {
        assertEquals(GhostAccess.Purpose.REPLAY, GhostAccess.Purpose.of(-1));
        assertEquals(GhostAccess.Purpose.OPPONENT, GhostAccess.Purpose.of(1));
    }
}
