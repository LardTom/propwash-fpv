package de.lardtom.propwash.server;

import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

public final class ViewAnchors {
    private static final Map<ServerPlayer, Anchor> ANCHORS = new IdentityHashMap<>();

    private ViewAnchors() {
    }

    public static ChunkPos center(ServerPlayer player, ChunkPos original) {
        Anchor anchor = active(player);
        return anchor != null ? anchor.center() : original;
    }

    public static Vec3 position(ServerPlayer player, Vec3 original) {
        Anchor anchor = active(player);
        return anchor != null ? anchor.drone().position() : original;
    }

    public static int viewDistance(ServerPlayer player, int original) {
        Anchor anchor = active(player);
        return anchor != null ? Math.min(original, anchor.viewDistance()) : original;
    }

    public static boolean isAnchored(ServerPlayer player) {
        return active(player) != null;
    }

    public static @Nullable QuadcopterEntity anchorDrone(ServerPlayer player) {
        Anchor anchor = active(player);
        return anchor != null ? anchor.drone() : null;
    }

    public static int size() {
        return ANCHORS.size();
    }

    static void set(ServerPlayer player, QuadcopterEntity drone, int viewDistance, ChunkPos center) {
        Anchor current = ANCHORS.get(player);
        int distance = Math.max(StreamBudget.MIN_VIEW_DISTANCE, viewDistance);
        if (current == null || current.drone() != drone || current.viewDistance() != distance || !current.center().equals(center)) {
            ANCHORS.put(player, new Anchor(drone, distance, center));
        }
    }

    static boolean remove(ServerPlayer player) {
        return ANCHORS.remove(player) != null;
    }

    static void clear() {
        ANCHORS.clear();
    }

    private static @Nullable Anchor active(ServerPlayer player) {
        if (ANCHORS.isEmpty()) {
            return null;
        }
        Anchor anchor = ANCHORS.get(player);
        if (anchor == null || anchor.drone().isRemoved() || anchor.drone().level() != player.level()) {
            return null;
        }
        return anchor;
    }

    private record Anchor(QuadcopterEntity drone, int viewDistance, ChunkPos center) {
    }
}
