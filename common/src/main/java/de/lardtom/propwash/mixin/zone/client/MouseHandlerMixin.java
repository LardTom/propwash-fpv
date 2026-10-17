package de.lardtom.propwash.mixin.zone.client;

import de.lardtom.propwash.zone.client.ZoneEditor;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void propwash$zoneEditScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        if (ZoneEditor.onScroll(handle, xoffset, yoffset)) {
            ci.cancel();
        }
    }
}
