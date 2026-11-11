package de.lardtom.propwash.mixin.gpucompat.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.renderpearl.backend.opengl.DirectStateAccess;
import de.lardtom.propwash.gpucompat.client.GpuCompatHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DirectStateAccess.class)
public abstract class DirectStateAccessMixin {
    @ModifyExpressionValue(method = "create",
            at = @At(value = "FIELD", target = "Lorg/lwjgl/opengl/GLCapabilities;GL_ARB_direct_state_access:Z"))
    private static boolean propwash$compat(boolean offered) {
        return GpuCompatHooks.directStateAccess(offered);
    }

    @Inject(method = "create", at = @At("RETURN"))
    private static void propwash$created(CallbackInfoReturnable<DirectStateAccess> cir) {
        GpuCompatHooks.dsaCreated(cir.getReturnValue());
    }
}
