package de.lardtom.propwash.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

final class TicketLedger {
    static final long TIMEOUT_TICKS = 40L;
    static final int REFRESH_INTERVAL = 20;

    private final Map<Key, Integer> placed = new HashMap<>();
    private final Set<Key> wanted = new HashSet<>();

    void want(ServerLevel level, TicketType type, ChunkPos pos, int radius) {
        if (radius < 0) {
            return;
        }
        this.wanted.add(new Key(level, type, pos.pack(), radius));
    }

    void commit(int tick) {
        for (Key key : this.wanted) {
            Integer last = this.placed.get(key);
            if (last == null || tick - last >= REFRESH_INTERVAL) {
                key.level().getChunkSource().addTicketWithRadius(key.type(), ChunkPos.unpack(key.chunk()), key.radius());
                this.placed.put(key, tick);
            }
        }
        Iterator<Map.Entry<Key, Integer>> iterator = this.placed.entrySet().iterator();
        while (iterator.hasNext()) {
            Key key = iterator.next().getKey();
            if (!this.wanted.contains(key)) {
                key.level().getChunkSource().removeTicketWithRadius(key.type(), ChunkPos.unpack(key.chunk()), key.radius());
                iterator.remove();
            }
        }
        this.wanted.clear();
    }

    void forgetLevel(ServerLevel level) {
        this.placed.keySet().removeIf(key -> key.level() == level);
        this.wanted.removeIf(key -> key.level() == level);
    }

    void clear() {
        this.placed.clear();
        this.wanted.clear();
    }

    int size() {
        return this.placed.size();
    }

    int count(TicketType type) {
        int count = 0;
        for (Key key : this.placed.keySet()) {
            if (key.type() == type) {
                count++;
            }
        }
        return count;
    }

    private record Key(ServerLevel level, TicketType type, long chunk, int radius) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && key.level == this.level && key.type == this.type
                    && key.chunk == this.chunk && key.radius == this.radius;
        }

        @Override
        public int hashCode() {
            return (System.identityHashCode(this.level) * 31 + System.identityHashCode(this.type)) * 31
                    + Long.hashCode(this.chunk) * 17 + this.radius;
        }
    }
}
