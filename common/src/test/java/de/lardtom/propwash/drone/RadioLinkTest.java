package de.lardtom.propwash.drone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadioLinkTest {
    @Test
    void fullQualityUpToFullRange() {
        assertEquals(1.0F, RadioLink.quality(0.0));
        assertEquals(1.0F, RadioLink.quality(400.0));
        assertEquals(1.0F, RadioLink.quality(800.0));
    }

    @Test
    void noQualityFromTwelveHundred() {
        assertEquals(0.0F, RadioLink.quality(1200.0));
        assertEquals(0.0F, RadioLink.quality(5000.0));
        assertEquals(0.0F, RadioLink.quality(Double.NaN));
        assertEquals(0.5F, RadioLink.quality(1000.0), 1.0E-4F);
    }

    @Test
    void qualityIsMonotonic() {
        float last = 2.0F;
        for (double d = 0.0; d <= 1400.0; d += 25.0) {
            float q = RadioLink.quality(d);
            assertTrue(q <= last, "bei " + d);
            last = q;
        }
    }

    @Test
    void rangesAreConsistent() {
        assertTrue(RadioLink.START_RANGE <= RadioLink.FULL_RANGE);
        assertTrue(RadioLink.RELEASE_RANGE >= RadioLink.FULL_RANGE + RadioLink.FADE_RANGE);
        assertEquals(160.0, RadioLink.startRange(160.0));
        assertEquals(RadioLink.START_RANGE, RadioLink.startRange(Double.POSITIVE_INFINITY));
    }

    @Test
    void serverLimitPullsTheFadeIn() {
        assertEquals(0.0F, RadioLink.quality(155.0, 160.0));
        assertEquals(1.0F, RadioLink.quality(100.0, 160.0));
        assertEquals(1.0F, RadioLink.quality(700.0, Double.POSITIVE_INFINITY));
    }

    @Test
    void freeRangeLiftsTheRadioCap() {
        assertEquals(1.0F, RadioLink.quality(5000.0, Double.POSITIVE_INFINITY, true));
        assertEquals(0.0F, RadioLink.quality(5000.0, Double.POSITIVE_INFINITY, false));
        assertEquals(0.0F, RadioLink.quality(155.0, 160.0, true));
        assertEquals(Double.POSITIVE_INFINITY, RadioLink.startRange(Double.POSITIVE_INFINITY, true));
        assertEquals(Double.POSITIVE_INFINITY, RadioLink.releaseRange(Double.POSITIVE_INFINITY, true));
        assertEquals(RadioLink.RELEASE_RANGE, RadioLink.releaseRange(Double.POSITIVE_INFINITY, false));
        assertEquals(160.0, RadioLink.releaseRange(160.0, true));
    }

    @Test
    void creativeRangeFadesLikeTheRadio() {
        assertEquals(1.0F, RadioLink.quality(2000.0, Double.POSITIVE_INFINITY, true, 2000.0));
        assertEquals(0.5F, RadioLink.quality(2500.0, Double.POSITIVE_INFINITY, true, 2000.0), 1.0E-4F);
        assertEquals(0.0F, RadioLink.quality(3000.0, Double.POSITIVE_INFINITY, true, 2000.0));
        assertEquals(1.0F, RadioLink.quality(9000.0, Double.POSITIVE_INFINITY, true, 0.0));
        assertEquals(0.0F, RadioLink.quality(9000.0, Double.POSITIVE_INFINITY, false, 2000.0));
    }
}
