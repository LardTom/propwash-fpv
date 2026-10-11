package de.lardtom.propwash.rescue;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public final class RescueBlocks {
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
        return state.getCollisionShape(level, pos).isEmpty() ? Ground.NONE : Ground.SOLID;
    }

    static boolean hazard(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POINTED_DRIPSTONE);
    }
}
