package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateSignalTest {
    @Test
    void defaultIsOnWithTenTicksForEveryPass() {
        assertTrue(GateSignal.DEFAULT.enabled());
        assertEquals(10, GateSignal.DEFAULT.pulseTicks());
        assertFalse(GateSignal.DEFAULT.raceOnly());
        assertEquals(10, GateSignal.DEFAULT.pack());
        assertEquals(GateSignal.DEFAULT, GateSignal.unpack(GateSignal.DEFAULT.pack()));
    }

    @Test
    void packingKeepsEverySetting() {
        for (int ticks = GateSignal.MIN_PULSE; ticks <= GateSignal.MAX_PULSE; ticks++) {
            for (boolean enabled : new boolean[]{true, false}) {
                for (boolean raceOnly : new boolean[]{true, false}) {
                    GateSignal signal = new GateSignal(enabled, ticks, raceOnly);
                    assertEquals(signal, GateSignal.unpack(signal.pack()));
                }
            }
        }
    }

    @Test
    void pulseLengthIsClamped() {
        assertEquals(2, new GateSignal(true, 0, false).pulseTicks());
        assertEquals(2, new GateSignal(true, -50, false).pulseTicks());
        assertEquals(100, new GateSignal(true, 101, false).pulseTicks());
        assertEquals(2, GateSignal.unpack(0).pulseTicks());
        assertEquals(100, GateSignal.unpack(0x7F).pulseTicks());
        assertEquals(40, GateSignal.DEFAULT.withPulse(40).pulseTicks());
    }

    @Test
    void racesOnlyCountsCourseGatesInARaceAndTheStartGate() {
        GateSignal free = GateSignal.DEFAULT;
        GateSignal race = GateSignal.DEFAULT.withRaceOnly(true);
        GateSignal off = GateSignal.DEFAULT.withEnabled(false);
        assertTrue(free.fires(false, false, false));
        assertTrue(free.fires(true, false, true));
        assertFalse(off.fires(true, true, true));
        assertFalse(race.fires(false, false, false));
        assertFalse(race.fires(true, false, false));
        assertTrue(race.fires(true, false, true));
        assertTrue(race.fires(true, true, false));
        assertFalse(race.fires(false, true, false));
    }

    @Test
    void secondsAreShownWithoutTrailingZeros() {
        assertEquals("0.1", GateSignal.secondsText(2));
        assertEquals("0.15", GateSignal.secondsText(3));
        assertEquals("0.5", GateSignal.secondsText(10));
        assertEquals("1", GateSignal.secondsText(20));
        assertEquals("2.05", GateSignal.secondsText(41));
        assertEquals("5", GateSignal.secondsText(100));
        assertEquals(0.5, GateSignal.DEFAULT.seconds());
    }
}
