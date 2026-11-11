package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.interact.CovVineArena;
import de.lardtom.propwash.interact.InteractRules;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CovVineJoinCheck {
    public static final int SETTLE_TICKS = 60;
    public static final int START_DELAY_TICKS = 200;
    public static final int CYCLE_TICKS = 120;
    private static final int SCENE_DX = 20;
    private static final double PENETRATION = 0.01;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private static final class Track {
        final int joined;
        int @Nullable [] base;
        int started;
        @Nullable QuadcopterEntity drone;
        int flight = -1;
        int insideTicks;
        int totalInside;
        boolean reported;

        Track(int joined) {
            this.joined = joined;
        }
    }

    private CovVineJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        List<CovVineArena.Flight> flights = CovVineArena.joinFlights();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            ServerLevel level = player.level();
            int[] base = track.base;
            if (base == null) {
                if (tick - track.joined < SETTLE_TICKS) {
                    continue;
                }
                int x = player.getBlockX() + SCENE_DX;
                int z = player.getBlockZ();
                level.getChunk(x >> 4, z >> 4);
                base = new int[]{x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z};
                track.base = base;
                WorldRules.set(server, InteractRules.breakRule(), false);
                WorldRules.set(server, WorldRuleSpecs.CRASH_DISARM, 0);
                CovVineArena.build(level, base);
                player.teleportTo(level, base[0] - 6.5, base[1], base[2] - 5.5, Set.of(), -60.0F, 15.0F, true);
                track.drone = CovFlightJoinSupport.startFpv(player, DroneBuild.FREESTYLE, base[0] - 1.5, base[1], base[2] + 0.5, -90.0F,
                        "Ranken-Balken von oben");
                track.started = tick;
                continue;
            }
            QuadcopterEntity drone = track.drone;
            if (drone == null || drone.isRemoved()) {
                continue;
            }
            int elapsed = tick - track.started - START_DELAY_TICKS;
            if (elapsed < 0) {
                continue;
            }
            int index = elapsed / CYCLE_TICKS;
            if (elapsed % CYCLE_TICKS == 0) {
                if (track.flight >= 0) {
                    Constants.LOG.info("[Beitrittstest] Ranken-Balken von oben: Sturz {}/{} am Server: {} Ticks im Block, Lage am Ende {} {} {}",
                            track.flight + 1, flights.size(), track.insideTicks, fmt(drone.getX() - base[0]), fmt(drone.getY() - base[1]),
                            fmt(drone.getZ() - base[2]));
                    track.totalInside += track.insideTicks;
                }
                if (index < flights.size()) {
                    track.flight = index;
                    track.insideTicks = 0;
                    Vec3 at = CovVineArena.launch(base, flights.get(index));
                    drone.setWear(PartWear.PRISTINE);
                    drone.resetTo(at, CovVineArena.facing());
                } else if (!track.reported) {
                    track.reported = true;
                    Constants.LOG.info("[Beitrittstest] Ranken-Balken von oben am Server: {} Stürze, zusammen {} Ticks im Block", flights.size(),
                            track.totalInside);
                    WorldRules.set(server, WorldRuleSpecs.CRASH_DISARM, 1);
                }
                continue;
            }
            if (index < flights.size()) {
                AABB box = drone.getBoundingBox();
                if (!level.noBlockCollision(drone, box.deflate(PENETRATION))) {
                    track.insideTicks++;
                }
            }
        }
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
