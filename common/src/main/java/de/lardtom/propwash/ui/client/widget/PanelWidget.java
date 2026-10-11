package de.lardtom.propwash.ui.client.widget;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.ui.client.theme.WidgetState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public abstract class PanelWidget extends AbstractWidget {
    private @Nullable ScreenRectangle clip;

    protected PanelWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    public PanelWidget clip(@Nullable ScreenRectangle clip) {
        this.clip = clip;
        return this;
    }

    public @Nullable ScreenRectangle clipRect() {
        return this.clip;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!super.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        return this.clip == null || this.clip.containsPoint((int) Math.floor(mouseX), (int) Math.floor(mouseY));
    }

    protected boolean acceptsRightClick() {
        return false;
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
        return buttonInfo.button() == InputConstants.MOUSE_BUTTON_LEFT
                || (this.acceptsRightClick() && buttonInfo.button() == InputConstants.MOUSE_BUTTON_RIGHT);
    }

    protected static boolean isRightClick(MouseButtonEvent event) {
        return event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
    }

    protected static int stepMultiplier(boolean shift) {
        return shift ? 10 : 1;
    }

    protected static boolean shiftDown() {
        return Minecraft.getInstance().hasShiftDown();
    }

    protected Font font() {
        return Minecraft.getInstance().font;
    }

    protected WidgetState state() {
        return WidgetState.of(this);
    }

    protected boolean highlighted() {
        return this.isHovered() || this.isFocused();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
