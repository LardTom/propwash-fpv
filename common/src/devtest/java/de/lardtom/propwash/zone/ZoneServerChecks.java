package de.lardtom.propwash.zone;

import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.AssistWords;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.server.WorldRulesRights;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class ZoneServerChecks {
    public static final String TUNNEL = "Rauchtest Tunnel";
    public static final String FIELD = "Rauchtest Übungsplatz";
    private static volatile String summary = "";

    private ZoneServerChecks() {
    }

    public static String summary() {
        return summary;
    }

    private static Zone named(MinecraftServer server, String dimension, String name) {
        return ZoneData.get(server).byName(dimension, name).orElseThrow(() -> new IllegalStateException("Zone fehlt: " + name));
    }

    public static @Nullable String check(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getRespawnData().pos();
        String dimension = ZoneServer.dimension(level);
        ServerPlayer guest = WorldRulesRights.guest(server);
        List<QuadcopterEntity> drones = new ArrayList<>();
        int before = ZoneData.get(server).all().size();
        try {
            if (server.getPlayerList().isOp(guest.nameAndId())) {
                return "Gast steht schon in der Operator-Liste";
            }
            Zone tunnel = Zone.create(dimension, TUNNEL, ZoneShape.CYLINDER, ZoneBox.cylinder(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() - 7.5, 4.0, 12.0))
                    .withRaceLegal(true).withOverride(WorldRuleSpecs.GRAVITY_SCALE, 0);
            Zone field = Zone.create(dimension, FIELD, ZoneShape.BOX, ZoneBox.blocks(spawn.getX() + 6, spawn.getY() - 1, spawn.getZ() - 4,
                    spawn.getX() + 14, spawn.getY() + 10, spawn.getZ() + 4)).withOverride(WorldRuleSpecs.BATTERY_SCALE, 0);
            if (ZoneServer.mayEdit(guest) || ZoneServer.snapshotFor(guest).editable()) {
                return "Gast ohne Recht darf Zonen ändern";
            }
            for (int i = 0; i < 20; i++) {
                if (ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.SAVE, tunnel), guest).ok()) {
                    return "Server übernimmt die Zone eines Gasts ohne Recht";
                }
            }
            if (ZoneData.get(server).all().size() != before) {
                return "Zone eines Gasts gespeichert";
            }
            if (run(server, guest.createCommandSourceStack(), "propwash zone create \"Gast\" box 0 0 0 1 1 1") != 0
                    || ZoneData.get(server).byName(dimension, "Gast").isPresent()) {
                return "Gast legt per Befehl eine Zone an";
            }
            server.getPlayerList().op(guest.nameAndId());
            if (!ZoneServer.mayEdit(guest) || !ZoneServer.snapshotFor(guest).editable()) {
                return "Operator darf Zonen nicht ändern";
            }
            if (!ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.SAVE, tunnel), guest).ok()
                    || !ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.SAVE, field), guest).ok()) {
                return "Server übernimmt die Zonen eines Operators nicht";
            }
            Zone savedTunnel = named(server, dimension, TUNNEL);
            Zone savedField = named(server, dimension, FIELD);
            if (ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.SAVE, field.withName(TUNNEL.toUpperCase(Locale.ROOT))), guest).ok()) {
                return "doppelter Name angenommen";
            }
            if (ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.SAVE, savedField.withBox(new ZoneBox(0, 0, 0, 5000, 4, 4))), guest).ok()) {
                return "zu große Zone angenommen";
            }
            if (ZoneServer.snapshotFor(guest).zones().size() != ZoneData.get(server).in(dimension).size()) {
                return "Sync enthält nicht alle Zonen der Dimension";
            }
            if (!ZoneServer.snapshotFor(guest).zones().contains(savedTunnel)) {
                return "Sync ohne den Tunnel";
            }
            var codec = ZoneData.TYPE.codec();
            ZoneData loaded = codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, ZoneData.get(server)).getOrThrow()).getOrThrow();
            if (!loaded.all().equals(ZoneData.get(server).all())) {
                return "Weltdaten der Zonen nicht verlustfrei";
            }
            QuadcopterEntity inTunnel = drone(level, spawn.getX() + 0.5, spawn.getY() + 3.0, spawn.getZ() - 7.5, drones);
            QuadcopterEntity inField = drone(level, spawn.getX() + 10.5, spawn.getY() + 3.0, spawn.getZ() + 0.5, drones);
            QuadcopterEntity outside = drone(level, spawn.getX() - 8.5, spawn.getY() + 3.0, spawn.getZ() + 8.5, drones);
            AssistReasons tunnelReasons = RaceServer.serverReasons(inTunnel);
            AssistReasons fieldReasons = RaceServer.serverReasons(inField);
            AssistReasons outsideReasons = RaceServer.serverReasons(outside);
            if (tunnelReasons.any()) {
                return "rennfreier Tunnel zählt als Flughilfe: " + tunnelReasons;
            }
            if (!fieldReasons.equals(AssistReasons.of(List.of(AssistDetail.zone(savedField.id(), WorldRuleSpecs.BATTERY_SCALE, 0))))) {
                return "Übungsplatz ohne Zonen-Grund: " + fieldReasons;
            }
            if (outsideReasons.any()) {
                return "außerhalb der Zonen trotzdem Flughilfe: " + outsideReasons;
            }
            String text = AssistWords.describe(fieldReasons).getString();
            if (!text.contains(FIELD)) {
                return "Grund nennt die Zone nicht: " + text;
            }
            ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.SAVE, savedTunnel.withRaceLegal(false)), guest);
            AssistReasons unlocked = RaceServer.serverReasons(inTunnel);
            if (!unlocked.has(LapAssist.ZONE_PHYSICS) || unlocked.details(LapAssist.ZONE_PHYSICS).getFirst().reference() != savedTunnel.id()) {
                return "Tunnel ohne Rennfreigabe zählt nicht als Flughilfe: " + unlocked;
            }
            server.getPlayerList().deop(guest.nameAndId());
            if (ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.DELETE, savedTunnel), guest).ok() || ZoneData.get(server).get(savedTunnel.id()) == null) {
                return "Gast löscht nach dem Entzug der Rechte eine Zone";
            }
            summary = "Gast abgelehnt (Paket und Befehl), Operator legt an, Sync je Dimension, Weltdaten verlustfrei, Tunnel rennfrei ohne Flughilfe, "
                    + "Übungsplatz: " + text + ", außerhalb sauber";
            return null;
        } finally {
            if (server.getPlayerList().isOp(guest.nameAndId())) {
                server.getPlayerList().deop(guest.nameAndId());
            }
            for (QuadcopterEntity drone : drones) {
                drone.discard();
            }
            for (String name : List.of(TUNNEL, FIELD)) {
                ZoneData.get(server).byName(dimension, name).ifPresent(zone -> ZoneData.get(server).remove(zone.id()));
            }
        }
    }

    public static int run(MinecraftServer server, CommandSourceStack source, String command) {
        AtomicInteger result = new AtomicInteger();
        server.getCommands().performPrefixedCommand(source.withCallback((success, value) -> result.set(success ? value : 0)), command);
        return result.get();
    }

    private static QuadcopterEntity drone(ServerLevel level, double x, double y, double z, List<QuadcopterEntity> out) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.snapTo(x, y, z, 0.0F, 0.0F);
        out.add(drone);
        return drone;
    }
}
