package de.lardtom.propwash.mixin.drone.client;

import de.lardtom.propwash.drone.client.RemoteHoldPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void propwash$holdRemote(HumanoidRenderState state, CallbackInfo ci) {
        RemoteHoldPose.apply((HumanoidModel<?>) (Object) this, state);
    }
}
