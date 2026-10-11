package de.lardtom.propwash.osd;

import de.lardtom.propwash.race.RaceFormat;
import de.lardtom.propwash.ui.SpeedUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class OsdFormatTest {
    private static final double EPS = 1.0E-9;

    @Test
    void decimalsAreLocaleIndependentAndNeverNegativeZero() {
        assertEquals("16.8", OsdFormat.decimal(16.84, 1));
        assertEquals("3.85", OsdFormat.decimal(3.845, 2));
        assertEquals("0.0", OsdFormat.decimal(-0.01, 1));
        assertEquals("-2.5", OsdFormat.decimal(-2.46, 1));
        assertEquals("--", OsdFormat.decimal(Double.NaN, 1));
        assertEquals("--", OsdFormat.integer(Double.POSITIVE_INFINITY));
        assertEquals("0", OsdFormat.integer(-0.2));
        assertEquals("462", OsdFormat.integer(461.6));
    }

    @Test
    void durations() {
        assertEquals("00:00", OsdFormat.duration(0.0));
        assertEquals("01:23", OsdFormat.duration(83.9));
        assertEquals("99:59", OsdFormat.duration(5999.0));
        assertEquals("1:40:00", OsdFormat.duration(6000.0));
        assertEquals("--:--", OsdFormat.duration(-1.0));
        assertEquals("--:--", OsdFormat.duration(Double.NaN));
    }

    @Test
    void lapTimesAndDeltasUseTheRaceFormatter() {
        assertEquals("31.87", OsdFormat.lapText(OsdFormat.lapKey(31_870_000_000L)));
        assertEquals("1:05.10", OsdFormat.lapText(OsdFormat.lapKey(65_099_999_999L)));
        assertEquals("1:02.35", OsdFormat.lapText(OsdFormat.lapKey(62_349_900_000L)));
        assertEquals(RaceFormat.time(62_349_900_000L, 2), OsdFormat.lapText(OsdFormat.lapKey(62_349_900_000L)));
        assertEquals("--.--", OsdFormat.lapText(OsdFormat.lapKey(-1L)));
        assertEquals("-0.42", OsdFormat.deltaText(OsdFormat.deltaKey(-420_000_000L)));
        assertEquals("+1.05", OsdFormat.deltaText(OsdFormat.deltaKey(1_050_000_000L)));
        assertEquals("+0.00", OsdFormat.deltaText(OsdFormat.deltaKey(0L)));
        assertEquals("-0.00", OsdFormat.deltaText(OsdFormat.deltaKey(-1_000_000L)));
        assertEquals("+62.50", OsdFormat.deltaText(OsdFormat.deltaKey(62_500_000_000L)));
        assertEquals(OsdFormat.lapKey(62_345_000_000L), OsdFormat.lapKey(62_354_999_999L));
    }

    @Test
    void cacheKeysChangeOnlyWithTheDisplayedText() {
        assertEquals(OsdFormat.altitudeKey(12.61, SpeedUnit.KMH), OsdFormat.altitudeKey(12.64, SpeedUnit.KMH));
        assertNotEquals(OsdFormat.altitudeKey(12.61, SpeedUnit.KMH), OsdFormat.altitudeKey(12.66, SpeedUnit.KMH));
        assertNotEquals(OsdFormat.altitudeKey(12.0, SpeedUnit.KMH), OsdFormat.altitudeKey(12.0, SpeedUnit.MPH));
        assertEquals("-3.5M", OsdFormat.altitudeText(OsdFormat.altitudeKey(-3.5, SpeedUnit.KMH)));
        assertEquals("--", OsdFormat.altitudeText(OsdFormat.altitudeKey(Double.NaN, SpeedUnit.KMH)));
        assertEquals("12.35", OsdFormat.decimal(12.345, 2));
        assertEquals("-0.5", OsdFormat.decimal(-0.46, 1));
        assertEquals("--", OsdFormat.decimal(Double.POSITIVE_INFINITY, 1));
        assertEquals("7", OsdFormat.integer(6.5));
    }

    @Test
    void clock() {
        assertEquals("07:05", OsdFormat.clock(7, 5));
        assertEquals("00:00", OsdFormat.clock(24, 60));
    }

    @Test
    void angleWrapping() {
        assertEquals(10.0, OsdFormat.wrap360(370.0), EPS);
        assertEquals(350.0, OsdFormat.wrap360(-10.0), EPS);
        assertEquals(0.0, OsdFormat.wrap360(360.0), EPS);
        assertEquals(-170.0, OsdFormat.wrap180(190.0), EPS);
        assertEquals(180.0, OsdFormat.wrap180(180.0), EPS);
    }

    @Test
    void compassConventionsMatchTheSimAndMinecraft() {
        assertEquals(180.0, OsdFormat.compassFromSimHeading(0.0), EPS);
        assertEquals(90.0, OsdFormat.compassFromSimHeading(90.0), EPS);
        assertEquals(0.0, OsdFormat.compassFromSimHeading(180.0), EPS);
        assertEquals(270.0, OsdFormat.compassFromSimHeading(-90.0), EPS);
        assertEquals(180.0, OsdFormat.compassFromYRot(0.0), EPS);
        assertEquals(270.0, OsdFormat.compassFromYRot(90.0), EPS);
        assertEquals(0.0, OsdFormat.bearing(0.0, -5.0), EPS);
        assertEquals(90.0, OsdFormat.bearing(3.0, 0.0), EPS);
        assertEquals(180.0, OsdFormat.bearing(0.0, 2.0), EPS);
        assertEquals(270.0, OsdFormat.bearing(-1.0, 0.0), EPS);
    }

    @Test
    void headingText() {
        assertEquals("005", OsdFormat.heading(4.6));
        assertEquals("000", OsdFormat.heading(359.7));
        assertEquals("---", OsdFormat.heading(Double.NaN));
    }

    @Test
    void unitsAndPercentages() {
        assertEquals(36.0, OsdFormat.speed(10.0, SpeedUnit.KMH), 1.0E-9);
        assertEquals(22.369362920544, OsdFormat.speed(10.0, SpeedUnit.MPH), 1.0E-6);
        assertEquals("KM/H", OsdFormat.speedUnit(SpeedUnit.KMH));
        assertEquals("MPH", OsdFormat.speedUnit(SpeedUnit.MPH));
        assertEquals("118M", OsdFormat.distance(118.2, SpeedUnit.KMH));
        assertEquals("1.2KM", OsdFormat.distance(1249.0, SpeedUnit.KMH));
        assertEquals("388FT", OsdFormat.distance(118.2, SpeedUnit.MPH));
        assertEquals("2.0MI", OsdFormat.distance(3218.7, SpeedUnit.MPH));
        assertEquals("12.6M", OsdFormat.altitude(12.6, SpeedUnit.KMH));
        assertEquals("124M", OsdFormat.altitude(123.9, SpeedUnit.KMH));
        assertEquals("41FT", OsdFormat.altitude(12.6, SpeedUnit.MPH));
        assertEquals("↑1.8M/S", OsdFormat.vario(1.8, SpeedUnit.KMH));
        assertEquals("↓0.5M/S", OsdFormat.vario(-0.5, SpeedUnit.KMH));
        assertEquals(" 0.0M/S", OsdFormat.vario(0.01, SpeedUnit.KMH));
        assertEquals("↑5.9F/S", OsdFormat.vario(1.8, SpeedUnit.MPH));
        assertEquals(98, OsdFormat.percent(0.984));
        assertEquals(100, OsdFormat.percent(3.0));
        assertEquals(0, OsdFormat.percent(Double.NaN));
        assertEquals("+12", OsdFormat.signedAngle(12.2));
        assertEquals("-08", OsdFormat.signedAngle(-8.0));
        assertEquals(" 00", OsdFormat.signedAngle(0.3));
    }

    @Test
    void rssiMapping() {
        assertEquals(-50, OsdFormat.rssiDbm(1.0));
        assertEquals(-115, OsdFormat.rssiDbm(0.0));
        assertEquals(-115, OsdFormat.rssiDbm(Double.NaN));
    }

    @Test
    void batterySegments() {
        assertEquals(10, OsdFormat.batteryBarSegments(1.0, 10));
        assertEquals(6, OsdFormat.batteryBarSegments(0.58, 10));
        assertEquals(1, OsdFormat.batteryBarSegments(0.01, 10));
        assertEquals(0, OsdFormat.batteryBarSegments(0.0, 10));
        assertEquals(0, OsdFormat.batteryBarSegments(Double.NaN, 10));
    }
}
