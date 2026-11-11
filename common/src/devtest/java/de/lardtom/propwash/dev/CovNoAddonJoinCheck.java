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
import de.lardtom.propwash.input.addon.AddonInputs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CovNoAddonJoinCheck {
    public static final int SETTLE_TICKS = 60;
    public static final int RIG_DZ = 4;
    public static final int WALL_DX = 4;
    public static final int WALL_THICK = 3;
    public static final double START_DX = 1.5;
    public static final double HOVER = 2.0;
    private static final int CLEAR_BACK = 3;
    private static final int CLEAR_FRONT = 14;
    private static final int CLEAR_SIDE = 3;
    private static final int CLEAR_HEIGHT = 12;
    private static final int WALL_HALF = 2;
    private static final int WALL_HEIGHT = 5;
    private static final int REPORT_TICKS = 100;
    private static final Quaternionf FACING_WALL = new Quaternionf().rotationY((float) (-Math.PI / 2.0));
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private static final class Track {
        final int joined;
        int stage;
        int x;
        int y;
        int z;
        int droneId = -1;
        int epoch;
        int reported = -1;
        double furthest = Double.NEGATIVE_INFINITY;

        Track(int joined) {
            this.joined = joined;
        }

        Vec3 ground() {
            return new Vec3(this.x + START_DX, this.y, this.z + 0.5);
        }
    }

    private CovNoAddonJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            try {
                step(player, track, tick);
            } catch (RuntimeException e) {
                Constants.LOG.error("[Beitrittstest] Kollision ohne Add-on: Stufe {} fehlgeschlagen", track.stage, e);
                track.stage = 9;
            }
        }
    }

    private static void step(ServerPlayer player, Track track, int tick) {
        switch (track.stage) {
            case 0 -> {
                if (tick - track.joined >= SETTLE_TICKS) {
                    start(player, track);
                    track.stage = 1;
                }
            }
            case 1 -> {
                if (!(player.level().getEntity(track.droneId) instanceof QuadcopterEntity drone)) {
                    return;
                }
                if (drone.isArmed()) {
                    drone.resetTo(track.ground().add(0.0, HOVER, 0.0), FACING_WALL);
                    track.epoch = drone.resetEpoch();
                    Constants.LOG.info("[Beitrittstest] Kollision ohne Add-on: Drohne scharf, vor die Wand {} bis {} gesetzt (Server ohne Beispiel-Add-on: "
                            + "Schalter {}, Kollisionsregeln am Server {})", track.x + WALL_DX, track.x + WALL_DX + WALL_THICK, AddonInputs.switches().size(),
                            de.lardtom.propwash.drone.collision.CollisionRules.rules().size());
                    track.stage = 2;
                    track.reported = tick;
                }
            }
            case 2 -> {
                if (!(player.level().getEntity(track.droneId) instanceof QuadcopterEntity drone)) {
                    return;
                }
                track.furthest = Math.max(track.furthest, drone.getX());
                if (tick - track.reported >= REPORT_TICKS) {
                    track.reported = tick;
                    Constants.LOG.info("[Beitrittstest] Kollision ohne Add-on: Rücksetzungen am Server {}, Server-x {} (weitester {}), Wand {} bis {}",
                            drone.resetEpoch() - track.epoch, String.format(Locale.ROOT, "%.2f", drone.getX()),
                            String.format(Locale.ROOT, "%.2f", track.furthest), track.x + WALL_DX, track.x + WALL_DX + WALL_THICK);
                }
            }
            default -> {
            }
        }
    }

    private static void start(ServerPlayer player, Track track) {
        ServerLevel level = player.level();
        track.x = player.getBlockX();
        track.y = player.getBlockY();
        track.z = player.getBlockZ() + RIG_DZ;
        for (int dx = -CLEAR_BACK; dx <= CLEAR_FRONT; dx++) {
            for (int dz = -CLEAR_SIDE; dz <= CLEAR_SIDE; dz++) {
                level.setBlockAndUpdate(new BlockPos(track.x + dx, track.y - 1, track.z + dz), Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy < CLEAR_HEIGHT; dy++) {
                    boolean wall = dx >= WALL_DX && dx < WALL_DX + WALL_THICK && Math.abs(dz) <= WALL_HALF && dy < WALL_HEIGHT;
                    level.setBlockAndUpdate(new BlockPos(track.x + dx, track.y + dy, track.z + dz),
                            wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        Vec3 ground = track.ground();
        drone.snapTo(ground.x, ground.y, ground.z, 0.0F, 0.0F);
        drone.setAttitude(FACING_WALL);
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
        Constants.LOG.info("[Beitrittstest] Kollision ohne Add-on: Testfeld bei {} {} {}, Drohne {} vor einer {} Blöcke dicken Wand, FPV {}",
                track.x, track.y, track.z, drone.getId(), WALL_THICK, result);
    }
}
