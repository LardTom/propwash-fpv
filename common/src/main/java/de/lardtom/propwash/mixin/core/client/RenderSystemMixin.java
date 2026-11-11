package de.lardtom.propwash.mixin.core.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import de.lardtom.propwash.preview.PreviewBuild;
import de.lardtom.propwash.preview.client.PreviewPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSystem.class)
public abstract class RenderSystemMixin {
    @Inject(method = "getCompiledPipelineNullable", at = @At("HEAD"), cancellable = true)
    private static void propwash$previewPipeline(RenderPipeline pipeline, CallbackInfoReturnable<CompiledRenderPipeline> cir) {
        if (PreviewBuild.ENABLED) {
            CompiledRenderPipeline own = PreviewPipeline.lookup(pipeline);
            if (own != null) {
                cir.setReturnValue(own);
            }
        }
    }
}
