package de.lardtom.propwash.video.record.path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplificationTest {
    @Test
    void sliderEnds() {
        assertSame(Simplification.OFF, Simplification.ofSlider(0.0));
        assertSame(Simplification.OFF, Simplification.ofSlider(-1.0));
        assertSame(Simplification.OFF, Simplification.ofSlider(Double.NaN));
        assertTrue(Simplification.OFF.off());
        Simplification right = Simplification.ofSlider(1.0);
        assertEquals(0.20, right.position(), 1.0E-12);
        assertEquals(10.0, right.angle(), 1.0E-9);
        assertEquals(Simplification.MAX_SPAN, right.span());
        Simplification left = Simplification.ofSlider(1.0E-9);
        assertEquals(Simplification.MIN_POSITION, left.position(), 1.0E-9);
        assertEquals(Simplification.MIN_SPAN, left.span());
    }

    @Test
    void reducedPositionMatchesOldDensity() {
        Simplification reduced = Simplification.ofSlider(Simplification.DEFAULT_SLIDER);
        assertEquals(KeyframeDensity.REDUCED.positionTolerance(), reduced.position(), 1.0E-12);
        assertEquals(KeyframeDensity.REDUCED.angleTolerance(), reduced.angle(), 1.0E-9);
        assertEquals(CameraTrack.MAX_SPAN_TICKS, reduced.span());
        assertEquals(0.188, Simplification.DEFAULT_SLIDER, 0.001);
        assertEquals(0.0, Simplification.sliderOf(KeyframeDensity.EVERY_TICK), 0.0);
        assertEquals(Simplification.DEFAULT_SLIDER, Simplification.sliderOf(KeyframeDensity.REDUCED), 0.0);
    }

    @Test
    void anchorsOfTheDesign() {
        double six = Simplification.slider(0.06);
        assertEquals(0.674, six, 0.001);
        Simplification at = Simplification.ofSlider(six);
        assertEquals(0.06, at.position(), 1.0E-9);
        assertEquals(3.0, at.angle(), 1.0E-9);
        assertEquals(30, at.span());
        double previous = 0.0;
        for (int i = 1; i <= 100; i++) {
            Simplification s = Simplification.ofSlider(i / 100.0);
            assertTrue(s.position() > previous);
            assertTrue(s.span() >= Simplification.MIN_SPAN && s.span() <= Simplification.MAX_SPAN);
            assertEquals(i / 100.0, Simplification.slider(s.position()), 1.0E-9);
            previous = s.position();
        }
    }

    @Test
    void coarserGrowsFromTheWantedTolerance() {
        Simplification base = Simplification.ofPosition(0.02);
        Simplification twice = base.coarser(2.0);
        assertEquals(0.04, twice.position(), 1.0E-12);
        assertEquals(2.0, twice.angle(), 1.0E-9);
        assertEquals(20, twice.span());
        Simplification fromOff = Simplification.OFF.coarser(1.0);
        assertEquals(Simplification.MIN_POSITION, fromOff.position(), 1.0E-12);
        assertEquals(Simplification.MIN_SPAN, fromOff.span());
        assertEquals(Simplification.MAX_SPAN, base.coarser(100.0).span());
    }
}
