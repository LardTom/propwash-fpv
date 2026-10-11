package de.lardtom.propwash.drone.led;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedAnimatorTest {
    private static final int COUNT = 8;

    private static LedAnimator.Input input(boolean armed, float charge, double seconds) {
        return new LedAnimator.Input(armed, true, charge, seconds);
    }

    private static int rgb(int argb) {
        return argb & 0x00FFFFFF;
    }

    private static int alpha(int argb) {
        return argb >>> 24;
    }

    @Test
    void staticShinesInTheChosenColourScaledByBrightness() {
        LedSettings settings = new LedSettings(LedMode.STATIC, LedColor.BLUE, 50, true);
        int color = LedAnimator.color(settings, 3, COUNT, input(false, 1.0F, 12.3));
        assertEquals(LedColor.BLUE.rgb(), rgb(color));
        assertEquals(128, alpha(color), 1);
    }

    @Test
    void noPowerMeansDark() {
        LedAnimator.Input unpowered = new LedAnimator.Input(true, false, 0.0F, 1.0);
        for (LedMode mode : LedMode.values()) {
            assertEquals(0, LedAnimator.color(LedSettings.DEFAULT.withMode(mode), 0, COUNT, unpowered), mode.name());
        }
        assertFalse(LedAnimator.light(LedSettings.DEFAULT, COUNT, unpowered).on());
        assertEquals(0, LedAnimator.luminance(LedSettings.DEFAULT, COUNT, unpowered));
    }

    @Test
    void blinkTogglesTwicePerSecond() {
        LedSettings settings = LedSettings.DEFAULT.withMode(LedMode.BLINK).withColor(LedColor.RED);
        assertEquals(LedColor.RED.rgb(), rgb(LedAnimator.color(settings, 0, COUNT, input(false, 1.0F, 0.1))));
        assertEquals(0, LedAnimator.color(settings, 0, COUNT, input(false, 1.0F, 0.35)));
        assertTrue(LedAnimator.color(settings, 0, COUNT, input(false, 1.0F, 0.6)) != 0);
    }

    @Test
    void armStatusIsGreenAndBreathingWhenDisarmed() {
        LedSettings settings = LedSettings.DEFAULT.withMode(LedMode.ARMED).withColor(LedColor.RED);
        int disarmedLow = LedAnimator.color(settings, 0, COUNT, input(false, 1.0F, 0.0));
        int disarmedHigh = LedAnimator.color(settings, 0, COUNT, input(false, 1.0F, LedAnimator.BREATH_SECONDS / 2.0));
        assertEquals(LedAnimator.DISARMED_RGB, rgb(disarmedLow));
        assertTrue(alpha(disarmedHigh) > alpha(disarmedLow) + 100);
        int armed = LedAnimator.color(settings, 0, COUNT, input(true, 1.0F, 0.0));
        assertEquals(LedColor.RED.rgb(), rgb(armed));
        assertEquals(255, alpha(armed));
    }

    @Test
    void lowBatteryWarningBlinksOrange() {
        LedSettings settings = LedSettings.DEFAULT.withMode(LedMode.LOW_BATTERY).withColor(LedColor.CYAN);
        assertEquals(LedColor.CYAN.rgb(), rgb(LedAnimator.color(settings, 0, COUNT, input(true, 0.8F, 0.0))));
        int low = LedAnimator.color(settings, 0, COUNT, input(true, 0.1F, 0.05));
        assertEquals(LedAnimator.WARNING_RGB, rgb(low));
        assertEquals(0, LedAnimator.color(settings, 0, COUNT, input(true, 0.1F, 0.25)));
    }

    @Test
    void rainbowSpreadsHuesAndMoves() {
        LedSettings settings = LedSettings.DEFAULT.withMode(LedMode.RAINBOW);
        Set<Integer> colors = new HashSet<>();
        for (int i = 0; i < COUNT; i++) {
            colors.add(rgb(LedAnimator.color(settings, i, COUNT, input(false, 1.0F, 0.0))));
        }
        assertEquals(COUNT, colors.size());
        int now = LedAnimator.color(settings, 0, COUNT, input(false, 1.0F, 0.0));
        int later = LedAnimator.color(settings, 0, COUNT, input(false, 1.0F, 1.0));
        assertTrue(now != later);
        assertEquals(0xFF0000, LedAnimator.hueToRgb(0.0));
        assertEquals(0x00FF00, LedAnimator.hueToRgb(1.0 / 3.0));
        assertEquals(0x0000FF, LedAnimator.hueToRgb(2.0 / 3.0));
    }

    @Test
    void switchOnTheRadioCutsThePowerOfTheWholeStrip() {
        assertTrue(LedAnimator.powered(true, 0.8F, false));
        assertFalse(LedAnimator.powered(true, 0.8F, true));
        assertFalse(LedAnimator.powered(false, 0.8F, false));
        assertFalse(LedAnimator.powered(true, 0.0F, false));
        for (LedMode mode : LedMode.values()) {
            LedSettings settings = new LedSettings(mode, LedColor.CYAN, 100, true);
            for (boolean armed : new boolean[]{false, true}) {
                LedAnimator.Input switchedOff = new LedAnimator.Input(armed, LedAnimator.powered(true, 0.1F, true), 0.1F, 0.1);
                for (int i = 0; i < COUNT; i++) {
                    assertEquals(0, LedAnimator.color(settings, i, COUNT, switchedOff), mode + " LED " + i);
                }
                assertFalse(LedAnimator.light(settings, COUNT, switchedOff).on(), mode.name());
                assertEquals(0, LedAnimator.luminance(settings, COUNT, switchedOff), mode.name());
            }
        }
        LedAnimator.Input back = new LedAnimator.Input(false, LedAnimator.powered(true, 1.0F, false), 1.0F, 0.0);
        assertTrue(LedAnimator.light(LedSettings.DEFAULT, COUNT, back).on());
        assertEquals(LedAnimator.MAX_LUMINANCE, LedAnimator.luminance(LedSettings.DEFAULT, COUNT, back));
    }

    @Test
    void lightMixesTheStripForTheHeadlight() {
        LedAnimator.Light white = LedAnimator.light(LedSettings.DEFAULT, COUNT, input(false, 1.0F, 0.0));
        assertEquals(1.0F, white.red(), 1.0E-4F);
        assertEquals(1.0F, white.green(), 1.0E-4F);
        assertEquals(1.0F, white.blue(), 1.0E-4F);
        assertEquals(1.0F, white.intensity(), 1.0E-4F);
        LedAnimator.Light red = LedAnimator.light(LedSettings.DEFAULT.withColor(LedColor.RED).withBrightness(50), COUNT, input(false, 1.0F, 0.0));
        assertEquals(1.0F, red.red(), 1.0E-4F);
        assertTrue(red.green() < 0.3F);
        assertEquals(0.5F, red.intensity(), 0.01F);
        LedAnimator.Light rainbow = LedAnimator.light(LedSettings.DEFAULT.withMode(LedMode.RAINBOW), COUNT, input(false, 1.0F, 0.0));
        assertTrue(rainbow.red() > 0.6F && rainbow.green() > 0.6F && rainbow.blue() > 0.6F, rainbow.toString());
        assertEquals(LedAnimator.MAX_LUMINANCE, LedAnimator.luminance(LedSettings.DEFAULT, COUNT, input(false, 1.0F, 0.0)));
    }
}
