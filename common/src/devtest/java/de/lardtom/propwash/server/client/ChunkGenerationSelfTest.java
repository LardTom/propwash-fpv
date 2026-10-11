package de.lardtom.propwash.server.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.server.PilotLink;
import de.lardtom.propwash.server.RangeManager;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.server.ServerRulesConfig;
import de.lardtom.propwash.server.ViewAnchors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

final class ChunkGenerationSelfTest {
    static final String NAME = "server: Drohne erzeugt Chunks im unerforschten Gebiet";
    static final int MAX_TICKS = 2400;
    private static final String PROBE = "generation";
    private static final double FLIGHT_DISTANCE = 640.0;
    private static final double STEP_PER_TICK = 4.0;
    private static final double FLIGHT_HEIGHT = 12.0;
    private static final int VIEW_DISTANCE = 6;
    private static final int MAX_STALL_TICKS = 400;
    private static final int MAX_ARRIVAL_TICKS = 400;

    private ChunkGenerationSelfTest() {
    }

    static StepResult step(SelfTest.StepContext ctx) {
        Probe probe = (Probe) ctx.state().get(PROBE);
        if (probe == null) {
            probe = new Probe();
            ctx.state().put(PROBE, probe);
            Probe p = probe;
            probe.pending = ctx.onServer(server -> start(server, p));
            return StepResult.pending();
        }
        if (probe.pending != null) {
            if (probe.pending.isCompletedExceptionally()) {
                return finish(ctx, probe, StepResult.fail("Server-Schritt in Phase " + probe.phase + " fehlgeschlagen: "
                        + probe.pending.exceptionNow()));
            }
            if (!probe.pending.isDone()) {
                return StepResult.pending();
            }
            probe.pending = null;
        }
        if (probe.failure != null) {
            return finish(ctx, probe, StepResult.fail(probe.failure));
        }
        Probe p = probe;
        switch (probe.phase) {
            case CHECK_STORED -> {
                CompletableFuture<Boolean> stored = probe.storedScan;
                if (stored == null || !stored.isDone()) {
                    return StepResult.pending();
                }
                if (Boolean.TRUE.equals(stored.getNow(false))) {
                    return finish(ctx, probe, StepResult.fail("Zielgebiet " + probe.targetChunk + " war schon erzeugt, der Test beweist nichts"));
                }
                probe.phase = Phase.FLYING;
                probe.pending = ctx.onServer(server -> fly(server, p));
            }
            case FLYING -> probe.pending = ctx.onServer(server -> fly(server, p));
            case ARRIVED -> {
                int missingOnClient = missingOnClient(probe);
                if (probe.serverReady && missingOnClient == 0) {
                    ctx.log(String.format(Locale.ROOT,
                            "%.0f Blöcke in unerforschtes Gebiet geflogen, %d Stockungs-Ticks, Vorausladen bis %d Chunks, %d Chunks um die Drohne erzeugt und beim Client",
                            FLIGHT_DISTANCE, probe.stallTotal, probe.maxAhead, probe.checkedChunks));
                    return finish(ctx, probe, StepResult.pass());
                }
                if (++probe.waitTicks > MAX_ARRIVAL_TICKS) {
                    return finish(ctx, probe, StepResult.fail("Chunks um die Drohne fehlen: Server=" + probe.status
                            + " fehlendBeimClient=" + missingOnClient));
                }
                probe.pending = ctx.onServer(server -> inspect(server, p));
            }
            default -> {
                return StepResult.pending();
            }
        }
        return StepResult.pending();
    }

    private static void start(MinecraftServer server, Probe probe) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            probe.failure = "kein Spieler auf dem Server";
            return;
        }
        ServerPlayer player = players.getFirst();
        ServerLevel level = player.level();
        ServerRulesConfig config = ServerRulesConfig.get();
        probe.savedConfig = config.copy();
        config.assign(new ServerRulesConfig());
        config.droneViewDistance = VIEW_DISTANCE;
        if (!ServerRules.unlimitedRange(player) || !ServerRules.mayGenerateChunks(player)) {
            probe.failure = "Standard-Regeln erlauben dem Einzelspieler keine unbegrenzte Reichweite mit neuen Chunks: unbegrenzt="
                    + ServerRules.unlimitedRange(player) + " erzeugen=" + ServerRules.mayGenerateChunks(player);
            return;
        }
        probe.origin = player.position();
        probe.x = player.getX();
        probe.y = player.getY() + FLIGHT_HEIGHT;
        probe.z = player.getZ();
        probe.targetChunk = ChunkPos.containing(BlockPos.containing(probe.x, probe.y, probe.z - FLIGHT_DISTANCE));
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            probe.failure = "EntityType.create lieferte null";
            return;
        }
        drone.snapTo(probe.x, player.getY() + 3.0, probe.z, 0.0F, 0.0F);
        if (!level.addFreshEntity(drone)) {
            probe.failure = "addFreshEntity fehlgeschlagen";
            return;
        }
        drone.setPilot(player);
        probe.drone = drone;
        probe.droneId = drone.getId();
        probe.playerId = player.getId();
        probe.storedScan = RangeManager.storedAsGenerated(level, probe.targetChunk);
        probe.phase = Phase.CHECK_STORED;
    }

    private static void fly(MinecraftServer server, Probe probe) {
        ServerPlayer player = player(server, probe);
        QuadcopterEntity drone = drone(probe);
        if (player == null || drone == null) {
            probe.failure = "Spieler oder Drohne verschwunden (Flug)";
            return;
        }
        if (drone.pilot().isEmpty()) {
            probe.failure = "Pilot während des Flugs verloren nach " + Math.round(probe.origin().z - probe.z) + " Blöcken";
            return;
        }
        PilotLink link = RangeManager.link(player.getUUID()).orElse(null);
        ChunkPos lookahead = link != null ? link.lookahead() : null;
        if (lookahead != null) {
            ChunkPos droneChunk = drone.chunkPosition();
            probe.maxAhead = Math.max(probe.maxAhead, droneChunk.z() - lookahead.z());
        }
        double flown = probe.origin().z - probe.z;
        if (flown >= FLIGHT_DISTANCE) {
            if (probe.maxAhead <= 0) {
                probe.failure = "Vorausladen in Flugrichtung war nie aktiv (Tempo "
                        + (link != null ? Math.round(link.estimatedSpeed()) : -1) + " Blöcke/s)";
                return;
            }
            probe.phase = Phase.ARRIVED;
            inspect(server, probe);
            return;
        }
        double nextZ = probe.z - Math.min(STEP_PER_TICK, FLIGHT_DISTANCE - flown);
        ServerLevel level = player.level();
        ChunkPos next = ChunkPos.containing(BlockPos.containing(probe.x, probe.y, nextZ));
        if (level.getChunkSource().getChunkNow(next.x(), next.z()) == null) {
            probe.stallTotal++;
            if (++probe.stallTicks > MAX_STALL_TICKS) {
                probe.failure = "Chunk " + next + " vor der Drohne wird nicht erzeugt (nach " + Math.round(flown) + " Blöcken, "
                        + (link != null ? link.decision() + " sicht=" + link.viewDistance() + " erzeugt=" + link.generatesChunks() : "kein Link")
                        + " tickets=" + RangeManager.ticketCount() + ")";
            }
            return;
        }
        probe.stallTicks = 0;
        probe.z = nextZ;
        drone.resetTo(new Vec3(probe.x, probe.y, probe.z), new Quaternionf());
    }

    private static void inspect(MinecraftServer server, Probe probe) {
        ServerPlayer player = player(server, probe);
        QuadcopterEntity drone = drone(probe);
        if (player == null || drone == null) {
            probe.failure = "Spieler oder Drohne verschwunden (Prüfung)";
            return;
        }
        PilotLink link = RangeManager.link(player.getUUID()).orElse(null);
        ChunkPos center = drone.chunkPosition();
        int radius = Math.max(1, (link != null ? link.viewDistance() : 0) - 1);
        ServerLevel level = player.level();
        int missing = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (level.getChunkSource().getChunkNow(center.x() + dx, center.z() + dz) == null) {
                    missing++;
                }
            }
        }
        ChunkTrackingView view = player.getChunkTrackingView();
        boolean centered = view instanceof ChunkTrackingView.Positioned positioned && positioned.center().equals(center);
        boolean anchored = ViewAnchors.isAnchored(player) && ViewAnchors.anchorDrone(player) == drone;
        probe.checkCenter = center;
        probe.checkRadius = radius;
        probe.checkedChunks = (2 * radius + 1) * (2 * radius + 1);
        probe.status = "fehlend=" + missing + " anker=" + anchored + " zentriert=" + centered + " radius=" + radius
                + (link != null ? " entscheidung=" + link.decision() + " sicht=" + link.viewDistance() : " kein Link");
        probe.serverReady = missing == 0 && anchored && centered;
    }

    private static int missingOnClient(Probe probe) {
        ClientLevel level = Minecraft.getInstance().level;
        ChunkPos center = probe.checkCenter;
        if (level == null || center == null) {
            return Integer.MAX_VALUE;
        }
        Entity seen = level.getEntity(probe.droneId);
        int missing = seen instanceof QuadcopterEntity ? 0 : 1;
        int radius = probe.checkRadius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!level.getChunkSource().hasChunk(center.x() + dx, center.z() + dz)) {
                    missing++;
                }
            }
        }
        return missing;
    }

    private static StepResult finish(SelfTest.StepContext ctx, Probe probe, StepResult result) {
        if (!probe.cleaned) {
            probe.cleaned = true;
            ctx.onServer(server -> cleanup(server, probe));
        }
        return result;
    }

    private static void cleanup(MinecraftServer server, Probe probe) {
        if (probe.savedConfig != null) {
            ServerRulesConfig.get().assign(probe.savedConfig);
        }
        QuadcopterEntity drone = drone(probe);
        if (drone != null) {
            drone.discard();
        }
        ServerPlayer player = player(server, probe);
        Vec3 origin = probe.origin;
        if (player != null && origin != null) {
            player.teleportTo(origin.x, origin.y, origin.z);
        }
    }

    private static @Nullable ServerPlayer player(MinecraftServer server, Probe probe) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getId() == probe.playerId) {
                return player;
            }
        }
        return null;
    }

    private static @Nullable QuadcopterEntity drone(Probe probe) {
        QuadcopterEntity drone = probe.drone;
        return drone != null && !drone.isRemoved() ? drone : null;
    }

    private enum Phase {
        STARTING,
        CHECK_STORED,
        FLYING,
        ARRIVED
    }

    private static final class Probe {
        volatile @Nullable CompletableFuture<Void> pending;
        volatile Phase phase = Phase.STARTING;
        volatile @Nullable String failure;
        volatile @Nullable ServerRulesConfig savedConfig;
        volatile @Nullable Vec3 origin;
        volatile @Nullable CompletableFuture<Boolean> storedScan;
        volatile ChunkPos targetChunk = new ChunkPos(0, 0);
        volatile @Nullable ChunkPos checkCenter;
        volatile int checkRadius;
        volatile int checkedChunks;
        volatile @Nullable QuadcopterEntity drone;
        volatile int droneId = -1;
        volatile int playerId = -1;
        volatile double x;
        volatile double y;
        volatile double z;
        volatile int stallTicks;
        volatile int stallTotal;
        volatile int maxAhead;
        volatile boolean serverReady;
        volatile String status = "";
        int waitTicks;
        boolean cleaned;

        Vec3 origin() {
            Vec3 value = this.origin;
            return value != null ? value : Vec3.ZERO;
        }
    }
}
