package de.lardtom.propwash.mixin.ui.client;

import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.SliderLook;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.theme.ThemedWidgets;
import de.lardtom.propwash.ui.client.theme.WidgetState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin extends AbstractWidget.WithInactiveMessage {
    @Shadow
    protected double value;

    @Unique
    private int propwash$labelWidth;

    protected AbstractSliderButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Shadow
    protected abstract void setValue(double newValue);

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void propwash$themedSlider(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (!ThemedScreen.active()) {
            this.propwash$labelWidth = 0;
            ThemeStats.vanilla(ThemeStats.Kind.SLIDER);
            return;
        }
        Font font = Minecraft.getInstance().font;
        WidgetState state = WidgetState.of(this);
        SliderLook look = SliderLook.of(this);
        Component label = look.label();
        Component shown = look.value();
        int labelWidth = look.labelWidth();
        boolean modified = look.modified();
        this.propwash$labelWidth = labelWidth;
        ThemedWidgets.slider(graphics, font, this.getX(), this.getY(), this.getWidth(), this.getHeight(), labelWidth, label, shown, this.value, state,
                modified);
        this.handleCursor(graphics);
        ThemeStats.themed(ThemeStats.Kind.SLIDER);
        ci.cancel();
    }

    @Inject(method = "setValueFromMouse", at = @At("HEAD"), cancellable = true)
    private void propwash$themedValueFromMouse(MouseButtonEvent event, CallbackInfo ci) {
        if (!ThemedScreen.active()) {
            return;
        }
        int left = this.getX() + this.propwash$labelWidth;
        if (this.propwash$labelWidth > 0 && event.x() < left) {
            ci.cancel();
            return;
        }
        this.setValue(Metrics.sliderFraction(event.x(), left, this.getX() + this.getWidth() - left));
        ci.cancel();
    }
}
