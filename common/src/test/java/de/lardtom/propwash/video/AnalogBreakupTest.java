package de.lardtom.propwash.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalogBreakupTest {
    private static final double STEP = 0.005;

    @Test
    void stagesFollowTheLinkInOrder() {
        assertEquals(AnalogBreakup.Stage.CLEAN, AnalogBreakup.stage(1.0));
        assertEquals(AnalogBreakup.Stage.CLEAN, AnalogBreakup.stage(0.85));
        assertEquals(AnalogBreakup.Stage.GLITCHES, AnalogBreakup.stage(0.7));
        assertEquals(AnalogBreakup.Stage.COLOUR_LOSS, AnalogBreakup.stage(0.5));
        assertEquals(AnalogBreakup.Stage.SNOW, AnalogBreakup.stage(0.25));
        assertEquals(AnalogBreakup.Stage.STATIC, AnalogBreakup.stage(0.05));
        assertEquals(AnalogBreakup.Stage.STATIC, AnalogBreakup.stage(Double.NaN));
        AnalogBreakup.Stage previous = AnalogBreakup.Stage.CLEAN;
        for (double q = 1.0; q >= 0.0; q -= STEP) {
            AnalogBreakup.Stage stage = AnalogBreakup.stage(q);
            assertTrue(stage.ordinal() >= previous.ordinal(), "stage went back at " + q);
            assertTrue(stage.ordinal() - previous.ordinal() <= 1, "stage skipped at " + q);
            previous = stage;
        }
        assertEquals(5, AnalogBreakup.Stage.STATIC.number());
    }

    @Test
    void cleanStageIsSoftButUndisturbed() {
        AnalogBreakup.Levels l = AnalogBreakup.levels(0.9);
        assertEquals(0.0, l.glitchRate(), 1.0E-9);
        assertEquals(0.0, l.sparkle(), 1.0E-9);
        assertEquals(1.0, l.colour(), 1.0E-9);
        assertEquals(0.0, l.snow(), 1.0E-9);
        assertEquals(0.0, l.sync(), 1.0E-9);
        assertEquals(0.0, l.staticMix(), 1.0E-9);
        assertTrue(AnalogBreakup.softness(0.9) > 0.0, "analog is never razor sharp below full link");
    }

    @Test
    void glitchStageKeepsColourAndHasNoSnow() {
        for (double q = AnalogBreakup.COLOUR_BELOW; q < AnalogBreakup.CLEAN_ABOVE; q += STEP) {
            AnalogBreakup.Levels l = AnalogBreakup.levels(q);
            assertEquals(AnalogBreakup.Stage.GLITCHES, l.stage());
            assertTrue(l.glitchRate() > 0.0, "no glitches at " + q);
            assertEquals(1.0, l.colour(), 1.0E-9, "colour already fading at " + q);
            assertEquals(0.0, l.snow(), 1.0E-9, "snow already at " + q);
            assertEquals(0.0, l.sync(), 1.0E-9, "sync trouble already at " + q);
        }
        assertTrue(AnalogBreakup.sparkle(0.65) > 0.0);
        assertTrue(AnalogBreakup.lineJitter(0.65) > 0.0);
        assertTrue(AnalogBreakup.glitchRate(0.65) > AnalogBreakup.glitchRate(0.75));
    }

    @Test
    void colourFadesInBandsAndTurnsBlackAndWhiteBeforeTheSnow() {
        assertTrue(AnalogBreakup.chromaBands(0.59) > 0.0, "bands come first");
        assertEquals(1.0, AnalogBreakup.colour(0.59), 0.02, "whole-frame colour still nearly intact while bands start");
        assertTrue(AnalogBreakup.colour(0.54) < 0.9 && AnalogBreakup.colour(0.54) > 0.1);
        assertEquals(0.0, AnalogBreakup.colour(AnalogBreakup.GREY_BELOW), 1.0E-9, "black and white in the second half of stage 3");
        assertEquals(0.0, AnalogBreakup.colour(0.47), 1.0E-9, "stage 3 reads as black and white");
        assertEquals(0.0, AnalogBreakup.snow(AnalogBreakup.GRAIN_BELOW), 1.0E-9, "no snow while the colour goes");
        assertTrue(AnalogBreakup.snow(0.47) > 0.0 && AnalogBreakup.snow(0.47) <= AnalogBreakup.GRAIN, "light grain in the grey image");
        assertTrue(AnalogBreakup.lineJitter(0.47) > AnalogBreakup.lineJitter(0.6), "lines wobble more towards the snow");
        double previous = 1.0;
        for (double q = 1.0; q >= 0.0; q -= STEP) {
            double colour = AnalogBreakup.colour(q);
            assertTrue(colour <= previous + 1.0E-12, "colour came back at " + q);
            previous = colour;
        }
        assertTrue(AnalogBreakup.colourFlickerRate(0.54) > 0.0, "colour burst flickers while locking");
        assertEquals(0.0, AnalogBreakup.colourFlickerRate(0.3), 1.0E-9, "no flicker once fully grey");
    }

    @Test
    void snowGrowsOverTheGreyImageWithSyncTrouble() {
        for (double q = AnalogBreakup.STATIC_BELOW; q < AnalogBreakup.SNOW_BELOW; q += STEP) {
            assertEquals(0.0, AnalogBreakup.colour(q), 1.0E-9, "colour back at " + q);
        }
        double previous = -1.0;
        for (double q = AnalogBreakup.SNOW_BELOW; q >= 0.0; q -= STEP) {
            double snow = AnalogBreakup.snow(q);
            assertTrue(snow >= previous - 1.0E-12, "snow shrinking at " + q);
            previous = snow;
        }
        assertTrue(AnalogBreakup.snow(0.25) > 0.2 && AnalogBreakup.snow(0.25) < AnalogBreakup.MAX_SNOW);
        assertTrue(AnalogBreakup.sync(0.25) > 0.0);
        assertTrue(AnalogBreakup.shiftRate(0.25) > 0.0);
        assertTrue(AnalogBreakup.rollRate(0.25) > 0.0);
        assertTrue(AnalogBreakup.rollRate(0.25) < AnalogBreakup.shiftRate(0.25), "short rolls are rarer than shifts");
        assertEquals(0.0, AnalogBreakup.staticMix(0.25), 1.0E-9);
    }

    @Test
    void fullStaticAtTheEnd() {
        assertEquals(1.0, AnalogBreakup.staticMix(0.0), 1.0E-9);
        assertEquals(1.0, AnalogBreakup.staticMix(AnalogBreakup.STATIC_FULL), 1.0E-9);
        assertTrue(AnalogBreakup.staticMix(0.06) > 0.0 && AnalogBreakup.staticMix(0.06) < 1.0, "static fades in, no hard cut");
    }

    @Test
    void everyLevelIsContinuousInTheLink() {
        double[] last = null;
        for (double q = 0.0; q <= 1.0; q += 0.001) {
            AnalogBreakup.Levels l = AnalogBreakup.levels(q);
            double[] now = {l.glitchRate() / AnalogBreakup.MAX_GLITCH_RATE, l.sparkle(), l.lineJitter() / AnalogBreakup.MAX_LINE_JITTER,
                    l.chromaBands(), l.colour(), l.chromaNoise(), l.snow(), l.sync(), l.staticMix()};
            if (last != null) {
                for (int i = 0; i < now.length; i++) {
                    assertEquals(last[i], now[i], 0.03, "level " + i + " jumps at " + q);
                }
            }
            last = now;
        }
    }
}
