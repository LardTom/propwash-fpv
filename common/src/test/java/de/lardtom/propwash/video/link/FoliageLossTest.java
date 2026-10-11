package de.lardtom.propwash.video.link;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoliageLossTest {
    @Test
    void parametersOfItuP833InLeafModelAt5800MHz() {
        assertEquals(1.16, FoliageLoss.INITIAL_SLOPE, 1.0E-9);
        assertEquals(1.27 / Math.pow(5.8, 0.63), FoliageLoss.FINAL_SLOPE, 1.0E-12);
        assertEquals(0.42, FoliageLoss.FINAL_SLOPE, 0.005);
        assertEquals(27.9, FoliageLoss.TURNOVER_DB, 0.1);
        assertEquals(50.0, FoliageLoss.MAX_DB, 0.5);
        assertEquals(47.8, 1.15 * Math.pow(5800.0, 0.43), 0.1);
    }

    @Test
    void shortDepthsCostAboutTheInitialSlope() {
        assertEquals(1.15, FoliageLoss.db(1.0), 0.01);
        assertEquals(5.57, FoliageLoss.db(5.0), 0.02);
        assertEquals(10.7, FoliageLoss.db(10.0), 0.05);
        assertEquals(0.0, FoliageLoss.db(0.0), 0.0);
        assertEquals(0.0, FoliageLoss.db(-3.0), 0.0);
    }

    @Test
    void deepFoliageSaturatesTowardsTheFinalSlopeAndTheWoodlandMaximum() {
        double previous = 0.0;
        double previousSlope = Double.POSITIVE_INFINITY;
        for (int d = 1; d <= 60; d++) {
            double loss = FoliageLoss.db(d);
            double slope = loss - previous;
            assertTrue(loss > previous, "depth " + d);
            assertTrue(slope <= previousSlope + 1.0E-9, "slope at " + d);
            previous = loss;
            previousSlope = slope;
        }
        assertEquals(FoliageLoss.MAX_DB, FoliageLoss.db(200.0), 0.0);
        assertTrue(FoliageLoss.db(40.0) < 40.0 * FoliageLoss.INITIAL_SLOPE * 0.8);
    }
}
