package de.lardtom.propwash.rescue;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.server.EdgeFence;
import de.lardtom.propwash.server.EdgeMask;
import de.lardtom.propwash.sim.DroneSim;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

final class FlightFence {
    static final int REACH = 3;
    static final int REFRESH_TICKS = 5;
    static final double HOLD_BAND = 1.0;

    @FunctionalInterface
    interface Keepable {
        boolean test(int chunkX, int chunkZ);
    }

    private final Vector3d position = new Vector3d();
    private final Vector3d velocity = new Vector3d();
    private final double[] horizontal = new double[2];
    private EdgeFence fence = EdgeFence.NONE;
    private @Nullable ServerLevel level;
    private long center = Long.MIN_VALUE;
    private int age;
    private boolean atLimit;

    void update(ServerLevel level, QuadcopterEntity drone, FlightChunks chunks) {
        ChunkPos chunk = drone.chunkPosition();
        long packed = chunk.pack();
        if (level == this.level && packed == this.center && ++this.age < REFRESH_TICKS) {
            return;
        }
        this.level = level;
        this.center = packed;
        this.age = 0;
        this.fence = of(chunk.x(), chunk.z(), (x, z) -> chunks.keepable(level, x, z));
    }

    void use(EdgeFence fence) {
        this.fence = fence;
        this.level = null;
        this.center = Long.MIN_VALUE;
        this.age = 0;
    }

    boolean limit(DroneSim sim) {
        this.atLimit = false;
        if (this.fence.empty()) {
            return false;
        }
        sim.getPosition(this.position);
        sim.getVelocity(this.velocity);
        this.horizontal[0] = this.velocity.x;
        this.horizontal[1] = this.velocity.z;
        if (!this.limit(this.position.x, this.position.z, this.horizontal)) {
            return false;
        }
        sim.setHorizontalVelocity(this.horizontal[0], this.horizontal[1]);
        return true;
    }

    boolean limit(double x, double z, double[] horizontal) {
        this.atLimit = false;
        if (!this.fence.limit(x, z, horizontal)) {
            return false;
        }
        this.atLimit = this.fence.distance(x, z) <= EdgeFence.STOP_DISTANCE + HOLD_BAND;
        return true;
    }

    boolean atLimit() {
        return this.atLimit;
    }

    static EdgeFence of(int centerX, int centerZ, Keepable keepable) {
        return EdgeFence.of(mask(centerX, centerZ, keepable));
    }

    static EdgeMask mask(int centerX, int centerZ, Keepable keepable) {
        long[] bits = new long[3];
        for (int dz = -REACH; dz <= REACH; dz++) {
            for (int dx = -REACH; dx <= REACH; dx++) {
                if (!keepable.test(centerX + dx, centerZ + dz)) {
                    int index = (dz + EdgeMask.RADIUS) * EdgeMask.SIDE + dx + EdgeMask.RADIUS;
                    bits[index >>> 6] |= 1L << (index & 63);
                }
            }
        }
        return new EdgeMask(centerX, centerZ, bits[0], bits[1], bits[2]);
    }
}
