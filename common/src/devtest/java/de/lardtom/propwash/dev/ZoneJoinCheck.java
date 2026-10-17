package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.AssistWords;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.server.WorldRulesRights;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneBox;
import de.lardtom.propwash.zone.ZoneData;
import de.lardtom.propwash.zone.ZoneEditC2S;
import de.lardtom.propwash.zone.ZoneServer;
import de.lardtom.propwash.zone.ZoneShape;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ZoneJoinCheck {
    public static final String FLOAT = "Beitrittstest Schwerelos";
    public static final String FIELD = "Beitrittstest Übungsplatz";
    public static final int CREATE_AFTER_TICKS = 40;
    public static final int UNLOCK_AFTER_TICKS = 400;
    public static final int REPORT_AFTER_TICKS = 460;
    public static final int DELETE_AFTER_TICKS = 600;
    private static final Map<UUID, Integer> JOINED = new HashMap<>();

    private ZoneJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int since = tick - JOINED.computeIfAbsent(player.getUUID(), id -> tick);
            if (since == CREATE_AFTER_TICKS) {
                create(server, player);
            } else if (since == UNLOCK_AFTER_TICKS) {
                admin(server, ZoneEditC2S.Action.SAVE, find(server, player, FLOAT).map(zone -> zone.withRaceLegal(false)));
                Constants.LOG.info("[Beitrittstest] Zonen: {} zählt ab jetzt als Flughilfe", FLOAT);
            } else if (since == REPORT_AFTER_TICKS) {
                report(player);
            } else if (since == DELETE_AFTER_TICKS) {
                admin(server, ZoneEditC2S.Action.DELETE, find(server, player, FLOAT));
                admin(server, ZoneEditC2S.Action.DELETE, find(server, player, FIELD));
                Constants.LOG.info("[Beitrittstest] Zonen: beide Zonen gelöscht");
            }
        }
    }

    private static Optional<Zone> find(MinecraftServer server, ServerPlayer player, String name) {
        return ZoneData.get(server).byName(ZoneServer.dimension(player.level()), name);
    }

    private static void create(MinecraftServer server, ServerPlayer player) {
        String dimension = ZoneServer.dimension(player.level());
        double x = Math.floor(player.getX()) + 0.5;
        double z = Math.floor(player.getZ()) + 0.5;
        Zone zeroG = Zone.create(dimension, FLOAT, ZoneShape.BOX, ZoneBox.around(x, Math.floor(player.getY()) - 4.0, z, 40.0, 48.0, 40.0))
                .withRaceLegal(true).withTransition(5).withOverride(WorldRuleSpecs.GRAVITY_SCALE, 0).withOverride(WorldRuleSpecs.WIND, 0);
        Zone field = Zone.create(dimension, FIELD, ZoneShape.CYLINDER, ZoneBox.cylinder(x + 60.0, Math.floor(player.getY()), z, 8.0, 10.0))
                .withOverride(WorldRuleSpecs.BATTERY_SCALE, 0);
        admin(server, ZoneEditC2S.Action.SAVE, Optional.of(zeroG));
        admin(server, ZoneEditC2S.Action.SAVE, Optional.of(field));
        Constants.LOG.info("[Beitrittstest] Zonen: {} (rennfrei, Schwerkraft 0 %) um {} und {} angelegt, {} darf Zonen ändern: {}", FLOAT,
                player.getPlainTextName(), FIELD, player.getPlainTextName(), ZoneServer.mayEdit(player));
    }

    private static void admin(MinecraftServer server, ZoneEditC2S.Action action, Optional<Zone> zone) {
        if (zone.isEmpty()) {
            Constants.LOG.warn("[Beitrittstest] Zonen: Zone fehlt für {}", action);
            return;
        }
        ServerPlayer admin = WorldRulesRights.guest(server);
        server.getPlayerList().op(admin.nameAndId());
        try {
            ZoneServer.Outcome outcome = ZoneServer.edit(new ZoneEditC2S(action, zone.get()), admin);
            if (!outcome.ok()) {
                Constants.LOG.warn("[Beitrittstest] Zonen: {} {} abgelehnt: {}", action, zone.get().name(), outcome.problem());
            }
        } finally {
            server.getPlayerList().deop(admin.nameAndId());
        }
    }

    private static void report(ServerPlayer player) {
        Optional<QuadcopterEntity> drone = DronePiloting.pilotedBy(player.getUUID());
        if (drone.isEmpty()) {
            Constants.LOG.info("[Beitrittstest] Zonen: {} steuert keine Drohne", player.getPlainTextName());
            return;
        }
        AssistReasons reasons = RaceServer.serverReasons(drone.get());
        Constants.LOG.info("[Beitrittstest] Zonen: Server-Gründe für die Drohne von {} bei y {}: {}", player.getPlainTextName(),
                String.format(Locale.ROOT, "%.1f", drone.get().getY()), reasons.none() ? "keine" : AssistWords.describe(reasons).getString());
    }
}
