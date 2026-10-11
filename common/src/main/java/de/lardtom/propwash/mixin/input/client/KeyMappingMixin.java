package de.lardtom.propwash.mixin.input.client;

import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.platform.client.ClientServices;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
    @Inject(method = "same", at = @At("HEAD"), cancellable = true)
    private void propwash$sharedKey(KeyMapping other, CallbackInfoReturnable<Boolean> cir) {
        KeyMapping self = (KeyMapping) (Object) this;
        if (KeyDefaults.sharesKey(self.getName(), other.getName()) && ClientServices.PLATFORM.sameKeyModifier(self, other)) {
            cir.setReturnValue(false);
        }
    }
}
