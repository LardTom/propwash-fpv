package de.lardtom.propwash.relay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeaterMeterTest {
    @Test
    void barsFollowTheSignal() {
        assertEquals(0, RepeaterMeter.bars(Double.NaN));
        assertEquals(0, RepeaterMeter.bars(0.0));
        assertEquals(0, RepeaterMeter.bars(RepeaterMeter.NOTHING));
        assertEquals(1, RepeaterMeter.bars(0.05));
        assertEquals(1, RepeaterMeter.bars(0.25));
        assertEquals(2, RepeaterMeter.bars(0.4));
        assertEquals(3, RepeaterMeter.bars(0.72));
        assertEquals(4, RepeaterMeter.bars(0.8));
        assertEquals(4, RepeaterMeter.bars(1.0));
        assertEquals(4, RepeaterMeter.bars(2.0));
    }

    @Test
    void colorsGoFromRedToGreenAndOffIsDark() {
        assertEquals(RepeaterMeter.DARK, RepeaterMeter.color(0));
        assertEquals(RepeaterMeter.RED, RepeaterMeter.color(1));
        assertEquals(RepeaterMeter.YELLOW, RepeaterMeter.color(2));
        assertEquals(RepeaterMeter.GREEN, RepeaterMeter.color(3));
        assertEquals(RepeaterMeter.GREEN, RepeaterMeter.color(4));
    }

    @Test
    void itemMeterIsOnlyShownWhileMeasuring() {
        RepeaterMeter.hide();
        assertFalse(RepeaterMeter.active());
        assertEquals(0.0F, RepeaterMeter.quality());
        RepeaterMeter.show(0.72);
        assertTrue(RepeaterMeter.active());
        assertEquals(0.72F, RepeaterMeter.quality(), 1.0E-6F);
        RepeaterMeter.show(1.5);
        assertEquals(1.0F, RepeaterMeter.quality());
        RepeaterMeter.hide();
        assertFalse(RepeaterMeter.active());
    }
}
