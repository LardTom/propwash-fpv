package de.lardtom.propwash.capture;

import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;

import java.util.function.Consumer;

public record CameraArea(int centerX, int centerZ, int radius) {
    public CameraArea {
        if (radius < 1) {
            throw new IllegalArgumentException("Radius " + radius);
        }
    }

    public static CameraArea of(ChunkPos center, int radius) {
        return new CameraArea(center.x(), center.z(), radius);
    }

    public ChunkPos center() {
        return new ChunkPos(this.centerX, this.centerZ);
    }

    public boolean contains(int chunkX, int chunkZ, boolean includeNeighbors) {
        return ChunkTrackingView.isWithinDistance(this.centerX, this.centerZ, this.radius, chunkX, chunkZ, includeNeighbors);
    }

    public int minX() {
        return this.centerX - this.radius - 1;
    }

    public int maxX() {
        return this.centerX + this.radius + 1;
    }

    public int minZ() {
        return this.centerZ - this.radius - 1;
    }

    public int maxZ() {
        return this.centerZ + this.radius + 1;
    }

    public int extentFrom(int chunkX, int chunkZ) {
        return Math.max(Math.abs(this.centerX - chunkX), Math.abs(this.centerZ - chunkZ)) + this.radius + 1;
    }

    public int distanceFrom(int chunkX, int chunkZ) {
        return Math.max(Math.abs(this.centerX - chunkX), Math.abs(this.centerZ - chunkZ));
    }

    public void forEach(Consumer<ChunkPos> consumer) {
        for (int x = this.minX(); x <= this.maxX(); x++) {
            for (int z = this.minZ(); z <= this.maxZ(); z++) {
                if (this.contains(x, z, true)) {
                    consumer.accept(new ChunkPos(x, z));
                }
            }
        }
    }

    public int chunks() {
        int count = 0;
        for (int x = this.minX(); x <= this.maxX(); x++) {
            for (int z = this.minZ(); z <= this.maxZ(); z++) {
                if (this.contains(x, z, true)) {
                    count++;
                }
            }
        }
        return count;
    }
}
