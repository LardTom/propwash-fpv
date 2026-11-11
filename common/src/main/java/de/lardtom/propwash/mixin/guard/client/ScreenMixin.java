package de.lardtom.propwash.mixin.guard.client;

import de.lardtom.propwash.guard.client.GuardClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "defaultHandleGameClickEvent", at = @At("HEAD"), cancellable = true)
    private static void propwash$openNamespaceReport(ClickEvent event, Minecraft minecraft, Screen screen, CallbackInfo ci) {
        if (GuardClient.handleClick(event, minecraft, screen)) {
            ci.cancel();
        }
    }
}
