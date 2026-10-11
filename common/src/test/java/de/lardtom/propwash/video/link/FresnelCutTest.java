package de.lardtom.propwash.video.link;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FresnelCutTest {
    private static final int N = FresnelCut.SAMPLES;

    @FunctionalInterface
    interface Mask {
        double amplitude(double x, double y);
    }

    static double lossDb(Mask mask) {
        double center = mask.amplitude(0.0, 0.0);
        double[] right = new double[N];
        double[] left = new double[N];
        double[] up = new double[N];
        double[] down = new double[N];
        for (int i = 0; i < N; i++) {
            double r = FresnelCut.radius(i);
            right[i] = mask.amplitude(r, 0.0);
            left[i] = mask.amplitude(-r, 0.0);
            up[i] = mask.amplitude(0.0, r);
            down[i] = mask.amplitude(0.0, -r);
        }
        double x = FresnelCut.power(center, right, N, left, N);
        double y = FresnelCut.power(center, up, N, down, N);
        return FresnelCut.lossDb(FresnelCut.combine(x, y, center * center));
    }

    private static double jAverage(double nu, int edges) {
        return -10.0 * Math.log10(edges * Math.pow(10.0, -KnifeEdge.lossDb(nu) / 10.0));
    }

    private static double jMinimum(double nu, int edges) {
        return -20.0 * Math.log10(edges * Math.pow(10.0, -KnifeEdge.lossDb(nu) / 20.0));
    }

    @Test
    void openFieldIsExactlyLossless() {
        double[] ones = new double[N];
        Arrays.fill(ones, 1.0);
        assertEquals(1.0, FresnelCut.power(1.0, ones, N, ones, N), 1.0E-12);
        assertEquals(1.0, FresnelCut.power(1.0, ones, 0, ones, 3), 1.0E-12);
        assertEquals(1.0, FresnelCut.combine(1.0, 1.0, 1.0), 0.0);
        assertEquals(0.0, FresnelCut.lossDb(1.0), 0.0);
        assertEquals(0.0, lossDb((x, y) -> 1.0), 1.0E-9);
    }

    @Test
    void uniformScreenCostsExactlyItsOwnLoss() {
        for (double t : new double[]{0.9, 0.5, 0.1, 0.01}) {
            assertEquals(-20.0 * Math.log10(t), lossDb((x, y) -> t), 1.0E-6, "transmission " + t);
        }
    }

    @Test
    void combinationIsExactForSeparableCases() {
        assertEquals(0.3, FresnelCut.combine(0.3, 0.0, 0.0), 1.0E-12);
        assertEquals(0.3, FresnelCut.combine(0.0, 0.3, 0.0), 1.0E-12);
        assertEquals(0.3, FresnelCut.combine(0.3, 1.0, 1.0), 1.0E-12);
        assertEquals(0.25, FresnelCut.combine(0.25, 0.25, 0.25), 1.0E-12);
        assertEquals(0.12, FresnelCut.combine(0.4, 0.3, 1.0), 1.0E-12);
        assertEquals(FresnelCut.MIN_POWER, FresnelCut.combine(0.0, 0.0, 0.0), 0.0);
    }

    @Test
    void straightEdgeMatchesTheKnifeEdgeOfItuP526() {
        for (double nu = -2.0; nu <= 5.0; nu += 0.1) {
            double edge = nu;
            double horizontal = lossDb((x, y) -> y < edge ? 0.0 : 1.0);
            double vertical = lossDb((x, y) -> x > -edge ? 0.0 : 1.0);
            assertEquals(KnifeEdge.lossDb(edge), horizontal, 1.6, "horizontal edge at " + edge);
            assertEquals(horizontal, vertical, 1.0E-9, "edge orientation " + edge);
        }
    }

    @Test
    void nearTheLineOfSightAlreadyCostsSignal() {
        double near = lossDb((x, y) -> y < -0.4 ? 0.0 : 1.0);
        double far = lossDb((x, y) -> y < -1.6 ? 0.0 : 1.0);
        assertTrue(near > 1.5, "obstacle 0.4 zones below the line " + near);
        assertTrue(far < 0.5, "obstacle 1.6 zones below the line " + far);
    }

    @Test
    void narrowObstaclesShadowLessThanWideOnes() {
        double previous = 0.0;
        for (double half : new double[]{0.2, 0.5, 1.0, 2.0, 4.0, 8.0, 20.0}) {
            double loss = lossDb((x, y) -> Math.abs(x) < half ? 0.0 : 1.0);
            assertTrue(loss > previous, "strip " + half + ": " + loss + " <= " + previous);
            previous = loss;
        }
        double trunk = lossDb((x, y) -> Math.abs(x) < 1.5 ? 0.0 : 1.0);
        double wall = lossDb((x, y) -> Math.abs(x) < 15.0 && y < 15.0 ? 0.0 : 1.0);
        assertTrue(wall > trunk + 10.0, "trunk " + trunk + " wall " + wall);
    }

    @Test
    void stripLiesBetweenCoherentAndAverageLossOfItsTwoEdges() {
        for (double half : new double[]{0.7, 1.0, 1.5, 2.5, 4.0, 8.0}) {
            double loss = lossDb((x, y) -> Math.abs(x) < half ? 0.0 : 1.0);
            assertTrue(loss >= jMinimum(half, 2) - 1.0 && loss <= jAverage(half, 2) + 1.5,
                    "strip " + half + ": " + loss + " vs " + jMinimum(half, 2) + ".." + jAverage(half, 2));
        }
    }

    @Test
    void finiteScreenApproachesTheAverageLossOfItsFourEdges() {
        for (double half : new double[]{2.0, 4.0, 10.0, 25.0}) {
            double loss = lossDb((x, y) -> Math.abs(x) < half && Math.abs(y) < half ? 0.0 : 1.0);
            assertEquals(jAverage(half, 4), loss, 4.0, "screen " + half);
        }
    }

    @Test
    void openingInAWallIsAnAperture() {
        double window = lossDb((x, y) -> Math.abs(x) < 2.0 && Math.abs(y) < 2.0 ? 1.0 : 0.0);
        double slot = lossDb((x, y) -> Math.abs(x) < 2.0 ? 1.0 : 0.0);
        double small = lossDb((x, y) -> Math.abs(x) < 0.3 && Math.abs(y) < 0.3 ? 1.0 : 0.0);
        assertTrue(window < 3.0, "window " + window);
        assertTrue(slot <= window + 1.0E-9, "slot " + slot);
        assertTrue(small > 10.0, "small hole " + small);
    }
}
