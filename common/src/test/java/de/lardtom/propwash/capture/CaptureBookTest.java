package de.lardtom.propwash.capture;

import de.lardtom.propwash.api.drone.CameraChunkResult;
import de.lardtom.propwash.api.drone.CameraChunkSupply;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureBookTest {
    private static final int BUDGET = 4096;
    private final UUID pilot = UUID.randomUUID();
    private final UUID droneA = UUID.randomUUID();
    private final UUID droneB = UUID.randomUUID();
    private final UUID droneC = UUID.randomUUID();

    @Test
    void openUpdateAndClose() {
        CaptureBook book = new CaptureBook();
        CameraChunkResult opened = book.open(this.pilot, this.droneA, 11, 3, 4, false, BUDGET);
        assertEquals(CameraChunkResult.Status.OPENED, opened.status());
        assertEquals(3, opened.radius());
        assertTrue(opened.accepted());
        assertEquals(1, book.count());
        assertEquals(CaptureRules.cost(3), book.chunks());
        assertTrue(book.has(this.pilot));
        CaptureBook.Supply supply = book.get(this.pilot, this.droneA);
        assertNotNull(supply);
        assertFalse(supply.supplying());
        CameraChunkResult updated = book.open(this.pilot, this.droneA, 12, 20, 4, false, BUDGET);
        assertEquals(CameraChunkResult.Status.UPDATED, updated.status());
        assertEquals(4, updated.radius());
        assertEquals(12, supply.entityId());
        assertEquals(20, supply.requested());
        assertEquals(1, book.count());
        assertTrue(book.close(this.pilot, this.droneA));
        assertFalse(book.close(this.pilot, this.droneA));
        assertNull(book.get(this.pilot, this.droneA));
        assertTrue(book.isEmpty());
        assertFalse(book.has(this.pilot));
        assertEquals(0, book.chunks());
    }

    @Test
    void refusalsLeaveNoTrace() {
        CaptureBook book = new CaptureBook();
        assertEquals(CameraChunkResult.Status.RULE_OFF, book.open(this.pilot, this.droneA, 1, 4, 0, false, BUDGET).status());
        assertTrue(book.isEmpty());
        assertEquals(CameraChunkResult.Status.SERVER_BUSY, book.open(this.pilot, this.droneA, 1, 4, 4, true, BUDGET).status());
        assertEquals(CameraChunkResult.Status.SERVER_BUSY, book.open(this.pilot, this.droneA, 1, 4, 4, false, CaptureRules.cost(4) - 1).status());
        assertTrue(book.isEmpty());
        assertFalse(CameraChunkResult.refused(CameraChunkResult.Status.TOO_MANY).accepted());
        assertEquals(0, CameraChunkResult.refused(CameraChunkResult.Status.NOT_OWN_DRONE).radius());
    }

    @Test
    void atMostTwoPerPlayerAndSixteenOnTheServer() {
        CaptureBook book = new CaptureBook();
        assertTrue(book.open(this.pilot, this.droneA, 1, 2, 4, false, BUDGET).accepted());
        assertTrue(book.open(this.pilot, this.droneB, 2, 2, 4, false, BUDGET).accepted());
        assertEquals(CameraChunkResult.Status.TOO_MANY, book.open(this.pilot, this.droneC, 3, 2, 4, false, BUDGET).status());
        assertEquals(CameraChunkSupply.MAX_PER_PLAYER, book.supplies(this.pilot).size());
        assertTrue(book.open(this.pilot, this.droneA, 1, 3, 4, false, BUDGET).accepted(), "Ändern zählt nicht als neue Lage");
        for (int i = 0; i < CaptureRules.MAX_SERVER - 2; i++) {
            assertTrue(book.open(UUID.randomUUID(), UUID.randomUUID(), 100 + i, 1, 4, false, BUDGET).accepted(), "Lage " + i);
        }
        assertEquals(CaptureRules.MAX_SERVER, book.count());
        assertEquals(CameraChunkResult.Status.SERVER_BUSY, book.open(UUID.randomUUID(), UUID.randomUUID(), 999, 1, 4, false, BUDGET).status());
        assertEquals(2, book.closeAll(this.pilot));
        assertEquals(0, book.closeAll(this.pilot));
        assertEquals(CaptureRules.MAX_SERVER - 2, book.count());
        assertEquals(CaptureRules.MAX_SERVER - 2, book.players().size());
        book.clear();
        assertTrue(book.isEmpty());
    }

    @Test
    void growingOverTheBudgetKeepsTheOldRadius() {
        CaptureBook book = new CaptureBook();
        int budget = CaptureRules.cost(2) + CaptureRules.cost(3);
        assertTrue(book.open(this.pilot, this.droneA, 1, 2, 8, false, budget).accepted());
        assertTrue(book.open(UUID.randomUUID(), this.droneB, 2, 3, 8, false, budget).accepted());
        CameraChunkResult grow = book.open(this.pilot, this.droneA, 1, 6, 8, false, budget);
        assertEquals(CameraChunkResult.Status.SERVER_BUSY, grow.status());
        assertEquals(2, book.get(this.pilot, this.droneA).radius());
        assertEquals(CameraChunkResult.Status.UPDATED, book.open(this.pilot, this.droneA, 1, 1, 8, false, budget).status());
        assertEquals(1, book.get(this.pilot, this.droneA).radius());
    }

    @Test
    void worldRuleChangesResizeAndPlacementPauses() {
        CaptureBook book = new CaptureBook();
        book.open(this.pilot, this.droneA, 1, 6, 8, false, BUDGET);
        CaptureBook.Supply supply = book.get(this.pilot, this.droneA);
        assertEquals(6, supply.radius());
        assertEquals(2, book.resize(supply, 2));
        assertEquals(CaptureRules.cost(2), book.chunks());
        assertEquals(6, book.resize(supply, 8));
        assertEquals(0, book.resize(supply, 0));
        book.resize(supply, 8);
        book.place(supply, new CameraArea(40, 0, 6), false);
        assertTrue(supply.supplying());
        book.place(supply, new CameraArea(400, 0, 6), true);
        assertTrue(supply.paused());
        assertFalse(supply.supplying());
    }

    @Test
    void lifecycleEndsOnEveryReason() {
        CaptureRules.Check ok = new CaptureRules.Check(true, true, true, true, true, 4);
        CaptureRules.Check[] endings = {
                new CaptureRules.Check(false, true, true, true, true, 4),
                new CaptureRules.Check(true, false, true, true, true, 4),
                new CaptureRules.Check(true, true, false, true, true, 4),
                new CaptureRules.Check(true, true, true, false, true, 4),
                new CaptureRules.Check(true, true, true, true, false, 4),
                new CaptureRules.Check(true, true, true, true, true, 0)
        };
        CaptureRules.End[] expected = {CaptureRules.End.DISCONNECTED, CaptureRules.End.DIED, CaptureRules.End.DRONE_GONE,
                CaptureRules.End.DIMENSION, CaptureRules.End.NOT_OWN_DRONE, CaptureRules.End.RULE_OFF};
        for (int i = 0; i < endings.length; i++) {
            CaptureBook book = new CaptureBook();
            book.open(this.pilot, this.droneA, 1, 4, 4, false, BUDGET);
            for (int t = 0; t < 3; t++) {
                assertNull(CaptureRules.end(ok));
                assertTrue(book.has(this.pilot));
            }
            CaptureRules.End end = CaptureRules.end(endings[i]);
            assertEquals(expected[i], end);
            for (CaptureBook.Supply supply : book.supplies(this.pilot)) {
                book.close(supply.player(), supply.drone());
            }
            assertTrue(book.isEmpty(), expected[i].name());
            assertTrue(book.open(this.pilot, this.droneA, 1, 4, 4, false, BUDGET).accepted(), "nach " + expected[i] + " wieder anmeldbar");
        }
    }
}
