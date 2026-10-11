package de.lardtom.propwash.ui.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StepSliderTest {
    @Test
    void snapsRelativeToMinimum() {
        assertEquals(0.3, StepSlider.snap(0.32, 0.1, 1.0, 0.2), 1e-9);
        assertEquals(1.0, StepSlider.snap(0.98, 0.1, 1.0, 0.2), 1e-9);
    }

    @Test
    void clampsAndHandlesInvalidInput() {
        assertEquals(16.0, StepSlider.snap(-5.0, 16.0, 512.0, 16.0), 1e-9);
        assertEquals(512.0, StepSlider.snap(900.0, 16.0, 512.0, 16.0), 1e-9);
        assertEquals(16.0, StepSlider.snap(Double.NaN, 16.0, 512.0, 16.0), 1e-9);
        assertEquals(0.37, StepSlider.snap(0.37, 0.0, 1.0, 0.0), 1e-9);
    }
}
