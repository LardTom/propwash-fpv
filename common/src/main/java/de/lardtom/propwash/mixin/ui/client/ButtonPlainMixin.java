package de.lardtom.propwash.mixin.ui.client;

import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.theme.ThemedWidgets;
import de.lardtom.propwash.ui.client.theme.WidgetState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Button.Plain.class)
public abstract class ButtonPlainMixin extends Button {
    protected ButtonPlainMixin(int x, int y, int width, int height, Component message, Button.OnPress onPress, Button.CreateNarration narration) {
        super(x, y, width, height, message, onPress, narration);
    }

    @Inject(method = "extractContents", at = @At("HEAD"), cancellable = true)
    private void propwash$themedContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (!ThemedScreen.active()) {
            ThemeStats.vanilla(ThemeStats.Kind.BUTTON);
            return;
        }
        ThemedWidgets.button(graphics, Minecraft.getInstance().font, this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.message,
                ThemeDraw.ButtonStyle.NORMAL, WidgetState.of(this), false);
        ThemeStats.themed(ThemeStats.Kind.BUTTON);
        ci.cancel();
    }
}
