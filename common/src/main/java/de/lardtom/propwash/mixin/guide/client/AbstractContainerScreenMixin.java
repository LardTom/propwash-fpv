package de.lardtom.propwash.mixin.guide.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import de.lardtom.propwash.guide.client.HoldToOpen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow
    protected @Nullable Slot hoveredSlot;

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Shadow
    @Final
    protected int imageWidth;

    @Inject(method = "extractTooltip", at = @At("HEAD"))
    private void propwash$flightSchoolHold(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        HoldToOpen.containerFrame((AbstractContainerScreen<?>) (Object) this, graphics, this.hoveredSlot, this.leftPos, this.topPos, this.imageWidth,
                mouseX, mouseY);
    }

    @ModifyExpressionValue(method = "extractTooltip", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;getTooltipFromContainerItem(Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;"))
    private List<Component> propwash$flightSchoolLine(List<Component> lines) {
        return HoldToOpen.withHint(lines);
    }
}
