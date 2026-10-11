package de.lardtom.propwash.mixin.drone.client;

import de.lardtom.propwash.drone.client.PilotBody;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void propwash$holdUnloadedPilotBody(Vec3 input, CallbackInfo ci) {
        if (PilotBody.held(this)) {
            LivingEntity self = (LivingEntity) (Object) this;
            self.setDeltaMovement(Vec3.ZERO);
            ci.cancel();
        }
    }
}
