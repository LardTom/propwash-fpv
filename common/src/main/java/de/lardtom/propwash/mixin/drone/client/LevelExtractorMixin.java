package de.lardtom.propwash.mixin.drone.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.drone.client.render.FpvProps;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @ModifyExpressionValue(method = "extract", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;consumeFrustumUpdate()Z"))
    private boolean propwash$refreshFrustumWhileFlying(boolean original) {
        return original || FpvView.active();
    }

    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void propwash$hidePilotBodyAtLens(Entity entity, Frustum frustum, double camX, double camY, double camZ, float partialTicks,
                                              long chunkFadeDuration, CallbackInfoReturnable<Boolean> cir) {
        if (FpvView.hidesBody(entity, camX, camY, camZ)) {
            cir.setReturnValue(false);
        }
    }

    @ModifyExpressionValue(method = "extractVisibleEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;isDetached()Z"))
    private boolean propwash$extractCameraDroneForFpvProps(boolean detached, @Local Entity entity) {
        return detached || FpvProps.extractsCameraDrone(entity);
    }

    @ModifyExpressionValue(method = "extractVisibleEntities", at = @At(value = "CONSTANT",
            args = "classValue=net/minecraft/client/player/LocalPlayer"))
    private boolean propwash$showPilotBody(boolean isLocalPlayer) {
        return isLocalPlayer && !FpvView.active();
    }

    @Inject(method = "extractPlayerState", at = @At("TAIL"))
    private void propwash$clearPilotScreenEffects(Camera camera, DeltaTracker deltaTracker, float worldPartialTicks, PlayerRenderState state,
                                                  CallbackInfo ci) {
        if (FpvView.active()) {
            FpvView.clearPilotScreenEffects(state);
        }
    }
}
