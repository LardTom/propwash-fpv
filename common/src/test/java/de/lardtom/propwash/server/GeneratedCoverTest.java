package de.lardtom.propwash.server;

import de.lardtom.propwash.server.GeneratedArea.Status;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCoverTest {
    private static void assertExact(GeneratedArea.Lookup lookup, int cx, int cz, int radius, List<GeneratedCover.Square> squares) {
        for (GeneratedCover.Square square : squares) {
            assertTrue(square.radius() >= 0, square.toString());
            for (int x = square.centerX() - square.radius(); x <= square.centerX() + square.radius(); x++) {
                for (int z = square.centerZ() - square.radius(); z <= square.centerZ() + square.radius(); z++) {
                    assertEquals(Status.GENERATED, lookup.status(x, z), "Ticket " + square + " lädt " + x + "/" + z);
                    assertTrue(Math.abs(x - cx) <= radius && Math.abs(z - cz) <= radius, "Ticket " + square + " reicht über den Bereich");
                }
            }
        }
        for (int x = cx - radius + 1; x <= cx + radius - 1; x++) {
            for (int z = cz - radius + 1; z <= cz + radius - 1; z++) {
                if (!GeneratedCover.interior(lookup, x, z)) {
                    continue;
                }
                boolean ticks = false;
                for (GeneratedCover.Square square : squares) {
                    ticks |= square.ticks(x, z);
                }
                assertTrue(ticks, "Erzeugter Chunk " + x + "/" + z + " wird nie gesendet (Loch)");
            }
        }
    }

    @Test
    void fullyGeneratedAreaIsOneTicket() {
        GeneratedArea.Lookup all = (x, z) -> Status.GENERATED;
        List<GeneratedCover.Square> squares = GeneratedCover.cover(all, 10, -4, 14);
        assertEquals(List.of(new GeneratedCover.Square(10, -4, 14)), squares);
    }

    @Test
    void straightEdgeLoadsEveryGeneratedChunkWithFewTickets() {
        GeneratedArea.Lookup edge = (x, z) -> x <= 3 ? Status.GENERATED : Status.MISSING;
        List<GeneratedCover.Square> squares = GeneratedCover.cover(edge, 0, 0, 14);
        assertExact(edge, 0, 0, 14, squares);
        assertTrue(squares.size() <= 40, "zu viele Tickets: " + squares.size());
        assertEquals(29 * 18, countGenerated(edge, 0, 0, 14));
    }

    @Test
    void unknownChunksAreNeverLoaded() {
        GeneratedArea.Lookup lookup = (x, z) -> x == 2 && z == 2 ? Status.UNKNOWN : z > 5 ? Status.MISSING : Status.GENERATED;
        List<GeneratedCover.Square> squares = GeneratedCover.cover(lookup, 0, 0, 8);
        assertExact(lookup, 0, 0, 8, squares);
        for (GeneratedCover.Square square : squares) {
            assertFalse(square.contains(2, 2));
        }
    }

    @Test
    void nothingGeneratedMeansNoTicket() {
        assertTrue(GeneratedCover.cover((x, z) -> Status.MISSING, 0, 0, 6).isEmpty());
        assertTrue(GeneratedCover.cover((x, z) -> Status.GENERATED, 0, 0, 0).isEmpty());
        assertTrue(GeneratedCover.cover((x, z) -> Status.GENERATED, 0, 0, -1).isEmpty());
    }

    @Test
    void randomIslandsAreCoveredExactly() {
        Random random = new Random(42L);
        for (int round = 0; round < 40; round++) {
            boolean[][] grid = new boolean[25][25];
            for (int i = 0; i < 6; i++) {
                int ox = random.nextInt(25);
                int oz = random.nextInt(25);
                int r = 1 + random.nextInt(7);
                for (int x = Math.max(0, ox - r); x <= Math.min(24, ox + r); x++) {
                    for (int z = Math.max(0, oz - r); z <= Math.min(24, oz + r); z++) {
                        grid[x][z] = true;
                    }
                }
            }
            GeneratedArea.Lookup lookup = (x, z) -> {
                int gx = x + 12;
                int gz = z + 12;
                return gx >= 0 && gz >= 0 && gx < 25 && gz < 25 && grid[gx][gz] ? Status.GENERATED : Status.MISSING;
            };
            List<GeneratedCover.Square> squares = GeneratedCover.cover(lookup, 0, 0, 12);
            assertExact(lookup, 0, 0, 12, squares);
            assertTrue(squares.size() <= GeneratedCover.MAX_SQUARES);
        }
    }

    @Test
    void edgeChunksNextToMissingOnesAreNeverTicking() {
        GeneratedArea.Lookup edge = (x, z) -> x <= 3 ? Status.GENERATED : Status.MISSING;
        for (GeneratedCover.Square square : GeneratedCover.cover(edge, 0, 0, 10)) {
            assertFalse(square.ticks(3, 0) && square.contains(4, 0));
            assertTrue(square.centerX() + square.radius() <= 3, square.toString());
        }
        assertFalse(GeneratedCover.interior(edge, 3, 0));
        assertTrue(GeneratedCover.interior(edge, 2, 0));
        assertTrue(GeneratedCover.cover((x, z) -> ((x + z) & 1) == 0 ? Status.GENERATED : Status.MISSING, 0, 0, 16).isEmpty());
    }

    @Test
    void manySmallIslandsAreCappedNearestFirst() {
        GeneratedArea.Lookup islands = (x, z) -> Math.floorMod(x, 4) != 3 && Math.floorMod(z, 4) != 3 ? Status.GENERATED : Status.MISSING;
        List<GeneratedCover.Square> squares = GeneratedCover.cover(islands, 1, 1, 34);
        assertEquals(GeneratedCover.MAX_SQUARES, squares.size());
        assertTrue(squares.stream().anyMatch(square -> square.ticks(1, 1)));
        for (GeneratedCover.Square square : squares) {
            assertEquals(1, square.radius());
            assertTrue(GeneratedCover.interior(islands, square.centerX(), square.centerZ()));
        }
    }

    private static int countGenerated(GeneratedArea.Lookup lookup, int cx, int cz, int radius) {
        int count = 0;
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                if (lookup.status(x, z) == Status.GENERATED) {
                    count++;
                }
            }
        }
        return count;
    }
}
