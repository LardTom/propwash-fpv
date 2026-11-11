package de.lardtom.propwash.mixin.share.client;

import de.lardtom.propwash.share.client.chat.ChatTooltipPositioner;
import de.lardtom.propwash.share.client.chat.ShareChat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
    @Shadow
    public abstract int guiWidth();

    @Shadow
    private void setTooltipForNextFrameInternal(Font font, List<ClientTooltipComponent> lines, int xo, int yo, ClientTooltipPositioner positioner,
                                                @Nullable Identifier style, boolean replaceExisting) {
        throw new AssertionError();
    }

    @Inject(method = "componentHoverEffect", at = @At("HEAD"), cancellable = true)
    private void propwash$shareCodePreview(Font font, Style hoveredStyle, int xMouse, int yMouse, CallbackInfo ci) {
        List<ClientTooltipComponent> lines = ShareChat.tooltip(hoveredStyle, font, this.guiWidth());
        if (lines != null) {
            this.setTooltipForNextFrameInternal(font, lines, xMouse, yMouse, ChatTooltipPositioner.INSTANCE, null, false);
            ci.cancel();
        }
    }
}
