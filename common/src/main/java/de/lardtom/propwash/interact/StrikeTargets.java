package de.lardtom.propwash.interact;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.UUID;

public final class StrikeTargets {
    private StrikeTargets() {
    }

    public static boolean solid(Entity entity, UUID pilot) {
        return entity instanceof LivingEntity living && valid(living) && !living.getUUID().equals(pilot);
    }

    public static boolean valid(LivingEntity entity) {
        return entity.isAlive() && !entity.isRemoved() && !entity.isSpectator() && !(entity instanceof ArmorStand);
    }
}
