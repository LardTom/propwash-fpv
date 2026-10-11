package de.lardtom.propwash.video.link;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnifeEdgeTest {
    private static final double[][] REFERENCE = {
            {0.1, 0.0999975, 0.0005236},
            {0.5, 0.4923442, 0.0647324},
            {1.0, 0.7798934, 0.4382591},
            {1.5, 0.4452612, 0.6975050},
            {2.0, 0.4882534, 0.3434157},
            {3.0, 0.6057208, 0.4963130},
            {5.0, 0.5636312, 0.4991914},
            {10.0, 0.4998987, 0.4681700}};

    @Test
    void fresnelIntegralsMatchReferenceValues() {
        double[] cs = new double[2];
        for (double[] row : REFERENCE) {
            KnifeEdge.fresnel(row[0], cs);
            assertEquals(row[1], cs[0], 2.0E-6, "C(" + row[0] + ")");
            assertEquals(row[2], cs[1], 2.0E-6, "S(" + row[0] + ")");
            KnifeEdge.fresnel(-row[0], cs);
            assertEquals(-row[1], cs[0], 2.0E-6);
            assertEquals(-row[2], cs[1], 2.0E-6);
        }
        KnifeEdge.fresnel(Double.POSITIVE_INFINITY, cs);
        assertEquals(0.5, cs[0], 0.0);
        assertEquals(0.5, cs[1], 0.0);
        KnifeEdge.fresnel(0.0, cs);
        assertEquals(0.0, cs[0], 1.0E-12);
    }

    @Test
    void knifeEdgeLossFollowsItuP526Equation31() {
        assertEquals(6.03, KnifeEdge.lossDb(0.0), 0.01);
        assertEquals(13.93, KnifeEdge.lossDb(1.0), 0.01);
        assertEquals(19.04, KnifeEdge.lossDb(2.0), 0.01);
        assertEquals(0.0, KnifeEdge.lossDb(-0.78), 0.0);
        assertEquals(0.0, KnifeEdge.lossDb(-3.0), 0.0);
        assertEquals(0.0, KnifeEdge.lossDb(Double.NaN), 0.0);
        assertEquals(0.0, KnifeEdge.lossDb(-0.7799), 0.01);
    }

    @Test
    void approximationStaysCloseToTheExactFresnelSolution() {
        for (double nu = -0.7; nu <= 10.0; nu += 0.05) {
            assertEquals(KnifeEdge.exactLossDb(nu), KnifeEdge.lossDb(nu), 0.5, "nu " + nu);
        }
        assertEquals(6.02, KnifeEdge.exactLossDb(0.0), 0.01);
    }

    @Test
    void lossGrowsWithDeeperShadow() {
        double previous = 0.0;
        for (double nu = -0.75; nu <= 20.0; nu += 0.25) {
            double loss = KnifeEdge.lossDb(nu);
            assertTrue(loss >= previous, "nu " + nu);
            previous = loss;
        }
    }
}
