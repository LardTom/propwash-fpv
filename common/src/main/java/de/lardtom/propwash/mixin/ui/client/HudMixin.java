package de.lardtom.propwash.mixin.ui.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.lardtom.propwash.ui.client.OverlayMessageWrap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
    @Shadow
    private int overlayMessageTime;

    @Inject(method = "setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("TAIL"))
    private void propwash$longOverlayMessagesStayLonger(Component message, boolean animate, CallbackInfo ci) {
        this.overlayMessageTime += OverlayMessageWrap.extraTicks(message);
    }

    @WrapOperation(method = "extractOverlayMessage",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"))
    private void propwash$wrapLongOverlayMessage(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, int color,
                                                 Operation<Void> original) {
        if (!OverlayMessageWrap.extract(graphics, font, text, y, color)) {
            original.call(graphics, font, text, x, y, width, color);
        }
    }
}
