package de.lardtom.nsprobe.mixin;

import de.lardtom.propwash.osd.OsdElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(OsdElement.class)
public interface OsdElementInvoker {
    @Invoker("<init>")
    static OsdElement nsprobe$create(String name, int ordinal, int baseCol, int baseRow, OsdElement.Align align, boolean classicEnabled,
                                     boolean movable, OsdElement.Group group) {
        throw new AssertionError();
    }
}
