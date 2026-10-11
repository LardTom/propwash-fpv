package de.lardtom.propwash.server;

import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SpectatorLinks {
    private static final Map<ServerPlayer, Watch> WATCHING = new IdentityHashMap<>();

    private SpectatorLinks() {
    }

    public static void watch(ServerPlayer player, QuadcopterEntity drone) {
        Watch current = WATCHING.get(player);
        if (current != null && current.drone == drone) {
            current.released = false;
            return;
        }
        Watch next = new Watch(drone);
        next.anchored = current != null && current.anchored;
        WATCHING.put(player, next);
    }

    public static void unwatch(ServerPlayer player) {
        Watch watch = WATCHING.get(player);
        if (watch != null) {
            watch.released = true;
        }
    }

    public static @Nullable QuadcopterEntity watched(ServerPlayer player) {
        Watch watch = WATCHING.get(player);
        return watch == null || watch.released ? null : watch.drone;
    }

    public static boolean anchored(ServerPlayer player) {
        Watch watch = WATCHING.get(player);
        return watch != null && !watch.released && watch.anchored;
    }

    public static int viewDistance(ServerPlayer player) {
        Watch watch = WATCHING.get(player);
        return watch == null || watch.released ? 0 : watch.viewDistance;
    }

    public static int size() {
        return WATCHING.size();
    }

    static void apply(TicketLedger tickets, Set<ServerPlayer> moves, int tick) {
        if (WATCHING.isEmpty()) {
            return;
        }
        List<ServerPlayer> done = new ArrayList<>();
        for (Map.Entry<ServerPlayer, Watch> entry : WATCHING.entrySet()) {
            ServerPlayer player = entry.getKey();
            Watch watch = entry.getValue();
            QuadcopterEntity drone = watch.drone;
            boolean usable = !watch.released && !player.hasDisconnected() && !player.isRemoved() && !drone.isRemoved()
                    && drone.level() == player.level() && drone.level() instanceof ServerLevel;
            if (!usable) {
                if (watch.anchored) {
                    ViewAnchors.remove(player);
                    moves.add(player);
                }
                done.add(player);
                continue;
            }
            ServerLevel level = (ServerLevel) drone.level();
            PilotLink link = pilotLink(drone);
            int wanted = ServerRules.droneViewDistance(player);
            int distance = link != null && link.anchored() ? Math.min(wanted, Math.max(StreamBudget.MIN_VIEW_DISTANCE, link.viewDistance())) : wanted;
            ChunkPos droneChunk = drone.chunkPosition();
            ViewAnchors.set(player, drone, distance, droneChunk);
            watch.viewDistance = Math.max(StreamBudget.MIN_VIEW_DISTANCE, distance);
            boolean generates = link != null ? link.generatesChunks() : ServerRules.mayGenerateChunks(player);
            if (generates) {
                tickets.want(level, ServerModule.DRONE_VIEW_TICKET.get(), droneChunk, StreamBudget.viewTicketRadius(watch.viewDistance));
                tickets.want(level, ServerModule.DRONE_TICKET.get(), droneChunk, AnchorRules.DRONE_TICKET_RADIUS);
            }
            tickets.want(player.level(), ServerModule.PILOT_TICKET.get(), player.chunkPosition(), AnchorRules.PILOT_TICKET_RADIUS);
            long section = SectionPos.of(drone).asLong();
            if (!watch.anchored || section != watch.lastSection || tick - watch.lastMoveTick >= RangeManager.MOVE_INTERVAL) {
                watch.anchored = true;
                watch.lastSection = section;
                watch.lastMoveTick = tick;
                moves.add(player);
            }
        }
        for (ServerPlayer player : done) {
            WATCHING.remove(player);
        }
    }

    static void onPlayerLeave(ServerPlayer player) {
        if (WATCHING.remove(player) != null) {
            ViewAnchors.remove(player);
        }
    }

    static void clear() {
        WATCHING.clear();
    }

    private static @Nullable PilotLink pilotLink(QuadcopterEntity drone) {
        Optional<UUID> pilot = drone.pilot();
        if (pilot.isEmpty()) {
            return null;
        }
        PilotLink link = RangeManager.link(pilot.get()).orElse(null);
        return link != null && link.drone() == drone ? link : null;
    }

    private static final class Watch {
        private final QuadcopterEntity drone;
        private boolean released;
        private boolean anchored;
        private int viewDistance;
        private long lastSection = Long.MIN_VALUE;
        private int lastMoveTick = Integer.MIN_VALUE;

        private Watch(QuadcopterEntity drone) {
            this.drone = drone;
        }
    }
}
