package de.lardtom.propwash.drone.bind;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ReceiverBinds {
    public static final double SEARCH_RANGE = 128.0;
    public static final String LEGACY_SECRET_FILE = "receiver_bind.dat";

    private ReceiverBinds() {
    }

    public static void init() {
        BindComponents.bootstrap();
        ServerBus.STARTED.register(ReceiverBinds::dropLegacySecret);
    }

    public static BindAccess access(ServerPlayer player, QuadcopterEntity drone) {
        return BindAccess.of(drone.receiverBind(), player.getUUID());
    }

    public static BindAccess bind(ServerPlayer player, QuadcopterEntity drone) {
        BindAccess before = access(player, drone);
        drone.setReceiverBind(ReceiverBind.of(player.getUUID()));
        return before;
    }

    public static boolean reset(QuadcopterEntity drone) {
        ReceiverBind bind = drone.receiverBind();
        if (bind == null || bind.cleared()) {
            return false;
        }
        drone.setReceiverBind(ReceiverBind.CLEARED);
        return true;
    }

    public static Optional<QuadcopterEntity> nearestBound(ServerPlayer player) {
        UUID self = player.getUUID();
        List<QuadcopterEntity> drones = player.level().getEntitiesOfClass(QuadcopterEntity.class,
                player.getBoundingBox().inflate(SEARCH_RANGE),
                drone -> !drone.isRemoved() && (drone.pilot().isEmpty() || drone.isFlownBy(self)) && drone.receiverBind() != null);
        double rangeSq = SEARCH_RANGE * SEARCH_RANGE;
        return BindSearch.pick(drones, drone -> access(player, drone),
                drone -> {
                    double distance = drone.distanceToSqr(player);
                    return distance <= rangeSq ? distance : Double.POSITIVE_INFINITY;
                });
    }

    public static void point(ItemStack stack, QuadcopterEntity drone) {
        stack.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
    }

    public static Path legacySecretFile(Path dataFolder) {
        return dataFolder.resolve(Constants.MOD_ID).resolve(LEGACY_SECRET_FILE);
    }

    public static boolean dropLegacyFile(Path file) {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            Constants.LOG.warn("Alte Bindungs-Datei {} ließ sich nicht löschen: {}", file.getFileName(), e.toString());
            return false;
        }
    }

    private static void dropLegacySecret(MinecraftServer server) {
        dropLegacyFile(legacySecretFile(server.getWorldPath(LevelResource.DATA)));
    }
}
