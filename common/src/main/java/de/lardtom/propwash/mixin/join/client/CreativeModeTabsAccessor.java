package de.lardtom.propwash.mixin.join.client;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreativeModeTabs.class)
public interface CreativeModeTabsAccessor {
    @Accessor("CACHED_PARAMETERS")
    static void propwash$setCachedParameters(CreativeModeTab.@Nullable ItemDisplayParameters parameters) {
        throw new AssertionError();
    }
}
