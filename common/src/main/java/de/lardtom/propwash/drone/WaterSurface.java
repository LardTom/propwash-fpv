package de.lardtom.propwash.drone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

public final class WaterSurface {
    public static final int MAX_SCAN = 16;

    private WaterSurface() {
    }

    public static double relative(Level level, double x, double y, double z) {
        if (!level.hasChunk(SectionPos.posToSectionCoord(x), SectionPos.posToSectionCoord(z))) {
            return Double.NEGATIVE_INFINITY;
        }
        BlockPos pos = BlockPos.containing(x, y, z);
        if (level.isOutsideBuildHeight(pos)) {
            return Double.NEGATIVE_INFINITY;
        }
        if (!water(level.getFluidState(pos))) {
            double cauldron = DroneFluids.cauldronWaterSurface(level.getBlockState(pos), pos, x, y, z);
            if (cauldron > Double.NEGATIVE_INFINITY) {
                return cauldron;
            }
            BlockPos below = pos.below();
            FluidState under = level.getFluidState(below);
            if (water(under)) {
                return below.getY() + under.getHeight(level, below) - y;
            }
            return DroneFluids.cauldronWaterSurface(level.getBlockState(below), below, x, y, z);
        }
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 0; i < MAX_SCAN; i++) {
            FluidState here = level.getFluidState(cursor);
            BlockPos above = cursor.above();
            if (level.isOutsideBuildHeight(above) || !water(level.getFluidState(above))) {
                return cursor.getY() + here.getHeight(level, cursor) - y;
            }
            cursor.move(0, 1, 0);
        }
        return cursor.getY() - y;
    }

    private static boolean water(FluidState state) {
        return !state.isEmpty() && state.is(FluidTags.WATER);
    }
}
