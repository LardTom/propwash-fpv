package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceClockTest {
    private static final long S = LapTracker.SECOND;

    @Test
    void clockStandsStillWhilePaused() {
        RaceClock clock = new RaceClock();
        assertEquals(10L * S, clock.now(10L * S));
        clock.setPaused(true, 10L * S);
        assertTrue(clock.paused());
        assertEquals(10L * S, clock.now(25L * S));
        clock.setPaused(false, 30L * S);
        assertFalse(clock.paused());
        assertEquals(12L * S, clock.now(32L * S));
    }

    @Test
    void repeatedPauseSignalsDoNotShiftTwice() {
        RaceClock clock = new RaceClock();
        clock.setPaused(true, S);
        clock.setPaused(true, 3L * S);
        clock.setPaused(false, 5L * S);
        clock.setPaused(false, 6L * S);
        assertEquals(3L * S, clock.now(7L * S));
    }

    @Test
    void pausedCountdownDoesNotReachGoAndLapExcludesPause() {
        List<GateGeometry> gates = List.of(GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(0.0, 64.0, 10.0, 0.0F, GateSize.MEDIUM));
        RaceClock clock = new RaceClock();
        LapTracker tracker = new LapTracker();
        tracker.setCourse(1, gates, clock.now(0L));
        tracker.update(clock.now(0L), 0.0, 65.5, -1.0);
        tracker.beginCountdown(clock.now(0L), 3L * S);
        tracker.drainEvents();
        clock.setPaused(true, S);
        clock.setPaused(false, 60L * S);
        tracker.tick(clock.now(60L * S));
        assertEquals(LapTracker.Phase.COUNTDOWN, tracker.phase());
        assertEquals(2L * S, tracker.countdownRemainingNanos(clock.now(60L * S)));
        tracker.tick(clock.now(62L * S));
        assertEquals(LapTracker.Phase.RACING, tracker.phase());
        assertEquals(S, tracker.currentLapNanos(clock.now(63L * S)));
    }
}
