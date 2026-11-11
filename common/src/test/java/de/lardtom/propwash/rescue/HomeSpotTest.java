package de.lardtom.propwash.rescue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeSpotTest {
    private static final double WIDTH = 0.29;
    private static final double HEIGHT = 0.07;
    private static final double EPS = 1.0E-9;

    private static final class World implements HomeResetRules.Space {
        private final List<double[]> boxes = new ArrayList<>();

        World box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.boxes.add(new double[]{minX, minY, minZ, maxX, maxY, maxZ});
            return this;
        }

        World block(int x, int y, int z) {
            return this.box(x, y, z, x + 1, y + 1, z + 1);
        }

        World partial(int x, int y, int z, double height) {
            return this.box(x, y, z, x + 1, y + height, z + 1);
        }

        World floor(int y, int radius) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    this.block(x, y, z);
                }
            }
            return this;
        }

        @Override
        public boolean free(double x, double y, double z) {
            double half = WIDTH * 0.5;
            for (double[] b : this.boxes) {
                if (b[0] < x + half && b[3] > x - half && b[1] < y + HEIGHT && b[4] > y && b[2] < z + half && b[5] > z - half) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public double fall(double x, double y, double z, double depth) {
            double half = WIDTH * 0.5;
            double best = depth;
            for (double[] b : this.boxes) {
                if (b[0] < x + half && b[3] > x - half && b[2] < z + half && b[5] > z - half && b[4] <= y + EPS) {
                    best = Math.min(best, y - b[4]);
                }
            }
            return best;
        }
    }

    @Test
    void freeHomeIsUsedExactly() {
        World world = new World().floor(63, 4);
        HomeResetRules.Spot spot = HomeResetRules.spot(0.5, 64.0, 0.5, world);
        assertNotNull(spot);
        assertEquals(64.0, spot.y(), EPS);
        assertEquals(0.5, spot.x(), EPS);
        assertEquals(0.5, spot.z(), EPS);
        assertFalse(spot.nearby());
    }

    @Test
    void homeInsideAPartialBlockStandsOnItWithoutHint() {
        for (double height : new double[]{0.0625, 0.25, 0.5, 0.75, 0.875}) {
            World world = new World().floor(63, 4).partial(0, 64, 0, height);
            HomeResetRules.Spot spot = HomeResetRules.spot(0.5, 64.0, 0.5, world);
            assertNotNull(spot, "Höhe " + height);
            assertEquals(64.0 + height, spot.y(), EPS, "Höhe " + height);
            assertEquals(0.5, spot.x(), EPS);
            assertEquals(0.5, spot.z(), EPS);
            assertFalse(spot.nearby(), "Höhe " + height);
        }
    }

    @Test
    void blockOnTheHomeUnderACeilingGoesBesideInsteadOfThroughTheCeiling() {
        World world = new World().floor(63, 4).block(0, 64, 0).block(0, 65, 0).block(0, 66, 0).block(0, 67, 0);
        HomeResetRules.Spot spot = HomeResetRules.spot(0.5, 64.0, 0.5, world);
        assertNotNull(spot);
        assertEquals(64.0, spot.y(), EPS);
        assertEquals(1.0, Math.abs(spot.x() - 0.5) + Math.abs(spot.z() - 0.5), EPS);
        assertTrue(spot.nearby());
        assertTrue(world.free(spot.x(), spot.y(), spot.z()));
    }

    @Test
    void blockOnTheHomeUnderOpenSkyPrefersTheSameLevel() {
        World world = new World().floor(63, 4).block(0, 64, 0);
        HomeResetRules.Spot spot = HomeResetRules.spot(0.5, 64.0, 0.5, world);
        assertNotNull(spot);
        assertEquals(64.0, spot.y(), EPS);
        assertEquals(1.0, Math.abs(spot.x() - 0.5) + Math.abs(spot.z() - 0.5), EPS);
        assertTrue(spot.nearby());
    }

    @Test
    void neverDropsThroughAFloorOrOffAPlatform() {
        World world = new World().floor(63, 4).block(-1, 70, 0).block(0, 70, 0).block(1, 70, 0).block(0, 71, 0);
        HomeResetRules.Spot spot = HomeResetRules.spot(0.5, 71.0, 0.5, world);
        assertNotNull(spot);
        assertEquals(71.0, spot.y(), EPS);
        assertEquals(0.5, spot.z(), EPS);
        assertEquals(1.0, Math.abs(spot.x() - 0.5), EPS);

        World single = new World().floor(63, 4).block(0, 70, 0).block(0, 71, 0);
        HomeResetRules.Spot top = HomeResetRules.spot(0.5, 71.0, 0.5, single);
        assertNotNull(top);
        assertEquals(72.0, top.y(), EPS);
        assertTrue(top.nearby());
    }

    @Test
    void mayStepDownOneBlockButNotMore() {
        World world = new World().floor(63, 4).block(0, 64, 0).block(0, 65, 0).block(0, 66, 0).block(0, 67, 0);
        HomeResetRules.Spot spot = HomeResetRules.spot(0.5, 65.0, 0.5, world);
        assertNotNull(spot);
        assertEquals(64.0, spot.y(), EPS);

        World deep = new World().floor(62, 4).block(0, 63, 0).block(0, 64, 0).block(0, 65, 0).block(0, 66, 0).block(0, 67, 0);
        assertNull(HomeResetRules.spot(0.5, 65.0, 0.5, deep));
    }

    @Test
    void buriedHomeHasNoSpot() {
        World world = new World().floor(63, 6);
        for (int y = 64; y <= 69; y++) {
            for (int x = -5; x <= 5; x++) {
                for (int z = -5; z <= 5; z++) {
                    world.block(x, y, z);
                }
            }
        }
        assertNull(HomeResetRules.spot(0.5, 64.0, 0.5, world));
    }

    @Test
    void groundIsTheTopOfAPartialBlockEvenWhenItFloats() {
        World world = new World().floor(63, 4).partial(0, 70, 0, 0.5);
        assertEquals(70.5, HomeResetRules.ground(0.5, 70.5, 0.5, world, 96.0), EPS);
        assertEquals(70.5, HomeResetRules.ground(0.5, 70.49, 0.5, world, 96.0), EPS);
        World table = new World().floor(63, 4).partial(0, 64, 0, 0.75);
        assertEquals(64.75, HomeResetRules.ground(0.5, 64.75, 0.5, table, 96.0), EPS);
        World carpet = new World().floor(63, 4).partial(0, 64, 0, 0.0625);
        assertEquals(64.0625, HomeResetRules.ground(0.5, 64.0625, 0.5, carpet, 96.0), EPS);
    }

    @Test
    void groundInTheAirFallsToTheFirstSurfaceOrStays() {
        World world = new World().floor(63, 4).block(0, 70, 0);
        assertEquals(71.0, HomeResetRules.ground(0.5, 80.0, 0.5, world, 96.0), EPS);
        assertEquals(64.0, HomeResetRules.ground(2.5, 80.0, 0.5, world, 96.0), EPS);
        assertEquals(80.0, HomeResetRules.ground(0.5, 80.0, 0.5, new World(), 96.0), EPS);
    }

    @Test
    void groundInsideABlockLiftsOutOrGivesUp() {
        World world = new World().floor(63, 4).block(0, 64, 0);
        assertEquals(65.0, HomeResetRules.ground(0.5, 64.2, 0.5, world, 96.0), EPS);
        World wall = new World().floor(63, 4).block(0, 64, 0).block(0, 65, 0);
        assertTrue(Double.isNaN(HomeResetRules.ground(0.5, 64.2, 0.5, wall, 96.0)));
        assertTrue(Double.isNaN(HomeResetRules.lift(0.5, 64.2, 0.5, wall)));
        assertEquals(64.0, HomeResetRules.lift(1.5, 64.0, 0.5, wall), EPS);
    }
}
