package de.lardtom.propwash.interact;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrikeRulesTest {
    @Test
    void gameruleOffMeansNoHitsAtAll() {
        assertEquals(StrikeRules.Decision.RULE_OFF, StrikeRules.decide(false, false, false, true, true, true));
        assertEquals(StrikeRules.Decision.RULE_OFF, StrikeRules.decide(false, false, true, true, true, true));
    }

    @Test
    void playersOnlyWithPvpAndNeverTheOwnPilot() {
        assertEquals(StrikeRules.Decision.PVP_OFF, StrikeRules.decide(true, false, true, false, true, true));
        assertEquals(StrikeRules.Decision.HIT, StrikeRules.decide(true, false, true, true, true, true));
        assertEquals(StrikeRules.Decision.OWN_PILOT, StrikeRules.decide(true, true, true, true, true, true));
        assertEquals(StrikeRules.Decision.HIT, StrikeRules.decide(true, false, false, false, true, true), "Mobs auch ohne PvP");
    }

    @Test
    void cooldownAndBudgetLimitRepeatedHits() {
        assertEquals(StrikeRules.Decision.COOLDOWN, StrikeRules.decide(true, false, false, true, false, true));
        assertEquals(StrikeRules.Decision.BUDGET, StrikeRules.decide(true, false, false, true, true, false));
    }

    @Test
    void claimedSpeedIsClampedToWhatTheServerSaw() {
        assertEquals(12.0, StrikeRules.acceptedSpeed(12.0, 11.0, 50.0), 1.0E-9);
        assertEquals(10.0 * StrikeRules.PEAK_FACTOR + StrikeRules.PEAK_MARGIN, StrikeRules.acceptedSpeed(80.0, 10.0, 60.0), 1.0E-9);
        assertEquals(30.0, StrikeRules.acceptedSpeed(80.0, 40.0, 30.0), 1.0E-9);
        assertEquals(StrikeRules.PEAK_MARGIN, StrikeRules.acceptedSpeed(25.0, 0.0, 50.0), 1.0E-9);
        assertEquals(0.0, StrikeRules.acceptedSpeed(Double.NaN, 10.0, 50.0), 1.0E-9);
        assertEquals(0.0, StrikeRules.acceptedSpeed(-3.0, 10.0, 50.0), 1.0E-9);
    }

    @Test
    void reachGrowsWithSpeed() {
        assertTrue(StrikeRules.withinReach(0.0, 0.0));
        assertTrue(StrikeRules.withinReach(1.4, 0.0));
        assertFalse(StrikeRules.withinReach(2.0, 0.0));
        assertTrue(StrikeRules.withinReach(4.0, 20.0));
        assertFalse(StrikeRules.withinReach(10.0, 20.0));
        assertFalse(StrikeRules.withinReach(Double.NaN, 5.0));
    }

    @Test
    void gapBetweenBoxes() {
        assertEquals(0.0, StrikeRules.gap(0, 0, 0, 1, 1, 1, 0.5, 0.5, 0.5, 2, 2, 2), 1.0E-9);
        assertEquals(1.0, StrikeRules.gap(0, 0, 0, 1, 1, 1, 2, 0, 0, 3, 1, 1), 1.0E-9);
        assertEquals(Math.sqrt(2.0), StrikeRules.gap(0, 0, 0, 1, 1, 1, 2, 2, 0, 3, 3, 1), 1.0E-9);
    }
}
