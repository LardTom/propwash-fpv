package de.lardtom.propwash.mixin.race.client;

import de.lardtom.propwash.race.client.GateEditor;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void propwash$gateEditKeys(CallbackInfo ci) {
        GateEditor.beforeKeybinds((Minecraft) (Object) this);
    }
}
