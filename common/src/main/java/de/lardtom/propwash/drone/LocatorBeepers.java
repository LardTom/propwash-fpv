package de.lardtom.propwash.drone;

import de.lardtom.propwash.drone.net.BeeperOffC2S;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.server.ServerRules;
import net.minecraft.server.level.ServerPlayer;

public final class LocatorBeepers {
    private LocatorBeepers() {
    }

    static void onBeeperOff(BeeperOffC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        if (!(player.level().getEntity(payload.entityId()) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            return;
        }
        if (!drone.beeperBy().filter(player.getUUID()::equals).isPresent()) {
            return;
        }
        double range = RadioLink.releaseRange(ServerRules.maxPilotDistance(player), DronePiloting.freeRange(player, drone));
        if (player.position().distanceTo(drone.position()) <= range) {
            drone.setBeeper(null);
        }
    }
}
