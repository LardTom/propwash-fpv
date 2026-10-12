package de.lardtom.propwash.drone.tag;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Set;

public final class DroneTags {
    private DroneTags() {
    }

    public static void placedBy(Entity drone, Player player) {
        apply(drone, DroneTagRules.replace(drone.entityTags(), DroneTagRules.OWNER_PREFIX,
                DroneTagRules.ownerTag(player.getGameProfile().name())));
    }

    public static void flownBy(Entity drone, Player player) {
        apply(drone, DroneTagRules.replace(drone.entityTags(), DroneTagRules.PILOT_PREFIX,
                DroneTagRules.pilotTag(player.getGameProfile().name())));
    }

    public static void flightState(Entity drone, boolean flying, boolean autopilot) {
        Set<String> tags = drone.entityTags();
        if (tags.contains(DroneTagRules.FLYING) == flying && tags.contains(DroneTagRules.AUTOPILOT) == autopilot) {
            return;
        }
        apply(drone, DroneTagRules.flightState(tags, flying, autopilot));
    }

    private static void apply(Entity drone, DroneTagRules.Change change) {
        for (String tag : change.remove()) {
            drone.removeTag(tag);
        }
        for (String tag : change.add()) {
            drone.addTag(tag);
        }
    }
}
