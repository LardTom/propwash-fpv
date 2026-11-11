package de.lardtom.propwash.drone;

import de.lardtom.propwash.sim.Fluid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

public final class DroneFluids {
    private DroneFluids() {
    }

    public static Fluid at(Level level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!level.hasChunk(SectionPos.posToSectionCoord(x), SectionPos.posToSectionCoord(z)) || level.isOutsideBuildHeight(pos)) {
            return Fluid.NONE;
        }
        FluidState fluid = level.getFluidState(pos);
        if (!fluid.isEmpty() && y <= pos.getY() + fluid.getHeight(level, pos)) {
            return fluid.is(FluidTags.LAVA) ? Fluid.LAVA : Fluid.WATER;
        }
        BlockState state = level.getBlockState(pos);
        CauldronContent content = content(state);
        return content == null ? Fluid.NONE : content.fluidAt(fillLevel(state), x - pos.getX(), y - pos.getY(), z - pos.getZ());
    }

    public static double cauldronWaterSurface(BlockState state, BlockPos pos, double x, double y, double z) {
        CauldronContent content = content(state);
        return content == null ? Double.NEGATIVE_INFINITY
                : content.waterSurface(fillLevel(state), x - pos.getX(), y - pos.getY(), z - pos.getZ());
    }

    public static @Nullable CauldronContent content(BlockState state) {
        if (state.is(Blocks.WATER_CAULDRON)) {
            return CauldronContent.WATER;
        }
        if (state.is(Blocks.LAVA_CAULDRON)) {
            return CauldronContent.LAVA;
        }
        if (state.is(Blocks.POWDER_SNOW_CAULDRON)) {
            return CauldronContent.POWDER_SNOW;
        }
        return null;
    }

    public static int fillLevel(BlockState state) {
        return state.hasProperty(LayeredCauldronBlock.LEVEL) ? state.getValue(LayeredCauldronBlock.LEVEL) : CauldronContent.MAX_LEVEL;
    }
}
