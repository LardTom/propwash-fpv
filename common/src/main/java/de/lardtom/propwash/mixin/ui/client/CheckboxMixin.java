package de.lardtom.propwash.mixin.ui.client;

import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.theme.ThemedWidgets;
import de.lardtom.propwash.ui.client.theme.WidgetState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Checkbox.class)
public abstract class CheckboxMixin extends AbstractButton {
    @Shadow
    private boolean selected;

    protected CheckboxMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "extractContents", at = @At("HEAD"), cancellable = true)
    private void propwash$themedContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (!ThemedScreen.active()) {
            ThemeStats.vanilla(ThemeStats.Kind.CHECKBOX);
            return;
        }
        Font font = Minecraft.getInstance().font;
        ThemedWidgets.checkbox(graphics, font, this.getX(), this.getY(), this.getWidth(), this.getHeight(), Checkbox.getBoxSize(font), this.message,
                this.selected, WidgetState.of(this));
        ThemeStats.themed(ThemeStats.Kind.CHECKBOX);
        ci.cancel();
    }
}
