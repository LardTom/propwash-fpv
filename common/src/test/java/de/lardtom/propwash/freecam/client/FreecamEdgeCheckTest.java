package de.lardtom.propwash.freecam.client;

import de.lardtom.propwash.freecam.FreecamBounds;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreecamEdgeCheckTest {
    private static final int PLAYER_CX = 2;
    private static final int PLAYER_CZ = -3;
    private static final double ANCHOR_X = 40.5;
    private static final double ANCHOR_Z = -35.5;
    private static final double Y = 70.0;
    private static final double PUSH_X = 6.0 / 20.0;
    private static final double PUSH_Z = 3.0 / 20.0;
    private static final int TICKS = 30;

    private static final class Area implements FreecamBounds.Columns {
        private final Map<Integer, Integer> reach = new HashMap<>();
        private final Set<Long> missing = new HashSet<>();

        Area row(int chunkZ, int lastChunkX) {
            this.reach.put(chunkZ, lastChunkX);
            return this;
        }

        Area rows(int fromChunkZ, int toChunkZ, int lastChunkX) {
            for (int cz = fromChunkZ; cz <= toChunkZ; cz++) {
                this.row(cz, lastChunkX);
            }
            return this;
        }

        Area missing(int chunkX, int chunkZ) {
            this.missing.add(((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL));
            return this;
        }

        @Override
        public boolean loaded(int chunkX, int chunkZ) {
            Integer last = this.reach.get(chunkZ);
            return last != null && chunkX >= -6 && chunkX <= last && !this.missing.contains(((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL));
        }
    }

    private record Glide(double x, double z, int blockedX, List<String> problems) {
    }

    private static Glide glide(Area area, double startX, double startZ, int changeTick, Runnable change) {
        FreecamBounds bounds = new FreecamBounds(area, -64.0, 320.0, ANCHOR_X, ANCHOR_Z);
        FreecamBounds.Step step = new FreecamBounds.Step();
        double x = startX;
        double z = startZ;
        int blockedX = 0;
        List<String> problems = new ArrayList<>();
        for (int tick = 0; tick < TICKS; tick++) {
            if (tick == changeTick) {
                change.run();
            }
            bounds.step(x, Y, z, x + PUSH_X, Y, z + PUSH_Z, step);
            x = step.x;
            z = step.z;
            blockedX += step.blockedX ? 1 : 0;
            assertTrue(bounds.columnLoaded(x, z), "Freecam außerhalb der geladenen Chunks bei Tick " + tick);
            String outside = FreecamEdgeCheck.outside(area, PLAYER_CX, PLAYER_CZ, x, z);
            if (outside != null) {
                problems.add(tick + ": " + outside);
            }
        }
        return new Glide(x, z, blockedX, problems);
    }

    @Test
    void edgeIsTheEndOfTheLoadedRunInTheRow() {
        Area area = new Area().row(-3, 9).row(-2, 11).missing(5, -1).row(-1, 9);
        assertEquals(160, FreecamEdgeCheck.edgeX(area, PLAYER_CX, -3));
        assertEquals(192, FreecamEdgeCheck.edgeX(area, PLAYER_CX, -2));
        assertEquals(80, FreecamEdgeCheck.edgeX(area, PLAYER_CX, -1));
        assertEquals(48, FreecamEdgeCheck.edgeX(new Area(), PLAYER_CX, -3));
    }

    @Test
    void glideIntoANeighbourRowThatReachesFurtherIsInside() {
        Area area = new Area().rows(-6, 0, 10).row(-2, 11);
        Glide glide = glide(area, 175.99, -35.5, -1, () -> {
        });
        assertTrue(glide.blockedX() > 20, "erst an der Grenze der Spieler-Reihe gehalten: " + glide.blockedX());
        assertEquals(-2, FreecamBounds.chunk(glide.z()));
        assertTrue(glide.x() > 176.0, "in der Reihe −2 weiter bis Chunk 11: x " + glide.x());
        assertEquals(List.of(), glide.problems());
    }

    @Test
    void chunkArrivingInTheFreecamRowDuringTheGlideIsInside() {
        Area area = new Area().rows(-6, 0, 10);
        Glide glide = glide(area, 175.99, -35.5, 10, () -> area.row(-3, 11));
        assertTrue(glide.x() > 176.0, "nach dem Laden weiter: x " + glide.x());
        assertEquals(List.of(), glide.problems());
    }

    @Test
    void gapInThePlayerRowDoesNotMoveTheEdgeOfTheFreecamRow() {
        Area area = new Area().rows(-6, 0, 9).missing(6, -3);
        Glide glide = glide(area, 159.99, -31.5, -1, () -> {
        });
        assertEquals(TICKS, glide.blockedX());
        assertEquals(159.99, glide.x(), 1.0E-9);
        assertEquals(List.of(), glide.problems());
    }

    @Test
    void freecamInAColumnThatIsNotLoadedIsReported() {
        Area area = new Area().rows(-6, 0, 10).row(-2, 11);
        assertNotNull(FreecamEdgeCheck.outside(area, PLAYER_CX, PLAYER_CZ, 176.28, -35.5));
        assertNotNull(FreecamEdgeCheck.outside(area, PLAYER_CX, PLAYER_CZ, 192.01, -31.0));
        assertNotNull(FreecamEdgeCheck.outside(area, PLAYER_CX, PLAYER_CZ, -96.5, -35.5));
        assertNull(FreecamEdgeCheck.outside(area, PLAYER_CX, PLAYER_CZ, 175.99, -35.5));
        assertNull(FreecamEdgeCheck.outside(area, PLAYER_CX, PLAYER_CZ, 191.99, -31.0));
    }

    @Test
    void reportNamesTheChunkAndTheEdgesOfBothRows() {
        Area area = new Area().rows(-6, 0, 10).row(-2, 11);
        assertEquals("x 192.01 z -31.00, Chunk 12/-2 nicht geladen, Grenze der Reihe -2 bei x 192, der Spieler-Reihe -3 bei x 176",
                FreecamEdgeCheck.outside(area, PLAYER_CX, PLAYER_CZ, 192.01, -31.0));
        assertEquals("Chunk-Reihe -3 mit Grenze x 176, Reihe -2 mit Grenze x 192", FreecamEdgeCheck.rows(area, PLAYER_CX, -35.5, -31.0));
        assertEquals("Chunk-Reihe -3 mit Grenze x 176", FreecamEdgeCheck.rows(area, PLAYER_CX, -35.5, -33.0));
    }

    @Test
    void positionThatIsNotFiniteIsReported() {
        Area area = new Area().rows(-6, 0, 10);
        assertNotNull(FreecamEdgeCheck.outside(area, PLAYER_CX, PLAYER_CZ, Double.NaN, -35.5));
        assertNotNull(FreecamEdgeCheck.outside(area, PLAYER_CX, PLAYER_CZ, 100.0, Double.POSITIVE_INFINITY));
    }
}
