package de.lardtom.propwash.apiimpl;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportRateLimitTest {
    private static final UUID ALICE = new UUID(1L, 1L);
    private static final UUID BOB = new UUID(2L, 2L);

    @Test
    void twoRequestsPerSecondAndPlayer() {
        ImportRateLimit limit = new ImportRateLimit();
        assertTrue(limit.tryAcquire(ALICE, 100));
        assertTrue(limit.tryAcquire(ALICE, 100));
        assertFalse(limit.tryAcquire(ALICE, 100));
        assertFalse(limit.tryAcquire(ALICE, 119));
        assertTrue(limit.tryAcquire(BOB, 100));
        assertTrue(limit.tryAcquire(ALICE, 120));
        assertTrue(limit.tryAcquire(ALICE, 120));
        assertFalse(limit.tryAcquire(ALICE, 121));
        assertFalse(limit.tryAcquire(ALICE, 139));
        assertTrue(limit.tryAcquire(ALICE, 140));
    }

    @Test
    void rejectedRequestsDoNotExtendTheWindow() {
        ImportRateLimit limit = new ImportRateLimit(1, 20);
        assertTrue(limit.tryAcquire(ALICE, 0));
        for (int tick = 1; tick < 20; tick++) {
            assertFalse(limit.tryAcquire(ALICE, tick));
        }
        assertTrue(limit.tryAcquire(ALICE, 20));
    }

    @Test
    void forgottenPlayersStartFreshAndOldEntriesArePruned() {
        ImportRateLimit limit = new ImportRateLimit(1, 20);
        assertTrue(limit.tryAcquire(ALICE, 0));
        limit.forget(ALICE);
        assertTrue(limit.tryAcquire(ALICE, 1));
        for (int i = 0; i < 100; i++) {
            limit.tryAcquire(new UUID(9L, i), 5);
        }
        limit.tryAcquire(BOB, 1000);
        assertEquals(1, limit.tracked());
        assertThrows(IllegalArgumentException.class, () -> new ImportRateLimit(0, 20));
    }
}
