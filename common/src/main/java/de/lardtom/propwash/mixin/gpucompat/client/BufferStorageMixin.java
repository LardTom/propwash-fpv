package de.lardtom.propwash.mixin.gpucompat.client;

import com.mojang.renderpearl.backend.opengl.BufferStorage;
import de.lardtom.propwash.gpucompat.client.GpuCompatHooks;
import org.lwjgl.opengl.GLCapabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(BufferStorage.class)
public abstract class BufferStorageMixin {
    @Inject(method = "create", at = @At("HEAD"))
    private static void propwash$offered(GLCapabilities capabilities, Set<String> enabledExtensions, boolean forceMutable,
                                         CallbackInfoReturnable<BufferStorage> cir) {
        GpuCompatHooks.storageOffered(capabilities.GL_ARB_buffer_storage);
    }

    @ModifyVariable(method = "create", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static boolean propwash$compat(boolean forceMutable) {
        return GpuCompatHooks.forceMutable(forceMutable);
    }

    @Inject(method = "create", at = @At("RETURN"))
    private static void propwash$created(CallbackInfoReturnable<BufferStorage> cir) {
        GpuCompatHooks.storageCreated(cir.getReturnValue());
    }
}
