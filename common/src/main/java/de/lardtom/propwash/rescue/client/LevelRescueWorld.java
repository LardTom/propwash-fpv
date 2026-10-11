package de.lardtom.propwash.rescue.client;

import de.lardtom.propwash.rescue.Ground;
import de.lardtom.propwash.rescue.RescueBlocks;
import de.lardtom.propwash.rescue.RescueWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

public final class LevelRescueWorld implements RescueWorld {
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private @Nullable Level level;
    private @Nullable LevelChunk chunk;
    private int chunkX = Integer.MIN_VALUE;
    private int chunkZ = Integer.MIN_VALUE;

    public LevelRescueWorld() {
    }

    public LevelRescueWorld bind(Level level) {
        if (this.level != level) {
            this.level = level;
            this.forgetChunk();
        }
        return this;
    }

    public void forgetChunk() {
        this.chunk = null;
        this.chunkX = Integer.MIN_VALUE;
        this.chunkZ = Integer.MIN_VALUE;
    }

    public void release() {
        this.level = null;
        this.forgetChunk();
    }

    private @Nullable LevelChunk chunkAt(int x, int z) {
        Level current = this.level;
        if (current == null) {
            return null;
        }
        int cx = x >> 4;
        int cz = z >> 4;
        if (cx != this.chunkX || cz != this.chunkZ || this.chunk == null) {
            this.chunk = current.getChunkSource().getChunkNow(cx, cz);
            this.chunkX = cx;
            this.chunkZ = cz;
        }
        return this.chunk;
    }

    @Override
    public boolean solid(int x, int y, int z) {
        Level current = this.level;
        if (current == null) {
            return false;
        }
        if (y < current.getMinY()) {
            return true;
        }
        if (y > current.getMaxY()) {
            return false;
        }
        LevelChunk c = this.chunkAt(x, z);
        if (c == null) {
            return false;
        }
        this.cursor.set(x, y, z);
        BlockState state = c.getBlockState(this.cursor);
        if (state.isAir()) {
            return false;
        }
        return !state.getCollisionShape(current, this.cursor).isEmpty();
    }

    @Override
    public int surfaceY(int x, int z) {
        LevelChunk c = this.chunkAt(x, z);
        if (c == null) {
            return UNKNOWN_SURFACE;
        }
        return c.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) + 1;
    }

    @Override
    public Ground ground(int x, int y, int z) {
        Level current = this.level;
        if (current == null) {
            return Ground.UNKNOWN;
        }
        if (y < current.getMinY()) {
            return Ground.SOLID;
        }
        if (y > current.getMaxY()) {
            return Ground.NONE;
        }
        LevelChunk c = this.chunkAt(x, z);
        if (c == null) {
            return Ground.UNKNOWN;
        }
        this.cursor.set(x, y, z);
        return RescueBlocks.classify(current, this.cursor, c.getBlockState(this.cursor));
    }

    @Override
    public int minY() {
        Level current = this.level;
        return current == null ? 0 : current.getMinY();
    }

    @Override
    public int maxY() {
        Level current = this.level;
        return current == null ? 0 : current.getMaxY();
    }
}
