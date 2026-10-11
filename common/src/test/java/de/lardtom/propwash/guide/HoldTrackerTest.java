package de.lardtom.propwash.guide;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldTrackerTest {
    @Test
    void hintAppearsAfterHoverDelay() {
        HoldTracker tracker = new HoldTracker(600L, 300L);
        assertFalse(tracker.update("propwash:drone@3", true, false, 1000L).hint());
        assertFalse(tracker.update("propwash:drone@3", true, false, 1200L).hint());
        assertTrue(tracker.update("propwash:drone@3", true, false, 1300L).hint());
        assertFalse(tracker.update("propwash:drone@4", true, false, 1310L).hint());
    }

    @Test
    void holdingFiresOnceAfterTheHoldTime() {
        HoldTracker tracker = new HoldTracker(600L, 300L);
        HoldTracker.Result start = tracker.update("a", true, true, 0L);
        assertTrue(start.hint());
        assertEquals(0.0F, start.progress(), 1.0E-6F);
        assertEquals(0.5F, tracker.update("a", true, true, 300L).progress(), 1.0E-6F);
        assertTrue(tracker.update("a", true, true, 600L).fire());
        assertFalse(tracker.update("a", true, true, 700L).fire());
        assertFalse(tracker.update("a", true, true, 2000L).fire());
        tracker.update("a", true, false, 2100L);
        tracker.update("a", true, true, 2200L);
        assertTrue(tracker.update("a", true, true, 2800L).fire());
    }

    @Test
    void releasingOrLeavingResetsProgress() {
        HoldTracker tracker = new HoldTracker(600L, 300L);
        tracker.update("a", true, true, 0L);
        tracker.update("a", true, true, 500L);
        assertEquals(0.0F, tracker.update("a", true, false, 510L).progress());
        tracker.update("a", true, true, 520L);
        assertFalse(tracker.update("a", true, true, 1000L).fire());
        tracker.update("b", true, true, 1010L);
        assertEquals(0.0F, tracker.update("b", true, true, 1010L).progress(), 1.0E-6F);
        assertFalse(tracker.update("c", false, true, 1100L).hint());
        assertFalse(tracker.update("", true, true, 1200L).hint());
    }
}
