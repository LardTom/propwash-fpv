package de.lardtom.propwash.stats;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightTallyTest {
    private static final UUID PILOT = new UUID(7L, 11L);

    private static int[] fly(FlightTally tally, long fromTick, int packets, int ticksPerPacket, double blocksPerPacket, double speed,
                             float charge) {
        int[] sum = new int[3];
        for (int i = 1; i <= packets; i++) {
            FlightTally.Step step = tally.update(fromTick + (long) i * ticksPerPacket, true, blocksPerPacket, speed, charge);
            sum[0] += step.ticks();
            sum[1] += step.centimeters();
            sum[2] += step.armed() ? 1 : 0;
        }
        return sum;
    }

    @Test
    void armedPacketsCountTheTicksBetweenThem() {
        FlightTally tally = new FlightTally(PILOT, 100L, false, 1.0F);
        FlightTally.Step arm = tally.update(101L, true, 0.0, 0.0, 1.0F);
        assertTrue(arm.armed());
        assertEquals(0, arm.ticks(), "das Scharfschalten selbst ist noch keine Flugzeit");
        int[] sum = fly(tally, 101L, 200, 1, 0.5, 10.0, 1.0F);
        assertEquals(200, sum[0]);
        assertEquals(10_000, sum[1], "200 × 0,5 Blöcke = 100 m");
        assertEquals(0, sum[2], "dauerhaft scharf ist eine Scharfschaltung");
    }

    @Test
    void packetsInTheSameTickAndSlowSendRatesCountOnce() {
        FlightTally tally = new FlightTally(PILOT, 0L, true, 1.0F);
        int total = 0;
        for (int tick = 1; tick <= 40; tick++) {
            for (int burst = 0; burst < 3; burst++) {
                total += tally.update(tick, true, 0.0, 0.0, 1.0F).ticks();
            }
        }
        assertEquals(40, total, "drei Pakete je Tick zählen den Tick einmal");
        FlightTally slow = new FlightTally(PILOT, 0L, true, 1.0F);
        assertEquals(100, fly(slow, 0L, 25, 4, 0.0, 0.0, 1.0F)[0], "5 Hz zählen dieselbe Zeit");
    }

    @Test
    void longGapsAndDisarmedFlightDoNotCount() {
        FlightTally tally = new FlightTally(PILOT, 0L, true, 1.0F);
        assertEquals(0, tally.update(FlightTally.MAX_GAP_TICKS + 1L, true, 3.0, 0.0, 1.0F).ticks(), "Lücke über 1 s");
        assertEquals(5, tally.update(FlightTally.MAX_GAP_TICKS + 6L, true, 0.0, 0.0, 1.0F).ticks(), "danach wieder normal");
        FlightTally.Step disarm = tally.update(FlightTally.MAX_GAP_TICKS + 7L, false, 2.0, 25.0, 1.0F);
        assertEquals(1, disarm.ticks(), "der Takt bis zum Abschalten gehört noch zum Flug");
        FlightTally.Step falling = tally.update(FlightTally.MAX_GAP_TICKS + 8L, false, 2.0, 25.0, 1.0F);
        assertTrue(falling.empty(), "unscharf zählt weder Zeit, Strecke noch Tempo: " + falling);
    }

    @Test
    void distanceKeepsTheRemainderBelowOneCentimeter() {
        FlightTally tally = new FlightTally(PILOT, 0L, true, 1.0F);
        int[] sum = fly(tally, 0L, 1000, 1, 1.0 / 256.0, 0.08, 1.0F);
        assertEquals(390, sum[1], "1000 × 3,9 mm ergeben 3,90 m, auch wenn kein Paket 1 cm erreicht");
        assertEquals(0, tally.update(1001L, true, FlightTally.MAX_STEP_BLOCKS + 1.0, 0.0, 1.0F).centimeters(), "Sprung über 64 Blöcke");
        assertEquals(0, tally.update(1002L, true, Double.NaN, 0.0, 1.0F).centimeters());
    }

    @Test
    void everyArmingCountsAndATakeoverOfAnArmedDroneDoesNot() {
        FlightTally tally = new FlightTally(PILOT, 0L, false, 1.0F);
        int arms = 0;
        boolean armed = false;
        for (int tick = 1; tick <= 60; tick++) {
            armed = tick % 20 >= 10;
            arms += tally.update(tick, armed, 0.0, 0.0, 1.0F).armed() ? 1 : 0;
        }
        assertEquals(3, arms);
        assertFalse(armed);
        FlightTally takeover = new FlightTally(PILOT, 0L, true, 1.0F);
        assertFalse(takeover.update(1L, true, 0.0, 0.0, 1.0F).armed(), "übernommene scharfe Drohne");
    }

    @Test
    void anEmptyPackCountsOncePerPackAndOnlyInFlight() {
        FlightTally tally = new FlightTally(PILOT, 0L, false, 0.5F);
        assertFalse(tally.update(1L, true, 0.0, 0.0, 0.01F).depleted());
        assertTrue(tally.update(2L, true, 0.0, 0.0, 0.0F).depleted());
        assertFalse(tally.update(3L, true, 0.0, 0.0, 0.0F).depleted(), "leer bleibt leer");
        assertFalse(tally.update(4L, false, 0.0, 0.0, 0.0F).depleted());
        assertFalse(tally.update(5L, false, 0.0, 0.0, 1.0F).depleted(), "neuer Akku");
        assertTrue(tally.update(6L, true, 0.0, 0.0, 0.0F).depleted(), "zweiter Akku leer geflogen");
        FlightTally parked = new FlightTally(PILOT, 0L, false, 0.2F);
        assertFalse(parked.update(1L, false, 0.0, 0.0, 0.0F).depleted(), "unscharf auf null ist nicht leer geflogen");
        FlightTally broken = new FlightTally(PILOT, 0L, true, 0.2F);
        assertTrue(broken.update(1L, true, 0.0, 0.0, Float.NaN).depleted(), "NaN liest der Server als 0");
    }

    @Test
    void topSpeedReportsOnlyNewSessionHighsInTenthsOfKmh() {
        FlightTally tally = new FlightTally(PILOT, 0L, true, 1.0F);
        assertEquals(360, tally.update(1L, true, 0.0, 10.0, 1.0F).topSpeed(), "10 m/s = 36,0 km/h");
        assertEquals(0, tally.update(2L, true, 0.0, 9.0, 1.0F).topSpeed());
        assertEquals(0, tally.update(3L, true, 0.0, 10.0, 1.0F).topSpeed(), "gleich schnell meldet nichts neu");
        assertEquals(1260, tally.update(4L, true, 0.0, 35.0, 1.0F).topSpeed());
        assertEquals(0, tally.update(5L, false, 0.0, 50.0, 1.0F).topSpeed(), "unscharf fallend zählt nicht");
        assertEquals(0, tally.update(6L, true, 0.0, Double.POSITIVE_INFINITY, 1.0F).topSpeed());
    }
}
