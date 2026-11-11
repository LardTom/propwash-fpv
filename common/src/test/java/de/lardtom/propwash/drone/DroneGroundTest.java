package de.lardtom.propwash.drone;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneGroundTest {
    private static final double RANGE = 0.5;
    private static final double START = DroneGround.START;
    private static final VoxelShape FENCE = Shapes.box(0.375, 0.0, 0.375, 0.625, 1.5, 0.625);
    private static final VoxelShape WALL = Shapes.box(0.25, 0.0, 0.25, 0.75, 1.5, 0.75);
    private static final VoxelShape SLAB = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);
    private static final VoxelShape STAIRS = Shapes.or(SLAB, Shapes.box(0.0, 0.5, 0.0, 1.0, 1.0, 0.5));
    private static final VoxelShape CARPET = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.0625, 1.0);

    private static final class World {
        final Map<BlockPos, VoxelShape> shapes = new HashMap<>();
        int lookups;

        World with(int x, int y, int z, VoxelShape shape) {
            this.shapes.put(new BlockPos(x, y, z), shape);
            return this;
        }

        double top(double x, double bottom, double z) {
            return DroneGround.blockTop(x, bottom + START, z, RANGE, pos -> {
                this.lookups++;
                return this.shapes.getOrDefault(pos.immutable(), Shapes.empty());
            }, new BlockPos.MutableBlockPos());
        }
    }

    @Test
    void aFullBlockCarriesAtItsTop() {
        World w = new World().with(0, 64, 0, Shapes.block());
        assertEquals(65.0, w.top(0.5, 65.0, 0.5), 1.0e-9);
        assertEquals(65.0, w.top(0.01, 65.0, 0.99), 1.0e-9);
    }

    @Test
    void fencesAndWallsCarryAtOneAndAHalfBlocksFromTheBlockBelow() {
        World fence = new World().with(0, 64, 0, FENCE);
        assertEquals(65.5, fence.top(0.5, 65.5, 0.5), 1.0e-9, "Zaunpfosten");
        assertEquals(65.5, fence.top(0.6, 65.9, 0.4), 1.0e-9, "Zaunpfosten, Unterkante 40 cm darüber");
        assertEquals(Double.NEGATIVE_INFINITY, fence.top(0.2, 65.5, 0.5), "neben dem Pfosten");
        assertEquals(Double.NEGATIVE_INFINITY, fence.top(0.5, 65.5, 0.7), "neben dem Pfosten");
        World wall = new World().with(0, 64, 0, WALL);
        assertEquals(65.5, wall.top(0.3, 65.5, 0.7), 1.0e-9, "Mauer");
        assertEquals(Double.NEGATIVE_INFINITY, wall.top(0.8, 65.5, 0.5), "neben der Mauer");
    }

    @Test
    void theScanReachesOneBlockBelowTheRay() {
        double start = 65.5 + START;
        assertTrue(DroneGround.TALL_SHAPE_REACH >= 1);
        World fence = new World().with(0, 64, 0, FENCE);
        fence.top(0.5, 65.5, 0.5);
        assertEquals(65, (int) Math.floor(start - RANGE), "der Strahl selbst bleibt im Block über dem Zaun");
        assertEquals(2, fence.lookups, "Block des Strahls und der Block darunter");
    }

    @Test
    void slabsAndStairsCarryAtTheirOwnHeight() {
        World slab = new World().with(0, 65, 0, SLAB).with(0, 64, 0, Shapes.block());
        assertEquals(65.5, slab.top(0.5, 65.5, 0.5), 1.0e-9);
        World stairs = new World().with(0, 65, 0, STAIRS).with(0, 64, 0, Shapes.block());
        assertEquals(66.0, stairs.top(0.5, 66.0, 0.25), 1.0e-9, "obere Stufe");
        assertEquals(Double.NEGATIVE_INFINITY, stairs.top(0.5, 66.0, 0.75), "untere Stufe liegt 0,5 tiefer, außerhalb der Reichweite");
        assertEquals(65.5, stairs.top(0.5, 65.5, 0.75), 1.0e-9, "auf der unteren Stufe");
    }

    @Test
    void theHighestSurfaceInTheColumnWins() {
        World w = new World().with(0, 65, 0, CARPET).with(0, 64, 0, FENCE);
        assertEquals(65.5, w.top(0.5, 65.5, 0.5), 1.0e-9);
        World stacked = new World().with(0, 65, 0, SLAB).with(0, 64, 0, Shapes.block());
        assertEquals(65.5, stacked.top(0.5, 65.5, 0.5), 1.0e-9);
    }

    @Test
    void nothingWithinReachIsFree() {
        World w = new World().with(0, 63, 0, Shapes.block());
        assertEquals(Double.NEGATIVE_INFINITY, w.top(0.5, 65.0, 0.5));
        assertEquals(Double.NEGATIVE_INFINITY, new World().top(0.5, 65.0, 0.5));
    }

    @Test
    void startingInsideACollisionShapeIsUnknown() {
        World fence = new World().with(0, 64, 0, FENCE);
        assertTrue(Double.isNaN(fence.top(0.5, 65.3, 0.5)), "Unterkante im oberen Teil des Zauns");
        World block = new World().with(0, 65, 0, Shapes.block());
        assertTrue(Double.isNaN(block.top(0.5, 65.5, 0.5)), "Unterkante in einem Block");
    }
}
