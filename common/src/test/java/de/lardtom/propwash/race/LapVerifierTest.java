package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LapVerifierTest {
    private static final int COURSE = 7;
    private static final long S = LapTracker.SECOND;

    private static void lap(LapVerifier verifier, int gates, long start, long duration) {
        verifier.record(COURSE, 0, start);
        for (int i = 1; i < gates; i++) {
            verifier.record(COURSE, i, start + duration * i / gates);
        }
        verifier.record(COURSE, 0, start + duration);
    }

    @Test
    void noCrossingsMeansNoLap() {
        assertNull(new LapVerifier().find(COURSE, 4, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE));
    }

    @Test
    void fullLapInOrderIsFound() {
        LapVerifier verifier = new LapVerifier();
        lap(verifier, 4, 10 * S, 20 * S);
        LapVerifier.VerifiedLap found = verifier.find(COURSE, 4, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE);
        assertNotNull(found);
        assertEquals(20 * S, found.nanos());
    }

    @Test
    void skippedGateIsNotALap() {
        LapVerifier verifier = new LapVerifier();
        verifier.record(COURSE, 0, S);
        verifier.record(COURSE, 1, 2 * S);
        verifier.record(COURSE, 3, 3 * S);
        verifier.record(COURSE, 0, 4 * S);
        assertNull(verifier.find(COURSE, 4, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE));
    }

    @Test
    void gatesOutOfOrderAreNotALap() {
        LapVerifier verifier = new LapVerifier();
        verifier.record(COURSE, 0, S);
        verifier.record(COURSE, 2, 2 * S);
        verifier.record(COURSE, 1, 3 * S);
        verifier.record(COURSE, 0, 4 * S);
        assertNull(verifier.find(COURSE, 3, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE));
    }

    @Test
    void otherCourseDoesNotCount() {
        LapVerifier verifier = new LapVerifier();
        verifier.record(COURSE + 1, 0, S);
        verifier.record(COURSE + 1, 1, 2 * S);
        verifier.record(COURSE + 1, 0, 3 * S);
        assertNull(verifier.find(COURSE, 2, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE));
    }

    @Test
    void consecutiveLapsShareTheirBoundaryAndCannotBeReused() {
        LapVerifier verifier = new LapVerifier();
        lap(verifier, 3, 0L, 10 * S);
        LapVerifier.VerifiedLap first = verifier.find(COURSE, 3, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE);
        assertNotNull(first);
        assertNull(verifier.find(COURSE, 3, first.endSequence(), Long.MIN_VALUE));
        verifier.record(COURSE, 1, 13 * S);
        verifier.record(COURSE, 2, 16 * S);
        verifier.record(COURSE, 0, 19 * S);
        LapVerifier.VerifiedLap second = verifier.find(COURSE, 3, first.endSequence(), Long.MIN_VALUE);
        assertNotNull(second);
        assertEquals(10 * S, second.startNanos());
        assertEquals(9 * S, second.nanos());
    }

    @Test
    void crossingsBeforeTheStartWindowAreIgnored() {
        LapVerifier verifier = new LapVerifier();
        lap(verifier, 2, 0L, 10 * S);
        assertNull(verifier.find(COURSE, 2, LapVerifier.NO_SEQUENCE, 5 * S));
    }

    @Test
    void singleGateCourseNeedsTwoCrossings() {
        LapVerifier verifier = new LapVerifier();
        verifier.record(COURSE, 0, S);
        assertNull(verifier.find(COURSE, 1, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE));
        verifier.record(COURSE, 0, 5 * S);
        LapVerifier.VerifiedLap found = verifier.find(COURSE, 1, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE);
        assertNotNull(found);
        assertEquals(4 * S, found.nanos());
    }

    @Test
    void historyIsBounded() {
        LapVerifier verifier = new LapVerifier();
        for (int i = 0; i < LapVerifier.MAX_CROSSINGS + 100; i++) {
            verifier.record(COURSE, 1, i);
        }
        assertEquals(LapVerifier.MAX_CROSSINGS, verifier.size());
        verifier.prune(LapVerifier.MAX_CROSSINGS);
        assertEquals(100, verifier.size());
    }

    @Test
    void firstMissingNamesTheCheckpointThatBreaksTheChain() {
        LapVerifier verifier = new LapVerifier();
        verifier.record(COURSE, 0, S);
        verifier.record(COURSE, 1, 2 * S);
        verifier.record(COURSE, 3, 3 * S);
        verifier.record(COURSE, 0, 4 * S);
        assertEquals(2, verifier.firstMissing(COURSE, 4, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE, false), "Prüfpunkt 2 fehlt");
    }

    @Test
    void firstMissingAtTheStartAndAtTheEnd() {
        LapVerifier verifier = new LapVerifier();
        assertEquals(0, verifier.firstMissing(COURSE, 3, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE, false), "Ohne Start-Durchflug fehlt der Start");
        verifier.record(COURSE, 0, S);
        verifier.record(COURSE, 1, 2 * S);
        verifier.record(COURSE, 2, 3 * S);
        assertEquals(0, verifier.firstMissing(COURSE, 3, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE, false), "Ohne Ziel-Durchflug fehlt wieder Prüfpunkt 0");
        verifier.record(COURSE, 0, 4 * S);
        assertEquals(LapVerifier.NOTHING_MISSING, verifier.firstMissing(COURSE, 3, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE, false));
        assertNotNull(verifier.find(COURSE, 3, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE));
    }

    @Test
    void firstMissingOnPointToPointEndsAtTheLastCheckpoint() {
        LapVerifier verifier = new LapVerifier();
        verifier.record(COURSE, 0, S);
        verifier.record(COURSE, 1, 2 * S);
        assertEquals(2, verifier.firstMissing(COURSE, 3, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE, true));
        verifier.record(COURSE, 2, 3 * S);
        assertEquals(LapVerifier.NOTHING_MISSING, verifier.firstMissing(COURSE, 3, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE, true));
    }

    @Test
    void firstMissingKeepsTwinCrossingsApart() {
        LapVerifier verifier = new LapVerifier();
        verifier.record(COURSE, 0, S, 100L);
        verifier.record(COURSE, 2, S, 100L);
        verifier.record(COURSE, 1, 2 * S, 101L);
        verifier.record(COURSE, 0, 3 * S, 102L);
        verifier.record(COURSE, 2, 3 * S, 102L);
        assertEquals(0, verifier.firstMissing(COURSE, 3, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE, false), "Rundenende braucht einen eigenen Durchflug");
    }

    @Test
    void restartMakesTheLastFinishPassTheNextStart() {
        LapVerifier verifier = new LapVerifier();
        verifier.record(COURSE, 0, S);
        verifier.record(COURSE, 2, 2 * S);
        verifier.record(COURSE, 0, 3 * S);
        assertNull(verifier.find(COURSE, 3, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE), "Prüfpunkt 1 fehlt: Runde 1 zählt nicht");
        long restart = verifier.restart(COURSE, 0);
        assertEquals(verifier.lastSequence(), restart, "Der letzte Durchflug durch Start/Ziel");
        verifier.record(COURSE, 1, 4 * S);
        verifier.record(COURSE, 2, 5 * S);
        verifier.record(COURSE, 0, 6 * S);
        LapVerifier.VerifiedLap next = verifier.find(COURSE, 3, restart, Long.MIN_VALUE);
        assertNotNull(next, "Runde 2 zählt wieder");
        assertEquals(3 * S, next.startNanos());
        assertEquals(6 * S, next.endNanos());
    }

    @Test
    void restartWithoutFinishPassWaitsForTheNextOne() {
        LapVerifier verifier = new LapVerifier();
        verifier.record(COURSE, 1, S);
        long restart = verifier.restart(COURSE, 0);
        assertEquals(verifier.lastSequence(), restart);
        verifier.record(COURSE, 2, 2 * S);
        verifier.record(COURSE, 0, 3 * S);
        assertNull(verifier.find(COURSE, 3, restart, Long.MIN_VALUE), "Runde 2 hat keinen Start");
        verifier.record(COURSE, 1, 4 * S);
        verifier.record(COURSE, 2, 5 * S);
        verifier.record(COURSE, 0, 6 * S);
        assertNotNull(verifier.find(COURSE, 3, restart, Long.MIN_VALUE), "Runde 3 setzt am nächsten Start-Durchflug an");
    }
}
