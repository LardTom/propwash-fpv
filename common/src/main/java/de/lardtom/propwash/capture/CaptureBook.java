package de.lardtom.propwash.capture;

import de.lardtom.propwash.api.drone.CameraChunkResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CaptureBook {
    private final Map<UUID, Map<UUID, Supply>> byPlayer = new LinkedHashMap<>();

    public static final class Supply {
        private final UUID player;
        private final UUID drone;
        private int entityId;
        private int requested;
        private int radius;
        private @Nullable CameraArea area;
        private boolean paused;

        Supply(UUID player, UUID drone, int entityId, int requested, int radius) {
            this.player = player;
            this.drone = drone;
            this.entityId = entityId;
            this.requested = requested;
            this.radius = radius;
        }

        public UUID player() {
            return this.player;
        }

        public UUID drone() {
            return this.drone;
        }

        public int entityId() {
            return this.entityId;
        }

        public int requested() {
            return this.requested;
        }

        public int radius() {
            return this.radius;
        }

        public @Nullable CameraArea area() {
            return this.area;
        }

        public boolean paused() {
            return this.paused;
        }

        public boolean supplying() {
            return this.area != null && !this.paused;
        }
    }

    public CameraChunkResult open(UUID player, UUID drone, int entityId, int requested, int ruleRadius, boolean overloaded, int budget) {
        int radius = CaptureRules.granted(requested, ruleRadius);
        if (radius == 0) {
            return CameraChunkResult.refused(CameraChunkResult.Status.RULE_OFF);
        }
        Map<UUID, Supply> own = this.byPlayer.get(player);
        Supply existing = own == null ? null : own.get(drone);
        if (existing != null) {
            long delta = CaptureRules.cost(radius) - CaptureRules.cost(existing.radius);
            if (delta > 0 && (overloaded || this.chunks() + delta > budget)) {
                return CameraChunkResult.refused(CameraChunkResult.Status.SERVER_BUSY);
            }
            existing.requested = requested;
            existing.radius = radius;
            existing.entityId = entityId;
            return new CameraChunkResult(CameraChunkResult.Status.UPDATED, radius);
        }
        CaptureRules.Admission admission = CaptureRules.admit(own == null ? 0 : own.size(), this.count(), this.chunks(),
                CaptureRules.cost(radius), budget, overloaded);
        if (admission == CaptureRules.Admission.TOO_MANY) {
            return CameraChunkResult.refused(CameraChunkResult.Status.TOO_MANY);
        }
        if (admission == CaptureRules.Admission.SERVER_BUSY) {
            return CameraChunkResult.refused(CameraChunkResult.Status.SERVER_BUSY);
        }
        this.byPlayer.computeIfAbsent(player, key -> new LinkedHashMap<>()).put(drone, new Supply(player, drone, entityId, requested, radius));
        return new CameraChunkResult(CameraChunkResult.Status.OPENED, radius);
    }

    public boolean close(UUID player, UUID drone) {
        Map<UUID, Supply> own = this.byPlayer.get(player);
        if (own == null || own.remove(drone) == null) {
            return false;
        }
        if (own.isEmpty()) {
            this.byPlayer.remove(player);
        }
        return true;
    }

    public int closeAll(UUID player) {
        Map<UUID, Supply> own = this.byPlayer.remove(player);
        return own == null ? 0 : own.size();
    }

    public @Nullable Supply get(UUID player, UUID drone) {
        Map<UUID, Supply> own = this.byPlayer.get(player);
        return own == null ? null : own.get(drone);
    }

    public List<Supply> supplies(UUID player) {
        Map<UUID, Supply> own = this.byPlayer.get(player);
        return own == null ? List.of() : new ArrayList<>(own.values());
    }

    public Set<UUID> players() {
        return Set.copyOf(this.byPlayer.keySet());
    }

    public boolean has(UUID player) {
        return this.byPlayer.containsKey(player);
    }

    public boolean isEmpty() {
        return this.byPlayer.isEmpty();
    }

    public int count() {
        int count = 0;
        for (Map<UUID, Supply> own : this.byPlayer.values()) {
            count += own.size();
        }
        return count;
    }

    public long chunks() {
        long chunks = 0;
        for (Map<UUID, Supply> own : this.byPlayer.values()) {
            for (Supply supply : own.values()) {
                chunks += CaptureRules.cost(supply.radius);
            }
        }
        return chunks;
    }

    public int resize(Supply supply, int ruleRadius) {
        supply.radius = CaptureRules.granted(supply.requested, ruleRadius);
        return supply.radius;
    }

    public void place(Supply supply, @Nullable CameraArea area, boolean paused) {
        supply.area = area;
        supply.paused = paused;
    }

    public void clear() {
        this.byPlayer.clear();
    }
}
