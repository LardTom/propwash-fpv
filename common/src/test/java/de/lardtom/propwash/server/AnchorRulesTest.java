package de.lardtom.propwash.server;

import de.lardtom.propwash.server.AnchorRules.Decision;
import de.lardtom.propwash.server.AnchorRules.Situation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnchorRulesTest {
    private static final GeneratedArea.Scan ALL = GeneratedArea.unrestricted();

    private static Situation situation(boolean unlimited, double distance, boolean onlyGenerated, GeneratedArea.Scan area) {
        return new Situation(true, unlimited, distance, 160.0, onlyGenerated, area, false);
    }

    @Test
    void unlimitedRangeAnchorsAtTheDroneAtAnyDistance() {
        assertEquals(Decision.ANCHOR, AnchorRules.decide(situation(true, 5.0, true, ALL)));
        assertEquals(Decision.ANCHOR, AnchorRules.decide(situation(true, 25_000.0, true, ALL)));
    }

    @Test
    void limitedRangeKeepsTheBodyViewInsideTheLimit() {
        assertEquals(Decision.LIMITED, AnchorRules.decide(situation(false, 100.0, true, ALL)));
        assertEquals(Decision.LIMITED, AnchorRules.decide(situation(false, 161.0, true, ALL)));
    }

    @Test
    void limitedRangeReleasesBeyondTheLimit() {
        assertEquals(Decision.RELEASE_TOO_FAR, AnchorRules.decide(situation(false, 300.0, true, ALL)));
        assertEquals(Decision.RELEASE_TOO_FAR, AnchorRules.decide(situation(false, Double.NaN, true, ALL)));
    }

    @Test
    void otherDimensionDetaches() {
        Situation s = new Situation(false, true, Double.POSITIVE_INFINITY, 160.0, true, ALL, false);
        assertEquals(Decision.DETACHED, AnchorRules.decide(s));
        assertFalse(AnchorRules.decide(s).releases());
    }

    @Test
    void spawnProtectionReleasesEvenWithUnlimitedRange() {
        Situation s = new Situation(true, true, 10.0, 160.0, true, ALL, true);
        assertEquals(Decision.RELEASE_PROTECTED, AnchorRules.decide(s));
        assertTrue(AnchorRules.decide(s).releases());
    }

    @Test
    void unexploredTerrainReleasesOnlyWhenTheDroneIsInsideIt() {
        GeneratedArea.Scan inside = new GeneratedArea.Scan(-1, 0, true);
        assertEquals(Decision.RELEASE_UNEXPLORED, AnchorRules.decide(situation(true, 500.0, true, inside)));
        assertEquals(Decision.ANCHOR, AnchorRules.decide(situation(true, 500.0, false, inside)));
    }

    @Test
    void unexploredTerrainNextToTheDroneKeepsThePilotBecauseTheFenceBrakes() {
        for (int ring = 1; ring <= AnchorRules.DRONE_TICKET_RADIUS; ring++) {
            GeneratedArea.Scan border = new GeneratedArea.Scan(ring - 1, ring, true);
            assertEquals(Decision.ANCHOR, AnchorRules.decide(situation(true, 500.0, true, border)), "Ring " + ring);
            assertEquals(-1, AnchorRules.droneTicketRadius(Decision.ANCHOR, border, true), "Ring " + ring);
        }
    }

    @Test
    void unknownChunksDoNotReleaseWhileTheyAreChecked() {
        GeneratedArea.Scan pending = new GeneratedArea.Scan(0, GeneratedArea.NONE, false);
        assertEquals(Decision.ANCHOR, AnchorRules.decide(situation(true, 500.0, true, pending)));
        assertEquals(-1, AnchorRules.droneTicketRadius(Decision.ANCHOR, pending, true));
    }

    @Test
    void missingChunksFurtherAwayOnlyShrinkTheViewTicket() {
        GeneratedArea.Scan far = new GeneratedArea.Scan(6, 7, true);
        assertEquals(Decision.ANCHOR, AnchorRules.decide(situation(true, 500.0, true, far)));
        assertEquals(AnchorRules.DRONE_TICKET_RADIUS, AnchorRules.droneTicketRadius(Decision.ANCHOR, far, true));
        assertEquals(6, AnchorRules.viewTicketRadius(Decision.ANCHOR, 10, far, true));
        assertEquals(12, AnchorRules.viewTicketRadius(Decision.ANCHOR, 10, far, false));
        assertEquals(12, AnchorRules.viewTicketRadius(Decision.ANCHOR, 10, ALL, true));
    }

    @Test
    void viewTicketOnlyWhileAnchored() {
        assertEquals(-1, AnchorRules.viewTicketRadius(Decision.LIMITED, 10, ALL, false));
        assertEquals(AnchorRules.DRONE_TICKET_RADIUS, AnchorRules.droneTicketRadius(Decision.LIMITED, ALL, false));
        assertEquals(-1, AnchorRules.droneTicketRadius(Decision.DETACHED, ALL, false));
        assertEquals(-1, AnchorRules.droneTicketRadius(Decision.RELEASE_TOO_FAR, ALL, false));
    }

    @Test
    void viewTicketTooCloseToTheBorderIsDropped() {
        GeneratedArea.Scan tight = new GeneratedArea.Scan(2, 3, true);
        assertEquals(-1, AnchorRules.viewTicketRadius(Decision.ANCHOR, 10, tight, true));
    }

    @Test
    void limitedRangeStaysInsideTheTrackedChunks() {
        assertEquals(144.0, AnchorRules.limitedRange(500.0, 10));
        assertEquals(96.0, AnchorRules.limitedRange(96.0, 10));
        assertEquals(AnchorRules.MIN_LIMITED_RANGE, AnchorRules.limitedRange(500.0, 2));
    }
}
