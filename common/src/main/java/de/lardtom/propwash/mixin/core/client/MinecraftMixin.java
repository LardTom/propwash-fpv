package de.lardtom.propwash.mixin.core.client;

import de.lardtom.propwash.core.client.ClientBus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "renderFrame",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;update(Lnet/minecraft/client/DeltaTracker;)V"))
    private void propwash$frameStart(boolean advanceGameTime, CallbackInfo ci) {
        Minecraft self = (Minecraft) (Object) this;
        ClientBus.FRAME_START.fire(l -> l.onFrame(self, self.getDeltaTracker()));
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void propwash$disconnect(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
        ClientBus.fireDisconnect((Minecraft) (Object) this);
    }

    @Inject(method = "clearClientLevel", at = @At("HEAD"))
    private void propwash$clearClientLevel(Screen screen, CallbackInfo ci) {
        ClientBus.fireDisconnect((Minecraft) (Object) this);
    }
}
