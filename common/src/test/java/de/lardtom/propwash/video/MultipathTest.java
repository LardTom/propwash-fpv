package de.lardtom.propwash.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultipathTest {
    private static final Multipath.Reflectivity OPEN = (x, y, z) -> 0.0;

    private static Multipath.Reflectivity wallAt(int wallX) {
        return (x, y, z) -> x == wallX ? 1.0 : 0.0;
    }

    @Test
    void openSkyHasNoReflectors() {
        assertEquals(0.0, Multipath.reflectors(0.5, 64.5, 0.5, OPEN), 1.0E-9);
        assertEquals(0.0, Multipath.reflectors(Double.NaN, 64.5, 0.5, wallAt(1)), 1.0E-9);
    }

    @Test
    void nearWallsReflectMoreThanFarWalls() {
        double near = Multipath.reflectors(0.5, 64.5, 0.5, wallAt(1));
        double far = Multipath.reflectors(0.5, 64.5, 0.5, wallAt(5));
        double none = Multipath.reflectors(0.5, 64.5, 0.5, wallAt(40));
        assertTrue(near > far, near + " vs " + far);
        assertTrue(far > 0.0);
        assertEquals(0.0, none, 1.0E-9);
        Multipath.Reflectivity corridor = (x, y, z) -> x == 1 || x == -1 ? 1.0 : 0.0;
        assertTrue(Multipath.reflectors(0.5, 64.5, 0.5, corridor) > near, "two walls reflect more than one");
    }

    @Test
    void partialShadowingNeedsSpreadAndAnOpenPath() {
        assertEquals(0.0, Multipath.partial(new double[]{0.0, 0.0, 0.0}, 3), 1.0E-9);
        assertEquals(0.0, Multipath.partial(new double[]{40.0, 40.0, 60.0}, 1), 1.0E-9);
        assertTrue(Multipath.partial(new double[]{0.0, 14.0, 0.0}, 3) > 0.5);
        assertTrue(Multipath.partial(new double[]{30.0, 48.0, 30.0}, 3) < 0.1, "fully shadowed path is not multipath");
    }

    @Test
    void factorIsBounded() {
        assertEquals(0.0, Multipath.factor(0.0, 0.0, 0.0), 1.0E-9);
        double full = Multipath.factor(1.0, 1.0, 1.0);
        assertTrue(full > 0.9 && full <= 1.0);
        assertTrue(Multipath.factor(0.5, 0.0, 0.0) > Multipath.factor(0.0, 0.5, 0.0), "drone side counts more");
    }
}
