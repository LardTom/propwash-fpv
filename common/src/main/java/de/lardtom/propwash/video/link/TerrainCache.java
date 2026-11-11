package de.lardtom.propwash.video.link;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Objects;

public final class TerrainCache {
    public static final int CAPACITY = 4096;

    private final int capacity;
    private final LinkedHashMap<Long, TerrainColumns> chunks;

    public TerrainCache() {
        this(CAPACITY);
    }

    public TerrainCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.capacity = capacity;
        this.chunks = new LinkedHashMap<>(Math.min(capacity, 1024), 0.75F, true);
    }

    public static long key(int chunkX, int chunkZ) {
        return chunkX & 0xFFFFFFFFL | (chunkZ & 0xFFFFFFFFL) << 32;
    }

    public void put(long key, TerrainColumns columns) {
        this.chunks.put(key, Objects.requireNonNull(columns, "columns"));
        while (this.chunks.size() > this.capacity) {
            this.chunks.pollFirstEntry();
        }
    }

    public @Nullable TerrainColumns get(long key) {
        return this.chunks.get(key);
    }

    public boolean contains(long key) {
        return this.chunks.containsKey(key);
    }

    public void clear() {
        this.chunks.clear();
    }

    public int size() {
        return this.chunks.size();
    }

    public int capacity() {
        return this.capacity;
    }
}
