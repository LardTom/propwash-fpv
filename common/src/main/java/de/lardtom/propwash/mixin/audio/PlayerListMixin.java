package de.lardtom.propwash.mixin.audio;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import de.lardtom.propwash.audio.DroneHearing;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @WrapOperation(method = "broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getX()D"))
    private double propwash$hearX(ServerPlayer player, Operation<Double> original, @Local(argsOnly = true, ordinal = 0) double x,
                                  @Local(argsOnly = true, ordinal = 1) double y, @Local(argsOnly = true, ordinal = 2) double z,
                                  @Share("propwash$drone") LocalRef<QuadcopterEntity> heard) {
        QuadcopterEntity drone = DroneHearing.closer(player, x, y, z);
        heard.set(drone);
        return drone != null ? drone.getX() : original.call(player);
    }

    @WrapOperation(method = "broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getY()D"))
    private double propwash$hearY(ServerPlayer player, Operation<Double> original, @Share("propwash$drone") LocalRef<QuadcopterEntity> heard) {
        QuadcopterEntity drone = heard.get();
        return drone != null ? drone.getY() : original.call(player);
    }

    @WrapOperation(method = "broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getZ()D"))
    private double propwash$hearZ(ServerPlayer player, Operation<Double> original, @Share("propwash$drone") LocalRef<QuadcopterEntity> heard) {
        QuadcopterEntity drone = heard.get();
        return drone != null ? drone.getZ() : original.call(player);
    }
}
