package de.lardtom.propwash.mixin.share.client;

import de.lardtom.propwash.share.client.chat.ShareChat;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void propwash$shareCodeClick(Style clicked, boolean allowInsertions, CallbackInfoReturnable<Boolean> cir) {
        if (ShareChat.click(clicked, allowInsertions, (Screen) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
