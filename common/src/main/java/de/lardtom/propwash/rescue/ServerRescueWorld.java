package de.lardtom.propwash.rescue;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

final class ServerRescueWorld implements RescueWorld {
    private static final byte ALLOWED = 1;
    private static final byte DENIED = 2;

    @FunctionalInterface
    interface ChunkGate {
        boolean allowed(ServerLevel level, int chunkX, int chunkZ);
    }

    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final Long2ByteOpenHashMap gates = new Long2ByteOpenHashMap();
    private final ChunkGate gate;
    private @Nullable ServerLevel level;
    private @Nullable LevelChunk chunk;
    private int chunkX = Integer.MIN_VALUE;
    private int chunkZ = Integer.MIN_VALUE;
    private int exemptX = Integer.MIN_VALUE;
    private int exemptZ = Integer.MIN_VALUE;

    ServerRescueWorld(ChunkGate gate) {
        this.gate = gate;
    }

    ServerRescueWorld bind(ServerLevel level) {
        if (this.level != level) {
            this.level = level;
        }
        this.chunk = null;
        this.chunkX = Integer.MIN_VALUE;
        this.chunkZ = Integer.MIN_VALUE;
        this.gates.clear();
        return this;
    }

    void exempt(int chunkX, int chunkZ) {
        this.exemptX = chunkX;
        this.exemptZ = chunkZ;
        this.gates.clear();
        this.chunk = null;
        this.chunkX = Integer.MIN_VALUE;
        this.chunkZ = Integer.MIN_VALUE;
    }

    void release() {
        this.level = null;
        this.chunk = null;
        this.gates.clear();
    }

    boolean allowed(int chunkX, int chunkZ) {
        ServerLevel current = this.level;
        if (current == null) {
            return false;
        }
        long key = ChunkPos.pack(chunkX, chunkZ);
        byte state = this.gates.get(key);
        if (state == 0) {
            boolean own = chunkX == this.exemptX && chunkZ == this.exemptZ;
            state = own || this.gate.allowed(current, chunkX, chunkZ) ? ALLOWED : DENIED;
            this.gates.put(key, state);
        }
        return state == ALLOWED;
    }

    private @Nullable LevelChunk chunkAt(int x, int z) {
        ServerLevel current = this.level;
        if (current == null) {
            return null;
        }
        int cx = x >> 4;
        int cz = z >> 4;
        if (cx != this.chunkX || cz != this.chunkZ) {
            this.chunkX = cx;
            this.chunkZ = cz;
            this.chunk = this.allowed(cx, cz) ? current.getChunkSource().getChunkNow(cx, cz) : null;
        }
        return this.chunk;
    }

    @Override
    public boolean solid(int x, int y, int z) {
        ServerLevel current = this.level;
        if (current == null) {
            return true;
        }
        if (y < current.getMinY()) {
            return true;
        }
        if (y > current.getMaxY()) {
            return false;
        }
        LevelChunk c = this.chunkAt(x, z);
        if (c == null) {
            return true;
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
        ServerLevel current = this.level;
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
        ServerLevel current = this.level;
        return current == null ? 0 : current.getMinY();
    }

    @Override
    public int maxY() {
        ServerLevel current = this.level;
        return current == null ? 0 : current.getMaxY();
    }
}
