package de.lardtom.propwash.mixin.race.client;

import de.lardtom.propwash.race.client.GateEditor;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void propwash$gateEditScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        if (GateEditor.onScroll(handle, xoffset, yoffset)) {
            ci.cancel();
        }
    }
}
