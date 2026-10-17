package de.lardtom.propwash.video;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkBudgetTest {
    @Test
    void closeRangeIsPerfectForEveryVideoType() {
        for (VideoType type : VideoType.values()) {
            assertEquals(1.0, LinkBudget.quality(type, 5.0, 0.0), 1.0E-9, type.name());
            assertEquals(1.0, LinkBudget.quality(type, 0.0, 0.0), 1.0E-9, type.name());
        }
    }

    @Test
    void qualityNeverRisesWithDistance() {
        for (VideoType type : VideoType.values()) {
            double previous = 1.0;
            for (double d = 1.0; d < type.rangeBlocks * 3.0; d += 2.0) {
                double q = LinkBudget.quality(type, d, 0.0);
                assertTrue(q <= previous + 1.0E-12, type.name() + " at " + d);
                assertTrue(q >= 0.0 && q <= 1.0);
                previous = q;
            }
            assertEquals(0.0, previous, 1.0E-12, type.name() + " far away");
        }
    }

    @Test
    void analogDegradesGraduallyAroundItsRange() {
        VideoType type = VideoType.ANALOG_RACE;
        double quarter = LinkBudget.quality(type, type.rangeBlocks * 0.25, 0.0);
        double half = LinkBudget.quality(type, type.rangeBlocks * 0.5, 0.0);
        double atRange = LinkBudget.quality(type, type.rangeBlocks, 0.0);
        double beyond = LinkBudget.quality(type, type.rangeBlocks * 1.5, 0.0);
        assertEquals(1.0, quarter, 1.0E-9);
        assertTrue(half > 0.6 && half < 0.9, "half range " + half);
        assertTrue(atRange > 0.15 && atRange < 0.4, "at range " + atRange);
        assertTrue(beyond > 0.0 && beyond < 0.1, "1.5x range " + beyond);
        assertEquals(0.0, LinkBudget.quality(type, type.rangeBlocks * 2.01, 0.0), 1.0E-9);
    }

    @Test
    void digitalHoldsCleanThenDegradesInStagesBeforeItIsLost() {
        VideoType type = VideoType.DIGITAL_HD;
        assertEquals(1.0, LinkBudget.quality(type, type.rangeBlocks * 0.44, 0.0), 1.0E-9);
        assertEquals(DigitalBreakup.Stage.CLEAN, DigitalBreakup.stage(LinkBudget.quality(type, type.rangeBlocks * 0.6, 0.0)));
        double atRange = LinkBudget.quality(type, type.rangeBlocks, 0.0);
        assertEquals(DigitalBreakup.Stage.BLOCKY, DigitalBreakup.stage(atRange), "at range " + atRange);
        assertEquals(DigitalBreakup.Stage.FREEZES, DigitalBreakup.stage(LinkBudget.quality(type, type.rangeBlocks * 1.3, 0.0)));
        assertEquals(DigitalBreakup.Stage.LOST, DigitalBreakup.stage(LinkBudget.quality(type, type.rangeBlocks * 1.5, 0.0)));
        assertEquals(0.0, LinkBudget.quality(type, type.rangeBlocks * 1.8, 0.0), 1.0E-9);
    }

    @Test
    void digitalTransitionIsNarrowerThanAnalog() {
        double analogWidth = width(VideoSystem.ANALOG);
        double digitalWidth = width(VideoSystem.DIGITAL);
        assertTrue(digitalWidth < analogWidth * 0.75, "digital " + digitalWidth + " analog " + analogWidth);
    }

    private static double width(VideoSystem system) {
        double range = 400.0;
        double clean = Double.NaN;
        double lost = Double.NaN;
        for (double d = 1.0; d < range * 4.0; d += 0.5) {
            double q = LinkBudget.quality(system, d, range, 0.0);
            if (Double.isNaN(clean) && q < 0.99) {
                clean = d;
            }
            if (Double.isNaN(lost) && q <= 0.01) {
                lost = d;
            }
        }
        return lost / clean;
    }

    @Test
    void biggerTransmittersReachFurther() {
        double distance = 300.0;
        assertTrue(LinkBudget.quality(VideoType.ANALOG_RACE, distance, 0.0) > LinkBudget.quality(VideoType.ANALOG_NANO, distance, 0.0));
        assertTrue(LinkBudget.quality(VideoType.DIGITAL_HD, 380.0, 0.0) > LinkBudget.quality(VideoType.DIGITAL_LITE, 380.0, 0.0));
    }

    @Test
    void obstructionActsLikeExtraDistance() {
        VideoType type = VideoType.ANALOG_RACE;
        double clear = LinkBudget.quality(type, 150.0, 0.0);
        double wall = LinkBudget.quality(type, 150.0, 7.0);
        double hill = LinkBudget.quality(type, 150.0, 40.0);
        assertTrue(wall < clear);
        assertEquals(0.0, hill, 1.0E-9);
        double doubled = LinkBudget.quality(type, 300.0, 0.0);
        double sixDb = LinkBudget.quality(type, 150.0, 20.0 * Math.log10(2.0));
        assertEquals(doubled, sixDb, 1.0E-9);
    }

    @Test
    void unlimitedRangeIsAlwaysPerfect() {
        assertEquals(1.0, LinkBudget.quality(VideoSystem.DIGITAL, 1.0E6, Double.POSITIVE_INFINITY, 0.0), 1.0E-12);
        assertEquals(Double.POSITIVE_INFINITY, LinkBudget.maxReachBlocks(VideoSystem.ANALOG, Double.POSITIVE_INFINITY));
    }

    @Test
    void maxReachMatchesTheLossPoint() {
        for (VideoSystem system : new VideoSystem[] {VideoSystem.ANALOG, VideoSystem.DIGITAL}) {
            double reach = LinkBudget.maxReachBlocks(system, 400.0);
            assertEquals(0.0, LinkBudget.quality(system, reach * 1.001, 400.0, 0.0), 1.0E-9);
            assertTrue(LinkBudget.quality(system, reach * 0.95, 400.0, 0.0) > 0.0);
        }
    }
}
