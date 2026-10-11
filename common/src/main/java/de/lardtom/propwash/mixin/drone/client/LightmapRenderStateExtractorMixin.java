package de.lardtom.propwash.mixin.drone.client;

import de.lardtom.propwash.drone.client.FpvView;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {
    @Inject(method = "extract", at = @At("TAIL"))
    private void propwash$ignorePilotEffects(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
        if (FpvView.active() && renderState.needsUpdate) {
            FpvView.clearPilotLightEffects(renderState);
        }
    }
}
