package de.lardtom.propwash.mixin.video.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import de.lardtom.propwash.video.client.SpectateSession;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @WrapWithCondition(method = "keyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;pauseGame(Z)V"))
    private boolean propwash$escapeLeavesSpectating(Minecraft minecraft, boolean suppressPauseMenu) {
        return !SpectateSession.INSTANCE.consumeEscape();
    }
}
