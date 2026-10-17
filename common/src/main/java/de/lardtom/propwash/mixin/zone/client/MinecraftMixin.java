package de.lardtom.propwash.mixin.zone.client;

import de.lardtom.propwash.zone.client.ZoneEditor;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void propwash$zoneEditKeys(CallbackInfo ci) {
        ZoneEditor.beforeKeybinds((Minecraft) (Object) this);
    }
}
