package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.audio.DroneSoundStage;
import de.lardtom.propwash.audio.DroneSoundStage.Phase;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.server.ViewAnchors;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class DroneSoundJoinCheck {
    public static final String PREFIX = "dronesound server ";
    public static final int SETTLE_TICKS = 60;
    public static final int FPV_TICKS = 100;
    public static final int STEP_TICKS = 5;
    public static final double STEP_BLOCKS = 8.0;
    public static final int ARRIVE_TICKS = 60;
    public static final int LISTEN_TICKS = 40;
    public static final int MOVE_TIMEOUT_TICKS = 20 * 60;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private DroneSoundJoinCheck() {
    }

    private static final class Track {
        final int joined;
        int stage;
        int mark;
        @Nullable QuadcopterEntity drone;
        @Nullable Vec3 far;
        @Nullable Vec3 near;
        final StringBuilder lines = new StringBuilder();

        Track(int joined) {
            this.joined = joined;
        }

        void next(int stage, int tick) {
            this.stage = stage;
            this.mark = tick;
        }
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            try {
                step(player, track, tick);
            } catch (RuntimeException e) {
                Constants.LOG.error("[Beitrittstest] Geräusche an der Drohne: Stufe {} fehlgeschlagen", track.stage, e);
                report(player, track, "fail Stufe " + track.stage + ": " + e.getMessage());
            }
        }
    }

    private static void step(ServerPlayer player, Track track, int tick) {
        int since = tick - track.mark;
        switch (track.stage) {
            case 0 -> {
                if (tick - track.joined < SETTLE_TICKS) {
                    return;
                }
                if (!DroneSoundStage.available()) {
                    report(player, track, "fail Beispiel-Werfer nicht angemeldet");
                    return;
                }
                track.far = DroneSoundStage.spot(player, Phase.FAR);
                track.near = DroneSoundStage.spot(player, Phase.NEAR);
                track.drone = DroneSoundStage.start(player);
                Constants.LOG.info("[Beitrittstest] Geräusche an der Drohne: {} fliegt Drohne {} mit Beispiel-Werfer im FPV", player.getPlainTextName(),
                        track.drone.getId());
                track.next(1, tick);
            }
            case 1 -> {
                if (since >= FPV_TICKS) {
                    track.next(2, tick);
                }
            }
            case 2, 5 -> move(player, track, tick, since, track.stage == 2 ? track.far : track.near);
            case 3, 6 -> {
                if (since < ARRIVE_TICKS) {
                    return;
                }
                Phase phase = track.stage == 3 ? Phase.FAR : Phase.NEAR;
                DroneSoundStage.Emission emission = DroneSoundStage.emit(player, drone(track), phase);
                String line = String.format(Locale.ROOT, "%s Drohne %.0f Blöcke vom Körper, Werfer %s, Blick am Server %s", phase, emission.distance(), emission.fired(),
                        ViewAnchors.isAnchored(player) ? "an der Drohne" : "am Körper");
                Constants.LOG.info("[Beitrittstest] Geräusche an der Drohne: {}", line);
                track.lines.append(track.lines.isEmpty() ? "" : "; ").append(line);
                track.next(track.stage + 1, tick);
            }
            case 4 -> {
                if (since >= LISTEN_TICKS) {
                    track.next(5, tick);
                }
            }
            case 7 -> {
                if (since >= LISTEN_TICKS) {
                    DroneSoundStage.release(player, drone(track));
                    report(player, track, "done " + track.lines);
                }
            }
            default -> {
            }
        }
    }

    private static void move(ServerPlayer player, Track track, int tick, int since, @Nullable Vec3 target) {
        QuadcopterEntity drone = drone(track);
        if (target == null) {
            throw new IllegalStateException("kein Ziel");
        }
        if (since > MOVE_TIMEOUT_TICKS) {
            throw new IllegalStateException(String.format(Locale.ROOT, "Drohne kommt nicht an: %.1f %.1f statt %.1f %.1f", drone.getX(), drone.getZ(), target.x, target.z));
        }
        if (!drone.isFlownBy(player.getUUID())) {
            throw new IllegalStateException("FPV beim Verlegen beendet");
        }
        if (since % STEP_TICKS != 0) {
            return;
        }
        if (Math.hypot(drone.getX() - target.x, drone.getZ() - target.z) <= 1.0) {
            track.next(track.stage + 1, tick);
            return;
        }
        DroneSoundStage.stepToward(drone, target, STEP_BLOCKS);
    }

    private static QuadcopterEntity drone(Track track) {
        QuadcopterEntity drone = track.drone;
        if (drone == null || drone.isRemoved()) {
            throw new IllegalStateException("Werfer-Drohne verschwunden");
        }
        return drone;
    }

    private static void report(ServerPlayer player, Track track, String text) {
        track.stage = 9;
        Constants.LOG.info("[Beitrittstest] Geräusche an der Drohne: {}", text);
        player.sendSystemMessage(Component.literal(PREFIX + text));
    }
}
