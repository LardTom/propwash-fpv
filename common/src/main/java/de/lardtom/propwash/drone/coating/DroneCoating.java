package de.lardtom.propwash.drone.coating;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public final class DroneCoating {
    private DroneCoating() {
    }

    public static boolean coat(ServerPlayer player, QuadcopterEntity drone, ItemStack spray) {
        if (drone.waterproof()) {
            player.sendOverlayMessage(Component.translatable("propwash.drone.coating.already").withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (CoatingRules.usedUp(spray.getDamageValue())) {
            return false;
        }
        drone.setWaterproof(true);
        spray.hurtAndBreak(CoatingRules.COST, player, EquipmentSlot.MAINHAND);
        ServerLevel level = player.level();
        level.playSound(null, drone.getX(), drone.getY(), drone.getZ(), SoundEvents.HONEYCOMB_WAX_ON, SoundSource.PLAYERS, 0.8F, 1.3F);
        level.sendParticles(ParticleTypes.WAX_ON, drone.getX(), drone.getY() + drone.getBbHeight(), drone.getZ(), 12, 0.2, 0.1, 0.2, 0.5);
        int left = spray.isEmpty() ? 0 : CoatingRules.usesLeft(spray.getDamageValue());
        String key = WorldRules.on(level.getServer(), WorldRuleSpecs.WATERPROOF_DRONES) ? "propwash.drone.coating.done_rule"
                : "propwash.drone.coating.done";
        player.sendOverlayMessage(Component.translatable(key, left).withStyle(ChatFormatting.AQUA));
        return true;
    }
}
