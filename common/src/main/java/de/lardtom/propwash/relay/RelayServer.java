package de.lardtom.propwash.relay;

import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class RelayServer {
    private static final RelayReach REACH = new RelayReach();

    private RelayServer() {
    }

    public static synchronized boolean inRange(ServerPlayer player, Vec3 drone, double releaseRange, double serverLimit) {
        if (player.position().distanceTo(drone) <= releaseRange) {
            return true;
        }
        if (Double.isFinite(serverLimit) || !Double.isFinite(releaseRange)) {
            return false;
        }
        int relays = WorldRules.get(player.level().getServer(), WorldRuleSpecs.SIGNAL_REPEATERS);
        return REACH.linked(player.level(), player.position(), drone, releaseRange, relays);
    }
}
