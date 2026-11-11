package de.lardtom.propwash.drone;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class LoadedDrones {
    private static final Map<ServerLevel, Entry> LEVELS = new WeakHashMap<>();

    private LoadedDrones() {
    }

    static synchronized void track(QuadcopterEntity drone, boolean loaded) {
        if (!(drone.level() instanceof ServerLevel level)) {
            return;
        }
        Entry entry = LEVELS.get(level);
        if (loaded) {
            if (entry == null) {
                entry = new Entry();
                LEVELS.put(level, entry);
            }
            if (entry.drones.add(drone)) {
                entry.snapshot = null;
            }
        } else if (entry != null && entry.drones.remove(drone)) {
            entry.snapshot = null;
        }
    }

    public static synchronized List<Entity> of(@Nullable ServerLevel level) {
        Entry entry = level == null ? null : LEVELS.get(level);
        if (entry == null) {
            return List.of();
        }
        if (entry.snapshot == null) {
            List<Entity> live = new ArrayList<>(entry.drones.size());
            for (QuadcopterEntity drone : entry.drones) {
                if (!drone.isRemoved() && drone.level() == level) {
                    live.add(drone);
                }
            }
            entry.snapshot = List.copyOf(live);
        }
        return entry.snapshot;
    }

    public static synchronized void forget(ServerLevel level) {
        LEVELS.remove(level);
    }

    public static synchronized void clear() {
        LEVELS.clear();
    }

    private static final class Entry {
        private final Set<QuadcopterEntity> drones = new LinkedHashSet<>();
        private @Nullable List<Entity> snapshot;
    }
}
