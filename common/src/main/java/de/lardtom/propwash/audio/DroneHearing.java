package de.lardtom.propwash.audio;

import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.server.SpectatorLinks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class DroneHearing {
    private DroneHearing() {
    }

    public static @Nullable QuadcopterEntity drone(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null || !server.isSameThread()) {
            return null;
        }
        QuadcopterEntity watched = usable(player, SpectatorLinks.watched(player));
        QuadcopterEntity piloted = usable(player, DronePiloting.pilotedBy(player.getUUID()).orElse(null));
        HearingPoints.View view = HearingPoints.view(watched != null, piloted != null ? piloted.pilotMode() : PilotMode.NONE);
        return switch (view) {
            case SPECTATOR -> watched;
            case FPV_PILOT -> piloted;
            case BODY, LINE_OF_SIGHT_PILOT -> null;
        };
    }

    public static @Nullable QuadcopterEntity closer(ServerPlayer player, double x, double y, double z) {
        QuadcopterEntity drone = drone(player);
        if (drone == null) {
            return null;
        }
        return HearingPoints.droneCloser(player.getX(), player.getY(), player.getZ(), drone.getX(), drone.getY(), drone.getZ(), x, y, z) ? drone : null;
    }

    public static double distanceSqr(ServerPlayer player, Vec3 at, double body) {
        QuadcopterEntity drone = drone(player);
        return drone == null ? body : Math.min(body, drone.distanceToSqr(at));
    }

    private static @Nullable QuadcopterEntity usable(ServerPlayer player, @Nullable QuadcopterEntity drone) {
        return drone != null && !drone.isRemoved() && drone.level() == player.level() ? drone : null;
    }
}
