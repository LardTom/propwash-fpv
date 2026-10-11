package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RaceFormatTest {
    @Test
    void timesKeepTheirMillisecondFormat() {
        assertEquals("31.870", RaceFormat.time(31_870_000_000L));
        assertEquals("1:02.350", RaceFormat.time(62_349_900_000L));
        assertEquals("1:05.100", RaceFormat.time(65_099_999_999L));
        assertEquals("0.000", RaceFormat.time(0L));
        assertEquals("--.---", RaceFormat.time(-1L));
        assertEquals("-0.420", RaceFormat.delta(-420_000_000L));
        assertEquals("+62.500", RaceFormat.delta(62_500_000_000L));
    }

    @Test
    void fewerDecimalsRoundLikeTheMillisecondFormat() {
        assertEquals("1:02.35", RaceFormat.time(62_349_900_000L, 2));
        assertEquals("1:02.34", RaceFormat.time(62_344_999_999L, 2));
        assertEquals("1:00.00", RaceFormat.time(59_999_000_000L, 2));
        assertEquals("31.87", RaceFormat.time(31_870_000_000L, 2));
        assertEquals("--.--", RaceFormat.time(-1L, 2));
        assertEquals("--", RaceFormat.time(-1L, 0));
        assertEquals("32", RaceFormat.time(31_500_000_000L, 0));
        assertEquals("-0.42", RaceFormat.delta(-420_000_000L, 2));
        assertEquals("+1.05", RaceFormat.delta(1_049_999_999L, 2));
        assertEquals("+0.00", RaceFormat.delta(0L, 2));
    }

    @Test
    void roundedTicksMatchTheDisplayedDigits() {
        assertEquals(6235L, RaceFormat.roundedTicks(62_349_900_000L, 2));
        assertEquals(62_350L, RaceFormat.roundedTicks(62_349_900_000L, 3));
        assertEquals(RaceFormat.roundedTicks(62_345_000_000L, 2), RaceFormat.roundedTicks(62_354_999_999L, 2));
    }
}
