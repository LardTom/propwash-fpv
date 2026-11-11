package de.lardtom.propwash.mixin.share.client;

import de.lardtom.propwash.share.client.chat.ShareChat;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GuiMessage.class)
public abstract class GuiMessageMixin {
    @ModifyArg(method = "splitLines",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"),
            index = 0)
    private FormattedText propwash$shareCodes(FormattedText message) {
        return ShareChat.display(message);
    }
}
