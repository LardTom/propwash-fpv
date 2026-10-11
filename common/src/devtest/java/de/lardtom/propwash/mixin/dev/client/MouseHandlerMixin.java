package de.lardtom.propwash.mixin.dev.client;

import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.dev.client.DetachedMouse;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow
    private double xpos;
    @Shadow
    private double ypos;
    @Shadow
    private boolean mouseGrabbed;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void propwash$detach(CallbackInfo ci) {
        if (DetachedMouse.requested()) {
            DetachedMouse.install();
        }
    }

    @Inject(method = "resyncMousePosition", at = @At("HEAD"), cancellable = true)
    private void propwash$resyncFromVirtualCursor(CallbackInfo ci) {
        if (!DetachedMouse.installed()) {
            return;
        }
        if (!this.mouseGrabbed) {
            Window window = DetachedMouse.window();
            this.xpos = DetachedMouse.x(window);
            this.ypos = DetachedMouse.y(window);
        }
        ci.cancel();
    }
}
