package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceLobbyTest {
    private static final long S = LapTracker.SECOND;
    private static final UUID A = new UUID(0L, 1L);
    private static final UUID B = new UUID(0L, 2L);
    private static final UUID C = new UUID(0L, 3L);

    @Test
    void soloPilotStartsAfterGrace() {
        RaceLobby lobby = new RaceLobby(5);
        assertTrue(lobby.join(A, "Anna", 1, 0L));
        assertEquals(RaceLobby.MIN_COUNTDOWN_SECONDS, lobby.countdownSeconds());
        assertFalse(lobby.shouldStart(List.of(A), RaceLobby.SOLO_GRACE_NANOS - 1L));
        assertTrue(lobby.shouldStart(List.of(A), RaceLobby.SOLO_GRACE_NANOS));
        RaceLobby.Heat heat = lobby.start(S);
        assertTrue(lobby.isEmpty());
        assertEquals(List.of(A), heat.pilots());
        assertFalse(heat.multiplayer());
    }

    @Test
    void waitsForEveryPilotAtTheStartUntilTheDeadline() {
        RaceLobby lobby = new RaceLobby(5);
        lobby.join(A, "Anna", 5, 0L);
        assertEquals(5, lobby.countdownSeconds());
        assertFalse(lobby.shouldStart(List.of(A, B), 2L * S));
        lobby.join(B, "Ben", 9, 3L * S);
        assertEquals(5, lobby.countdownSeconds());
        assertEquals(List.of("Anna", "Ben"), lobby.names());
        assertFalse(lobby.shouldStart(List.of(A, B, C), 4L * S));
        assertTrue(lobby.shouldStart(List.of(A, B), 4L * S));
        assertTrue(lobby.shouldStart(List.of(A, B, C), RaceLobby.WAIT_NANOS));
        assertEquals(RaceLobby.WAIT_NANOS - 4L * S, lobby.remainingNanos(4L * S));
    }

    @Test
    void leavingEmptiesTheLobby() {
        RaceLobby lobby = new RaceLobby(2);
        lobby.join(A, "Anna", 3, 0L);
        assertTrue(lobby.leave(A));
        assertFalse(lobby.leave(A));
        assertTrue(lobby.isEmpty());
        assertFalse(lobby.shouldStart(List.of(), 20L * S));
    }

    @Test
    void lobbyIsLimited() {
        RaceLobby lobby = new RaceLobby(2);
        for (int i = 0; i < RaceLobby.MAX_MEMBERS; i++) {
            assertTrue(lobby.join(new UUID(1L, i), "P" + i, 3, 0L));
        }
        assertFalse(lobby.join(C, "Carl", 3, 0L));
    }

    @Test
    void heatCountsPlacesAndIgnoresDroppedPilots() {
        RaceLobby lobby = new RaceLobby(1);
        lobby.join(A, "Anna", 3, 0L);
        lobby.join(B, "Ben", 3, 0L);
        lobby.join(C, "Carl", 3, 0L);
        RaceLobby.Heat heat = lobby.start(S);
        assertTrue(heat.multiplayer());
        heat.drop(B);
        assertEquals(1, heat.finish(C));
        assertEquals(-1, heat.finish(C));
        assertEquals(-1, heat.finish(B));
        assertFalse(heat.done(2L * S));
        assertEquals(2, heat.finish(A));
        assertTrue(heat.done(2L * S));
        assertEquals(-1, heat.finish(new UUID(9L, 9L)));
    }

    @Test
    void startLightsCountDown() {
        assertEquals(3, StartLights.lamps(3L * S));
        assertEquals(3, StartLights.lamps(S));
        assertEquals(5, StartLights.lamps(10L * S));
        assertEquals(1, StartLights.lit(3L * S, 3L * S));
        assertEquals(2, StartLights.lit(3L * S, 2L * S));
        assertEquals(3, StartLights.lit(3L * S, S / 2L));
        assertEquals(0, StartLights.lit(10L * S, 8L * S));
        assertEquals(1, StartLights.lit(10L * S, 5L * S));
        assertEquals(5, StartLights.lit(10L * S, 1L));
    }
}
