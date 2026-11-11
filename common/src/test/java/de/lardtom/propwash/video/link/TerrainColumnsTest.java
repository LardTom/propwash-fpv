package de.lardtom.propwash.video.link;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainColumnsTest {
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 160;
    private static final Medium CARPET = new Medium(RfMaterial.CLOTH, new double[]{0.0, 0.0, 0.0, 1.0, 1.0 / 16.0, 1.0}, 1.0);
    private static final Medium SNOW_LAYER = new Medium(RfMaterial.SNOW, new double[]{0.0, 0.0, 0.0, 1.0, 0.25, 1.0}, 1.0);
    private static final Medium SNOW_BLOCK = Medium.full(RfMaterial.SNOW);
    private static final Medium ICE = new Medium(RfMaterial.ICE, null, 1.5 / 7.0);
    private static final Medium WATER = new Medium(RfMaterial.WATER, null, 0.5);
    private static final Medium GRASS = new Medium(RfMaterial.PLANT, new double[]{0.125, 0.0, 0.125, 0.875, 0.8125, 0.875}, 1.5 / 7.0);
    private static final Medium FENCE = new Medium(RfMaterial.TIMBER, new double[]{0.375, 0.0, 0.375, 0.625, 1.5, 0.625}, 1.0);

    @Test
    void newColumnsAreAirEverywhere() {
        TerrainColumns columns = new TerrainColumns();
        for (int lx = 0; lx < TerrainColumns.SIZE; lx++) {
            for (int lz = 0; lz < TerrainColumns.SIZE; lz++) {
                assertSame(Medium.AIR, columns.medium(lx, MIN_Y, lz));
                assertSame(Medium.AIR, columns.medium(lx, 0, lz));
                assertEquals(RfMaterial.AIR, columns.material(lx, lz));
                assertEquals(0, columns.canopy(lx, lz));
            }
        }
    }

    @Test
    void encodingKeepsTopCanopyAndEveryMaterial() {
        TerrainColumns columns = new TerrainColumns();
        RfMaterial[] materials = RfMaterial.values();
        for (int i = 0; i < materials.length; i++) {
            columns.set(i, 3, -64 + 17 * i, i * 15, materials[i]);
        }
        for (int i = 0; i < materials.length; i++) {
            assertEquals(-64 + 17 * i, columns.top(i, 3));
            assertEquals(i * 15, columns.canopy(i, 3));
            assertEquals(materials[i], columns.material(i, 3));
        }
        columns.set(15, 15, 2031, 200, RfMaterial.EARTH);
        assertEquals(2031, columns.top(15, 15));
        assertEquals(200, columns.canopy(15, 15));
        columns.set(15, 15, -2032, 300, RfMaterial.SAND);
        assertEquals(-2032, columns.top(15, 15));
        assertEquals(TerrainColumns.MAX_CANOPY, columns.canopy(15, 15));
        columns.set(15, 15, 40_000, -5, RfMaterial.SAND);
        assertEquals(Short.MAX_VALUE, columns.top(15, 15));
        assertEquals(0, columns.canopy(15, 15));
        assertEquals(RfMaterial.AIR, columns.material(0, 0));
        assertEquals(columns.material(2, 3), columns.material(16 + 2, 3));
    }

    @Test
    void mediumIsSolidUpToTopThenCanopyThenAir() {
        TerrainColumns columns = new TerrainColumns();
        columns.set(4, 9, 62, 5, RfMaterial.EARTH);
        Medium ground = columns.medium(4, 62, 9);
        assertEquals(RfMaterial.EARTH, ground.material());
        assertTrue(ground.fullBlock());
        assertEquals(1.0, ground.reflectivity(), 0.0);
        assertSame(ground, columns.medium(4, MIN_Y, 9));
        Medium leaves = columns.medium(4, 63, 9);
        assertEquals(RfMaterial.FOLIAGE, leaves.material());
        assertTrue(leaves.fullBlock());
        assertEquals(1.2 / 7.0, leaves.reflectivity(), 1.0E-12);
        assertSame(leaves, columns.medium(4, 67, 9));
        assertSame(Medium.AIR, columns.medium(4, 68, 9));
        assertSame(Medium.AIR, columns.medium(5, 62, 9));
        columns.set(5, 9, 40, 0, RfMaterial.WATER);
        assertEquals(0.5, columns.medium(5, 40, 9).reflectivity(), 1.0E-12);
        assertSame(Medium.AIR, columns.medium(5, 41, 9));
        columns.set(6, 9, 70, 3, RfMaterial.AIR);
        assertSame(Medium.AIR, columns.medium(6, 70, 9));
        assertEquals(RfMaterial.FOLIAGE, columns.medium(6, 73, 9).material());
    }

    @Test
    void thinBlocksArePassableHalfBlocksAreNot() {
        assertTrue(TerrainColumns.passable(Medium.AIR));
        assertTrue(TerrainColumns.passable(ScenarioWorld.LEAVES));
        assertTrue(TerrainColumns.passable(GRASS));
        assertTrue(TerrainColumns.passable(CARPET));
        assertTrue(TerrainColumns.passable(SNOW_LAYER));
        assertTrue(TerrainColumns.passable(FENCE));
        assertFalse(TerrainColumns.passable(ScenarioWorld.SLAB));
        assertFalse(TerrainColumns.passable(ScenarioWorld.STONE));
        assertFalse(TerrainColumns.passable(SNOW_BLOCK));
        assertFalse(TerrainColumns.passable(WATER));
    }

    @Test
    void groundIsFoundBelowPlantsCarpetsSnowLayersAndFences() {
        ScenarioWorld world = ScenarioWorld.flat().set(1, 0, 1, GRASS).set(2, 0, 1, CARPET).set(3, 0, 1, SNOW_LAYER)
                .set(4, 0, 1, FENCE).set(4, 1, 1, FENCE).set(5, 0, 1, ScenarioWorld.SLAB).set(6, 0, 1, ScenarioWorld.STONE);
        TerrainColumns columns = remember(world, 0, 0);
        assertColumn(columns, 0, 1, -1, 0, RfMaterial.EARTH);
        assertColumn(columns, 1, 1, -1, 0, RfMaterial.EARTH);
        assertColumn(columns, 2, 1, -1, 0, RfMaterial.EARTH);
        assertColumn(columns, 3, 1, -1, 0, RfMaterial.EARTH);
        assertColumn(columns, 4, 1, -1, 0, RfMaterial.EARTH);
        assertColumn(columns, 5, 1, 0, 0, RfMaterial.TIMBER);
        assertColumn(columns, 6, 1, 0, 0, RfMaterial.EARTH);
    }

    @Test
    void treeLeavesBecomeCanopyOverTheGroundAndTheTrunk() {
        ScenarioWorld world = ScenarioWorld.flat().oak(8, 8, 5);
        TerrainColumns columns = remember(world, 0, 0);
        assertColumn(columns, 8, 8, 4, 1, RfMaterial.TRUNK);
        assertColumn(columns, 9, 8, -1, 6, RfMaterial.EARTH);
        assertColumn(columns, 10, 8, -1, 4, RfMaterial.EARTH);
        assertColumn(columns, 12, 8, -1, 0, RfMaterial.EARTH);
        assertEquals(RfMaterial.FOLIAGE, columns.medium(10, 0, 8).material());
        assertSame(Medium.AIR, columns.medium(10, 4, 8));
    }

    @Test
    void snowAndIceOnTopTakeTheMaterialBelow() {
        ScenarioWorld world = ScenarioWorld.flat().set(1, 0, 1, SNOW_BLOCK).set(2, 0, 1, SNOW_BLOCK).set(2, 1, 1, SNOW_BLOCK)
                .fill(3, -3, 1, 3, -1, 1, WATER).set(3, 0, 1, ICE);
        for (int y = 0; y <= 5; y++) {
            world.set(4, y, 1, SNOW_BLOCK);
        }
        world.set(5, 0, 1, SNOW_BLOCK).set(5, -1, 1, ICE);
        TerrainColumns columns = remember(world, 0, 0);
        assertColumn(columns, 1, 1, 0, 0, RfMaterial.EARTH);
        assertColumn(columns, 2, 1, 1, 0, RfMaterial.EARTH);
        assertColumn(columns, 3, 1, 0, 0, RfMaterial.WATER);
        assertColumn(columns, 4, 1, 5, 0, RfMaterial.SNOW);
        assertColumn(columns, 5, 1, 0, 0, RfMaterial.EARTH);
    }

    @Test
    void highCanopyResumesAtTheGroundHeightmap() {
        ScenarioWorld world = ScenarioWorld.flat().fill(0, 50, 0, 15, 53, 15, ScenarioWorld.LEAVES).set(3, 0, 3, ScenarioWorld.STONE);
        TerrainColumns columns = remember(world, 0, 0);
        assertColumn(columns, 2, 2, -1, 54, RfMaterial.EARTH);
        assertColumn(columns, 3, 3, 0, 53, RfMaterial.EARTH);
        TerrainColumns blind = new TerrainColumns();
        blind.scanColumn(2, 2, 2, 2, 53, Integer.MIN_VALUE, MIN_Y, world);
        assertColumn(blind, 2, 2, 53 - TerrainColumns.MAX_DEPTH, TerrainColumns.MAX_DEPTH, RfMaterial.AIR);
        assertSame(Medium.AIR, blind.medium(2, -1, 2));
    }

    @Test
    void emptyColumnsStayAir() {
        ScenarioWorld world = new ScenarioWorld(false).set(1, 20, 1, ScenarioWorld.STONE);
        TerrainColumns columns = remember(world, 0, 0);
        assertColumn(columns, 1, 1, 20, 0, RfMaterial.EARTH);
        assertEquals(MIN_Y - 1, columns.top(0, 0));
        assertEquals(RfMaterial.AIR, columns.material(0, 0));
        assertSame(Medium.AIR, columns.medium(0, MIN_Y, 0));
    }

    @Test
    void unknownTerrainIsAirAndRememberedTerrainLosesLikeLoaded() {
        ScenarioWorld world = ScenarioWorld.flat()
                .ridge(60, 14, 12, -20, 20)
                .fill(100, 0, -15, 103, 19, 14, ScenarioWorld.STONE)
                .forest(130, 170, -12, 12, 5)
                .house(190, -4, 9, 9, 5, ScenarioWorld.COBBLE, true);
        RememberedWorld remembered = RememberedWorld.of(world, -2, 14, -3, 2);
        ObstructionTracer.World unknown = (x, y, z) -> Medium.AIR;
        double[][] paths = {
                {30.5, 4.5, 0.5, 90.5, 4.5, 0.5},
                {30.5, 10.5, 3.5, 90.5, 12.5, -2.5},
                {80.5, 10.5, 0.5, 125.5, 10.5, 0.5},
                {80.5, 3.5, -6.5, 125.5, 15.5, 6.5},
                {120.5, 6.5, 0.5, 180.5, 6.5, 0.5},
                {120.5, 4.5, 0.5, 180.5, 5.5, 0.5},
        };
        for (double[] p : paths) {
            double loaded = scan(world, p);
            double memory = scan(remembered, p);
            String label = String.format(java.util.Locale.ROOT, "%.1f/%.1f/%.1f → %.1f/%.1f/%.1f: geladen %.2f dB, gemerkt %.2f dB",
                    p[0], p[1], p[2], p[3], p[4], p[5], loaded, memory);
            assertTrue(loaded > 3.0, label);
            assertEquals(loaded, memory, 3.0, label);
            assertEquals(0.0, scan(unknown, p), 0.0, label);
        }
    }

    @Test
    void rememberedHouseCountsAsOneBlockOfItsRoofMaterial() {
        ScenarioWorld world = ScenarioWorld.flat().house(190, -4, 9, 9, 5, ScenarioWorld.COBBLE, true);
        RememberedWorld remembered = RememberedWorld.of(world, 10, 13, -1, 0);
        TerrainColumns columns = remembered.columns(TerrainCache.key(12, -1));
        assertColumn(columns, 0, 12, 5, 0, RfMaterial.TIMBER);
        assertColumn(columns, 1, 13, 6, 0, RfMaterial.TIMBER);
        double loaded = ObstructionTracer.traceDb(175.5, 2.5, 0.5, 215.5, 2.5, 0.5, world, ObstructionScan.MAX_LOSS_DB);
        double memory = ObstructionTracer.traceDb(175.5, 2.5, 0.5, 215.5, 2.5, 0.5, remembered, ObstructionScan.MAX_LOSS_DB);
        assertEquals(2.0 * (RfMaterial.MASONRY.dbPerMeter() + RfMaterial.MASONRY.dbPerCrossing()), loaded, 1.0E-9);
        assertEquals(RfMaterial.Wall.MAX_WALL_RUN_M * RfMaterial.TIMBER.dbPerMeter() + RfMaterial.TIMBER.dbPerCrossing(), memory, 1.0E-9);
        assertTrue(scan(remembered, new double[]{175.5, 2.5, 0.5, 215.5, 2.5, 0.5}) > 6.0);
    }

    @Test
    void groundPathsStayTheSameWhenTheyGrazeTheRememberedFloor() {
        ScenarioWorld world = ScenarioWorld.flat();
        RememberedWorld remembered = RememberedWorld.of(world, -1, 4, -1, 1);
        double[] grazing = {0.5, 0.2, 0.5, 60.5, 0.2, 0.5};
        assertEquals(scan(world, grazing), scan(remembered, grazing), 1.0E-9);
        double[] buried = {0.5, -0.5, 0.5, 60.5, -0.5, 0.5};
        assertEquals(scan(world, buried), scan(remembered, buried), 1.0E-9);
        assertNotNull(remembered.columns(TerrainCache.key(0, 0)));
    }

    private static double scan(ObstructionTracer.World world, double[] p) {
        ObstructionScan scan = new ObstructionScan();
        scan.start(p[0], p[1], p[2], p[3], p[4], p[5]);
        return scan.evaluate(world);
    }

    private static void assertColumn(TerrainColumns columns, int lx, int lz, int top, int canopy, RfMaterial material) {
        String at = "Säule " + lx + "/" + lz;
        assertEquals(top, columns.top(lx, lz), at + " top");
        assertEquals(canopy, columns.canopy(lx, lz), at + " canopy");
        assertEquals(material, columns.material(lx, lz), at + " material");
    }

    static TerrainColumns remember(ObstructionTracer.World world, int chunkX, int chunkZ) {
        int originX = chunkX * TerrainColumns.SIZE;
        int originZ = chunkZ * TerrainColumns.SIZE;
        return TerrainColumns.scan(originX, originZ, MIN_Y,
                (lx, lz) -> surface(world, originX + lx, originZ + lz, false),
                (lx, lz) -> surface(world, originX + lx, originZ + lz, true),
                world);
    }

    private static int surface(ObstructionTracer.World world, int x, int z, boolean noLeaves) {
        for (int y = MAX_Y; y >= MIN_Y; y--) {
            Medium medium = world.medium(x, y, z);
            if (medium.empty() || medium.material() == RfMaterial.PLANT || (noLeaves && medium.material() == RfMaterial.FOLIAGE)) {
                continue;
            }
            return y;
        }
        return MIN_Y - 1;
    }

    static final class RememberedWorld implements ObstructionTracer.World {
        private final Map<Long, TerrainColumns> chunks = new HashMap<>();

        static RememberedWorld of(ObstructionTracer.World world, int fromChunkX, int toChunkX, int fromChunkZ, int toChunkZ) {
            RememberedWorld remembered = new RememberedWorld();
            for (int cx = fromChunkX; cx <= toChunkX; cx++) {
                for (int cz = fromChunkZ; cz <= toChunkZ; cz++) {
                    remembered.chunks.put(TerrainCache.key(cx, cz), remember(world, cx, cz));
                }
            }
            return remembered;
        }

        TerrainColumns columns(long key) {
            return this.chunks.get(key);
        }

        @Override
        public Medium medium(int x, int y, int z) {
            if (y < MIN_Y) {
                return Medium.AIR;
            }
            TerrainColumns columns = this.chunks.get(TerrainCache.key(x >> 4, z >> 4));
            return columns == null ? Medium.AIR : columns.medium(x & 15, y, z & 15);
        }
    }
}
