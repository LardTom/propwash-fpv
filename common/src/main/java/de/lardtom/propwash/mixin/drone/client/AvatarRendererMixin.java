package de.lardtom.propwash.mixin.drone.client;

import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.drone.client.PilotBodyPose;
import de.lardtom.propwash.drone.client.PilotHeads;
import de.lardtom.propwash.drone.client.RemoteHoldPose;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void propwash$posePilotBody(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        PilotHeads.apply(entity, state);
        if (FpvView.active() && FpvView.isPilotBody(entity)) {
            PilotBodyPose.apply(state);
        }
        RemoteHoldPose.extract(entity, state);
    }
}
