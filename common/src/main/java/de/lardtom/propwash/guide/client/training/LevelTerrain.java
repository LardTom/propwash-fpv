package de.lardtom.propwash.guide.client.training;

import de.lardtom.propwash.guide.training.Placement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

final class LevelTerrain implements Placement.Terrain {
    private final Level level;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private int cachedX = Integer.MIN_VALUE;
    private int cachedY = Integer.MIN_VALUE;
    private int cachedZ = Integer.MIN_VALUE;
    private double low = Double.NaN;
    private double high = Double.NaN;

    LevelTerrain(Level level) {
        this.level = level;
    }

    @Override
    public boolean loaded(int x, int z) {
        return this.level.hasChunkAt(x, z);
    }

    @Override
    public double solidBottom(int x, int y, int z) {
        this.probe(x, y, z);
        return this.low;
    }

    @Override
    public double solidTop(int x, int y, int z) {
        this.probe(x, y, z);
        return this.high;
    }

    private void probe(int x, int y, int z) {
        if (x == this.cachedX && y == this.cachedY && z == this.cachedZ) {
            return;
        }
        this.cachedX = x;
        this.cachedY = y;
        this.cachedZ = z;
        this.low = Double.NaN;
        this.high = Double.NaN;
        if (this.level.isOutsideBuildHeight(y)) {
            return;
        }
        this.pos.set(x, y, z);
        BlockState state = this.level.getBlockState(this.pos);
        if (!state.getFluidState().isEmpty()) {
            this.low = 0.0;
            this.high = Math.max(0.1, state.getFluidState().getHeight(this.level, this.pos));
            return;
        }
        VoxelShape shape = state.getCollisionShape(this.level, this.pos);
        if (shape.isEmpty()) {
            return;
        }
        this.low = shape.min(Direction.Axis.Y);
        this.high = shape.max(Direction.Axis.Y);
    }
}
