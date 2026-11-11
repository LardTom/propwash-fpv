package de.lardtom.propwash.rx;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class RxIndex {
    private static final Map<Level, RxIndex> LEVELS = Collections.synchronizedMap(new WeakHashMap<>());

    private final Long2ObjectOpenHashMap<RxBlockEntity> byPos = new Long2ObjectOpenHashMap<>();
    private final Map<UUID, Long2ObjectLinkedOpenHashMap<RxBlockEntity>> byOwner = new HashMap<>();
    private int version;

    public static RxIndex of(Level level) {
        return LEVELS.computeIfAbsent(level, key -> new RxIndex());
    }

    public static @Nullable RxIndex existing(@Nullable Level level) {
        return level == null ? null : LEVELS.get(level);
    }

    public synchronized void put(RxBlockEntity entity) {
        long key = entity.getBlockPos().asLong();
        RxBlockEntity previous = this.byPos.put(key, entity);
        if (previous != null) {
            this.unlinkOwner(key, previous);
        }
        UUID owner = entity.settings().owner();
        if (owner != null) {
            this.byOwner.computeIfAbsent(owner, id -> new Long2ObjectLinkedOpenHashMap<>()).put(key, entity);
        }
        this.version++;
    }

    public synchronized void remove(RxBlockEntity entity) {
        long key = entity.getBlockPos().asLong();
        RxBlockEntity present = this.byPos.get(key);
        if (present != entity) {
            return;
        }
        this.byPos.remove(key);
        this.unlinkOwner(key, entity);
        this.version++;
    }

    private void unlinkOwner(long key, RxBlockEntity entity) {
        for (var it = this.byOwner.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (entry.getValue().get(key) == entity) {
                entry.getValue().remove(key);
            }
            if (entry.getValue().isEmpty()) {
                it.remove();
            }
        }
    }

    public synchronized int size() {
        return this.byPos.size();
    }

    public synchronized int version() {
        return this.version;
    }

    public synchronized boolean contains(BlockPos pos) {
        return this.byPos.containsKey(pos.asLong());
    }

    public synchronized @Nullable RxBlockEntity at(BlockPos pos) {
        return this.byPos.get(pos.asLong());
    }

    public synchronized Set<UUID> owners() {
        return Set.copyOf(this.byOwner.keySet());
    }

    public synchronized List<RxBlockEntity> ownedBy(UUID owner) {
        Long2ObjectLinkedOpenHashMap<RxBlockEntity> entries = this.byOwner.get(owner);
        return entries == null ? List.of() : new ArrayList<>(entries.values());
    }

    public synchronized boolean hasOwners() {
        return !this.byOwner.isEmpty();
    }
}
