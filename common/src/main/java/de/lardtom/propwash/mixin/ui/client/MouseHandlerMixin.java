package de.lardtom.propwash.mixin.ui.client;

import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.theme.TooltipScroll;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void propwash$scrollTallTooltip(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        if (ThemedScreen.active() && TooltipScroll.scroll(yoffset)) {
            ci.cancel();
        }
    }
}
