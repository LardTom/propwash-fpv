package de.lardtom.propwash.mixin.ui.client;

import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TooltipRenderUtil.class)
public abstract class TooltipRenderUtilMixin {
    @Inject(method = "extractTooltipBackground", at = @At("HEAD"), cancellable = true)
    private static void propwash$themedTooltip(GuiGraphicsExtractor graphics, int x, int y, int w, int h, @Nullable Identifier style,
                                               CallbackInfo ci) {
        ThemeStats.tooltip(x, y, w, h);
        boolean propwash = PropwashRegistries.TOOLTIP_STYLE.equals(style);
        if (!propwash && (style != null || !ThemedScreen.active())) {
            ThemeStats.vanilla(ThemeStats.Kind.TOOLTIP);
            return;
        }
        ThemeDraw.tooltipFrame(graphics, Math.max(x - 4, 0), Math.max(y - 4, 0), Math.min(x + w + 4, graphics.guiWidth()),
                Math.min(y + h + 4, graphics.guiHeight()));
        ThemeStats.themed(ThemeStats.Kind.TOOLTIP);
        ci.cancel();
    }
}
