package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CovLandingJoinCheck {
    public static final int SETTLE_TICKS = 60;
    public static final int REST_TICKS = 20;
    public static final double FLIPPED_DEG = 60.0;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private static final class Track {
        final int joined;
        boolean started;
        double padY;
        double lastY = Double.NaN;
        int still;
        boolean resting;
        int rests;
        int flipped;
        double maxRestTilt;

        Track(int joined) {
            this.joined = joined;
        }
    }

    private CovLandingJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            if (!track.started) {
                if (tick - track.joined >= SETTLE_TICKS) {
                    track.started = true;
                    BlockPos ground = player.blockPosition();
                    CovFlightJoinSupport.clearAbove(player.level(), ground, 4, 8, null);
                    track.padY = ground.getY();
                    player.teleportTo(player.level(), ground.getX() + 0.5, ground.getY(), ground.getZ() - 3.5, Set.of(), 0.0F, 0.0F, true);
                    CovFlightJoinSupport.startFpv(player, DroneBuild.FREESTYLE, ground.getX() + 0.5, ground.getY() + 0.05, ground.getZ() + 0.5, 0.0F,
                            "Landungen");
                }
                continue;
            }
            QuadcopterEntity drone = DronePiloting.pilotedBy(player.getUUID()).orElse(null);
            if (drone == null) {
                continue;
            }
            double y = drone.getY();
            boolean low = y - track.padY < 0.3;
            boolean still = Double.isFinite(track.lastY) && Math.abs(y - track.lastY) < 0.005;
            track.lastY = y;
            track.still = low && still ? track.still + 1 : 0;
            if (track.still == REST_TICKS && !track.resting) {
                track.resting = true;
                track.rests++;
                double tilt = CovFlightJoinSupport.tiltDeg(drone);
                track.maxRestTilt = Math.max(track.maxRestTilt, tilt);
                if (tilt > FLIPPED_DEG) {
                    track.flipped++;
                }
                Constants.LOG.info("[Beitrittstest] Landungen: Ruhelage {} am Server, Neigung {}°, scharf {}", track.rests,
                        String.format(Locale.ROOT, "%.1f", tilt), drone.isArmed());
            }
            if (!low) {
                track.resting = false;
            }
            if (track.rests > 0 && tick % 200 == 0) {
                Constants.LOG.info("[Beitrittstest] Landungen am Server: {} Ruhelagen, {} gekippt (über {}°), größte Neigung {}°", track.rests, track.flipped,
                        FLIPPED_DEG, String.format(Locale.ROOT, "%.1f", track.maxRestTilt));
            }
        }
    }
}
