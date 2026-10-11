package de.lardtom.propwash.rescue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class VoxelWorld implements RescueWorld {
    static final int MIN_Y = -16;
    static final int MAX_Y = 160;

    private final Set<Long> blocks = new HashSet<>();
    private final Map<Long, Ground> kinds = new HashMap<>();
    private final Map<Long, Integer> surfaces = new HashMap<>();
    private final int groundTop;

    VoxelWorld(int groundTop) {
        this.groundTop = groundTop;
    }

    VoxelWorld fill(int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    this.blocks.add(key(x, y, z));
                }
            }
        }
        this.surfaces.clear();
        return this;
    }

    VoxelWorld mark(Ground kind, int x0, int y0, int z0, int x1, int y1, int z1) {
        boolean solid = solidKind(kind);
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    long k = key(x, y, z);
                    this.kinds.put(k, kind);
                    if (solid) {
                        this.blocks.add(k);
                    } else {
                        this.blocks.remove(k);
                    }
                }
            }
        }
        this.surfaces.clear();
        return this;
    }

    VoxelWorld clear(int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    this.blocks.remove(key(x, y, z));
                    this.kinds.remove(key(x, y, z));
                }
            }
        }
        this.surfaces.clear();
        return this;
    }

    @Override
    public boolean solid(int x, int y, int z) {
        if (y < MIN_Y) {
            return true;
        }
        if (y > MAX_Y) {
            return false;
        }
        long k = key(x, y, z);
        Ground kind = this.kinds.get(k);
        if (kind != null) {
            return solidKind(kind);
        }
        return y < this.groundTop || this.blocks.contains(k);
    }

    private static boolean solidKind(Ground kind) {
        return kind == Ground.SOLID || kind == Ground.FOLIAGE || kind == Ground.HAZARD;
    }

    @Override
    public int surfaceY(int x, int z) {
        long k = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        Integer cached = this.surfaces.get(k);
        if (cached != null) {
            return cached;
        }
        int surface = this.groundTop;
        for (int y = MAX_Y; y >= this.groundTop; y--) {
            long cell = key(x, y, z);
            if (this.blocks.contains(cell) || this.kinds.containsKey(cell)) {
                surface = y + 1;
                break;
            }
        }
        this.surfaces.put(k, surface);
        return surface;
    }

    @Override
    public Ground ground(int x, int y, int z) {
        Ground kind = this.kinds.get(key(x, y, z));
        if (kind != null) {
            return kind;
        }
        return this.solid(x, y, z) ? Ground.SOLID : Ground.NONE;
    }

    @Override
    public int minY() {
        return MIN_Y;
    }

    @Override
    public int maxY() {
        return MAX_Y;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }
}
