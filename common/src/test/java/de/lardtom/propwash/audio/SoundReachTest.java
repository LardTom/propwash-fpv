package de.lardtom.propwash.audio;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundReachTest {
    private static double cruiseRange(DroneBuild build) {
        MotorTimbre timbre = MotorTimbre.of(build);
        double output = Math.min(1.0, timbre.hoverOutput() + 0.15);
        return SoundReach.of(build).rangeBlocks(SoundReach.amplitude(timbre.rpmForOutput(output), timbre.maxRpm(), output));
    }

    private static double fullRange(DroneBuild build) {
        MotorTimbre timbre = MotorTimbre.of(build);
        return SoundReach.of(build).rangeBlocks(SoundReach.amplitude(timbre.maxRpm(), timbre.maxRpm(), 1.0));
    }

    @Test
    void levelFollowsShaftPowerAndTipMach() {
        assertEquals(SoundReach.REFERENCE_LEVEL_DB, SoundReach.levelDb(SoundReach.REFERENCE_POWER_W, SoundReach.REFERENCE_TIP_MACH), 1.0e-9);
        double base = SoundReach.levelDb(50.0, 0.2);
        assertEquals(3.01, SoundReach.levelDb(100.0, 0.2) - base, 0.01);
        assertEquals(9.03, SoundReach.levelDb(50.0, 0.4) - base, 0.01);
        assertTrue(Double.isFinite(SoundReach.levelDb(Double.NaN, -1.0)));
    }

    @Test
    void inverseSquareLawSetsTheRange() {
        double level = 86.0;
        assertEquals(-6.02, SoundReach.levelAt(level, 20.0) - SoundReach.levelAt(level, 10.0), 0.01);
        assertEquals(-20.0, SoundReach.levelAt(level, 10.0) - level, 1.0e-9);
        assertEquals(10.0, SoundReach.rangeForLevel(SoundReach.THRESHOLD_DB + 20.0), 1.0e-9);
        assertEquals(100.0, SoundReach.rangeForLevel(SoundReach.THRESHOLD_DB + 40.0), 1.0e-9);
        double range = SoundReach.rangeForLevel(level);
        assertEquals(SoundReach.THRESHOLD_DB, SoundReach.levelAt(level, range), 1.0e-9);
    }

    @Test
    void attenuationIsInverseDistanceWithSmoothFade() {
        double range = 70.0;
        assertEquals(1.0, SoundReach.attenuation(0.0, range), 1.0e-12);
        assertEquals(1.0, SoundReach.attenuation(SoundReach.NEAR_BLOCKS, range), 1.0e-12);
        assertEquals(0.5, SoundReach.attenuation(2.0 * SoundReach.NEAR_BLOCKS, range), 1.0e-12);
        double a10 = SoundReach.attenuation(10.0, range);
        double a20 = SoundReach.attenuation(20.0, range);
        assertEquals(-6.02, 20.0 * Math.log10(a20 / a10), 0.01);
        assertEquals(0.0, SoundReach.attenuation(range, range), 1.0e-12);
        assertEquals(0.0, SoundReach.attenuation(range + 1.0, range), 1.0e-12);
        assertTrue(SoundReach.attenuation(0.9 * range, range) > 0.0);
        double previous = 1.0;
        double previousSlope = 0.0;
        for (double d = 0.0; d <= range + 1.0; d += 0.01) {
            double a = SoundReach.attenuation(d, range);
            assertTrue(a <= previous + 1.0e-12, "steigt bei " + d);
            double slope = previous - a;
            assertTrue(slope < 0.004, "Sprung bei " + d);
            assertTrue(d < SoundReach.NEAR_BLOCKS + 0.02 || slope < previousSlope + 1.0e-4, "Knick bei " + d);
            previousSlope = slope;
            previous = a;
        }
        assertEquals(0.0, SoundReach.attenuation(Double.NaN, range), 0.0);
        assertEquals(0.0, SoundReach.attenuation(5.0, 0.0), 0.0);
    }

    @Test
    void presetRangesMatchTheirSize() {
        double whoop = SoundReach.of(DroneBuild.WHOOP).hoverRange();
        double freestyle = SoundReach.of(DroneBuild.FREESTYLE).hoverRange();
        double racer = SoundReach.of(DroneBuild.RACER).hoverRange();
        double longRange = SoundReach.of(DroneBuild.LONG_RANGE).hoverRange();
        assertTrue(whoop >= 15.0 && whoop <= 25.0, "65-mm-Whoop schwebend " + whoop);
        assertTrue(cruiseRange(DroneBuild.WHOOP) <= 25.0, "65-mm-Whoop im Flug " + cruiseRange(DroneBuild.WHOOP));
        assertTrue(fullRange(DroneBuild.WHOOP) <= 30.0, "65-mm-Whoop Vollgas " + fullRange(DroneBuild.WHOOP));
        assertTrue(freestyle >= 60.0 && freestyle <= 80.0, "5\" schwebend " + freestyle);
        assertTrue(cruiseRange(DroneBuild.RACER) >= 55.0 && cruiseRange(DroneBuild.RACER) <= 90.0, "5\" Race im Flug " + cruiseRange(DroneBuild.RACER));
        assertTrue(racer < freestyle, "leichter Racer schwebt leiser " + racer + " vs " + freestyle);
        assertTrue(longRange > freestyle, "7\" " + longRange + " vs 5\" " + freestyle);
        for (DroneBuild build : DroneBuild.PRESETS) {
            SoundReach reach = SoundReach.of(build);
            double hover = reach.hoverRange();
            assertTrue(cruiseRange(build) >= hover && fullRange(build) >= cruiseRange(build), "Reichweite steigt mit Gas: " + build);
            assertTrue(fullRange(build) <= 2.0 * hover + 1.0e-9, "Vollgas höchstens doppelte Reichweite: " + build);
            assertTrue(reach.rangeBlocks(0.0) == SoundReach.MIN_RANGE_BLOCKS, "stehende Motoren: " + build);
        }
    }

    @Test
    void levelFollowsTheSizeCurveWithinABand() {
        assertEquals(SoundReach.REFERENCE_LEVEL_DB, SoundReach.sizeLevelDb(SoundReach.REFERENCE_PROP_MM), 1.0e-9);
        assertEquals(SoundReach.SIZE_SLOPE_DB * Math.log10(2.0), SoundReach.sizeLevelDb(260.0) - SoundReach.sizeLevelDb(130.0), 1.0e-9);
        double size = SoundReach.sizeLevelDb(76.0);
        assertEquals(size + SoundReach.ROTOR_BAND_DB, SoundReach.sizedLevelDb(size + 9.0, 76.0), 1.0e-9);
        assertEquals(size - SoundReach.ROTOR_BAND_DB, SoundReach.sizedLevelDb(size - 9.0, 76.0), 1.0e-9);
        assertEquals(size + SoundReach.ROTOR_SHARE, SoundReach.sizedLevelDb(size + 1.0, 76.0), 1.0e-9);
        assertTrue(Double.isFinite(SoundReach.sizedLevelDb(Double.NaN, Double.NaN)));
        for (DroneBuild build : DroneBuild.PRESETS) {
            SoundReach reach = SoundReach.of(build);
            assertEquals(SoundReach.levelDb(reach.shaftPowerW(), reach.tipMach()), reach.rotorLevelDb(), 1.0e-9);
            assertTrue(Math.abs(reach.levelDb() - SoundReach.sizeLevelDb(reach.propDiameterMm())) <= SoundReach.ROTOR_BAND_DB + 1.0e-9,
                    "Pegel weit neben der Größenkurve: " + build);
        }
    }

    @Test
    void cinewhoopSitsBetweenWhoopAndFiveInch() {
        double whoop = SoundReach.of(DroneBuild.WHOOP).hoverRange();
        double cinewhoop = SoundReach.of(DroneBuild.CINEWHOOP).hoverRange();
        double freestyle = SoundReach.of(DroneBuild.FREESTYLE).hoverRange();
        double longRange = SoundReach.of(DroneBuild.LONG_RANGE).hoverRange();
        assertTrue(cinewhoop >= 35.0 && cinewhoop <= 65.0, "3\" Cinewhoop schwebend " + cinewhoop);
        assertTrue(whoop < cinewhoop && cinewhoop < freestyle && freestyle < longRange,
                "Hörweite wächst mit der Größe: " + whoop + " / " + cinewhoop + " / " + freestyle + " / " + longRange);
        assertTrue(cruiseRange(DroneBuild.CINEWHOOP) < cruiseRange(DroneBuild.FREESTYLE), "3\" Cinewhoop im Flug " + cruiseRange(DroneBuild.CINEWHOOP));
    }

    @Test
    void xClassRotorCarriesFarthestButStaysCapped() {
        SoundReach xClass = SoundReach.fromRotor(296.0, 0.227, 330.0, 0.3);
        SoundReach freestyle = SoundReach.of(DroneBuild.FREESTYLE);
        assertTrue(xClass.levelDb() > freestyle.levelDb() + 6.0, "X-Class " + xClass.levelDb() + " dB(A)");
        assertTrue(xClass.hoverRange() > 2.0 * freestyle.hoverRange(), "X-Class " + xClass.hoverRange());
        assertEquals(SoundReach.MAX_RANGE_BLOCKS, xClass.rangeBlocks(1.0), 1.0e-9);
    }

    @Test
    void referenceLevelsMatchMeasurements() {
        double freestyle = SoundReach.of(DroneBuild.FREESTYLE).levelDb();
        double whoop = SoundReach.of(DroneBuild.WHOOP).levelDb();
        assertTrue(freestyle >= 78.0 && freestyle <= 82.0, "5\" " + freestyle + " dB(A) in 1 m");
        assertTrue(whoop >= 64.0 && whoop <= 72.0, "Whoop " + whoop + " dB(A) in 1 m");
    }

    @Test
    void airAbsorptionDarkensDistantSound() {
        assertEquals(SoundReach.CLOSE_CUTOFF_HZ, SoundReach.airCutoffHz(0.5), 1.0e-9);
        double previous = Double.MAX_VALUE;
        for (double d = 1.0; d <= 400.0; d += 1.0) {
            double hz = SoundReach.airCutoffHz(d);
            assertTrue(hz <= previous, "Grenzfrequenz steigt bei " + d);
            previous = hz;
        }
        assertEquals(8_000.0, SoundReach.airCutoffHz(25.64), 5.0);
        double at100 = SoundReach.airCutoffHz(100.0);
        assertTrue(at100 > 3_000.0 && at100 < 4_500.0, "100 m " + at100);
        assertEquals(SoundReach.FAR_CUTOFF_HZ, SoundReach.airCutoffHz(5_000.0), 1.0e-9);
    }

    @Test
    void amplitudeMatchesTheSynthLoudnessCurve() {
        assertEquals(0.0, SoundReach.amplitude(0.0, 30_000.0, 1.0), 0.0);
        assertEquals(1.0, SoundReach.amplitude(30_000.0, 30_000.0, 1.0), 1.0e-12);
        assertEquals(1.0, SoundReach.amplitude(60_000.0, 30_000.0, 1.0), 1.0e-12);
        assertEquals(Math.pow(0.5, 0.6) * 0.8, SoundReach.amplitude(15_000.0, 30_000.0, 0.5), 1.0e-12);
        assertEquals(0.0, SoundReach.amplitude(Double.NaN, 30_000.0, Double.NaN), 0.0);
    }
}
