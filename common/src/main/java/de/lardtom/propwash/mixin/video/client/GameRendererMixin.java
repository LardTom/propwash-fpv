package de.lardtom.propwash.mixin.video.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import de.lardtom.propwash.video.client.CleanFrameTap;
import de.lardtom.propwash.video.client.HeadlightPass;
import de.lardtom.propwash.video.client.VideoPass;
import de.lardtom.propwash.video.client.record.ActionCamRecorder;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;applyPostEffects()V", shift = At.Shift.AFTER),
            require = 0)
    private void propwash$videoFeed(CallbackInfo ci) {
        RenderTarget main = ((GameRenderer) (Object) this).mainRenderTarget();
        CleanFrameTap.offer(main);
        ActionCamRecorder.INSTANCE.onRenderedFrame(main);
        VideoPass.INSTANCE.apply(main);
    }

    @ModifyArg(method = "renderLevel()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"),
            require = 0)
    private Matrix4f propwash$levelProjection(Matrix4f projection) {
        HeadlightPass.INSTANCE.captureProjection(projection);
        return projection;
    }

    @Inject(method = "renderLevel()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;render3dHud(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/OptionsRenderState;Z)V"),
            require = 0)
    private void propwash$headlight(CallbackInfo ci) {
        HeadlightPass.INSTANCE.apply(((GameRenderer) (Object) this).mainRenderTarget());
    }
}
