package de.lardtom.propwash.server;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.network.PropwashNet;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

public final class RangeManager {
    static final int MOVE_INTERVAL = 5;
    static final int MAX_HIDDEN_TICKS = 200;

    private static final Map<UUID, PilotLink> LINKS = new LinkedHashMap<>();
    private static final Map<ServerLevel, GeneratedChunks> GENERATED = new IdentityHashMap<>();
    private static final TicketLedger TICKETS = new TicketLedger();
    private static final Set<ServerPlayer> MOVES = Collections.newSetFromMap(new IdentityHashMap<>());
    private static int tick;

    private RangeManager() {
    }

    static void init() {
        QuadcopterEntity.PILOT_CHANGED.register(RangeManager::onPilotChanged);
        ServerBus.TICK_END.register(RangeManager::onTick);
        ServerBus.PLAYER_LEAVE.register(RangeManager::onPlayerLeave);
        ServerBus.LEVEL_UNLOAD.register(RangeManager::onLevelUnload);
        ServerBus.STOPPED.register(server -> reset());
    }

    public static Collection<PilotLink> links() {
        return Collections.unmodifiableCollection(LINKS.values());
    }

    public static Optional<PilotLink> link(UUID pilot) {
        return Optional.ofNullable(LINKS.get(pilot));
    }

    public static int ticketCount() {
        return TICKETS.size();
    }

    public static int pendingChunkChecks() {
        int pending = 0;
        for (GeneratedChunks chunks : GENERATED.values()) {
            pending += chunks.pending();
        }
        return pending;
    }

    public static CompletableFuture<Boolean> storedAsGenerated(ServerLevel level, ChunkPos pos) {
        return GeneratedChunks.scanStored(level, pos);
    }

    public static void onChunkFull(ServerLevel level, ChunkPos pos) {
        GeneratedChunks chunks = GENERATED.get(level);
        if (chunks != null) {
            chunks.markGenerated(pos.pack());
        }
    }

    public static boolean generatedAround(ServerLevel level, int chunkX, int chunkZ) {
        GeneratedChunks chunks = GENERATED.computeIfAbsent(level, GeneratedChunks::new);
        return GeneratedArea.scan(chunks, chunkX, chunkZ, AnchorRules.DRONE_TICKET_RADIUS).generatedWithin(AnchorRules.DRONE_TICKET_RADIUS);
    }

    public static void keepSimulated(ServerLevel level, ChunkPos center) {
        TICKETS.want(level, ServerModule.DRONE_TICKET.get(), center, AnchorRules.DRONE_TICKET_RADIUS);
    }

    public static boolean isIdle() {
        return LINKS.isEmpty() && TICKETS.size() == 0 && ViewAnchors.size() == 0 && GENERATED.isEmpty() && SpectatorLinks.size() == 0;
    }

    private static void onPilotChanged(QuadcopterEntity drone, Optional<UUID> previous, Optional<UUID> current) {
        if (previous.isPresent()) {
            PilotLink link = LINKS.get(previous.get());
            if (link != null && link.drone == drone) {
                LINKS.remove(previous.get());
                detach(link);
            }
        }
        if (current.isPresent()) {
            PilotLink old = LINKS.remove(current.get());
            if (old != null) {
                detach(old);
            }
            LINKS.put(current.get(), new PilotLink(current.get(), drone));
        }
    }

    private static void onPlayerLeave(ServerPlayer player) {
        PilotLink link = LINKS.remove(player.getUUID());
        if (link != null) {
            link.pilot = null;
            unanchor(link);
        }
        ViewAnchors.remove(player);
        SpectatorLinks.onPlayerLeave(player);
        MOVES.remove(player);
    }

    private static void onLevelUnload(ServerLevel level) {
        List<UUID> gone = new ArrayList<>();
        for (PilotLink link : LINKS.values()) {
            if (link.drone.level() == level) {
                gone.add(link.pilotId);
            }
        }
        for (UUID id : gone) {
            PilotLink link = LINKS.remove(id);
            if (link != null) {
                detach(link);
            }
        }
        TICKETS.forgetLevel(level);
        GENERATED.remove(level);
    }

    static void reset() {
        LINKS.clear();
        GENERATED.clear();
        TICKETS.clear();
        MOVES.clear();
        ViewAnchors.clear();
        SpectatorLinks.clear();
        tick = 0;
    }

    private static void detach(PilotLink link) {
        unanchor(link);
        ServerPlayer pilot = link.pilot;
        if (pilot != null && !pilot.hasDisconnected() && !link.lastStatus.equals(RangeStatusS2C.IDLE)) {
            PropwashNet.sendToPlayer(pilot, RangeStatusS2C.IDLE);
        }
        if (pilot != null && !pilot.hasDisconnected() && !link.lastEdge.equals(GenerationEdgeS2C.OFF)) {
            PropwashNet.sendToPlayer(pilot, GenerationEdgeS2C.OFF);
        }
        link.lastStatus = RangeStatusS2C.IDLE;
        link.lastEdge = GenerationEdgeS2C.OFF;
        link.cover = List.of();
        link.stableCover = null;
    }

    private static void unanchor(PilotLink link) {
        if (!link.anchored) {
            return;
        }
        link.anchored = false;
        link.viewTicket = null;
        link.lookahead = null;
        link.lookaheadChunks = 0;
        link.anchorCenter = null;
        ServerPlayer pilot = link.pilot;
        if (pilot != null) {
            ViewAnchors.remove(pilot);
            MOVES.add(pilot);
        }
    }

    private static void flushMoves() {
        if (MOVES.isEmpty()) {
            return;
        }
        List<ServerPlayer> players = new ArrayList<>(MOVES);
        MOVES.clear();
        for (ServerPlayer player : players) {
            if (!player.isRemoved() && !player.hasDisconnected()) {
                player.level().getChunkSource().move(player);
            }
        }
    }

    private static void onTick(MinecraftServer server) {
        tick++;
        ServerRulesConfig config = ServerRulesConfig.get();
        for (GeneratedChunks chunks : GENERATED.values()) {
            chunks.tick(config.chunkChecksPerTick);
        }
        if (LINKS.isEmpty()) {
            SpectatorLinks.apply(TICKETS, MOVES, tick);
            TICKETS.commit(tick);
            flushMoves();
            return;
        }
        validate(server);
        List<PilotLink> anchoring = new ArrayList<>();
        List<PilotLink> releases = new ArrayList<>();
        for (PilotLink link : LINKS.values()) {
            evaluate(link);
            if (link.decision == AnchorRules.Decision.ANCHOR) {
                anchoring.add(link);
            } else if (link.decision.releases()) {
                releases.add(link);
            }
        }
        int[] wanted = new int[anchoring.size()];
        for (int i = 0; i < wanted.length; i++) {
            wanted[i] = anchoring.get(i).wantedViewDistance;
        }
        int[] allotted = StreamBudget.allocate(wanted, config.maxStreamedChunks);
        for (int i = 0; i < allotted.length; i++) {
            anchoring.get(i).viewDistance = allotted[i];
        }
        for (PilotLink link : LINKS.values()) {
            apply(link);
        }
        SpectatorLinks.apply(TICKETS, MOVES, tick);
        TICKETS.commit(tick);
        for (PilotLink link : releases) {
            release(link);
        }
        flushMoves();
    }

    private static void validate(MinecraftServer server) {
        List<PilotLink> dropped = new ArrayList<>();
        for (PilotLink link : LINKS.values()) {
            ServerPlayer pilot = server.getPlayerList().getPlayer(link.pilotId);
            if (link.pilot != null && link.pilot != pilot) {
                ViewAnchors.remove(link.pilot);
                link.anchored = false;
                link.anchorCenter = null;
            }
            link.pilot = pilot;
            boolean visible = link.drone.level() instanceof ServerLevel level && level.getEntity(link.drone.getId()) == link.drone;
            link.hiddenTicks = visible ? 0 : link.hiddenTicks + 1;
            boolean valid = pilot != null
                    && !link.drone.isRemoved()
                    && link.hiddenTicks <= MAX_HIDDEN_TICKS
                    && link.drone.pilot().filter(link.pilotId::equals).isPresent();
            if (!valid) {
                dropped.add(link);
            }
        }
        for (PilotLink link : dropped) {
            LINKS.remove(link.pilotId);
            detach(link);
        }
    }

    private static void evaluate(PilotLink link) {
        ServerPlayer pilot = link.pilot;
        QuadcopterEntity drone = link.drone;
        if (pilot == null || !(drone.level() instanceof ServerLevel level)) {
            link.decision = AnchorRules.Decision.DETACHED;
            return;
        }
        boolean sameLevel = pilot.level() == level;
        boolean unlimited = ServerRules.unlimitedRange(pilot);
        if (sameLevel) {
            link.motion.track(drone.getX(), drone.getZ());
        } else {
            link.motion.reset();
        }
        link.generates = ServerRules.mayGenerateChunks(pilot);
        boolean restricted = !link.generates;
        link.distance = sameLevel ? pilot.position().distanceTo(drone.position()) : Double.POSITIVE_INFINITY;
        link.wantedViewDistance = ServerRules.droneViewDistance(pilot);
        int scanRadius = Math.max(EdgeMask.RADIUS, unlimited ? StreamBudget.viewTicketRadius(link.wantedViewDistance) : AnchorRules.DRONE_TICKET_RADIUS);
        link.area = restricted && sameLevel ? scanArea(link, level, scanRadius) : GeneratedArea.unrestricted();
        AnchorRules.Situation situation = new AnchorRules.Situation(sameLevel, unlimited, link.distance,
                ServerRules.limitedRange(pilot), restricted, link.area,
                sameLevel && ServerRules.inForbiddenSpawnArea(pilot, drone));
        link.decision = AnchorRules.decide(situation);
    }

    private static GeneratedArea.Scan scanArea(PilotLink link, ServerLevel level, int radius) {
        GeneratedChunks chunks = GENERATED.computeIfAbsent(level, GeneratedChunks::new);
        ChunkPos center = link.drone.chunkPosition();
        long key = center.pack();
        if (key != link.areaChunk || chunks.version() != link.areaVersion || radius != link.areaRadius || !link.scannedArea.complete()) {
            link.scannedArea = GeneratedArea.scan(chunks, center.x(), center.z(), radius);
            link.areaChunk = key;
            link.areaVersion = chunks.version();
            link.areaRadius = radius;
        }
        return link.scannedArea;
    }

    private static void apply(PilotLink link) {
        ServerPlayer pilot = link.pilot;
        AnchorRules.Decision decision = link.decision;
        if (pilot == null || !(link.drone.level() instanceof ServerLevel level)) {
            link.lookahead = null;
            return;
        }
        boolean restricted = !link.generates;
        if (decision == AnchorRules.Decision.ANCHOR) {
            anchor(link, pilot);
        } else {
            unanchor(link);
        }
        ChunkPos droneChunk = link.drone.chunkPosition();
        int droneRadius = AnchorRules.droneTicketRadius(decision, link.area, restricted);
        if (droneRadius >= 0) {
            link.droneTicket = new PilotLink.Spot(level, droneChunk, droneRadius);
            TICKETS.want(level, ServerModule.DRONE_TICKET.get(), droneChunk, droneRadius);
        } else if (link.droneTicket != null && keepsLastTickets(decision, link.area)) {
            TICKETS.want(link.droneTicket.level(), ServerModule.DRONE_TICKET.get(), link.droneTicket.pos(), link.droneTicket.radius());
        } else {
            link.droneTicket = null;
        }
        if (decision == AnchorRules.Decision.ANCHOR) {
            int wantedViewRadius = StreamBudget.viewTicketRadius(link.viewDistance);
            PilotLink.Spot single = null;
            PilotLink.Cover cover = null;
            if (restricted && !link.area.generatedWithin(wantedViewRadius)) {
                cover = new PilotLink.Cover(level, coverFor(link, level, droneChunk, wantedViewRadius));
            } else {
                int viewRadius = AnchorRules.viewTicketRadius(decision, link.viewDistance, link.area, restricted);
                if (viewRadius >= 0) {
                    single = new PilotLink.Spot(level, droneChunk, viewRadius);
                }
            }
            wantView(single, cover);
            if (link.area.complete()) {
                link.viewTicket = single;
                link.stableCover = cover;
            } else {
                wantView(link.viewTicket, link.stableCover);
            }
        } else {
            link.viewTicket = null;
            link.stableCover = null;
        }
        placeLookahead(link, level, decision == AnchorRules.Decision.ANCHOR && !restricted);
        if (link.anchored) {
            TICKETS.want(pilot.level(), ServerModule.PILOT_TICKET.get(), pilot.chunkPosition(), AnchorRules.PILOT_TICKET_RADIUS);
        }
        sendStatus(link, pilot);
        sendEdge(link, pilot, level, restricted && pilot.level() == level);
    }

    private static void wantView(PilotLink.@Nullable Spot single, PilotLink.@Nullable Cover cover) {
        if (single != null) {
            TICKETS.want(single.level(), ServerModule.DRONE_VIEW_TICKET.get(), single.pos(), single.radius());
        }
        if (cover != null) {
            for (GeneratedCover.Square square : cover.squares()) {
                TICKETS.want(cover.level(), ServerModule.DRONE_VIEW_TICKET.get(), new ChunkPos(square.centerX(), square.centerZ()), square.radius());
            }
        }
    }

    private static List<GeneratedCover.Square> coverFor(PilotLink link, ServerLevel level, ChunkPos center, int radius) {
        GeneratedChunks chunks = GENERATED.computeIfAbsent(level, GeneratedChunks::new);
        long key = center.pack();
        if (key != link.coverChunk || chunks.version() != link.coverVersion || radius != link.coverRadius) {
            link.cover = GeneratedCover.cover(chunks, center.x(), center.z(), radius);
            link.coverChunk = key;
            link.coverVersion = chunks.version();
            link.coverRadius = radius;
        }
        return link.cover;
    }

    private static void sendEdge(PilotLink link, ServerPlayer pilot, ServerLevel level, boolean restricted) {
        GenerationEdgeS2C edge;
        if (restricted) {
            GeneratedChunks chunks = GENERATED.computeIfAbsent(level, GeneratedChunks::new);
            ChunkPos center = link.drone.chunkPosition();
            long key = center.pack();
            if (key != link.edgeChunk || chunks.version() != link.edgeVersion) {
                link.edge = EdgeMask.scan(chunks, center.x(), center.z());
                link.edgeChunk = key;
                link.edgeVersion = chunks.version();
            }
            edge = GenerationEdgeS2C.of(link.edge);
        } else {
            link.edge = EdgeMask.NONE;
            link.edgeChunk = Long.MIN_VALUE;
            edge = GenerationEdgeS2C.OFF;
        }
        if (!edge.equals(link.lastEdge)) {
            link.lastEdge = edge;
            PropwashNet.sendToPlayer(pilot, edge);
        }
    }

    private static void placeLookahead(PilotLink link, ServerLevel level, boolean allowed) {
        int wanted = allowed ? Lookahead.aheadChunks(link.motion.speedPerSecond(), link.viewDistance) : 0;
        int ahead = Lookahead.stable(link.lookaheadChunks, wanted);
        link.lookaheadChunks = ahead;
        int radius = Lookahead.radius(link.viewDistance, ahead);
        if (radius < 0) {
            link.lookahead = null;
            return;
        }
        long center = Lookahead.center(link.drone.getX(), link.drone.getZ(), link.motion.velocityX(), link.motion.velocityZ(), ahead);
        PilotLink.Spot previous = link.lookahead;
        if (previous == null || previous.level() != level || previous.radius() != radius || !Lookahead.keepCenter(previous.pos().pack(), center)) {
            link.lookahead = new PilotLink.Spot(level, ChunkPos.unpack(center), radius);
        }
        PilotLink.Spot spot = link.lookahead;
        TICKETS.want(spot.level(), ServerModule.DRONE_LOOKAHEAD_TICKET.get(), spot.pos(), spot.radius());
    }

    private static boolean keepsLastTickets(AnchorRules.Decision decision, GeneratedArea.Scan area) {
        return (decision == AnchorRules.Decision.ANCHOR || decision == AnchorRules.Decision.LIMITED)
                && !area.missingWithin(AnchorRules.DRONE_TICKET_RADIUS);
    }

    private static void anchor(PilotLink link, ServerPlayer pilot) {
        boolean wasAnchored = link.anchored;
        if (link.area.generatedWithin(0)) {
            link.anchorCenter = link.drone.chunkPosition();
        } else if (link.anchorCenter == null) {
            link.anchorCenter = pilot.chunkPosition();
        }
        ViewAnchors.set(pilot, link.drone, link.viewDistance, link.anchorCenter);
        link.anchored = true;
        long section = SectionPos.of(link.drone).asLong();
        if (!wasAnchored || section != link.lastSection || tick - link.lastMoveTick >= MOVE_INTERVAL) {
            link.lastSection = section;
            link.lastMoveTick = tick;
            MOVES.add(pilot);
        }
    }

    private static void sendStatus(PilotLink link, ServerPlayer pilot) {
        RangeStatusS2C status = new RangeStatusS2C(true, link.anchored, ServerRules.maxPilotDistance(pilot), link.viewDistance());
        if (!status.equals(link.lastStatus)) {
            link.lastStatus = status;
            PropwashNet.sendToPlayer(pilot, status);
        }
    }

    private static void release(PilotLink link) {
        ServerPlayer pilot = link.pilot;
        AnchorRules.Decision decision = link.decision;
        if (LINKS.get(link.pilotId) != link || !link.drone.pilot().filter(link.pilotId::equals).isPresent()) {
            return;
        }
        Component message = switch (decision) {
            case RELEASE_TOO_FAR -> Component.translatable("propwash.server.release.too_far",
                    pilot != null ? Math.round(ServerRules.limitedRange(pilot)) : 0);
            case RELEASE_UNEXPLORED -> Component.translatable("propwash.server.release.unexplored");
            case RELEASE_PROTECTED -> Component.translatable("propwash.server.release.spawn_protection");
            default -> null;
        };
        link.drone.setPilot(null);
        if (LINKS.get(link.pilotId) == link) {
            LINKS.remove(link.pilotId);
            detach(link);
        }
        if (pilot != null && message != null) {
            pilot.sendSystemMessage(message);
        }
        Constants.LOG.debug("Pilot {} von Drohne {} gelöst: {}", link.pilotId, link.drone.getId(), decision);
    }
}
