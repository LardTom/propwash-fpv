package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class VtxFixture {
    static final String CRAFT_NAME = "VTX Test";

    private static volatile int droneId = -1;
    private static volatile @Nullable ItemStack previousHead;
    private static volatile @Nullable ItemStack previousHand;
    private static volatile List<BlockPos> wall = List.of();

    private VtxFixture() {
    }

    static int droneId() {
        return droneId;
    }

    static @Nullable QuadcopterEntity clientDrone() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || droneId < 0) {
            return null;
        }
        return minecraft.level.getEntity(droneId) instanceof QuadcopterEntity drone ? drone : null;
    }

    static @Nullable QuadcopterEntity piloted() {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        return drone != null && drone.getId() == droneId && session.isFpv() && session.sim() != null ? drone : null;
    }

    static void spawn(MinecraftServer server, DroneBuild build, Identifier goggles, Map<String, Float> tune, double distance, boolean fly) {
        ServerPlayer player = player(server);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        discardOld(server);
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        Vec3 feet = player.position();
        drone.snapTo(feet.x, feet.y, feet.z + distance, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf());
        drone.setBuild(build);
        drone.setTune(new FlightTune(tune));
        drone.setCustomName(Component.literal(CRAFT_NAME));
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        droneId = drone.getId();
        ItemStack head = ContentIds.item(goggles).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (head.isEmpty()) {
            throw new IllegalStateException("Brille nicht registriert: " + goggles);
        }
        head.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        if (previousHead == null) {
            previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
            previousHand = player.getMainHandItem().copy();
        }
        player.setItemSlot(EquipmentSlot.HEAD, head);
        if (!fly) {
            return;
        }
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    static void buildWall(MinecraftServer server, double distance) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        BlockPos feet = player.blockPosition();
        List<BlockPos> placed = new ArrayList<>();
        int z = feet.getZ() + (int) Math.round(distance);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = 0; dy <= 6; dy++) {
                BlockPos pos = new BlockPos(feet.getX() + dx, feet.getY() + dy, z);
                if (level.getBlockState(pos).isAir()) {
                    level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                    placed.add(pos);
                }
            }
        }
        wall = placed;
    }

    static void clearWall(MinecraftServer server) {
        List<BlockPos> blocks = wall;
        wall = List.of();
        if (blocks.isEmpty()) {
            return;
        }
        ServerLevel level = player(server).level();
        for (BlockPos pos : blocks) {
            if (level.getBlockState(pos).is(Blocks.STONE)) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
    }

    static void setTune(MinecraftServer server, Map<String, Float> tune) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(droneId) instanceof QuadcopterEntity drone) {
                drone.setTune(new FlightTune(tune));
                return;
            }
        }
        throw new IllegalStateException("Testdrohne " + droneId + " fehlt auf dem Server");
    }

    static Map<String, Float> serverTune(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(droneId) instanceof QuadcopterEntity drone) {
                return drone.tune().values();
            }
        }
        return Map.of();
    }

    static void release(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (!players.isEmpty()) {
            ServerPlayer player = players.getFirst();
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            ItemStack head = previousHead;
            if (head != null) {
                player.setItemSlot(EquipmentSlot.HEAD, head);
            }
            ItemStack hand = previousHand;
            if (hand != null) {
                player.setItemSlot(EquipmentSlot.MAINHAND, hand);
            }
        }
        discardOld(server);
        clearWall(server);
        previousHead = null;
        previousHand = null;
        droneId = -1;
    }

    private static void discardOld(MinecraftServer server) {
        if (droneId < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(droneId) instanceof QuadcopterEntity old) {
                old.discard();
            }
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }
}
