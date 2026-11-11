package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FreecamJoinCheck {
    public static final int RULE_ON_TICKS = 20 * 20;
    public static final int RULE_OFF_TICKS = 20 * 15;
    public static final int REPORT_TICKS = 20 * 5;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private static final class Track {
        final int joined;
        final int drones;
        int stage;
        int mark;

        Track(int joined, int drones) {
            this.joined = joined;
            this.drones = drones;
        }
    }

    private FreecamJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TRACKS.computeIfAbsent(player.getUUID(), id -> {
                int drones = drones(server);
                Constants.LOG.info("[Beitrittstest] Freecam: {} beigetreten, Regel allow_freecam {}, {} Drohnen am Server",
                        player.getGameProfile().name(), WorldRules.on(server, WorldRuleSpecs.ALLOW_FREECAM) ? "an" : "aus (Standard)", drones);
                return new Track(tick, drones);
            });
        }
        for (Track track : TRACKS.values()) {
            step(server, track, tick);
        }
    }

    private static void step(MinecraftServer server, Track track, int tick) {
        switch (track.stage) {
            case 0 -> {
                if (tick - track.joined >= RULE_ON_TICKS) {
                    WorldRules.set(server, WorldRuleSpecs.ALLOW_FREECAM, 1);
                    Constants.LOG.info("[Beitrittstest] Freecam: Regel allow_freecam an");
                    track.stage = 1;
                    track.mark = tick;
                }
            }
            case 1 -> {
                if (tick - track.mark >= RULE_OFF_TICKS) {
                    WorldRules.set(server, WorldRuleSpecs.ALLOW_FREECAM, 0);
                    Constants.LOG.info("[Beitrittstest] Freecam: Regel allow_freecam aus");
                    track.stage = 2;
                    track.mark = tick;
                }
            }
            case 2 -> {
                if (tick - track.mark >= REPORT_TICKS) {
                    int drones = drones(server);
                    Constants.LOG.info("[Beitrittstest] Freecam: {} neue Drohnen (vorher {}, jetzt {})", drones - track.drones, track.drones, drones);
                    track.stage = 3;
                }
            }
            default -> {
            }
        }
    }

    private static int drones(MinecraftServer server) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            count += level.getEntities(DroneModule.DRONE.get(), entity -> true).size();
        }
        return count;
    }
}
