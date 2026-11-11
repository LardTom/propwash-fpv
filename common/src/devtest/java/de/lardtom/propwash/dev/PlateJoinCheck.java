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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PlateJoinCheck {
    public static final int BUILD_AFTER_TICKS = 60;
    public static final int PLATE_DX = 3;
    public static final double DROP = 0.75;
    public static final int REPORT_EVERY = 40;
    public static final int GIVE_UP_TICKS = 20 * 90;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private static final class Track {
        final int joined;
        int built = -1;
        int powered = -1;
        int opened = -1;
        int droneId = -1;
        BlockPos plate = BlockPos.ZERO;
        boolean done;

        Track(int joined) {
            this.joined = joined;
        }
    }

    private PlateJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            if (track.built < 0) {
                if (tick - track.joined >= BUILD_AFTER_TICKS) {
                    build(player, track, tick);
                }
                continue;
            }
            if (!track.done) {
                observe(player, track, tick);
            }
        }
    }

    private static void build(ServerPlayer player, Track track, int tick) {
        track.built = tick;
        ServerLevel level = player.level();
        int x = player.getBlockX() + PLATE_DX;
        int z = player.getBlockZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlockAndUpdate(new BlockPos(x + dx, y - 1, z + dz), Blocks.STONE.defaultBlockState());
                for (int dy = 3; dy >= 0; dy--) {
                    level.setBlockAndUpdate(new BlockPos(x + dx, y + dy, z + dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockPos plate = new BlockPos(x, y, z);
        BlockPos door = plate.east();
        level.setBlockAndUpdate(plate, Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        level.setBlock(door, lower, Block.UPDATE_ALL);
        level.setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        track.plate = plate;
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            Constants.LOG.error("[Beitrittstest] Druckplatte: Drohne nicht erzeugt");
            track.done = true;
            return;
        }
        drone.snapTo(x + 0.5, y + DROP, z + 0.5, 0.0F, 0.0F);
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
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        Constants.LOG.info("[Beitrittstest] Druckplatte: Holzplatte bei {} {} {} mit Tür östlich daneben, Drohne {} Blöcke darüber, FPV für {}: {}",
                x, y, z, DROP, player.getPlainTextName(), result);
    }

    private static void observe(ServerPlayer player, Track track, int tick) {
        ServerLevel level = player.level();
        BlockState plate = level.getBlockState(track.plate);
        BlockState door = level.getBlockState(track.plate.east());
        QuadcopterEntity drone = level.getEntity(track.droneId) instanceof QuadcopterEntity found ? found : null;
        boolean powered = plate.is(Blocks.OAK_PRESSURE_PLATE) && plate.getValue(PressurePlateBlock.POWERED);
        boolean open = door.is(Blocks.OAK_DOOR) && door.getValue(DoorBlock.OPEN);
        String where = drone == null ? "Drohne fehlt" : String.format(Locale.ROOT, "Drohne %.2f über der Platte, gesteuert %s, vom Client %s",
                drone.getY() - track.plate.getY(), drone.pilot().isPresent(), drone.isClientAuthoritative());
        if (powered && track.powered < 0) {
            track.powered = tick;
            Constants.LOG.info("[Beitrittstest] Druckplatte: an nach {} Ticks ({})", tick - track.built, where);
        }
        if (open && track.opened < 0) {
            track.opened = tick;
            Constants.LOG.info("[Beitrittstest] Druckplatte: Tür offen nach {} Ticks", tick - track.built);
        }
        if (track.powered >= 0 && track.opened >= 0) {
            track.done = true;
            return;
        }
        if ((tick - track.built) % REPORT_EVERY == 0) {
            Constants.LOG.info("[Beitrittstest] Druckplatte nach {} Ticks: Platte {}, Tür {} ({})", tick - track.built,
                    powered ? "an" : "aus", open ? "offen" : "zu", where);
        }
        if (tick - track.built > GIVE_UP_TICKS) {
            track.done = true;
            Constants.LOG.warn("[Beitrittstest] Druckplatte: nach {} Ticks nicht ausgelöst ({})", GIVE_UP_TICKS, where);
        }
    }
}
