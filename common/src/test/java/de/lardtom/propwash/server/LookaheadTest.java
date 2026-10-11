package de.lardtom.propwash.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LookaheadTest {
    @Test
    void standingDroneHasNoLookahead() {
        assertEquals(0, Lookahead.aheadChunks(0.0, 12));
        assertEquals(0, Lookahead.aheadChunks(Lookahead.MIN_SPEED - 0.1, 12));
        assertEquals(0, Lookahead.aheadChunks(Double.NaN, 12));
        assertEquals(-1, Lookahead.radius(12, 0));
    }

    @Test
    void racingSpeedPreloadsSeveralChunksAhead() {
        double hundredKmh = 100.0 / 3.6;
        int ahead = Lookahead.aheadChunks(hundredKmh, 12);
        assertEquals(7, ahead);
        int radius = Lookahead.radius(12, ahead);
        assertEquals(StreamBudget.viewTicketRadius(12) - 4, radius);
        assertTrue(ahead + radius > StreamBudget.viewTicketRadius(12), "Vorausladen reicht über die Sicht-Tickets hinaus");
    }

    @Test
    void lookaheadIsCappedAndNeverSmallerThanTheDroneTicket() {
        int ahead = Lookahead.aheadChunks(10_000.0, 6);
        assertEquals(12, ahead);
        assertEquals(Lookahead.MIN_RADIUS, Lookahead.radius(6, ahead));
        assertTrue(Lookahead.radius(2, 4) >= AnchorRules.DRONE_TICKET_RADIUS + 1);
    }

    @Test
    void centerLiesAlongTheFlightDirection() {
        long center = Lookahead.center(8.0, 8.0, 0.0, -4.0, 5);
        assertEquals(0, Lookahead.unpackX(center));
        assertEquals(-5, Lookahead.unpackZ(center));
        long diagonal = Lookahead.center(-8.0, -8.0, -1.0, -1.0, 4);
        assertTrue(Lookahead.unpackX(diagonal) <= -3 && Lookahead.unpackZ(diagonal) <= -3);
        long still = Lookahead.center(100.0, -100.0, 0.0, 0.0, 3);
        assertEquals(6, Lookahead.unpackX(still));
        assertEquals(-7, Lookahead.unpackZ(still));
    }

    @Test
    void velocityFollowsMovementAndIgnoresTeleports() {
        Lookahead motion = new Lookahead();
        double z = 0.0;
        for (int tick = 0; tick < 40; tick++) {
            motion.track(0.0, z);
            z -= 4.0;
        }
        assertEquals(80.0, motion.speedPerSecond(), 1.0);
        assertTrue(motion.velocityZ() < 0.0);
        motion.track(0.0, z + 10_000.0);
        assertEquals(0.0, motion.speedPerSecond(), 1e-9);
        motion.track(Double.NaN, 0.0);
        assertFalse(Double.isNaN(motion.speedPerSecond()));
    }

    @Test
    void jitteryPacketsStillGiveTheMeanSpeed() {
        Lookahead motion = new Lookahead();
        double x = 0.0;
        for (int tick = 0; tick < 60; tick++) {
            motion.track(x, 0.0);
            x += tick % 2 == 0 ? 0.0 : 3.0;
        }
        assertEquals(30.0, motion.speedPerSecond(), 8.0);
    }

    @Test
    void smallSpeedChangesDoNotMoveTheTicket() {
        assertEquals(7, Lookahead.stable(7, 6));
        assertEquals(7, Lookahead.stable(7, 8));
        assertEquals(9, Lookahead.stable(7, 9));
        assertEquals(5, Lookahead.stable(0, 5));
        assertEquals(0, Lookahead.stable(7, 0));
        assertTrue(Lookahead.keepCenter(Lookahead.pack(3, -4), Lookahead.pack(4, -5)));
        assertFalse(Lookahead.keepCenter(Lookahead.pack(3, -4), Lookahead.pack(3, -6)));
    }

    @Test
    void packMatchesChunkPosLayout() {
        long packed = Lookahead.pack(-3, 7);
        assertEquals(-3, Lookahead.unpackX(packed));
        assertEquals(7, Lookahead.unpackZ(packed));
        assertEquals((-3L & 0xFFFFFFFFL) | (7L << 32), packed);
    }
}
