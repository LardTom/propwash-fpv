package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CovSurfaceJoinCheck {
    public static final int SETTLE_TICKS = 60;
    public static final int WALL_DX = 36;
    public static final int WALL_HALF = 4;
    public static final int WALL_HEIGHT = 8;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private static final class Track {
        final int joined;
        boolean started;
        double padY;
        double lastY = Double.NaN;
        int reports;

        Track(int joined) {
            this.joined = joined;
        }
    }

    private CovSurfaceJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            if (!track.started) {
                if (tick - track.joined >= SETTLE_TICKS) {
                    track.started = true;
                    ServerLevel level = player.level();
                    BlockPos ground = player.blockPosition();
                    CovFlightJoinSupport.fill(level, ground.offset(-4, 0, -6), ground.offset(WALL_DX + 4, 16, 6), Blocks.AIR.defaultBlockState(), null);
                    CovFlightJoinSupport.fill(level, ground.offset(-4, -1, -6), ground.offset(WALL_DX + 4, -1, 6), Blocks.STONE.defaultBlockState(), null);
                    CovFlightJoinSupport.fill(level, ground.offset(-2, -1, -2), ground.offset(2, -1, 2), Blocks.SLIME_BLOCK.defaultBlockState(), null);
                    CovFlightJoinSupport.fill(level, ground.offset(WALL_DX, 0, -WALL_HALF), ground.offset(WALL_DX, WALL_HEIGHT, WALL_HALF),
                            Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState(), null);
                    CovFlightJoinSupport.fill(level, ground.offset(-2, -1, -6), ground.offset(2, -1, -4), Blocks.HAY_BLOCK.defaultBlockState(), null);
                    track.padY = ground.getY();
                    player.teleportTo(level, ground.getX() + 0.5, ground.getY(), ground.getZ() - 5.5, Set.of(), 0.0F, 0.0F, true);
                    Constants.LOG.info("[Beitrittstest] Slime und Wolle: Slime-Feld 5 × 5, Heuballen 5 × 3 und Wollwand {} Blöcke östlich gebaut", WALL_DX);
                    CovFlightJoinSupport.startFpv(player, DroneBuild.FREESTYLE, ground.getX() + 0.5, ground.getY() + 0.05, ground.getZ() + 0.5, -90.0F,
                            "Slime und Wolle");
                }
                continue;
            }
            QuadcopterEntity drone = DronePiloting.pilotedBy(player.getUUID()).orElse(null);
            if (drone == null) {
                continue;
            }
            double y = drone.getY() - track.padY;
            if (tick % 5 == 0 && Double.isFinite(track.lastY) && track.reports < 400) {
                track.reports++;
                Constants.LOG.info("[Beitrittstest] Slime und Wolle: Drohne bei x {} y {} m, scharf {}", String.format(Locale.ROOT, "%.1f", drone.getX()),
                        String.format(Locale.ROOT, "%.2f", y), drone.isArmed());
            }
            track.lastY = y;
        }
    }
}
