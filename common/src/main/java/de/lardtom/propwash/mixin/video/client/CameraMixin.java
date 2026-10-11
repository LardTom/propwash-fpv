package de.lardtom.propwash.mixin.video.client;

import de.lardtom.propwash.video.client.VideoState;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void propwash$fpvFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (((Camera) (Object) this).isPanoramicMode()) {
            return;
        }
        float fov = VideoState.INSTANCE.fpvFov();
        if (fov > 0.0F) {
            cir.setReturnValue(fov);
        }
    }
}
