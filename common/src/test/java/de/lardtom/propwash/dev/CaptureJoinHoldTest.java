package de.lardtom.propwash.dev;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureJoinHoldTest {
    private static final int TICKET_TICKS = 40;

    @Test
    void farDroneStaysLoadedUntilALateRequestIsReleased() {
        for (int release : new int[]{1, 5, 39, 40, 60, 100, 300, 2400}) {
            int held = 0;
            int end = release + CaptureJoinCheck.DISCARD_DELAY;
            for (int tick = 1; tick < end; tick++) {
                int sinceRelease = tick < release ? -1 : tick - release;
                if (CaptureJoinCheck.holdRadius(tick, sinceRelease) >= 0) {
                    held = tick;
                }
                assertTrue(tick - held < TICKET_TICKS, "Abmelden nach " + release + " Ticks: Lage ab Tick " + (held + TICKET_TICKS)
                        + " ohne Ticket, die Drohne entlädt vor der Anfrage");
            }
        }
    }

    @Test
    void farDroneStaysLoadedWhileTheClientNeverAsks() {
        int held = 0;
        for (int tick = 1; tick < 20 * 120; tick++) {
            if (CaptureJoinCheck.holdRadius(tick, -1) >= 0) {
                held = tick;
            }
            assertTrue(tick - held < TICKET_TICKS, "ohne Anfrage: Lage ab Tick " + (held + TICKET_TICKS) + " ohne Ticket");
        }
    }

    @Test
    void holdEndsWithTheDiscard() {
        for (int since = CaptureJoinCheck.DISCARD_DELAY; since < CaptureJoinCheck.DISCARD_DELAY + 200; since++) {
            assertEquals(-1, CaptureJoinCheck.holdRadius(1000 + since, since), "nach " + since + " Ticks");
        }
    }
}
