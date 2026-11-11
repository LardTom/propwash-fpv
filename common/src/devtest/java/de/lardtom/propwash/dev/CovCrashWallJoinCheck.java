package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CovCrashWallJoinCheck {
    public static final String WHAT = "Crash-Regeln aus, Racer an der Wand";
    public static final int SETTLE_TICKS = 60;
    public static final int LOG_TICKS = 20;
    public static final DroneBuild BUILD = DroneBuild.RACER;
    public static final int HALF = 8;
    public static final int HEIGHT = 8;
    public static final int WALL_DX = 3;
    public static final int WALL_HALF = 7;
    public static final int WALL_HEIGHT = 3;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();
    private static boolean restored;

    private static final class Track {
        final int joined;
        boolean started;
        int drone = -1;
        int epoch;
        int resets;

        Track(int joined) {
            this.joined = joined;
        }
    }

    private CovCrashWallJoinCheck() {
    }

    public static @Nullable QuadcopterEntity prepare(ServerPlayer player, @Nullable Map<BlockPos, BlockState> original) {
        ServerLevel level = player.level();
        CovCrashRulesJoinCheck.setRules(level.getServer());
        BlockPos ground = player.blockPosition();
        CovFlightJoinSupport.clearAbove(level, ground, HALF, HEIGHT, original);
        CovFlightJoinSupport.fill(level, ground.offset(WALL_DX, 0, -WALL_HALF), ground.offset(WALL_DX, WALL_HEIGHT, WALL_HALF),
                Blocks.STONE.defaultBlockState(), original);
        player.teleportTo(level, ground.getX() + 0.5, ground.getY(), ground.getZ() - HALF + 1.5, Set.of(), 0.0F, 20.0F, true);
        return CovFlightJoinSupport.startFpv(player, BUILD, ground.getX() + 0.5, ground.getY() + 0.05, ground.getZ() + 0.5, 0.0F, WHAT);
    }

    static void tick(MinecraftServer server, int tick) {
        boolean anyone = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            anyone = true;
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            if (!track.started) {
                if (tick - track.joined >= SETTLE_TICKS) {
                    track.started = true;
                    restored = false;
                    prepare(player, null);
                }
                continue;
            }
            QuadcopterEntity drone = DronePiloting.pilotedBy(player.getUUID()).orElse(null);
            if (drone == null) {
                continue;
            }
            if (drone.getId() != track.drone) {
                track.drone = drone.getId();
                track.epoch = drone.resetEpoch();
            }
            if (drone.resetEpoch() != track.epoch) {
                track.epoch = drone.resetEpoch();
                track.resets++;
                Constants.LOG.info("[Beitrittstest] {}: Server setzt die Drohne zurück ({}. Mal) auf {}, scharf {}", WHAT, track.resets,
                        position(drone), drone.isArmed());
            }
            if (tick % LOG_TICKS == 0) {
                Constants.LOG.info("[Beitrittstest] {}: Drohne am Server {}, Neigung {}°, scharf {}, Rücksetzer bisher {}", WHAT, position(drone),
                        String.format(Locale.ROOT, "%.0f", CovFlightJoinSupport.tiltDeg(drone)), drone.isArmed(), track.resets);
            }
        }
        if (!anyone && !TRACKS.isEmpty() && !restored) {
            restored = true;
            for (Track track : TRACKS.values()) {
                Constants.LOG.info("[Beitrittstest] {}: Server hat die Drohne insgesamt {}-mal zurückgesetzt", WHAT, track.resets);
            }
            CovCrashRulesJoinCheck.restoreRules(server);
        }
    }

    private static String position(QuadcopterEntity drone) {
        return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", drone.getX(), drone.getY(), drone.getZ());
    }
}
