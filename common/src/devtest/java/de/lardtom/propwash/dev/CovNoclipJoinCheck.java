package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.guard.NamespaceGuard;
import de.lardtom.propwash.platform.Services;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CovNoclipJoinCheck {
    public static final int FLY_AFTER_TICKS = 60;
    private static final Map<UUID, Integer> JOINED = new HashMap<>();
    private static boolean initialized;

    private CovNoclipJoinCheck() {
    }

    static synchronized void init() {
        if (initialized || !JoinTestEnv.covNoclip()) {
            return;
        }
        initialized = true;
        ServerBus.PLAYER_JOIN.register(CovNoclipJoinCheck::joined);
    }

    private static void joined(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        server.getPlayerList().op(player.nameAndId());
        Constants.LOG.info("[Beitrittstest] Noclip: {} ist Operator, Noclip am Server {}, Funde {}", player.getPlainTextName(),
                Services.PLATFORM.isModLoaded("pwnoclip"), NamespaceGuard.report().summary());
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int joined = JOINED.computeIfAbsent(player.getUUID(), id -> tick);
            if (joined >= 0 && tick - joined >= FLY_AFTER_TICKS) {
                JOINED.put(player.getUUID(), Integer.MIN_VALUE / 2);
                fly(player);
            }
        }
    }

    private static void fly(ServerPlayer player) {
        ServerLevel level = player.level();
        int x = player.getBlockX() + 3;
        int z = player.getBlockZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            Constants.LOG.error("[Beitrittstest] Noclip: Drohne nicht erzeugt");
            return;
        }
        drone.snapTo(x + 0.5, y + 0.1, z + 0.5, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        level.addFreshEntity(drone);
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
        Constants.LOG.info("[Beitrittstest] Noclip: FPV für {} an Drohne {}: {}", player.getPlainTextName(), drone.getId(), result);
    }
}
