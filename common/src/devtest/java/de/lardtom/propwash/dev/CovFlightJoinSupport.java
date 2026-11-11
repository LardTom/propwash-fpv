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
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public final class CovFlightJoinSupport {
    private CovFlightJoinSupport() {
    }

    static void fill(ServerLevel level, BlockPos from, BlockPos to, BlockState state, @Nullable Map<BlockPos, BlockState> original) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (original != null) {
                original.putIfAbsent(pos.immutable(), level.getBlockState(pos));
            }
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
    }

    static void clearAbove(ServerLevel level, BlockPos ground, int half, int height, @Nullable Map<BlockPos, BlockState> original) {
        fill(level, ground.offset(-half, 0, -half), ground.offset(half, height, half), Blocks.AIR.defaultBlockState(), original);
        fill(level, ground.offset(-half, -1, -half), ground.offset(half, -1, half), Blocks.STONE.defaultBlockState(), original);
    }

    static @Nullable QuadcopterEntity startFpv(ServerPlayer player, DroneBuild build, double x, double y, double z, float yaw, String what) {
        ServerLevel level = player.level();
        player.setGameMode(GameType.CREATIVE);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            Constants.LOG.error("[Beitrittstest] {}: Drohne nicht erzeugt", what);
            return null;
        }
        drone.snapTo(x, y, z, yaw, 0.0F);
        drone.setAttitude(new Quaternionf().rotationY((float) Math.toRadians(180.0 - yaw)));
        drone.setBuild(build);
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
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        Constants.LOG.info("[Beitrittstest] {}: {} startet FPV mit {}: {}", what, player.getPlainTextName(), build.frame().key(), result);
        return drone;
    }

    static double tiltDeg(QuadcopterEntity drone) {
        Vector3f up = new Quaternionf(drone.orientation()).transform(new Vector3f(0.0F, 1.0F, 0.0F));
        return Math.toDegrees(Math.acos(Math.clamp(up.y, -1.0F, 1.0F)));
    }
}
