package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.stats.PilotStats;
import de.lardtom.propwash.stats.PropwashStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

public final class StatsJoinCheck {
    public static final String RESULT = "[stats] ";
    public static final String SERVER = RESULT + "server ";
    public static final int SETTLE_TICKS = 60;
    public static final int QUIET_TICKS = 60;
    public static final String FPV_LOADED = "fpv_loaded";
    public static final String FPV_FLOWN = "fpv_flown";
    public static final String FPV_DONE = "fpv_done";
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private static final class Track {
        final int joined;
        int droneId = -1;
        Map<String, Integer> before = Map.of();
        boolean armedSeen;
        int quietSince = -1;
        boolean reported;

        Track(int joined) {
            this.joined = joined;
        }
    }

    private StatsJoinCheck() {
    }

    public static Map<String, Integer> values(ServerPlayer player) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (PropwashStats.Entry entry : PropwashStats.all()) {
            values.put(entry.name(), PilotStats.value(player, entry));
        }
        return values;
    }

    public static Map<String, Integer> delta(Map<String, Integer> before, Map<String, Integer> after) {
        Map<String, Integer> delta = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : after.entrySet()) {
            int change = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
            if (change != 0) {
                delta.put(entry.getKey(), change);
            }
        }
        return delta;
    }

    public static String encode(Map<String, Integer> values) {
        StringJoiner joiner = new StringJoiner(",");
        values.forEach((name, value) -> joiner.add(name + "=" + value));
        return joiner.toString();
    }

    public static Map<String, Integer> decode(String text) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String part : text.trim().split(",")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                try {
                    values.put(part.substring(0, eq).trim(), Integer.parseInt(part.substring(eq + 1).trim()));
                } catch (NumberFormatException ignored) {
                    values.put(part.substring(0, eq).trim(), Integer.MIN_VALUE);
                }
            }
        }
        return values;
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            if (track.reported) {
                continue;
            }
            if (track.droneId < 0) {
                if (tick - track.joined >= SETTLE_TICKS) {
                    start(player, track);
                }
                continue;
            }
            if (!(player.level().getEntity(track.droneId) instanceof QuadcopterEntity drone)) {
                continue;
            }
            if (drone.isArmed()) {
                track.armedSeen = true;
                track.quietSince = -1;
                continue;
            }
            if (!track.armedSeen) {
                continue;
            }
            if (track.quietSince < 0) {
                track.quietSince = tick;
            }
            if (tick - track.quietSince < QUIET_TICKS) {
                continue;
            }
            track.reported = true;
            Map<String, Integer> after = values(player);
            Map<String, Integer> delta = delta(track.before, after);
            DronePiloting.release(drone);
            drone.discard();
            fpv(server, player, delta);
            Constants.LOG.info("[Beitrittstest] Statistik: Server zählt {} (gesamt {})", delta, after);
            player.sendSystemMessage(Component.literal(SERVER + encode(after) + " | delta " + encode(delta)));
        }
    }

    private static void fpv(MinecraftServer server, ServerPlayer player, Map<String, Integer> delta) {
        delta.put(FPV_LOADED, FpvAdvancementCases.loaded(server));
        delta.put(FPV_FLOWN, FpvAdvancementCases.missing(server, player, FpvAdvancementCases.FLOWN).isEmpty() ? 1 : 0);
        List<String> cases = FpvAdvancementCases.cases(server, player);
        delta.put(FPV_DONE, FpvAdvancementCases.NAMES.size() - FpvAdvancementCases.missing(server, player, FpvAdvancementCases.NAMES).size());
        Constants.LOG.info("[Beitrittstest] Fortschritte: {} geladen, aus dem Flug {}, Pakete des Clients {}, Wege am Server {}", delta.get(FPV_LOADED),
                delta.get(FPV_FLOWN) == 1 ? "erreicht" : "fehlen", FpvAdvancementCases.missing(server, player, FpvAdvancementCases.CLIENT).isEmpty()
                        ? "angekommen" : "fehlen " + FpvAdvancementCases.missing(server, player, FpvAdvancementCases.CLIENT), String.join("; ", cases));
    }

    private static void start(ServerPlayer player, Track track) {
        ServerLevel level = player.level();
        int x = player.getBlockX() + 2;
        int z = player.getBlockZ() - 4;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            Constants.LOG.error("[Beitrittstest] Statistik: Drohne nicht erzeugt");
            track.reported = true;
            return;
        }
        drone.snapTo(x + 0.5, ground + 0.05, z + 0.5, 180.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        level.addFreshEntity(drone);
        track.droneId = drone.getId();
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.getInventory().setItem(0, remote);
        player.getInventory().setSelectedSlot(0);
        player.connection.send(new ClientboundSetHeldSlotPacket(0));
        track.before = values(player);
        FpvAdvancementCases.revokeAll(level.getServer(), player);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        Constants.LOG.info("[Beitrittstest] Statistik: Drohne bei {} {} {}, FPV {}, Zählung {}, vorher {}", x, ground, z, result,
                PilotStats.tracking(drone), track.before);
    }
}
