package de.lardtom.nsprobe.mixin;

import de.lardtom.propwash.input.SwitchFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(SwitchFunction.class)
public abstract class SwitchFunctionMixin {
    @Shadow
    @Final
    @Mutable
    private static SwitchFunction[] $VALUES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void nsprobe$addConstant(CallbackInfo ci) {
        SwitchFunction[] values = Arrays.copyOf($VALUES, $VALUES.length + 1);
        values[values.length - 1] = SwitchFunctionInvoker.nsprobe$create("NSPROBE", values.length - 1);
        $VALUES = values;
    }
}
