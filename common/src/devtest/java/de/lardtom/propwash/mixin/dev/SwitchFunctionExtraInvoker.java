package de.lardtom.propwash.mixin.dev;

import de.lardtom.propwash.input.SwitchFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SwitchFunction.class)
public interface SwitchFunctionExtraInvoker {
    @Invoker("<init>")
    static SwitchFunction propwash$create(String name, int ordinal) {
        throw new AssertionError();
    }
}
