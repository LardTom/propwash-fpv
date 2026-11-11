package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.CameraChunkStatus;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.capture.CameraArea;
import de.lardtom.propwash.capture.CameraTrackingView;
import de.lardtom.propwash.capture.CaptureRules;
import de.lardtom.propwash.capture.CaptureServer;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.tag.DroneTags;
import de.lardtom.propwash.server.ServerModule;
import de.lardtom.propwash.server.ServerRules;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CaptureJoinCheck {
    public static final String RESULT = "[capture] ";
    public static final String SPAWNED = RESULT + "server spawned ";
    public static final String MARKER = "capture-marker";
    public static final int SETTLE_TICKS = 60;
    public static final int RADIUS = 3;
    public static final int NEAR_RADIUS = 2;
    public static final int MIN_BLOCKS = 300;
    public static final int DISCARD_DELAY = 200;
    public static final int LEAVE_CHECK = 40;
    public static final int HOLD_EVERY = 20;
    public static final int SPAWN_HOLD = 2;
    private static final Map<UUID, Track> TRACKS = new LinkedHashMap<>();
    private static @Nullable String failure = "kein Spieler beigetreten";
    private static @Nullable String summary;
    private static volatile boolean pending;

    private CaptureJoinCheck() {
    }

    public record Far(int droneId, UUID drone, int markerId, UUID marker, CameraArea area, int blocks, Vec3 playerAt, QuadcopterEntity entity) {
    }

    private static final class Track {
        final ServerPlayer player;
        final int joined;
        int spawnedAt = -1;
        @Nullable Far far;
        @Nullable QuadcopterEntity near;
        final List<UUID> spawned = new ArrayList<>();
        boolean complete;
        int chunks;
        int releasedAt = -1;
        boolean viewFreed;
        boolean discarded;
        int goneAt = -1;
        int disconnectedBefore;
        boolean nearActive;
        boolean reported;

        Track(ServerPlayer player, int joined) {
            this.player = player;
            this.joined = joined;
        }
    }

    public static Far spawnFar(ServerPlayer player, int radius, List<UUID> spawned) {
        return spawnFar(player, radius, spawned, 0);
    }

    public static Far spawnFar(ServerPlayer player, int radius, List<UUID> spawned, int offsetChunksZ) {
        ServerLevel level = player.level();
        int view = ServerRules.playerViewDistance(player);
        int chunkX = Math.max((player.getBlockX() + MIN_BLOCKS + 8) >> 4, player.chunkPosition().x() + view + radius + 6);
        ChunkPos center = new ChunkPos(chunkX, player.chunkPosition().z() + offsetChunksZ);
        QuadcopterEntity drone = spawnDrone(level, player, center.getMiddleBlockX(), center.getMiddleBlockZ(), true);
        ArmorStand marker = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
        if (marker == null) {
            throw new IllegalStateException("kein Marker");
        }
        int mx = center.getMiddleBlockX() + 3;
        int mz = center.getMiddleBlockZ() + 1;
        marker.snapTo(mx + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, mx, mz), mz + 0.5, 0.0F, 0.0F);
        marker.setCustomName(Component.literal(MARKER));
        marker.setNoGravity(true);
        level.addFreshEntity(marker);
        spawned.add(drone.getUUID());
        spawned.add(marker.getUUID());
        int blocks = (int) Math.round(Math.abs(drone.getX() - player.getX()));
        return new Far(drone.getId(), drone.getUUID(), marker.getId(), marker.getUUID(), CameraArea.of(center, radius), blocks, player.position(), drone);
    }

    public static QuadcopterEntity spawnDrone(ServerLevel level, ServerPlayer owner, int x, int z, boolean own) {
        ChunkPos chunk = new ChunkPos(x >> 4, z >> 4);
        level.getChunk(chunk.x(), chunk.z());
        level.getChunkSource().addTicketWithRadius(ServerModule.DRONE_VIEW_TICKET.get(), chunk, SPAWN_HOLD);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("keine Drohne");
        }
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        drone.snapTo(x + 0.5, ground + 0.05, z + 0.5, 0.0F, 0.0F);
        drone.setBuild(DroneBuild.FREESTYLE);
        if (own) {
            DroneTags.placedBy(drone, owner);
        }
        level.addFreshEntity(drone);
        return drone;
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Track track = TRACKS.get(player.getUUID());
            if (track == null || track.player != player) {
                track = new Track(player, tick);
                TRACKS.put(player.getUUID(), track);
            }
            if (track.far == null) {
                if (tick - track.joined >= SETTLE_TICKS) {
                    start(track, tick);
                }
                continue;
            }
            observe(track, tick);
        }
        boolean open = false;
        for (Track track : TRACKS.values()) {
            if (track.far == null || track.reported) {
                continue;
            }
            if (track.goneAt < 0 && (track.player.hasDisconnected() || track.player.isRemoved())) {
                track.goneAt = tick;
            }
            if (track.goneAt >= 0 && reportDue(tick - track.goneAt, CaptureServer.sessions(), CaptureServer.supplies(), CaptureServer.tickets(),
                    CaptureServer.ends(CaptureRules.End.DISCONNECTED) - track.disconnectedBefore)) {
                report(server, track);
                continue;
            }
            open = true;
        }
        pending = open;
    }

    static boolean reportDue(int sinceGone, int sessions, int supplies, int tickets, int disconnected) {
        return sinceGone >= LEAVE_CHECK || sessions == 0 && supplies == 0 && tickets == 0 && disconnected >= 1;
    }

    public static boolean pending() {
        return pending;
    }

    static int holdRadius(int sinceSpawn, int sinceRelease) {
        if (sinceRelease >= 0) {
            return sinceRelease < DISCARD_DELAY && sinceRelease % HOLD_EVERY == 0 ? 1 : -1;
        }
        return sinceSpawn % HOLD_EVERY == 0 ? SPAWN_HOLD : -1;
    }

    private static void start(Track track, int tick) {
        ServerPlayer player = track.player;
        Far far = spawnFar(player, RADIUS, track.spawned);
        track.far = far;
        track.spawnedAt = tick;
        QuadcopterEntity near = spawnDrone(player.level(), player, player.getBlockX() + 3, player.getBlockZ() + 3, true);
        track.near = near;
        track.spawned.add(near.getUUID());
        failure = "Lage nicht angemeldet";
        player.sendSystemMessage(Component.literal(SPAWNED + far.droneId() + " " + far.markerId() + " " + far.area().centerX() + " "
                + far.area().centerZ() + " " + RADIUS + " " + far.blocks() + " " + near.getId() + " " + NEAR_RADIUS));
        Constants.LOG.info("[Beitrittstest] Kamera-Chunks: Drohne {} Blöcke entfernt bei Chunk {}, Marker {}, nahe Drohne {}", far.blocks(),
                far.area().center(), far.markerId(), near.getId());
    }

    private static void observe(Track track, int tick) {
        ServerPlayer player = track.player;
        Far far = track.far;
        if (far == null || player.hasDisconnected()) {
            return;
        }
        ServerLevel level = player.level();
        Entity drone = level.getEntity(far.drone());
        Optional<CameraChunkStatus> status = drone == null ? Optional.empty() : PropwashApi.drones().cameraChunks().status(player, drone);
        if (status.isPresent()) {
            if (status.get().complete() && !track.complete) {
                track.complete = true;
                track.chunks = status.get().chunks();
                Constants.LOG.info("[Beitrittstest] Kamera-Chunks: alle {} Chunks gesendet, Ansicht {}", track.chunks,
                        player.getChunkTrackingView() instanceof CameraTrackingView ? "Spieler plus Lage" : "vanilla");
                failure = "Lage nicht wieder abgemeldet";
            }
        } else if (track.complete && track.releasedAt < 0) {
            track.releasedAt = tick;
            track.disconnectedBefore = CaptureServer.ends(CaptureRules.End.DISCONNECTED);
        }
        if (track.near != null && !track.near.isRemoved()) {
            track.nearActive = PropwashApi.drones().cameraChunks().status(player, track.near).isPresent();
        }
        int hold = track.discarded ? -1 : holdRadius(tick - track.spawnedAt, track.releasedAt < 0 ? -1 : tick - track.releasedAt);
        if (hold >= 0) {
            level.getChunkSource().addTicketWithRadius(ServerModule.DRONE_VIEW_TICKET.get(), far.area().center(), hold);
        }
        if (track.releasedAt < 0) {
            return;
        }
        int since = tick - track.releasedAt;
        if (!track.viewFreed && !CameraTrackingView.areas(player.getChunkTrackingView()).contains(far.area())) {
            track.viewFreed = true;
            failure = "Trennen nicht geprüft";
            Constants.LOG.info("[Beitrittstest] Kamera-Chunks: abgemeldet, Lage nach {} Ticks aus der Ansicht entfernt", since);
        }
        if (!track.discarded && since >= DISCARD_DELAY) {
            track.discarded = true;
            discard(level, far.drone());
            discard(level, far.marker());
        }
    }

    private static void report(MinecraftServer server, Track track) {
        track.reported = true;
        int disconnected = CaptureServer.ends(CaptureRules.End.DISCONNECTED) - track.disconnectedBefore;
        int sessions = CaptureServer.sessions();
        int supplies = CaptureServer.supplies();
        int tickets = CaptureServer.tickets();
        boolean clean = sessions == 0 && supplies == 0 && tickets == 0;
        if (!track.complete) {
            failure = "Lage nie vollständig gesendet";
        } else if (track.releasedAt < 0) {
            failure = "Lage nie abgemeldet";
        } else if (!track.viewFreed) {
            failure = "Lage nach dem Abmelden noch in der Ansicht";
        } else if (!track.nearActive || disconnected < 1) {
            failure = "Trennen beendete keine Lage (nahe Lage aktiv " + track.nearActive + ", beendet " + disconnected + ")";
        } else if (!clean) {
            failure = "nach dem Trennen übrig: " + sessions + " Sitzungen, " + supplies + " Lagen, " + tickets + " Tickets";
        } else {
            failure = null;
        }
        summary = "Lage " + (track.far == null ? 0 : track.far.blocks()) + " Blöcke, " + track.chunks + " Chunks gesendet, abgemeldet und frei, Trennen beendet "
                + disconnected + " Lage, danach " + sessions + " Sitzungen, " + supplies + " Lagen, " + tickets + " Tickets";
        ServerLevel level = server.overworld();
        for (UUID id : track.spawned) {
            discard(level, id);
        }
        Constants.LOG.info("[Beitrittstest] Kamera-Chunks: {} – {}", failure == null ? "PASS" : "FAIL " + failure, summary);
    }

    private static void discard(ServerLevel level, UUID id) {
        Entity entity = level.getEntity(id);
        if (entity != null) {
            entity.discard();
        }
    }

    public static @Nullable String failure() {
        return failure;
    }

    public static @Nullable String summary() {
        return summary;
    }
}
