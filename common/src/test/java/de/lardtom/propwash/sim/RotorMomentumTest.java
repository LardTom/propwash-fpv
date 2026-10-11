package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotorMomentumTest {
    private static final double[] RATIOS = {1.2, 1.5, 2.0, 3.0, 4.0, 6.0, 10.0};

    private static double factor(double lambda, double ratio, double coupling) {
        return RotorAero.axialThrustFactor(lambda, ratio, 1.0, coupling);
    }

    @Test
    void hoverAndZeroThrustSpeedStayFixed() {
        for (double r : RATIOS) {
            assertEquals(1.0, factor(0.0, r, 1.0), 1e-12, "r=" + r);
            assertEquals(0.0, factor(1.0, r, 1.0), 1e-12, "r=" + r);
            assertEquals(0.0, factor(1.5, r, 1.0), 1e-12, "r=" + r);
            assertTrue(factor(0.999999, r, 1.0) < 1e-4, "r=" + r);
        }
    }

    @Test
    void climbKeepsMoreThrustThanTheLinearLawAndFallsMonotonically() {
        for (double r : RATIOS) {
            double previous = 1.0;
            for (int i = 1; i < 100; i++) {
                double lambda = i / 100.0;
                double f = factor(lambda, r, 1.0);
                assertTrue(f >= 1.0 - lambda - 1e-12, "r=" + r + " lambda=" + lambda);
                assertTrue(f < previous, "r=" + r + " lambda=" + lambda);
                previous = f;
            }
        }
    }

    @Test
    void initialSlopeFollowsMomentumTheory() {
        for (double r : RATIOS) {
            double h = 1e-6;
            double slope = (factor(h, r, 1.0) - 1.0) / h;
            assertEquals(-r / (2.0 * r - 1.0), slope, 1e-4, "r=" + r);
        }
    }

    @Test
    void crossFlowFadesTheCouplingLikeGlauertsInducedVelocity() {
        assertEquals(1.0, RotorAero.inducedCoupling(0.0, 5.0), 1e-12);
        assertEquals(1.0 - 1.0 / Math.sqrt(5.0), RotorAero.inducedCoupling(5.0, 5.0), 1e-12);
        assertEquals(1.0 - 4.0 / Math.sqrt(20.0), RotorAero.inducedCoupling(10.0, 5.0), 1e-12);
        assertEquals(0.0, RotorAero.inducedCoupling(5.0, 0.0), 1e-12);
        assertTrue(RotorAero.inducedCoupling(1e200, 5.0) >= 0.0);
        double previous = 1.0;
        for (int i = 1; i <= 100; i++) {
            double c = RotorAero.inducedCoupling(i * 0.5, 5.0);
            assertTrue(c < previous && c > 0.0, "cross=" + i * 0.5);
            previous = c;
        }
        assertEquals(1.0 - 0.3, factor(0.3, 4.0, 0.0), 1e-12);
        double half = factor(0.3, 4.0, 0.5);
        assertEquals(0.5 * (factor(0.3, 4.0, 1.0) + 0.7), half, 1e-12);
    }

    @Test
    void descentAndDegenerateRotorsKeepTheLinearLaw() {
        for (double lambda : new double[]{-0.05, -0.3, -0.6, -2.0}) {
            assertEquals(RotorAero.axialThrustFactor(lambda), factor(lambda, 4.0, 1.0), 0.0);
        }
        assertEquals(0.8, RotorAero.axialThrustFactor(0.2, 1.0, 1.0, 1.0), 1e-12);
        assertEquals(0.8, RotorAero.axialThrustFactor(0.2, 4.0, 0.0, 1.0), 1e-12);
        assertEquals(0.8, RotorAero.axialThrustFactor(0.2, 4.0e7, 1.0, 1.0), 1e-9);
    }

    @Test
    void inducedVelocityPerOmegaMatchesTheHoverInducedVelocity() {
        for (DroneBuild build : DroneBuild.PRESETS) {
            AirframeSpec p = AirframeSpec.of(build);
            assertEquals(p.hoverInducedVelocity, p.inducedPerOmega * p.hoverOmega, 1e-9 * p.hoverInducedVelocity, build.toString());
            double ratio = RotorAero.zeroThrustSpeed(p.zeroThrustPerOmega, p.hoverOmega) / p.hoverInducedVelocity;
            assertTrue(ratio > 2.0 && ratio < 8.0, build + " r=" + ratio);
        }
    }

    @Test
    void verticalClimbKeepsMoreThrustButFastForwardFlightStaysLinear() {
        AirframeSpec p = AirframeSpec.of(DroneBuild.PRESETS.get(0));
        double omega = 1.5 * p.hoverOmega;
        double v0 = RotorAero.zeroThrustSpeed(p.zeroThrustPerOmega, omega);
        double axial = 0.25 * v0;
        double vertical = RotorAero.axialThrustFactor(p, 0.25, omega, 0.0);
        double forward = RotorAero.axialThrustFactor(p, 0.25, omega, 10.0 * p.hoverInducedVelocity);
        assertTrue(vertical > 0.75 + 0.05, "vertical=" + vertical);
        assertEquals(0.75, forward, 0.002);
        assertEquals(RotorAero.thrust(p, omega, 0.0, 0.0, 1.0, 1.0) * vertical * RotorAero.ductFactor(p.ducted, p.ductGain, 0.25)
                / RotorAero.ductFactor(p.ducted, p.ductGain, 0.0), RotorAero.thrust(p, omega, axial, 0.0, 1.0, 1.0), 1e-9);
    }
}
