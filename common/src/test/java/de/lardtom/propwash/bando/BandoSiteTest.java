package de.lardtom.propwash.bando;

import de.lardtom.propwash.bando.plan.BandoFamily;
import de.lardtom.propwash.bando.plan.BandoGenerator;
import de.lardtom.propwash.bando.plan.BandoPlan;
import de.lardtom.propwash.bando.plan.BandoSite;
import de.lardtom.propwash.bando.plan.BandoTransform;
import de.lardtom.propwash.bando.plan.BandoVariant;
import de.lardtom.propwash.bando.plan.Meta;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BandoSiteTest {
    private static final int SEA = 63;
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    private record Terrain(IntBinaryOperator surface, IntBinaryOperator floor, boolean biome) implements BandoSite.Sampler {
        @Override
        public int surface(int x, int z) {
            return this.surface.applyAsInt(x, z);
        }

        @Override
        public int floor(int x, int z) {
            return this.floor.applyAsInt(x, z);
        }

        @Override
        public boolean biome(int x, int y, int z) {
            return this.biome;
        }
    }

    private record Near(BandoSite.Sampler terrain, IntBinaryOperator away) implements BandoSite.Sampler {
        @Override
        public int surface(int x, int z) {
            return this.terrain.surface(x, z);
        }

        @Override
        public int floor(int x, int z) {
            return this.terrain.floor(x, z);
        }

        @Override
        public boolean biome(int x, int y, int z) {
            return this.terrain.biome(x, y, z);
        }

        @Override
        public boolean keepAway(int x, int y, int z) {
            return this.away.applyAsInt(x, z) != 0;
        }
    }

    private static Terrain land(IntBinaryOperator height) {
        return new Terrain(height, height, true);
    }

    @Test
    void transformRoundTripsAndRotatesLikeMinecraft() {
        BandoPlan plan = BandoGenerator.generate(BandoVariant.WAREHOUSE, 42L);
        for (int turns = 0; turns < 4; turns++) {
            BandoTransform t = BandoTransform.centered(plan, turns, 100, 64, -200);
            for (int x = 0; x < plan.sizeX(); x += 3) {
                for (int z = 0; z < plan.sizeZ(); z += 3) {
                    int wx = t.worldX(x, z);
                    int wz = t.worldZ(x, z);
                    assertTrue(wx >= t.originX() && wx < t.originX() + t.widthX() && wz >= t.originZ() && wz < t.originZ() + t.widthZ());
                    assertEquals(x, t.localX(wx, wz));
                    assertEquals(z, t.localZ(wx, wz));
                    for (int facing = 0; facing < 4; facing++) {
                        int nx = x + Meta.dx(facing);
                        int nz = z + Meta.dz(facing);
                        int world = Meta.rotateFacing(facing, turns);
                        assertEquals(Meta.dx(world), t.worldX(nx, nz) - wx, "Drehung " + turns + " Richtung " + facing);
                        assertEquals(Meta.dz(world), t.worldZ(nx, nz) - wz, "Drehung " + turns + " Richtung " + facing);
                    }
                }
            }
        }
        assertEquals(Meta.FACE_EAST, Meta.rotateFaces(Meta.FACE_NORTH, 1));
        assertEquals(Meta.FACE_NORTH | Meta.FACE_UP, Meta.rotateFaces(Meta.FACE_WEST | Meta.FACE_UP, 1));
        assertEquals(Meta.AXIS_Z, Meta.rotateAxis(Meta.AXIS_X, 1));
    }

    @Test
    void flatDryLandIsTaken() {
        BandoPlan plan = BandoGenerator.generate(BandoVariant.BARN, 1L);
        Optional<BandoTransform> site = BandoSite.place(plan, 0, 0, 0, SEA, MIN_Y, MAX_Y, land((x, z) -> 70));
        assertTrue(site.isPresent());
        assertEquals(70, site.get().worldY(plan.ground()));
    }

    @Test
    void floorSitsInTheSlopeNotOnTopOfIt() {
        BandoPlan plan = BandoGenerator.generate(BandoVariant.MINE_HEAD, 3L);
        IntBinaryOperator slope = (x, z) -> 90 + Math.max(-9, Math.min(9, x / 2));
        Optional<BandoTransform> site = BandoSite.place(plan, 0, 0, 0, SEA, MIN_Y, MAX_Y, land(slope));
        assertTrue(site.isPresent());
        BandoTransform t = site.get();
        int[] f = BandoSite.footprint(plan);
        List<Integer> heights = new ArrayList<>();
        for (int x = f[0]; x <= f[2]; x++) {
            for (int z = f[1]; z <= f[3]; z++) {
                heights.add(slope.applyAsInt(t.worldX(x, z), t.worldZ(x, z)));
            }
        }
        heights.sort(null);
        int floor = t.worldY(plan.ground());
        int median = heights.get(heights.size() / 2);
        assertTrue(floor <= median && floor >= median - 2, "Boden " + floor + ", Median des Geländes " + median);
        assertTrue(floor > heights.getFirst(), "Boden ganz unten im Hang");
        assertEquals(4, BandoSite.level(new int[]{9, 8, 7, 6, 5, 4, 3, 2, 1, 0}));
        assertEquals(70, BandoSite.level(new int[]{70, 70, 70, 70, 70}));
    }

    @Test
    void terrainIsShapedUpToTheFloor() {
        for (BandoVariant variant : BandoVariant.values()) {
            BandoPlan plan = BandoGenerator.generate(variant, 11L);
            int expected = variant.family() == BandoFamily.COAST ? 0 : plan.ground() + 1;
            assertEquals(expected, BandoSite.boxBottom(plan), variant + ": Unterkante der Bounding Box");
        }
    }

    @Test
    void steepWaterOrWrongBiomeIsRefused() {
        BandoPlan plan = BandoGenerator.generate(BandoVariant.FACTORY, 1L);
        assertFalse(BandoSite.place(plan, 0, 0, 0, SEA, MIN_Y, MAX_Y, land((x, z) -> 70 + x)).isPresent(), "zu steil");
        assertFalse(BandoSite.place(plan, 0, 0, 0, SEA, MIN_Y, MAX_Y, new Terrain((x, z) -> 62, (x, z) -> x > 5 ? 58 : 62, true)).isPresent(),
                "teils unter Wasser");
        assertFalse(BandoSite.place(plan, 0, 0, 0, SEA, MIN_Y, MAX_Y, new Terrain((x, z) -> 70, (x, z) -> 70, false)).isPresent(), "Biom");
        assertFalse(BandoSite.place(plan, 0, 0, 0, SEA, MIN_Y, MAX_Y, land((x, z) -> 60)).isPresent(), "unter dem Meeresspiegel");
    }

    @Test
    void mountainsAcceptMoreSlopeThanFields() {
        BandoPlan field = BandoGenerator.generate(BandoVariant.WAREHOUSE, 3L);
        BandoPlan mine = BandoGenerator.generate(BandoVariant.MINE_HEAD, 3L);
        IntBinaryOperator slope = (x, z) -> 90 + Math.max(-5, Math.min(5, x / 2));
        assertFalse(BandoSite.place(field, 0, 0, 0, SEA, MIN_Y, MAX_Y, land(slope)).isPresent());
        assertTrue(BandoSite.place(mine, 0, 0, 0, SEA, MIN_Y, MAX_Y, land(slope)).isPresent());
    }

    @Test
    void pierTurnsTowardsTheWater() {
        BandoPlan plan = BandoGenerator.generate(BandoVariant.PIER, 5L);
        Terrain shore = new Terrain((x, z) -> x < -6 ? 64 : 62, (x, z) -> x < -6 ? 64 : 62 - Math.min(8, (x + 6) / 2 + 1), true);
        Optional<BandoTransform> site = BandoSite.place(plan, 0, 0, 0, SEA, MIN_Y, MAX_Y, shore);
        assertTrue(site.isPresent(), "kein Steg an der Küste");
        BandoTransform t = site.get();
        int mid = plan.sizeX() / 2;
        assertTrue(t.worldX(mid, 0) < t.worldX(mid, plan.sizeZ() - 1), "Steg zeigt nicht aufs Wasser");
        assertEquals(SEA - 1, t.worldY(plan.ground()));
        Terrain inland = land((x, z) -> 70);
        assertFalse(BandoSite.place(plan, 0, 0, 0, SEA, MIN_Y, MAX_Y, inland).isPresent(), "Steg im Inland");
    }

    private static IntBinaryOperator raised(BandoTransform t, int x0, int z0, int x1, int z1, int height, IntBinaryOperator around) {
        return (x, z) -> {
            int lx = t.localX(x, z);
            int lz = t.localZ(x, z);
            return lx >= x0 && lx <= x1 && lz >= z0 && lz <= z1 ? height : around.applyAsInt(x, z);
        };
    }

    @Test
    void pierStaysOffCliffsAndRocks() {
        BandoPlan plan = BandoGenerator.generate(BandoVariant.PIER, 5L);
        IntBinaryOperator beach = (x, z) -> x < -6 ? 64 : 62;
        IntBinaryOperator seabed = (x, z) -> x < -6 ? 64 : 62 - Math.min(8, (x + 6) / 2 + 1);
        Optional<BandoTransform> clean = BandoSite.place(plan, 0, 0, 0, SEA, MIN_Y, MAX_Y, new Terrain(beach, seabed, true));
        assertTrue(clean.isPresent());
        BandoTransform t = clean.get();
        int[] f = BandoSite.footprint(plan);
        int mid = plan.sizeX() / 2;
        IntBinaryOperator cliff = raised(t, 0, 0, f[0] - 1, 6, 72, beach);
        IntBinaryOperator cliffFloor = raised(t, 0, 0, f[0] - 1, 6, 72, seabed);
        assertFalse(BandoSite.place(plan, 0, 0, 0, SEA, MIN_Y, MAX_Y, new Terrain(cliff, cliffFloor, true)).isPresent(), "Steg läuft in eine Steilküste");
        IntBinaryOperator rock = raised(t, mid - 1, 11, mid + 1, 13, 66, beach);
        IntBinaryOperator rockFloor = raised(t, mid - 1, 11, mid + 1, 13, 66, seabed);
        assertFalse(BandoSite.place(plan, 0, 0, 0, SEA, MIN_Y, MAX_Y, new Terrain(rock, rockFloor, true)).isPresent(), "Felsen im Steg");
        BandoPlan platform = BandoGenerator.generate(BandoVariant.PLATFORM, 7L);
        Optional<BandoTransform> open = BandoSite.place(platform, 0, 0, 0, SEA, MIN_Y, MAX_Y, new Terrain((x, z) -> 62, (x, z) -> 40, true));
        assertTrue(open.isPresent());
        int center = platform.sizeX() / 2;
        IntBinaryOperator island = raised(open.get(), center - 1, center + 2, center + 1, center + 5, 66, (x, z) -> 62);
        IntBinaryOperator islandFloor = raised(open.get(), center - 1, center + 2, center + 1, center + 5, 66, (x, z) -> 40);
        assertFalse(BandoSite.place(platform, 0, 0, 0, SEA, MIN_Y, MAX_Y, new Terrain(island, islandFloor, true)).isPresent(), "Insel unter der Bohrinsel");
    }

    @Test
    void bandosKeepAwayFromIceSpikesAndIcebergs() {
        BandoPlan plan = BandoGenerator.generate(BandoVariant.COLLAPSED_HALL, 4L);
        Terrain flat = land((x, z) -> 70);
        Optional<BandoTransform> free = BandoSite.place(plan, 0, 0, 0, SEA, MIN_Y, MAX_Y, new Near(flat, (x, z) -> 0));
        assertTrue(free.isPresent());
        BandoTransform t = free.get();
        int east = t.originX() + t.widthX() - 1;
        for (int gap = 1; gap <= BandoSite.KEEP_AWAY; gap++) {
            int border = east + gap;
            assertFalse(BandoSite.place(plan, 0, 0, 0, SEA, MIN_Y, MAX_Y, new Near(flat, (x, z) -> x >= border ? 1 : 0)).isPresent(),
                    "Eiszapfen-Biom " + gap + " Blöcke neben dem Bando");
        }
        int far = east + BandoSite.KEEP_AWAY + 1;
        assertTrue(BandoSite.place(plan, 0, 0, 0, SEA, MIN_Y, MAX_Y, new Near(flat, (x, z) -> x >= far ? 1 : 0)).isPresent(),
                "Abstand " + (BandoSite.KEEP_AWAY + 1) + " reicht");
        BandoPlan pier = BandoGenerator.generate(BandoVariant.PIER, 5L);
        Terrain shore = new Terrain((x, z) -> x < -6 ? 64 : 62, (x, z) -> x < -6 ? 64 : 62 - Math.min(8, (x + 6) / 2 + 1), true);
        Optional<BandoTransform> open = BandoSite.place(pier, 0, 0, 0, SEA, MIN_Y, MAX_Y, new Near(shore, (x, z) -> 0));
        assertTrue(open.isPresent());
        int south = open.get().originZ() + open.get().widthZ() - 1 + BandoSite.KEEP_AWAY / 2;
        assertFalse(BandoSite.place(pier, 0, 0, 0, SEA, MIN_Y, MAX_Y, new Near(shore, (x, z) -> z >= south ? 1 : 0)).isPresent(),
                "Eisberge im Nachbar-Ozean");
    }

    @Test
    void reachChunksFindEveryStartWhoseStructureCanTouchTheBando() {
        java.util.Random random = new java.util.Random(11L);
        int chunk = BandoSite.CHUNK;
        for (int round = 0; round < 300; round++) {
            int minX = random.nextInt(400) - 200;
            int minZ = random.nextInt(400) - 200;
            int maxX = minX + random.nextInt(64);
            int maxZ = minZ + random.nextInt(64);
            int reach = random.nextInt(49);
            int[] chunks = BandoSite.reachChunks(minX, minZ, maxX, maxZ, reach);
            for (int cx = Math.floorDiv(minX, chunk) - 6; cx <= Math.floorDiv(maxX, chunk) + 6; cx++) {
                for (int cz = Math.floorDiv(minZ, chunk) - 6; cz <= Math.floorDiv(maxZ, chunk) + 6; cz++) {
                    boolean touches = cx * chunk - reach <= maxX && cx * chunk + chunk - 1 + reach >= minX
                            && cz * chunk - reach <= maxZ && cz * chunk + chunk - 1 + reach >= minZ;
                    boolean scanned = cx >= chunks[0] && cx <= chunks[2] && cz >= chunks[1] && cz <= chunks[3];
                    assertEquals(touches, scanned, "Start-Chunk " + cx + "," + cz + " Reichweite " + reach + " Bando " + minX + "," + minZ + "–" + maxX + "," + maxZ);
                }
            }
        }
    }

    @Test
    void platformNeedsDeepWaterAndSwampSinks() {
        assertEquals(BandoVariant.PLATFORM, BandoSite.coastVariant(20));
        assertEquals(BandoVariant.PIER, BandoSite.coastVariant(2));
        BandoPlan platform = BandoGenerator.generate(BandoVariant.PLATFORM, 7L);
        assertTrue(BandoSite.place(platform, 0, 0, 0, SEA, MIN_Y, MAX_Y, new Terrain((x, z) -> 62, (x, z) -> 40, true)).isPresent());
        assertFalse(BandoSite.place(platform, 0, 0, 0, SEA, MIN_Y, MAX_Y, new Terrain((x, z) -> 62, (x, z) -> 59, true)).isPresent());
        BandoPlan swamp = BandoGenerator.generate(BandoVariant.SUNKEN_WAREHOUSE, 7L);
        Optional<BandoTransform> sunk = BandoSite.place(swamp, 0, 0, 0, SEA, MIN_Y, MAX_Y, new Terrain((x, z) -> 63, (x, z) -> 62, true));
        assertTrue(sunk.isPresent());
        assertEquals(SEA - 3, sunk.get().worldY(swamp.ground()));
        assertFalse(BandoSite.place(swamp, 0, 0, 0, SEA, MIN_Y, MAX_Y, land((x, z) -> 75)).isPresent(), "Sumpf-Halle auf einem Hügel");
    }

    @Test
    void shoreCenterNeedsTheBiomeInEveryCellTheBiomeLookupCanPick() {
        BandoTransform t = new BandoTransform(100, 60, 200, 1, 9, 30);
        int cx = t.originX() + t.widthX() / 2;
        int cz = t.originZ() + t.widthZ() / 2;
        assertTrue(BandoSite.centered(t, 62, new Terrain((x, z) -> 62, (x, z) -> 50, true)));
        assertFalse(BandoSite.centered(t, 62, new Terrain((x, z) -> 62, (x, z) -> 50, false)));
        for (int[] river : new int[][]{{cx + 2, cz}, {cx - 2, cz}, {cx, cz + 2}, {cx, cz - 2}}) {
            BandoSite.Sampler s = new BandoSite.Sampler() {
                public int surface(int x, int z) {
                    return 62;
                }

                public int floor(int x, int z) {
                    return 50;
                }

                public boolean biome(int x, int y, int z) {
                    return x >> 2 != river[0] >> 2 || z >> 2 != river[1] >> 2;
                }
            };
            assertFalse(BandoSite.centered(t, 62, s), "Flussfeld bei " + river[0] + "," + river[1]);
        }
        BandoSite.Sampler far = new BandoSite.Sampler() {
            public int surface(int x, int z) {
                return 62;
            }

            public int floor(int x, int z) {
                return 50;
            }

            public boolean biome(int x, int y, int z) {
                return Math.abs(x - cx) < 8 && Math.abs(z - cz) < 8;
            }
        };
        assertTrue(BandoSite.centered(t, 62, far));
    }
}
