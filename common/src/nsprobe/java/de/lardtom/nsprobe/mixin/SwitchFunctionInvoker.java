package de.lardtom.nsprobe.mixin;

import de.lardtom.propwash.input.SwitchFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SwitchFunction.class)
public interface SwitchFunctionInvoker {
    @Invoker("<init>")
    static SwitchFunction nsprobe$create(String name, int ordinal) {
        throw new AssertionError();
    }
}
