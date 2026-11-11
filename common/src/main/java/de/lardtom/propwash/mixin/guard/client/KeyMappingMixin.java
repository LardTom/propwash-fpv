package de.lardtom.propwash.mixin.guard.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.guard.client.KeyMappingOrigins;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
    @Inject(method = "<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;I)V",
            at = @At("TAIL"))
    private void propwash$recordRegistrant(String name, InputConstants.Type type, int key, KeyMapping.Category category, int order, CallbackInfo ci) {
        KeyMappingOrigins.record(name);
    }
}
