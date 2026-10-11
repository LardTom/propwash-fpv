package de.lardtom.propwash.mixin.ui.client;

import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.theme.ThemedWidgets;
import de.lardtom.propwash.ui.client.theme.WidgetState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(CycleButton.class)
public abstract class CycleButtonMixin<T> extends AbstractButton {
    @Shadow
    @Final
    private Component name;

    @Shadow
    @Final
    private Function<T, Component> valueStringifier;

    @Shadow
    @Final
    private CycleButton.DisplayState displayState;

    @Shadow
    @Final
    private CycleButton.SpriteSupplier<T> spriteSupplier;

    protected CycleButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Shadow
    public abstract T getValue();

    @SuppressWarnings("unchecked")
    @Inject(method = "extractContents", at = @At("HEAD"), cancellable = true)
    private void propwash$themedContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (!ThemedScreen.active()) {
            ThemeStats.vanilla(ThemeStats.Kind.CYCLE);
            return;
        }
        T value = this.getValue();
        CycleButton<T> self = (CycleButton<T>) (Object) this;
        if (this.spriteSupplier.apply(self, value) != null) {
            ThemeStats.vanilla(ThemeStats.Kind.CYCLE);
            return;
        }
        Font font = Minecraft.getInstance().font;
        WidgetState state = WidgetState.of(this);
        Component valueText = this.displayState == CycleButton.DisplayState.HIDE ? Component.empty() : this.valueStringifier.apply(value);
        boolean withLabel = this.displayState == CycleButton.DisplayState.NAME_AND_VALUE && !this.name.getString().isEmpty();
        boolean onOff = value instanceof Boolean && this.valueStringifier.apply((T) Boolean.TRUE) == CommonComponents.OPTION_ON;
        int controlNeed = onOff ? Metrics.TOGGLE_WIDTH + 5 + font.width(ThemedWidgets.onOff((Boolean) value))
                : font.width(valueText) + 2 * Metrics.ARROW_WIDTH + 8;
        int labelWidth = withLabel ? ThemedWidgets.labelWidth(font, this.name, this.getWidth(), controlNeed) : 0;
        Component label = withLabel ? this.name : null;
        if (value instanceof Boolean on && this.valueStringifier.apply((T) Boolean.TRUE) == CommonComponents.OPTION_ON) {
            if (this.displayState != CycleButton.DisplayState.HIDE) {
                valueText = ThemedWidgets.onOff(on);
            }
            ThemedWidgets.toggle(graphics, font, this.getX(), this.getY(), this.getWidth(), this.getHeight(), labelWidth, label, on, valueText, state,
                    false);
        } else {
            ThemedWidgets.cycle(graphics, font, this.getX(), this.getY(), this.getWidth(), this.getHeight(), labelWidth, label, valueText, state,
                    false);
        }
        ThemeStats.themed(ThemeStats.Kind.CYCLE);
        ci.cancel();
    }
}
