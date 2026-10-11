package de.lardtom.propwash.mixin.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.dev.client.DetachedMouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InputConstants.class)
public abstract class InputConstantsMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private static void propwash$grabWithoutSystemCursor(Window window, double xpos, double ypos, CallbackInfo ci) {
        if (DetachedMouse.installed()) {
            DetachedMouse.grab(xpos, ypos);
            ci.cancel();
        }
    }

    @Inject(method = "releaseMouse", at = @At("HEAD"), cancellable = true)
    private static void propwash$releaseWithoutSystemCursor(Window window, double xpos, double ypos, CallbackInfo ci) {
        if (DetachedMouse.installed()) {
            DetachedMouse.release(xpos, ypos);
            ci.cancel();
        }
    }
}
