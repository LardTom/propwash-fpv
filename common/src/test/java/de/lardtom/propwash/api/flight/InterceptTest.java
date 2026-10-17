package de.lardtom.propwash.api.flight;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterceptTest {
    private static double[] fly(Intercept.Aim aim, double speed, double gravity, double t) {
        return new double[]{aim.dx() * speed * t, aim.dy() * speed * t - 0.5 * gravity * t * t, aim.dz() * speed * t};
    }

    @Test
    void staticTargetWithoutGravityIsAStraightLine() {
        Intercept.Aim aim = Intercept.aim(0, 0, 0, 10, 0, 0, 0, 0, 0, 2.0, 0.0, 100.0).orElseThrow();
        assertEquals(1.0, aim.dx(), 1.0E-9);
        assertEquals(5.0, aim.time(), 1.0E-6);
    }

    @Test
    void movingTargetIsLedAndGravityCompensated() {
        double speed = 3.0;
        double gravity = 0.05;
        Intercept.Aim aim = Intercept.aim(0, 0, 0, 20, 5, 0, 0, 0, 0.4, speed, gravity, 200.0).orElseThrow();
        double[] hit = fly(aim, speed, gravity, aim.time());
        double tx = 20.0;
        double ty = 5.0;
        double tz = 0.4 * aim.time();
        double miss = Math.sqrt(Math.pow(hit[0] - tx, 2) + Math.pow(hit[1] - ty, 2) + Math.pow(hit[2] - tz, 2));
        assertTrue(miss < 0.3, "Verfehlt um " + miss);
        assertTrue(aim.dy() > 5.0 / Math.hypot(20.0, 5.0), "Kein Vorhalt gegen die Schwerkraft");
        assertTrue(aim.dz() > 0.0, "Kein Vorhalt in Flugrichtung");
    }

    @Test
    void unreachableOrInvalidGivesNothing() {
        assertEquals(Optional.empty(), Intercept.aim(0, 0, 0, 10, 0, 0, 5.0, 0, 0, 1.0, 0.0, 100.0));
        assertEquals(Optional.empty(), Intercept.aim(0, 0, 0, 10, 0, 0, 0, 0, 0, 0.0, 0.0, 100.0));
        assertEquals(Optional.empty(), Intercept.aim(0, 0, 0, Double.NaN, 0, 0, 0, 0, 0, 1.0, 0.0, 100.0));
        assertEquals(Optional.empty(), Intercept.aim(0, 0, 0, 1000, 0, 0, 0, 0, 0, 1.0, 0.0, 100.0));
    }
}
