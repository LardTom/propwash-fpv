package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.input.AxisSpec;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.addon.AddonInputState;
import de.lardtom.propwash.input.addon.AddonInputs;
import de.lardtom.propwash.input.addon.AddonSwitchServer;
import de.lardtom.propwash.input.net.AddonFeaturesS2C;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class Addon17JoinCheck {
    public static final int FLY_AFTER_TICKS = 60;
    public static final double AXIS_STEP = 0.05;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();
    private static final Map<UUID, NameAndId> OPPED = new LinkedHashMap<>();
    private static boolean initialized;

    private static final class Track {
        final int joined;
        int flying = -1;
        int droneId = -1;
        final Map<Identifier, Double> axes = new HashMap<>();

        Track(int joined) {
            this.joined = joined;
        }
    }

    private Addon17JoinCheck() {
    }

    static synchronized void init() {
        if (initialized || !JoinTestEnv.addon17()) {
            return;
        }
        initialized = true;
        ServerBus.PLAYER_JOIN.register(Addon17JoinCheck::joined);
        ServerBus.PLAYER_LEAVE.register(player -> {
            MinecraftServer server = player.level().getServer();
            if (server != null) {
                deop(server, player.getUUID(), "nach dem Verlassen");
            }
        });
        ServerBus.STOPPED.register(server -> {
            for (UUID id : new ArrayList<>(OPPED.keySet())) {
                deop(server, id, "beim Stopp des Servers");
            }
        });
        PropwashEvents.ADDON_SWITCH_CHANGED.register((player, drone, id, active) ->
                Constants.LOG.info("[Beitrittstest] Add-on-Schalter: ADDON_SWITCH_CHANGED {} {}={} an Drohne {}", player.getPlainTextName(), id, active,
                        drone.getId()));
    }

    private static void joined(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        NameAndId id = player.nameAndId();
        if (!server.getPlayerList().isOp(id)) {
            server.getPlayerList().op(id);
            OPPED.put(player.getUUID(), id);
        }
        AddonFeaturesS2C features = AddonSwitchServer.features();
        Constants.LOG.info("[Beitrittstest] Add-on-Schalter: {} ist Operator, addon_features: Schalter {}, Achsen {}, Kollisionsregeln {}",
                player.getPlainTextName(), features.switches(), features.axes(), features.collisionRules().size());
    }

    private static void deop(MinecraftServer server, UUID player, String when) {
        NameAndId id = OPPED.remove(player);
        if (id == null) {
            return;
        }
        server.getPlayerList().deop(id);
        Constants.LOG.info("[Beitrittstest] Add-on-Schalter: {} ist kein Operator mehr ({})", id.name(), when);
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            if (track.flying < 0) {
                if (tick - track.joined >= FLY_AFTER_TICKS) {
                    fly(player, track, tick);
                }
                continue;
            }
            if (player.level().getEntity(track.droneId) instanceof QuadcopterEntity drone) {
                report(drone, track);
            }
        }
    }

    private static void fly(ServerPlayer player, Track track, int tick) {
        track.flying = tick;
        ServerLevel level = player.level();
        int x = player.getBlockX() + 3;
        int z = player.getBlockZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            Constants.LOG.error("[Beitrittstest] Add-on-Schalter: Drohne nicht erzeugt");
            return;
        }
        drone.snapTo(x + 0.5, y + 0.1, z + 0.5, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        level.addFreshEntity(drone);
        track.droneId = drone.getId();
        player.setGameMode(GameType.CREATIVE);
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.getInventory().setItem(0, remote);
        player.getInventory().setSelectedSlot(0);
        player.connection.send(new ClientboundSetHeldSlotPacket(0));
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        Constants.LOG.info("[Beitrittstest] Add-on-Schalter: FPV für {} an Drohne {}: {}", player.getPlainTextName(), drone.getId(), result);
    }

    private static void report(QuadcopterEntity drone, Track track) {
        for (AxisSpec spec : AddonInputs.axes()) {
            if (!spec.serverSync()) {
                continue;
            }
            double value = AddonInputState.axis(drone, spec.id(), spec.center());
            Double last = track.axes.get(spec.id());
            if (last == null || Math.abs(value - last) >= AXIS_STEP) {
                track.axes.put(spec.id(), value);
                if (last != null) {
                    Constants.LOG.info("[Beitrittstest] Add-on-Achse: {}={} an Drohne {}", spec.id(), String.format(Locale.ROOT, "%.3f", value),
                            drone.getId());
                }
            }
        }
    }
}
