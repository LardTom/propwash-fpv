package de.lardtom.propwash.mixin.core.client;

import com.llamalad7.mixinextras.sugar.Local;
import de.lardtom.propwash.preview.PreviewBuild;
import de.lardtom.propwash.preview.client.PreviewMark;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;applyCursor(Lcom/mojang/blaze3d/platform/Window;)V"))
    private void propwash$previewMark(DeltaTracker deltaTracker, boolean shouldRenderLevel, boolean resourcesLoaded, CallbackInfo ci,
                                      @Local GuiGraphicsExtractor graphics) {
        if (PreviewBuild.ENABLED) {
            PreviewMark.extract(graphics, resourcesLoaded);
        }
    }
}
