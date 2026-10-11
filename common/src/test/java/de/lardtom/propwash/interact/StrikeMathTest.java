package de.lardtom.propwash.interact;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.AirframeSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrikeMathTest {
    private static double mass(DroneBuild build) {
        return AirframeSpec.of(build).massKg;
    }

    @Test
    void tinyWhoopsBounceOffWithoutHurting() {
        assertFalse(StrikeMath.of(mass(DroneBuild.WHOOP), 6.0).hits(), "Whoop mit 6 m/s");
        assertFalse(StrikeMath.of(mass(DroneBuild.FREESTYLE), 1.5).hits(), "langsames Anstupsen");
    }

    @Test
    void damageGrowsWithMomentumAndIsCapped() {
        double m = mass(DroneBuild.FREESTYLE);
        StrikeMath.Strike slow = StrikeMath.of(m, 8.0);
        StrikeMath.Strike fast = StrikeMath.of(m, 20.0);
        assertTrue(slow.hits() && fast.hits());
        assertEquals(StrikeMath.DAMAGE_PER_MOMENTUM * m * 20.0, fast.damage(), 1.0E-4);
        assertTrue(fast.damage() > slow.damage());
        assertTrue(fast.knockback() > slow.knockback());
        StrikeMath.Strike brick = StrikeMath.of(4.0, 40.0);
        assertEquals((float) StrikeMath.MAX_DAMAGE, brick.damage());
        assertEquals((float) StrikeMath.MAX_KNOCKBACK, brick.knockback());
        assertEquals(160.0, brick.momentum(), 1.0E-9);
    }

    @Test
    void fullSpeedFreestyleHurtsLikeAStrongHitButNotMore() {
        StrikeMath.Strike strike = StrikeMath.of(mass(DroneBuild.FREESTYLE), 35.0);
        assertTrue(strike.damage() >= 6.0F && strike.damage() <= StrikeMath.MAX_DAMAGE, "Schaden " + strike.damage());
        assertTrue(strike.knockback() > 0.4F, "mehr Rückstoß als ein Faustschlag");
    }

    @Test
    void cargoMakesTheSameHitHarder() {
        double m = mass(DroneBuild.FREESTYLE);
        assertTrue(StrikeMath.of(m + 0.4, 15.0).damage() > StrikeMath.of(m, 15.0).damage());
    }

    @Test
    void closingSpeedOnlyCountsTheApproach() {
        assertEquals(10.0, StrikeMath.closingSpeed(10.0, 0.0, 3.0, 1.0, 0.0, 0.0), 1.0E-9);
        assertEquals(0.0, StrikeMath.closingSpeed(-10.0, 0.0, 0.0, 1.0, 0.0, 0.0), 1.0E-9);
        assertEquals(3.0, StrikeMath.closingSpeed(10.0, 0.0, 3.0, 0.0, 0.0, 2.0), 1.0E-9);
        assertEquals(0.0, StrikeMath.closingSpeed(Double.NaN, 0.0, 0.0, 1.0, 0.0, 0.0), 1.0E-9);
        assertEquals(0.0, StrikeMath.closingSpeed(5.0, 0.0, 0.0, 0.0, 0.0, 0.0), 1.0E-9);
    }

    @Test
    void invalidInputsNeverHit() {
        assertFalse(StrikeMath.of(Double.NaN, 20.0).hits());
        assertFalse(StrikeMath.of(0.5, Double.POSITIVE_INFINITY).hits());
        assertFalse(StrikeMath.of(-1.0, 20.0).hits());
        assertEquals(StrikeMath.Strike.NONE, StrikeMath.of(0.5, 0.0));
    }
}
