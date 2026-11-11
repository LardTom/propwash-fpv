package de.lardtom.propwash.rescue;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.server.AnchorRules;
import de.lardtom.propwash.server.GeneratedArea;
import de.lardtom.propwash.server.Lookahead;
import de.lardtom.propwash.server.RangeManager;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class FlightChunks {
    static final int RADIUS = 1;
    static final int MAX_REMEMBERED = 4096;
    static final int AHEAD_RADIUS = Lookahead.MIN_RADIUS;
    static final int MAX_AHEAD = 4;
    private static final int SIDE = 2 * RADIUS + 1;
    private final LongOpenHashSet generated = new LongOpenHashSet();
    private @Nullable ServerLevel level;
    private @Nullable ServerLevel remembered;
    private long center;
    private int kept;
    private boolean generating;
    private long ahead = Long.MIN_VALUE;

    void keep(QuadcopterEntity drone) {
        this.keep(drone, false);
    }

    void keep(QuadcopterEntity drone, boolean generate) {
        if (drone.isRemoved() || !(drone.level() instanceof ServerLevel current)) {
            return;
        }
        ChunkPos chunk = drone.chunkPosition();
        long packed = chunk.pack();
        if (current != this.level || packed != this.center || generate != this.generating) {
            this.level = current;
            this.center = packed;
            this.generating = generate;
            this.kept = 0;
        }
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int bit = 1 << ((dx + RADIUS) * SIDE + dz + RADIUS);
                int x = chunk.x() + dx;
                int z = chunk.z() + dz;
                if ((this.kept & bit) == 0 && (generate || this.keepable(current, x, z))) {
                    this.kept |= bit;
                }
                if ((this.kept & bit) != 0) {
                    RangeManager.keepSimulated(current, new ChunkPos(x, z));
                }
            }
        }
        if (generate) {
            this.ahead(current, drone);
        } else {
            this.ahead = Long.MIN_VALUE;
        }
    }

    private void ahead(ServerLevel level, QuadcopterEntity drone) {
        Vec3 motion = drone.getDeltaMovement();
        int chunks = aheadChunks(Math.hypot(motion.x, motion.z) * 20.0);
        if (chunks <= 0) {
            this.ahead = Long.MIN_VALUE;
            return;
        }
        long wanted = Lookahead.center(drone.getX(), drone.getZ(), motion.x, motion.z, chunks);
        if (this.ahead == Long.MIN_VALUE || !Lookahead.keepCenter(this.ahead, wanted)) {
            this.ahead = wanted;
        }
        RangeManager.keepAhead(level, new ChunkPos(Lookahead.unpackX(this.ahead), Lookahead.unpackZ(this.ahead)), AHEAD_RADIUS);
    }

    static int aheadChunks(double speed) {
        if (!(speed >= Lookahead.MIN_SPEED)) {
            return 0;
        }
        return (int) Math.min(MAX_AHEAD, Math.round(speed * Lookahead.SECONDS / 16.0));
    }

    boolean keepable(ServerLevel level, int chunkX, int chunkZ) {
        int r = AnchorRules.DRONE_TICKET_RADIUS;
        boolean all = true;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!this.generated(level, chunkX + dx, chunkZ + dz)) {
                    all = false;
                }
            }
        }
        return all;
    }

    private boolean generated(ServerLevel level, int chunkX, int chunkZ) {
        if (level != this.remembered || this.generated.size() > MAX_REMEMBERED) {
            this.remembered = level;
            this.generated.clear();
        }
        long key = ChunkPos.pack(chunkX, chunkZ);
        if (this.generated.contains(key)) {
            return true;
        }
        if (RangeManager.generatedStatus(level, chunkX, chunkZ, 0) != GeneratedArea.Status.GENERATED) {
            return false;
        }
        this.generated.add(key);
        return true;
    }
}
