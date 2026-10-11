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
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PilotJoinCheck {
    public static final int SETTLE_TICKS = 60;
    public static final double DROP = 8.0;
    public static final int REPORT_EVERY = 100;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private static final class Track {
        final int joined;
        int started = -1;
        double startY;
        boolean landed;

        Track(int joined) {
            this.joined = joined;
        }
    }

    private PilotJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            if (track.started < 0) {
                if (tick - track.joined >= SETTLE_TICKS) {
                    start(player, track, tick);
                }
                continue;
            }
            boolean piloting = DronePiloting.pilotedBy(player.getUUID()).isPresent();
            if (!track.landed && player.onGround() && tick - track.started > 5) {
                track.landed = true;
                Constants.LOG.info("[Beitrittstest] Pilotenkörper gelandet nach {} Ticks: {} Blöcke gefallen, Gesundheit {}, steuert {}",
                        tick - track.started, String.format(Locale.ROOT, "%.2f", track.startY - player.getY()),
                        player.getHealth(), piloting);
            }
            if ((tick - track.started) % REPORT_EVERY == 0) {
                Constants.LOG.info("[Beitrittstest] Pilot {} nach {} Ticks: steuert {}, am Boden {}, y {}, Gesundheit {}",
                        player.getPlainTextName(), tick - track.started, piloting, player.onGround(),
                        String.format(Locale.ROOT, "%.2f", player.getY()), player.getHealth());
            }
        }
    }

    private static void start(ServerPlayer player, Track track, int tick) {
        track.started = tick;
        ServerLevel level = player.level();
        player.setGameMode(GameType.SURVIVAL);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            Constants.LOG.error("[Beitrittstest] Pilot: Drohne nicht erzeugt");
            return;
        }
        drone.snapTo(player.getX() + 0.5, player.getY(), player.getZ() + 4.5, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        level.addFreshEntity(drone);
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.getInventory().setItem(0, remote);
        player.getInventory().setSelectedSlot(0);
        player.connection.send(new ClientboundSetHeldSlotPacket(0));
        player.teleportTo(level, player.getX(), player.getY() + DROP, player.getZ(), Set.of(), player.getYRot(), 0.0F, true);
        player.resetFallDistance();
        track.startY = player.getY();
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        Constants.LOG.info("[Beitrittstest] Pilot {} startet FPV {} Blöcke über dem Boden: {}", player.getPlainTextName(), DROP, result);
    }
}
