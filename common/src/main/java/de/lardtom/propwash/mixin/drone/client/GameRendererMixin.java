package de.lardtom.propwash.mixin.drone.client;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.drone.client.render.FpvPropPass;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @ModifyArg(method = "renderLevel()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"),
            require = 0)
    private Matrix4f propwash$fpvProjection(Matrix4f projection) {
        FpvPropPass.captureProjection(projection);
        return projection;
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void propwash$hideHands(CameraRenderState cameraState, PlayerRenderState playerState, GpuTextureView depthTextureView,
                                    CallbackInfo ci) {
        GameRenderer self = (GameRenderer) (Object) this;
        FpvPropPass.render(cameraState, self.featureRenderDispatcher(), self.mainRenderTarget(), depthTextureView);
        if (FpvView.active()) {
            ci.cancel();
        }
    }
}
