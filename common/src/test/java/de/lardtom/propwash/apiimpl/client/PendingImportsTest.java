package de.lardtom.propwash.apiimpl.client;

import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingImportsTest {
    @Test
    void answersCompleteTheMatchingRequestOnce() {
        PendingImports pending = new PendingImports(10);
        PendingImports.Ticket first = pending.open();
        PendingImports.Ticket second = pending.open();
        assertNotEquals(first.id(), second.id());
        assertTrue(pending.complete(second.id(), ImportResult.of(ImportStatus.APPLIED)));
        assertFalse(pending.complete(second.id(), ImportResult.of(ImportStatus.BUSY)));
        assertEquals(ImportStatus.APPLIED, second.future().join().status());
        assertFalse(first.future().isDone());
        assertEquals(1, pending.size());
    }

    @Test
    void unansweredRequestsTimeOutAsNoTarget() {
        PendingImports pending = new PendingImports(3);
        PendingImports.Ticket ticket = pending.open();
        pending.tick();
        pending.tick();
        assertFalse(ticket.future().isDone());
        pending.tick();
        assertEquals(ImportStatus.NO_TARGET, ticket.future().join().status());
        assertEquals(0, pending.size());
    }

    @Test
    void disconnectFailsEveryOpenRequestAndCallbacksMayReenter() {
        PendingImports pending = new PendingImports(10);
        PendingImports.Ticket a = pending.open();
        AtomicReference<PendingImports.Ticket> reopened = new AtomicReference<>();
        a.future().thenRun(() -> reopened.set(pending.open()));
        PendingImports.Ticket b = pending.open();
        pending.failAll(ImportStatus.NO_TARGET);
        assertEquals(ImportStatus.NO_TARGET, a.future().join().status());
        assertEquals(ImportStatus.NO_TARGET, b.future().join().status());
        assertEquals(1, pending.size());
        assertFalse(reopened.get().future().isDone());
    }
}
