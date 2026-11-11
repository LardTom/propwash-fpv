package de.lardtom.propwash.rescue;

import de.lardtom.propwash.barrier.DroneBarriers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RescueBlocks {
    private static final double TOP_LAYER = 1.0E-3;

    private RescueBlocks() {
    }

    public static Ground classify(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return Ground.NONE;
        }
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            return fluid.is(FluidTags.LAVA) ? Ground.LAVA : Ground.WATER;
        }
        if (state.is(BlockTags.LEAVES)) {
            return Ground.FOLIAGE;
        }
        if (hazard(state)) {
            return Ground.HAZARD;
        }
        return DroneBarriers.droneShape(state, level, pos).isEmpty() ? Ground.NONE : Ground.SOLID;
    }

    public static double top(BlockGetter level, BlockPos pos, BlockState state) {
        VoxelShape shape = DroneBarriers.droneShape(state, level, pos);
        if (shape.isEmpty()) {
            return Double.NaN;
        }
        double top = shape.max(Direction.Axis.Y);
        if (top > 1.0 + 1.0E-6) {
            return Double.NaN;
        }
        VoxelShape face = Shapes.box(0.0, Math.max(0.0, top - TOP_LAYER), 0.0, 1.0, top, 1.0);
        return Shapes.joinIsNotEmpty(face, shape, BooleanOp.ONLY_FIRST) ? Double.NaN : top;
    }

    static boolean hazard(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POINTED_DRIPSTONE);
    }
}
