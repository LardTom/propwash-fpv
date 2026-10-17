package de.lardtom.propwash.rescue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeSyncTest {
    @Test
    void serverStateCountsUntilThePilotPushes() {
        HomeSync sync = new HomeSync();
        sync.beginSession();
        assertTrue(sync.accepts(0));
        int first = sync.push();
        assertFalse(sync.accepts(0));
        assertTrue(sync.accepts(first));
        int second = sync.push();
        assertTrue(second > first);
        assertFalse(sync.accepts(first));
        assertTrue(sync.accepts(second));
    }

    @Test
    void newSessionTakesTheServerAssignment() {
        HomeSync sync = new HomeSync();
        sync.beginSession();
        sync.push();
        sync.push();
        sync.beginSession();
        assertTrue(sync.accepts(0));
        int next = sync.push();
        assertEquals(3, next);
        assertFalse(sync.accepts(0));
    }

    @Test
    void sessionStartDecision() {
        assertEquals(HomeSync.Start.PUSH_OWN, HomeSync.onStart(true, false, false));
        assertEquals(HomeSync.Start.PUSH_OWN, HomeSync.onStart(true, true, false));
        assertEquals(HomeSync.Start.KEEP, HomeSync.onStart(true, true, true));
        assertEquals(HomeSync.Start.ADOPT_STORED, HomeSync.onStart(false, true, false));
        assertEquals(HomeSync.Start.KEEP, HomeSync.onStart(false, false, false));
    }
}
