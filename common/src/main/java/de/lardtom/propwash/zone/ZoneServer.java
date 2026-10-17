package de.lardtom.propwash.zone;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.apiimpl.ImportRateLimit;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.PhysicsRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ZoneServer {
    public static final String CREATED = "propwash.zone.msg.created";
    public static final String CHANGED = "propwash.zone.msg.changed";
    public static final String DELETED = "propwash.zone.msg.deleted";
    public static final String DENIED = "propwash.zone.msg.denied";
    public static final String PROBLEM = "propwash.zone.problem.";
    private static final int EDITABLE_CHECK_TICKS = 20;
    private static final int DENIALS_PER_WINDOW = 2;
    private static final long DENIAL_WINDOW_TICKS = 100L;
    private static final Map<UUID, Sent> SENT = new HashMap<>();
    private static ImportRateLimit denials = new ImportRateLimit(DENIALS_PER_WINDOW, DENIAL_WINDOW_TICKS);
    private static volatile @Nullable MinecraftServer running;
    private static boolean initialized;

    private record Sent(int version, String dimension, boolean editable) {
    }

    public record Outcome(@Nullable Zone zone, @Nullable String problem) {
        public boolean ok() {
            return this.problem == null;
        }

        static Outcome failed(String problem) {
            return new Outcome(null, problem);
        }
    }

    private ZoneServer() {
    }

    static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashNet.registerS2C(ZonesS2C.TYPE, ZonesS2C.CODEC);
        PropwashNet.registerC2S(ZoneEditC2S.TYPE, ZoneEditC2S.CODEC, ZoneServer::onEdit);
        ServerBus.STARTED.register(server -> {
            SENT.clear();
            denials = new ImportRateLimit(DENIALS_PER_WINDOW, DENIAL_WINDOW_TICKS);
            running = server;
            ZoneNames.server(ZoneServer::nameOnServerThread);
        });
        ServerBus.STOPPED.register(server -> {
            SENT.clear();
            if (running == server) {
                running = null;
                ZoneNames.server(null);
            }
        });
        ServerBus.PLAYER_JOIN.register(player -> send(player, true));
        ServerBus.PLAYER_LEAVE.register(player -> {
            SENT.remove(player.getUUID());
            denials.forget(player.getUUID());
        });
        ServerBus.TICK_END.register(ZoneServer::sync);
    }

    private static @Nullable String nameOnServerThread(int id) {
        MinecraftServer server = running;
        if (server == null || !server.isSameThread()) {
            return null;
        }
        Zone zone = ZoneData.get(server).get(id);
        return zone == null ? null : zone.name();
    }

    public static String dimension(Level level) {
        return level.dimension().identifier().toString();
    }

    public static List<Zone> zones(Level level) {
        MinecraftServer server = level.getServer();
        return server == null ? List.of() : ZoneData.get(server).in(dimension(level));
    }

    public static ZoneResolution resolve(Level level, double x, double y, double z) {
        MinecraftServer server = level.getServer();
        PhysicsRules world = WorldRules.physics(server);
        if (server == null) {
            return ZoneResolution.world(world);
        }
        return ZoneResolver.resolve(ZoneData.get(server).in(dimension(level)), world, x, y, z);
    }

    public static boolean mayEdit(ServerPlayer player) {
        return WorldRules.mayEdit(player);
    }

    public static ZonesS2C snapshotFor(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        String dimension = dimension(player.level());
        return new ZonesS2C(mayEdit(player), dimension, server == null ? List.of() : ZoneData.get(server).in(dimension));
    }

    private static void send(ServerPlayer player, boolean force) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        ZoneData data = ZoneData.get(server);
        String dimension = dimension(player.level());
        Sent last = SENT.get(player.getUUID());
        boolean recheck = force || last == null || last.version != data.version() || !last.dimension.equals(dimension)
                || server.getTickCount() % EDITABLE_CHECK_TICKS == Math.floorMod(player.getUUID().hashCode(), EDITABLE_CHECK_TICKS);
        if (!recheck) {
            return;
        }
        boolean editable = mayEdit(player);
        Sent next = new Sent(data.version(), dimension, editable);
        if (!force && next.equals(last)) {
            return;
        }
        if (PropwashNet.sendToPlayer(player, new ZonesS2C(editable, dimension, data.in(dimension)))) {
            SENT.put(player.getUUID(), next);
        }
    }

    public static void sync(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, false);
        }
    }

    private static void onEdit(ZoneEditC2S payload, ServerPayloadContext context) {
        edit(payload, context.player());
    }

    public static Outcome edit(ZoneEditC2S payload, ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return Outcome.failed(ZoneCheck.UNKNOWN);
        }
        if (!mayEdit(player)) {
            if (denials.tryAcquire(player.getUUID(), server.getTickCount())) {
                player.sendSystemMessage(Component.translatable(DENIED).withStyle(ChatFormatting.RED));
                Constants.LOG.warn("{} wollte eine Zone ohne Berechtigung ändern", player.getGameProfile().name());
                SENT.remove(player.getUUID());
            }
            return Outcome.failed(ZoneCheck.DENIED);
        }
        Outcome outcome = switch (payload.action()) {
            case SAVE -> save(server, payload.zone().id() == Zone.NEW ? payload.zone().withDimension(dimension(player.level())) : payload.zone(),
                    player.getDisplayName(), player.getGameProfile().name());
            case DELETE -> delete(server, payload.zone().id(), player.getDisplayName(), player.getGameProfile().name());
        };
        if (!outcome.ok()) {
            player.sendSystemMessage(Component.translatable(PROBLEM + outcome.problem()).withStyle(ChatFormatting.RED));
        }
        return outcome;
    }

    public static Outcome save(MinecraftServer server, Zone zone, Component by, String byName) {
        ZoneData data = ZoneData.get(server);
        Zone stored = zone.id() == Zone.NEW ? null : data.get(zone.id());
        Zone candidate = stored == null ? zone : zone.withDimension(stored.dimension());
        String problem = ZoneCheck.problem(candidate, data.in(candidate.dimension()));
        if (problem != null) {
            return Outcome.failed(problem);
        }
        if (candidate.equals(stored)) {
            return new Outcome(stored, null);
        }
        Zone saved = data.put(candidate);
        Constants.LOG.info(stored == null ? "{} legt die Zone {} an ({}, {})" : "{} ändert die Zone {} ({}, {})", byName, saved.name(), saved.id(), saved.dimension());
        broadcast(server, Component.translatable(stored == null ? CREATED : CHANGED, by, saved.name()));
        sync(server);
        return new Outcome(saved, null);
    }

    public static Outcome delete(MinecraftServer server, int id, Component by, String byName) {
        Zone removed = ZoneData.get(server).remove(id);
        if (removed == null) {
            return Outcome.failed(ZoneCheck.UNKNOWN);
        }
        Constants.LOG.info("{} löscht die Zone {} ({}, {})", byName, removed.name(), removed.id(), removed.dimension());
        broadcast(server, Component.translatable(DELETED, by, removed.name()));
        sync(server);
        return new Outcome(removed, null);
    }

    private static void broadcast(MinecraftServer server, Component message) {
        Component styled = message.copy().withStyle(ChatFormatting.GRAY);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(styled);
        }
    }
}
