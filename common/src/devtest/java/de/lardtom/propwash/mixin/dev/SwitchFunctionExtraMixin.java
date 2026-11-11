package de.lardtom.propwash.mixin.dev;

import de.lardtom.propwash.input.DevExtraFunction;
import de.lardtom.propwash.input.SwitchFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Mixin(SwitchFunction.class)
public abstract class SwitchFunctionExtraMixin {
    @Shadow
    @Final
    @Mutable
    private static SwitchFunction[] $VALUES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void propwash$appendExtra(CallbackInfo ci) {
        if (!DevExtraFunction.requested()) {
            return;
        }
        SwitchFunction[] values = Arrays.copyOf($VALUES, $VALUES.length + 1);
        values[values.length - 1] = SwitchFunctionExtraInvoker.propwash$create(DevExtraFunction.NAME, values.length - 1);
        $VALUES = values;
    }

    @Inject(method = "command()Z", at = @At("HEAD"), cancellable = true)
    private void propwash$extraIsNoCommand(CallbackInfoReturnable<Boolean> cir) {
        if (DevExtraFunction.is((SwitchFunction) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "translationKey", at = @At("HEAD"), cancellable = true)
    private void propwash$extraLabel(CallbackInfoReturnable<String> cir) {
        if (DevExtraFunction.is((SwitchFunction) (Object) this)) {
            cir.setReturnValue(DevExtraFunction.LABEL);
        }
    }
}
