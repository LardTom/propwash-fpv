package de.lardtom.propwash.mixin.zone.client;

import de.lardtom.propwash.zone.client.ZoneEditor;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void propwash$zoneEditKeys(long handle, int action, KeyEvent event, CallbackInfo ci) {
        if (ZoneEditor.onKey(handle, action, event)) {
            ci.cancel();
        }
    }
}
