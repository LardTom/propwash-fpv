package de.lardtom.propwash.video;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.server.SpectatorLinks;
import de.lardtom.propwash.video.net.ChannelListS2C;
import de.lardtom.propwash.video.net.SpectateC2S;
import de.lardtom.propwash.video.net.SpectateS2C;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SpectateServer {
    public static final int LIST_INTERVAL_TICKS = 10;

    private static final Map<QuadcopterEntity, Boolean> CANDIDATES = new LinkedHashMap<>();
    private static final Map<QuadcopterEntity, String> SIMULATED = new LinkedHashMap<>();
    private static final Map<UUID, QuadcopterEntity> WATCHERS = new HashMap<>();
    private static final Map<UUID, ChannelListS2C> SENT = new HashMap<>();
    private static boolean initialized;
    private static int timer;

    private SpectateServer() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashNet.registerS2C(ChannelListS2C.TYPE, ChannelListS2C.CODEC);
        PropwashNet.registerS2C(SpectateS2C.TYPE, SpectateS2C.CODEC);
        PropwashNet.registerC2S(SpectateC2S.TYPE, SpectateC2S.CODEC, SpectateServer::onRequest);
        QuadcopterEntity.PILOT_CHANGED.register(SpectateServer::onPilotChanged);
        ServerBus.TICK_END.register(SpectateServer::onTick);
        ServerBus.PLAYER_JOIN.register(player -> SENT.remove(player.getUUID()));
        ServerBus.PLAYER_LEAVE.register(SpectateServer::onLeave);
        ServerBus.STARTED.register(server -> reset());
        ServerBus.STOPPED.register(server -> reset());
    }

    public static boolean transmitting(QuadcopterEntity drone) {
        return !drone.isRemoved() && drone.hasBattery() && drone.batteryCharge() > 0.0F
                && (drone.pilot().isPresent() || SIMULATED.containsKey(drone));
    }

    public static List<QuadcopterEntity> transmitters() {
        List<QuadcopterEntity> out = new ArrayList<>();
        for (QuadcopterEntity drone : CANDIDATES.keySet()) {
            if (transmitting(drone)) {
                out.add(drone);
            }
        }
        return out;
    }

    public static int transmittersExcept(QuadcopterEntity drone, UUID pilot) {
        int count = 0;
        for (QuadcopterEntity other : CANDIDATES.keySet()) {
            if (other != drone && transmitting(other) && !other.isFlownBy(pilot)) {
                count++;
            }
        }
        return count;
    }

    public static @Nullable QuadcopterEntity watching(ServerPlayer player) {
        return WATCHERS.get(player.getUUID());
    }

    public static int watcherCount() {
        return WATCHERS.size();
    }

    public static void simulateTransmitter(QuadcopterEntity drone, @Nullable String pilotName) {
        if (pilotName == null) {
            SIMULATED.remove(drone);
            return;
        }
        SIMULATED.put(drone, pilotName);
        CANDIDATES.put(drone, Boolean.TRUE);
        assignChannels();
    }

    public static void refreshSoon() {
        timer = LIST_INTERVAL_TICKS;
        SENT.clear();
    }

    public static int channelOf(QuadcopterEntity drone) {
        return VtxSettings.channel(drone.tune().values(), drone.build().video().system);
    }

    public static SpectateS2C.@Nullable Result check(ServerPlayer player, QuadcopterEntity drone) {
        if (!ServerRules.spectatingAllowed()) {
            return SpectateS2C.Result.RULE_OFF;
        }
        if (!transmitting(drone)) {
            return SpectateS2C.Result.SIGNAL_ENDED;
        }
        if (drone.level() != player.level()) {
            return SpectateS2C.Result.OTHER_DIMENSION;
        }
        if (piloting(player)) {
            return SpectateS2C.Result.PILOTING;
        }
        Optional<VideoSystem> goggles = ContentIds.wornGogglesSystem(player);
        if (goggles.isEmpty()) {
            return SpectateS2C.Result.NO_GOGGLES;
        }
        if (!goggles.get().receives(drone.build().video().system)) {
            return SpectateS2C.Result.WRONG_SYSTEM;
        }
        return null;
    }

    private static boolean piloting(ServerPlayer player) {
        UUID id = player.getUUID();
        for (QuadcopterEntity drone : CANDIDATES.keySet()) {
            if (!drone.isRemoved() && drone.isFlownBy(id)) {
                return true;
            }
        }
        return false;
    }

    private static void onPilotChanged(QuadcopterEntity drone, Optional<UUID> previous, Optional<UUID> current) {
        if (current.isPresent()) {
            CANDIDATES.put(drone, Boolean.TRUE);
            assignChannels();
            timer = LIST_INTERVAL_TICKS;
        } else if (!SIMULATED.containsKey(drone)) {
            CANDIDATES.remove(drone);
            timer = LIST_INTERVAL_TICKS;
        }
    }

    private static void onRequest(SpectateC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        if (payload.drone().isEmpty()) {
            stop(player, SpectateS2C.Result.STOPPED);
            return;
        }
        UUID wanted = payload.drone().get();
        QuadcopterEntity target = null;
        for (QuadcopterEntity drone : CANDIDATES.keySet()) {
            if (!drone.isRemoved() && drone.getUUID().equals(wanted)) {
                target = drone;
                break;
            }
        }
        SpectateS2C.Result denied = target == null ? SpectateS2C.Result.NOT_FOUND : check(player, target);
        if (denied != null) {
            QuadcopterEntity old = WATCHERS.remove(player.getUUID());
            if (old != null) {
                SpectatorLinks.unwatch(player);
            }
            PropwashNet.sendToPlayer(player, SpectateS2C.ended(denied));
            return;
        }
        WATCHERS.put(player.getUUID(), target);
        SpectatorLinks.watch(player, target);
        PropwashNet.sendToPlayer(player, new SpectateS2C(SpectateS2C.Result.STARTED, target.getUUID(), target.getId()));
        Constants.LOG.debug("{} schaut Kanal {} von Drohne {}", player.getName().getString(), channelOf(target), target.getId());
    }

    private static void stop(ServerPlayer player, SpectateS2C.Result result) {
        WATCHERS.remove(player.getUUID());
        SpectatorLinks.unwatch(player);
        PropwashNet.sendToPlayer(player, SpectateS2C.ended(result));
    }

    private static void onLeave(ServerPlayer player) {
        WATCHERS.remove(player.getUUID());
        SENT.remove(player.getUUID());
    }

    private static void onTick(MinecraftServer server) {
        CANDIDATES.keySet().removeIf(drone -> drone.isRemoved() || (drone.pilot().isEmpty() && !SIMULATED.containsKey(drone)));
        SIMULATED.keySet().removeIf(QuadcopterEntity::isRemoved);
        validateWatchers(server);
        if (++timer >= LIST_INTERVAL_TICKS) {
            timer = 0;
            assignChannels();
            broadcast(server);
        }
    }

    private static void validateWatchers(MinecraftServer server) {
        if (WATCHERS.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, QuadcopterEntity> entry : new ArrayList<>(WATCHERS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                WATCHERS.remove(entry.getKey());
                continue;
            }
            SpectateS2C.Result problem = check(player, entry.getValue());
            if (problem != null) {
                stop(player, problem);
            } else {
                SpectatorLinks.watch(player, entry.getValue());
            }
        }
    }

    private static void assignChannels() {
        Map<VideoSystem, List<QuadcopterEntity>> groups = new EnumMap<>(VideoSystem.class);
        for (QuadcopterEntity drone : CANDIDATES.keySet()) {
            if (transmitting(drone)) {
                groups.computeIfAbsent(VideoChannels.tableOf(drone.build().video().system), k -> new ArrayList<>()).add(drone);
            }
        }
        for (Map.Entry<VideoSystem, List<QuadcopterEntity>> group : groups.entrySet()) {
            List<QuadcopterEntity> drones = group.getValue();
            List<Integer> preferred = new ArrayList<>(drones.size());
            List<Boolean> manual = new ArrayList<>(drones.size());
            for (QuadcopterEntity drone : drones) {
                Map<String, Float> tune = drone.tune().values();
                preferred.add(VtxSettings.channel(tune, group.getKey()));
                manual.add(VtxSettings.manual(tune));
            }
            List<Integer> picks = VideoChannels.assignAll(group.getKey(), preferred, manual);
            for (int i = 0; i < drones.size(); i++) {
                int pick = picks.get(i);
                if (pick != preferred.get(i) && pick >= 0) {
                    QuadcopterEntity drone = drones.get(i);
                    drone.setTune(new FlightTune(VtxSettings.with(drone.tune().values(), pick, false)));
                }
            }
        }
    }

    private static void broadcast(MinecraftServer server) {
        boolean allowed = ServerRules.spectatingAllowed();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            List<ChannelListS2C.Entry> entries = new ArrayList<>();
            for (QuadcopterEntity drone : CANDIDATES.keySet()) {
                if (transmitting(drone) && drone.level() == player.level()) {
                    entries.add(entry(server, drone));
                }
            }
            entries.sort(Comparator.comparingInt((ChannelListS2C.Entry e) -> VideoChannels.tableOf(e.system()).ordinal())
                    .thenComparingInt(ChannelListS2C.Entry::channel)
                    .thenComparing(ChannelListS2C.Entry::pilotName));
            ChannelListS2C list = new ChannelListS2C(allowed, entries);
            if (!list.equals(SENT.get(player.getUUID())) && PropwashNet.sendToPlayer(player, list)) {
                SENT.put(player.getUUID(), list);
            }
        }
    }

    private static ChannelListS2C.Entry entry(MinecraftServer server, QuadcopterEntity drone) {
        Optional<UUID> pilot = drone.pilot();
        String pilotName = SIMULATED.get(drone);
        if (pilotName == null) {
            ServerPlayer player = pilot.map(id -> server.getPlayerList().getPlayer(id)).orElse(null);
            pilotName = player != null ? player.getName().getString() : "";
        }
        Component custom = drone.getCustomName();
        VideoSystem system = drone.build().video().system;
        Map<String, Float> tune = drone.tune().values();
        return new ChannelListS2C.Entry(drone.getUUID(), drone.getId(), system, VtxSettings.channel(tune, system), VtxSettings.manual(tune),
                pilot, pilotName, custom == null ? "" : custom.getString(), drone.build().frame().key());
    }

    private static void reset() {
        CANDIDATES.clear();
        SIMULATED.clear();
        WATCHERS.clear();
        SENT.clear();
        timer = 0;
    }
}
