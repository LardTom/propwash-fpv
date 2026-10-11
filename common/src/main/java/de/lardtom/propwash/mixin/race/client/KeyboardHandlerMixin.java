package de.lardtom.propwash.mixin.race.client;

import de.lardtom.propwash.race.client.GateEditor;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void propwash$gateEditKeys(long handle, int action, KeyEvent event, CallbackInfo ci) {
        if (GateEditor.onKey(handle, action, event)) {
            ci.cancel();
        }
    }
}
