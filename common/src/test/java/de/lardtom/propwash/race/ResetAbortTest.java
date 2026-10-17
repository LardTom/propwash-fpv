package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResetAbortTest {
    private static final long MS = 1_000_000L;
    private static final double Y = 65.5;

    private static List<GateGeometry> course() {
        return List.of(
                GateGeometry.of(0.0, 64.0, 0.0, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(0.0, 64.0, 10.0, 0.0F, GateSize.MEDIUM),
                GateGeometry.of(0.0, 64.0, 20.0, 0.0F, GateSize.MEDIUM));
    }

    private static final class Flight {
        final LapTracker tracker = new LapTracker();
        final List<LapTracker.Event> events = new ArrayList<>();
        long now = 1_000L * MS;
        double x;
        double z = -1.0E-6;

        Flight() {
            this.tracker.setLapTarget(3);
            this.tracker.setCourse(7, course(), 0L);
            this.tracker.update(this.now, this.x, Y, this.z);
        }

        void to(double tx, double tz) {
            double dx = tx - this.x;
            double dz = tz - this.z;
            int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) / 0.4));
            double sx = this.x;
            double sz = this.z;
            for (int i = 1; i <= steps; i++) {
                this.now += 40L * MS;
                this.x = sx + dx * i / steps;
                this.z = sz + dz * i / steps;
                this.tracker.update(this.now, this.x, Y, this.z);
                this.events.addAll(this.tracker.drainEvents());
            }
        }

        void almostLap() {
            this.to(0.0, 25.0);
            this.to(10.0, 25.0);
            this.to(10.0, -5.0);
            this.to(0.0, -5.0);
            this.to(0.0, -0.3);
        }

        void teleport(double tx, double tz) {
            this.now += 40L * MS;
            this.x = tx;
            this.z = tz;
            this.tracker.update(this.now, tx, Y, tz);
            this.events.addAll(this.tracker.drainEvents());
        }

        long count(LapTracker.EventType type) {
            return this.events.stream().filter(e -> e.type() == type).count();
        }
    }

    @Test
    void resetAbortsTheRunAndTheJumpCountsNothing() {
        Flight flight = new Flight();
        flight.almostLap();
        assertTrue(flight.tracker.racing());
        assertEquals(1, flight.count(LapTracker.EventType.START));
        assertEquals(0, flight.count(LapTracker.EventType.LAP));
        flight.tracker.abort(flight.now, LapTracker.AbortReason.RESET);
        flight.tracker.resetPosition();
        flight.events.addAll(flight.tracker.drainEvents());
        flight.teleport(0.0, 0.3);
        assertFalse(flight.tracker.racing());
        assertEquals(LapTracker.Phase.IDLE, flight.tracker.phase());
        assertEquals(0, flight.count(LapTracker.EventType.LAP));
        assertEquals(1, flight.count(LapTracker.EventType.START));
        LapTracker.Event abort = flight.events.stream().filter(e -> e.type() == LapTracker.EventType.ABORT).findFirst().orElseThrow();
        assertEquals(LapTracker.AbortReason.RESET, abort.reason());
    }

    @Test
    void withoutTheResetTheSameJumpWouldCompleteALap() {
        Flight flight = new Flight();
        flight.almostLap();
        flight.teleport(0.0, 0.3);
        assertEquals(1, flight.count(LapTracker.EventType.LAP));
    }

    @Test
    void afterAResetGateOneStartsAFreshRun() {
        Flight flight = new Flight();
        flight.almostLap();
        flight.tracker.abort(flight.now, LapTracker.AbortReason.RESET);
        flight.tracker.resetPosition();
        flight.teleport(0.0, -3.0);
        flight.to(0.0, 2.0);
        assertTrue(flight.tracker.racing());
        assertEquals(2, flight.count(LapTracker.EventType.START));
        assertEquals(0, flight.count(LapTracker.EventType.LAP));
    }
}
