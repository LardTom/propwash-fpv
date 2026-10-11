package de.lardtom.propwash.mixin.ui.client;

import de.lardtom.propwash.ui.client.theme.GuiLayerAccess;
import de.lardtom.propwash.ui.client.theme.ScreenTooltipPositioner;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.theme.TooltipScroll;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin implements GuiLayerAccess {
    @Shadow
    @Final
    private GuiRenderState guiRenderState;

    @Shadow
    @Final
    private Matrix3x2fStack pose;

    @Unique
    private final Deque<ScreenRectangle> propwash$scissors = new ArrayDeque<>();

    @Shadow
    public abstract int guiWidth();

    @Shadow
    public abstract int guiHeight();

    @Inject(method = "enableScissor", at = @At("TAIL"))
    private void propwash$pushScissor(int x0, int y0, int x1, int y1, CallbackInfo ci) {
        ScreenRectangle rectangle = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformAxisAligned(this.pose);
        ScreenRectangle last = this.propwash$scissors.peekLast();
        if (last == null) {
            last = new ScreenRectangle(0, 0, this.guiWidth(), this.guiHeight());
        }
        ScreenRectangle intersection = rectangle.intersection(last);
        this.propwash$scissors.addLast(intersection != null ? intersection : ScreenRectangle.empty());
    }

    @Inject(method = "disableScissor", at = @At("HEAD"))
    private void propwash$popScissor(CallbackInfo ci) {
        this.propwash$scissors.pollLast();
    }

    @ModifyVariable(method = "tooltip*", at = @At("HEAD"), argsOnly = true)
    private ClientTooltipPositioner propwash$keepTooltipOnScreen(ClientTooltipPositioner positioner) {
        return ThemedScreen.active() ? ScreenTooltipPositioner.of(positioner) : positioner;
    }

    @ModifyVariable(method = "tooltip*", at = @At("HEAD"), argsOnly = true)
    private List<ClientTooltipComponent> propwash$scrollTallTooltip(List<ClientTooltipComponent> lines) {
        return ThemedScreen.active() ? TooltipScroll.window(Minecraft.getInstance().font, lines, this.guiHeight()) : lines;
    }

    @Override
    public GuiRenderState propwash$renderState() {
        return this.guiRenderState;
    }

    @Override
    public @Nullable ScreenRectangle propwash$scissor() {
        return this.propwash$scissors.peekLast();
    }
}
