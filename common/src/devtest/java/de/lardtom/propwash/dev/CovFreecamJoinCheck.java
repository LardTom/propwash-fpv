package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CovFreecamJoinCheck {
    public static final int RULE_ON_TICKS = 20 * 15;
    public static final int RESTORE_TICKS = 20 * 30;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private static final class Track {
        final String name;
        final int joined;
        int stage;
        int mark;
        int joins = 1;

        Track(String name, int joined) {
            this.name = name;
            this.joined = joined;
        }
    }

    private CovFreecamJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            Track track = TRACKS.get(player.getUUID());
            if (track == null) {
                server.getPlayerList().deop(player.nameAndId());
                WorldRules.set(server, WorldRuleSpecs.ALLOW_FREECAM, WorldRuleSpecs.ALLOW_FREECAM.defaultValue());
                Constants.LOG.info("[Beitrittstest] Freecam-Abdeckung: {} beigetreten, Operator {}, Regel allow_freecam {}", player.getPlainTextName(),
                        server.getPlayerList().isOp(player.nameAndId()) ? "ja" : "nein", WorldRules.on(server, WorldRuleSpecs.ALLOW_FREECAM) ? "an" : "aus");
                TRACKS.put(player.getUUID(), new Track(player.getPlainTextName(), tick));
            } else if (track.stage == 2) {
                track.joins++;
                track.stage = 3;
                track.mark = tick;
                Constants.LOG.info("[Beitrittstest] Freecam-Abdeckung: {} wieder beigetreten ({}. Beitritt), Regel allow_freecam {}", track.name, track.joins,
                        WorldRules.on(server, WorldRuleSpecs.ALLOW_FREECAM) ? "an" : "aus");
            }
        }
        for (Map.Entry<UUID, Track> entry : TRACKS.entrySet()) {
            Track track = entry.getValue();
            boolean present = online.contains(entry.getKey());
            switch (track.stage) {
                case 0 -> {
                    if (tick - track.joined >= RULE_ON_TICKS) {
                        WorldRules.set(server, WorldRuleSpecs.ALLOW_FREECAM, 1);
                        Constants.LOG.info("[Beitrittstest] Freecam-Abdeckung: Regel allow_freecam an");
                        track.stage = 1;
                        track.mark = tick;
                    }
                }
                case 1 -> {
                    if (!present) {
                        Constants.LOG.info("[Beitrittstest] Freecam-Abdeckung: {} hat die Verbindung getrennt", track.name);
                        track.stage = 2;
                        track.mark = tick;
                    }
                }
                case 3 -> {
                    if (tick - track.mark >= RESTORE_TICKS) {
                        WorldRules.set(server, WorldRuleSpecs.ALLOW_FREECAM, WorldRuleSpecs.ALLOW_FREECAM.defaultValue());
                        Constants.LOG.info("[Beitrittstest] Freecam-Abdeckung: Regel allow_freecam zurück auf Standard (aus)");
                        track.stage = 4;
                    }
                }
                default -> {
                }
            }
        }
    }
}
