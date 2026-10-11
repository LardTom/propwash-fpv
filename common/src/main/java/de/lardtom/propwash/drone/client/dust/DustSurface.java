package de.lardtom.propwash.drone.client.dust;

import de.lardtom.propwash.drone.dust.DustModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class DustSurface {
    public record Ground(DustModel.Surface surface, double top, DustModel.Material material, int color) {
        public static final Ground NONE = new Ground(DustModel.Surface.NONE, Double.NEGATIVE_INFINITY, DustModel.Material.HARD, 0);
    }

    private DustSurface() {
    }

    public static Ground probe(Level level, double x, double y, double z, double depth, BlockPos.MutableBlockPos cursor) {
        if (!(depth >= 0.0) || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return Ground.NONE;
        }
        double lowest = y - depth;
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int bottom = Mth.floor(lowest);
        for (int by = Mth.floor(y); by >= bottom; by--) {
            cursor.set(bx, by, bz);
            if (!level.isLoaded(cursor)) {
                return Ground.NONE;
            }
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) {
                continue;
            }
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                if (!fluid.is(FluidTags.WATER)) {
                    return Ground.NONE;
                }
                double top = by + fluid.getHeight(level, cursor);
                return top < lowest ? Ground.NONE : new Ground(DustModel.Surface.WATER, top, DustModel.Material.HARD, DustModel.SPRAY_COLOR);
            }
            if (state.is(BlockTags.LEAVES)) {
                return Ground.NONE;
            }
            double top;
            if (state.getBlock() instanceof SnowLayerBlock) {
                top = by + state.getValue(SnowLayerBlock.LAYERS) / 8.0;
            } else {
                VoxelShape collision = state.getCollisionShape(level, cursor);
                boolean snow = state.is(BlockTags.SNOW);
                if (collision.isEmpty() && !snow) {
                    continue;
                }
                VoxelShape outline = state.getShape(level, cursor);
                double collisionTop = collision.isEmpty() ? 0.0 : collision.max(Direction.Axis.Y);
                double outlineTop = outline.isEmpty() ? 0.0 : outline.max(Direction.Axis.Y);
                double height = Math.max(collisionTop, outlineTop);
                top = by + (height > 0.0 ? height : 1.0);
            }
            if (top < lowest) {
                return Ground.NONE;
            }
            DustModel.Material material = material(state);
            return new Ground(DustModel.Surface.SOLID, top, material, DustModel.color(state.getMapColor(level, cursor).col, material));
        }
        return Ground.NONE;
    }

    public static DustModel.Material material(BlockState state) {
        if (state.is(BlockTags.SAND) || state.is(BlockTags.SNOW) || state.is(BlockTags.CONCRETE_POWDERS)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.SUSPICIOUS_GRAVEL)) {
            return DustModel.Material.LOOSE;
        }
        if (state.is(BlockTags.ICE)) {
            return DustModel.Material.SLICK;
        }
        if (state.is(BlockTags.DIRT) || state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH)) {
            return DustModel.Material.SOIL;
        }
        SoundType sound = state.getSoundType();
        if (sound == SoundType.SAND || sound == SoundType.SNOW || sound == SoundType.POWDER_SNOW || sound == SoundType.SUSPICIOUS_SAND
                || sound == SoundType.SUSPICIOUS_GRAVEL || sound == SoundType.SOUL_SAND) {
            return DustModel.Material.LOOSE;
        }
        if (sound == SoundType.GRAVEL || sound == SoundType.ROOTED_DIRT || sound == SoundType.SOUL_SOIL) {
            return DustModel.Material.SOIL;
        }
        if (sound == SoundType.GRASS || sound == SoundType.WET_GRASS || sound == SoundType.MOSS || sound == SoundType.MOSS_CARPET
                || sound == SoundType.NYLIUM) {
            return DustModel.Material.COVERED;
        }
        if (sound == SoundType.GLASS) {
            return DustModel.Material.SLICK;
        }
        return DustModel.Material.HARD;
    }
}
