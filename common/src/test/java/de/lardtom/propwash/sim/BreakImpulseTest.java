package de.lardtom.propwash.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreakImpulseTest {
    @Test
    void residualSpeedKeepsTheEnergyBalance() {
        double m = 0.525;
        double after = BreakImpulse.residual(m, 25.0, 36.0);
        assertEquals(0.5 * m * 625.0 - 36.0, 0.5 * m * after * after, 1.0E-9);
        assertEquals(0.0, BreakImpulse.residual(m, 5.0, 36.0), 0.0, "mehr Energie als vorhanden hält die Drohne an, dreht sie nicht um");
    }

    @Test
    void shardsShareTheMomentum() {
        double m = 0.525;
        double plug = BreakImpulse.plugMass(10.0, 0.03, 1.0);
        assertEquals(BreakImpulse.SHARD_COUPLING * 10.0 * 0.03, plug, 1.0E-12);
        double after = BreakImpulse.afterShards(m, 20.0, plug);
        assertEquals(m * 20.0, (m + plug) * after, 1.0E-9, "Impuls von Drohne und mitgerissenen Scherben bleibt");
        assertEquals(20.0, BreakImpulse.afterShards(m, 20.0, 0.0), 0.0);
        assertEquals(0.0, BreakImpulse.afterShards(m, 0.0, plug), 0.0);
        assertEquals(2.0 * plug, BreakImpulse.plugMass(10.0, 0.03, 2.0), 1.0E-12);
        assertEquals(0.0, BreakImpulse.plugMass(Double.NaN, 0.03, 1.0), 0.0);
        assertEquals(0.0, BreakImpulse.plugMass(10.0, -1.0, 1.0), 0.0);
    }

    @Test
    void hitAreaFollowsTheAttitudeAndTheShareInTheGlass() {
        double side = 0.018;
        double top = 0.035;
        double frontal = 0.012;
        assertEquals(frontal, BreakImpulse.hitArea(side, top, frontal, 0.0, 0.0, -1.0, 1.0), 1.0E-12);
        assertEquals(top, BreakImpulse.hitArea(side, top, frontal, 0.0, 1.0, 0.0, 1.0), 1.0E-12);
        double tilted = BreakImpulse.hitArea(side, top, frontal, 0.0, Math.sin(Math.toRadians(40.0)), -Math.cos(Math.toRadians(40.0)), 1.0);
        assertTrue(tilted > 2.0 * frontal, "geneigt zeigt die Drohne der Scheibe ihre Oberseite: " + tilted);
        assertEquals(0.5 * tilted, BreakImpulse.hitArea(side, top, frontal, 0.0, Math.sin(Math.toRadians(40.0)), -Math.cos(Math.toRadians(40.0)), 0.5),
                1.0E-12);
        assertEquals(0.0, BreakImpulse.hitArea(side, top, frontal, 0.0, 0.0, -1.0, Double.NaN), 0.0);
    }

    @Test
    void strengthIsClampedAndLimitsTorqueAndStrikes() {
        assertEquals(0.0, BreakImpulse.strength(-1.0), 0.0);
        assertEquals(0.0, BreakImpulse.strength(Double.NaN), 0.0);
        assertEquals(BreakImpulse.MAX_STRENGTH, BreakImpulse.strength(50.0), 0.0);
        assertEquals(BreakImpulse.TUMBLE_SHARE, BreakImpulse.tumbleShare(1.0), 1.0E-12);
        assertTrue(BreakImpulse.tumbleShare(BreakImpulse.MAX_STRENGTH) <= 1.0);
        assertEquals(BreakImpulse.STRIKE_LOSS, BreakImpulse.strikeLoss(3.0), 1.0E-12, "ein Prop verliert nie mehr als bei 100 %");
        assertEquals(0.5 * BreakImpulse.STRIKE_LOSS, BreakImpulse.strikeLoss(0.5), 1.0E-12);
    }

    @Test
    void irregularityIsReproducibleAndSpread() {
        double sum = 0.0;
        double min = 1.0;
        double max = -1.0;
        for (long seed = 0; seed < 2000; seed++) {
            double a = BreakImpulse.unit(seed, 0);
            assertEquals(a, BreakImpulse.unit(seed, 0), 0.0);
            assertTrue(a >= -1.0 && a < 1.0);
            assertNotEquals(a, BreakImpulse.unit(seed, 1), 0.0);
            sum += a;
            min = Math.min(min, a);
            max = Math.max(max, a);
        }
        assertTrue(Math.abs(sum / 2000.0) < 0.05, "Mittel nahe null: " + sum / 2000.0);
        assertTrue(min < -0.95 && max > 0.95);
    }
}
