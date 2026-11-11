package de.lardtom.propwash.video;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.video.net.PilotFeedC2S;
import de.lardtom.propwash.video.net.PilotFeedS2C;
import de.lardtom.propwash.video.net.PilotLookC2S;
import de.lardtom.propwash.video.net.PilotLookS2C;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PilotFeedServer {
    public static final int FEEDS_PER_SECOND = 30;
    public static final int FEED_BURST = 10;
    public static final int LOOKS_PER_SECOND = 2;
    public static final int LOOK_BURST = 4;
    private static final Map<UUID, Pilot> PILOTS = new HashMap<>();
    private static final Map<QuadcopterEntity, PilotLook> SIMULATED_LOOKS = new IdentityHashMap<>();
    private static boolean initialized;
    private static int received;
    private static int refused;
    private static int relayed;

    private PilotFeedServer() {
    }

    public record Stats(int feeds, int looks, int refused, long firstMillis, long lastMillis, @Nullable PilotFeed last, @Nullable PilotLook look) {
        public double feedsPerSecond() {
            long span = this.lastMillis - this.firstMillis;
            return this.feeds < 2 || span <= 0L ? 0.0 : (this.feeds - 1) * 1000.0 / span;
        }
    }

    private static final class Pilot {
        final FeedRate feedRate = new FeedRate(FEEDS_PER_SECOND, FEED_BURST);
        final FeedRate lookRate = new FeedRate(LOOKS_PER_SECOND, LOOK_BURST);
        @Nullable PilotLook look;
        @Nullable PilotFeed last;
        int feeds;
        int looks;
        int refused;
        long firstMillis;
        long lastMillis;
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashNet.registerS2C(PilotFeedS2C.TYPE, PilotFeedS2C.CODEC);
        PropwashNet.registerS2C(PilotLookS2C.TYPE, PilotLookS2C.CODEC);
        PropwashNet.registerC2S(PilotFeedC2S.TYPE, PilotFeedC2S.CODEC, PilotFeedServer::onFeed);
        PropwashNet.registerC2S(PilotLookC2S.TYPE, PilotLookC2S.CODEC, PilotFeedServer::onLook);
        ServerBus.PLAYER_LEAVE.register(player -> PILOTS.remove(player.getUUID()));
        ServerBus.STARTED.register(server -> reset());
        ServerBus.STOPPED.register(server -> reset());
    }

    private static void onFeed(PilotFeedC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        QuadcopterEntity drone = flown(player, payload.entityId());
        if (drone == null) {
            refused++;
            return;
        }
        Pilot pilot = PILOTS.computeIfAbsent(player.getUUID(), id -> new Pilot());
        long now = Util.getMillis();
        if (!pilot.feedRate.accept(now)) {
            pilot.refused++;
            refused++;
            return;
        }
        if (pilot.feeds == 0) {
            pilot.firstMillis = now;
        }
        pilot.feeds++;
        pilot.lastMillis = now;
        pilot.last = payload.feed();
        received++;
        relay(player.level().getServer(), drone, payload.feed());
    }

    private static void onLook(PilotLookC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        QuadcopterEntity drone = flown(player, payload.entityId());
        if (drone == null) {
            refused++;
            return;
        }
        Pilot pilot = PILOTS.computeIfAbsent(player.getUUID(), id -> new Pilot());
        if (!pilot.lookRate.accept(Util.getMillis())) {
            pilot.refused++;
            refused++;
            return;
        }
        pilot.looks++;
        pilot.look = payload.look();
        relayLook(player.level().getServer(), drone, payload.look());
    }

    private static @Nullable QuadcopterEntity flown(ServerPlayer player, int entityId) {
        QuadcopterEntity drone = DronePiloting.pilotedBy(player.getUUID()).orElse(null);
        return drone != null && drone.getId() == entityId ? drone : null;
    }

    public static void onWatchStarted(ServerPlayer watcher, QuadcopterEntity drone) {
        PilotLook look = lookOf(drone);
        if (look != null) {
            PropwashNet.sendToPlayer(watcher, new PilotLookS2C(drone.getId(), look));
        }
    }

    public static void simulate(QuadcopterEntity drone, PilotFeed feed) {
        MinecraftServer server = drone.level().getServer();
        if (server != null) {
            relay(server, drone, feed);
        }
    }

    public static void simulateLook(QuadcopterEntity drone, @Nullable PilotLook look) {
        if (look == null) {
            SIMULATED_LOOKS.remove(drone);
            return;
        }
        SIMULATED_LOOKS.put(drone, look);
        MinecraftServer server = drone.level().getServer();
        if (server != null) {
            relayLook(server, drone, look);
        }
    }

    public static Optional<Stats> stats(UUID pilot) {
        Pilot p = PILOTS.get(pilot);
        return p == null ? Optional.empty() : Optional.of(new Stats(p.feeds, p.looks, p.refused, p.firstMillis, p.lastMillis, p.last, p.look));
    }

    public static int received() {
        return received;
    }

    public static int refused() {
        return refused;
    }

    public static int relayed() {
        return relayed;
    }

    private static @Nullable PilotLook lookOf(QuadcopterEntity drone) {
        PilotLook simulated = SIMULATED_LOOKS.get(drone);
        if (simulated != null) {
            return simulated;
        }
        Optional<UUID> pilot = drone.pilot();
        Pilot p = pilot.map(PILOTS::get).orElse(null);
        return p == null ? null : p.look;
    }

    private static void relay(@Nullable MinecraftServer server, QuadcopterEntity drone, PilotFeed feed) {
        if (server == null) {
            return;
        }
        List<UUID> watchers = SpectateServer.watchersOf(drone);
        if (watchers.isEmpty()) {
            return;
        }
        PilotFeedS2C out = new PilotFeedS2C(drone.getId(), feed);
        for (UUID id : watchers) {
            ServerPlayer watcher = server.getPlayerList().getPlayer(id);
            if (watcher != null && PropwashNet.sendToPlayer(watcher, out)) {
                relayed++;
            }
        }
    }

    private static void relayLook(@Nullable MinecraftServer server, QuadcopterEntity drone, PilotLook look) {
        if (server == null) {
            return;
        }
        PilotLookS2C out = new PilotLookS2C(drone.getId(), look);
        for (UUID id : SpectateServer.watchersOf(drone)) {
            ServerPlayer watcher = server.getPlayerList().getPlayer(id);
            if (watcher != null) {
                PropwashNet.sendToPlayer(watcher, out);
            }
        }
        Constants.LOG.debug("OSD-Layout des Piloten von Drohne {} an Zuschauer geschickt", drone.getId());
    }

    private static void reset() {
        PILOTS.clear();
        SIMULATED_LOOKS.clear();
        received = 0;
        refused = 0;
        relayed = 0;
    }
}
