package de.lardtom.propwash.video.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.video.link.TerrainCache;
import de.lardtom.propwash.video.link.TerrainColumns;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;

public final class TerrainMemory {
    public static final TerrainMemory INSTANCE = new TerrainMemory();

    private final TerrainCache cache = new TerrainCache();
    private final ClientBlockLoss blocks = new ClientBlockLoss();
    private WeakReference<Level> level = new WeakReference<>(null);
    private boolean enabled = true;
    private boolean warned;
    private long scans;
    private long scanNanos;

    private TerrainMemory() {
    }

    public void remember(LevelChunk chunk) {
        long started = System.nanoTime();
        try {
            this.bind(chunk.getLevel());
            if (!chunk.hasPrimedHeightmap(Heightmap.Types.MOTION_BLOCKING)) {
                return;
            }
            boolean ground = chunk.hasPrimedHeightmap(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
            ChunkPos pos = chunk.getPos();
            TerrainColumns columns = TerrainColumns.scan(pos.getMinBlockX(), pos.getMinBlockZ(), chunk.getMinY(),
                    (lx, lz) -> chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, lx, lz),
                    (lx, lz) -> ground ? chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz) : Integer.MIN_VALUE,
                    (x, y, z) -> this.blocks.inChunk(chunk, x, y, z));
            this.cache.put(pos.pack(), columns);
            this.scans++;
            this.scanNanos += System.nanoTime() - started;
        } catch (RuntimeException e) {
            if (!this.warned) {
                this.warned = true;
                Constants.LOG.warn("Signal: Gelände des Chunks {} nicht gemerkt", chunk.getPos(), e);
            }
        }
    }

    public @Nullable TerrainColumns columns(Level level, int chunkX, int chunkZ) {
        if (!this.enabled || level != this.level.get()) {
            return null;
        }
        return this.cache.get(ChunkPos.pack(chunkX, chunkZ));
    }

    public boolean remembers(Level level, int chunkX, int chunkZ) {
        return level == this.level.get() && this.cache.contains(ChunkPos.pack(chunkX, chunkZ));
    }

    public int size() {
        return this.cache.size();
    }

    public long scans() {
        return this.scans;
    }

    public double averageScanMillis() {
        return this.scans == 0 ? 0.0 : this.scanNanos / 1.0E6 / this.scans;
    }

    boolean enabled() {
        return this.enabled;
    }

    void enabled(boolean on) {
        this.enabled = on;
    }

    private void bind(Level owner) {
        if (owner != this.level.get()) {
            this.cache.clear();
            this.level = new WeakReference<>(owner);
        }
        this.blocks.bind(owner);
    }
}
