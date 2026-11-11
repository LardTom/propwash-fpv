package de.lardtom.propwash.capture;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.drone.CameraChunkResult;
import de.lardtom.propwash.api.drone.CameraChunkStatus;
import de.lardtom.propwash.api.drone.CameraChunkSupply;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.operator.OperatorServer;
import de.lardtom.propwash.drone.tag.DroneTagRules;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.server.GeneratedArea;
import de.lardtom.propwash.server.RangeManager;
import de.lardtom.propwash.server.ServerModule;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.server.ServerRulesConfig;
import de.lardtom.propwash.server.ViewAnchors;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CaptureServer {
    public static final CameraChunkSupply API = new Api();
    private static final CaptureBook BOOK = new CaptureBook();
    private static final Map<UUID, Session> SESSIONS = new LinkedHashMap<>();
    private static final Map<UUID, int[]> REQUESTS = new HashMap<>();
    private static final CaptureTickets TICKETS = new CaptureTickets();
    private static final Map<CaptureRules.End, Integer> ENDS = new EnumMap<>(CaptureRules.End.class);
    private static volatile boolean active;
    private static int tick;
    private static boolean initialized;

    private CaptureServer() {
    }

    private record Live(QuadcopterEntity drone, CameraArea area) {
    }

    private static final class Session {
        final ServerPlayer player;
        final Map<UUID, QuadcopterEntity> drones = new LinkedHashMap<>();
        final Map<UUID, long[]> generated = new HashMap<>();
        List<Live> live = List.of();
        List<CaptureS2C.Entry> sentEntries = List.of();
        int sentRadius;
        int refreshAt;
        boolean refresh;

        Session(ServerPlayer player) {
            this.player = player;
        }
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashNet.registerS2C(CaptureS2C.TYPE, CaptureS2C.CODEC);
        PropwashNet.registerC2S(CaptureC2S.TYPE, CaptureC2S.CODEC, CaptureServer::onRequest);
        ServerBus.TICK_END.register(CaptureServer::tick);
        ServerBus.PLAYER_LEAVE.register(CaptureServer::onLeave);
        ServerBus.STARTED.register(server -> clear());
        ServerBus.STOPPED.register(server -> clear());
        ServerBus.LEVEL_UNLOAD.register(TICKETS::forgetLevel);
    }

    public static boolean involved(ServerPlayer player) {
        if (player.getChunkTrackingView() instanceof CameraTrackingView) {
            return true;
        }
        return active && hasSession(player);
    }

    private static synchronized boolean hasSession(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.player == player;
    }

    public static synchronized ChunkTrackingView view(ServerPlayer player, ServerLevel level, ChunkPos center, int viewDistance) {
        Session session = session(player);
        if (session == null || session.live.isEmpty()) {
            return ChunkTrackingView.of(center, viewDistance);
        }
        List<CameraArea> areas = new ArrayList<>(session.live.size());
        for (Live live : session.live) {
            if (live.drone().level() == level && !CaptureRules.tooFar(center.x(), center.z(), live.area())) {
                areas.add(live.area());
            }
        }
        return CameraTrackingView.of(center, viewDistance, areas);
    }

    public static synchronized void beforeApply(ServerPlayer player, ChunkTrackingView previous, ChunkTrackingView next) {
        Session session = session(player);
        ChunkPos center = CameraTrackingView.center(next);
        if (session == null || center == null) {
            return;
        }
        Set<CameraArea> areas = new LinkedHashSet<>(CameraTrackingView.areas(previous));
        areas.addAll(CameraTrackingView.areas(next));
        int grown = CaptureRules.grown(session.sentRadius, CaptureRules.storageRadius(center.x(), center.z(), areas));
        if (grown != session.sentRadius) {
            session.sentRadius = grown;
            send(session, CaptureS2C.NO_ANSWER, CaptureS2C.NO_ANSWER);
        }
    }

    public static synchronized void afterApply(ServerPlayer player, ChunkTrackingView next) {
        Session session = session(player);
        if (session == null) {
            return;
        }
        ChunkPos center = CameraTrackingView.center(next);
        int needed = center == null ? 0 : CaptureRules.storageRadius(center.x(), center.z(), CameraTrackingView.areas(next));
        int shrunk = CaptureRules.shrunk(session.sentRadius, needed);
        if (shrunk != session.sentRadius) {
            session.sentRadius = shrunk;
            send(session, CaptureS2C.NO_ANSWER, CaptureS2C.NO_ANSWER);
        }
        session.refresh = true;
    }

    public static boolean seesEntity(ServerPlayer player, Entity entity, int effectiveRange) {
        if (!active || !(player.getChunkTrackingView() instanceof CameraTrackingView view)) {
            return false;
        }
        return seesEntity(player, view, entity, effectiveRange);
    }

    private static synchronized boolean seesEntity(ServerPlayer player, CameraTrackingView view, Entity entity, int effectiveRange) {
        Session session = session(player);
        if (session == null || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        for (Live live : session.live) {
            if (live.drone().level() != level || !view.areas().contains(live.area())) {
                continue;
            }
            double range = Math.min(effectiveRange, live.area().radius() * 16.0);
            double dx = entity.getX() - live.drone().getX();
            double dz = entity.getZ() - live.drone().getZ();
            if (dx * dx + dz * dz <= range * range) {
                ChunkPos chunk = entity.chunkPosition();
                return entity.broadcastToPlayer(player) && level.getChunkSource().chunkMap.isChunkTracked(player, chunk.x(), chunk.z());
            }
        }
        return false;
    }

    public static synchronized int sessions() {
        return SESSIONS.size();
    }

    public static synchronized int supplies() {
        return BOOK.count();
    }

    public static synchronized int tickets() {
        return TICKETS.size();
    }

    public static synchronized int sentRadius(ServerPlayer player) {
        Session session = session(player);
        return session == null ? 0 : session.sentRadius;
    }

    public static synchronized int ends(CaptureRules.End end) {
        return ENDS.getOrDefault(end, 0);
    }

    static synchronized CameraChunkResult open(ServerPlayer player, Entity drone, int radius) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(drone, "drone");
        if (player.hasDisconnected() || player.isRemoved() || !player.isAlive()) {
            return CameraChunkResult.refused(CameraChunkResult.Status.PLAYER_GONE);
        }
        if (!(drone instanceof QuadcopterEntity quad) || quad.isRemoved()) {
            return CameraChunkResult.refused(CameraChunkResult.Status.NOT_A_DRONE);
        }
        if (quad.level() != player.level()) {
            return CameraChunkResult.refused(CameraChunkResult.Status.OTHER_DIMENSION);
        }
        if (!ownDrone(player, quad)) {
            return CameraChunkResult.refused(CameraChunkResult.Status.NOT_OWN_DRONE);
        }
        if (!PropwashNet.canSend(player, CaptureS2C.TYPE)) {
            return CameraChunkResult.refused(CameraChunkResult.Status.CLIENT_UNSUPPORTED);
        }
        MinecraftServer server = player.level().getServer();
        int rule = WorldRules.get(server, WorldRuleSpecs.CAMERA_CHUNKS);
        boolean overloaded = server != null && CaptureRules.overloaded(server.getAverageTickTimeNanos());
        CameraChunkResult result = BOOK.open(player.getUUID(), quad.getUUID(), quad.getId(), radius, rule, overloaded,
                ServerRulesConfig.get().maxStreamedChunks);
        if (!result.accepted()) {
            return result;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.player != player) {
            session = new Session(player);
            SESSIONS.put(player.getUUID(), session);
            active = true;
        }
        session.drones.put(quad.getUUID(), quad);
        CaptureBook.Supply supply = BOOK.get(player.getUUID(), quad.getUUID());
        if (supply != null) {
            place(session, supply, quad);
        }
        rebuild(session);
        if (result.status() == CameraChunkResult.Status.OPENED) {
            Constants.LOG.info("Kamera-Chunks für {}: Drohne {} angemeldet, Radius {} (Welt-Regel {})", player.getGameProfile().name(),
                    quad.getId(), result.radius(), rule);
        }
        return result;
    }

    static synchronized boolean close(ServerPlayer player, Entity drone) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(drone, "drone");
        Session session = session(player);
        if (session == null || !BOOK.close(player.getUUID(), drone.getUUID())) {
            return false;
        }
        session.drones.remove(drone.getUUID());
        session.generated.remove(drone.getUUID());
        count(CaptureRules.End.CLOSED);
        rebuild(session);
        Constants.LOG.info("Kamera-Chunks für {}: Drohne {} abgemeldet", player.getGameProfile().name(), drone.getId());
        return true;
    }

    static synchronized int closeAll(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        Session session = session(player);
        if (session == null) {
            return 0;
        }
        int closed = BOOK.closeAll(player.getUUID());
        for (int i = 0; i < closed; i++) {
            count(CaptureRules.End.CLOSED);
        }
        session.drones.clear();
        session.generated.clear();
        rebuild(session);
        return closed;
    }

    static synchronized Optional<CameraChunkStatus> status(ServerPlayer player, Entity drone) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(drone, "drone");
        CaptureBook.Supply supply = BOOK.get(player.getUUID(), drone.getUUID());
        return supply == null ? Optional.empty() : Optional.of(status(player, supply));
    }

    static synchronized List<CameraChunkStatus> active(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        List<CameraChunkStatus> out = new ArrayList<>();
        for (CaptureBook.Supply supply : BOOK.supplies(player.getUUID())) {
            out.add(status(player, supply));
        }
        return List.copyOf(out);
    }

    private static CameraChunkStatus status(ServerPlayer player, CaptureBook.Supply supply) {
        CameraArea area = supply.area();
        ChunkPos center = area == null ? player.chunkPosition() : area.center();
        int[] counts = new int[2];
        if (area != null && supply.supplying()) {
            ChunkMap map = player.level().getChunkSource().chunkMap;
            area.forEach(pos -> {
                counts[0]++;
                if (map.isChunkTracked(player, pos.x(), pos.z())) {
                    counts[1]++;
                }
            });
        }
        return new CameraChunkStatus(supply.drone(), supply.entityId(), center, supply.requested(), supply.radius(), supply.paused(),
                counts[0], counts[1]);
    }

    public static boolean ownDrone(ServerPlayer player, QuadcopterEntity drone) {
        if (drone.isFlownBy(player.getUUID())) {
            return true;
        }
        if (OperatorServer.operator(drone).filter(operator -> operator == player).isPresent()) {
            return true;
        }
        return drone.entityTags().contains(DroneTagRules.ownerTag(player.getGameProfile().name()));
    }

    private static synchronized void tick(MinecraftServer server) {
        tick++;
        if (SESSIONS.isEmpty()) {
            if (TICKETS.size() > 0) {
                TICKETS.commit(tick);
            }
            return;
        }
        int rule = WorldRules.get(server, WorldRuleSpecs.CAMERA_CHUNKS);
        for (Session session : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer player = session.player;
            UUID id = player.getUUID();
            boolean online = server.getPlayerList().getPlayer(id) == player && !player.hasDisconnected() && !player.isRemoved();
            boolean alive = player.isAlive();
            for (CaptureBook.Supply supply : BOOK.supplies(id)) {
                QuadcopterEntity drone = session.drones.get(supply.drone());
                boolean loaded = drone != null && !drone.isRemoved();
                CaptureRules.Check check = new CaptureRules.Check(online, alive, loaded, loaded && drone.level() == player.level(),
                        loaded && ownDrone(player, drone), rule);
                CaptureRules.End end = CaptureRules.end(check);
                if (end != null) {
                    end(session, supply, end);
                    continue;
                }
                BOOK.resize(supply, rule);
                place(session, supply, drone);
            }
            if (!online) {
                SESSIONS.remove(id);
                active = !SESSIONS.isEmpty();
                continue;
            }
            rebuild(session);
            wantTickets(session);
            List<CaptureS2C.Entry> entries = entries(session);
            if (!entries.equals(session.sentEntries)) {
                send(session, CaptureS2C.NO_ANSWER, CaptureS2C.NO_ANSWER);
            }
            boolean supplying = BOOK.has(id);
            if (session.refresh || supplying && tick >= session.refreshAt) {
                session.refresh = false;
                session.refreshAt = tick + CaptureRules.REFRESH_TICKS;
                player.level().getChunkSource().chunkMap.move(player);
            }
            if (!supplying && !(player.getChunkTrackingView() instanceof CameraTrackingView)) {
                if (session.sentRadius != 0) {
                    session.sentRadius = 0;
                    send(session, CaptureS2C.NO_ANSWER, CaptureS2C.NO_ANSWER);
                }
                if (!session.refresh) {
                    SESSIONS.remove(id);
                    active = !SESSIONS.isEmpty();
                }
            }
        }
        TICKETS.commit(tick);
    }

    private static void place(Session session, CaptureBook.Supply supply, QuadcopterEntity drone) {
        ServerPlayer player = session.player;
        CameraArea area = CameraArea.of(drone.chunkPosition(), Math.max(1, supply.radius()));
        ChunkPos base = ViewAnchors.center(player, player.chunkPosition());
        boolean outOfRange = !(player.position().distanceTo(drone.position()) <= ServerRules.maxPilotDistance(player));
        BOOK.place(supply, area, outOfRange || CaptureRules.tooFar(base.x(), base.z(), area));
    }

    private static void rebuild(Session session) {
        List<Live> live = new ArrayList<>();
        for (CaptureBook.Supply supply : BOOK.supplies(session.player.getUUID())) {
            QuadcopterEntity drone = session.drones.get(supply.drone());
            CameraArea area = supply.area();
            if (drone != null && area != null && supply.supplying()) {
                live.add(new Live(drone, area));
            }
        }
        session.live = List.copyOf(live);
    }

    private static void wantTickets(Session session) {
        ServerPlayer player = session.player;
        boolean generates = ServerRules.mayGenerateChunks(player);
        for (Live live : session.live) {
            if (!(live.drone().level() instanceof ServerLevel level)) {
                continue;
            }
            CameraArea area = live.area();
            int wanted = CaptureRules.ticketRadius(area.radius());
            int radius = generates ? wanted : generatedRadius(session, live, level, wanted);
            if (radius >= 0) {
                TICKETS.want(level, ServerModule.DRONE_VIEW_TICKET.get(), area.center(), radius);
            }
        }
        for (CaptureBook.Supply supply : BOOK.supplies(player.getUUID())) {
            QuadcopterEntity drone = session.drones.get(supply.drone());
            if (supply.paused() && drone != null && drone.level() instanceof ServerLevel level) {
                TICKETS.want(level, ServerModule.DRONE_VIEW_TICKET.get(), drone.chunkPosition(), 0);
            }
        }
    }

    private static int generatedRadius(Session session, Live live, ServerLevel level, int wanted) {
        CameraArea area = live.area();
        long key = area.center().pack();
        long[] cached = session.generated.get(live.drone().getUUID());
        if (cached != null && cached[0] == key && cached[1] == wanted && tick - cached[3] < CaptureRules.REFRESH_TICKS) {
            return (int) cached[2];
        }
        int radius = -1;
        for (int r = wanted; r >= 1; r--) {
            if (RangeManager.generatedStatus(level, area.centerX(), area.centerZ(), r) == GeneratedArea.Status.GENERATED) {
                radius = r;
                break;
            }
        }
        session.generated.put(live.drone().getUUID(), new long[]{key, wanted, radius, tick});
        return radius;
    }

    private static void end(Session session, CaptureBook.Supply supply, CaptureRules.End end) {
        BOOK.close(supply.player(), supply.drone());
        session.drones.remove(supply.drone());
        session.generated.remove(supply.drone());
        session.refresh = true;
        count(end);
        Constants.LOG.info("Kamera-Chunks für {}: Drohne {} beendet ({})", session.player.getGameProfile().name(), supply.entityId(), end);
    }

    private static void count(CaptureRules.End end) {
        ENDS.merge(end, 1, Integer::sum);
    }

    private static List<CaptureS2C.Entry> entries(Session session) {
        List<CaptureS2C.Entry> entries = new ArrayList<>();
        for (CaptureBook.Supply supply : BOOK.supplies(session.player.getUUID())) {
            entries.add(new CaptureS2C.Entry(supply.entityId(), supply.radius(), !supply.supplying()));
        }
        return List.copyOf(entries);
    }

    private static void send(Session session, int answerEntity, int answerStatus) {
        List<CaptureS2C.Entry> entries = entries(session);
        session.sentEntries = entries;
        PropwashNet.sendToPlayer(session.player, new CaptureS2C(session.sentRadius, entries, answerEntity, answerStatus));
    }

    private static @Nullable Session session(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.player == player ? session : null;
    }

    private static synchronized void onRequest(CaptureC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        int[] window = REQUESTS.computeIfAbsent(player.getUUID(), key -> new int[]{tick, 0});
        if (!CaptureRules.allowRequest(window, tick)) {
            return;
        }
        Entity entity = player.level().getEntity(payload.entityId());
        int status;
        if (payload.radius() <= 0) {
            if (entity != null) {
                close(player, entity);
            }
            status = CaptureS2C.NO_ANSWER;
        } else {
            CameraChunkResult result = entity == null ? CameraChunkResult.refused(CameraChunkResult.Status.NOT_A_DRONE)
                    : open(player, entity, payload.radius());
            status = result.status().ordinal();
        }
        Session session = session(player);
        if (session != null) {
            send(session, payload.entityId(), status);
        } else {
            PropwashNet.sendToPlayer(player, new CaptureS2C(0, List.of(), payload.entityId(), status));
        }
    }

    private static synchronized void onLeave(ServerPlayer player) {
        REQUESTS.remove(player.getUUID());
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.player != player) {
            return;
        }
        for (CaptureBook.Supply supply : BOOK.supplies(player.getUUID())) {
            end(session, supply, CaptureRules.End.DISCONNECTED);
        }
        SESSIONS.remove(player.getUUID());
        active = !SESSIONS.isEmpty();
    }

    private static synchronized void clear() {
        BOOK.clear();
        SESSIONS.clear();
        active = false;
        REQUESTS.clear();
        TICKETS.clear();
    }

    private static final class Api implements CameraChunkSupply {
        @Override
        public CameraChunkResult open(ServerPlayer player, Entity drone, int radiusChunks) {
            return CaptureServer.open(player, drone, radiusChunks);
        }

        @Override
        public boolean close(ServerPlayer player, Entity drone) {
            return CaptureServer.close(player, drone);
        }

        @Override
        public int closeAll(ServerPlayer player) {
            return CaptureServer.closeAll(player);
        }

        @Override
        public Optional<CameraChunkStatus> status(ServerPlayer player, Entity drone) {
            return CaptureServer.status(player, drone);
        }

        @Override
        public List<CameraChunkStatus> active(ServerPlayer player) {
            return CaptureServer.active(player);
        }
    }
}
