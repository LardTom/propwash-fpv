package de.lardtom.propwash.mixin.drone.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import de.lardtom.propwash.drone.client.PilotBody;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V", shift = At.Shift.AFTER))
    private void propwash$idlePilotBody(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (PilotBody.idle(self)) {
            PilotBody.clearInput(self);
        }
    }

    @ModifyExpressionValue(method = "sendPosition",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isControlledCamera()Z"))
    private boolean propwash$reportPilotBody(boolean controlledCamera) {
        return controlledCamera || PilotBody.reportsPosition((LocalPlayer) (Object) this);
    }
}
