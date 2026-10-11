package de.lardtom.propwash.interact;

import de.lardtom.propwash.race.FreeformRegion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreakRulesTest {
    @Test
    void ruleBlockAndBudgetComeFirst() {
        assertEquals(BreakRules.Outcome.RULE_OFF, BreakRules.decide(false, true, true, true, true, false, false, true));
        assertEquals(BreakRules.Outcome.NOT_BREAKABLE, BreakRules.decide(true, false, true, true, true, false, false, true));
        assertEquals(BreakRules.Outcome.BUDGET, BreakRules.decide(true, true, false, true, true, false, false, true));
        assertEquals(BreakRules.Outcome.TOO_FAR, BreakRules.decide(true, true, true, false, true, false, false, true));
        assertEquals(BreakRules.Outcome.TOO_WEAK, BreakRules.decide(true, true, true, true, false, false, false, true));
    }

    @Test
    void permissionsLikeAPlayerBreak() {
        assertEquals(BreakRules.Outcome.COURSE, BreakRules.decide(true, true, true, true, true, true, false, true));
        assertEquals(BreakRules.Outcome.GAME_MODE, BreakRules.decide(true, true, true, true, true, false, true, true));
        assertEquals(BreakRules.Outcome.PROTECTED, BreakRules.decide(true, true, true, true, true, false, false, false));
        assertEquals(BreakRules.Outcome.BREAK, BreakRules.decide(true, true, true, true, true, false, false, true));
    }

    @Test
    void onlyPermissionReasonsAreToldAndProtectionIsRememberedAsAnArea() {
        for (BreakRules.Outcome outcome : BreakRules.Outcome.values()) {
            boolean told = outcome == BreakRules.Outcome.COURSE || outcome == BreakRules.Outcome.GAME_MODE
                    || outcome == BreakRules.Outcome.PROTECTED;
            assertEquals(told, outcome.told(), outcome.name());
            assertTrue(outcome.messageKey().startsWith("propwash.interact.break.denied."));
            assertEquals(outcome, BreakRules.Outcome.byId(outcome.ordinal()));
        }
        assertEquals(BreakRules.Outcome.PROTECTED, BreakRules.Outcome.byId(99));
        assertEquals(BreakRules.Outcome.PROTECTED, BreakRules.Outcome.byId(-1));
        assertTrue(BreakRules.memoryRadius(BreakRules.Outcome.PROTECTED) >= 16.0, "deckt den Spawn-Schutz (16) ab");
        assertEquals(0.0, BreakRules.memoryRadius(BreakRules.Outcome.TOO_WEAK));
        assertTrue(BreakRules.memoryTicks(BreakRules.Outcome.PROTECTED) > BreakRules.memoryTicks(BreakRules.Outcome.TOO_WEAK));
    }

    @Test
    void freeformGateFramesAreTheBlocksAroundTheOpening() {
        boolean[] cells = {true, true, true, true, true, true};
        FreeformRegion region = FreeformRegion.of(FreeformRegion.AXIS_Z, 3, 2, 1, 0, cells);
        assertNotNull(region);
        assertTrue(BreakRules.frame(region, -1, 0), "links");
        assertTrue(BreakRules.frame(region, 3, 1), "rechts");
        assertTrue(BreakRules.frame(region, 1, -1), "unten");
        assertTrue(BreakRules.frame(region, 2, 2), "oben");
        assertFalse(BreakRules.frame(region, 1, 1), "Öffnung selbst");
        assertFalse(BreakRules.frame(region, -1, -1), "Ecke schließt die Öffnung nicht");
        assertFalse(BreakRules.frame(region, 6, 6), "weit weg");
        assertFalse(BreakRules.frame(null, 0, 0));
    }
}
