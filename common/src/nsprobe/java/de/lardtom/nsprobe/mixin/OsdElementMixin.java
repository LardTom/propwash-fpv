package de.lardtom.nsprobe.mixin;

import de.lardtom.propwash.osd.OsdElement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(OsdElement.class)
public abstract class OsdElementMixin {
    @Shadow
    @Final
    @Mutable
    private static OsdElement[] $VALUES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void nsprobe$addConstant(CallbackInfo ci) {
        OsdElement[] values = Arrays.copyOf($VALUES, $VALUES.length + 1);
        values[values.length - 1] = OsdElementInvoker.nsprobe$create("NSPROBE", values.length - 1, 0, 0, OsdElement.Align.LEFT, false, true,
                OsdElement.Group.values()[0]);
        $VALUES = values;
    }
}
