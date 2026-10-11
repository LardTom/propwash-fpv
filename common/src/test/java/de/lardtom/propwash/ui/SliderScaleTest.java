package de.lardtom.propwash.ui;

import de.lardtom.propwash.osd.OsdConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SliderScaleTest {
    @Test
    void identityAndPercentConvertBothWays() {
        assertEquals(42.0, SliderScale.IDENTITY.toDisplay(42.0), 1e-12);
        assertEquals(42.0, SliderScale.IDENTITY.fromDisplay(42.0), 1e-12);
        assertEquals(35.0, SliderScale.PERCENT.toDisplay(0.35), 1e-9);
        assertEquals(0.35, SliderScale.PERCENT.fromDisplay(35.0), 1e-12);
        assertEquals(0.0, SliderScale.PERCENT.displayMin(0.0, 1.0), 1e-12);
        assertEquals(100.0, SliderScale.PERCENT.displayMax(0.0, 1.0), 1e-12);
    }

    @Test
    void decimalsFollowTheDisplayedStepEvenForFloatSteps() {
        assertEquals(1, SliderScale.PERCENT.decimals(0.0F, 0.005F));
        assertEquals(0, SliderScale.PERCENT.decimals(0.0, 0.01));
        assertEquals(0, SliderScale.PERCENT.decimals(0.1F, 0.05F));
        assertEquals(2, SliderScale.IDENTITY.decimals(0.25, 0.05));
        assertEquals(1, SliderScale.IDENTITY.decimals(0.5, 0.1F));
        assertEquals(0, SliderScale.IDENTITY.decimals(16.0, 16.0));
        assertEquals(1, SliderScale.IDENTITY.decimals(0.5, 1.0));
        assertEquals(2, SliderScale.IDENTITY.decimals(0.0, 0.0));
        assertEquals(SliderScale.MAX_DECIMALS, SliderScale.decimalsFor(Math.PI));
        assertEquals(0, SliderScale.decimalsFor(0.0));
    }

    @Test
    void typedValuesBecomeRawValuesInsideTheRange() {
        assertEquals(0.5, SliderScale.PERCENT.rawFor(50.0, 0.0, 1.0), 1e-12);
        assertEquals(1.0, SliderScale.PERCENT.rawFor(250.0, 0.0, 1.0), 1e-12);
        assertEquals(0.0, SliderScale.PERCENT.rawFor(-5.0, 0.0, 1.0), 1e-12);
        assertEquals(0.0, SliderScale.PERCENT.rawFor(Double.NaN, 0.0, 1.0), 1e-12);
        assertEquals(0.025, SliderScale.PERCENT.rawFor(2.5, 0.0, 0.25), 1e-12);
    }

    @Test
    void affineScaleMatchesTheOsdScaleSteps() {
        SliderScale scale = SliderScale.of(100.0 / OsdConfig.STEPS_PER_UNIT, OsdConfig.MIN_SCALE * 100.0);
        for (int index = 0; index <= OsdConfig.SCALE_STEPS; index++) {
            double percent = Math.round(OsdConfig.scaleAt(index) * 100.0F);
            assertEquals(percent, scale.toDisplay(index), 1e-6, "Schritt " + index);
            assertEquals(index, Math.round(scale.fromDisplay(percent)), "Prozent " + percent);
        }
        SliderScale element = SliderScale.of(100.0 / OsdConfig.STEPS_PER_UNIT, OsdConfig.MIN_SCALE * 100.0 - 100.0 / OsdConfig.STEPS_PER_UNIT);
        assertEquals(50.0, element.displayMin(1.0, OsdConfig.SCALE_STEPS + 1.0), 1e-9);
        assertEquals(200.0, element.displayMax(1.0, OsdConfig.SCALE_STEPS + 1.0), 1e-9);
        assertEquals(1.0, element.rawFor(10.0, 1.0, OsdConfig.SCALE_STEPS + 1.0), 1e-9);
    }

    @Test
    void floatBoundsRoundToWhatTheSliderShows() {
        assertEquals(5.0, SliderScale.round(SliderScale.PERCENT.toDisplay(0.05F), SliderScale.PERCENT.decimals(0.05F, 0.05F)), 0.0);
        assertEquals(30.0, SliderScale.round(SliderScale.PERCENT.toDisplay(0.3F), SliderScale.PERCENT.decimals(0.0F, 0.005F)), 0.0);
        assertEquals(2.5, SliderScale.round(2.4999999, 1), 0.0);
        assertEquals(Double.POSITIVE_INFINITY, SliderScale.round(Double.POSITIVE_INFINITY, 2), 0.0);
    }

    @Test
    void zeroOrInvalidFactorIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SliderScale.of(0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> SliderScale.of(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> SliderScale.of(1.0, Double.POSITIVE_INFINITY));
    }
}
