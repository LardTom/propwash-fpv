package de.lardtom.propwash.mixin.dev.client;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.api.CommandEncoderBackend;
import de.lardtom.propwash.gpucompat.client.GpuFaults;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder")
public abstract class GlCommandEncoderFaultMixin {
    @Inject(method = "copyTextureToTexture", at = @At("HEAD"), cancellable = true)
    private void propwash$copyFault(GpuTexture source, GpuTexture destination, int mipLevel, int destX, int destY, int sourceX, int sourceY,
                                    int width, int height, CallbackInfo ci) {
        if (GpuFaults.skipCopy()) {
            ci.cancel();
        }
    }

    @Inject(method = "copyTextureToBuffer(Lcom/mojang/renderpearl/api/textures/GpuTexture;Lcom/mojang/renderpearl/api/buffers/GpuBuffer;JLjava/lang/Runnable;IIIII)V",
            at = @At("HEAD"), cancellable = true)
    private void propwash$readFault(GpuTexture source, GpuBuffer destination, long offset, Runnable callback, int mipLevel, int x, int y, int width,
                                    int height, CallbackInfo ci) {
        int bytes = width * height * source.getFormat().blockSize();
        if (GpuFaults.zeroRead((CommandEncoderBackend) (Object) this, destination, offset, bytes, callback)) {
            ci.cancel();
        }
    }
}
