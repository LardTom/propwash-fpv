package de.lardtom.propwash.mixin.drone;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import de.lardtom.propwash.drone.PilotProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @ModifyReturnValue(method = "isInvulnerableTo", at = @At("RETURN"))
    private boolean propwash$protectPilot(boolean invulnerable, ServerLevel level, DamageSource source) {
        return invulnerable || PilotProtection.protects((ServerPlayer) (Object) this, source);
    }
}
