package de.lardtom.propwash.osd;

import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.operator.OperatorServer;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.server.SpectatorLinks;
import de.lardtom.propwash.video.SpectateServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SpectatorCountServer {
    private static final SpectatorCount.Outbox OUTBOX = new SpectatorCount.Outbox();
    private static final Map<QuadcopterEntity, Integer> COUNTS = new IdentityHashMap<>();
    private static boolean initialized;
    private static int sent;

    private SpectatorCountServer() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashNet.registerS2C(SpectatorCountS2C.TYPE, SpectatorCountS2C.CODEC);
        ServerBus.TICK_END.register(SpectatorCountServer::onTick);
        ServerBus.PLAYER_JOIN.register(player -> OUTBOX.forget(player.getUUID()));
        ServerBus.PLAYER_LEAVE.register(player -> OUTBOX.forget(player.getUUID()));
        ServerBus.STARTED.register(server -> reset());
        ServerBus.STOPPED.register(server -> reset());
    }

    public static int count(MinecraftServer server, QuadcopterEntity drone) {
        tally(server.getPlayerList().getPlayers());
        int count = SpectatorCount.of(COUNTS, drone);
        COUNTS.clear();
        return count;
    }

    public static int sent() {
        return sent;
    }

    private static void onTick(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        tally(players);
        for (ServerPlayer player : players) {
            UUID id = player.getUUID();
            QuadcopterEntity shown = SpectatorCount.shown(DronePiloting.pilotedBy(id).orElse(null), SpectateServer.watching(player),
                    SpectatorLinks.watched(player));
            if (shown == null) {
                continue;
            }
            int count = SpectatorCount.of(COUNTS, shown);
            if (OUTBOX.offer(id, shown.getId(), count) && PropwashNet.sendToPlayer(player, new SpectatorCountS2C(shown.getId(), count))) {
                sent++;
            }
        }
        COUNTS.clear();
    }

    private static void tally(List<ServerPlayer> players) {
        COUNTS.clear();
        for (ServerPlayer player : players) {
            SpectatorCount.add(COUNTS, player.getUUID(), SpectateServer.watching(player), SpectatorLinks.watched(player),
                    SpectatorCountServer::excluded);
        }
    }

    private static boolean excluded(QuadcopterEntity drone, UUID player) {
        return drone.isRemoved() || drone.isFlownBy(player)
                || OperatorServer.operator(drone).map(operator -> operator.getUUID().equals(player)).orElse(false);
    }

    private static void reset() {
        OUTBOX.clear();
        COUNTS.clear();
        sent = 0;
    }
}
