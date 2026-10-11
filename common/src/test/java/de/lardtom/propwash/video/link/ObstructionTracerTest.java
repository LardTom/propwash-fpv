package de.lardtom.propwash.video.link;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObstructionTracerTest {
    private static final Medium BRICK = Medium.full(RfMaterial.MASONRY);
    private static final double PER_METER = RfMaterial.MASONRY.dbPerMeter();
    private static final double CROSSING = RfMaterial.MASONRY.dbPerCrossing();

    private static ObstructionTracer.World wall(int fromZ, int toZ, Medium medium) {
        return (x, y, z) -> z >= fromZ && z <= toZ && Math.abs(x) <= 5 && Math.abs(y) <= 5 ? medium : Medium.AIR;
    }

    @Test
    void emptyWorldHasNoLoss() {
        assertEquals(0.0, ObstructionTracer.traceDb(0.5, 0.5, 0.5, 100.5, 20.5, -40.5, (x, y, z) -> Medium.AIR, 60.0), 0.0);
    }

    @Test
    void lossScalesWithWallThicknessAndCountsTheSurfacesOnce() {
        double one = ObstructionTracer.traceDb(0.5, 0.5, 0.5, 0.5, 0.5, 20.5, wall(5, 5, BRICK), 1000.0);
        double three = ObstructionTracer.traceDb(0.5, 0.5, 0.5, 0.5, 0.5, 20.5, wall(5, 7, BRICK), 1000.0);
        assertEquals(PER_METER + CROSSING, one, 1.0E-9);
        assertEquals(3.0 * PER_METER + CROSSING, three, 1.0E-9);
    }

    @Test
    void separateWallsCountTheirSurfacesEach() {
        ObstructionTracer.World two = (x, y, z) -> (z == 3 || z == 6) ? BRICK : Medium.AIR;
        double loss = ObstructionTracer.traceDb(0.5, 0.5, 0.5, 0.5, 0.5, 20.5, two, 1000.0);
        assertEquals(2.0 * (PER_METER + CROSSING), loss, 1.0E-9);
    }

    @Test
    void directionDoesNotMatter() {
        double forward = ObstructionTracer.traceDb(0.3, 0.7, -3.2, 2.9, -1.4, 17.8, wall(4, 6, BRICK), 1000.0);
        double backward = ObstructionTracer.traceDb(2.9, -1.4, 17.8, 0.3, 0.7, -3.2, wall(4, 6, BRICK), 1000.0);
        assertEquals(forward, backward, 1.0E-9);
    }

    @Test
    void diagonalPathsCountTheLengthInsideBlocks() {
        double straight = ObstructionTracer.traceDb(0.5, 0.5, 0.0, 0.5, 0.5, 10.0, wall(5, 5, BRICK), 1000.0);
        double diagonal = ObstructionTracer.traceDb(0.0, 0.5, 0.0, 10.0, 0.5, 10.0, wall(5, 5, BRICK), 1000.0);
        assertEquals(PER_METER + CROSSING, straight, 1.0E-9);
        assertEquals(PER_METER * Math.sqrt(2.0) + CROSSING, diagonal, 1.0E-6);
    }

    @Test
    void partialBlocksOnlyCountTheirShape() {
        Medium slab = new Medium(RfMaterial.MASONRY, new double[]{0.0, 0.0, 0.0, 1.0, 0.5, 1.0}, 1.0);
        ObstructionTracer.World world = wall(5, 5, slab);
        double below = ObstructionTracer.traceDb(0.5, 0.25, 0.0, 0.5, 0.25, 10.0, world, 1000.0);
        double above = ObstructionTracer.traceDb(0.5, 0.75, 0.0, 0.5, 0.75, 10.0, world, 1000.0);
        double rising = ObstructionTracer.traceDb(0.5, -1.0, 4.0, 0.5, 1.0, 6.0, world, 1000.0);
        assertEquals(PER_METER + CROSSING, below, 1.0E-9);
        assertEquals(0.0, above, 0.0);
        assertEquals(PER_METER * 0.5 * Math.sqrt(2.0) + CROSSING, rising, 1.0E-6);
    }

    @Test
    void glassPanesCostOncePerPaneAndNotPerMeter() {
        Medium pane = Medium.full(RfMaterial.GLASS);
        double single = ObstructionTracer.traceDb(0.5, 0.5, 0.0, 0.5, 0.5, 10.0, wall(5, 5, pane), 1000.0);
        double thick = ObstructionTracer.traceDb(0.5, 0.5, 0.0, 0.5, 0.5, 10.0, wall(4, 6, pane), 1000.0);
        assertEquals(RfMaterial.GLASS.dbPerCrossing(), single, 1.0E-9);
        assertEquals(single, thick, 1.0E-9);
    }

    @Test
    void foliageSaturatesAlongTheRay() {
        Medium leaves = Medium.full(RfMaterial.FOLIAGE);
        double five = ObstructionTracer.traceDb(0.5, 0.5, 0.0, 0.5, 0.5, 20.0, wall(5, 9, leaves), 1000.0);
        double twenty = ObstructionTracer.traceDb(0.5, 0.5, 0.0, 0.5, 0.5, 40.0, wall(5, 24, leaves), 1000.0);
        assertEquals(FoliageLoss.db(5.0), five, 1.0E-9);
        assertEquals(FoliageLoss.db(20.0), twenty, 1.0E-9);
        assertTrue(twenty < 4.0 * five, "foliage loss grows slower than linear: " + five + " / " + twenty);
    }

    @Test
    void lossIsCapped() {
        assertEquals(25.0, ObstructionTracer.traceDb(0.5, 0.5, 0.5, 0.5, 0.5, 50.5, wall(2, 30, Medium.full(RfMaterial.EARTH)), 25.0), 0.0);
    }

    @Test
    void pathsThatMissTheWallAreClear() {
        double past = ObstructionTracer.traceDb(10.5, 0.5, 0.5, 10.5, 0.5, 30.5, wall(5, 10, BRICK), 1000.0);
        assertEquals(0.0, past, 0.0);
    }

    @Test
    void degenerateSegmentsAreClear() {
        ObstructionTracer.World solid = (x, y, z) -> BRICK;
        assertEquals(0.0, ObstructionTracer.traceDb(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, solid, 60.0), 0.0);
        assertEquals(0.0, ObstructionTracer.traceDb(0.0, 0.0, 0.0, Double.NaN, 0.0, 0.0, solid, 60.0), 0.0);
    }

    @Test
    void negativeCoordinatesUseFloorCells() {
        double loss = ObstructionTracer.traceDb(-0.5, 0.5, -10.5, -0.5, 0.5, 10.5,
                (x, y, z) -> x == -1 && z == -3 ? BRICK : Medium.AIR, 1000.0);
        assertEquals(PER_METER + CROSSING, loss, 1.0E-9);
    }

    @Test
    void polylinesKeepOneSurfaceAcrossTheirJoints() {
        RayLoss loss = new RayLoss();
        double[] points = {0.5, 0.5, 0.0, 0.5, 0.5, 5.5, 0.5, 0.5, 10.0};
        ObstructionTracer.trace(points, 3, wall(5, 5, BRICK), loss, 1000.0);
        assertEquals(PER_METER + CROSSING, loss.totalDb(1000.0), 1.0E-9);
        assertTrue(loss.cells() >= 10);
    }
}
