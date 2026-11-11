package de.lardtom.propwash.dev;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureJoinStopTest {
    @Test
    void cleanStateAfterTheLeaveReportsAtOnce() {
        assertTrue(CaptureJoinCheck.reportDue(0, 0, 0, 0, 1));
        assertTrue(CaptureJoinCheck.reportDue(1, 0, 0, 0, 2));
    }

    @Test
    void leftoversWaitUntilTheLeaveCheckRunsOut() {
        for (int since = 0; since < CaptureJoinCheck.LEAVE_CHECK; since++) {
            assertFalse(CaptureJoinCheck.reportDue(since, 0, 0, 1, 1), "Ticket nach " + since);
            assertFalse(CaptureJoinCheck.reportDue(since, 1, 0, 0, 1), "Sitzung nach " + since);
            assertFalse(CaptureJoinCheck.reportDue(since, 0, 1, 0, 1), "Lage nach " + since);
            assertFalse(CaptureJoinCheck.reportDue(since, 0, 0, 0, 0), "ohne Ende durch Trennen nach " + since);
        }
        assertTrue(CaptureJoinCheck.reportDue(CaptureJoinCheck.LEAVE_CHECK, 1, 1, 1, 0));
    }

    @Test
    void stopWaitsOnlyForSwitchedOnChecksThatArePending() {
        ServerSmoke.ServerOnlyCheck open = new ServerSmoke.ServerOnlyCheck("Kamera-Chunks", true, () -> "Trennen nicht geprüft", () -> true);
        ServerSmoke.ServerOnlyCheck off = new ServerSmoke.ServerOnlyCheck("Kamera-Chunks", false, () -> "Trennen nicht geprüft", () -> true);
        ServerSmoke.ServerOnlyCheck done = new ServerSmoke.ServerOnlyCheck("Kamera-Chunks", true, () -> null, () -> false);
        ServerSmoke.ServerOnlyCheck plain = new ServerSmoke.ServerOnlyCheck("Weiter Flug ohne Pilot", true, () -> "nach 2 von 5 Durchgängen nicht fertig");
        assertTrue(ServerSmoke.pending(List.of(plain, open)));
        assertFalse(ServerSmoke.pending(List.of(off, done, plain)));
        assertFalse(ServerSmoke.pending(List.of()));
    }

    @Test
    void captureCheckWithoutPlayerIsNotPendingAtTheStop() {
        String property = JoinTestEnv.CAPTURE.toLowerCase(Locale.ROOT).replace('_', '.');
        String before = System.getProperty(property);
        System.setProperty(property, "1");
        try {
            assertFalse(ServerSmoke.pending(ServerSmoke.serverOnlyChecks()));
            assertEquals("FAIL Kamera-Chunks: kein Spieler beigetreten", ServerSmoke.stopResult(null, ServerSmoke.serverOnlyChecks()));
        } finally {
            if (before == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, before);
            }
        }
    }
}
